package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceDatePickerDialog
import com.moonspace.adminfinanciera.core.ui.forms.FinanceAmountField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.forms.parsePositiveAmountToCentavos
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.recurring.domain.FinanceRecurringRule
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurrenceFrequency
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleDraft
import com.moonspace.adminfinanciera.feature.recurring.domain.parseIsoDate
import com.moonspace.adminfinanciera.feature.recurring.domain.todayIsoDate
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun RecurringRuleEditor(
    sessionKey: Int,
    rule: FinanceRecurringRule?,
    categories: List<FinanceCategory>,
    isSubmitting: Boolean,
    actionErrorMessage: String?,
    onSave: (RecurringRuleDraft) -> Unit
) {
    var kindValue by rememberSaveable(sessionKey) {
        mutableStateOf(rule?.kind?.apiValue ?: TransactionKind.Expense.apiValue)
    }
    var amount by rememberSaveable(sessionKey) {
        mutableStateOf(rule?.amountCentavos?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty())
    }
    var categoryId by rememberSaveable(sessionKey) { mutableStateOf(rule?.categoryId) }
    var description by rememberSaveable(sessionKey) { mutableStateOf(rule?.description.orEmpty()) }
    var frequencyValue by rememberSaveable(sessionKey) {
        mutableStateOf(rule?.frequency?.apiValue ?: RecurrenceFrequency.Monthly.apiValue)
    }
    var intervalText by rememberSaveable(sessionKey) {
        mutableStateOf((rule?.intervalCount ?: 1).toString())
    }
    var startOn by rememberSaveable(sessionKey) { mutableStateOf(rule?.startOn ?: todayIsoDate()) }
    var isDatePickerShowing by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var showValidation by rememberSaveable(sessionKey) { mutableStateOf(false) }

    val kind = TransactionKind.fromApiValue(kindValue) ?: TransactionKind.Expense
    val frequency = RecurrenceFrequency.fromApiValue(frequencyValue) ?: RecurrenceFrequency.Monthly
    val availableCategories = categories.filter {
        it.kind == kind && (it.isActive || it.id == categoryId)
    }
    val selectedCategoryAvailable = availableCategories.any { it.id == categoryId }
    val parsedAmount = parsePositiveAmountToCentavos(amount)
    val parsedInterval = intervalText.toIntOrNull()?.takeIf { it in 1..3650 }
    val validStartDate = parseIsoDate(startOn) != null

    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        RecurringKindSelector(
            selectedKind = kind,
            onKindSelected = { selected -> kindValue = selected.apiValue },
            enabled = !isSubmitting
        )
        FinanceAmountField(
            value = amount,
            onValueChange = { amount = it },
            label = stringResource(R.string.transactions_amount_label),
            currencyLabel = stringResource(R.string.transactions_currency),
            enabled = !isSubmitting,
            isError = showValidation && parsedAmount == null,
            supportingText = if (showValidation && parsedAmount == null) {
                stringResource(R.string.recurring_invalid_amount)
            } else null
        )
        Text(
            text = stringResource(R.string.transactions_category_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        RecurringCategorySelector(
            categories = availableCategories,
            selectedCategoryId = categoryId,
            onCategorySelected = { categoryId = it.id },
            enabled = !isSubmitting
        )
        if (showValidation && (!selectedCategoryAvailable || categoryId == null)) {
            Text(
                text = stringResource(R.string.recurring_category_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        RecurringFrequencySelector(
            selectedFrequency = frequency,
            onFrequencySelected = { frequencyValue = it.apiValue },
            enabled = !isSubmitting
        )
        FinanceTextField(
            value = intervalText,
            onValueChange = { intervalText = it.filter(Char::isDigit).take(4) },
            label = stringResource(
                when (frequency) {
                    RecurrenceFrequency.Daily -> R.string.recurring_interval_days
                    RecurrenceFrequency.Weekly -> R.string.recurring_interval_weeks
                    RecurrenceFrequency.Monthly -> R.string.recurring_interval_months
                }
            ),
            enabled = !isSubmitting,
            isError = showValidation && parsedInterval == null,
            supportingText = if (showValidation && parsedInterval == null) {
                stringResource(R.string.recurring_invalid_interval)
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        FinanceButton(
            label = stringResource(R.string.recurring_start_date, formatLocalDate(startOn)),
            onClick = { isDatePickerShowing = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        )
        if (showValidation && !validStartDate) {
            Text(
                text = stringResource(R.string.recurring_invalid_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        FinanceTextField(
            value = description,
            onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
            label = stringResource(R.string.transactions_description_label),
            enabled = !isSubmitting,
            singleLine = false
        )
        actionErrorMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Error) }
        FinanceButton(
            label = stringResource(R.string.recurring_save_rule),
            onClick = {
                showValidation = true
                val selectedCategoryId = categoryId
                if (parsedAmount != null && parsedInterval != null && validStartDate &&
                    selectedCategoryId != null && selectedCategoryAvailable
                ) {
                    onSave(
                        RecurringRuleDraft(
                            id = rule?.id,
                            kind = kind,
                            amountCentavos = parsedAmount,
                            categoryId = selectedCategoryId,
                            description = description,
                            frequency = frequency,
                            intervalCount = parsedInterval,
                            startOn = startOn
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.recurring_saving_rule)
        )
    }

    if (isDatePickerShowing) {
        FinanceDatePickerDialog(
            title = stringResource(R.string.recurring_start_date_title),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = parseIsoDate(startOn)?.time,
            onDateSelected = { selectedMillis ->
                startOn = isoDateFromPicker(selectedMillis)
                isDatePickerShowing = false
            },
            onDismissRequest = { isDatePickerShowing = false }
        )
    }
}

private fun isoDateFromPicker(millis: Long): String = SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date(millis))

private fun formatLocalDate(value: String): String {
    val date = parseIsoDate(value) ?: return value
    return SimpleDateFormat(LOCAL_DATE_PATTERN, Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(date)
}

private const val MAX_DESCRIPTION_LENGTH = 120
private const val ISO_DATE_PATTERN = "yyyy-MM-dd"
private const val LOCAL_DATE_PATTERN = "d MMM yyyy"
