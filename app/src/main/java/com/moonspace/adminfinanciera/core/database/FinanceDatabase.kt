package com.moonspace.adminfinanciera.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "household_cache")
data class HouseholdCacheEntity(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id")
    val householdId: String?,
    @androidx.room.ColumnInfo(name = "current_user_role")
    val currentUserRole: String?,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "household_member_cache",
    primaryKeys = ["account_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("household_id")]
)
data class HouseholdMemberCacheEntity(
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id")
    val householdId: String,
    @androidx.room.ColumnInfo(name = "user_id")
    val userId: String,
    val email: String,
    val role: String
)

@Entity(
    tableName = "categories",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "id"], unique = true),
        Index(value = ["household_id", "kind", "name_key"], unique = true),
        Index(value = ["household_id", "kind", "is_active"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    val name: String,
    @androidx.room.ColumnInfo(name = "name_key") val nameKey: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "is_active") val isActive: Boolean,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["household_id", "occurred_on"]),
        Index(value = ["household_id", "created_by", "occurred_on"]),
        Index(value = ["household_id", "category_id"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    val currency: String,
    @androidx.room.ColumnInfo(name = "occurred_on") val occurredOn: String,
    val description: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["account_id", "entity_type", "entity_id"], unique = true),
        Index(value = ["account_id", "enqueued_at"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "entity_type") val entityType: String,
    @androidx.room.ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @androidx.room.ColumnInfo(name = "enqueued_at") val enqueuedAt: Long,
    val attempts: Int = 0
)

@Entity(
    tableName = "transaction_deletion_markers",
    primaryKeys = ["transaction_id"],
    indices = [Index(value = ["deleted_at"])]
)
data class TransactionDeletionMarkerEntity(
    @androidx.room.ColumnInfo(name = "transaction_id") val transactionId: String,
    @androidx.room.ColumnInfo(name = "deleted_at") val deletedAt: Long
)

@Dao
interface HouseholdCacheDao {
    @Query("SELECT * FROM household_cache WHERE account_id = :accountId LIMIT 1")
    suspend fun getHousehold(accountId: String): HouseholdCacheEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHousehold(entity: HouseholdCacheEntity)

    @Query("UPDATE household_cache SET household_id = :householdId, current_user_role = :role, updated_at = :updatedAt WHERE account_id = :accountId")
    suspend fun updateHousehold(accountId: String, householdId: String?, role: String?, updatedAt: Long): Int

    suspend fun saveHousehold(entity: HouseholdCacheEntity) {
        val updated = updateHousehold(
            entity.accountId,
            entity.householdId,
            entity.currentUserRole,
            entity.updatedAt
        )
        if (updated == 0) insertHousehold(entity)
    }

    @Query("SELECT * FROM household_member_cache WHERE account_id = :accountId ORDER BY email COLLATE NOCASE")
    suspend fun getMembers(accountId: String): List<HouseholdMemberCacheEntity>

    @Query("DELETE FROM household_member_cache WHERE account_id = :accountId")
    suspend fun clearMembers(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMembers(entities: List<HouseholdMemberCacheEntity>)

    suspend fun saveSnapshot(
        household: HouseholdCacheEntity,
        members: List<HouseholdMemberCacheEntity>
    ) {
        saveHousehold(household)
        clearMembers(household.accountId)
        if (members.isNotEmpty()) saveMembers(members)
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE household_id = :householdId AND is_active = 1 ORDER BY kind, name COLLATE NOCASE")
    fun observeActive(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE household_id = :householdId ORDER BY kind, name COLLATE NOCASE")
    fun observeAll(householdId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND household_id = :householdId LIMIT 1")
    suspend fun findById(householdId: String, categoryId: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE household_id = :householdId AND kind = :kind AND name_key = :nameKey LIMIT 1")
    suspend fun findByName(householdId: String, kind: String, nameKey: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId AND kind = :kind AND name_key = :nameKey AND id != :excludedId")
    suspend fun countNameConflict(householdId: String, kind: String, nameKey: String, excludedId: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId AND kind = :kind AND is_active = 1")
    suspend fun countActive(householdId: String, kind: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE household_id = :householdId")
    suspend fun countForHousehold(householdId: String): Int

    @Insert
    suspend fun insert(entity: CategoryEntity)

    @Update
    suspend fun update(entity: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(entities: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE household_id = :householdId ORDER BY occurred_on DESC, updated_at DESC")
    fun observeAllForHousehold(householdId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE household_id = :householdId AND created_by = :userId ORDER BY occurred_on DESC, updated_at DESC")
    fun observeOwn(householdId: String, userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND household_id = :householdId LIMIT 1")
    suspend fun findById(householdId: String, transactionId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :transactionId AND household_id = :householdId")
    suspend fun delete(householdId: String, transactionId: String)

    @Query("DELETE FROM transactions WHERE household_id = :householdId AND created_by != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("DELETE FROM transactions WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)
}

@Dao
interface SyncOutboxDao {
    @Query("DELETE FROM sync_outbox WHERE account_id = :accountId AND entity_type = :entityType AND entity_id = :entityId")
    suspend fun removeForEntity(accountId: String, entityType: String, entityId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: SyncOutboxEntity)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE account_id = :accountId")
    fun observeCount(accountId: String): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE account_id = :accountId ORDER BY enqueued_at ASC")
    suspend fun pending(accountId: String): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE account_id = :accountId")
    suspend fun clearAccount(accountId: String)
}

@Dao
interface TransactionDeletionMarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(marker: TransactionDeletionMarkerEntity)

    @Query("DELETE FROM transaction_deletion_markers WHERE transaction_id = :transactionId")
    suspend fun remove(transactionId: String)

    @Query("DELETE FROM transaction_deletion_markers")
    suspend fun clearAll()
}

@Database(
    entities = [
        HouseholdCacheEntity::class,
        HouseholdMemberCacheEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SyncOutboxEntity::class,
        TransactionDeletionMarkerEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun householdCacheDao(): HouseholdCacheDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun transactionDeletionMarkerDao(): TransactionDeletionMarkerDao
}
