package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "household_member_cache",
    primaryKeys = ["account_id", "user_id"],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("household_id")]
)
data class HouseholdMemberCacheEntity(
    @androidx.room.ColumnInfo(name = "account_id")
    val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id")
    val householdId: String,
    @androidx.room.ColumnInfo(name = "user_id")
    val userId: String,
    val email: String,
    val role: String
)
