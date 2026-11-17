package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Source-linked nominal resistance-band loading for reviewed, repetition-based
 * exercises. A printed band-tension rating is NOT a constant measured external
 * load or equivalent to dumbbell, cable or barbell mass. It only identifies a
 * physically owned band setting at this particular gym.
 *
 * Existing Kotlin numeric load fields cannot safely represent bodyweight plus
 * assisted/resisted bands, arbitrary time holds, distance carries or band
 * tension without a manufacturer's nominal rating. Those cases remain out of
 * this tranche. Do not silently fall back to bodyweight or a continuous stack.
 */
object RatedResistanceBandDomain {
    const val EQUIPMENT_ID = "rated-resistance-band-set"

    private val reviewedIds = setOf(
        "band_lateral_raise", "band-curl", "banded-tib-raise",
        "band-ulnar-dev", "lateral-walk", "band-leg-curl",
        "band-pulldown", "rear-band-pull-apart", "band-good-morning",
        "band-hip-thrust", "band-adduction", "band-abduction",
        "bands-face-pull", "bands-squat", "bands-tricep-ext",
        "bands-chest-press"
    )

    fun reviewedExerciseIds():Set<String> = reviewedIds.toSet()

    fun isRatedBandExercise(exerciseId:String):Boolean = exerciseId in reviewedIds

    /**
     * Only discrete, athlete-confirmed, positive manufacturer-rated tension
     * values qualify. A dynamically generated stack or guessed increment
     * cannot become an authoritative band load.
     */
    fun confirmedDiscreteStock(equipmentId:String?,stock:VerifiedLoadStock?):Boolean {
        if(equipmentId!=EQUIPMENT_ID) return true
        if(stock !is VerifiedLoadStock.Totals ||
            stock.basis!=LoadBasis.EXERCISE_OVERRIDE ||
            stock.loads.isEmpty() || stock.loads.size>250)
            return false
        val ratings=stock.loads
        return ratings.all { value ->
            value>BigDecimal.ZERO && value<=BigDecimal("2000") &&
                value.stripTrailingZeros().scale()<=3
        } && ratings.map { it.stripTrailingZeros() }.distinct().size==ratings.size
    }

    fun validForReviewedExercise(exerciseId:String,stock:VerifiedLoadStock?):Boolean =
        !isRatedBandExercise(exerciseId) ||
            confirmedDiscreteStock(EQUIPMENT_ID,stock)
}
