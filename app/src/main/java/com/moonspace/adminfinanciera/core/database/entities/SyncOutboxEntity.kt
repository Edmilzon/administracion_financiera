package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

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
