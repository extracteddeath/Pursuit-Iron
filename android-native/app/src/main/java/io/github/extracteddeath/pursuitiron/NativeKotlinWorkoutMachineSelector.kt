package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinMachineAlternativesDomain
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutLastReferenceResult
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPerformedSet

/**
 * A screen-local projection of an owner-fenced, cold-replayable private file.
 * The snapshot is never itself permission to log: journal writes are still
 * verified by the independent workout authority.
 */
internal data class NativeWorkoutMachineChoicesUi(
    val encoded:String,
    val ready:Boolean,
    val busy:Boolean,
    val error:String?,
    val onSelect:(String,String)->Unit,
    val onCreate:(String,String,Boolean)->Unit
)

@Composable
internal fun rememberNativeWorkoutMachineChoices(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    custom:Boolean,
    ready:Boolean,
    exerciseSignature:String,
    roster:NativeMachineRosterSnapshot?,
    onRosterChanged:(NativeMachineRosterSnapshot)->Unit
):NativeWorkoutMachineChoicesUi {
    val scope=rememberCoroutineScope()
    var saved by remember(host,workoutId,custom) {
        mutableStateOf<NativeWorkoutMachineChoiceSnapshot?>(null)
    }
    var busy by remember(host,workoutId,custom){mutableStateOf(false)}
    var error by remember(host,workoutId,custom){mutableStateOf<String?>(null)}
    LaunchedEffect(host,workoutId,custom,ready,exerciseSignature) {
        saved=null
        error=null
        if(!ready)return@LaunchedEffect
        busy=true
        val recovered=withContext(Dispatchers.IO) {
            host.recoverWorkoutMachineChoices(workoutId,custom)
        }
        if(recovered is NativeWorkoutMachineChoiceResult.Saved)
            saved=recovered.snapshot
        else error="Machine selection could not be verified. Recorded sets are unchanged."
        busy=false
    }
    val encoded=saved?.machines?.entries?.joinToString(";"){
        "${it.key}=${it.value}"
    }.orEmpty()
    return NativeWorkoutMachineChoicesUi(
        encoded,ready && saved!=null,!ready || busy,error,
        onSelect={exerciseId,machineId->
            val current=saved
            if(current!=null && ready && !busy) {
                busy=true
                error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO){
                        host.selectWorkoutPhysicalMachine(workoutId,custom,
                            exerciseId,machineId,current.revision)
                    }
                    if(result is NativeWorkoutMachineChoiceResult.Saved)
                        saved=result.snapshot
                    else error="Machine selection was not saved. Reopen this workout to verify the current choice."
                    busy=false
                }
            }
        },
        onCreate={exerciseId,label,makeDefault->
            val current=saved
            val confirmedRoster=roster
            if(current!=null && confirmedRoster!=null && ready && !busy) {
                busy=true
                error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO) {
                        host.addWorkoutPhysicalMachine(workoutId,custom,
                            exerciseId,label,makeDefault,
                            confirmedRoster.revision,current.revision)
                    }
                    when(result) {
                        is NativeWorkoutMachineCreationResult.Created -> {
                            onRosterChanged(result.roster)
                            saved=result.choice
                        }
                        is NativeWorkoutMachineCreationResult.AddedNeedsSelection -> {
                            onRosterChanged(result.roster)
                            error=result.reason
                        }
                        is NativeWorkoutMachineCreationResult.Rejected ->
                            error="Could not add that machine: ${result.reason}. "+
                                "Your logged sets were not changed."
                    }
                    busy=false
                }
            }
        })
}

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


/**
 * The single machine-aware *initial* editor value for both Kotlin journals.
 * A saved actual belongs to the machine on which it was performed, not
 * necessarily the machine currently selected for a new/corrected set.
 * Never transfer A's load to B or turn a coaching recommendation into an
 * editable target. This does not modify the issued prescription.
 */
internal fun initialMachineScopedLoad(
    issued:DraftSetLoad,
    saved:KotlinPerformedSet?,
    selectedMachineId:String?
):String {
    if(saved!=null)
        return if(saved.machineInstanceId==selectedMachineId)
            saved.load?.toPlainString().orEmpty() else ""
    return if(selectedMachineId!=null) ""
        else (issued as? DraftSetLoad.Verified)?.value?.toPlainString().orEmpty()
}

