package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
