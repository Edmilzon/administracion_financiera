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
import com.moonspace.adminfinanciera.feature.transactions.presentation.FinanceTransactionsScreen
import com.moonspace.adminfinanciera.feature.transactions.presentation.FinanceTransactionsViewModel
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
        val transactionsViewModel: FinanceTransactionsViewModel = viewModel(
            factory = remember(container) {
                FinanceTransactionsViewModel.Factory(
                    context,
                    container.householdMembersRepository,
                    container.financialRepository
                )
            }
        )
        val transactionsState by transactionsViewModel.uiState.collectAsStateWithLifecycle()
        var destination by rememberSaveable(user.id) { mutableStateOf("dashboard") }

        LaunchedEffect(user.id, destination) {
            when (destination) {
                DESTINATION_USERS -> membersViewModel.load(user)
                DESTINATION_TRANSACTIONS -> transactionsViewModel.load(user)
            }
        }

        if (destination == DESTINATION_USERS) {
            HouseholdMembersScreen(
                user = user,
                state = membersState,
                onBack = { destination = DESTINATION_DASHBOARD },
                onRefresh = { membersViewModel.load(user) },
                onCreateHousehold = { membersViewModel.createHousehold(user, it) },
                onCreateMember = { email, password, role ->
                    membersViewModel.createMember(user, email, password, role)
                },
                onUpdateRole = { userId, role -> membersViewModel.updateRole(user, userId, role) },
                onRemoveMember = { userId -> membersViewModel.removeMember(user, userId) }
            )
        } else if (destination == DESTINATION_TRANSACTIONS) {
            FinanceTransactionsScreen(
                user = user,
                state = transactionsState,
                onBack = { destination = DESTINATION_DASHBOARD },
                onOpenUsers = { destination = DESTINATION_USERS },
                onRefresh = { transactionsViewModel.load(user) },
                onSaveTransaction = transactionsViewModel::saveTransaction,
                onDeleteTransaction = transactionsViewModel::deleteTransaction,
                onSaveCategory = transactionsViewModel::saveCategory,
                onDeactivateCategory = transactionsViewModel::deactivateCategory,
                onSelectMonth = transactionsViewModel::selectMonth,
                onSelectKind = transactionsViewModel::selectKind,
                onSelectMember = transactionsViewModel::selectMember,
                onClearFilters = transactionsViewModel::clearFilters,
                onClearMessages = transactionsViewModel::clearMessages
            )
        } else {
            DashboardScreen(
                user = user,
                isSigningOut = uiState.isSubmitting,
                onSignOut = {
                    transactionsViewModel.clearAccountContext()
                    container.closeFinancialDatabases()
                    authViewModel.signOut()
                },
                onOpenUsers = { destination = DESTINATION_USERS },
                onOpenTransactions = { destination = DESTINATION_TRANSACTIONS }
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

private const val DESTINATION_DASHBOARD = "dashboard"
private const val DESTINATION_USERS = "users"
private const val DESTINATION_TRANSACTIONS = "transactions"

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
