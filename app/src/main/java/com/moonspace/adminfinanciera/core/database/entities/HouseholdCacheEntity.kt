package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(tableName = "household_cache")
data class HouseholdCacheEntity(
    @PrimaryKey
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id")
    val householdId: String?,
    @androidx.room.ColumnInfo(name = "current_user_role")
    val currentUserRole: String?,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
