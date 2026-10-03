package com.moonspace.adminfinanciera.feature.transactions.domain

import java.math.BigInteger

data class FinanceCategoryTotal(
    val categoryId: String,
    val categoryName: String,
    val kind: TransactionKind,
    val amountCentavos: BigInteger
)

data class FinanceMemberTotal(
    val userId: String,
    val email: String?,
    val incomeCentavos: BigInteger,
    val expenseCentavos: BigInteger,
    val balanceCentavos: BigInteger
)

data class FinanceTransactionReport(
    val incomeCentavos: BigInteger,
    val expenseCentavos: BigInteger,
    val balanceCentavos: BigInteger,
    val categoryTotals: List<FinanceCategoryTotal>,
    val memberTotals: List<FinanceMemberTotal>
)

fun filterFinanceTransactions(
    transactions: List<FinanceTransaction>,
    monthKey: String?,
    kind: TransactionKind?,
    memberId: String?
): List<FinanceTransaction> = transactions.filter { transaction ->
    (monthKey == null || transaction.occurredOn.startsWith(monthKey)) &&
        (kind == null || transaction.kind == kind) &&
        (memberId == null || transaction.createdBy == memberId)
}

fun buildFinanceTransactionReport(
    transactions: List<FinanceTransaction>,
    memberEmails: Map<String, String>
): FinanceTransactionReport {
    val income = transactions.filter { it.kind == TransactionKind.Income }.sumCentavos()
    val expense = transactions.filter { it.kind == TransactionKind.Expense }.sumCentavos()
    val categories = transactions
        .groupBy { it.categoryId to it.kind }
        .map { (key, records) ->
            FinanceCategoryTotal(
                categoryId = key.first,
                categoryName = records.first().categoryName,
                kind = key.second,
                amountCentavos = records.sumCentavos()
            )
        }
        .sortedWith(
            compareBy<FinanceCategoryTotal> { if (it.kind == TransactionKind.Expense) 0 else 1 }
                .thenByDescending { it.amountCentavos }
                .thenBy { it.categoryName.lowercase() }
        )
    val members = transactions
        .groupBy(FinanceTransaction::createdBy)
        .map { (userId, records) ->
            val memberIncome = records.filter { it.kind == TransactionKind.Income }.sumCentavos()
            val memberExpense = records.filter { it.kind == TransactionKind.Expense }.sumCentavos()
            FinanceMemberTotal(
                userId = userId,
                email = memberEmails[userId],
                incomeCentavos = memberIncome,
                expenseCentavos = memberExpense,
                balanceCentavos = memberIncome - memberExpense
            )
        }
        .sortedWith(
            compareByDescending<FinanceMemberTotal> { it.incomeCentavos + it.expenseCentavos }
                .thenBy { it.email.orEmpty().lowercase() }
        )

    return FinanceTransactionReport(
        incomeCentavos = income,
        expenseCentavos = expense,
        balanceCentavos = income - expense,
        categoryTotals = categories,
        memberTotals = members
    )
}

private fun List<FinanceTransaction>.sumCentavos(): BigInteger = fold(BigInteger.ZERO) { sum, transaction ->
    sum + BigInteger.valueOf(transaction.amountCentavos)
}
