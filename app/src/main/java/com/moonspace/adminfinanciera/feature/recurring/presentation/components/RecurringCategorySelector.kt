package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDropdownField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDropdownOption
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory

@Composable
fun RecurringCategorySelector(
    categories: List<FinanceCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (FinanceCategory) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FinanceDropdownField(
        label = stringResource(R.string.transactions_category_label),
        options = categories.map { category ->
            FinanceDropdownOption(value = category.id, label = category.name)
        },
        selectedValue = selectedCategoryId,
        placeholder = stringResource(R.string.transactions_choose_category),
        onOptionSelected = { id ->
            categories.firstOrNull { it.id == id }?.let(onCategorySelected)
        },
        modifier = modifier,
        enabled = enabled
    )
}
