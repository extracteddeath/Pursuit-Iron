package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

private sealed interface NativeKotlinRecapScreenState {
    data object Loading:NativeKotlinRecapScreenState
    data object Unavailable:NativeKotlinRecapScreenState
    data class Ready(
        val summary:NativeKotlinWorkoutRecap,
        val completion:NativeKotlinMixedHistoryCompletion,
        val historyRevision:Long
    ):NativeKotlinRecapScreenState
}

/**
 * One compact, source-backed recap after a VERIFIED generated/custom Finish.
 * This never reads provisional journal state or saves, modifies, or
 * regenerates a workout. History corrections can be reloaded explicitly.
 */
@Composable
internal fun NativeKotlinVerifiedFinishRecap(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    isCustom:Boolean
) {
    var refresh by remember(host,workoutId,isCustom){mutableIntStateOf(0)}
    var state by remember(host,workoutId,isCustom) {
        mutableStateOf<NativeKotlinRecapScreenState>(
            NativeKotlinRecapScreenState.Loading)
    }
    val zone=remember{ZoneId.systemDefault()}
    val locale=remember{Locale.getDefault()}
    LaunchedEffect(host,workoutId,isCustom,refresh) {
        state=NativeKotlinRecapScreenState.Loading
        val next=withContext(Dispatchers.IO) {
            try {
                val ownerBefore=host.currentHistoryExportOwner()
                val active=(host.reviewActiveAthleteSetup() as?
                    NativeSavedSetupResume.Ready)?.setup?.athleteProfileId
                val timeline=(host.reviewVerifiedKotlinHistory() as?
                    NativeKotlinMixedHistoryReadResult.Review)?.timeline
                val ownerAfter=host.currentHistoryExportOwner()
                val selected=timeline?.completions?.singleOrNull {
                    it.manifest.workoutId==workoutId
                }
                val matchingKind=when(selected) {
                    is NativeKotlinMixedHistoryCompletion.Composer -> !isCustom
                    is NativeKotlinMixedHistoryCompletion.Custom -> isCustom
                    // A generated/custom set recap is not a timed or
                    // distance-measured workout recap.
                    is NativeKotlinMixedHistoryCompletion.Measured,
                    is NativeKotlinMixedHistoryCompletion.Freestyle -> false
                    null -> false
                }
                if(ownerBefore==null || ownerBefore!=ownerAfter ||
                    active==null || ownerBefore.athleteId!=active ||
                    timeline?.athleteId!=active || !matchingKind ||
                    selected==null)NativeKotlinRecapScreenState.Unavailable
                else NativeKotlinWorkoutRecapDomain
                    .fromVerifiedCompletion(selected)?.let {
                        NativeKotlinRecapScreenState.Ready(
                            it,selected,timeline.manifestRevision)
                    } ?:NativeKotlinRecapScreenState.Unavailable
            } catch(cancel:CancellationException) {
                throw cancel
            } catch(_:Exception) {
                NativeKotlinRecapScreenState.Unavailable
            }
        }
        state=next
    }
    PursuitCard {
        PursuitSectionHeading(
            "Your workout recap",
            "Only saved, verified set outcomes. No estimated PRs or load changes.")
        when(val current=state) {
            NativeKotlinRecapScreenState.Loading ->
                LinearProgressIndicator(Modifier.fillMaxWidth())
            NativeKotlinRecapScreenState.Unavailable -> {
                Text("Workout history could not be verified. The saved Finish is unchanged. Recheck your history to view this recap.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
                OutlinedButton(onClick={refresh++},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                    Text("Retry verified recap")
                }
            }
            is NativeKotlinRecapScreenState.Ready -> {
                val summary=current.summary
                Text(
                    "Session ${current.completion.manifest.completionOrder} · "+
                        (nativeHistoryDateLabel(current.completion.manifest,
                            zone,locale) ?:"Date not recorded"),
                    style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                PursuitMetricStrip(listOf(
                    "Working sets" to summary.completedMain.toString(),
                    "Skipped" to summary.skippedMain.toString(),
                    "Failed" to summary.failedMain.toString()))
                summary.performance?.let { verified ->
                    NativeKotlinPerformanceEvidenceLines(verified)
                }
                Text(
                    "${summary.completedWarmups} completed warm-ups · "+
                        "${summary.completedAmplifiers} completed amplifier follow-ups",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(summary.skippedOther+summary.failedOther>0)
                    Text(
                        "${summary.skippedOther} skipped and "+
                            "${summary.failedOther} failed warm-up/amplifier entries",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                var showAll by rememberSaveable(workoutId,historyVersionKey(current)) {
                    mutableStateOf(false)
                }
                val visible=if(showAll)summary.exercises else
                    summary.exercises.take(5)
                Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                    visible.forEach { row ->
                        Text(
                            kotlinExerciseLabel(row.exerciseId)+
                                " · ${row.completedMain} complete, "+
                                "${row.skippedMain} skipped, ${row.failedMain} failed",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
                if(summary.exercises.size>5)
                    TextButton(onClick={showAll=!showAll},
                        modifier=Modifier.heightIn(min=48.dp)) {
                        Text(if(showAll)"Show fewer exercises"
                            else "Show all ${summary.exercises.size} exercises")
                    }
                Text(
                    "Counts reflect corrected, saved workout history. Review or "+
                        "correct an individual set in History. Refresh after "+
                        "making a correction to update this recap.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={refresh++},
                    modifier=Modifier.heightIn(min=48.dp)) {
                    Text("Refresh saved recap")
                }
            }
        }
    }
}

/** Recomposition after the same workout's correction resets expanded UI. */
private fun historyVersionKey(state:NativeKotlinRecapScreenState.Ready):String =
    "${state.historyRevision}:${state.completion.manifest.savedCorrectionRevision}"
