package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * A factual read-only timeline for BOTH authored custom cycles and generated
 * Composer workouts. This does not issue coaching adjustments, PR badges, or
 * workout prescriptions. Never substitute it for an owner-approved history
 * migration or the eventual longitudinal coaching policy.
 */
sealed interface NativeKotlinMixedHistoryCompletion {
    val manifest: KotlinHistoryManifestEntry

    data class Composer(
        override val manifest: KotlinHistoryManifestEntry,
        val evidence: KotlinWorkoutHistoryEvidence
    ) : NativeKotlinMixedHistoryCompletion

    data class Custom(
        override val manifest: KotlinHistoryManifestEntry,
        val evidence: CustomWorkoutHistoricalEvidence
    ) : NativeKotlinMixedHistoryCompletion
}

data class NativeKotlinMixedHistoryTimeline(
    val athleteId: String,
    val manifestRevision: Long,
    val completions: List<NativeKotlinMixedHistoryCompletion>
) {
    /** Factual evidence is not an approved all-program PR calculation. */
    val canPublishPr: Boolean get() = false
    val canApplyProgression: Boolean get() = false
}

enum class NativeKotlinMixedHistoryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_ENROLLED,
    PENDING_TRANSACTION, INVALID_MANIFEST, UNVERIFIED_COMPOSER,
    UNVERIFIED_CUSTOM, MANIFEST_CHANGED, STORAGE_UNAVAILABLE
}

sealed interface NativeKotlinMixedHistoryReadResult {
    data class Review(val timeline: NativeKotlinMixedHistoryTimeline) :
        NativeKotlinMixedHistoryReadResult

    data class Rejected(val reason: NativeKotlinMixedHistoryRefusal) :
        NativeKotlinMixedHistoryReadResult
}

/**
 * Owner-fenced, exhaustively re-opened disk projection. The shared ledger
 * decides WHICH workouts exist and their chronological order. Each workout's
 * own independently verified storage decides WHAT actually happened.
 *
 * No UI-provided IDs, source snapshots, counts, flags, or cached Last values.
 * A bad file blocks the ENTIRE read rather than dropping an inconvenient
 * workout and producing an inaccurate personal best or progress chart.
 */
