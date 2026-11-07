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
        val purpose: ComposerSessionPurpose = ComposerSessionPurpose.UNSPECIFIED,
        /** Authenticated original 0-based program day slot; -1 for synthetic
         * legacy evidence with no separately authenticated slot. */
        val dayIndex: Int = -1
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

enum class NativeOwnerNextSetRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, ASSISTANCE_DISABLED, NO_CURRENT_APPROVAL,
    GYM_SOURCE_UNAVAILABLE, GYM_SOURCE_CHANGED,
    ALREADY_ISSUED_OR_CONFLICT, STORAGE_UNAVAILABLE
}
sealed interface NativeOwnerNextSetResult {
    data class Rejected(val reason:NativeOwnerNextSetRefusal) :
        NativeOwnerNextSetResult
    data class HistoryRejected(val reason:NativeKotlinMixedHistoryRefusal) :
        NativeOwnerNextSetResult
    data class Review(
        val approvalRevision:Long,
        val candidate:NativeCurrentRepPrescriptionResult
    ) : NativeOwnerNextSetResult
}

data class NativeKotlinAcceptedRepIntent(
    val exerciseId:String,
    val expectedApprovalRevision:Long,
    val expectedHistoryRevision:Long,
    val expectedCurrentWorkoutId:String,
    val expectedHistoricalWorkoutId:String,
    val expectedChangedSetKey:String,
    val expectedObservedReps:List<Int>,
    val expectedNewRepTargets:List<Int>,
    val nextWorkoutId:String,
    val nextGrantId:String,
    val nextStorageReceiptId:String,
    /** The UI must collect a fresh, explicit acceptance of every set. */
    val explicitlyAccepted:Boolean
)
enum class NativeKotlinRepAcceptanceRefusal {
    INVALID_SCOPE, NOT_ACCEPTED, NOT_AUTHORIZED, ASSISTANCE_DISABLED, NO_CURRENT_APPROVAL,
    APPROVAL_CHANGED, HISTORY_CHANGED, GYM_CHANGED,
    SOURCE_NOT_VERIFIED, ALREADY_ISSUED,
    IDENTIFIER_ALREADY_USED, PROPOSAL_CHANGED, STORAGE_REJECTED,
    STORAGE_UNAVAILABLE
}
sealed interface NativeKotlinRepAcceptanceResult {
    data class Rejected(val reason:NativeKotlinRepAcceptanceRefusal) :
        NativeKotlinRepAcceptanceResult
    data class Committed(
        val approvalRevision:Long,
        val approvedWorkoutId:String,
        val historicalRevision:Long
    ):NativeKotlinRepAcceptanceResult {
        val workoutIssued:Boolean get()=false
    }
}

enum class NativeOwnerLoadReviewRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, ASSISTANCE_DISABLED, NO_CURRENT_APPROVAL,
    GYM_SOURCE_UNAVAILABLE, GYM_SOURCE_CHANGED, STORAGE_UNAVAILABLE
}
sealed interface NativeOwnerLoadReviewResult {
    data class Rejected(val reason: NativeOwnerLoadReviewRefusal) :
        NativeOwnerLoadReviewResult
    data class HistoryRejected(val reason: NativeKotlinMixedHistoryRefusal) :
        NativeOwnerLoadReviewResult
    data class Review(
        val coaching: NativeCurrentLoadReviewResult,
        /** From owner-fenced accepted record; still rechecked on write. */
        val approvalStorageRevision: Long = 0
    ) : NativeOwnerLoadReviewResult
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
        movementRestricted: Boolean,
        authoringStore: NativeKotlinCustomAuthoringSourceFileStore? = null
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
                if(authoringStore != null) {
                    val source=(authoringStore.read(owner)
                        as? NativeAuthoringSourceRead.Found)?.source
                        ?: return@withOwnerTransaction NativeOwnerLoadReviewResult.Rejected(
                            NativeOwnerLoadReviewRefusal.GYM_SOURCE_UNAVAILABLE)
                    if(NativeKotlinApprovedGymCoherenceDomain.inspect(approval,source)
                        != NativeApprovedGymCoherenceResult.Matched)
                        return@withOwnerTransaction NativeOwnerLoadReviewResult.Rejected(
                            NativeOwnerLoadReviewRefusal.GYM_SOURCE_CHANGED)
                }
                when(val history=readFenced(owner,athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        NativeOwnerLoadReviewResult.HistoryRejected(history.reason)
                    is NativeKotlinMixedHistoryReadResult.Review ->
                        NativeOwnerLoadReviewResult.Review(
                            NativeKotlinCurrentLoadCoachReview.inspect(
                                approval,history.timeline,exerciseId,
                                history.timeline.manifestRevision,
                                maximumAuthorizedIncrease,readiness,
                                movementRestricted),
                            approval.storageRevision)
                }
            }
        }.getOrElse {
            NativeOwnerLoadReviewResult.Rejected(
                NativeOwnerLoadReviewRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * New Kotlin per-set rep prescription, read from the LATEST completed
     * exercise appearance in the real, full mixed-history ledger.
     *
     * No caller-supplied history or manually edited candidate is trusted.
     * Refuse already-issued workout identities to avoid changing targets of
     * an in-progress or finished session. Candidate is READ ONLY.
     */
    fun reviewCurrentNextSetTargets(
        owner:String,
        athlete:String,
        approvals:NativeKotlinApprovalFileAuthority,
        exerciseId:String,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        authoringStore:NativeKotlinCustomAuthoringSourceFileStore?=null
    ):NativeOwnerNextSetResult {
        fun reject(reason:NativeOwnerNextSetRefusal)=
            NativeOwnerNextSetResult.Rejected(reason)
        if(!id.matches(owner) || !id.matches(athlete) ||
            !id.matches(exerciseId))
            return reject(NativeOwnerNextSetRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete))
                    return@withOwnerTransaction reject(
                        NativeOwnerNextSetRefusal.NOT_AUTHORIZED)
                val approval=approvals.currentOwnedApproval(owner)
                    ?.takeIf {it.athleteId==athlete}
                    ?:return@withOwnerTransaction reject(
                        NativeOwnerNextSetRefusal.NO_CURRENT_APPROVAL)
                if(authoringStore!=null) {
                    val authored=(authoringStore.read(owner)
                        as? NativeAuthoringSourceRead.Found)?.source
                        ?:return@withOwnerTransaction reject(
                            NativeOwnerNextSetRefusal.GYM_SOURCE_UNAVAILABLE)
                    if(NativeKotlinApprovedGymCoherenceDomain.inspect(
                        approval,authored)!=NativeApprovedGymCoherenceResult.Matched)
                        return@withOwnerTransaction reject(
                            NativeOwnerNextSetRefusal.GYM_SOURCE_CHANGED)
                }
                // A grant may be CURRENT but its journal already issued.
                // Once issued, no history-derived target may overwrite it.
                val existing=composerStore.openStored(
                    owner,approval.grant.workoutId,
                    approvals::canAccessIssuedWorkout)
                if(existing != NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.NOT_ISSUED))
                    return@withOwnerTransaction reject(
                        NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT)
                val custom=customStore.open(owner,approval.grant.workoutId)
                if(custom != NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_ISSUED))
                    return@withOwnerTransaction reject(
                        NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT)
                return@withOwnerTransaction when(
                    val history=readFenced(owner,athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        NativeOwnerNextSetResult.HistoryRejected(history.reason)
                    is NativeKotlinMixedHistoryReadResult.Review ->
                        NativeOwnerNextSetResult.Review(
                            approval.storageRevision,
                            NativeKotlinCurrentRepPrescriptionDomain.inspect(
                                approval,history.timeline,exerciseId,
                                readiness,movementRestricted))
                }
            }
        }.getOrElse {
            reject(NativeOwnerNextSetRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Save an explicitly confirmed per-set prescription under the shared
     * owner fence. Reopen every journal and correction at write time. This
     * only replaces an UNISSUED current grant with a fresh grant; already
     * issued targets are immutable. Every submitted proposed rep is compared
     * to an independently calculated, full-workout preview.
     */
    fun acceptReviewedNextSetTargets(
        owner:String,
        athlete:String,
        approvals:NativeKotlinApprovalFileAuthority,
        intent:NativeKotlinAcceptedRepIntent,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        authoringStore:NativeKotlinCustomAuthoringSourceFileStore
    ):NativeKotlinRepAcceptanceResult {
        fun reject(reason:NativeKotlinRepAcceptanceRefusal)=
            NativeKotlinRepAcceptanceResult.Rejected(reason)
        val shortId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
        if(!id.matches(owner) || !id.matches(athlete) ||
            !id.matches(intent.exerciseId) ||
            !shortId.matches(intent.nextWorkoutId) ||
            !shortId.matches(intent.nextGrantId) ||
            !shortId.matches(intent.nextStorageReceiptId) ||
            !id.matches(intent.expectedCurrentWorkoutId) ||
            !id.matches(intent.expectedHistoricalWorkoutId) ||
            !id.matches(intent.expectedChangedSetKey) ||
            intent.expectedApprovalRevision !in 1..9_007_199_254_740_990L ||
            intent.expectedHistoryRevision !in 1..9_007_199_254_740_991L ||
            intent.expectedObservedReps.size !in 1..32 ||
            intent.expectedNewRepTargets.size!=intent.expectedObservedReps.size)
            return reject(NativeKotlinRepAcceptanceRefusal.INVALID_SCOPE)
        if(!intent.explicitlyAccepted)
            return reject(NativeKotlinRepAcceptanceRefusal.NOT_ACCEPTED)
        if(readiness==CoachReadiness.LOW || movementRestricted)
            return reject(NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete))
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.NOT_AUTHORIZED)
                val current=approvals.currentOwnedApproval(owner)
                    ?.takeIf {it.athleteId==athlete}
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.NO_CURRENT_APPROVAL)
                if(current.storageRevision!=intent.expectedApprovalRevision ||
                    current.grant.workoutId!=intent.expectedCurrentWorkoutId ||
                    current.acceptedRepTargets!=null)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.APPROVAL_CHANGED)
                val authored=(authoringStore.read(owner)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.SOURCE_NOT_VERIFIED)
                if(NativeKotlinApprovedGymCoherenceDomain.inspect(
                    current,authored)!=NativeApprovedGymCoherenceResult.Matched)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.GYM_CHANGED)
                if(intent.nextWorkoutId==current.grant.workoutId ||
                    intent.nextGrantId==current.grant.grantId ||
                    intent.nextStorageReceiptId==current.storageReceiptId)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.IDENTIFIER_ALREADY_USED)
                // Complete history is held under the lock throughout the
                // comparison, proposal generation and approval-file commit.
                val history=when(val read=readFenced(owner,athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        return@withOwnerTransaction reject(
                            NativeKotlinRepAcceptanceRefusal.SOURCE_NOT_VERIFIED)
                    is NativeKotlinMixedHistoryReadResult.Review ->read.timeline
                }
                if(history.manifestRevision!=intent.expectedHistoryRevision)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.HISTORY_CHANGED)
                if(history.completions.any {
                    it.manifest.workoutId==intent.nextWorkoutId ||
                        it.manifest.storedReceiptId==intent.nextStorageReceiptId
                })
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.IDENTIFIER_ALREADY_USED)
                fun unused(workoutId:String):Boolean {
                    val old=composerStore.openStored(
                        owner,workoutId,approvals::canAccessIssuedWorkout)
                    val custom=customStore.open(owner,workoutId)
                    return old==NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.NOT_ISSUED) &&
                        custom==NativeCustomJournalResult.Rejected(
                            NativeCustomJournalRefusal.NOT_ISSUED)
                }
                if(!unused(current.grant.workoutId))
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.ALREADY_ISSUED)
                if(!unused(intent.nextWorkoutId))
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.IDENTIFIER_ALREADY_USED)
                val original=NativeKotlinCurrentRepPrescriptionDomain.inspect(
                    current,history,intent.exerciseId,readiness,false)
                    as? NativeCurrentRepPrescriptionResult.Reviewed
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED)
                val evidence=original.proposal
                    as? AdaptiveSetPrescriptionResult.Candidate
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED)
                if(original.historyRevision!=intent.expectedHistoryRevision ||
                    original.priorWorkoutId!=intent.expectedHistoricalWorkoutId ||
                    evidence.editedSetKey!=intent.expectedChangedSetKey ||
                    evidence.observedReps!=intent.expectedObservedReps ||
                    evidence.newRepTargets!=intent.expectedNewRepTargets)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED)
                val next=current.copy(
                    grant=current.grant.copy(
                        grantId=intent.nextGrantId,workoutId=intent.nextWorkoutId),
                    storageReceiptId=intent.nextStorageReceiptId,
                    storageRevision=current.storageRevision+1,
                    acceptedRepTargets=NativeKotlinAcceptedRepTargets(
                        original.priorWorkoutId,original.historyRevision,
                        intent.exerciseId,evidence.editedSetKey,
                        Collections.unmodifiableList(evidence.observedReps.toList()),
                        Collections.unmodifiableList(evidence.newRepTargets.toList()),
                        readiness,evidence.decisionKind))
                val rederived=NativeKotlinCurrentRepPrescriptionDomain.inspect(
                    next,history,intent.exerciseId,readiness,false)
                    as? NativeCurrentRepPrescriptionResult.Reviewed
                val nextEvidence=rederived?.proposal
                    as? AdaptiveSetPrescriptionResult.Candidate
                if(nextEvidence==null ||
                    nextEvidence.historicalRevision!=original.historyRevision ||
                    nextEvidence.historicalWorkoutId!=original.priorWorkoutId ||
                    nextEvidence.workout.grantId!=next.grant.grantId ||
                    nextEvidence.workout.workoutId!=next.grant.workoutId ||
                    nextEvidence.newRepTargets!=evidence.newRepTargets ||
                    nextEvidence.observedReps!=evidence.observedReps ||
                    nextEvidence.decisionKind!=evidence.decisionKind)
                    return@withOwnerTransaction reject(
                        NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED)
                return@withOwnerTransaction when(val written=
                    approvals.publish(owner,current.storageRevision,next)) {
                    is NativeApprovalWriteResult.Committed ->
                        NativeKotlinRepAcceptanceResult.Committed(
                            written.revision,next.grant.workoutId,
                            original.historyRevision)
                    is NativeApprovalWriteResult.Rejected ->
                        reject(NativeKotlinRepAcceptanceRefusal.STORAGE_REJECTED)
                }
            }
        }.getOrElse {
            reject(NativeKotlinRepAcceptanceRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Critical issuance-time verifier. This is invoked TWICE by the
     * coordinator (before and inside the journal lock), with the same owner
     * fence held. Any history correction, gym/profile edit, account switch
     * or changed proposed reps invalidates the accepted target. Fail closed.
     */
    fun rederiveAcceptedRepSnapshot(
        owner:String,
        athlete:String,
        approvals:NativeKotlinApprovalFileAuthority,
        approval:NativeKotlinWorkoutApproval,
        authoringStore:NativeKotlinCustomAuthoringSourceFileStore
    ):KotlinStagedWorkout? {
        // Called while the coordinator may already hold the NEW workout's
        // OS journal lock. Do NOT call reviewCurrentNextSetTargets here:
        // that preview function must open the same journal to check whether
        // it was issued, which would deadlock/throw OverlappingFileLockException.
        // The issuer's own lock and issueLocked() establish absence/conflict.
        // This path rechecks ALL other source and corrected-history evidence.
        val proof=approval.acceptedRepTargets ?:return null
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete) ||
                    approval.ownerKey!=owner || approval.athleteId!=athlete ||
                    approvals.currentOwnedApproval(owner)!=approval)
                    return@withOwnerTransaction null
                val authored=(authoringStore.read(owner)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction null
                if(NativeKotlinApprovedGymCoherenceDomain.inspect(
                    approval,authored)!=NativeApprovedGymCoherenceResult.Matched)
                    return@withOwnerTransaction null
                val history=(readFenced(owner,athlete)
                    as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                    ?:return@withOwnerTransaction null
                if(history.manifestRevision!=proof.historyRevision)
                    return@withOwnerTransaction null
                val reviewed=NativeKotlinCurrentRepPrescriptionDomain.inspect(
                    approval,history,proof.exerciseId,
                    proof.reviewedReadiness,false)
                    as? NativeCurrentRepPrescriptionResult.Reviewed
                    ?:return@withOwnerTransaction null
                val result=reviewed.proposal
                    as? AdaptiveSetPrescriptionResult.Candidate
                    ?:return@withOwnerTransaction null
                if(reviewed.historyRevision!=proof.historyRevision ||
                    reviewed.priorWorkoutId!=proof.historicalWorkoutId ||
                    result.editedSetKey!=proof.changedSetKey ||
                    result.observedReps!=proof.observedReps ||
                    result.newRepTargets!=proof.newRepTargets ||
                    result.decisionKind!=proof.decisionKind ||
                    result.workout.workoutId!=approval.grant.workoutId ||
                    result.workout.grantId!=approval.grant.grantId)
                    return@withOwnerTransaction null
                result.workout
            }
        }.getOrNull()
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
        movementRestricted: Boolean,
        authoringStore: NativeKotlinCustomAuthoringSourceFileStore? = null
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
                if(authoringStore != null) {
                    val source=(authoringStore.read(owner)
                        as? NativeAuthoringSourceRead.Found)?.source
                        ?:return@withOwnerTransaction reject(
                            NativeKotlinCoachAcceptanceRefusal.GYM_SOURCE_UNAVAILABLE)
                    if(NativeKotlinApprovedGymCoherenceDomain.inspect(current,source)
                        != NativeApprovedGymCoherenceResult.Matched)
                        return@withOwnerTransaction reject(
                            NativeKotlinCoachAcceptanceRefusal.GYM_SOURCE_CHANGED)
                }
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
                // Custom workouts use their OWN immutable journal namespace.
                // A generated workout must not reuse an unfinished custom ID,
                // even though no completion manifest entry exists yet.
                val existingCustom=customStore.open(owner,intent.newWorkoutId)
                if(existingCustom != NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.NOT_ISSUED))
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
                    policies=coaching.candidatePolicies ?: current.policies,
                    storageReceiptId = intent.newStorageReceiptId,
                    storageRevision = nextRevision)
                val validation = KotlinWorkoutSnapshotDomain.stage(
                    KotlinWorkoutSnapshotRequest(
                        nextSource,nextSource,nextGrant,proposed,next.policies))
                if(validation !is KotlinWorkoutSnapshotResult.Staged)
                    return@withOwnerTransaction reject(
                        NativeKotlinCoachAcceptanceRefusal.NEW_WORKOUT_UNRESOLVED)
                // Recheck complete proposed program against trusted physical
                // stock: a larger new load cannot be authorized by a stale
                // copy of gym equipment embedded in the old approval.
                if(authoringStore != null) {
                    val source=(authoringStore.read(owner)
                        as? NativeAuthoringSourceRead.Found)?.source
                        ?:return@withOwnerTransaction reject(
                            NativeKotlinCoachAcceptanceRefusal.GYM_SOURCE_UNAVAILABLE)
                    if(NativeKotlinApprovedGymCoherenceDomain.inspect(next,source)
                        != NativeApprovedGymCoherenceResult.Matched)
                        return@withOwnerTransaction reject(
                            NativeKotlinCoachAcceptanceRefusal.GYM_SOURCE_CHANGED)
                }
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
                    entry, evidence, original.snapshot.purpose,
                    original.snapshot.dayIndex))
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
