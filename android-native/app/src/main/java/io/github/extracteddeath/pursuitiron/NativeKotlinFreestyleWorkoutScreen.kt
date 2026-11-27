package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private fun freestyleId(prefix:String)=prefix+"-"+UUID.randomUUID().toString().replace("-","")

/** A separate entry mode, not a second number, history or program engine.
 * All factual mutations use the same owner-fenced freestyle store. */
@Composable
internal fun NativeKotlinFreestyleWorkoutScreen(
    host:NativeKotlinCandidateHost,
    onClose:()->Unit,
    onViewHistory:(String)->Unit
) {
    val scope=rememberCoroutineScope()
    var activeId by rememberSaveable{mutableStateOf<String?>(null)}
    var current by remember(host){mutableStateOf<KotlinFreestyleWorkout?>(null)}
    var inventory by remember(host){mutableStateOf<NativeFreestyleInventoryResult?>(null)}
    var library by remember(host){mutableStateOf<NativeFreestyleRoutineResult?>(null)}
    var source by remember(host){mutableStateOf<NativeCustomAuthoringSnapshot?>(null)}
    var busy by remember{mutableStateOf(false)}
    var message by rememberSaveable{mutableStateOf<String?>(null)}
    var verifiedFinish by remember{mutableStateOf(false)}
    var finishReview by remember{mutableStateOf<KotlinFreestyleOperation.Finish?>(null)}
    var routineDelete by remember{mutableStateOf<String?>(null)}
    var name by rememberSaveable(activeId){mutableStateOf("")}
    var search by rememberSaveable(activeId){mutableStateOf("")}
    var visibleChoices by rememberSaveable(activeId){mutableIntStateOf(30)}
    var restSeconds by rememberSaveable(activeId){mutableIntStateOf(90)}
    val routines=(library as? NativeFreestyleRoutineResult.Ready)?.library

    suspend fun reloadLists() {
        val read=withContext(Dispatchers.IO) {
            Triple(host.reviewUnfinishedFreestyle(),host.reviewFreestyleRoutines(),
                (host.reviewMeasuredAuthoringSource() as? NativeAuthoringSourceRead.Found)?.source)
        }
        inventory=read.first;library=read.second;source=read.third
    }
    suspend fun accept(result:NativeFreestyleResult) {
        when(result) {
            is NativeFreestyleResult.Durable -> {
                activeId=result.workout.workoutId;current=result.workout
                verifiedFinish=result.workout.finishedByEventId!=null &&
                    withContext(Dispatchers.IO){host.verifyFinishedFreestyle(result.workout.workoutId)}
                if(result.workout.finishedByEventId!=null && !verifiedFinish)
                    message="Completion is saved but History requires verification."
            }
            is NativeFreestyleResult.Rejected ->
                message="Workout not changed · "+result.reason.name.lowercase().replace('_',' ')
        }
    }
    fun perform(operation:suspend ()->Unit) {
        if(busy)return
        busy=true;message=null
        scope.launch {
            try{operation()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){message="Could not verify saved data. Try again."}
            finally{busy=false}
        }
    }
    fun apply(state:KotlinFreestyleWorkout,op:KotlinFreestyleOperation) {
        perform{accept(withContext(Dispatchers.IO){host.applyFreestyle(state.workoutId,op)})}
    }
    LaunchedEffect(host) {
        busy=true
        try {
            reloadLists()
            activeId?.let{id->accept(withContext(Dispatchers.IO){host.openFreestyle(id)})}
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){message="Could not verify saved data. Try again."}
        finally{busy=false}
    }
    BackHandler {if(!busy)onClose()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.md)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Freestyle",style=MaterialTheme.typography.headlineSmall)
            TextButton(enabled=!busy,onClick=onClose){Text(if(current?.finishedByEventId==null && activeId!=null)"Minimize" else "Back")}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        val state=current
        if(activeId!=null && state==null) {
            PursuitCard {
                PursuitSectionHeading("Saved workout unavailable",
                    "No replacement session was created. Reopen the exact workout or return to Train.")
                OutlinedButton(enabled=!busy,onClick={
                    activeId?.let{id->perform{accept(withContext(Dispatchers.IO){host.openFreestyle(id)})}}
                }){Text("Retry saved workout")}
                TextButton(enabled=!busy,onClick={activeId=null;perform{reloadLists()}}){Text("Return to workout list")}
            }
        } else if(state==null) {
            PursuitCard {
                PursuitSectionHeading("Start an empty workout","Choose your own exercises and log actual sets.")
                PursuitPrimaryAction("Start empty workout",enabled=!busy && source!=null) {
                    val id=freestyleId("free")
                    perform{accept(withContext(Dispatchers.IO){host.startFreestyle(id)})}
                }
            }
            PursuitCard {
                PursuitSectionHeading("Unfinished freestyle workouts","Resume saved sets from this device.")
                when(val result=inventory) {
                    is NativeFreestyleInventoryResult.Ready -> {
                        if(result.workouts.isEmpty())Text("No unfinished freestyle workouts.")
                        result.workouts.forEach{item->
                            OutlinedButton(enabled=!busy,modifier=Modifier.fillMaxWidth(),onClick={
                                perform{accept(withContext(Dispatchers.IO){host.openFreestyle(item.workoutId)})}
                            }) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(item.exerciseIds.take(3).joinToString(" · "){kotlinExerciseLabel(it)}
                                        .ifEmpty{"Empty workout"})
                                    Text("${item.recordedSets} recorded sets",style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    is NativeFreestyleInventoryResult.Rejected ->Text("Unfinished workouts could not be verified.")
                    null ->Unit
                }
                TextButton(enabled=!busy,onClick={perform{reloadLists()}}){Text("Refresh")}
            }
            PursuitCard {
                PursuitSectionHeading("Saved routines","Only the exercise order and set types are reused.")
                if(routines==null)Text("Routine library unavailable.")
                else if(routines.routines.isEmpty())Text("Save a finished freestyle workout as a routine.")
                routines?.routines?.forEach{routine->
                    Text(routine.name,style=MaterialTheme.typography.titleMedium)
                    Text("${routine.slots.size} exercises · ${routine.slots.sumOf{it.setTypes.size}} set rows")
                    Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)){
                        Button(enabled=!busy && source!=null,onClick={
                            val rev=routines?.revision?:return@Button
                            val id=freestyleId("free")
                            perform{accept(withContext(Dispatchers.IO){
                                host.startFreestyle(id,routine.routineId,rev)})}
                        }){Text("Start routine")}
                        TextButton(enabled=!busy,onClick={routineDelete=routine.routineId}){Text("Delete")}
                    }
                }
            }
        } else if(state.finishedByEventId!=null) {
            PursuitCard {
                PursuitSectionHeading(if(verifiedFinish)"Workout complete" else "Verify completion",
                    "${state.effectiveSets.values.count{it.actual!=null}} actual sets retained.")
                if(!verifiedFinish)PursuitPrimaryAction("Recover and verify History",enabled=!busy){
                    perform{
                        withContext(Dispatchers.IO){host.recoverPendingNativeHistory()}
                        accept(withContext(Dispatchers.IO){host.openFreestyle(state.workoutId)})
                    }
                }
                if(verifiedFinish) {
                    PursuitPrimaryAction("View History",enabled=!busy){onViewHistory(state.workoutId)}
                    OutlinedTextField(value=name,onValueChange={name=it.take(80)},
                        enabled=!busy,label={Text("Routine name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(enabled=!busy && name.isNotBlank() && routines!=null,onClick={
                        val rev=routines?.revision?:return@Button
                        perform{
                            library=withContext(Dispatchers.IO){
                                host.saveFreestyleRoutine(state.workoutId,freestyleId("routine"),name,rev)}
                            message=if(library is NativeFreestyleRoutineResult.Ready)"Routine saved." else "Routine not saved."
                        }
                    }){Text("Save as routine")}
                }
                TextButton(enabled=!busy,onClick={activeId=null;current=null;perform{reloadLists()}}){Text("Workout list")}
            }
        } else {
            PursuitCard {
                PursuitSectionHeading("Your workout","Actual sets only · no program targets")
                Text("${state.effectiveSets.values.count{it.actual!=null}} recorded sets")
                Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)){
                    listOf(60,90,120).forEach{seconds->
                        FilterChip(selected=restSeconds==seconds,enabled=!busy,onClick={restSeconds=seconds},
                            label={Text("${seconds}s rest")})
                    }
                }
                // One existing elapsed-clock timer. No prescribed or auto
                // rest is inferred for a freestyle session.
                NativeRestTimer(state.workoutId,restSeconds,!busy,ownerId=state.source.athleteId)
            }
            state.exercises.forEachIndexed{index,exercise->
                key(exercise.slotId) {
                    PursuitCard {
                        PursuitSectionHeading(kotlinExerciseLabel(exercise.exerciseId),
                            exercise.attachmentId?.let{"Attachment: $it"})
                        Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            fun move(to:Int) {
                                val order=state.exercises.map{it.slotId}.toMutableList()
                                val slot=order.removeAt(index);order.add(to,slot)
                                apply(state,KotlinFreestyleOperation.Reorder(freestyleId("reorder"),state.revision,order))
                            }
                            TextButton(enabled=!busy && index>0,onClick={move(index-1)}){Text("Move up")}
                            TextButton(enabled=!busy && index<state.exercises.lastIndex,onClick={move(index+1)}){Text("Move down")}
                        }
                        val records=state.effectiveSets.values.filter{it.slotId==exercise.slotId}
                        NativeFreestyleSetEditor(exercise,records,!busy,state.revision){op->apply(state,op)}
                    }
                }
            }
            PursuitCard {
                PursuitSectionHeading("Add exercise","Only exercises available in your verified gym appear.")
                OutlinedTextField(search,{search=it;visibleChoices=30},label={Text("Search exercises")},
                    enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                val reviewed=source
                val choices=remember(reviewed,search,state.source) {
                    if(reviewed==null || KotlinFreestyleWorkoutDomain.source(reviewed.setup,reviewed.revision)!=state.source)
                        emptyList()
                    else CustomExerciseAuthoringDomain.picker(reviewed.setup,reviewed.catalog)
                        .filter{search.isBlank() || kotlinExerciseLabel(it.exerciseId).contains(search,ignoreCase=true) ||
                            it.exerciseId.contains(search,ignoreCase=true)}
                        .sortedBy{kotlinExerciseLabel(it.exerciseId)}
                }
                if(choices.isEmpty())Text("No matching verified exercises. Refresh gym context if it changed.")
                choices.take(visibleChoices).forEach{choice->
                    OutlinedButton(enabled=!busy,modifier=Modifier.fillMaxWidth(),onClick={
                        val currentSource=reviewed?:return@OutlinedButton
                        val selected=KotlinFreestyleWorkoutDomain.selectedExercise(state,currentSource.setup,
                            currentSource.revision,currentSource.catalog,freestyleId("slot"),choice.exerciseId)
                        if(selected!=null)apply(state,KotlinFreestyleOperation.Add(freestyleId("add"),state.revision,selected))
                    }){Text(kotlinExerciseLabel(choice.exerciseId))}
                }
                if(choices.size>visibleChoices)TextButton(enabled=!busy,onClick={visibleChoices+=30}){Text("Show more exercises")}
                TextButton(enabled=!busy,onClick={perform{reloadLists()}}){Text("Refresh gym")}
            }
            TextButton(enabled=!busy,onClick={
                perform{
                    withContext(Dispatchers.IO){host.recoverPendingNativeHistory()}
                    accept(withContext(Dispatchers.IO){host.openFreestyle(state.workoutId)})
                }
            }){Text("Recover interrupted completion")}
            PursuitPrimaryAction("Finish workout",enabled=!busy && state.effectiveSets.values.any{it.actual!=null}){
                finishReview=KotlinFreestyleOperation.Finish(freestyleId("finish"),state.revision)
            }
        }
    }
    finishReview?.let{finish->
        AlertDialog(onDismissRequest={finishReview=null},title={Text("Finish workout?")},
            text={Text("Your recorded actuals will be saved in History. Unentered routine rows are not performed sets.")},
            confirmButton={TextButton(enabled=!busy,onClick={
                val id=activeId?:return@TextButton
                finishReview=null
                perform{
                    val result=withContext(Dispatchers.IO){host.finishFreestyle(id,finish)}
                    accept(result)
                    if(result is NativeFreestyleResult.Rejected) {
                        // A pending transaction may have written the original
                        // Finish before the UI received its response.
                        val reopened=withContext(Dispatchers.IO){host.openFreestyle(id)}
                        if(reopened is NativeFreestyleResult.Durable &&
                            reopened.workout.finishedByEventId!=null)accept(reopened)
                    }
                }
            }){Text("Finish")}},dismissButton={TextButton(onClick={finishReview=null}){Text("Keep training")}})
    }
    routineDelete?.let{id->
        AlertDialog(onDismissRequest={routineDelete=null},title={Text("Delete routine?")},
            text={Text("Saved workout History will not change.")},
            confirmButton={TextButton(enabled=!busy && routines!=null,onClick={
                val rev=routines?.revision?:return@TextButton
                routineDelete=null
                perform{library=withContext(Dispatchers.IO){host.deleteFreestyleRoutine(id,rev)}}
            }){Text("Delete")}},dismissButton={TextButton(onClick={routineDelete=null}){Text("Cancel")}})
    }
}

