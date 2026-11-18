package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Athlete-initiated and previewed plan repair. Unlike the separately signed
 * adaptive-next-block acceptance flow, this edits the athlete's CURRENT
 * custom draft after a full fresh audit. It revokes that draft's old signoff
 * by advancing its revision. It never issues a workout or rewrites a journal.
 *
 * A Ready is ONLY a preview. The owner-fenced host independently rederives
 * the complete proposal at commit, including today's saved SET_COUNT grant.
 */
internal data class NativeStrengthVolumeRepairRequest(
    val cycleId:String,
    val blockId:String,
    val expectedCycleRevision:Long,
    val expectedSourceRevision:Long,
    val coaching:CustomCoachAuditInputs,
    val maximumWorkingSetsPerSlot:Int
)

internal data class NativeStrengthVolumeRepairReady(
    val request:NativeStrengthVolumeRepairRequest,
    val expectedPermissionRevision:Long,
    val expectedCoachSettingsRevision:Long,
    val proposedCycle:CustomTrainingCycle,
    val additionsPerWeek:Int,
    val weeksCovered:Int,
    val changedSlots:List<NativeStrengthVolumeRepairSlot>
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}

internal data class NativeStrengthVolumeRepairSlot(
    val weekday:Int,
    val slotId:String,
    val exerciseId:String,
    val beforeWorkingSets:Int,
    val afterWorkingSets:Int
)

internal sealed interface NativeStrengthVolumeRepairPreview {
    data class Ready(val receipt:NativeStrengthVolumeRepairReady):
        NativeStrengthVolumeRepairPreview
    data class Rejected(val explanation:String):NativeStrengthVolumeRepairPreview
}

internal sealed interface NativeStrengthVolumeRepairDecision {
    data class Saved(val newCycleRevision:Long):NativeStrengthVolumeRepairDecision
    data class Rejected(val explanation:String):NativeStrengthVolumeRepairDecision
}

/** Only meaningful proposed per-slot changes may be displayed or committed. */
internal fun reviewedStrengthVolumeSlots(
    old:CustomTrainingCycle,new:CustomTrainingCycle,blockId:String
):List<NativeStrengthVolumeRepairSlot> {
    val before=old.blocks.single {it.blockId==blockId}
    val after=new.blocks.single {it.blockId==blockId}
    return before.days.flatMap {day->
        val later=after.days.single {it.weekday==day.weekday}
        day.slots.mapNotNull {slot->
            val proposed=later.slots.single {it.slotId==slot.slotId}
            val a=slot.sets.count{it.type==DraftSetType.WORKING}
            val b=proposed.sets.count{it.type==DraftSetType.WORKING}
            if(a==b)null
            else NativeStrengthVolumeRepairSlot(
                day.weekday,slot.slotId,slot.exerciseId,a,b)
        }
    }
}
