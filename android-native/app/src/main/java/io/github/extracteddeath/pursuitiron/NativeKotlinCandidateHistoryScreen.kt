package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Display real corrected native facts. No JS history, estimated PRs or invented dates. */
@Composable
internal fun NativeKotlinCandidateHistoryScreen(
    host:NativeKotlinCandidateHost,
    onOpenProgram:()->Unit={},
    onOpenCoach:(String?)->Unit={},
    onOpenProgress:()->Unit={}
) {
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var result by remember{mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)}
    var showSeparateHistoryConsent by remember{mutableStateOf(false)}
    var understandsSeparateHistory by remember{mutableStateOf(false)}
    var enrollmentMessage by remember{mutableStateOf<String?>(null)}
    var historyExerciseId by rememberSaveable{mutableStateOf<String?>(null)}
    fun reload() {
        if(busy)return
        busy=true;result=null
        scope.launch {
            result=withContext(Dispatchers.IO) {
                runCatching{host.reviewVerifiedKotlinHistory()}.getOrElse{
                    NativeKotlinMixedHistoryReadResult.Rejected(NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)}
            }
            busy=false
        }
    }
    LaunchedEffect(host){reload()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("TRAINING LOG","Workout history",
                "Your completed workouts and saved set corrections.")
            OutlinedButton(onClick=::reload,enabled=!busy){Text(if(busy)"Loading…" else "Refresh history")}
            enrollmentMessage?.let { Text(it,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium) }
        }
        when(val read=result) {
            is NativeKotlinMixedHistoryReadResult.Review -> {
                item {
                    PursuitPrimaryAction("Continue to my program",enabled=!busy) {
                        onOpenProgram()
                    }
                    OutlinedButton(onClick=onOpenProgress,
                        enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                        Text("Strength standards")
                    }
                    if(read.timeline.completions.isNotEmpty())
                        OutlinedButton(
                            onClick={onOpenCoach(null)},enabled=!busy,
                            modifier=Modifier.fillMaxWidth()) {
                            Text("Review next workout with Coach")
                        }
                }
                if(read.timeline.completions.isEmpty())item {Text("Your first finished workout will appear here.")}
                items(read.timeline.completions.asReversed(),key={it.manifest.workoutId}) {entry->
                    var expanded by rememberSaveable(entry.manifest.workoutId){mutableStateOf(false)}
                    PursuitCard {
                        Text("Session ${entry.manifest.completionOrder}",style=MaterialTheme.typography.titleMedium)
                        Text(if(entry is NativeKotlinMixedHistoryCompletion.Custom)"Custom program" else "Generated program",
                            style=MaterialTheme.typography.bodySmall)
                        // These are effective, correction-aware facts from the
                        // verified owner history—not a cached PR or estimate.
                        val performance=when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->
                                KotlinHistoryPerformanceSummaryDomain.fromComposer(entry.evidence.sets)
                            is NativeKotlinMixedHistoryCompletion.Custom ->
                                KotlinHistoryPerformanceSummaryDomain.fromCustom(entry.evidence.visits)
                        }
                        Text("${performance.completedWorkingSets} of ${performance.workingSets} working sets completed",
                            style=MaterialTheme.typography.titleSmall)
                        if(performance.skippedWorkingSets>0 || performance.failedWorkingSets>0)
                            Text("${performance.skippedWorkingSets} skipped · ${performance.failedWorkingSets} failed attempts",
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                        if(performance.completedWorkingSets>0) {
                            Text("Reps: ${performance.repsWithinRange} within target · ${performance.repsBelowMinimum} below · ${performance.repsAboveMaximum} above",
                                style=MaterialTheme.typography.bodySmall)
                            Text("Actual RIR recorded: ${performance.actualRirReportedWorkingSets} of ${performance.completedWorkingSets} completed working sets",
                                color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                        }
                        if(performance.followUpVisits>0)
                            Text("Amplifier follow-ups: ${performance.completedFollowUpVisits} of ${performance.followUpVisits} completed (not counted as main sets)",
                                style=MaterialTheme.typography.bodySmall)
                        // Generated exercise selection is only a navigation
                        // hint. Coach still intersects with the CURRENT
                        // approved program; custom training stays in its
                        // separately consented cycle review workflow.
                        // Full exercise ledger is separate from Coach.
                        // Any completed custom or generated movement can open
                        // complete owner-verified, correction-aware history.
                        val allMovements=when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->
                                entry.evidence.sets.map{it.exerciseId}
                            is NativeKotlinMixedHistoryCompletion.Custom ->
                                entry.evidence.visits.map{it.exerciseId}
                        }.distinct()
                        if(allMovements.isNotEmpty()) {
                            PursuitSectionHeading("Exercise history",
                                "Open all logged visits without leaving History.")
                            Row(Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                allMovements.forEach { exercise ->
                                    OutlinedButton(enabled=!busy,
                                        onClick={historyExerciseId=exercise}) {
                                        Text(kotlinExerciseLabel(exercise))
                                    }
                                }
                            }
                        }
                        if(entry is NativeKotlinMixedHistoryCompletion.Composer) {
                            val movements=entry.evidence.exerciseReviews
                                .filter{it.workingSets.isNotEmpty()}
                                .map{it.exerciseId}.distinct()
                            if(movements.isNotEmpty()) {
                                Text("Review a completed movement",
                                    style=MaterialTheme.typography.labelLarge)
                                Row(Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    movements.forEach{exercise->
                                        OutlinedButton(
                                            enabled=!busy,
                                            onClick={onOpenCoach(exercise)}) {
                                            Text(exercise.replace('_',' ').replace('-',' '))
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide sets" else "View targets and actuals")}
                        if(expanded)when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->entry.evidence.sets.forEach {set->
                                NativeKotlinSavedComposerHistorySetRow(
                                    host,entry,set,onChanged={reload()})
                            }
                            is NativeKotlinMixedHistoryCompletion.Custom ->entry.evidence.visits.forEach {set->
                                NativeKotlinSavedCustomHistoryVisitRow(
                                    host,entry,set,onChanged={reload()})
                            }
                        }
                    }
                }
            }
            is NativeKotlinMixedHistoryReadResult.Rejected ->item {
                Text(when(read.reason) {
                    NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED ->"Confirm your saved athlete setup to open history."
                    NativeKotlinMixedHistoryRefusal.NOT_ENROLLED ->
                        "This Kotlin athlete has no enrolled native workout history. Existing web training history is NOT migrated here."
                    NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION ->
                        "Finish recovery is pending. Return to Train and recover the saved workout, then refresh."
                    else ->"Saved history could not be verified. Your saved workouts have been kept."
                },color=MaterialTheme.colorScheme.error)
                if(read.reason==NativeKotlinMixedHistoryRefusal.NOT_ENROLLED) {
                    PursuitCard {
                        Text("Start an independent Kotlin history",
                            style=MaterialTheme.typography.titleMedium)
                        Text("For a new, separately owned Kotlin profile only. This creates an empty local training history without importing or changing your JavaScript/web workouts. Prior experience and fatigue history will NOT be known to the Kotlin coach. If you need your old history included, do not start separately: wait for verified migration.",
                            style=MaterialTheme.typography.bodyMedium)
                        Button(
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth(),
                            onClick={
                                understandsSeparateHistory=false
                                showSeparateHistoryConsent=true
                            }) {Text("Review separate-history choice")}
                    }
                }
            }
            null ->if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        }
    }
    historyExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={historyExerciseId=null})
    }
    if(showSeparateHistoryConsent)AlertDialog(
        onDismissRequest={
            if(!busy)showSeparateHistoryConsent=false
        },
        title={Text("Start a separate Kotlin training history?")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("This is NOT a migration. It does not bring over old web or JavaScript workouts, personal records, fatigue, or progression history. It does not erase them either. The coach will start with only future Kotlin logs.")
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked=understandsSeparateHistory,
                        onCheckedChange={understandsSeparateHistory=it},
                        enabled=!busy)
                    Text("I want a new independent history, not an import.")
                }
            }
        },
        confirmButton={
            Button(
                enabled=understandsSeparateHistory&&!busy,
                onClick={
                    showSeparateHistoryConsent=false
                    busy=true
                    enrollmentMessage=null
                    scope.launch {
                        val enrollment=withContext(Dispatchers.IO) {
                            runCatching {
                                host.enrollSeparateKotlinHistory(true)
                            }.getOrNull()
                        }
                        busy=false
                        when(enrollment) {
                            is NativeHistoryEnrollmentResult.Enrolled -> {
                                enrollmentMessage=
                                    "Separate native history verified. Web app workouts remain untouched and are not included."
                                reload()
                            }
                            is NativeHistoryEnrollmentResult.Rejected ->
                                enrollmentMessage="Separate history was not created: ${enrollment.reason.name.replace('_',' ').lowercase()}. Existing records were kept."
                            null ->enrollmentMessage=
                                "Could not verify a new native history. No enrollment was claimed."
                        }
                    }
                }) {Text("Start separate history")}
        },
        dismissButton={
            TextButton(
                enabled=!busy,
                onClick={showSeparateHistoryConsent=false}
            ) {Text("Keep existing history unchanged")}
        }
    )
}

