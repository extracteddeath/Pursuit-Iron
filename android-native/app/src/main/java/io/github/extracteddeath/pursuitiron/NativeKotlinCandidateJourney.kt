package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Source-only KOTLIN-OWNED journey, intentionally not substituted for the
 * live app's NativeRuntimeHost or legacy-backed MainActivity screen.
 *
 * Can be mounted in an isolated native UI test or an explicitly gated R&D
 * launcher later. Shared controllers keep a single app-private source owner
 * while screen navigation preserves authored draft revisions.
 */
private enum class KotlinCandidateDestination(val label:String) {
    ONBOARD("My setup"),GYM("My gym"),PROGRAM("My program"),
    COACH("Coach"),FUTURE("Future weeks"),SETTINGS("Settings"),TRAIN("Train")
}

@Composable
internal fun NativeKotlinCandidateJourney() {
    val context=LocalContext.current.applicationContext
    val host=remember(context){NativeKotlinCandidateHost(context)}
    val gymController=remember(host){NativeKotlinGymSourceController(host)}
    val cycleController=remember(host){NativeKotlinCustomCycleController(host)}
    val sessionController=remember(host){NativeKotlinSessionController(host)}
    DisposableEffect(gymController,cycleController,sessionController) {
        onDispose {
            gymController.close()
            cycleController.close()
            sessionController.close()
        }
    }
    var destination by rememberSaveable {
        mutableStateOf(KotlinCandidateDestination.ONBOARD)
    }
    val savedScreens=rememberSaveableStateHolder()
    LaunchedEffect(destination) {
        if(destination==KotlinCandidateDestination.PROGRAM)
            cycleController.refresh()
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)
            .horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KotlinCandidateDestination.entries.forEach { page ->
                FilterChip(selected=destination==page,
                    onClick={destination=page},
                    label={Text(page.label)})
            }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxSize()) {
            savedScreens.SaveableStateProvider(destination.name) {
                when(destination) {
                    KotlinCandidateDestination.ONBOARD ->
                        NativeKotlinOnboardingCandidateScreen(gymController)
                    KotlinCandidateDestination.GYM ->
                        NativeKotlinGymSourceCandidateScreen(gymController)
                    KotlinCandidateDestination.PROGRAM ->
                        NativeKotlinCustomCycleCandidateScreen(cycleController)
                    KotlinCandidateDestination.COACH ->
                        NativeKotlinCandidateCoachScreen(host,
                            onOpenSettings={destination=KotlinCandidateDestination.SETTINGS})
                    KotlinCandidateDestination.FUTURE ->
                        NativeKotlinFutureWeekCoachScreen(host,cycleController,
                            onOpenProgram={destination=KotlinCandidateDestination.PROGRAM})
                    KotlinCandidateDestination.SETTINGS ->
                        NativeKotlinCoachSettingsScreen(host,
                            onBack={destination=KotlinCandidateDestination.COACH})
                    KotlinCandidateDestination.TRAIN ->
                        NativeKotlinCandidateTrainEntry(host,sessionController)
                }
            }
        }
    }
}


/**
 * Makes the existing complete Kotlin logger reachable in the ISOLATED
 * candidate journey. Merely entering an ID never issues or invents a session.
 * The native store requires the same owned, revision-bound approval grant
 * and history enrollment before "Start" can persist any workout.
 */
@Composable
private fun NativeKotlinCandidateTrainEntry(
    host: NativeKotlinCandidateHost,
    controller: NativeKotlinSessionController
) {
    var entryId by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selectionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    val valid = entryId.matches(
        Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$"))
    if(selectedId == null) {
        Column(Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PageHeading("KOTLIN COACH · ISOLATED",
                "Your native workout",
                "Only an explicitly approved Kotlin workout can be started.")
            PursuitCard(highlighted=true) {
                Text("Open an approved workout",
                    style=MaterialTheme.typography.titleMedium)
                Text("Open the workout from your saved native approval. The app checks the current athlete, program revision and grant before you can train.")
                PursuitPrimaryAction("Open my approved workout",
                    enabled=!searching) {
                    searching=true
                    selectionMessage=null
                    scope.launch {
                        try {
                            val found=withContext(Dispatchers.IO) {
                                host.currentlyApprovedKotlinWorkoutId()
                            }
                            if(found!=null) {
                                selectedId=found
                            } else {
                                selectionMessage="No current native generated-workout approval was found. Finish and save an approved Kotlin plan first."
                            }
                        } catch(_:Exception) {
                            selectionMessage="Native approval storage could not be read. No workout was started."
                        } finally {
                            searching=false
                        }
                    }
                }
                selectionMessage?.let {
                    Text(it,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text("Engineering fallback: enter a known native workout ID.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value=entryId,
                    onValueChange={entryId=it},
                    singleLine=true,
                    label={Text("Approved native workout ID")},
                    isError=entryId.isNotBlank() && !valid,
                    modifier=Modifier.fillMaxWidth())
                PursuitPrimaryAction(
                    "Open workout by ID",enabled=valid && !searching
                ) { selectedId=entryId }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Native workout · ${selectedId}",
                    style=MaterialTheme.typography.labelLarge)
                TextButton(onClick={
                    controller.clear()
                    selectedId=null
                }) { Text("Other workout") }
            }
            Box(Modifier.weight(1f)) {
                NativeKotlinWorkoutCandidateScreen(
                    controller,checkNotNull(selectedId))
            }
        }
    }
}
