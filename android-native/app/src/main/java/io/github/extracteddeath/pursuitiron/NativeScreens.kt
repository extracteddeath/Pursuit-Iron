package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * These are independently designed NATIVE journeys, not ports of React screens.
 * Preview state is local UI state only. Nothing here calls a prescription or data API.
 */
@Composable
internal fun TodayScreen(
    readiness: String,
    length: Int,
    injuryFlag: Boolean,
    openTrain: () -> Unit,
    openPlan: () -> Unit,
    adjustSession: () -> Unit
) {
    PageHeading("YOUR TRAINING", "Make today count.",
        "A clear decision point for training, not another dashboard full of banners.")
    PursuitCard(highlighted = true) {
        PreviewLabel("NATIVE EXPERIENCE DEMO", emphasis = true)
        Text("A better start to every session", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Your next workout, priorities and available time will come from your verified program. Explore the training interaction design now.")
        Button(onClick = openTrain, modifier = Modifier.fillMaxWidth()) {
            Text("Explore the training experience")
        }
    }
    SectionLabel("Adapt to today", "Session-only")
    PursuitCard {
        Text("Readiness", style = MaterialTheme.typography.titleMedium)
        Text("Preview selection: " + readiness,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Time: " + (if (length == 0) "Full session" else length.toString() + " minutes") +
            (if (injuryFlag) " · limitation flagged (demo)" else ""),
            style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = adjustSession, modifier = Modifier.fillMaxWidth()) {
            Text("Adjust today's session")
        }
        Text("The actual engine—not the UI—will decide safe load and session changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionLabel("Your training system")
    PursuitCard {
        Text("Program at a glance", style = MaterialTheme.typography.titleLarge)
        Text("Goals, exercise selection, weekly structure and cycle changes belong in one cohesive planning experience.")
        OutlinedButton(onClick = openPlan) { Text("Explore the new Plan") }
    }
}

private val demoExercises = listOf("BB bench press", "Chest-supported row", "Romanian deadlift")

@Composable
internal fun TrainScreen(openTools: () -> Unit) {
    var exerciseIndex by rememberSaveable { mutableIntStateOf(0) }
    var weight by rememberSaveable { mutableIntStateOf(185) }
    var reps by rememberSaveable { mutableIntStateOf(8) }
    var rir by rememberSaveable { mutableIntStateOf(2) }
    var focus by rememberSaveable { mutableStateOf(false) }
    var reference by rememberSaveable { mutableStateOf("Last") }
    var rest by rememberSaveable { mutableIntStateOf(90) }
    var note by rememberSaveable { mutableStateOf("") }

    PageHeading("TRAINING WORKSPACE", "One set at a time.",
        "Explore the interaction design with sample numbers. Nothing is prescribed, logged or synced.")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        PreviewLabel("DEMO · NO LIVE DATA", emphasis = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Focus", style = MaterialTheme.typography.labelLarge)
            Switch(checked = focus, onCheckedChange = { focus = it },
                modifier = Modifier.padding(start = 10.dp))
        }
    }
    if (!focus) {
        SectionLabel("Exercise", "Select demo")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            demoExercises.forEachIndexed { index, label ->
                PreviewChoice(label, exerciseIndex == index) {
                    exerciseIndex = index
                    weight = listOf(185, 120, 185)[index]
                    reps = 8
                    rir = 2
                }
            }
        }
    }
    PursuitCard(highlighted = true) {
        PreviewLabel("WORKING SET · SAMPLE")
        Text(demoExercises[exerciseIndex], style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Set 1 of 4", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Last", "Target").forEach { choice ->
                PreviewChoice(choice, reference == choice) { reference = choice }
            }
        }
        Text(
            if (reference == "Last") "Sample last: 185 lb × 8 · 2 RIR"
            else "Sample target: 6–10 reps · 2 RIR",
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider()
        PreviewStepper("Weight", weight.toString() + " lb",
            subtract = { weight = (weight - 5).coerceAtLeast(0) },
            add = { weight += 5 })
        PreviewStepper("Reps", reps.toString(),
            subtract = { reps = (reps - 1).coerceAtLeast(0) },
            add = { reps += 1 })
        PreviewStepper("RIR", rir.toString(),
            subtract = { rir = (rir - 1).coerceAtLeast(0) },
            add = { rir = (rir + 1).coerceAtMost(10) })
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Complete set · engine not connected")
        }
    }
    SectionLabel("Session tools")
    PursuitCard {
        Text("Rest between sets", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120).forEach { value ->
                PreviewChoice(value.toString() + "s", rest == value) { rest = value }
            }
        }
        Text("Timer selection is a preview; no background timer is started.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = openTools, modifier = Modifier.fillMaxWidth()) {
            Text("Exercise actions and tools")
        }
    }
    if (!focus) {
        OutlinedTextField(value = note, onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Session note · temporary demo") },
            supportingText = { Text("This note exists only in the preview UI; it is not workout history.") },
            maxLines = 3)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun PlanScreen() {
    var goal by rememberSaveable { mutableStateOf("Hypertrophy") }
    var days by rememberSaveable { mutableIntStateOf(4) }
    var adaptiveSplit by rememberSaveable { mutableStateOf(true) }

    PageHeading("PROGRAM DESIGN", "Built around your goals.",
        "A native planning journey can replace several old editing screens without losing their controls.")
    PursuitCard(highlighted = true) {
        PreviewLabel("PROGRAM DESIGN DEMO", emphasis = true)
        Text("Make the plan yours", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("These selections are local UI examples. A future program will be generated only by the canonical engine.")
        Text("Primary goal", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hypertrophy", "Strength", "Powerbuilding").forEach { option ->
                PreviewChoice(option, option == goal) { goal = option }
            }
        }
        HorizontalDivider()
        Text("Days per week", style = MaterialTheme.typography.titleMedium)
        PreviewStepper("Training frequency", days.toString() + " days",
            subtract = { days = (days - 1).coerceAtLeast(3) },
            add = { days = (days + 1).coerceAtMost(6) })
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Adapt the training split", style = MaterialTheme.typography.titleSmall)
                Text("Future cycles may change frequency when authorized.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = adaptiveSplit, onCheckedChange = { adaptiveSplit = it })
        }
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Generate real program · unavailable")
        }
    }
    SectionLabel("Plan essentials")
    PursuitCard {
        Text("Equipment and exercise choice", style = MaterialTheme.typography.titleMedium)
        Text("Gym inventory, increments, bar weights, allowed movements, focus/reduced muscles and exercise variants stay first-class.")
    }
    PursuitCard {
        Text("Adaptive cycles", style = MaterialTheme.typography.titleMedium)
        Text("Hypertrophy → Strength → Peak, plus custom program support and user-approved split/exercise adaptation.")
    }
    PursuitCard {
        Text("Schedule and volume", style = MaterialTheme.typography.titleMedium)
        Text("Days, hard/long sessions, MEV/MAV/MRV guardrails, session length and safe exercise ordering are preserved as behavior.")
    }
}

@Composable
internal fun InsightsScreen() {
    PageHeading("YOUR RECORD", "Progress with context.",
        "No fabricated personal bests or charts. Your history will appear after safe import and revision verification.")
    PursuitCard(highlighted = true) {
        PreviewLabel("EMPTY STATE · LIVE DATA NOT CONNECTED", emphasis = true)
        Text("Your work deserves accurate credit.", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("The native app will use edited, canonical history rather than duplicate records or estimated PRs.")
    }
    SectionLabel("Training intelligence")
    PursuitCard {
        Text("Strength and personal records", style = MaterialTheme.typography.titleMedium)
        Text("PR confirmation, last session comparisons, lift trends, strength standards and milestone cards.")
    }
    PursuitCard {
        Text("Volume and consistency", style = MaterialTheme.typography.titleMedium)
        Text("Week-to-week training, per-muscle volume, biceps/brachialis and separate forearm flexion/extension accounting.")
    }
    PursuitCard {
        Text("History you can trust", style = MaterialTheme.typography.titleMedium)
        Text("Calendar, corrections, revision-aware analytics, bodyweight history and validated backup/export.")
    }
}
