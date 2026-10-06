package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.feature.reports.domain.GeneratedFinanceReport

@Composable
fun ReportDeliverySheet(
    report: GeneratedFinanceReport,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    FinanceBottomSheet(
        title = stringResource(R.string.reports_delivery_title),
        onDismissRequest = onDismissRequest
    ) {
        Text(
            text = stringResource(R.string.reports_delivery_message, report.fileName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FinanceButton(
            label = stringResource(R.string.reports_save_file),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            variant = FinanceButtonVariant.Primary
        )
        FinanceButton(
            label = stringResource(R.string.reports_share_file),
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            variant = FinanceButtonVariant.Secondary
        )
    }
}
