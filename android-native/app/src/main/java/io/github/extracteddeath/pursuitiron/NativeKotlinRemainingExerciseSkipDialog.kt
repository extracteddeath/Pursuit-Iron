package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.extracteddeath.pursuitiron.rebuild.RemainingExerciseReview

/** UI selection only, never an authorization to write a journal. */
internal data class NativeExerciseSkipRequest(val scopeId:String,val revision:Long)

@Composable
internal fun NativeKotlinRemainingExerciseSkipDialog(
    request:NativeExerciseSkipRequest,
    review:RemainingExerciseReview<*>?,
    busy:Boolean,
    onConfirm:()->Unit,
    onDismiss:()->Unit
) {
    val current=review?.takeIf{it.journalRevision==request.revision}
    val canSkip=!busy && current!=null && current.entries.isNotEmpty()
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text("Skip remaining exercise?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            if(current!=null) {
                Text(kotlinExerciseLabel(current.exerciseId),
                    style=MaterialTheme.typography.titleMedium)
                val parts=buildList {
                    if(current.warmupCount>0)add("${current.warmupCount} warm-up ${if(current.warmupCount==1)"set" else "sets"}")
                    if(current.workingCount>0)add("${current.workingCount} working ${if(current.workingCount==1)"set" else "sets"}")
                    if(current.followUpCount>0)add("${current.followUpCount} amplifier follow-up ${if(current.followUpCount==1)"entry" else "entries"}")
                }
                Text(if(parts.isEmpty())"No remaining entries to skip."
                    else parts.joinToString(" · "))
                Text("Already recorded sets are kept. Only the remaining entries shown here will be recorded as skipped. Your future program stays the same.")
                Text("If saving is interrupted, reopen the workout to review what was saved before skipping the rest.",
                    style=MaterialTheme.typography.bodySmall)
            } else Text("This workout changed while confirmation was open. Close and review the remaining entries again.")
        }},
        confirmButton={Button(enabled=canSkip,onClick=onConfirm) {
            Text("Skip remaining entries")
        }},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Keep training")}}
    )
}
