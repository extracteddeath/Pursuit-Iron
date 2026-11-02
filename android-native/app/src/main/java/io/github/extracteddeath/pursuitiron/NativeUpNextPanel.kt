package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeNextSetPreview
import io.github.extracteddeath.pursuitiron.domain.NativePendingSet

/** Shared M3 training preview: no private store, training math or mutations. */
@Composable
internal fun NativeUpNextPanel(
    preview: NativeNextSetPreview,
    selectedPosition: Int,
    enabled: Boolean,
    onOpenExercise: (Int)->Unit
) {
    if(preview.remainingTotal==0)return
    val upcoming=preview.upNext
    val next=upcoming ?: preview.current ?: return
    val isResume=preview.current==null
    val afterFinal=preview.current!=null && upcoming==null
    OutlinedCard(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal=16.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(when {
                afterFinal -> "FINAL SET"
                isResume -> "CONTINUE WORKOUT"
                else -> "UP NEXT"
            },style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(next.exercise.name,
                style=MaterialTheme.typography.titleMedium)
            val setLabel=when {
                next.set.kind=="myo" -> "Myo-rep mini"
                next.set.kind=="drop" -> "Drop set"
                else -> "Set ${next.set.slot.setIndex+1}"
            }
            Text("$"+"setLabel · ${next.set.load} · ${next.set.targetReps} target reps"+
                (next.set.targetRir?.let { " · $"+"it RIR" } ?: ""),
                style=MaterialTheme.typography.bodyMedium)
            Text("${preview.remainingTotal} unresolved set"+
                (if(preview.remainingTotal==1)"" else "s")+" remaining",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(next.exercisePosition!=selectedPosition) {
                FilledTonalButton(
                    onClick={onOpenExercise(next.exercisePosition)},
                    enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                    Text("Open ${next.exercise.name}")
                }
            } else {
                Text(if(afterFinal)"Complete or skip this final set to finish."
                    else "The next set is on this exercise; Focus moves after the save.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
