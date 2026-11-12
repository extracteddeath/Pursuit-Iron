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
    val onSelect:(String,String)->Unit
)

@Composable
internal fun rememberNativeWorkoutMachineChoices(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    custom:Boolean,
    ready:Boolean,
    exerciseSignature:String
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
