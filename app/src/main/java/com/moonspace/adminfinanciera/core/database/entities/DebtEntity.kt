package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "debts",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "created_by", "direction"]),
        Index(value = ["household_id", "due_on"]),
        Index(value = ["household_id", "id"], unique = true)
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
data class DebtEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    val direction: String,
    val counterparty: String,
    val description: String?,
    @ColumnInfo(name = "principal_centavos") val principalCentavos: Long,
    val currency: String,
    @ColumnInfo(name = "opened_on") val openedOn: String,
    @ColumnInfo(name = "due_on") val dueOn: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_remote_backed", defaultValue = "0") val isRemoteBacked: Boolean = false
)
