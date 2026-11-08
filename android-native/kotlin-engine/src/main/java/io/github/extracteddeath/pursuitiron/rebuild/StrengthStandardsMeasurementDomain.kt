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

    /** Epley estimate for 1–10 real reps; do not extrapolate very high reps. */
    fun epley(load:BigDecimal,reps:Int):BigDecimal? {
        if(reps !in 1..10 || load<=BigDecimal.ZERO ||
            load>BigDecimal("100000"))return null
        if(reps==1)return load
        return load.multiply(BigDecimal.valueOf((30+reps).toLong()))
            .divide(BigDecimal("30"),4,RoundingMode.HALF_UP)
    }

    fun relativeStrength(
        oneRepEstimate:BigDecimal,bodyweight:BigDecimal
    ):BigDecimal? {
        if(oneRepEstimate<=BigDecimal.ZERO ||
            bodyweight<=BigDecimal.ZERO)return null
        return oneRepEstimate.divide(bodyweight,5,RoundingMode.HALF_UP)
    }
}
