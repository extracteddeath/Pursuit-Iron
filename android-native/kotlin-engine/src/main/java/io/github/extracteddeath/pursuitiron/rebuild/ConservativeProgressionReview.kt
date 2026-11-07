package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Candidate progression for human review only: this is not a prescription,
 * not an automatic program edit, and never mutates workout observations.
 *
 * Two independent, comparable workouts must show every working set at the
 * target rep ceiling, the SAME physically confirmed load, and actually
 * reported RIR >= 2 or, when unreported, an explicitly identified original
 * goal-RIR assumption. This calculation never rewrites logged actual effort.
 *
 * This deliberately conservative first rule will later be complemented by
 * explicit fatigue, longitudinal evidence, individual variability and
 * per-exercise programming policies before engine ownership can switch.
 */
data class ProgressionWorkoutEvidence(
    val workoutId: String,
    val exerciseId: String,
    val unit: LoadUnit,
    val targetReps: RepBand,
    val expectedWorkingSets: Int,
    val observations: List<WorkingSetFact>
)

enum class IncreaseReviewReason {
    INCOMPLETE_SESSION,
    BELOW_REP_FLOOR,
    STILL_BUILDING_REPS,
    MIXED_ACTUAL_LOADS,
    NEEDS_SECOND_COMPARABLE_SESSION,
    ISSUED_LOAD_NOT_COMPLETED,
    ACTUAL_RIR_MISSING,
    TOO_CLOSE_TO_FAILURE,
    ZERO_LOAD_NEEDS_EXERCISE_SEMANTICS,
    CONFIRMED_INVENTORY_REQUIRED,
    CURRENT_LOAD_UNVERIFIED,
    NO_HIGHER_VERIFIED_LOAD,
    INCREMENT_EXCEEDS_USER_LIMIT,
    QUALIFIED_FOR_REVIEW
}

data class IncreaseReview(
    val reason: IncreaseReviewReason,
    val currentObservedLoad: BigDecimal?,
    val reviewCandidate: BigDecimal?,
    val previous: ProgressionEvidence,
    val latest: ProgressionEvidence,
    val assumedGoalRirSetCount: Int = 0
) {
    val canOfferIncrease: Boolean
        get() = reason == IncreaseReviewReason.QUALIFIED_FOR_REVIEW &&
            reviewCandidate != null

    init {
        require((reason == IncreaseReviewReason.QUALIFIED_FOR_REVIEW) ==
            (reviewCandidate != null))
    }
}

object ConservativeProgressionReview {
    private val zero=BigDecimal.ZERO
    private val maxAllowedStep=BigDecimal("100000")
    private val minimumProgressionRir=2

    private fun onlyObservedLoad(workout: ProgressionWorkoutEvidence): BigDecimal? {
        val actual=workout.observations.filter { it.outcome == SetOutcome.COMPLETED }
            .mapNotNull { it.actualLoad?.stripTrailingZeros() }
            .distinct()
        return if(actual.size==1) actual.single() else null
    }

    private fun reviewedEvidence(workout: ProgressionWorkoutEvidence): ProgressionEvidence =
        DoubleProgressionEvidenceDomain.assess(workout.targetReps,
            workout.expectedWorkingSets,workout.observations)