private fun kotlinHistoryLoadLabel(load:DraftSetLoad):String=when(load) {
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()}"
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
}

@Composable
private fun KotlinHistoryActualRow(
    exercise:String,label:String,issuedLoad:DraftSetLoad,issuedReps:RepBand,
    issuedRir:Int?,outcome:KotlinJournalSetOutcome,actual:KotlinPerformedSet?
) {
    Text("${kotlinExerciseLabel(exercise)} · $label",
        style=MaterialTheme.typography.titleSmall)
    val goalReps=if(issuedReps.minimum==issuedReps.maximum)
        "${issuedReps.minimum} reps" else "${issuedReps.minimum}–${issuedReps.maximum} reps"
    Text("Target: ${kotlinHistoryLoadLabel(issuedLoad)} × $goalReps · "+
        (issuedRir?.let{"$it RIR target"} ?: "No RIR target"),
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    val performed=actual?.let{
        // Local snapshot is required: Kotlin cannot smart-cast a public
        // property exposed from the independent engine module.
        val observedLoad=it.load
        val weight=if(observedLoad==null) "Bodyweight" else
            "${observedLoad.stripTrailingZeros().toPlainString()} ${it.unit?.name?.lowercase() ?: "unit unreported"}"
        "$weight × ${it.reps} reps · "+
            (it.actualRir?.let{rir->"$rir actual RIR"} ?: "Actual RIR not logged")
    } ?: "No performed values available"
    val values=when(outcome) {
        KotlinJournalSetOutcome.SKIPPED -> "Skipped"
        KotlinJournalSetOutcome.FAILED_ATTEMPT -> "Failed attempt · $performed"
        KotlinJournalSetOutcome.COMPLETED -> "Actual: $performed"
    }
    Text(values,style=MaterialTheme.typography.bodyMedium)
}

/**
 * Share parsing between generated and custom correction forms. Unknown actual
 * RIR is never filled from the goal, and skipped sets never acquire fake reps.
 * The native reducers remain the final authority for valid load/outcome pairs.
 */
private fun kotlinHistoryCorrectionActual(
    outcome:KotlinJournalSetOutcome,
    enteredLoad:String,enteredReps:String,enteredRir:String,
    unit:LoadUnit?
):KotlinPerformedSet? {
    if(outcome==KotlinJournalSetOutcome.SKIPPED)return null
    val load=if(enteredLoad.isBlank())null else
        runCatching{java.math.BigDecimal(enteredLoad.trim())}.getOrNull()
    val reps=enteredReps.toIntOrNull() ?:return null
    val rir=if(enteredRir.isBlank())null else enteredRir.toIntOrNull()
    if((enteredLoad.isNotBlank() && load==null) ||
        (enteredRir.isNotBlank() && rir==null) ||
        (load!=null && unit==null))return null
    return KotlinPerformedSet(load,if(load==null)null else unit,reps,rir)
}

/** Generated/composer history uses the same committed native history
 * transaction as Finish. Neither this UI nor a direct correction-file append
 * may change already-issued targets or publish uncommitted observations. */
@Composable
private fun NativeKotlinSavedComposerHistorySetRow(
    host:NativeKotlinCandidateHost,
    entry:NativeKotlinMixedHistoryCompletion.Composer,
    fact:KotlinHistoricalSetFact,
    onChanged:()->Unit
) {
    val label=fact.type.name.lowercase().replaceFirstChar{it.uppercase()}+
        " ${fact.originalOrdinal+1}"
    KotlinHistoryActualRow(fact.exerciseId,label,
        fact.issuedLoad,fact.issuedReps,fact.issuedRir,
        fact.outcome,fact.actual)
    var editing by remember(entry.manifest.workoutId,fact.setKey,
        fact.effectiveEventId){mutableStateOf(false)}
    var submitting by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var outcome by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.outcome)
    }
    var load by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.load?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var reps by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.reps?.toString().orEmpty())
    }
    var rir by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.actualRir?.toString().orEmpty())
    }
    val scope=rememberCoroutineScope()
    TextButton(onClick={editing=!editing;error=null},enabled=!submitting){
        Text(if(editing)"Cancel correction" else "Correct saved set")
    }
    if(editing) {
        Text("The prescribed goal stays unchanged. Only the logged result will be corrected.",
            style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KotlinJournalSetOutcome.entries.forEach {choice->
                FilterChip(selected=outcome==choice,enabled=!submitting,
                    onClick={outcome=choice},
                    label={Text(choice.name.replace('_',' ').lowercase()
                        .replaceFirstChar{it.uppercase()})})
            }
        }
        if(outcome!=KotlinJournalSetOutcome.SKIPPED) {
            OutlinedTextField(value=load,onValueChange={load=it},
                label={Text("Actual weight (blank for bodyweight)")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=reps,onValueChange={reps=it},
                label={Text("Actual reps")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=rir,onValueChange={rir=it},
                label={Text("Actual RIR (optional)")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
        }
        Button(enabled=!submitting,onClick={
            val performed=kotlinHistoryCorrectionActual(
                outcome,load,reps,rir,
                fact.actual?.unit ?: (fact.issuedLoad as? DraftSetLoad.Verified)?.unit)
            val selectedOutcome=outcome
            if(selectedOutcome!=KotlinJournalSetOutcome.SKIPPED &&
                performed==null) {
                error="Enter a valid weight, rep count and optional actual RIR."
            } else {
                submitting=true;error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO){
                        runCatching{
                            // Re-read the full owner-fenced corrected ledger
                            // before sending a CAS event. A stale edit is
                            // rejected rather than overwriting later work.
                            val verified=host.reviewVerifiedKotlinHistory()
                                as? NativeKotlinMixedHistoryReadResult.Review
                            val current=verified?.timeline?.completions
                                ?.filterIsInstance<NativeKotlinMixedHistoryCompletion.Composer>()
                                ?.singleOrNull{
                                    it.manifest.workoutId==entry.manifest.workoutId
                                }
                            val currentSet=current?.evidence?.sets?.singleOrNull{
                                it.setKey==fact.setKey
                            }
                            if(currentSet==null ||
                                currentSet.effectiveEventId!=fact.effectiveEventId ||
                                currentSet.issuedLoad!=fact.issuedLoad ||
                                currentSet.issuedReps!=fact.issuedReps ||
                                currentSet.issuedRir!=fact.issuedRir ||
                                current?.manifest?.savedCorrectionRevision!=
                                    entry.manifest.savedCorrectionRevision) null
                            else host.correctSavedKotlinWorkout(
                                entry.manifest.workoutId,
                                KotlinSavedSetCorrection(
                                    "composer-correction-"+java.util.UUID.randomUUID()
                                        .toString().replace("-",""),
                                    entry.manifest.savedCorrectionRevision,
                                    fact.setKey,fact.effectiveEventId,
                                    selectedOutcome,performed))
                        }.getOrNull()
                    }
                    submitting=false
                    if(result is NativeSavedCorrectionFileResult.Durable){
                        editing=false;error=null;onChanged()
                    } else error="Correction was not saved. The history or prior set may have changed. Refresh and retry."
                }
            }
        },modifier=Modifier.fillMaxWidth()){
            Text(if(submitting)"Saving actual correction…" else "Save corrected result")
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall)}
    }
}

