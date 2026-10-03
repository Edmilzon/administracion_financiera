package com.moonspace.adminfinanciera.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.moonspace.adminfinanciera.core.ui.theme.FinanceAmountStyle
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSemanticColorScheme
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

enum class FinanceAmountKind {
    Income,
    Expense,
    Neutral
}

@Composable
fun FinanceAmountText(
    formattedAmount: String,
    kind: FinanceAmountKind,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val palette = FinanceSemanticColorScheme
    val (sign, color) = when (kind) {
        FinanceAmountKind.Income -> "+" to palette.success
        FinanceAmountKind.Expense -> "−" to palette.expense
        FinanceAmountKind.Neutral -> "" to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "$sign$formattedAmount",
        modifier = modifier.semantics { contentDescription = accessibilityLabel },
        style = if (emphasized) FinanceAmountStyle else MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1
    )
}

@Composable
fun FinanceSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FinanceStatusPill(
    label: String,
    tone: FinanceStatusTone,
    modifier: Modifier = Modifier
) {
    val palette = FinanceSemanticColorScheme
    val (container, content) = when (tone) {
        FinanceStatusTone.Info -> palette.infoContainer to palette.onInfoContainer
        FinanceStatusTone.Success -> palette.successContainer to palette.onSuccessContainer
        FinanceStatusTone.Warning -> palette.warningContainer to palette.onWarningContainer
        FinanceStatusTone.Error -> palette.expenseContainer to palette.onExpenseContainer
    }
    Surface(
        modifier = modifier,
        shape = FinanceShapes.Pill,
        color = container
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = FinanceSpacing.Small, vertical = FinanceSpacing.XSmall),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

@Composable
fun FinanceEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(FinanceSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(FinanceSpacing.XSmall))
            FinanceButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun FinanceErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
    ) {
        FinanceStatusBanner(
            title = title,
            message = description,
            tone = FinanceStatusTone.Error
        )
        if (retryLabel != null && onRetry != null) {
            FinanceButton(label = retryLabel, onClick = onRetry)
        }
    }
}

@Composable
fun FinanceLoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(FinanceSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
