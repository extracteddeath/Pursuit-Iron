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
        val evidence: KotlinWorkoutHistoryEvidence,
        /** Authenticated from the original immutable native issued snapshot. */
        val purpose: ComposerSessionPurpose = ComposerSessionPurpose.UNSPECIFIED
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
 * Factual working-set projection produced ONLY by the complete owner-verified,
 * revision-checked native history read, with the same transaction lock held.
 * This is not a cache validity guarantee after the lock is released.
 */
sealed interface NativeKotlinMixedFactsReadResult {
    data class Review(val facts: NativeKotlinMixedHistoryFacts) :
        NativeKotlinMixedFactsReadResult
    data class HistoryRejected(val reason: NativeKotlinMixedHistoryRefusal) :
        NativeKotlinMixedFactsReadResult
    data class FactsRejected(val reason: NativeMixedFactsRefusal) :
        NativeKotlinMixedFactsReadResult
}

enum class NativeOwnerLoadReviewRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NO_CURRENT_APPROVAL, STORAGE_UNAVAILABLE
}
sealed interface NativeOwnerLoadReviewResult {
    data class Rejected(val reason: NativeOwnerLoadReviewRefusal) :
        NativeOwnerLoadReviewResult
    data class HistoryRejected(val reason: NativeKotlinMixedHistoryRefusal) :
        NativeOwnerLoadReviewResult
    data class Review(val coaching: NativeCurrentLoadReviewResult) :
        NativeOwnerLoadReviewResult
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

