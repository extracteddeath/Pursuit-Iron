package io.github.extracteddeath.pursuitiron

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.EngineStatus
import io.github.extracteddeath.pursuitiron.domain.FeatureArea
import io.github.extracteddeath.pursuitiron.domain.FeatureCatalog
import io.github.extracteddeath.pursuitiron.domain.UnconnectedEngineGateway
import io.github.extracteddeath.pursuitiron.domain.CurrentReadOnlyPlanSource
import io.github.extracteddeath.pursuitiron.domain.UnconnectedCurrentReadOnlyPlanSource
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramPresentation
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyTrainingDaySelection
import io.github.extracteddeath.pursuitiron.domain.NativeProgramProfile
import io.github.extracteddeath.pursuitiron.domain.JournalSetSlot
import io.github.extracteddeath.pursuitiron.domain.JournalSetDisposition
import io.github.extracteddeath.pursuitiron.domain.JournalSetValues
import androidx.compose.runtime.remember

/** Navigation follows training tasks instead of mirroring React screens. */
private enum class NativeDestination(val title: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Home),
    TRAIN("Train", Icons.Outlined.FitnessCenter),
    PLAN("Plan", Icons.Outlined.EventNote),
    INSIGHTS("Insights", Icons.Outlined.BarChart)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativePreviewApp(
    schemaVerifier: CanonicalBackupSchemaVerifier? = null,
    schemaVerifierLoading: Boolean = false,
    syntheticOutlineReader: CanonicalSyntheticOutlineReader? = null,
    // Neither an active program nor the current canonical store is connected.
    activeProgramId: String? = null,
    currentPlanSource: CurrentReadOnlyPlanSource = UnconnectedCurrentReadOnlyPlanSource,
    canonicalStoreChangeKey: Long = 0L,
    onboarding: NativeRuntimeState? = null,
    onReviewProgram: (NativeProgramProfile)->Unit = {},
    onSaveProgram: ()->Unit = {},
    onEditProgram: ()->Unit = {},
    onStartWorkout: ()->Unit = {},
    onRecordSet: (JournalSetSlot,JournalSetDisposition,JournalSetValues?)->Unit = {_,_,_->},
    onFinishWorkout: ()->Unit = {}
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableIntStateOf(0) }
    var readiness by rememberSaveable { mutableStateOf("Normal") }
    var length by rememberSaveable { mutableIntStateOf(0) }
    var injuryFlag by rememberSaveable { mutableStateOf(false) }
    val status = UnconnectedEngineGateway().status
    val destinationStates = rememberSaveableStateHolder()
    var exploreDemo by rememberSaveable { mutableStateOf(false) }
    if (onboarding!=null && onboarding.saved==null && !exploreDemo) {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                destinationStates.SaveableStateProvider("onboarding") {
                    NativeOnboardingScreen(onboarding,onReviewProgram,onSaveProgram,onEditProgram,
                        onExplore={exploreDemo=true})
                }
            }
        }
        return
    }
    // Program/day navigation intent is transient, never saved or interpreted
    // as a workout session, set log or persistence permission.
    var selectedTrainingDay by remember { mutableStateOf<ReadOnlyTrainingDaySelection?>(null) }
    // Changing destinations clears any previously approved personal Plan view.
    val currentPlan = rememberNativeCurrentPlanPresentation(
        selectedProgramId = if (section == NativeDestination.PLAN.ordinal)
            activeProgramId
        else if (section == NativeDestination.TRAIN.ordinal &&
            selectedTrainingDay?.reference?.programId == activeProgramId)
            activeProgramId else null,
        source = currentPlanSource,
        storeChangeKey = canonicalStoreChangeKey
    )
    // Crossfade retains the outgoing destination temporarily. Never carry its
    // personal Plan structure beyond the selected destination.
    val visiblePlan = if (section == NativeDestination.PLAN.ordinal ||
        (section == NativeDestination.TRAIN.ordinal && selectedTrainingDay != null))
        currentPlan else ReadOnlyProgramPresentation.Unavailable

    BoxWithConstraints {
        val expanded = maxWidth >= 840.dp
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Pursuit Iron", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text("NATIVE EXPERIENCE PREVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { sheet = 2 }) {
                            Icon(Icons.Outlined.Apps, contentDescription = "Explore retained features")
                        }
                    }
                )
            },
            bottomBar = {
                if (!expanded) NavigationBar {
                    NativeDestination.entries.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = section == index,
                            onClick = {
                                selectedTrainingDay = null
                                section = index
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (expanded) NavigationRail {
                    NativeDestination.entries.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = section == index,
                            onClick = {
                                selectedTrainingDay = null
                                section = index
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                            alwaysShowLabel = true
                        )
                    }
                }
                Crossfade(
                    targetState = section, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    modifier = Modifier.weight(1f), label = "training destination"
                ) { current ->
                    destinationStates.SaveableStateProvider(current) {
                        Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp)
                                .fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (NativeDestination.entries[current]) {
                                NativeDestination.TODAY -> TodayScreen(
                                    readiness, length, injuryFlag,
                                    openTrain = { selectedTrainingDay = null; section = 1 },
                                    openPlan = { selectedTrainingDay = null; section = 2 },
                                    adjustSession = { sheet = 1 },
                                    savedProgram=onboarding?.saved,
                                    workoutState=onboarding,
                                    startWorkout=onStartWorkout,
                                    setupProgram={exploreDemo=false})
                                NativeDestination.TRAIN -> if(onboarding?.saved!=null && selectedTrainingDay==null)
                                    NativeWorkoutScreen(onboarding,onStartWorkout,onRecordSet,onFinishWorkout)
                                else TrainScreen(
                                    openTools = { sheet = 3 },
                                    syntheticOutlineReader = syntheticOutlineReader,
                                    selectedDay = selectedTrainingDay,
                                    currentPlan = visiblePlan,
                                    returnToPlan = { selectedTrainingDay = null; section = 2 }
                                )
                                NativeDestination.PLAN -> PlanScreen(
                                    currentPlan = visiblePlan,
                                    hasSavedProgram=onboarding?.saved!=null,
                                    schemaVerifier = schemaVerifier,
                                    schemaVerifierLoading = schemaVerifierLoading,
                                    onPreviewDay = { day ->
                                        selectedTrainingDay = day
                                        section = NativeDestination.TRAIN.ordinal
                                    }
                                )
                                NativeDestination.INSIGHTS -> InsightsScreen(onboarding)
                            }
                            if(onboarding?.saved==null)ConnectionNotice(status,false)
                        }
                    }
                    }
                }
            }
        }
        if (sheet != 0) ModalBottomSheet(onDismissRequest = { sheet = 0 }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (sheet) {
                    1 -> SessionAdjustmentSheet(
                        readiness, length, injuryFlag,
                        chooseReadiness = { readiness = it },
                        chooseLength = { length = it },
                        toggleInjury = { injuryFlag = it })
                    2 -> FeatureOverviewSheet()
                    3 -> ExerciseActionsSheet()
                }
                FilledTonalButton(onClick = { sheet = 0 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ConnectionNotice(status: EngineStatus,hasProgram: Boolean=false) {
    PursuitCard {
        Text("Preview status", style = MaterialTheme.typography.titleSmall)
        Text(if(hasProgram) "Your program is saved on this device. Workout logging is unavailable in this preview."
            else if (status is EngineStatus.NotConnected)
            "Canonical training engine not connected. No program is generated and no workout data is saved."
            else "Training engine unavailable. Real workout actions remain disabled.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SessionAdjustmentSheet(
    readiness: String, length: Int, injuryFlag: Boolean,
    chooseReadiness: (String) -> Unit,
    chooseLength: (Int) -> Unit,
    toggleInjury: (Boolean) -> Unit
) {
    PageHeading("TODAY'S TRAINING", "Adjust your session",
        "These are temporary design selections—not applied training modifications.")
    Text("How are you feeling?", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Low", "Normal", "Strong").forEach { option ->
            PreviewChoice(option, readiness == option) { chooseReadiness(option) }
        }
    }
    Text("Available time", style = MaterialTheme.typography.titleMedium)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 45, 30, 20).forEach { duration ->
            PreviewChoice(if (duration == 0) "Full" else duration.toString() + " min",
                length == duration) { chooseLength(duration) }
        }
    }
    Text("Training limitations", style = MaterialTheme.typography.titleMedium)
    PreviewChoice("Show limitation reminder (demo)", injuryFlag) { toggleInjury(!injuryFlag) }
    Text("In the real app, readiness, injury restrictions and time trimming will call canonical engine policies and preserve user-owned loads.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FeatureOverviewSheet() {
    PageHeading("CAPABILITY CONTRACT", "Features, not screens",
        "These features must survive the rebuild. Listing them does not mean they are implemented.")
    FeatureArea.entries.forEach { area ->
        Text(area.label, style = MaterialTheme.typography.titleLarge)
        FeatureCatalog.capabilities.filter { it.area == area }.forEach { feature ->
            PursuitCard {
                Text(feature.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(feature.outcome, style = MaterialTheme.typography.bodyMedium)
                Text("Awaiting: " + feature.gates.joinToString(" · ") {
                    when (it) {
                        io.github.extracteddeath.pursuitiron.domain.FeatureGate.CANONICAL_ENGINE -> "Engine parity"
                        io.github.extracteddeath.pursuitiron.domain.FeatureGate.VERIFIED_STORAGE -> "Data safety"
                        io.github.extracteddeath.pursuitiron.domain.FeatureGate.ANDROID_DEVICE -> "Device QA"
                    }
                }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseActionsSheet() {
    PageHeading("CONTEXTUAL TOOLS", "Everything for this exercise",
        "One contextual entry point instead of scattering controls across the workout screen.")
    listOf(
        "Previous sets and history" to "Read canonical history after verification",
        "Swap exercise" to "Equipment-aware alternatives and stable movement IDs",
        "Plates and bar weight" to "Available increments, machine settings and loading",
        "Warm-ups, supersets, amplifiers" to "Session controls with canonical rest policies",
        "Notes and technique" to "Session and pinned notes; variants and attachments"
    ).forEach { (title, detail) ->
        PursuitCard {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text("Use real exercise tools · engine not connected")
    }
}
