package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "budgets",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "category_id"]),
        Index(value = ["household_id", "user_id", "month_start"]),
        Index(
            value = ["account_id", "household_id", "user_id", "month_start", "category_key"],
            unique = true
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "user_id") val userId: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String?,
    @androidx.room.ColumnInfo(name = "category_key") val categoryKey: String,
    @androidx.room.ColumnInfo(name = "month_start") val monthStart: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false
)
