package com.moonspace.adminfinanciera.feature.transactions.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.CategoryEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.database.TransactionDeletionMarkerEntity
import com.moonspace.adminfinanciera.core.database.TransactionEntity
import com.moonspace.adminfinanciera.feature.transactions.domain.CategoryDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceDataError
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceDataException
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class RoomFinancialRepository(
    private val databases: EncryptedFinanceDatabaseProvider
) : FinancialRepository {
    override fun observeCategories(
        accountId: String,
        householdId: String,
        includeInactive: Boolean
    ): Flow<List<FinanceCategory>> {
        val dao = databases.databaseFor(accountId).categoryDao()
        val source = if (includeInactive) dao.observeAll(householdId) else dao.observeActive(householdId)
        return source.map { categories -> categories.mapNotNull { it.toDomain() } }
    }

    override fun observeTransactions(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceTransaction>> {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        val transactionFlow = if (canReadWholeHousehold) {
            database.transactionDao().observeAllForHousehold(householdId)
        } else {
            database.transactionDao().observeOwn(householdId, userId)
        }
        return combine(transactionFlow, database.categoryDao().observeAll(householdId)) { transactions, categories ->
            val categoriesById = categories.associateBy(CategoryEntity::id)
            transactions.mapNotNull { transaction ->
                transaction.toDomain(categoriesById[transaction.categoryId])
            }
        }
    }

    override fun observePendingCount(accountId: String): Flow<Int> =
        databases.databaseFor(accountId).syncOutboxDao().observeCount(accountId)

    override suspend fun prepareHousehold(accountId: String, householdId: String, role: String) {
        if (householdId.isBlank() || role !in ALLOWED_ROLES) {
            throw FinanceDataException(FinanceDataError.HouseholdUnavailable)
        }
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val cached = database.householdCacheDao().getHousehold(accountId)
            if (cached == null || cached.householdId != householdId || cached.currentUserRole != role) {
                database.householdCacheDao().saveHousehold(
                    com.moonspace.adminfinanciera.core.database.HouseholdCacheEntity(
                        accountId = accountId,
                        householdId = householdId,
                        currentUserRole = role,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                if (cached?.householdId != null && cached.householdId != householdId) {
                    database.transactionDao().deleteForHousehold(cached.householdId)
                    database.categoryDao().deleteForHousehold(cached.householdId)
                    database.syncOutboxDao().clearAccount(accountId)
                    database.transactionDeletionMarkerDao().clearAll()
                }
                if (role == ROLE_MEMBER) {
                    database.transactionDao().removeOthers(householdId, accountId)
                }
            }

            val categoryDao = database.categoryDao()
            if (categoryDao.countForHousehold(householdId) == 0) {
                val now = System.currentTimeMillis()
                val defaults = DEFAULT_CATEGORIES.map { (kind, slug, name) ->
                    CategoryEntity(
                        id = stableDefaultCategoryId(householdId, kind, slug),
                        accountId = accountId,
                        householdId = householdId,
                        name = name,
                        nameKey = normalizeCategoryName(name),
                        kind = kind,
                        isActive = true,
                        createdBy = null,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                categoryDao.insertDefaults(defaults)
            }
        }
    }

    override suspend fun saveTransaction(
        accountId: String,
        userId: String,
        householdId: String,
        draft: TransactionDraft
    ) {
        requireAccountOwner(accountId, userId)
        if (draft.amountCentavos <= 0) throw FinanceDataException(FinanceDataError.InvalidAmount)
        if (!isIsoDate(draft.occurredOn)) throw FinanceDataException(FinanceDataError.InvalidDate)

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val category = database.categoryDao().findById(householdId, draft.categoryId)
            if (category == null || !category.isActive || category.kind != draft.kind.apiValue) {
                throw FinanceDataException(FinanceDataError.CategoryUnavailable)
            }

            val previous = draft.id?.let { database.transactionDao().findById(householdId, it) }
            if (draft.id != null && (previous == null || previous.createdBy != userId)) {
                throw FinanceDataException(FinanceDataError.RecordUnavailable)
            }

            val now = System.currentTimeMillis()
            val id = previous?.id ?: UUID.randomUUID().toString()
            database.transactionDao().save(
                TransactionEntity(
                    id = id,
                    accountId = accountId,
                    householdId = householdId,
                    createdBy = previous?.createdBy ?: userId,
                    categoryId = category.id,
                    kind = draft.kind.apiValue,
                    amountCentavos = draft.amountCentavos,
                    currency = CURRENCY_BOB,
                    occurredOn = draft.occurredOn,
                    description = draft.description?.trim()?.takeIf(String::isNotEmpty),
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now
                )
            )
            database.transactionDeletionMarkerDao().remove(id)
            enqueue(accountId, ENTITY_TRANSACTION, id)
        }
    }

    override suspend fun deleteOwnTransaction(
        accountId: String,
        userId: String,
        householdId: String,
        transactionId: String
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val transaction = database.transactionDao().findById(householdId, transactionId)
            if (transaction == null || transaction.createdBy != userId) {
                throw FinanceDataException(FinanceDataError.RecordUnavailable)
            }
            database.transactionDao().delete(householdId, transactionId)
            database.transactionDeletionMarkerDao().save(
                TransactionDeletionMarkerEntity(
                    transactionId = transactionId,
                    deletedAt = System.currentTimeMillis()
                )
            )
            enqueue(accountId, ENTITY_TRANSACTION, transactionId, OPERATION_DELETE)
        }
    }

    override suspend fun saveCategory(
        accountId: String,
        userId: String,
        householdId: String,
        role: String,
        draft: CategoryDraft
    ) {
        requireAccountOwner(accountId, userId)
        requireAdmin(role)
        val name = draft.name.trim()
        if (name.isEmpty() || name.length > MAX_CATEGORY_NAME_LENGTH) {
            throw FinanceDataException(FinanceDataError.InvalidCategoryName)
        }

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val dao = database.categoryDao()
            val previous = draft.id?.let { dao.findById(householdId, it) }
            if (draft.id != null && previous == null) {
                throw FinanceDataException(FinanceDataError.RecordUnavailable)
            }
            if (previous != null && previous.kind != draft.kind.apiValue) {
                throw FinanceDataException(FinanceDataError.CategoryUnavailable)
            }
            val nameKey = normalizeCategoryName(name)
            if (dao.countNameConflict(householdId, draft.kind.apiValue, nameKey, previous?.id.orEmpty()) > 0) {
                throw FinanceDataException(FinanceDataError.CategoryNameConflict)
            }

            val now = System.currentTimeMillis()
            val categoryId = previous?.id ?: UUID.randomUUID().toString()
            val updatedCategory = CategoryEntity(
                    id = categoryId,
                    accountId = accountId,
                    householdId = householdId,
                    name = name,
                    nameKey = nameKey,
                    kind = draft.kind.apiValue,
                    isActive = previous?.isActive ?: true,
                    createdBy = previous?.createdBy ?: userId,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now
                )
            if (previous == null) dao.insert(updatedCategory) else dao.update(updatedCategory)
            enqueue(accountId, ENTITY_CATEGORY, categoryId)
        }
    }

    override suspend fun deactivateCategory(
        accountId: String,
        householdId: String,
        role: String,
        categoryId: String
    ) {
        requireAdmin(role)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val dao = database.categoryDao()
            val category = dao.findById(householdId, categoryId)
                ?: throw FinanceDataException(FinanceDataError.RecordUnavailable)
            if (category.isActive && dao.countActive(householdId, category.kind) <= 1) {
                throw FinanceDataException(FinanceDataError.LastActiveCategory)
            }
            if (category.isActive) {
                dao.update(category.copy(isActive = false, updatedAt = System.currentTimeMillis()))
                enqueue(accountId, ENTITY_CATEGORY, categoryId)
            }
        }
    }

    private suspend fun enqueue(
        accountId: String,
        entityType: String,
        entityId: String,
        operation: String = OPERATION_UPSERT
    ) {
        val outboxDao = databases.databaseFor(accountId).syncOutboxDao()
        outboxDao.removeForEntity(accountId, entityType, entityId)
        outboxDao.enqueue(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                enqueuedAt = System.currentTimeMillis()
            )
        )
    }

    private fun requireAdmin(role: String) {
        if (role != ROLE_ADMIN) throw FinanceDataException(FinanceDataError.PermissionDenied)
    }

    private fun requireAccountOwner(accountId: String, userId: String) {
        if (accountId.isBlank() || accountId != userId) {
            throw FinanceDataException(FinanceDataError.PermissionDenied)
        }
    }

    private fun normalizeCategoryName(name: String): String = name.trim().lowercase(Locale.ROOT)

    private fun isIsoDate(value: String): Boolean = try {
        val format = SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(value)?.let(format::format) == value
    } catch (_: Exception) {
        false
    }

    private fun CategoryEntity.toDomain(): FinanceCategory? {
        val kind = TransactionKind.fromApiValue(kind) ?: return null
        return FinanceCategory(id, householdId, name, kind, isActive, createdBy)
    }

    private fun TransactionEntity.toDomain(category: CategoryEntity?): FinanceTransaction? {
        val kind = TransactionKind.fromApiValue(kind) ?: return null
        val categoryName = category?.name ?: return null
        return FinanceTransaction(
            id = id,
            householdId = householdId,
            createdBy = createdBy,
            categoryId = categoryId,
            categoryName = categoryName,
            kind = kind,
            amountCentavos = amountCentavos,
            currency = currency,
            occurredOn = occurredOn,
            description = description,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun stableDefaultCategoryId(householdId: String, kind: String, slug: String): String =
        UUID.nameUUIDFromBytes("$householdId:category:$kind:$slug".toByteArray(Charsets.UTF_8)).toString()

    private companion object {
        const val CURRENCY_BOB = "BOB"
        const val ENTITY_TRANSACTION = "transaction"
        const val ENTITY_CATEGORY = "category"
        const val OPERATION_UPSERT = "upsert"
        const val OPERATION_DELETE = "delete"
        const val ROLE_ADMIN = "admin"
        const val ROLE_MEMBER = "member"
        const val MAX_CATEGORY_NAME_LENGTH = 40
        const val ISO_DATE_PATTERN = "yyyy-MM-dd"
        val ALLOWED_ROLES = setOf(ROLE_ADMIN, ROLE_MEMBER)
        val DEFAULT_CATEGORIES = listOf(
            Triple("expense", "comida", "Comida"),
            Triple("expense", "pasaje", "Pasaje"),
            Triple("expense", "varios", "Varios"),
            Triple("income", "salario", "Salario"),
            Triple("income", "trabajos-extra", "Trabajos extra")
        )
    }
}
