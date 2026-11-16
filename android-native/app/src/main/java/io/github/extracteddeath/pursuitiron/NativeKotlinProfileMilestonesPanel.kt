package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/** This level bar belongs inside Profile, not to the workout Leveling button. */
@Composable
internal fun NativeKotlinProfileLevelBar(progress: NativeProfileActivityProgress?) {
    if (progress == null) {
        Text("Activity level unavailable until completed native training history is verified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Training level ${progress.level}",
            style = MaterialTheme.typography.titleMedium)
        Text("${progress.totalXp} XP",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
    LinearProgressIndicator(
        progress = { progress.levelFraction },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        progress.xpForNextLevel?.let {
            "${progress.xpInLevel} / $it XP toward level ${progress.level + 1}"
        } ?: "Maximum activity level reached",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun NativeKotlinProfileMilestonesPanel(
    progress: NativeProfileActivityProgress?,
    athleteId: String?
) {
    var showAll by rememberSaveable(athleteId) { mutableStateOf(false) }
    PursuitCard {
        PursuitSectionHeading("Training milestones",
            "Based only on verified completed sessions and working sets")
        if (progress == null) {
            Text("Milestones are unavailable while native history is unverified.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            val next = progress.milestones.firstOrNull { !it.earned(progress) }
            Text("${progress.earnedMilestones} of ${progress.milestones.size} milestones earned",
                style = MaterialTheme.typography.titleMedium)
            val visible = if (showAll) progress.milestones
                else (progress.milestones.filter { it.earned(progress) }.takeLast(2) +
                    listOfNotNull(next)).distinctBy { it.id }
            if (visible.isEmpty())
                Text("Complete your first workout to unlock a milestone.",
                    style = MaterialTheme.typography.bodyMedium)
            visible.forEach { milestone ->
                val completed = milestone.current(progress)
                val earned = milestone.earned(progress)
                Column(Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PursuitSpace.xxs)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(milestone.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (earned) "Earned" else
                            "${completed.coerceAtMost(milestone.required)} / ${milestone.required}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (earned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(milestone.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!earned) LinearProgressIndicator(
                        progress = {
                            (completed.toFloat() / milestone.required.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider()
            }
            TextButton(onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (showAll) "Show recent and next" else "View all milestones")
            }
            Text("XP is 100 per completed workout + 10 per completed working set. "+
                "These are participation markers only; they never influence training weights, reps, RIR or coaching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
