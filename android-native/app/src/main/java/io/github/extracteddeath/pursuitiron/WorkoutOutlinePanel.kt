package io.github.extracteddeath.pursuitiron

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentation
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentationPolicy
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutline

/**
 * Shared M3E presentation for audited read-only outline data. It is NOT
 * wired to the JS engine, training recommendations or stored user sessions.
 * In the installable native preview the supplied outline is always null.
 */
@Composable
internal fun WorkoutOutlinePanel(outline: WorkoutOutline?) {
    val presentation = OutlinePresentationPolicy.inspect(outline)
    var showLayoutExample by rememberSaveable { mutableStateOf(false) }

    SectionLabel("Workout outline", "Read-only foundation")
    when (presentation) {
        is OutlinePresentation.Unavailable -> {
            PursuitCard(highlighted = true) {
                PreviewLabel("NO VERIFIED PERSONAL PROGRAM", emphasis = true)
                Text("See the structure before the workout",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text(
                    "A verified plan will show the exact exercise order, working sets, " +
                        "rep and effort ranges, and rest. Your weights and history " +
                        "will never be invented by the interface.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { showLayoutExample = !showLayoutExample },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showLayoutExample) "Hide layout example" else "Explore layout example")
                }
            }
            AnimatedVisibility(visible = showLayoutExample) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PreviewLabel("ILLUSTRATIVE UI ONLY · NOT GENERATED OR SAVED")
                    OutlineExerciseRow(
                        index = 1, name = "Example primary movement",
                        role = "Example role", sets = "3 sets", reps = "5–8 reps",
                        rir = "2–3 RIR", rest = "180s rest"
                    )
                    OutlineExerciseRow(
                        index = 2, name = "Example accessory movement",
                        role = "Example role", sets = "2 sets", reps = "10–12 reps",
                        rir = "1–2 RIR", rest = "90s rest"
                    )
                    Text(
                        "These are layout placeholders, not engine outputs, " +
                            "personal recommendations, or completed sets.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        is OutlinePresentation.SyntheticOnly -> {
            val synthetic = presentation.outline
            PursuitCard(highlighted = true) {
                PreviewLabel("SYNTHETIC ENGINE TEST · NOT YOUR PROGRAM", emphasis = true)
                Text(
                    synthetic.day.replaceFirstChar { it.uppercase() } + " · " +
                        synthetic.exercises.size + " exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Estimated ${synthetic.estimatedMinutes.toInt()} min · " +
                        "maximum ${synthetic.maxMinutes.toInt()} min",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "This isolated test outline is read-only. It cannot prescribe " +
                        "working weights, log sets, or update a real program.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            synthetic.exercises.forEachIndexed { index, exercise ->
                OutlineExerciseRow(
                    index = index + 1,
                    name = exercise.name,
                    role = exercise.role.replace('_', ' '),
                    sets = "${exercise.sets} sets",
                    reps = "${exercise.repMin}–${exercise.repMax} reps",
                    rir = "${exercise.rirMin}–${exercise.rirMax} RIR",
                    rest = "${exercise.restSeconds.toInt()}s rest"
                )
            }
        }
    }
}

/** Shared layout for the placeholder and the independently validated DTO. */
@Composable
private fun OutlineExerciseRow(
    index: Int, name: String, role: String, sets: String,
    reps: String, rir: String, rest: String
) {
    PursuitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                index.toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(role, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("$sets · $reps · $rir",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium)
        Text(rest, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
