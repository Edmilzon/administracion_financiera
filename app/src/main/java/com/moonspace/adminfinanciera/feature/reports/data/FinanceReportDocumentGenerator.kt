package com.moonspace.adminfinanciera.feature.reports.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportBreakdown
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportExporter
import com.moonspace.adminfinanciera.feature.reports.domain.FinanceReportRow
import com.moonspace.adminfinanciera.feature.reports.domain.GeneratedFinanceReport
import com.moonspace.adminfinanciera.feature.reports.domain.ReportExportFormat
import com.moonspace.adminfinanciera.feature.reports.domain.ReportKindFilter
import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FinanceReportDocumentGenerator(private val cacheDirectory: File) : FinanceReportExporter {
    override suspend fun generate(report: FinanceReport, format: ReportExportFormat): GeneratedFinanceReport {
        val outputDirectory = File(cacheDirectory, REPORT_DIRECTORY)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IllegalStateException("No se pudo preparar el almacenamiento temporal del informe.")
        }
        outputDirectory.listFiles()?.forEach(File::delete)
        val outputFile = File(
            outputDirectory,
            "informe_financiero_${report.filters.startOn}_${report.filters.endOn}.${format.extension}"
        )
        try {
            when (format) {
                ReportExportFormat.Pdf -> writePdf(report, outputFile)
                ReportExportFormat.Excel -> writeWorkbook(report, outputFile)
            }
            return GeneratedFinanceReport(
                filePath = outputFile.absolutePath,
                fileName = outputFile.name,
                format = format
            )
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
    }

    private fun writePdf(report: FinanceReport, outputFile: File) {
        val document = PdfDocument()
        try {
            val writer = FinanceReportPdfWriter(document, report)
            writer.write()
            FileOutputStream(outputFile).use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun writeWorkbook(report: FinanceReport, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            zip.writeEntry("[Content_Types].xml", CONTENT_TYPES_XML)
            zip.writeEntry("_rels/.rels", PACKAGE_RELATIONSHIPS_XML)
            zip.writeEntry("xl/workbook.xml", workbookXml())
            zip.writeEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELATIONSHIPS_XML)
            zip.writeEntry("xl/styles.xml", STYLES_XML)
            zip.writeEntry("xl/worksheets/sheet1.xml", summarySheet(report))
            zip.writeEntry("xl/worksheets/sheet2.xml", transactionsSheet(report))
        }
    }

    private fun summarySheet(report: FinanceReport): String {
        val rows = mutableListOf<List<WorkbookCell>>()
        rows += listOf(WorkbookCell.Text("Informe financiero", WorkbookCell.HEADER))
        rows += listOf(WorkbookCell.Text("Período"), WorkbookCell.Text("${report.filters.startOn} – ${report.filters.endOn}"))
        rows += listOf(WorkbookCell.Text("Alcance"), WorkbookCell.Text(report.scopeLabel))
        rows += listOf(WorkbookCell.Text("Tipo"), WorkbookCell.Text(report.filters.kind.label()))
        rows += listOf(WorkbookCell.Text("Categoría"), WorkbookCell.Text(report.selectedCategoryLabel()))
        rows += listOf(WorkbookCell.Text("Persona"), WorkbookCell.Text(report.selectedMemberLabel()))
        rows.add(emptyList())
        rows += listOf(
            WorkbookCell.Text("Resumen", WorkbookCell.HEADER),
            WorkbookCell.Text("Importe (BOB)", WorkbookCell.HEADER),
            WorkbookCell.Text("Movimientos", WorkbookCell.HEADER)
        )
        rows += listOf(WorkbookCell.Text("Ingresos"), WorkbookCell.Number(report.total.incomeCentavos.toMajorUnits()), WorkbookCell.Number(report.rows.count { it.kind == TransactionKind.Income }))
        rows += listOf(WorkbookCell.Text("Gastos"), WorkbookCell.Number(report.total.expenseCentavos.toMajorUnits()), WorkbookCell.Number(report.rows.count { it.kind == TransactionKind.Expense }))
        rows += listOf(WorkbookCell.Text("Diferencia"), WorkbookCell.Number(report.total.netCentavos.toMajorUnits()), WorkbookCell.Number(report.total.transactionCount))
        rows.add(emptyList())
        rows += listOf(
            WorkbookCell.Text("Por categoría", WorkbookCell.HEADER),
            WorkbookCell.Text("Ingresos (BOB)", WorkbookCell.HEADER),
            WorkbookCell.Text("Gastos (BOB)", WorkbookCell.HEADER),
            WorkbookCell.Text("Diferencia (BOB)", WorkbookCell.HEADER),
            WorkbookCell.Text("Movimientos", WorkbookCell.HEADER)
        )
        report.categoryBreakdown.forEach { rows += it.asWorkbookRow() }
        if (report.memberBreakdown.isNotEmpty()) {
            rows.add(emptyList())
            rows += listOf(
                WorkbookCell.Text("Por persona", WorkbookCell.HEADER),
                WorkbookCell.Text("Ingresos (BOB)", WorkbookCell.HEADER),
                WorkbookCell.Text("Gastos (BOB)", WorkbookCell.HEADER),
                WorkbookCell.Text("Diferencia (BOB)", WorkbookCell.HEADER),
                WorkbookCell.Text("Movimientos", WorkbookCell.HEADER)
            )
            report.memberBreakdown.forEach { rows += it.asWorkbookRow() }
        }
        return worksheetXml(rows, listOf(32, 20, 20, 20, 18))
    }

    private fun transactionsSheet(report: FinanceReport): String {
        val rows = mutableListOf<List<WorkbookCell>>()
        rows += listOf(
            WorkbookCell.Text("Fecha", WorkbookCell.HEADER),
            WorkbookCell.Text("Tipo", WorkbookCell.HEADER),
            WorkbookCell.Text("Categoría", WorkbookCell.HEADER),
            WorkbookCell.Text("Persona", WorkbookCell.HEADER),
            WorkbookCell.Text("Descripción", WorkbookCell.HEADER),
            WorkbookCell.Text("Importe (BOB)", WorkbookCell.HEADER)
        )
        report.rows.forEach { row ->
            rows += listOf(
                WorkbookCell.Text(row.occurredOn),
                WorkbookCell.Text(row.kind.label()),
                WorkbookCell.Text(row.categoryName),
                WorkbookCell.Text(row.memberLabel),
                WorkbookCell.Text(row.description.orEmpty()),
                WorkbookCell.Number(row.amountCentavos.toMajorUnits())
            )
        }
        return worksheetXml(rows, listOf(16, 14, 24, 32, 48, 18))
    }

    private fun FinanceReportBreakdown.asWorkbookRow(): List<WorkbookCell> = listOf(
        WorkbookCell.Text(label),
        WorkbookCell.Number(incomeCentavos.toMajorUnits()),
        WorkbookCell.Number(expenseCentavos.toMajorUnits()),
        WorkbookCell.Number(netCentavos.toMajorUnits()),
        WorkbookCell.Number(transactionCount)
    )

    private fun worksheetXml(rows: List<List<WorkbookCell>>, columnWidths: List<Int>): String = buildString {
        append(XML_DECLARATION)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<cols>")
        columnWidths.forEachIndexed { index, width ->
            append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        append("</cols><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            if (cells.isNotEmpty()) {
                val rowNumber = rowIndex + 1
                append("<row r=\"$rowNumber\">")
                cells.forEachIndexed { columnIndex, cell ->
                    val reference = "${columnName(columnIndex)}$rowNumber"
                    when (cell) {
                        is WorkbookCell.Text -> {
                            append("<c r=\"$reference\" t=\"inlineStr\"")
                            if (cell.style != WorkbookCell.DEFAULT) append(" s=\"${cell.style}\"")
                            append("><is><t xml:space=\"preserve\">${xmlEscape(cell.value)}</t></is></c>")
                        }
                        is WorkbookCell.Number -> {
                            append("<c r=\"$reference\"")
                            if (cell.style != WorkbookCell.DEFAULT) append(" s=\"${cell.style}\"")
                            append("><v>${cell.value}</v></c>")
                        }
                    }
                }
                append("</row>")
            }
        }
        append("</sheetData></worksheet>")
    }

    private fun ZipOutputStream.writeEntry(path: String, contents: String) {
        putNextEntry(ZipEntry(path))
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        var name = ""
        while (value > 0) {
            val remainder = (value - 1) % 26
            name = ('A' + remainder) + name
            value = (value - 1) / 26
        }
        return name
    }

    private fun xmlEscape(value: String): String = value
        .filter { character ->
            character == '\t' || character == '\n' || character == '\r' ||
                (character.code >= 0x20 && character.code != 0xFFFE && character.code != 0xFFFF)
        }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val REPORT_DIRECTORY = "finance-reports"
        const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val CONTENT_TYPES_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
        """.trimIndent()
        val PACKAGE_RELATIONSHIPS_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        val WORKBOOK_RELATIONSHIPS_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
        """.trimIndent()
        val STYLES_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
              <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
              <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
              <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """.trimIndent()
        fun workbookXml(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Resumen" sheetId="1" r:id="rId1"/><sheet name="Movimientos" sheetId="2" r:id="rId2"/></sheets>
            </workbook>
        """.trimIndent()
    }
}

