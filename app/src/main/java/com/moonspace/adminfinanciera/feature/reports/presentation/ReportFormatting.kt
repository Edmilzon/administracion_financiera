package com.moonspace.adminfinanciera.feature.reports.presentation

import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun formatReportMoney(centavos: BigInteger): String = formatReportMoney(BigDecimal(centavos, 2))

internal fun formatReportMoney(centavos: Long): String = formatReportMoney(BigDecimal.valueOf(centavos, 2))

private fun formatReportMoney(amount: BigDecimal): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-BO")).apply {
        currency = Currency.getInstance("BOB")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "${formatter.format(amount)} BOB"
}

internal fun formatReportDate(value: String): String = runCatching {
    val parser = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.forLanguageTag("es-BO")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(parser.parse(value) ?: return value)
}.getOrDefault(value)

internal fun parseReportDateMillis(value: String): Long? = runCatching {
    val parser = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    parser.parse(value)?.time
}.getOrNull()

internal fun reportDateFromMillis(value: Long): String = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(value))

private const val DATE_PATTERN = "yyyy-MM-dd"
private const val DISPLAY_DATE_PATTERN = "dd/MM/yyyy"
