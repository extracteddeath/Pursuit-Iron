package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Exact, read-only removal review for one uniquely identified custom Plan slot.
 * The athlete must confirm any pinned exercise or authored week exceptions.
 * A linked superset needs unlinking first; removing one member must not leave
 * a dangling round or silently reassign the other member's set identity.
 */
internal data class NativeKotlinPlanExerciseRemovalReview(
    val athleteId:String,
    val cycleId:String,
    val cycleRevision:Long,
    val blockId:String,
    val blockLabel:String,
    val blockWeeks:Int,
    val weekday:Int,
    val dayLabel:String,
    val slotId:String,
    val exerciseId:String,
    val pinned:Boolean,
    val baseWorkingSets:Int,
    val baseWarmupSets:Int,
    val remainingExercises:Int,
    val overriddenWeeks:List<Int>,
    val overrideWorkingSets:Int
)

/**
 * A confirmation is NOT a write capability. Reconstruct the same review
 * against the current durable draft under the existing native revision-CAS
 * writer before calling the Kotlin cycle builder. No load or set in another
 * slot is changed, and completed/issued workout records remain independent.
 */
internal object NativeKotlinPlanExerciseRemoval {
    fun inspect(
        cycle:CustomTrainingCycle,
        blockId:String,
        weekday:Int,
        slotId:String
    ):NativeKotlinPlanExerciseRemovalReview? {
        val block=cycle.blocks.singleOrNull{it.blockId==blockId} ?:return null
        val day=block.days.singleOrNull{it.weekday==weekday} ?:return null
        val slot=day.slots.singleOrNull{it.slotId==slotId} ?:return null
        if(slot.supersetGroup!=null || block.weeks !in 1..52)return null
        val overrides=cycle.weekOverrides[blockId].orEmpty()
            .filter{it.weekday==weekday && it.slotId==slotId}
        val weeks=overrides.map{it.weekInBlock}
        if(weeks.distinct().size!=weeks.size ||
            weeks.any{it !in 1..block.weeks})return null
        return NativeKotlinPlanExerciseRemovalReview(
            athleteId=cycle.athleteId,
            cycleId=cycle.cycleId,
            cycleRevision=cycle.revision,
            blockId=blockId,
            blockLabel=block.label,
            blockWeeks=block.weeks,
            weekday=weekday,
            dayLabel=day.label,
            slotId=slot.slotId,
            exerciseId=slot.exerciseId,
            pinned=slot.pinned,
            baseWorkingSets=slot.sets.count{it.type==DraftSetType.WORKING},
            baseWarmupSets=slot.sets.count{it.type==DraftSetType.WARMUP},
            remainingExercises=day.slots.size-1,
            overriddenWeeks=weeks.sorted(),
            overrideWorkingSets=overrides.sumOf{exception->
                exception.sets.count{it.type==DraftSetType.WORKING}
            }
        )
    }

    fun confirmed(
        current:CustomTrainingCycle,
        reviewed:NativeKotlinPlanExerciseRemovalReview
    ):CustomCycleBuilderResult {
        if(current.revision!=reviewed.cycleRevision ||
            current.cycleId!=reviewed.cycleId ||
            current.athleteId!=reviewed.athleteId)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.STALE_REVISION)
        if(inspect(current,reviewed.blockId,reviewed.weekday,reviewed.slotId)!=reviewed)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return CustomCycleBuilderDomain.removeSlot(
            current,current.revision,reviewed.blockId,reviewed.weekday,
            reviewed.slotId,confirmPinned=true)
    }
}
