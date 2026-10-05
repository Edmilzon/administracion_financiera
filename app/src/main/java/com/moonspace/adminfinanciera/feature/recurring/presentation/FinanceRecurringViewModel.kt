package com.moonspace.adminfinanciera.feature.recurring.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.recurring.domain.FinanceRecurringRule
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleDraft
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleError
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleException
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class FinanceRecurringUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasHousehold: Boolean = false,
    val householdId: String? = null,
    val role: HouseholdRole? = null,
    val rules: List<FinanceRecurringRule> = emptyList(),
    val categories: List<FinanceCategory> = emptyList(),
    val memberEmails: Map<String, String> = emptyMap(),
    val pendingSyncCount: Int = 0,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val noticeMessage: String? = null,
    val ruleSavedVersion: Int = 0,
    val occurrenceRegisteredVersion: Int = 0
)

class FinanceRecurringViewModel(
    context: Context,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val financialRepository: FinancialRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(FinanceRecurringUiState())
    val uiState: StateFlow<FinanceRecurringUiState> = _uiState.asStateFlow()
    private var currentUser: AuthUser? = null
    private var loadJob: Job? = null
    private var rulesJob: Job? = null
    private var categoriesJob: Job? = null
    private var pendingJob: Job? = null

    fun load(user: AuthUser) {
        if (currentUser?.id != user.id) {
            stopObserving()
            currentUser = user
            _uiState.value = FinanceRecurringUiState()
        }
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        loadJob = viewModelScope.launch {
            try {
                val snapshot = householdMembersRepository.load(user)
                val householdId = snapshot.householdId
                val role = snapshot.currentUserRole
                if (householdId == null || role == null) {
                    stopObserving()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasHousehold = false,
                        householdId = null,
                        role = null,
                        rules = emptyList(),
                        categories = emptyList(),
                        memberEmails = emptyMap()
                    )
                    return@launch
                }

                financialRepository.prepareHousehold(user.id, householdId, role.apiValue)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = true,
                    householdId = householdId,
                    role = role,
                    memberEmails = snapshot.members.associate { it.userId to it.email },
                    errorMessage = null
                )
                startObserving(user, householdId, role)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = false,
                    errorMessage = userMessage(error)
                )
            }
        }
    }

    fun saveRule(draft: RecurringRuleDraft) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            recurringRuleRepository.saveRule(user.id, user.id, householdId, draft)
            _uiState.value = _uiState.value.copy(ruleSavedVersion = _uiState.value.ruleSavedVersion + 1)
        }
    }

    fun setRuleActive(ruleId: String, active: Boolean) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            recurringRuleRepository.setRuleActive(user.id, user.id, householdId, ruleId, active)
            _uiState.value = _uiState.value.copy(ruleSavedVersion = _uiState.value.ruleSavedVersion + 1)
        }
    }

    fun confirmOccurrence(ruleId: String) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            recurringRuleRepository.confirmOccurrence(user.id, user.id, householdId, ruleId)
            _uiState.value = _uiState.value.copy(
                occurrenceRegisteredVersion = _uiState.value.occurrenceRegisteredVersion + 1
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(actionErrorMessage = null, noticeMessage = null)
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        stopObserving()
        currentUser = null
        _uiState.value = FinanceRecurringUiState(isLoading = false)
    }

    private fun startObserving(user: AuthUser, householdId: String, role: HouseholdRole) {
        stopObserving()
        rulesJob = viewModelScope.launch {
            recurringRuleRepository.observeRules(
                accountId = user.id,
                householdId = householdId,
                userId = user.id,
                canReadWholeHousehold = role == HouseholdRole.Admin
            ).collect { rules -> _uiState.value = _uiState.value.copy(rules = rules) }
        }
        categoriesJob = viewModelScope.launch {
            financialRepository.observeCategories(user.id, householdId, includeInactive = true)
                .collect { categories -> _uiState.value = _uiState.value.copy(categories = categories) }
        }
        pendingJob = viewModelScope.launch {
            financialRepository.observePendingCount(user.id)
                .collect { count -> _uiState.value = _uiState.value.copy(pendingSyncCount = count) }
        }
    }

    private fun stopObserving() {
        rulesJob?.cancel()
        categoriesJob?.cancel()
        pendingJob?.cancel()
        rulesJob = null
        categoriesJob = null
        pendingJob = null
    }

    private fun submit(action: suspend () -> Unit) {
        if (_uiState.value.isSubmitting) return
        _uiState.value = _uiState.value.copy(isSubmitting = true, actionErrorMessage = null, noticeMessage = null)
        viewModelScope.launch {
            try {
                action()
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    actionErrorMessage = null,
                    noticeMessage = appContext.getString(R.string.recurring_saved_locally)
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    actionErrorMessage = userMessage(error),
                    noticeMessage = null
                )
            }
        }
    }

    private fun userMessage(error: Exception): String = when (error) {
        is RecurringRuleException -> appContext.getString(
            when (error.error) {
                RecurringRuleError.InvalidAmount -> R.string.recurring_invalid_amount
                RecurringRuleError.InvalidDate -> R.string.recurring_invalid_date
                RecurringRuleError.InvalidInterval -> R.string.recurring_invalid_interval
                RecurringRuleError.CategoryUnavailable -> R.string.recurring_category_unavailable
                RecurringRuleError.RuleUnavailable -> R.string.recurring_rule_unavailable
                RecurringRuleError.OccurrenceNotDue -> R.string.recurring_occurrence_not_due
                RecurringRuleError.PermissionDenied -> R.string.recurring_permission_denied
                RecurringRuleError.HouseholdUnavailable -> R.string.transactions_household_unavailable
            }
        )
        is UserManagementException -> error.message ?: appContext.getString(R.string.recurring_load_failed)
        else -> appContext.getString(R.string.recurring_operation_failed)
    }

    override fun onCleared() {
        loadJob?.cancel()
        stopObserving()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val householdMembersRepository: HouseholdMembersRepository,
        private val financialRepository: FinancialRepository,
        private val recurringRuleRepository: RecurringRuleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceRecurringViewModel::class.java))
            return FinanceRecurringViewModel(
                context,
                householdMembersRepository,
                financialRepository,
                recurringRuleRepository
            ) as T
        }
    }
}
