package com.moonspace.adminfinanciera.feature.debts.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinancePageTitle
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusPill
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceBottomSheet
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceConfirmDialog
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceDatePickerDialog
import com.moonspace.adminfinanciera.core.ui.forms.FinanceAmountField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceDateField
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentOption
import com.moonspace.adminfinanciera.core.ui.forms.FinanceSegmentedSelector
import com.moonspace.adminfinanciera.core.ui.forms.FinanceTextField
import com.moonspace.adminfinanciera.core.ui.forms.parsePositiveAmountToCentavos
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.auth.domain.AuthUser
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDirection
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDraft
import com.moonspace.adminfinanciera.feature.debts.domain.DebtPayment
import com.moonspace.adminfinanciera.feature.debts.domain.DebtPaymentDraft
import com.moonspace.adminfinanciera.feature.debts.domain.FinanceDebt
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdRole
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun FinanceDebtsScreen(
    user: AuthUser,
    state: FinanceDebtsUiState,
    onOpenUsers: () -> Unit,
    onRefresh: () -> Unit,
    onSaveDebt: (DebtDraft) -> Unit,
    onDeleteDebt: (String) -> Unit,
    onSavePayment: (DebtPaymentDraft) -> Unit,
    onDeletePayment: (String) -> Unit,
    onClearMessages: () -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf(DEBT_FILTER_ALL) }
    var isShowingDebtEditor by rememberSaveable { mutableStateOf(false) }
    var isShowingPaymentEditor by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var isDiscardingPayment by rememberSaveable { mutableStateOf(false) }
    var debtToEdit by remember { mutableStateOf<FinanceDebt?>(null) }
    var paymentTargetDebt by remember { mutableStateOf<FinanceDebt?>(null) }
    var paymentToEdit by remember { mutableStateOf<DebtPayment?>(null) }
    var debtToDelete by remember { mutableStateOf<FinanceDebt?>(null) }
    var paymentToDelete by remember { mutableStateOf<DebtPayment?>(null) }
    var editorDirection by rememberSaveable { mutableStateOf(DebtDirection.OwedByMe.apiValue) }
    var editorCounterparty by rememberSaveable { mutableStateOf("") }
    var editorDescription by rememberSaveable { mutableStateOf("") }
    var editorPrincipal by rememberSaveable { mutableStateOf("") }
    var editorOpenedOn by rememberSaveable { mutableStateOf(todayIsoDate()) }
    var editorDueOn by rememberSaveable { mutableStateOf<String?>(null) }
    var paymentAmount by rememberSaveable { mutableStateOf("") }
    var paymentDate by rememberSaveable { mutableStateOf(todayIsoDate()) }
    var paymentNote by rememberSaveable { mutableStateOf("") }
    var activeDateField by rememberSaveable { mutableStateOf<String?>(null) }
    var observedSavedVersion by remember { mutableIntStateOf(state.savedVersion) }
    var observedDeletedVersion by remember { mutableIntStateOf(state.deletedVersion) }
    var initialDebtValues by remember { mutableStateOf(emptyList<String>()) }
    var initialPaymentValues by remember { mutableStateOf(emptyList<String>()) }
    val debtEditorHasChanges = listOf(
        editorDirection,
        editorCounterparty,
        editorDescription,
        editorPrincipal,
        editorOpenedOn,
        editorDueOn.orEmpty()
    ) != initialDebtValues
    val paymentEditorHasChanges = listOf(
        paymentAmount,
        paymentDate,
        paymentNote
    ) != initialPaymentValues

    fun openDebtEditor(debt: FinanceDebt?) {
        onClearMessages()
        debtToEdit = debt
        editorDirection = debt?.direction?.apiValue ?: DebtDirection.OwedByMe.apiValue
        editorCounterparty = debt?.counterparty.orEmpty()
        editorDescription = debt?.description.orEmpty()
        editorPrincipal = debt?.principalCentavos?.let(::formatAmountInput).orEmpty()
        editorOpenedOn = debt?.openedOn ?: todayIsoDate()
        editorDueOn = debt?.dueOn
        initialDebtValues = listOf(
            editorDirection,
            editorCounterparty,
            editorDescription,
            editorPrincipal,
            editorOpenedOn,
            editorDueOn.orEmpty()
        )
        isShowingDebtEditor = true
    }

    fun openPaymentEditor(debt: FinanceDebt, payment: DebtPayment?) {
        onClearMessages()
        paymentTargetDebt = debt
        paymentToEdit = payment
        paymentAmount = payment?.amountCentavos?.let(::formatAmountInput).orEmpty()
        paymentDate = payment?.paidOn ?: todayIsoDate()
        paymentNote = payment?.note.orEmpty()
        initialPaymentValues = listOf(paymentAmount, paymentDate, paymentNote)
        isShowingPaymentEditor = true
    }

    LaunchedEffect(state.savedVersion) {
        if (state.savedVersion != observedSavedVersion) {
            observedSavedVersion = state.savedVersion
            isShowingDebtEditor = false
            isShowingPaymentEditor = false
            debtToEdit = null
            paymentTargetDebt = null
            paymentToEdit = null
            activeDateField = null
        }
    }
    LaunchedEffect(state.deletedVersion) {
        if (state.deletedVersion != observedDeletedVersion) {
            observedDeletedVersion = state.deletedVersion
            debtToDelete = null
            paymentToDelete = null
        }
    }

    val filteredDebts = remember(state.debts, selectedFilter) {
        when (selectedFilter) {
            DebtDirection.OwedByMe.apiValue -> state.debts.filter { it.direction == DebtDirection.OwedByMe }
            DebtDirection.OwedToMe.apiValue -> state.debts.filter { it.direction == DebtDirection.OwedToMe }
            else -> state.debts
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = FinanceSpacing.ScreenHorizontal, vertical = FinanceSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
    ) {
        item {
            FinancePageTitle(title = stringResource(R.string.debts_title))
        }
        when {
            state.isLoading -> item { FinanceLoadingState(stringResource(R.string.debts_loading)) }
            state.errorMessage != null -> item {
                FinanceErrorState(
                    title = stringResource(R.string.debts_load_failed),
                    description = state.errorMessage,
                    retryLabel = stringResource(R.string.debts_retry),
                    onRetry = onRefresh
                )
            }
            !state.hasHousehold -> item {
                FinanceEmptyState(
                    title = stringResource(R.string.transactions_no_household_title),
                    description = stringResource(R.string.transactions_no_household_description),
                    actionLabel = stringResource(R.string.debts_open_users),
                    onAction = onOpenUsers
                )
            }
            else -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
                        state.noticeMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Success) }
                        state.actionErrorMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Error) }
                        DebtTotals(state)
                        FinanceButton(
                            label = stringResource(R.string.debts_add),
                            onClick = { openDebtEditor(null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                            DebtFilterButton(
                                label = stringResource(R.string.debts_all),
                                selected = selectedFilter == DEBT_FILTER_ALL,
                                modifier = Modifier.weight(1f)
                            ) { selectedFilter = DEBT_FILTER_ALL }
                            DebtFilterButton(
                                label = stringResource(R.string.debts_owed_by_me),
                                selected = selectedFilter == DebtDirection.OwedByMe.apiValue,
                                modifier = Modifier.weight(1f)
                            ) { selectedFilter = DebtDirection.OwedByMe.apiValue }
                            DebtFilterButton(
                                label = stringResource(R.string.debts_owed_to_me),
                                selected = selectedFilter == DebtDirection.OwedToMe.apiValue,
                                modifier = Modifier.weight(1f)
                            ) { selectedFilter = DebtDirection.OwedToMe.apiValue }
                        }
                    }
                }
                if (filteredDebts.isEmpty()) {
                    item {
                        FinanceEmptyState(
                            title = stringResource(R.string.debts_empty_title),
                            description = stringResource(R.string.debts_empty_description)
                        )
                    }
                } else {
                    items(filteredDebts, key = FinanceDebt::id) { debt ->
                        DebtCard(
                            debt = debt,
                            currentUserId = user.id,
                            isAdmin = state.role == HouseholdRole.Admin,
                            ownerEmail = state.memberEmails[debt.createdBy],
                            onEdit = { openDebtEditor(debt) },
                            onDelete = { debtToDelete = debt },
                            onAddPayment = { openPaymentEditor(debt, null) },
                            onEditPayment = { payment -> openPaymentEditor(debt, payment) },
                            onDeletePayment = { payment -> paymentToDelete = payment }
                        )
                    }
                }
            }
        }
    }

    if (isShowingDebtEditor && state.hasHousehold) {
        FinanceBottomSheet(
            title = stringResource(if (debtToEdit == null) R.string.debts_editor_add_title else R.string.debts_editor_edit_title),
            onDismissRequest = {
                if (!state.isSubmitting) {
                    isShowingDebtEditor = false
                    isConfirmingDiscard = debtEditorHasChanges
                }
            }
        ) {
            DebtEditorContent(
                direction = DebtDirection.fromApiValue(editorDirection) ?: DebtDirection.OwedByMe,
                onDirectionChange = { editorDirection = it.apiValue },
                counterparty = editorCounterparty,
                onCounterpartyChange = { editorCounterparty = it },
                description = editorDescription,
                onDescriptionChange = { editorDescription = it },
                principal = editorPrincipal,
                onPrincipalChange = { editorPrincipal = it },
                openedOn = editorOpenedOn,
                dueOn = editorDueOn,
                onOpenDatePicker = { activeDateField = it },
                onClearDueDate = { editorDueOn = null },
                isSubmitting = state.isSubmitting,
                errorMessage = state.actionErrorMessage,
                onSave = {
                    val amount = parsePositiveAmountToCentavos(editorPrincipal) ?: 0L
                    onSaveDebt(
                        DebtDraft(
                            id = debtToEdit?.id,
                            direction = DebtDirection.fromApiValue(editorDirection) ?: DebtDirection.OwedByMe,
                            counterparty = editorCounterparty,
                            description = editorDescription,
                            principalCentavos = amount,
                            openedOn = editorOpenedOn,
                            dueOn = editorDueOn
                        )
                    )
                }
            )
        }
    }

    if (isShowingPaymentEditor && paymentTargetDebt != null) {
        val debt = requireNotNull(paymentTargetDebt)
        FinanceBottomSheet(
            title = stringResource(if (paymentToEdit == null) R.string.debts_payment_editor_title else R.string.debts_edit_payment),
            onDismissRequest = {
                if (!state.isSubmitting) {
                    isShowingPaymentEditor = false
                    isConfirmingDiscard = paymentEditorHasChanges
                    isDiscardingPayment = true
                }
            }
        ) {
            PaymentEditorContent(
                amount = paymentAmount,
                onAmountChange = { paymentAmount = it },
                paidOn = paymentDate,
                onOpenDatePicker = { activeDateField = DATE_FIELD_PAYMENT },
                note = paymentNote,
                onNoteChange = { paymentNote = it },
                isSubmitting = state.isSubmitting,
                errorMessage = state.actionErrorMessage,
                onSave = {
                    onSavePayment(
                        DebtPaymentDraft(
                            id = paymentToEdit?.id,
                            debtId = debt.id,
                            amountCentavos = parsePositiveAmountToCentavos(paymentAmount) ?: 0L,
                            paidOn = paymentDate,
                            note = paymentNote
                        )
                    )
                }
            )
        }
    }

    if (isConfirmingDiscard && !isShowingDebtEditor) {
        FinanceConfirmDialog(
            title = stringResource(R.string.debts_discard_title),
            message = stringResource(R.string.debts_discard_message),
            confirmLabel = stringResource(R.string.debts_discard_action),
            dismissLabel = stringResource(R.string.debts_continue_editing),
            onConfirm = {
                isConfirmingDiscard = false
                if (isDiscardingPayment) {
                    isShowingPaymentEditor = false
                    paymentTargetDebt = null
                    paymentToEdit = null
                    paymentAmount = ""
                    paymentNote = ""
                    isDiscardingPayment = false
                } else {
                    isShowingDebtEditor = false
                    debtToEdit = null
                    editorCounterparty = ""
                    editorDescription = ""
                    editorPrincipal = ""
                    editorDueOn = null
                }
            },
            onDismissRequest = {
                isConfirmingDiscard = false
                if (isDiscardingPayment) {
                    isShowingPaymentEditor = true
                    isDiscardingPayment = false
                } else {
                    isShowingDebtEditor = true
                }
            },
            isDestructive = true
        )
    }

    debtToDelete?.let { debt ->
        FinanceConfirmDialog(
            title = stringResource(R.string.debts_delete_title),
            message = stringResource(R.string.debts_delete_message),
            confirmLabel = stringResource(R.string.debts_delete_action),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onDeleteDebt(debt.id) },
            onDismissRequest = { debtToDelete = null },
            isDestructive = true,
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.debts_saving)
        )
    }

    paymentToDelete?.let { payment ->
        FinanceConfirmDialog(
            title = stringResource(R.string.debts_delete_payment_title),
            message = stringResource(R.string.debts_delete_payment_message),
            confirmLabel = stringResource(R.string.debts_delete_payment),
            dismissLabel = stringResource(R.string.ui_cancel),
            onConfirm = { onDeletePayment(payment.id) },
            onDismissRequest = { paymentToDelete = null },
            isDestructive = true,
            isProcessing = state.isSubmitting,
            processingLabel = stringResource(R.string.debts_saving)
        )
    }

    when (activeDateField) {
        DATE_FIELD_OPENED -> FinanceDatePickerDialog(
            title = stringResource(R.string.debts_choose_open_date),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = parseDateMillis(editorOpenedOn),
            onDateSelected = { editorOpenedOn = dateFromMillis(it); activeDateField = null },
            onDismissRequest = { activeDateField = null }
        )
        DATE_FIELD_DUE -> FinanceDatePickerDialog(
            title = stringResource(R.string.debts_choose_due_date),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = editorDueOn?.let(::parseDateMillis),
            onDateSelected = { editorDueOn = dateFromMillis(it); activeDateField = null },
            onDismissRequest = { activeDateField = null }
        )
        DATE_FIELD_PAYMENT -> FinanceDatePickerDialog(
            title = stringResource(R.string.debts_choose_payment_date),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = parseDateMillis(paymentDate),
            onDateSelected = { paymentDate = dateFromMillis(it); activeDateField = null },
            onDismissRequest = { activeDateField = null }
        )
    }
}

