package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Observed training week, ordered by weekIndex rather than a display date.
 * Actual fatigue/readiness is explicit user feedback, not inferred from
 * prescribed effort or presumed from a skipped workout.
 */
data class LoggedTrainingWeek(
    val weekIndex:Int,
    val weekId:String,
    val phase:TrainingPhase,
    val scheduledWorkoutIds:Set<String>,
    val completedWorkoutIds:Set<String>,
    val keyExerciseWorkouts:List<ProgressionWorkoutEvidence>,
    val reportedReadiness:CoachReadiness?,
    /** Null = unreported, false = explicitly no excessive fatigue. */
    val reportedExcessFatigue:Boolean?,
    val reportedMovementRestriction:Boolean
)

/** Thresholds are explicitly authored for the athlete; not universal claims. */
data class LongitudinalReviewPolicy(
    val minimumCompletedWorkoutsPerWeek:Int,
    val minimumKeyExerciseWorkoutsPerWeek:Int,
    val consecutiveStrainWeeksForLoadReview:Int,
    val consecutiveStrainAndRecoveryWeeksForDeloadReview:Int,
    val comparableWeeksForPlateauReview:Int,
    val successfulWeeksForPhaseReview:Int
)

data class LongitudinalReviewRequest(
    val chronologicalWeeks:List<LoggedTrainingWeek>,
    val keyExerciseIds:Set<String>,
    val policy:LongitudinalReviewPolicy,
    /** Explicitly requested next phase, never an automatic calendar rollover. */
    val requestedNextPhase:TrainingPhase?=null
)

data class ObservedWeekPattern(
    val weekIndex:Int,
    val weekId:String,
    val phase:TrainingPhase,
    val scheduledWorkouts:Int,
    val completedWorkouts:Int,
    val observedKeyWorkouts:Int,
    val completeKeyWorkouts:Int,
    val belowRepFloorWorkouts:Int,
    val nearFailureWorkouts:Int,
    val atRepCeilingWorkouts:Int,
    val workoutsWithMissingActualRir:Int,
    val exerciseIdsObserved:Set<String>,
    val sufficientForReview:Boolean,
    val explicitRecoveryConcern:Boolean,
    val observedTrainingStrain:Boolean
)

enum class BlockReviewDirection {
    REVIEW_REPORTED_RESTRICTION,
    REVIEW_DELOAD_WITH_ATHLETE,
    REVIEW_RECOVERY_BEFORE_TRAINING,
    REVIEW_TRAINING_LOAD,
    REVIEW_STALLED_EXERCISES,
    REVIEW_REQUESTED_PHASE_TRANSITION,
    CONTINUE_CURRENT_BLOCK,
    GATHER_MORE_TRAINING_EVIDENCE
}

data class LongitudinalCoachReview(
    val direction:BlockReviewDirection,
    val currentPhase:TrainingPhase,
    val nextPhaseForReview:TrainingPhase?,
    val weekPatterns:List<ObservedWeekPattern>,
    val comparablePlateauExerciseIds:Set<String>,
    val confidence:CoachConfidence,
    val explanation:String
) {
    init {
        require(explanation.isNotBlank())
        require((direction==BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION)==
            (nextPhaseForReview!=null))
    }
    /** No phase change, deload, set, or target is authorized by this result. */
    val canChangeExistingProgram:Boolean get()=false
}

object LongitudinalCoachDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,127}$")
    private const val MAX_WEEKS=16
    private const val MAX_EXERCISE_WORKOUTS=72

    private fun validate(request:LongitudinalReviewRequest) {
        val w=request.chronologicalWeeks
        val p=request.policy
        require(w.size in 1..MAX_WEEKS)
        require(request.keyExerciseIds.size in 1..16)
        require(request.keyExerciseIds.all {id.matches(it)})
        require(p.minimumCompletedWorkoutsPerWeek in 1..6)
        require(p.minimumKeyExerciseWorkoutsPerWeek in 1..18)
        require(p.consecutiveStrainWeeksForLoadReview in 2..8)
        require(p.consecutiveStrainAndRecoveryWeeksForDeloadReview in 2..8)
        require(p.comparableWeeksForPlateauReview in 3..8)
        require(p.successfulWeeksForPhaseReview in 2..8)
        require(w.map {it.weekIndex}==w.map {it.weekIndex}.distinct().sorted())
        require(w.map {it.weekId}.distinct().size==w.size)
        val globalWorkoutIds=hashSetOf<String>()
        w.forEach {week ->
            require(week.weekIndex in 0..10000 && id.matches(week.weekId))
            require(week.scheduledWorkoutIds.size in 1..6)
            require(week.scheduledWorkoutIds.all {id.matches(it)})
            require(week.completedWorkoutIds.all {
                it in week.scheduledWorkoutIds
            })
            require(week.scheduledWorkoutIds.all {globalWorkoutIds.add(it)})
            require(week.keyExerciseWorkouts.size<=MAX_EXERCISE_WORKOUTS)
            require(week.keyExerciseWorkouts.all {e ->
                e.exerciseId in request.keyExerciseIds &&
                    e.workoutId in week.completedWorkoutIds
            })
            require(week.keyExerciseWorkouts.map {
                it.workoutId to it.exerciseId
            }.distinct().size==week.keyExerciseWorkouts.size)
            week.keyExerciseWorkouts.forEach {e ->
                DoubleProgressionEvidenceDomain.assess(
                    e.targetReps,e.expectedWorkingSets,e.observations)
            }
        }
    }

    private fun pattern(week:LoggedTrainingWeek,
        request:LongitudinalReviewRequest):ObservedWeekPattern {
        val facts=week.keyExerciseWorkouts.map {e ->
            DoubleProgressionEvidenceDomain.assess(
                e.targetReps,e.expectedWorkingSets,e.observations)
        }
        val complete=facts.count {it.completedWorkingSets==it.expectedWorkingSets}
        val below=facts.count {it.belowFloorOrdinals.isNotEmpty()}
        val hard=facts.count {it.nearFailureOrdinals.isNotEmpty()}
        val ceilings=facts.count {it.signal==
            ProgressionEvidenceSignal.UNIFORM_LOAD_CEILING_FOR_REVIEW}
        val missingRir=facts.count {
            it.actualRirReportedOrdinals.size!=it.expectedWorkingSets
        }
        val observed=week.keyExerciseWorkouts.map {it.exerciseId}.toSet()
        val enough=week.completedWorkoutIds.size>=
                request.policy.minimumCompletedWorkoutsPerWeek &&
            week.keyExerciseWorkouts.size>=
                request.policy.minimumKeyExerciseWorkoutsPerWeek &&
            observed.containsAll(request.keyExerciseIds) &&
            complete==week.keyExerciseWorkouts.size &&
            week.keyExerciseWorkouts.isNotEmpty()
        return ObservedWeekPattern(
            week.weekIndex,week.weekId,week.phase,
            week.scheduledWorkoutIds.size,week.completedWorkoutIds.size,
            week.keyExerciseWorkouts.size,complete,below,hard,ceilings,
            missingRir,Collections.unmodifiableSet(observed),enough,
            week.reportedReadiness==CoachReadiness.LOW ||
                week.reportedExcessFatigue==true,
            below>0 || hard>0
        )
    }

    /** A gap in week indices or phase change breaks an evidence streak. */
    private fun recentContiguous(
        weeks:List<ObservedWeekPattern>):List<ObservedWeekPattern> {
        val last=weeks.last()
        val result=ArrayList<ObservedWeekPattern>()
        for(w in weeks.asReversed()) {
            if(w.phase!=last.phase || !w.sufficientForReview)break
            if(result.isNotEmpty() && w.weekIndex!=result.last().weekIndex-1)break
            result.add(w)
        }
        return result.asReversed()
    }

    /**
     * A plateau needs the same exercise, prescription, uniform physical
     * working load and set structure in EVERY compared week. No comparison
     * across phase/rep-band changes or multiple sessions cherry-picked.
     */
    private fun plateauIds(
        request:LongitudinalReviewRequest,
        count:Int
    ):Set<String> {
        val weeks=request.chronologicalWeeks.takeLast(count)
        if(weeks.size!=count || weeks.zipWithNext().any {
            it.second.weekIndex!=it.first.weekIndex+1 ||
                it.second.phase!=it.first.phase
        })return emptySet()
        val stalled=linkedSetOf<String>()
        for(exerciseId in request.keyExerciseIds.sorted()) {
            val sessions=weeks.map {week ->
                week.keyExerciseWorkouts.filter {it.exerciseId==exerciseId}
            }
            if(sessions.any {it.size!=1})continue
            val comparable=sessions.map {it.single()}
            val first=comparable.first()
            if(comparable.any {
                it.unit!=first.unit || it.targetReps!=first.targetReps ||
                    it.expectedWorkingSets!=first.expectedWorkingSets
            })continue
            val metrics=comparable.map {e ->
                val f=DoubleProgressionEvidenceDomain.assess(
                    e.targetReps,e.expectedWorkingSets,e.observations)
                if(f.completedWorkingSets!=e.expectedWorkingSets ||
                    f.mixedActualLoads || f.belowFloorOrdinals.isNotEmpty() ||
                    f.ceilingOrdinals.size==e.expectedWorkingSets) null
                else {
                    val loads=e.observations.mapNotNull {
                        it.actualLoad?.stripTrailingZeros()
                    }.distinct()
                    if(loads.size!=1)null
                    else {
                        val reportedRir=e.observations.mapNotNull {it.actualRir}
                        Triple(loads.single(),e.observations.sumOf {
                            checkNotNull(it.actualReps)
                        },if(reportedRir.size==e.expectedWorkingSets)
                            reportedRir.sum() else null)
                    }
                }
            }
            if(metrics.any {it==null})continue
            val safe=metrics.filterNotNull()
            val stableLoad=safe.all {it.first.compareTo(safe.first().first)==0}
            val notImproving=safe.zipWithNext().all { (prior,current) ->
                val moreReps=current.second>prior.second
                // Equal completed work at explicitly reported LOWER effort
                // (more actual reps in reserve) is also improvement.
                val priorActualRir=prior.third
                val currentActualRir=current.third
                val easierSameReps=current.second==prior.second &&
                    currentActualRir!=null && priorActualRir!=null &&
                    currentActualRir>priorActualRir
                !moreReps && !easierSameReps
            }
            if(stableLoad && notImproving)stalled.add(exerciseId)
        }
        return Collections.unmodifiableSet(stalled)
    }

    fun review(request:LongitudinalReviewRequest):LongitudinalCoachReview {
        validate(request)
        val summaries=request.chronologicalWeeks.map {pattern(it,request)}
        val latest=summaries.last()
        val policy=request.policy
        val contiguous=recentContiguous(summaries)
        val lastWeeks=contiguous.takeLast(
            policy.consecutiveStrainAndRecoveryWeeksForDeloadReview)
        val deloadReview=lastWeeks.size==
                policy.consecutiveStrainAndRecoveryWeeksForDeloadReview &&
            lastWeeks.all {it.observedTrainingStrain && it.explicitRecoveryConcern}
        val strainWeeks=contiguous.takeLast(policy.consecutiveStrainWeeksForLoadReview)
        val sustainedStrain=strainWeeks.size==
                policy.consecutiveStrainWeeksForLoadReview &&
            strainWeeks.all {it.observedTrainingStrain}
        val plateau=if(contiguous.size>=policy.comparableWeeksForPlateauReview)
            plateauIds(request,policy.comparableWeeksForPlateauReview)
        else emptySet()
        val success=contiguous.takeLast(policy.successfulWeeksForPhaseReview)
        val sustainedSuccess=success.size==policy.successfulWeeksForPhaseReview &&
            success.all {
                it.atRepCeilingWorkouts==it.observedKeyWorkouts &&
                    it.nearFailureWorkouts==0 && it.belowRepFloorWorkouts==0 &&
                    !it.explicitRecoveryConcern
            }
        val recentMissingRir=contiguous.takeLast(
            maxOf(policy.successfulWeeksForPhaseReview,
                policy.consecutiveStrainAndRecoveryWeeksForDeloadReview))
            .any {it.workoutsWithMissingActualRir>0}
        val restriction=request.chronologicalWeeks.last().reportedMovementRestriction
        val direction=when {
            restriction ->BlockReviewDirection.REVIEW_REPORTED_RESTRICTION
            deloadReview->BlockReviewDirection.REVIEW_DELOAD_WITH_ATHLETE
            !latest.sufficientForReview->
                BlockReviewDirection.GATHER_MORE_TRAINING_EVIDENCE
            latest.explicitRecoveryConcern->
                BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING
            sustainedStrain->BlockReviewDirection.REVIEW_TRAINING_LOAD
            plateau.isNotEmpty()->BlockReviewDirection.REVIEW_STALLED_EXERCISES
            request.requestedNextPhase!=null &&
                request.requestedNextPhase!=latest.phase && sustainedSuccess->
                BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION
            else->BlockReviewDirection.CONTINUE_CURRENT_BLOCK
        }
        val phase=if(direction==BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION)
            request.requestedNextPhase else null
        val message=when(direction) {
            BlockReviewDirection.REVIEW_REPORTED_RESTRICTION ->
                "A movement restriction was reported. Review suitable substitutions and the week before prescribing more work."
            BlockReviewDirection.REVIEW_DELOAD_WITH_ATHLETE ->
                "Consecutive well-documented weeks show difficult training together with reported recovery concerns. Review a deload, not an automatic volume cut."
            BlockReviewDirection.GATHER_MORE_TRAINING_EVIDENCE ->
                "Some sessions or key-lift observations are missing. Keep absent performance distinct from poor performance before changing the block."
            BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING ->
                "Recovery concerns were reported this week. Review readiness and session demands before progression."
            BlockReviewDirection.REVIEW_TRAINING_LOAD ->
                "Repeated well-documented difficult weeks warrant a review of load, rest, volume and exercise placement."
            BlockReviewDirection.REVIEW_STALLED_EXERCISES ->
                "Comparable sessions have not improved at a fixed load. Review the affected exercises, recovery and program structure."
            BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION ->
                "The requested phase transition has repeated successful training evidence. Review new phase constraints and obtain approval before changing the program."
            BlockReviewDirection.CONTINUE_CURRENT_BLOCK ->
                "Continue the current block while tracking performance and recovery; no justified block-level change is established."
        }
        return LongitudinalCoachReview(
            direction,latest.phase,phase,
            Collections.unmodifiableList(summaries),
            plateau,
            if(!latest.sufficientForReview || recentMissingRir)
                CoachConfidence.LIMITED
            else if(contiguous.size>=2)CoachConfidence.MODERATE
            else CoachConfidence.LIMITED,
            message)
    }
}
