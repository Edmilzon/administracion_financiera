package com.moonspace.adminfinanciera.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Schedules account-scoped synchronization without placing credentials in WorkManager data. */
class FinanceSyncScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun scheduleForSignedInAccount(accountId: String) {
        require(accountId.isNotBlank())
        workManager.enqueueUniquePeriodicWork(
            periodicWorkName(accountId),
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FinanceSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(connectedConstraint)
                .setInputData(workDataOf(FinanceSyncWorker.INPUT_ACCOUNT_ID to accountId))
                .addTag(FinanceSyncWorker.TAG)
                .build()
        )
        scheduleNow(accountId)
    }

    fun scheduleNow(accountId: String) {
        if (accountId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<FinanceSyncWorker>()
            .setConstraints(connectedConstraint)
            .setInputData(workDataOf(FinanceSyncWorker.INPUT_ACCOUNT_ID to accountId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(FinanceSyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            oneTimeWorkName(accountId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAccount(accountId: String) {
        if (accountId.isBlank()) return
        workManager.cancelUniqueWork(oneTimeWorkName(accountId))
        workManager.cancelUniqueWork(periodicWorkName(accountId))
    }

    private fun oneTimeWorkName(accountId: String) = "finance-sync-once-${accountHash(accountId)}"
    private fun periodicWorkName(accountId: String) = "finance-sync-periodic-${accountHash(accountId)}"

    private fun accountHash(accountId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(accountId.toByteArray(Charsets.UTF_8))
        .take(HASH_PREFIX_BYTES)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val SYNC_INTERVAL_HOURS = 12L
        const val RETRY_BACKOFF_MILLIS = 30_000L
        const val HASH_PREFIX_BYTES = 12

        val connectedConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
