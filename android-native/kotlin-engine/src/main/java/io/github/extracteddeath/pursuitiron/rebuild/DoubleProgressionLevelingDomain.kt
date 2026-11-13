package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode

/** Optional double-progression leveling: historical actuals never become issued loads. */
data class LevelingSetFact(val load: BigDecimal, val reps: Int, val actualRir: Int?)
enum class LevelingRefusal {
    INCOMPLETE_OR_INVALID, BELOW_REP_FLOOR, INSUFFICIENT_EFFORT_EVIDENCE,
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
 * Review-only leveling for an accidentally ascending/descending DP session.
 * A mathematically close inventory value is NOT proof of future all-set
 * capacity. The athlete must inspect and explicitly accept a future plan edit.
 * Missing actual RIR cannot silently be fabricated as measured capacity.
 */
object DoubleProgressionLevelingDomain {
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
        if (facts.any { it.reps < repBand.minimum || it.reps > repBand.maximum })
            return reject(LevelingRefusal.BELOW_REP_FLOOR)
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
            "Optional leveling only. Confirm all-set rep/RIR feasibility and approve a future straight-set edit; no logged or issued load was changed." + effortNote
        )
    }
}
