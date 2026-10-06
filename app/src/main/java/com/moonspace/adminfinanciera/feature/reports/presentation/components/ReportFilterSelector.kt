package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDropdownField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDropdownOption
import com.moonspace.adminfinanciera.feature.reports.domain.ReportFilterOption

@Composable
fun ReportFilterSelector(
    label: String,
    options: List<ReportFilterOption>,
    selectedValue: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val dropdownOptions = options.map { option ->
        FinanceDropdownOption(value = option.value, label = option.label)
    }
    FinanceDropdownField(
        label = label,
        options = dropdownOptions,
        selectedValue = selectedValue ?: options.firstOrNull()?.value,
        placeholder = stringResource(R.string.reports_filter_choose),
        onOptionSelected = onSelected,
        modifier = modifier
    )
}
