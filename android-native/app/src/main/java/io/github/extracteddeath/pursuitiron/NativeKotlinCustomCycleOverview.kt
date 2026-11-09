package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomNextCycleSlot
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Read-only base structure. Exact week overrides remain in the verified workout preview. */
private fun authoredLoadDescription(load:DraftSetLoad):String = when(load) {
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()}"
}

private fun authoredSetDescription(set:CustomAuthoredSet,number:Int):String {
    val kind=set.type.name.lowercase().replaceFirstChar{it.uppercase()}
    val reps=if(set.reps.minimum==set.reps.maximum)
        "${set.reps.minimum}" else "${set.reps.minimum}–${set.reps.maximum}"
    return "Set $number · $kind · ${authoredLoadDescription(set.load)} · " +
        "$reps reps · RIR ${set.targetRir?.toString() ?: "not set"} · " +
        "${set.restSeconds}s rest"
}
@Composable
internal fun NativeKotlinCustomCycleOverview(
    cycle:CustomTrainingCycle,selection:NativeCustomNextCycleSlot?
) {
    val week=when(selection) {
        is NativeCustomNextCycleSlot.Next -> selection.absoluteWeek
        is NativeCustomNextCycleSlot.Prepared -> selection.absoluteWeek
        is NativeCustomNextCycleSlot.ResumeIssued -> selection.absoluteWeek
        else -> 1
    }
    val weekday=when(selection) {
        is NativeCustomNextCycleSlot.Next -> selection.weekday
        is NativeCustomNextCycleSlot.Prepared -> selection.weekday
        is NativeCustomNextCycleSlot.ResumeIssued -> selection.weekday
        else -> null
    }
    var remainingWeek=week
    val selectedBlock=cycle.blocks.firstOrNull {
        if(remainingWeek>it.weeks){remainingWeek-=it.weeks;false}else true
    }
    val days=listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    PursuitCard {
        Text("Program outline",style=MaterialTheme.typography.titleLarge)
        Text("Base training schedule. Your workout preview includes any changes for its specific week.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        cycle.blocks.forEach { block ->
            Text(block.label,style=MaterialTheme.typography.titleMedium)
            Text(block.phase.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}+
                " · ${block.weeks} ${if(block.weeks==1)"week" else "weeks"} · ${block.days.size} days/week",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            block.days.sortedBy{it.weekday}.forEach { day ->
                var expanded by rememberSaveable(cycle.cycleId,block.blockId,day.weekday,week,weekday) {
                    mutableStateOf(block==selectedBlock &&
                        day.weekday==(weekday ?: block.days.minOfOrNull{it.weekday}))
                }
                HorizontalDivider()
                PursuitDisclosureRow(
                    title="${days.getOrElse(day.weekday){"Day"}} · ${day.label}",
                    subtitle="${day.slots.size} exercises",
                    expanded=expanded,onToggle={expanded=!expanded}
                )
                if(expanded)day.slots.forEach { slot ->
                    val working=slot.sets.count{it.type==DraftSetType.WORKING}
                    val warmups=slot.sets.count{it.type==DraftSetType.WARMUP}
                    var showTargets by rememberSaveable(cycle.cycleId,block.blockId,
                        day.weekday,slot.slotId) {mutableStateOf(false)}
                    Column(Modifier.fillMaxWidth().padding(horizontal=PursuitSpace.sm),
                        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            Column(Modifier.weight(1f)) {
                                Text(kotlinExerciseLabel(slot.exerciseId),
                                    style=MaterialTheme.typography.bodyMedium)
                                Text("$working working ${if(working==1)"set" else "sets"}"+
                                    if(warmups>0)" · $warmups ${if(warmups==1)"warm-up" else "warm-ups"}" else "",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick={showTargets=!showTargets},
                                modifier=Modifier.heightIn(min=48.dp)) {
                                Text(if(showTargets)"Hide sets" else "See sets")
                            }
                        }
                        if(showTargets) {
                            slot.sets.forEachIndexed {index,set ->
                                Text(authoredSetDescription(set,index+1),
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                set.amplifier?.let { amplifier ->
                                    Text("  ${amplifier.kind.name.lowercase().replace('_',' ')} · " +
                                        "${amplifier.followUpReps} follow-up reps · " +
                                        "${amplifier.followUpRestSeconds}s rest" +
                                        (amplifier.followUpLoad?.let {
                                            " · ${authoredLoadDescription(it)}"
                                        } ?: " · same load"),
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
