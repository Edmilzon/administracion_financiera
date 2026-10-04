package com.moonspace.adminfinanciera.feature.transactions.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.CategoryEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.FinanceDatabase
import com.moonspace.adminfinanciera.core.database.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.database.SyncStateEntity
import com.moonspace.adminfinanciera.core.database.TransactionEntity
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.core.network.NeonDataApiMethod
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
) {
    suspend fun syncAccount(accountId: String) {
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
            remoteCategoryIds = remoteCategories.mapTo(mutableSetOf(), CategoryEntity::id)
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
        transactions: List<TransactionEntity>,
        deletedTransactionIds: Set<String>,
        previousSuccessfulSyncAt: Long?,
        requiresFullResync: Boolean,
        syncedAt: Long
    ) {
        val categoryDao = database.categoryDao()
        val transactionDao = database.transactionDao()
        val outboxDao = database.syncOutboxDao()
        val deletionMarkerDao = database.transactionDeletionMarkerDao()
        val remoteCategoryById = categories.associateBy(CategoryEntity::id)
        val remoteTransactionById = transactions.associateBy(TransactionEntity::id)

        database.withTransaction {
            val cachedHousehold = database.householdCacheDao().getHousehold(accountId)
            if (cachedHousehold?.householdId != householdId) return@withTransaction
            database.householdCacheDao().saveHousehold(
                cachedHousehold.copy(currentUserRole = role, updatedAt = syncedAt)
            )

            if (role == ROLE_MEMBER) {
                transactionDao.removeOthers(householdId, accountId)
            }

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
        remoteCategoryIds: MutableSet<String>
    ) {
        val outboxDao = database.syncOutboxDao()
        var sentCount = 0
        while (true) {
            val pending = outboxDao.pending(accountId)
                .sortedWith(compareBy<SyncOutboxEntity>({ if (it.entityType == ENTITY_CATEGORY) 0 else 1 }, SyncOutboxEntity::enqueuedAt))
            if (pending.isEmpty()) return
            if (sentCount >= MAX_OPERATIONS_PER_RUN) {
                throw IOException("The finance sync batch limit was reached; remaining changes will be retried.")
            }

            pending.forEach { operation ->
                if (operation.accountId != accountId) return@forEach
                when (operation.entityType) {
                    ENTITY_CATEGORY -> sendCategoryOperation(database, householdId, operation, remoteCategoryIds)
                    ENTITY_TRANSACTION -> sendTransactionOperation(database, accountId, householdId, operation)
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
        isRemoteBacked = true
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
        private const val TRANSACTIONS_RESOURCE = "transactions"
        private const val DELETION_MARKERS_RESOURCE = "transaction_deletion_markers"
        private const val RPC_RESOURCE = "rpc"
        private const val PRUNE_MARKERS_RPC = "prune_expired_transaction_deletion_markers"
        private const val CATEGORY_COLUMNS = "id,household_id,created_by,name,kind,is_active,created_at,updated_at"
        private const val TRANSACTION_COLUMNS = "id,household_id,created_by,category_id,kind,amount::text,currency,occurred_on,description,created_at,updated_at"
        private const val DELETION_MARKER_COLUMNS = "transaction_id,household_id,created_by,deleted_at"
        private const val ENTITY_CATEGORY = "category"
        private const val ENTITY_TRANSACTION = "transaction"
        private const val OPERATION_UPSERT = "upsert"
        private const val OPERATION_DELETE = "delete"
        private const val ROLE_ADMIN = "admin"
        private const val ROLE_MEMBER = "member"
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
