package com.moonspace.adminfinanciera.feature.transactions.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.transactions.domain.CategoryDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceDataError
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceDataException
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransactionReport
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import com.moonspace.adminfinanciera.feature.transactions.domain.buildFinanceTransactionReport
import com.moonspace.adminfinanciera.feature.transactions.domain.filterFinanceTransactions
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class FinanceTransactionsUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasHousehold: Boolean = false,
    val householdId: String? = null,
    val role: HouseholdRole? = null,
    val categories: List<FinanceCategory> = emptyList(),
    val transactions: List<FinanceTransaction> = emptyList(),
    val memberEmails: Map<String, String> = emptyMap(),
    val pendingSyncCount: Int = 0,
    val selectedMonthKey: String? = null,
    val selectedKind: TransactionKind? = null,
    val selectedMemberId: String? = null,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val noticeMessage: String? = null,
    val transactionSavedVersion: Int = 0,
    val transactionDeletedVersion: Int = 0,
    val categorySavedVersion: Int = 0
) {
    val canManageCategories: Boolean get() = role == HouseholdRole.Admin
    val availableMonthKeys: List<String>
        get() = (transactions.map { it.occurredOn.take(7) } + listOfNotNull(selectedMonthKey))
            .distinct()
            .sortedDescending()
    val filteredTransactions: List<FinanceTransaction>
        get() = filterFinanceTransactions(transactions, selectedMonthKey, selectedKind, selectedMemberId)
    val report: FinanceTransactionReport
        get() = buildFinanceTransactionReport(filteredTransactions, memberEmails)
    val hasActiveFilters: Boolean
        get() = selectedMonthKey != null || selectedKind != null || selectedMemberId != null
}

