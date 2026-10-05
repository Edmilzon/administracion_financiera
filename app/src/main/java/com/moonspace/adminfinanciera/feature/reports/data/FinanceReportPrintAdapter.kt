package com.moonspace.adminfinanciera.feature.reports.data

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileInputStream

class FinanceReportPrintAdapter(private val pdfFile: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(pdfFile.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }
        try {
            FileInputStream(pdfFile).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use(input::copyTo)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message)
        }
    }
}
