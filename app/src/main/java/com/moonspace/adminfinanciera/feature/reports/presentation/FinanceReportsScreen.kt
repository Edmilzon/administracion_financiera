package com.moonspace.adminfinanciera.feature.reports.presentation

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.core.ui.components.FinanceButton
import com.moonspace.adminfinanciera.core.ui.components.FinanceButtonVariant
import com.moonspace.adminfinanciera.core.ui.components.FinanceEmptyState
import com.moonspace.adminfinanciera.core.ui.components.FinanceErrorState
import com.moonspace.adminfinanciera.core.ui.components.FinanceLoadingState
import com.moonspace.adminfinanciera.core.ui.components.FinanceSectionHeader
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusBanner
import com.moonspace.adminfinanciera.core.ui.components.FinanceStatusTone
import com.moonspace.adminfinanciera.core.ui.dialogs.FinanceDatePickerDialog
import com.moonspace.adminfinanciera.core.ui.theme.FinanceSpacing
import com.moonspace.adminfinanciera.feature.reports.data.FinanceReportPrintAdapter
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.GeneratedFinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.ReportExportFormat
import com.moonspace.adminfinanciera.feature.reports.domain.ReportFilterOption
import com.moonspace.adminfinanciera.feature.reports.domain.ReportKindFilter
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportBreakdownCard
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportExportActions
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportFilterSelector
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportMovementCard
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportPeriodSelector
import com.moonspace.adminfinanciera.feature.reports.presentation.components.ReportSummaryCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

