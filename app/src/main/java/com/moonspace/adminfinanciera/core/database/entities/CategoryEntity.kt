package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "categories",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "id"], unique = true),
        Index(value = ["household_id", "kind", "name_key"], unique = true),
        Index(value = ["household_id", "kind", "is_active"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    val name: String,
    @androidx.room.ColumnInfo(name = "name_key") val nameKey: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "is_active") val isActive: Boolean,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long
)
