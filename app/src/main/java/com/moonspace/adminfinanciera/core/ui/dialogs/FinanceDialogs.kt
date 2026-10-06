package com.moonspace.adminfinanciera.core.ui.dialogs

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.theme.FinanceShapes
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import androidx.compose.ui.res.stringResource

@Composable
fun FinanceConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    isProcessing: Boolean = false,
    processingLabel: String = confirmLabel
) {
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismissRequest() },
        modifier = modifier,
        shape = FinanceShapes.Card,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            FinanceButton(
                label = confirmLabel,
                onClick = onConfirm,
                variant = if (isDestructive) FinanceButtonVariant.Destructive else FinanceButtonVariant.Primary,
                enabled = !isProcessing,
                isLoading = isProcessing,
                loadingLabel = processingLabel
            )
        },
        dismissButton = {
            FinanceButton(
                label = dismissLabel,
                onClick = onDismissRequest,
                variant = FinanceButtonVariant.Text,
                enabled = !isProcessing
            )
        }
    )
}

@Composable
fun FinanceMessageDialog(
    title: String,
    message: String,
    actionLabel: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = FinanceShapes.Card,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            FinanceButton(
                label = actionLabel,
                onClick = onDismissRequest,
                variant = FinanceButtonVariant.Primary
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDatePickerDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    initialDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = FinanceShapes.Card,
        confirmButton = {
            FinanceButton(
                label = confirmLabel,
                onClick = { datePickerState.selectedDateMillis?.let(onDateSelected) },
                enabled = datePickerState.selectedDateMillis != null
            )
        },
        dismissButton = {
            FinanceButton(
                label = dismissLabel,
                onClick = onDismissRequest,
                variant = FinanceButtonVariant.Text
            )
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            showModeToggle = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = FinanceShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = FinanceSpacing.XSmall
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FinanceSpacing.Medium)
                .padding(bottom = FinanceSpacing.Large)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                FinanceButton(
                    label = stringResource(R.string.ui_close),
                    onClick = onDismissRequest,
                    variant = FinanceButtonVariant.Text
                )
            }
            content()
        }
    }
}
