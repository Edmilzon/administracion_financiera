package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
