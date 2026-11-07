package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * A human-reviewable exercise-level coaching interpretation.
 *
 * - Considers a sequence of real completed sessions, not the last heavy set.
 * - Does NOT treat missing RIR as actual failure or invent actual effort.
 * - Never alters issued workout targets, history or user-owned loads.
 * - Suggestions are provisional until the full native training engine,
 *   individual response model and longitudinal acceptance are certified.
 */
enum class CoachReadiness { LOW, NORMAL, STRONG }

enum class CoachDirection {
    REVIEW_MOVEMENT_RESTRICTION,
    PLAN_EASIER_SESSION,
    RESOLVE_UNFINISHED_SESSION,
    REVIEW_FAILED_ATTEMPT,
    REVIEW_WORKING_LOAD,
    BUILD_REPS_AT_CURRENT_LOAD,
    STABILIZE_SET_LOADING,
    REPEAT_SUCCESSFUL_PERFORMANCE,
    REVIEW_SMALL_INCREASE,
    VERIFY_LOADING_OPTIONS,
    CONFIRM_ZERO_LOAD_MEANING,
    CONTINUE_AT_HIGHEST_AVAILABLE_LOAD
}

enum class CoachConfidence { LIMITED, MODERATE, STRONG }

enum class CoachEvidenceFlag {
    REPS_BELOW_TARGET, SOME_SETS_BELOW_CEILING, DIFFERENT_SET_LOADS,
    COMPLETED_AT_CEILING, REPEATABLE_PERFORMANCE, REPORTED_NEAR_FAILURE,
    EFFORT_NOT_REPORTED, DEVICE_LOAD_NOT_VERIFIED, ACTUAL_SET_MISSING,
    FAILED_ATTEMPT, READINESS_LOW, USER_REPORTED_RESTRICTION,
    GOAL_RIR_ASSUMED
}

data class CoachExerciseContext(
    val readiness: CoachReadiness,
    /** An explicit user-reported restriction; not an inferred diagnosis. */
    val movementRestricted: Boolean,
    val availableLoads: VerifiedLoadStock?,
    val maximumStepForReview: BigDecimal,
    /** Explicitly established by native authored/issued per-set loading.
     * Never enable for a top/backoff or unverified user-edited session. */
    val verifiedPerSetLoading: Boolean = false
)

data class CoachExerciseReview(
    val direction: CoachDirection,
    val confidence: CoachConfidence,
    /** A possible next physical load, never an issued working-set target. */
    val nextLoadToReview: BigDecimal?,
    /** Avoid per-set banners: one short explanation for the exercise. */
    val explanation: String,
    val evidence: Set<CoachEvidenceFlag>,
    val sessionsExamined: Int,
    val observedWorkingLoad: BigDecimal?,
    /** Independent, set-specific suggestion. No mutation of issued targets. */
    val nextRepTargetsForReview: List<Int>? = null
) {
    init {
        require(nextRepTargetsForReview==null ||
            (direction==CoachDirection.BUILD_REPS_AT_CURRENT_LOAD &&
                nextLoadToReview==null))
        require(nextLoadToReview == null || direction == CoachDirection.REVIEW_SMALL_INCREASE)
        require(direction != CoachDirection.REVIEW_SMALL_INCREASE || nextLoadToReview != null)
        require(explanation.isNotBlank())
    }
}

object CoachExerciseDecisionDomain {
    private val zero=BigDecimal.ZERO
    private val maxStep=BigDecimal("100000")

