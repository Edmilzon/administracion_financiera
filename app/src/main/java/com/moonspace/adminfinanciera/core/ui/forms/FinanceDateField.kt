package com.moonspace.adminfinanciera.core.ui.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun FinanceDateField(
    value: String,
    label: String,
    chooseDateLabel: String,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FinanceTextField(
        value = value,
        onValueChange = {},
        label = label,
        modifier = modifier,
        enabled = enabled,
        readOnly = true,
        trailingContent = {
            FinanceButton(
                label = chooseDateLabel,
                onClick = onOpenPicker,
                variant = FinanceButtonVariant.Text,
                enabled = enabled
            )
        }
    )
}

fun formatFinanceDate(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
    val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(epochMillis))
}
