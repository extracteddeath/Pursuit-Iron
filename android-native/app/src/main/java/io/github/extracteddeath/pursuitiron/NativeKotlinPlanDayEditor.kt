package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Pure, fail-closed action projection; the native writer remains the authority. */
internal data class NativePlanExerciseActions(
    val edit:Boolean=false,
    val swap:Boolean=false,
    val earlier:Boolean=false,
    val later:Boolean=false,
    val weekOverride:Boolean=false,
    val superset:Boolean=false,
    val remove:Boolean=false
)

internal fun nativePlanDayIsCurrent(
    draft:CustomTrainingCycle,block:CustomTrainingBlock,day:CustomTrainingDay
):Boolean {
    val saved=draft.blocks.singleOrNull{it.blockId==block.blockId}
        ?.takeIf{it==block} ?:return false
    return saved.days.singleOrNull{it.weekday==day.weekday}==day
}

internal fun nativePlanExerciseActions(
    draft:CustomTrainingCycle,block:CustomTrainingBlock,day:CustomTrainingDay,
    index:Int,busy:Boolean
):NativePlanExerciseActions {
    if(busy || !nativePlanDayIsCurrent(draft,block,day))
        return NativePlanExerciseActions()
    val slot=day.slots.getOrNull(index) ?:return NativePlanExerciseActions()
    if(day.slots.count{it.slotId==slot.slotId}!=1)
        return NativePlanExerciseActions()
    val hasWeekOverride=draft.weekOverrides[block.blockId].orEmpty().any {
        it.weekday==day.weekday && it.slotId==slot.slotId
    }
    return NativePlanExerciseActions(
        edit=true,swap=!hasWeekOverride && slot.supersetGroup==null,
        earlier=NativeKotlinPlanSupersetOrdering.proposed(
            day,slot.slotId,-1)!=null,
        later=NativeKotlinPlanSupersetOrdering.proposed(
            day,slot.slotId,1)!=null,
        weekOverride=hasWeekOverride,superset=slot.supersetGroup!=null,
        remove=slot.supersetGroup==null)
}

internal fun nativePlanCanCreateSuperset(
    draft:CustomTrainingCycle,block:CustomTrainingBlock,day:CustomTrainingDay,
    busy:Boolean,allowSupersets:Boolean
):Boolean = !busy && allowSupersets && nativePlanDayIsCurrent(draft,block,day) &&
    day.slots.count {slot->
        slot.supersetGroup==null &&
            slot.sets.any{it.type==DraftSetType.WORKING}
    }>=2

/**
 * Plan labels are factual current-gym information, not per-program loads or
 * authority to change an already issued workout's selected machine.
 */
internal fun nativePlanMachineDescription(
    review:NativePlanMachineReview?,exerciseId:String
):String? {
    if(review==null || exerciseId !in review.eligibleExerciseIds)
        return null
    val groups=review.roster.configurations.filter {
        it.gymId==review.gymId && it.exerciseId==exerciseId
    }
    if(groups.isEmpty())return "No physical machines saved at this gym yet."
    val group=groups.singleOrNull()
        ?:return "Machine A/B choices need review in Gym."
    val active=group.machines.filter{it.active}
    val gymDefault=active.singleOrNull { it.id==group.defaultMachineId }
    return if(active.isEmpty())"No active physical machines at this gym."
        else buildString {
            append(active.size)
            append(if(active.size==1)" active physical machine" else " active physical machines")
            append(" at this gym")
            if(gymDefault!=null) {
                append(" · Default: ")
                append(gymDefault.label)
            } else append(" · Default not verified")
        }
}

