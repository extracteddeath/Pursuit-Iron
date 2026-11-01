package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*

/** Only a durably pinned, engine-issued session can reach these controls. */
@Composable
internal fun NativeWorkoutScreen(
    state: NativeRuntimeState,onStart: ()->Unit,
    onRecord: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit,onFinish: ()->Unit
) {
    val progress=state.workout
    if(progress==null) {
        PageHeading("TRAIN",if(state.completion!=null)"Workout saved" else "Ready when you are",
            state.saved?.program?.name ?: "Your training")
        PursuitCard(highlighted=true) {
            if(state.completion!=null)Text("Your workout is in your training history. The next session will use your logged performance.")
            else Text("Open your next scheduled workout with targets from your saved program.")
            Button(onClick=onStart,enabled=state.ready && !state.workoutBusy && !state.workoutBlocked,
                modifier=Modifier.fillMaxWidth()) {
                Text(if(state.workoutBusy)"Opening workout…" else "Start next workout")
            }
        }
        state.workoutError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        return
    }
    val plan=progress.plan;val journal=progress.journal
    val initial=plan.exercises.indexOfFirst { ex->ex.sets.any { set->journal.completed.none { it.intent.slot==set.slot } } }.coerceAtLeast(0)
    var exerciseIndex by rememberSaveable(plan.session.workoutId) { mutableIntStateOf(initial) }
    val exercise=plan.exercises[exerciseIndex.coerceIn(plan.exercises.indices)]
    val committedSlots=journal.completed.map { it.intent.slot }.toSet()
    val nextUnfinished=plan.exercises.indices.firstOrNull { index->
        index>exerciseIndex && plan.exercises[index].sets.any { it.slot !in committedSlots }
    } ?: plan.exercises.indices.firstOrNull { index->
        index<exerciseIndex && plan.exercises[index].sets.any { it.slot !in committedSlots }
    }
    var showFinishReview by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showLast by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    PageHeading("WEEK ${plan.session.weekIndex}",plan.dayLabel,plan.programName)
    Text("${journal.completed.size} of ${plan.slots.size} working sets saved · ${plan.session.unit}",
        style=MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(progress={journal.completed.size.toFloat()/plan.slots.size},
        modifier=Modifier.fillMaxWidth())
    Text("Completed and skipped sets are saved on this device. You can leave and resume this workout.",
        style=MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        plan.exercises.forEachIndexed { index,ex->
            val completed=ex.sets.count { it.slot in committedSlots }
            FilterChip(selected=index==exerciseIndex,onClick={exerciseIndex=index},
                label={Text("${index+1}. ${ex.name} · $completed/${ex.sets.size}")})
        }
    }
    PursuitCard {
        Text(exercise.name,style=MaterialTheme.typography.headlineSmall)
        Text("Rest ${exercise.restSeconds} seconds between working sets" + if(exercise.superset)" · Superset" else "")
        if(exercise.warmups.isNotEmpty()) {
            Text("Warm-up targets",style=MaterialTheme.typography.titleSmall)
            exercise.warmups.forEach { Text(it,style=MaterialTheme.typography.bodyMedium) }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=!showLast,onClick={showLast=false},label={Text("Targets")})
        FilterChip(selected=showLast,onClick={showLast=true},label={Text("Last time")})
    }
    exercise.sets.forEach { set->
        val commit=journal.completed.find { it.intent.slot==set.slot }
        key(plan.session.workoutId,set.slot.exerciseIndex,set.slot.setIndex) {
            NativeWorkingSet(plan.session,set,commit,state.workoutBusy || journal.isFinished,showLast,onRecord)
        }
    }
    if(nextUnfinished!=null)FilledTonalButton(onClick={exerciseIndex=nextUnfinished},
        modifier=Modifier.fillMaxWidth()) {
        Text("Go to next unfinished exercise")
    }
    state.workoutError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    PursuitCard(highlighted=journal.unresolvedCount==0) {
        if(journal.unresolvedCount>0)Text("Complete or skip ${journal.unresolvedCount} remaining sets to finish.")
        else Text(if(journal.isFinished)"All sets are saved. Finishing your training history…" else "Every working set is saved. Ready to finish.")
        Button(onClick={if(journal.isFinished)onFinish() else showFinishReview=true},
            enabled=journal.unresolvedCount==0 && !state.workoutBusy,
            modifier=Modifier.fillMaxWidth()) {
            Text(if(state.workoutBusy)"Saving…" else if(journal.isFinished)"Retry finishing workout" else "Review and finish")
        }
    }
    if(showFinishReview && !journal.isFinished && journal.unresolvedCount==0) {
        val skipped=journal.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED }
        AlertDialog(
            onDismissRequest={showFinishReview=false},
            title={Text("Finish this workout?")},
            text={Text("${journal.completed.size-skipped} completed · $skipped skipped · ${journal.completed.size} of ${plan.slots.size} sets saved. Finishing publishes these observations to training history.")},
            confirmButton={Button(onClick={showFinishReview=false;onFinish()},
                enabled=!state.workoutBusy) { Text("Finish workout") }},
            dismissButton={TextButton(onClick={showFinishReview=false}) { Text("Keep training") }}
        )
    }
}

