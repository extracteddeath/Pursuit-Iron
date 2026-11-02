package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID

@Composable
internal fun NativeWarmupPanel(progress: NativeWorkoutProgress,exercise: NativeWorkoutExercise,
    busy: Boolean,error: String?,onSave: (JournalWarmupIntent)->Unit) {
    val journal=progress.journal;val session=progress.plan.session
    val saved=journal.warmups.filter {it.slot.exerciseIndex==exercise.index}
    val count=maxOf(exercise.warmupTargets.size,saved.maxOfOrNull {it.slot.warmupIndex+1} ?: 0)
    var selected by rememberSaveable(session.workoutId,exercise.index) {mutableIntStateOf(-1)}
    var awaiting by rememberSaveable(session.workoutId,exercise.index) {mutableStateOf<String?>(null)}
    val drafts=rememberSaveableStateHolder()
    LaunchedEffect(journal.warmupEvents.size,busy) {
        if(!busy && awaiting!=null && journal.warmupEvents.any {it.operationId==awaiting}) {
            selected=-1;awaiting=null
        }
    }
    Text("Warm-ups",style=MaterialTheme.typography.titleSmall)
    for(index in 0 until count) {
        val target=exercise.warmupTargets.getOrNull(index);val actual=saved.firstOrNull {it.slot.warmupIndex==index}
        TextButton(onClick={selected=index},enabled=!busy && !journal.isFinished,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text("Warm-up ${index+1}"+(target?.let {" · ${it.load} ${session.unit} × ${it.reps}"} ?: " · Added warm-up"))
                Text(warmupActualLabel(actual?.disposition,actual?.values),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(count<20)OutlinedButton(onClick={selected=count},enabled=!busy && !journal.isFinished) {Text("Add warm-up")}
    if(selected<0 || selected>count || selected>=20)return
    val slot=JournalWarmupSlot(exercise.index,selected,exercise.id)
    val target=exercise.warmupTargets.getOrNull(selected);val commit=saved.firstOrNull {it.slot==slot}
    NativeEditSheet(busy,onDismiss={selected=-1}) {
        drafts.SaveableStateProvider("${session.workoutId}:${exercise.index}:$selected:${commit?.operationId.orEmpty()}") {
            Text("Warm-up ${selected+1}",style=MaterialTheme.typography.headlineSmall)
            Text(exercise.name,style=MaterialTheme.typography.titleMedium)
            NativeWarmupFields(session.unit,target?.load,target?.reps,commit?.disposition,commit?.values,
                maxOf(session.startedAt,commit?.recordedAt ?: session.startedAt),busy,error,
                canSkip=target!=null || commit!=null,onSave={op,clock,disposition,value->
                    awaiting=op
                    onSave(JournalWarmupIntent(op,slot,disposition,value,clock,commit?.operationId))
                },onCancel={selected=-1})
        }
    }
}

internal fun warmupActualLabel(disposition: JournalSetDisposition?,values: JournalSetValues?): String = when(disposition) {
    null -> "Not recorded"
    JournalSetDisposition.SKIPPED -> "Skipped · saved"
    JournalSetDisposition.COMPLETED -> values?.let {
        (if(it.reps==0)"Attempt · " else "")+"${it.load} ${it.unit} × ${it.reps} · saved"
    } ?: "Not recorded"
}

/** The same numeric controls and explicit save are used for live and historical warm-ups. */
@Composable
internal fun NativeWarmupFields(unit: String,targetLoad: String?,targetReps: String?,
    disposition: JournalSetDisposition?,actual: JournalSetValues?,clockFloor: Long,busy: Boolean,error: String?,
    canSkip: Boolean,onSave: (String,Long,JournalSetDisposition,JournalSetValues?)->Unit,onCancel: ()->Unit) {
    var load by rememberSaveable {mutableStateOf(actual?.load ?: targetLoad.orEmpty())}
    var reps by rememberSaveable {mutableStateOf(actual?.reps?.toString() ?: targetReps.orEmpty())}
    var rir by rememberSaveable {mutableStateOf(actual?.rir?.toString().orEmpty())}
    var operation by rememberSaveable {mutableStateOf<String?>(null)}
    var clock by rememberSaveable {mutableLongStateOf(0)}
    var pending by rememberSaveable {mutableStateOf<String?>(null)}
    var confirmSkip by rememberSaveable {mutableStateOf(false)}
    fun changed() {operation=null;clock=0;pending=null}
    fun save(kind: JournalSetDisposition,value: JournalSetValues?) {
        if(operation==null || pending!=kind.name) {
            operation="warmup-"+UUID.randomUUID();clock=maxOf(System.currentTimeMillis(),clockFloor+1);pending=kind.name
        }
        onSave(checkNotNull(operation),clock,kind,value)
    }
    targetReps?.let {Text("Original target: ${targetLoad.orEmpty()} $unit × $it",style=MaterialTheme.typography.bodySmall)}
    NativeSetValueFields(unit,load,reps,rir,!busy,{load=it;changed()},{reps=it;changed()},{rir=it;changed()})
    val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
    val value=repCount?.let {JournalSetValues(load,unit,it,effort)}
    val valid=NativeWarmupPolicy.validValues(value,unit) && (rir.isEmpty() || effort!=null && effort in 0..10)
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Button(onClick={if(pending==JournalSetDisposition.SKIPPED.name)save(JournalSetDisposition.SKIPPED,null)
        else save(JournalSetDisposition.COMPLETED,value)},
        enabled=!busy && (pending==JournalSetDisposition.SKIPPED.name || valid) &&
            (operation!=null || disposition!=JournalSetDisposition.COMPLETED || value!=actual),modifier=Modifier.fillMaxWidth()) {
        Text(if(busy)"Saving…" else if(operation!=null)"Retry warm-up save" else "Save warm-up")
    }
    if(canSkip && disposition!=JournalSetDisposition.SKIPPED)TextButton(onClick={confirmSkip=true},enabled=!busy) {Text("Mark warm-up skipped")}
    TextButton(onClick=onCancel,enabled=!busy) {Text("Close")}
    if(confirmSkip)AlertDialog(onDismissRequest={if(!busy)confirmSkip=false},title={Text("Skip this warm-up?")},
        text={Text("It will be saved as skipped.")},
        confirmButton={TextButton(onClick={confirmSkip=false;save(JournalSetDisposition.SKIPPED,null)},enabled=!busy) {Text("Skip warm-up")}},
        dismissButton={TextButton(onClick={confirmSkip=false},enabled=!busy) {Text("Keep warm-up")}})
}
