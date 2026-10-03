package com.moonspace.adminfinanciera.core.ui.forms

import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun FinanceAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currencyLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null
) {
    FinanceTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        prefixContent = { Text(currencyLabel) }
    )
}

/** Accepts a positive amount with at most two decimal places and returns exact centavos. */
fun parsePositiveAmountToCentavos(input: String): Long? {
    val normalized = input.trim().replace(',', '.')
    if (!normalized.matches(Regex("^\\d+(\\.\\d{1,2})?$"))) return null
    return runCatching {
        java.math.BigDecimal(normalized)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it > 0 }
    }.getOrNull()
}
