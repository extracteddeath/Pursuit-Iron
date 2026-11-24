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
    UNSUPPORTED_ASSISTANCE_DIRECTION,
    NO_LOWER_ASSISTED_SETTING
}

/**
 * Physical-number evidence captured by the SAME acceptance pass that checks
 * the proposal. This is deliberately not a second policy or a new proof of
 * completed training. The exact neighboring settings come from the verified
 * implement, not a unit-level guessed increment or display rounding.
 *
 * A return to a demonstrated lower load can skip adjacent settings, so
 * its historical evidence must be established by the calling coach.
 */
enum class KotlinFutureLoadPhysicalReason {
    EARNED_ADJACENT_HIGHER_SETTING,
    EARNED_ADJACENT_LOWER_ASSISTANCE,
    VERIFIED_LOWER_SETTING_HISTORY_REQUIRED
}

data class KotlinFutureLoadPhysicalProof(
    val reason:KotlinFutureLoadPhysicalReason,
    val verifiedIssued:BigDecimal,
    val verifiedCandidate:BigDecimal,
    /** Exact neighbor of the issued setting, not an independently suggested load. */
    val nextHigher:BigDecimal?,
    val nextLower:BigDecimal?,
    val absoluteChange:BigDecimal
) {
    val requiresIndependentPerformanceEvidence:Boolean get()=true
    val requiresPreviouslyDemonstratedLoad:Boolean get()=
        reason==KotlinFutureLoadPhysicalReason.VERIFIED_LOWER_SETTING_HISTORY_REQUIRED
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

data class KotlinFutureLoadFact(
    val issuedLoad:BigDecimal,
    val proposedLoad:BigDecimal,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val source:KotlinFutureLoadSource,
    val authorizedIncrease:BigDecimal,
    /** Read-only physical receipt of the accepted number decision. */
    val physicalProof:KotlinFutureLoadPhysicalProof
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
        // Physical stock can contain a zero machine selector, but it is
        // NOT automatically a valid issued working weight. Only a basis
        // defined as zero ADDED load may issue zero. Future-load reviews
        // must not reauthorize a malformed current workout or silently
        // turn zero into a machine/cable/assistance progression.
        if(!KotlinSetNumberAuthorityDomain.validPrescribedLoad(issuedLoad,basis))
            return no(KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD)
        // The zero-assisted selector has its own explicit refusal below;
        // never present it as an earned move to unassisted bodyweight.
        val zeroAssistanceTerminal=
            basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT &&
            source==KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT &&
            candidateLoad.signum()==0
        if(!zeroAssistanceTerminal &&
            !KotlinSetNumberAuthorityDomain.validPrescribedLoad(candidateLoad,basis))
            return no(KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD)
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
                if(!basis.higherSettingIncreasesDifficulty)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(basis.compareDifficulty(candidateLoad,issuedLoad)<=0)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                if(current.nextHarder==null || !same(current.nextHarder!!,candidateLoad))
                    return no(KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT)
                if(candidateLoad.subtract(issuedLoad)>maximumAuthorizedIncrease)
                    return no(KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
            }
            KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT -> {
                if(basis.higherSettingIncreasesDifficulty)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(basis.compareDifficulty(candidateLoad,issuedLoad)<=0)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                // No invented minimum or negative counterweights; resolve the
                // actual PREVIOUS available station setting from verified stock.
                if(current.nextHarder==null || !same(current.nextHarder!!,candidateLoad))
                    return no(KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT)
                // A zero selector ends assisted work. Switching to unassisted
                // reps requires a separately prescribed movement and baseline.
                if(candidateLoad.signum()==0)
                    return no(KotlinFutureLoadRefusal.NO_LOWER_ASSISTED_SETTING)
                if(issuedLoad.subtract(candidateLoad)>maximumAuthorizedIncrease)
                    return no(KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
            }
            KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD -> {
                if(!basis.higherSettingIncreasesDifficulty)
                    return no(KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)
                if(basis.compareDifficulty(candidateLoad,issuedLoad)>=0)
                    return no(KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION)
                // A return is intentionally NOT forced to one physical step:
                // its exact lower value must have been demonstrated in the
                // independently verified earlier workout. This domain only
                // checks physical numbers, not the historical evidence.
                // No future weight is changed without separate athlete review.
            }
        }
        // Capture the actual physical inspection used above. Downstream
        // generated/custom coaches already forward this KotlinFutureLoadFact;
        // they must not recalculate a different neighbor for "Why this load?".
        val reason=when(source) {
            KotlinFutureLoadSource.EARNED_NEXT_INCREMENT ->
                KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING
            KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT ->
                KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_LOWER_ASSISTANCE
            KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD ->
                KotlinFutureLoadPhysicalReason.VERIFIED_LOWER_SETTING_HISTORY_REQUIRED
        }
        val proof=KotlinFutureLoadPhysicalProof(
            reason=reason,
            verifiedIssued=normalized(current.entered),
            verifiedCandidate=normalized(candidate.entered),
            nextHigher=current.nextAbove?.let(::normalized),
            nextLower=current.nextBelow?.let(::normalized),
            absoluteChange=normalized(candidateLoad.subtract(issuedLoad).abs()))
        return KotlinFutureLoadReview.Accepted(KotlinFutureLoadFact(
            normalized(issuedLoad),normalized(candidateLoad),unit,basis,
            source,normalized(maximumAuthorizedIncrease),proof))
    }
}
