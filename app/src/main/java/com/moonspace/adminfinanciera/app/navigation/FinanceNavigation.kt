package com.moonspace.adminfinanciera.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.app.di.FinanceAppContainer
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.feature.auth.presentation.AuthViewModel
import com.moonspace.adminfinanciera.feature.auth.presentation.LoginScreen
import com.moonspace.adminfinanciera.feature.dashboard.presentation.DashboardScreen

@Composable
fun FinanceNavigation() {
    val context = LocalContext.current.applicationContext
    val container = remember(context) { FinanceAppContainer(context) }
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(container) { AuthViewModel.Factory(container.authRepository) }
    )
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authViewModel) {
        authViewModel.restoreSession()
    }

    when {
        uiState.isCheckingSession -> SessionLoadingScreen()
        uiState.user != null -> DashboardScreen(
            user = requireNotNull(uiState.user),
            isSigningOut = uiState.isSubmitting,
            onSignOut = authViewModel::signOut
        )

        else -> LoginScreen(
            hasLocalAccount = uiState.hasLocalAccount,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.errorMessage,
            onCreateLocalAccount = authViewModel::createLocalAccount,
            onSignIn = authViewModel::signIn
        )
    }
}

@Composable
private fun SessionLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            FinanceLoadingState(message = stringResource(R.string.session_loading))
        }
    }
}
