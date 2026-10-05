package com.moonspace.adminfinanciera.feature.recurring.domain

import kotlinx.coroutines.flow.Flow

interface RecurringRuleRepository {
    fun observeRules(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceRecurringRule>>

    suspend fun saveRule(accountId: String, userId: String, householdId: String, draft: RecurringRuleDraft)
    suspend fun setRuleActive(accountId: String, userId: String, householdId: String, ruleId: String, active: Boolean)
    suspend fun confirmOccurrence(accountId: String, userId: String, householdId: String, ruleId: String)
}
