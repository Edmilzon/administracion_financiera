package com.moonspace.adminfinanciera.feature.budgets.presentation

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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.moonspace.adminfinanciera.core.ui.components.FinanceIconButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusPill
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceConfirmDialog
import com.moonspace.adminfinanciera.core.ui.forms.FinanceAmountField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.forms.parsePositiveAmountToCentavos
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSemanticColorScheme
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetDraft
import com.moonspace.adminfinanciera.feature.budgets.domain.BudgetUsage
import com.moonspace.adminfinanciera.feature.budgets.domain.FinanceBudget
import com.moonspace.adminfinanciera.feature.budgets.domain.MAX_BUDGET_LIMIT_CENTAVOS
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

@Composable
fun FinanceBudgetsScreen(
    user: AuthUser,
    state: FinanceBudgetsUiState,
    onOpenUsers: () -> Unit,
    onRefresh: () -> Unit,
    onSaveBudget: (BudgetDraft) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onMoveMonth: (Int) -> Unit,
    onClearMessages: () -> Unit
) {
    var isShowingEditor by rememberSaveable { mutableStateOf(false) }
    var isAddingCategoryBudget by rememberSaveable { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<FinanceBudget?>(null) }
    var budgetToDelete by remember { mutableStateOf<FinanceBudget?>(null) }
    var isConfirmingEditorDiscard by rememberSaveable { mutableStateOf(false) }
    var editorAmount by rememberSaveable { mutableStateOf("") }
    var initialEditorAmount by rememberSaveable { mutableStateOf("") }
    var editorCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialEditorCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var observedSave by remember { mutableIntStateOf(state.budgetSavedVersion) }
    var observedDelete by remember { mutableIntStateOf(state.budgetDeletedVersion) }
    val monthLabel = remember(state.selectedMonthStart) { formatMonthLabel(state.selectedMonthStart) }
    val ownGeneralBudgetExists = state.budgets.any {
        it.userId == user.id && it.categoryId == null && it.monthStart == state.selectedMonthStart
    }
    val ownCategoryIds = state.budgets
        .filter { it.userId == user.id && it.monthStart == state.selectedMonthStart }
        .mapNotNullTo(mutableSetOf()) { it.categoryId }
    val categoriesAvailableToBudget = state.categories.filter {
        it.isActive && it.id !in ownCategoryIds
    }
    val usages = remember(state.budgets, state.transactions, state.selectedMonthStart) { state.budgetUsage }
    val editorHasChanges = editorAmount != initialEditorAmount || editorCategoryId != initialEditorCategoryId

    fun openBudgetEditor(budget: FinanceBudget?, addCategory: Boolean) {
        onClearMessages()
        budgetToEdit = budget
        isAddingCategoryBudget = addCategory
        initialEditorAmount = budget?.amountLimitCentavos?.let(::formatAmountInput).orEmpty()
        initialEditorCategoryId = budget?.categoryId
            ?: if (addCategory) categoriesAvailableToBudget.firstOrNull()?.id else null
        editorAmount = initialEditorAmount
        editorCategoryId = initialEditorCategoryId
        isConfirmingEditorDiscard = false
        isShowingEditor = true
    }

    LaunchedEffect(state.budgetSavedVersion) {
        if (state.budgetSavedVersion != observedSave) {
            observedSave = state.budgetSavedVersion
            budgetToEdit = null
            isShowingEditor = false
            isConfirmingEditorDiscard = false
        }
    }
    LaunchedEffect(state.budgetDeletedVersion) {
        if (state.budgetDeletedVersion != observedDelete) {
            observedDelete = state.budgetDeletedVersion
            budgetToDelete = null
        }
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
                text = stringResource(R.string.budgets_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        when {
            state.isLoading -> item { FinanceLoadingState(stringResource(R.string.budgets_loading)) }
            state.errorMessage != null -> item {
                FinanceErrorState(
                    title = stringResource(R.string.budgets_load_failed),
                    description = state.errorMessage,
                    retryLabel = stringResource(R.string.budgets_retry),
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
                        if (state.actionErrorMessage != null && !isShowingEditor) {
                            FinanceStatusBanner(state.actionErrorMessage, tone = FinanceStatusTone.Error)
                        }
                        BudgetMonthSelector(
                            monthLabel = monthLabel,
                            onPrevious = { onMoveMonth(-1) },
                            onNext = { onMoveMonth(1) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                            FinanceButton(
                                label = stringResource(R.string.budgets_add_general),
                                onClick = { openBudgetEditor(null, addCategory = false) },
                                modifier = Modifier.weight(1f),
                                variant = FinanceButtonVariant.Secondary,
                                enabled = !ownGeneralBudgetExists
                            )
                            FinanceButton(
                                label = stringResource(R.string.budgets_add_category),
                                onClick = { openBudgetEditor(null, addCategory = true) },
                                modifier = Modifier.weight(1f),
                                variant = FinanceButtonVariant.Primary,
                                enabled = categoriesAvailableToBudget.isNotEmpty()
                            )
                        }
                        if (usages.isNotEmpty()) {
                            FinanceSectionHeader(
                                title = stringResource(R.string.budgets_list_title),
                                supportingText = stringResource(R.string.budgets_list_period, monthLabel)
                            )
                        }
                    }
                }
                if (usages.isEmpty()) {
                    item {
                        FinanceEmptyState(
                            title = stringResource(R.string.budgets_empty_title),
                            description = stringResource(R.string.budgets_empty_description)
                        )
                    }
                } else {
                    items(usages, key = { it.budget.id }) { usage ->
                        BudgetUsageCard(
                            usage = usage,
                            currentUserId = user.id,
                            isAdmin = state.role == HouseholdRole.Admin,
                            memberEmail = state.memberEmails[usage.budget.userId],
                            onEdit = {
                                openBudgetEditor(usage.budget, addCategory = false)
                            },
                            onDelete = { budgetToDelete = usage.budget }
                        )
                    }
                }
            }
        }
    }

    if (isShowingEditor && state.hasHousehold) {
        FinanceBottomSheet(
            title = stringResource(
                if (budgetToEdit == null) R.string.budgets_editor_add_title
                else R.string.budgets_editor_edit_title
            ),
            onDismissRequest = {
                if (!state.isSubmitting) {
                    isShowingEditor = false
                    isConfirmingEditorDiscard = editorHasChanges
                }
            }
        ) {
            BudgetEditor(
                budgetId = budgetToEdit?.id,
                amount = editorAmount,
                onAmountChange = { editorAmount = it },
                categoryId = editorCategoryId,
                onCategorySelected = { editorCategoryId = it },
                categories = state.categories,
                existingCategoryIds = ownCategoryIds,
                selectedMonthStart = state.selectedMonthStart,
                isSubmitting = state.isSubmitting,
                actionErrorMessage = state.actionErrorMessage,
                onSave = onSaveBudget
            )
        }
    }

    if (isConfirmingEditorDiscard && !isShowingEditor) {
        FinanceConfirmDialog(
            title = stringResource(R.string.budgets_discard_title),
            message = stringResource(R.string.budgets_discard_message),
            confirmLabel = stringResource(R.string.budgets_discard),
            dismissLabel = stringResource(R.string.budgets_continue_editing),
            onConfirm = {
                isConfirmingEditorDiscard = false
                budgetToEdit = null
                isAddingCategoryBudget = false
                editorAmount = ""
                editorCategoryId = null
            },
            onDismissRequest = {
                isConfirmingEditorDiscard = false
                isShowingEditor = true
            },
            isDestructive = true
        )
    }

    budgetToDelete?.let { budget ->
        FinanceConfirmDialog(
            title = stringResource(R.string.budgets_delete_title),
            message = stringResource(
                R.string.budgets_delete_message,
                budget.categoryName ?: stringResource(R.string.budgets_general_label)
            ),
            confirmLabel = stringResource(R.string.budgets_delete),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onDeleteBudget(budget.id) },
            onDismissRequest = { budgetToDelete = null },
            isDestructive = true,
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.budgets_deleting)
        )
    }
}