private sealed interface WorkbookCell {
    data class Text(val value: String, val style: Int = DEFAULT) : WorkbookCell
    data class Number(val value: String, val style: Int = NUMBER) : WorkbookCell {
        constructor(value: Int) : this(value.toString(), DEFAULT)
    }

    companion object {
        const val DEFAULT = 0
        const val HEADER = 1
        const val NUMBER = 2
    }
}

private class FinanceReportPdfWriter(
    private val document: PdfDocument,
    private val report: FinanceReport
) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 38f
    private val contentWidth = pageWidth - margin * 2
    private val bottomLimit = pageHeight - 48f
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 38, 33)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 21f
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 59, 51)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 12f
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(47, 56, 52)
        textSize = 9f
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 89, 84)
        textSize = 8f
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 45, 40)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 9f
    }
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0
    private var inMovementTable = false

    fun write() {
        beginPage(isContinuation = false)
        drawTitle()
        drawSummary()
        drawBreakdowns()
        drawTransactions()
        finishPage()
    }

    private fun drawTitle() {
        drawText("Informe financiero", margin, y, headingPaint)
        y += 30f
        drawText("Período: ${report.filters.startOn} – ${report.filters.endOn}", margin, y, bodyPaint)
        y += 15f
        drawText("Alcance: ${report.scopeLabel}", margin, y, bodyPaint)
        y += 15f
        drawText("Filtros: ${report.filters.kind.label()} · ${report.selectedCategoryLabel()} · ${report.selectedMemberLabel()}", margin, y, smallPaint)
        y += 14f
        drawText("Moneda: BOB", margin, y, smallPaint)
        y += 17f
        drawRule()
        y += 12f
    }

    private fun drawSummary() {
        drawSection("Resumen")
        drawSummaryLine("Ingresos", report.total.incomeCentavos.toReportAmount())
        drawSummaryLine("Gastos", report.total.expenseCentavos.toReportAmount())
        drawSummaryLine("Diferencia", report.total.netCentavos.toReportAmount())
        drawSummaryLine("Movimientos", report.total.transactionCount.toString())
    }

    private fun drawBreakdowns() {
        drawSection("Por categoría")
        if (report.categoryBreakdown.isEmpty()) {
            drawBodyLine("No hay movimientos para desglosar.")
        } else {
            report.categoryBreakdown.forEach { drawBreakdownLine(it) }
        }
        if (report.memberBreakdown.isNotEmpty()) {
            drawSection("Por persona")
            report.memberBreakdown.forEach { drawBreakdownLine(it) }
        }
    }

    private fun drawTransactions() {
        drawSection("Movimientos (${report.rows.size})")
        inMovementTable = true
        drawMovementTableHeader()
        report.rows.forEach(::drawTransactionRow)
        inMovementTable = false
    }

    private fun drawSummaryLine(label: String, value: String) {
        ensureSpace(16f)
        drawText(label, margin, y, bodyPaint)
        drawText(value, pageWidth - margin - boldPaint.measureText(value), y, boldPaint)
        y += 15f
    }

    private fun drawSection(title: String) {
        ensureSpace(28f)
        y += 7f
        drawText(title, margin, y, sectionPaint)
        y += 15f
        drawRule()
        y += 9f
    }

    private fun drawBodyLine(value: String) {
        ensureSpace(15f)
        drawText(value, margin, y, bodyPaint)
        y += 14f
    }

    private fun drawBreakdownLine(item: FinanceReportBreakdown) {
        val text = "${item.label}  ·  Ing. ${item.incomeCentavos.toReportAmount()}  ·  Gastos ${item.expenseCentavos.toReportAmount()}  ·  Dif. ${item.netCentavos.toReportAmount()}  ·  ${item.transactionCount} mov."
        ensureSpace(14f)
        drawText(ellipsize(text, bodyPaint, contentWidth), margin, y, bodyPaint)
        y += 13f
    }

    private fun drawMovementTableHeader() {
        ensureSpace(23f)
        val widths = movementColumnWidths()
        val labels = listOf("Fecha", "Tipo", "Categoría", "Persona", "Importe BOB")
        var x = margin
        labels.forEachIndexed { index, label ->
            drawText(ellipsize(label, boldPaint, widths[index] - 5f), x, y, boldPaint)
            x += widths[index]
        }
        y += 5f
        drawRule()
        y += 12f
    }

    private fun drawTransactionRow(row: FinanceReportRow) {
        ensureSpace(22f)
        val widths = movementColumnWidths()
        val values = listOf(
            formatReportDate(row.occurredOn),
            row.kind.label(),
            row.categoryName,
            row.memberLabel
        )
        var x = margin
        values.forEachIndexed { index, value ->
            drawText(ellipsize(value, smallPaint, widths[index] - 5f), x, y, smallPaint)
            x += widths[index]
        }
        val amount = row.amountCentavos.toReportAmount()
        drawText(ellipsize(amount, boldPaint, widths.last() - 4f), x, y, boldPaint)
        y += 13f
        row.description?.takeIf(String::isNotBlank)?.let { description ->
            wrapText("Descripción: $description", bodyPaint, contentWidth - 8f).forEach { line ->
                ensureSpace(12f)
                drawText(line, margin + 5f, y, bodyPaint)
                y += 11f
            }
        }
        y += 4f
        drawRule()
        y += 8f
    }

    private fun movementColumnWidths(): List<Float> {
        val available = contentWidth
        return listOf(58f, 52f, 112f, 154f, available - 376f)
    }

    private fun ensureSpace(height: Float) {
        if (y + height <= bottomLimit) return
        finishPage()
        beginPage(isContinuation = true)
        if (inMovementTable) drawMovementTableHeader()
    }

    private fun beginPage(isContinuation: Boolean) {
        pageNumber += 1
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = document.startPage(pageInfo)
        canvas = page?.canvas
        y = 44f
        if (isContinuation) {
            drawText("Informe financiero · ${report.filters.startOn} – ${report.filters.endOn}", margin, y, boldPaint)
            y += 20f
            drawRule()
            y += 12f
        }
    }

    private fun finishPage() {
        val currentPage = page ?: return
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(185, 194, 188)
            strokeWidth = 0.7f
        }
        currentPage.canvas.drawLine(margin, pageHeight - 37f, pageWidth - margin, pageHeight - 37f, linePaint)
        val footer = "Página $pageNumber"
        val footerPaint = smallPaint
        currentPage.canvas.drawText(
            footer,
            pageWidth - margin - footerPaint.measureText(footer),
            pageHeight - 22f,
            footerPaint
        )
        document.finishPage(currentPage)
        page = null
        canvas = null
    }

    private fun drawRule() {
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(211, 218, 213)
            strokeWidth = 0.6f
        }
        canvas?.drawLine(margin, y, pageWidth - margin, y, rulePaint)
    }

    private fun drawText(value: String, x: Float, baseline: Float, paint: Paint) {
        canvas?.drawText(value, x, baseline, paint)
    }

    private fun ellipsize(value: String, paint: Paint, width: Float): String {
        if (paint.measureText(value) <= width) return value
        val suffix = "…"
        var end = value.length
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > width) end -= 1
        return value.substring(0, end) + suffix
    }

    private fun wrapText(value: String, paint: Paint, width: Float): List<String> {
        val output = mutableListOf<String>()
        var current = StringBuilder()
        value.split(Regex("\\s+")).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > width) {
                output += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) output += current.toString()
        return output.ifEmpty { listOf("") }
    }
}

