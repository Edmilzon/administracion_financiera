package com.moonspace.adminfinanciera.feature.users.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdSnapshot
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class HouseholdMembersUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val snapshot: HouseholdSnapshot? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
)

class HouseholdMembersViewModel(
    context: Context,
    private val repository: HouseholdMembersRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(HouseholdMembersUiState())
    val uiState: StateFlow<HouseholdMembersUiState> = _uiState.asStateFlow()
    private var loadedUserId: String? = null
    private var loadJob: Job? = null
    private var lastLoadAttemptAt = 0L

    fun load(currentUser: AuthUser, forceRefresh: Boolean = false) {
        val userChanged = loadedUserId != currentUser.id
        val now = SystemClock.elapsedRealtime()
        if (!userChanged && !forceRefresh &&
            (loadJob?.isActive == true || now - lastLoadAttemptAt < SCREEN_CACHE_TTL_MILLIS)
        ) return
        if (_uiState.value.isSubmitting) return
        if (userChanged) {
            loadJob?.cancel()
            lastLoadAttemptAt = 0L
        }
        loadedUserId = currentUser.id
        lastLoadAttemptAt = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            snapshot = if (userChanged) null else _uiState.value.snapshot,
            errorMessage = null,
            noticeMessage = null
        )
        loadJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    snapshot = repository.load(currentUser),
                    errorMessage = null
                )
                lastLoadAttemptAt = SystemClock.elapsedRealtime()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = userMessage(error)
                )
            }
        }
    }

    fun createHousehold(currentUser: AuthUser, name: String) {
        submit(currentUser) {
            repository.createHousehold(currentUser, name)
            appContext.getString(R.string.users_space_created)
        }
    }

    fun createMember(currentUser: AuthUser, email: String, password: String, role: HouseholdRole) {
        submit(currentUser) {
            val result = repository.createMember(currentUser, email, password, role)
            when {
                result.emailVerificationRequired -> appContext.getString(R.string.users_member_created_verify)
                result.isNewAccount -> appContext.getString(R.string.users_member_created)
                else -> appContext.getString(R.string.users_existing_account_added)
            }
        }
    }

    fun updateRole(currentUser: AuthUser, targetUserId: String, role: HouseholdRole) {
        submit(currentUser) {
            repository.updateRole(currentUser, targetUserId, role)
            appContext.getString(R.string.users_role_updated)
        }
    }

    fun removeMember(currentUser: AuthUser, targetUserId: String) {
        submit(currentUser) {
            repository.removeMember(currentUser, targetUserId)
            appContext.getString(R.string.users_member_removed)
        }
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        loadedUserId = null
        lastLoadAttemptAt = 0L
        _uiState.value = HouseholdMembersUiState()
    }

    private fun submit(
        currentUser: AuthUser,
        action: suspend () -> String
    ) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null, noticeMessage = null)
        viewModelScope.launch {
            try {
                val notice = action()
                val snapshot = repository.load(currentUser)
                lastLoadAttemptAt = SystemClock.elapsedRealtime()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSubmitting = false,
                    snapshot = snapshot,
                    errorMessage = null,
                    noticeMessage = notice
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSubmitting = false,
                    errorMessage = userMessage(error),
                    noticeMessage = null
                )
            }
        }
    }

    private fun userMessage(error: Exception): String = when (error) {
        is UserManagementException -> error.message ?: appContext.getString(R.string.users_request_failed)
        else -> appContext.getString(R.string.users_request_failed)
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val repository: HouseholdMembersRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HouseholdMembersViewModel::class.java))
            return HouseholdMembersViewModel(context, repository) as T
        }
    }
}

private const val SCREEN_CACHE_TTL_MILLIS = 60_000L
