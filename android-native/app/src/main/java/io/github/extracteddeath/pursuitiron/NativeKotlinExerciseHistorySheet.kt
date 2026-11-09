package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Exercise history uses ONLY the complete correction-aware, owner-verified
 * mixed Kotlin timeline. Never fall back to a single successful set from an
 * older workout if the latest session was skipped or unsuccessful.
 */
private enum class ExerciseHistoryView { WORKING, ALL, SETBACKS }

private data class ExerciseVisitDisplay(
    val key:String,
    val sessionOrder:Long,
    val entryNumber:Int,
    val type:DraftSetType,
    val followUp:Boolean,
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
                it.exerciseId==exerciseId
            }.mapIndexed { index,set ->
                ExerciseVisitDisplay("${completed.manifest.workoutId}:${set.setKey}",
                    order,index+1,set.type,false,set.outcome,set.issuedLoad,
                    set.issuedReps,set.issuedRir,set.actual)
            }
        is NativeKotlinMixedHistoryCompletion.Custom ->
            completed.evidence.visits.filter {
                it.exerciseId==exerciseId
            }.mapIndexed { index,set ->
                ExerciseVisitDisplay("${completed.manifest.workoutId}:${set.setKey}:${set.part.name}",
                    order,index+1,set.setType,
                    set.part==CustomWorkoutVisitPart.FOLLOW_UP,
                    set.outcome,set.prescribedLoad,
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
    onDismiss:()->Unit,
    onFavoritesChanged:(List<String>)->Unit={}
) {
    val context=LocalContext.current.applicationContext
    val preferences=remember(context) {
        context.getSharedPreferences(NativeExerciseFavorites.PREFS_NAME,0)
    }
    val scope=rememberCoroutineScope()
    var history by remember(host,exerciseId) {
        mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)
    }
    var favoriteIds by remember(host,exerciseId) {
        mutableStateOf<List<String>>(emptyList())
    }
    var favoriteSaving by remember(host,exerciseId){mutableStateOf(false)}
    var favoriteNotice by remember(host,exerciseId){mutableStateOf<String?>(null)}
    var historyView by rememberSaveable(exerciseId){mutableStateOf(ExerciseHistoryView.WORKING)}
    LaunchedEffect(host,exerciseId) {
        val verified=withContext(Dispatchers.IO) {
            runCatching {host.reviewVerifiedKotlinHistory()}.getOrElse {
                NativeKotlinMixedHistoryReadResult.Rejected(
                    NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
            }
        }
        val loadedFavorites=if(verified is NativeKotlinMixedHistoryReadResult.Review) {
            withContext(Dispatchers.IO) {
                NativeExerciseFavorites.read(preferences,
                    verified.timeline.athleteId,
                    NativeExerciseFavorites.loggedIds(verified.timeline).toSet())
            }
        } else emptyList()
        // Publish the matching history and its current preference together;
        // an early tap must not overwrite other previously pinned movements.
        favoriteIds=loadedFavorites
        history=verified
        favoriteNotice=null
    }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth()
            .padding(horizontal=PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.md)) {
            PageHeading("EXERCISE HISTORY",kotlinExerciseLabel(exerciseId),
                "Full training history · corrections included")
            when(val result=history) {
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is NativeKotlinMixedHistoryReadResult.Rejected ->
                    PursuitEmptyState("History unavailable",
                        "Verified saved history could not be opened. Your workout entries are unchanged.")
                is NativeKotlinMixedHistoryReadResult.Review -> {
                    val verified=result.timeline
                    val allowed=remember(verified) {
                        NativeExerciseFavorites.loggedIds(verified).toSet()
                    }
                    if(exerciseId in allowed) {
                        val isPinned=exerciseId in favoriteIds
                        OutlinedButton(
                            enabled=!favoriteSaving &&
                                (isPinned || favoriteIds.size<
                                    NativeExerciseFavorites.MAX_FAVORITES),
                            onClick={
                                val candidate=if(isPinned)favoriteIds-exerciseId
                                    else favoriteIds+exerciseId
                                favoriteSaving=true
                                favoriteNotice=null
                                scope.launch {
                                    val wrote=withContext(Dispatchers.IO) {
                                        val current=runCatching {
                                            host.reviewVerifiedKotlinHistory()
                                        }.getOrNull()
                                        val live=(current as?
                                            NativeKotlinMixedHistoryReadResult.Review)?.timeline
                                        if(live==null ||
                                            live.athleteId!=verified.athleteId ||
                                            live.manifestRevision!=verified.manifestRevision)
                                            false
                                        else NativeExerciseFavorites.write(
                                            preferences,live.athleteId,candidate,
                                            NativeExerciseFavorites.loggedIds(live).toSet())
                                    }
                                    if((history as?
                                        NativeKotlinMixedHistoryReadResult.Review)
                                            ?.timeline==verified) {
                                        if(wrote) {
                                            favoriteIds=candidate
                                            onFavoritesChanged(candidate)
                                        }
                                        favoriteNotice=if(wrote)
                                            "Exercise favorites saved privately."
                                        else "History changed or could not be verified. Reopen to retry."
                                    }
                                    favoriteSaving=false
                                }
                            },
                            modifier=Modifier.heightIn(min=48.dp)
                        ) {Text(if(isPinned)"Unpin from exercise favorites"
                            else "Pin this exercise")}
                        favoriteNotice?.let {
                            Text(it,style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val rows=remember(result.timeline.athleteId,
                        result.timeline.manifestRevision,exerciseId) {
                        exerciseHistoryRows(result.timeline,exerciseId)
                    }
                    if(rows.isEmpty())
                        PursuitEmptyState("No recorded sets yet",
                            "After this exercise has a finished native workout, its actual visits will appear here.")
                    else {
                        PursuitHistoryStatus(
                            "${rows.map{it.sessionOrder}.distinct().size} training sessions · ${rows.size} recorded entries",
                            "Newest session first. Skips and failed attempts remain visible; missing RIR is never estimated.")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            FilterChip(selected=historyView==ExerciseHistoryView.WORKING,
                                onClick={historyView=ExerciseHistoryView.WORKING},
                                label={Text("Working sets")})
                            FilterChip(selected=historyView==ExerciseHistoryView.ALL,
                                onClick={historyView=ExerciseHistoryView.ALL},
                                label={Text("All entries")})
                            FilterChip(selected=historyView==ExerciseHistoryView.SETBACKS,
                                onClick={historyView=ExerciseHistoryView.SETBACKS},
                                label={Text("Skipped / failed")})
                        }
                        val visible=when(historyView) {
                            ExerciseHistoryView.WORKING -> rows.filter {
                                it.type==DraftSetType.WORKING && !it.followUp
                            }
                            ExerciseHistoryView.ALL -> rows
                            ExerciseHistoryView.SETBACKS -> rows.filter {
                                it.outcome!=KotlinJournalSetOutcome.COMPLETED
                            }
                        }
                        val sessions=visible.groupBy { it.sessionOrder }
                        val allSessions=rows.groupBy { it.sessionOrder }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),
                            contentPadding=PaddingValues(bottom=PursuitSpace.xl),
                            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            if(visible.isEmpty())item {
                                Text(if(historyView==ExerciseHistoryView.SETBACKS)
                                    "No skipped or failed entries in this verified history."
                                    else "No working sets in this history. Select All entries.",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            items(visible,key={it.key}) { row ->
                                val visits=sessions.getValue(row.sessionOrder)
                                if(visits.first().key==row.key) {
                                    val completed=visits.count {
                                        it.outcome==KotlinJournalSetOutcome.COMPLETED
                                    }
                                    val skipped=visits.count {
                                        it.outcome==KotlinJournalSetOutcome.SKIPPED
                                    }
                                    val failed=visits.count {
                                        it.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT
                                    }
                                    PursuitHistoryStatus(
                                        "Session ${row.sessionOrder} · ${visits.size} entries shown",
                                        "$completed completed · $skipped skipped · $failed failed"
                                    )
                                }
                                PursuitCard {
                                    val kind=when {
                                        row.followUp -> "Amplifier follow-up"
                                        row.type==DraftSetType.WARMUP -> "Warm-up"
                                        else -> "Working set"
                                    }
                                    // Ordinals must follow the full source visit list,
                                    // not only filtered results; a working set does
                                    // not become "set 4" because of three warmups.
                                    val ordinal=allSessions.getValue(row.sessionOrder)
                                        .takeWhile { it.key!=row.key }
                                        .count {
                                            it.type==row.type && it.followUp==row.followUp
                                        }+1
                                    Text("$kind $ordinal",
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
