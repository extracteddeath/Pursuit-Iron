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
    val amended:CustomWorkoutDaySnapshot,
    /** One physical-number decision shared with generated future programs. */
    val numberFact:KotlinFutureLoadFact?=null
) {
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
}
enum class CustomNextLoadAmendmentRefusal {
    NOT_EARNED, UNRELATED_SLOT, NON_STRAIGHT_SETS,
    DIFFERENT_PLANNED_LOAD, DIFFERENT_PHYSICAL_MACHINE,
    UNVERIFIED_EQUIPMENT, INVALID_INCREMENT
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
        if(reviewed.status !in setOf(
                CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                CustomNextWorkoutCoachingStatus.READY_FOR_ASSISTANCE_REVIEW) ||
            reviewed.latestWorkoutId==null ||
            reviewed.comparedWorkoutIds.size!=2 ||
            reviewed.comparedWorkoutIds.distinct().size!=2 ||
            reviewed.loadReviewCandidate==null ||
            reviewed.observedStraightSetLoad==null)
            return no(CustomNextLoadAmendmentRefusal.NOT_EARNED)
        val slot=original.slots.singleOrNull{it.slotId==slotId}
            ?:return no(CustomNextLoadAmendmentRefusal.UNRELATED_SLOT)
        // Two gym machines can share the same exercise and set keys while
        // having completely independent verified performance. The evidence
        // preview must be scoped to the exact station of the amended slot.
        if(reviewed.physicalMachineInstanceId!=slot.machineInstanceId)
            return no(CustomNextLoadAmendmentRefusal.DIFFERENT_PHYSICAL_MACHINE)
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
        // A downstream load proposal must independently honor the same
        // machine-versus-free-barbell authority as the coaching review.
        // A stale or caller-provided positive preview is not authorization
        // to turn a tagged physical station into a free-barbell increase.
        if(KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(
                current.basis,slot.machineInstanceId))
            return no(CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT)
        if(!same(current.value,reviewed.observedStraightSetLoad))
            return no(CustomNextLoadAmendmentRefusal.DIFFERENT_PLANNED_LOAD)
        val candidate=checkNotNull(reviewed.loadReviewCandidate)
        val assisted=reviewed.status==
            CustomNextWorkoutCoachingStatus.READY_FOR_ASSISTANCE_REVIEW
        val numberReview=KotlinFutureLoadNumberAuthorityDomain.review(
            issuedLoad=current.value,candidateLoad=candidate,
            unit=current.unit,basis=current.basis,stock=stock,
            maximumAuthorizedIncrease=maximumAuthorizedIncrease,
            source=if(assisted)KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT
                else KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
        if(numberReview is KotlinFutureLoadReview.Rejected) {
            val unverified=numberReview.reason in setOf(
                KotlinFutureLoadRefusal.UNVERIFIED_UNIT_OR_BASIS,
                KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD,
                KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD)
            return no(if(unverified)
                CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT
            else CustomNextLoadAmendmentRefusal.INVALID_INCREMENT)
        }
        val verifiedNumbers=numberReview as KotlinFutureLoadReview.Accepted
        // Keep every original warmup exactly as prescribed. On an assisted
        // machine its counterweight must remain HIGHER (easier) than the new
        // working setting; never silently create a harder warmup.
        if(assisted && slot.sets.filter{
                it.prescription.type==DraftSetType.WARMUP
            }.any{warmup->
                val w=warmup.prescription.load as? DraftSetLoad.Verified
                w==null || w.unit!=current.unit || w.basis!=current.basis ||
                    w.value<=candidate ||
                    (EquipmentLoadDomain.inspect(stock,w.value,w.unit)
                        as? LoadInspection.Confirmed)?.exact!=true
            })return no(CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT)
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
                current.basis,amended,verifiedNumbers.fact))
    }
}
