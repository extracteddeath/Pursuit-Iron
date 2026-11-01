package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID

private fun historySetLabel(exercise: NativeHistoryExercise,set: NativeHistorySet): String {
    if(set.kind=="working")return "Set ${set.ordinal}"
    val number=exercise.sets.count { it.kind==set.kind && it.setIndex<=set.setIndex }
    return if(set.kind=="myo")"Myo-rep mini $number" else "Drop set $number"
}

/** Latest acknowledged workout. Records are read afresh from canonical history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeHistoryPanel(state: NativeRuntimeState,onCorrect: (String)->Unit,onReload: ()->Unit) {
    val history=state.history
    if(history==null) {
        PursuitCard {
            Text("Saved sets",style=MaterialTheme.typography.titleMedium)
            Text(state.historyError ?: "Open your latest saved workout to review its sets.")
            TextButton(onClick=onReload,enabled=!state.historyBusy && !state.workoutBusy && state.workout==null) {
                Text(if(state.historyBusy)"Loading…" else "Load saved sets")
            }
        }
        return
    }
    var selectedExercise by rememberSaveable(history.entrySha256) { mutableStateOf<String?>(null) }
    var selectedSet by rememberSaveable(history.entrySha256) { mutableIntStateOf(-1) }
    val canEdit=state.workout==null && !state.workoutBusy && !state.historyBusy
    SectionLabel("Latest saved workout")
    Text("${history.setsDone} completed · ${history.totalSets-history.setsDone} skipped · ${history.volume} ${history.unit} volume",
        style=MaterialTheme.typography.bodyMedium)
    if(state.workout!=null)Text("Finish your active workout before correcting saved history.",
        style=MaterialTheme.typography.bodySmall)
    if(history.exercises.isEmpty())Text("No completed sets were recorded in this workout.")
    history.exercises.forEach { exercise ->
        PursuitCard {
            Text(exercise.name,style=MaterialTheme.typography.titleMedium)
            exercise.record?.let { record ->
                val weight=java.math.BigDecimal.valueOf(record.load).stripTrailingZeros().toPlainString()
                Text("Heaviest working set: $weight ${record.unit} × ${record.reps}",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            exercise.sets.forEach { set ->
                TextButton(onClick={selectedExercise=exercise.id;selectedSet=set.setIndex},enabled=canEdit,
                    modifier=Modifier.fillMaxWidth()) {
                    val label=historySetLabel(exercise,set)
                    Text("$label · ${set.load} ${history.unit} × ${set.reps}" +
                        (set.rir?.let { " · $it RIR" } ?: "") + " · Edit")
                }
            }
        }
    }
    state.historyError?.let { error ->
        Text(error,color=MaterialTheme.colorScheme.error)
        TextButton(onClick=onReload,enabled=canEdit) { Text("Reload saved sets") }
    }
    val exercise=history.exercises.firstOrNull { it.id==selectedExercise }
    val set=exercise?.sets?.firstOrNull { it.setIndex==selectedSet }
    if(exercise!=null && set!=null) {
        NativeSetEditSheet(state.historyBusy,onDismiss={selectedExercise=null}) {
            NativeHistorySetEditor(history,exercise,set,state.historyBusy,state.historyError,onCorrect,
                onCancel={selectedExercise=null})
        }
    }
}

@Composable
private fun NativeHistorySetEditor(history: NativeWorkoutHistory,exercise: NativeHistoryExercise,set: NativeHistorySet,
    busy: Boolean,error: String?,onCorrect: (String)->Unit,onCancel: ()->Unit) {
    var load by rememberSaveable(history.entrySha256,exercise.id,set.setIndex) { mutableStateOf(set.load) }
    var reps by rememberSaveable(history.entrySha256,exercise.id,set.setIndex) { mutableStateOf(set.reps.toString()) }
    var rir by rememberSaveable(history.entrySha256,exercise.id,set.setIndex) { mutableStateOf(set.rir?.toString() ?: "") }
    // A lost acknowledgement retries the exact operation, including its timestamp.
    var command by rememberSaveable(history.entrySha256,exercise.id,set.setIndex) { mutableStateOf<String?>(null) }
    Text("Correct saved set",style=MaterialTheme.typography.headlineSmall)
    Text("${exercise.name} · ${historySetLabel(exercise,set)}",style=MaterialTheme.typography.titleMedium)
    Text("Update what you performed. Records and future workouts use the corrected values.",
        style=MaterialTheme.typography.bodyMedium)
    NativeSetValueFields(history.unit,load,reps,rir,!busy,
        {load=it;command=null},{reps=it;command=null},{rir=it;command=null})
    val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
    val valid=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")) &&
        repCount!=null && repCount in 1..100 && (rir.isEmpty() || effort!=null && effort in 0..10)
    val changed=load.toBigDecimalOrNull()?.compareTo(set.load.toBigDecimal())!=0 || repCount!=set.reps || effort!=set.rir
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Button(onClick={
        val exact=command ?: NativeHistoryCodec.correctionJson(history,"correct-"+UUID.randomUUID(),
            System.currentTimeMillis(),listOf(NativeHistoryChange(exercise.id,set.setIndex,load,checkNotNull(repCount),effort)))
            .also { command=it }
        onCorrect(exact)
    },enabled=!busy && valid && (changed || command!=null),modifier=Modifier.fillMaxWidth()) {
        Text(if(busy)"Saving correction…" else if(command!=null)"Retry correction" else "Save correction")
    }
    TextButton(onClick=onCancel,enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Cancel") }
}
