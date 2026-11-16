package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** The same stateless day controls are used inline and beside the outline. */
@Composable
internal fun NativeKotlinPlanDayEditor(
    draft:CustomTrainingCycle,block:CustomTrainingBlock,day:CustomTrainingDay,
    busy:Boolean,allowSupersets:Boolean,
    onEditExercise:(String)->Unit,onSwapExercise:(String)->Unit,
    onMoveExercise:(String,Int)->Unit,onEditSuperset:(String?)->Unit,
    onEditDay:()->Unit,onAddExercise:()->Unit
) {
    val supersetGroups=day.slots.mapNotNull{it.supersetGroup}.distinct()
    if(day.slots.size>=2 || supersetGroups.isNotEmpty()) {
        TextButton(onClick={onEditSuperset(null)},
            enabled=!busy && allowSupersets &&
                day.slots.count{it.supersetGroup==null && it.sets.any{set->set.type==DraftSetType.WORKING}}>=2,
            modifier=Modifier.heightIn(min=48.dp)){Text("Create superset")}
        supersetGroups.forEach{group->
            OutlinedButton(onClick={onEditSuperset(group)},
                enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Edit superset · "+day.slots.filter{it.supersetGroup==group}
                    .joinToString(" / "){kotlinExerciseLabel(it.exerciseId)})
            }
        }
    }
    if (day.slots.isNotEmpty()) {
        day.slots.forEachIndexed { slotIndex, slot ->
            Text("${kotlinExerciseLabel(slot.exerciseId)} · ${slot.sets.size} sets" +
                if (slot.pinned) " · Pinned" else "",
                style = MaterialTheme.typography.bodyMedium)
            val hasProtectedWeekEdit=draft.weekOverrides[
                block.blockId].orEmpty().any {
                it.weekday==day.weekday && it.slotId==slot.slotId
            }
            Row(Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(PursuitSpace.sm)) {
                TextButton(
                    onClick = { onEditExercise(slot.slotId) },
                    enabled = !busy
                ) { Text("Edit sets") }
                TextButton(
                    onClick={onSwapExercise(slot.slotId)},
                    enabled=!busy && slot.supersetGroup==null &&
                        !hasProtectedWeekEdit
                ) {Text("Swap")}
                TextButton(onClick = {
                    onMoveExercise(slot.slotId,-1)
                }, enabled = !busy && slotIndex > 0) {
                    Text("Earlier")
                }
                TextButton(onClick = {
                    onMoveExercise(slot.slotId,1)
                }, enabled = !busy &&
                    slotIndex < day.slots.lastIndex) {
                    Text("Later")
                }
            }
            if(hasProtectedWeekEdit || slot.supersetGroup!=null)
                Text(if(hasProtectedWeekEdit)
                    "Swap requires reviewing this exercise's week-specific overrides first."
                    else "Swap requires unlinking this supersetted exercise first.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text("No exercises yet. This day is an unfinished draft.",
            style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PursuitSpace.sm)) {
        TextButton(onClick = {
            onEditDay()
        }, enabled = !busy) { Text("Edit day") }
        TextButton(onClick = {
            onAddExercise()
        }, enabled = !busy && day.slots.size < 32) {
            Text("Add exercise")
        }
    }
}
