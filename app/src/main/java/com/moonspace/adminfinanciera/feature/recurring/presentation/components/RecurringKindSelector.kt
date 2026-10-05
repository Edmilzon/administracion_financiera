package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind

@Composable
fun RecurringKindSelector(
    selectedKind: TransactionKind,
    onKindSelected: (TransactionKind) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
    ) {
        Text(
            text = stringResource(R.string.transactions_kind_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
            TransactionKind.entries.forEach { kind ->
                val label = stringResource(
                    if (kind == TransactionKind.Income) R.string.transactions_income
                    else R.string.transactions_expense
                )
                FinanceButton(
                    label = label,
                    onClick = { onKindSelected(kind) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    variant = if (kind == selectedKind) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
            }
        }
    }
}
