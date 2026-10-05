package com.moonspace.adminfinanciera.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moonspace.adminfinanciera.core.database.dao.BudgetDao
import com.moonspace.adminfinanciera.core.database.dao.CategoryDao
import com.moonspace.adminfinanciera.core.database.dao.DebtDao
import com.moonspace.adminfinanciera.core.database.dao.DebtPaymentDao
import com.moonspace.adminfinanciera.core.database.dao.HouseholdCacheDao
import com.moonspace.adminfinanciera.core.database.dao.RecurringRuleDao
import com.moonspace.adminfinanciera.core.database.dao.SyncOutboxDao
import com.moonspace.adminfinanciera.core.database.dao.SyncStateDao
import com.moonspace.adminfinanciera.core.database.dao.TransactionDao
import com.moonspace.adminfinanciera.core.database.dao.TransactionDeletionMarkerDao
import com.moonspace.adminfinanciera.core.database.entities.BudgetEntity
import com.moonspace.adminfinanciera.core.database.entities.CategoryEntity
import com.moonspace.adminfinanciera.core.database.entities.DebtEntity
import com.moonspace.adminfinanciera.core.database.entities.DebtPaymentEntity
import com.moonspace.adminfinanciera.core.database.entities.HouseholdCacheEntity
import com.moonspace.adminfinanciera.core.database.entities.HouseholdMemberCacheEntity
import com.moonspace.adminfinanciera.core.database.entities.RecurringRuleEntity
import com.moonspace.adminfinanciera.core.database.entities.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.database.entities.SyncStateEntity
import com.moonspace.adminfinanciera.core.database.entities.TransactionDeletionMarkerEntity
import com.moonspace.adminfinanciera.core.database.entities.TransactionEntity

@Database(
    entities = [
        HouseholdCacheEntity::class,
        HouseholdMemberCacheEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        SyncOutboxEntity::class,
        TransactionDeletionMarkerEntity::class,
        SyncStateEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun syncStateDao(): SyncStateDao
    abstract fun householdCacheDao(): HouseholdCacheDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun debtDao(): DebtDao
    abstract fun debtPaymentDao(): DebtPaymentDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun transactionDeletionMarkerDao(): TransactionDeletionMarkerDao
}