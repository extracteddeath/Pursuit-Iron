package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.CatalogMovement

/**
 * Owner-scoped native gym A/B editor. Labels and defaults apply to a physical
 * machine; the logical exercise remains one training/volume identity.
 */
@Composable
internal fun NativeKotlinMachineAlternativesPanel(
    controller:NativeKotlinGymSourceController,
    gymId:String,
    catalog:List<CatalogMovement>,
    roster:NativeMachineRosterSnapshot?,
    busy:Boolean
) {
    var query by rememberSaveable{mutableStateOf("")}
    var exerciseId by rememberSaveable{mutableStateOf("")}
    var label by rememberSaveable{mutableStateOf("Machine A")}
    val matches=catalog.asSequence().filter {ex->
        query.isNotBlank() && (ex.exerciseId.contains(query.trim(),ignoreCase=true) ||
            kotlinExerciseLabel(ex.exerciseId).contains(query.trim(),ignoreCase=true))
    }.distinctBy {it.exerciseId}.take(6).toList()
    val config=roster?.configurations?.firstOrNull{
        it.gymId==gymId && it.exerciseId==exerciseId
    }

    PursuitCard {
        PursuitSectionHeading("Physical machines A / B",
            "The same exercise can use differently weighted machines. Your gym remembers its default; completed workouts never change.")
        OutlinedTextField(
            value=query,onValueChange={query=it.take(80)},
            label={Text("Find a machine exercise")},
            supportingText={Text("Choose the logical exercise, then label its actual stations.")},
            singleLine=true,modifier=Modifier.fillMaxWidth()
        )
        matches.forEach {ex->
            OutlinedButton(onClick={
                exerciseId=ex.exerciseId
                query=kotlinExerciseLabel(ex.exerciseId)
            },modifier=Modifier.fillMaxWidth(),enabled=!busy) {
                Text(kotlinExerciseLabel(ex.exerciseId))
            }
        }
        if(exerciseId.isNotBlank()) {
            Text("Exercise: ${kotlinExerciseLabel(exerciseId)}",
                style=MaterialTheme.typography.titleMedium)
            if(config==null||config.machines.isEmpty()) {
                Text("No physical machine alternatives saved.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                config.machines.forEach {machine->
                    key(machine.id) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(machine.label,style=MaterialTheme.typography.titleSmall)
                                Text(if(machine.id==config.defaultMachineId)
                                    "Default in this gym"
                                    else if(machine.active) "Alternative"
                                    else "Inactive · past records retained",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            if(machine.active&&machine.id!=config.defaultMachineId)
                                TextButton(
                                    onClick={controller.defaultPhysicalMachine(
                                        exerciseId,machine.id)},
                                    enabled=!busy
                                ) {Text("Set default")}
                        }
                        if(machine.id!=config.defaultMachineId)
                            TextButton(
                                onClick={controller.setPhysicalMachineActive(
                                    exerciseId,machine.id,!machine.active)},
                                enabled=!busy
                            ) {
                                Text(if(machine.active)"Deactivate" else "Reactivate")
                            }
                        machine.increment?.let {
                            Text("Verified increment: ${it.toPlainString()} ${machine.unit?.name ?: ""}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedTextField(value=label,onValueChange={label=it.take(80)},
                label={Text("New machine name")},
                supportingText={Text("For example: Machine A, Hammer Strength, or Upstairs press.")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            Button(
                onClick={
                    controller.addPhysicalMachine(exerciseId,label)
                    label="Machine ${(('B'.code+(config?.machines?.size?:0)).coerceAtMost('Z'.code)).toChar()}"
                },
                enabled=!busy && roster!=null && label.isNotBlank() &&
                    (config?.machines?.size?:0)<32,
                modifier=Modifier.fillMaxWidth()
            ) {Text("Add machine")}
            Text("Each machine keeps its own weight history and progression. A different machine is not a strength loss.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
