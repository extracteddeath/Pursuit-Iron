package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.CatalogMovement
import io.github.extracteddeath.pursuitiron.rebuild.KotlinMachineAlternativesDomain

private fun parseMachineLoad(value:String):BigDecimal? =
    value.trim().takeIf{it.isNotEmpty()}?.let{
        runCatching{BigDecimal(it)}.getOrNull()
    }

private fun validMachineStack(
    minimum:BigDecimal?,increment:BigDecimal?,maximum:BigDecimal?
):Boolean {
    if(minimum==null && maximum==null)return true
    return minimum!=null && increment!=null && maximum!=null &&
        minimum>=BigDecimal.ZERO && maximum>=minimum &&
        increment>BigDecimal.ZERO && maximum<=BigDecimal("100000") &&
        minimum.stripTrailingZeros().scale()<=3 &&
        increment.stripTrailingZeros().scale()<=3 &&
        maximum.stripTrailingZeros().scale()<=3 &&
        maximum-minimum<=increment.multiply(BigDecimal("99999")) &&
        // Reject a maximum the defined uniform stack can never reach.
        (maximum-minimum).remainder(increment).compareTo(BigDecimal.ZERO)==0
}

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
    var incrementText by rememberSaveable{mutableStateOf("")}
    var minimumText by rememberSaveable{mutableStateOf("")}
    var maximumText by rememberSaveable{mutableStateOf("")}
    val increment=parseMachineLoad(incrementText)
    val minimum=parseMachineLoad(minimumText)
    val maximum=parseMachineLoad(maximumText)
    val stackValid=validMachineStack(minimum,increment,maximum) &&
        (minimumText.isBlank()||minimum!=null) &&
        (maximumText.isBlank()||maximum!=null)
    val incrementValid=incrementText.isBlank()||
        (increment!=null && increment>BigDecimal.ZERO &&
            increment<=BigDecimal("100000") &&
            increment.stripTrailingZeros().scale()<=3)
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
                        val calibrated=machine.minimum!=null &&
                            machine.maximum!=null && machine.increment!=null &&
                            machine.unit!=null
                        if(calibrated &&
                            KotlinMachineAlternativesDomain.reachableCalibratedMaximum(machine))
                            Text("Verified stack: ${machine.minimum}–${machine.maximum} ${machine.unit?.name ?: ""} · steps of ${machine.increment}",
                                style=MaterialTheme.typography.bodySmall)
                        else if(calibrated)
                            Text("Calibration needs correction: this maximum cannot be reached using the confirmed step. History is preserved, but this machine is not verified for load increases.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.error)
                        else Text("Stack not verified · load increases require a confirmed machine-specific range.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        var minEdit by rememberSaveable(machine.id,roster?.revision){
                            mutableStateOf(machine.minimum?.toPlainString().orEmpty())
                        }
                        var stepEdit by rememberSaveable(machine.id,roster?.revision){
                            mutableStateOf(machine.increment?.toPlainString().orEmpty())
                        }
                        var maxEdit by rememberSaveable(machine.id,roster?.revision){
                            mutableStateOf(machine.maximum?.toPlainString().orEmpty())
                        }
                        val minValue=parseMachineLoad(minEdit)
                        val stepValue=parseMachineLoad(stepEdit)
                        val maxValue=parseMachineLoad(maxEdit)
                        val editValid=validMachineStack(minValue,stepValue,maxValue) &&
                            (minEdit.isBlank()||minValue!=null) &&
                            (maxEdit.isBlank()||maxValue!=null) &&
                            (stepEdit.isBlank()||
                                (stepValue!=null && stepValue>BigDecimal.ZERO &&
                                    stepValue.stripTrailingZeros().scale()<=3))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
                            modifier=Modifier.fillMaxWidth()) {
                            OutlinedTextField(value=minEdit,onValueChange={minEdit=it.take(16)},
                                label={Text("Min")},singleLine=true,
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                                modifier=Modifier.weight(1f))
                            OutlinedTextField(value=stepEdit,onValueChange={stepEdit=it.take(16)},
                                label={Text("Step")},singleLine=true,
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                                modifier=Modifier.weight(1f))
                            OutlinedTextField(value=maxEdit,onValueChange={maxEdit=it.take(16)},
                                label={Text("Max")},singleLine=true,
                                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                                modifier=Modifier.weight(1f))
                        }
                        OutlinedButton(
                            enabled=!busy && editValid,
                            onClick={controller.calibratePhysicalMachine(
                                exerciseId,machine.id,minValue,stepValue,maxValue)}
                        ) {Text("Save this machine's confirmed stack")}

                    }
                }
            }
            OutlinedTextField(value=label,onValueChange={label=it.take(80)},
                label={Text("New machine name")},
                supportingText={Text("For example: Machine A, Hammer Strength, or Upstairs press.")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(
                value=incrementText,
                onValueChange={incrementText=it.take(16)},
                label={Text("Machine weight increment (optional)")},
                supportingText={Text("For example: 5 or 10. Leave blank if the stack is not calibrated.")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                isError=!incrementValid,
                singleLine=true,modifier=Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
                modifier=Modifier.fillMaxWidth()){
                OutlinedTextField(value=minimumText,onValueChange={minimumText=it.take(16)},
                    label={Text("Min")},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    modifier=Modifier.weight(1f))
                OutlinedTextField(value=maximumText,onValueChange={maximumText=it.take(16)},
                    label={Text("Max")},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    modifier=Modifier.weight(1f))
            }
            Text("Specify minimum, increment and maximum only if you have checked this physical machine's stack. Otherwise its first working weight remains manual.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick={
                    controller.addPhysicalMachine(exerciseId,label,increment,minimum,maximum)
                    label="Machine ${(('B'.code+(config?.machines?.size?:0)).coerceAtMost('Z'.code)).toChar()}"
                },
                enabled=!busy && roster!=null && label.isNotBlank() &&
                    incrementValid && stackValid && (config?.machines?.size?:0)<32,
                modifier=Modifier.fillMaxWidth()
            ) {Text("Add machine")}
            Text("Each machine keeps its own weight history and progression. A different machine is not a strength loss.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
