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

/**
 * Source-only KOTLIN-OWNED journey, intentionally not substituted for the
 * live app's NativeRuntimeHost or legacy-backed MainActivity screen.
 *
 * Can be mounted in an isolated native UI test or an explicitly gated R&D
 * launcher later. Shared controllers keep a single app-private source owner
 * while screen navigation preserves authored draft revisions.
 */
private enum class KotlinCandidateDestination(val label:String) {
    ONBOARD("My setup"),GYM("My gym"),PROGRAM("My program"),TRAIN("Train")
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
                    KotlinCandidateDestination.TRAIN ->
                        NativeKotlinCandidateTrainEntry(sessionController)
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
    controller: NativeKotlinSessionController
) {
    var entryId by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
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
                Text("Enter the workout ID from an already saved, owner-verified native program grant. This is a candidate testing flow, not the live workout screen.")
                OutlinedTextField(
                    value=entryId,
                    onValueChange={entryId=it},
                    singleLine=true,
                    label={Text("Approved native workout ID")},
                    isError=entryId.isNotBlank() && !valid,
                    modifier=Modifier.fillMaxWidth())
                PursuitPrimaryAction(
                    "Open native workout",enabled=valid
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
