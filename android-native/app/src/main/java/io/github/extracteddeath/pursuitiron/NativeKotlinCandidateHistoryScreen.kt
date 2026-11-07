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
                                KotlinHistoryActualRow(set.exerciseId,
                                    if(set.part==CustomWorkoutVisitPart.FOLLOW_UP)"Follow-up" else
                                        set.setType.name.lowercase().replaceFirstChar{it.uppercase()},
                                    set.outcome,set.actual)
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
