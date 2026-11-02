package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
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
    onReduceAffectedMuscle: (String)->Unit = {},
    onSaveSessionNote: (String)->Unit = {},
    onSaveWarmup: (JournalWarmupIntent)->Unit = {},
    onSaveExerciseNote: (JournalExerciseNote)->Unit = {},
    onInspectEquipmentLoad:(JournalSetSlot,String)->Unit = {_,_->}
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
            Button(onClick=onStart,enabled=state.ready && !state.workoutBusy && !state.historyBusy && !state.backupExportBusy && !state.workoutBlocked,
                modifier=Modifier.fillMaxWidth()) {
                Text(if(state.backupExportBusy)"Saving backup…" else if(state.workoutBusy)"Opening workout…" else "Start next workout")
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
    val nextPreview=NativeNextSetPolicy.inspect(progress,exerciseIndex)
    var showNote by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showFinishReview by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showSkipExercise by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    var showLast by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    // A session opens on one actionable set. Show all remains available.
    var focusMode by rememberSaveable(plan.session.workoutId) { mutableStateOf(true) }
    val lastSetReceiptId=journal.events.lastOrNull()?.intent?.operationId
    // The event observed at first composition is a baseline, not a new set.
    // Keep this saveable through rotation, navigation and ordinary recovery.
    var observedFocusReceipt by rememberSaveable(plan.session.workoutId) {
        mutableStateOf(lastSetReceiptId)
    }
    LaunchedEffect(lastSetReceiptId,focusMode,state.workoutBusy,state.historyBusy,exerciseIndex) {
        if(lastSetReceiptId!=observedFocusReceipt) {
            if(!focusMode || journal.isFinished)observedFocusReceipt=lastSetReceiptId
            else if(!state.workoutBusy && !state.historyBusy) {
                val destination=NativeNextSetPolicy.advanceAfterAcknowledged(
                    progress,exerciseIndex,observedFocusReceipt)
                observedFocusReceipt=lastSetReceiptId
                if(destination!=null)exerciseIndex=destination
            }
        }
    }
    // Each working set has its own saveable draft even while other exercises
    // replace the visible composition. Drafts are not confirmed journal events.
    val setDraftStates=rememberSaveableStateHolder()
    PageHeading("WEEK ${plan.session.weekIndex}",plan.dayLabel,plan.programName)
    var affectedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    var confirmAffectedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    var confirmReducedPart by rememberSaveable(plan.session.workoutId) { mutableStateOf("") }
    var showAffectedTools by rememberSaveable(plan.session.workoutId) { mutableStateOf(false) }
    if(plan.shortenedMinutes!=null) {
        Text("Shortened workout · ${plan.shortenedMinutes} min selected · ${plan.shortenedEstimatedMinutes} min estimated",
            style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
        Text("Your logged dose will be marked time-limited; reductions are not failed sets.",
            style=MaterialTheme.typography.bodySmall)
    }
    val skippedSetCount=journal.completed.count {
        it.intent.disposition==JournalSetDisposition.SKIPPED
    }
    Text("${journal.completed.size} / ${plan.slots.size} sets resolved · " +
        "${journal.completed.size-skippedSetCount} logged · $skippedSetCount skipped",
        style=MaterialTheme.typography.titleSmall)
    LinearProgressIndicator(progress={journal.completed.size.toFloat()/plan.slots.size},
        modifier=Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        plan.exercises.forEachIndexed { index,ex->
            val completed=ex.sets.count { it.slot in committedSlots }
            FilterChip(selected=index==exerciseIndex,onClick={exerciseIndex=index},
                label={Text("${index+1}. ${ex.name} · $completed/${ex.sets.size}")})
        }
    }
    var showWarmups by rememberSaveable(plan.session.workoutId,exercise.index) {
        mutableStateOf(false)
    }
    Text(exercise.name,style=MaterialTheme.typography.headlineSmall)
    if(exercise.superset)Text("Superset pairing",style=MaterialTheme.typography.labelMedium)
    if(exercise.warmupTargets.isNotEmpty() ||
        journal.warmups.any { it.slot.exerciseIndex==exercise.index }) {
        OutlinedButton(onClick={showWarmups=!showWarmups},
            modifier=Modifier.fillMaxWidth()) {
            Text(if(showWarmups)"Hide warm-ups" else
                "Warm-ups · ${exercise.warmupTargets.size} suggested")
        }
    } else if(!showWarmups) {
        TextButton(onClick={showWarmups=true}) { Text("Add a warm-up") }
    }
    if(showWarmups) NativeWarmupPanel(
        progress,exercise,state.workoutBusy || state.historyBusy,
        state.warmupError,onSaveWarmup)

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
    val pinnedNote=NativeExerciseNotePolicy.resolved(plan,journal.exerciseNotes,exercise.id)
    var showPinnedNote by rememberSaveable(plan.session.workoutId,exercise.id) {mutableStateOf(false)}
    if(showPinnedNote)NativeExerciseNoteSheet(progress,exercise,state.workoutBusy || state.historyBusy,
        state.exerciseNoteError,onSaveExerciseNote) {showPinnedNote=false}
    if(showNote)NativeSessionNoteSheet(progress,state.workoutBusy,state.workoutError,onSaveSessionNote) {showNote=false}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        IconButton(onClick={showNote=true},enabled=!journal.isFinished && !state.workoutBusy) {
            Icon(Icons.Outlined.Edit,contentDescription=if(journal.note?.text.isNullOrEmpty())"Add session note" else "Edit session note",
                tint=if(journal.note?.text.isNullOrEmpty())MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        }
        if(pinnedNote!=null)IconButton(onClick={showPinnedNote=true},
            enabled=!journal.isFinished && !state.workoutBusy && !state.historyBusy) {
            Icon(Icons.Outlined.PushPin,contentDescription="Edit pinned note and setup for ${exercise.name}",
                tint=if(pinnedNote.text.isNotEmpty() || pinnedNote.setup.values.any {it.isNotEmpty()})
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilterChip(selected=!showLast,onClick={showLast=false},label={Text("Targets")})
        FilterChip(selected=showLast,onClick={showLast=true},label={Text("Last time")})
        Spacer(Modifier.weight(1f))
        FilterChip(selected=focusMode,onClick={focusMode=!focusMode},
            label={Text(if(focusMode)"Show all" else "Focus")})
    }
    if(pinnedNote!=null && (pinnedNote.text.isNotEmpty() || pinnedNote.setup.values.any {it.isNotEmpty()})) {
        OutlinedCard(onClick={showPinnedNote=true},enabled=!journal.isFinished && !state.workoutBusy && !state.historyBusy,
            modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val setup=pinnedNote.fields.mapNotNull {field->pinnedNote.setup[field.key]?.takeIf {it.isNotEmpty()}
                    ?.let {"${field.label} $it"}}.joinToString(" · ")
                if(setup.isNotEmpty())Text(setup,style=MaterialTheme.typography.labelMedium)
                if(pinnedNote.text.isNotEmpty())Text(pinnedNote.text,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
    // Focus is a projection of the same durable set journal, never a
    // different logger. It advances only after an acknowledged set receipt.
    val visibleSets=if(focusMode)listOf(exercise.sets.firstOrNull {
        it.slot !in committedSlots
    } ?: exercise.sets.last()) else exercise.sets
    visibleSets.forEach { set->
        val commit=journal.completed.find { it.intent.slot==set.slot }
        key(plan.session.workoutId,set.slot.exerciseIndex,set.slot.setIndex) {
            setDraftStates.SaveableStateProvider(
                "${plan.session.workoutId}:${set.slot.exerciseIndex}:${set.slot.setIndex}"
            ) {
                NativeWorkingSet(plan.session,set,commit,state.workoutBusy || journal.isFinished,showLast,onRecord,
                    onCorrect,state.workoutError,state,onInspectEquipmentLoad)
            }
        }
    }
    val activeExtension=if(focusMode)exercise.sets.firstOrNull {
        it.slot !in committedSlots
    }?.extensionRestSeconds else null
    NativeRestTimer(plan.session.workoutId, activeExtension ?: exercise.restSeconds,
        enabled=!state.workoutBusy && !journal.isFinished,
        latestReceiptOperationId=journal.events.lastOrNull()?.intent?.operationId,
        autoCue=NativeRestCuePolicy.latest(progress,exercise.index))
    if(exercisePending>0 && !journal.isFinished) {
        TextButton(onClick={showSkipExercise=true},
            enabled=!state.workoutBusy && !state.historyBusy) {
            Text("Skip remaining ${exercisePending} sets for this exercise")
        }
    }
    nextPreview?.let { preview->
        NativeUpNextPanel(preview,exerciseIndex,
            enabled=!state.workoutBusy && !state.historyBusy,
            unit=plan.session.unit,
            onOpenExercise={ position ->exerciseIndex=position })
    }
    if(!showAffectedTools) {
        TextButton(onClick={showAffectedTools=true},
            enabled=!state.workoutBusy && !journal.isFinished) {
            Text("Adjust workout for discomfort")
        }
    } else PursuitCard {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Affected muscle review",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={showAffectedTools=false}) {Text("Close")}
        }
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
        val recordedWarmups=journal.warmups.map {it.slot}.toSet()
        val unloggedWarmups=plan.exercises.flatMap {it.warmupTargets}.count {it.slot !in recordedWarmups}
        AlertDialog(
            onDismissRequest={showFinishReview=false},
            title={Text("Finish this workout?")},
            text={Text("${journal.completed.size-skipped} recorded · $skipped skipped · ${journal.completed.size} of ${plan.slots.size} sets saved. Finishing publishes these observations to training history."+
                if(unloggedWarmups>0)" $unloggedWarmups warm-up targets are unrecorded." else "")},
            confirmButton={Button(onClick={showFinishReview=false;onFinish()},
                enabled=!state.workoutBusy && !state.historyBusy) { Text("Finish workout") }},
            dismissButton={TextButton(onClick={showFinishReview=false}) { Text("Keep training") }}
        )
    }
}

@Composable
private fun NativeWorkingSet(session: JournalSessionKey,set: NativeWorkoutSet,commit: JournalSetCommit?,busy: Boolean,
    showLast: Boolean,onRecord: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit,
    onCorrect: (JournalSetIntent)->Unit,error: String?,
    state:NativeRuntimeState,onInspectEquipmentLoad:(JournalSetSlot,String)->Unit) {
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
                Text((if(value.reps==0)"Failed attempt · " else "")+"${value.load} ${value.unit} × ${value.reps} · saved")
                Text(value.rir?.let { "$it RIR reported" } ?: "RIR not reported",style=MaterialTheme.typography.bodySmall)
            }
            NativeActiveSetCorrection(session,set,commit,busy,error,onCorrect)
        } else {
            NativeSetValueFields(session.unit,load,reps,rir,!busy,{load=it},{reps=it},{rir=it},
                allowFailedAttempt=set.allowFailedAttempt)
            val review=state.equipmentLoad?.takeIf {
                it.workoutId==session.workoutId && it.slot==set.slot &&
                it.unit==session.unit && it.inputLoad==load
            }
            if(load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$"))) {
                OutlinedButton(onClick={onInspectEquipmentLoad(set.slot,load)},
                    enabled=!busy && !state.equipmentLoadBusy &&
                        !state.historyBusy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(state.equipmentLoadBusy)"Checking available loads…"
                        else "Check equipment-aware weights")
                }
            }
            if(review?.confirmed==true) {
                val explanation=when(review.family) {
                    "dumbbells_per_hand" -> "Verified dumbbell loads · per hand"
                    "barbell_plates" -> "Verified bar weight and plate pairs · total load"
                    "exercise_override" -> if(review.mode=="external_load")
                        "Verified selectable logged values" else "Verified exercise-specific loading"
                    "smith_confirmed_totals" -> "Verified Smith total load values"
                    "machine_stack" -> "Verified machine rack"
                    "cable_stack" -> "Verified cable stack"
                    else -> "Verified equipment"
                }
                Text(explanation,style=MaterialTheme.typography.labelMedium)
                if(review.family=="exercise_override" && review.mode=="external_load")
                    Text("These are your verified logged machine values, not a measurement of " +
                        "effective resistance. Use the same logging basis for every set.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!review.exact)Text("This entered load is not selectable in the saved equipment list. Manual entry remains available.",
                    style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick={load=checkNotNull(review.previous)},
                        enabled=!busy && review.previous!=null,modifier=Modifier.weight(1f)) {
                        Text(review.previous?.let { "− $it ${session.unit}" } ?: "No lower load")
                    }
                    OutlinedButton(onClick={load=checkNotNull(review.next)},
                        enabled=!busy && review.next!=null,modifier=Modifier.weight(1f)) {
                        Text(review.next?.let { "+ $it ${session.unit}" } ?: "No higher load")
                    }
                }
            } else if(review!=null) {
                Text("Equipment not confirmed for this exercise. No assumed load increments are offered.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.equipmentLoadError?.let {
                if(review==null)Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            }
            val validLoad=NativeSetEntryPolicy.accepts(load,reps,rir,set.allowFailedAttempt)
            val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
            Button(onClick={onRecord(set.slot,JournalSetDisposition.COMPLETED,
                JournalSetValues(load,session.unit,checkNotNull(repCount),effort))},
                enabled=!busy && validLoad,modifier=Modifier.fillMaxWidth()) {
                Text(if(set.isExtension)"Complete extension" else if(repCount==0)"Record failed attempt" else "Complete set")
            }
            TextButton(onClick={showSkipConfirmation=true},enabled=!busy) { Text(if(set.isExtension)"Skip extension" else "Skip set") }
        }
    }
}
