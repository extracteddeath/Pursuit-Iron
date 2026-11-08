package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exercise history uses ONLY the complete correction-aware, owner-verified
 * mixed Kotlin timeline. Never fall back to a single successful set from an
 * older workout if the latest session was skipped or unsuccessful.
 */
private data class ExerciseVisitDisplay(
    val key:String,
    val sessionOrder:Long,
    val entryNumber:Int,
    val outcome:KotlinJournalSetOutcome,
    val issuedLoad:DraftSetLoad,
    val issuedReps:RepBand,
    val issuedRir:Int?,
    val actual:KotlinPerformedSet?
)

private fun exerciseHistoryRows(
    timeline:NativeKotlinMixedHistoryTimeline,exerciseId:String
):List<ExerciseVisitDisplay> = timeline.completions.asReversed().flatMap { completed ->
    val order=completed.manifest.completionOrder
    when(completed) {
        is NativeKotlinMixedHistoryCompletion.Composer ->
            completed.evidence.sets.filter {
                it.exerciseId==exerciseId && it.type==DraftSetType.WORKING
            }.mapIndexed { index,set ->
                ExerciseVisitDisplay("${completed.manifest.workoutId}:${set.setKey}",
                    order,index+1,set.outcome,set.issuedLoad,
                    set.issuedReps,set.issuedRir,set.actual)
            }
        is NativeKotlinMixedHistoryCompletion.Custom ->
            completed.evidence.visits.filter {
                it.exerciseId==exerciseId &&
                    it.part==CustomWorkoutVisitPart.MAIN &&
                    it.setType==DraftSetType.WORKING
            }.mapIndexed { index,set ->
                ExerciseVisitDisplay("${completed.manifest.workoutId}:${set.setKey}",
                    order,index+1,set.outcome,set.prescribedLoad,
                    set.prescribedReps,set.prescribedRir,set.actual)
            }
    }
}

private fun historyLoadLabel(load:DraftSetLoad):String=when(load) {
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()}"
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
}

private fun actualLabel(actual:KotlinPerformedSet?):String {
    if(actual==null)return "No performed value"
    val weight=actual.load?.let {
        "${it.stripTrailingZeros().toPlainString()} ${actual.unit?.name?.lowercase().orEmpty()}"
    } ?: "Bodyweight"
    return "$weight × ${actual.reps} reps · "+
        (actual.actualRir?.let{"$it RIR"} ?: "RIR not reported")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeKotlinExerciseHistorySheet(
    host:NativeKotlinCandidateHost,
    exerciseId:String,
    onDismiss:()->Unit
) {
    var history by remember(host,exerciseId) {
        mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)
    }
    LaunchedEffect(host,exerciseId) {
        history=withContext(Dispatchers.IO) {
            runCatching {host.reviewVerifiedKotlinHistory()}.getOrElse {
                NativeKotlinMixedHistoryReadResult.Rejected(
                    NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
            }
        }
    }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth()
            .padding(horizontal=PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.md)) {
            PageHeading("EXERCISE HISTORY",kotlinExerciseLabel(exerciseId),
                "All saved working sets · corrections included")
            when(val result=history) {
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is NativeKotlinMixedHistoryReadResult.Rejected ->
                    PursuitEmptyState("History unavailable",
                        "Verified saved history could not be opened. Your workout entries are unchanged.")
                is NativeKotlinMixedHistoryReadResult.Review -> {
                    val rows=remember(result.timeline.manifestRevision,exerciseId) {
                        exerciseHistoryRows(result.timeline,exerciseId)
                    }
                    if(rows.isEmpty())
                        PursuitEmptyState("No recorded working sets yet",
                            "After this exercise has a finished native workout, its actual sets will appear here.")
                    else {
                        PursuitHistoryStatus(
                            "${rows.map{it.sessionOrder}.distinct().size} training sessions · ${rows.size} working sets",
                            "Newest session first. Skips and failed attempts remain visible; missing RIR is never estimated.")
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),
                            contentPadding=PaddingValues(bottom=PursuitSpace.xl),
                            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            items(rows,key={it.key}) { row ->
                                PursuitCard {
                                    Text("Session ${row.sessionOrder} · Set ${row.entryNumber}",
                                        style=MaterialTheme.typography.titleSmall)
                                    val targetReps=if(row.issuedReps.minimum==row.issuedReps.maximum)
                                        "${row.issuedReps.minimum}"
                                    else "${row.issuedReps.minimum}–${row.issuedReps.maximum}"
                                    Text("Target · ${historyLoadLabel(row.issuedLoad)} × $targetReps"+
                                        (row.issuedRir?.let{" · $it RIR"} ?: ""),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(when(row.outcome) {
                                        KotlinJournalSetOutcome.SKIPPED -> "Skipped · no performed set"
                                        KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                                            "Failed attempt · ${actualLabel(row.actual)}"
                                        KotlinJournalSetOutcome.COMPLETED ->
                                            "Actual · ${actualLabel(row.actual)}"
                                    },style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
