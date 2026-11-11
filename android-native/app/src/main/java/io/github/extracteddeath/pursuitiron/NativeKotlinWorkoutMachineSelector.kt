package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutLastReferenceResult

/** Stable compact saveable per-exercise choice; IDs cannot contain separators. */
internal fun workoutMachineChoice(encoded:String,exerciseId:String,defaultId:String?):String? =
    encoded.split(";").firstOrNull {it.substringBefore("=")==exerciseId &&
        it.contains("=")}?.substringAfter("=")?.takeIf{it.isNotBlank()}?:defaultId

internal fun updateWorkoutMachineChoice(
    encoded:String,exerciseId:String,machineId:String
):String {
    val old=encoded.split(";").filter{
        it.contains("=")&&it.substringBefore("=")!=exerciseId
    }
    return (old+"$exerciseId=$machineId").joinToString(";")
}

/** One shared Material 3 selector for both generated and custom workout journals. */
@Composable
internal fun NativeKotlinWorkoutMachineSelector(
    config:KotlinGymMachineAlternatives?,
    selectedId:String?,
    enabled:Boolean,
    onSelect:(String)->Unit
) {
    if(config==null||config.machines.isEmpty())return
    PursuitCard {
        Text("Physical machine",style=MaterialTheme.typography.titleMedium)
        Text("This exercise is shared, but each machine has its own weight history and progression.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            config.machines.filter {it.active||it.id==selectedId}.forEach {machine->
                FilterChip(selected=machine.id==selectedId,
                    enabled=enabled&&machine.active,
                    onClick={onSelect(machine.id)},
                    label={Text(machine.label)})
            }
        }
        val chosen=config.machines.firstOrNull{it.id==selectedId}
        Text(
            if(chosen!=null)
                "Selected: ${chosen.label}. Enter the actual weight for this machine; no conversion from another machine is assumed."
            else "Choose a machine before logging this set.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A selected physical machine cannot display another stack as "Last".
 * The authoritative reference domain has already authenticated same-gym,
 * same-day workout history. This enforces the additional machine boundary.
 */
internal fun physicalMachineLastReferences(
    previous:WorkoutLastReferenceResult?,
    selectedMachineId:String?
):WorkoutLastReferenceResult? =
    if(selectedMachineId==null)previous
    else if(previous is WorkoutLastReferenceResult.Ready)
        WorkoutLastReferenceResult.Ready(previous.sets.filterValues{
            it.actual?.machineInstanceId==selectedMachineId
        })
    else previous
