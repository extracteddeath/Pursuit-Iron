package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID
import java.util.Date
import java.text.DateFormat

private fun historyDateLabel(date: Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(date))

private fun historySetLabel(exercise: NativeHistoryExercise,set: NativeHistorySet): String {
    if(set.kind=="working")return "Set ${set.ordinal}"
    val number=exercise.sets.count { it.exerciseIndex==set.exerciseIndex && it.kind==set.kind && it.setIndex<=set.setIndex }
    return if(set.kind=="myo")"Myo-rep mini $number" else "Drop set $number"
}

/** Revision-bound saved workouts. Records are read afresh from canonical history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeHistoryPanel(state: NativeRuntimeState,onCorrect: (String)->Unit,onReload: ()->Unit,
    onOpen: (String)->Unit,onPage: (Int)->Unit,onList: ()->Unit,onFilter: (NativeHistoryFilter)->Unit) {
    val history=state.history
    if(history==null) {
        val page=state.historyPage
        NativeHistoryFilters(state.historyFilter,!state.historyBusy && !state.workoutBusy,onFilter)
        if(state.historyBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        if(page==null) {
            PursuitCard {
                Text("Saved workouts",style=MaterialTheme.typography.titleMedium)
                Text(if(state.completion!=null)"Your latest workout is saved. Load your history to review it."
                    else "Load the workouts saved on this device.")
                TextButton(onClick={onPage(0)},enabled=!state.historyBusy && !state.workoutBusy) {
                    Text(if(state.historyBusy)"Loading…" else "Load workouts")
                }
            }
        } else if(page.total==0) {
            PursuitCard {
                if(page.filter.isActive) {
                    PreviewLabel("NO MATCHING WORKOUTS")
                    Text("Try another name or date range.",style=MaterialTheme.typography.titleMedium)
                    Text("${page.unfilteredTotal} saved workouts on this device.")
                } else {
                    PreviewLabel("NO SAVED WORKOUTS")
                    Text("Your completed workouts will appear here.",style=MaterialTheme.typography.titleMedium)
                    Text("Finish a workout to review its sets and track your results.")
                }
            }
        } else {
            SectionLabel(if(page.filter.isActive)"${page.total} of ${page.unfilteredTotal} workouts" else "${page.total} saved workouts")
            page.entries.forEach { summary ->
                PursuitCard {
                    Text(summary.dayLabel,style=MaterialTheme.typography.titleMedium)
                    Text(historyDateLabel(summary.date),style=MaterialTheme.typography.bodyMedium)
                    Text("${summary.programName} · Week ${summary.weekIndex}",style=MaterialTheme.typography.bodySmall)
                    Text("${summary.setsDone} completed · ${summary.totalSets-summary.setsDone} skipped · ${summary.volume} ${summary.unit} volume",
                        style=MaterialTheme.typography.bodySmall)
                    if(summary.detailsAvailable)FilledTonalButton(onClick={onOpen(summary.historyId)},
                        enabled=!state.historyBusy && !state.workoutBusy,modifier=Modifier.fillMaxWidth()) { Text("Review workout") }
                    else Text("Summary only · set details are unavailable",style=MaterialTheme.typography.bodySmall)
                }
            }
            Text("${page.offset+1}–${page.offset+page.entries.size} of ${page.total}",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(onClick={onPage((page.offset-NativeHistoryPageCodec.PAGE_SIZE).coerceAtLeast(0))},
                    enabled=page.offset>0 && !state.historyBusy && !state.workoutBusy) { Text("Newer workouts") }
                OutlinedButton(onClick={page.nextOffset?.let(onPage)},
                    enabled=page.nextOffset!=null && !state.historyBusy && !state.workoutBusy) { Text("Older workouts") }
            }
        }
        state.historyError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick={onPage(0)},enabled=!state.historyBusy && !state.workoutBusy) { Text("Reload workouts") }
        return
    }
    var selectedExercise by rememberSaveable(history.entrySha256) { mutableStateOf<String?>(null) }
    var selectedSet by rememberSaveable(history.entrySha256) { mutableIntStateOf(-1) }
    val canEdit=state.workout==null && !state.workoutBusy && !state.historyBusy
    TextButton(onClick=onList,enabled=!state.historyBusy) { Text("Back to workouts") }
    Text(history.dayLabel,style=MaterialTheme.typography.headlineSmall)
    Text("${historyDateLabel(history.date)} · Week ${history.weekIndex}",style=MaterialTheme.typography.bodyMedium)
    if(state.historyBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
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
                Text("Overall heaviest working set: $weight ${record.unit} × ${record.reps}",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            val occurrences=exercise.sets.groupBy { it.exerciseIndex }
            occurrences.forEach { (exerciseIndex,sets) ->
                if(occurrences.size>1)Text("Exercise ${exerciseIndex+1} in workout",
                    style=MaterialTheme.typography.titleSmall)
                sets.forEach { set ->
                    TextButton(onClick={selectedExercise=exercise.id;selectedSet=set.setIndex},enabled=canEdit,
                        modifier=Modifier.fillMaxWidth()) {
                        val label=historySetLabel(exercise,set)
                        Text("$label · ${set.load} ${history.unit} × ${set.reps}" +
                            (set.rir?.let { " · $it RIR" } ?: "") + " · Edit")
                    }
                }
            }
        }
    }
    state.historyError?.let { error ->
        Text(error,color=MaterialTheme.colorScheme.error)
        TextButton(onClick=onReload,enabled=!state.historyBusy && !state.workoutBusy) { Text("Reload saved sets") }
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
    if(exercise.sets.map { it.exerciseIndex }.distinct().size>1)
        Text("Exercise ${set.exerciseIndex+1} in workout",style=MaterialTheme.typography.bodyMedium)
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
