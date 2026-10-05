package com.moonspace.adminfinanciera.core.database.dao

import androidx.room.*
import com.moonspace.adminfinanciera.core.database.entities.*
import kotlinx.coroutines.flow.Flow

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
