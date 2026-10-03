package com.moonspace.adminfinanciera.app.di

import android.content.Context
import com.moonspace.adminfinanciera.feature.auth.data.LocalAuthRepository
import com.moonspace.adminfinanciera.feature.auth.domain.AuthRepository

class FinanceAppContainer(context: Context) {
    val authRepository: AuthRepository = LocalAuthRepository(context.applicationContext)
}
