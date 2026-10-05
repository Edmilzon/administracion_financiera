package com.moonspace.adminfinanciera.feature.auth.domain

data class AuthUser(
    val id: String,
    val email: String,
    val name: String = ""
)

data class AuthBootstrap(
    val user: AuthUser?,
    val isAuthConfigured: Boolean,
    val noticeMessage: String? = null
)

sealed interface SignInResult {
    data class Success(val user: AuthUser) : SignInResult
    data class NeedsEmailVerification(val message: String) : SignInResult
    data class Failure(val message: String) : SignInResult
}

sealed interface MemberAccountProvisionResult {
    data class Ready(
        val user: AuthUser,
        val isNewAccount: Boolean,
        val emailVerificationRequired: Boolean
    ) : MemberAccountProvisionResult

    data class Failure(val message: String) : MemberAccountProvisionResult
}

sealed interface AuthAccountUpdateResult {
    data class Success(val user: AuthUser? = null, val message: String) : AuthAccountUpdateResult
    data class Failure(val message: String) : AuthAccountUpdateResult
}

enum class AuthAccountMutation {
    ProfileName,
    Password
}

interface AuthRepository {
    suspend fun restoreSession(): AuthBootstrap
    suspend fun createAccount(email: String, password: String): SignInResult
    suspend fun createMemberAccount(email: String, password: String): MemberAccountProvisionResult
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun updateProfileName(name: String): AuthAccountUpdateResult
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthAccountUpdateResult
    suspend fun signOut()
}
