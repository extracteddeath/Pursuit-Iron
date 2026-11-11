package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.math.RoundingMode

private fun performanceLabel(value:BigDecimal,unit:String):String =
    "${value.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} $unit"

/**
 * A genuine observed-history trend for any exact movement ID, not an
 * unsourced Strength Standards ranking or a coaching recommendation.
 * Only a caller holding the full owner-verified mixed timeline may show it.
 */
@Composable
internal fun NativeKotlinExercisePerformanceTrend(
    timeline:NativeKotlinMixedHistoryTimeline,
    exerciseId:String,
    displayUnit:LoadUnit,
    onOpenHistory:()->Unit
) {
    var basisChoice by rememberSaveable(exerciseId){mutableStateOf<String?>(null)}
    var stationGym by rememberSaveable(exerciseId){mutableStateOf<String?>(null)}
    var stationMachine by rememberSaveable(exerciseId){mutableStateOf<String?>(null)}
    var windowChoice by rememberSaveable(exerciseId){mutableStateOf<String?>(null)}
    var showValues by rememberSaveable(exerciseId){mutableStateOf(false)}
    val outcome=remember(timeline,exerciseId,displayUnit) {
        NativeExercisePerformanceTrendDomain.project(
            timeline,exerciseId,displayUnit)
    }
    val basisOptions=outcome.series.map {
        it.key.basis?.name ?: "BODYWEIGHT"
    }.distinct()
    val basis=(basisChoice?.takeIf{it in basisOptions}
        ?:basisOptions.firstOrNull())
    val basisSeries=outcome.series.filter {
        (it.key.basis?.name ?: "BODYWEIGHT")==basis
    }
    val stations=basisSeries.map{it.key.gymId to it.key.machineInstanceId}.distinct()
    val station=stations.firstOrNull {
        it.first==stationGym && it.second==stationMachine
    } ?:stations.firstOrNull()
    val stationSeries=basisSeries.filter{
        it.key.gymId==station?.first && it.key.machineInstanceId==station?.second
    }
    val windows=stationSeries.map{it.key.reps.name}
    val window=windowChoice?.takeIf{it in windows} ?:windows.firstOrNull()
    val selected=stationSeries.firstOrNull{it.key.reps.name==window}
    val bodyweight=selected?.key?.bodyweightOnly==true
    val unit=if(bodyweight)"reps" else displayUnit.name.lowercase()
    fun measure(value:BigDecimal)=performanceLabel(value,unit)
    PursuitCard {
        PursuitSectionHeading("Working-set performance",
            kotlinExerciseLabel(exerciseId))
        if(outcome.series.isEmpty()) {
            PursuitHistoryStatus("No comparable completed sets",
                "Complete working sets with valid recorded loads or unweighted bodyweight reps to create a factual trend.")
        } else {
            if(basisOptions.size>1) {
                Text("Equipment loading",
                    style=MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    basisOptions.forEach {choice ->
                        val label=if(choice=="BODYWEIGHT")"Bodyweight"
                        else kotlinLoadBasisLabel(LoadBasis.valueOf(choice))
                        FilterChip(selected=basis==choice,
                            onClick={
                                basisChoice=choice
                                stationGym=null
                                stationMachine=null
                                windowChoice=null
                                showValues=false
                            },
                            label={Text(label)})
                    }
                }
            }
            if(stations.size>1) {
                Text("Gym · physical machine",style=MaterialTheme.typography.labelLarge)
                Text("Physical machine loads are independent. Select one station to see its actual trend.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    stations.forEach {item->
                        FilterChip(selected=station==item,
                            onClick={
                                stationGym=item.first
                                stationMachine=item.second
                                windowChoice=null
                                showValues=false
                            },
                            label={Text(if(item.first==null)
                                "Portable / unassigned"
                                else "${item.first} · ${item.second ?: "Older unassigned machine"}")})
                    }
                }
            }
            val windowOptions=stationSeries.map{it.key.reps}.distinct()
            if(windowOptions.size>1) {
                Text("Performed repetitions",
                    style=MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    windowOptions.forEach {choice ->
                        FilterChip(selected=window==choice.name,
                            onClick={
                                windowChoice=choice.name
                                showValues=false
                            },
                            label={Text(choice.label)})
                    }
                }
            }
            if(selected!=null) {
                val points=selected.points.sortedBy{it.completionOrder}
                val recent=points.takeLast(20)
                val first=recent.first()
                val last=recent.last()
                val firstValue=first.actualLoad ?:BigDecimal(first.performedReps)
                val lastValue=last.actualLoad ?:BigDecimal(last.performedReps)
                Text(
                    (if(bodyweight)
                        "Best completed unweighted working-set reps"
                    else "Heaviest completed working set")+
                        " per session · ${selected.key.reps.label} · "+
                        (if(bodyweight)"bodyweight"
                            else kotlinLoadBasisLabel(selected.key.basis)),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(recent.size==1) {
                    PursuitHistoryStatus("One qualifying session",
                        "Session ${last.completionOrder} · ${measure(lastValue)}" +
                            " for ${last.performedReps} reps. A second qualifying session is needed to show change.")
                } else {
                    val delta=lastValue-firstValue
                    val difference=(if(delta.signum()>0)"+" else "")+
                        measure(delta)
                    PursuitMetricStrip(listOf(
                        "First shown" to measure(firstValue),
                        "Latest" to measure(lastValue),
                        "Difference" to difference
                    ))
                    PursuitFactualLinePlot(
                        recent.map{sample->
                            PursuitFactualTrendPoint(sample.completionOrder,
                                sample.actualLoad ?:BigDecimal(sample.performedReps))
                        },
                        if(bodyweight)"Completed reps" else "Completed working load",
                        ::measure
                    )
                    Text("First shown · session ${first.completionOrder} · "+
                            measure(firstValue)+" × ${first.performedReps} reps",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Latest · session ${last.completionOrder} · "+
                            measure(lastValue)+" × ${last.performedReps} reps",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if(points.size>20)
                    "Last 20 of ${points.size} qualifying sessions shown."
                    else "${points.size} qualifying ${if(points.size==1)"session" else "sessions"} shown.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Only the exact exercise, selected physical machine (when relevant), loading basis and rep window are compared. " +
                    "Reps, effort and fatigue may still differ. These are recorded values, " +
                    "not an adjusted strength score, a PR certificate or a coaching target.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={showValues=!showValues},
                    modifier=Modifier.heightIn(min=48.dp)) {
                    Text(if(showValues)"Hide recorded points"
                        else "Show recorded points")
                }
                if(showValues) {
                    recent.asReversed().forEach {sample ->
                        val actual=sample.actualLoad ?:
                            BigDecimal(sample.performedReps)
                        Text("Session ${sample.completionOrder} · "+
                            measure(actual)+" · ${sample.performedReps} reps",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        OutlinedButton(onClick=onOpenHistory,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text("Open full exercise history")
        }
    }
}
