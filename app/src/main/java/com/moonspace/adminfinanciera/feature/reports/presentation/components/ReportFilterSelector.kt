package com.moonspace.adminfinanciera.feature.reports.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.reports.domain.ReportFilterOption

@Composable
fun ReportFilterSelector(
    label: String,
    options: List<ReportFilterOption>,
    selectedValue: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label
        ?: stringResource(R.string.reports_filter_choose)

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FinanceSpacing.XSmall)
    ) {
        androidx.compose.material3.Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        Box {
            FinanceButton(
                label = selectedLabel,
                onClick = { isExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                variant = FinanceButtonVariant.Secondary
            )
            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { androidx.compose.material3.Text(option.label) },
                        onClick = {
                            isExpanded = false
                            onSelected(option.value)
                        }
                    )
                }
            }
        }
    }
}
