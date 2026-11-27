package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

internal data class NativeRemainingEditAnchor(
    val workoutId:String,val revision:Long,val occurrenceId:String,val exerciseId:String,
    val workingCount:Int,val resolvedCount:Int,val reps:RepBand,val restSeconds:Int
)

/** Both workout screens present the same exact-revision, workout-only review.
 * This is a draft, not an approval: the native owner checks the full
 * current signed program and stock again before saving anything. */
@Composable
internal fun NativeRemainingEditSheet(
    anchor:NativeRemainingEditAnchor,currentRevision:Long?,busy:Boolean,
    review:(MidSessionWorkingEdit)->MidSessionWorkingEditReview<*>?,
    onApply:(MidSessionWorkingEdit)->Unit,onDismiss:()->Unit
) {
    var count by rememberSaveable(anchor.workoutId,anchor.revision,anchor.occurrenceId){
        mutableStateOf(anchor.workingCount.toString())
    }
    var minimum by rememberSaveable(anchor.workoutId,anchor.revision,anchor.occurrenceId){
        mutableStateOf(anchor.reps.minimum.toString())
    }
    var maximum by rememberSaveable(anchor.workoutId,anchor.revision,anchor.occurrenceId){
        mutableStateOf(anchor.reps.maximum.toString())
    }
    var rest by rememberSaveable(anchor.workoutId,anchor.revision,anchor.occurrenceId){
        mutableStateOf(anchor.restSeconds.toString())
    }
    val min=minimum.toIntOrNull();val max=maximum.toIntOrNull()
    val n=count.toIntOrNull();val seconds=rest.toIntOrNull()
    val request=if(n!=null && min!=null && max!=null && seconds!=null &&
        n in 1..20 && min in 1..100 && max in min..100 && seconds in 0..3600)
        MidSessionWorkingEdit(anchor.workoutId,anchor.revision,anchor.occurrenceId,n,
            RepBand(min,max).takeUnless{it==anchor.reps},seconds.takeUnless{it==anchor.restSeconds})
    else null
    val stale=currentRevision!=anchor.revision
    val result=if(!stale && request!=null)review(request) else null
    val preview=result as? MidSessionWorkingEditReview.Reviewed<*>
    NativeEditSheet(busy=busy,onDismiss=onDismiss) {
        Text("Adjust remaining sets",style=MaterialTheme.typography.headlineSmall)
        Text(kotlinExerciseLabel(anchor.exerciseId),style=MaterialTheme.typography.titleMedium)
        Text("This workout only. Completed work, warm-ups and your saved program stay unchanged.",
            style=MaterialTheme.typography.bodyMedium)
        Text("${anchor.resolvedCount} of ${anchor.workingCount} working sets already resolved")
        OutlinedTextField(count,{count=it.take(2)},label={Text("Total working sets")},
            singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(minimum,{minimum=it.take(3)},label={Text("Min reps")},
                modifier=Modifier.weight(1f),enabled=!busy,singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(maximum,{maximum=it.take(3)},label={Text("Max reps")},
                modifier=Modifier.weight(1f),enabled=!busy,singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        OutlinedTextField(rest,{rest=it.take(4)},label={Text("Rest (seconds)")},
            singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        when {
            stale -> Text("Workout changed. Close and review again.",color=MaterialTheme.colorScheme.error)
            request==null ->Text("Enter a valid set count, rep range and rest period.")
            preview!=null -> {
                Text("${preview.addedSetKeys.size} added · ${preview.omittedSetKeys.size} removed · ${preview.resolvedSetKeys.size} resolved rows preserved")
                Text("Saving requires current equipment and full training-limit verification. Undo is available before the next saved action.",
                    style=MaterialTheme.typography.bodySmall)
            }
            result is MidSessionWorkingEditReview.Rejected ->Text(
                when(result.reason){
                    MidSessionWorkingEditRefusal.NO_CHANGE->"Choose a change to review."
                    MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL->"This prescription is protected; use the existing program or technique review."
                    MidSessionWorkingEditRefusal.RESOLVED_WORK->"Recorded sets cannot be removed."
                    MidSessionWorkingEditRefusal.NO_PENDING_WORK->"No pending working sets."
                    else->"This change is unavailable: "+result.reason.name.lowercase().replace('_',' ')
                },style=MaterialTheme.typography.bodySmall)
        }
        PursuitPrimaryAction("Confirm workout-only change",enabled=!busy && !stale && preview!=null){
            request?.let(onApply)
        }
        TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}
    }
}
