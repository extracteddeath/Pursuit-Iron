package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Read-only performance-display measurements for Strength Standards.
 *
 * Neither an estimated 1RM nor a bodyweight ratio is a tested maximum,
 * a progression prescription, or a certified PR. Uses only actual completed
 * working sets supplied by the separately verified history owner.
 */
object StrengthStandardsMeasurementDomain {
    // The international avoirdupois pound is exactly 0.45359237 kg.
    private val kilogramsPerPound=BigDecimal("0.45359237")

    fun convert(
        value:BigDecimal,from:LoadUnit,to:LoadUnit
    ):BigDecimal {
        require(value>=BigDecimal.ZERO)
        return when {
            from==to -> value
            from==LoadUnit.LB && to==LoadUnit.KG ->
                value.multiply(kilogramsPerPound)
            from==LoadUnit.KG && to==LoadUnit.LB ->
                value.divide(kilogramsPerPound,8,RoundingMode.HALF_UP)
            else -> error("Unrecognized weight units")
        }
    }

    /** An external added-load observation alone is not the total load
     * lifted by a weighted bodyweight movement. Until a separately
     * reviewed model includes the athlete's mass, do not infer a max or
     * derive percentage prescriptions from that number. Ordinary earned
     * added-load progression and factual records remain available. */
    fun supportsMaxBasedPrescription(exerciseId:String,basis:LoadBasis):Boolean =
        exerciseId!="weighted-pullup" && basis!=LoadBasis.ASSISTANCE_COUNTERWEIGHT

    val estimateRepRange = 1..10

    /** Epley estimate for 1–10 real reps; do not extrapolate very high reps. */
    fun epley(load:BigDecimal,reps:Int):BigDecimal? {
        if(reps !in estimateRepRange || load<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadRange(load))return null
        if(reps==1)return load
        return epleyAtRepCount(load,reps,4)
    }

    /** Effort-adjusted training estimate, not a tested max. Matches the
     * current web's bounded Epley arithmetic (up to 15 reps to failure).
     * The caller must resolve reported/assumed goal effort before calling. */
    fun effortAdjustedEpley(load:BigDecimal,performedReps:Int,reserve:Int):BigDecimal? {
        if(performedReps !in 1..15 || !KotlinSetNumberAuthorityDomain.validRir(reserve) ||
            performedReps+reserve>15 || load<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(load))return null
        return epleyAtRepCount(load,performedReps+reserve,1)
    }

    private fun epleyAtRepCount(load:BigDecimal,reps:Int,scale:Int):BigDecimal =
        load.multiply(BigDecimal.valueOf((30+reps).toLong()))
            .divide(BigDecimal("30"),scale,RoundingMode.HALF_UP)

    fun relativeStrength(
        oneRepEstimate:BigDecimal,bodyweight:BigDecimal
    ):BigDecimal? {
        if(oneRepEstimate<=BigDecimal.ZERO ||
            bodyweight<=BigDecimal.ZERO)return null
        return oneRepEstimate.divide(bodyweight,5,RoundingMode.HALF_UP)
    }
}