    private fun normalized(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    private fun observedUniformLoad(workout: ProgressionWorkoutEvidence): BigDecimal? {
        val loads=workout.observations.filter {it.outcome==SetOutcome.COMPLETED}
            .map {checkNotNull(it.actualLoad).stripTrailingZeros()}.distinct()
        return if(loads.size==1) normalized(loads.single()) else null
    }

    /**
     * Recent sessions must be oldest to newest and belong to the same
     * exercise, unit, rep prescription and working-set structure.
     *
     * A full engine will separately audit timestamps, program revisions,
     * exercise variant compatibility and user approvals before using
     * this as a training prescription.
     */
    fun review(
        chronologicalSessions: List<ProgressionWorkoutEvidence>,
        context: CoachExerciseContext
    ): CoachExerciseReview {
        require(chronologicalSessions.size in 1..8)
        require(context.maximumStepForReview>zero && context.maximumStepForReview<=maxStep &&
            context.maximumStepForReview.stripTrailingZeros().scale()<=3)
        val last=chronologicalSessions.last()
        require(last.workoutId.isNotBlank() && last.exerciseId.isNotBlank())
        require(chronologicalSessions.map {it.workoutId}.distinct().size==
            chronologicalSessions.size)
        require(chronologicalSessions.all {
            it.workoutId.isNotBlank() && it.exerciseId==last.exerciseId &&
                it.unit==last.unit && it.targetReps==last.targetReps &&
                it.expectedWorkingSets==last.expectedWorkingSets
        })
        // This validates every observation, including older incomplete
        // sessions, before any coaching explanation can be emitted.
        val assessments=chronologicalSessions.map {
            DoubleProgressionEvidenceDomain.assess(
                it.targetReps,it.expectedWorkingSets,it.observations)
        }
        val latestFacts=assessments.last()
        val currentLoad=observedUniformLoad(last)
        val observedRir=last.observations.filter {
            it.outcome==SetOutcome.COMPLETED
        }.mapNotNull {it.actualRir}
        val missingRir=latestFacts.actualRirReportedOrdinals.size!=last.expectedWorkingSets
        val nearFailure=observedRir.any {it<=1}
        val latestEffort=last.observations.map(ProgressionEffortDomain::resolve)
        val nearLimit=latestEffort.any {it.rir!=null && it.rir<=1}

        val flags=linkedSetOf<CoachEvidenceFlag>()
        if(missingRir) flags+=CoachEvidenceFlag.EFFORT_NOT_REPORTED
        if(nearFailure) flags+=CoachEvidenceFlag.REPORTED_NEAR_FAILURE
        if(latestFacts.missingOrdinals.isNotEmpty() ||
            latestFacts.skippedOrdinals.isNotEmpty()) flags+=CoachEvidenceFlag.ACTUAL_SET_MISSING
        if(latestFacts.failedAttemptOrdinals.isNotEmpty())
            flags+=CoachEvidenceFlag.FAILED_ATTEMPT
        if(latestFacts.belowFloorOrdinals.isNotEmpty())
            flags+=CoachEvidenceFlag.REPS_BELOW_TARGET
        if(latestFacts.mixedActualLoads) flags+=CoachEvidenceFlag.DIFFERENT_SET_LOADS
        if(latestFacts.ceilingOrdinals.size<last.expectedWorkingSets)
            flags+=CoachEvidenceFlag.SOME_SETS_BELOW_CEILING
        else flags+=CoachEvidenceFlag.COMPLETED_AT_CEILING

        fun result(direction:CoachDirection,confidence:CoachConfidence,
            explanation:String,candidate:BigDecimal?=null,
            repTargets:List<Int>?=null,assumedCount:Int=0):CoachExerciseReview {
            if(assumedCount>0)flags+=CoachEvidenceFlag.GOAL_RIR_ASSUMED
            return CoachExerciseReview(direction,
                if(assumedCount>0)CoachConfidence.LIMITED else confidence,
                candidate,explanation+ProgressionEffortDomain.assumptionNote(assumedCount),
                flags.toSet(),chronologicalSessions.size,currentLoad,
                repTargets)
        }

        // Exercise restrictions and day readiness are separate from
        // performance failure; never mark a recovery substitution as
        // "could not complete targets."
        if(context.movementRestricted) {
            flags+=CoachEvidenceFlag.USER_REPORTED_RESTRICTION
            return result(CoachDirection.REVIEW_MOVEMENT_RESTRICTION,CoachConfidence.STRONG,
                "This movement is marked as restricted. Review a suitable substitution or a different session before training it.")
        }
        if(context.readiness==CoachReadiness.LOW) {
            flags+=CoachEvidenceFlag.READINESS_LOW
            return result(CoachDirection.PLAN_EASIER_SESSION,CoachConfidence.STRONG,
                "Readiness is low today. Review a lighter session or recovery work without treating it as missed progress.")
        }

        if(latestFacts.completedWorkingSets!=last.expectedWorkingSets) {
            val failed=latestFacts.failedAttemptOrdinals.isNotEmpty()
            return result(if(failed)CoachDirection.REVIEW_FAILED_ATTEMPT
                else CoachDirection.RESOLVE_UNFINISHED_SESSION,CoachConfidence.LIMITED,
                if(failed) "A failed attempt was recorded. Review the cause and the working load before progressing."
                else "Some sets were skipped or left unfinished. Keep their results distinct and review the session before changing this load.")
        }
        if(latestFacts.belowFloorOrdinals.isNotEmpty())
            return result(CoachDirection.REVIEW_WORKING_LOAD,CoachConfidence.STRONG,
                "Some sets fell below the prescribed rep range. Review the load and fatigue before adding weight.")
        if(latestFacts.mixedActualLoads) {
            // A verified per-set prescription is different from an athlete
            // unexpectedly editing weights during a straight-set workout.
            // Each ordinal uses its own observed effort and intended RIR.
            // No weight is raised or copied to other days/sets.
            if(context.verifiedPerSetLoading &&
                latestFacts.ceilingOrdinals.size<last.expectedWorkingSets) {
                val review=FatigueAwareRepTargetReviewDomain.assess(
                    last,context.availableLoads,
                    context.availableLoads?.basis ?: LoadBasis.EXERCISE_OVERRIDE,
                    context.readiness,context.movementRestricted,
                    verifiedPerSetLoading=true)
                if(review.candidateRepTargets!=null)
                    return result(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,
                        CoachConfidence.LIMITED,
                        "Review each set's reps at its intended RIR, keeping its own weight. These are estimates from the last workout; earlier work and actual rest can change later capacity.",
                        repTargets=review.candidateRepTargets,
                        assumedCount=review.assumedGoalRirSetCount)
                if(review.reason==FatigueRepReviewReason.EFFORT_REQUIRES_LOAD_REVIEW)
                    return result(CoachDirection.REVIEW_WORKING_LOAD,CoachConfidence.LIMITED,
                        "Review the set-specific loads and recovery: the reported effort suggests at least one set cannot maintain the rep range at its intended RIR.")
            }
            return result(CoachDirection.STABILIZE_SET_LOADING,CoachConfidence.LIMITED,
                "Working weights varied across the sets. Use the full set history to choose a repeatable load; the heaviest set alone is not a starting target.")
        }
        if(latestFacts.ceilingOrdinals.size!=last.expectedWorkingSets) {
            val repReview=FatigueAwareRepTargetReviewDomain.assess(
                last,context.availableLoads,
                context.availableLoads?.basis ?: LoadBasis.EXERCISE_OVERRIDE,
                context.readiness,context.movementRestricted)
            if(repReview.reason==FatigueRepReviewReason.EFFORT_REQUIRES_LOAD_REVIEW)
                return result(CoachDirection.REVIEW_WORKING_LOAD,CoachConfidence.LIMITED,
                    "The reported effort suggests this load cannot maintain the rep range at the intended RIR. Review the load and recovery.")
            return result(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,
                CoachConfidence.LIMITED,
                if(repReview.candidateRepTargets!=null)
                    "Review each set's reps at its intended RIR using that set's recorded performance. Stop at the intended effort if fewer reps are available. These estimates do not yet model changes in rest, earlier work or longer-term fatigue."
                else "Keep building quality reps at the current load. A set-specific change is not supported by this review yet.",
                repTargets=repReview.candidateRepTargets,
                assumedCount=if(repReview.candidateRepTargets!=null)repReview.assumedGoalRirSetCount else 0)
        }
        if(nearLimit)
            return result(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,
                if(nearFailure)CoachConfidence.MODERATE else CoachConfidence.LIMITED,
                if(nearFailure) "All sets reached the top of the rep range, but reported effort was very high. Repeat or recover before reviewing an increase."
                else "All sets reached the rep ceiling, but their assumed goal RIR leaves too little reserve for an increase.")

        // Look for the most recent comparable prior session, never cherry
        // pick an older success past intervening contradictory evidence.
        val priorIndex=chronologicalSessions.lastIndex-1
        val repeatable=priorIndex>=0 && run {
            val prior=chronologicalSessions[priorIndex]
            val facts=assessments[priorIndex]
            facts.completedWorkingSets==prior.expectedWorkingSets &&
                facts.ceilingOrdinals.size==prior.expectedWorkingSets &&
                !facts.mixedActualLoads && facts.belowFloorOrdinals.isEmpty() &&
                !prior.observations.any {
                    ProgressionEffortDomain.resolve(it).rir?.let {rir->rir<=1}==true
                } && observedUniformLoad(prior)?.compareTo(currentLoad)==0
        }
        if(!repeatable)
            return result(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,CoachConfidence.MODERATE,
                "This is a promising session. Repeat a comparable performance before reviewing a load increase.")
        // Preserve factual missing-RIR flags while allowing the ORIGINAL
        // goal as an explicitly disclosed progression assumption.
        val previousMissingRir=assessments[priorIndex].actualRirReportedOrdinals.size!=
            last.expectedWorkingSets
        if(previousMissingRir) flags+=CoachEvidenceFlag.EFFORT_NOT_REPORTED
        val comparedEffort=(chronologicalSessions[priorIndex].observations+
            last.observations).map(ProgressionEffortDomain::resolve)
        if(comparedEffort.any {it.rir==null})
            return result(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,CoachConfidence.LIMITED,
                "Your reps are promising, but some sets have neither actual RIR nor an original goal RIR. Review that effort context before increasing the load.")
        flags+=CoachEvidenceFlag.REPEATABLE_PERFORMANCE

        val load=currentLoad ?: return result(CoachDirection.STABILIZE_SET_LOADING,
            CoachConfidence.LIMITED,"Confirm a consistent working load before progression.")
        if(load.compareTo(zero)==0)
            return result(CoachDirection.CONFIRM_ZERO_LOAD_MEANING,CoachConfidence.LIMITED,
                "Confirm whether zero represents bodyweight, assistance, or another loading setup before changing resistance.")

        val feasibility=EquipmentLoadDomain.inspect(context.availableLoads,load,last.unit)
        if(feasibility !is LoadInspection.Confirmed || !feasibility.exact) {
            flags+=CoachEvidenceFlag.DEVICE_LOAD_NOT_VERIFIED
            return result(CoachDirection.VERIFY_LOADING_OPTIONS,CoachConfidence.LIMITED,
                "Your logged work is consistent, but the available equipment or increments are not confirmed. Verify the next usable load.")
        }
        val next=feasibility.nextAbove
            ?:return result(CoachDirection.CONTINUE_AT_HIGHEST_AVAILABLE_LOAD,
                CoachConfidence.MODERATE,
                "The current weight is the highest confirmed option. Continue building quality rather than inventing another increment.")
        if(next.subtract(load)>context.maximumStepForReview) {
            flags+=CoachEvidenceFlag.DEVICE_LOAD_NOT_VERIFIED
            return result(CoachDirection.VERIFY_LOADING_OPTIONS,CoachConfidence.LIMITED,
                "The next available weight exceeds your approved increment. Hold the current load or adjust your equipment options.")
        }
        val eligible=ConservativeProgressionReview.assess(
            chronologicalSessions[priorIndex],last,context.availableLoads,
            context.maximumStepForReview)
        if(!eligible.canOfferIncrease)
            return result(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,CoachConfidence.LIMITED,
                "The completed work has not earned a change to the original issued load. Keep the current load and review the full prescription.")
        return result(CoachDirection.REVIEW_SMALL_INCREASE,
            CoachConfidence.MODERATE,
            "The full working sets reached the rep target in consecutive sessions with sufficient effort reserve. Review a small, equipment-valid increase.",
            next,assumedCount=eligible.assumedGoalRirSetCount)
    }
}
