package com.moonspace.adminfinanciera.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object FinanceRoomMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_state` " +
                    "(`account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                    "`last_successful_sync_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`account_id`))"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `is_remote_backed` " +
                    "INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                    "`user_id` TEXT NOT NULL, `category_id` TEXT, `category_key` TEXT NOT NULL, " +
                    "`month_start` TEXT NOT NULL, `amount_centavos` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                    "`is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`household_id`, `category_id`) REFERENCES `categories`(`household_id`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_account_id` ON `budgets` (`account_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_household_id_category_id` " +
                    "ON `budgets` (`household_id`, `category_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_household_id_user_id_month_start` " +
                    "ON `budgets` (`household_id`, `user_id`, `month_start`)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_account_id_household_id_user_id_month_start_category_key` " +
                    "ON `budgets` (`account_id`, `household_id`, `user_id`, `month_start`, `category_key`)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source_recurring_rule_id` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `scheduled_for` TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_source_recurring_rule_id_scheduled_for` " +
                    "ON `transactions` (`source_recurring_rule_id`, `scheduled_for`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                    "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                    "`created_by` TEXT NOT NULL, `category_id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`amount_centavos` INTEGER NOT NULL, `currency` TEXT NOT NULL, `description` TEXT, " +
                    "`frequency` TEXT NOT NULL, `interval_count` INTEGER NOT NULL, `start_on` TEXT NOT NULL, " +
                    "`next_due_on` TEXT NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`household_id`, `category_id`) REFERENCES `categories`(`household_id`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE RESTRICT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_account_id` ON `recurring_rules` (`account_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_household_id_next_due_on_is_active` " +
                    "ON `recurring_rules` (`household_id`, `next_due_on`, `is_active`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_household_id_created_by_next_due_on` " +
                    "ON `recurring_rules` (`household_id`, `created_by`, `next_due_on`)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `debts` (" +
                    "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                    "`created_by` TEXT NOT NULL, `direction` TEXT NOT NULL, `counterparty` TEXT NOT NULL, " +
                    "`description` TEXT, `principal_centavos` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                    "`opened_on` TEXT NOT NULL, `due_on` TEXT, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_account_id` ON `debts` (`account_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_debts_household_id_id` ON `debts` (`household_id`, `id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_household_id_created_by_direction` ON `debts` (`household_id`, `created_by`, `direction`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debts_household_id_due_on` ON `debts` (`household_id`, `due_on`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `debt_payments` (" +
                    "`id` TEXT NOT NULL, `account_id` TEXT NOT NULL, `household_id` TEXT NOT NULL, " +
                    "`debt_id` TEXT NOT NULL, `created_by` TEXT NOT NULL, `amount_centavos` INTEGER NOT NULL, " +
                    "`paid_on` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, `is_remote_backed` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`account_id`) REFERENCES `household_cache`(`account_id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`household_id`, `debt_id`) REFERENCES `debts`(`household_id`, `id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_account_id` ON `debt_payments` (`account_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_household_id_debt_id_paid_on` ON `debt_payments` (`household_id`, `debt_id`, `paid_on`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_debt_payments_household_id_created_by` ON `debt_payments` (`household_id`, `created_by`)")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_household_id_category_id` " +
                    "ON `recurring_rules` (`household_id`, `category_id`)"
            )
        }
    }
}
