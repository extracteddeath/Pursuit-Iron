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
import io.github.extracteddeath.pursuitiron.rebuild.CatalogPhysicalMachineEligibility

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
    busy:Boolean,
    focusedMachineExerciseId:String?=null,
    onMachineFocusConsumed:()->Unit={}
) {
    var query by rememberSaveable{mutableStateOf("")}
    var exerciseId by rememberSaveable{mutableStateOf("")}
    var stalePlanFocus by remember(gymId){mutableStateOf(false)}
    LaunchedEffect(gymId,focusedMachineExerciseId,catalog) {
        val focused=focusedMachineExerciseId ?:return@LaunchedEffect
        // The current Gym source, not an ID carried from Plan navigation,
        // decides whether the movement is still an eligible machine stack.
        val option=catalog.singleOrNull {
            it.exerciseId==focused &&
                CatalogPhysicalMachineEligibility.eligible(it)
        }
        stalePlanFocus=option==null
        if(option!=null) {
            exerciseId=option.exerciseId
            query=kotlinExerciseLabel(option.exerciseId)
        } else {
            // Never leave another, previously selected machine editable when
            // the linked Plan movement is no longer in this reviewed catalog.
            exerciseId=""
            query=""
        }
        onMachineFocusConsumed()
    }
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
    val verifiedMachineIds=CatalogPhysicalMachineEligibility
        .eligibleExerciseIds(catalog)
    val matches=catalog.asSequence().filter {ex->
        ex.exerciseId in verifiedMachineIds &&
            query.isNotBlank() &&
            (ex.exerciseId.contains(query.trim(),ignoreCase=true) ||
            kotlinExerciseLabel(ex.exerciseId).contains(query.trim(),ignoreCase=true))
    }.take(6).toList()
    val config=roster?.configurations?.firstOrNull{
        it.gymId==gymId && it.exerciseId==exerciseId &&
            it.exerciseId in verifiedMachineIds
    }
    // Plan -> Gym focus may resolve before roster data is loaded. Once the
    // exact gym is available, avoid suggesting an already existing Machine A.
    LaunchedEffect(gymId,exerciseId,roster?.revision) {
        if(exerciseId.isNotBlank()) {
            val existing=roster?.configurations?.firstOrNull {
                it.gymId==gymId && it.exerciseId==exerciseId
            }?.machines.orEmpty().map{it.label.trim().lowercase()}.toSet()
            if(label.trim().lowercase() in existing) {
                label=('A'..'Z').map{"Machine $it"}
                    .firstOrNull{it.lowercase() !in existing} ?:"Other machine"
            }
        }
    }
    // A saved group must be discoverable without guessing the logical
    // exercise name. The roster is already the verified current gym snapshot.
    val savedGroups=roster?.configurations.orEmpty().filter {
        it.gymId==gymId && it.exerciseId in verifiedMachineIds &&
            it.machines.isNotEmpty()
    }.sortedBy { kotlinExerciseLabel(it.exerciseId) }
    val historicalGroups=roster?.configurations.orEmpty().filter {
        it.gymId==gymId && it.exerciseId !in verifiedMachineIds &&
            it.machines.isNotEmpty()
    }
    var showAllSaved by rememberSaveable(gymId) { mutableStateOf(false) }

    PursuitCard {
        PursuitSectionHeading("Physical machines A / B",
            "The same exercise can use differently weighted machines. Your gym remembers its default; completed workouts never change.")
        if(stalePlanFocus)Text(
            "That Plan exercise is no longer verified as a machine/cable movement "+
                "at this gym. Search current equipment instead.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.error)
        if(historicalGroups.isNotEmpty()) {
            Text("${historicalGroups.size} historical machine groups retained",
                style=MaterialTheme.typography.titleSmall)
            Text("Their physical records remain separate in History, but the "+
                "current reviewed catalog does not qualify these exercises "+
                "for Machine A/B editing.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            historicalGroups.take(5).forEach {old->
                Text(kotlinExerciseLabel(old.exerciseId)+" · History only",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(savedGroups.isNotEmpty()) {
            Text("Saved Machine A/B groups at this gym",
                style=MaterialTheme.typography.titleMedium)
            savedGroups.take(if(showAllSaved)savedGroups.size else 5).forEach {saved->
                val currentDefault=saved.machines.firstOrNull {
                    it.id==saved.defaultMachineId
                }?.label
                OutlinedButton(
                    onClick={
                        exerciseId=saved.exerciseId
                        query=kotlinExerciseLabel(saved.exerciseId)
                        val used=saved.machines.map {it.label.trim().lowercase()}.toSet()
                        label=('A'..'Z').map {"Machine $it"}
                            .firstOrNull {it.lowercase() !in used} ?: "Other machine"
                    },
                    enabled=!busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(kotlinExerciseLabel(saved.exerciseId),
                            style=MaterialTheme.typography.labelLarge)
                        Text("${saved.machines.size} physical machines"+
                            (currentDefault?.let{" · Default: $it"} ?:" · No default"),
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(savedGroups.size>5)TextButton(
                onClick={showAllSaved=!showAllSaved},
                modifier=Modifier.heightIn(min=48.dp)
            ) {
                Text(if(showAllSaved)"Show fewer saved groups"
                     else "Show all ${savedGroups.size} saved groups")
            }
        } else if(roster!=null)Text(
            "No physical machine groups yet. Search for an exercise below to add Machine A.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value=query,onValueChange={
                query=it.take(80)
                stalePlanFocus=false
            },
            label={Text("Find a machine exercise")},
            supportingText={Text("Choose the logical exercise, then label its actual stations.")},
            singleLine=true,modifier=Modifier.fillMaxWidth()
        )
        matches.filter {it.exerciseId!=exerciseId}.forEach {ex->
            OutlinedButton(onClick={
                exerciseId=ex.exerciseId
                query=kotlinExerciseLabel(ex.exerciseId)
                label="Machine A"
            },modifier=Modifier.fillMaxWidth(),enabled=!busy) {
                Text(kotlinExerciseLabel(ex.exerciseId))
            }
        }
        if(exerciseId.isNotBlank()) {
            Text("Exercise: ${kotlinExerciseLabel(exerciseId)}",
                style=MaterialTheme.typography.titleMedium)
            if(exerciseId !in verifiedMachineIds) {
                Text("This exercise is not currently verified as a machine "+
                    "or cable-stack movement. Its old Machine A/B records "+
                    "are read-only and never reclassified.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
            } else if(config==null||config.machines.isEmpty()) {
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
                        NativeKotlinMachineRenameControl(
                            gymId=gymId,machine=machine,peers=config.machines,
                            busy=busy,onRename={newLabel->
                                controller.renamePhysicalMachine(exerciseId,machine.id,newLabel)
                            }
                        )
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
                        var showStackEditor by rememberSaveable(gymId,machine.id) {
                            mutableStateOf(false)
                        }
                        TextButton(
                            onClick={showStackEditor=!showStackEditor},
                            enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp)
                        ) { Text(if(showStackEditor) "Hide stack calibration" else "Edit verified weight stack") }
                        if(showStackEditor) {
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
            }
            OutlinedTextField(value=label,onValueChange={label=it.take(80)},
                label={Text("New machine name")},
                supportingText={Text("For example: Machine A, Hammer Strength, or Upstairs press.")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            var showNewStackCalibration by rememberSaveable(gymId,exerciseId) {
                mutableStateOf(false)
            }
            TextButton(onClick={showNewStackCalibration=!showNewStackCalibration},
                enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {
                Text(if(showNewStackCalibration) "Hide optional stack details"
                    else "Add verified stack details (optional)")
            }
            if(showNewStackCalibration) {
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
            }
            Button(
                onClick={
                    // A newly encountered machine can be logged without guessing
                    // a resistance conversion or requiring a calibration form.
                    controller.addPhysicalMachine(exerciseId,label,
                        if(showNewStackCalibration) increment else null,
                        if(showNewStackCalibration) minimum else null,
                        if(showNewStackCalibration) maximum else null)
                    val usedNames=config?.machines.orEmpty().map {
                        it.label.trim().lowercase()
                    }.toSet()+label.trim().lowercase()
                    label=('A'..'Z').map{"Machine $it"}
                        .firstOrNull{it.lowercase() !in usedNames} ?:"Other machine"
                },
                enabled=!busy && roster!=null &&
                    exerciseId in verifiedMachineIds && label.isNotBlank() &&
                    (!showNewStackCalibration || (incrementValid && stackValid)) &&
                    (config?.machines?.size?:0)<32,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Add physical machine")}
            Text("Each machine keeps its own weight history and progression. A different machine is not a strength loss.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
