package com.moonspace.adminfinanciera.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.moonspace.adminfinanciera.feature.users.presentation.HouseholdMembersScreen
import com.moonspace.adminfinanciera.feature.users.presentation.HouseholdMembersViewModel

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

    if (uiState.isCheckingSession) {
        SessionLoadingScreen()
    } else if (uiState.user != null) {
        val user = requireNotNull(uiState.user)
        val membersViewModel: HouseholdMembersViewModel = viewModel(
            factory = remember(container) {
                HouseholdMembersViewModel.Factory(context, container.householdMembersRepository)
            }
        )
        val membersState by membersViewModel.uiState.collectAsStateWithLifecycle()
        var isShowingMembers by rememberSaveable(user.id) { mutableStateOf(false) }

        LaunchedEffect(user.id, isShowingMembers) {
            if (isShowingMembers) membersViewModel.load(user)
        }

        if (isShowingMembers) {
            HouseholdMembersScreen(
                user = user,
                state = membersState,
                onBack = { isShowingMembers = false },
                onRefresh = { membersViewModel.load(user) },
                onCreateHousehold = { membersViewModel.createHousehold(user, it) },
                onCreateMember = { email, password, role ->
                    membersViewModel.createMember(user, email, password, role)
                },
                onUpdateRole = { userId, role -> membersViewModel.updateRole(user, userId, role) },
                onRemoveMember = { userId -> membersViewModel.removeMember(user, userId) }
            )
        } else {
            DashboardScreen(
                user = user,
                isSigningOut = uiState.isSubmitting,
                onSignOut = authViewModel::signOut,
                onOpenUsers = { isShowingMembers = true }
            )
        }
    } else {
        LoginScreen(
            isAuthConfigured = uiState.isAuthConfigured,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.errorMessage,
            noticeMessage = uiState.noticeMessage,
            onCreateAccount = authViewModel::createAccount,
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
