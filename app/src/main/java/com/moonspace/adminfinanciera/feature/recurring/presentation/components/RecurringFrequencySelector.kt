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
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurrenceFrequency

@Composable
fun RecurringFrequencySelector(
    selectedFrequency: RecurrenceFrequency,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
    ) {
        Text(
            text = stringResource(R.string.recurring_frequency_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
            RecurrenceFrequency.entries.forEach { frequency ->
                val label = stringResource(
                    when (frequency) {
                        RecurrenceFrequency.Daily -> R.string.recurring_frequency_daily
                        RecurrenceFrequency.Weekly -> R.string.recurring_frequency_weekly
                        RecurrenceFrequency.Monthly -> R.string.recurring_frequency_monthly
                    }
                )
                FinanceButton(
                    label = label,
                    onClick = { onFrequencySelected(frequency) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    variant = if (frequency == selectedFrequency) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
            }
        }
    }
}
