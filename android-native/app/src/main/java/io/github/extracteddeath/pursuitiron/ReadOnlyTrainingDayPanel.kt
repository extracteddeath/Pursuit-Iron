package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyTrainingDayView
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramReference

/**
 * Real program structure, but intentionally NOT a generated workout. Exercise
 * IDs are canonical source IDs: don't guess names, working weights, RIR, sets
 * or training prescriptions. A certified engine will supply those later.
 */
@Composable
internal fun ReadOnlyTrainingDayPanel(
    day: ReadOnlyTrainingDayView,
    returnToPlan: () -> Unit,
    openWorkout: () -> Unit = {},
    reference: ReadOnlyProgramReference? = null,
    lookupNames: suspend (ReadOnlyProgramReference,List<String>)->Map<String,String>? = {_,_->null}
) {
    PageHeading("YOUR PROGRAM", "Training day details",
        "Review your saved exercises. The live workout starts from Train.")
    if (day is ReadOnlyTrainingDayView.Structure) {
        var names by remember(reference,day.exerciseIds) {
            mutableStateOf<Map<String,String>>(emptyMap())
        }
        LaunchedEffect(reference,day.exerciseIds) {
            names=if(reference==null || day.exerciseIds.isEmpty()) emptyMap()
            else try {
                lookupNames(reference,day.exerciseIds.distinct()).orEmpty()
            } catch(cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch(_: Exception) { emptyMap() }
        }
        PursuitCard(highlighted = true) {
            PreviewLabel("SAVED TRAINING DAY", emphasis = true)
            Text(day.programName, style = MaterialTheme.typography.titleMedium)
            Text(day.dayLabel, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(day.exerciseIds.size.toString() + " saved exercise slots",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Exercises", "${day.exerciseIds.size} movements")
        day.exerciseIds.forEachIndexed { index, exerciseId ->
            PursuitCard {
                ReadOnlyMovementIdentityRow(
                    ordinal = index,
                    canonicalExerciseId = exerciseId,
                    displayName = names[exerciseId]
                )
            }
        }
        Text("This is the saved day structure, not a session prescription. " +
            "Train opens the next scheduled workout with engine-issued sets and targets.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick=openWorkout,modifier=Modifier.fillMaxWidth()) {
            Text("Go to Train")
        }
    } else {
        PursuitCard {
            Text("This program day is unavailable",
                style = MaterialTheme.typography.titleMedium)
            Text("It may have changed, been deleted or failed current-revision verification. Choose a day from the verified Plan again.")
        }
    }
    FilledTonalButton(onClick = returnToPlan, modifier = Modifier.fillMaxWidth()) {
        Text("Back to program")
    }
}
