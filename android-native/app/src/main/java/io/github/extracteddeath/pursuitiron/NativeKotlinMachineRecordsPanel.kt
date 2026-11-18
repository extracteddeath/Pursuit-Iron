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
    onOpenHistory:(String)->Unit,
    /** Source-authenticated physical station: lowest counterweight is harder. */
    assistanceByMachine:Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact> =
        emptyMap()
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val ordered=remember(bestByMachine,assistanceByMachine) {
        (bestByMachine.entries+assistanceByMachine.entries).sortedWith(
            compareBy<Map.Entry<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact>>(
                {it.key.exerciseId},{it.key.gymId},{it.key.machineInstanceId},
                {it.key.basis.name},{it.key.unit.name}))
    }
    PursuitDisclosureRow(
        title="Physical machine records",
        subtitle=if(ordered.isEmpty())"No separately identified machine sets yet"
            else "${ordered.size} gym-specific station records · completed working sets",
        expanded=expanded,onToggle={expanded=!expanded}
    )
    if(!expanded)return
    Text(
        "Each physical machine has its own completed working-set history. For ordinary machines, higher external weight is heavier. For assisted pull-up/dip machines, LESS selected counterweight is harder. Machine A/B records are not comparable strength scores or PR badges.",
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
            val stationLabel=NativeKotlinMachineStationLabels.label(
                key.exerciseId,key.gymId,key.machineInstanceId,
                ordered.filter{it.key.exerciseId==key.exerciseId}
                    .map{it.key.gymId to it.key.machineInstanceId},roster)
            val basis=kotlinLoadBasisLabel(key.basis)
            val unit=key.unit.name.lowercase()
            val assisted=key.basis==
                io.github.extracteddeath.pursuitiron.rebuild.LoadBasis.ASSISTANCE_COUNTERWEIGHT
            val bestSingle=if(assisted)null else singlesByMachine[key]
            PursuitCard {
                Text(kotlinExerciseLabel(key.exerciseId),
                    style=MaterialTheme.typography.titleMedium)
                Text("$stationLabel · $basis",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                PursuitMetricStrip(listOf(
                    (if(assisted)"Least assistance" else "Heaviest logged") to "${record.actualLoad.stripTrailingZeros().toPlainString()} $unit × ${record.reps}",
                    "Session" to record.completionOrder.toString()
                ))
                if(assisted)Text(
                    "Machine counterweight: a smaller value means less help and more bodyweight demand. This record does not estimate lifted weight or certify strength.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
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