/**
 * Post-Finish corrections never mutate the issued prescription or set
 * journal. They produce a separate owner-fenced correction transaction and
 * cause the whole verified history screen to refresh after commitment.
 */
@Composable
private fun NativeKotlinSavedCustomHistoryVisitRow(
    host:NativeKotlinCandidateHost,
    entry:NativeKotlinMixedHistoryCompletion.Custom,
    fact:CustomHistoricalVisitFact,
    onChanged:()->Unit
) {
    KotlinHistoryActualRow(fact.exerciseId,
        if(fact.part==CustomWorkoutVisitPart.FOLLOW_UP)"Amplifier follow-up" else
            fact.setType.name.lowercase().replaceFirstChar{it.uppercase()},
        fact.prescribedLoad,fact.prescribedReps,fact.prescribedRir,
        fact.outcome,fact.actual)
    var editing by remember(entry.manifest.workoutId,fact.setKey,
        fact.part,fact.effectiveEventId) {mutableStateOf(false)}
    var submitting by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var outcome by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.outcome)
    }
    var load by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.load?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var reps by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.reps?.toString().orEmpty())
    }
    var rir by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.actualRir?.toString().orEmpty())
    }
    val scope=rememberCoroutineScope()
    TextButton(onClick={editing=!editing;error=null},enabled=!submitting) {
        Text(if(editing)"Cancel correction" else "Correct saved set")
    }
    if(editing) {
        Text("Original prescribed targets remain unchanged. Only your recorded result will be corrected.",
            style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KotlinJournalSetOutcome.entries.forEach {choice->
                FilterChip(selected=outcome==choice,
                    enabled=!submitting,
                    onClick={outcome=choice},
                    label={Text(choice.name.replace('_',' ').lowercase()
                        .replaceFirstChar{it.uppercase()})})
            }
        }
        if(outcome!=KotlinJournalSetOutcome.SKIPPED) {
            OutlinedTextField(value=load,onValueChange={load=it},
                label={Text("Actual weight (blank for bodyweight)")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=reps,onValueChange={reps=it},
                label={Text("Actual reps")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=rir,onValueChange={rir=it},
                label={Text("Actual RIR (optional)")},
                singleLine=true,modifier=Modifier.fillMaxWidth())
        }
        Button(enabled=!submitting,onClick={
            val actual=kotlinHistoryCorrectionActual(
                outcome,load,reps,rir,
                fact.actual?.unit ?: (fact.prescribedLoad as? DraftSetLoad.Verified)?.unit)
            val selectedOutcome=outcome
            if(selectedOutcome!=KotlinJournalSetOutcome.SKIPPED &&
                actual==null) {
                error="Enter a valid actual load, rep count and optional RIR."
            } else {
                submitting=true;error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO) {
                        runCatching {
                        val verified=host.reviewVerifiedKotlinHistory()
                            as? NativeKotlinMixedHistoryReadResult.Review
                        val current=verified?.timeline?.completions
                            ?.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                            ?.singleOrNull{it.manifest.workoutId==entry.manifest.workoutId}
                        val currentFact=current?.evidence?.visits?.singleOrNull {
                            it.setKey==fact.setKey && it.part==fact.part
                        }
                        val correction=host.readFinishedCustomCorrections(
                            entry.manifest.workoutId)
                            as? NativeCustomSavedCorrectionResult.Opened
                        if(currentFact==null || correction==null ||
                            currentFact.effectiveEventId!=fact.effectiveEventId ||
                            current?.manifest?.savedCorrectionRevision!=
                                correction.state.correctionRevision)
                            null
                        else host.correctFinishedCustomWorkout(
                            entry.manifest.workoutId,
                            CustomSavedVisitCorrection(
                                "custom-correction-"+java.util.UUID.randomUUID()
                                    .toString().replace("-",""),
                                correction.state.correctionRevision,
                                CustomWorkoutVisitKey(fact.setKey,fact.part),
                                fact.effectiveEventId,selectedOutcome,actual))
                        }.getOrNull()
                    }
                    submitting=false
                    if(result is NativeCustomCorrectionTransactionResult.Committed) {
                        editing=false;error=null;onChanged()
                    } else {
                        error="Correction was not saved. History or the prior set changed. Refresh the verified history and retry."
                    }
                }
            }
        },modifier=Modifier.fillMaxWidth()) {
            Text(if(submitting)"Saving actual correction…" else "Save corrected result")
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall) }
    }
}
