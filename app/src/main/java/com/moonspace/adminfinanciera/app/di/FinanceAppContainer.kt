package com.moonspace.adminfinanciera.app.di

import android.content.Context
import com.moonspace.adminfinanciera.BuildConfig
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.core.sync.FinanceSyncScheduler
import com.moonspace.adminfinanciera.feature.transactions.data.NeonFinanceSyncRepository
import com.moonspace.adminfinanciera.feature.auth.data.NeonAuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository
import com.moonspace.adminfinanciera.feature.users.data.NeonHouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMembersRepository
import com.moonspace.adminfinanciera.feature.transactions.data.RoomFinancialRepository
import com.moonspace.adminfinanciera.feature.transactions.domain.FinancialRepository
import com.moonspace.adminfinanciera.feature.budgets.data.RoomBudgetRepository
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetRepository

class FinanceAppContainer(context: Context) {
    val financeDatabases = EncryptedFinanceDatabaseProvider(context.applicationContext)
    val syncScheduler = FinanceSyncScheduler(context.applicationContext)
    val neonApiConfig = NeonApiConfig(
        authBaseUrl = BuildConfig.NEON_AUTH_BASE_URL,
        dataApiBaseUrl = BuildConfig.NEON_DATA_API_URL
    )
    private val neonAuthRepository = NeonAuthRepository(context.applicationContext, neonApiConfig)
    val authRepository: AuthRepository = neonAuthRepository
    val dataApiClient = NeonDataApiClient(neonApiConfig, neonAuthRepository)
    val householdMembersRepository: HouseholdMembersRepository = NeonHouseholdMembersRepository(
        context = context.applicationContext,
        config = neonApiConfig,
        dataApiClient = dataApiClient,
        authRepository = authRepository,
        databases = financeDatabases
    )
    val financialRepository: FinancialRepository = RoomFinancialRepository(financeDatabases, syncScheduler)
    val budgetRepository: BudgetRepository = RoomBudgetRepository(financeDatabases, syncScheduler)
    val financeSyncRepository = NeonFinanceSyncRepository(
        config = neonApiConfig,
        dataApiClient = dataApiClient,
        authRepository = authRepository,
        databases = financeDatabases
    )

    fun closeFinancialDatabases() = financeDatabases.closeAll()
}
