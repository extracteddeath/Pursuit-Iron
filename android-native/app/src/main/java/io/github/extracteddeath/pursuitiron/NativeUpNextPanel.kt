package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeNextSetPreview

/** Shared M3 training preview: no private store, training math or mutations. */
@Composable
internal fun NativeUpNextPanel(
    preview: NativeNextSetPreview,
    selectedPosition: Int,
    enabled: Boolean,
    unit: String,
    onOpenExercise: (Int)->Unit,
    compact: Boolean = false
) {
    if(preview.remainingTotal==0)return
    val upcoming=preview.upNext
    val next=upcoming ?: preview.current ?: return
    val isResume=preview.current==null
    val afterFinal=preview.current!=null && upcoming==null
    val setLabel=when(next.set.kind) {
        "myo" -> "Myo-rep mini"
        "drop" -> "Drop set"
        else -> "Set ${next.set.slot.setIndex+1}"
    }
    val target="${next.set.load} $unit × ${next.set.targetReps} reps" +
        (next.set.targetRir?.let { " · $it RIR" } ?: "")
    // Focus mode condenses the preview without losing the exact engine-issued
    // slot, units or optional effort target. It never advances the journal.
    if(compact) {
        OutlinedCard(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large) {
            Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(when {
                        afterFinal -> "Final set · ${next.exercise.name}"
                        isResume -> "Continue · ${next.exercise.name}"
                        else -> "Up next · ${next.exercise.name}"
                    },style=MaterialTheme.typography.titleSmall,maxLines=2,
                        overflow=TextOverflow.Ellipsis)
                    Text("$setLabel · $target",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,
                        overflow=TextOverflow.Ellipsis)
                }
                if(next.exercisePosition!=selectedPosition) {
                    TextButton(onClick={onOpenExercise(next.exercisePosition)},
                        enabled=enabled,
                        modifier=Modifier.semantics {
                            contentDescription="Open ${next.exercise.name}"
                        }) { Text("Open") }
                }
            }
        }
        return
    }
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
            Text("$setLabel · $target",style=MaterialTheme.typography.bodyMedium)
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
