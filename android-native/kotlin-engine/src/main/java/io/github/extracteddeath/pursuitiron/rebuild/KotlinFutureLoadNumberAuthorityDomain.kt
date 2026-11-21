package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * The one physical-number acceptance gate for a proposed FUTURE working load
 * in generated and custom Kotlin plans. Evidence qualification stays with
 * the progression coach; workout targets, journals and edits stay unchanged.
 *
 * No unit conversion, inferred machine increment, fallback stock, rounding or
 * silent down-weighting is permitted. The caller must independently fence
 * athlete, program/day, physical machine, observations and permissions.
 */
enum class KotlinFutureLoadSource {
    EARNED_NEXT_INCREMENT,
    EARNED_ASSISTANCE_DECREMENT,
    RETURN_TO_DEMONSTRATED_LOAD
}

enum class KotlinFutureLoadRefusal {
    INVALID_NUMBER_OR_BUDGET,
    UNVERIFIED_UNIT_OR_BASIS,
    UNVERIFIED_ISSUED_LOAD,
    UNVERIFIED_CANDIDATE_LOAD,
    WRONG_CHANGE_DIRECTION,
    NOT_ADJACENT_VERIFIED_INCREMENT,
    EXCEEDS_AUTHORIZED_INCREMENT,
    UNSUPPORTED_ASSISTANCE_DIRECTION
}

data class KotlinFutureLoadFact(
    val issuedLoad:BigDecimal,
    val proposedLoad:BigDecimal,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val source:KotlinFutureLoadSource,
    val authorizedIncrease:BigDecimal
) {
    val mayChangeIssuedWorkout:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
    val maySaveFutureProgram:Boolean get()=false
}

sealed interface KotlinFutureLoadReview {
    data class Accepted(val fact:KotlinFutureLoadFact):KotlinFutureLoadReview
    data class Rejected(val reason:KotlinFutureLoadRefusal):KotlinFutureLoadReview
}

object KotlinFutureLoadNumberAuthorityDomain {
    private val zero=BigDecimal.ZERO
    private val minPrecision=BigDecimal("0.001")

    // Shared numeric envelope, not a second precision/ceiling table.
    // Authored stock, adjacency, directions and athlete consent remain here.
    private fun validLoad(value:BigDecimal):Boolean =
        KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value)
    private fun validIncrease(value:BigDecimal):Boolean =
        value>zero && KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value)
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun normalized(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    fun review(
        issuedLoad:BigDecimal,
        candidateLoad:BigDecimal,
        unit:LoadUnit,
        basis:LoadBasis,
        stock:VerifiedLoadStock?,
        maximumAuthorizedIncrease:BigDecimal,
        source:KotlinFutureLoadSource
    ):KotlinFutureLoadReview {
        fun no(reason:KotlinFutureLoadRefusal)=KotlinFutureLoadReview.Rejected(reason)
        if(!validLoad(issuedLoad) || !validLoad(candidateLoad) ||
            !validIncrease(maximumAuthorizedIncrease))
            return no(KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET)
        if(stock==null || stock.unit!=unit || stock.basis!=basis)
            return no(KotlinFutureLoadRefusal.UNVERIFIED_UNIT_OR_BASIS)
        val current=EquipmentLoadDomain.inspect(stock,issuedLoad,unit)
            as? LoadInspection.Confirmed
            ?:return no(KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD)
        if(!current.exact)
            return no(KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD)
        val candidate=EquipmentLoadDomain.inspect(stock,candidateLoad,unit)
            as? LoadInspection.Confirmed
            ?:return no(KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD)
        if(!candidate.exact)
            return no(KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD)

        when(source) {
            KotlinFutureLoadSource.EARNED_NEXT_INCREMENT -> {
                if(basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(candidateLoad<=issuedLoad)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                if(current.nextAbove==null || !same(current.nextAbove,candidateLoad))
                    return no(KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT)
                if(candidateLoad.subtract(issuedLoad)>maximumAuthorizedIncrease)
                    return no(KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
            }
            KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT -> {
                if(basis!=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(candidateLoad>=issuedLoad)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                // No invented minimum or negative counterweights; resolve the
                // actual PREVIOUS available station setting from verified stock.
                val before=EquipmentLoadDomain.inspect(
                    stock,issuedLoad.subtract(minPrecision),unit)
                    as? LoadInspection.Confirmed
                    ?:return no(KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD)
                if(before.atOrBelow==null ||
                    !same(before.atOrBelow,candidateLoad))
                    return no(KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT)
                if(issuedLoad.subtract(candidateLoad)>maximumAuthorizedIncrease)
                    return no(KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
            }
            KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD -> {
                if(basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(candidateLoad>=issuedLoad)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                // A return is intentionally NOT forced to one physical step:
                // its exact lower value must have been demonstrated in the
                // independently verified earlier workout. This domain only
                // checks physical numbers, not the historical evidence.
                // No future weight is changed without separate athlete review.
            }
        }
        return KotlinFutureLoadReview.Accepted(KotlinFutureLoadFact(
            normalized(issuedLoad),normalized(candidateLoad),unit,basis,
            source,normalized(maximumAuthorizedIncrease)))
    }
}
