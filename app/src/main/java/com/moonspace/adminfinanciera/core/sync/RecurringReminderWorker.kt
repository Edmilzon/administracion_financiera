package com.moonspace.adminfinanciera.core.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moonspace.adminfinanciera.R
import com.moonspace.adminfinanciera.app.MainActivity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.feature.recurring.domain.todayIsoDate
import kotlinx.coroutines.CancellationException

/** Posts one generic notification for all overdue rules; it never creates a transaction. */
class RecurringReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(INPUT_ACCOUNT_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val scheduler = RecurringReminderScheduler(applicationContext)
        if (!scheduler.isAccountActive(accountId)) return Result.success()
        val databases = EncryptedFinanceDatabaseProvider(applicationContext)
        var shouldScheduleNextDay = false
        return try {
            val database = databases.databaseFor(accountId)
            val household = database.householdCacheDao().getHousehold(accountId)
                ?.householdId
                ?.takeIf(String::isNotBlank)
            if (household != null) {
                val dueCount = database.recurringRuleDao()
                    .dueForAccount(accountId, household, todayIsoDate())
                    .size
                if (dueCount > 0 && scheduler.isAccountActive(accountId)) notifyDueRules(dueCount)
            }
            shouldScheduleNextDay = true
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            databases.closeAll()
            if (shouldScheduleNextDay && scheduler.isAccountActive(accountId)) {
                scheduler.scheduleNextDay(accountId)
            }
        }
    }

    private fun notifyDueRules(dueCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.recurring_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val openApp = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_RECURRING, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_transactions)
            .setContentTitle(applicationContext.getString(R.string.recurring_notification_title))
            .setContentText(applicationContext.resources.getQuantityString(
                R.plurals.recurring_notification_count,
                dueCount,
                dueCount
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the permission check and posting.
        }
    }

    companion object {
        const val INPUT_ACCOUNT_ID = "account_id"
        private const val CHANNEL_ID = "finance-recurring-reminders"
        private const val NOTIFICATION_ID = 901
    }
}
