package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The athlete's immediate equipment problem has two DIFFERENT authorities:
 * selecting a physical Machine A/B reuses the same exercise but changes the
 * station-specific draft/Last owner; replacing the movement requires a fresh
 * source-verified workout-only prescription and a new exercise identity.
 * No direct load, target, journal or program edits originate here.
 */
internal data class NativeKotlinEquipmentRecoveryAvailability(
    val canChangeStation:Boolean,
    val canReplaceExercise:Boolean,
    val reason:String?
)

internal object NativeKotlinEquipmentRecoveryPolicy {
    fun review(
        activeWorkout:Boolean,
        hasPhysicalMachineConfig:Boolean,
        hasRecordedExerciseWork:Boolean,
        linkedSuperset:Boolean=false
    ):NativeKotlinEquipmentRecoveryAvailability {
        val canStation=activeWorkout && hasPhysicalMachineConfig
        val canReplace=activeWorkout && !hasRecordedExerciseWork && !linkedSuperset
        val reason=when {
            !activeWorkout -> "Finish or recovery review is read-only."
            hasRecordedExerciseWork ->
                "This exercise already has recorded work. Its saved sets and history are protected."
            linkedSuperset ->
                "This exercise belongs to a linked superset. Change the program or review the whole group instead."
            else -> null
        }
        return NativeKotlinEquipmentRecoveryAvailability(canStation,canReplace,reason)
    }
}

/**
 * Visible in Focus AND All Sets. A one-tap disclosure offers the two existing
 * verified paths rather than hiding swap behind an icon or treating Machine B
 * as a different exercise. No buttons operate until the caller has a valid
 * current journal; the owner re-verifies the selected change after a tap.
 */
@Composable
internal fun NativeKotlinEquipmentRecoveryControl(
    scopeKey:String,
    availability:NativeKotlinEquipmentRecoveryAvailability,
    busy:Boolean,
    onChangeStation:()->Unit,
    onReplaceExercise:()->Unit
) {
    var expanded by rememberSaveable(scopeKey){mutableStateOf(false)}
    OutlinedButton(
        onClick={expanded=!expanded},
        enabled=!busy,
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
    ) {
        Text(if(expanded)"Hide equipment options"
            else "Equipment unavailable? Change machine or exercise")
    }
    if(expanded)PursuitCard {
        Text("Equipment changed?",
            style=MaterialTheme.typography.titleMedium)
        Text("Choose how to continue this workout. Your saved program and "+
            "previously logged sets will not be overwritten.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick={
                expanded=false
                onChangeStation()
            },
            enabled=!busy && availability.canChangeStation,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
        ) {
            Column(Modifier.fillMaxWidth()){
                Text("Same lift · different physical machine")
                Text("Choose or add Machine A/B. Loads, Last and PRs remain "+
                    "separate per physical station.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if(!availability.canChangeStation)Text(
            "No physical machine selector applies to this lift. For a "+
                "different exercise, choose a verified replacement.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick={
                expanded=false
                onReplaceExercise()
            },
            enabled=!busy && availability.canReplaceExercise,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
        ) {
            Column(Modifier.fillMaxWidth()){
                Text("Different exercise · workout only")
                Text("Choose an available movement, then explicitly author "+
                    "new loads, reps and effort. The program stays unchanged.",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        availability.reason?.let{
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick={expanded=false},
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text("Back to workout")
        }
    }
}
