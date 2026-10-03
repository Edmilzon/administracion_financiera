package com.moonspace.adminfinanciera.feature.auth.data

import android.content.Context
import android.util.Base64
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthBootstrap
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.auth.domain.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class LocalAuthRepository(context: Context) : AuthRepository {
    private val appContext = context.applicationContext
    private val store = AndroidLocalAuthStore(appContext)

    override suspend fun restoreSession(): AuthBootstrap = withContext(Dispatchers.IO) {
        val account = store.read()
        AuthBootstrap(
            user = account?.takeIf { it.isSignedIn }?.toAuthUser(),
            hasLocalAccount = account != null
        )
    }

    override suspend fun createLocalAccount(email: String, password: String): SignInResult =
        withContext(Dispatchers.IO) {
            if (store.read() != null) {
                return@withContext SignInResult.Failure(appContext.getString(R.string.local_account_exists))
            }

            try {
                val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
                val normalizedEmail = email.trim().lowercase(Locale.ROOT)
                val account = LocalAccountRecord(
                    userId = UUID.randomUUID().toString(),
                    email = normalizedEmail,
                    salt = salt,
                    passwordHash = derivePasswordHash(password, salt),
                    isSignedIn = true
                )
                store.save(account)
                SignInResult.Success(account.toAuthUser())
            } catch (_: Exception) {
                SignInResult.Failure(appContext.getString(R.string.local_account_save_error))
            }
        }

    override suspend fun signIn(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        val account = store.read()
            ?: return@withContext SignInResult.Failure(appContext.getString(R.string.local_account_missing))

        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val submittedHash = try {
            derivePasswordHash(password, account.salt)
        } catch (_: Exception) {
            return@withContext SignInResult.Failure(appContext.getString(R.string.local_login_error))
        }

        if (normalizedEmail != account.email || !MessageDigest.isEqual(submittedHash, account.passwordHash)) {
            return@withContext SignInResult.Failure(appContext.getString(R.string.login_invalid_credentials))
        }

        try {
            val signedIn = account.copy(isSignedIn = true)
            store.save(signedIn)
            SignInResult.Success(signedIn.toAuthUser())
        } catch (_: Exception) {
            SignInResult.Failure(appContext.getString(R.string.local_login_error))
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val account = store.read() ?: return@withContext
        store.save(account.copy(isSignedIn = false))
    }

    private fun LocalAccountRecord.toAuthUser() = AuthUser(
        id = userId,
        email = email,
        isLocalOnly = true
    )

    private fun derivePasswordHash(password: String, salt: ByteArray): ByteArray {
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    private companion object {
        const val SALT_LENGTH_BYTES = 16
        const val PBKDF2_ITERATIONS = 210_000
        const val HASH_LENGTH_BITS = 256
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1"
        val secureRandom = SecureRandom()
    }
}
