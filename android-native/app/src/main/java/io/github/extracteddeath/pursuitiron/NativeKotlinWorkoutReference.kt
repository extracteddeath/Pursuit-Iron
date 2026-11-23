package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Immutable invalidation keys only. The host still cold-verifies all facts. */
internal sealed interface WorkoutLastReferenceInput {
    data class Generated(val snapshot:KotlinStagedWorkout):WorkoutLastReferenceInput
    data class Custom(val snapshot:CustomWorkoutDaySnapshot):WorkoutLastReferenceInput
}

@Composable
internal fun rememberWorkoutLastReferences(
    host:NativeKotlinCandidateHost,workoutId:String,custom:Boolean,enabled:Boolean,
    source:WorkoutLastReferenceInput?,refreshVersion:Int=0
):WorkoutLastReferenceResult? {
    // Reset synchronously when a swap/undo changes the issued snapshot or
    // an explicit retry begins. An old Ready result must not be actionable
    // for one frame while the new source effect waits to run. Ordinary set
    // and note receipts keep this immutable snapshot and need no reread of
    // the athlete's complete history.
    var result by remember(host,workoutId,custom,enabled,source,refreshVersion){
        mutableStateOf<WorkoutLastReferenceResult?>(null)
    }
    LaunchedEffect(host,workoutId,custom,enabled,source,refreshVersion) {
        result=null
        if(enabled && source!=null)result=try {
            withContext(Dispatchers.IO) {
                host.reviewWorkoutLastReferences(workoutId,custom)
            }
        } catch(cancelled:CancellationException) {
            // A disposed workout must never publish a late previous-session
            // result or suppress coroutine cancellation.
            throw cancelled
        } catch(_:Exception) {
            // Last/Leveling is optional display evidence, not a journal owner.
            // Failed storage reads cannot crash or silently edit an active set.
            WorkoutLastReferenceResult.Unavailable
        }
    }
    return if(enabled)result else null
}