/**
 * Compact Material 3 entry point in both Kotlin workout types. The physical
 * machine picker is a sheet, not another permanent card above working sets.
 * It only selects an existing verified gym machine; a tap never rewrites the
 * issued prescription, a completed set, another station's Last, or gym defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeKotlinWorkoutMachineSelector(
    config:KotlinGymMachineAlternatives?,
    selectedId:String?,
    enabled:Boolean,
    onSelect:(String)->Unit,
    onCreate:((String,Boolean)->Unit)?=null
) {
    if(config==null || (config.machines.isEmpty() && onCreate==null))return
    var open by remember(config.gymId,config.exerciseId) { mutableStateOf(false) }
    val selected=config.machines.firstOrNull {it.id==selectedId}
    val display=selected?.label ?: "Choose machine"
    val active=config.machines.filter {it.active || it.id==selectedId}
    val existingNames=config.machines.map{it.label.trim().lowercase()}.toSet()
    val suggestedName=('A'..'Z').map{"Machine $it"}
        .firstOrNull{it.lowercase() !in existingNames} ?: "Other machine"
    var newMachineName by remember(config.gymId,config.exerciseId){
        mutableStateOf("")
    }
    var makeDefault by remember(config.gymId,config.exerciseId){
        mutableStateOf(false)
    }
    OutlinedButton(
        onClick={open=true},
        enabled=true, // inspection remains available on finished/paused workouts
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
    ) {
        Text("Physical machine · $display",
            maxLines=2,style=MaterialTheme.typography.labelLarge)
    }
    if(open)ModalBottomSheet(onDismissRequest={open=false}) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal=20.dp,vertical=12.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Text("Choose physical machine",style=MaterialTheme.typography.titleLarge)
            Text("Same exercise, different weight stacks. Your actual load, Last, "+
                "progression and records stay with each physical machine.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(selected!=null && !selected.active)Text(
                "The previously selected machine is inactive. Select an active "+
                    "machine before recording another set.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.error)
            active.forEach {machine->
                val current=machine.id==selectedId
                OutlinedButton(
                    onClick={
                        // Existing owner-fenced Kotlin writer confirms this choice.
                        onSelect(machine.id)
                        open=false
                    },
                    enabled=enabled && machine.active && !current,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(machine.label,
                            style=MaterialTheme.typography.labelLarge)
                        if(current)Text("Selected for this workout",
                            style=MaterialTheme.typography.bodySmall)
                        else if(!machine.active)Text(
                            "Inactive · saved sets remain on this machine",
                            style=MaterialTheme.typography.bodySmall)
                        else Text("Switch unfinished sets to this machine",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(onCreate!=null) {
                HorizontalDivider()
                Text("Different machine here?",
                    style=MaterialTheme.typography.titleMedium)
                Text("Add it now without leaving the workout. Each physical "+
                    "machine keeps its own loads and performance history.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value=newMachineName,onValueChange={newMachineName=it.take(80)},
                    label={Text("Machine name")},
                    placeholder={Text(suggestedName)},
                    singleLine=true,modifier=Modifier.fillMaxWidth(),
                    enabled=enabled
                )
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked=makeDefault,
                        onCheckedChange={makeDefault=it},
                        enabled=enabled)
                    Text("Use as the default for this exercise at this gym",
                        style=MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick={
                        onCreate(newMachineName.trim().ifEmpty{suggestedName},makeDefault)
                        newMachineName=""
                        makeDefault=false
                    },
                    enabled=enabled && config.machines.size<32,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {Text("Add machine & use now")}
            } else Text(
                "Different machine not saved? Add it under Your gym → "+
                    "Physical machines A/B. Existing sets are not reassigned.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick={open=false},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) { Text("Back to workout") }
            Spacer(Modifier.height(12.dp))
        }
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
    if(previous is WorkoutLastReferenceResult.Ready) {
        // Null is historical legacy only, not permission to borrow A or B.
        val requested=KotlinMachineAlternativesDomain.normalizeMachineId(selectedMachineId)
        val matching=if(previous.machineSets.isNotEmpty())
            previous.machineSets.filterKeys { it.machineId==requested }
                .mapKeys { it.key.key }
        else previous.sets.filterValues {
            KotlinMachineAlternativesDomain.normalizeMachineId(
                it.actual?.machineInstanceId)==requested
        }
        WorkoutLastReferenceResult.Ready(matching)
    } else previous
