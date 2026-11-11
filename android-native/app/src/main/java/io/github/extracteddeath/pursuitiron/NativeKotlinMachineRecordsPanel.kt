package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*

/**
 * Read-only per-station bests from the FULL corrected owner-verified mixed
 * history, never entered plans, unsaved workout values or gym defaults.
 * A and B are intentionally incomparable: no conversions, common PR badges,
 * barbell standards, or automatic progression permissions result from this UI.
 */
@Composable
internal fun NativeKotlinMachineRecordsPanel(
    bestByMachine:Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact>,
    singlesByMachine:Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact>,
    roster:NativeMachineRosterSnapshot?,
    onOpenHistory:(String)->Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val ordered=remember(bestByMachine) {
        bestByMachine.entries.sortedWith(
            compareBy<Map.Entry<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact>>(
                {it.key.exerciseId},{it.key.gymId},{it.key.machineInstanceId},
                {it.key.basis.name},{it.key.unit.name}))
    }
    PursuitDisclosureRow(
        title="Physical machine bests",
        subtitle=if(ordered.isEmpty())"No separately identified machine sets yet"
            else "${ordered.size} gym-specific station records · completed working sets",
        expanded=expanded,onToggle={expanded=!expanded}
    )
    if(!expanded)return
    Text(
        "Each physical machine has its own weight history. These are best logged, completed working sets—not certified PR badges, comparable machine strength scores, or barbell standards.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant
    )
    if(ordered.isEmpty()) {
        PursuitCard {
            Text("Log completed working sets with a selected physical machine to see separate records here.",
                style=MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val visible=if(showAll)ordered else ordered.take(8)
    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        visible.forEach { (key,record)->
            val label=roster?.configurations?.singleOrNull{
                it.gymId==key.gymId && it.exerciseId==key.exerciseId
            }?.machines?.singleOrNull{it.id==key.machineInstanceId}?.label
                ?: "Machine ${key.machineInstanceId}"
            val basis=key.basis.name.lowercase().replace('_',' ')
            val unit=key.unit.name.lowercase()
            val bestSingle=singlesByMachine[key]
            PursuitCard {
                Text(kotlinExerciseLabel(key.exerciseId),
                    style=MaterialTheme.typography.titleMedium)
                Text("${key.gymId} · $label · $basis",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                PursuitMetricStrip(listOf(
                    "Heaviest logged" to "${record.actualLoad.stripTrailingZeros().toPlainString()} $unit × ${record.reps}",
                    "Session" to record.completionOrder.toString()
                ))
                if(bestSingle!=null)Text(
                    "Best logged single: ${bestSingle.actualLoad.stripTrailingZeros().toPlainString()} $unit × 1",
                    style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={onOpenHistory(key.exerciseId)},
                    modifier=Modifier.heightIn(min=48.dp)) {
                    Text("Full exercise history")
                }
            }
        }
        if(ordered.size>8)
            OutlinedButton(onClick={showAll=!showAll},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(if(showAll)"Show fewer machines"
                    else "Show all ${ordered.size} station records")
            }
    }
}
