package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * The next SESSION focus reflects the existing verified program and confirmed
 * next-day selection and canonical reviewed week. Never count the block's
 * base template as the selected workout when a week override is present.
 */
@Composable
internal fun NativeKotlinNextSessionBrief(
    cycle:CustomTrainingCycle,
    absoluteWeek:Int,
    weekday:Int,
    resolvedWeek:CustomResolvedWeek?
) {
    if(absoluteWeek<1 || weekday !in 0..6)return
    val week=resolvedWeek?.takeIf {
        it.cycleId==cycle.cycleId && it.cycleRevision==cycle.revision &&
            it.absoluteWeek==absoluteWeek
    }
    val day=week?.days?.singleOrNull{it.weekday==weekday}
    val block=cycle.blocks.singleOrNull{it.blockId==week?.blockId}
    if(day==null || block==null) {
        Text("Week-specific prescription unavailable. Review the current program before training.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
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
    Text("Full plan for this exact week. Any explicitly approved shorter session or issued workout is shown separately.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}
