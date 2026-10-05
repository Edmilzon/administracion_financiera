package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

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
