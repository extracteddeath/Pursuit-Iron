package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomAuthoredLoadReversionStatus {
    INVALID_HISTORY, NOT_LATER_TRAINING_WEEK, TARGET_ALREADY_TRAINED,
    NO_IMMEDIATELY_PREVIOUS_DAY, CHANGED_CONTEXT, NO_HIGHER_AUTHORED_LOAD,
    CONFIRM_HIGHER_AUTHORED_LOAD
}

data class CustomAuthoredLoadReversionSet(
    val setKey:String, val ordinal:Int, val lastIssuedLoad:BigDecimal,
    val authoredLoad:BigDecimal, val unit:LoadUnit, val basis:LoadBasis
)

data class CustomAuthoredLoadReversionReview(
    val status:CustomAuthoredLoadReversionStatus,
    val previousWorkoutId:String?,
    val higherSets:List<CustomAuthoredLoadReversionSet>,
    val explanation:String
) {
    val requiresAthleteReview:Boolean
        get()=status==CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
}

/**
 * A signed, authored NEXT workout is not evidence that its higher week load
 * was earned. Detect even a failed, skipped or near-failure most recent day;
 * successful progression and safe lower-load repeat are separate reviews.
 *
 * Only the immediately previous finished SAME weekday, unchanged source,
 * block and exercise variant may supply an issued weight. Different training
 * days, earlier favorable weeks and other sources cannot be borrowed.
 * This only flags the reversion; it never changes a prescription.
 */
object CustomAuthoredLoadReversionDomain {
    fun review(request:CustomBlockBaselineRequest):CustomAuthoredLoadReversionReview {
        fun result(status:CustomAuthoredLoadReversionStatus,why:String,id:String?=null,
            sets:List<CustomAuthoredLoadReversionSet> = emptyList())=
            CustomAuthoredLoadReversionReview(status,id,
                Collections.unmodifiableList(sets.toList()),why)
        val next=request.next
        if(request.completed.any{
                it.completionOrder<1 || it.evidence.workoutId.isBlank() ||
                    it.evidence.journalRevision<1 ||
                    it.evidence.athleteId!=it.snapshot.stamp.athleteId ||
                    it.evidence.cycleId!=it.snapshot.stamp.cycleId ||
                    it.evidence.cycleRevision!=it.snapshot.stamp.cycleRevision
            } || request.completed.zipWithNext().any{
                it.first.completionOrder>=it.second.completionOrder
            } || request.completed.map{it.evidence.workoutId}.distinct().size!=
                request.completed.size)
            return result(CustomAuthoredLoadReversionStatus.INVALID_HISTORY,
                "Completion order or frozen source provenance is invalid.")
        if(next.blockPurpose!=CustomBlockPurpose.TRAINING ||
            next.absoluteWeek !in 2..104 || next.weekInBlock<2)
            return result(CustomAuthoredLoadReversionStatus.NOT_LATER_TRAINING_WEEK,
                "Only later weeks of the same training block can return to an earlier issued load.")
        val target=next.slots.singleOrNull{it.slotId==request.slotId}
            ?:return result(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
                "The selected workout slot is unavailable or ambiguous.")
        val sameDay=request.completed.filter{
            it.snapshot.stamp.athleteId==next.stamp.athleteId &&
                it.snapshot.stamp.cycleId==next.stamp.cycleId &&
                it.snapshot.weekday==next.weekday
        }
        if(sameDay.any{it.snapshot.absoluteWeek>=next.absoluteWeek})
            return result(CustomAuthoredLoadReversionStatus.TARGET_ALREADY_TRAINED,
                "The target training day or a later one is already completed.")
        if(sameDay.count{it.snapshot.absoluteWeek==next.absoluteWeek-1}>1)
            return result(CustomAuthoredLoadReversionStatus.INVALID_HISTORY,
                "Two finished sessions occupy the previous scheduled day.")
        val previous=sameDay.lastOrNull()
            ?:return result(CustomAuthoredLoadReversionStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
                "No prior finished same-day workout is available.")
        val old=previous.snapshot
        if(old.absoluteWeek!=next.absoluteWeek-1 || old.weekInBlock!=next.weekInBlock-1)
            return result(CustomAuthoredLoadReversionStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
                "An intervening workout is missing; older weeks cannot stand in.",
                previous.evidence.workoutId)
        if(old.stamp!=next.stamp || old.blockId!=next.blockId ||
            old.blockPurpose!=next.blockPurpose || old.phase!=next.phase ||
            old.sessionPurpose!=next.sessionPurpose || old.split!=next.split)
            return result(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
                "The plan, gym, block, phase or session purpose has changed.",
                previous.evidence.workoutId)
        val prior=old.slots.singleOrNull{it.slotId==target.slotId}
        if(prior==null || prior.exerciseId!=target.exerciseId ||
            prior.familyId!=target.familyId || prior.variantId!=target.variantId ||
            prior.attachmentId!=target.attachmentId || prior.role!=target.role ||
            prior.supersetGroup!=target.supersetGroup ||
            prior.sets.size!=target.sets.size ||
            prior.sets.map{it.ordinalInSlot}!=target.sets.map{it.ordinalInSlot} ||
            prior.sets.map{it.prescription.type}!=target.sets.map{it.prescription.type} ||
            prior.sets.map{it.ordinalInSlot}.distinct().size!=prior.sets.size ||
            target.sets.map{it.ordinalInSlot}.distinct().size!=target.sets.size ||
            prior.sets.any{it.ordinalInSlot<1 || it.setKey.isBlank()} ||
            target.sets.any{it.ordinalInSlot<1 || it.setKey.isBlank()})
            return result(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
                "Exercise identity or set structure differs; no direct load comparison is certified.",
                previous.evidence.workoutId)
        val higher=ArrayList<CustomAuthoredLoadReversionSet>()
        for((a,b) in prior.sets.zip(target.sets)) {
            if(b.prescription.type!=DraftSetType.WORKING)continue
            val oldLoad=a.prescription.load as? DraftSetLoad.Verified
            val newLoad=b.prescription.load as? DraftSetLoad.Verified
            if(oldLoad==null || newLoad==null ||
                oldLoad.unit!=newLoad.unit || oldLoad.basis!=newLoad.basis)
                return result(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
                    "Weight units, loading basis or bodyweight prescription changed.",
                    previous.evidence.workoutId)
            if(newLoad.value>oldLoad.value)
                higher+=CustomAuthoredLoadReversionSet(b.setKey,b.ordinalInSlot,
                    oldLoad.value,newLoad.value,newLoad.unit,newLoad.basis)
        }
        return if(higher.isEmpty())result(
            CustomAuthoredLoadReversionStatus.NO_HIGHER_AUTHORED_LOAD,
            "Authored weights do not exceed the immediately prior issued working loads.",
            previous.evidence.workoutId)
        else result(CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD,
            "The authored week is heavier than the last issued session. " +
                "This is not evidence of earned progression, including after failure or missing effort. " +
                "Review lower-load continuity or consciously choose the authored plan.",
            previous.evidence.workoutId,higher)
    }
}
