package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/** Non-writing, exact-source confirmation for an authored day removal.
 * No stale dialog can turn a renamed/reordered/replaced block into permission
 * to erase a different day. The existing native writer remains authoritative. */
internal data class NativePlanDayRemovalReview(
    val athleteId:String,
    val cycleId:String,
    val cycleRevision:Long,
    val blockId:String,
    val blockLabel:String,
    val blockWeeks:Int,
    val weekday:Int,
    val dayLabel:String,
    val exerciseCount:Int,
    val workingSets:Int,
    val warmupSets:Int,
    val pinnedExercises:Int,
    val overrideCount:Int,
    val overrideWeeks:List<Int>,
    val overrideWorkingSets:Int,
    val remainingDays:Int
)

internal data class NativePlanBlockRemovalReview(
    val athleteId:String,
    val cycleId:String,
    val cycleRevision:Long,
    val blockId:String,
    val blockLabel:String,
    val blockWeeks:Int,
    val blockPosition:Int,
    val remainingBlocks:Int,
    val trainingDays:Int,
    val exerciseVisits:Int,
    val workingSets:Int,
    val warmupSets:Int,
    val pinnedExercises:Int,
    val overrideCount:Int,
    val overrideWorkingSets:Int,
    val shiftsLaterBlockWeeks:Boolean
)

/**
 * A visible preview cannot authorize a workout mutation. Inspect the durable
 * draft again in the controller's existing revision-CAS writer before changing
 * the draft; completed, prepared, issued and logged training is untouched.
 */
internal object NativeKotlinPlanStructureRemoval {
    fun day(
        cycle:CustomTrainingCycle,blockId:String,weekday:Int
    ):NativePlanDayRemovalReview? {
        val block=cycle.blocks.singleOrNull{it.blockId==blockId}?:return null
        val selected=block.days.singleOrNull{it.weekday==weekday}?:return null
        val overrides=cycle.weekOverrides[blockId].orEmpty()
            .filter{it.weekday==weekday}
        if(overrides.any{it.weekInBlock !in 1..block.weeks} ||
            overrides.map{it.weekInBlock to it.slotId}.distinct().size!=overrides.size ||
            overrides.any{item->selected.slots.count{it.slotId==item.slotId}!=1})
            return null
        return NativePlanDayRemovalReview(
            athleteId=cycle.athleteId,cycleId=cycle.cycleId,
            cycleRevision=cycle.revision,blockId=blockId,
            blockLabel=block.label,blockWeeks=block.weeks,
            weekday=weekday,dayLabel=selected.label,
            exerciseCount=selected.slots.size,
            workingSets=selected.slots.sumOf{slot->slot.sets.count{
                it.type==DraftSetType.WORKING
            }},
            warmupSets=selected.slots.sumOf{slot->slot.sets.count{
                it.type==DraftSetType.WARMUP
            }},
            pinnedExercises=selected.slots.count{it.pinned},
            overrideCount=overrides.size,
            overrideWeeks=overrides.map{it.weekInBlock}.distinct().sorted(),
            overrideWorkingSets=overrides.sumOf{it.sets.count{
                set->set.type==DraftSetType.WORKING
            }},
            remainingDays=block.days.size-1
        )
    }

    fun block(cycle:CustomTrainingCycle,blockId:String):
        NativePlanBlockRemovalReview? {
        if(cycle.blocks.size<=1)return null
        val index=cycle.blocks.indexOfFirst{it.blockId==blockId}
        if(index<0 || cycle.blocks.count{it.blockId==blockId}!=1)return null
        val chosen=cycle.blocks[index]
        val overrides=cycle.weekOverrides[blockId].orEmpty()
        if(overrides.any{exception->
            exception.weekInBlock !in 1..chosen.weeks ||
                chosen.days.count{it.weekday==exception.weekday}!=1 ||
                chosen.days.single{it.weekday==exception.weekday}
                    .slots.count{it.slotId==exception.slotId}!=1
        } || overrides.map{Triple(it.weekInBlock,it.weekday,it.slotId)}
                .distinct().size!=overrides.size)return null
        val slots=chosen.days.flatMap{it.slots}
        return NativePlanBlockRemovalReview(
            athleteId=cycle.athleteId,cycleId=cycle.cycleId,
            cycleRevision=cycle.revision,blockId=blockId,
            blockLabel=chosen.label,blockWeeks=chosen.weeks,
            blockPosition=index,remainingBlocks=cycle.blocks.size-1,
            trainingDays=chosen.days.size,
            exerciseVisits=slots.size,
            workingSets=slots.sumOf{slot->slot.sets.count{
                it.type==DraftSetType.WORKING
            }},
            warmupSets=slots.sumOf{slot->slot.sets.count{
                it.type==DraftSetType.WARMUP
            }},
            pinnedExercises=slots.count{it.pinned},
            overrideCount=overrides.size,
            overrideWorkingSets=overrides.sumOf{
                it.sets.count{set->set.type==DraftSetType.WORKING}
            },
            shiftsLaterBlockWeeks=index<cycle.blocks.lastIndex
        )
    }

    fun confirmedDay(
        current:CustomTrainingCycle,review:NativePlanDayRemovalReview
    ):CustomCycleBuilderResult {
        if(current.athleteId!=review.athleteId ||
            current.cycleId!=review.cycleId ||
            current.revision!=review.cycleRevision)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.STALE_REVISION)
        if(day(current,review.blockId,review.weekday)!=review)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return CustomCycleBuilderDomain.removeDay(
            current,current.revision,review.blockId,review.weekday)
    }

    fun confirmedBlock(
        current:CustomTrainingCycle,review:NativePlanBlockRemovalReview
    ):CustomCycleBuilderResult {
        if(current.athleteId!=review.athleteId ||
            current.cycleId!=review.cycleId ||
            current.revision!=review.cycleRevision)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.STALE_REVISION)
        if(block(current,review.blockId)!=review)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return CustomCycleBuilderDomain.removeBlock(
            current,current.revision,review.blockId)
    }
}