class FinanceTransactionsViewModel(
    context: Context,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val financialRepository: FinancialRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(FinanceTransactionsUiState())
    val uiState: StateFlow<FinanceTransactionsUiState> = _uiState.asStateFlow()
    private var currentUser: AuthUser? = null
    private var loadJob: Job? = null
    private var categoryJob: Job? = null
    private var transactionJob: Job? = null
    private var pendingJob: Job? = null
    private var lastLoadAttemptAt = 0L

    fun load(user: AuthUser, forceRefresh: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (currentUser?.id == user.id && !forceRefresh &&
            (loadJob?.isActive == true || now - lastLoadAttemptAt < SCREEN_CACHE_TTL_MILLIS)
        ) return
        if (currentUser?.id != user.id) {
            stopObserving()
            currentUser = user
            _uiState.value = FinanceTransactionsUiState()
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
                        categories = emptyList(),
                        transactions = emptyList(),
                        memberEmails = emptyMap(),
                        selectedMonthKey = null,
                        selectedKind = null,
                        selectedMemberId = null,
                        errorMessage = null
                    )
                    return@launch
                }

                financialRepository.prepareHousehold(user.id, householdId, role.apiValue)
                val previousState = _uiState.value
                val householdChanged = previousState.householdId != householdId
                val memberEmails = snapshot.members.associate { it.userId to it.email }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = true,
                    householdId = householdId,
                    role = role,
                    memberEmails = memberEmails,
                    selectedMonthKey = previousState.selectedMonthKey.takeUnless { householdChanged },
                    selectedKind = previousState.selectedKind.takeUnless { householdChanged },
                    selectedMemberId = previousState.selectedMemberId
                        ?.takeIf { !householdChanged && role == HouseholdRole.Admin && it in memberEmails },
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

    fun saveTransaction(draft: TransactionDraft) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            financialRepository.saveTransaction(user.id, user.id, householdId, draft)
            _uiState.value = _uiState.value.copy(transactionSavedVersion = _uiState.value.transactionSavedVersion + 1)
        }
    }

    fun deleteTransaction(transactionId: String) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            financialRepository.deleteOwnTransaction(user.id, user.id, householdId, transactionId)
            _uiState.value = _uiState.value.copy(transactionDeletedVersion = _uiState.value.transactionDeletedVersion + 1)
        }
    }

    fun saveCategory(draft: CategoryDraft) {
        val user = currentUser ?: return
        val state = _uiState.value
        val householdId = state.householdId ?: return
        val role = state.role?.apiValue ?: return
        submit {
            financialRepository.saveCategory(user.id, user.id, householdId, role, draft)
            _uiState.value = _uiState.value.copy(categorySavedVersion = _uiState.value.categorySavedVersion + 1)
        }
    }

    fun deactivateCategory(categoryId: String) {
        val state = _uiState.value
        val householdId = state.householdId ?: return
        val role = state.role?.apiValue ?: return
        val user = currentUser ?: return
        submit {
            financialRepository.deactivateCategory(user.id, householdId, role, categoryId)
            _uiState.value = _uiState.value.copy(categorySavedVersion = _uiState.value.categorySavedVersion + 1)
        }
    }

    fun selectMonth(monthKey: String?) {
        if (monthKey != null && monthKey !in _uiState.value.availableMonthKeys) return
        _uiState.value = _uiState.value.copy(selectedMonthKey = monthKey)
    }

    fun selectKind(kind: TransactionKind?) {
        _uiState.value = _uiState.value.copy(selectedKind = kind)
    }

    fun selectMember(memberId: String?) {
        val state = _uiState.value
        if (memberId != null && (!state.canManageCategories || memberId !in state.memberEmails)) return
        _uiState.value = state.copy(selectedMemberId = memberId)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedMonthKey = null,
            selectedKind = null,
            selectedMemberId = null
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, actionErrorMessage = null, noticeMessage = null)
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        stopObserving()
        currentUser = null
        lastLoadAttemptAt = 0L
        _uiState.value = FinanceTransactionsUiState(isLoading = false)
    }

    private fun startObserving(user: AuthUser, householdId: String, role: HouseholdRole) {
        stopObserving()
        categoryJob = viewModelScope.launch {
            financialRepository.observeCategories(
                accountId = user.id,
                householdId = householdId,
                includeInactive = role == HouseholdRole.Admin
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
        pendingJob = viewModelScope.launch {
            financialRepository.observePendingCount(user.id).collect { count ->
                _uiState.value = _uiState.value.copy(pendingSyncCount = count)
            }
        }
    }

    private fun stopObserving() {
        categoryJob?.cancel()
        transactionJob?.cancel()
        pendingJob?.cancel()
        categoryJob = null
        transactionJob = null
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
                    noticeMessage = appContext.getString(R.string.transactions_saved_locally)
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
        is FinanceDataException -> appContext.getString(
            when (error.error) {
                FinanceDataError.InvalidAmount -> R.string.transactions_invalid_amount
                FinanceDataError.InvalidDate -> R.string.transactions_invalid_date
                FinanceDataError.InvalidCategoryName -> R.string.categories_invalid_name
                FinanceDataError.CategoryUnavailable -> R.string.transactions_category_unavailable
                FinanceDataError.CategoryNameConflict -> R.string.categories_name_conflict
                FinanceDataError.LastActiveCategory -> R.string.categories_last_active
                FinanceDataError.PermissionDenied -> R.string.categories_admin_only
                FinanceDataError.RecordUnavailable -> R.string.transactions_record_unavailable
                FinanceDataError.HouseholdUnavailable -> R.string.transactions_household_unavailable
            }
        )
        is UserManagementException -> error.message ?: appContext.getString(R.string.transactions_load_failed)
        else -> appContext.getString(R.string.transactions_operation_failed)
    }

    override fun onCleared() {
        loadJob?.cancel()
        stopObserving()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val householdMembersRepository: HouseholdMembersRepository,
        private val financialRepository: FinancialRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceTransactionsViewModel::class.java))
            return FinanceTransactionsViewModel(context, householdMembersRepository, financialRepository) as T
        }
    }
}

private const val SCREEN_CACHE_TTL_MILLIS = 60_000L
