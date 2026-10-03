package com.moonspace.adminfinanciera.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.auth.domain.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isCheckingSession: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasLocalAccount: Boolean = false,
    val user: AuthUser? = null,
    val errorMessage: String? = null
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
                hasLocalAccount = bootstrap.hasLocalAccount,
                user = bootstrap.user
            )
        }
    }

    fun createLocalAccount(email: String, password: String) {
        if (_uiState.value.isSubmitting || _uiState.value.hasLocalAccount) return
        submit { repository.createLocalAccount(email, password) }
    }

    fun signIn(email: String, password: String) {
        if (_uiState.value.isSubmitting || !_uiState.value.hasLocalAccount) return
        submit { repository.signIn(email, password) }
    }

    private fun submit(action: suspend () -> SignInResult) {
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = action()) {
                is SignInResult.Success -> _uiState.value = AuthUiState(
                    isCheckingSession = false,
                    isSubmitting = false,
                    hasLocalAccount = true,
                    user = result.user
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
        if (_uiState.value.isSubmitting) return
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
