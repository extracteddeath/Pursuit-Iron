package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode

/** Optional double-progression leveling: prior actuals may prefill current UNSAVED editors only. */
data class LevelingSetFact(val load: BigDecimal, val reps: Int, val actualRir: Int?)
enum class LevelingRefusal {
    INCOMPLETE_OR_INVALID, BELOW_REP_FLOOR, ABOVE_REP_CEILING, INSUFFICIENT_EFFORT_EVIDENCE,
    UNVERIFIED_STOCK, NO_SUPPORTED_CANDIDATE, ALREADY_LEVEL
}
sealed interface LevelingReviewResult {
    data class Candidate(
        val averageObservedLoad: BigDecimal,
        val proposedStraightSetLoad: BigDecimal,
        val repsPerSet: Int,
        val setCount: Int,
        val note: String,
        val mayModifyIssuedWorkout: Boolean = false
    ) : LevelingReviewResult
    data class Rejected(val reason: LevelingRefusal) : LevelingReviewResult
}

/**
 * Optional draft-prefill review for a previous ascending/descending DP session.
 * A mathematically close inventory value does NOT prove today's set capacity;
 * this domain never changes historical facts, issued targets or future plans.
 * Missing actual RIR cannot be invented as measured capacity.
 */
object DoubleProgressionLevelingDomain {
    /**
     * Use a single authenticated previous workout as evidence for TODAY'S
     * editable entries only. Last references already scope to the athlete,
     * gym, program day and exercise ordinal. Check every slot again here:
     * missing/skipped sets, mixed visits, stations and load bases must refuse.
     * Returning a candidate NEVER writes journal entries or prescriptions.
     */
    fun reviewLastForCurrentWorkout(
        currentSets: List<Pair<WorkoutReferenceKey,DraftSetLoad>>,
        previous: WorkoutLastReferenceResult?,
        machineInstanceId: String?,
        repBand: RepBand,
        goalRir: Int,
        verifiedStock: VerifiedLoadStock?
    ): LevelingReviewResult {
        fun invalid() = LevelingReviewResult.Rejected(LevelingRefusal.INCOMPLETE_OR_INVALID)
        if (currentSets.size !in 2..12 ||
            currentSets.map { it.first }.distinct().size != currentSets.size ||
            currentSets.any { it.first.followUp })
            return invalid()
        val issued = currentSets.map { it.second as? DraftSetLoad.Verified
            ?: return invalid() }
        if (issued.map { it.unit }.distinct().size != 1 ||
            issued.map { it.basis }.distinct().size != 1)
            return invalid()
        val saved = previous as? WorkoutLastReferenceResult.Ready ?: return invalid()
        val requestedMachine = KotlinMachineAlternativesDomain.normalizeMachineId(machineInstanceId)
        val observed = currentSets.map { (key, load) ->
            val reference = saved.sets[key] ?: return invalid()
            val actual = WorkoutLastDraftDomain.reviewedActual(saved,key,load)
                ?: return invalid()
            if (KotlinMachineAlternativesDomain.normalizeMachineId(
                    actual.machineInstanceId) != requestedMachine ||
                actual.unit != issued.first().unit || actual.load == null)
                return invalid()
            reference to actual
        }
        // Per-machine Last may represent different past sessions for different
        // set ordinals. Never average those into a fictional single pyramid.
        if (observed.map { it.first.workoutId }.distinct().size != 1 ||
            observed.map { it.first.completionOrder }.distinct().size != 1)
            return invalid()
        if (verifiedStock != null && verifiedStock.unit != issued.first().unit)
            return LevelingReviewResult.Rejected(LevelingRefusal.UNVERIFIED_STOCK)
        return review(observed.map { (_,actual) ->
            LevelingSetFact(requireNotNull(actual.load),actual.reps,actual.actualRir)
        },repBand,goalRir,verifiedStock)
    }

    fun review(
        facts: List<LevelingSetFact>,
        repBand: RepBand,
        goalRir: Int,
        verifiedStock: VerifiedLoadStock?
    ): LevelingReviewResult {
        fun reject(reason: LevelingRefusal) = LevelingReviewResult.Rejected(reason)
        if (facts.size !in 2..12 || goalRir !in 0..10 ||
            facts.any { it.load <= BigDecimal.ZERO || it.reps <= 0 ||
                it.actualRir != null && it.actualRir !in 0..10 })
            return reject(LevelingRefusal.INCOMPLETE_OR_INVALID)
        if (repBand.minimum <= 0 || repBand.maximum < repBand.minimum)
            return reject(LevelingRefusal.INCOMPLETE_OR_INVALID)
        if (facts.any { it.reps < repBand.minimum })
            return reject(LevelingRefusal.BELOW_REP_FLOOR)
        if (facts.any { it.reps > repBand.maximum })
            return reject(LevelingRefusal.ABOVE_REP_CEILING)
        if (facts.any { it.actualRir != null && it.actualRir < goalRir })
            return reject(LevelingRefusal.INSUFFICIENT_EFFORT_EVIDENCE)
        if (facts.map { it.load.stripTrailingZeros() }.distinct().size == 1)
            return reject(LevelingRefusal.ALREADY_LEVEL)
        if (verifiedStock == null) return reject(LevelingRefusal.UNVERIFIED_STOCK)
        val mean = facts.fold(BigDecimal.ZERO) { acc, fact -> acc + fact.load }
            .divide(BigDecimal(facts.size), 3, RoundingMode.HALF_UP)
        val inspection = EquipmentLoadDomain.inspect(verifiedStock, mean, verifiedStock.unit)
            as? LoadInspection.Confirmed ?: return reject(LevelingRefusal.UNVERIFIED_STOCK)
        val choices = listOfNotNull(inspection.atOrBelow, inspection.nextAbove)
        val closest = choices.minWithOrNull(
            compareBy<BigDecimal> { it.subtract(mean).abs() }.thenBy { it }
        ) ?: return reject(LevelingRefusal.NO_SUPPORTED_CANDIDATE)
        // Never use the rounded mean as a guarantee: each set's capacity,
        // fatigue, and actual RIR still need the athlete's review.
        val commonReps = facts.minOf { it.reps }
        val missingRir = facts.count { it.actualRir == null }
        val effortNote = if (missingRir == 0) "" else
            " RIR was unreported for $missingRir set(s); their goal RIR is only an assumption, not measured capacity."
        return LevelingReviewResult.Candidate(
            mean.stripTrailingZeros(), closest, commonReps, facts.size,
            "Editable current-workout starting point only. Log the weight, reps and actual RIR you really perform; no historical, issued or future target has changed." + effortNote
        )
    }
}
