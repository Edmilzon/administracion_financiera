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
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountKind
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountText
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportRow
import com.moonspace.adminfinanciera.feature.reports.presentation.formatReportDate
import com.moonspace.adminfinanciera.feature.reports.presentation.formatReportMoney
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind

@Composable
fun ReportMovementCard(row: FinanceReportRow, modifier: Modifier = Modifier) {
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
            ) {
                Text(
                    text = row.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.reports_movement_metadata,
                        formatReportDate(row.occurredOn),
                        if (row.kind == TransactionKind.Income) {
                            stringResource(R.string.transactions_income)
                        } else {
                            stringResource(R.string.transactions_expense)
                        },
                        row.memberLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FinanceAmountText(
                formattedAmount = formatReportMoney(row.amountCentavos),
                kind = if (row.kind == TransactionKind.Income) FinanceAmountKind.Income else FinanceAmountKind.Expense,
                accessibilityLabel = "${if (row.kind == TransactionKind.Income) stringResource(R.string.transactions_income) else stringResource(R.string.transactions_expense)}, ${formatReportMoney(row.amountCentavos)}",
                emphasized = true
            )
        }
    }
}