/** Same accessible, non-horizontal-scrolling day editor in both Plan layouts. */
@Composable
internal fun NativeKotlinPlanDayEditor(
    draft:CustomTrainingCycle,block:CustomTrainingBlock,day:CustomTrainingDay,
    busy:Boolean,allowSupersets:Boolean,
    onEditExercise:(String)->Unit,onSwapExercise:(String)->Unit,
    onMoveExercise:(String,Int)->Unit,onEditSuperset:(String?)->Unit,
    onRemoveExercise:(String)->Unit,onMoveToDay:(String)->Unit,
    onEditDay:()->Unit,onAddExercise:()->Unit,onRemoveDay:()->Unit,
    machineReview:NativePlanMachineReview?=null,
    onManageMachine:(String)->Unit={},
    enableProgressionEditing:Boolean=false,
    onProgressionChoice:(String,SavedProgressionMethod?)->Unit={_,_->}
) {
    if(!nativePlanDayIsCurrent(draft,block,day)) {
        PursuitEmptyState("Program changed",
            "This day no longer matches the current Plan. Reopen it before editing.")
        return
    }
    if(day.slots.isEmpty()) {
        PursuitEmptyState("No exercises yet",
            "Add a movement to this draft day before review and approval.")
    } else {
        PursuitSectionHeading("Exercises",
            "Review exact working sets and move exercises into your preferred order.")
        day.slots.forEachIndexed {index,slot->
            val actions=nativePlanExerciseActions(draft,block,day,index,busy)
            val working=slot.sets.count{it.type==DraftSetType.WORKING}
            val warmups=slot.sets.count{it.type==DraftSetType.WARMUP}
            val partnerNames=slot.supersetGroup?.let {group->
                day.slots.filter{it.supersetGroup==group && it.slotId!=slot.slotId}
                    .map{kotlinExerciseLabel(it.exerciseId)}
            }.orEmpty()
            key(slot.slotId) {
            var moreActionsExpanded by rememberSaveable { mutableStateOf(false) }
            var showProgressionPicker by remember(draft, block.blockId, day.weekday, slot.slotId) {
                mutableStateOf(false)
            }
            if(showProgressionPicker && enableProgressionEditing) {
                NativeKotlinProgressionMethodPicker(
                    draft,block.blockId,day.weekday,slot.slotId,busy,
                    onDismiss={showProgressionPicker=false},
                    onSave={method->
                        showProgressionPicker=false
                        onProgressionChoice(slot.slotId,method)
                    })
            }
            PursuitCard {
                Text((index+1).toString()+". "+kotlinExerciseLabel(slot.exerciseId),
                    style=MaterialTheme.typography.titleMedium)
                val facts=buildString {
                    append(working);append(" working")
                    if(warmups>0){append(" · ");append(warmups);append(" warm-up")}
                    append(" · ");append(slot.role.name.lowercase().replace('_',' '))
                    if(slot.pinned)append(" · Pinned")
                    if(actions.weekOverride)append(" · Week-specific sets")
                }
                Text(facts,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Progression · "+savedProgressionLabel(slot.progressionMethod),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(actions.superset)Text(
                    "Superset with "+partnerNames.joinToString(" / ").ifEmpty{"linked movement"},
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.primary)
                nativePlanMachineDescription(machineReview,slot.exerciseId)?.let {summary->
                    Text("Physical Machine A/B · $summary",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick={onManageMachine(slot.exerciseId)},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Manage Machine A/B at my gym")}
                    Text("A gym default applies to future workouts. Use the workout "+
                        "machine picker to change an already issued session; "+
                        "neither action rewrites logged weights.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PursuitResponsiveActionPair(
                    primary={m->
                        Button(onClick={onEditExercise(slot.slotId)},enabled=actions.edit,
                            modifier=m.heightIn(min=48.dp)) {Text("Edit sets")}
                    },
                    secondary={m->
                        OutlinedButton(onClick={onSwapExercise(slot.slotId)},
                            enabled=actions.swap,modifier=m.heightIn(min=48.dp)) {
                            Text("Swap exercise")
                        }
                    }
                )
                PursuitDisclosureRow(
                    "More exercise actions",
                    "Reorder, move to another day or remove from this draft",
                    moreActionsExpanded,{moreActionsExpanded=!moreActionsExpanded}
                )
                if(moreActionsExpanded) {
                    if(enableProgressionEditing && actions.edit)
                        TextButton(
                            onClick={showProgressionPicker=true},
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        ) {Text("Change progression method")}
                    if(day.slots.size>1)PursuitResponsiveActionPair(
                        primary={m->
                            TextButton(onClick={onMoveExercise(slot.slotId,-1)},
                                enabled=actions.earlier,modifier=m.heightIn(min=48.dp)) {
                                Text("Move earlier")
                            }
                        },
                        secondary={m->
                            TextButton(onClick={onMoveExercise(slot.slotId,1)},
                                enabled=actions.later,modifier=m.heightIn(min=48.dp)) {
                                Text("Move later")
                            }
                        }
                    )
                    if(block.days.size>1 && actions.edit)OutlinedButton(
                        onClick={onMoveToDay(slot.slotId)},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Move to another training day")}
                    if(actions.remove)TextButton(
                        onClick={onRemoveExercise(slot.slotId)},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Remove exercise from program")}
                    if(actions.weekOverride || actions.superset)Text(
                        if(actions.superset)
                            "Move this entire superset together, or unlink it before swapping or removing."
                        else "Review this exercise's week-specific overrides before swapping. Removing it will show every affected week first.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }
    }
    val groups=day.slots.mapNotNull{it.supersetGroup}.distinct()
    if(day.slots.size>=2 || groups.isNotEmpty()) {
        PursuitSectionHeading("Supersets",
            "Linked movements keep their authored sets and round order.")
        groups.forEach{group->
            OutlinedButton(onClick={onEditSuperset(group)},enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Edit superset · "+day.slots.filter{it.supersetGroup==group}
                    .joinToString(" / "){kotlinExerciseLabel(it.exerciseId)})
            }
        }
        if(day.slots.size>=2) {
            OutlinedButton(onClick={onEditSuperset(null)},
                enabled=nativePlanCanCreateSuperset(
                    draft,block,day,busy,allowSupersets),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Create superset")
            }
            if(!allowSupersets)Text(
                "New supersets are disabled in your training preferences.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    PursuitSectionHeading("Day tools",
        "Your changes stay in the draft until program review and Save.")
    PursuitResponsiveActionPair(
        primary={m->
            Button(onClick=onAddExercise,enabled=!busy && day.slots.size<32,
                modifier=m.heightIn(min=48.dp)){Text("Add exercise")}
        },
        secondary={m->
            OutlinedButton(onClick=onEditDay,enabled=!busy,
                modifier=m.heightIn(min=48.dp)){Text("Edit day")}
        }
    )
    TextButton(onClick=onRemoveDay,enabled=!busy,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
        Text("Remove training day")
    }
}
