package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

private fun BigDecimal.creditText() = stripTrailingZeros().toPlainString()
private fun Enum<*>.editLabel() = name.lowercase().replace('_',' ')
private fun CustomAuthoredSet.editDescription(): String {
    fun loadText(load: DraftSetLoad): String = when(load) {
        DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
        is DraftSetLoad.Verified -> "${load.value.toPlainString()} ${load.unit} · ${load.basis.editLabel()}"
    }
    return "${type.editLabel()} · ${loadText(load)} · ${reps.minimum}–${reps.maximum} reps · RIR ${targetRir ?: "unset"} · ${activeSeconds}s work / ${restSeconds}s rest"+
        (amplifier?.let { amp -> " · ${amp.kind.editLabel()} · ${amp.followUpReps} follow-up reps / ${amp.followUpRestSeconds}s rest"+
            (amp.followUpLoad?.let { " · ${loadText(it)}" } ?: " · same load") } ?: "")
}

/** Athlete-entered sets and full-cycle consequences; saving remains a separate action. */
@Composable
internal fun NativeKotlinCustomCycleWeekEditSheet(
    review: CustomCoachWeekEditReview,
    busy: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val before=review.before.weeks.first {it.absoluteWeek==review.absoluteWeek}
    val after=review.after.weeks.first {it.absoluteWeek==review.absoluteWeek}
    NativeEditSheet(busy=busy,onDismiss=onDismiss) {
        Text("Compare your week edit",style=MaterialTheme.typography.titleLarge)
        Text("Week ${review.absoluteWeek} · ${listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")[review.weekday]}",
            style=MaterialTheme.typography.titleMedium)
        Text(if(review.restoresBlockSets) "Restore this slot's original block sets for this week."
            else "Use the exact sets you entered for this week and exercise slot.",
            style=MaterialTheme.typography.bodyMedium)
        Text("Current sets",style=MaterialTheme.typography.titleMedium)
        review.originalSets.forEachIndexed {index,set ->
            Text("${index+1}. ${set.editDescription()}",style=MaterialTheme.typography.bodySmall)
        }
        Text("Proposed sets",style=MaterialTheme.typography.titleMedium)
        review.proposedSets.forEachIndexed {index,set ->
            Text("${index+1}. ${set.editDescription()}",style=MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text("Weekly workload · current → proposed",style=MaterialTheme.typography.titleMedium)
        Text("Working sets: ${before.workingSets} → ${after.workingSets}")
        val day=review.weekday
        Text("Selected day's known work and rest: ${before.knownSecondsByDay.getValue(day)}s → ${after.knownSecondsByDay.getValue(day)}s",
            style=MaterialTheme.typography.bodySmall)
        fun duration(value:Long?)=value?.let {"${it}s"} ?: "not fully declared"
        Text("Selected day's full duration: ${duration(before.estimatedSecondsByDay[day])} → ${duration(after.estimatedSecondsByDay[day])}",
            style=MaterialTheme.typography.bodySmall)
        val changedMuscles=TrainingMuscle.entries.filter {
            before.directSets[it]!=after.directSets[it] ||
                before.totalSetCredits[it]!=after.totalSetCredits[it] ||
                before.directDays[it]!=after.directDays[it]
        }
        if(changedMuscles.isEmpty())Text("Muscle set credits and direct training days are unchanged.",
            style=MaterialTheme.typography.bodySmall)
        changedMuscles.forEach {muscle ->
            Text(muscle.editLabel(),style=MaterialTheme.typography.labelLarge)
            Text("Direct credits: ${before.directSets.getValue(muscle).creditText()} → ${after.directSets.getValue(muscle).creditText()} · total credits: ${before.totalSetCredits.getValue(muscle).creditText()} → ${after.totalSetCredits.getValue(muscle).creditText()} · direct days: ${before.directDays.getValue(muscle)} → ${after.directDays.getValue(muscle)}",
                style=MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Text("Full-cycle review",style=MaterialTheme.typography.titleMedium)
        Text("Resolved ${review.resolvedIssues.size} · introduced ${review.introducedIssues.size} · still present ${review.remainingIssues.size}",
            style=MaterialTheme.typography.bodyMedium)
        Text("Both versions use the same policy inputs. Missing inputs stay unresolved. Findings include effects on later training weeks.",
            style=MaterialTheme.typography.bodySmall)
        listOf("Resolved" to review.resolvedIssues,
            "Introduced" to review.introducedIssues,
            "Still present" to review.remainingIssues).forEach { (label,issues) ->
            if(issues.isNotEmpty()) {
                Text(label,style=MaterialTheme.typography.titleSmall)
                issues.forEach {issue ->
                    Text("${issue.location} · ${issue.explanation}",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(if(review.after.issues.isEmpty())
            "The proposed draft meets the supplied review requirements. Run a fresh audit after saving to sign off on the new revision."
            else "The proposed draft has ${review.after.issues.size} unresolved review requirements. Saving retains those requirements for the next audit.",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick=onSave,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text("Save this week's draft sets")
        }
        TextButton(onClick=onDismiss,enabled=!busy) {Text("Cancel edit")}
    }
}
