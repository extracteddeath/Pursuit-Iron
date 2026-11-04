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
    ONBOARD("My setup"),GYM("My gym"),PROGRAM("My program")
}

@Composable
internal fun NativeKotlinCandidateJourney() {
    val context=LocalContext.current.applicationContext
    val host=remember(context){NativeKotlinCandidateHost(context)}
    val gymController=remember(host){NativeKotlinGymSourceController(host)}
    val cycleController=remember(host){NativeKotlinCustomCycleController(host)}
    DisposableEffect(gymController,cycleController) {
        onDispose {
            gymController.close()
            cycleController.close()
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
                }
            }
        }
    }
}
