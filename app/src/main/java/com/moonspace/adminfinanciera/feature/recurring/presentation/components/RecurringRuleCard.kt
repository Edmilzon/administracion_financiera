package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountKind
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountText
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusPill
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.recurring.domain.FinanceRecurringRule
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurrenceFrequency
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun RecurringRuleCard(
    rule: FinanceRecurringRule,
    currentUserId: String,
    ownerEmail: String?,
    today: String,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onConfirmOccurrence: () -> Unit
) {
    val isOwner = currentUserId == rule.createdBy
    val isDue = rule.isDue(today)
    val kind = if (rule.kind == TransactionKind.Income) FinanceAmountKind.Income else FinanceAmountKind.Expense
    val amount = "Bs ${BigDecimal.valueOf(rule.amountCentavos, 2).toPlainString()}"
    val cadence = when {
        rule.intervalCount == 1 -> stringResource(
            when (rule.frequency) {
                RecurrenceFrequency.Daily -> R.string.recurring_every_day
                RecurrenceFrequency.Weekly -> R.string.recurring_every_week
                RecurrenceFrequency.Monthly -> R.string.recurring_every_month
            }
        )
        else -> pluralStringResource(
            when (rule.frequency) {
                RecurrenceFrequency.Daily -> R.plurals.recurring_interval_days_count
                RecurrenceFrequency.Weekly -> R.plurals.recurring_interval_weeks_count
                RecurrenceFrequency.Monthly -> R.plurals.recurring_interval_months_count
            },
            rule.intervalCount,
            rule.intervalCount
        )
    }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (rule.kind == TransactionKind.Income) R.string.transactions_income
                            else R.string.transactions_expense
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FinanceAmountText(
                    formattedAmount = amount,
                    kind = kind,
                    accessibilityLabel = amount,
                    emphasized = true
                )
            }
            rule.description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = cadence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FinanceStatusPill(
                    label = when {
                        !rule.isActive -> stringResource(R.string.recurring_paused)
                        isDue -> stringResource(R.string.recurring_due)
                        else -> stringResource(R.string.recurring_next_due, formatLocalDate(rule.nextDueOn))
                    },
                    tone = when {
                        !rule.isActive -> FinanceStatusTone.Info
                        isDue -> FinanceStatusTone.Warning
                        else -> FinanceStatusTone.Success
                    }
                )
                if (!isOwner) {
                    Text(
                        text = stringResource(R.string.recurring_owner_read_only, ownerEmail ?: stringResource(R.string.budgets_owner_unknown)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isOwner) {
                Spacer(Modifier.height(FinanceSpacing.XSmall))
                if (rule.isActive && isDue) {
                    FinanceButton(
                        label = stringResource(R.string.recurring_register_occurrence),
                        onClick = onConfirmOccurrence,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                    FinanceButton(
                        label = stringResource(R.string.recurring_edit),
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        variant = FinanceButtonVariant.Secondary
                    )
                    FinanceButton(
                        label = stringResource(
                            if (rule.isActive) R.string.recurring_pause else R.string.recurring_activate
                        ),
                        onClick = { onSetActive(!rule.isActive) },
                        modifier = Modifier.weight(1f),
                        variant = FinanceButtonVariant.Text
                    )
                }
            }
        }
    }
}

private fun formatLocalDate(value: String): String = runCatching {
    val source = SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = source.parse(value) ?: return value
    SimpleDateFormat(LOCAL_DATE_PATTERN, Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(date)
}.getOrDefault(value)

private const val ISO_DATE_PATTERN = "yyyy-MM-dd"
private const val LOCAL_DATE_PATTERN = "d MMM yyyy"
