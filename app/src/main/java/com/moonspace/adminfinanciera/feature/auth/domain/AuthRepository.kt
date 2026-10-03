package com.moonspace.adminfinanciera.feature.auth.domain

data class AuthUser(
    val id: String,
    val email: String
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

interface AuthRepository {
    suspend fun restoreSession(): AuthBootstrap
    suspend fun createAccount(email: String, password: String): SignInResult
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun signOut()
}