    /**
     * Safe factual bridge: verification and projection both occur under the
     * SAME owner fence. Do not project a caller-constructed timeline as a
     * replacement for this path. A UI must still reject async/stale results
     * when the manifest revision changes after this method returns.
     */
    fun readFacts(owner: String, athlete: String): NativeKotlinMixedFactsReadResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return NativeKotlinMixedFactsReadResult.HistoryRejected(
                NativeKotlinMixedHistoryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                when (val original = readFenced(owner, athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        NativeKotlinMixedFactsReadResult.HistoryRejected(original.reason)
                    is NativeKotlinMixedHistoryReadResult.Review ->
                        when (val projected =
                            NativeKotlinMixedHistoryFactsDomain.project(original.timeline)) {
                            is NativeMixedFactsResult.Rejected ->
                                NativeKotlinMixedFactsReadResult.FactsRejected(projected.reason)
                            is NativeMixedFactsResult.Review ->
                                NativeKotlinMixedFactsReadResult.Review(projected.facts)
                        }
                }
            }
        }.getOrElse {
            NativeKotlinMixedFactsReadResult.HistoryRejected(
                NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Authenticated, read-only next-program coaching from the COMPLETE
     * native ledger. Caller never supplies a hand-picked workout history
     * pair, a cached Last value or a fabricated approved ComposerRequest.
     *
     * A single owner fence spans current approval, all saved corrections,
     * every completion, and an end-of-read revision check. The returned
     * suggestion does not grant mutation/issuance rights after unlock.
     */
    fun reviewCurrentApprovedLoad(
        owner: String,
        athlete: String,
        approvals: NativeKotlinApprovalFileAuthority,
        exerciseId: String,
        maximumAuthorizedIncrease: java.math.BigDecimal,
        readiness: CoachReadiness,
        movementRestricted: Boolean
    ): NativeOwnerLoadReviewResult {
        if(!id.matches(owner) || !id.matches(athlete))
            return NativeOwnerLoadReviewResult.Rejected(
                NativeOwnerLoadReviewRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete))
                    return@withOwnerTransaction NativeOwnerLoadReviewResult.Rejected(
                        NativeOwnerLoadReviewRefusal.NOT_AUTHORIZED)
                val approval=approvals.currentOwnedApproval(owner)
                    ?.takeIf { it.athleteId == athlete }
                    ?:return@withOwnerTransaction NativeOwnerLoadReviewResult.Rejected(
                        NativeOwnerLoadReviewRefusal.NO_CURRENT_APPROVAL)
                when(val history=readFenced(owner,athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        NativeOwnerLoadReviewResult.HistoryRejected(history.reason)
                    is NativeKotlinMixedHistoryReadResult.Review ->
                        NativeOwnerLoadReviewResult.Review(
                            NativeKotlinCurrentLoadCoachReview.inspect(
                                approval,history.timeline,exerciseId,
                                history.timeline.manifestRevision,
                                maximumAuthorizedIncrease,readiness,
                                movementRestricted))
                }
            }
        }.getOrElse {
            NativeOwnerLoadReviewResult.Rejected(
                NativeOwnerLoadReviewRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * BOUNDED isolated native program acceptance transaction.
     *
     * This executes under exactly the same owner lock as native approvals,
     * journal issuance and history commits. No UI-supplied proposed program
     * or saved set data is trusted. All of the actual completed journals and
     * saved corrections are opened again at commit time; a stale review,
     * unbuildable target or reused workout identity fails closed.
     *
     * The approval file is the current candidate generated-program owner.
     * This does NOT modify a prior issued workout or transfer legacy history
     * ownership. Future production must additionally authenticate the
     * persisted athlete/gym/catalog source and certify directory fsync.
     */
    fun acceptCurrentApprovedLoad(
        owner: String,
        athlete: String,
        approvals: NativeKotlinApprovalFileAuthority,
        intent: NativeKotlinCoachLoadAcceptanceIntent,
        maximumAuthorizedIncrease: java.math.BigDecimal,
        readiness: CoachReadiness,
        movementRestricted: Boolean
    ): NativeKotlinCoachAcceptanceResult {
        fun reject(reason: NativeKotlinCoachAcceptanceRefusal) =
            NativeKotlinCoachAcceptanceResult.Rejected(reason)
        val validWorkoutId = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
        if(!id.matches(owner) || !id.matches(athlete) ||
            !id.matches(intent.exerciseId) ||
            !validWorkoutId.matches(intent.newWorkoutId) ||
            !validWorkoutId.matches(intent.newGrantId) ||
            !validWorkoutId.matches(intent.newStorageReceiptId) ||
            intent.newDayIndex !in 0..5 ||
            intent.expectedApprovalRevision !in 1..9_007_199_254_740_990L ||
            intent.expectedHistoryRevision !in 1..9_007_199_254_740_991L ||
            intent.expectedComparedWorkoutIds.size != 2 ||
            intent.expectedComparedWorkoutIds.distinct().size != 2 ||
            intent.expectedComparedWorkoutIds.any { !id.matches(it) } ||
            intent.expectedAffectedDayIndices.isEmpty() ||
            intent.expectedAffectedDayIndices.distinct().size !=
                intent.expectedAffectedDayIndices.size)
            return reject(NativeKotlinCoachAcceptanceRefusal.INVALID_SCOPE)
        if(!intent.explicitlyAccepted)
            return reject(NativeKotlinCoachAcceptanceRefusal.NOT_EXPLICITLY_ACCEPTED)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner, athlete))
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.NOT_AUTHORIZED)
                val current = approvals.currentOwnedApproval(owner)
                    ?.takeIf { it.athleteId == athlete }
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.NO_CURRENT_APPROVAL)
                if(current.storageRevision != intent.expectedApprovalRevision)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.APPROVAL_CHANGED)
                if(current.grant.workoutId == intent.newWorkoutId ||
                    current.grant.grantId == intent.newGrantId ||
                    current.storageReceiptId == intent.newStorageReceiptId)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.IDENTIFIER_ALREADY_USED)
                val history = when(val read = readFenced(owner, athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        return@withOwnerTransaction reject(
                            NativeKotlinCoachAcceptanceRefusal.HISTORY_UNAVAILABLE)
                    is NativeKotlinMixedHistoryReadResult.Review -> read.timeline
                }
                if(history.manifestRevision != intent.expectedHistoryRevision)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.HISTORY_CHANGED)
                if(history.completions.any {
                    it.manifest.workoutId == intent.newWorkoutId ||
                    it.manifest.storedReceiptId == intent.newStorageReceiptId
                })
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.IDENTIFIER_ALREADY_USED)
                // Detect even an issued, unfinished workout omitted from the
                // completion manifest. A corrupt/unauthorized existing journal
                // is NEVER interpreted as a missing identity.
                val existing = composerStore.openStored(
                    owner, intent.newWorkoutId) { who, source ->
                    who == owner && source.athleteId == athlete &&
                        runCatching {
                            approvals.canAccessIssuedWorkout(who, source)
                        }.getOrDefault(false)
                }
                if(existing != NativeJournalFileResult.Rejected(
                    NativeJournalFileRefusal.NOT_ISSUED))
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.IDENTIFIER_ALREADY_USED)

                val review = NativeKotlinCurrentLoadCoachReview.inspect(
                    current,history,intent.exerciseId,history.manifestRevision,
                    maximumAuthorizedIncrease,readiness,movementRestricted)
                val verified = review as? NativeCurrentLoadReviewResult.Reviewed
                    ?: return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.NO_VERIFIED_COACHING_CHANGE)
                val coaching = verified.coaching
                val proposed = coaching.candidateProgram
                if(coaching.status != CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW ||
                    proposed == null || coaching.kind != intent.expectedKind ||
                    verified.comparedWorkoutIds != intent.expectedComparedWorkoutIds ||
                    coaching.affectedDayIndices != intent.expectedAffectedDayIndices ||
                    coaching.originalLoad?.compareTo(intent.expectedOriginalLoad) != 0 ||
                    coaching.candidateLoad?.compareTo(intent.expectedCandidateLoad) != 0 ||
                    intent.newDayIndex !in proposed.days.indices)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.PROPOSAL_CHANGED)
                val source = current.currentSource
                if(source.programRevision >= 9_007_199_254_740_991L)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.APPROVAL_CHANGED)
                val nextSource = source.copy(
                    programRevision = source.programRevision + 1)
                val nextRevision = current.storageRevision + 1
                val nextGrant = KotlinWorkoutSelectionGrant(
                    intent.newGrantId,intent.newWorkoutId,nextSource,
                    intent.newDayIndex)
                val next = current.copy(
                    approvedSource = nextSource,
                    currentSource = nextSource,
                    grant = nextGrant,
                    program = proposed,
                    storageReceiptId = intent.newStorageReceiptId,
                    storageRevision = nextRevision)
                val validation = KotlinWorkoutSnapshotDomain.stage(
                    KotlinWorkoutSnapshotRequest(
                        nextSource,nextSource,nextGrant,proposed,next.policies))
                if(validation !is KotlinWorkoutSnapshotResult.Staged)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.NEW_WORKOUT_UNRESOLVED)
                return@withOwnerTransaction when(
                    approvals.publish(owner,current.storageRevision,next)) {
                    is NativeApprovalWriteResult.Committed ->
                        NativeKotlinCoachAcceptanceResult.Committed(
                            nextSource,intent.newWorkoutId,nextRevision,
                            history.manifestRevision)
                    is NativeApprovalWriteResult.Rejected ->
                        reject(NativeKotlinCoachAcceptanceRefusal.APPROVAL_STORE_REJECTED)
                }
            }
        }.getOrElse {
            reject(NativeKotlinCoachAcceptanceRefusal.STORAGE_UNAVAILABLE)
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
            ledger.revision < 1 ||
            ledger.completions.size > KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS ||
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
                values.add(NativeKotlinMixedHistoryCompletion.Composer(
                    entry, evidence, original.snapshot.purpose))
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
