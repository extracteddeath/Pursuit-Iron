package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

enum class NativeHistoryTransactionRefusal {
    INVALID_REQUEST, NOT_ENROLLED_OR_AUTHORIZED, HISTORY_NOT_READY,
    WORKOUT_NOT_READY, WORKOUT_ALREADY_UNTRACKED,
    HISTORY_RECEIPT_MISMATCH, OPERATION_REJECTED,
    LEDGER_REJECTED, STORAGE_UNAVAILABLE
}

sealed interface NativeHistoryTransactionResult {
    data class Durable(
        val manifest: NativeKotlinHistoryManifest,
        val replayed: Boolean
    ) : NativeHistoryTransactionResult

    data class Rejected(
        val reason: NativeHistoryTransactionRefusal,
        val ledgerReason: NativeHistoryLedgerRefusal? = null
    ) : NativeHistoryTransactionResult
}

/**
 * Prototype crash-safe redo coordinator for the isolated Kotlin replacement.
 * No UI or legacy JS imports. Every operation holds the SAME owner fence as
 * the workout issuer and the authenticated source/revision writers.
 *
 * prepare manifest intent (atomic) -> commit individual journal receipt
 * (atomic) -> finalize manifest revision (atomic). After a process death,
 * recover() replays the exact pending event ID and finalizes, or fails closed.
 * The prepared intent remains on disk if a source is corrupt or inaccessible.
 *
 * This only covers operations performed THROUGH this coordinator. Existing
 * unfenced JS/native writes and old pre-enrollment workout history MUST NOT
 * be silently imported or declared complete. The global index remains
 * review-only until all Finish and edit entrypoints use this protocol.
 */
