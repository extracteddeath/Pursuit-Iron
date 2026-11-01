package io.github.extracteddeath.pursuitiron

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
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
            ReadOnlyTrainingDayRow(
                dayIndex = index,
                title = day.label,
                exerciseCount = day.exerciseIds.size
            )
        }
    }
}
