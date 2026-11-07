package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomBlockTransitionLoadStatus {
    BASELINE_UNAVAILABLE, NO_TRANSITION_NEEDED,
    RECOVERY_OR_RESTRICTION, UNSUPPORTED_PRESCRIPTION,
    EFFORT_UNAVAILABLE, NEAR_FAILURE,
    SHORTER_REST, DEMAND_EXCEEDS_EVIDENCE,
    EQUIPMENT_UNVERIFIED, WARMUP_ABOVE_STARTING_LOAD,
    READY_FOR_REVIEW
}

data class CustomBlockTransitionSet(
    val setKey:String,val ordinal:Int,
    val previousLoad:BigDecimal,val authoredLoad:BigDecimal,
    val proposedLoad:BigDecimal,val unit:LoadUnit,val basis:LoadBasis,
    val startingReps:Int,val targetRir:Int,
    val previousPerformedReps:Int,val previousActualRir:Int?,
    val previousGoalRir:Int?,val effortSource:ProgressionEffortSource
)

data class CustomBlockTransitionLoadReview(
    val status:CustomBlockTransitionLoadStatus,
    val reference:CustomBlockBaselineReview,
    val sets:List<CustomBlockTransitionSet>,
    val assumedGoalRirSetCount:Int,
    val proposedWorkout:CustomWorkoutDaySnapshot?,
    val explanation:String
) {
    val changesAuthoredLoads:Boolean get()=sets.any {
        it.authoredLoad.compareTo(it.proposedLoad)!=0
    }
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

/**
 * Conservative new-recipe review, not an estimated maximum or earned increase.
 * The previous same-day exposure must still pass the shared complete-history,
 * source, split, physical-stock and factual-receipt checks. Each set can retain
 * at most its own demonstrated load and the current authored load. The new
 * starting reps plus reserve cannot exceed that set's observed reps plus its
 * reported effort (or explicitly assumed ORIGINAL goal). Native acceptance
 * must separately re-audit the entire cycle before any changed target is issued.
 */
object CustomBlockTransitionLoadDomain {
    fun review(request:CustomBlockBaselineRequest,readiness:CoachReadiness,
        movementRestricted:Boolean):CustomBlockTransitionLoadReview {
        val exposure=CustomBlockBaselineContinuityDomain.inspect(request,false)
        val reference=exposure.baseline
        fun result(status:CustomBlockTransitionLoadStatus,why:String,
            sets:List<CustomBlockTransitionSet> =emptyList(),assumed:Int=0,
            projected:CustomWorkoutDaySnapshot?=null)=
            CustomBlockTransitionLoadReview(status,reference,
                Collections.unmodifiableList(sets.toList()),assumed,projected,
                why+ProgressionEffortDomain.assumptionNote(assumed))
        val previous=exposure.previous
        val prior=exposure.priorSlot
        if(previous==null || prior==null)
            return result(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
                reference.explanation)
        val next=request.next.slots.single{it.slotId==request.slotId}
        if(previous.snapshot.phase==request.next.phase &&
            previous.snapshot.sessionPurpose==request.next.sessionPurpose &&
            prior.sets.map{it.prescription}==next.sets.map{it.prescription} &&
            prior.sets.map{it.ordinalInSlot}==next.sets.map{it.ordinalInSlot})
            return result(CustomBlockTransitionLoadStatus.NO_TRANSITION_NEEDED,
                "The ordinary unchanged-block review already covers these starting weights.")
        if(readiness==CoachReadiness.LOW || movementRestricted)
            return result(CustomBlockTransitionLoadStatus.RECOVERY_OR_RESTRICTION,
                "Review recovery or the movement restriction before selecting new-block weights.")
        val targets=next.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val old=prior.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val priorWarmups=prior.sets.filter{it.prescription.type==DraftSetType.WARMUP}
        val nextWarmups=next.sets.filter{it.prescription.type==DraftSetType.WARMUP}
        if(targets.isEmpty() || targets.size>old.size || next.supersetGroup!=null ||
            targets.map{it.ordinalInSlot}!=old.take(targets.size).map{it.ordinalInSlot} ||
            priorWarmups.map{it.ordinalInSlot to it.prescription}!=
                nextWarmups.map{it.ordinalInSlot to it.prescription} ||
            next.sets.map{it.setKey}.distinct().size!=next.sets.size ||
            next.sets.map{it.ordinalInSlot}.distinct().size!=next.sets.size ||
            next.sets.any{it.ordinalInSlot<1 || it.setKey.isBlank() ||
                it.prescription.amplifier!=null} ||
            targets.any{set->
                val p=set.prescription
                p.load !is DraftSetLoad.Verified || p.amplifier!=null ||
                    p.targetRir !in 2..10 || p.reps.minimum !in 1..100 ||
                    p.reps.maximum !in p.reps.minimum..100 ||
                    old.none{it.ordinalInSlot==set.ordinalInSlot}
            })
            return result(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,
                "New sets, changed set positions, techniques, bodyweight or missing/near-limit goal effort need a separate starting-load policy.")
        val stock=request.verifiedStock
            ?:return result(CustomBlockTransitionLoadStatus.EQUIPMENT_UNVERIFIED,
                "Current physical equipment could not be verified.")
        val proposed=ArrayList<CustomBlockTransitionSet>()
        var assumed=0
        for((index,target) in targets.withIndex()) {
            val historical=old.single{it.ordinalInSlot==target.ordinalInSlot}
            val fact=reference.priorWorkingSets.single{it.ordinal==target.ordinalInSlot}
            val effort=ProgressionEffortDomain.forCompletedSet(
                fact.actualRir,historical.prescription.targetRir)
            if(effort.assumed)assumed++
            val reserve=effort.rir
                ?:return result(CustomBlockTransitionLoadStatus.EFFORT_UNAVAILABLE,
                    "The previous set has neither reported effort nor its original goal RIR.",assumed=assumed)
            if(reserve<=1)
                return result(CustomBlockTransitionLoadStatus.NEAR_FAILURE,
                    "The latest same-day work was too close to failure to transfer a starting weight.",assumed=assumed)
            // Rest after this slot's final working set cannot establish its
            // earlier performance. Only rests before another working set
            // constrain this slot's load transfer.
            if(index<targets.lastIndex &&
                target.prescription.restSeconds<historical.prescription.restSeconds)
                return result(CustomBlockTransitionLoadStatus.SHORTER_REST,
                    "The new rest period is shorter. Establish performance under that rest before transferring loads.",assumed=assumed)
            val goal=checkNotNull(target.prescription.targetRir)
            if(target.prescription.reps.minimum+goal>fact.performedReps+reserve)
                return result(CustomBlockTransitionLoadStatus.DEMAND_EXCEEDS_EVIDENCE,
                    "The new starting reps and reserve exceed this set's previous demonstrated work. Review a fresh calibration weight.",assumed=assumed)
            val authored=target.prescription.load as DraftSetLoad.Verified
            val oldLoad=historical.prescription.load as DraftSetLoad.Verified
            val weight=minOf(authored.value,fact.priorLoad)
            fun buildable(value:BigDecimal)=EquipmentLoadDomain.inspect(stock,value,authored.unit)
                .let{it is LoadInspection.Confirmed && it.exact}
            if(authored.basis!=oldLoad.basis || authored.unit!=oldLoad.unit ||
                authored.basis!=stock.basis || authored.unit!=stock.unit ||
                !buildable(authored.value) || !buildable(weight))
                return result(CustomBlockTransitionLoadStatus.EQUIPMENT_UNVERIFIED,
                    "Both the authored and conservative starting weights must be exactly buildable on the same equipment.",assumed=assumed)
            proposed+=CustomBlockTransitionSet(target.setKey,target.ordinalInSlot,
                fact.priorLoad,authored.value,weight,authored.unit,authored.basis,
                target.prescription.reps.minimum,goal,fact.performedReps,
                fact.actualRir,historical.prescription.targetRir,effort.source)
        }
        val first=proposed.first()
        if(next.sets.filter{it.prescription.type==DraftSetType.WARMUP}.any {set->
            val load=set.prescription.load as? DraftSetLoad.Verified
            load==null || load.unit!=first.unit || load.basis!=first.basis ||
                load.value>proposed.minOf{it.proposedLoad}
        })return result(CustomBlockTransitionLoadStatus.WARMUP_ABOVE_STARTING_LOAD,
            "Review the warmups before accepting a lower working weight.",assumed=assumed)
        val changed=next.copy(sets=Collections.unmodifiableList(next.sets.map{set->
            val change=proposed.singleOrNull{it.setKey==set.setKey}
            if(change==null)set else set.copy(prescription=set.prescription.copy(
                load=(set.prescription.load as DraftSetLoad.Verified)
                    .copy(value=change.proposedLoad)))
        }))
        val projected=request.next.copy(slots=Collections.unmodifiableList(
            request.next.slots.map{if(it.slotId==request.slotId)changed else it}))
        return result(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,
            "Start at the new rep-range minimum using no more than each set's previously demonstrated weight or its authored weight. This is a new-block calibration proposal, not earned progression. Changed loads need separate athlete acceptance and a full-cycle audit.",
            proposed,assumed,projected)
    }
}
