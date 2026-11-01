package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*

/** Only a durably pinned, engine-issued session can reach these controls. */
@Composable
internal fun NativeWorkoutScreen(
    state: NativeRuntimeState,onStart: ()->Unit,
    onRecord: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit,onFinish: ()->Unit,
    onCorrect: (JournalSetIntent)->Unit = {},
    onSkipExercise: (Int)->Unit = {},
    onInspectMuscles: ()->Unit = {},
    onSkipAffectedMuscle: (String)->Unit = {},
    onReduceAffectedMuscle: (String)->Unit = {}
) {
    val progress=state.workout
    // Native sessions keep the screen awake while Train is visible. The
    // setting is scoped to this composable and restored on navigation away.
    val view=LocalView.current
    DisposableEffect(view,progress?.plan?.session?.workoutId) {
        val prior=view.keepScreenOn
        if(progress!=null)view.keepScreenOn=true
        onDispose { view.keepScreenOn=prior }
    }
    if(progress==null) {
        PageHeading("TRAIN",if(state.completion!=null)"Workout saved" else "Ready when you are",
            state.saved?.program?.name ?: "Your training")
        PursuitCard(highlighted=true) {
            if(state.completion!=null)Text("Your workout is in your training history. The next session will use your logged performance.")
            else Text("Open your next scheduled workout with targets from your saved program.")
            Button(onClick=onStart,enabled=state.ready && !state.workoutBusy && !state.historyBusy && !state.workoutBlocked,
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
    val exercisePending=exercise.sets.count { it.slot !in committedSlots }
    val nextUnfinished=plan.exercises.indices.firstOrNull { index->
        index>exerciseIndex && plan.exercises[index].sets.any { it.slot !in committedSlots }
    } ?: plan.exercises.indices.firstOrNull { index->
        index<exerciseIndex && plan.exercises[index].sets.any { it.slot !in committedSlots }
    }
    var showFinishReview by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showSkipExercise by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showLast by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var focusMode by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    // Each working set has its own saveable draft even while other exercises
    // replace the visible composition. Drafts are not confirmed journal events.
    val setDraftStates=rememberSaveableStateHolder()
    PageHeading("WEEK ${plan.session.weekIndex}",plan.dayLabel,plan.programName)
    var affectedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    var confirmAffectedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    var confirmReducedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    PursuitCard {
        Text("Affected muscle review",style=MaterialTheme.typography.titleMedium)
        Text("Shows muscles involved in this workout, including secondary work. Review potentially affected lifts; nothing is skipped automatically.",
            style=MaterialTheme.typography.bodySmall)
        if(state.muscleReview==null) {
            OutlinedButton(onClick=onInspectMuscles,
                enabled=!state.muscleReviewBusy && !state.workoutBusy && !state.historyBusy) {
                Text(if(state.muscleReviewBusy)"Checking exercises…" else "Review muscle involvement")
            }
        }
        state.muscleReviewError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        state.muscleReview?.takeIf { it.workoutId==plan.session.workoutId }?.let { review ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                review.parts.forEach { part ->
                    FilterChip(selected=affectedPart==part,onClick={affectedPart=part},
                        label={Text(part.replace('_',' ').replaceFirstChar { it.uppercase() })})
                }
            }
            if(affectedPart.isNotBlank()) {
                val related=review.rows.filter { affectedPart in it.involvedParts }
                Text("${related.size} potentially related exercise(s)",style=MaterialTheme.typography.labelLarge)
                related.forEach { match ->
                    Text(match.name,style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={
                        val index=plan.exercises.indexOfFirst { it.index==match.exerciseIndex }
                        if(index>=0)exerciseIndex=index
                    },enabled=!state.workoutBusy) { Text("Open exercise to review or skip") }
                }
                val relatedIndices=related.map { it.exerciseIndex }.toSet()
                val pendingRelated=journal.slots.count {
                    it.exerciseIndex in relatedIndices && it !in committedSlots
                }
                val pendingReduction=runCatching {
                    NativeSessionSkipPolicy.reduceAffected(journal,review,affectedPart)
                }.getOrDefault(emptyList()).size
                if(pendingReduction>0 && !journal.isFinished) {
                    OutlinedButton(onClick={confirmReducedPart=affectedPart},
                        enabled=!state.workoutBusy && !state.historyBusy,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Reduce planned work · skip $pendingReduction unfinished sets")
                    }
                    Text("Keeps the first half of originally issued sets per related exercise, rounded up. Already logged sets stay logged.",
                        style=MaterialTheme.typography.bodySmall)
                }
                if(pendingRelated>0 && !journal.isFinished) {
                    FilledTonalButton(onClick={confirmAffectedPart=affectedPart},
                        enabled=!state.workoutBusy && !state.historyBusy,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Skip $pendingRelated unfinished related sets")
                    }
                } else {
                    Text("No unfinished related sets remain.")
                }
            }
            Text("Muscle involvement is not medical guidance. Avoid movements that cause pain.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirmReducedPart.isNotEmpty()) {
        val review=state.muscleReview
        val removed=review?.let {
            runCatching { NativeSessionSkipPolicy.reduceAffected(journal,it,confirmReducedPart) }
                .getOrDefault(emptyList())
        }.orEmpty()
        val named=review?.rows?.filter { confirmReducedPart in it.involvedParts }
            ?.map { it.name }?.distinct().orEmpty()
        AlertDialog(onDismissRequest={confirmReducedPart=""},
            title={Text("Reduce planned work for $confirmReducedPart?")},
            text={Text("${removed.size} unfinished late sets will be saved as skipped across ${named.joinToString(", ")}. The first half of each original prescription remains available, and all completed sets are preserved. This does not change future workouts.")},
            confirmButton={Button(onClick={
                val approved=confirmReducedPart
                confirmReducedPart=""
                onReduceAffectedMuscle(approved)
            },enabled=removed.isNotEmpty() && !state.workoutBusy && !state.historyBusy &&
                !journal.isFinished) { Text("Confirm reduced work") }},
            dismissButton={TextButton(onClick={confirmReducedPart=""}) { Text("Keep full workout") }})
    }
    if(confirmAffectedPart.isNotEmpty()) {
        val selected=state.muscleReview?.rows?.filter { confirmAffectedPart in it.involvedParts }.orEmpty()
        val named=selected.map { it.name }.distinct()
        val indices=selected.map { it.exerciseIndex }.toSet()
        val pendingCount=journal.slots.count { it.exerciseIndex in indices && it !in committedSlots }
        AlertDialog(onDismissRequest={confirmAffectedPart=""},
            title={Text("Skip unfinished sets for $confirmAffectedPart?")},
            text={Text("$pendingCount unfinished sets across ${named.joinToString(", ")} will be saved as skipped. Completed work is preserved. This does not record a medical diagnosis or alter future workouts.")},
            confirmButton={Button(onClick={
                val confirmedPart=confirmAffectedPart
                confirmAffectedPart=""
                onSkipAffectedMuscle(confirmedPart)
            },enabled=pendingCount>0 && !state.workoutBusy && !state.historyBusy &&
                !journal.isFinished) { Text("Confirm related skips") }},
            dismissButton={TextButton(onClick={confirmAffectedPart=""}) { Text("Keep training") }})
    }
    if(plan.shortenedMinutes!=null) {
        Text("Shortened workout · ${plan.shortenedMinutes} min selected · ${plan.shortenedEstimatedMinutes} min estimated",
            style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
        Text("Your logged dose will be marked time-limited; reductions are not failed sets.",
            style=MaterialTheme.typography.bodySmall)
    }
    Text("${journal.completed.size} of ${plan.slots.size} set steps saved · ${plan.session.unit}",
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
        if(exercise.superset)Text("Superset pairing",style=MaterialTheme.typography.labelMedium)
        val activeExtension=if(focusMode)exercise.sets.firstOrNull { it.slot !in committedSlots }
            ?.extensionRestSeconds else null
        NativeRestTimer(plan.session.workoutId, activeExtension ?: exercise.restSeconds,
            enabled=!state.workoutBusy && !journal.isFinished)
        if(exercise.warmups.isNotEmpty()) {
            Text("Warm-up targets",style=MaterialTheme.typography.titleSmall)
            exercise.warmups.forEach { Text(it,style=MaterialTheme.typography.bodyMedium) }
        }
        if(exercisePending>0 && !journal.isFinished) {
            OutlinedButton(onClick={showSkipExercise=true},
                enabled=!state.workoutBusy && !state.historyBusy,
                modifier=Modifier.fillMaxWidth()) {
                Text("Skip remaining exercise · $exercisePending sets")
            }
        }
    }
    if(showSkipExercise) {
        AlertDialog(
            onDismissRequest={showSkipExercise=false},
            title={Text("Skip the rest of ${exercise.name}?")},
            text={Text("$exercisePending unfinished sets will each be saved as skipped. Completed sets stay unchanged. This does not automatically modify other exercises or your future program.")},
            confirmButton={Button(onClick={showSkipExercise=false;onSkipExercise(exercise.index)},
                enabled=exercisePending>0 && !state.workoutBusy && !state.historyBusy && !journal.isFinished) {
                Text("Skip remaining sets")
            }},
            dismissButton={TextButton(onClick={showSkipExercise=false}) { Text("Keep training") }}
        )
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=!showLast,onClick={showLast=false},label={Text("Targets")})
        FilterChip(selected=showLast,onClick={showLast=true},label={Text("Last time")})
        Spacer(Modifier.weight(1f))
        FilterChip(selected=focusMode,onClick={focusMode=!focusMode},
            label={Text(if(focusMode)"Show all" else "Focus")})
    }
    // Focus is a projection of the same durable set journal, never a
    // different logger. It advances only after an acknowledged set receipt.
    val visibleSets=if(focusMode)listOf(exercise.sets.firstOrNull {
        it.slot !in committedSlots
    } ?: exercise.sets.last()) else exercise.sets
    if(focusMode)Text("One set at a time · full controls remain available",
        style=MaterialTheme.typography.bodySmall)
    visibleSets.forEach { set->
        val commit=journal.completed.find { it.intent.slot==set.slot }
        key(plan.session.workoutId,set.slot.exerciseIndex,set.slot.setIndex) {
            setDraftStates.SaveableStateProvider(
                "${plan.session.workoutId}:${set.slot.exerciseIndex}:${set.slot.setIndex}"
            ) {
                NativeWorkingSet(plan.session,set,commit,state.workoutBusy || journal.isFinished,showLast,onRecord,
                    onCorrect,state.workoutError)
            }
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
            enabled=journal.unresolvedCount==0 && !state.workoutBusy && !state.historyBusy,
            modifier=Modifier.fillMaxWidth()) {
            Text(if(state.historyBusy)"Loading history…" else if(state.workoutBusy)"Saving…" else if(journal.isFinished)"Retry finishing workout" else "Review and finish")
        }
    }
    if(showFinishReview && !journal.isFinished && journal.unresolvedCount==0) {
        val skipped=journal.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED }
        AlertDialog(
            onDismissRequest={showFinishReview=false},
            title={Text("Finish this workout?")},
            text={Text("${journal.completed.size-skipped} completed · $skipped skipped · ${journal.completed.size} of ${plan.slots.size} sets saved. Finishing publishes these observations to training history.")},
            confirmButton={Button(onClick={showFinishReview=false;onFinish()},
                enabled=!state.workoutBusy && !state.historyBusy) { Text("Finish workout") }},
            dismissButton={TextButton(onClick={showFinishReview=false}) { Text("Keep training") }}
        )
    }
}

@Composable
private fun NativeWorkingSet(session: JournalSessionKey,set: NativeWorkoutSet,commit: JournalSetCommit?,busy: Boolean,
    showLast: Boolean,onRecord: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit,
    onCorrect: (JournalSetIntent)->Unit,error: String?) {
    var load by rememberSaveable { mutableStateOf(set.load) }
    var reps by rememberSaveable { mutableStateOf(set.reps) }
    // Optional actual effort starts empty, independently of the prescribed RIR.
    var rir by rememberSaveable { mutableStateOf("") }
    var showSkipConfirmation by rememberSaveable { mutableStateOf(false) }
    if(showSkipConfirmation && commit==null) {
        AlertDialog(
            onDismissRequest={showSkipConfirmation=false},
            title={Text(if(set.isExtension)"Skip extension?" else "Skip set ${set.slot.setIndex+1}?")},
            text={Text("Skipping is saved to your workout log. It will not be treated as a completed training set.")},
            confirmButton={Button(onClick={
                showSkipConfirmation=false
                onRecord(set.slot,JournalSetDisposition.SKIPPED,null)
            },enabled=!busy) { Text("Skip set") }},
            dismissButton={TextButton(onClick={showSkipConfirmation=false}) { Text("Keep set") }}
        )
    }
    PursuitCard {
        Text(if(set.isExtension)"${if(set.kind=="myo")"Myo-rep mini" else "Drop"} set"
            else "Set ${set.slot.setIndex+1}",style=MaterialTheme.typography.titleMedium)
        set.extensionRestSeconds?.let { seconds->
            Text("Extension of the last working set · $seconds-second rest target",
                style=MaterialTheme.typography.labelMedium)
        }
        if(showLast)Text(set.last?.let { "Last time: $it" } ?: "No previous result for this set",
            style=MaterialTheme.typography.bodySmall)
        else Text("Target: ${set.targetReps} reps" + (set.targetRir?.let { " · $it RIR" } ?: ""))
        if(commit!=null) {
            if(commit.intent.disposition==JournalSetDisposition.SKIPPED)Text("Skipped · saved")
            else commit.intent.values?.let { value->
                Text("${value.load} ${value.unit} × ${value.reps} · saved")
                Text(value.rir?.let { "$it RIR reported" } ?: "RIR not reported",style=MaterialTheme.typography.bodySmall)
            }
            NativeActiveSetCorrection(session,set,commit,busy,error,onCorrect)
        } else {
            NativeSetValueFields(session.unit,load,reps,rir,!busy,{load=it},{reps=it},{rir=it})
            val validLoad=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$"))
            val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
            Button(onClick={onRecord(set.slot,JournalSetDisposition.COMPLETED,
                JournalSetValues(load,session.unit,checkNotNull(repCount),effort))},
                enabled=!busy && validLoad && repCount!=null && repCount in 1..100 &&
                    (rir.isEmpty() || effort!=null && effort in 0..10),modifier=Modifier.fillMaxWidth()) {
                Text(if(set.isExtension)"Complete extension" else "Complete set")
            }
            TextButton(onClick={showSkipConfirmation=true},enabled=!busy) { Text(if(set.isExtension)"Skip extension" else "Skip set") }
        }
    }
}
