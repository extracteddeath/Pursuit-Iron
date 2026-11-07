package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Materializes an actual candidate next-session set prescription, derived
 * from the FULL independently staged Kotlin program and a completed workout.
 * This is an immutable candidate: it has no signed grant, journal, approval,
 * or right to replace already issued targets. The native owner still must
 * authenticate the history record and its corrections before acceptance.
 *
 * Crucially, per-set rep targets come from OBSERVED performance, not inferred
 * from a generic 1RM or multiplied across every set. Physical weights, warmups,
 * active/rest seconds and purpose-specific effort remain unchanged.
 */
enum class AdaptiveRepDecisionKind {
    /** Frozen v1 receipt identities; new reviews use target-RIR v2. */
    EARNED_ONE_MORE_REP,
    REPEAT_PREVIOUSLY_ISSUED_TARGETS,
    TARGET_RIR_ALIGNED_REPS,
    REPEAT_TARGET_RIR_ALIGNED_REPS;

    val isRepeat: Boolean get() = this==REPEAT_PREVIOUSLY_ISSUED_TARGETS ||
        this==REPEAT_TARGET_RIR_ALIGNED_REPS
    val policyVersion: AdaptiveRepPolicyVersion get() = when(this) {
        EARNED_ONE_MORE_REP, REPEAT_PREVIOUSLY_ISSUED_TARGETS ->
            AdaptiveRepPolicyVersion.LEGACY_SINGLE_SET_V1
        TARGET_RIR_ALIGNED_REPS, REPEAT_TARGET_RIR_ALIGNED_REPS ->
            AdaptiveRepPolicyVersion.TARGET_RIR_V2
    }
}

enum class AdaptiveSetPrescriptionRefusal {
    STALE_SOURCE_OR_HISTORY,
    APPROVED_PROGRAM_UNRESOLVED,
    HISTORY_NOT_COMPARABLE,
    UNSUPPORTED_SET_SCHEME,
    INSUFFICIENT_REP_PROGRESS_EVIDENCE,
    CANDIDATE_REAUDIT_FAILED
}

sealed interface AdaptiveSetPrescriptionResult {
    data class Rejected(
        val reason: AdaptiveSetPrescriptionRefusal,
        val detail: String
    ) : AdaptiveSetPrescriptionResult
    data class Candidate(
        /** Immutable real per-set prescription, not issued/persisted. */
        val workout: KotlinStagedWorkout,
        val historicalWorkoutId: String,
        val historicalRevision: Long,
        val exerciseId: String,
        val editedSetKey: String,
        val observedReps: List<Int>,
        val newRepTargets: List<Int>,
        val decisionKind:AdaptiveRepDecisionKind=AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS,
        val assumedGoalRirSetCount:Int=0,
        val forecastLimitations:Set<RepForecastLimitation> = emptySet()
    ) : AdaptiveSetPrescriptionResult {
        val mayAutoApply: Boolean get() = false
        val mayIssueWorkout: Boolean get() = false
    }
}

data class AdaptiveSetPrescriptionRequest(
    val nextWorkout: KotlinWorkoutSnapshotRequest,
    /** Owner-authenticated historical workout SOURCE is required on commit. */
    val historicalSource: KotlinTrainingSourceRevision,
    val latestCompletedExercise: ProgressionWorkoutEvidence,
    val historyRevision: Long,
    val currentHistoryRevision: Long,
    val readiness: CoachReadiness,
    val movementRestricted: Boolean,
    /** From owner-verified real issued set bands, never screen input. */
    val previouslyIssuedExactReps: List<Int>?=null,
    /** Only the native receipt verifier selects v1 to replay an old approval. */
    val policyVersion:AdaptiveRepPolicyVersion=AdaptiveRepPolicyVersion.TARGET_RIR_V2
)

object KotlinAdaptiveSetPrescriptionDomain {
    private fun same(a: BigDecimal,b: BigDecimal) = a.compareTo(b)==0

