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
import androidx.compose.runtime.Composable
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
    onDismiss:()->Unit
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
