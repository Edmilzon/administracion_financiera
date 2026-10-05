package com.moonspace.adminfinanciera.feature.reports.domain

import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceCategory
import com.moonspace.adminfinanciera.feature.transactions.domain.FinanceTransaction
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import com.moonspace.adminfinanciera.feature.users.domain.HouseholdMember
import java.math.BigInteger

enum class ReportExportFormat(val extension: String, val mimeType: String) {
    Pdf("pdf", "application/pdf"),
    Excel("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
}

data class GeneratedFinanceReport(
    val filePath: String,
    val fileName: String,
    val format: ReportExportFormat
)

interface FinanceReportExporter {
    suspend fun generate(report: FinanceReport, format: ReportExportFormat): GeneratedFinanceReport
}

enum class ReportKindFilter {
    All,
    Income,
    Expense
}

data class ReportFilters(
    val startOn: String,
    val endOn: String,
    val kind: ReportKindFilter = ReportKindFilter.All,
    val categoryId: String? = null,
    val memberId: String? = null
)

data class FinanceReportRow(
    val id: String,
    val occurredOn: String,
    val kind: TransactionKind,
    val amountCentavos: Long,
    val categoryId: String,
    val categoryName: String,
    val createdBy: String,
    val memberLabel: String,
    val description: String?
)

data class FinanceReportTotal(
    val incomeCentavos: BigInteger,
    val expenseCentavos: BigInteger,
    val netCentavos: BigInteger,
    val transactionCount: Int
)

data class FinanceReportBreakdown(
    val label: String,
    val incomeCentavos: BigInteger,
    val expenseCentavos: BigInteger,
    val netCentavos: BigInteger,
    val transactionCount: Int
)

data class ReportFilterOption(
    val value: String?,
    val label: String
)

data class FinanceReport(
    val filters: ReportFilters,
    val scopeLabel: String,
    val rows: List<FinanceReportRow>,
    val total: FinanceReportTotal,
    val categoryBreakdown: List<FinanceReportBreakdown>,
    val memberBreakdown: List<FinanceReportBreakdown>,
    val categoryOptions: List<ReportFilterOption>,
    val memberOptions: List<ReportFilterOption>
)

fun buildFinanceReport(
    transactions: List<FinanceTransaction>,
    categories: List<FinanceCategory>,
    members: List<HouseholdMember>,
    currentUserId: String,
    currentUserLabel: String,
    isAdministrator: Boolean,
    filters: ReportFilters
): FinanceReport {
    val categoriesById = categories.associateBy(FinanceCategory::id)
    val memberLabels = members.associate { member -> member.userId to member.email }
    val selectedMemberId = if (isAdministrator) filters.memberId else currentUserId
    val safeFilters = filters.copy(memberId = selectedMemberId)
    val rows = transactions.asSequence()
        .filter { it.occurredOn >= safeFilters.startOn && it.occurredOn <= safeFilters.endOn }
        .filter { safeFilters.kind.matches(it.kind) }
        .filter { safeFilters.categoryId == null || it.categoryId == safeFilters.categoryId }
        .filter { selectedMemberId == null || it.createdBy == selectedMemberId }
        .mapNotNull { transaction ->
            val category = categoriesById[transaction.categoryId] ?: return@mapNotNull null
            FinanceReportRow(
                id = transaction.id,
                occurredOn = transaction.occurredOn,
                kind = transaction.kind,
                amountCentavos = transaction.amountCentavos,
                categoryId = transaction.categoryId,
                categoryName = category.name,
                createdBy = transaction.createdBy,
                memberLabel = memberLabels[transaction.createdBy]
                    ?: if (transaction.createdBy == currentUserId) currentUserLabel else transaction.createdBy,
                description = transaction.description
            )
        }
        .sortedWith(compareByDescending<FinanceReportRow>(FinanceReportRow::occurredOn).thenBy(FinanceReportRow::id))
        .toList()

    val total = rows.toTotal()
    val categoryBreakdown = rows.groupBy(FinanceReportRow::categoryName)
        .map { (label, groupRows) -> groupRows.toBreakdown(label) }
        .sortedByDescending { it.incomeCentavos + it.expenseCentavos }
    val memberBreakdown = if (isAdministrator) {
        rows.groupBy(FinanceReportRow::memberLabel)
            .map { (label, groupRows) -> groupRows.toBreakdown(label) }
            .sortedByDescending { it.incomeCentavos + it.expenseCentavos }
    } else {
        emptyList()
    }
    val selectedMemberLabel = selectedMemberId?.let { memberLabels[it] }
    return FinanceReport(
        filters = safeFilters,
        scopeLabel = when {
            !isAdministrator -> currentUserLabel
            selectedMemberId == null -> "Espacio completo"
            else -> selectedMemberLabel ?: selectedMemberId
        },
        rows = rows,
        total = total,
        categoryBreakdown = categoryBreakdown,
        memberBreakdown = memberBreakdown,
        categoryOptions = listOf(ReportFilterOption(null, "Todas las categorías")) +
            categories.sortedBy(FinanceCategory::name).map { ReportFilterOption(it.id, it.name) },
        memberOptions = listOf(ReportFilterOption(null, "Todo el espacio")) +
            members.sortedBy(HouseholdMember::email).map { ReportFilterOption(it.userId, it.email) }
    )
}

private fun ReportKindFilter.matches(kind: TransactionKind): Boolean = when (this) {
    ReportKindFilter.All -> true
    ReportKindFilter.Income -> kind == TransactionKind.Income
    ReportKindFilter.Expense -> kind == TransactionKind.Expense
}

private fun List<FinanceReportRow>.toTotal(): FinanceReportTotal {
    val income = filter { it.kind == TransactionKind.Income }.sumAmount()
    val expense = filter { it.kind == TransactionKind.Expense }.sumAmount()
    return FinanceReportTotal(
        incomeCentavos = income,
        expenseCentavos = expense,
        netCentavos = income - expense,
        transactionCount = size
    )
}

private fun List<FinanceReportRow>.toBreakdown(label: String): FinanceReportBreakdown {
    val income = filter { it.kind == TransactionKind.Income }.sumAmount()
    val expense = filter { it.kind == TransactionKind.Expense }.sumAmount()
    return FinanceReportBreakdown(
        label = label,
        incomeCentavos = income,
        expenseCentavos = expense,
        netCentavos = income - expense,
        transactionCount = size
    )
}

private fun List<FinanceReportRow>.sumAmount(): BigInteger = fold(BigInteger.ZERO) { sum, row ->
    sum + BigInteger.valueOf(row.amountCentavos)
}
