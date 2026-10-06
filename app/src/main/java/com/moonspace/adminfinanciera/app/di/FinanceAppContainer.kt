package com.moonspace.adminfinanciera.app.di

import android.content.Context
import com.moonspace.adminfinanciera.BuildConfig
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.core.sync.FinanceSyncScheduler
import com.moonspace.adminfinanciera.core.sync.FinanceSyncRepository
import com.moonspace.adminfinanciera.core.sync.RecurringReminderScheduler
import com.moonspace.adminfinanciera.feature.transactions.data.NeonFinanceSyncRepository
import com.moonspace.adminfinanciera.feature.auth.data.NeonAuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.users.data.NeonHouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.transactions.data.RoomFinancialRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.budgets.data.RoomBudgetRepository
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetRepository
import com.moonspace.adminfinanciera.feature.recurring.data.RoomRecurringRuleRepository
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleRepository
import com.moonspace.adminfinanciera.feature.reports.data.FinanceReportDocumentGenerator
import com.moonspace.adminfinanciera.feature.debts.data.RoomDebtRepository
import com.moonspace.adminfinanciera.feature.debts.domain.DebtRepository

class FinanceAppContainer(context: Context) {
    private val appContext = context.applicationContext
    val financeDatabases = EncryptedFinanceDatabaseProvider(appContext)
    val syncScheduler = FinanceSyncScheduler(appContext)
    val recurringReminderScheduler = RecurringReminderScheduler(appContext)
    val financeReportDocumentGenerator = FinanceReportDocumentGenerator(appContext.cacheDir)
    val neonApiConfig = NeonApiConfig(
        authBaseUrl = BuildConfig.NEON_AUTH_BASE_URL,
        dataApiBaseUrl = BuildConfig.NEON_DATA_API_URL
    )
    private val neonAuthRepository = NeonAuthRepository(appContext, neonApiConfig)
    val authRepository: AuthRepository = neonAuthRepository
    val dataApiClient = NeonDataApiClient(neonApiConfig, neonAuthRepository)
    val householdMembersRepository: HouseholdMembersRepository = NeonHouseholdMembersRepository(
        context = appContext,
        config = neonApiConfig,
        dataApiClient = dataApiClient,
        authRepository = authRepository,
        databases = financeDatabases
    )
    val financialRepository: FinancialRepository = RoomFinancialRepository(financeDatabases, syncScheduler)
    val budgetRepository: BudgetRepository = RoomBudgetRepository(financeDatabases, syncScheduler)
    val recurringRuleRepository: RecurringRuleRepository = RoomRecurringRuleRepository(
        financeDatabases,
        syncScheduler,
        recurringReminderScheduler
    )
    val debtRepository: DebtRepository = RoomDebtRepository(financeDatabases, syncScheduler)
    val financeSyncRepository: FinanceSyncRepository = NeonFinanceSyncRepository(
        config = neonApiConfig,
        dataApiClient = dataApiClient,
        authRepository = authRepository,
        databases = financeDatabases
    )

    fun closeFinancialDatabases() = financeDatabases.closeAll()
}
