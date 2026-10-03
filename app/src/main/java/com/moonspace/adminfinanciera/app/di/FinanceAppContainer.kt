package com.moonspace.adminfinanciera.app.di

import android.content.Context
import com.moonspace.adminfinanciera.BuildConfig
import com.moonspace.adminfinanciera.core.network.NeonApiConfig
import com.moonspace.adminfinanciera.core.network.NeonDataApiClient
import com.moonspace.adminfinanciera.feature.auth.data.NeonAuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository

class FinanceAppContainer(context: Context) {
    val neonApiConfig = NeonApiConfig(
        authBaseUrl = BuildConfig.NEON_AUTH_BASE_URL,
        dataApiBaseUrl = BuildConfig.NEON_DATA_API_URL
    )
    private val neonAuthRepository = NeonAuthRepository(context.applicationContext, neonApiConfig)
    val authRepository: AuthRepository = neonAuthRepository
    val dataApiClient = NeonDataApiClient(neonApiConfig, neonAuthRepository)
}
