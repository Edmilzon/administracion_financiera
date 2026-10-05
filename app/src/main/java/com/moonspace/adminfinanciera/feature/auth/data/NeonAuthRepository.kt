package com.moonspace.adminfinanciera.feature.auth.data

import android.content.Context
import android.util.Base64
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.network.NeonAccessTokenProvider
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.feature.auth.domain.AuthAccountUpdateResult
import com.moonspace.adminfinanciera.feature.auth.domain.AuthBootstrap
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.auth.domain.MemberAccountProvisionResult
import com.moonspace.adminfinanciera.feature.auth.domain.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

internal class NeonAuthRepository(
    context: Context,
    private val config: NeonApiConfig
) : AuthRepository, NeonAccessTokenProvider {
    private val appContext = context.applicationContext
    private val store = AndroidNeonSessionStore(appContext)
    private val authClient = NeonAuthRestClient(config.authApiBaseUrl())
    private val tokenMutex = Mutex()
    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var cachedAccessTokenExpiresAtSeconds: Long = 0

    override suspend fun restoreSession(): AuthBootstrap = withContext(Dispatchers.IO) {
        if (!config.isAuthConfigured) {
            return@withContext AuthBootstrap(
                user = null,
                isAuthConfigured = false,
                noticeMessage = appContext.getString(R.string.auth_config_missing)
            )
        }

        val storedSession = store.read()
            ?: return@withContext AuthBootstrap(
                user = null,
                isAuthConfigured = true,
                noticeMessage = dataApiNotice()
            )

        try {
            val response = authClient.getSession(storedSession.cookieHeader)
            val refreshed = response.toStoredSession(storedSession, requireIdentity = true)
            if (refreshed == null) {
                clearSession()
                AuthBootstrap(user = null, isAuthConfigured = true, noticeMessage = dataApiNotice())
            } else {
                store.save(refreshed)
                cacheToken(response.accessToken)
                AuthBootstrap(
                    user = refreshed.toAuthUser(),
                    isAuthConfigured = true,
                    noticeMessage = dataApiNotice()
                )
            }
        } catch (error: NeonAuthHttpException) {
            if (error.statusCode == 401 || error.statusCode == 403) {
                clearSession()
                AuthBootstrap(user = null, isAuthConfigured = true, noticeMessage = dataApiNotice())
            } else {
                AuthBootstrap(
                    user = storedSession.toAuthUser(),
                    isAuthConfigured = true,
                    noticeMessage = appContext.getString(R.string.auth_connection_unavailable)
                )
            }
        } catch (_: IOException) {
            AuthBootstrap(
                user = storedSession.toAuthUser(),
                isAuthConfigured = true,
                noticeMessage = appContext.getString(R.string.auth_connection_unavailable)
            )
        }
    }

    override suspend fun createAccount(email: String, password: String): SignInResult =
        authenticate(isCreatingAccount = true) { authClient.signUp(normalizeEmail(email), password) }

    override suspend fun createMemberAccount(
        email: String,
        password: String
    ): MemberAccountProvisionResult = withContext(Dispatchers.IO) {
        if (!config.isAuthConfigured) {
            return@withContext MemberAccountProvisionResult.Failure(
                appContext.getString(R.string.auth_config_missing)
            )
        }

        val normalizedEmail = normalizeEmail(email)
        try {
            val response = authClient.signUp(normalizedEmail, password)
            response.toManagedAccount(isNewAccount = true)
        } catch (error: NeonAuthHttpException) {
            if (!error.isExistingAccount()) {
                return@withContext MemberAccountProvisionResult.Failure(errorMessage(error))
            }

            // A retry can finish household linking after sign-up succeeded but the Data API request failed.
            // Keep the returned cookie local to this request; never replace the administrator's session.
            try {
                authClient.signIn(normalizedEmail, password).toManagedAccount(isNewAccount = false)
            } catch (signInError: NeonAuthHttpException) {
                MemberAccountProvisionResult.Failure(errorMessage(signInError))
            } catch (_: IOException) {
                MemberAccountProvisionResult.Failure(appContext.getString(R.string.auth_connection_unavailable))
            } catch (_: Exception) {
                MemberAccountProvisionResult.Failure(appContext.getString(R.string.auth_request_failed))
            }
        } catch (_: IOException) {
            MemberAccountProvisionResult.Failure(appContext.getString(R.string.auth_connection_unavailable))
        } catch (_: Exception) {
            MemberAccountProvisionResult.Failure(appContext.getString(R.string.auth_request_failed))
        }
    }

    override suspend fun signIn(email: String, password: String): SignInResult =
        authenticate(isCreatingAccount = false) {
            authClient.signIn(normalizeEmail(email), password)
        }

    override suspend fun updateProfileName(name: String): AuthAccountUpdateResult = withContext(Dispatchers.IO) {
        val current = store.read()
            ?: return@withContext AuthAccountUpdateResult.Failure(
                appContext.getString(R.string.auth_session_unavailable)
            )
        if (!config.isAuthConfigured) {
            return@withContext AuthAccountUpdateResult.Failure(
                appContext.getString(R.string.auth_config_missing)
            )
        }
        try {
            val response = authClient.updateProfileName(name, current.cookieHeader)
            if (response.userId != null && response.userId != current.userId) {
                return@withContext AuthAccountUpdateResult.Failure(
                    appContext.getString(R.string.auth_request_failed)
                )
            }
            val updated = response.toStoredSession(current, requireIdentity = false)
                ?.copy(name = response.name?.takeIf(String::isNotBlank) ?: name)
                ?: current.copy(name = name)
            store.save(updated)
            cacheToken(response.accessToken ?: cachedAccessToken)
            AuthAccountUpdateResult.Success(
                user = updated.toAuthUser(),
                message = appContext.getString(R.string.users_profile_saved)
            )
        } catch (error: NeonAuthHttpException) {
            AuthAccountUpdateResult.Failure(errorMessage(error))
        } catch (_: IOException) {
            AuthAccountUpdateResult.Failure(appContext.getString(R.string.auth_connection_unavailable))
        } catch (_: Exception) {
            AuthAccountUpdateResult.Failure(appContext.getString(R.string.auth_request_failed))
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): AuthAccountUpdateResult = withContext(Dispatchers.IO) {
        val current = store.read()
            ?: return@withContext AuthAccountUpdateResult.Failure(
                appContext.getString(R.string.auth_session_unavailable)
            )
        if (!config.isAuthConfigured) {
            return@withContext AuthAccountUpdateResult.Failure(
                appContext.getString(R.string.auth_config_missing)
            )
        }
        try {
            val response = authClient.changePassword(currentPassword, newPassword, current.cookieHeader)
            if (response.userId != null && response.userId != current.userId) {
                return@withContext AuthAccountUpdateResult.Failure(
                    appContext.getString(R.string.auth_request_failed)
                )
            }
            val refreshed = response.toStoredSession(current, requireIdentity = false) ?: current
            store.save(refreshed)
            cacheToken(response.accessToken ?: cachedAccessToken)
            AuthAccountUpdateResult.Success(message = appContext.getString(R.string.users_password_changed))
        } catch (error: NeonAuthHttpException) {
            AuthAccountUpdateResult.Failure(passwordErrorMessage(error))
        } catch (_: IOException) {
            AuthAccountUpdateResult.Failure(appContext.getString(R.string.auth_connection_unavailable))
        } catch (_: Exception) {
            AuthAccountUpdateResult.Failure(appContext.getString(R.string.auth_request_failed))
        }
    }

    private suspend fun authenticate(
        isCreatingAccount: Boolean,
        request: suspend () -> NeonAuthResponse
    ): SignInResult =
        withContext(Dispatchers.IO) {
            if (!config.isAuthConfigured) {
                return@withContext SignInResult.Failure(appContext.getString(R.string.auth_config_missing))
            }
            try {
                val response = request()
                if (response.emailVerificationRequired || (isCreatingAccount && response.userId != null && response.sessionCookie == null)) {
                    return@withContext SignInResult.NeedsEmailVerification(
                        appContext.getString(R.string.auth_email_verification_required)
                    )
                }
                val userId = response.userId
                val responseEmail = response.email
                val cookie = response.sessionCookie
                if (userId.isNullOrBlank() || responseEmail.isNullOrBlank() || cookie.isNullOrBlank()) {
                    return@withContext SignInResult.Failure(
                        appContext.getString(R.string.auth_session_unavailable)
                    )
                }

                val session = NeonStoredSession(
                    userId = userId,
                    email = responseEmail,
                    name = response.name?.takeIf(String::isNotBlank)
                        ?: responseEmail.substringBefore('@'),
                    cookieHeader = cookie
                )
                store.save(session)
                cacheToken(response.accessToken)
                SignInResult.Success(session.toAuthUser())
            } catch (error: NeonAuthHttpException) {
                SignInResult.Failure(errorMessage(error))
            } catch (_: IOException) {
                SignInResult.Failure(appContext.getString(R.string.auth_connection_unavailable))
            } catch (_: IllegalStateException) {
                SignInResult.Failure(appContext.getString(R.string.auth_config_missing))
            } catch (_: Exception) {
                SignInResult.Failure(appContext.getString(R.string.auth_request_failed))
            }
        }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        val storedSession = store.read()
        try {
            if (storedSession != null && config.isAuthConfigured) {
                authClient.signOut(storedSession.cookieHeader)
            }
        } catch (_: Exception) {
            // Clear the device session even if the phone is offline; the server cookie expires separately.
        } finally {
            clearSession()
        }
    }

    override suspend fun getAccessToken(): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis() / 1_000
        cachedAccessToken?.takeIf { cachedAccessTokenExpiresAtSeconds > now + TOKEN_EXPIRY_SAFETY_SECONDS }
            ?.let { return@withLock it }

        val storedSession = store.read() ?: return@withLock null
        if (!config.isAuthConfigured) return@withLock null
        try {
            val response = authClient.getSession(storedSession.cookieHeader)
            val refreshed = response.toStoredSession(storedSession, requireIdentity = true) ?: run {
                clearSession()
                return@withLock null
            }
            store.save(refreshed)
            cacheToken(response.accessToken)
            cachedAccessToken?.takeIf { cachedAccessTokenExpiresAtSeconds > now }
        } catch (error: NeonAuthHttpException) {
            if (error.statusCode == 401 || error.statusCode == 403) clearSession()
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun NeonAuthResponse.toStoredSession(
        previous: NeonStoredSession,
        requireIdentity: Boolean
    ): NeonStoredSession? {
        if (requireIdentity && (userId == null || email == null)) return null
        val resolvedUserId = userId ?: previous.userId
        val resolvedEmail = email ?: previous.email
        val resolvedName = name?.takeIf(String::isNotBlank) ?: previous.name
        val cookie = sessionCookie ?: previous.cookieHeader
        if (cookie.isBlank()) return null
        return NeonStoredSession(
            userId = resolvedUserId,
            email = resolvedEmail,
            name = resolvedName,
            cookieHeader = cookie
        )
    }

    private fun NeonStoredSession.toAuthUser() = AuthUser(id = userId, email = email, name = name)

    private fun cacheToken(token: String?) {
        cachedAccessToken = token
        cachedAccessTokenExpiresAtSeconds = token?.let(::jwtExpiresAtSeconds) ?: 0
    }

    private fun clearSession() {
        store.clear()
        cachedAccessToken = null
        cachedAccessTokenExpiresAtSeconds = 0
    }

    private fun jwtExpiresAtSeconds(token: String): Long = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching 0L
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded, Charsets.UTF_8)).optLong("exp")
    }.getOrDefault(0L)

    private fun errorMessage(error: NeonAuthHttpException): String = when {
        error.statusCode == 401 || error.statusCode == 403 ->
            appContext.getString(R.string.login_invalid_credentials)
        error.statusCode == 409 || error.errorCode?.contains("already", ignoreCase = true) == true ->
            appContext.getString(R.string.auth_account_exists)
        else -> appContext.getString(R.string.auth_request_failed)
    }

    private fun passwordErrorMessage(error: NeonAuthHttpException): String = when {
        error.statusCode == 401 || error.statusCode == 403 ||
            error.errorCode?.contains("password", ignoreCase = true) == true ->
            appContext.getString(R.string.users_current_password_invalid)
        else -> appContext.getString(R.string.auth_request_failed)
    }

    private fun NeonAuthResponse.toManagedAccount(isNewAccount: Boolean): MemberAccountProvisionResult {
        val userId = userId
        val email = email
        if (userId.isNullOrBlank() || email.isNullOrBlank()) {
            return MemberAccountProvisionResult.Failure(
                appContext.getString(R.string.auth_session_unavailable)
            )
        }
        // Deliberately do not call store.save() or cacheToken(): the current admin session stays active.
        return MemberAccountProvisionResult.Ready(
            user = AuthUser(
                id = userId,
                email = email,
                name = name?.takeIf(String::isNotBlank) ?: email.substringBefore('@')
            ),
            isNewAccount = isNewAccount,
            emailVerificationRequired = emailVerificationRequired
        )
    }

    private fun NeonAuthHttpException.isExistingAccount(): Boolean =
        statusCode == 409 || errorCode?.contains("already", ignoreCase = true) == true ||
            errorCode?.contains("exist", ignoreCase = true) == true

    private fun dataApiNotice(): String? = if (config.isDataApiConfigured) null else {
        appContext.getString(R.string.auth_data_api_missing)
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private companion object {
        const val TOKEN_EXPIRY_SAFETY_SECONDS = 30
    }
}
