package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomExerciseSlot
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingDay

/**
 * A Plan "Move earlier/later" operates on one logical movement group,
 * never a single physical row cut out of an active superset.
 *
 * A noncontiguous or incomplete old round is not silently repaired by
 * arbitrary reordering: the athlete must unlink/re-author that group first.
 * No mutation, coaching/progression calculation, journal write, or implicit
 * change to the selected slot's set targets happens here.
 */
internal object NativeKotlinPlanSupersetOrdering {
    fun proposed(
        day:CustomTrainingDay,
        selectedSlotId:String,
        direction:Int
    ):List<String>? {
        val slots=day.slots
        if(direction !in listOf(-1,1) || slots.size !in 2..32 ||
            slots.map{it.slotId}.distinct().size!=slots.size)
            return null
        val byGroup=slots.withIndex().filter{it.value.supersetGroup!=null}
            .groupBy{it.value.supersetGroup}
        if(byGroup.values.any{members->
            members.size !in 2..3 ||
                members.last().index-members.first().index+1!=members.size
        })return null
        val groups=mutableListOf<List<CustomExerciseSlot>>()
        var i=0
        while(i<slots.size) {
            val slot=slots[i]
            val group=slot.supersetGroup
            if(group==null) {
                groups+=listOf(slot)
                i++
            } else {
                val size=byGroup[group]?.size ?:return null
                val members=slots.drop(i).take(size)
                if(members.size!=size || members.any{it.supersetGroup!=group})
                    return null
                groups+=members
                i+=size
            }
        }
        val position=groups.indexOfFirst{unit->
            unit.any{it.slotId==selectedSlotId}
        }
        val destination=position+direction
        if(position<0 || destination !in groups.indices)return null
        val next=groups.toMutableList()
        val moved=next.removeAt(position)
        next.add(destination,moved)
        return next.flatMap{it.map(CustomExerciseSlot::slotId)}
    }
}
