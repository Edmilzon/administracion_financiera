package com.moonspace.adminfinanciera.feature.reports.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportExporter
import com.moonspace.adminfinanciera.feature.reports.domain.GeneratedFinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.ReportExportFormat
import com.moonspace.adminfinanciera.feature.reports.domain.ReportFilters
import com.moonspace.adminfinanciera.feature.reports.domain.ReportKindFilter
import com.moonspace.adminfinanciera.feature.reports.domain.buildFinanceReport
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMember
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FinanceReportsUiState(
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val hasHousehold: Boolean = false,
    val isAdministrator: Boolean = false,
    val selectedStartOn: String = currentMonthStart(),
    val selectedEndOn: String = todayIsoDate(),
    val selectedKind: ReportKindFilter = ReportKindFilter.All,
    val selectedCategoryId: String? = null,
    val selectedMemberId: String? = null,
    val report: FinanceReport? = null,
    val generatedFile: GeneratedFinanceReport? = null,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null
)

class FinanceReportsViewModel(
    context: Context,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val financialRepository: FinancialRepository,
    private val documentGenerator: FinanceReportExporter
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(FinanceReportsUiState())
    val uiState: StateFlow<FinanceReportsUiState> = _uiState.asStateFlow()
    private var currentUser: AuthUser? = null
    private var loadJob: Job? = null
    private var transactionJob: Job? = null
    private var categoryJob: Job? = null
    private var lastLoadAttemptAt = 0L
    private var transactions: List<FinanceTransaction> = emptyList()
    private var categories: List<FinanceCategory> = emptyList()
    private var members: List<HouseholdMember> = emptyList()
    private var householdId: String? = null
    private var role: HouseholdRole? = null

    fun load(user: AuthUser, forceRefresh: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (currentUser?.id == user.id && !forceRefresh &&
            (loadJob?.isActive == true || now - lastLoadAttemptAt < SCREEN_CACHE_TTL_MILLIS)
        ) return
        if (currentUser?.id != user.id) {
            stopObserving()
            clearSource()
            currentUser = user
            _uiState.value = FinanceReportsUiState(
                isLoading = true,
                selectedStartOn = currentMonthStart(),
                selectedEndOn = todayIsoDate()
            )
        }
        loadJob?.cancel()
        lastLoadAttemptAt = SystemClock.elapsedRealtime()
        val hasCachedReport = _uiState.value.report != null
        _uiState.value = _uiState.value.copy(isLoading = !hasCachedReport, errorMessage = null)
        loadJob = viewModelScope.launch {
            try {
                val snapshot = householdMembersRepository.load(user)
                val currentHouseholdId = snapshot.householdId
                val currentRole = snapshot.currentUserRole
                if (currentHouseholdId.isNullOrBlank() || currentRole == null) {
                    stopObserving()
                    clearSource()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hasHousehold = false,
                        report = null,
                        generatedFile = null
                    )
                    return@launch
                }

                financialRepository.prepareHousehold(user.id, currentHouseholdId, currentRole.apiValue)
                val canReadWholeHousehold = currentRole == HouseholdRole.Admin
                val newTransactions = financialRepository.observeTransactions(
                    accountId = user.id,
                    householdId = currentHouseholdId,
                    userId = user.id,
                    canReadWholeHousehold = canReadWholeHousehold
                ).first()
                val newCategories = financialRepository.observeCategories(
                    accountId = user.id,
                    householdId = currentHouseholdId,
                    includeInactive = true
                ).first()
                transactions = newTransactions
                categories = newCategories
                members = snapshot.members
                householdId = currentHouseholdId
                role = currentRole
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = true,
                    isAdministrator = canReadWholeHousehold,
                    errorMessage = null,
                    actionErrorMessage = null
                )
                rebuildReport(user)
                startObserving(user, currentHouseholdId, canReadWholeHousehold)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val canKeepCachedReport = _uiState.value.report != null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = canKeepCachedReport,
                    errorMessage = if (canKeepCachedReport) null
                    else appContext.getString(R.string.reports_load_failed)
                )
            }
        }
    }

    fun selectStartDate(value: String) {
        val current = _uiState.value
        val nextEnd = if (value > current.selectedEndOn) value else current.selectedEndOn
        updateFilters(current.copy(selectedStartOn = value, selectedEndOn = nextEnd))
    }

    fun selectEndDate(value: String) {
        val current = _uiState.value
        val nextStart = if (value < current.selectedStartOn) value else current.selectedStartOn
        updateFilters(current.copy(selectedStartOn = nextStart, selectedEndOn = value))
    }

    fun selectKind(value: String?) {
        val kind = when (value) {
            "income" -> ReportKindFilter.Income
            "expense" -> ReportKindFilter.Expense
            else -> ReportKindFilter.All
        }
        updateFilters(_uiState.value.copy(selectedKind = kind))
    }

    fun selectCategory(value: String?) = updateFilters(_uiState.value.copy(selectedCategoryId = value))

    fun selectMember(value: String?) {
        if (!_uiState.value.isAdministrator) return
        updateFilters(_uiState.value.copy(selectedMemberId = value))
    }

    fun resetFilters() {
        val now = todayIsoDate()
        updateFilters(
            _uiState.value.copy(
                selectedStartOn = now.substringBeforeLast('-') + "-01",
                selectedEndOn = now,
                selectedKind = ReportKindFilter.All,
                selectedCategoryId = null,
                selectedMemberId = null
            )
        )
    }

    fun generate(format: ReportExportFormat) {
        val report = _uiState.value.report ?: return
        if (_uiState.value.isGenerating) return
        _uiState.value = _uiState.value.copy(isGenerating = true, actionErrorMessage = null, generatedFile = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { documentGenerator.generate(report, format) }
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    generatedFile = result,
                    actionErrorMessage = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    generatedFile = null,
                    actionErrorMessage = appContext.getString(R.string.reports_export_failed)
                )
            }
        }
    }

    fun reportDeliveryFailed() {
        _uiState.value = _uiState.value.copy(actionErrorMessage = appContext.getString(R.string.reports_delivery_failed))
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        stopObserving()
        currentUser = null
        lastLoadAttemptAt = 0L
        clearSource()
        _uiState.value = FinanceReportsUiState(isLoading = false)
    }

    private fun updateFilters(next: FinanceReportsUiState) {
        val categoryExists = categories.any { it.id == next.selectedCategoryId }
        val memberExists = members.any { it.userId == next.selectedMemberId }
        _uiState.value = next.copy(
            selectedCategoryId = next.selectedCategoryId.takeIf { it == null || categoryExists },
            selectedMemberId = next.selectedMemberId.takeIf { it == null || memberExists },
            generatedFile = null,
            actionErrorMessage = null
        )
        currentUser?.let(::rebuildReport)
    }

    private fun rebuildReport(user: AuthUser) {
        val currentHouseholdId = householdId ?: return
        val canReadWholeHousehold = role == HouseholdRole.Admin
        val state = _uiState.value
        val filters = ReportFilters(
            startOn = state.selectedStartOn,
            endOn = state.selectedEndOn,
            kind = state.selectedKind,
            categoryId = state.selectedCategoryId,
            memberId = state.selectedMemberId
        )
        val report = buildFinanceReport(
            transactions = transactions.filter { it.householdId == currentHouseholdId },
            categories = categories.filter { it.householdId == currentHouseholdId },
            members = members,
            currentUserId = user.id,
            currentUserLabel = user.email,
            isAdministrator = canReadWholeHousehold,
            filters = filters
        )
        _uiState.value = _uiState.value.copy(report = report)
    }

    private fun clearSource() {
        stopObserving()
        transactions = emptyList()
        categories = emptyList()
        members = emptyList()
        householdId = null
        role = null
    }

    private fun startObserving(user: AuthUser, currentHouseholdId: String, canReadWholeHousehold: Boolean) {
        stopObserving()
        transactionJob = viewModelScope.launch {
            financialRepository.observeTransactions(
                accountId = user.id,
                householdId = currentHouseholdId,
                userId = user.id,
                canReadWholeHousehold = canReadWholeHousehold
            ).collect { updated ->
                transactions = updated
                rebuildReport(user)
            }
        }
        categoryJob = viewModelScope.launch {
            financialRepository.observeCategories(user.id, currentHouseholdId, includeInactive = true)
                .collect { updated ->
                    categories = updated
                    rebuildReport(user)
                }
        }
    }

    private fun stopObserving() {
        transactionJob?.cancel()
        categoryJob?.cancel()
        transactionJob = null
        categoryJob = null
    }

    override fun onCleared() {
        loadJob?.cancel()
        currentUser = null
        clearSource()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val householdMembersRepository: HouseholdMembersRepository,
        private val financialRepository: FinancialRepository,
        private val documentGenerator: FinanceReportExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceReportsViewModel::class.java))
            return FinanceReportsViewModel(
                context,
                householdMembersRepository,
                financialRepository,
                documentGenerator
            ) as T
        }
    }
}

private const val SCREEN_CACHE_TTL_MILLIS = 60_000L

private fun currentMonthStart(): String = todayIsoDate().substringBeforeLast('-') + "-01"

private fun todayIsoDate(): String = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
    timeZone = TimeZone.getDefault()
}.format(Date())

private const val DATE_PATTERN = "yyyy-MM-dd"
