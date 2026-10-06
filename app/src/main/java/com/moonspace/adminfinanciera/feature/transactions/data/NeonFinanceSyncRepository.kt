package com.moonspace.adminfinanciera.feature.transactions.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.entities.BudgetEntity
import com.moonspace.adminfinanciera.core.database.entities.CategoryEntity
import com.moonspace.adminfinanciera.core.database.entities.DebtEntity
import com.moonspace.adminfinanciera.core.database.entities.DebtPaymentEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.FinanceDatabase
import com.moonspace.adminfinanciera.core.database.entities.RecurringRuleEntity
import com.moonspace.adminfinanciera.core.database.entities.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.database.entities.SyncStateEntity
import com.moonspace.adminfinanciera.core.database.entities.TransactionEntity
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.core.network.NeonDataApiMethod
import com.moonspace.adminfinanciera.core.sync.FinanceSyncRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Downloads the authorized household snapshot, then sends only this account's queued changes. */
class NeonFinanceSyncRepository(
    private val config: NeonApiConfig,
    private val dataApiClient: NeonDataApiClient,
    private val authRepository: AuthRepository,
    private val databases: EncryptedFinanceDatabaseProvider
) : FinanceSyncRepository {
    override suspend fun syncAccount(accountId: String) {
        require(accountId.isNotBlank())
        val accountLock = accountLocks.getOrPut(accountId) { Mutex() }
        accountLock.withLock { syncAccountLocked(accountId) }
    }

    private suspend fun syncAccountLocked(accountId: String) {
        if (!config.isDataApiConfigured) return
        val signedInUser = authRepository.restoreSession().user
        if (signedInUser?.id != accountId) return

        val database = databases.databaseFor(accountId)
        val household = database.householdCacheDao().getHousehold(accountId) ?: return
        val householdId = household.householdId?.takeIf(String::isNotBlank) ?: return

        val membershipRows = requestRows(
            resource = HOUSEHOLD_MEMBERS_RESOURCE,
            query = mapOf(
                "select" to "household_id,user_id,role",
                "user_id" to "eq.$accountId",
                "household_id" to "eq.$householdId"
            ),
            orderColumn = "user_id"
        )
        val membership = membershipRows.firstOrNull { it.optString("user_id") == accountId }
            ?: return
        val role = membership.optString("role")
        if (role != ROLE_ADMIN && role != ROLE_MEMBER) return

        val lastSyncAt = database.syncStateDao().get(accountId)
            ?.takeIf { it.householdId == householdId }
            ?.lastSuccessfulSyncAt
        val now = System.currentTimeMillis()
        val requiresFullResync = lastSyncAt != null && now - lastSyncAt >= STALE_SYNC_AGE_MILLIS

        request(
            resource = "$RPC_RESOURCE/$PRUNE_MARKERS_RPC",
            method = NeonDataApiMethod.POST,
            body = JSONObject().put("p_household_id", householdId)
        )

        val remoteCategories = requestRows(
            resource = CATEGORIES_RESOURCE,
            query = mapOf(
                "select" to CATEGORY_COLUMNS,
                "household_id" to "eq.$householdId"
            )
        ).map { it.toCategory(accountId) }
        val remoteBudgets = requestRows(
            resource = BUDGETS_RESOURCE,
            query = buildMap {
                put("select", BUDGET_COLUMNS)
                put("household_id", "eq.$householdId")
                if (role == ROLE_MEMBER) put("user_id", "eq.$accountId")
            }
        ).map { it.toBudget(accountId) }
        val remoteRecurringRules = requestRows(
            resource = RECURRING_RULES_RESOURCE,
            query = buildMap {
                put("select", RECURRING_RULE_COLUMNS)
                put("household_id", "eq.$householdId")
                if (role == ROLE_MEMBER) put("created_by", "eq.$accountId")
            }
        ).map { it.toRecurringRule(accountId) }
        val remoteDebts = requestRows(
            resource = DEBTS_RESOURCE,
            query = buildMap {
                put("select", DEBT_COLUMNS)
                put("household_id", "eq.$householdId")
                if (role == ROLE_MEMBER) put("created_by", "eq.$accountId")
            }
        ).map { it.toDebt(accountId) }
        val remoteDebtPayments = requestRows(
            resource = DEBT_PAYMENTS_RESOURCE,
            query = buildMap {
                put("select", DEBT_PAYMENT_COLUMNS)
                put("household_id", "eq.$householdId")
                if (role == ROLE_MEMBER) put("created_by", "eq.$accountId")
            }
        ).map { it.toDebtPayment(accountId) }
        val remoteMarkers = requestRows(
            resource = DELETION_MARKERS_RESOURCE,
            query = mapOf(
                "select" to DELETION_MARKER_COLUMNS,
                "household_id" to "eq.$householdId"
            ),
            orderColumn = "transaction_id"
        )
        val transactionQuery = buildMap {
            put("select", TRANSACTION_COLUMNS)
            put("household_id", "eq.$householdId")
            if (role == ROLE_MEMBER) put("created_by", "eq.$accountId")
        }
        val remoteTransactions = requestRows(
            resource = TRANSACTIONS_RESOURCE,
            query = transactionQuery
        ).map { it.toTransaction(accountId) }

        applyRemoteSnapshot(
            database = database,
            accountId = accountId,
            householdId = householdId,
            role = role,
            categories = remoteCategories,
            budgets = remoteBudgets,
            recurringRules = remoteRecurringRules,
            debts = remoteDebts,
            debtPayments = remoteDebtPayments,
            transactions = remoteTransactions,
            deletedTransactionIds = remoteMarkers.mapNotNull { it.optString("transaction_id").takeIf(String::isNotBlank) }.toSet(),
            previousSuccessfulSyncAt = lastSyncAt,
            requiresFullResync = requiresFullResync,
            syncedAt = now
        )
        sendPendingOperations(
            database = database,
            accountId = accountId,
            householdId = householdId,
            remoteCategoryIds = remoteCategories.mapTo(mutableSetOf(), CategoryEntity::id),
            remoteBudgetIds = remoteBudgets.mapTo(mutableSetOf(), BudgetEntity::id),
            remoteRecurringRuleIds = remoteRecurringRules.mapTo(mutableSetOf(), RecurringRuleEntity::id),
            remoteDebtIds = remoteDebts.mapTo(mutableSetOf(), DebtEntity::id),
            remoteDebtPaymentIds = remoteDebtPayments.mapTo(mutableSetOf(), DebtPaymentEntity::id)
        )
        database.syncStateDao().save(
            SyncStateEntity(
                accountId = accountId,
                householdId = householdId,
                lastSuccessfulSyncAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun applyRemoteSnapshot(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        role: String,
        categories: List<CategoryEntity>,
        budgets: List<BudgetEntity>,
        recurringRules: List<RecurringRuleEntity>,
        debts: List<DebtEntity>,
        debtPayments: List<DebtPaymentEntity>,
        transactions: List<TransactionEntity>,
        deletedTransactionIds: Set<String>,
        previousSuccessfulSyncAt: Long?,
        requiresFullResync: Boolean,
        syncedAt: Long
    ) {
        val categoryDao = database.categoryDao()
        val budgetDao = database.budgetDao()
        val recurringRuleDao = database.recurringRuleDao()
        val debtDao = database.debtDao()
        val debtPaymentDao = database.debtPaymentDao()
        val transactionDao = database.transactionDao()
        val outboxDao = database.syncOutboxDao()
        val deletionMarkerDao = database.transactionDeletionMarkerDao()
        val remoteCategoryById = categories.associateBy(CategoryEntity::id)
        val remoteBudgetById = budgets.associateBy(BudgetEntity::id)
        val remoteRecurringRuleById = recurringRules.associateBy(RecurringRuleEntity::id)
        val remoteDebtById = debts.associateBy(DebtEntity::id)
        val remoteDebtPaymentById = debtPayments.associateBy(DebtPaymentEntity::id)
        val remoteTransactionById = transactions.associateBy(TransactionEntity::id)

        database.withTransaction {
            val cachedHousehold = database.householdCacheDao().getHousehold(accountId)
            if (cachedHousehold?.householdId != householdId) return@withTransaction
            database.householdCacheDao().saveHousehold(
                cachedHousehold.copy(currentUserRole = role, updatedAt = syncedAt)
            )

            if (role == ROLE_MEMBER) {
                transactionDao.removeOthers(householdId, accountId)
                recurringRuleDao.removeOthers(householdId, accountId)
                budgetDao.removeOthers(householdId, accountId)
                debtDao.removeOthers(householdId, accountId)
                debtPaymentDao.removeOthers(householdId, accountId)
            }

            val localBudgetsBeforeImport = budgetDao.allForHousehold(householdId)
            val localRecurringRulesBeforeImport = recurringRuleDao.allForHousehold(householdId)
            val localDebtsBeforeImport = debtDao.allForHousehold(householdId)
            val localDebtPaymentsBeforeImport = debtPaymentDao.allForHousehold(householdId)
            val localTransactionsBeforeImport = transactionDao.allForHousehold(householdId)
            val queuedBeforeImport = outboxDao.pending(accountId).associateBy { it.entityType to it.entityId }

            deletedTransactionIds.forEach { transactionId ->
                transactionDao.delete(householdId, transactionId)
                outboxDao.removeForEntity(accountId, ENTITY_TRANSACTION, transactionId)
                deletionMarkerDao.remove(transactionId)
            }

            categories.forEach { remoteCategory ->
                val pendingCategory = queuedBeforeImport[ENTITY_CATEGORY to remoteCategory.id]
                if (pendingCategory == null || role == ROLE_MEMBER) {
                    if (role == ROLE_MEMBER && pendingCategory != null) {
                        outboxDao.removeById(pendingCategory.id)
                    }
                    categoryDao.upsert(remoteCategory)
                }
            }

            budgets.forEach { remoteBudget ->
                if (queuedBeforeImport[ENTITY_BUDGET to remoteBudget.id] == null) {
                    budgetDao.save(remoteBudget)
                }
            }

            recurringRules.forEach { remoteRule ->
                if (queuedBeforeImport[ENTITY_RECURRING_RULE to remoteRule.id] == null) {
                    recurringRuleDao.save(remoteRule)
                }
            }

            debts.forEach { remoteDebt ->
                if (queuedBeforeImport[ENTITY_DEBT to remoteDebt.id] == null) {
                    debtDao.save(remoteDebt)
                }
            }

            debtPayments.forEach { remotePayment ->
                val parentOperation = queuedBeforeImport[ENTITY_DEBT to remotePayment.debtId]
                if (parentOperation?.operation == OPERATION_DELETE ||
                    debtDao.findById(householdId, remotePayment.debtId) == null
                ) {
                    return@forEach
                }
                if (queuedBeforeImport[ENTITY_DEBT_PAYMENT to remotePayment.id] == null) {
                    debtPaymentDao.save(remotePayment)
                }
            }

            localDebtsBeforeImport.forEach { localDebt ->
                if (localDebt.id in remoteDebtById) return@forEach
                val pending = queuedBeforeImport[ENTITY_DEBT to localDebt.id]
                if (pending == null) {
                    localDebtPaymentsBeforeImport.filter { it.debtId == localDebt.id }.forEach { payment ->
                        outboxDao.removeForEntity(accountId, ENTITY_DEBT_PAYMENT, payment.id)
                    }
                    debtDao.deleteById(householdId, localDebt.id)
                    return@forEach
                }
                val wasBasedOnPreviousSnapshot = previousSuccessfulSyncAt != null &&
                    localDebt.updatedAt <= previousSuccessfulSyncAt
                if (localDebt.isRemoteBacked && wasBasedOnPreviousSnapshot &&
                    pending.operation == OPERATION_UPSERT
                ) {
                    localDebtPaymentsBeforeImport.filter { it.debtId == localDebt.id }.forEach { payment ->
                        outboxDao.removeForEntity(accountId, ENTITY_DEBT_PAYMENT, payment.id)
                    }
                    debtDao.deleteById(householdId, localDebt.id)
                    outboxDao.removeForEntity(accountId, ENTITY_DEBT, localDebt.id)
                }
            }

            localDebtPaymentsBeforeImport.forEach { localPayment ->
                if (localPayment.id in remoteDebtPaymentById) return@forEach
                val pending = queuedBeforeImport[ENTITY_DEBT_PAYMENT to localPayment.id]
                if (pending == null) {
                    debtPaymentDao.deleteById(householdId, localPayment.id)
                    return@forEach
                }
                val wasBasedOnPreviousSnapshot = previousSuccessfulSyncAt != null &&
                    localPayment.updatedAt <= previousSuccessfulSyncAt
                if (localPayment.isRemoteBacked && wasBasedOnPreviousSnapshot &&
                    pending.operation == OPERATION_UPSERT
                ) {
                    debtPaymentDao.deleteById(householdId, localPayment.id)
                    outboxDao.removeForEntity(accountId, ENTITY_DEBT_PAYMENT, localPayment.id)
                }
            }

            localRecurringRulesBeforeImport.forEach { localRule ->
                if (localRule.id in remoteRecurringRuleById) return@forEach
                val pending = queuedBeforeImport[ENTITY_RECURRING_RULE to localRule.id]
                if (pending == null) {
                    recurringRuleDao.deleteById(householdId, localRule.id)
                    return@forEach
                }
                val wasBasedOnPreviousSnapshot = previousSuccessfulSyncAt != null &&
                    localRule.updatedAt <= previousSuccessfulSyncAt
                if (localRule.isRemoteBacked && wasBasedOnPreviousSnapshot &&
                    pending.operation == OPERATION_UPSERT
                ) {
                    recurringRuleDao.deleteById(householdId, localRule.id)
                    outboxDao.removeForEntity(accountId, ENTITY_RECURRING_RULE, localRule.id)
                }
            }

            localBudgetsBeforeImport.forEach { localBudget ->
                if (localBudget.id in remoteBudgetById) return@forEach
                val pending = queuedBeforeImport[ENTITY_BUDGET to localBudget.id]
                if (pending == null) {
                    budgetDao.deleteById(householdId, localBudget.id)
                    return@forEach
                }

                val wasBasedOnPreviousSnapshot = previousSuccessfulSyncAt != null &&
                    localBudget.updatedAt <= previousSuccessfulSyncAt
                if (localBudget.isRemoteBacked && wasBasedOnPreviousSnapshot &&
                    pending.operation == OPERATION_UPSERT
                ) {
                    budgetDao.deleteById(householdId, localBudget.id)
                    outboxDao.removeForEntity(accountId, ENTITY_BUDGET, localBudget.id)
                }
            }

            remoteTransactionById.values
                .filterNot { it.id in deletedTransactionIds }
                .forEach { remoteTransaction ->
                    val pending = queuedBeforeImport[ENTITY_TRANSACTION to remoteTransaction.id]
                    if (pending == null) transactionDao.save(remoteTransaction)
                }

            localTransactionsBeforeImport.forEach { localTransaction ->
                val id = localTransaction.id
                if (id in deletedTransactionIds || id in remoteTransactionById) return@forEach
                val pending = queuedBeforeImport[ENTITY_TRANSACTION to id]
                if (pending == null) {
                    transactionDao.delete(householdId, id)
                    return@forEach
                }

                val wasBasedOnPreviousSnapshot = previousSuccessfulSyncAt != null &&
                    localTransaction.createdAt <= previousSuccessfulSyncAt
                val wasRemoteBacked = localTransaction.isRemoteBacked ||
                    (requiresFullResync && wasBasedOnPreviousSnapshot)
                if (wasRemoteBacked && pending.operation == OPERATION_UPSERT) {
                    transactionDao.delete(householdId, id)
                    outboxDao.removeForEntity(accountId, ENTITY_TRANSACTION, id)
                    deletionMarkerDao.remove(id)
                }
            }

            categoryDao.allForHousehold(householdId).forEach { localCategory ->
                if (localCategory.id in remoteCategoryById) return@forEach
                val pending = queuedBeforeImport[ENTITY_CATEGORY to localCategory.id]
                if (pending != null && role == ROLE_ADMIN) return@forEach
                if (pending != null) outboxDao.removeById(pending.id)
                if (categoryDao.countForCategory(householdId, localCategory.id) == 0) {
                    categoryDao.deleteById(householdId, localCategory.id)
                }
            }
        }
    }

    private suspend fun sendPendingOperations(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        remoteCategoryIds: MutableSet<String>,
        remoteBudgetIds: MutableSet<String>,
        remoteRecurringRuleIds: MutableSet<String>,
        remoteDebtIds: MutableSet<String>,
        remoteDebtPaymentIds: MutableSet<String>
    ) {
        val outboxDao = database.syncOutboxDao()
        var sentCount = 0
        while (true) {
            val pending = outboxDao.pending(accountId)
                .sortedWith(
                    compareBy<SyncOutboxEntity> {
                        when (it.entityType) {
                            ENTITY_CATEGORY -> 0
                            ENTITY_RECURRING_RULE -> 1
                            ENTITY_BUDGET -> 2
                            ENTITY_DEBT -> 3
                            ENTITY_DEBT_PAYMENT -> 4
                            ENTITY_TRANSACTION -> 5
                            else -> 6
                        }
                    }.thenBy(SyncOutboxEntity::enqueuedAt)
                )
            if (pending.isEmpty()) return
            if (sentCount >= MAX_OPERATIONS_PER_RUN) {
                throw IOException("The finance sync batch limit was reached; remaining changes will be retried.")
            }

            pending.forEach { operation ->
                if (operation.accountId != accountId) return@forEach
                when (operation.entityType) {
                    ENTITY_CATEGORY -> sendCategoryOperation(database, householdId, operation, remoteCategoryIds)
                    ENTITY_BUDGET -> sendBudgetOperation(database, accountId, householdId, operation, remoteBudgetIds)
                    ENTITY_RECURRING_RULE -> sendRecurringRuleOperation(
                        database,
                        accountId,
                        householdId,
                        operation,
                        remoteRecurringRuleIds
                    )
                    ENTITY_TRANSACTION -> sendTransactionOperation(database, accountId, householdId, operation)
                    ENTITY_DEBT -> sendDebtOperation(
                        database, accountId, householdId, operation, remoteDebtIds
                    )
                    ENTITY_DEBT_PAYMENT -> sendDebtPaymentOperation(
                        database, accountId, householdId, operation, remoteDebtPaymentIds
                    )
                    else -> throw IOException("Unsupported queued finance entity type.")
                }
                sentCount += 1
                if (sentCount >= MAX_OPERATIONS_PER_RUN && outboxDao.pending(accountId).isNotEmpty()) {
                    throw IOException("The finance sync batch limit was reached; remaining changes will be retried.")
                }
            }
        }
    }

    private suspend fun sendCategoryOperation(
        database: FinanceDatabase,
        householdId: String,
        operation: SyncOutboxEntity,
        remoteCategoryIds: MutableSet<String>
    ) {
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported category operation in the finance sync queue.")
        }
        val category = database.categoryDao().findById(householdId, operation.entityId)
        if (category == null) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        if (category.id in remoteCategoryIds) {
            request(
                resource = CATEGORIES_RESOURCE,
                method = NeonDataApiMethod.PATCH,
                query = mapOf(
                    "id" to "eq.${category.id}",
                    "household_id" to "eq.$householdId"
                ),
                body = category.toUpdateJson()
            )
        } else {
            request(
                resource = CATEGORIES_RESOURCE,
                method = NeonDataApiMethod.POST,
                body = category.toJson()
            )
            remoteCategoryIds += category.id
        }
        database.syncOutboxDao().removeById(operation.id)
    }

    private suspend fun sendBudgetOperation(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        operation: SyncOutboxEntity,
        remoteBudgetIds: MutableSet<String>
    ) {
        val budgetDao = database.budgetDao()
        if (operation.operation == OPERATION_DELETE) {
            request(
                resource = BUDGETS_RESOURCE,
                method = NeonDataApiMethod.DELETE,
                query = mapOf(
                    "id" to "eq.${operation.entityId}",
                    "household_id" to "eq.$householdId",
                    "user_id" to "eq.$accountId"
                )
            )
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported budget operation in the finance sync queue.")
        }

        val budget = budgetDao.findById(householdId, operation.entityId)
        if (budget == null || budget.userId != accountId) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        val alreadyRemote = budget.id in remoteBudgetIds
        request(
            resource = BUDGETS_RESOURCE,
            method = if (alreadyRemote) NeonDataApiMethod.PATCH else NeonDataApiMethod.POST,
            query = if (alreadyRemote) {
                mapOf(
                    "id" to "eq.${budget.id}",
                    "household_id" to "eq.$householdId",
                    "user_id" to "eq.$accountId"
                )
            } else {
                mapOf("on_conflict" to "id")
            },
            body = budget.toJson(),
            prefer = if (alreadyRemote) null else UPSERT_PREFER
        )
        remoteBudgetIds += budget.id
        database.withTransaction {
            budgetDao.markRemoteBacked(householdId, budget.id)
            database.syncOutboxDao().removeById(operation.id)
        }
    }

    private suspend fun sendRecurringRuleOperation(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        operation: SyncOutboxEntity,
        remoteRuleIds: MutableSet<String>
    ) {
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported recurring rule operation in the finance sync queue.")
        }
        val ruleDao = database.recurringRuleDao()
        val rule = ruleDao.findById(householdId, operation.entityId)
        if (rule == null || rule.createdBy != accountId) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        val alreadyRemote = rule.id in remoteRuleIds
        request(
            resource = RECURRING_RULES_RESOURCE,
            method = if (alreadyRemote) NeonDataApiMethod.PATCH else NeonDataApiMethod.POST,
            query = if (alreadyRemote) {
                mapOf(
                    "id" to "eq.${rule.id}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            } else {
                mapOf("on_conflict" to "id")
            },
            body = rule.toJson(),
            prefer = if (alreadyRemote) null else UPSERT_PREFER
        )
        remoteRuleIds += rule.id
        database.withTransaction {
            ruleDao.markRemoteBacked(householdId, rule.id)
            database.syncOutboxDao().removeById(operation.id)
        }
    }

    private suspend fun sendDebtOperation(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        operation: SyncOutboxEntity,
        remoteDebtIds: MutableSet<String>
    ) {
        if (operation.operation == OPERATION_DELETE) {
            request(
                resource = DEBTS_RESOURCE,
                method = NeonDataApiMethod.DELETE,
                query = mapOf(
                    "id" to "eq.${operation.entityId}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            )
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported debt operation in the finance sync queue.")
        }

        val debt = database.debtDao().findById(householdId, operation.entityId)
        if (debt == null || debt.createdBy != accountId) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        val alreadyRemote = debt.id in remoteDebtIds
        request(
            resource = DEBTS_RESOURCE,
            method = if (alreadyRemote) NeonDataApiMethod.PATCH else NeonDataApiMethod.POST,
            query = if (alreadyRemote) {
                mapOf(
                    "id" to "eq.${debt.id}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            } else {
                mapOf("on_conflict" to "id")
            },
            body = debt.toJson(),
            prefer = if (alreadyRemote) null else UPSERT_PREFER
        )
        remoteDebtIds += debt.id
        database.withTransaction {
            database.debtDao().markRemoteBacked(householdId, debt.id)
            database.syncOutboxDao().removeById(operation.id)
        }
    }

    private suspend fun sendDebtPaymentOperation(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        operation: SyncOutboxEntity,
        remotePaymentIds: MutableSet<String>
    ) {
        if (operation.operation == OPERATION_DELETE) {
            request(
                resource = DEBT_PAYMENTS_RESOURCE,
                method = NeonDataApiMethod.DELETE,
                query = mapOf(
                    "id" to "eq.${operation.entityId}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            )
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported debt payment operation in the finance sync queue.")
        }

        val payment = database.debtPaymentDao().findById(householdId, operation.entityId)
        val debt = payment?.let { database.debtDao().findById(householdId, it.debtId) }
        if (payment == null || payment.createdBy != accountId || debt?.createdBy != accountId) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        val alreadyRemote = payment.id in remotePaymentIds
        request(
            resource = DEBT_PAYMENTS_RESOURCE,
            method = if (alreadyRemote) NeonDataApiMethod.PATCH else NeonDataApiMethod.POST,
            query = if (alreadyRemote) {
                mapOf(
                    "id" to "eq.${payment.id}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            } else {
                mapOf("on_conflict" to "id")
            },
            body = payment.toJson(),
            prefer = if (alreadyRemote) null else UPSERT_PREFER
        )
        remotePaymentIds += payment.id
        database.withTransaction {
            database.debtPaymentDao().markRemoteBacked(householdId, payment.id)
            database.syncOutboxDao().removeById(operation.id)
        }
    }

    private suspend fun sendTransactionOperation(
        database: FinanceDatabase,
        accountId: String,
        householdId: String,
        operation: SyncOutboxEntity
    ) {
        if (operation.operation == OPERATION_DELETE) {
            request(
                resource = TRANSACTIONS_RESOURCE,
                method = NeonDataApiMethod.DELETE,
                query = mapOf(
                    "id" to "eq.${operation.entityId}",
                    "household_id" to "eq.$householdId",
                    "created_by" to "eq.$accountId"
                )
            )
            request(
                resource = DELETION_MARKERS_RESOURCE,
                method = NeonDataApiMethod.POST,
                query = mapOf("on_conflict" to "transaction_id"),
                body = JSONObject()
                    .put("transaction_id", operation.entityId)
                    .put("household_id", householdId)
                    .put("created_by", accountId),
                prefer = UPSERT_PREFER
            )
            database.withTransaction {
                database.transactionDeletionMarkerDao().remove(operation.entityId)
                database.syncOutboxDao().removeById(operation.id)
            }
            return
        }
        if (operation.operation != OPERATION_UPSERT) {
            throw IOException("Unsupported transaction operation in the finance sync queue.")
        }

        val transaction = database.transactionDao().findById(householdId, operation.entityId)
        if (transaction == null) {
            database.syncOutboxDao().removeById(operation.id)
            return
        }
        request(
            resource = TRANSACTIONS_RESOURCE,
            method = NeonDataApiMethod.POST,
            query = mapOf("on_conflict" to "id"),
            body = transaction.toJson(),
            prefer = UPSERT_PREFER
        )
        database.withTransaction {
            database.transactionDao().markRemoteBacked(householdId, operation.entityId)
            database.syncOutboxDao().removeById(operation.id)
        }
    }

    private suspend fun requestRows(
        resource: String,
        query: Map<String, String>,
        orderColumn: String = "id"
    ): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        var offset = 0
        while (true) {
            val page = request(
                resource = resource,
                method = NeonDataApiMethod.GET,
                query = query + mapOf(
                    "order" to "$orderColumn.asc",
                    "limit" to PAGE_SIZE.toString(),
                    "offset" to offset.toString()
                )
            ).body
            val pageRows = try {
                val array = JSONArray(page)
                List(array.length()) { index -> array.getJSONObject(index) }
            } catch (error: Exception) {
                throw IOException("Neon Data API returned an invalid finance snapshot.", error)
            }
            rows += pageRows
            if (pageRows.size < PAGE_SIZE) return rows
            offset += PAGE_SIZE
        }
    }

    private suspend fun request(
        resource: String,
        method: NeonDataApiMethod,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        prefer: String? = null
    ): com.moonspace.adminfinanciera.core.network.NeonDataApiResponse {
        val response = dataApiClient.request(resource, method, query, body, prefer)
        if (!response.isSuccessful) {
            throw IOException("Neon Data API request failed with HTTP ${response.statusCode}.")
        }
        return response
    }

    private fun JSONObject.toCategory(accountId: String): CategoryEntity {
        val name = getString("name")
        return CategoryEntity(
            id = getString("id"),
            accountId = accountId,
            householdId = getString("household_id"),
            name = name,
            nameKey = name.trim().lowercase(Locale.ROOT),
            kind = getString("kind"),
            isActive = getBoolean("is_active"),
            createdBy = optNullableString("created_by"),
            createdAt = getInstantMillis("created_at"),
            updatedAt = getInstantMillis("updated_at")
        )
    }

    private fun JSONObject.toBudget(accountId: String): BudgetEntity {
        val categoryId = optNullableString("category_id")
        return BudgetEntity(
            id = getString("id"),
            accountId = accountId,
            householdId = getString("household_id"),
            userId = getString("user_id"),
            categoryId = categoryId,
            categoryKey = categoryId ?: GENERAL_CATEGORY_KEY,
            monthStart = getString("month_start"),
            amountCentavos = getBigDecimal("amount_limit").movePointRight(2).longValueExact(),
            createdAt = getInstantMillis("created_at"),
            updatedAt = getInstantMillis("updated_at"),
            isRemoteBacked = true
        )
    }

    private fun JSONObject.toRecurringRule(accountId: String): RecurringRuleEntity = RecurringRuleEntity(
        id = getString("id"),
        accountId = accountId,
        householdId = getString("household_id"),
        createdBy = getString("created_by"),
        categoryId = getString("category_id"),
        kind = getString("kind"),
        amountCentavos = getBigDecimal("amount").movePointRight(2).longValueExact(),
        currency = getString("currency").trim(),
        description = optNullableString("description"),
        frequency = getString("frequency"),
        intervalCount = getInt("interval_count"),
        startOn = getString("start_on"),
        nextDueOn = getString("next_due_on"),
        isActive = getBoolean("is_active"),
        createdAt = getInstantMillis("created_at"),
        updatedAt = getInstantMillis("updated_at"),
        isRemoteBacked = true
    )

    private fun JSONObject.toDebt(accountId: String): DebtEntity {
        val direction = getString("direction")
        if (direction != "owed_by_me" && direction != "owed_to_me") {
            throw IOException("Neon Data API returned an invalid debt direction.")
        }
        return DebtEntity(
            id = getString("id"),
            accountId = accountId,
            householdId = getString("household_id"),
            createdBy = getString("created_by"),
            direction = direction,
            counterparty = getString("counterparty"),
            description = optNullableString("description"),
            principalCentavos = getBigDecimal("principal_amount").movePointRight(2).longValueExact(),
            currency = getString("currency").trim(),
            openedOn = getString("opened_on"),
            dueOn = optNullableString("due_on"),
            createdAt = getInstantMillis("created_at"),
            updatedAt = getInstantMillis("updated_at"),
            isRemoteBacked = true
        )
    }

    private fun JSONObject.toDebtPayment(accountId: String): DebtPaymentEntity = DebtPaymentEntity(
        id = getString("id"),
        accountId = accountId,
        householdId = getString("household_id"),
        debtId = getString("debt_id"),
        createdBy = getString("created_by"),
        amountCentavos = getBigDecimal("amount").movePointRight(2).longValueExact(),
        paidOn = getString("paid_on"),
        note = optNullableString("note"),
        createdAt = getInstantMillis("created_at"),
        updatedAt = getInstantMillis("updated_at"),
        isRemoteBacked = true
    )

    private fun JSONObject.toTransaction(accountId: String): TransactionEntity = TransactionEntity(
        id = getString("id"),
        accountId = accountId,
        householdId = getString("household_id"),
        createdBy = getString("created_by"),
        categoryId = getString("category_id"),
        kind = getString("kind"),
        amountCentavos = getBigDecimal("amount").movePointRight(2).longValueExact(),
        currency = getString("currency").trim(),
        occurredOn = getString("occurred_on"),
        description = optNullableString("description"),
        createdAt = getInstantMillis("created_at"),
        updatedAt = getInstantMillis("updated_at"),
        isRemoteBacked = true,
        sourceRecurringRuleId = optNullableString("source_recurring_rule_id"),
        scheduledFor = optNullableString("scheduled_for")
    )

    private fun JSONObject.getBigDecimal(name: String): BigDecimal = try {
        BigDecimal(get(name).toString())
    } catch (error: Exception) {
        throw IOException("Neon Data API returned an invalid transaction amount.", error)
    }

    private fun JSONObject.getInstantMillis(name: String): Long {
        val timestamp = getString(name)
        val match = TIMESTAMP_PATTERN.matchEntire(timestamp)
            ?: throw IOException("Neon Data API returned an invalid timestamp.")
        val fractionalSeconds = match.groupValues[2].padEnd(3, '0').take(3)
        val timezone = match.groupValues[3].let { value ->
            when {
                value == "Z" || value.length == 6 -> value
                value.length == 5 -> value.substring(0, 3) + ":" + value.substring(3)
                else -> throw IOException("Neon Data API returned an invalid timestamp.")
            }
        }
        val normalized = "${match.groupValues[1]}.$fractionalSeconds$timezone"
        return try {
            timestampParser().parse(normalized)?.time
                ?: throw IOException("Neon Data API returned an invalid timestamp.")
        } catch (error: Exception) {
            throw IOException("Neon Data API returned an invalid timestamp.", error)
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private fun CategoryEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("created_by", createdBy ?: JSONObject.NULL)
        .put("name", name)
        .put("kind", kind)
        .put("is_active", isActive)
        .put("created_at", formatTimestamp(createdAt))
        .put("updated_at", formatTimestamp(updatedAt))

    private fun CategoryEntity.toUpdateJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("is_active", isActive)

    private fun TransactionEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("created_by", createdBy)
        .put("category_id", categoryId)
        .put("kind", kind)
        .put("amount", BigDecimal.valueOf(amountCentavos, 2).toPlainString())
        .put("currency", currency)
        .put("occurred_on", occurredOn)
        .put("description", description ?: JSONObject.NULL)
        .put("source_recurring_rule_id", sourceRecurringRuleId ?: JSONObject.NULL)
        .put("scheduled_for", scheduledFor ?: JSONObject.NULL)
        .put("created_at", formatTimestamp(createdAt))
        .put("updated_at", formatTimestamp(updatedAt))

    private fun BudgetEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("user_id", userId)
        .put("category_id", categoryId ?: JSONObject.NULL)
        .put("month_start", monthStart)
        .put("amount_limit", BigDecimal.valueOf(amountCentavos, 2).toPlainString())

    private fun RecurringRuleEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("created_by", createdBy)
        .put("category_id", categoryId)
        .put("kind", kind)
        .put("amount", BigDecimal.valueOf(amountCentavos, 2).toPlainString())
        .put("currency", currency)
        .put("description", description ?: JSONObject.NULL)
        .put("frequency", frequency)
        .put("interval_count", intervalCount)
        .put("start_on", startOn)
        .put("next_due_on", nextDueOn)
        .put("is_active", isActive)

    private fun DebtEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("created_by", createdBy)
        .put("direction", direction)
        .put("counterparty", counterparty)
        .put("description", description ?: JSONObject.NULL)
        .put("principal_amount", BigDecimal.valueOf(principalCentavos, 2).toPlainString())
        .put("currency", currency)
        .put("opened_on", openedOn)
        .put("due_on", dueOn ?: JSONObject.NULL)
        .put("created_at", formatTimestamp(createdAt))
        .put("updated_at", formatTimestamp(updatedAt))

    private fun DebtPaymentEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("household_id", householdId)
        .put("debt_id", debtId)
        .put("created_by", createdBy)
        .put("amount", BigDecimal.valueOf(amountCentavos, 2).toPlainString())
        .put("paid_on", paidOn)
        .put("note", note ?: JSONObject.NULL)
        .put("created_at", formatTimestamp(createdAt))
        .put("updated_at", formatTimestamp(updatedAt))

    private fun formatTimestamp(timestampMillis: Long): String = timestampFormatter().format(Date(timestampMillis))

    private fun timestampParser() = SimpleDateFormat(TIMESTAMP_PATTERN_FORMAT, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun timestampFormatter() = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    companion object {
        private const val HOUSEHOLD_MEMBERS_RESOURCE = "household_members"
        private const val CATEGORIES_RESOURCE = "categories"
        private const val BUDGETS_RESOURCE = "budgets"
        private const val RECURRING_RULES_RESOURCE = "recurring_rules"
        private const val DEBTS_RESOURCE = "debts"
        private const val DEBT_PAYMENTS_RESOURCE = "debt_payments"
        private const val TRANSACTIONS_RESOURCE = "transactions"
        private const val DELETION_MARKERS_RESOURCE = "transaction_deletion_markers"
        private const val RPC_RESOURCE = "rpc"
        private const val PRUNE_MARKERS_RPC = "prune_expired_transaction_deletion_markers"
        private const val CATEGORY_COLUMNS = "id,household_id,created_by,name,kind,is_active,created_at,updated_at"
        private const val BUDGET_COLUMNS = "id,household_id,user_id,category_id,month_start,amount_limit::text,created_at,updated_at"
        private const val RECURRING_RULE_COLUMNS = "id,household_id,created_by,category_id,kind,amount::text,currency,description,frequency,interval_count,start_on,next_due_on,is_active,created_at,updated_at"
        private const val DEBT_COLUMNS = "id,household_id,created_by,direction,counterparty,description,principal_amount::text,currency,opened_on,due_on,created_at,updated_at"
        private const val DEBT_PAYMENT_COLUMNS = "id,household_id,debt_id,created_by,amount::text,paid_on,note,created_at,updated_at"
        private const val TRANSACTION_COLUMNS = "id,household_id,created_by,category_id,source_recurring_rule_id,scheduled_for,kind,amount::text,currency,occurred_on,description,created_at,updated_at"
        private const val DELETION_MARKER_COLUMNS = "transaction_id,household_id,created_by,deleted_at"
        private const val ENTITY_CATEGORY = "category"
        private const val ENTITY_BUDGET = "budget"
        private const val ENTITY_RECURRING_RULE = "recurring_rule"
        private const val ENTITY_DEBT = "debt"
        private const val ENTITY_DEBT_PAYMENT = "debt_payment"
        private const val ENTITY_TRANSACTION = "transaction"
        private const val OPERATION_UPSERT = "upsert"
        private const val OPERATION_DELETE = "delete"
        private const val ROLE_ADMIN = "admin"
        private const val ROLE_MEMBER = "member"
        private const val GENERAL_CATEGORY_KEY = "__general__"
        private const val UPSERT_PREFER = "resolution=merge-duplicates,return=minimal"
        private const val PAGE_SIZE = 500
        private const val MAX_OPERATIONS_PER_RUN = 500
        private const val STALE_SYNC_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
        private const val TIMESTAMP_PATTERN_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        val TIMESTAMP_PATTERN = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})$")
        private val accountLocks = ConcurrentHashMap<String, Mutex>()
    }
}
