package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
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
internal fun NativeKotlinCandidateHistoryScreen(host:NativeKotlinCandidateHost) {
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var result by remember{mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)}
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
            PageHeading("PURSUIT IRON · KOTLIN LAB","Workout history",
                "Your completed workouts and saved set corrections.")
            OutlinedButton(onClick=::reload,enabled=!busy){Text(if(busy)"Loading…" else "Refresh history")}
        }
        when(val read=result) {
            is NativeKotlinMixedHistoryReadResult.Review -> {
                if(read.timeline.completions.isEmpty())item {Text("Your first finished workout will appear here.")}
                items(read.timeline.completions.asReversed(),key={it.manifest.workoutId}) {entry->
                    var expanded by rememberSaveable(entry.manifest.workoutId){mutableStateOf(false)}
                    PursuitCard {
                        Text("Session ${entry.manifest.completionOrder}",style=MaterialTheme.typography.titleMedium)
                        Text(if(entry is NativeKotlinMixedHistoryCompletion.Custom)"Custom program" else "Generated program",
                            style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide sets" else "View performed sets")}
                        if(expanded)when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->entry.evidence.sets.forEachIndexed {index,set->
                                KotlinHistoryActualRow(set.exerciseId,"Set ${index+1}",set.outcome,set.actual)
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
                    NativeKotlinMixedHistoryRefusal.NOT_ENROLLED ->"Complete native setup before opening history."
                    NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION ->"Finish recovery is pending. Return to Train and recover the saved workout, then refresh."
                    else ->"Saved history could not be verified. Your saved workouts have been kept."
                },color=MaterialTheme.colorScheme.error)
            }
            null ->if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        }
    }
}

@Composable
private fun KotlinHistoryActualRow(exercise:String,label:String,outcome:KotlinJournalSetOutcome,actual:KotlinPerformedSet?) {
    Text(exercise.replace('_',' ').replace('-',' '),style=MaterialTheme.typography.titleSmall)
    val performed=actual?.let{
        "${it.load?.toPlainString() ?: "Bodyweight"} ${it.unit?.name?.lowercase().orEmpty()} × ${it.reps} · "+
            (it.actualRir?.let{rir->"$rir RIR"} ?:"RIR not logged")
    } ?:"No performed values available"
    val values=when(outcome) {
        KotlinJournalSetOutcome.SKIPPED ->"Skipped"
        KotlinJournalSetOutcome.FAILED_ATTEMPT ->"Failed attempt · $performed"
        KotlinJournalSetOutcome.COMPLETED ->performed
    }
    Text("$label · $values",style=MaterialTheme.typography.bodyMedium)
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
        if(fact.part==CustomWorkoutVisitPart.FOLLOW_UP)"Follow-up" else
            fact.setType.name.lowercase().replaceFirstChar{it.uppercase()},
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
            val actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)
                null
            else {
                val parsedLoad=if(load.isBlank())null else
                    runCatching{java.math.BigDecimal(load.trim())}.getOrNull()
                val parsedReps=reps.toIntOrNull()
                val parsedRir=if(rir.isBlank())null else rir.toIntOrNull()
                val unit=fact.actual?.unit ?:
                    (fact.prescribedLoad as? DraftSetLoad.Verified)?.unit
                if(parsedReps==null ||
                    (!rir.isBlank() && parsedRir==null) ||
                    (!load.isBlank() && parsedLoad==null) ||
                    (parsedLoad!=null && unit==null))null
                else KotlinPerformedSet(parsedLoad,
                    if(parsedLoad==null)null else unit,
                    parsedReps,parsedRir)
            }
            if(outcome!=KotlinJournalSetOutcome.SKIPPED &&
                actual==null) {
                error="Enter a valid actual load, rep count and optional RIR."
            } else {
                submitting=true;error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO) {
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
                                fact.effectiveEventId,outcome,actual))
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
