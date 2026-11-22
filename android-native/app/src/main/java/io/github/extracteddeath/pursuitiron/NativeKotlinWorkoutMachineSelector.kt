package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinMachineAlternativesDomain
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutLastReferenceDomain
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutLastReferenceResult
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutReferenceKey
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPerformedSet
import kotlin.coroutines.cancellation.CancellationException

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
    val createdMachineId:String?,
    val canRetry:Boolean,
    val onRetry:()->Unit,
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
    var createdMachineId by remember(host,workoutId,custom) {
        mutableStateOf<String?>(null)
    }

    /**
     * A rejected/ambiguous write is NOT grounds for keeping a stale
     * machine-choice revision. Reopen the current owner's actual workout
     * choice and, when requested, current gym roster before offering a retry.
     * This is a read only: never replay the rejected save automatically.
     */
    suspend fun readVerifiedChoices(refreshRoster:Boolean):Boolean {
        val recovered=withContext(Dispatchers.IO) {
            host.recoverWorkoutMachineChoices(workoutId,custom)
        } as? NativeWorkoutMachineChoiceResult.Saved
        if(recovered==null) {
            saved=null
            return false
        }
        if(refreshRoster) {
            val latestRoster=withContext(Dispatchers.IO) {
                host.readMachineRoster()
            } as? NativeMachineRosterResult.Saved
            if(latestRoster==null) {
                saved=null
                return false
            }
            onRosterChanged(latestRoster.snapshot)
        }
        saved=recovered.snapshot
        return true
    }

    LaunchedEffect(host,workoutId,custom,ready,exerciseSignature) {
        saved=null
        error=null
        createdMachineId=null
        if(!ready)return@LaunchedEffect
        busy=true
        try {
            if(!readVerifiedChoices(refreshRoster=false))
                error="Saved machine choices could not be verified. Retry before recording another set."
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(_:Exception) {
            saved=null
            error="Saved machine choices could not be read. Retry verification without losing your unsaved set entries."
        } finally {
            busy=false
        }
    }

    val encoded=saved?.machines?.entries?.joinToString(";"){
        "${it.key}=${it.value}"
    }.orEmpty()
    return NativeWorkoutMachineChoicesUi(
        encoded=encoded,
        ready=ready && saved!=null,
        busy=!ready || busy,
        error=error,
        createdMachineId=createdMachineId,
        canRetry=ready && !busy,
        onRetry={
            if(ready && !busy) {
                busy=true
                error=null
                createdMachineId=null
                saved=null
                scope.launch {
                    try {
                        if(!readVerifiedChoices(refreshRoster=true))
                            error="The current workout machine choices or gym roster are unavailable. No station change was assumed."
                    } catch(cancelled:CancellationException) {
                        throw cancelled
                    } catch(_:Exception) {
                        saved=null
                        error="Machine verification failed. Your set history and entered drafts were not edited."
                    } finally {
                        busy=false
                    }
                }
            }
        },
        onSelect={exerciseId,machineId->
            val current=saved
            if(current!=null && ready && !busy) {
                busy=true
                error=null
                createdMachineId=null
                scope.launch {
                    try {
                        val result=withContext(Dispatchers.IO) {
                            host.selectWorkoutPhysicalMachine(workoutId,custom,
                                exerciseId,machineId,current.revision)
                        }
                        if(result is NativeWorkoutMachineChoiceResult.Saved) {
                            saved=result.snapshot
                        } else {
                            val refreshed=readVerifiedChoices(refreshRoster=true)
                            error=if(refreshed)
                                "Machine selection was not confirmed. The latest saved choice is shown; select again if needed."
                            else "Machine selection was not confirmed and saved choices could not be reopened. Retry verification."
                        }
                    } catch(cancelled:CancellationException) {
                        throw cancelled
                    } catch(_:Exception) {
                        // Even if a disk write succeeded before its
                        // acknowledgement was lost, never guess its result.
                        saved=null
                        error="Machine selection status is unknown. Retry verification before recording another set."
                    } finally {
                        busy=false
                    }
                }
            }
        },
        onCreate={exerciseId,label,makeDefault->
            val current=saved
            val confirmedRoster=roster
            // Never silently consume the Add tap when the gym read failed.
            // An athlete may have opened Exercise Info while roster storage
            // was unavailable; show the retry action instead of doing nothing.
            if(current==null || confirmedRoster==null) {
                error="Current workout machine choices or gym roster are unavailable. Refresh verification before adding a physical machine."
            } else if(ready && !busy) {
                busy=true
                error=null
                createdMachineId=null
                scope.launch {
                    try {
                        val result=withContext(Dispatchers.IO) {
                            host.addWorkoutPhysicalMachine(workoutId,custom,
                                exerciseId,label,makeDefault,
                                confirmedRoster.revision,current.revision)
                        }
                        when(result) {
                            is NativeWorkoutMachineCreationResult.Created -> {
                                onRosterChanged(result.roster)
                                saved=result.choice
                                // Only the exact two-owner confirmation
                                // acknowledges a newly selected station.
                                createdMachineId=result.choice.machines[exerciseId]
                            }
                            is NativeWorkoutMachineCreationResult.AddedNeedsSelection -> {
                                onRosterChanged(result.roster)
                                val refreshed=readVerifiedChoices(refreshRoster=false)
                                error=result.reason+if(refreshed)
                                    " Review the saved station and select the new machine."
                                else " Retry verification to select the new machine."
                            }
                            is NativeWorkoutMachineCreationResult.Rejected -> {
                                val refreshed=readVerifiedChoices(refreshRoster=true)
                                error=if(refreshed)
                                    "Could not add that machine: ${result.reason}. Review the refreshed gym and retry."
                                else "Could not add that machine: ${result.reason}. Retry machine verification."
                            }
                        }
                    } catch(cancelled:CancellationException) {
                        throw cancelled
                    } catch(_:Exception) {
                        saved=null
                        error="New machine status is unknown. Retry verification before adding or selecting another."
                    } finally {
                        busy=false
                    }
                }
            }
        }
    )
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
    onCreate:((String,Boolean)->Unit)?=null,
    onRetry:(()->Unit)?=null,
    retryEnabled:Boolean=false,
    error:String?=null,
    acknowledgedCreatedMachineId:String?=null,
    priorHistory:WorkoutLastReferenceResult?=null,
    priorWorkingKeys:List<WorkoutReferenceKey> = emptyList(),
    onRequestHistory:(()->Unit)?=null,
    externalOpen:Boolean=false,
    onExternalOpenConsumed:(()->Unit)?=null,
    showInlineTrigger:Boolean=true
) {
    if(config==null || (config.machines.isEmpty() && onCreate==null))return
    var open by remember(config.gymId,config.exerciseId) { mutableStateOf(false) }
    // A direct workout 'Equipment unavailable?' action opens the SAME
    // source-checked selector; it cannot silently select or create a machine.
    // Clear the one-shot request so returning to this exercise later does
    // not reopen the sheet or re-fetch Last without an athlete tap.
    LaunchedEffect(externalOpen) {
        if(externalOpen) {
            open=true
            onRequestHistory?.invoke()
            onExternalOpenConsumed?.invoke()
        }
    }
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
    // A request is not proof of a saved physical-machine change. Close only
    // after the owner-fenced writer has published the selected station.
    var pendingChoice by remember(config.gymId,config.exerciseId) {
        mutableStateOf<String?>(null)
    }
    var pendingCreation by remember(config.gymId,config.exerciseId) {
        mutableStateOf<Pair<String?,Int>?>(null)
    }
    LaunchedEffect(selectedId,config.machines,acknowledgedCreatedMachineId) {
        if(pendingChoice!=null && selectedId==pendingChoice) {
            pendingChoice=null
            open=false
        }
        val creation=pendingCreation
        if(creation!=null && config.machines.size>creation.second &&
            selectedId!=null && selectedId!=creation.first &&
            acknowledgedCreatedMachineId==selectedId &&
            config.machines.any{it.id==selectedId}) {
            // Both durable source owners must already have acknowledged this
            // new physical machine before selectedId can change.
            pendingCreation=null
            newMachineName=""
            makeDefault=false
            open=false
        }
    }
    if(showInlineTrigger) {
    TextButton(
        onClick={
            open=true
            // The exact same-day source is fetched only when this picker
            // is opened. The workout owner validates it; the sheet never
            // searches other gyms or rewrites today's issued prescription.
            onRequestHistory?.invoke()
        },
        enabled=true, // inspection remains available on finished/paused workouts
        modifier=Modifier.heightIn(min=48.dp).widthIn(max=260.dp)
            .semantics {
                contentDescription="Current physical machine: $display. Choose or add machine"
            },
    ) {
        // Selecting a station is occasional; don't make this a full-width
        // headline card above the working set. The sheet retains full actions.
        Text("Machine · $display",maxLines=1,
            overflow=TextOverflow.Ellipsis,
            style=MaterialTheme.typography.labelMedium)
    }
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
                "progression and records stay with each physical machine. "+
                "Switching does not convert or lower another machine's load.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let {Text(it,style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.error)}
            if(error!=null && onRetry!=null)
                OutlinedButton(
                    onClick=onRetry,
                    enabled=retryEnabled,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {Text("Refresh verified machine choices")}
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
                        // Keep the sheet visible until the source-backed choice
                        // revision returns. A failed save must remain actionable
                        // here, not be hidden behind a prematurely closed sheet.
                        pendingCreation=null
                        pendingChoice=machine.id
                        onSelect(machine.id)
                    },
                    enabled=enabled && machine.active && !current,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(machine.label,
                            style=MaterialTheme.typography.labelLarge)
                        Text(workoutStationLastCaption(
                            priorHistory,priorWorkingKeys,machine.id),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // Do not erase the draft before the owner-fenced roster
                        // and workout-choice writes are both confirmed. On partial
                        // save the athlete can see the new machine and select it.
                        pendingChoice=null
                        pendingCreation=selectedId to config.machines.size
                        onCreate(newMachineName.trim().ifEmpty{suggestedName},makeDefault)
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
    WorkoutLastReferenceDomain.physicalStation(previous,selectedMachineId)
