package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Only a local navigation request. It is NEVER an injury diagnosis or
 * authorization for a workout writer to skip an exercise.
 */
internal data class NativeMovementConcernRequest(
    val scopeId:String?,
    val journalRevision:Long
)

internal data class NativeMovementConcernOptions(
    val current:Boolean=false,
    val exerciseId:String?=null,
    val remaining:Int=0,
    val alreadyRecorded:Int=0,
    val skipRequest:NativeExerciseSkipRequest?=null,
    /** Navigation only. A fresh source review still decides replacement. */
    val replacementScopeId:String?=null
)

/** One strict eligibility rule shared by the Focus toolbar, concern guide
 * and the callbacks that open an owner-verified replacement review. Any
 * historical set record blocks swapping, even if corrected later.
 */
internal object NativeMovementSwapEligibility {
    fun generated(journal:KotlinWorkoutJournalState?,exerciseId:String?):Boolean {
        if(journal==null || exerciseId.isNullOrBlank())return false
        return KotlinComposerSwapReviewDomain.canReviewReplacement(journal,exerciseId)
    }
    fun custom(journal:CustomWorkoutJournalDraft?,slotId:String?):Boolean {
        if(journal==null || slotId.isNullOrBlank() ||
            journal.finishedByEventId!=null)return false
        val slot=journal.snapshot.slots.singleOrNull{it.slotId==slotId}
            ?:return false
        if(slot.supersetGroup!=null)return false
        val keys=slot.sets.map{it.setKey}.toSet()
        return journal.receipts.none {receipt->
            (receipt.operation as? CustomWorkoutJournalOperation.Record)
                ?.visit?.setKey?.let{it in keys}==true
        }
    }
}

internal object NativeMovementConcernPresentation {
    fun review(
        request:NativeMovementConcernRequest,
        currentRevision:Long?,
        active:Boolean,
        remaining:RemainingExerciseReview<*>?,
        canReviewReplacement:Boolean=false
    ):NativeMovementConcernOptions {
        // An in-flight write, closed journal or updated revision never
        // inherits the scope selected when this dialog first opened.
        if(!active || currentRevision==null ||
            request.journalRevision!=currentRevision)
            return NativeMovementConcernOptions()
        val exact=remaining?.takeIf {
            request.scopeId!=null &&
                it.journalRevision==currentRevision
        }
        val count=exact?.entries?.size ?:0
        return NativeMovementConcernOptions(
            current=true,
            exerciseId=exact?.exerciseId,
            remaining=count,
            alreadyRecorded=exact?.preservedEntryCount ?:0,
            skipRequest=if(count>0)
                NativeExerciseSkipRequest(
                    checkNotNull(request.scopeId),currentRevision)
            else null,
            replacementScopeId=if(count>0 && canReviewReplacement &&
                exact?.preservedEntryCount==0)request.scopeId else null
        )
    }
}

/** Report a movement concern without inventing a diagnosis or silently
 * reducing weight, changing future plans or skipping any issued set.
 * "Review skip" opens the EXISTING independent confirmation and writer.
 */
