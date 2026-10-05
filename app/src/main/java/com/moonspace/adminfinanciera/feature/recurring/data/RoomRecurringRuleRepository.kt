package com.moonspace.adminfinanciera.feature.recurring.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.entities.CategoryEntity
import com.moonspace.adminfinanciera.core.database.entities.RecurringRuleEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.entities.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.database.entities.TransactionEntity
import com.moonspace.adminfinanciera.core.sync.FinanceSyncScheduler
import com.moonspace.adminfinanciera.core.sync.RecurringReminderScheduler
import com.moonspace.adminfinanciera.feature.recurring.domain.FinanceRecurringRule
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurrenceFrequency
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleDraft
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleError
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleException
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleRepository
import com.moonspace.adminfinanciera.feature.recurring.domain.nextRecurringDate
import com.moonspace.adminfinanciera.feature.recurring.domain.parseIsoDate
import com.moonspace.adminfinanciera.feature.recurring.domain.todayIsoDate
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomRecurringRuleRepository(
    private val databases: EncryptedFinanceDatabaseProvider,
    private val syncScheduler: FinanceSyncScheduler,
    private val reminderScheduler: RecurringReminderScheduler
) : RecurringRuleRepository {
    override fun observeRules(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceRecurringRule>> {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        val rules = if (canReadWholeHousehold) {
            database.recurringRuleDao().observeAllForHousehold(householdId)
        } else {
            database.recurringRuleDao().observeOwn(householdId, userId)
        }
        return combine(rules, database.categoryDao().observeAll(householdId)) { recurringRules, categories ->
            val categoriesById = categories.associateBy(CategoryEntity::id)
            recurringRules.mapNotNull { it.toDomain(categoriesById[it.categoryId]) }
        }
    }

    override suspend fun saveRule(
        accountId: String,
        userId: String,
        householdId: String,
        draft: RecurringRuleDraft
    ) {
        requireAccountOwner(accountId, userId)
        if (householdId.isBlank()) throw RecurringRuleException(RecurringRuleError.HouseholdUnavailable)
        if (draft.amountCentavos <= 0) throw RecurringRuleException(RecurringRuleError.InvalidAmount)
        if (draft.intervalCount !in 1..MAX_INTERVAL_COUNT) {
            throw RecurringRuleException(RecurringRuleError.InvalidInterval)
        }
        if (parseIsoDate(draft.startOn) == null) throw RecurringRuleException(RecurringRuleError.InvalidDate)

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val ruleDao = database.recurringRuleDao()
            val previous = draft.id?.let { ruleDao.findById(householdId, it) }
            if (draft.id != null && (previous == null || previous.createdBy != userId)) {
                throw RecurringRuleException(RecurringRuleError.RuleUnavailable)
            }
            val category = database.categoryDao().findById(householdId, draft.categoryId)
                ?: throw RecurringRuleException(RecurringRuleError.CategoryUnavailable)
            if (category.kind != draft.kind.apiValue ||
                (!category.isActive && previous?.categoryId != category.id)
            ) {
                throw RecurringRuleException(RecurringRuleError.CategoryUnavailable)
            }

            val now = System.currentTimeMillis()
            val scheduleChanged = previous == null || previous.startOn != draft.startOn
            val nextDueOn = if (scheduleChanged) draft.startOn else previous!!.nextDueOn
            val ruleId = previous?.id ?: UUID.randomUUID().toString()
            ruleDao.save(
                RecurringRuleEntity(
                    id = ruleId,
                    accountId = accountId,
                    householdId = householdId,
                    createdBy = previous?.createdBy ?: userId,
                    categoryId = category.id,
                    kind = draft.kind.apiValue,
                    amountCentavos = draft.amountCentavos,
                    currency = CURRENCY_BOB,
                    description = draft.description?.trim()?.takeIf(String::isNotEmpty),
                    frequency = draft.frequency.apiValue,
                    intervalCount = draft.intervalCount,
                    startOn = draft.startOn,
                    nextDueOn = nextDueOn,
                    isActive = previous?.isActive ?: true,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    isRemoteBacked = previous?.isRemoteBacked ?: false
                )
            )
            enqueue(accountId, ruleId)
        }
        syncScheduler.scheduleNow(accountId)
        reminderScheduler.scheduleDaily(accountId)
    }

    override suspend fun setRuleActive(
        accountId: String,
        userId: String,
        householdId: String,
        ruleId: String,
        active: Boolean
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val rule = database.recurringRuleDao().findById(householdId, ruleId)
            if (rule == null || rule.createdBy != userId) {
                throw RecurringRuleException(RecurringRuleError.RuleUnavailable)
            }
            if (rule.isActive != active) {
                database.recurringRuleDao().save(
                    rule.copy(isActive = active, updatedAt = System.currentTimeMillis())
                )
                enqueue(accountId, ruleId)
            }
        }
        syncScheduler.scheduleNow(accountId)
        reminderScheduler.scheduleDaily(accountId)
    }

    override suspend fun confirmOccurrence(
        accountId: String,
        userId: String,
        householdId: String,
        ruleId: String
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val ruleDao = database.recurringRuleDao()
            val rule = ruleDao.findById(householdId, ruleId)
            if (rule == null || rule.createdBy != userId || !rule.isActive) {
                throw RecurringRuleException(RecurringRuleError.RuleUnavailable)
            }
            val scheduledFor = rule.nextDueOn
            if (scheduledFor > todayIsoDate()) {
                throw RecurringRuleException(RecurringRuleError.OccurrenceNotDue)
            }
            val category = database.categoryDao().findById(householdId, rule.categoryId)
                ?: throw RecurringRuleException(RecurringRuleError.CategoryUnavailable)
            if (category.kind != rule.kind || !category.isActive) {
                throw RecurringRuleException(RecurringRuleError.CategoryUnavailable)
            }
            val transactionDao = database.transactionDao()
            val existing = transactionDao.findRecurringOccurrence(ruleId, scheduledFor)
            if (existing == null) {
                val transactionId = UUID.nameUUIDFromBytes(
                    "recurring:$ruleId:$scheduledFor".toByteArray(Charsets.UTF_8)
                ).toString()
                val now = System.currentTimeMillis()
                val inserted = transactionDao.insertIfMissing(
                    TransactionEntity(
                        id = transactionId,
                        accountId = accountId,
                        householdId = householdId,
                        createdBy = userId,
                        categoryId = rule.categoryId,
                        kind = rule.kind,
                        amountCentavos = rule.amountCentavos,
                        currency = rule.currency,
                        occurredOn = scheduledFor,
                        description = rule.description,
                        createdAt = now,
                        updatedAt = now,
                        sourceRecurringRuleId = rule.id,
                        scheduledFor = scheduledFor
                    )
                )
                if (inserted != -1L) enqueue(accountId, transactionId, ENTITY_TRANSACTION)
            }
            val nextDueOn = nextRecurringDate(
                currentDueOn = scheduledFor,
                startOn = rule.startOn,
                frequency = RecurrenceFrequency.fromApiValue(rule.frequency)
                    ?: throw RecurringRuleException(RecurringRuleError.RuleUnavailable),
                intervalCount = rule.intervalCount
            ) ?: throw RecurringRuleException(RecurringRuleError.InvalidDate)
            ruleDao.save(rule.copy(nextDueOn = nextDueOn, updatedAt = System.currentTimeMillis()))
            enqueue(accountId, ruleId)
        }
        syncScheduler.scheduleNow(accountId)
        reminderScheduler.scheduleDaily(accountId)
    }

    private suspend fun enqueue(accountId: String, entityId: String, entityType: String = ENTITY_RECURRING_RULE) {
        val outbox = databases.databaseFor(accountId).syncOutboxDao()
        outbox.removeForEntity(accountId, entityType, entityId)
        outbox.enqueue(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                entityType = entityType,
                entityId = entityId,
                operation = OPERATION_UPSERT,
                enqueuedAt = System.currentTimeMillis()
            )
        )
    }

    private fun RecurringRuleEntity.toDomain(category: CategoryEntity?): FinanceRecurringRule? {
        val kind = TransactionKind.fromApiValue(kind) ?: return null
        val frequency = RecurrenceFrequency.fromApiValue(frequency) ?: return null
        val categoryName = category?.name ?: return null
        return FinanceRecurringRule(
            id = id,
            householdId = householdId,
            createdBy = createdBy,
            categoryId = categoryId,
            categoryName = categoryName,
            kind = kind,
            amountCentavos = amountCentavos,
            currency = currency,
            description = description,
            frequency = frequency,
            intervalCount = intervalCount,
            startOn = startOn,
            nextDueOn = nextDueOn,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun requireAccountOwner(accountId: String, userId: String) {
        if (accountId.isBlank() || accountId != userId) {
            throw RecurringRuleException(RecurringRuleError.PermissionDenied)
        }
    }

    private companion object {
        const val CURRENCY_BOB = "BOB"
        const val ENTITY_TRANSACTION = "transaction"
        const val ENTITY_RECURRING_RULE = "recurring_rule"
        const val OPERATION_UPSERT = "upsert"
        const val MAX_INTERVAL_COUNT = 3650
    }
}
