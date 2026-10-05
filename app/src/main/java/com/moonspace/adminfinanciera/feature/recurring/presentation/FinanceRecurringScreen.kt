package com.moonspace.adminfinanciera.feature.recurring.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceEmptyState
import com.moonspace.adminfinanciera.core.ui.components.FinanceErrorState
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceConfirmDialog
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.recurring.domain.FinanceRecurringRule
import com.moonspace.adminfinanciera.feature.recurring.domain.RecurringRuleDraft
import com.moonspace.adminfinanciera.feature.recurring.domain.todayIsoDate
import com.moonspace.adminfinanciera.feature.recurring.presentation.components.RecurringRuleCard
import com.moonspace.adminfinanciera.feature.recurring.presentation.components.RecurringRuleEditor
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import kotlinx.coroutines.delay

@Composable
fun FinanceRecurringScreen(
    user: AuthUser,
    state: FinanceRecurringUiState,
    onOpenUsers: () -> Unit,
    onRefresh: () -> Unit,
    onSaveRule: (RecurringRuleDraft) -> Unit,
    onSetRuleActive: (String, Boolean) -> Unit,
    onConfirmOccurrence: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    var isShowingEditor by rememberSaveable { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<FinanceRecurringRule?>(null) }
    var ruleToConfirm by remember { mutableStateOf<FinanceRecurringRule?>(null) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var observedSaveVersion by remember { mutableIntStateOf(state.ruleSavedVersion) }
    var observedOccurrenceVersion by remember { mutableIntStateOf(state.occurrenceRegisteredVersion) }
    var today by remember { mutableStateOf(todayIsoDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(DATE_REFRESH_INTERVAL_MILLIS)
            today = todayIsoDate()
        }
    }
    LaunchedEffect(state.ruleSavedVersion) {
        if (state.ruleSavedVersion != observedSaveVersion) {
            observedSaveVersion = state.ruleSavedVersion
            isShowingEditor = false
            editingRule = null
        }
    }
    LaunchedEffect(state.occurrenceRegisteredVersion) {
        if (state.occurrenceRegisteredVersion != observedOccurrenceVersion) {
            observedOccurrenceVersion = state.occurrenceRegisteredVersion
            ruleToConfirm = null
        }
    }

    fun openEditor(rule: FinanceRecurringRule?) {
        onClearMessages()
        editingRule = rule
        editorSession += 1
        isShowingEditor = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = FinanceSpacing.ScreenHorizontal,
            vertical = FinanceSpacing.Medium
        ),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
    ) {
        item {
            Text(
                text = stringResource(R.string.recurring_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        when {
            state.isLoading -> item { FinanceLoadingState(stringResource(R.string.recurring_loading)) }
            state.errorMessage != null -> item {
                FinanceErrorState(
                    title = stringResource(R.string.recurring_load_failed),
                    description = state.errorMessage,
                    retryLabel = stringResource(R.string.recurring_retry),
                    onRetry = onRefresh
                )
            }
            !state.hasHousehold -> item {
                FinanceEmptyState(
                    title = stringResource(R.string.transactions_no_household_title),
                    description = stringResource(R.string.transactions_no_household_description),
                    actionLabel = stringResource(R.string.transactions_open_users),
                    onAction = onOpenUsers
                )
            }
            else -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
                        state.noticeMessage?.let {
                            FinanceStatusBanner(it, tone = FinanceStatusTone.Success)
                        }
                        if (state.actionErrorMessage != null && !isShowingEditor && ruleToConfirm == null) {
                            FinanceStatusBanner(state.actionErrorMessage, tone = FinanceStatusTone.Error)
                        }
                        FinanceButton(
                            label = stringResource(R.string.recurring_add_rule),
                            onClick = { openEditor(null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FinanceSectionHeader(
                            title = stringResource(R.string.recurring_list_title),
                            supportingText = stringResource(R.string.recurring_visible_count, state.rules.size)
                        )
                    }
                }
                if (state.rules.isEmpty()) {
                    item {
                        FinanceEmptyState(
                            title = stringResource(R.string.recurring_empty_title),
                            description = stringResource(R.string.recurring_empty_description),
                            actionLabel = stringResource(R.string.recurring_add_rule),
                            onAction = { openEditor(null) }
                        )
                    }
                } else {
                    items(state.rules, key = FinanceRecurringRule::id) { rule ->
                        RecurringRuleCard(
                            rule = rule,
                            currentUserId = user.id,
                            ownerEmail = state.memberEmails[rule.createdBy],
                            today = today,
                            onEdit = { openEditor(rule) },
                            onSetActive = { active -> onSetRuleActive(rule.id, active) },
                            onConfirmOccurrence = { ruleToConfirm = rule }
                        )
                    }
                }
            }
        }
    }

    if (isShowingEditor && state.hasHousehold) {
        FinanceBottomSheet(
            title = stringResource(
                if (editingRule == null) R.string.recurring_editor_add_title
                else R.string.recurring_editor_edit_title
            ),
            onDismissRequest = {
                if (!state.isSubmitting) {
                    isShowingEditor = false
                    editingRule = null
                    onClearMessages()
                }
            }
        ) {
            androidx.compose.runtime.key(editorSession) {
                RecurringRuleEditor(
                    sessionKey = editorSession,
                    rule = editingRule,
                    categories = state.categories,
                    isSubmitting = state.isSubmitting,
                    actionErrorMessage = state.actionErrorMessage,
                    onSave = onSaveRule
                )
            }
        }
    }

    ruleToConfirm?.let { rule ->
        FinanceConfirmDialog(
            title = stringResource(R.string.recurring_confirm_title),
            message = buildString {
                append(stringResource(
                R.string.recurring_confirm_message,
                if (rule.kind == TransactionKind.Income) stringResource(R.string.transactions_income)
                else stringResource(R.string.transactions_expense),
                rule.categoryName,
                formatAmount(rule.amountCentavos),
                formatLocalDate(rule.nextDueOn)
                ))
                state.actionErrorMessage?.let { append("\n\n$it") }
            },
            confirmLabel = stringResource(R.string.recurring_confirm_register),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onConfirmOccurrence(rule.id) },
            onDismissRequest = {
                ruleToConfirm = null
                onClearMessages()
            },
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.recurring_registering)
        )
    }
}

private fun formatAmount(amountCentavos: Long): String =
    "Bs ${java.math.BigDecimal.valueOf(amountCentavos, 2).toPlainString()}"

private fun formatLocalDate(value: String): String {
    val date = com.moonspace.adminfinanciera.feature.recurring.domain.parseIsoDate(value) ?: return value
    return java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(date)
}

private const val DATE_REFRESH_INTERVAL_MILLIS = 60_000L
