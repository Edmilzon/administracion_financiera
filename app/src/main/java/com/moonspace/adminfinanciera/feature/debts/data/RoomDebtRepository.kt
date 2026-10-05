package com.moonspace.adminfinanciera.feature.debts.data

import androidx.room.withTransaction
import com.moonspace.adminfinanciera.core.database.entities.DebtEntity
import com.moonspace.adminfinanciera.core.database.entities.DebtPaymentEntity
import com.moonspace.adminfinanciera.core.database.EncryptedFinanceDatabaseProvider
import com.moonspace.adminfinanciera.core.database.entities.SyncOutboxEntity
import com.moonspace.adminfinanciera.core.sync.FinanceSyncScheduler
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDataError
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDataException
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDirection
import com.moonspace.adminfinanciera.feature.debts.domain.DebtDraft
import com.moonspace.adminfinanciera.feature.debts.domain.DebtPayment
import com.moonspace.adminfinanciera.feature.debts.domain.DebtPaymentDraft
import com.moonspace.adminfinanciera.feature.debts.domain.DebtRepository
import com.moonspace.adminfinanciera.feature.debts.domain.FinanceDebt
import com.moonspace.adminfinanciera.feature.debts.domain.MAX_DEBT_AMOUNT_CENTAVOS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class RoomDebtRepository(
    private val databases: EncryptedFinanceDatabaseProvider,
    private val syncScheduler: FinanceSyncScheduler
) : DebtRepository {
    override fun observeDebts(
        accountId: String,
        householdId: String,
        userId: String,
        canReadWholeHousehold: Boolean
    ): Flow<List<FinanceDebt>> {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        val debts = if (canReadWholeHousehold) {
            database.debtDao().observeAllForHousehold(householdId)
        } else {
            database.debtDao().observeOwn(householdId, userId)
        }
        val payments = if (canReadWholeHousehold) {
            database.debtPaymentDao().observeAllForHousehold(householdId)
        } else {
            database.debtPaymentDao().observeOwn(householdId, userId)
        }
        return combine(debts, payments) { debtRows, paymentRows ->
            val paymentsByDebt = paymentRows.groupBy(DebtPaymentEntity::debtId)
            debtRows.map { debt ->
                debt.toDomain(paymentsByDebt[debt.id].orEmpty())
            }
        }
    }

    override suspend fun saveDebt(
        accountId: String,
        userId: String,
        householdId: String,
        draft: DebtDraft
    ) {
        requireAccountOwner(accountId, userId)
        if (householdId.isBlank()) throw DebtDataException(DebtDataError.HouseholdUnavailable)
        if (draft.principalCentavos !in 1..MAX_DEBT_AMOUNT_CENTAVOS) {
            throw DebtDataException(DebtDataError.InvalidAmount)
        }
        val counterparty = draft.counterparty.trim()
        if (counterparty.isBlank() || counterparty.length > MAX_COUNTERPARTY_LENGTH) {
            throw DebtDataException(DebtDataError.InvalidCounterparty)
        }
        val description = draft.description?.trim()?.takeIf(String::isNotEmpty)
        if (description != null && description.length > MAX_DESCRIPTION_LENGTH) {
            throw DebtDataException(DebtDataError.InvalidDescription)
        }
        if (!isValidDate(draft.openedOn) || (draft.dueOn != null &&
                (!isValidDate(draft.dueOn) || draft.dueOn < draft.openedOn))
        ) {
            throw DebtDataException(DebtDataError.InvalidDate)
        }

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            requireHousehold(database, accountId, householdId)
            val debtDao = database.debtDao()
            val previous = draft.id?.let { debtDao.findById(householdId, it) }
            if (draft.id != null && (previous == null || previous.createdBy != userId)) {
                throw DebtDataException(DebtDataError.DebtUnavailable)
            }
            val existingPayments = previous?.let {
                database.debtPaymentDao().forDebt(householdId, it.id)
            }.orEmpty()
            val paid = existingPayments.fold(BigInteger.ZERO) { total, payment ->
                total + BigInteger.valueOf(payment.amountCentavos)
            }
            if (paid > BigInteger.valueOf(draft.principalCentavos)) {
                throw DebtDataException(DebtDataError.PrincipalBelowPaidAmount)
            }
            val now = System.currentTimeMillis()
            val id = previous?.id ?: UUID.randomUUID().toString()
            debtDao.save(
                DebtEntity(
                    id = id,
                    accountId = accountId,
                    householdId = householdId,
                    createdBy = userId,
                    direction = draft.direction.apiValue,
                    counterparty = counterparty,
                    description = description,
                    principalCentavos = draft.principalCentavos,
                    currency = CURRENCY_BOB,
                    openedOn = draft.openedOn,
                    dueOn = draft.dueOn,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    isRemoteBacked = previous?.isRemoteBacked ?: false
                )
            )
            enqueue(database, accountId, ENTITY_DEBT, id)
        }
        syncScheduler.scheduleNow(accountId)
    }

    override suspend fun deleteOwnDebt(
        accountId: String,
        userId: String,
        householdId: String,
        debtId: String
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val debt = database.debtDao().findById(householdId, debtId)
            if (debt == null || debt.createdBy != userId) {
                throw DebtDataException(DebtDataError.DebtUnavailable)
            }
            val payments = database.debtPaymentDao().forDebt(householdId, debtId)
            payments.forEach { payment ->
                database.syncOutboxDao().removeForEntity(accountId, ENTITY_DEBT_PAYMENT, payment.id)
            }
            database.debtDao().deleteById(householdId, debtId)
            database.syncOutboxDao().removeForEntity(accountId, ENTITY_DEBT, debtId)
            enqueue(database, accountId, ENTITY_DEBT, debtId, OPERATION_DELETE)
        }
        syncScheduler.scheduleNow(accountId)
    }

    override suspend fun savePayment(
        accountId: String,
        userId: String,
        householdId: String,
        draft: DebtPaymentDraft
    ) {
        requireAccountOwner(accountId, userId)
        if (draft.amountCentavos !in 1..MAX_DEBT_AMOUNT_CENTAVOS) {
            throw DebtDataException(DebtDataError.InvalidAmount)
        }
        if (!isValidDate(draft.paidOn)) throw DebtDataException(DebtDataError.InvalidDate)
        val note = draft.note?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && note.length > MAX_PAYMENT_NOTE_LENGTH) {
            throw DebtDataException(DebtDataError.InvalidDescription)
        }

        val database = databases.databaseFor(accountId)
        database.withTransaction {
            requireHousehold(database, accountId, householdId)
            val debt = database.debtDao().findById(householdId, draft.debtId)
            if (debt == null || debt.createdBy != userId) {
                throw DebtDataException(DebtDataError.DebtUnavailable)
            }
            val paymentDao = database.debtPaymentDao()
            val previous = draft.id?.let { paymentDao.findById(householdId, it) }
            if (draft.id != null &&
                (previous == null || previous.debtId != debt.id || previous.createdBy != userId)
            ) {
                throw DebtDataException(DebtDataError.PaymentUnavailable)
            }
            val alreadyPaid = paymentDao.forDebt(householdId, debt.id)
                .asSequence()
                .filterNot { it.id == previous?.id }
                .fold(BigInteger.ZERO) { total, payment ->
                    total + BigInteger.valueOf(payment.amountCentavos)
                }
            val remaining = BigInteger.valueOf(debt.principalCentavos) - alreadyPaid
            if (BigInteger.valueOf(draft.amountCentavos) > remaining) {
                throw DebtDataException(DebtDataError.PaymentExceedsBalance)
            }
            val now = System.currentTimeMillis()
            val id = previous?.id ?: UUID.randomUUID().toString()
            paymentDao.save(
                DebtPaymentEntity(
                    id = id,
                    accountId = accountId,
                    householdId = householdId,
                    debtId = debt.id,
                    createdBy = userId,
                    amountCentavos = draft.amountCentavos,
                    paidOn = draft.paidOn,
                    note = note,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now,
                    isRemoteBacked = previous?.isRemoteBacked ?: false
                )
            )
            enqueue(database, accountId, ENTITY_DEBT_PAYMENT, id)
        }
        syncScheduler.scheduleNow(accountId)
    }

    override suspend fun deleteOwnPayment(
        accountId: String,
        userId: String,
        householdId: String,
        paymentId: String
    ) {
        requireAccountOwner(accountId, userId)
        val database = databases.databaseFor(accountId)
        database.withTransaction {
            val payment = database.debtPaymentDao().findById(householdId, paymentId)
            val debt = payment?.let { database.debtDao().findById(householdId, it.debtId) }
            if (payment == null || debt?.createdBy != userId || payment.createdBy != userId) {
                throw DebtDataException(DebtDataError.PaymentUnavailable)
            }
            database.debtPaymentDao().deleteById(householdId, paymentId)
            enqueue(database, accountId, ENTITY_DEBT_PAYMENT, paymentId, OPERATION_DELETE)
        }
        syncScheduler.scheduleNow(accountId)
    }

    private suspend fun requireHousehold(
        database: com.moonspace.adminfinanciera.core.database.FinanceDatabase,
        accountId: String,
        householdId: String
    ) {
        if (database.householdCacheDao().getHousehold(accountId)?.householdId != householdId) {
            throw DebtDataException(DebtDataError.HouseholdUnavailable)
        }
    }

    private suspend fun enqueue(
        database: com.moonspace.adminfinanciera.core.database.FinanceDatabase,
        accountId: String,
        entityType: String,
        entityId: String,
        operation: String = OPERATION_UPSERT
    ) {
        val outbox = database.syncOutboxDao()
        outbox.removeForEntity(accountId, entityType, entityId)
        outbox.enqueue(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                accountId = accountId,
                entityType = entityType,
                entityId = entityId,
                operation = operation,
                enqueuedAt = System.currentTimeMillis()
            )
        )
    }

    private fun requireAccountOwner(accountId: String, userId: String) {
        if (accountId.isBlank() || accountId != userId) {
            throw DebtDataException(DebtDataError.PermissionDenied)
        }
    }

    private fun isValidDate(value: String): Boolean = try {
        val format = SimpleDateFormat(DATE_PATTERN, Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        format.parse(value)?.let(format::format) == value
    } catch (_: Exception) {
        false
    }

    private fun DebtEntity.toDomain(paymentRows: List<DebtPaymentEntity>): FinanceDebt = FinanceDebt(
        id = id,
        householdId = householdId,
        createdBy = createdBy,
        direction = DebtDirection.fromApiValue(direction) ?: DebtDirection.OwedByMe,
        counterparty = counterparty,
        description = description,
        principalCentavos = principalCentavos,
        currency = currency,
        openedOn = openedOn,
        dueOn = dueOn,
        createdAt = createdAt,
        updatedAt = updatedAt,
        payments = paymentRows.filter { it.debtId == id }
            .map { it.toDomain() }
            .sortedByDescending(DebtPayment::paidOn)
    )

    private fun DebtPaymentEntity.toDomain() = DebtPayment(
        id = id,
        debtId = debtId,
        amountCentavos = amountCentavos,
        paidOn = paidOn,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private companion object {
        const val CURRENCY_BOB = "BOB"
        const val ENTITY_DEBT = "debt"
        const val ENTITY_DEBT_PAYMENT = "debt_payment"
        const val OPERATION_UPSERT = "upsert"
        const val OPERATION_DELETE = "delete"
        const val MAX_COUNTERPARTY_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_PAYMENT_NOTE_LENGTH = 250
        const val DATE_PATTERN = "yyyy-MM-dd"
    }
}
