package com.moonspace.adminfinanciera.feature.transactions.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountKind
import com.moonspace.adminfinanciera.core.ui.components.FinanceAmountText
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceCard
import com.moonspace.adminfinanciera.core.ui.components.FinanceEmptyState
import com.moonspace.adminfinanciera.core.ui.components.FinanceErrorState
import com.moonspace.adminfinanciera.core.ui.components.FinanceListRow
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceConfirmDialog
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceDatePickerDialog
import com.moonspace.adminfinanciera.core.ui.forms.FinanceAmountField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDateField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.forms.parsePositiveAmountToCentavos
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.transactions.domain.CategoryDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceMemberTotal
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransactionReport
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionDraft
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import com.moonspace.adminfinanciera.feature.transactions.domain.buildFinanceTransactionReport
import com.moonspace.adminfinanciera.feature.transactions.domain.filterFinanceTransactions
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.text.NumberFormat

@Composable
fun FinanceTransactionsScreen(
    user: AuthUser,
    state: FinanceTransactionsUiState,
    onBack: () -> Unit,
    onOpenUsers: () -> Unit,
    onRefresh: () -> Unit,
    onSaveTransaction: (TransactionDraft) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onSaveCategory: (CategoryDraft) -> Unit,
    onDeactivateCategory: (String) -> Unit,
    onSelectMonth: (String?) -> Unit,
    onSelectKind: (TransactionKind?) -> Unit,
    onSelectMember: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onClearMessages: () -> Unit
) {
    val filteredTransactions = remember(
        state.transactions,
        state.selectedMonthKey,
        state.selectedKind,
        state.selectedMemberId
    ) {
        filterFinanceTransactions(
            state.transactions,
            state.selectedMonthKey,
            state.selectedKind,
            state.selectedMemberId
        )
    }
    val report = remember(filteredTransactions, state.memberEmails) {
        buildFinanceTransactionReport(filteredTransactions, state.memberEmails)
    }
    var isShowingEditor by rememberSaveable { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<FinanceTransaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<FinanceTransaction?>(null) }
    var isShowingCategoryManager by rememberSaveable { mutableStateOf(false) }
    var observedTransactionSave by remember { mutableIntStateOf(state.transactionSavedVersion) }
    var observedTransactionDelete by remember { mutableIntStateOf(state.transactionDeletedVersion) }

    LaunchedEffect(state.transactionSavedVersion) {
        if (state.transactionSavedVersion != observedTransactionSave) {
            observedTransactionSave = state.transactionSavedVersion
            transactionToEdit = null
            isShowingEditor = false
        }
    }
    LaunchedEffect(state.transactionDeletedVersion) {
        if (state.transactionDeletedVersion != observedTransactionDelete) {
            observedTransactionDelete = state.transactionDeletedVersion
            transactionToDelete = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FinanceSpacing.ScreenHorizontal, vertical = FinanceSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
    ) {
        FinanceButton(
            label = stringResource(R.string.transactions_back),
            onClick = onBack,
            variant = FinanceButtonVariant.Text
        )
        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
            Text(
                text = stringResource(R.string.transactions_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.transactions_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            state.isLoading -> FinanceLoadingState(stringResource(R.string.transactions_loading))
            state.errorMessage != null -> FinanceErrorState(
                title = stringResource(R.string.transactions_load_failed),
                description = state.errorMessage,
                retryLabel = stringResource(R.string.transactions_retry),
                onRetry = onRefresh
            )
            !state.hasHousehold -> FinanceEmptyState(
                title = stringResource(R.string.transactions_no_household_title),
                description = stringResource(R.string.transactions_no_household_description),
                actionLabel = stringResource(R.string.transactions_open_users),
                onAction = onOpenUsers
            )
            else -> {
                FinanceStatusBanner(
                    message = if (state.pendingSyncCount > 0) {
                        stringResource(R.string.transactions_pending_sync_count, state.pendingSyncCount)
                    } else {
                        stringResource(R.string.transactions_pending_sync_empty)
                    },
                    tone = if (state.pendingSyncCount > 0) FinanceStatusTone.Warning else FinanceStatusTone.Info
                )
                if (state.noticeMessage != null) {
                    FinanceStatusBanner(state.noticeMessage, tone = FinanceStatusTone.Success)
                }
                if (state.actionErrorMessage != null && !isShowingEditor && !isShowingCategoryManager) {
                    FinanceStatusBanner(state.actionErrorMessage, tone = FinanceStatusTone.Error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
                ) {
                    FinanceButton(
                        label = stringResource(R.string.transactions_register),
                        onClick = {
                            onClearMessages()
                            transactionToEdit = null
                            isShowingEditor = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (state.canManageCategories) {
                        FinanceButton(
                            label = stringResource(R.string.transactions_manage_categories),
                            onClick = {
                                onClearMessages()
                                isShowingCategoryManager = true
                            },
                            variant = FinanceButtonVariant.Secondary
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small),
                    contentPadding = PaddingValues(bottom = FinanceSpacing.Large)
                ) {
                    item(key = "transaction_filters") {
                        TransactionFilterPanel(
                            monthKeys = state.availableMonthKeys,
                            selectedMonthKey = state.selectedMonthKey,
                            selectedKind = state.selectedKind,
                            selectedMemberId = state.selectedMemberId,
                            memberEmails = state.memberEmails,
                            currentUserId = user.id,
                            canFilterMembers = state.canManageCategories,
                            hasActiveFilters = state.hasActiveFilters,
                            onSelectMonth = onSelectMonth,
                            onSelectKind = onSelectKind,
                            onSelectMember = onSelectMember,
                            onClearFilters = onClearFilters
                        )
                    }
                    item(key = "transaction_summary") {
                        TransactionSummaryCard(report, state.selectedMonthKey)
                    }
                    if (report.categoryTotals.isNotEmpty()) {
                        item(key = "category_breakdown_header") {
                            FinanceSectionHeader(stringResource(R.string.transactions_category_breakdown))
                        }
                        items(
                            items = report.categoryTotals,
                            key = { "${it.kind.apiValue}:${it.categoryId}" }
                        ) { total ->
                            FinanceListRow(
                                title = total.categoryName,
                                supportingText = kindLabel(total.kind),
                                trailingContent = {
                                    FinanceAmountText(
                                        formattedAmount = formatBobs(total.amountCentavos),
                                        kind = total.kind.toAmountKind(),
                                        accessibilityLabel = "${kindLabel(total.kind)} ${total.categoryName}: ${formatBobs(total.amountCentavos)}"
                                    )
                                }
                            )
                        }
                    }
                    if (state.canManageCategories && report.memberTotals.isNotEmpty()) {
                        item(key = "member_breakdown_header") {
                            FinanceSectionHeader(stringResource(R.string.transactions_member_breakdown))
                        }
                        items(report.memberTotals, key = FinanceMemberTotal::userId) { total ->
                            MemberBreakdownCard(
                                total = total,
                                currentUserId = user.id
                            )
                        }
                    }
                    item(key = "transaction_list_header") {
                        FinanceSectionHeader(
                            title = stringResource(R.string.transactions_list_title),
                            supportingText = pluralStringResource(
                                R.plurals.transactions_results_count,
                                filteredTransactions.size,
                                filteredTransactions.size
                            )
                        )
                    }
                    if (filteredTransactions.isEmpty()) {
                        item(key = "transaction_empty_state") {
                            FinanceEmptyState(
                                title = stringResource(
                                    if (state.transactions.isEmpty()) R.string.transactions_empty_title
                                    else R.string.transactions_no_filter_results_title
                                ),
                                description = stringResource(
                                    if (state.transactions.isEmpty()) R.string.transactions_empty_description
                                    else R.string.transactions_no_filter_results_description
                                ),
                                actionLabel = if (state.hasActiveFilters) {
                                    stringResource(R.string.transactions_clear_filters)
                                } else null,
                                onAction = if (state.hasActiveFilters) onClearFilters else null
                            )
                        }
                    } else {
                        items(filteredTransactions, key = FinanceTransaction::id) { transaction ->
                            val isOwn = transaction.createdBy == user.id
                            val author = if (isOwn) {
                                stringResource(R.string.transactions_registered_by_you)
                            } else {
                                state.memberEmails[transaction.createdBy]?.let {
                                    stringResource(R.string.transactions_registered_by_member, it)
                                } ?: stringResource(R.string.users_role_member)
                            }
                            TransactionCard(
                                transaction = transaction,
                                author = author,
                                canEdit = isOwn,
                                onEdit = {
                                    onClearMessages()
                                    transactionToEdit = transaction
                                    isShowingEditor = true
                                },
                                onDelete = {
                                    onClearMessages()
                                    transactionToDelete = transaction
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isShowingEditor && state.hasHousehold) {
        TransactionEditorSheet(
            initialTransaction = transactionToEdit,
            categories = state.categories,
            actionErrorMessage = state.actionErrorMessage,
            isSubmitting = state.isSubmitting,
            onSave = onSaveTransaction,
            onDismiss = {
                isShowingEditor = false
                transactionToEdit = null
                onClearMessages()
            }
        )
    }

    if (isShowingCategoryManager && state.canManageCategories) {
        CategoryManagerSheet(
            categories = state.categories,
            actionErrorMessage = state.actionErrorMessage,
            isSubmitting = state.isSubmitting,
            savedVersion = state.categorySavedVersion,
            onSaveCategory = onSaveCategory,
            onDeactivateCategory = onDeactivateCategory,
            onDismiss = {
                isShowingCategoryManager = false
                onClearMessages()
            }
        )
    }

    transactionToDelete?.let { transaction ->
        FinanceConfirmDialog(
            title = stringResource(R.string.transactions_delete_title),
            message = stringResource(R.string.transactions_delete_message),
            confirmLabel = stringResource(R.string.transactions_delete),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onDeleteTransaction(transaction.id) },
            onDismissRequest = { transactionToDelete = null },
            isDestructive = true,
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.transactions_deleting)
        )
    }
}

private data class FinanceFilterOption(val key: String?, val label: String)

@Composable
private fun TransactionFilterPanel(
    monthKeys: List<String>,
    selectedMonthKey: String?,
    selectedKind: TransactionKind?,
    selectedMemberId: String?,
    memberEmails: Map<String, String>,
    currentUserId: String,
    canFilterMembers: Boolean,
    hasActiveFilters: Boolean,
    onSelectMonth: (String?) -> Unit,
    onSelectKind: (TransactionKind?) -> Unit,
    onSelectMember: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    val monthOptions = listOf(
        FinanceFilterOption(null, stringResource(R.string.transactions_filter_all_months))
    ) + monthKeys.map { FinanceFilterOption(it, formatMonthLabel(it)) }
    val kindOptions = listOf(
        FinanceFilterOption(null, stringResource(R.string.transactions_filter_all_types)),
        FinanceFilterOption(TransactionKind.Income.apiValue, stringResource(R.string.transactions_filter_income)),
        FinanceFilterOption(TransactionKind.Expense.apiValue, stringResource(R.string.transactions_filter_expenses))
    )
    val youLabel = stringResource(R.string.users_you)
    val memberOptions = listOf(
        FinanceFilterOption(null, stringResource(R.string.transactions_filter_all_people))
    ) + memberEmails.entries
        .sortedBy { it.value.lowercase() }
        .map { (userId, email) ->
            FinanceFilterOption(userId, if (userId == currentUserId) youLabel else email)
        }

    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            FinanceSectionHeader(title = stringResource(R.string.transactions_filter_title))
            FinanceDropdownFilter(
                label = stringResource(R.string.transactions_filter_month),
                selectedKey = selectedMonthKey,
                options = monthOptions,
                onSelected = onSelectMonth
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                FinanceDropdownFilter(
                    label = stringResource(R.string.transactions_filter_type),
                    selectedKey = selectedKind?.apiValue,
                    options = kindOptions,
                    onSelected = { value ->
                        onSelectKind(value?.let { TransactionKind.fromApiValue(it) })
                    },
                    modifier = Modifier.weight(1f)
                )
                if (canFilterMembers) {
                    FinanceDropdownFilter(
                        label = stringResource(R.string.transactions_filter_person),
                        selectedKey = selectedMemberId,
                        options = memberOptions,
                        onSelected = onSelectMember,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (hasActiveFilters) {
                FinanceButton(
                    label = stringResource(R.string.transactions_clear_filters),
                    onClick = onClearFilters,
                    variant = FinanceButtonVariant.Text
                )
            }
        }
    }
}

@Composable
private fun FinanceDropdownFilter(
    label: String,
    selectedKey: String?,
    options: List<FinanceFilterOption>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.key == selectedKey }?.label
        ?: options.first().label
    Box(modifier.fillMaxWidth()) {
        FinanceButton(
            label = "$label: $selectedLabel",
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            variant = FinanceButtonVariant.Secondary
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionSummaryCard(report: FinanceTransactionReport, selectedMonthKey: String?) {
    val periodLabel = selectedMonthKey?.let(::formatMonthLabel)
        ?: stringResource(R.string.transactions_filter_all_months)
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            FinanceSectionHeader(
                title = stringResource(R.string.transactions_summary_title),
                supportingText = stringResource(R.string.transactions_summary_period, periodLabel)
            )
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_income),
                amountCentavos = report.incomeCentavos,
                kind = FinanceAmountKind.Income
            )
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_expenses),
                amountCentavos = report.expenseCentavos,
                kind = FinanceAmountKind.Expense
            )
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_balance),
                amountCentavos = report.balanceCentavos,
                kind = FinanceAmountKind.Neutral
            )
        }
    }
}

@Composable
private fun SummaryAmountRow(
    label: String,
    amountCentavos: BigInteger,
    kind: FinanceAmountKind
) {
    val amount = if (kind == FinanceAmountKind.Neutral) {
        formatSignedBobs(amountCentavos)
    } else {
        formatBobs(amountCentavos)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FinanceAmountText(
            formattedAmount = amount,
            kind = kind,
            accessibilityLabel = "$label, $amount",
            emphasized = kind == FinanceAmountKind.Neutral
        )
    }
}

@Composable
private fun MemberBreakdownCard(total: FinanceMemberTotal, currentUserId: String) {
    val label = when {
        total.userId == currentUserId -> stringResource(R.string.users_you)
        !total.email.isNullOrBlank() -> total.email
        else -> stringResource(R.string.users_role_member)
    }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_income),
                amountCentavos = total.incomeCentavos,
                kind = FinanceAmountKind.Income
            )
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_expenses),
                amountCentavos = total.expenseCentavos,
                kind = FinanceAmountKind.Expense
            )
            SummaryAmountRow(
                label = stringResource(R.string.transactions_summary_balance),
                amountCentavos = total.balanceCentavos,
                kind = FinanceAmountKind.Neutral
            )
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: FinanceTransaction,
    author: String,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            FinanceListRow(
                title = transaction.description?.takeIf(String::isNotBlank) ?: transaction.categoryName,
                supportingText = "${formatDate(transaction.occurredOn)} · $author",
                onClick = if (canEdit) onEdit else null,
                trailingContent = {
                    FinanceAmountText(
                        formattedAmount = formatBobs(transaction.amountCentavos),
                        kind = if (transaction.kind == TransactionKind.Income) FinanceAmountKind.Income
                        else FinanceAmountKind.Expense,
                        accessibilityLabel = formatBobs(transaction.amountCentavos),
                        emphasized = true
                    )
                }
            )
            if (canEdit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FinanceSpacing.Medium)
                        .padding(bottom = FinanceSpacing.Small),
                    horizontalArrangement = Arrangement.End
                ) {
                    FinanceButton(
                        label = stringResource(R.string.transactions_edit),
                        onClick = onEdit,
                        variant = FinanceButtonVariant.Text
                    )
                    FinanceButton(
                        label = stringResource(R.string.transactions_delete),
                        onClick = onDelete,
                        variant = FinanceButtonVariant.Text
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionEditorSheet(
    initialTransaction: FinanceTransaction?,
    categories: List<FinanceCategory>,
    actionErrorMessage: String?,
    isSubmitting: Boolean,
    onSave: (TransactionDraft) -> Unit,
    onDismiss: () -> Unit
) {
    var kind by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.kind ?: TransactionKind.Expense)
    }
    var amount by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.let { formatAmountInput(it.amountCentavos) }.orEmpty())
    }
    var occurredOn by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.occurredOn ?: currentIsoDate())
    }
    var categoryId by remember(initialTransaction?.id, categories) {
        mutableStateOf(
            initialTransaction?.categoryId
                ?: categories.firstOrNull { it.isActive && it.kind == TransactionKind.Expense }?.id.orEmpty()
        )
    }
    var description by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.description.orEmpty())
    }
    var isDatePickerOpen by remember { mutableStateOf(false) }
    var isCategoryMenuOpen by remember { mutableStateOf(false) }
    val activeCategories = categories.filter { it.isActive && it.kind == kind }
    val selectedCategory = activeCategories.firstOrNull { it.id == categoryId }
    val parsedAmount = parsePositiveAmountToCentavos(amount)

    FinanceBottomSheet(
        title = stringResource(
            if (initialTransaction == null) R.string.transactions_editor_add_title
            else R.string.transactions_editor_edit_title
        ),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
            Text(stringResource(R.string.transactions_kind_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                FinanceButton(
                    label = stringResource(R.string.transactions_expense),
                    onClick = {
                        kind = TransactionKind.Expense
                        categoryId = categories.firstOrNull { it.isActive && it.kind == TransactionKind.Expense }?.id.orEmpty()
                    },
                    variant = if (kind == TransactionKind.Expense) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
                FinanceButton(
                    label = stringResource(R.string.transactions_income),
                    onClick = {
                        kind = TransactionKind.Income
                        categoryId = categories.firstOrNull { it.isActive && it.kind == TransactionKind.Income }?.id.orEmpty()
                    },
                    variant = if (kind == TransactionKind.Income) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
            }
            FinanceAmountField(
                value = amount,
                onValueChange = { amount = it },
                label = stringResource(R.string.transactions_amount_label),
                currencyLabel = stringResource(R.string.transactions_currency),
                isError = amount.isNotBlank() && parsedAmount == null,
                supportingText = if (amount.isNotBlank() && parsedAmount == null) {
                    stringResource(R.string.transactions_invalid_amount)
                } else null
            )
            FinanceDateField(
                value = formatDate(occurredOn),
                label = stringResource(R.string.transactions_date_label),
                chooseDateLabel = stringResource(R.string.transactions_choose_date),
                onOpenPicker = { isDatePickerOpen = true }
            )
            Text(stringResource(R.string.transactions_category_label), style = MaterialTheme.typography.labelLarge)
            Box {
                FinanceButton(
                    label = selectedCategory?.name ?: stringResource(R.string.transactions_choose_category),
                    onClick = { isCategoryMenuOpen = true },
                    variant = FinanceButtonVariant.Secondary,
                    enabled = activeCategories.isNotEmpty()
                )
                DropdownMenu(
                    expanded = isCategoryMenuOpen,
                    onDismissRequest = { isCategoryMenuOpen = false }
                ) {
                    activeCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                isCategoryMenuOpen = false
                            }
                        )
                    }
                }
            }
            FinanceTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.transactions_description_label),
                singleLine = false
            )
            if (actionErrorMessage != null) {
                FinanceStatusBanner(actionErrorMessage, tone = FinanceStatusTone.Error)
            }
            FinanceButton(
                label = stringResource(R.string.transactions_save),
                onClick = {
                    val cents = parsedAmount ?: return@FinanceButton
                    onSave(
                        TransactionDraft(
                            id = initialTransaction?.id,
                            kind = kind,
                            amountCentavos = cents,
                            categoryId = categoryId,
                            occurredOn = occurredOn,
                            description = description
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = parsedAmount != null && selectedCategory != null && !isSubmitting,
                isLoading = isSubmitting,
                loadingLabel = stringResource(R.string.transactions_saving)
            )
        }
    }

    if (isDatePickerOpen) {
        FinanceDatePickerDialog(
            title = stringResource(R.string.transactions_date_label),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = isoDateToMillis(occurredOn),
            onDateSelected = { millis ->
                occurredOn = millisToIsoDate(millis)
                isDatePickerOpen = false
            },
            onDismissRequest = { isDatePickerOpen = false }
        )
    }
}

