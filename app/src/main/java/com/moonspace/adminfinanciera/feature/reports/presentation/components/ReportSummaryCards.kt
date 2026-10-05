package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountKind
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountText
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportBreakdown
import com.moonspace.adminfinanciera.feature.reports.presentation.formatReportMoney

@Composable
fun ReportSummaryCard(report: FinanceReport, modifier: Modifier = Modifier) {
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            SummaryRow(
                label = stringResource(R.string.reports_income),
                amount = formatReportMoney(report.total.incomeCentavos),
                kind = FinanceAmountKind.Income
            )
            SummaryRow(
                label = stringResource(R.string.reports_expense),
                amount = formatReportMoney(report.total.expenseCentavos),
                kind = FinanceAmountKind.Expense
            )
            SummaryRow(
                label = stringResource(R.string.reports_net),
                amount = formatReportMoney(report.total.netCentavos),
                kind = FinanceAmountKind.Neutral
            )
            Text(
                text = pluralStringResource(
                    R.plurals.reports_movement_count,
                    report.total.transactionCount,
                    report.total.transactionCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: String, kind: FinanceAmountKind) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FinanceAmountText(
            formattedAmount = amount,
            kind = kind,
            accessibilityLabel = "$label: $amount",
            emphasized = true
        )
    }
}

@Composable
fun ReportBreakdownCard(
    title: String,
    items: List<FinanceReportBreakdown>,
    modifier: Modifier = Modifier
) {
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.reports_movement_count,
                                item.transactionCount,
                                item.transactionCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.reports_breakdown_values,
                            formatReportMoney(item.incomeCentavos),
                            formatReportMoney(item.expenseCentavos),
                            formatReportMoney(item.netCentavos)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
