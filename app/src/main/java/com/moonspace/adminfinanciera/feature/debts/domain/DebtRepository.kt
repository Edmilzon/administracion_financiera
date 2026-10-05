package com.moonspace.adminfinanciera.feature.debts.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

enum class DebtDirection(val apiValue: String) {
    OwedByMe("owed_by_me"),
    OwedToMe("owed_to_me");

    companion object {
        fun fromApiValue(value: String): DebtDirection? = entries.firstOrNull { it.apiValue == value }
    }
}

data class FinanceDebt(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val direction: DebtDirection,
    val counterparty: String,
    val description: String?,
    val principalCentavos: Long,
    val currency: String,
    val openedOn: String,
    val dueOn: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val payments: List<DebtPayment>
) {
    val paidCentavos: BigInteger
        get() = payments.fold(BigInteger.ZERO) { total, payment -> total + BigInteger.valueOf(payment.amountCentavos) }

    val remainingCentavos: BigInteger
        get() = BigInteger.valueOf(principalCentavos) - paidCentavos

    val isSettled: Boolean
        get() = remainingCentavos == BigInteger.ZERO
}

data class DebtPayment(
    val id: String,
    val debtId: String,
    val amountCentavos: Long,
    val paidOn: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class DebtDraft(
    val id: String? = null,
    val direction: DebtDirection,
    val counterparty: String,
    val description: String?,
    val principalCentavos: Long,
    val openedOn: String,
    val dueOn: String?
)

data class DebtPaymentDraft(
    val id: String? = null,
    val debtId: String,
    val amountCentavos: Long,
    val paidOn: String,
    val note: String?
)

enum class DebtDataError {
    InvalidAmount,
    InvalidCounterparty,
    InvalidDate,
    InvalidDescription,
    DebtUnavailable,
    PaymentUnavailable,
    PaymentExceedsBalance,
    PrincipalBelowPaidAmount,
    PermissionDenied,
    HouseholdUnavailable
}

class DebtDataException(val error: DebtDataError) : Exception()

interface DebtRepository {
    fun observeDebts(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceDebt>>

    suspend fun saveDebt(accountId: String, userId: String, householdId: String, draft: DebtDraft)
    suspend fun deleteOwnDebt(accountId: String, userId: String, householdId: String, debtId: String)
    suspend fun savePayment(accountId: String, userId: String, householdId: String, draft: DebtPaymentDraft)
    suspend fun deleteOwnPayment(accountId: String, userId: String, householdId: String, paymentId: String)
}

const val MAX_DEBT_AMOUNT_CENTAVOS = 999_999_999_999_999_999L
