package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.reports.domain.GeneratedFinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.ReportExportFormat

@Composable
fun ReportExportActions(
    isGenerating: Boolean,
    generatedFile: GeneratedFinanceReport?,
    onGeneratePdf: () -> Unit,
    onGenerateExcel: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Text(
                text = stringResource(R.string.reports_export_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.reports_export_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (generatedFile == null) {
                FinanceButton(
                    label = stringResource(R.string.reports_generate_pdf),
                    onClick = onGeneratePdf,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating,
                    isLoading = isGenerating,
                    loadingLabel = stringResource(R.string.reports_generating),
                    variant = FinanceButtonVariant.Primary
                )
                FinanceButton(
                    label = stringResource(R.string.reports_generate_excel),
                    onClick = onGenerateExcel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating,
                    variant = FinanceButtonVariant.Secondary
                )
            } else {
                Text(
                    text = stringResource(R.string.reports_file_ready, generatedFile.fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
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
                if (generatedFile.format == ReportExportFormat.Pdf) {
                    FinanceButton(
                        label = stringResource(R.string.reports_print_file),
                        onClick = onPrint,
                        modifier = Modifier.fillMaxWidth(),
                        variant = FinanceButtonVariant.Text
                    )
                }
            }
        }
    }
}
