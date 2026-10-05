package com.moonspace.adminfinanciera.feature.recurring.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory

@Composable
fun RecurringCategorySelector(
    categories: List<FinanceCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (FinanceCategory) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    Box(modifier = modifier.fillMaxWidth()) {
        FinanceButton(
            label = selected?.name ?: stringResource(R.string.transactions_choose_category),
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && categories.isNotEmpty(),
            variant = FinanceButtonVariant.Secondary
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        expanded = false
                        onCategorySelected(category)
                    }
                )
            }
        }
    }
}
