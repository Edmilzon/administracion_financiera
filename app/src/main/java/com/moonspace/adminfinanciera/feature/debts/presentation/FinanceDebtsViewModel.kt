package com.moonspace.adminfinanciera.feature.debts.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDataError
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDataException
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDraft
import com.moonspace.adminfinanciera.feature.debts.domain.DebtPaymentDraft
import com.moonspace.adminfinanciera.feature.debts.domain.DebtRepository
import com.moonspace.adminfinanciera.feature.debts.domain.FinanceDebt
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import com.moonspace.adminfinanciera.feature.users.domain.UserManagementException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.math.BigInteger

data class FinanceDebtsUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val hasHousehold: Boolean = false,
    val householdId: String? = null,
    val role: HouseholdRole? = null,
    val debts: List<FinanceDebt> = emptyList(),
    val memberEmails: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val noticeMessage: String? = null,
    val savedVersion: Int = 0,
    val deletedVersion: Int = 0
) {
    val owedByMeRemainingCentavos: BigInteger
        get() = debts.asSequence()
            .filter { it.direction.apiValue == "owed_by_me" }
            .fold(BigInteger.ZERO) { total, debt -> total + debt.remainingCentavos }

    val owedToMeRemainingCentavos: BigInteger
        get() = debts.asSequence()
            .filter { it.direction.apiValue == "owed_to_me" }
            .fold(BigInteger.ZERO) { total, debt -> total + debt.remainingCentavos }

    val netBalanceCentavos: BigInteger
        get() = owedToMeRemainingCentavos - owedByMeRemainingCentavos
}

class FinanceDebtsViewModel(
    context: Context,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val financialRepository: FinancialRepository,
    private val debtRepository: DebtRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(FinanceDebtsUiState())
    val uiState: StateFlow<FinanceDebtsUiState> = _uiState.asStateFlow()
    private var currentUser: AuthUser? = null
    private var loadJob: Job? = null
    private var debtsJob: Job? = null
    private var lastLoadAttemptAt = 0L

    fun load(user: AuthUser, forceRefresh: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (currentUser?.id == user.id && !forceRefresh &&
            (loadJob?.isActive == true || now - lastLoadAttemptAt < SCREEN_CACHE_TTL_MILLIS)
        ) return
        if (currentUser?.id != user.id) {
            stopObserving()
            currentUser = user
            _uiState.value = FinanceDebtsUiState()
            lastLoadAttemptAt = 0L
        }
        loadJob?.cancel()
        lastLoadAttemptAt = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(
            isLoading = !_uiState.value.hasHousehold,
            errorMessage = null
        )
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
                        debts = emptyList(),
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
                observeDebts(user, householdId, role)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val canKeepCachedData = _uiState.value.hasHousehold
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasHousehold = canKeepCachedData,
                    errorMessage = if (canKeepCachedData) null else error.message
                        ?: appContext.getString(R.string.debts_load_failed)
                )
            }
        }
    }

    fun saveDebt(draft: DebtDraft) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            debtRepository.saveDebt(user.id, user.id, householdId, draft)
            _uiState.value = _uiState.value.copy(savedVersion = _uiState.value.savedVersion + 1)
        }
    }

    fun deleteDebt(debtId: String) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            debtRepository.deleteOwnDebt(user.id, user.id, householdId, debtId)
            _uiState.value = _uiState.value.copy(deletedVersion = _uiState.value.deletedVersion + 1)
        }
    }

    fun savePayment(draft: DebtPaymentDraft) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            debtRepository.savePayment(user.id, user.id, householdId, draft)
            _uiState.value = _uiState.value.copy(savedVersion = _uiState.value.savedVersion + 1)
        }
    }

    fun deletePayment(paymentId: String) {
        val user = currentUser ?: return
        val householdId = _uiState.value.householdId ?: return
        submit {
            debtRepository.deleteOwnPayment(user.id, user.id, householdId, paymentId)
            _uiState.value = _uiState.value.copy(deletedVersion = _uiState.value.deletedVersion + 1)
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(actionErrorMessage = null, noticeMessage = null)
    }

    fun clearAccountContext() {
        loadJob?.cancel()
        stopObserving()
        currentUser = null
        lastLoadAttemptAt = 0L
        _uiState.value = FinanceDebtsUiState(isLoading = false)
    }

    private fun observeDebts(user: AuthUser, householdId: String, role: HouseholdRole) {
        debtsJob?.cancel()
        debtsJob = viewModelScope.launch {
            debtRepository.observeDebts(
                accountId = user.id,
                householdId = householdId,
                userId = user.id,
                canReadWholeHousehold = role == HouseholdRole.Admin
            ).collect { debts ->
                _uiState.value = _uiState.value.copy(debts = debts)
            }
        }
    }

    private fun stopObserving() {
        debtsJob?.cancel()
        debtsJob = null
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
                    noticeMessage = appContext.getString(R.string.debts_saved_locally)
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
        is DebtDataException -> appContext.getString(
            when (error.error) {
                DebtDataError.InvalidAmount -> R.string.debts_invalid_amount
                DebtDataError.InvalidCounterparty -> R.string.debts_invalid_counterparty
                DebtDataError.InvalidDate -> R.string.debts_invalid_date
                DebtDataError.InvalidDescription -> R.string.debts_invalid_description
                DebtDataError.DebtUnavailable -> R.string.debts_record_unavailable
                DebtDataError.PaymentUnavailable -> R.string.debts_payment_unavailable
                DebtDataError.PaymentExceedsBalance -> R.string.debts_payment_exceeds_balance
                DebtDataError.PrincipalBelowPaidAmount -> R.string.debts_principal_below_paid
                DebtDataError.PermissionDenied -> R.string.debts_permission_denied
                DebtDataError.HouseholdUnavailable -> R.string.transactions_household_unavailable
            }
        )
        is UserManagementException -> error.message ?: appContext.getString(R.string.debts_operation_failed)
        else -> appContext.getString(R.string.debts_operation_failed)
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
        private val debtRepository: DebtRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceDebtsViewModel::class.java))
            return FinanceDebtsViewModel(
                context,
                householdMembersRepository,
                financialRepository,
                debtRepository
            ) as T
        }
    }

    private companion object {
        const val SCREEN_CACHE_TTL_MILLIS = 60_000L
    }
}
