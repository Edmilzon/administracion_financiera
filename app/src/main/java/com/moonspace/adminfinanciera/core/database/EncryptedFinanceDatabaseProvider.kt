package com.moonspace.adminfinanciera.core.database

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Opens one SQLCipher database per authenticated account. */
class EncryptedFinanceDatabaseProvider(context: Context) {
    private val appContext = context.applicationContext
    private val keyStore = DatabaseKeyStore(appContext)
    private val databases = mutableMapOf<String, OpenDatabase>()

    @Synchronized
    fun databaseFor(accountId: String): FinanceDatabase {
        require(accountId.isNotBlank()) { "An authenticated account is required." }
        val accountHash = accountHash(accountId)
        return databases.getOrPut(accountHash) {
            val passphrase = keyStore.getOrCreate(accountHash)
            val factory = SupportOpenHelperFactory(passphrase)
            OpenDatabase(
                database = Room.databaseBuilder(
                    appContext,
                    FinanceDatabase::class.java,
                    "finance_$accountHash.db"
                ).openHelperFactory(factory)
                    .addMigrations(FinanceDatabase.MIGRATION_1_2, FinanceDatabase.MIGRATION_2_3)
                    .build(),
                passphrase = passphrase
            )
        }.database
    }

    @Synchronized
    fun closeAccount(accountId: String) {
        val open = databases.remove(accountHash(accountId)) ?: return
        open.database.close()
        open.passphrase.fill(0)
    }

    @Synchronized
    fun closeAll() {
        databases.values.forEach { open ->
            open.database.close()
            open.passphrase.fill(0)
        }
        databases.clear()
    }

    private data class OpenDatabase(
        val database: FinanceDatabase,
        val passphrase: ByteArray
    )

    private class DatabaseKeyStore(context: Context) {
        private val preferences: SharedPreferences = context.getSharedPreferences(
            WRAPPED_KEYS_PREFERENCES,
            Context.MODE_PRIVATE
        )

        @Synchronized
        fun getOrCreate(accountHash: String): ByteArray {
            val preferenceKey = "wrapped_$accountHash"
            val stored = preferences.getString(preferenceKey, null)
            if (stored != null) return unwrap(accountHash, stored)

            val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey(masterKeyAlias(accountHash)))
            val wrapped = cipher.iv + cipher.doFinal(passphrase)
            val encoded = Base64.encodeToString(wrapped, Base64.NO_WRAP)
            check(preferences.edit().putString(preferenceKey, encoded).commit()) {
                "Could not persist the protected local database key."
            }
            return passphrase
        }

        private fun unwrap(accountHash: String, encoded: String): ByteArray {
            val wrapped = try {
                Base64.decode(encoded, Base64.NO_WRAP)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("The protected local database key is invalid.", error)
            }
            require(wrapped.size > GCM_IV_SIZE_BYTES) { "The protected local database key is incomplete." }
            val iv = wrapped.copyOfRange(0, GCM_IV_SIZE_BYTES)
            val encryptedPassphrase = wrapped.copyOfRange(GCM_IV_SIZE_BYTES, wrapped.size)
            return try {
                Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                    init(
                        Cipher.DECRYPT_MODE,
                        getOrCreateMasterKey(masterKeyAlias(accountHash)),
                        GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
                    )
                    doFinal(encryptedPassphrase)
                }
            } catch (error: Exception) {
                throw IllegalStateException("The protected local database key could not be opened.", error)
            }
        }

        private fun getOrCreateMasterKey(alias: String): SecretKey {
            val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            val existing = store.getKey(alias, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generator.generateKey()
        }
    }

    companion object {
        private const val WRAPPED_KEYS_PREFERENCES = "finance_room_key_wrappers"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128
        private const val PASSPHRASE_SIZE_BYTES = 32

        init {
            System.loadLibrary("sqlcipher")
        }

        private fun accountHash(accountId: String): String = MessageDigest
            .getInstance("SHA-256")
            .digest(accountId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        private fun masterKeyAlias(accountHash: String): String = "finance_room_master_$accountHash"
    }
}