class NativeKotlinHistoryTransactionCoordinator(
    private val ledgerStore: NativeKotlinHistoryManifestFileStore,
    private val journalStore: KotlinNativeJournalFileStore,
    private val correctionStore: KotlinNativeSavedCorrectionFileStore,
    private val workoutAuthority: NativeKotlinWorkoutAuthority,
    private val ownerFence: NativeKotlinApprovalRevisionFence
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun eligible(owner: String, athlete: String, source: KotlinTrainingSourceRevision) =
        source.athleteId == athlete && runCatching {
            workoutAuthority.canAccessIssuedWorkout(owner, source)
        }.getOrDefault(false)

    private fun denied(reason: NativeHistoryTransactionRefusal,
        storage: NativeHistoryLedgerRefusal? = null) =
        NativeHistoryTransactionResult.Rejected(reason, storage)

    private fun loaded(owner: String, athlete: String): NativeHistoryLedger? =
        (ledgerStore.open(owner, athlete) as? NativeHistoryLedgerResult.Durable)
            ?.ledger

    private fun manifest(ledger: NativeHistoryLedger) =
        NativeKotlinHistoryManifest(
            ledger.ownerKey, ledger.athleteId, ledger.revision,
            ledger.completions.toList()
        )

    /** Explicit owner-approved enrollment; not a migration from old JS files. */
    fun enroll(
        owner: String, athlete: String,
        authorizeCurrentOwner: (String, String) -> Boolean
    ): NativeHistoryTransactionResult = fenced(owner, athlete) {
        val result = ledgerStore.enroll(owner, athlete, authorizeCurrentOwner)
        when (result) {
            is NativeHistoryLedgerResult.Durable ->
                if (result.ledger.pending != null)
                    denied(NativeHistoryTransactionRefusal.HISTORY_NOT_READY)
                else NativeHistoryTransactionResult.Durable(
                    manifest(result.ledger), result.replayed)
            is NativeHistoryLedgerResult.Rejected ->
                denied(NativeHistoryTransactionRefusal.NOT_ENROLLED_OR_AUTHORIZED,
                    result.reason)
        }
    }

    fun complete(
        owner: String, athlete: String, workoutId: String,
        operation: KotlinJournalOperation.Finish
    ): NativeHistoryTransactionResult = fenced(owner, athlete) {
        if (!id.matches(workoutId))
            return@fenced denied(NativeHistoryTransactionRefusal.INVALID_REQUEST)
        val base = recoverPending(owner, athlete)
        if (base !is NativeHistoryLedgerResult.Durable)
            return@fenced fromLedger(base)
        val existing = base.ledger.completions.singleOrNull {
            it.workoutId == workoutId
        }
        if (existing != null) {
            val journal = journalStore.openStored(owner, workoutId) { o, source ->
                eligible(o, athlete, source)
            }
            val verified = (journal as? NativeJournalFileResult.Durable)?.journal
            return@fenced if (verified != null &&
                verified.finishedByEventId == operation.eventId &&
                existing.originalJournalRevision == verified.revision &&
                operation.expectedJournalRevision + 1 == verified.revision)
                NativeHistoryTransactionResult.Durable(manifest(base.ledger), true)
            else denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        }
        val current = journalStore.openStored(owner, workoutId) { o, source ->
            eligible(o, athlete, source)
        }
        val journal = (current as? NativeJournalFileResult.Durable)?.journal
            ?: return@fenced denied(NativeHistoryTransactionRefusal.WORKOUT_NOT_READY)
        if (journal.finishedByEventId != null)
            return@fenced denied(NativeHistoryTransactionRefusal.WORKOUT_ALREADY_UNTRACKED)
        if (journal.revision != operation.expectedJournalRevision ||
            !journal.canProposeFinish)
            return@fenced denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)
        val edits = correctionStore.openStored(owner, workoutId) { o, source ->
            eligible(o, athlete, source)
        }
        if (edits !is NativeSavedCorrectionFileResult.Rejected ||
            edits.reason != NativeSavedCorrectionRefusal.NOT_ISSUED_OR_FINISHED) {
            // No valid post-Finish corrections can exist before Finish.
            // The correction store's unfinished refusal means only the
            // original journal is present, as required for this operation.
            return@fenced denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
        }
        val intent = NativeHistoryWriteIntent.Finish(workoutId, operation)
        val prepared = ledgerStore.prepare(
            owner, athlete, base.ledger.revision, intent)
        if (prepared !is NativeHistoryLedgerResult.Durable)
            return@fenced fromLedger(prepared)
        finishIntent(owner, athlete, prepared.ledger)
    }

    fun correct(
        owner: String, athlete: String, workoutId: String,
        operation: KotlinSavedSetCorrection
    ): NativeHistoryTransactionResult = fenced(owner, athlete) {
        if (!id.matches(workoutId))
            return@fenced denied(NativeHistoryTransactionRefusal.INVALID_REQUEST)
        val base = recoverPending(owner, athlete)
        if (base !is NativeHistoryLedgerResult.Durable)
            return@fenced fromLedger(base)
        val old = base.ledger.completions.singleOrNull {
            it.workoutId == workoutId
        } ?: return@fenced denied(NativeHistoryTransactionRefusal.WORKOUT_NOT_READY)
        val open = correctionStore.openStored(owner, workoutId) { o, source ->
            eligible(o, athlete, source)
        }
        val previous = (open as? NativeSavedCorrectionFileResult.Opened)?.state
            ?: return@fenced denied(NativeHistoryTransactionRefusal.WORKOUT_NOT_READY)
        if (previous.originalFinishedJournal.storedIdentity.storageReceiptId !=
            old.storedReceiptId ||
            previous.originalFinishedJournal.revision != old.originalJournalRevision ||
            previous.originalFinishedJournal.finishedByEventId != old.finishEventId ||
            previous.correctionRevision != old.savedCorrectionRevision)
            return@fenced denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)

        // The same request may have committed before an acknowledgment was
        // lost. Idempotent retries do not advance global history revision.
        val replay = previous.receipts.firstOrNull {
            it.correction.eventId == operation.eventId
        }
        if (replay != null)
            return@fenced if (replay.correction == operation)
                NativeHistoryTransactionResult.Durable(manifest(base.ledger), true)
            else denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)

        if (operation.expectedCorrectionRevision != previous.correctionRevision)
            return@fenced denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)
        val preview = KotlinSavedHistoryCorrectionDomain.reduce(previous, operation)
        if (preview !is KotlinSavedCorrectionDecision.Proposed || preview.replayed)
            return@fenced denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)
        val intent = NativeHistoryWriteIntent.Correct(workoutId, operation)
        val prepared = ledgerStore.prepare(
            owner, athlete, base.ledger.revision, intent)
        if (prepared !is NativeHistoryLedgerResult.Durable)
            return@fenced fromLedger(prepared)
        finishIntent(owner, athlete, prepared.ledger)
    }

    /** Idempotent owner-scoped redo; failure keeps pending history unavailable. */
    fun recover(owner: String, athlete: String): NativeHistoryTransactionResult =
        fenced(owner, athlete) {
            val recovered = recoverPending(owner, athlete)
            if (recovered !is NativeHistoryLedgerResult.Durable)
                fromLedger(recovered)
            else NativeHistoryTransactionResult.Durable(
                manifest(recovered.ledger), true)
        }

    private fun recoverPending(
        owner: String, athlete: String
    ): NativeHistoryLedgerResult {
        val stored = ledgerStore.open(owner, athlete)
        if (stored !is NativeHistoryLedgerResult.Durable) return stored
        if (stored.ledger.pending == null) return stored
        val redo = finishIntent(owner, athlete, stored.ledger)
        if (redo !is NativeHistoryTransactionResult.Durable)
            return NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.PENDING_TRANSACTION)
        return ledgerStore.open(owner, athlete)
    }

    private fun finishIntent(
        owner: String, athlete: String, prepared: NativeHistoryLedger
    ): NativeHistoryTransactionResult {
        val intent = prepared.pending
            ?: return denied(NativeHistoryTransactionRefusal.HISTORY_NOT_READY)
        val entry = when (intent) {
            is NativeHistoryWriteIntent.Finish -> {
                val applied = journalStore.appendStored(
                    owner, intent.workoutId, intent.operation) { o, source ->
                    eligible(o, athlete, source)
                }
                val result = (applied as? NativeJournalFileResult.Durable)?.journal
                    ?: return denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)
                if (result.finishedByEventId != intent.operation.eventId ||
                    result.revision != intent.operation.expectedJournalRevision + 1 ||
                    KotlinWorkoutHistoryEvidenceDomain.project(result)
                        !is KotlinHistoryProjectionResult.Candidate)
                    return denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
                KotlinHistoryManifestEntry(
                    prepared.completions.size.toLong() + 1,
                    intent.workoutId, result.storedIdentity.storageReceiptId,
                    intent.operation.eventId, result.revision, 0
                )
            }
            is NativeHistoryWriteIntent.Correct -> {
                val existing = prepared.completions.singleOrNull {
                    it.workoutId == intent.workoutId
                } ?: return denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
                val applied = correctionStore.appendStored(
                    owner, intent.workoutId, intent.operation) { o, source ->
                    eligible(o, athlete, source)
                }
                val value = (applied as? NativeSavedCorrectionFileResult.Durable)?.state
                    ?: return denied(NativeHistoryTransactionRefusal.OPERATION_REJECTED)
                val original = value.originalFinishedJournal
                if (original.storedIdentity.storageReceiptId != existing.storedReceiptId ||
                    original.revision != existing.originalJournalRevision ||
                    original.finishedByEventId != existing.finishEventId ||
                    value.correctionRevision != existing.savedCorrectionRevision + 1 ||
                    KotlinWorkoutHistoryEvidenceDomain.projectSaved(value)
                        !is KotlinHistoryProjectionResult.Candidate)
                    return denied(NativeHistoryTransactionRefusal.HISTORY_RECEIPT_MISMATCH)
                existing.copy(savedCorrectionRevision = value.correctionRevision)
            }
        }
        val committed = ledgerStore.finalize(owner, athlete, intent, entry)
        return if (committed is NativeHistoryLedgerResult.Durable)
            NativeHistoryTransactionResult.Durable(manifest(committed.ledger), false)
        else fromLedger(committed)
    }

    private fun fromLedger(value: NativeHistoryLedgerResult):
        NativeHistoryTransactionResult = when (value) {
        is NativeHistoryLedgerResult.Durable ->
            denied(NativeHistoryTransactionRefusal.HISTORY_NOT_READY)
        is NativeHistoryLedgerResult.Rejected -> denied(
            when (value.reason) {
                NativeHistoryLedgerRefusal.NOT_ENROLLED,
                NativeHistoryLedgerRefusal.NOT_AUTHORIZED ->
                    NativeHistoryTransactionRefusal.NOT_ENROLLED_OR_AUTHORIZED
                NativeHistoryLedgerRefusal.PENDING_TRANSACTION ->
                    NativeHistoryTransactionRefusal.HISTORY_NOT_READY
                else -> NativeHistoryTransactionRefusal.LEDGER_REJECTED
            }, value.reason)
    }

    private fun fenced(
        owner: String, athlete: String,
        operation: () -> NativeHistoryTransactionResult
    ): NativeHistoryTransactionResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return denied(NativeHistoryTransactionRefusal.INVALID_REQUEST)
        return runCatching {
            ownerFence.withOwnerTransaction(owner, operation)
        }.getOrElse { denied(NativeHistoryTransactionRefusal.STORAGE_UNAVAILABLE) }
    }
}
