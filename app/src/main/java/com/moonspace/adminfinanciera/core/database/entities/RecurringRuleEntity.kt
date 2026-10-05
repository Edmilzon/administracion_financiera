package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "recurring_rules",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "next_due_on", "is_active"]),
        Index(value = ["household_id", "created_by", "next_due_on"])
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
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    val currency: String,
    val description: String?,
    val frequency: String,
    @androidx.room.ColumnInfo(name = "interval_count") val intervalCount: Int,
    @androidx.room.ColumnInfo(name = "start_on") val startOn: String,
    @androidx.room.ColumnInfo(name = "next_due_on") val nextDueOn: String,
    @androidx.room.ColumnInfo(name = "is_active") val isActive: Boolean,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false
)