/** Target/Last is display-only and never substitutes a history observation. */
@Composable
internal fun PursuitWorkoutReferencePicker(
    mode:WorkoutReferenceMode,onChange:(WorkoutReferenceMode)->Unit,
    onRetry:(()->Unit)?=null
) {
    val choices:@Composable (Modifier)->Unit={modifier->
        SingleChoiceSegmentedButtonRow(modifier=modifier) {
            WorkoutReferenceMode.entries.forEachIndexed { index,choice ->
                SegmentedButton(
                    selected=mode==choice,
                    shape=SegmentedButtonDefaults.itemShape(index=index,count=2),
                    onClick={onChange(choice)}
                ) {
                    Text(if(choice==WorkoutReferenceMode.TARGET)"Target" else "Last")
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=maxWidth<360.dp || LocalDensity.current.fontScale>=1.30f
        if(stacked) {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                Text("Reference",style=MaterialTheme.typography.labelLarge)
                choices(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                verticalAlignment=Alignment.CenterVertically) {
                Text("Reference",modifier=Modifier.weight(1f),
                    style=MaterialTheme.typography.labelLarge)
                choices(Modifier)
            }
        }
    }
    if(mode==WorkoutReferenceMode.LAST && onRetry!=null)TextButton(
        onClick=onRetry,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
    ) {Text("Retry Last")}
}

/**
 * Optional single-tap draft helper. Only verified COMPLETED same-day history
 * with the exact current load basis and set key can be applied. Never logs a
 * set, changes an issued target, infers RIR, or overwrites a corrected receipt.
 */
@Composable
internal fun PursuitUseLastCompletedSet(
    mode:WorkoutReferenceMode,
    history:WorkoutLastReferenceResult?,
    key:WorkoutReferenceKey,
    currentLoad:DraftSetLoad,
    enabled:Boolean=true,
    onApply:(KotlinPerformedSet)->Unit
) {
    if(mode!=WorkoutReferenceMode.LAST)return
    val actual=WorkoutLastDraftDomain.reviewedActual(history,key,currentLoad)
        ?:return
    val weight=kotlinActualLoadLabel(actual,currentLoad)
    OutlinedButton(
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
        enabled=enabled,
        onClick={onApply(actual)}
    ) {
        Text("Use last completed set · $weight × ${actual.reps}")
    }
    Text("Fills editable actuals only. Missing last RIR stays blank; tap Complete to save.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Fixed weight → reps → RIR order for BOTH references, with the same layout. */
@Composable
internal fun PursuitWorkoutSetReference(
    mode:WorkoutReferenceMode,
    load:DraftSetLoad,reps:RepBand,targetRir:Int?,
    history:WorkoutLastReferenceResult?,key:WorkoutReferenceKey,
    exactRepTarget: Int? = null
) {
    val last=(history as? WorkoutLastReferenceResult.Ready)?.sets?.get(key)
    val actual=last?.actual
    val target=mode==WorkoutReferenceMode.TARGET
    val verified=load as? DraftSetLoad.Verified
    val weight=if(target)verified?.let{
        "${it.value.stripTrailingZeros().toPlainString()} ${it.unit.name.lowercase()}"
    } ?:"Bodyweight"
    else kotlinLastWeightMetricLabel(actual,load)
    val repText=if(target) {
        if(exactRepTarget != null) exactRepTarget.toString()
        else if(reps.minimum==reps.maximum)reps.minimum.toString()
        else "${reps.minimum}–${reps.maximum}"
    } else actual?.reps?.toString() ?:"—"
    val rir=if(target)targetRir?.toString() ?:"—"
        else actual?.actualRir?.toString() ?:"—"
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
        PursuitMetricStrip(listOf("Weight" to weight,"Reps" to repText,"RIR" to rir))
        val caption=if(target) "Planned · ${kotlinLoadBasisLabel(verified?.basis)}" +
            (if (exactRepTarget != null && reps.minimum != reps.maximum)
                " · ${reps.minimum}–${reps.maximum} rep range" else "")
        else when {
            history==null -> "Loading verified history…"
            history is WorkoutLastReferenceResult.Unavailable -> "Last is unavailable. Your set can still be logged."
            last==null -> "No matching previous set for this program day."
            last.outcome==KotlinJournalSetOutcome.SKIPPED -> "Session ${last.completionOrder} · skipped"
            last.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT -> "Session ${last.completionOrder} · failed attempt"
            else -> "Session ${last.completionOrder} · ${kotlinLoadBasisLabel(last.loadBasis)}"+
                if(actual?.actualRir==null)" · RIR unreported" else ""
        }
        Text(caption,modifier=Modifier.heightIn(min=PursuitSpace.xxl),
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/**
 * One cancellation-scoped, asynchronous prior-session Leveling review for
 * BOTH native workout types. Inventory inspection may enumerate many valid
 * plate combinations: never do that work during Compose composition.
 *
 * Key all inputs (including the physical station and exact Previous facts).
 * A changed key immediately exposes null, not a stale actionable candidate.
 * No callback here can edit a journal, approved workout, or future load.
 */
@Composable
internal fun rememberPriorWorkoutLevelingReview(
    currentSets:List<Pair<WorkoutReferenceKey,DraftSetLoad>>,
    previous:WorkoutLastReferenceResult?,
    machineInstanceId:String?,
    repBand:RepBand?,
    goalRir:Int?,
    verifiedStock:VerifiedLoadStock?
):LevelingReviewResult? {
    var review by remember(currentSets,previous,machineInstanceId,repBand,
        goalRir,verifiedStock) {
        mutableStateOf<LevelingReviewResult?>(null)
    }
    LaunchedEffect(currentSets,previous,machineInstanceId,repBand,
        goalRir,verifiedStock) {
        review=null
        if(previous==null) return@LaunchedEffect
        if(repBand==null || goalRir==null) {
            review=LevelingReviewResult.Rejected(
                LevelingRefusal.INCOMPLETE_OR_INVALID)
            return@LaunchedEffect
        }
        review=try {
            withContext(Dispatchers.Default) {
                DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(
                    currentSets,previous,machineInstanceId,repBand,goalRir,
                    verifiedStock)
            }
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(_:Exception) {
            // Leveling is optional: an unforeseen inventory/validation
            // problem must not kill the running workout or touch its journal.
            LevelingReviewResult.Rejected(LevelingRefusal.INCOMPLETE_OR_INVALID)
        }
    }
    return review
}
