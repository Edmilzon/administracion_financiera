package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
