package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Durable source intent, NOT authority to issue a named protocol. The
 * future owner-fenced path must reconstruct the complete recipe from the
 * confirmed reference, signed week/day/slot and current verified stock.
 * A rep-out is an explicit target type, not an arbitrary rep band or an
 * amplifier. The exact minimum rep target remains on CustomAuthoredSet.
 */
data class CustomPercentageSetIntent(
    val scheme: NamedPercentageScheme,
    val tier: PercentageTier,
    val dayRole: PercentageDayRole,
    val weekInBlock: Int,
    val blockWeeks: Int,
    val sourceOrdinal: Int,
    val sourceSetCount: Int,
    /** For GZCLP this is the signed tier working load, not a training max. */
    val referenceLoad: BigDecimal,
    val sourcePercent: BigDecimal,
    val effectivePercent: BigDecimal,
    /** Only GZCLP has a history-driven stage. */
    val tierStage: Int?,
    val repOut: Boolean,
    val recovery: Boolean
) {
    /** Structural transport validation; never substitutes for policy replay. */
    fun validForSet(set: CustomAuthoredSet): Boolean {
        val load=set.load as? DraftSetLoad.Verified ?:return false
        val target=set.exactRepTarget ?:return false
        if(set.type!=DraftSetType.WORKING || set.amplifier!=null ||
            load.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
            !KotlinSetNumberAuthorityDomain.validPrescribedLoad(load.value,load.basis) ||
            target!=set.reps.minimum || target!=set.reps.maximum ||
            weekInBlock !in 1..52 || blockWeeks !in 1..52 ||
            weekInBlock>blockWeeks && !recovery ||
            sourceSetCount !in 1..20 || sourceOrdinal !in 1..sourceSetCount ||
            referenceLoad<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(referenceLoad) ||
            sourcePercent<=BigDecimal.ZERO || sourcePercent>BigDecimal("100") ||
            sourcePercent.stripTrailingZeros().scale()>3 ||
            effectivePercent<=BigDecimal.ZERO || effectivePercent>BigDecimal("300") ||
            effectivePercent.stripTrailingZeros().scale()>8 ||
            recovery && repOut)
            return false
        if((scheme==NamedPercentageScheme.TEXAS)!=(dayRole!=PercentageDayRole.STANDARD))
            return false
        if(scheme==NamedPercentageScheme.GZCLP) {
            if(tierStage==null || tierStage !in 0..2 ||
                sourcePercent.compareTo(BigDecimal("100"))!=0 ||
                effectivePercent.compareTo(if(recovery)BigDecimal("60") else BigDecimal("100"))!=0)
                return false
        } else if(tierStage!=null)return false
        val raw=referenceLoad.multiply(effectivePercent).divide(BigDecimal("100"))
        return load.value<=raw
    }
}