@Composable
private fun BudgetMonthSelector(monthLabel: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FinanceSpacing.Small, vertical = FinanceSpacing.XSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FinanceIconButton(
                contentDescription = stringResource(R.string.budgets_month_previous),
                onClick = onPrevious
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
            Text(
                text = monthLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FinanceIconButton(
                contentDescription = stringResource(R.string.budgets_month_next),
                onClick = onNext
            ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
private fun BudgetUsageCard(
    usage: BudgetUsage,
    currentUserId: String,
    isAdmin: Boolean,
    memberEmail: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val budget = usage.budget
    val canManage = budget.userId == currentUserId
    val limit = BigInteger.valueOf(budget.amountLimitCentavos)
    val progress = usage.usedCentavos.toBigDecimal()
        .divide(limit.toBigDecimal(), 4, RoundingMode.HALF_UP)
        .toFloat()
        .coerceIn(0f, 1f)
    val title = budget.categoryName ?: stringResource(R.string.budgets_general_label)
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (!budget.isGeneral && budget.categoryKind != null) {
                        Text(
                            text = stringResource(
                                if (budget.categoryKind == TransactionKind.Income) {
                                    R.string.transactions_income
                                } else {
                                    R.string.transactions_expense
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isAdmin) {
                        Text(
                            text = if (budget.userId == currentUserId) {
                                stringResource(R.string.budgets_owner_you)
                            } else {
                                stringResource(
                                    R.string.budgets_owner,
                                    memberEmail ?: stringResource(R.string.budgets_owner_unknown)
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (canManage) {
                    Column(horizontalAlignment = Alignment.End) {
                        FinanceButton(
                            label = stringResource(R.string.budgets_edit),
                            onClick = onEdit,
                            variant = FinanceButtonVariant.Text
                        )
                        FinanceButton(
                            label = stringResource(R.string.budgets_delete),
                            onClick = onDelete,
                            variant = FinanceButtonVariant.Text
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (usage.usedCentavos > limit) {
                    FinanceSemanticColorScheme.expense
                } else {
                    FinanceSemanticColorScheme.success
                }
            )
            Text(
                text = stringResource(
                    R.string.budgets_used_of_limit,
                    formatBobs(usage.usedCentavos),
                    formatBobs(limit)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (usage.remainingCentavos.signum() >= 0) {
                    FinanceStatusPill(
                        label = stringResource(R.string.budgets_remaining, formatBobs(usage.remainingCentavos)),
                        tone = FinanceStatusTone.Success
                    )
                } else {
                    FinanceStatusPill(
                        label = stringResource(R.string.budgets_exceeded, formatBobs(usage.remainingCentavos.negate())),
                        tone = FinanceStatusTone.Error
                    )
                }
                FinanceAmountText(
                    formattedAmount = formatBobs(usage.usedCentavos),
                    kind = FinanceAmountKind.Neutral,
                    accessibilityLabel = stringResource(
                        R.string.budgets_used_accessibility,
                        formatBobs(usage.usedCentavos)
                    )
                )
            }
        }
    }
}

@Composable
private fun BudgetEditor(
    budgetId: String?,
    amount: String,
    onAmountChange: (String) -> Unit,
    categoryId: String?,
    onCategorySelected: (String?) -> Unit,
    categories: List<FinanceCategory>,
    existingCategoryIds: Set<String>,
    selectedMonthStart: String,
    isSubmitting: Boolean,
    actionErrorMessage: String?,
    onSave: (BudgetDraft) -> Unit
) {
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val parsedAmount = remember(amount) { parsePositiveAmountToCentavos(amount) }
    val validAmount = parsedAmount?.takeIf { it <= MAX_BUDGET_LIMIT_CENTAVOS }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val categoryLabel = if (categoryId == null) {
        stringResource(R.string.budgets_general_label)
    } else {
        selectedCategory?.let { category ->
            val kindLabel = stringResource(
                if (category.kind == TransactionKind.Income) R.string.transactions_income
                else R.string.transactions_expense
            )
            "${category.name} · $kindLabel"
        } ?: stringResource(R.string.budgets_choose_category)
    }

    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        Text(
            text = stringResource(R.string.budgets_editor_month, formatMonthLabel(selectedMonthStart)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.budgets_scope_label), style = MaterialTheme.typography.labelLarge)
        Box {
            FinanceButton(
                label = categoryLabel,
                onClick = { categoryMenuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                variant = FinanceButtonVariant.Secondary
            )
            DropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budgets_general_label)) },
                    onClick = {
                        onCategorySelected(null)
                        categoryMenuOpen = false
                    }
                )
                categories
                    .filter { (it.isActive || it.id == categoryId) && (it.id !in existingCategoryIds || it.id == categoryId) }
                    .forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${category.name} · ${stringResource(if (category.kind == TransactionKind.Income) R.string.transactions_income else R.string.transactions_expense)}"
                            )
                        },
                        onClick = {
                            onCategorySelected(category.id)
                            categoryMenuOpen = false
                        }
                    )
                }
            }
        }
        FinanceAmountField(
            value = amount,
            onValueChange = onAmountChange,
            label = stringResource(R.string.budgets_amount_label),
            currencyLabel = stringResource(R.string.transactions_currency),
            isError = amount.isNotBlank() && validAmount == null,
            supportingText = if (amount.isNotBlank() && validAmount == null) {
                stringResource(R.string.budgets_invalid_amount)
            } else null
        )
        if (actionErrorMessage != null) {
            FinanceStatusBanner(actionErrorMessage, tone = FinanceStatusTone.Error)
        }
        FinanceButton(
            label = stringResource(R.string.budgets_save),
            onClick = {
                val cents = validAmount ?: return@FinanceButton
                onSave(BudgetDraft(budgetId, categoryId, selectedMonthStart, cents))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = validAmount != null && !isSubmitting,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.budgets_saving)
        )
    }
}

private fun formatMonthLabel(monthStart: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(monthStart) ?: return@runCatching monthStart
    val locale = Locale.forLanguageTag("es-BO")
    SimpleDateFormat("MMMM yyyy", locale).format(date).replaceFirstChar { it.titlecase(locale) }
}.getOrDefault(monthStart)

private fun formatAmountInput(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString()

private fun formatBobs(centavos: BigInteger): String {
    val formatter = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-BO"))
    formatter.currency = Currency.getInstance("BOB")
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(BigDecimal(centavos).movePointLeft(2))
}