@Composable
internal fun NativeKotlinMovementConcernDialog(
    options:NativeMovementConcernOptions,
    busy:Boolean,
    onWriteNote:()->Unit,
    onReviewSkip:()->Unit,
    onReviewReplacement:()->Unit,
    onDismiss:()->Unit,
    onReviewMuscles:(()->Unit)?=null
) {
    val editable=options.current && !busy
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text("Movement concern")},
        text={Column(
            Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            Text(
                "Optional coaching is now set to low readiness with a movement restriction for this session. Your issued weights, reps and future program are unchanged.",
                style=MaterialTheme.typography.bodyMedium)
            if(!options.current) {
                Text(
                    "The saved workout changed while this guide was open. Close it, review the latest set, then report the concern again.",
                    color=MaterialTheme.colorScheme.error)
            } else {
                options.exerciseId?.let{exercise->
                    Text(kotlinExerciseLabel(exercise),
                        style=MaterialTheme.typography.titleMedium)
                }
                if(options.remaining>0)Text(
                    "${options.remaining} unresolved entries in this exercise · ${options.alreadyRecorded} already recorded. Existing actuals are preserved.",
                    style=MaterialTheme.typography.bodySmall)
                else Text(
                    "No current unresolved exercise is selected. You may still document the concern.",
                    style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick=onWriteNote,
                enabled=editable,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Write session note")}
            onReviewMuscles?.let { review->
                OutlinedButton(onClick=review,enabled=editable,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text("Review affected muscles")
                }
            }
            if(options.skipRequest!=null)OutlinedButton(
                onClick=onReviewSkip,
                enabled=editable,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Review skipping remaining exercise")}
            if(options.replacementScopeId!=null)OutlinedButton(
                onClick=onReviewReplacement,
                enabled=editable,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Review replacement exercises")}
            else if(options.current && options.remaining>0 &&
                options.exerciseId!=null)Text(
                "Replacement is available only before this exercise has recorded work, and not for linked superset movements.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Writing a note requires Save. Skipping requires a second confirmation. Replacement requires a separate gym-verified review. Nothing happens automatically.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={
            TextButton(enabled=!busy,onClick=onDismiss,
                modifier=Modifier.heightIn(min=48.dp)) {
                Text("Back to workout")
            }
        }
    )
}


/** Shared generated/custom review, with exact source-derived entries. */
@Composable
internal fun <K> NativeKotlinMuscleConcernDialog(
    workoutId:String,revision:Long,currentRevision:Long?,busy:Boolean,
    load:()->List<WorkoutConcernReview<K>>,
    onConfirm:(WorkoutConcernReview<K>)->Unit,onDismiss:()->Unit
) {
    var reviews by remember(workoutId,revision){mutableStateOf<List<WorkoutConcernReview<K>>?>(null)}
    var selectedMuscle by remember(workoutId,revision){mutableStateOf<TrainingMuscle?>(null)}
    var action by remember(workoutId,revision){mutableStateOf(WorkoutConcernAction.SKIP)}
    val current=currentRevision==revision
    LaunchedEffect(workoutId,revision) {
        reviews=withContext(Dispatchers.IO){runCatching{load()}.getOrDefault(emptyList())}
            .filter{it.workoutId==workoutId && it.journalRevision==revision}
    }
    val shown=reviews?.singleOrNull{it.muscle==selectedMuscle && it.action==action}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Review affected muscles")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            Text("Choose the affected muscle. This is not a diagnosis or clearance to train through pain. Stop the movement if it causes concerning symptoms.")
            if(!current)Text("The workout changed. Reopen this review.",color=MaterialTheme.colorScheme.error)
            else if(reviews==null)Text("Checking saved movement metadata…")
            else if(reviews.isNullOrEmpty())Text("Exact muscle involvement could not be verified, or no affected entries remain. Use the individual movement review instead.")
            else {
                reviews.orEmpty().map{it.muscle}.distinct().forEach{muscle->
                    TextButton(onClick={selectedMuscle=muscle},enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text((if(selectedMuscle==muscle)"Selected · " else "")+nativeLibraryWords(muscle.name))
                    }
                }
                selectedMuscle?.let{
                    WorkoutConcernAction.entries.forEach{option->
                        OutlinedButton(onClick={action=option},enabled=!busy,
                            modifier=Modifier.fillMaxWidth()) {
                            Text((if(action==option)"Selected · " else "")+
                                if(option==WorkoutConcernAction.SKIP)"Skip affected work" else "Reduce affected work")
                        }
                    }
                }
                shown?.let{review->
                    review.groups.forEach{group->
                        Text(kotlinExerciseLabel(group.exerciseId)+" · "+group.entries.size+
                            " entries to skip · "+group.recordedCount+" recorded retained")
                    }
                    Text(if(action==WorkoutConcernAction.REDUCE)
                        "Caps working-set attempts at half the original issued count (rounded up), counts completed and failed attempts even if logged out of order, and skips extra pending work and unlocked affected technique extensions. Already recorded entries stay unchanged."
                        else "Skips the unresolved affected entries only.")
                    Text("No future program, issued target or existing actual will change.")
                }
            }
        }},
        confirmButton={Button(onClick={shown?.let(onConfirm)},
            enabled=!busy && current && shown?.entries?.isNotEmpty()==true){Text("Confirm reviewed skips")}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text("Back")}})
}
