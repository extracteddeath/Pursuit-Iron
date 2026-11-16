package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/** A move is a scheduling change to authored future workouts, never a
 * conversion of the selected lift's history or an instruction to log a set.
 * The exact source slots (including an entire linked superset) and all
 * week exceptions are kept under their original stable identities. */
/** Ephemeral Compose navigation request; reopening or a source revision change
 * must show a fresh destination and confirmation, not replay an old click. */
internal data class NativeKotlinPlanCrossDayMoveScope(
    val athleteId:String,
    val cycleId:String,
    val cycleRevision:Long,
    val blockId:String,
    val fromWeekday:Int,
    val selectedSlotId:String
)

internal data class NativeKotlinPlanCrossDayMoveReview(
    val athleteId:String,
    val cycleId:String,
    val cycleRevision:Long,
    val blockId:String,
    val blockLabel:String,
    val fromWeekday:Int,
    val fromDayLabel:String,
    val toWeekday:Int,
    val toDayLabel:String,
    val selectedSlotId:String,
    val movedSlotIds:List<String>,
    val movedExerciseIds:List<String>,
    val destinationIndex:Int,
    val baseWorkingSets:Int,
    val baseWarmupSets:Int,
    val pinnedCount:Int,
    val supersetGroup:String?,
    val overrideWeeks:List<Int>,
    val overrideCount:Int,
    val overrideWorkingSets:Int,
    val remainingSourceSlots:Int
)

/** Read-only review and independent writer revalidation, shared by
 * the selected destination UI and owner-revisioned durable cycle editor.
 * It never silently moves one member of a superset, creates a new slot ID,
 * reinterprets equipment, or discards week-specific prescriptions. */
internal object NativeKotlinPlanCrossDayMove {
    fun inspect(
        cycle:CustomTrainingCycle,
        blockId:String,
        fromWeekday:Int,
        selectedSlotId:String,
        toWeekday:Int
    ):NativeKotlinPlanCrossDayMoveReview? {
        if(fromWeekday==toWeekday)return null
        val block=cycle.blocks.singleOrNull{it.blockId==blockId} ?:return null
        val source=block.days.singleOrNull{it.weekday==fromWeekday} ?:return null
        val target=block.days.singleOrNull{it.weekday==toWeekday} ?:return null
        val selected=source.slots.singleOrNull{it.slotId==selectedSlotId}
            ?:return null
        val group=selected.supersetGroup
        val moving=if(group==null)listOf(selected)
            else source.slots.filter{it.supersetGroup==group}
        if(moving.isEmpty() || (group!=null && moving.size !in 2..3) ||
            target.slots.size+moving.size>32)return null
        val ids=moving.map{it.slotId}
        if(ids.distinct().size!=ids.size ||
            target.slots.any{it.slotId in ids} ||
            (group!=null && target.slots.any{it.supersetGroup==group}))
            return null
        val overrides=cycle.weekOverrides[blockId].orEmpty().filter {
            it.weekday==fromWeekday && it.slotId in ids
        }
        val overrideKeys=overrides.map{it.weekInBlock to it.slotId}
        if(overrideKeys.distinct().size!=overrideKeys.size ||
            overrides.any{it.weekInBlock !in 1..block.weeks})
            return null
        return NativeKotlinPlanCrossDayMoveReview(
            athleteId=cycle.athleteId,
            cycleId=cycle.cycleId,
            cycleRevision=cycle.revision,
            blockId=blockId,
            blockLabel=block.label,
            fromWeekday=fromWeekday,
            fromDayLabel=source.label,
            toWeekday=toWeekday,
            toDayLabel=target.label,
            selectedSlotId=selectedSlotId,
            movedSlotIds=ids,
            movedExerciseIds=moving.map{it.exerciseId},
            destinationIndex=target.slots.size,
            baseWorkingSets=moving.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WORKING}
            },
            baseWarmupSets=moving.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WARMUP}
            },
            pinnedCount=moving.count{it.pinned},
            supersetGroup=group,
            overrideWeeks=overrides.map{it.weekInBlock}.distinct().sorted(),
            overrideCount=overrides.size,
            overrideWorkingSets=overrides.sumOf{exception->
                exception.sets.count{it.type==DraftSetType.WORKING}
            },
            remainingSourceSlots=source.slots.size-moving.size
        )
    }

    fun confirmed(
        current:CustomTrainingCycle,
        reviewed:NativeKotlinPlanCrossDayMoveReview
    ):CustomCycleBuilderResult {
        if(current.athleteId!=reviewed.athleteId ||
            current.cycleId!=reviewed.cycleId ||
            current.revision!=reviewed.cycleRevision)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.STALE_REVISION)
        if(inspect(current,reviewed.blockId,reviewed.fromWeekday,
            reviewed.selectedSlotId,reviewed.toWeekday)!=reviewed)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return CustomCycleBuilderDomain.moveSlots(
            current,current.revision,reviewed.blockId,
            reviewed.fromWeekday,reviewed.toWeekday,
            reviewed.movedSlotIds,reviewed.destinationIndex
        )
    }
}
