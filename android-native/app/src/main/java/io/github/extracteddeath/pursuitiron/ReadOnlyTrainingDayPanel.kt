package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyTrainingDayView

/**
 * Real program structure, but intentionally NOT a generated workout. Exercise
 * IDs are canonical source IDs: don't guess names, working weights, RIR, sets
 * or training prescriptions. A certified engine will supply those later.
 */
@Composable
internal fun ReadOnlyTrainingDayPanel(
    day: ReadOnlyTrainingDayView,
    returnToPlan: () -> Unit
) {
    PageHeading("VERIFIED PLAN STRUCTURE", "Your selected day",
        "Program and exercise identity only; this is not a prescribed or loggable workout.")
    if (day is ReadOnlyTrainingDayView.Structure) {
        PursuitCard(highlighted = true) {
            PreviewLabel("CURRENT PROGRAM · READ ONLY", emphasis = true)
            Text(day.programName, style = MaterialTheme.typography.titleMedium)
            Text(day.dayLabel, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(day.exerciseIds.size.toString() + " saved exercise slots",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Saved movement IDs", "Not prescriptions")
        day.exerciseIds.forEachIndexed { index, exerciseId ->
            PursuitCard {
                ReadOnlyMovementIdentityRow(
                    ordinal = index,
                    canonicalExerciseId = exerciseId
                )
            }
        }
        PursuitCard {
            Text("Workout execution is not connected",
                style = MaterialTheme.typography.titleMedium)
            Text("The canonical engine must approve live sets, loads, RIR, rest and session recovery before a workout can start.")
        }
    } else {
        PursuitCard {
            Text("This program day is unavailable",
                style = MaterialTheme.typography.titleMedium)
            Text("It may have changed, been deleted or failed current-revision verification. Choose a day from the verified Plan again.")
        }
    }
    FilledTonalButton(onClick = returnToPlan, modifier = Modifier.fillMaxWidth()) {
        Text("Return to Plan")
    }
}