    fun draft(request: AdaptiveSetPrescriptionRequest): AdaptiveSetPrescriptionResult {
        fun denied(reason: AdaptiveSetPrescriptionRefusal,detail: String) =
            AdaptiveSetPrescriptionResult.Rejected(reason,detail)
        val base=request.nextWorkout
        val history=request.latestCompletedExercise
        if(request.historyRevision<1 ||
            request.historyRevision!=request.currentHistoryRevision ||
            base.source!=base.currentSource ||
            base.source!=request.historicalSource ||
            base.grant.approvedSource!=base.source ||
            history.workoutId.isBlank() ||
            history.workoutId==base.grant.workoutId)
            return denied(AdaptiveSetPrescriptionRefusal.STALE_SOURCE_OR_HISTORY,
                "Source, history or next workout revision changed; recalculate from current native storage.")

        // Re-stage the ENTIRE approved program; no UI-provided candidate
        // workout, partially valid day or unverified exercise stock is trusted.
        val staged=KotlinWorkoutSnapshotDomain.stage(base)
        val original=(staged as? KotlinWorkoutSnapshotResult.Staged)?.snapshot
            ?:return denied(AdaptiveSetPrescriptionRefusal.APPROVED_PROGRAM_UNRESOLVED,
                "The currently approved week could not produce a fully verified session.")

        val option=base.approvedProgram.exercises.singleOrNull {
            it.exerciseId==history.exerciseId
        }
        val equipment=option?.equipment as? ComposerEquipment.Verified
        val chosen=original.targets.filter {
            it.exerciseId==history.exerciseId && it.type==DraftSetType.WORKING
        }.sortedBy { it.ordinal }
        if(equipment==null || chosen.isEmpty() ||
            chosen.size!=history.expectedWorkingSets ||
            history.targetReps.minimum<1 ||
            history.unit!=equipment.unit ||
            chosen.map {it.ordinal}!=(0 until chosen.size).toList() ||
            chosen.any {it.reps!=history.targetReps ||
                it.targetRir==null ||
                it.load !is DraftSetLoad.Verified})
            return denied(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
                "The latest exercise history does not match all approved next-session working sets.")

        // A recognized last-set backoff is eligible ONLY if its rep envelope
        // and issued RIR are the same as the main sets. Each prescribed
        // weight is independently re-staged and verified, never promoted to
        // the heaviest set. Other advanced schemes remain unchanged.
        val policy=CoachSessionPrescriptionDomain.selectedPolicy(
            base.policies,history.exerciseId,original.purpose,original.dayIndex)
        val backoff=policy?.scheme==SetLoadScheme.LAST_SET_BACKOFF
        if(policy==null || (
                policy.scheme!=SetLoadScheme.STRAIGHT && !backoff) ||
            (backoff && (
                policy.backoffReps!=policy.workingReps ||
                    policy.backoffRir!=policy.targetRir)) ||
            policy.warmups.isNotEmpty() ||
            original.targets.any {
                it.exerciseId==history.exerciseId && it.type==DraftSetType.WARMUP
            })
            return denied(AdaptiveSetPrescriptionRefusal.UNSUPPORTED_SET_SCHEME,
                "This load/effort scheme requires a separate approved set-specific prescription.")

        // Verify EVERY ordinal against the CURRENT full approved schedule,
        // including the separately authored last backoff. Uniform loading is
        // still required for normal STRAIGHT prescriptions.
        val issuedLoads=chosen.map {it.load as DraftSetLoad.Verified}
        val purposeLoad=issuedLoads.first().value
        val observed=history.observations.sortedBy {it.workingOrdinal}
        if(issuedLoads.any {
                it.basis!=equipment.inventory?.basis ||
                    it.unit!=history.unit
            } || (!backoff && issuedLoads.any{!same(it.value,purposeLoad)}) ||
            (backoff && (
                issuedLoads.size<2 ||
                    issuedLoads.dropLast(1).any{
                        !same(it.value,purposeLoad)
                    } || issuedLoads.last().value>=purposeLoad)) ||
            observed.size!=chosen.size ||
            observed.map {it.workingOrdinal}!=(0 until chosen.size).toList() ||
            observed.indices.any {index ->
                observed[index].issuedLoad==null ||
                    !same(checkNotNull(observed[index].issuedLoad),
                        issuedLoads[index].value)
            })
            return denied(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
                "Each original issued working-set load must match its new approved ordinal.")

        val proposed=runCatching {
            FatigueAwareRepTargetReviewDomain.assess(
                history,equipment.inventory,
                checkNotNull(equipment.inventory).basis,
                request.readiness,request.movementRestricted,
                verifiedPerSetLoading=backoff,policyVersion=request.policyVersion,
                targetRirByOrdinal=chosen.map {it.targetRir})
        }.getOrNull()
        if(proposed==null)
            return denied(AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                "The completed working sets could not support a comparable rep review.")
        val earlierTargets=request.previouslyIssuedExactReps
        if(earlierTargets!=null && (earlierTargets.size!=chosen.size ||
            earlierTargets.any {it !in history.targetReps.minimum..history.targetReps.maximum}))
            return denied(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
                "The recorded individualized target count or range differs from the approved plan.")

        val estimatedTargets=proposed.candidateRepTargets
        val legacy=request.policyVersion==AdaptiveRepPolicyVersion.LEGACY_SINGLE_SET_V1
        var kind=if(legacy)AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP
            else AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS
        val targets=if(earlierTargets!=null && (
            estimatedTargets==null || estimatedTargets==earlierTargets || (legacy && (
                earlierTargets.indices.any {checkNotNull(observed[it].actualReps)<earlierTargets[it]} ||
                estimatedTargets.indices.any {estimatedTargets[it]<earlierTargets[it]} ||
                estimatedTargets.indices.count {estimatedTargets[it]>earlierTargets[it]}!=1)))) {
            // A hold is a useful concrete prescription, not a reason to
            // discard already accepted exact targets. These reasons arise
            // only AFTER complete work, rep-floor, effort, readiness and
            // physical-loading checks. Every other refusal still wins.
            if(proposed.reason !in setOf(
                    FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW,
                    FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW,
                    FatigueRepReviewReason.NO_DEMONSTRATED_EFFORT_HEADROOM,
                    FatigueRepReviewReason.ALREADY_AT_REP_CEILING))
                return denied(AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                    "Recovery, effort or completed-set evidence does not support repeating these targets.")
            kind=if(legacy)AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS
                else AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS
            earlierTargets
        } else estimatedTargets ?:return denied(
            AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
            "No supported change at the intended RIR. Keep the approved targets.")
        if(targets.size!=chosen.size ||
            targets.any { it !in history.targetReps.minimum..history.targetReps.maximum } ||
            (!backoff &&
                proposed.observedLoad?.let{same(it,purposeLoad)}!=true) ||
            (backoff && proposed.observedSetLoads?.let{actual ->
                actual.size==issuedLoads.size &&
                    actual.indices.all {i ->
                        same(actual[i],issuedLoads[i].value)
                    }
            }!=true))
            return denied(AdaptiveSetPrescriptionRefusal.CANDIDATE_REAUDIT_FAILED,
                "Observed per-set loads or proposed reps no longer match the approved exercise.")

        val targetByKey=chosen.mapIndexed {i,set ->
            set.setKey to targets[i]
        }.toMap()
        if(targetByKey.size!=chosen.size)
            return denied(AdaptiveSetPrescriptionRefusal.CANDIDATE_REAUDIT_FAILED,
                "Set IDs are ambiguous; preserve the original prescription.")
        val changed=if(!kind.isRepeat)
            checkNotNull(proposed.proposedOrdinal)
        else checkNotNull(earlierTargets).indices.firstOrNull {
            checkNotNull(observed[it].actualReps)<earlierTargets[it]
        } ?:0
        val updated=original.targets.map {set ->
            val reps=targetByKey[set.setKey]
            if(reps==null) set else set.copy(reps=RepBand(reps,reps))
        }
        val changedRows=updated.zip(original.targets).filter { (next,old) ->
            next!=old
        }
        if(changedRows.size!=chosen.size ||
            changedRows.any { (next,old) ->
                next.load!=old.load || next.targetRir!=old.targetRir ||
                    next.activeSeconds!=old.activeSeconds ||
                    next.restSeconds!=old.restSeconds ||
                    next.type!=DraftSetType.WORKING
            } ||
            updated.size!=original.targets.size ||
            updated.map {it.setKey}!=original.targets.map {it.setKey} ||
            updated.count {it.reps.minimum==it.reps.maximum &&
                it.exerciseId==history.exerciseId &&
                it.type==DraftSetType.WORKING}!=chosen.size)
            return denied(AdaptiveSetPrescriptionRefusal.CANDIDATE_REAUDIT_FAILED,
                "The complete staged session differs beyond its explicitly reviewed rep targets.")

        return AdaptiveSetPrescriptionResult.Candidate(
            original.copy(targets=Collections.unmodifiableList(updated)),
            history.workoutId,request.historyRevision,history.exerciseId,
            chosen[changed].setKey,
            Collections.unmodifiableList(checkNotNull(proposed.observedReps)),
            Collections.unmodifiableList(targets),kind,proposed.assumedGoalRirSetCount,
            proposed.forecastLimitations
        )
    }
}
