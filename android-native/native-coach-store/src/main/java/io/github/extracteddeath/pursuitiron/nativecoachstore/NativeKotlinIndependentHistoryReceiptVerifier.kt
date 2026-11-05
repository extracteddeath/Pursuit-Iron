package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Independently replays ONE durable Composer or custom workout receipt
 * directly from native workout/correction files. Does NOT consult the
 * version-2 history ledger. This verifies factual evidence for the claimed
 * Finish/correction; it does NOT prove unique workout enrollment, complete
 * historical inventory, or authorize a segmented root publication.
 *
 * Every call holds the same owner fence as actual native workout issuance,
 * corrections, sign-out, and future segmented writer transactions.
 */
enum class NativeIndependentReceiptRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, INVALID_ENTRY,
    JOURNAL_NOT_VERIFIED, FINISH_MISMATCH, CORRECTION_MISMATCH,
    EVIDENCE_NOT_VERIFIED, SOURCE_CHANGED, STORAGE_UNAVAILABLE
}

data class NativeIndependentReceiptEvidence(
    val entry: KotlinHistoryManifestEntry,
    val intentEventId: String,
    val isCustom: Boolean
) {
    val canPublishHistory: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val provesCompleteHistory: Boolean get() = false
}
sealed interface NativeIndependentReceiptResult {
    data class Verified(val evidence: NativeIndependentReceiptEvidence) :
        NativeIndependentReceiptResult
    data class Rejected(val reason: NativeIndependentReceiptRefusal) :
        NativeIndependentReceiptResult
}