class NativeKotlinMixedHistoryReadCoordinator(
    private val ledgerStore: NativeKotlinHistoryManifestFileStore,
    private val composerStore: KotlinNativeJournalFileStore,
    private val correctionStore: KotlinNativeSavedCorrectionFileStore,
    private val customStore: NativeKotlinCustomWorkoutJournalFileStore,
    private val workoutAuthority: NativeKotlinWorkoutAuthority,
    private val identity: NativeKotlinCurrentIdentity,
    private val fence: NativeKotlinApprovalRevisionFence,
    /** Null means custom saved edits remain deliberately unavailable. */
    private val customCorrections: NativeKotlinCustomSavedCorrectionFileStore? = null
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun rejected(reason: NativeKotlinMixedHistoryRefusal) =
        NativeKotlinMixedHistoryReadResult.Rejected(reason)

    private fun authorized(owner: String, athlete: String): Boolean =
        runCatching { identity.signedInIdentity() }.getOrNull()?.let {
            it.ownerKey == owner && it.athleteId == athlete
        } == true

    fun read(owner: String, athlete: String): NativeKotlinMixedHistoryReadResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return rejected(NativeKotlinMixedHistoryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) { readFenced(owner, athlete) }
        }.getOrElse {
            rejected(NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private fun readFenced(
        owner: String, athlete: String
    ): NativeKotlinMixedHistoryReadResult {
        if (!authorized(owner, athlete))
            return rejected(NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED)
        val opened = ledgerStore.open(owner, athlete)
        if (opened is NativeHistoryLedgerResult.Rejected)
            return rejected(when (opened.reason) {
                NativeHistoryLedgerRefusal.NOT_ENROLLED ->
                    NativeKotlinMixedHistoryRefusal.NOT_ENROLLED
                NativeHistoryLedgerRefusal.NOT_AUTHORIZED ->
                    NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED
                NativeHistoryLedgerRefusal.PENDING_TRANSACTION ->
                    NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION
                NativeHistoryLedgerRefusal.STORAGE_UNAVAILABLE ->
                    NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE
                else -> NativeKotlinMixedHistoryRefusal.INVALID_MANIFEST
            })
        if (opened !is NativeHistoryLedgerResult.Durable)
            return rejected(NativeKotlinMixedHistoryRefusal.INVALID_MANIFEST)
        val ledger = opened.ledger
        if (ledger.pending != null)
            return rejected(NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION)
        if (!ledger.readyForHistoryReads ||
            ledger.ownerKey != owner || ledger.athleteId != athlete ||
            ledger.revision < 1 || ledger.completions.size > 128 ||
            ledger.completions.map { it.workoutId }.distinct().size !=
                ledger.completions.size ||
            ledger.completions.withIndex().any { (index, item) ->
                item.completionOrder != index.toLong() + 1
            })
            return rejected(NativeKotlinMixedHistoryRefusal.INVALID_MANIFEST)

        val values = ArrayList<NativeKotlinMixedHistoryCompletion>(
            ledger.completions.size)
        for (entry in ledger.completions) {
            if (entry.storedReceiptId.startsWith("custom-")) {
                val stored = customStore.open(owner, entry.workoutId)
                val journal = (stored as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
                    ?: return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM)
                if (journal.snapshot.stamp.athleteId != athlete ||
                    journal.workoutId != entry.workoutId ||
                    journal.revision != entry.originalJournalRevision ||
                    journal.finishedByEventId != entry.finishEventId ||
                    NativeCustomFinishReceipt.compute(owner, athlete, journal) !=
                        entry.storedReceiptId)
                    return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM)
                val edits = customCorrections?.open(owner, entry.workoutId)
                if (edits != null && edits !is NativeCustomSavedCorrectionResult.Opened)
                    return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM)
                val currentEdits = edits as? NativeCustomSavedCorrectionResult.Opened
                if ((currentEdits == null && entry.savedCorrectionRevision != 0L) ||
                    (currentEdits != null &&
                        (currentEdits.state.originalFinishedJournal != journal ||
                         currentEdits.state.correctionRevision !=
                            entry.savedCorrectionRevision ||
                         currentEdits.hasCorrectionFile !=
                            (entry.savedCorrectionRevision > 0))))
                    return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM)
                val evidence = if (entry.savedCorrectionRevision > 0L) {
                    val projected = CustomSavedHistoryEvidenceDomain.projectSaved(
                        checkNotNull(currentEdits).state)
                    (projected as? CustomSavedHistoryProjectionResult.Candidate)
                        ?.corrected?.evidence
                } else {
                    (CustomWorkoutHistoryEvidenceDomain.project(journal)
                        as? CustomHistoryEvidenceResult.Candidate)?.evidence
                } ?: return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM)
                values.add(NativeKotlinMixedHistoryCompletion.Custom(entry, evidence))
            } else {
                val access: (String, KotlinTrainingSourceRevision) -> Boolean =
                    { who, source ->
                        who == owner && source.athleteId == athlete && runCatching {
                            workoutAuthority.canAccessIssuedWorkout(who, source)
                        }.getOrDefault(false)
                    }
                val openedJournal = composerStore.openStored(
                    owner, entry.workoutId, access)
                val original = (openedJournal as? NativeJournalFileResult.Durable)
                    ?.journal ?: return rejected(
                        NativeKotlinMixedHistoryRefusal.UNVERIFIED_COMPOSER)
                val openedCorrections = correctionStore.openStored(
                    owner, entry.workoutId, access)
                val edits = openedCorrections as? NativeSavedCorrectionFileResult.Opened
                    ?: return rejected(
                        NativeKotlinMixedHistoryRefusal.UNVERIFIED_COMPOSER)
                if (original.storedIdentity.storageReceiptId != entry.storedReceiptId ||
                    original.snapshot.workoutId != entry.workoutId ||
                    original.revision != entry.originalJournalRevision ||
                    original.finishedByEventId != entry.finishEventId ||
                    edits.state.originalFinishedJournal != original ||
                    edits.state.correctionRevision != entry.savedCorrectionRevision)
                    return rejected(NativeKotlinMixedHistoryRefusal.UNVERIFIED_COMPOSER)
                val projection = if (edits.hasCorrectionFile)
                    KotlinWorkoutHistoryEvidenceDomain.projectSaved(edits.state)
                else KotlinWorkoutHistoryEvidenceDomain.project(original)
                val evidence = (projection as? KotlinHistoryProjectionResult.Candidate)
                    ?.evidence ?: return rejected(
                        NativeKotlinMixedHistoryRefusal.UNVERIFIED_COMPOSER)
                values.add(NativeKotlinMixedHistoryCompletion.Composer(entry, evidence))
            }
        }

        // A second owner read under the shared lock detects incompatible
        // authorities, in-flight changes, and revoked authentication.
        val reread = ledgerStore.open(owner, athlete)
        if (!authorized(owner, athlete))
            return rejected(NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED)
        if (reread !is NativeHistoryLedgerResult.Durable ||
            reread.ledger != ledger)
            return rejected(NativeKotlinMixedHistoryRefusal.MANIFEST_CHANGED)
        return NativeKotlinMixedHistoryReadResult.Review(
            NativeKotlinMixedHistoryTimeline(athlete, ledger.revision,
                Collections.unmodifiableList(values.toList()))
        )
    }
}
