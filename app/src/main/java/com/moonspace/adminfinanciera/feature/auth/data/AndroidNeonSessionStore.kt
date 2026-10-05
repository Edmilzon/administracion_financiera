package com.moonspace.adminfinanciera.feature.auth.data

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class NeonStoredSession(
    val userId: String,
    val email: String,
    val name: String,
    val cookieHeader: String
)

/** Stores only the revocable Auth session cookie and user identity, encrypted with Android Keystore. */
internal class AndroidNeonSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        removeRetiredLocalCredentials()
    }

    fun read(): NeonStoredSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_LENGTH_BYTES)
            val iv = encrypted.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = encrypted.copyOfRange(IV_LENGTH_BYTES, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val json = JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            NeonStoredSession(
                userId = json.getString("userId"),
                email = json.getString("email"),
                name = json.optString("name").takeIf(String::isNotBlank)
                    ?: json.getString("email").substringBefore('@'),
                cookieHeader = json.getString("cookieHeader")
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Suppress("UseKtx")
    fun save(session: NeonStoredSession) {
        val payload = JSONObject()
            .put("userId", session.userId)
            .put("email", session.email)
            .put("name", session.name)
            .put("cookieHeader", session.cookieHeader)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(payload)
        check(
            preferences.edit()
                .putString(SESSION_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .commit()
        ) { "Unable to persist the encrypted Neon session." }
    }

    @Suppress("UseKtx")
    fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "Unable to clear the encrypted Neon session."
        }
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

    private fun removeRetiredLocalCredentials() {
        appContext.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    private companion object {
        const val PREFERENCES_NAME = "finance_remote_auth"
        const val SESSION_KEY = "encrypted_neon_session"
        const val KEY_ALIAS = "finance_neon_session_key"
        const val LEGACY_PREFERENCES_NAME = "finance_local_auth"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
