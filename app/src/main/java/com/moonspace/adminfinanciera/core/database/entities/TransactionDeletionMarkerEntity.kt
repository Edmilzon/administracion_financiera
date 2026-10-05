package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "transaction_deletion_markers",
    primaryKeys = ["transaction_id"],
    indices = [Index(value = ["deleted_at"])]
)
data class TransactionDeletionMarkerEntity(
    @androidx.room.ColumnInfo(name = "transaction_id") val transactionId: String,
    @androidx.room.ColumnInfo(name = "deleted_at") val deletedAt: Long
)