@Composable
private fun DebtTotals(state: FinanceDebtsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
        DebtTotalCard(
            label = stringResource(R.string.debts_owed_by_me),
            cents = state.owedByMeRemainingCentavos,
            kind = if (state.owedByMeRemainingCentavos.signum() == 0) FinanceAmountKind.Neutral
            else FinanceAmountKind.NegativeBalance,
            modifier = Modifier.weight(1f)
        )
        DebtTotalCard(
            label = stringResource(R.string.debts_owed_to_me),
            cents = state.owedToMeRemainingCentavos,
            kind = if (state.owedToMeRemainingCentavos.signum() == 0) FinanceAmountKind.Neutral
            else FinanceAmountKind.PositiveBalance,
            modifier = Modifier.weight(1f)
        )
    }
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FinanceSpacing.Medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.debts_net_balance), style = MaterialTheme.typography.titleMedium)
            val kind = when (state.netBalanceCentavos.signum()) {
                1 -> FinanceAmountKind.PositiveBalance
                -1 -> FinanceAmountKind.NegativeBalance
                else -> FinanceAmountKind.Neutral
            }
            FinanceAmountText(
                formattedAmount = formatDebtMoney(state.netBalanceCentavos.abs()),
                kind = kind,
                accessibilityLabel = "${stringResource(R.string.debts_net_balance)} ${formatDebtMoney(state.netBalanceCentavos.abs())}",
                emphasized = true
            )
            Text(
                text = stringResource(
                    when (state.netBalanceCentavos.signum()) {
                        1 -> R.string.debts_net_a_favor
                        -1 -> R.string.debts_net_por_pagar
                        else -> R.string.debts_net_equilibrado
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebtTotalCard(label: String, cents: BigInteger, kind: FinanceAmountKind, modifier: Modifier = Modifier) {
    FinanceCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(FinanceSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceAmountText(
                formattedAmount = formatDebtMoney(cents),
                kind = kind,
                accessibilityLabel = "$label ${formatDebtMoney(cents)}",
                emphasized = true
            )
        }
    }
}

@Composable
private fun DebtFilterButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FinanceButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = if (selected) FinanceButtonVariant.Primary else FinanceButtonVariant.Secondary,
        compact = true
    )
}

