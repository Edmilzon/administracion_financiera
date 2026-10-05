package com.moonspace.adminfinanciera.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.auth.domain.AuthAccountUpdateResult
import com.moonspace.adminfinanciera.feature.auth.domain.AuthAccountMutation
import com.moonspace.adminfinanciera.feature.auth.domain.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isCheckingSession: Boolean = true,
    val isSubmitting: Boolean = false,
    val isAuthConfigured: Boolean = false,
    val user: AuthUser? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val isUpdatingAccount: Boolean = false,
    val accountUpdateErrorMessage: String? = null,
    val accountUpdateNoticeMessage: String? = null,
    val accountUpdateVersion: Int = 0,
    val pendingAccountMutation: AuthAccountMutation? = null,
    val lastAccountMutation: AuthAccountMutation? = null
)

class AuthViewModel(
    private val repository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun restoreSession() {
        if (!_uiState.value.isCheckingSession) return
        viewModelScope.launch {
            val bootstrap = repository.restoreSession()
            _uiState.value = AuthUiState(
                isCheckingSession = false,
                isAuthConfigured = bootstrap.isAuthConfigured,
                user = bootstrap.user,
                noticeMessage = bootstrap.noticeMessage
            )
        }
    }

    fun createAccount(email: String, password: String) {
        if (_uiState.value.isSubmitting || !_uiState.value.isAuthConfigured) return
        submit { repository.createAccount(email, password) }
    }

    fun signIn(email: String, password: String) {
        if (_uiState.value.isSubmitting || !_uiState.value.isAuthConfigured) return
        submit { repository.signIn(email, password) }
    }

    fun updateProfileName(name: String) {
        if (_uiState.value.isUpdatingAccount || _uiState.value.isSubmitting) return
        updateAccount(AuthAccountMutation.ProfileName) { repository.updateProfileName(name) }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (_uiState.value.isUpdatingAccount || _uiState.value.isSubmitting) return
        updateAccount(AuthAccountMutation.Password) {
            repository.changePassword(currentPassword, newPassword)
        }
    }

    fun clearAccountUpdateMessages() {
        _uiState.value = _uiState.value.copy(
            accountUpdateErrorMessage = null,
            accountUpdateNoticeMessage = null
        )
    }

    private fun updateAccount(
        mutation: AuthAccountMutation,
        action: suspend () -> AuthAccountUpdateResult
    ) {
        _uiState.value = _uiState.value.copy(
            isUpdatingAccount = true,
            pendingAccountMutation = mutation,
            accountUpdateErrorMessage = null,
            accountUpdateNoticeMessage = null
        )
        viewModelScope.launch {
            when (val result = action()) {
                is AuthAccountUpdateResult.Success -> _uiState.value = _uiState.value.copy(
                    isUpdatingAccount = false,
                    pendingAccountMutation = null,
                    user = result.user ?: _uiState.value.user,
                    accountUpdateErrorMessage = null,
                    accountUpdateNoticeMessage = result.message,
                    accountUpdateVersion = _uiState.value.accountUpdateVersion + 1,
                    lastAccountMutation = mutation
                )

                is AuthAccountUpdateResult.Failure -> _uiState.value = _uiState.value.copy(
                    isUpdatingAccount = false,
                    pendingAccountMutation = null,
                    accountUpdateErrorMessage = result.message,
                    accountUpdateNoticeMessage = null
                )
            }
        }
    }

    private fun submit(action: suspend () -> SignInResult) {
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = action()) {
                is SignInResult.Success -> _uiState.value = AuthUiState(
                    isCheckingSession = false,
                    isSubmitting = false,
                    isAuthConfigured = true,
                    user = result.user
                )

                is SignInResult.NeedsEmailVerification -> _uiState.value = _uiState.value.copy(
                    isCheckingSession = false,
                    isSubmitting = false,
                    noticeMessage = result.message,
                    errorMessage = null
                )

                is SignInResult.Failure -> _uiState.value = _uiState.value.copy(
                    isCheckingSession = false,
                    isSubmitting = false,
                    errorMessage = result.message
                )
            }
        }
    }

    fun signOut() {
        if (_uiState.value.isSubmitting || _uiState.value.isUpdatingAccount) return
        _uiState.value = _uiState.value.copy(isSubmitting = true)
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = _uiState.value.copy(
                isCheckingSession = false,
                isSubmitting = false,
                user = null,
                errorMessage = null
            )
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(repository) as T
        }
    }
}
