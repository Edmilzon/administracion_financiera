package com.moonspace.adminfinanciera.feature.budgets.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.BudgetEntity
import com.moonspace.adminfinanciera.core.database.CategoryEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.sync.FinanceSyncScheduler
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDataError
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDataException
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDraft
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetRepository
import com.moonspace.adminfinanciera.feature.budgets.domain.FinanceBudget
import com.moonspace.adminfinanciera.feature.budgets.domain.MAX_BUDGET_LIMIT_CENTAVOS
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class RoomBudgetRepository(
    private val databases: EncryptedFinanceDatabaseProvider,
    private val syncScheduler: FinanceSyncScheduler
) : BudgetRepository {
    override fun observeBudgets(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean,
        monthStart: String
    ): Flow<List<FinanceBudget>> {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        val budgets = if (canReadWholeHousehold) {
            database.budgetDao().observeAllForMonth(householdId, monthStart)
        } else {
            database.budgetDao().observeOwnForMonth(householdId, userId, monthStart)
        }
        return combine(budgets, database.categoryDao().observeAll(householdId)) { rows, categories ->
            val categoriesById = categories.associateBy(CategoryEntity::id)
            rows.map { row -> row.toDomain(categoriesById[row.categoryId]) }
        }
    }

    override suspend fun saveBudget(
        accountId: String,
        userId: String,
        householdId: String,
        draft: BudgetDraft
    ) {
        requireAccountOwner(accountId, userId)
        if (householdId.isBlank()) throw BudgetDataException(BudgetDataError.HouseholdUnavailable)
        if (draft.amountLimitCentavos !in 1..MAX_BUDGET_LIMIT_CENTAVOS) {
            throw BudgetDataException(BudgetDataError.InvalidAmount)
        }
        if (!isMonthStart(draft.monthStart)) throw BudgetDataException(BudgetDataError.InvalidMonth)

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val dao = database.budgetDao()
            val previous = draft.id?.let { dao.findById(householdId, it) }
            if (draft.id != null && (previous == null || previous.userId != userId)) {
                throw BudgetDataException(BudgetDataError.BudgetUnavailable)
            }

            val category = draft.categoryId?.let { id ->
                database.categoryDao().findById(householdId, id)
                    ?: throw BudgetDataException(BudgetDataError.CategoryUnavailable)
            }
            if (category != null && !category.isActive && previous?.categoryId != category.id) {
                throw BudgetDataException(BudgetDataError.CategoryUnavailable)
            }
            val categoryKey = category?.id ?: GENERAL_CATEGORY_KEY
            val duplicate = dao.findForScope(householdId, userId, draft.monthStart, categoryKey)
            if (duplicate != null && duplicate.id != previous?.id) {
                throw BudgetDataException(BudgetDataError.DuplicateBudget)
            }

            val now = System.currentTimeMillis()
            val id = if (previous == null) {
                stableBudgetId(householdId, userId, draft.monthStart, categoryKey)
            } else if (previous.monthStart == draft.monthStart && previous.categoryKey == categoryKey) {
                previous.id
            } else {
                stableBudgetId(householdId, userId, draft.monthStart, categoryKey)
            }
            if (previous != null && previous.id != id) {
                dao.deleteById(householdId, previous.id)
                database.syncOutboxDao().removeForEntity(accountId, ENTITY_BUDGET, previous.id)
                enqueue(accountId, previous.id, OPERATION_DELETE)
            }
            dao.save(
                BudgetEntity(
                    id = id,
                    accountId = accountId,
                    householdId = householdId,
                    userId = userId,
                    categoryId = category?.id,
                    categoryKey = categoryKey,
                    monthStart = draft.monthStart,
                    amountCentavos = draft.amountLimitCentavos,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    isRemoteBacked = previous?.isRemoteBacked ?: false
                )
            )
            enqueue(accountId, id)
        }
        syncScheduler.scheduleNow(accountId)
    }

    override suspend fun deleteOwnBudget(
        accountId: String,
        userId: String,
        householdId: String,
        budgetId: String
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val budget = database.budgetDao().findById(householdId, budgetId)
            if (budget == null || budget.userId != userId) {
                throw BudgetDataException(BudgetDataError.BudgetUnavailable)
            }
            database.budgetDao().deleteById(householdId, budgetId)
            enqueue(accountId, budgetId, OPERATION_DELETE)
        }
        syncScheduler.scheduleNow(accountId)
    }

    private suspend fun enqueue(accountId: String, budgetId: String, operation: String = OPERATION_UPSERT) {
        val outboxDao = databases.databaseFor(accountId).syncOutboxDao()
        outboxDao.removeForEntity(accountId, ENTITY_BUDGET, budgetId)
        outboxDao.enqueue(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                entityType = ENTITY_BUDGET,
                entityId = budgetId,
                operation = operation,
                enqueuedAt = System.currentTimeMillis()
            )
        )
    }

    private fun requireAccountOwner(accountId: String, userId: String) {
        if (accountId.isBlank() || accountId != userId) {
            throw BudgetDataException(BudgetDataError.PermissionDenied)
        }
    }

    private fun isMonthStart(value: String): Boolean = try {
        val format = SimpleDateFormat(MONTH_START_PATTERN, Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(value)?.let(format::format) == value && value.endsWith("-01")
    } catch (_: Exception) {
        false
    }

    private fun stableBudgetId(
        householdId: String,
        userId: String,
        monthStart: String,
        categoryKey: String
    ): String = UUID.nameUUIDFromBytes(
        "$householdId:$userId:$monthStart:$categoryKey".toByteArray(Charsets.UTF_8)
    ).toString()

    private fun BudgetEntity.toDomain(category: CategoryEntity?): FinanceBudget = FinanceBudget(
        id = id,
        householdId = householdId,
        userId = userId,
        categoryId = categoryId,
        categoryName = category?.name,
        categoryKind = category?.kind?.let(TransactionKind::fromApiValue),
        monthStart = monthStart,
        amountLimitCentavos = amountCentavos,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private companion object {
        const val ENTITY_BUDGET = "budget"
        const val OPERATION_UPSERT = "upsert"
        const val OPERATION_DELETE = "delete"
        const val GENERAL_CATEGORY_KEY = "__general__"
        const val MONTH_START_PATTERN = "yyyy-MM-dd"
    }
}
