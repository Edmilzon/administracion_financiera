package com.moonspace.adminfinanciera.feature.auth.domain

data class AuthUser(
    val id: String,
    val email: String,
    val isLocalOnly: Boolean = true
)

data class AuthBootstrap(
    val user: AuthUser?,
    val hasLocalAccount: Boolean
)

sealed interface SignInResult {
    data class Success(val user: AuthUser) : SignInResult
    data class Failure(val message: String) : SignInResult
}

interface AuthRepository {
    suspend fun restoreSession(): AuthBootstrap
    suspend fun createLocalAccount(email: String, password: String): SignInResult
    suspend fun signIn(email: String, password: String): SignInResult
    suspend fun signOut()
}
