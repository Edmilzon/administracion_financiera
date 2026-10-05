package com.moonspace.adminfinanciera.feature.budgets.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDataError
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDataException
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDraft
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetRepository
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetUsage
import com.moonspace.adminfinanciera.feature.budgets.domain.FinanceBudget
import com.moonspace.adminfinanciera.feature.budgets.domain.calculateBudgetUsage
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class FinanceBudgetsUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasHousehold: Boolean = false,
    val householdId: String? = null,
    val role: HouseholdRole? = null,
    val selectedMonthStart: String = currentMonthStart(),
    val budgets: List<FinanceBudget> = emptyList(),
    val categories: List<FinanceCategory> = emptyList(),
    val transactions: List<FinanceTransaction> = emptyList(),
    val memberEmails: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val noticeMessage: String? = null,
    val budgetSavedVersion: Int = 0,
    val budgetDeletedVersion: Int = 0
) {
    val budgetUsage: List<BudgetUsage>
        get() = calculateBudgetUsage(budgets, transactions, selectedMonthStart)
}

class FinanceBudgetsViewModel(
    context: Context,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val financialRepository: FinancialRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(FinanceBudgetsUiState())
    val uiState: StateFlow<FinanceBudgetsUiState> = _uiState.asStateFlow()
    private var currentUser: AuthUser? = null
    private var loadJob: Job? = null
    private var categoryJob: Job? = null
    private var transactionJob: Job? = null
    private var budgetJob: Job? = null
    private var lastLoadAttemptAt = 0L

    fun load(user: AuthUser, forceRefresh: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (currentUser?.id == user.id && !forceRefresh &&
            (loadJob?.isActive == true || now - lastLoadAttemptAt < SCREEN_CACHE_TTL_MILLIS)
        ) return
        if (currentUser?.id != user.id) {
            stopObserving()
            currentUser = user
            _uiState.value = FinanceBudgetsUiState()
            lastLoadAttemptAt = 0L
        }
        loadJob?.cancel()
        lastLoadAttemptAt = SystemClock.elapsedRealtime()
        val hasCachedHousehold = _uiState.value.hasHousehold
        _uiState.value = _uiState.value.copy(isLoading = !hasCachedHousehold, errorMessage = null)
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
                        budgets = emptyList(),
                        categories = emptyList(),
                        transactions = emptyList(),
                        memberEmails = emptyMap()
                    )
                    return@launch
                }

                financialRepository.prepareHousehold(user.id, householdId, role.apiValue)
                val previousMonth = _uiState.value.selectedMonthStart
                val memberEmails = snapshot.members.associate { it.userId to it.email }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = true,
                    householdId = householdId,
                    role = role,
                    selectedMonthStart = previousMonth,
                    memberEmails = memberEmails,
                    errorMessage = null
                )
                startObserving(user, householdId, role)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val canKeepCachedData = _uiState.value.hasHousehold
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = canKeepCachedData,
                    errorMessage = if (canKeepCachedData) null else userMessage(error)
                )
            }
        }
    }

    fun saveBudget(draft: BudgetDraft) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            budgetRepository.saveBudget(user.id, user.id, householdId, draft)
            _uiState.value = _uiState.value.copy(budgetSavedVersion = _uiState.value.budgetSavedVersion + 1)
        }
    }

    fun deleteBudget(budgetId: String) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            budgetRepository.deleteOwnBudget(user.id, user.id, householdId, budgetId)
            _uiState.value = _uiState.value.copy(budgetDeletedVersion = _uiState.value.budgetDeletedVersion + 1)
        }
    }

    fun moveMonthBy(months: Int) {
        if (months == 0) return
        val nextMonth = shiftMonth(_uiState.value.selectedMonthStart, months) ?: return
        _uiState.value = _uiState.value.copy(selectedMonthStart = nextMonth)
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        val role = _uiState.value.role ?: return
        budgetJob?.cancel()
        budgetJob = observeBudgets(user, householdId, role, nextMonth)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(actionErrorMessage = null, noticeMessage = null)
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        stopObserving()
        currentUser = null
        lastLoadAttemptAt = 0L
        _uiState.value = FinanceBudgetsUiState(isLoading = false)
    }

    private fun startObserving(user: AuthUser, householdId: String, role: HouseholdRole) {
        stopObserving()
        categoryJob = viewModelScope.launch {
            financialRepository.observeCategories(
                accountId = user.id,
                householdId = householdId,
                includeInactive = true
            ).collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
        transactionJob = viewModelScope.launch {
            financialRepository.observeTransactions(
                accountId = user.id,
                householdId = householdId,
                userId = user.id,
                canReadWholeHousehold = role == HouseholdRole.Admin
            ).collect { transactions ->
                _uiState.value = _uiState.value.copy(transactions = transactions)
            }
        }
        budgetJob = observeBudgets(user, householdId, role, _uiState.value.selectedMonthStart)
    }

    private fun observeBudgets(
        user: AuthUser,
        householdId: String,
        role: HouseholdRole,
        monthStart: String
    ): Job = viewModelScope.launch {
        budgetRepository.observeBudgets(
            accountId = user.id,
            householdId = householdId,
            userId = user.id,
            canReadWholeHousehold = role == HouseholdRole.Admin,
            monthStart = monthStart
        ).collect { budgets ->
            _uiState.value = _uiState.value.copy(budgets = budgets)
        }
    }

    private fun stopObserving() {
        categoryJob?.cancel()
        transactionJob?.cancel()
        budgetJob?.cancel()
        categoryJob = null
        transactionJob = null
        budgetJob = null
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
                    noticeMessage = appContext.getString(R.string.budgets_saved_locally)
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
        is BudgetDataException -> appContext.getString(
            when (error.error) {
                BudgetDataError.InvalidAmount -> R.string.budgets_invalid_amount
                BudgetDataError.InvalidMonth -> R.string.budgets_invalid_month
                BudgetDataError.CategoryUnavailable -> R.string.budgets_category_unavailable
                BudgetDataError.DuplicateBudget -> R.string.budgets_duplicate
                BudgetDataError.PermissionDenied -> R.string.budgets_permission_denied
                BudgetDataError.BudgetUnavailable -> R.string.budgets_record_unavailable
                BudgetDataError.HouseholdUnavailable -> R.string.transactions_household_unavailable
            }
        )
        is UserManagementException -> error.message ?: appContext.getString(R.string.budgets_load_failed)
        else -> appContext.getString(R.string.budgets_operation_failed)
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
        private val budgetRepository: BudgetRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceBudgetsViewModel::class.java))
            return FinanceBudgetsViewModel(
                context,
                householdMembersRepository,
                financialRepository,
                budgetRepository
            ) as T
        }
    }
}

private const val SCREEN_CACHE_TTL_MILLIS = 60_000L

private fun currentMonthStart(): String = SimpleDateFormat(MONTH_START_PATTERN, Locale.ROOT)
    .format(Date())

private fun shiftMonth(monthStart: String, monthDelta: Int): String? = runCatching {
    val parser = SimpleDateFormat(MONTH_START_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = parser.parse(monthStart) ?: return null
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply {
        time = date
        add(Calendar.MONTH, monthDelta)
    }
    SimpleDateFormat(MONTH_START_PATTERN, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(calendar.time)
}.getOrNull()

private const val MONTH_START_PATTERN = "yyyy-MM-01"
