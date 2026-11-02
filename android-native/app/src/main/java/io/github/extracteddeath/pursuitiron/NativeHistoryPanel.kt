package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID
import java.util.Date
import java.text.DateFormat
import java.time.YearMonth
import java.time.ZoneId

private fun historyDateLabel(date: Long)=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(date))

private fun historySetLabel(exercise: NativeHistoryExercise,set: NativeHistorySet): String {
    if(set.kind=="working")return "Set ${set.ordinal}"+if(set.failedAttempt)" · Failed attempt" else ""
    val number=exercise.sets.count { it.exerciseIndex==set.exerciseIndex && it.kind==set.kind && it.setIndex<=set.setIndex }
    return if(set.kind=="myo")"Myo-rep mini $number" else "Drop set $number"
}

/** Revision-bound saved workouts. Records are read afresh from canonical history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeHistoryPanel(state: NativeRuntimeState,onCorrect: (String)->Unit,onReload: ()->Unit,
    onOpen: (String)->Unit,onPage: (Int)->Unit,onList: ()->Unit,onFilter: (NativeHistoryFilter)->Unit,
    onCalendar: (NativeHistoryCalendarQuery)->Unit) {
    val history=state.history
    val browserState=rememberSaveableStateHolder()
    // History remains readable during a live workout, but respects the
    // canonical reader's recovery and store-availability checks.
    val enabled=state.ready && state.saved!=null && !state.blocked &&
        !state.busy && !state.workoutBlocked && !state.historyBusy &&
        !state.workoutBusy && !state.backupExportBusy
    if(history==null) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            FilterChip(selected=state.historyCalendarQuery==null,onClick={onPage(0)},enabled=enabled,label={Text("List")})
            FilterChip(selected=state.historyCalendarQuery!=null,enabled=enabled,onClick={
                val zone=ZoneId.systemDefault()
                onCalendar(state.historyCalendarQuery ?: NativeHistoryCalendarQuery(YearMonth.now(zone),zone,state.historyFilter.search))
            },label={Text("Calendar")})
        }
        if(state.historyCalendarQuery!=null) {
            browserState.SaveableStateProvider("calendar") {
                NativeHistoryCalendarPanel(state,onCalendar,onOpen,onReload)
            }
            return
        }
        val page=state.historyPage
        NativeHistoryFilters(state.historyFilter,enabled,onFilter)
        if(state.historyBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        if(page==null) {
            PursuitCard {
                Text("Saved workouts",style=MaterialTheme.typography.titleMedium)
                Text(if(state.completion!=null)"Your latest workout is saved. Load your history to review it."
                    else "Load the workouts saved on this device.")
                TextButton(onClick={onPage(0)},enabled=enabled) {
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
                NativeHistorySummaryCard(summary,enabled,onOpen)
            }
            Text("${page.offset+1}–${page.offset+page.entries.size} of ${page.total}",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(onClick={onPage((page.offset-NativeHistoryPageCodec.PAGE_SIZE).coerceAtLeast(0))},
                    enabled=page.offset>0 && enabled) { Text("Newer workouts") }
                OutlinedButton(onClick={page.nextOffset?.let(onPage)},
                    enabled=page.nextOffset!=null && enabled) { Text("Older workouts") }
            }
        }
        state.historyError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick={onPage(0)},enabled=enabled) { Text("Reload workouts") }
        return
    }
    var selectedExercise by rememberSaveable(history.entrySha256) { mutableStateOf<String?>(null) }
    var selectedSet by rememberSaveable(history.entrySha256) { mutableIntStateOf(-1) }
    var selectedObservation by rememberSaveable(history.entrySha256) { mutableIntStateOf(-1) }
    var selectedWarmup by rememberSaveable(history.entrySha256) {mutableStateOf<String?>(null)}
    val historyEditorBusy=state.historyBusy || state.workoutBusy ||
        state.backupExportBusy || state.busy || state.workout!=null ||
        !state.ready || state.blocked || state.workoutBlocked
    val canEdit=!historyEditorBusy
    // If a new active workout is started from another tab, stale history
    // correction sheets must close instead of remaining seemingly editable.
    LaunchedEffect(state.workout?.plan?.session?.workoutId) {
        if(state.workout!=null) {
            selectedExercise=null
            selectedSet=-1
            selectedObservation=-1
            selectedWarmup=null
        }
    }
    TextButton(onClick=onList,enabled=!state.historyBusy) { Text(if(state.historyCalendarQuery!=null)"Back to calendar" else "Back to workouts") }
    Text(history.dayLabel,style=MaterialTheme.typography.headlineSmall)
    Text("${historyDateLabel(history.date)} · Week ${history.weekIndex}",style=MaterialTheme.typography.bodyMedium)
    if(state.historyBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    Text("${history.setsDone} recorded · ${history.totalSets-history.setsDone} skipped · ${history.volume} ${history.unit} volume",
        style=MaterialTheme.typography.bodyMedium)
    history.sessionNote?.takeIf { it.isNotBlank() }?.let { note->
        PursuitCard {
            Text("Session note",style=MaterialTheme.typography.titleMedium)
            Text(note,style=MaterialTheme.typography.bodyMedium)
        }
    }
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
                    val observation=history.editRows?.firstOrNull { it.slot.exerciseId==exercise.id &&
                        it.slot.exerciseIndex==set.exerciseIndex && it.slot.setIndex==set.ordinal-1 }
                    val label=observation?.let { historyObservationLabel(history,it) } ?: historySetLabel(exercise,set)
                    NativeHistorySetRow(label,
                        "${set.load} ${history.unit} × ${set.reps} reps" +
                            (set.rir?.let { " · $it RIR" } ?: ""),
                        canEdit,"Edit") {
                        if(observation!=null)
                            selectedObservation=observation.slot.exerciseIndex*20+observation.slot.setIndex
                        else {
                            selectedExercise=exercise.id
                            selectedSet=set.setIndex
                        }
                    }
                }
            }
        }
    }
    if(history.warmups.isNotEmpty())SectionLabel("Warm-ups")
    history.warmups.groupBy {it.slot.exerciseIndex}.values.forEach { rows->
        PursuitCard {
            Text(rows.first().name,style=MaterialTheme.typography.titleMedium)
            rows.forEach {row->
                NativeHistorySetRow("Warm-up ${row.slot.warmupIndex+1}",
                    warmupActualLabel(row.disposition,row.values),
                    canEdit,if(row.disposition==JournalSetDisposition.SKIPPED)"Record" else "Edit") {
                    selectedWarmup="${row.slot.exerciseIndex}:${row.slot.warmupIndex}"
                }
            }
        }
    }
    history.warmups.firstOrNull {"${it.slot.exerciseIndex}:${it.slot.warmupIndex}"==selectedWarmup}?.let {row->
        NativeSetEditSheet(historyEditorBusy,onDismiss={selectedWarmup=null}) {
            Text("Correct warm-up ${row.slot.warmupIndex+1}",style=MaterialTheme.typography.headlineSmall)
            Text(row.name,style=MaterialTheme.typography.titleMedium)
            NativeWarmupFields(history.unit,row.targetLoad,row.targetReps,row.disposition,row.values,history.date,
                historyEditorBusy,state.historyError,canSkip=true,onSave={op,clock,disposition,values->
                    onCorrect(NativeWarmupHistoryCodec.correctionJson(history,op,clock,
                        listOf(NativeWarmupChange(row.slot,disposition,values))))
                },onCancel={selectedWarmup=null})
        }
    }
    val otherRows=history.editRows.orEmpty().filter { !it.retained }
    if(otherRows.isNotEmpty())SectionLabel("Skipped and other recorded sets")
    otherRows.groupBy { it.slot.exerciseIndex }.forEach { (exerciseIndex,rows)->
        PursuitCard {
            Text(rows.first().name,style=MaterialTheme.typography.titleMedium)
            Text("Exercise ${exerciseIndex+1} in workout",style=MaterialTheme.typography.bodySmall)
            rows.forEach { row->
                val value=row.values
                NativeHistorySetRow(historyObservationLabel(history,row),
                    if(value==null)"Skipped · no recorded reps"
                    else "${value.load} ${history.unit} × ${value.reps} reps" +
                        (value.rir?.let { " · $it RIR" } ?: ""),
                    canEdit,if(value==null)"Record" else "Edit") {
                    selectedObservation=exerciseIndex*20+row.slot.setIndex
                }
            }
        }
    }
    state.historyError?.let { error ->
        Text(error,color=MaterialTheme.colorScheme.error)
        TextButton(onClick=onReload,enabled=enabled) { Text("Reload saved sets") }
    }
    val exercise=history.exercises.firstOrNull { it.id==selectedExercise }
    val set=exercise?.sets?.firstOrNull { it.setIndex==selectedSet }
    val observation=history.editRows?.firstOrNull { it.slot.exerciseIndex*20+it.slot.setIndex==selectedObservation }
    if(observation!=null)NativeSetEditSheet(historyEditorBusy,onDismiss={selectedObservation=-1}) {
        NativeHistoryObservationEditor(history,observation,historyEditorBusy,state.historyError,onCorrect,
            onCancel={selectedObservation=-1})
    }
    if(exercise!=null && set!=null) {
        NativeSetEditSheet(historyEditorBusy,onDismiss={selectedExercise=null}) {
            NativeHistorySetEditor(history,exercise,set,historyEditorBusy,state.historyError,onCorrect,
                onCancel={selectedExercise=null})
        }
    }
}

/** Shared compact history row. Display never edits until the explicit action. */
@Composable
private fun NativeHistorySetRow(
    label: String,
    detail: String,
    enabled: Boolean,
    action: String,
    onAction: ()->Unit
) {
    ListItem(
        headlineContent={Text(label,style=MaterialTheme.typography.titleSmall)},
        supportingContent={Text(detail,style=MaterialTheme.typography.bodySmall)},
        trailingContent={
            TextButton(onClick=onAction,enabled=enabled,
                modifier=Modifier.semantics {
                    contentDescription="$action $label"
                }) {Text(action)}
        },
        colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)
    )
}

/** The calendar and paged list open the identical revision-bound review action. */
@Composable
internal fun NativeHistorySummaryCard(summary: NativeHistorySummary,enabled: Boolean,onOpen: (String)->Unit) {
    PursuitCard {
        Text(summary.dayLabel,style=MaterialTheme.typography.titleMedium)
        Text(historyDateLabel(summary.date),style=MaterialTheme.typography.bodyMedium)
        Text("${summary.programName} · Week ${summary.weekIndex}",style=MaterialTheme.typography.bodySmall)
        Text("${summary.setsDone} recorded · ${summary.totalSets-summary.setsDone} skipped · ${summary.volume} ${summary.unit} volume",
            style=MaterialTheme.typography.bodySmall)
        if(summary.detailsAvailable)FilledTonalButton(onClick={onOpen(summary.historyId)},
            enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("Review workout") }
        else Text("Summary only · set details are unavailable",style=MaterialTheme.typography.bodySmall)
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
