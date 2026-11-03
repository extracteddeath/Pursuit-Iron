package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeJournalFileRefusal
import java.math.BigDecimal

/** Parses user-entered ACTUALS. Never replace missing observed RIR with target RIR. */
private fun performed(
    target:KotlinWorkoutTarget,
    weight:String,reps:String,rir:String,
    failed:Boolean=false
):KotlinPerformedSet? {
    val repetitions=if(failed)0 else reps.trim().toIntOrNull() ?:return null
    if(repetitions !in 0..100 || (!failed && repetitions==0))return null
    val effort=if(failed || rir.isBlank())null
        else rir.trim().toIntOrNull() ?:return null
    if(effort!=null && effort !in 0..10)return null
    return when(val load=target.load) {
        DraftSetLoad.ExplicitBodyweight ->
            KotlinPerformedSet(null,null,repetitions,effort)
        is DraftSetLoad.Verified -> {
            val value=runCatching{BigDecimal(weight.trim())}.getOrNull()
                ?:return null
            if(value<BigDecimal.ZERO || value>BigDecimal("100000") ||
                value.stripTrailingZeros().scale()>3)return null
            KotlinPerformedSet(value,load.unit,repetitions,effort)
        }
    }
}

private fun loadLabel(load:DraftSetLoad):String=when(load) {
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()} · "+
            when(load.basis) {
                LoadBasis.DUMBBELL_PER_HAND -> "per hand"
                LoadBasis.BARBELL_TOTAL -> "total barbell"
                LoadBasis.SMITH_TOTAL -> "Smith total"
                LoadBasis.CABLE_STACK -> "cable stack"
                LoadBasis.MACHINE_STACK -> "machine stack"
                LoadBasis.EXERCISE_OVERRIDE -> "verified load"
            }
}
private fun humanStatus(reason:NativeJournalFileRefusal?):String=when(reason) {
    NativeJournalFileRefusal.NOT_AUTHORIZED -> "Activate your approved local athlete before opening this workout."
    NativeJournalFileRefusal.NOT_ISSUED -> "No saved Kotlin workout exists for this selection."
    NativeJournalFileRefusal.SOURCE_CONFLICT,NativeJournalFileRefusal.ISSUE_CONFLICT ->
        "This workout was issued under different saved program details. It was not overwritten."
    NativeJournalFileRefusal.CORRUPT_RECEIPTS ->
        "Saved workout verification failed. No record was reset or overwritten."
    NativeJournalFileRefusal.JOURNAL_REJECTED ->
        "The set change was rejected. Reloaded the last durable workout state."
    NativeJournalFileRefusal.STORAGE_UNAVAILABLE ->
        "Unable to access verified native workout storage."
    NativeJournalFileRefusal.INVALID_ARGUMENT ->
        "The workout identifier was not valid."
    null -> ""
}

/**
 * Complete Kotlin-only Compose workout candidate. Its data and actions come
 * from the native revision-checked session controller, not the existing JS
 * runtime. Deliberately NOT selected from MainActivity yet: the authenticated
 * onboarding/program-approval migration is the next cutover gate.
 */
