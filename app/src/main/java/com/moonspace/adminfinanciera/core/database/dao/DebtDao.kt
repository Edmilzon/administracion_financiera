package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
