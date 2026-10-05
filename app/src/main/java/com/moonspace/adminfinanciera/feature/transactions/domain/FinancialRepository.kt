package com.moonspace.adminfinanciera.feature.transactions.domain

import kotlinx.coroutines.flow.Flow

enum class TransactionKind(val apiValue: String) {
    Income("income"),
    Expense("expense");

    companion object {
        fun fromApiValue(value: String): TransactionKind? = entries.firstOrNull { it.apiValue == value }
    }
}

data class FinanceCategory(
    val id: String,
    val householdId: String,
    val name: String,
    val kind: TransactionKind,
    val isActive: Boolean,
    val createdBy: String?
)

data class FinanceTransaction(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val categoryId: String,
    val categoryName: String,
    val kind: TransactionKind,
    val amountCentavos: Long,
    val currency: String,
    val occurredOn: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceRecurringRuleId: String? = null,
    val scheduledFor: String? = null
)

data class TransactionDraft(
    val id: String? = null,
    val kind: TransactionKind,
    val amountCentavos: Long,
    val categoryId: String,
    val occurredOn: String,
    val description: String?,
    val sourceRecurringRuleId: String? = null,
    val scheduledFor: String? = null
)

data class CategoryDraft(
    val id: String? = null,
    val name: String,
    val kind: TransactionKind
)

enum class FinanceDataError {
    InvalidAmount,
    InvalidDate,
    InvalidCategoryName,
    CategoryUnavailable,
    CategoryNameConflict,
    LastActiveCategory,
    PermissionDenied,
    RecordUnavailable,
    HouseholdUnavailable
}

class FinanceDataException(val error: FinanceDataError) : Exception()

interface FinancialRepository {
    fun observeCategories(accountId: String, householdId: String, includeInactive: Boolean): Flow<List<FinanceCategory>>
    fun observeTransactions(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceTransaction>>
    fun observePendingCount(accountId: String): Flow<Int>

    suspend fun prepareHousehold(accountId: String, householdId: String, role: String)
    suspend fun saveTransaction(accountId: String, userId: String, householdId: String, draft: TransactionDraft)
    suspend fun deleteOwnTransaction(accountId: String, userId: String, householdId: String, transactionId: String)
    suspend fun saveCategory(accountId: String, userId: String, householdId: String, role: String, draft: CategoryDraft)
    suspend fun deactivateCategory(
        accountId: String,
        householdId: String,
        role: String,
        categoryId: String
    )
}
