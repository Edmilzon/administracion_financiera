package com.moonspace.adminfinanciera.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "debts",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "created_by", "direction"]),
        Index(value = ["household_id", "due_on"]),
        Index(value = ["household_id", "id"], unique = true)
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
data class DebtEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    val direction: String,
    val counterparty: String,
    val description: String?,
    @ColumnInfo(name = "principal_centavos") val principalCentavos: Long,
    val currency: String,
    @ColumnInfo(name = "opened_on") val openedOn: String,
    @ColumnInfo(name = "due_on") val dueOn: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_remote_backed", defaultValue = "0") val isRemoteBacked: Boolean = false
)

@Entity(
    tableName = "debt_payments",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "debt_id", "paid_on"]),
        Index(value = ["household_id", "created_by"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "debt_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DebtPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    @ColumnInfo(name = "paid_on") val paidOn: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_remote_backed", defaultValue = "0") val isRemoteBacked: Boolean = false
)

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE household_id = :householdId ORDER BY due_on IS NULL, due_on, opened_on DESC, counterparty COLLATE NOCASE")
    fun observeAllForHousehold(householdId: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE household_id = :householdId AND created_by = :userId ORDER BY due_on IS NULL, due_on, opened_on DESC, counterparty COLLATE NOCASE")
    fun observeOwn(householdId: String, userId: String): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE household_id = :householdId AND id = :debtId LIMIT 1")
    suspend fun findById(householdId: String, debtId: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<DebtEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: DebtEntity): Long

    @Update
    suspend fun update(entity: DebtEntity): Int

    @Transaction
    suspend fun save(entity: DebtEntity) {
        if (insertIfMissing(entity) == -1L && update(entity) == 0) {
            throw IllegalStateException("A local debt conflicts with a remote debt identifier.")
        }
    }

    @Query("DELETE FROM debts WHERE household_id = :householdId AND id = :debtId")
    suspend fun deleteById(householdId: String, debtId: String)

    @Query("DELETE FROM debts WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Query("DELETE FROM debts WHERE household_id = :householdId AND created_by != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("UPDATE debts SET is_remote_backed = 1 WHERE household_id = :householdId AND id = :debtId")
    suspend fun markRemoteBacked(householdId: String, debtId: String)
}

@Dao
interface DebtPaymentDao {
    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId AND id = :paymentId LIMIT 1")
    suspend fun findById(householdId: String, paymentId: String): DebtPaymentEntity?

    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId ORDER BY paid_on DESC, created_at DESC")
    fun observeAllForHousehold(householdId: String): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId AND created_by = :userId ORDER BY paid_on DESC, created_at DESC")
    fun observeOwn(householdId: String, userId: String): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId AND debt_id = :debtId ORDER BY paid_on DESC, created_at DESC")
    fun observeForDebt(householdId: String, debtId: String): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId AND debt_id = :debtId")
    suspend fun forDebt(householdId: String, debtId: String): List<DebtPaymentEntity>

    @Query("SELECT * FROM debt_payments WHERE household_id = :householdId")
    suspend fun allForHousehold(householdId: String): List<DebtPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: DebtPaymentEntity)

    @Query("DELETE FROM debt_payments WHERE household_id = :householdId AND id = :paymentId")
    suspend fun deleteById(householdId: String, paymentId: String)

    @Query("DELETE FROM debt_payments WHERE household_id = :householdId")
    suspend fun deleteForHousehold(householdId: String)

    @Query("DELETE FROM debt_payments WHERE household_id = :householdId AND created_by != :userId")
    suspend fun removeOthers(householdId: String, userId: String)

    @Query("UPDATE debt_payments SET is_remote_backed = 1 WHERE household_id = :householdId AND id = :paymentId")
    suspend fun markRemoteBacked(householdId: String, paymentId: String)
}
