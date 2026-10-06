package com.moonspace.adminfinanciera.core.sync

/** Refreshes the signed-in account's authorized remote snapshot into local Room. */
interface FinanceSyncRepository {
    suspend fun syncAccount(accountId: String)
}
