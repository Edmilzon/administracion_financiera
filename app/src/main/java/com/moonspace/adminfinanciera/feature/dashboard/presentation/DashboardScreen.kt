package com.moonspace.adminfinanciera.feature.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusPill
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser

@Composable
fun DashboardScreen(
    user: AuthUser,
    isSigningOut: Boolean,
    onSignOut: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FinanceSpacing.ScreenHorizontal, vertical = FinanceSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Large)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FinanceButton(
                label = stringResource(R.string.home_sign_out),
                onClick = onSignOut,
                variant = FinanceButtonVariant.Secondary,
                enabled = !isSigningOut,
                isLoading = isSigningOut,
                loadingLabel = stringResource(R.string.home_signing_out)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_signed_in_as, user.email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        FinanceStatusBanner(
            message = stringResource(R.string.home_storage_pending),
            tone = FinanceStatusTone.Info
        )

        FinanceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            outlined = false,
            shape = FinanceShapes.Panel
        ) {
            Column(modifier = Modifier.padding(FinanceSpacing.Large)) {
                Text(
                    text = stringResource(R.string.home_summary_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(FinanceSpacing.Small))
                Text(
                    text = stringResource(R.string.home_summary_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
            FinanceSectionHeader(
                title = stringResource(R.string.home_menu_title),
                supportingText = stringResource(R.string.home_next_step)
            )
            MenuTile(
                number = "01",
                title = stringResource(R.string.menu_transactions),
                description = stringResource(R.string.menu_transactions_description),
                onClick = onOpenTransactions
            )
            MenuTile(
                number = "02",
                title = stringResource(R.string.menu_budgets),
                description = stringResource(R.string.menu_budgets_description),
                onClick = onOpenBudgets
            )
            MenuTile(
                number = "03",
                title = stringResource(R.string.menu_recurring),
                description = stringResource(R.string.menu_recurring_description),
                onClick = onOpenRecurring
            )
            MenuTile(
                number = "04",
                title = stringResource(R.string.menu_reports),
                description = stringResource(R.string.menu_reports_description)
            )
            MenuTile(
                number = "05",
                title = stringResource(R.string.menu_users),
                description = stringResource(R.string.menu_users_description),
                onClick = onOpenUsers
            )
        }
    }
}

@Composable
private fun MenuTile(
    number: String,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    val modifier = if (onClick == null) Modifier.fillMaxWidth()
    else Modifier.fillMaxWidth().clickable(onClick = onClick)
    FinanceCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Compact),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FinanceCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                outlined = false,
                shape = FinanceShapes.Control
            ) {
                Text(
                    text = number,
                    modifier = Modifier.padding(horizontal = FinanceSpacing.Small, vertical = FinanceSpacing.Medium),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FinanceStatusPill(
                    label = stringResource(
                        if (onClick == null) R.string.menu_coming_soon else R.string.menu_open
                    ),
                    tone = if (onClick == null) FinanceStatusTone.Info else FinanceStatusTone.Success
                )
            }
        }
    }
}