class NativeKotlinIndependentHistoryReceiptVerifier(
    private val composer: KotlinNativeJournalFileStore,
    private val composerCorrections: KotlinNativeSavedCorrectionFileStore,
    private val custom: NativeKotlinCustomWorkoutJournalFileStore,
    private val customCorrections: NativeKotlinCustomSavedCorrectionFileStore?,
    private val authority: NativeKotlinWorkoutAuthority,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REVISION = 9_007_199_254_740_991L

    private fun deny(reason: NativeIndependentReceiptRefusal) =
        NativeIndependentReceiptResult.Rejected(reason)

    private fun authorized(owner: String, athlete: String) =
        runCatching {
            identity.signedInIdentity() == NativeKotlinOwnerIdentity(owner, athlete)
        }.getOrDefault(false)

    private fun validEntry(e: KotlinHistoryManifestEntry) =
        e.completionOrder in 1..MAX_REVISION &&
            e.originalJournalRevision in 1..MAX_REVISION &&
            e.savedCorrectionRevision in 0..MAX_REVISION &&
            id.matches(e.workoutId) && id.matches(e.storedReceiptId) &&
            id.matches(e.finishEventId)

    fun verify(
        owner: String, athlete: String, intent: NativeHistoryWriteIntent,
        entry: KotlinHistoryManifestEntry
    ): NativeIndependentReceiptResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeIndependentReceiptRefusal.INVALID_SCOPE)
        if (!validEntry(entry) || entry.workoutId != intent.workoutId)
            return deny(NativeIndependentReceiptRefusal.INVALID_ENTRY)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    return@withOwnerTransaction deny(
                        NativeIndependentReceiptRefusal.NOT_AUTHORIZED)
                val result = when (intent) {
                    is NativeHistoryWriteIntent.Finish ->
                        verifyComposer(owner, athlete, intent, entry, true)
                    is NativeHistoryWriteIntent.Correct ->
                        verifyComposer(owner, athlete, intent, entry, false)
                    is NativeHistoryWriteIntent.CustomFinish ->
                        verifyCustom(owner, athlete, intent, entry, true)
                    is NativeHistoryWriteIntent.CustomCorrect ->
                        verifyCustom(owner, athlete, intent, entry, false)
                }
                if (!authorized(owner, athlete))
                    deny(NativeIndependentReceiptRefusal.SOURCE_CHANGED)
                else result
            }
        }.getOrElse { deny(NativeIndependentReceiptRefusal.STORAGE_UNAVAILABLE) }
    }

    private fun verifyComposer(
        owner: String, athlete: String, intent: NativeHistoryWriteIntent,
        entry: KotlinHistoryManifestEntry, isFinish: Boolean
    ): NativeIndependentReceiptResult {
        if (entry.storedReceiptId.startsWith("custom-"))
            return deny(NativeIndependentReceiptRefusal.INVALID_ENTRY)
        val access: (String, KotlinTrainingSourceRevision) -> Boolean =
            { who, source -> who == owner && source.athleteId == athlete &&
                runCatching {
                    authority.canAccessIssuedWorkout(who, source)
                }.getOrDefault(false)
            }
        val original = (composer.openStored(owner, entry.workoutId, access)
            as? NativeJournalFileResult.Durable)?.journal
            ?: return deny(NativeIndependentReceiptRefusal.JOURNAL_NOT_VERIFIED)
        if (original.snapshot.source.athleteId != athlete ||
            original.snapshot.workoutId != entry.workoutId ||
            original.storedIdentity.workoutId != entry.workoutId ||
            original.storedIdentity.storageReceiptId != entry.storedReceiptId ||
            original.revision != entry.originalJournalRevision ||
            original.finishedByEventId != entry.finishEventId)
            return deny(NativeIndependentReceiptRefusal.FINISH_MISMATCH)
        val saved = (composerCorrections.openStored(
            owner, entry.workoutId, access)
            as? NativeSavedCorrectionFileResult.Opened)
            ?: return deny(NativeIndependentReceiptRefusal.JOURNAL_NOT_VERIFIED)
        if (saved.state.originalFinishedJournal != original ||
            saved.state.correctionRevision != entry.savedCorrectionRevision ||
            saved.hasCorrectionFile != (entry.savedCorrectionRevision > 0L))
            return deny(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH)
        if (isFinish) {
            val finish = (intent as NativeHistoryWriteIntent.Finish).operation
            if (finish.eventId != entry.finishEventId ||
                finish.expectedJournalRevision + 1 != original.revision ||
                entry.savedCorrectionRevision != 0L)
                return deny(NativeIndependentReceiptRefusal.FINISH_MISMATCH)
        } else {
            val corrected = (intent as NativeHistoryWriteIntent.Correct).operation
            if (corrected.expectedCorrectionRevision + 1 !=
                    entry.savedCorrectionRevision ||
                saved.state.receipts.lastOrNull()?.correction != corrected ||
                saved.state.receipts.lastOrNull()?.appliedCorrectionRevision !=
                    entry.savedCorrectionRevision)
                return deny(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH)
        }
        val projected = if (saved.hasCorrectionFile)
            KotlinWorkoutHistoryEvidenceDomain.projectSaved(saved.state)
        else KotlinWorkoutHistoryEvidenceDomain.project(original)
        if (projected !is KotlinHistoryProjectionResult.Candidate)
            return deny(NativeIndependentReceiptRefusal.EVIDENCE_NOT_VERIFIED)
        val eventId = if (isFinish) entry.finishEventId
            else (intent as NativeHistoryWriteIntent.Correct).operation.eventId
        return NativeIndependentReceiptResult.Verified(
            NativeIndependentReceiptEvidence(entry, eventId, false))
    }

    private fun verifyCustom(
        owner: String, athlete: String, intent: NativeHistoryWriteIntent,
        entry: KotlinHistoryManifestEntry, isFinish: Boolean
    ): NativeIndependentReceiptResult {
        if (!entry.storedReceiptId.startsWith("custom-v2-"))
            return deny(NativeIndependentReceiptRefusal.INVALID_ENTRY)
        val original = (custom.open(owner, entry.workoutId)
            as? NativeCustomJournalResult.Durable)?.issued
            ?: return deny(NativeIndependentReceiptRefusal.JOURNAL_NOT_VERIFIED)
        val journal = original.journal
        if (original.ownerKey != owner || original.athleteId != athlete ||
            journal.snapshot.stamp.athleteId != athlete ||
            journal.workoutId != entry.workoutId ||
            journal.revision != entry.originalJournalRevision ||
            journal.finishedByEventId != entry.finishEventId ||
            NativeCustomFinishReceipt.compute(owner, athlete, journal) !=
                entry.storedReceiptId)
            return deny(NativeIndependentReceiptRefusal.FINISH_MISMATCH)
        // Without the custom correction store, an absence of edits cannot be
        // proved. Reject instead of silently treating the workout as unedited.
        val saved = (customCorrections?.open(owner, entry.workoutId)
            as? NativeCustomSavedCorrectionResult.Opened)
            ?: return deny(NativeIndependentReceiptRefusal.JOURNAL_NOT_VERIFIED)
        if (saved.state.originalFinishedJournal != journal ||
            saved.state.correctionRevision != entry.savedCorrectionRevision ||
            saved.hasCorrectionFile != (entry.savedCorrectionRevision > 0L))
            return deny(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH)
        if (isFinish) {
            val finish = (intent as NativeHistoryWriteIntent.CustomFinish).operation
            if (finish.eventId != entry.finishEventId ||
                finish.expectedRevision + 1 != journal.revision ||
                entry.savedCorrectionRevision != 0L)
                return deny(NativeIndependentReceiptRefusal.FINISH_MISMATCH)
        } else {
            val corrected = (intent as NativeHistoryWriteIntent.CustomCorrect).operation
            if (corrected.expectedCorrectionRevision + 1 !=
                    entry.savedCorrectionRevision ||
                saved.state.receipts.lastOrNull()?.correction != corrected ||
                saved.state.receipts.lastOrNull()?.appliedCorrectionRevision !=
                    entry.savedCorrectionRevision)
                return deny(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH)
        }
        val valid = if (saved.hasCorrectionFile)
            CustomSavedHistoryEvidenceDomain.projectSaved(saved.state)
                is CustomSavedHistoryProjectionResult.Candidate
        else CustomWorkoutHistoryEvidenceDomain.project(journal)
                is CustomHistoryEvidenceResult.Candidate
        if (!valid) return deny(NativeIndependentReceiptRefusal.EVIDENCE_NOT_VERIFIED)
        val eventId = if (isFinish) entry.finishEventId
            else (intent as NativeHistoryWriteIntent.CustomCorrect).operation.eventId
        return NativeIndependentReceiptResult.Verified(
            NativeIndependentReceiptEvidence(entry, eventId, true))
    }
}
