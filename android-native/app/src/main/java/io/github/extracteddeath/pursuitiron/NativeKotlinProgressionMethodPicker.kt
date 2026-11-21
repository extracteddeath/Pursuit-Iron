package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** A quiet, optional Plan editor action: progression is not an in-workout
 * machine picker or an exercise-headline badge. The actual change is saved
 * by the native revision-fenced custom-cycle controller after confirmation. */
internal fun savedProgressionLabel(method:SavedProgressionMethod?):String =
    when(method) {
        null -> "Program default"
        SavedProgressionMethod.AUTO -> "Auto"
        SavedProgressionMethod.DOUBLE -> "Double progression"
        SavedProgressionMethod.DYNAMIC -> "Dynamic · per set"
        SavedProgressionMethod.LADDER -> "Rep ladder"
        SavedProgressionMethod.LINEAR -> "Linear"
        SavedProgressionMethod.WAVE -> "Weekly wave"
        SavedProgressionMethod.E1RM -> "e1RM coaching"
        SavedProgressionMethod.MANUAL -> "Manual logging"
        SavedProgressionMethod.REP_TARGETS -> "Rep targets only"
        SavedProgressionMethod.ASSISTANCE_DOUBLE -> "Assisted machine"
        SavedProgressionMethod.BODYWEIGHT_REPS -> "Bodyweight reps"
    }

private fun savedProgressionExplanation(method:SavedProgressionMethod?):String =
    when(method) {
        null -> "Use the current program's default without locking a method."
        SavedProgressionMethod.AUTO ->
            "Choose a simple compatible progression from this exercise and phase."
        SavedProgressionMethod.DOUBLE ->
            "Raise the working weight only after every set reaches its rep ceiling."
        SavedProgressionMethod.DYNAMIC ->
            "Each working set earns its own next available weight."
        SavedProgressionMethod.LADDER ->
            "Build rep targets; after all sets reach the ceiling, increase load and reset reps."
        SavedProgressionMethod.LINEAR ->
            "Earn one physical load increment when all sets reach the rep floor."
        SavedProgressionMethod.WAVE ->
            "Keep an explicitly authored weekly loading sequence. No invented percentages."
        SavedProgressionMethod.E1RM ->
            "Specialist strength coaching review; requires valid comparative effort evidence."
        SavedProgressionMethod.MANUAL ->
            "Preserve prescribed working loads and record your actual results."
        SavedProgressionMethod.REP_TARGETS ->
            "Advance approved rep targets without changing weight."
        SavedProgressionMethod.ASSISTANCE_DOUBLE ->
            "After all sets hit the rep ceiling, offer one less-assistance machine setting."
        SavedProgressionMethod.BODYWEIGHT_REPS ->
            "Advance unweighted bodyweight rep targets without inventing external load."
    }

/** Independently review each possible choice against the authoritative
 * WHOLE cycle; do not duplicate exercise/equipment eligibility in Compose. */
internal fun nativeProgressionOptions(
    draft:CustomTrainingCycle,blockId:String,weekday:Int,slotId:String
):Map<SavedProgressionMethod,Boolean> {
    val key=ProgramProgressionSlotKey(blockId,weekday,slotId)
    return SavedProgressionMethod.entries.associateWith{method->
        val revised=draft.copy(blocks=draft.blocks.map{block->
            if(block.blockId!=blockId)block else block.copy(
                days=block.days.map{day->
                    if(day.weekday!=weekday)day else day.copy(
                        slots=day.slots.map{slot->
                            if(slot.slotId!=slotId)slot
                            else slot.copy(progressionMethod=method)
                        })
                })
        })
        val review=BasicProgramProgressionPlanDomain.review(revised)
            as? ProgramProgressionPlan.Ready
        review?.assignments?.singleOrNull{it.key==key}
            ?.savedMethod==method
    }
}

@Composable
internal fun NativeKotlinProgressionMethodPicker(
    draft:CustomTrainingCycle,
    blockId:String,
    weekday:Int,
    slotId:String,
    busy:Boolean,
    onDismiss:()->Unit,
    onSave:(SavedProgressionMethod?)->Unit
) {
    val slot=draft.blocks.singleOrNull{it.blockId==blockId}
        ?.days?.singleOrNull{it.weekday==weekday}
        ?.slots?.singleOrNull{it.slotId==slotId}
    if(slot==null)return
    val available=remember(draft.cycleId,draft.revision,blockId,weekday,slotId){
        nativeProgressionOptions(draft,blockId,weekday,slotId)
    }
    var proposed by remember(draft.cycleId,draft.revision,blockId,weekday,slotId) {
        mutableStateOf(slot.progressionMethod)
    }
    val candidate=proposed
    val allowed=candidate==null || available[candidate]==true
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text("Progression method")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)){
                Text(kotlinExerciseLabel(slot.exerciseId),
                    style=MaterialTheme.typography.titleSmall)
                Text("Changing a program method needs another full-cycle review and approval. Saved history and issued workouts will not change.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                    // Null is a genuine legacy/program-default choice, not
                    // a hidden alias of Double or Manual.
                    val choices:List<SavedProgressionMethod?> =
                        listOf(null)+SavedProgressionMethod.entries
                    choices.forEach {method ->
                        val optionAllowed=method==null || available[method]==true
                        Row(
                            modifier=Modifier.fillMaxWidth().clickable(
                                enabled=!busy && optionAllowed
                            ){proposed=method},
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            RadioButton(selected=proposed==method,
                                onClick={proposed=method},
                                enabled=!busy && optionAllowed)
                            Column(Modifier.weight(1f)) {
                                Text(savedProgressionLabel(method),
                                    style=MaterialTheme.typography.bodyMedium)
                                Text(savedProgressionExplanation(method),
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton={
            Button(onClick={onSave(proposed)},
                enabled=!busy && allowed && proposed!=slot.progressionMethod) {
                Text("Save method")
            }
        },
        dismissButton={
            TextButton(onClick=onDismiss,enabled=!busy){Text("Cancel")}
        }
    )
}
