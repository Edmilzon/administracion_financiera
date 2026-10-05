package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing

@Composable
fun ReportPeriodSelector(
    startLabel: String,
    endLabel: String,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
    ) {
        FinanceButton(
            label = stringResource(R.string.reports_from, startLabel),
            onClick = onStartClick,
            modifier = Modifier.weight(1f),
            variant = FinanceButtonVariant.Secondary,
            compact = true
        )
        FinanceButton(
            label = stringResource(R.string.reports_until, endLabel),
            onClick = onEndClick,
            modifier = Modifier.weight(1f),
            variant = FinanceButtonVariant.Secondary,
            compact = true
        )
    }
}
