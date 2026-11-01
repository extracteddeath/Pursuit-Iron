package io.github.extracteddeath.pursuitiron

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.EngineStatus
import io.github.extracteddeath.pursuitiron.domain.UnconnectedEngineGateway

private enum class PreviewTab(val title: String, val icon: ImageVector) {
    Home("Today", Icons.Outlined.Home),
    Plan("Plan", Icons.Outlined.EventNote),
    Workout("Workout", Icons.Outlined.FitnessCenter),
    Progress("Progress", Icons.Outlined.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativePreviewApp() {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val status = UnconnectedEngineGateway().status

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("Pursuit Iron", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("NATIVE DESIGN PREVIEW", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                PreviewTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { contentPadding ->
        Crossfade(targetState = tabIndex, animationSpec = tween(240), label = "preview navigation") { index ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (PreviewTab.entries[index]) {
                    PreviewTab.Home -> HomePreview(onPlan = { tabIndex = 1 }, onWorkout = { tabIndex = 2 })
                    PreviewTab.Plan -> PlanPreview()
                    PreviewTab.Workout -> WorkoutPreview()
                    PreviewTab.Progress -> ProgressPreview()
                }
                BridgeStatus(status)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PreviewHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomePreview(onPlan: () -> Unit, onWorkout: () -> Unit) {
    PreviewHeading("Saturday · design preview", "Your training, reimagined",
        "Explore a native Material 3 layout. No live program or history is connected.")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(onClick = {}, label = { Text("Preview only") })
            Text("Today's workout", style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Your existing workouts will appear here once the engine and backup bridge are verified.",
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Button(onClick = onWorkout, modifier = Modifier.fillMaxWidth()) {
                Text("Explore workout layout")
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your training plan", style = MaterialTheme.typography.titleLarge)
            Text("Program structure, session priorities and phase context will share one consistent design system.")
            FilledTonalButton(onClick = onPlan) { Text("View plan preview") }
        }
    }
}

@Composable
private fun PlanPreview() {
    PreviewHeading("Training architecture", "Your plan",
        "This is a navigation and component preview, not an imported or generated program.")
    PreviewPanel("Foundation", "Canonical engine boundary", "Read-only handshake and catalog checks implemented.")
    PreviewPanel("Next milestone", "Training parity", "Double progression, available weight increments, RIR and cycles must match the web engine.")
    PreviewPanel("Data transfer", "Versioned backup import", "The canonical v13 parser is available; native import remains disabled.")
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text("Open full program · not connected")
    }
}

@Composable
private fun PreviewPanel(eyebrow: String, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WorkoutPreview() {
    PreviewHeading("Interactive design sample", "Workout",
        "The controls below use temporary sample values only. They never save sets or modify your training data.")
    PreviewSetControls()
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Workout session", style = MaterialTheme.typography.titleLarge)
            Text("Actual exercise selection, prescriptions, progression and logging require the verified canonical engine.")
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Start real session · engine not connected")
            }
        }
    }
}

@Composable
private fun PreviewSetControls() {
    var weight by rememberSaveable { mutableIntStateOf(185) }
    var reps by rememberSaveable { mutableIntStateOf(8) }
    var rir by rememberSaveable { mutableIntStateOf(2) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AssistChip(onClick = {}, label = { Text("DEMO · not a training suggestion") })
            Text("Barbell bench press", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Set 1 of 4 · sample exercise", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            SampleStepper("Weight", "$weight lb", onMinus = { weight = (weight - 5).coerceAtLeast(0) },
                onPlus = { weight += 5 })
            SampleStepper("Reps", reps.toString(), onMinus = { reps = (reps - 1).coerceAtLeast(0) },
                onPlus = { reps += 1 })
            SampleStepper("RIR", rir.toString(), onMinus = { rir = (rir - 1).coerceAtLeast(0) },
                onPlus = { rir = (rir + 1).coerceAtMost(10) })
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Complete set · disabled in preview")
            }
        }
    }
}

@Composable
private fun SampleStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        FilledTonalButton(onClick = onMinus, modifier = Modifier.widthIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−") }
        FilledTonalButton(onClick = onPlus, modifier = Modifier.widthIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+") }
    }
}

@Composable
private fun ProgressPreview() {
    PreviewHeading("Progress", "Built around your history",
        "Personal bests, consistency, volume and strength trends will appear after safe import.")
    PreviewPanel("Your lifetime records", "No data imported", "The current web records remain unchanged and are not available in this native preview.")
    PreviewPanel("Native progress", "Charts and milestones", "The UI will use the canonical revision-aware history view, not duplicates of old records.")
}

@Composable
private fun BridgeStatus(status: EngineStatus) {
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Engine connection", style = MaterialTheme.typography.titleSmall)
            Text(if (status is EngineStatus.NotConnected) "Not connected · training features disabled"
                else "Engine unavailable · training features disabled",
                style = MaterialTheme.typography.bodySmall)
            Text("No workout history is read, written, or synced by this preview.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