    /** An explicit, user-approved maximum per-session step, never guessed. */
    fun assess(
        previous: ProgressionWorkoutEvidence,
        latest: ProgressionWorkoutEvidence,
        verifiedStock: VerifiedLoadStock?,
        maximumAllowedStep: BigDecimal
    ): IncreaseReview {
        require(previous.workoutId.isNotBlank() && latest.workoutId.isNotBlank() &&
            previous.workoutId != latest.workoutId)
        require(previous.exerciseId.isNotBlank() && previous.exerciseId == latest.exerciseId)
        require(previous.unit == latest.unit &&
            previous.targetReps == latest.targetReps &&
            previous.expectedWorkingSets == latest.expectedWorkingSets)
        require(maximumAllowedStep > zero && maximumAllowedStep <= maxAllowedStep &&
            maximumAllowedStep.stripTrailingZeros().scale() <= 3)

        val previousFacts=reviewedEvidence(previous)
        val latestFacts=reviewedEvidence(latest)
        val effort=(previous.observations+latest.observations)
            .map(ProgressionEffortDomain::resolve)
        val currentLoad=onlyObservedLoad(latest)
        fun result(reason: IncreaseReviewReason,candidate: BigDecimal?=null)=
            IncreaseReview(reason,currentLoad?.let {
                BigDecimal(it.stripTrailingZeros().toPlainString())
            },candidate,previousFacts,latestFacts,effort.count {it.assumed})

        // Latest failure has priority over earlier "success" and never triggers
        // an automatic weight reduction or promotion to the heaviest observed set.
        if(latestFacts.completedWorkingSets!=latest.expectedWorkingSets)
            return result(IncreaseReviewReason.INCOMPLETE_SESSION)
        if(latestFacts.belowFloorOrdinals.isNotEmpty())
            return result(IncreaseReviewReason.BELOW_REP_FLOOR)
        if(latestFacts.ceilingOrdinals.size!=latest.expectedWorkingSets)
            return result(IncreaseReviewReason.STILL_BUILDING_REPS)
        if(latestFacts.mixedActualLoads)
            return result(IncreaseReviewReason.MIXED_ACTUAL_LOADS)

        if(previousFacts.completedWorkingSets!=previous.expectedWorkingSets ||
            previousFacts.belowFloorOrdinals.isNotEmpty() ||
            previousFacts.ceilingOrdinals.size!=previous.expectedWorkingSets ||
            previousFacts.mixedActualLoads ||
            onlyObservedLoad(previous)?.compareTo(currentLoad) != 0)
            return result(IncreaseReviewReason.NEEDS_SECOND_COMPARABLE_SESSION)

        // The original issued goal may guide progression as an assumption.
        // Actual reports always override it, including explicit 0/1 RIR.
        if(effort.any {it.rir==null})
            return result(IncreaseReviewReason.ACTUAL_RIR_MISSING)
        if(effort.any {checkNotNull(it.rir)<minimumProgressionRir})
            return result(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE)

        val observed=checkNotNull(currentLoad)
        if(observed.compareTo(zero)==0)
            return result(IncreaseReviewReason.ZERO_LOAD_NEEDS_EXERCISE_SEMANTICS)

        // A lifter may deliberately use less (or more) than the session's
        // target. Keep the actual fact, but do not call this proof that the
        // ISSUED straight-set weight has been mastered over two sessions.
        // Recovery/return-to-demonstrated-load is a separate reviewed path.
        if(listOf(previous,latest).any { session ->
            session.observations.any {set->
                set.outcome==SetOutcome.COMPLETED &&
                    (set.issuedLoad==null || set.actualLoad==null ||
                        set.actualLoad.compareTo(set.issuedLoad)!=0)
            }
        })
            return result(IncreaseReviewReason.ISSUED_LOAD_NOT_COMPLETED)

        val inspected=EquipmentLoadDomain.inspect(verifiedStock,observed,latest.unit)
        if(inspected !is LoadInspection.Confirmed)
            return result(IncreaseReviewReason.CONFIRMED_INVENTORY_REQUIRED)
        if(!inspected.exact)
            return result(IncreaseReviewReason.CURRENT_LOAD_UNVERIFIED)
        val candidate=inspected.nextAbove ?: return result(IncreaseReviewReason.NO_HIGHER_VERIFIED_LOAD)
        if(candidate.subtract(observed)>maximumAllowedStep)
            return result(IncreaseReviewReason.INCREMENT_EXCEEDS_USER_LIMIT)

        // This is only an optional review candidate. No workout, program,
        // planned set, or user-entered value has changed.
        return result(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,candidate)
    }
}
