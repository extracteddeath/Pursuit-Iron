package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Pure recovery verifier for the Kotlin-only workout journal.
 *
 * The native store must authenticate and atomically read the issued snapshot,
 * its storage identity, and the complete ordered receipt stream. This verifier
 * never trusts a saved effective-set map or finished flag: it replays each
 * operation from the immutable issued targets. No JS engine or persistence
 * adapter participates. A valid replay is still NOT a durable acknowledgment.
 */
enum class KotlinJournalReplayRefusal {
    INVALID_ISSUED_IDENTITY,
    TOO_MANY_RECEIPTS,
    NONCONTIGUOUS_REVISION,
    INVALID_OPERATION,
    STORED_STATE_MISMATCH
}

sealed interface KotlinJournalReplayResult {
    data class Recovered(val journal: KotlinWorkoutJournalState) : KotlinJournalReplayResult
    data class Rejected(
        val reason: KotlinJournalReplayRefusal,
        /** Zero-based position; null when rejecting metadata or aggregate state. */
        val receiptIndex: Int? = null
    ) : KotlinJournalReplayResult
}

object KotlinWorkoutJournalReplayDomain {
    private const val MAX_RECEIPTS = 2048

    fun replay(
        snapshot: KotlinStagedWorkout,
        identity: KotlinStoredWorkoutIdentity,
        committedReceipts: List<KotlinJournalReceipt>,
        expectedRevision: Long,
        expectedFinishedByEventId: String? = null
    ): KotlinJournalReplayResult {
        val initial=runCatching {
            KotlinWorkoutJournalDomain.begin(snapshot,identity)
        }.getOrElse {
            return KotlinJournalReplayResult.Rejected(
                KotlinJournalReplayRefusal.INVALID_ISSUED_IDENTITY)
        }
        if(committedReceipts.size>MAX_RECEIPTS)
            return KotlinJournalReplayResult.Rejected(
                KotlinJournalReplayRefusal.TOO_MANY_RECEIPTS)
        if(expectedRevision<0 || expectedRevision!=committedReceipts.size.toLong())
            return KotlinJournalReplayResult.Rejected(
                KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION)
        var journal=initial
        for((index,receipt) in committedReceipts.withIndex()) {
            if(receipt.appliedRevision!=index.toLong()+1 ||
                receipt.operation.expectedJournalRevision!=index.toLong())
                return KotlinJournalReplayResult.Rejected(
                    KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION,index)
            val decision=KotlinWorkoutJournalDomain.reduce(journal,receipt.operation)
            if(decision !is KotlinJournalDecision.Proposed || decision.replayed ||
                decision.state.revision!=receipt.appliedRevision)
                return KotlinJournalReplayResult.Rejected(
                    KotlinJournalReplayRefusal.INVALID_OPERATION,index)
            journal=decision.state
        }
        if(journal.finishedByEventId!=expectedFinishedByEventId)
            return KotlinJournalReplayResult.Rejected(
                KotlinJournalReplayRefusal.STORED_STATE_MISMATCH)
        return KotlinJournalReplayResult.Recovered(journal)
    }

    /**
     * Compare a denormalized stored journal against a receipt-derived state.
     * The store must not treat the saved map as authoritative for recovery.
     */
    fun verifyStoredState(stored: KotlinWorkoutJournalState): KotlinJournalReplayResult {
        val recovered=replay(
            stored.originalSnapshot,stored.storedIdentity,stored.receipts,
            stored.revision,stored.finishedByEventId
        )
        if(recovered !is KotlinJournalReplayResult.Recovered)return recovered
        if(recovered.journal!=stored)
            return KotlinJournalReplayResult.Rejected(
                KotlinJournalReplayRefusal.STORED_STATE_MISMATCH)
        return recovered
    }
}