@Composable
private fun DebtCard(
    debt: FinanceDebt,
    currentUserId: String,
    isAdmin: Boolean,
    ownerEmail: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddPayment: () -> Unit,
    onEditPayment: (DebtPayment) -> Unit,
    onDeletePayment: (DebtPayment) -> Unit
) {
    var showHistory by rememberSaveable(debt.id) { mutableStateOf(false) }
    val canManage = debt.createdBy == currentUserId
    val directionLabel = stringResource(
        if (debt.direction == DebtDirection.OwedByMe) R.string.debts_owed_by_me else R.string.debts_owed_to_me
    )
    val balanceKind = when {
        debt.remainingCentavos == BigInteger.ZERO -> FinanceAmountKind.Neutral
        debt.direction == DebtDirection.OwedToMe -> FinanceAmountKind.PositiveBalance
        else -> FinanceAmountKind.NegativeBalance
    }
    val isOverdue = !debt.isSettled && debt.dueOn != null && debt.dueOn < todayIsoDate()
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(FinanceSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.Small)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                    Text(
                        debt.counterparty,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(directionLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    FinanceAmountText(
                        formattedAmount = formatDebtMoney(debt.remainingCentavos),
                        kind = balanceKind,
                        accessibilityLabel = "${stringResource(R.string.debts_remaining_amount)} ${formatDebtMoney(debt.remainingCentavos)}",
                        emphasized = true
                    )
                    Text(stringResource(R.string.debts_remaining_amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (debt.description != null) {
                Text(debt.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DebtAmountDetail(stringResource(R.string.debts_initial_amount), BigInteger.valueOf(debt.principalCentavos))
                DebtAmountDetail(stringResource(R.string.debts_paid_amount), debt.paidCentavos)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isAdmin) {
                    Text(
                        text = if (canManage) stringResource(R.string.debts_owner_you)
                        else stringResource(R.string.debts_owner, ownerEmail ?: stringResource(R.string.debts_owner_unknown)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(formatDebtDate(debt.openedOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    debt.isSettled -> FinanceStatusPill(stringResource(R.string.debts_settled), FinanceStatusTone.Success)
                    isOverdue -> FinanceStatusPill(stringResource(R.string.debts_overdue), FinanceStatusTone.Error)
                    debt.dueOn != null -> FinanceStatusPill(
                        stringResource(R.string.debts_due_on_value, formatDebtDate(debt.dueOn)),
                        FinanceStatusTone.Info
                    )
                }
            }
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                    if (!debt.isSettled) {
                        FinanceButton(
                            label = stringResource(R.string.debts_add_payment),
                            onClick = onAddPayment,
                            modifier = Modifier.weight(1.2f),
                            compact = true
                        )
                    }
                    FinanceButton(
                        label = stringResource(R.string.transactions_edit),
                        onClick = onEdit,
                        variant = FinanceButtonVariant.Secondary,
                        modifier = Modifier.weight(0.8f),
                        compact = true
                    )
                    FinanceButton(
                        label = stringResource(R.string.transactions_delete),
                        onClick = onDelete,
                        variant = FinanceButtonVariant.Text,
                        modifier = Modifier.weight(0.7f),
                        compact = true
                    )
                }
            }
            FinanceButton(
                label = stringResource(R.string.debts_history_count, debt.payments.size),
                onClick = { showHistory = !showHistory },
                variant = FinanceButtonVariant.Text,
                modifier = Modifier.fillMaxWidth()
            )
            if (showHistory) {
                DebtPaymentHistory(
                    debt = debt,
                    canManage = canManage,
                    direction = debt.direction,
                    onEditPayment = onEditPayment,
                    onDeletePayment = onDeletePayment
                )
            }
        }
    }
}

@Composable
private fun DebtAmountDetail(label: String, amount: BigInteger) {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDebtMoney(amount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DebtPaymentHistory(
    debt: FinanceDebt,
    canManage: Boolean,
    direction: DebtDirection,
    onEditPayment: (DebtPayment) -> Unit,
    onDeletePayment: (DebtPayment) -> Unit
) {
    FinanceSectionHeader(title = stringResource(R.string.debts_history_count, debt.payments.size))
    if (debt.payments.isEmpty()) {
        Text(
            stringResource(R.string.debts_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        debt.payments.forEach { payment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatDebtDate(payment.paidOn), style = MaterialTheme.typography.bodyMedium)
                    payment.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FinanceAmountText(
                    formattedAmount = formatDebtMoney(payment.amountCentavos),
                    kind = if (direction == DebtDirection.OwedByMe) {
                        FinanceAmountKind.Expense
                    } else {
                        FinanceAmountKind.Income
                    },
                    accessibilityLabel = "${stringResource(R.string.debts_paid_amount)} ${formatDebtMoney(payment.amountCentavos)}"
                )
                if (canManage) {
                    FinanceButton(
                        label = stringResource(R.string.transactions_edit),
                        onClick = { onEditPayment(payment) },
                        variant = FinanceButtonVariant.Text,
                        compact = true
                    )
                    FinanceButton(
                        label = stringResource(R.string.transactions_delete),
                        onClick = { onDeletePayment(payment) },
                        variant = FinanceButtonVariant.Text,
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtEditorContent(
    direction: DebtDirection,
    onDirectionChange: (DebtDirection) -> Unit,
    counterparty: String,
    onCounterpartyChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    principal: String,
    onPrincipalChange: (String) -> Unit,
    openedOn: String,
    dueOn: String?,
    onOpenDatePicker: (String) -> Unit,
    onClearDueDate: () -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        FinanceSegmentedSelector(
            label = stringResource(R.string.debts_direction_label),
            options = listOf(
                FinanceSegmentOption(
                    DebtDirection.OwedByMe,
                    stringResource(R.string.debts_owed_by_me)
                ),
                FinanceSegmentOption(
                    DebtDirection.OwedToMe,
                    stringResource(R.string.debts_owed_to_me)
                )
            ),
            selectedValue = direction,
            onSelected = onDirectionChange,
            enabled = !isSubmitting
        )
        FinanceTextField(
            value = counterparty,
            onValueChange = onCounterpartyChange,
            label = stringResource(R.string.debts_counterparty_label),
            enabled = !isSubmitting
        )
        FinanceAmountField(
            value = principal,
            onValueChange = onPrincipalChange,
            label = stringResource(R.string.debts_principal_label),
            currencyLabel = stringResource(R.string.transactions_currency),
            enabled = !isSubmitting
        )
        FinanceDateField(
            value = formatDebtDate(openedOn),
            label = stringResource(R.string.debts_opened_on_label),
            chooseDateLabel = stringResource(R.string.debts_choose_date),
            onOpenPicker = { onOpenDatePicker(DATE_FIELD_OPENED) },
            enabled = !isSubmitting
        )
        FinanceDateField(
            value = dueOn?.let(::formatDebtDate) ?: stringResource(R.string.debts_no_due_date),
            label = stringResource(R.string.debts_due_on_label),
            chooseDateLabel = stringResource(R.string.debts_choose_date),
            onOpenPicker = { onOpenDatePicker(DATE_FIELD_DUE) },
            enabled = !isSubmitting
        )
        if (dueOn != null) {
            FinanceButton(
                label = stringResource(R.string.debts_no_due_date),
                onClick = onClearDueDate,
                variant = FinanceButtonVariant.Text,
                enabled = !isSubmitting
            )
        }
        FinanceTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = stringResource(R.string.debts_description_label),
            singleLine = false,
            enabled = !isSubmitting
        )
        errorMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Error) }
        FinanceButton(
            label = stringResource(R.string.debts_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = counterparty.isNotBlank() && parsePositiveAmountToCentavos(principal) != null,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.debts_saving)
        )
    }
}

@Composable
private fun PaymentEditorContent(
    amount: String,
    onAmountChange: (String) -> Unit,
    paidOn: String,
    onOpenDatePicker: () -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
        FinanceAmountField(
            value = amount,
            onValueChange = onAmountChange,
            label = stringResource(R.string.debts_payment_amount_label),
            currencyLabel = stringResource(R.string.transactions_currency),
            enabled = !isSubmitting
        )
        FinanceDateField(
            value = formatDebtDate(paidOn),
            label = stringResource(R.string.debts_payment_date_label),
            chooseDateLabel = stringResource(R.string.debts_choose_date),
            onOpenPicker = onOpenDatePicker,
            enabled = !isSubmitting
        )
        FinanceTextField(
            value = note,
            onValueChange = onNoteChange,
            label = stringResource(R.string.debts_payment_note_label),
            enabled = !isSubmitting
        )
        errorMessage?.let { FinanceStatusBanner(it, tone = FinanceStatusTone.Error) }
        FinanceButton(
            label = stringResource(R.string.debts_payment_save),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = parsePositiveAmountToCentavos(amount) != null,
            isLoading = isSubmitting,
            loadingLabel = stringResource(R.string.debts_payment_saving)
        )
    }
}

private fun formatDebtMoney(centavos: Long): String = formatDebtMoney(BigInteger.valueOf(centavos))

private fun formatDebtMoney(centavos: BigInteger): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-BO")).apply {
        currency = Currency.getInstance("BOB")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return formatter.format(BigDecimal(centavos.abs(), 2))
}

private fun formatAmountInput(centavos: Long): String = BigDecimal.valueOf(centavos, 2).toPlainString()

private fun formatDebtDate(value: String): String = runCatching {
    val date = parseDateMillis(value)?.let(::Date) ?: return value
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(date)
}.getOrDefault(value)

private fun parseDateMillis(value: String): Long? = runCatching {
    SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }.parse(value)?.time
}.getOrNull()

private fun dateFromMillis(value: Long): String = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(value))

private fun todayIsoDate(): String = dateFromMillis(System.currentTimeMillis())

private const val DEBT_FILTER_ALL = "all"
private const val DATE_FIELD_OPENED = "opened"
private const val DATE_FIELD_DUE = "due"
private const val DATE_FIELD_PAYMENT = "payment"
private const val DATE_PATTERN = "yyyy-MM-dd"
