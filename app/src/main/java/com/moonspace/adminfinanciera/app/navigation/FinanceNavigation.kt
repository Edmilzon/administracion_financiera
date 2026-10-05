package com.moonspace.adminfinanciera.app.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.app.di.FinanceAppContainer
import com.moonspace.adminfinanciera.core.ui.components.FinanceBottomNavigationBar
import com.moonspace.adminfinanciera.core.ui.components.FinanceNavigationDestination
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.feature.auth.presentation.AuthViewModel
import com.moonspace.adminfinanciera.feature.auth.presentation.LoginScreen
import com.moonspace.adminfinanciera.feature.dashboard.presentation.DashboardScreen
import com.moonspace.adminfinanciera.feature.budgets.presentation.FinanceBudgetsScreen
import com.moonspace.adminfinanciera.feature.budgets.presentation.FinanceBudgetsViewModel
import com.moonspace.adminfinanciera.feature.transactions.presentation.FinanceTransactionsScreen
import com.moonspace.adminfinanciera.feature.transactions.presentation.FinanceTransactionsViewModel
import com.moonspace.adminfinanciera.feature.users.presentation.HouseholdMembersScreen
import com.moonspace.adminfinanciera.feature.users.presentation.HouseholdMembersViewModel
import com.moonspace.adminfinanciera.feature.recurring.presentation.FinanceRecurringScreen
import com.moonspace.adminfinanciera.feature.recurring.presentation.FinanceRecurringViewModel

@Composable
fun FinanceNavigation() {
    val currentContext = LocalContext.current
    val activity = remember(currentContext) { currentContext.findActivity() }
    val context = currentContext.applicationContext
    val container = remember(context) { FinanceAppContainer(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
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
        LaunchedEffect(user.id) {
            container.syncScheduler.scheduleForSignedInAccount(user.id)
            container.recurringReminderScheduler.scheduleDaily(user.id)
        }
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
        val budgetsViewModel: FinanceBudgetsViewModel = viewModel(
            factory = remember(container) {
                FinanceBudgetsViewModel.Factory(
                    context,
                    container.householdMembersRepository,
                    container.financialRepository,
                    container.budgetRepository
                )
            }
        )
        val budgetsState by budgetsViewModel.uiState.collectAsStateWithLifecycle()
        val recurringViewModel: FinanceRecurringViewModel = viewModel(
            factory = remember(container) {
                FinanceRecurringViewModel.Factory(
                    context,
                    container.householdMembersRepository,
                    container.financialRepository,
                    container.recurringRuleRepository
                )
            }
        )
        val recurringState by recurringViewModel.uiState.collectAsStateWithLifecycle()
        var destination by rememberSaveable(user.id) {
            mutableStateOf(
                if (activity?.intent?.getBooleanExtra(com.moonspace.adminfinanciera.app.MainActivity.EXTRA_OPEN_RECURRING, false) == true) {
                    DESTINATION_RECURRING
                } else {
                    DESTINATION_DASHBOARD
                }
            )
        }
        var askedForNotificationPermission by rememberSaveable(user.id) { mutableStateOf(false) }

        LaunchedEffect(user.id, destination) {
            when (destination) {
                DESTINATION_USERS -> membersViewModel.load(user)
                DESTINATION_TRANSACTIONS -> transactionsViewModel.load(user)
                DESTINATION_BUDGETS -> budgetsViewModel.load(user)
                DESTINATION_RECURRING -> {
                    recurringViewModel.load(user)
                    if (!askedForNotificationPermission) {
                        askedForNotificationPermission = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                currentContext,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }

        val navigationDestinations = remember {
            listOf(
                FinanceNavigationDestination(DESTINATION_DASHBOARD, R.string.nav_home, R.drawable.ic_nav_home),
                FinanceNavigationDestination(
                    DESTINATION_TRANSACTIONS,
                    R.string.nav_transactions,
                    R.drawable.ic_nav_transactions
                ),
                FinanceNavigationDestination(DESTINATION_BUDGETS, R.string.nav_budgets, R.drawable.ic_nav_budgets),
                FinanceNavigationDestination(
                    DESTINATION_RECURRING,
                    R.string.nav_recurring,
                    R.drawable.ic_nav_recurring
                ),
                FinanceNavigationDestination(DESTINATION_USERS, R.string.nav_users, R.drawable.ic_nav_users)
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            ),
            bottomBar = {
                FinanceBottomNavigationBar(
                    selectedDestination = destination,
                    destinations = navigationDestinations,
                    onDestinationSelected = { destination = it }
                )
            }
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
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
                } else if (destination == DESTINATION_BUDGETS) {
                    FinanceBudgetsScreen(
                        user = user,
                        state = budgetsState,
                        onBack = { destination = DESTINATION_DASHBOARD },
                        onOpenUsers = { destination = DESTINATION_USERS },
                        onRefresh = { budgetsViewModel.load(user) },
                        onSaveBudget = budgetsViewModel::saveBudget,
                        onDeleteBudget = budgetsViewModel::deleteBudget,
                        onMoveMonth = budgetsViewModel::moveMonthBy,
                        onClearMessages = budgetsViewModel::clearMessages
                    )
                } else if (destination == DESTINATION_RECURRING) {
                    FinanceRecurringScreen(
                        user = user,
                        state = recurringState,
                        onBack = { destination = DESTINATION_DASHBOARD },
                        onOpenUsers = { destination = DESTINATION_USERS },
                        onRefresh = { recurringViewModel.load(user) },
                        onSaveRule = recurringViewModel::saveRule,
                        onSetRuleActive = recurringViewModel::setRuleActive,
                        onConfirmOccurrence = recurringViewModel::confirmOccurrence,
                        onClearMessages = recurringViewModel::clearMessages
                    )
                } else {
                    DashboardScreen(
                        user = user,
                        isSigningOut = uiState.isSubmitting,
                        onSignOut = {
                            transactionsViewModel.clearAccountContext()
                            budgetsViewModel.clearAccountContext()
                            recurringViewModel.clearAccountContext()
                            container.syncScheduler.cancelAccount(user.id)
                            container.recurringReminderScheduler.cancelAccount(user.id)
                            container.closeFinancialDatabases()
                            authViewModel.signOut()
                        },
                        onOpenUsers = { destination = DESTINATION_USERS },
                        onOpenTransactions = { destination = DESTINATION_TRANSACTIONS },
                        onOpenBudgets = { destination = DESTINATION_BUDGETS },
                        onOpenRecurring = { destination = DESTINATION_RECURRING }
                    )
                }
            }
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
private const val DESTINATION_BUDGETS = "budgets"
private const val DESTINATION_RECURRING = "recurring"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SessionLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            FinanceLoadingState(message = stringResource(R.string.session_loading))
        }
    }
}
