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
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        Index(value = ["household_id", "category_id"]),
        Index(value = ["source_recurring_rule_id", "scheduled_for"], unique = true)
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
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false,
    @androidx.room.ColumnInfo(name = "source_recurring_rule_id") val sourceRecurringRuleId: String? = null,
    @androidx.room.ColumnInfo(name = "scheduled_for") val scheduledFor: String? = null
)

@Entity(
    tableName = "recurring_rules",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "next_due_on", "is_active"]),
        Index(value = ["household_id", "created_by", "next_due_on"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    val currency: String,
    val description: String?,
    val frequency: String,
    @androidx.room.ColumnInfo(name = "interval_count") val intervalCount: Int,
    @androidx.room.ColumnInfo(name = "start_on") val startOn: String,
    @androidx.room.ColumnInfo(name = "next_due_on") val nextDueOn: String,
    @androidx.room.ColumnInfo(name = "is_active") val isActive: Boolean,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false
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

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id")
    val householdId: String,
    @androidx.room.ColumnInfo(name = "last_successful_sync_at")
    val lastSuccessfulSyncAt: Long
)

@Entity(
    tableName = "budgets",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "category_id"]),
        Index(value = ["household_id", "user_id", "month_start"]),
        Index(
            value = ["account_id", "household_id", "user_id", "month_start", "category_key"],
            unique = true
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "user_id") val userId: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String?,
    @androidx.room.ColumnInfo(name = "category_key") val categoryKey: String,
    @androidx.room.ColumnInfo(name = "month_start") val monthStart: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity): Int

    @Transaction
    suspend fun upsert(entity: CategoryEntity) {
        if (insertIfMissing(entity) == -1L && update(entity) == 0) {
            throw IllegalStateException("A local category conflicts with a remote category identifier.")
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(entities: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE household_id = :householdId AND category_id = :categoryId")
    suspend fun countForCategory(householdId: String, categoryId: String): Int

    @Query("DELETE FROM categories WHERE household_id = :householdId AND id = :categoryId")
    suspend fun deleteById(householdId: String, categoryId: String)

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

    @Query("SELECT * FROM transactions WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE source_recurring_rule_id = :ruleId AND scheduled_for = :scheduledFor LIMIT 1")
    suspend fun findRecurringOccurrence(ruleId: String, scheduledFor: String): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :transactionId AND household_id = :householdId")
    suspend fun delete(householdId: String, transactionId: String)

    @Query("DELETE FROM transactions WHERE household_id = :householdId AND created_by != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("DELETE FROM transactions WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Query("UPDATE transactions SET is_remote_backed = 1 WHERE id = :transactionId AND household_id = :householdId")
    suspend fun markRemoteBacked(householdId: String, transactionId: String)
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE household_id = :householdId ORDER BY is_active DESC, next_due_on, created_by")
    fun observeAllForHousehold(householdId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE household_id = :householdId AND created_by = :userId ORDER BY is_active DESC, next_due_on")
    fun observeOwn(householdId: String, userId: String): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE household_id = :householdId AND id = :ruleId LIMIT 1")
    suspend fun findById(householdId: String, ruleId: String): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE account_id = :accountId AND household_id = :householdId AND is_active = 1 AND next_due_on <= :today ORDER BY next_due_on, id")
    suspend fun dueForAccount(accountId: String, householdId: String, today: String): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: RecurringRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE household_id = :householdId AND id = :ruleId")
    suspend fun deleteById(householdId: String, ruleId: String)

    @Query("DELETE FROM recurring_rules WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Query("DELETE FROM recurring_rules WHERE household_id = :householdId AND created_by != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("UPDATE recurring_rules SET is_remote_backed = 1 WHERE household_id = :householdId AND id = :ruleId")
    suspend fun markRemoteBacked(householdId: String, ruleId: String)
}

@Dao
interface SyncOutboxDao {
    @Query("DELETE FROM sync_outbox WHERE account_id = :accountId AND entity_type = :entityType AND entity_id = :entityId")
    suspend fun removeForEntity(accountId: String, entityType: String, entityId: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun removeById(id: String)

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
    @Query("SELECT * FROM transaction_deletion_markers WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun findById(transactionId: String): TransactionDeletionMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(marker: TransactionDeletionMarkerEntity)

    @Query("DELETE FROM transaction_deletion_markers WHERE transaction_id = :transactionId")
    suspend fun remove(transactionId: String)

    @Query("DELETE FROM transaction_deletion_markers")
    suspend fun clearAll()
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE account_id = :accountId LIMIT 1")
    suspend fun get(accountId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SyncStateEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE household_id = :householdId AND month_start = :monthStart ORDER BY user_id, category_key")
    fun observeAllForMonth(householdId: String, monthStart: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE household_id = :householdId AND user_id = :userId AND month_start = :monthStart ORDER BY category_key")
    fun observeOwnForMonth(householdId: String, userId: String, monthStart: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :budgetId AND household_id = :householdId LIMIT 1")
    suspend fun findById(householdId: String, budgetId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE household_id = :householdId AND user_id = :userId AND month_start = :monthStart AND category_key = :categoryKey LIMIT 1")
    suspend fun findForScope(
        householdId: String,
        userId: String,
        monthStart: String,
        categoryKey: String
    ): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE household_id = :householdId AND id = :budgetId")
    suspend fun deleteById(householdId: String, budgetId: String)

    @Query("DELETE FROM budgets WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Query("DELETE FROM budgets WHERE household_id = :householdId AND user_id != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("UPDATE budgets SET is_remote_backed = 1 WHERE id = :budgetId AND household_id = :householdId")
    suspend fun markRemoteBacked(householdId: String, budgetId: String)
}

@Database(
    entities = [
        HouseholdCacheEntity::class,
        HouseholdMemberCacheEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        SyncOutboxEntity::class,
        TransactionDeletionMarkerEntity::class,
        SyncStateEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun syncStateDao(): SyncStateDao
    abstract fun householdCacheDao(): HouseholdCacheDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun debtPaymentDao(): DebtPaymentDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun transactionDeletionMarkerDao(): TransactionDeletionMarkerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` " +
                        "(`account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                        "`last_successful_sync_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`account_id`))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `is_remote_backed` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (" +
                        "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                        "`user_id` TEXT NOT NULL, `category_id` TEXT, `category_key` TEXT NOT NULL, " +
                        "`month_start` TEXT NOT NULL, `amount_centavos` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                        "`is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`household_id`, `category_id`) REFERENCES `categories`(`household_id`, `id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_account_id` ON `budgets` (`account_id`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budgets_household_id_category_id` " +
                        "ON `budgets` (`household_id`, `category_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budgets_household_id_user_id_month_start` " +
                        "ON `budgets` (`household_id`, `user_id`, `month_start`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_account_id_household_id_user_id_month_start_category_key` " +
                        "ON `budgets` (`account_id`, `household_id`, `user_id`, `month_start`, `category_key`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source_recurring_rule_id` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `scheduled_for` TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_source_recurring_rule_id_scheduled_for` " +
                        "ON `transactions` (`source_recurring_rule_id`, `scheduled_for`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                        "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                        "`created_by` TEXT NOT NULL, `category_id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`amount_centavos` INTEGER NOT NULL, `currency` TEXT NOT NULL, `description` TEXT, " +
                        "`frequency` TEXT NOT NULL, `interval_count` INTEGER NOT NULL, `start_on` TEXT NOT NULL, " +
                        "`next_due_on` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`household_id`, `category_id`) REFERENCES `categories`(`household_id`, `id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_account_id` ON `recurring_rules` (`account_id`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_rules_household_id_next_due_on_is_active` " +
                        "ON `recurring_rules` (`household_id`, `next_due_on`, `is_active`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recurring_rules_household_id_created_by_next_due_on` " +
                        "ON `recurring_rules` (`household_id`, `created_by`, `next_due_on`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (" +
                        "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                        "`created_by` TEXT NOT NULL, `direction` TEXT NOT NULL, `counterparty` TEXT NOT NULL, " +
                        "`description` TEXT, `principal_centavos` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                        "`opened_on` TEXT NOT NULL, `due_on` TEXT, `created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_account_id` ON `debts` (`account_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debts_household_id_id` ON `debts` (`household_id`, `id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_household_id_created_by_direction` ON `debts` (`household_id`, `created_by`, `direction`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_household_id_due_on` ON `debts` (`household_id`, `due_on`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debt_payments` (" +
                        "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                        "`debt_id` TEXT NOT NULL, `created_by` TEXT NOT NULL, `amount_centavos` INTEGER NOT NULL, " +
                        "`paid_on` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`household_id`, `debt_id`) REFERENCES `debts`(`household_id`, `id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_account_id` ON `debt_payments` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_household_id_debt_id_paid_on` ON `debt_payments` (`household_id`, `debt_id`, `paid_on`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_household_id_created_by` ON `debt_payments` (`household_id`, `created_by`)")
            }
        }
    }
}