private fun FinanceReport.selectedCategoryLabel(): String =
    categoryOptions.firstOrNull { it.value == filters.categoryId }?.label ?: "Todas las categorías"

private fun FinanceReport.selectedMemberLabel(): String =
    memberOptions.firstOrNull { it.value == filters.memberId }?.label ?: "Todo el espacio"

private fun ReportKindFilter.label(): String = when (this) {
    ReportKindFilter.All -> "Todos los tipos"
    ReportKindFilter.Income -> "Ingresos"
    ReportKindFilter.Expense -> "Gastos"
}

private fun TransactionKind.label(): String = when (this) {
    TransactionKind.Income -> "Ingreso"
    TransactionKind.Expense -> "Gasto"
}

private fun BigInteger.toMajorUnits(): String = BigDecimal(this, 2).toPlainString()

private fun Long.toMajorUnits(): String = BigDecimal.valueOf(this, 2).toPlainString()

private fun BigInteger.toReportAmount(): String = formatBob(BigDecimal(this, 2))

private fun Long.toReportAmount(): String = formatBob(BigDecimal.valueOf(this, 2))

private fun formatBob(value: BigDecimal): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-BO")).apply {
        currency = Currency.getInstance("BOB")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "${formatter.format(value)} BOB"
}

private fun formatReportDate(value: String): String = runCatching {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-BO")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(parser.parse(value) ?: return value)
}.getOrDefault(value)
