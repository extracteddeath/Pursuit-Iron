package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

private val compareWeekdays=listOf(
    "Mon","Tue","Wed","Thu","Fri","Sat","Sun"
)
private fun compareWeekday(day:Int)=compareWeekdays.getOrElse(day){"Day"}
private fun compareSplit(split:CustomSplitIdentity):String=when(split) {
    is CustomSplitIdentity.Named->split.split.name
        .lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
    is CustomSplitIdentity.Freeform->split.label
}
private fun signedCount(value:Int)=when {
    value>0 -> "+$value"
    else -> value.toString()
}
private fun auditedRange(sets:List<Int>):String=
    if(sets.minOrNull()==sets.maxOrNull())
        sets.firstOrNull()?.toString() ?: "—"
    else "${sets.minOrNull()}–${sets.maxOrNull()}"

@Composable
private fun BlockComparisonColumn(
    title:String,
    facts:CustomBlockComparisonFacts,
    modifier:Modifier=Modifier
) {
    Surface(
        modifier=modifier.fillMaxWidth(),
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        shape=MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
        ) {
            Text(title,style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(facts.label,style=MaterialTheme.typography.titleMedium)
            Text("${facts.phase.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}} " +
                "· ${facts.weeks} weeks",
                style=MaterialTheme.typography.bodySmall)
            Text("Split: ${compareSplit(facts.split)}",
                style=MaterialTheme.typography.bodyMedium)
            Text("${facts.weekdays.size} days/week · " +
                facts.weekdays.joinToString{compareWeekday(it)},
                style=MaterialTheme.typography.bodySmall)
            Text("${facts.baseWorkingSets} base working sets/week · " +
                "${facts.baseWarmupSets} warm-up sets",
                style=MaterialTheme.typography.bodyMedium)
            Text("${facts.exerciseVisits} exercise visits · " +
                "${facts.uniqueExercises} movements · " +
                "${facts.pinnedVisits} pinned visits",
                style=MaterialTheme.typography.bodySmall)
            if(facts.weekOverrideCount>0)
                Text("${facts.weekOverrideCount} week-specific override(s)",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Compare actual saved adjacent cycle blocks. This is NEVER a generated
 * alternative, coach recommendation or permission to replace a training day.
 * Revision/source-gated audit measurements are presented separately from
 * the base authored work so week overrides cannot silently disappear.
 */
@Composable
internal fun NativeKotlinCycleBlockComparisonPanel(
    cycle:CustomTrainingCycle,
    currentAudit:CustomCoachAudit?,
    onEditBlock:(String)->Unit,
    enabled:Boolean=true
) {
    if(cycle.blocks.size<2)return
    var selected by rememberSaveable(cycle.cycleId){
        mutableStateOf(cycle.blocks[1].blockId)
    }
    val id=selected.takeIf {candidate->
        cycle.blocks.drop(1).any{it.blockId==candidate}
    } ?:cycle.blocks[1].blockId
    val comparison=remember(cycle,id,currentAudit){
        CustomCycleBlockComparisonDomain.compare(cycle,id,currentAudit)
    } ?:return
    val prior=comparison.prior
    val next=comparison.next
    val fontScale=LocalDensity.current.fontScale
    PursuitCard {
        PursuitSectionHeading("Compare training blocks",
            "See how the next authored block differs before you approve a cycle change.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            cycle.blocks.drop(1).forEach {block->
                FilterChip(
                    selected=id==block.blockId,
                    onClick={selected=block.blockId},
                    label={Text(block.label)}
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth>=670.dp && fontScale<1.35f) {
                Row(
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
                ) {
                    BlockComparisonColumn("Previous block",prior,
                        modifier=Modifier.weight(1f))
                    BlockComparisonColumn("Selected block",next,
                        modifier=Modifier.weight(1f))
                }
            } else Column(
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
            ) {
                BlockComparisonColumn("Previous block",prior)
                BlockComparisonColumn("Selected block",next)
            }
        }
        PursuitMetricStrip(listOf(
            "Base working sets" to "${prior.baseWorkingSets} → ${next.baseWorkingSets}",
            "Set change" to signedCount(comparison.baseWorkingSetDelta),
            "Days per week" to "${prior.weekdays.size} → ${next.weekdays.size}"
        ))
        Text("Base figures are authored per-week targets, not completed workouts. " +
            "The comparison is factual: fewer sets do not automatically mean " +
            "better recovery or sufficient strength training.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(prior.auditedWeeklyWorkingSets!=null &&
            next.auditedWeeklyWorkingSets!=null) {
            HorizontalDivider()
            PursuitSectionHeading("Full-cycle measurements",
                "Includes exact saved week overrides. Review status is shown separately.")
            PursuitMetricStrip(listOf(
                "Earlier weeks" to auditedRange(prior.auditedWeeklyWorkingSets),
                "Next weeks" to auditedRange(next.auditedWeeklyWorkingSets),
                "First-week change" to (
                    comparison.firstAuditedWeekDelta?.let(::signedCount) ?: "—")
            ))
            Text(
                if(comparison.matchingAuditStatus==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
                    "These weeks are from the current source-backed full-cycle review."
                else "These measurements have ${comparison.auditIssueCount ?: 0} " +
                    "unresolved audit finding(s). They are not coaching clearance.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else if(prior.weekOverrideCount>0 || next.weekOverrideCount>0) {
            Text("One or both blocks include week-specific prescriptions. " +
                "Run the full-cycle review to compare their effective weekly sets; " +
                "the base totals above exclude overrides.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(comparison.addedWeekdays.isNotEmpty() ||
            comparison.removedWeekdays.isNotEmpty()) {
            HorizontalDivider()
            PursuitSectionHeading("Calendar differences")
            if(comparison.addedWeekdays.isNotEmpty())
                Text("New training days: " +
                    comparison.addedWeekdays.joinToString {compareWeekday(it)})
            if(comparison.removedWeekdays.isNotEmpty())
                Text("Days no longer scheduled: " +
                    comparison.removedWeekdays.joinToString {compareWeekday(it)})
        }
        if(comparison.exerciseChanges.isNotEmpty()) {
            HorizontalDivider()
            PursuitSectionHeading("Exercise exposure changes",
                "Counts are visits per base week, not sets or predicted progress.")
            comparison.exerciseChanges.take(8).forEach {change->
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    Text(kotlinExerciseLabel(change.exerciseId),
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyMedium)
                    Text("${change.beforeVisits} → ${change.afterVisits}",
                        style=MaterialTheme.typography.labelLarge)
                }
            }
            if(comparison.exerciseChanges.size>8)
                Text("${comparison.exerciseChanges.size-8} more movement differences " +
                    "are available in the full program editor.",
                    style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick={onEditBlock(next.blockId)},
            enabled=enabled,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) {Text("Inspect and edit ${next.label}")}
        Text("No new program has been generated or approved here. " +
            "Adaptive alternatives require a complete athlete-specific " +
            "generator, verified coaching policies and a separate review.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