@Composable
fun FinanceReportsScreen(
    state: FinanceReportsUiState,
    onRefresh: () -> Unit,
    onSelectStartDate: (String) -> Unit,
    onSelectEndDate: (String) -> Unit,
    onSelectKind: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectMember: (String?) -> Unit,
    onResetFilters: () -> Unit,
    onGenerate: (ReportExportFormat) -> Unit,
    onDeliveryFailed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveSuccessMessage = stringResource(R.string.reports_save_success)
    val deliveryFailedMessage = stringResource(R.string.reports_delivery_failed)
    val printFailedMessage = stringResource(R.string.reports_print_failed)
    var showingStartPicker by remember { mutableStateOf(false) }
    var showingEndPicker by remember { mutableStateOf(false) }
    var deliveryMessage by remember { mutableStateOf<String?>(null) }
    var deliveryError by remember { mutableStateOf(false) }

    val generatedFile = state.generatedFile
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportExportFormat.Pdf.mimeType)
    ) { uri ->
        val reportFile = generatedFile?.takeIf { it.format == ReportExportFormat.Pdf }
        if (uri != null && reportFile != null) {
            saveGeneratedFile(
                context = context,
                report = reportFile,
                uri = uri,
                scope = scope,
                onSuccess = {
                    deliveryMessage = saveSuccessMessage
                    deliveryError = false
                },
                onFailure = {
                    deliveryMessage = deliveryFailedMessage
                    deliveryError = true
                    onDeliveryFailed()
                }
            )
        }
    }
    val saveExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportExportFormat.Excel.mimeType)
    ) { uri ->
        val reportFile = generatedFile?.takeIf { it.format == ReportExportFormat.Excel }
        if (uri != null && reportFile != null) {
            saveGeneratedFile(
                context = context,
                report = reportFile,
                uri = uri,
                scope = scope,
                onSuccess = {
                    deliveryMessage = saveSuccessMessage
                    deliveryError = false
                },
                onFailure = {
                    deliveryMessage = deliveryFailedMessage
                    deliveryError = true
                    onDeliveryFailed()
                }
            )
        }
    }

    if (showingStartPicker) {
        FinanceDatePickerDialog(
            title = stringResource(R.string.reports_choose_start),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = parseReportDateMillis(state.selectedStartOn),
            onDateSelected = { dateMillis ->
                onSelectStartDate(reportDateFromMillis(dateMillis))
                showingStartPicker = false
                deliveryMessage = null
            },
            onDismissRequest = { showingStartPicker = false }
        )
    }
    if (showingEndPicker) {
        FinanceDatePickerDialog(
            title = stringResource(R.string.reports_choose_end),
            confirmLabel = stringResource(R.string.ui_confirm),
            dismissLabel = stringResource(R.string.ui_cancel),
            initialDateMillis = parseReportDateMillis(state.selectedEndOn),
            onDateSelected = { dateMillis ->
                onSelectEndDate(reportDateFromMillis(dateMillis))
                showingEndPicker = false
                deliveryMessage = null
            },
            onDismissRequest = { showingEndPicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FinanceSpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
                Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.XSmall)) {
                    Text(
                        text = stringResource(R.string.reports_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reports_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (deliveryMessage != null) {
                    FinanceStatusBanner(
                        message = requireNotNull(deliveryMessage),
                        tone = if (deliveryError) FinanceStatusTone.Error else FinanceStatusTone.Success
                    )
                }
                if (state.actionErrorMessage != null) {
                    FinanceStatusBanner(state.actionErrorMessage, tone = FinanceStatusTone.Error)
                }
            }
        }
        when {
            state.isLoading -> item { FinanceLoadingState(stringResource(R.string.reports_loading)) }
            state.errorMessage != null -> item {
                FinanceErrorState(
                    title = stringResource(R.string.reports_load_failed_title),
                    description = state.errorMessage,
                    retryLabel = stringResource(R.string.transactions_retry),
                    onRetry = onRefresh
                )
            }
            !state.hasHousehold -> item {
                FinanceEmptyState(
                    title = stringResource(R.string.reports_no_household_title),
                    description = stringResource(R.string.reports_no_household_description)
                )
            }
            state.report != null -> reportItems(
                report = state.report,
                state = state,
                onSelectStartDate = { showingStartPicker = true },
                onSelectEndDate = { showingEndPicker = true },
                onSelectKind = onSelectKind,
                onSelectCategory = onSelectCategory,
                onSelectMember = onSelectMember,
                onResetFilters = {
                    deliveryMessage = null
                    onResetFilters()
                },
                onGenerate = onGenerate,
                onSave = {
                    state.generatedFile?.let { currentFile ->
                        deliveryMessage = null
                        when (currentFile.format) {
                            ReportExportFormat.Pdf -> savePdfLauncher.launch(currentFile.fileName)
                            ReportExportFormat.Excel -> saveExcelLauncher.launch(currentFile.fileName)
                        }
                    }
                },
                onShare = {
                    state.generatedFile?.let { reportFile ->
                        deliveryMessage = null
                        shareGeneratedFile(context, reportFile, onDeliveryFailed) { message ->
                            deliveryMessage = message
                            deliveryError = true
                        }
                    }
                },
                onPrint = {
                    state.generatedFile?.takeIf { it.format == ReportExportFormat.Pdf }?.let { reportFile ->
                        runCatching { printGeneratedPdf(context, reportFile) }
                            .onFailure {
                                deliveryMessage = printFailedMessage
                                deliveryError = true
                                onDeliveryFailed()
                            }
                    }
                }
            )
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.height(FinanceSpacing.Large)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(
    report: FinanceReport,
    state: FinanceReportsUiState,
    onSelectStartDate: () -> Unit,
    onSelectEndDate: () -> Unit,
    onSelectKind: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectMember: (String?) -> Unit,
    onResetFilters: () -> Unit,
    onGenerate: (ReportExportFormat) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(FinanceSpacing.Medium)) {
            FinanceSectionHeader(
                title = stringResource(R.string.reports_filters_title),
                supportingText = stringResource(R.string.reports_scope, report.scopeLabel)
            )
            ReportPeriodSelector(
                startLabel = formatReportDate(state.selectedStartOn),
                endLabel = formatReportDate(state.selectedEndOn),
                onStartClick = onSelectStartDate,
                onEndClick = onSelectEndDate
            )
            ReportFilterSelector(
                label = stringResource(R.string.reports_kind_filter),
                options = listOf(
                    ReportFilterOption("all", stringResource(R.string.reports_all_kinds)),
                    ReportFilterOption("income", stringResource(R.string.transactions_income)),
                    ReportFilterOption("expense", stringResource(R.string.transactions_expense))
                ),
                selectedValue = when (state.selectedKind) {
                    ReportKindFilter.All -> "all"
                    ReportKindFilter.Income -> "income"
                    ReportKindFilter.Expense -> "expense"
                },
                onSelected = { value -> onSelectKind(value?.takeUnless { it == "all" }) }
            )
            ReportFilterSelector(
                label = stringResource(R.string.reports_category_filter),
                options = report.categoryOptions,
                selectedValue = state.selectedCategoryId,
                onSelected = onSelectCategory
            )
            if (state.isAdministrator) {
                ReportFilterSelector(
                    label = stringResource(R.string.reports_member_filter),
                    options = report.memberOptions,
                    selectedValue = state.selectedMemberId,
                    onSelected = onSelectMember
                )
            }
            FinanceButton(
                label = stringResource(R.string.reports_reset_filters),
                onClick = onResetFilters,
                modifier = Modifier.fillMaxWidth(),
                variant = FinanceButtonVariant.Text
            )
        }
    }
    item {
        FinanceSectionHeader(
            title = stringResource(R.string.reports_summary_title),
            supportingText = pluralStringResource(
                R.plurals.reports_result_count,
                report.rows.size,
                report.rows.size,
                formatReportDate(state.selectedStartOn),
                formatReportDate(state.selectedEndOn)
            )
        )
    }
    item { ReportSummaryCard(report) }
    item {
        FinanceSectionHeader(
            title = stringResource(R.string.reports_categories_title),
            supportingText = stringResource(R.string.reports_categories_description)
        )
    }
    if (report.categoryBreakdown.isEmpty()) {
        item {
            FinanceEmptyState(
                title = stringResource(R.string.reports_no_breakdown_title),
                description = stringResource(R.string.reports_no_breakdown_description)
            )
        }
    } else {
        item {
            ReportBreakdownCard(
                title = stringResource(R.string.reports_categories_title),
                items = report.categoryBreakdown
            )
        }
    }
    if (report.memberBreakdown.isNotEmpty()) {
        item {
            ReportBreakdownCard(
                title = stringResource(R.string.reports_members_title),
                items = report.memberBreakdown
            )
        }
    }
    item { FinanceSectionHeader(title = stringResource(R.string.reports_transactions_title)) }
    if (report.rows.isEmpty()) {
        item {
            FinanceEmptyState(
                title = stringResource(R.string.reports_no_movements_title),
                description = stringResource(R.string.reports_no_movements_description)
            )
        }
    } else {
        items(report.rows, key = { it.id }) { row -> ReportMovementCard(row) }
    }
    item {
        ReportExportActions(
            isGenerating = state.isGenerating,
            generatedFile = state.generatedFile,
            onGeneratePdf = { onGenerate(ReportExportFormat.Pdf) },
            onGenerateExcel = { onGenerate(ReportExportFormat.Excel) },
            onSave = onSave,
            onShare = onShare,
            onPrint = onPrint
        )
    }
}

private fun saveGeneratedFile(
    context: Context,
    report: GeneratedFinanceReport,
    uri: android.net.Uri,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit,
    onFailure: () -> Unit
) {
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")
                    ?.use { output -> FileInputStream(File(report.filePath)).use { input -> input.copyTo(output) } }
                    ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
            }
        }
        if (result.isSuccess) onSuccess() else onFailure()
    }
}

private fun shareGeneratedFile(
    context: Context,
    report: GeneratedFinanceReport,
    onFailure: () -> Unit,
    onErrorMessage: (String) -> Unit
) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            File(report.filePath)
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = report.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, report.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.reports_share_chooser)))
    } catch (_: Exception) {
        onErrorMessage(context.getString(R.string.reports_share_failed))
        onFailure()
    }
}

private fun printGeneratedPdf(context: Context, report: GeneratedFinanceReport) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(
        report.fileName.substringBeforeLast('.'),
        FinanceReportPrintAdapter(File(report.filePath)),
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    )
}
