package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentOption
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentedSelector
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind

@Composable
fun RecurringKindSelector(
    selectedKind: TransactionKind,
    onKindSelected: (TransactionKind) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    FinanceSegmentedSelector(
        label = stringResource(R.string.transactions_kind_label),
        options = listOf(
            FinanceSegmentOption(
                TransactionKind.Expense,
                stringResource(R.string.transactions_expense)
            ),
            FinanceSegmentOption(
                TransactionKind.Income,
                stringResource(R.string.transactions_income)
            )
        ),
        selectedValue = selectedKind,
        onSelected = onKindSelected,
        modifier = modifier,
        enabled = enabled
    )
}
