package com.moonspace.adminfinanciera.core.database.entities

import androidx.room.*

@Entity(
    tableName = "debt_payments",
    indices = [
        Index("account_id"),
        Index(value = ["household_id", "debt_id", "paid_on"]),
        Index(value = ["household_id", "created_by"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = HouseholdCacheEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["household_id", "id"],
            childColumns = ["household_id", "debt_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DebtPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "household_id") val householdId: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "created_by") val createdBy: String,
    @ColumnInfo(name = "amount_centavos") val amountCentavos: Long,
    @ColumnInfo(name = "paid_on") val paidOn: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_remote_backed", defaultValue = "0") val isRemoteBacked: Boolean = false
)
