package com.moonspace.adminfinanciera.core.sync

import android.content.Context
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Schedules one local grouped reminder at 09:00 in the device's current time zone. */
class RecurringReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun scheduleDaily(accountId: String) {
        if (accountId.isBlank()) return
        preferences.edit { putString(ACTIVE_ACCOUNT_ID, accountId) }
        schedule(accountId, nextReminderTime(forceTomorrow = false))
    }

    internal fun scheduleNextDay(accountId: String) = schedule(accountId, nextReminderTime(forceTomorrow = true))

    internal fun isAccountActive(accountId: String): Boolean =
        preferences.getString(ACTIVE_ACCOUNT_ID, null) == accountId

    fun cancelAccount(accountId: String) {
        if (accountId.isBlank()) return
        val accountTag = accountTag(accountId)
        workManager.cancelAllWorkByTag(accountTag)
        if (isAccountActive(accountId)) {
            preferences.edit { remove(ACTIVE_ACCOUNT_ID) }
        }
    }

    private fun schedule(accountId: String, target: Calendar) {
        if (accountId.isBlank()) return
        val dateKey = "%04d%02d%02d".format(
            target.get(Calendar.YEAR),
            target.get(Calendar.MONTH) + 1,
            target.get(Calendar.DAY_OF_MONTH)
        )
        val workName = "recurring-reminder-${accountHash(accountId)}-$dateKey"
        val request = OneTimeWorkRequestBuilder<RecurringReminderWorker>()
            .setInitialDelay((target.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RecurringReminderWorker.INPUT_ACCOUNT_ID to accountId))
            .addTag(accountTag(accountId))
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    }

    private fun nextReminderTime(forceTomorrow: Boolean): Calendar = Calendar.getInstance().apply {
        val now = timeInMillis
        set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (forceTomorrow || timeInMillis <= now) add(Calendar.DAY_OF_MONTH, 1)
    }

    private fun accountTag(accountId: String) = "recurring-reminder-${accountHash(accountId)}"

    private fun accountHash(accountId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))
        .take(HASH_PREFIX_BYTES)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "finance_recurring_reminders"
        const val ACTIVE_ACCOUNT_ID = "active_account_id"
        const val REMINDER_HOUR = 9
        const val HASH_PREFIX_BYTES = 12
    }
}
