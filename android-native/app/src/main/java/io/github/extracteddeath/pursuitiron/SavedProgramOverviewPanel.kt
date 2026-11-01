package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramPresentation

/**
 * Shared Material 3 native read-only program structure. This component accepts
 * only a policy-approved presentation, never arbitrary JSON or a raw backup.
 * It does not show inferred working sets or allow any program/history mutation.
 */
@Composable
internal fun SavedProgramOverviewPanel(presentation: ReadOnlyProgramPresentation.Structure) {
    val program = presentation.program
    SectionLabel("Saved program", "Read-only structure")
    PursuitCard(highlighted = true) {
        PreviewLabel("SAVED PROGRAM · READ ONLY", emphasis = true)
        Text(program.name, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        val goal = program.goal.replaceFirstChar { it.uppercase() }
        Text(
            "${if (program.custom) "Custom" else "Generated"} · $goal · " +
                "${program.weeks} weeks · ${program.days.size} training days",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Training structure only. Weights, prescribed sets, RIR and changes " +
                "require the certified workout engine and current history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    program.days.forEachIndexed { index, day ->
        PursuitCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(day.label, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("${day.exerciseIds.size} exercises",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