/** Actual input uses the existing shared parser; blanks never become targets. */
@Composable
private fun NativeFreestyleSetEditor(
    exercise:KotlinFreestyleExercise,
    records:List<KotlinFreestyleOperation.Record>,
    enabled:Boolean,revision:Long,
    onRecord:(KotlinFreestyleOperation.Record)->Unit
) {
    var correctingKey by rememberSaveable{mutableStateOf<String?>(null)}
    var correctionPredecessor by rememberSaveable{mutableStateOf<String?>(null)}
    val correction=records.singleOrNull{it.setKey==correctingKey && it.eventId==correctionPredecessor}
    LaunchedEffect(correctingKey,correction?.eventId) {
        if(correctingKey!=null && correction==null) {
            correctingKey=null;correctionPredecessor=null
        }
    }
    records.forEach{record->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(nativeFreestyleHistoryActual(record.actual,exercise.basis,record.outcome),
                modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
            TextButton(enabled=enabled,onClick={
                correctingKey=record.setKey;correctionPredecessor=record.eventId
            }){Text("Edit")}
        }
    }
    key(correction?.eventId ?: "new",records.size) {
        val actual=correction?.actual
        var weight by rememberSaveable{mutableStateOf(actual?.load?.toPlainString().orEmpty())}
        var reps by rememberSaveable{mutableStateOf(actual?.reps?.toString().orEmpty())}
        var rir by rememberSaveable{mutableStateOf(actual?.actualRir?.toString().orEmpty())}
        val suggested=exercise.routineSetTypes.getOrNull(records.size)?:DraftSetType.WORKING
        var warmup by rememberSaveable{mutableStateOf((correction?.type?:suggested)==DraftSetType.WARMUP)}
        val type=if(warmup)DraftSetType.WARMUP else DraftSetType.WORKING
        val parsed=KotlinWorkoutEntryDomain.parseUnplanned(exercise.unit,weight,reps,rir)
        val failed=KotlinWorkoutEntryDomain.parseUnplanned(exercise.unit,weight,reps,rir,true)
        Text(if(correction==null)"Record a set" else "Correct recorded set",style=MaterialTheme.typography.titleSmall)
        val unit=exercise.unit
        if(unit!=null)OutlinedTextField(weight,{weight=it},enabled=enabled,
            label={Text("Actual weight (${unit.name.lowercase()})")},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            OutlinedTextField(reps,{reps=it},enabled=enabled,label={Text("Reps")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
            OutlinedTextField(rir,{rir=it},enabled=enabled,label={Text("RIR (optional)")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
        }
        FilterChip(selected=warmup,enabled=enabled && correction==null,onClick={warmup=!warmup},label={Text("Warm-up")})
        fun submit(outcome:KotlinJournalSetOutcome,performed:KotlinPerformedSet?) {
            // A corrected station tag remains its exact original identity.
            val actualWithStation=performed?.copy(machineInstanceId=correction?.actual?.machineInstanceId)
            onRecord(KotlinFreestyleOperation.Record(freestyleId("record"),revision,exercise.slotId,
                correction?.setKey?:freestyleId("set"),type,outcome,actualWithStation,correction?.eventId))
        }
        PursuitPrimaryAction(if(correction==null)"Complete set" else "Save correction",
            enabled=enabled && parsed is KotlinSetEntryResult.Accepted) {
            (parsed as? KotlinSetEntryResult.Accepted)?.let{submit(KotlinJournalSetOutcome.COMPLETED,it.performed)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            TextButton(enabled=enabled && failed is KotlinSetEntryResult.Accepted,onClick={
                (failed as? KotlinSetEntryResult.Accepted)?.let{submit(KotlinJournalSetOutcome.FAILED_ATTEMPT,it.performed)}
            }){Text("Failed attempt")}
            TextButton(enabled=enabled,onClick={submit(KotlinJournalSetOutcome.SKIPPED,null)}){Text("Skip set")}
            if(correction!=null)TextButton(enabled=enabled,onClick={
                correctingKey=null;correctionPredecessor=null
            }){Text("Cancel edit")}
        }
    }
}
