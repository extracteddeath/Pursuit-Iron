package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Separate crash-recoverable POST-FINISH custom correction transaction:
 * history intent -> exact saved-correction CAS file -> history finalization.
 *
 * The issued workout, original factual journal, and Finish receipt NEVER
 * change. A different pending Composer/Finish intent is not ours to recover.
 * No PR, progression, or live Compose history-edit path is authorized here.
 */
enum class NativeCustomCorrectionTransactionRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_ENROLLED, FOREIGN_PENDING_INTENT,
    HISTORY_NOT_READY, NOT_CUSTOM_COMPLETION, HISTORY_RECEIPT_MISMATCH,
    CORRECTION_REJECTED, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomCorrectionTransactionResult {
    data class Committed(
        val manifestRevision: Long,
        val correctionRevision: Long,
        val evidence: CustomSavedCorrectedHistory,
        val replayed: Boolean
    ) : NativeCustomCorrectionTransactionResult
    data class Rejected(val reason: NativeCustomCorrectionTransactionRefusal) :
        NativeCustomCorrectionTransactionResult
}

class NativeKotlinCustomHistoryCorrectionTransaction(
    private val history: NativeKotlinHistoryManifestFileStore,
    private val custom: NativeKotlinCustomWorkoutJournalFileStore,
    private val saved: NativeKotlinCustomSavedCorrectionFileStore,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun rejected(reason: NativeCustomCorrectionTransactionRefusal) =
        NativeCustomCorrectionTransactionResult.Rejected(reason)

    private fun authorized(owner: String, athlete: String): Boolean =
        runCatching { identity.signedInIdentity() }.getOrNull()?.let {
            it.ownerKey == owner && it.athleteId == athlete
        } == true

    private fun ledger(owner: String, athlete: String): NativeHistoryLedger? =
        (history.open(owner, athlete) as? NativeHistoryLedgerResult.Durable)
            ?.ledger?.takeIf {
                it.ownerKey == owner && it.athleteId == athlete
            }

    private fun verified(
        owner: String, athlete: String, entry: KotlinHistoryManifestEntry,
        expectedRevision: Long
    ): CustomSavedHistoryCorrectionState? {
        val issued = (custom.open(owner, entry.workoutId)
            as? NativeCustomJournalResult.Durable)?.issued?.journal ?: return null
        if (issued.snapshot.stamp.athleteId != athlete ||
            issued.revision != entry.originalJournalRevision ||
            issued.finishedByEventId != entry.finishEventId ||
            NativeCustomFinishReceipt.compute(owner, athlete, issued) !=
                entry.storedReceiptId) return null
        val opened = saved.open(owner, entry.workoutId)
            as? NativeCustomSavedCorrectionResult.Opened ?: return null
        return opened.state.takeIf {
            it.originalFinishedJournal == issued &&
            it.correctionRevision == expectedRevision &&
            (expectedRevision == 0L || opened.hasCorrectionFile) &&
            (expectedRevision != 0L || !opened.hasCorrectionFile) &&
            (CustomSavedHistoryEvidenceDomain.projectSaved(it)
                is CustomSavedHistoryProjectionResult.Candidate)
        }
    }

    private fun result(
        ledger: NativeHistoryLedger,
        state: CustomSavedHistoryCorrectionState,
        replayed: Boolean
    ): NativeCustomCorrectionTransactionResult {
        val evidence = CustomSavedHistoryEvidenceDomain.projectSaved(state)
            as? CustomSavedHistoryProjectionResult.Candidate
            ?: return rejected(
                NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        return NativeCustomCorrectionTransactionResult.Committed(
            ledger.revision, state.correctionRevision, evidence.corrected, replayed)
    }

    private fun replayPending(
        owner: String, athlete: String, current: NativeHistoryLedger
    ): NativeCustomCorrectionTransactionResult {
        val intent = current.pending as? NativeHistoryWriteIntent.CustomCorrect
            ?: return rejected(
                NativeCustomCorrectionTransactionRefusal.FOREIGN_PENDING_INTENT)
        if (!authorized(owner, athlete))
            return rejected(NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED)
        val entry = current.completions.singleOrNull {
            it.workoutId == intent.workoutId &&
            it.storedReceiptId.startsWith("custom-v2-") &&
            it.savedCorrectionRevision ==
                intent.operation.expectedCorrectionRevision
        } ?: return rejected(
            NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        val prior = verified(owner, athlete, entry, entry.savedCorrectionRevision)
        // A power failure may occur AFTER the correction file was atomically
        // written. In that case verified(oldRevision) is intentionally false;
        // appendPending independently replays its exact committed receipt.
        if (prior == null) {
            val reopened = saved.open(owner, intent.workoutId)
                as? NativeCustomSavedCorrectionResult.Opened
                ?: return rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
            if (reopened.state.correctionRevision !=
                entry.savedCorrectionRevision + 1 ||
                reopened.state.receipts.lastOrNull()?.correction != intent.operation)
                return rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        }
        val applied = saved.appendPending(
            owner, intent.workoutId, intent.operation)
        val state = (applied as? NativeCustomSavedCorrectionResult.Durable)?.state
            ?: return rejected(
                NativeCustomCorrectionTransactionRefusal.CORRECTION_REJECTED)
        if (state.correctionRevision != entry.savedCorrectionRevision + 1 ||
            state.receipts.lastOrNull()?.correction != intent.operation ||
            CustomSavedHistoryEvidenceDomain.projectSaved(state)
                !is CustomSavedHistoryProjectionResult.Candidate)
            return rejected(
                NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        val finalEntry = entry.copy(savedCorrectionRevision =
            state.correctionRevision)
        val updated = history.finalize(owner, athlete, intent, finalEntry)
            as? NativeHistoryLedgerResult.Durable
            ?: return rejected(
                NativeCustomCorrectionTransactionRefusal.HISTORY_NOT_READY)
        if (!authorized(owner, athlete))
            return rejected(NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED)
        return result(updated.ledger, state, false)
    }

    fun correct(
        owner: String, athlete: String, workoutId: String,
        operation: CustomSavedVisitCorrection
    ): NativeCustomCorrectionTransactionResult =
        fenced(owner, athlete, workoutId) {
            val current = ledger(owner, athlete)
                ?: return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.NOT_ENROLLED)
            if (current.pending != null) {
                val recovered = replayPending(owner, athlete, current)
                if (recovered !is NativeCustomCorrectionTransactionResult.Committed)
                    return@fenced recovered
            }
            val clean = ledger(owner, athlete)
                ?: return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_NOT_READY)
            if (clean.pending != null)
                return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_NOT_READY)
            val entry = clean.completions.singleOrNull {
                it.workoutId == workoutId &&
                it.storedReceiptId.startsWith("custom-v2-")
            } ?: return@fenced rejected(
                NativeCustomCorrectionTransactionRefusal.NOT_CUSTOM_COMPLETION)
            val state = verified(owner, athlete, entry,
                entry.savedCorrectionRevision)
                ?: return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_RECEIPT_MISMATCH)

            val old = state.receipts.firstOrNull {
                it.correction.eventId == operation.eventId
            }
            if (old != null)
                return@fenced if (old.correction == operation)
                    result(clean, state, true)
                else rejected(
                    NativeCustomCorrectionTransactionRefusal.CORRECTION_REJECTED)

            val decision = CustomSavedHistoryCorrectionDomain.reduce(
                state, operation)
            if (decision !is CustomSavedCorrectionDecision.Proposed ||
                decision.replayed)
                return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.CORRECTION_REJECTED)
            val intent = NativeHistoryWriteIntent.CustomCorrect(
                workoutId, operation)
            val prepared = history.prepare(owner, athlete,
                clean.revision, intent)
            if (prepared !is NativeHistoryLedgerResult.Durable)
                return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.HISTORY_NOT_READY)
            replayPending(owner, athlete, prepared.ledger)
        }

    /** Replays ONLY a prepared CustomCorrect; never consumes foreign intent. */
    fun recover(
        owner: String, athlete: String
    ): NativeCustomCorrectionTransactionResult =
        fenced(owner, athlete, null) {
            val state = ledger(owner, athlete)
                ?: return@fenced rejected(
                    NativeCustomCorrectionTransactionRefusal.NOT_ENROLLED)
            if (state.pending == null)
                rejected(NativeCustomCorrectionTransactionRefusal.HISTORY_NOT_READY)
            else replayPending(owner, athlete, state)
        }

    private fun fenced(
        owner: String, athlete: String, workoutId: String?,
        work: () -> NativeCustomCorrectionTransactionResult
    ): NativeCustomCorrectionTransactionResult {
        if (!id.matches(owner) || !id.matches(athlete) ||
            (workoutId != null && !id.matches(workoutId)))
            return rejected(NativeCustomCorrectionTransactionRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    rejected(NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED)
                else work()
            }
        }.getOrElse {
            rejected(NativeCustomCorrectionTransactionRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
