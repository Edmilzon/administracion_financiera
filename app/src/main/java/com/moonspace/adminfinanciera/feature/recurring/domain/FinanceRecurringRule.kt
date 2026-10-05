package com.moonspace.adminfinanciera.feature.recurring.domain

import com.moonspace.adminfinanciera.feature.transactions.domain.TransactionKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class RecurrenceFrequency(val apiValue: String) {
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly");

    companion object {
        fun fromApiValue(value: String): RecurrenceFrequency? = entries.firstOrNull { it.apiValue == value }
    }
}

data class FinanceRecurringRule(
    val id: String,
    val householdId: String,
    val createdBy: String,
    val categoryId: String,
    val categoryName: String,
    val kind: TransactionKind,
    val amountCentavos: Long,
    val currency: String,
    val description: String?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val startOn: String,
    val nextDueOn: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun isDue(today: String = todayIsoDate()): Boolean = isActive && nextDueOn <= today
}

data class RecurringRuleDraft(
    val id: String? = null,
    val kind: TransactionKind,
    val amountCentavos: Long,
    val categoryId: String,
    val description: String?,
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val startOn: String
)

enum class RecurringRuleError {
    InvalidAmount,
    InvalidDate,
    InvalidInterval,
    CategoryUnavailable,
    RuleUnavailable,
    OccurrenceNotDue,
    PermissionDenied,
    HouseholdUnavailable
}

class RecurringRuleException(val error: RecurringRuleError) : Exception()

/** Advances from the scheduled date while retaining the original day for month-end rules. */
fun nextRecurringDate(
    currentDueOn: String,
    startOn: String,
    frequency: RecurrenceFrequency,
    intervalCount: Int
): String? = runCatching {
    require(intervalCount in 1..3650)
    val current = parseIsoDate(currentDueOn) ?: return null
    val anchor = parseIsoDate(startOn) ?: return null
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply { time = current }
    when (frequency) {
        RecurrenceFrequency.Daily -> calendar.add(Calendar.DAY_OF_MONTH, intervalCount)
        RecurrenceFrequency.Weekly -> calendar.add(Calendar.DAY_OF_MONTH, intervalCount * 7)
        RecurrenceFrequency.Monthly -> {
            val desiredDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT)
                .apply { time = anchor }
                .get(Calendar.DAY_OF_MONTH)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MONTH, intervalCount)
            val day = minOf(desiredDay, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            calendar.set(Calendar.DAY_OF_MONTH, day)
        }
    }
    isoDateFormat().format(calendar.time)
}.getOrNull()

fun todayIsoDate(): String = SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT).format(Date())

fun parseIsoDate(value: String): Date? = runCatching {
    val format = isoDateFormat()
    format.parse(value)?.takeIf { format.format(it) == value }
}.getOrNull()

private fun isoDateFormat() = SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT).apply {
    isLenient = false
    timeZone = TimeZone.getTimeZone("UTC")
}

private const val ISO_DATE_PATTERN = "yyyy-MM-dd"
