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
    EARNED_ONE_MORE_REP,
    REPEAT_PREVIOUSLY_ISSUED_TARGETS
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
        val decisionKind:AdaptiveRepDecisionKind=AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP
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
    val previouslyIssuedExactReps: List<Int>?=null
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

        // Do not overwrite a warmup/top-set/backoff or a purpose-specific
        // scheme. Future supported variants need distinct, tested policies.
        val policies=base.policies.filter {
            it.exerciseId==history.exerciseId &&
                it.purpose==original.purpose
        }
        if(policies.size!=1 || policies.single().scheme!=SetLoadScheme.STRAIGHT ||
            policies.single().warmups.isNotEmpty() ||
            original.targets.any {
                it.exerciseId==history.exerciseId && it.type==DraftSetType.WARMUP
            })
            return denied(AdaptiveSetPrescriptionRefusal.UNSUPPORTED_SET_SCHEME,
                "Authored warmups, top/backoff and advanced loading require their own prescription.")

        val issuedLoads=chosen.map { it.load as DraftSetLoad.Verified }
        val observed=history.observations.sortedBy {it.workingOrdinal}
        if(issuedLoads.any {
            it.basis!=equipment.inventory?.basis ||
                it.unit!=history.unit ||
                !same(it.value,equipment.plannedLoad)
        } ||
            observed.size!=chosen.size ||
            observed.map {it.workingOrdinal}!=(0 until chosen.size).toList() ||
            observed.any { it.issuedLoad==null ||
                !same(checkNotNull(it.issuedLoad),equipment.plannedLoad) })
            return denied(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
                "Working-set load or ordinal differs from the full approved prescription.")

        val proposed=runCatching {
            FatigueAwareRepTargetReviewDomain.assess(
                history,equipment.inventory,
                checkNotNull(equipment.inventory).basis,
                request.readiness,request.movementRestricted)
        }.getOrNull()
        val targets=proposed?.candidateRepTargets
            ?:return denied(AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                "No demonstrated single-set rep increase. Keep the approved targets.")
        if(targets.size!=chosen.size ||
            targets.any { it !in history.targetReps.minimum..history.targetReps.maximum } ||
            proposed.observedLoad?.let {same(it,equipment.plannedLoad)} != true)
            return denied(AdaptiveSetPrescriptionRefusal.CANDIDATE_REAUDIT_FAILED,
                "Observed loads or proposed repetitions no longer match the approved exercise.")

        val earlierTargets=request.previouslyIssuedExactReps
        var kind=AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP
        if(earlierTargets!=null) {
            if(earlierTargets.size!=targets.size ||
                earlierTargets.any {it !in history.targetReps.minimum..history.targetReps.maximum})
                return denied(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
                    "The recorded individualized target count or range differs from the approved plan.")
            if(targets==earlierTargets) {
                // Same previously issued goals. This is a repeat target,
                // NOT a new coaching improvement or weight increase.
                kind=AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS
            } else {
                // A missed individualized set precludes increasing some
                // other set while calling the entire session an advance.
                if(earlierTargets.indices.any {
                    checkNotNull(history.observations.single {obs ->
                        obs.workingOrdinal==it
                    }.actualReps)<earlierTargets[it]
                } || targets.indices.any {targets[it]<earlierTargets[it]} ||
                    targets.indices.count {targets[it]>earlierTargets[it]}!=1)
                    return denied(AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                        "Repeat the previous individualized targets before earning another increase.")
            }
        }

        val targetByKey=chosen.mapIndexed {i,set ->
            set.setKey to targets[i]
        }.toMap()
        if(targetByKey.size!=chosen.size)
            return denied(AdaptiveSetPrescriptionRefusal.CANDIDATE_REAUDIT_FAILED,
                "Set IDs are ambiguous; preserve the original prescription.")
        val changed=checkNotNull(proposed.proposedOrdinal)
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
            Collections.unmodifiableList(targets),kind
        )
    }
}