@Composable
internal fun NativeKotlinWorkoutCandidateScreen(
    controller:NativeKotlinSessionController,
    workoutId:String
) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(workoutId) { controller.open(workoutId) }
    val presentation=state.presentation?.takeIf{it.workoutId==workoutId}
    val journal=state.journal?.takeIf{it.snapshot.workoutId==workoutId}
    val running=presentation!=null && !presentation.finished

    val view=LocalView.current
    DisposableEffect(view,running) {
        val previous=view.keepScreenOn
        if(running)view.keepScreenOn=true
        onDispose{view.keepScreenOn=previous}
    }

    var showFinishConfirm by rememberSaveable(workoutId){mutableStateOf(false)}
    var pendingSkip by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var pendingFail by remember(workoutId) {
        mutableStateOf<Pair<String,KotlinPerformedSet>?>(null)
    }
    var selectedKey by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var showAll by rememberSaveable(workoutId){mutableStateOf(false)}
    LaunchedEffect(presentation?.revision) {
        if(presentation!=null)
            selectedKey=presentation.firstUnresolvedSetKey ?:
                presentation.exercises.lastOrNull()?.sets?.lastOrNull()?.target?.setKey
    }
    val allSets=presentation?.exercises?.flatMap{it.sets}.orEmpty()
    val selected=allSets.firstOrNull{it.target.setKey==selectedKey}
        ?:allSets.firstOrNull()
    val activeKeys=if(showAll)allSets else listOfNotNull(selected)

    if(showFinishConfirm && presentation?.canFinish==true) {
        AlertDialog(onDismissRequest={showFinishConfirm=false},
            title={Text("Finish this workout?")},
            text={Text("All prescribed sets have an outcome. Finishing saves a permanent event; it will not change target weights or automatically create another workout.")},
            confirmButton={
                Button(onClick={
                    showFinishConfirm=false
                    controller.finish()
                },enabled=!state.busy){Text("Finish workout")}
            },
            dismissButton={TextButton(onClick={showFinishConfirm=false}){
                Text("Review sets")
            }})
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal=16.dp,vertical=20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageHeading("KOTLIN COACH · ISOLATED",
            if(presentation?.finished==true)"Workout complete"
            else "Today's workout",
            if(presentation==null)"Native session preview · no JavaScript"
            else "${presentation.purpose.name.replace('_',' ').lowercase()} · "+
                "${presentation.estimatedMinutes} min estimated")
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(presentation==null) {
            PursuitCard(highlighted=true) {
                Text("Open a workout issued by the independent Kotlin coach.",
                    style=MaterialTheme.typography.titleMedium)
                Text(humanStatus(state.unavailableReason).ifBlank{
                    "No confirmed native workout has been loaded. Your existing app data is unchanged."
                })
                if(state.unavailableReason==NativeJournalFileRefusal.NOT_ISSUED) {
                    PursuitPrimaryAction("Start explicitly approved workout",
                        enabled=!state.busy){controller.startApproved(workoutId)}
                }
                OutlinedButton(onClick={controller.open(workoutId)},
                    enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth()) {Text("Reload native workout")}
            }
            return@Column
        }

        PursuitSessionMetrics(
            logged=presentation.completedCount+presentation.failedCount,
            skipped=presentation.skippedCount,
            remaining=presentation.remainingCount)
        LinearProgressIndicator(
            progress={if(allSets.isEmpty())0f else
                presentation.resolvedCount.toFloat()/allSets.size},
            modifier=Modifier.fillMaxWidth())
        state.unavailableReason?.let {
            Text(humanStatus(it),color=MaterialTheme.colorScheme.error)
        }
        state.journalRefusal?.let {
            Text("Set action refused: ${it.name.replace('_',' ').lowercase()}. The saved workout was refreshed.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            presentation.exercises.forEach{ex->
                val selectedExercise=ex.sets.any{it.target.setKey==selected?.target?.setKey}
                FilterChip(selected=selectedExercise,
                    onClick={selectedKey=ex.nextUnresolvedSetKey
                        ?:ex.sets.last().target.setKey},
                    label={Text("${ex.exerciseId} · ${ex.resolvedCount}/${ex.sets.size}")})
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(if(showAll)"All issued sets" else "Focus · one set",
                style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={showAll=!showAll}) {
                Text(if(showAll)"Focus mode" else "Show all")
            }
        }
        activeKeys.forEach{set->
            key(set.target.setKey){
                NativeKotlinSetCard(set,presentation.finished,state.busy,
                    onRecord={outcome,actual->
                        controller.record(set.target.setKey,outcome,actual)
                    },
                    onSkip={pendingSkip=set.target.setKey},
                    onFail={attempt->pendingFail=set.target.setKey to attempt})
            }
        }
        if(!showAll && selected!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                val index=allSets.indexOfFirst{it.target.setKey==selected.target.setKey}
                OutlinedButton(onClick={selectedKey=allSets[index-1].target.setKey},
                    enabled=index>0) {Text("Previous set")}
                OutlinedButton(onClick={selectedKey=allSets[index+1].target.setKey},
                    enabled=index in 0 until allSets.lastIndex) {Text("Next set")}
            }
        }

        if(presentation.canFinish)PursuitCard(highlighted=true) {
            Text("All sets have a saved outcome",
                style=MaterialTheme.typography.titleMedium)
            PursuitPrimaryAction("Review and finish",enabled=!state.busy){
                showFinishConfirm=true
            }
        }
        if(presentation.finished)PursuitCard(highlighted=true) {
            Text("Workout completion is saved",
                style=MaterialTheme.typography.titleMedium)
            Text("Your original targets and every actual, skip, failure and correction remain in the Kotlin journal.")
        }
        OutlinedButton(onClick={controller.open(workoutId)},
            enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
            Text("Reload saved workout")
        }
    }

    val skip=allSets.firstOrNull{it.target.setKey==pendingSkip}
    if(skip!=null)AlertDialog(onDismissRequest={pendingSkip=null},
        title={Text("Skip this set?")},
        text={Text("Skipping records an explicit outcome. You can correct it before finishing.")},
        confirmButton={Button(onClick={
            controller.record(skip.target.setKey,KotlinJournalSetOutcome.SKIPPED,null)
            pendingSkip=null
        },enabled=!state.busy){Text("Skip set")}},
        dismissButton={TextButton(onClick={pendingSkip=null}){Text("Cancel")}})
    if(pendingSkip!=null && skip==null)LaunchedEffect(pendingSkip){pendingSkip=null}
    val failed=allSets.firstOrNull{it.target.setKey==pendingFail?.first}
    if(failed!=null)AlertDialog(onDismissRequest={pendingFail=null},
        title={Text("Log a failed attempt?")},
        text={Text("This records zero completed reps, not a successful set or a progression signal.")},
        confirmButton={Button(onClick={
            // The user explicitly entered the attempted load. Never
            // fabricate an actual by copying the coach's target weight.
            controller.record(failed.target.setKey,
                KotlinJournalSetOutcome.FAILED_ATTEMPT,pendingFail?.second)
            pendingFail=null
        },enabled=!state.busy){Text("Log failed attempt")}},
        dismissButton={TextButton(onClick={pendingFail=null}){Text("Cancel")}})
}

