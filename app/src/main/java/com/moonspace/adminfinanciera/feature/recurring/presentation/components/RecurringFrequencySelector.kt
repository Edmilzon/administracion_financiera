package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentOption
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentedSelector
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurrenceFrequency

@Composable
fun RecurringFrequencySelector(
    selectedFrequency: RecurrenceFrequency,
    onFrequencySelected: (RecurrenceFrequency) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FinanceSegmentedSelector(
        label = stringResource(R.string.recurring_frequency_label),
        options = RecurrenceFrequency.entries.map { frequency ->
            FinanceSegmentOption(
                frequency,
                stringResource(
                    when (frequency) {
                        RecurrenceFrequency.Daily -> R.string.recurring_frequency_daily
                        RecurrenceFrequency.Weekly -> R.string.recurring_frequency_weekly
                        RecurrenceFrequency.Monthly -> R.string.recurring_frequency_monthly
                    }
                )
            )
        },
        selectedValue = selectedFrequency,
        onSelected = onFrequencySelected,
        modifier = modifier,
        enabled = enabled
    )
}