@Composable
private fun CategoryManagerSheet(
    categories: List<FinanceCategory>,
    actionErrorMessage: String?,
    isSubmitting: Boolean,
    savedVersion: Int,
    onSaveCategory: (CategoryDraft) -> Unit,
    onDeactivateCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isShowingEditor by rememberSaveable { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<FinanceCategory?>(null) }
    var categoryToDeactivate by remember { mutableStateOf<FinanceCategory?>(null) }
    var observedSaveVersion by remember { mutableIntStateOf(savedVersion) }

    LaunchedEffect(savedVersion) {
        if (savedVersion != observedSaveVersion) {
            observedSaveVersion = savedVersion
            isShowingEditor = false
            categoryToEdit = null
            categoryToDeactivate = null
        }
    }

    FinanceBottomSheet(
        title = stringResource(R.string.categories_title),
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
            if (actionErrorMessage != null) {
                FinanceStatusBanner(actionErrorMessage, tone = FinanceStatusTone.Error)
            }
            if (isShowingEditor) {
                CategoryEditor(
                    initialCategory = categoryToEdit,
                    isSubmitting = isSubmitting,
                    onSave = onSaveCategory
                )
            } else {
                FinanceButton(
                    label = stringResource(R.string.categories_new),
                    onClick = {
                        categoryToEdit = null
                        isShowingEditor = true
                    }
                )
                categories.forEach { category ->
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            FinanceListRow(
                                title = category.name,
                                supportingText = "${kindLabel(category.kind)} · ${categoryStatusLabel(category.isActive)}",
                                trailingContent = {
                                    if (category.isActive) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                                            FinanceButton(
                                                label = stringResource(R.string.transactions_edit),
                                                onClick = {
                                                    categoryToEdit = category
                                                    isShowingEditor = true
                                                },
                                                variant = FinanceButtonVariant.Text
                                            )
                                            FinanceButton(
                                                label = stringResource(R.string.categories_deactivate),
                                                onClick = { categoryToDeactivate = category },
                                                variant = FinanceButtonVariant.Text
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    categoryToDeactivate?.let { category ->
        FinanceConfirmDialog(
            title = stringResource(R.string.categories_deactivate_title),
            message = stringResource(R.string.categories_deactivate_message),
            confirmLabel = stringResource(R.string.categories_deactivate),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onDeactivateCategory(category.id) },
            onDismissRequest = { categoryToDeactivate = null },
            isDestructive = true,
            isProcessing = isSubmitting,
            processingLabel = stringResource(R.string.categories_saving)
        )
    }
}

@Composable
private fun CategoryEditor(
    initialCategory: FinanceCategory?,
    isSubmitting: Boolean,
    onSave: (CategoryDraft) -> Unit
) {
    var name by remember(initialCategory?.id) { mutableStateOf(initialCategory?.name.orEmpty()) }
    var kind by remember(initialCategory?.id) {
        mutableStateOf(initialCategory?.kind ?: TransactionKind.Expense)
    }
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        Text(
            stringResource(
                if (initialCategory == null) R.string.categories_create_title
                else R.string.categories_edit_title
            ),
            style = MaterialTheme.typography.titleMedium
        )
        FinanceTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.categories_name_label),
            isError = name.length > 40
        )
        if (initialCategory == null) {
            Text(stringResource(R.string.transactions_kind_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                FinanceButton(
                    label = stringResource(R.string.transactions_expense),
                    onClick = { kind = TransactionKind.Expense },
                    variant = if (kind == TransactionKind.Expense) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
                FinanceButton(
                    label = stringResource(R.string.transactions_income),
                    onClick = { kind = TransactionKind.Income },
                    variant = if (kind == TransactionKind.Income) FinanceButtonVariant.Primary
                    else FinanceButtonVariant.Secondary
                )
            }
        }
        FinanceButton(
            label = stringResource(R.string.categories_save),
            onClick = {
                onSave(CategoryDraft(initialCategory?.id, name, kind))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.trim().isNotEmpty() && name.trim().length <= 40 && !isSubmitting,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.categories_saving)
        )
    }
}

@Composable
private fun kindLabel(kind: TransactionKind): String = stringResource(
    if (kind == TransactionKind.Income) R.string.transactions_income else R.string.transactions_expense
)

private fun TransactionKind.toAmountKind(): FinanceAmountKind = if (this == TransactionKind.Income) {
    FinanceAmountKind.Income
} else {
    FinanceAmountKind.Expense
}

@Composable
private fun categoryStatusLabel(isActive: Boolean): String = stringResource(
    if (isActive) R.string.categories_active else R.string.categories_inactive
)

private fun currentIsoDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

private fun millisToIsoDate(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(millis))

private fun isoDateToMillis(value: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
    isLenient = false
    timeZone = TimeZone.getTimeZone("UTC")
}.parse(value)?.time ?: System.currentTimeMillis()

private fun formatDate(value: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value) ?: return@runCatching value
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(date)
}.getOrDefault(value)

private fun formatAmountInput(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString()

private fun formatMonthLabel(monthKey: String): String = runCatching {
    val locale = Locale.forLanguageTag("es-BO")
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse("$monthKey-01") ?: return@runCatching monthKey
    SimpleDateFormat("MMMM yyyy", locale)
        .format(date)
        .replaceFirstChar { first -> first.titlecase(locale) }
}.getOrDefault(monthKey)

private fun formatBobs(centavos: Long): String = formatBobs(BigInteger.valueOf(centavos))

private fun formatBobs(centavos: BigInteger): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-BO"))
    formatter.currency = Currency.getInstance("BOB")
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(BigDecimal(centavos).movePointLeft(2))
}

private fun formatSignedBobs(centavos: BigInteger): String = when (centavos.signum()) {
    1 -> "+${formatBobs(centavos)}"
    -1 -> "−${formatBobs(centavos.negate())}"
    else -> formatBobs(BigInteger.ZERO)
}
