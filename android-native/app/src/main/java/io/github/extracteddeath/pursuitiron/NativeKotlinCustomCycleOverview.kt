package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomNextCycleSlot
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Read-only base structure. Exact week overrides remain in the verified workout preview. */
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
                TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                        Column(Modifier.weight(1f)) {
                            Text("${days.getOrElse(day.weekday){"Day"}} · ${day.label}",
                                style=MaterialTheme.typography.titleSmall)
                            Text("${day.slots.size} exercises",style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if(expanded)Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription=if(expanded)"Hide exercises" else "Show exercises")
                    }
                }
                if(expanded)day.slots.forEach { slot ->
                    val working=slot.sets.count{it.type==DraftSetType.WORKING}
                    val warmups=slot.sets.count{it.type==DraftSetType.WARMUP}
                    Column(Modifier.fillMaxWidth().padding(horizontal=PursuitSpace.sm),
                        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                        Text(kotlinExerciseLabel(slot.exerciseId),style=MaterialTheme.typography.bodyMedium)
                        Text("$working working ${if(working==1)"set" else "sets"}"+
                            if(warmups>0)" · $warmups ${if(warmups==1)"warm-up" else "warm-ups"}" else "",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