@Composable
private fun NativeWorkingSet(session: JournalSessionKey,set: NativeWorkoutSet,commit: JournalSetCommit?,busy: Boolean,
    showLast: Boolean,onRecord: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit) {
    var load by rememberSaveable { mutableStateOf(set.load) }
    var reps by rememberSaveable { mutableStateOf(set.reps) }
    // Optional actual effort starts empty, independently of the prescribed RIR.
    var rir by rememberSaveable { mutableStateOf("") }
    var showSkipConfirmation by rememberSaveable { mutableStateOf(false) }
    if(showSkipConfirmation && commit==null) {
        AlertDialog(
            onDismissRequest={showSkipConfirmation=false},
            title={Text("Skip set ${set.slot.setIndex+1}?")},
            text={Text("Skipping is saved to your workout log. It will not be treated as a completed training set.")},
            confirmButton={Button(onClick={
                showSkipConfirmation=false
                onRecord(set.slot,JournalSetDisposition.SKIPPED,null)
            },enabled=!busy) { Text("Skip set") }},
            dismissButton={TextButton(onClick={showSkipConfirmation=false}) { Text("Keep set") }}
        )
    }
    PursuitCard {
        Text("Set ${set.slot.setIndex+1}",style=MaterialTheme.typography.titleMedium)
        if(showLast)Text(set.last?.let { "Last time: $it" } ?: "No previous result for this set",
            style=MaterialTheme.typography.bodySmall)
        else Text("Target: ${set.targetReps} reps" + (set.targetRir?.let { " · $it RIR" } ?: ""))
        if(commit!=null) {
            if(commit.intent.disposition==JournalSetDisposition.SKIPPED)Text("Skipped · saved")
            else commit.intent.values?.let { value->
                Text("${value.load} ${value.unit} × ${value.reps} · saved")
                Text(value.rir?.let { "$it RIR reported" } ?: "RIR not reported",style=MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value=load,onValueChange={load=it},label={Text("Load (${session.unit})")},
                    modifier=Modifier.weight(1f),singleLine=true,enabled=!busy,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(value=reps,onValueChange={reps=it},label={Text("Reps")},
                    modifier=Modifier.weight(1f),singleLine=true,enabled=!busy,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }
            OutlinedTextField(value=rir,onValueChange={rir=it},label={Text("Actual RIR · optional")},
                supportingText={Text("Reps you could still have done. Use 0 load for bodyweight.")},
                modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!busy,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            val validLoad=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$"))
            val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
            Button(onClick={onRecord(set.slot,JournalSetDisposition.COMPLETED,
                JournalSetValues(load,session.unit,checkNotNull(repCount),effort))},
                enabled=!busy && validLoad && repCount!=null && repCount in 1..100 &&
                    (rir.isEmpty() || effort!=null && effort in 0..10),modifier=Modifier.fillMaxWidth()) {
                Text("Complete set")
            }
            TextButton(onClick={showSkipConfirmation=true},enabled=!busy) { Text("Skip set") }
        }
    }
}
