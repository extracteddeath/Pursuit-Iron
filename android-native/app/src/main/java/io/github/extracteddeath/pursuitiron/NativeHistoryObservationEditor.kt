package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID

internal fun historyObservationLabel(history: NativeWorkoutHistory,row: NativeHistoryObservation): String {
    if(row.kind=="working")return "Set ${row.slot.setIndex+1}"
    val ordinal=history.editRows.orEmpty().count { it.slot.exerciseIndex==row.slot.exerciseIndex &&
        it.kind==row.kind && it.slot.setIndex<=row.slot.setIndex }
    return if(row.kind=="myo")"Myo-rep mini $ordinal" else "Drop set $ordinal"
}

/** Explicit actual values only. The same atomic owner handles saved edits and restores. */
@Composable
internal fun NativeHistoryObservationEditor(history: NativeWorkoutHistory,row: NativeHistoryObservation,
    busy: Boolean,error: String?,onCorrect: (String)->Unit,onCancel: ()->Unit) {
    val key="${history.entrySha256}:${row.slot.exerciseIndex}:${row.slot.setIndex}"
    val actual=row.values
    var load by rememberSaveable(key) { mutableStateOf(actual?.load ?: "") }
    var reps by rememberSaveable(key) { mutableStateOf(actual?.reps?.toString() ?: "") }
    var rir by rememberSaveable(key) { mutableStateOf(actual?.rir?.toString() ?: "") }
    var command by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var confirmSkip by rememberSaveable(key) { mutableStateOf(false) }
    val skipped=row.disposition==JournalSetDisposition.SKIPPED
    Text(if(skipped)"Record skipped set" else "Correct saved set",style=MaterialTheme.typography.headlineSmall)
    Text("${row.name} · ${historyObservationLabel(history,row)}",style=MaterialTheme.typography.titleMedium)
    Text("Exercise ${row.slot.exerciseIndex+1} in workout",style=MaterialTheme.typography.bodySmall)
    Text("Enter what you performed. Records and future workouts use the corrected values.")
    NativeSetValueFields(history.unit,load,reps,rir,!busy,
        {load=it;command=null},{reps=it;command=null},{rir=it;command=null})
    val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
    val valid=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")) &&
        repCount!=null && repCount in 1..100 && (rir.isEmpty() || effort!=null && effort in 0..10)
    val entered=load.toBigDecimalOrNull();val previous=actual?.load?.toBigDecimalOrNull()
    val changed=skipped || entered==null || previous==null || entered.compareTo(previous)!=0 ||
        repCount!=actual?.reps || effort!=actual?.rir
    fun submit(disposition: JournalSetDisposition) {
        val value=if(disposition==JournalSetDisposition.SKIPPED)null else
            JournalSetValues(load,history.unit,checkNotNull(repCount),effort)
        val exact=command ?: NativeHistoryObservationCodec.correctionJson(history,"correct-"+UUID.randomUUID(),
            System.currentTimeMillis(),listOf(NativeHistoryObservationChange(row.slot,disposition,value))).also { command=it }
        onCorrect(exact)
    }
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Button(onClick={command?.let(onCorrect) ?: submit(JournalSetDisposition.COMPLETED)},
        enabled=!busy && (command!=null || valid && changed),modifier=Modifier.fillMaxWidth()) {
        Text(if(busy)"Saving correction…" else if(command!=null)"Retry correction" else if(skipped)"Record set" else "Save correction")
    }
    val otherWorking=history.editRows.orEmpty().any { it.slot.exerciseId==row.slot.exerciseId &&
        it.slot!=row.slot && it.retained && it.kind=="working" }
    val extensions=history.editRows.orEmpty().any { it.slot.exerciseId==row.slot.exerciseId && it.retained && it.kind!="working" }
    val canSkip=row.kind!="working" || otherWorking || !extensions
    if(!skipped) {
        TextButton(onClick={confirmSkip=true},enabled=!busy && canSkip,modifier=Modifier.fillMaxWidth()) { Text("Mark as skipped") }
        if(!canSkip)Text("Skip the completed extension sets first, or keep a working set recorded.",style=MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick=onCancel,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Cancel") }
    if(confirmSkip)AlertDialog(onDismissRequest={if(!busy)confirmSkip=false},
        title={Text("Mark this saved set as skipped?")},
        text={Text("It will no longer count as completed work. Its previous entry stays in the correction history.")},
        confirmButton={Button(onClick={confirmSkip=false;command=null;submit(JournalSetDisposition.SKIPPED)},enabled=!busy) {
            Text("Mark skipped") }},
        dismissButton={TextButton(onClick={confirmSkip=false},enabled=!busy) { Text("Keep set") }})
}
