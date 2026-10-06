package com.moonspace.adminfinanciera.feature.reports.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import com.moonspace.adminfinanciera.core.ui.theme.FinanceColors
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
            zip.writeEntry("xl/styles.xml", stylesXml())
            zip.writeEntry("xl/worksheets/sheet1.xml", summarySheet(report))
            zip.writeEntry("xl/worksheets/sheet2.xml", transactionsSheet(report))
        }
    }

    private fun summarySheet(report: FinanceReport): String {
        val rows = mutableListOf<List<WorkbookCell>>()
        val mergedRanges = mutableListOf("A1:E1")
        fun addSection(title: String) {
            val rowNumber = rows.size + 1
            rows += listOf(WorkbookCell.Text(title, WorkbookCell.SECTION))
            mergedRanges += "A$rowNumber:E$rowNumber"
        }

        rows += listOf(WorkbookCell.Text("Nexo Finanzas · Informe financiero", WorkbookCell.TITLE))
        rows += listOf(WorkbookCell.Text("Período", WorkbookCell.META_LABEL), WorkbookCell.Text("${formatReportDate(report.filters.startOn)} – ${formatReportDate(report.filters.endOn)}", WorkbookCell.META_VALUE))
        rows += listOf(WorkbookCell.Text("Alcance", WorkbookCell.META_LABEL), WorkbookCell.Text(report.scopeLabel, WorkbookCell.META_VALUE))
        rows += listOf(WorkbookCell.Text("Tipo", WorkbookCell.META_LABEL), WorkbookCell.Text(report.filters.kind.label(), WorkbookCell.META_VALUE))
        rows += listOf(WorkbookCell.Text("Categoría", WorkbookCell.META_LABEL), WorkbookCell.Text(report.selectedCategoryLabel(), WorkbookCell.META_VALUE))
        rows += listOf(WorkbookCell.Text("Persona", WorkbookCell.META_LABEL), WorkbookCell.Text(report.selectedMemberLabel(), WorkbookCell.META_VALUE))
        rows.add(emptyList())
        addSection("Resumen")
        rows += listOf(
            WorkbookCell.Text("Concepto", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Importe (BOB)", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Movimientos", WorkbookCell.TABLE_HEADER)
        )
        rows += listOf(WorkbookCell.Text("Ingresos", WorkbookCell.INCOME_LABEL), WorkbookCell.Number(report.total.incomeCentavos.toMajorUnits(), WorkbookCell.INCOME), WorkbookCell.Number(report.rows.count { it.kind == TransactionKind.Income }))
        rows += listOf(WorkbookCell.Text("Gastos", WorkbookCell.EXPENSE_LABEL), WorkbookCell.Number(report.total.expenseCentavos.toMajorUnits(), WorkbookCell.EXPENSE), WorkbookCell.Number(report.rows.count { it.kind == TransactionKind.Expense }))
        val netStyle = when (report.total.netCentavos.signum()) {
            -1 -> WorkbookCell.EXPENSE
            1 -> WorkbookCell.INCOME
            else -> WorkbookCell.NUMBER
        }
        val netLabelStyle = when (report.total.netCentavos.signum()) {
            -1 -> WorkbookCell.EXPENSE_LABEL
            1 -> WorkbookCell.INCOME_LABEL
            else -> WorkbookCell.DEFAULT
        }
        rows += listOf(WorkbookCell.Text("Diferencia", netLabelStyle), WorkbookCell.Number(report.total.netCentavos.toMajorUnits(), netStyle), WorkbookCell.Number(report.total.transactionCount))
        rows.add(emptyList())
        addSection("Por categoría")
        rows += listOf(
            WorkbookCell.Text("Categoría", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Ingresos (BOB)", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Gastos (BOB)", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Diferencia (BOB)", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Movimientos", WorkbookCell.TABLE_HEADER)
        )
        report.categoryBreakdown.forEach { rows += it.asWorkbookRow() }
        if (report.memberBreakdown.isNotEmpty()) {
            rows.add(emptyList())
            addSection("Por persona")
            rows += listOf(
                WorkbookCell.Text("Integrante", WorkbookCell.TABLE_HEADER),
                WorkbookCell.Text("Ingresos (BOB)", WorkbookCell.TABLE_HEADER),
                WorkbookCell.Text("Gastos (BOB)", WorkbookCell.TABLE_HEADER),
                WorkbookCell.Text("Diferencia (BOB)", WorkbookCell.TABLE_HEADER),
                WorkbookCell.Text("Movimientos", WorkbookCell.TABLE_HEADER)
            )
            report.memberBreakdown.forEach { rows += it.asWorkbookRow() }
        }
        return worksheetXml(
            rows,
            columnWidths = listOf(32, 20, 20, 20, 18),
            mergedRanges = mergedRanges,
            frozenRows = 1
        )
    }

    private fun transactionsSheet(report: FinanceReport): String {
        val rows = mutableListOf<List<WorkbookCell>>()
        rows += listOf(
            WorkbookCell.Text("Fecha", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Tipo", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Categoría", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Persona", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Descripción", WorkbookCell.TABLE_HEADER),
            WorkbookCell.Text("Importe (BOB)", WorkbookCell.TABLE_HEADER)
        )
        report.rows.forEach { row ->
            rows += listOf(
                WorkbookCell.Text(formatReportDate(row.occurredOn)),
                WorkbookCell.Text(
                    row.kind.label(),
                    if (row.kind == TransactionKind.Income) WorkbookCell.INCOME_LABEL else WorkbookCell.EXPENSE_LABEL
                ),
                WorkbookCell.Text(row.categoryName),
                WorkbookCell.Text(row.memberLabel),
                WorkbookCell.Text(row.description.orEmpty()),
                WorkbookCell.Number(
                    row.amountCentavos.toMajorUnits(),
                    if (row.kind == TransactionKind.Income) WorkbookCell.INCOME else WorkbookCell.EXPENSE
                )
            )
        }
        return worksheetXml(
            rows,
            columnWidths = listOf(16, 14, 24, 32, 48, 18),
            autoFilter = "A1:F${report.rows.size + 1}",
            frozenRows = 1
        )
    }

    private fun FinanceReportBreakdown.asWorkbookRow(): List<WorkbookCell> = listOf(
        WorkbookCell.Text(label),
        WorkbookCell.Number(incomeCentavos.toMajorUnits(), WorkbookCell.INCOME),
        WorkbookCell.Number(expenseCentavos.toMajorUnits(), WorkbookCell.EXPENSE),
        WorkbookCell.Number(
            netCentavos.toMajorUnits(),
            when (netCentavos.signum()) {
                -1 -> WorkbookCell.EXPENSE
                1 -> WorkbookCell.INCOME
                else -> WorkbookCell.NUMBER
            }
        ),
        WorkbookCell.Number(transactionCount)
    )

    private fun worksheetXml(
        rows: List<List<WorkbookCell>>,
        columnWidths: List<Int>,
        mergedRanges: List<String> = emptyList(),
        autoFilter: String? = null,
        frozenRows: Int = 0
    ): String = buildString {
        append(XML_DECLARATION)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\">")
        if (frozenRows > 0) {
            append("<pane ySplit=\"$frozenRows\" topLeftCell=\"A${frozenRows + 1}\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        }
        append("</sheetView></sheetViews>")
        append("<cols>")
        columnWidths.forEachIndexed { index, width ->
            append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        append("</cols><sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            if (cells.isNotEmpty()) {
                val rowNumber = rowIndex + 1
                if (rowIndex == 0) {
                    append("<row r=\"$rowNumber\" ht=\"30\" customHeight=\"1\">")
                } else {
                    append("<row r=\"$rowNumber\">")
                }
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
        append("</sheetData>")
        autoFilter?.let { append("<autoFilter ref=\"$it\"/>") }
        if (mergedRanges.isNotEmpty()) {
            append("<mergeCells count=\"${mergedRanges.size}\">")
            mergedRanges.forEach { range -> append("<mergeCell ref=\"$range\"/>") }
            append("</mergeCells>")
        }
        append("</worksheet>")
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
        fun stylesXml(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00 &quot;BOB&quot;;#,##0.00 &quot;BOB&quot;;0.00 &quot;BOB&quot;"/></numFmts>
              <fonts count="7">
                <font><sz val="10"/><color rgb="FF${FinanceColors.OnSurface.toExcelRgb()}"/><name val="Aptos"/></font>
                <font><b/><sz val="18"/><color rgb="FF${FinanceColors.OnPrimary.toExcelRgb()}"/><name val="Aptos Display"/></font>
                <font><b/><sz val="10"/><color rgb="FF${FinanceColors.Primary.toExcelRgb()}"/><name val="Aptos"/></font>
                <font><b/><sz val="10"/><color rgb="FF${FinanceColors.Success.toExcelRgb()}"/><name val="Aptos"/></font>
                <font><b/><sz val="10"/><color rgb="FF${FinanceColors.Expense.toExcelRgb()}"/><name val="Aptos"/></font>
                <font><b/><sz val="10"/><color rgb="FF${FinanceColors.OnPrimary.toExcelRgb()}"/><name val="Aptos"/></font>
                <font><b/><sz val="10"/><color rgb="FF${FinanceColors.OnSurface.toExcelRgb()}"/><name val="Aptos"/></font>
              </fonts>
              <fills count="7">
                <fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF${FinanceColors.Primary.toExcelRgb()}"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF${FinanceColors.Secondary.toExcelRgb()}"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF${FinanceColors.SurfaceVariant.toExcelRgb()}"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF${FinanceColors.SuccessContainer.toExcelRgb()}"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FF${FinanceColors.ExpenseContainer.toExcelRgb()}"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="2">
                <border><left/><right/><top/><bottom/><diagonal/></border>
                <border><left/><right/><top/><bottom style="thin"><color rgb="FF${FinanceColors.OutlineVariant.toExcelRgb()}"/></bottom><diagonal/></border>
              </borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="13">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
                <xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
                <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
                <xf numFmtId="0" fontId="5" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
                <xf numFmtId="0" fontId="5" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
                <xf numFmtId="164" fontId="3" fillId="5" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
                <xf numFmtId="164" fontId="4" fillId="6" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
                <xf numFmtId="164" fontId="2" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
                <xf numFmtId="0" fontId="3" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
                <xf numFmtId="0" fontId="4" fillId="6" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
                <xf numFmtId="0" fontId="2" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
              </cellXfs>
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
        const val TITLE = 1
        const val META_LABEL = 2
        const val META_VALUE = 3
        const val SECTION = 4
        const val TABLE_HEADER = 5
        const val NUMBER = 6
        const val INCOME = 7
        const val EXPENSE = 8
        const val NET = 9
        const val INCOME_LABEL = 10
        const val EXPENSE_LABEL = 11
        const val NET_LABEL = 12
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
    private val primary = FinanceColors.Primary.toArgb()
    private val primaryContainer = FinanceColors.PrimaryContainer.toArgb()
    private val darkText = FinanceColors.OnSurface.toArgb()
    private val mutedText = FinanceColors.OnSurfaceVariant.toArgb()
    private val borderColor = FinanceColors.OutlineVariant.toArgb()
    private val headingPaint = textPaint(Color.WHITE, 23f, bold = true)
    private val brandPaint = textPaint(Color.WHITE, 8f, bold = true)
    private val logoPaint = textPaint(FinanceColors.OnPrimaryContainer.toArgb(), 11f, bold = true)
    private val headerMetaPaint = textPaint(Color.WHITE, 9f)
    private val sourcePaint = textPaint(FinanceColors.PrimaryContainer.toArgb(), 7.5f, bold = true)
    private val sectionPaint = textPaint(Color.WHITE, 10f, bold = true)
    private val bodyPaint = textPaint(darkText, 9f)
    private val smallPaint = textPaint(mutedText, 8f)
    private val boldPaint = textPaint(darkText, 9f, bold = true)
    private val incomePaint = textPaint(FinanceColors.Success.toArgb(), 8f, bold = true)
    private val expensePaint = textPaint(FinanceColors.Expense.toArgb(), 8f, bold = true)
    private val whiteSmallPaint = textPaint(Color.WHITE, 8f, bold = true)
    private val primaryFillPaint = fillPaint(primary)
    private val secondaryFillPaint = fillPaint(FinanceColors.Secondary.toArgb())
    private val primaryContainerFillPaint = fillPaint(primaryContainer)
    private val incomeFillPaint = fillPaint(FinanceColors.SuccessContainer.toArgb())
    private val expenseFillPaint = fillPaint(FinanceColors.ExpenseContainer.toArgb())
    private val surfaceVariantFillPaint = fillPaint(FinanceColors.SurfaceVariant.toArgb())
    private val backgroundFillPaint = fillPaint(FinanceColors.Background.toArgb())
    private val whiteFillPaint = fillPaint(Color.WHITE)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        strokeWidth = 0.7f
    }
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0
    private var inMovementTable = false
    private var breakdownRowIndex = 0
    private var movementRowIndex = 0

    fun write() {
        beginPage(isContinuation = false)
        drawTitle()
        drawSummary()
        drawBreakdowns()
        drawTransactions()
        finishPage()
    }

    private fun drawTitle() {
        val scopeLines = wrapText("Alcance: ${report.scopeLabel}", headerMetaPaint, contentWidth)
        val filterText = "Filtros: ${report.filters.kind.label()} · ${report.selectedCategoryLabel()} · ${report.selectedMemberLabel()}"
        val filterLines = wrapText(filterText, headerMetaPaint, contentWidth)
        val scopeY = 116f
        val filtersY = scopeY + scopeLines.size * 12f + 2f
        val sourceY = filtersY + filterLines.size * 12f + 13f
        val headerBottom = sourceY + 13f

        canvas?.drawRect(0f, 0f, pageWidth.toFloat(), headerBottom, primaryFillPaint)
        canvas?.drawRect(0f, 0f, pageWidth.toFloat(), 6f, secondaryFillPaint)
        canvas?.drawRoundRect(RectF(margin, 22f, margin + 35f, 57f), 9f, 9f, primaryContainerFillPaint)
        drawText("NF", margin + 8f, 44f, logoPaint)
        drawText("NEXO FINANZAS", margin + 45f, 43f, brandPaint)
        drawText("Informe financiero", margin, 83f, headingPaint)
        drawText(
            "Período: ${formatReportDate(report.filters.startOn)} – ${formatReportDate(report.filters.endOn)}",
            margin,
            101f,
            headerMetaPaint
        )
        scopeLines.forEachIndexed { index, line -> drawText(line, margin, scopeY + index * 12f, headerMetaPaint) }
        filterLines.forEachIndexed { index, line -> drawText(line, margin, filtersY + index * 12f, headerMetaPaint) }
        drawText("Datos disponibles en este dispositivo · Moneda BOB", margin, sourceY, sourcePaint)
        y = headerBottom + 18f
    }

    private fun drawSummary() {
        drawSection("Resumen")
        val gap = 8f
        val cardWidth = (contentWidth - gap * 2f) / 3f
        drawMetricCard(
            x = margin,
            width = cardWidth,
            label = "Ingresos",
            value = report.total.incomeCentavos.toReportAmount(),
            textPaint = incomePaint,
            fillPaint = incomeFillPaint
        )
        drawMetricCard(
            x = margin + cardWidth + gap,
            width = cardWidth,
            label = "Gastos",
            value = report.total.expenseCentavos.toReportAmount(),
            textPaint = expensePaint,
            fillPaint = expenseFillPaint
        )
        val netPaint = when (report.total.netCentavos.signum()) {
            -1 -> expensePaint
            1 -> incomePaint
            else -> boldPaint
        }
        val netFill = when (report.total.netCentavos.signum()) {
            -1 -> expenseFillPaint
            1 -> incomeFillPaint
            else -> surfaceVariantFillPaint
        }
        drawMetricCard(
            x = margin + (cardWidth + gap) * 2f,
            width = cardWidth,
            label = "Diferencia",
            value = report.total.netCentavos.abs().toReportAmount(),
            textPaint = netPaint,
            fillPaint = netFill
        )
        y += 57f
        drawSummaryLine("Movimientos registrados", report.total.transactionCount.toString())
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
        drawText(label, margin + 4f, y, bodyPaint)
        drawText(value, pageWidth - margin - boldPaint.measureText(value), y, boldPaint)
        y += 17f
    }

    private fun drawSection(title: String) {
        ensureSpace(32f)
        val top = y - 12f
        canvas?.drawRoundRect(RectF(margin, top, pageWidth - margin, top + 23f), 6f, 6f, primaryFillPaint)
        drawText(title, margin + 10f, y + 3f, sectionPaint)
        y += 24f
    }

    private fun drawBodyLine(value: String) {
        ensureSpace(15f)
        drawText(value, margin, y, bodyPaint)
        y += 14f
    }

    private fun drawBreakdownLine(item: FinanceReportBreakdown) {
        ensureSpace(43f)
        val top = y - 12f
        canvas?.drawRoundRect(
            RectF(margin, top, pageWidth - margin, top + 39f),
            5f,
            5f,
            if (breakdownRowIndex++ % 2 == 0) backgroundFillPaint else surfaceVariantFillPaint
        )
        val countLabel = "${item.transactionCount} mov."
        val labelWidth = contentWidth - boldPaint.measureText(countLabel) - 24f
        drawText(ellipsize(item.label, boldPaint, labelWidth), margin + 9f, y + 1f, boldPaint)
        drawText(countLabel, pageWidth - margin - 9f - smallPaint.measureText(countLabel), y + 1f, smallPaint)

        val metricWidth = contentWidth / 3f
        val incomeText = "Ing. ${item.incomeCentavos.toReportAmount()}"
        val expenseText = "Gasto ${item.expenseCentavos.toReportAmount()}"
        val netText = "Dif. ${item.netCentavos.abs().toReportAmount()}"
        val netPaint = when (item.netCentavos.signum()) {
            -1 -> expensePaint
            1 -> incomePaint
            else -> boldPaint
        }
        drawText(ellipsize(incomeText, incomePaint, metricWidth - 12f), margin + 9f, y + 19f, incomePaint)
        drawText(ellipsize(expenseText, expensePaint, metricWidth - 12f), margin + metricWidth + 4f, y + 19f, expensePaint)
        drawText(ellipsize(netText, netPaint, metricWidth - 12f), margin + metricWidth * 2f, y + 19f, netPaint)
        y += 45f
    }

    private fun drawMovementTableHeader() {
        ensureSpace(27f)
        val widths = movementColumnWidths()
        val labels = listOf("Fecha", "Tipo", "Categoría", "Persona", "Importe BOB")
        canvas?.drawRoundRect(
            RectF(margin, y - 13f, pageWidth - margin, y + 7f),
            4f,
            4f,
            secondaryFillPaint
        )
        var x = margin
        labels.forEachIndexed { index, label ->
            drawText(ellipsize(label, whiteSmallPaint, widths[index] - 5f), x + 4f, y, whiteSmallPaint)
            x += widths[index]
        }
        y += 20f
    }

    private fun drawTransactionRow(row: FinanceReportRow) {
        val descriptionLines = row.description
            ?.takeIf(String::isNotBlank)
            ?.let { wrapText("Descripción: $it", bodyPaint, contentWidth - 18f) }
            .orEmpty()
        val rowHeight = 25f + descriptionLines.size * 11f
        ensureSpace(rowHeight)
        val widths = movementColumnWidths()
        val rowTop = y - 11f
        val rowBottom = y + rowHeight - 13f
        canvas?.drawRect(
            margin,
            rowTop,
            pageWidth - margin,
            rowBottom,
            if (movementRowIndex++ % 2 == 0) backgroundFillPaint else whiteFillPaint
        )
        val values = listOf(
            formatReportDate(row.occurredOn),
            row.kind.label(),
            row.categoryName,
            row.memberLabel
        )
        val typePaint = if (row.kind == TransactionKind.Income) incomePaint else expensePaint
        var x = margin
        values.forEachIndexed { index, value ->
            val paint = if (index == 1) typePaint else smallPaint
            drawText(ellipsize(value, paint, widths[index] - 8f), x + 4f, y, paint)
            x += widths[index]
        }
        val amount = row.amountCentavos.toReportAmount()
        drawText(ellipsize(amount, typePaint, widths.last() - 8f), x + 3f, y, typePaint)
        y += 12f
        descriptionLines.forEach { line ->
            drawText(ellipsize(line, bodyPaint, contentWidth - 14f), margin + 8f, y, bodyPaint)
            y += 11f
        }
        y += 9f
        canvas?.drawLine(margin, y - 5f, pageWidth - margin, y - 5f, rulePaint)
        y += 3f
    }

    private fun drawMetricCard(
        x: Float,
        width: Float,
        label: String,
        value: String,
        textPaint: Paint,
        fillPaint: Paint
    ) {
        val top = y - 10f
        canvas?.drawRoundRect(RectF(x, top, x + width, top + 51f), 7f, 7f, fillPaint)
        drawText(label, x + 9f, y + 4f, smallPaint)
        drawText(ellipsize(value, textPaint, width - 18f), x + 9f, y + 26f, textPaint)
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
        canvas?.drawColor(Color.WHITE)
        if (isContinuation) {
            canvas?.drawRect(0f, 0f, pageWidth.toFloat(), 61f, primaryFillPaint)
            canvas?.drawRect(0f, 0f, pageWidth.toFloat(), 5f, secondaryFillPaint)
            drawText("NEXO FINANZAS · INFORME FINANCIERO", margin, 27f, brandPaint)
            drawText(
                "Período: ${formatReportDate(report.filters.startOn)} – ${formatReportDate(report.filters.endOn)}",
                margin,
                47f,
                headerMetaPaint
            )
            y = 82f
        } else {
            y = 44f
        }
    }

    private fun finishPage() {
        val currentPage = page ?: return
        currentPage.canvas.drawLine(margin, pageHeight - 37f, pageWidth - margin, pageHeight - 37f, rulePaint)
        val footer = "Nexo Finanzas  ·  Página $pageNumber"
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

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textSize = size
        }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
}

private fun FinanceReport.selectedCategoryLabel(): String =
    categoryOptions.firstOrNull { it.value == filters.categoryId }?.label ?: "Todas las categorías"

private fun FinanceReport.selectedMemberLabel(): String =
    memberOptions.firstOrNull { it.value == filters.memberId }?.label ?: "Todo el espacio"

private fun androidx.compose.ui.graphics.Color.toExcelRgb(): String =
    "%06X".format(Locale.ROOT, toArgb() and 0x00FFFFFF)

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
