package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomSameBlockLoadStatus {
    INVALID_HISTORY, NOT_LATER_TRAINING_WEEK, TARGET_ALREADY_TRAINED,
    NO_IMMEDIATELY_PREVIOUS_DAY, CHANGED_CONTEXT, CHANGED_RECIPE,
    PREVIOUS_WORKOUT_INCOMPLETE, EQUIPMENT_UNVERIFIED,
    RESTRICTED_OR_LOW_READINESS, EFFORT_UNAVAILABLE, NEAR_FAILURE,
    NO_LOWER_VERIFIED_BASELINE, WARMUP_EXCEEDS_LOAD, REVIEW_LOWER_REPEAT
}

data class CustomSameBlockLoadSet(
    val setKey:String, val ordinal:Int,
    val authoredLoad:BigDecimal, val lastIssuedLoad:BigDecimal,
    val proposedLoad:BigDecimal, val unit:LoadUnit, val basis:LoadBasis,
    val previousPerformedReps:Int, val previousActualRir:Int?,
    val previousGoalRir:Int?, val effortSource:ProgressionEffortSource
)

data class CustomSameBlockLoadReview(
    val status:CustomSameBlockLoadStatus,
    val previousWorkoutId:String?,
    val sets:List<CustomSameBlockLoadSet>,
    val assumedGoalRirSetCount:Int,
    val proposedWorkout:CustomWorkoutDaySnapshot?,
    val explanation:String
) {
    val changesAuthoredLoads:Boolean get()=sets.any{
        it.proposedLoad.compareTo(it.authoredLoad)!=0
    }
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

/**
 * A LOWER already-issued working weight must not be mistaken for an earned
 * increase when the following authored week still contains the older weight.
 *
 * This is a *read-only* one-week handoff, not progression or permission to edit
 * the athlete's cycle. The native approval owner must re-audit the complete
 * cycle and obtain fresh per-workout consent before issuing the projection.
 * Only the immediately previous, completed, same-slot and same-purpose day
 * may serve as evidence; a failed latest day must never be cherry-picked away.
 */
object CustomSameBlockLoadContinuityDomain {
    fun review(
        request:CustomBlockBaselineRequest,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):CustomSameBlockLoadReview {
        fun result(status:CustomSameBlockLoadStatus,explanation:String,
            id:String?=null,sets:List<CustomSameBlockLoadSet> = emptyList(),
            assumed:Int=0,projected:CustomWorkoutDaySnapshot?=null
        )=CustomSameBlockLoadReview(status,id,
            Collections.unmodifiableList(sets.toList()),assumed,projected,
            explanation+ProgressionEffortDomain.assumptionNote(assumed))
        val next=request.next
        if(request.completed.map{it.completionOrder}.any{it<1} ||
            request.completed.zipWithNext().any{it.first.completionOrder>=it.second.completionOrder} ||
            request.completed.map{it.evidence.workoutId}.distinct().size!=request.completed.size ||
            request.completed.any {
                it.evidence.workoutId.isBlank() || it.evidence.journalRevision<1 ||
                it.evidence.athleteId!=it.snapshot.stamp.athleteId ||
                it.evidence.cycleId!=it.snapshot.stamp.cycleId ||
                it.evidence.cycleRevision!=it.snapshot.stamp.cycleRevision
            })
            return result(CustomSameBlockLoadStatus.INVALID_HISTORY,
                "Completion ordering or immutable workout provenance is invalid.")
        if(next.blockPurpose!=CustomBlockPurpose.TRAINING ||
            next.absoluteWeek !in 2..104 || next.weekInBlock<2)
            return result(CustomSameBlockLoadStatus.NOT_LATER_TRAINING_WEEK,
                "The accepted-load handoff applies only after the first training week of a block.")
        val selected=next.slots.singleOrNull{it.slotId==request.slotId}
            ?:return result(CustomSameBlockLoadStatus.CHANGED_CONTEXT,
                "The next workout no longer contains one unique exercise slot.")
        // Filtering by weekday, rather than exercise, prevents cherry-picking a
        // good older lift over the most recent failed or substituted session.
        val sameDay=request.completed.filter{
            it.snapshot.stamp.athleteId==next.stamp.athleteId &&
            it.snapshot.stamp.cycleId==next.stamp.cycleId &&
            it.snapshot.weekday==next.weekday
        }
        if(sameDay.any{it.snapshot.absoluteWeek>=next.absoluteWeek})
            return result(CustomSameBlockLoadStatus.TARGET_ALREADY_TRAINED,
                "This weekday is already completed in the target or a later week.")
        if(sameDay.count{it.snapshot.absoluteWeek==next.absoluteWeek-1}>1)
            return result(CustomSameBlockLoadStatus.INVALID_HISTORY,
                "Multiple completed workouts occupy the previous scheduled day; " +
                    "no single issued baseline may be selected.")
        val prior=sameDay.lastOrNull()
            ?:return result(CustomSameBlockLoadStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
                "There is no immediately previous completed same-weekday workout.")
        val old=prior.snapshot
        if(old.absoluteWeek!=next.absoluteWeek-1 ||
            old.weekInBlock!=next.weekInBlock-1)
            return result(CustomSameBlockLoadStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
                "A missing intervening training week cannot be bypassed.",
                prior.evidence.workoutId)
        if(old.stamp!=next.stamp || old.blockId!=next.blockId ||
            old.blockPurpose!=next.blockPurpose || old.phase!=next.phase ||
            old.sessionPurpose!=next.sessionPurpose || old.split!=next.split ||
            old.weekday!=next.weekday)
            return result(CustomSameBlockLoadStatus.CHANGED_CONTEXT,
                "Source, block, phase, split or session purpose changed; establish a new baseline.",
                prior.evidence.workoutId)
        val oldSlot=old.slots.singleOrNull{it.slotId==request.slotId}
        if(oldSlot!=null && oldSlot.machineInstanceId!=selected.machineInstanceId)
            return result(CustomSameBlockLoadStatus.CHANGED_CONTEXT,
                "The physical machine A/B changed; never borrow a different station's load history.",
                prior.evidence.workoutId)
        if(oldSlot==null || oldSlot.exerciseId!=selected.exerciseId ||
            oldSlot.familyId!=selected.familyId ||
            oldSlot.variantId!=selected.variantId ||
            oldSlot.attachmentId!=selected.attachmentId ||
            oldSlot.role!=selected.role ||
            oldSlot.supersetGroup!=selected.supersetGroup ||
            oldSlot.sets.size!=selected.sets.size ||
            selected.sets.size !in 1..32 ||
            oldSlot.sets.map{it.ordinalInSlot}!=selected.sets.map{it.ordinalInSlot} ||
            oldSlot.sets.map{it.prescription.type}!=selected.sets.map{it.prescription.type} ||
            oldSlot.sets.any{it.ordinalInSlot<1 || it.setKey.isBlank()} ||
            selected.sets.any{it.ordinalInSlot<1 || it.setKey.isBlank()} ||
            oldSlot.sets.map{it.ordinalInSlot}.distinct().size!=oldSlot.sets.size ||
            selected.sets.map{it.ordinalInSlot}.distinct().size!=selected.sets.size ||
            oldSlot.sets.map{it.setKey}.distinct().size!=oldSlot.sets.size ||
            selected.sets.map{it.setKey}.distinct().size!=selected.sets.size ||
            oldSlot.sets.zip(selected.sets).any{(a,b)->
                a.prescription.amplifier!=null || b.prescription.amplifier!=null ||
                a.prescription.copy(load=b.prescription.load)!=b.prescription
            })
            return result(CustomSameBlockLoadStatus.CHANGED_RECIPE,
                "Exercise identity, set structure, targets, rest, or technique changed.",
                prior.evidence.workoutId)
        val oldWork=oldSlot.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val nextWork=selected.sets.filter{it.prescription.type==DraftSetType.WORKING}
        if(oldWork.isEmpty() || oldWork.size!=nextWork.size ||
            oldSlot.sets.filter{it.prescription.type!=DraftSetType.WORKING}
                .zip(selected.sets.filter{it.prescription.type!=DraftSetType.WORKING})
                .any{(a,b)->a.prescription!=b.prescription})
            return result(CustomSameBlockLoadStatus.CHANGED_RECIPE,
                "Nonworking sets and their physical loading must remain unchanged.",
                prior.evidence.workoutId)
        if(movementRestricted || readiness==CoachReadiness.LOW)
            return result(CustomSameBlockLoadStatus.RESTRICTED_OR_LOW_READINESS,
                "Recovery or restriction takes precedence over repeating a prior working load.",
                prior.evidence.workoutId)
        val facts=prior.evidence.visits.filter{it.slotId==oldSlot.slotId}
        val workingFacts=facts.filter{
            it.part==CustomWorkoutVisitPart.MAIN && it.setType==DraftSetType.WORKING
        }
        if(facts.any{it.part==CustomWorkoutVisitPart.FOLLOW_UP} ||
            workingFacts.size!=oldWork.size ||
            workingFacts.map{it.setKey}.toSet()!=oldWork.map{it.setKey}.toSet())
            return result(CustomSameBlockLoadStatus.PREVIOUS_WORKOUT_INCOMPLETE,
                "The last issued workout lacks unique complete working-set receipts.",
                prior.evidence.workoutId)
        val stock=request.verifiedStock
            ?:return result(CustomSameBlockLoadStatus.EQUIPMENT_UNVERIFIED,
                "Current verified physical loading is unavailable.",prior.evidence.workoutId)
        val proposals=ArrayList<CustomSameBlockLoadSet>()
        var assumed=0
        for((oldSet,nextSet) in oldWork.zip(nextWork)) {
            val fact=workingFacts.singleOrNull{it.setKey==oldSet.setKey}
                ?:return result(CustomSameBlockLoadStatus.PREVIOUS_WORKOUT_INCOMPLETE,
                    "Duplicate or missing historical working-set receipt.",prior.evidence.workoutId)
            val lastLoad=oldSet.prescription.load as? DraftSetLoad.Verified
            val authored=nextSet.prescription.load as? DraftSetLoad.Verified
            if(lastLoad==null || authored==null ||
                lastLoad.unit!=authored.unit || lastLoad.basis!=authored.basis ||
                stock.unit!=authored.unit || stock.basis!=authored.basis)
                return result(CustomSameBlockLoadStatus.EQUIPMENT_UNVERIFIED,
                    "Verified unit, implement and basis must match.",prior.evidence.workoutId)
            val actual=fact.actual
            if(fact.exerciseId!=oldSlot.exerciseId ||
                fact.prescribedLoad!=lastLoad ||
                fact.prescribedReps!=oldSet.prescription.reps ||
                fact.prescribedRir!=oldSet.prescription.targetRir ||
                fact.setType!=oldSet.prescription.type ||
                fact.part!=CustomWorkoutVisitPart.MAIN ||
                fact.amplifierKind!=null || fact.effectiveEventId.isBlank() ||
                fact.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                !CustomWorkoutJournalDomain.actualValid(lastLoad,fact.outcome,actual) ||
                actual==null || actual.reps<oldSet.prescription.reps.minimum ||
                actual.load?.compareTo(lastLoad.value)!=0 || actual.unit!=lastLoad.unit)
                return result(CustomSameBlockLoadStatus.PREVIOUS_WORKOUT_INCOMPLETE,
                    "The most recent issued set was failed, missed, or not performed as prescribed.",
                    prior.evidence.workoutId)
            if(KotlinMachineAlternativesDomain.normalizeMachineId(
                    actual.machineInstanceId)!=
                KotlinMachineAlternativesDomain.normalizeMachineId(
                    selected.machineInstanceId))
                return result(CustomSameBlockLoadStatus.PREVIOUS_WORKOUT_INCOMPLETE,
                    "The previous working set was performed on a different or unassigned A/B machine.",
                    prior.evidence.workoutId)
            val effort=ProgressionEffortDomain.forCompletedSet(actual.actualRir,
                oldSet.prescription.targetRir)
            if(effort.assumed)assumed++
            if(effort.rir==null)
                return result(CustomSameBlockLoadStatus.EFFORT_UNAVAILABLE,
                    "Neither actual nor originally prescribed goal RIR is available.",
                    prior.evidence.workoutId,assumed=assumed)
            if(checkNotNull(effort.rir)<=1)
                return result(CustomSameBlockLoadStatus.NEAR_FAILURE,
                    "Near-failure work needs an independent recovery or load review.",
                    prior.evidence.workoutId,assumed=assumed)
            val repeat=minOf(lastLoad.value,authored.value)
            for(value in listOf(lastLoad.value,authored.value,repeat)) {
                val check=EquipmentLoadDomain.inspect(stock,value,authored.unit)
                if(check !is LoadInspection.Confirmed || !check.exact)
                    return result(CustomSameBlockLoadStatus.EQUIPMENT_UNVERIFIED,
                        "Every original and carried weight must be exactly buildable.",
                        prior.evidence.workoutId,assumed=assumed)
            }
            proposals+=CustomSameBlockLoadSet(nextSet.setKey,nextSet.ordinalInSlot,
                authored.value,lastLoad.value,repeat,authored.unit,authored.basis,
                actual.reps,actual.actualRir,oldSet.prescription.targetRir,effort.source)
        }
        if(proposals.none{it.proposedLoad<it.authoredLoad})
            return result(CustomSameBlockLoadStatus.NO_LOWER_VERIFIED_BASELINE,
                "The next authored targets already preserve or undercut the last verified loads.",
                prior.evidence.workoutId)
        val lowest=proposals.minOf{it.proposedLoad}
        if(selected.sets.filter{it.prescription.type==DraftSetType.WARMUP}.any{
                val warm=it.prescription.load as? DraftSetLoad.Verified
                warm==null || warm.unit!=stock.unit || warm.basis!=stock.basis ||
                    warm.value>lowest
            })
            return result(CustomSameBlockLoadStatus.WARMUP_EXCEEDS_LOAD,
                "A warmup would exceed the lower working load; review the full prescription.",
                prior.evidence.workoutId,assumed=assumed)
        val newSlot=selected.copy(sets=Collections.unmodifiableList(
            selected.sets.map{set->
                val change=proposals.singleOrNull{it.setKey==set.setKey}
                if(change==null)set else set.copy(prescription=set.prescription.copy(
                    load=(set.prescription.load as DraftSetLoad.Verified)
                        .copy(value=change.proposedLoad)))
            }))
        val projected=next.copy(slots=Collections.unmodifiableList(
            next.slots.map{if(it.slotId==selected.slotId)newSlot else it}))
        return result(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,
            "The immediately prior issued loads were lower than the next authored loads. " +
                "Review repeating each set's verified weight, not an earned increase. " +
                "This preview cannot edit the cycle or issue a workout.",
            prior.evidence.workoutId,proposals,assumed,projected)
    }
}
