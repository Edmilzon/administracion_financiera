package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["household_id", "occurred_on"]),
        Index(value = ["household_id", "created_by", "occurred_on"]),
        Index(value = ["household_id", "category_id"]),
        Index(value = ["source_recurring_rule_id", "scheduled_for"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "account_id") val accountId: String,
    @androidx.room.ColumnInfo(name = "household_id") val householdId: String,
    @androidx.room.ColumnInfo(name = "created_by") val createdBy: String,
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: String,
    val kind: String,
    @androidx.room.ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    val currency: String,
    @androidx.room.ColumnInfo(name = "occurred_on") val occurredOn: String,
    val description: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "is_remote_backed", defaultValue = "0")
    val isRemoteBacked: Boolean = false,
    @androidx.room.ColumnInfo(name = "source_recurring_rule_id") val sourceRecurringRuleId: String? = null,
    @androidx.room.ColumnInfo(name = "scheduled_for") val scheduledFor: String? = null
)
