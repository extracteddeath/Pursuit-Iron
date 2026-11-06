package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Future custom-cycle straight-set LOAD amendment, never an issued target.
 * One authored slot, one approved training weekday and ONLY working sets.
 * No change to reps, RIR, warmups, amplifiers, variants or the source cycle.
 */
data class CustomNextLoadAmendment(
    val slotId:String,
    val workingSetKeys:List<String>,
    val previousLoad:BigDecimal,
    val proposedLoad:BigDecimal,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val amended:CustomWorkoutDaySnapshot
) {
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
}
enum class CustomNextLoadAmendmentRefusal {
    NOT_EARNED, UNRELATED_SLOT, NON_STRAIGHT_SETS,
    DIFFERENT_PLANNED_LOAD, UNVERIFIED_EQUIPMENT, INVALID_INCREMENT
}
sealed interface CustomNextLoadAmendmentResult {
    data class Proposed(val amendment:CustomNextLoadAmendment):
        CustomNextLoadAmendmentResult
    data class Rejected(val reason:CustomNextLoadAmendmentRefusal):
        CustomNextLoadAmendmentResult
}
object CustomNextLoadAmendmentDomain {
    private val zero=BigDecimal.ZERO
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun normal(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    fun propose(
        original:CustomWorkoutDaySnapshot,slotId:String,
        reviewed:CustomNextWorkoutCoachingReview,
        stock:VerifiedLoadStock?,
        maximumAuthorizedIncrease:BigDecimal
    ):CustomNextLoadAmendmentResult {
        fun no(reason:CustomNextLoadAmendmentRefusal)=
            CustomNextLoadAmendmentResult.Rejected(reason)
        if(reviewed.status!=CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW ||
            reviewed.latestWorkoutId==null ||
            reviewed.comparedWorkoutIds.size!=2 ||
            reviewed.comparedWorkoutIds.distinct().size!=2 ||
            reviewed.loadReviewCandidate==null ||
            reviewed.observedStraightSetLoad==null)
            return no(CustomNextLoadAmendmentRefusal.NOT_EARNED)
        val slot=original.slots.singleOrNull{it.slotId==slotId}
            ?:return no(CustomNextLoadAmendmentRefusal.UNRELATED_SLOT)
        val sets=slot.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val verified=sets.mapNotNull{it.prescription.load as? DraftSetLoad.Verified}
        if(sets.isEmpty() || sets.size>32 ||
            verified.size!=sets.size ||
            sets.any{it.prescription.amplifier!=null} ||
            verified.any{it.unit!=verified.first().unit ||
                it.basis!=verified.first().basis ||
                !same(it.value,verified.first().value)} ||
            reviewed.nextSetSuggestions.map{it.setKey}!=sets.map{it.setKey} ||
            reviewed.nextSetSuggestions.any{it.changed})
            return no(CustomNextLoadAmendmentRefusal.NON_STRAIGHT_SETS)
        val current=verified.first()
        if(!same(current.value,reviewed.observedStraightSetLoad))
            return no(CustomNextLoadAmendmentRefusal.DIFFERENT_PLANNED_LOAD)
        val candidate=checkNotNull(reviewed.loadReviewCandidate)
        if(candidate<=current.value || maximumAuthorizedIncrease<=zero ||
            candidate.subtract(current.value)>maximumAuthorizedIncrease ||
            candidate.stripTrailingZeros().scale()>3)
            return no(CustomNextLoadAmendmentRefusal.INVALID_INCREMENT)
        if(stock==null || stock.unit!=current.unit || stock.basis!=current.basis)
            return no(CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT)
        val inspection=EquipmentLoadDomain.inspect(stock,current.value,current.unit)
        if(inspection !is LoadInspection.Confirmed || !inspection.exact ||
            inspection.nextAbove==null ||
            !same(inspection.nextAbove,candidate))
            return no(CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT)
        val nextLoad=current.copy(value=normal(candidate))
        val amendedSlot=slot.copy(sets=Collections.unmodifiableList(
            slot.sets.map{set->
                if(set.prescription.type!=DraftSetType.WORKING)set
                else set.copy(prescription=set.prescription.copy(load=nextLoad))
            }))
        val amended=original.copy(slots=Collections.unmodifiableList(
            original.slots.map{if(it.slotId==slotId)amendedSlot else it}))
        return CustomNextLoadAmendmentResult.Proposed(
            CustomNextLoadAmendment(slotId,
                Collections.unmodifiableList(sets.map{it.setKey}),
                normal(current.value),normal(candidate),current.unit,
                current.basis,amended))
    }
}
