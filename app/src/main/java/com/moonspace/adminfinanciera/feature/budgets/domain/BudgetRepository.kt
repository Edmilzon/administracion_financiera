package com.moonspace.adminfinanciera.feature.budgets.domain

import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

const val MAX_BUDGET_LIMIT_CENTAVOS = 999_999_999_999_999_999L

data class FinanceBudget(
    val id: String,
    val householdId: String,
    val userId: String,
    val categoryId: String?,
    val categoryName: String?,
    val categoryKind: TransactionKind?,
    val monthStart: String,
    val amountLimitCentavos: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isGeneral: Boolean get() = categoryId == null
}

data class BudgetDraft(
    val id: String? = null,
    val categoryId: String?,
    val monthStart: String,
    val amountLimitCentavos: Long
)

data class BudgetUsage(
    val budget: FinanceBudget,
    val usedCentavos: BigInteger
) {
    val remainingCentavos: BigInteger
        get() = BigInteger.valueOf(budget.amountLimitCentavos) - usedCentavos
}

enum class BudgetDataError {
    InvalidAmount,
    InvalidMonth,
    CategoryUnavailable,
    DuplicateBudget,
    PermissionDenied,
    BudgetUnavailable,
    HouseholdUnavailable
}

class BudgetDataException(val error: BudgetDataError) : Exception()

interface BudgetRepository {
    fun observeBudgets(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean,
        monthStart: String
    ): Flow<List<FinanceBudget>>

    suspend fun saveBudget(accountId: String, userId: String, householdId: String, draft: BudgetDraft)

    suspend fun deleteOwnBudget(accountId: String, userId: String, householdId: String, budgetId: String)
}

/** Budget totals count gross income and expense amounts; they are never netted against each other. */
fun calculateBudgetUsage(
    budgets: List<FinanceBudget>,
    transactions: List<FinanceTransaction>,
    monthStart: String
): List<BudgetUsage> {
    val monthKey = monthStart.take(7)
    return budgets
        .filter { it.monthStart == monthStart }
        .map { budget ->
            val used = transactions.asSequence()
                .filter { transaction ->
                    transaction.createdBy == budget.userId &&
                        transaction.occurredOn.startsWith(monthKey) &&
                        (budget.categoryId == null || transaction.categoryId == budget.categoryId)
                }
                .fold(BigInteger.ZERO) { sum, transaction ->
                    sum + BigInteger.valueOf(transaction.amountCentavos)
                }
            BudgetUsage(budget, used)
        }
        .sortedWith(
            compareBy<BudgetUsage> { if (it.budget.isGeneral) 0 else 1 }
                .thenBy { it.budget.userId }
                .thenBy { it.budget.categoryName.orEmpty().lowercase() }
        )
}
