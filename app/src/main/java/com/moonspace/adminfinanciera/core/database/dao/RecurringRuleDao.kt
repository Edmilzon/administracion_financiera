package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
