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
 * A working row in the CURRENT workout editor, never a historical or
 * prescribed set. The caller excludes warmups and amplifier follow-ups.
 * Completed rows supply facts, but must never be changed by Leveling.
 */
data class CurrentWorkoutLevelingRow(
    val setKey:String,
    val load:BigDecimal?,
    val reps:Int?,
    val completed:Boolean,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val machineInstanceId:String?
)

sealed interface CurrentWorkoutLevelingReview {
    data class Ready(
        val rawMean:BigDecimal,
        val roundedLoad:BigDecimal,
        val averageReps:Int,
        val eligibleSetKeys:List<String>,
        val includedWorkingSets:Int
    ):CurrentWorkoutLevelingReview
    data class Unavailable(val reason:Reason):CurrentWorkoutLevelingReview
    enum class Reason {
        INSUFFICIENT_ENTERED_SETS, NO_RISING_WEIGHTS, NO_UNFINISHED_SETS,
        INVALID_ENTRY, MIXED_EQUIPMENT, UNVERIFIED_STOCK, NO_AVAILABLE_LOAD
    }
}

/**
 * Optional draft-prefill review for a previous ascending/descending DP session.
 * A mathematically close inventory value does NOT prove today's set capacity;
 * this domain never changes historical facts, issued targets or future plans.
 * Missing actual RIR cannot be invented as measured capacity.
 */
object DoubleProgressionLevelingDomain {
    /**
     * Web parity: average all positive, entered, same-station WORKING loads
     * and reps from TODAY. Offer the nearest physically verified load. A
     * rising pyramid (not an already-level or descending session) is required.
     * The returned keys cover only unfinished, positive-load working rows;
     * nothing is ever journaled, auto-completed or changed for a future week.
     *
     * A real inventory is essential: the unit alone cannot establish an
     * increment or a valid plate/machine value.
     */
    fun reviewCurrentWorkout(
        rows:List<CurrentWorkoutLevelingRow>,
        verifiedStock:VerifiedLoadStock?
    ):CurrentWorkoutLevelingReview {
        fun refuse(reason:CurrentWorkoutLevelingReview.Reason)=
            CurrentWorkoutLevelingReview.Unavailable(reason)
        if(rows.size !in 2..32 || rows.map{it.setKey}.distinct().size!=rows.size ||
            rows.any {it.setKey.isBlank() || it.load!=null &&
                (it.load<BigDecimal.ZERO || it.load>BigDecimal("100000") ||
                    it.load.stripTrailingZeros().scale()>3) ||
                it.reps!=null && it.reps !in 1..100})
            return refuse(CurrentWorkoutLevelingReview.Reason.INVALID_ENTRY)
        // Unweighted/blank rows and sub-sets are not part of the average.
        val working=rows.filter {it.load!=null && it.load>BigDecimal.ZERO}
        if(working.size<2)
            return refuse(CurrentWorkoutLevelingReview.Reason.INSUFFICIENT_ENTERED_SETS)
        if(working.any{it.reps==null || it.completed && it.reps<1})
            return refuse(CurrentWorkoutLevelingReview.Reason.INVALID_ENTRY)
        if(working.map{it.unit}.distinct().size!=1 ||
            working.map{it.basis}.distinct().size!=1 ||
            working.map{KotlinMachineAlternativesDomain.normalizeMachineId(
                it.machineInstanceId)}.distinct().size!=1)
            return refuse(CurrentWorkoutLevelingReview.Reason.MIXED_EQUIPMENT)
        val loads=working.map{checkNotNull(it.load)}
        if(loads.zipWithNext().none{(a,b)->b>a})
            return refuse(CurrentWorkoutLevelingReview.Reason.NO_RISING_WEIGHTS)
        val eligible=working.filterNot{it.completed}.map{it.setKey}
        if(eligible.isEmpty())
            return refuse(CurrentWorkoutLevelingReview.Reason.NO_UNFINISHED_SETS)
        val mean=loads.fold(BigDecimal.ZERO,BigDecimal::add)
            .divide(BigDecimal(loads.size),3,RoundingMode.HALF_UP)
        val reps=working.sumOf { checkNotNull(it.reps) }
        val averageReps=BigDecimal(reps).divide(
            BigDecimal(working.size),0,RoundingMode.HALF_UP).toInt()
        if(verifiedStock==null || verifiedStock.unit!=working.first().unit ||
            verifiedStock.basis!=working.first().basis)
            return refuse(CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK)
        val inspection=EquipmentLoadDomain.inspect(
            verifiedStock,mean,working.first().unit)
                as? LoadInspection.Confirmed
                ?:return refuse(CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK)
        val rounded=listOfNotNull(inspection.atOrBelow,inspection.nextAbove)
            .filter{it>BigDecimal.ZERO}
            .minWithOrNull(compareBy<BigDecimal>{it.subtract(mean).abs()}.thenByDescending{it})
                ?:return refuse(CurrentWorkoutLevelingReview.Reason.NO_AVAILABLE_LOAD)
        return CurrentWorkoutLevelingReview.Ready(
            mean.stripTrailingZeros(),rounded,averageReps,eligible,working.size)
    }

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
        if (verifiedStock != null &&
            (verifiedStock.unit != issued.first().unit ||
                verifiedStock.basis != issued.first().basis))
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
