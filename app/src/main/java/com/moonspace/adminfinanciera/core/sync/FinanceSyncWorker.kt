package com.moonspace.adminfinanciera.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moonspace.adminfinanciera.app.di.FinanceAppContainer
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Runs a full account snapshot and drains its Room outbox while the network is available. */
class FinanceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(INPUT_ACCOUNT_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val container = FinanceAppContainer(applicationContext)
        return try {
            if (!container.neonApiConfig.isDataApiConfigured) {
                Result.success()
            } else {
                container.financeSyncRepository.syncAccount(accountId)
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            container.closeFinancialDatabases()
        }
    }

    companion object {
        const val INPUT_ACCOUNT_ID = "account_id"
        const val TAG = "finance-sync"
    }
}
