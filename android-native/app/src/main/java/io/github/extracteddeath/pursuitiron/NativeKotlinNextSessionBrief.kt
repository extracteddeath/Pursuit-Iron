package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * The next SESSION focus reflects the existing verified program and confirmed
 * next-day selection. It is a readable BASE outline, not a separate coach,
 * prediction, immutable issued workout or week-override authority.
 */
@Composable
internal fun NativeKotlinNextSessionBrief(
    cycle:CustomTrainingCycle,
    absoluteWeek:Int,
    weekday:Int
) {
    if(absoluteWeek<1 || weekday !in 0..6)return
    var withinBlock=absoluteWeek
    val block=cycle.blocks.firstOrNull {
        if(withinBlock>it.weeks) {
            withinBlock-=it.weeks
            false
        } else true
    } ?:return
    val day=block.days.firstOrNull{it.weekday==weekday} ?:return
    val working=day.slots.sumOf{slot->
        slot.sets.count{it.type==DraftSetType.WORKING}
    }
    val warmups=day.slots.sumOf{slot->
        slot.sets.count{it.type==DraftSetType.WARMUP}
    }
    HorizontalDivider()
    PursuitSectionHeading(
        "Your next session focus",
        "${block.label} · ${block.phase.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}} " +
            "· ${day.label}"
    )
    PursuitMetricStrip(listOf(
        "Exercises" to day.slots.size.toString(),
        "Working sets" to working.toString(),
        "Warm-up sets" to warmups.toString()
    ))
    if(day.slots.isEmpty()) {
        Text("This authored day has no exercises. Finish the program editor before reviewing it.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        day.slots.take(4).forEach {slot->
            Text(kotlinExerciseLabel(slot.exerciseId),
                style=MaterialTheme.typography.bodyMedium)
        }
        if(day.slots.size>4)
            Text("Plus ${day.slots.size-4} more in the complete program outline.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("Base program outline only. Exact week-specific sets, load changes and your current gym are verified in the workout preview before preparation.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}
