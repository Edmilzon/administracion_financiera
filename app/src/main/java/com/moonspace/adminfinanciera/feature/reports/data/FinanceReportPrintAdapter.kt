package com.moonspace.adminfinanciera.feature.reports.data

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FinanceReportPrintAdapter(private val pdfFile: File) : PrintDocumentAdapter() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "finance-report-print").apply { isDaemon = true }
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        executor.execute {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return@execute
            }
            try {
                val pageCount = openRenderer().use { it.pageCount }
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return@execute
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build()
                callback.onLayoutFinished(info, oldAttributes != newAttributes)
            } catch (error: Exception) {
                callback.onLayoutFailed(error.message ?: PRINT_FAILED_MESSAGE)
            }
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        executor.execute {
            try {
                val pageCount = openRenderer().use { it.pageCount }
                val requestedPages = pages.toPageIndexes(pageCount)
                if (cancellationSignal.isCanceled) throw PrintCancelledException()

                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    when {
                        requestedPages.isEmpty() -> Unit
                        requestedPages.size == pageCount && requestedPages.first() == 0 ->
                            copyPdfWithCancellation(output, cancellationSignal)
                        else -> writeSelectedPages(requestedPages, output, cancellationSignal)
                    }
                }
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(requestedPages.toPageRanges(pageCount))
                }
            } catch (_: PrintCancelledException) {
                destination.closeQuietly()
                callback.onWriteCancelled()
            } catch (error: Exception) {
                destination.closeQuietly()
                callback.onWriteFailed(error.message ?: PRINT_FAILED_MESSAGE)
            }
        }
    }

    override fun onFinish() {
        executor.shutdown()
        super.onFinish()
    }

    private fun openRenderer(): PdfRenderer {
        require(pdfFile.isFile) { "No se encontró el informe PDF." }
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(descriptor)
        } catch (error: Exception) {
            descriptor.closeQuietly()
            throw error
        }
    }

    private fun copyPdfWithCancellation(
        output: ParcelFileDescriptor.AutoCloseOutputStream,
        cancellationSignal: CancellationSignal
    ) {
        FileInputStream(pdfFile).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                if (cancellationSignal.isCanceled) throw PrintCancelledException()
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                output.write(buffer, 0, bytesRead)
            }
        }
    }

    private fun writeSelectedPages(
        requestedPages: List<Int>,
        output: ParcelFileDescriptor.AutoCloseOutputStream,
        cancellationSignal: CancellationSignal
    ) {
        val document = PdfDocument()
        try {
            openRenderer().use { renderer ->
                requestedPages.forEachIndexed { outputIndex, sourceIndex ->
                    if (cancellationSignal.isCanceled) throw PrintCancelledException()
                    renderer.openPage(sourceIndex).use { sourcePage ->
                        val renderScale = PRINT_RENDER_SCALE
                        val bitmap = Bitmap.createBitmap(
                            sourcePage.width * renderScale,
                            sourcePage.height * renderScale,
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            val transform = Matrix().apply {
                                setScale(renderScale.toFloat(), renderScale.toFloat())
                            }
                            sourcePage.render(
                                bitmap,
                                null,
                                transform,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )
                            if (cancellationSignal.isCanceled) throw PrintCancelledException()

                            val pageInfo = PdfDocument.PageInfo.Builder(
                                sourcePage.width,
                                sourcePage.height,
                                outputIndex + 1
                            ).create()
                            val outputPage = document.startPage(pageInfo)
                            outputPage.canvas.drawBitmap(
                                bitmap,
                                null,
                                Rect(0, 0, sourcePage.width, sourcePage.height),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            document.finishPage(outputPage)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
            if (cancellationSignal.isCanceled) throw PrintCancelledException()
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun Array<out PageRange>.toPageIndexes(pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        return asSequence()
            .flatMap { range ->
                val start = range.start.coerceAtLeast(0)
                val end = range.end.coerceAtMost(pageCount - 1)
                if (start <= end) (start..end).asSequence() else emptySequence()
            }
            .distinct()
            .sorted()
            .toList()
    }

    private fun List<Int>.toPageRanges(pageCount: Int): Array<PageRange> {
        if (isEmpty()) return emptyArray()
        if (size == pageCount && first() == 0 && last() == pageCount - 1) {
            return arrayOf(PageRange.ALL_PAGES)
        }
        val ranges = mutableListOf<PageRange>()
        var rangeStart = first()
        var rangeEnd = rangeStart
        drop(1).forEach { page ->
            if (page == rangeEnd + 1) {
                rangeEnd = page
            } else {
                ranges += PageRange(rangeStart, rangeEnd)
                rangeStart = page
                rangeEnd = page
            }
        }
        ranges += PageRange(rangeStart, rangeEnd)
        return ranges.toTypedArray()
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }

    private class PrintCancelledException : Exception()

    private companion object {
        const val COPY_BUFFER_SIZE = 16 * 1024
        const val PRINT_RENDER_SCALE = 2
        const val PRINT_FAILED_MESSAGE = "No se pudo preparar el informe para imprimir."
    }
}
