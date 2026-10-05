package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