@Composable
private fun NativeKotlinSetCard(
    set:KotlinWorkoutSetPresentation,
    finished:Boolean,
    busy:Boolean,
    onRecord:(KotlinJournalSetOutcome,KotlinPerformedSet?)->Unit,
    onSkip:()->Unit,
    onFail:(KotlinPerformedSet)->Unit
) {
    val target=set.target
    val existing=set.actual
    var weight by rememberSaveable(target.setKey){
        mutableStateOf(existing?.load?.toPlainString()
            ?: (target.load as? DraftSetLoad.Verified)?.value?.toPlainString().orEmpty())
    }
    var reps by rememberSaveable(target.setKey){
        mutableStateOf(existing?.reps?.takeIf{it>0}?.toString().orEmpty())
    }
    var effort by rememberSaveable(target.setKey){
        mutableStateOf(existing?.actualRir?.toString().orEmpty())
    }
    val actual=performed(target,weight,reps,effort)
    val failedActual=performed(target,weight,reps,effort,failed=true)
    val verifiedLoad=target.load as? DraftSetLoad.Verified
    val working=target.type==DraftSetType.WORKING
    PursuitCard(highlighted=!set.isResolved && !finished) {
        Text(target.exerciseId,style=MaterialTheme.typography.headlineSmall)
        Text("${if(working)"Working" else "Warmup"} set ${target.ordinal+1} · "+
            "${target.role.name.lowercase()}",
            style=MaterialTheme.typography.labelLarge)
        Text("Target: ${loadLabel(target.load)} · "+
            "${target.reps.minimum}–${target.reps.maximum} reps"+
            (target.targetRir?.let{" · ${it} RIR target"}?:""),
            style=MaterialTheme.typography.bodyMedium)
        if(set.isResolved) {
            Text("Saved: ${set.outcome?.name?.replace('_',' ')?.lowercase()}"+
                (existing?.let{" · ${it.reps} reps"+
                    (it.load?.let{amount->" at ${amount.stripTrailingZeros().toPlainString()}"}?:"")+
                    (it.actualRir?.let{rir->" · ${rir} actual RIR"}?: " · actual RIR unknown")
                } ?: ""),
                color=MaterialTheme.colorScheme.primary,
                style=MaterialTheme.typography.bodyMedium)
        }
        if(finished) return@PursuitCard
        if(verifiedLoad!=null) {
            OutlinedTextField(value=weight,onValueChange={weight=it},
                label={Text("Actual load · ${verifiedLoad.unit.name} · ${verifiedLoad.basis.name.lowercase().replace('_',' ')}")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                singleLine=true,modifier=Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),
            modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(reps,{reps=it},
                label={Text("Actual reps")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                singleLine=true,modifier=Modifier.weight(1f))
            OutlinedTextField(effort,{effort=it},
                label={Text("Actual RIR (optional)")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                singleLine=true,modifier=Modifier.weight(1f))
        }
        PursuitPrimaryAction(
            if(set.isResolved)"Save corrected set" else "Complete set",
            enabled=!busy && actual!=null
        ) {onRecord(KotlinJournalSetOutcome.COMPLETED,actual)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onSkip,enabled=!busy){Text("Skip")}
            TextButton(onClick={failedActual?.let(onFail)},
                enabled=!busy && failedActual!=null){Text("Failed attempt")}
        }
    }
}
