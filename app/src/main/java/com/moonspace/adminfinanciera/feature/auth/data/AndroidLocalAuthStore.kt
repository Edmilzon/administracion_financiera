package com.moonspace.adminfinanciera.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class LocalAccountRecord(
    val userId: String,
    val email: String,
    val salt: ByteArray,
    val passwordHash: ByteArray,
    val isSignedIn: Boolean
)

/** Encrypts the local account and session with a key held by Android Keystore. */
internal class AndroidLocalAuthStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): LocalAccountRecord? {
        val encoded = preferences.getString(ACCOUNT_KEY, null) ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_LENGTH_BYTES)
            val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            LocalAccountRecord(
                userId = json.getString("userId"),
                email = json.getString("email"),
                salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP),
                passwordHash = Base64.decode(json.getString("passwordHash"), Base64.NO_WRAP),
                isSignedIn = json.optBoolean("isSignedIn")
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(account: LocalAccountRecord) {
        val payload = JSONObject()
            .put("userId", account.userId)
            .put("email", account.email)
            .put("salt", Base64.encodeToString(account.salt, Base64.NO_WRAP))
            .put("passwordHash", Base64.encodeToString(account.passwordHash, Base64.NO_WRAP))
            .put("isSignedIn", account.isSignedIn)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(payload)
        val saved = preferences.edit()
            .putString(ACCOUNT_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to persist the encrypted local account." }
    }

    fun clear() {
        preferences.edit().remove(ACCOUNT_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "finance_local_auth"
        const val ACCOUNT_KEY = "encrypted_local_account"
        const val KEY_ALIAS = "finance_local_auth_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
