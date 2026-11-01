package io.github.extracteddeath.pursuitiron

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentation
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentationPolicy
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutline
import io.github.extracteddeath.pursuitiron.domain.SyntheticSetCursor
import io.github.extracteddeath.pursuitiron.domain.SyntheticSetPosition
import io.github.extracteddeath.pursuitiron.domain.SyntheticSetReviewNavigation

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
            // Navigation state is not saveable history. A new canonical result
            // resets every review cursor without replaying completed sets.
            var cursor by remember(synthetic) { mutableStateOf(SyntheticSetCursor(0, 0)) }
            var showAll by remember(synthetic) { mutableStateOf(false) }
            val point = SyntheticSetReviewNavigation.inspect(presentation, cursor)
            PursuitCard(highlighted = true) {
                PreviewLabel("SYNTHETIC ENGINE TEST · NOT YOUR PROGRAM", emphasis = true)
                Text(
                    synthetic.day.replaceFirstChar { it.uppercase() } + " · " +
                        synthetic.exercises.size + " exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Estimated " + synthetic.estimatedMinutes.toInt() + " min · " +
                        "maximum " + synthetic.maxMinutes.toInt() + " min",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Review canonical exercise-level ranges, not personal or set-specific " +
                        "prescriptions. Loads, logging, and history are unavailable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (point != null) {
                SyntheticSetReviewPanel(
                    point = point,
                    onPrevious = SyntheticSetReviewNavigation.previous(presentation, cursor)
                        ?.let { previous -> { cursor = previous } },
                    onNext = SyntheticSetReviewNavigation.next(presentation, cursor)
                        ?.let { next -> { cursor = next } }
                )
            }
            OutlinedButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (showAll) "Hide exercise list" else "Browse all exercises") }
            AnimatedVisibility(visible = showAll) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    synthetic.exercises.forEachIndexed { index, exercise ->
                        OutlineExerciseRow(
                            index = index + 1,
                            name = exercise.name,
                            role = exercise.role.replace('_', ' '),
                            sets = exercise.sets.toString() + " sets",
                            reps = exercise.repMin.toString() + "–" + exercise.repMax + " reps",
                            rir = exercise.rirMin.toString() + "–" + exercise.rirMax + " RIR",
                            rest = exercise.restSeconds.toInt().toString() + "s rest",
                            onReview = {
                                SyntheticSetReviewNavigation.jumpToExercise(presentation,index)
                                    ?.let { cursor = it }
                                showAll = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Shared layout for the placeholder and the independently validated DTO. */
@Composable
private fun OutlineExerciseRow(
    index: Int, name: String, role: String, sets: String,
    reps: String, rir: String, rest: String,
    onReview: (() -> Unit)? = null
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
        if (onReview != null) {
            TextButton(onClick = onReview) { Text("Review these sets") }
        }
    }
}


/**
 * Single, reusable native set-review surface. The source is an approved
 * synthetic canonical exercise; it has NO personal working load or actual
 * completed-set state. The disabled completion action is intentional.
 */
@Composable
private fun SyntheticSetReviewPanel(
    point: SyntheticSetPosition,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    PursuitCard(highlighted = true) {
        PreviewLabel("CANONICAL SYNTHETIC SET · READ ONLY", emphasis = true)
        Text(point.exercise.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(
            "Exercise " + point.exerciseNumber + " of " + point.exerciseCount +
                " · Set " + point.setNumber + " of " + point.totalExerciseSets,
            style = MaterialTheme.typography.titleSmall
        )
        LinearProgressIndicator(
            progress = { point.overallSetNumber.toFloat() / point.totalWorkoutSets },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Workout set " + point.overallSetNumber + " of " + point.totalWorkoutSets,
            style = MaterialTheme.typography.labelMedium)
        Text("Target reps: " + point.exercise.repMin + "–" + point.exercise.repMax,
            style = MaterialTheme.typography.titleMedium)
        Text("Target RIR: " + point.exercise.rirMin + "–" + point.exercise.rirMax,
            style = MaterialTheme.typography.titleMedium)
        Text("Rest: " + point.exercise.restSeconds.toInt() + " seconds",
            style = MaterialTheme.typography.bodyMedium)
        Text(
            "These ranges apply to the exercise. Per-set loads and progression " +
                "require an athlete-specific certified engine request.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onPrevious?.invoke() },
                enabled = onPrevious != null,
                modifier = Modifier.weight(1f)
            ) { Text("Previous set") }
            FilledTonalButton(
                onClick = { onNext?.invoke() },
                enabled = onNext != null,
                modifier = Modifier.weight(1f)
            ) { Text("Next set") }
        }
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Complete set · personal engine not connected")
        }
    }
}
