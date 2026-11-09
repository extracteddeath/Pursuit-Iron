package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Read-only, athlete-triggered path from a durable custom workout swap to
 * the exact SAVED program day. This is NOT consent, a draft mutation,
 * equipment approval, or an instruction to reuse session targets.
 *
 * The program editor independently reopens the current cycle and must
 * refuse a shortcut if the source revision, day, slot or overrides changed.
 */
internal data class NativeKotlinProgramSwapHandoff(
    val workoutId:String,
    val cycleId:String,
    val issuedCycleRevision:Long,
    val blockId:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val originalSlotId:String,
    val originalExerciseId:String,
    val workoutReplacementExerciseId:String
)

internal object NativeKotlinProgramSwapHandoffDomain {
    /**
     * Only currently active, un-undone replacement slots from the original
     * issued workout qualify. A chain of swaps can safely resolve to its
     * original approved program slot, but neither logged facts nor workouts
     * are represented as future program edits.
     */
    fun review(journal:CustomWorkoutJournalDraft):List<NativeKotlinProgramSwapHandoff> {
        val original=journal.originalSnapshot
        val swaps=journal.receipts.mapNotNull {
            it.operation as? CustomWorkoutJournalOperation.Swap
        }
        if(swaps.isEmpty())return emptyList()
        val currentBySlot=journal.snapshot.slots.associateBy {it.slotId}
        val linked=swaps.associateBy {it.previousSlotId}
        return original.slots.mapNotNull{root->
            var id=root.slotId
            val encountered=mutableSetOf<String>()
            var changed=false
            while(true){
                if(!encountered.add(id))return@mapNotNull null
                val next=linked[id] ?:break
                // An undone swap no longer leads to its former replacement.
                if(journal.receipts.any{
                    (it.operation as? CustomWorkoutJournalOperation.UndoSwap)
                        ?.swapEventId==next.eventId
                })break
                id=next.replacement.slotId
                changed=true
            }
            val active=currentBySlot[id]
            if(!changed || active==null ||
                active.exerciseId==root.exerciseId)return@mapNotNull null
            NativeKotlinProgramSwapHandoff(
                journal.workoutId,original.stamp.cycleId,
                original.stamp.cycleRevision,original.blockId,
                original.absoluteWeek,original.weekday,root.slotId,
                root.exerciseId,active.exerciseId)
        }
    }

    /**
     * Compare against the separately reopened LIVE program, not against the
     * in-workout journal snapshot. Protected week overrides and supersets
     * require manual resolution rather than a misleading quick edit.
     */
    fun canOpenExactEdit(
        request:NativeKotlinProgramSwapHandoff,
        draft:CustomTrainingCycle
    ):Boolean {
        if(draft.cycleId!=request.cycleId ||
            draft.revision!=request.issuedCycleRevision)return false
        val block=draft.blocks.singleOrNull{it.blockId==request.blockId}
            ?:return false
        val day=block.days.singleOrNull{it.weekday==request.weekday}
            ?:return false
        val slot=day.slots.singleOrNull{
            it.slotId==request.originalSlotId &&
                it.exerciseId==request.originalExerciseId
        }?:return false
        if(slot.supersetGroup!=null ||
            draft.weekOverrides[request.blockId].orEmpty().any{
                it.weekday==request.weekday &&
                    it.slotId==request.originalSlotId
            })return false
        return request.workoutReplacementExerciseId!=
            request.originalExerciseId
    }
}
