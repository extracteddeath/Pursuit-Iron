package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomResumeReview
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomDraftListResult
import io.github.extracteddeath.pursuitiron.rebuild.CustomCoachAuditInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Source-only KOTLIN-OWNED journey, intentionally not substituted for the
 * live app's NativeRuntimeHost or legacy-backed MainActivity screen.
 *
 * Mounted by the debug-only Kotlin Lab launcher. Shared controllers keep a single app-private source owner
 * while screen navigation preserves authored draft revisions.
 */
private enum class KotlinCandidateDestination(val label:String) {
    ONBOARD("My setup"),GYM("My gym"),STARTER("Build first"),PROGRAM("My program"),
    COACH("Coach"),FUTURE("Future weeks"),HISTORY("History"),SETTINGS("Settings"),TRAIN("Train")
}

@Composable
internal fun NativeKotlinCandidateJourney() {
    val context=LocalContext.current.applicationContext
    val host=remember(context){(context as PursuitIronApplication).kotlinCandidate}
    val gymController=remember(host){NativeKotlinGymSourceController(host)}
    val starterController=remember(host){NativeKotlinFirstProgramController(host)}
    val cycleController=remember(host){NativeKotlinCustomCycleController(host)}
    val sessionController=remember(host){NativeKotlinSessionController(host)}
    DisposableEffect(gymController,starterController,cycleController,sessionController) {
        onDispose {
            gymController.close()
            starterController.close()
            cycleController.close()
            sessionController.close()
        }
    }
    var destination by rememberSaveable {
        mutableStateOf(KotlinCandidateDestination.ONBOARD)
    }
    var selectedCustomWorkoutId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val savedScreens=rememberSaveableStateHolder()
    var pendingFirstProgramAudit by remember {
        mutableStateOf<Pair<String,CustomCoachAuditInputs>?>(null)
    }
    // Resume the owned native journey after process death; do not send a
    // returning lifter through onboarding again. No silent workout issuance
    // or journal opening: TRAIN independently revalidates saved receipts.
    var landingChecked by remember(host) { mutableStateOf(false) }
    var navigationTouched by remember { mutableStateOf(false) }
    LaunchedEffect(host) {
        if(!landingChecked) {
            val initial=withContext(Dispatchers.IO) {
                val unfinished=runCatching {
                    host.reviewResumableCustomWorkouts()
                }.getOrNull() as? NativeCustomResumeReview.Ready
                if(unfinished?.workouts?.isNotEmpty()==true)
                    KotlinCandidateDestination.TRAIN
                else {
                    val programs=runCatching {
                        host.listOwnedCustomCycleDrafts()
                    }.getOrNull() as? NativeCustomDraftListResult.Ready
                    if(programs?.drafts?.isNotEmpty()==true)
                        KotlinCandidateDestination.PROGRAM
                    else KotlinCandidateDestination.ONBOARD
                }
            }
            // Do not steal focus after a deliberate tab choice, or from a
            // restored workout currently open in the saved Android state.
            if(!navigationTouched &&
                destination==KotlinCandidateDestination.ONBOARD)
                destination=initial
            landingChecked=true
        }
    }
    LaunchedEffect(destination) {
        if(destination==KotlinCandidateDestination.PROGRAM) {
            val handoff=pendingFirstProgramAudit
            pendingFirstProgramAudit=null
            if(handoff!=null)
                cycleController.openSavedFirstProgramForReview(
                    handoff.first,handoff.second)
            else cycleController.openSavedTraining()
        }
    }
    var moreExpanded by remember { mutableStateOf(false) }
    fun navigate(to:KotlinCandidateDestination) {
        navigationTouched=true
        destination=to
    }
    // Four ordinary destinations stay within a thumb's reach. Setup,
    // generator, gym, and future-block tools remain in the explicit menu.
    // This is a real Compose navigation shell, not a new session owner.
    Scaffold(
        topBar={
            Surface(tonalElevation=1.dp) {
                Row(Modifier.fillMaxWidth()
                    .padding(horizontal=16.dp,vertical=6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pursuit Iron",style=MaterialTheme.typography.titleMedium)
                        Text(destination.label,
                            style=MaterialTheme.typography.labelMedium,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick={moreExpanded=true}) {
                            Icon(Icons.Filled.MoreVert,contentDescription="More screens")
                        }
                        DropdownMenu(expanded=moreExpanded,
                            onDismissRequest={moreExpanded=false}) {
                            val extras=listOf(
                                KotlinCandidateDestination.ONBOARD,
                                KotlinCandidateDestination.GYM,
                                KotlinCandidateDestination.STARTER,
                                KotlinCandidateDestination.FUTURE,
                                KotlinCandidateDestination.SETTINGS)
                            extras.forEach { page->
                                DropdownMenuItem(text={Text(page.label)},onClick={
                                    moreExpanded=false
                                    navigate(page)
                                })
                            }
                        }
                    }
                }
            }
        },
        bottomBar={
            NavigationBar {
                listOf(KotlinCandidateDestination.PROGRAM,
                    KotlinCandidateDestination.TRAIN,
                    KotlinCandidateDestination.HISTORY,
                    KotlinCandidateDestination.COACH).forEach {page->
                    val selected=destination==page ||
                        (page==KotlinCandidateDestination.PROGRAM &&
                            destination in listOf(
                                KotlinCandidateDestination.STARTER,
                                KotlinCandidateDestination.FUTURE)) ||
                        (page==KotlinCandidateDestination.COACH &&
                            destination==KotlinCandidateDestination.SETTINGS)
                    NavigationBarItem(
                        selected=selected,
                        onClick={navigate(page)},
                        icon={
                            when(page) {
                                KotlinCandidateDestination.PROGRAM ->
                                    Icon(Icons.Filled.Home,contentDescription=null)
                                KotlinCandidateDestination.TRAIN ->
                                    Icon(Icons.Filled.FitnessCenter,contentDescription=null)
                                KotlinCandidateDestination.HISTORY ->
                                    Icon(Icons.Filled.History,contentDescription=null)
                                else ->Icon(Icons.Filled.Person,contentDescription=null)
                            }
                        },
                        label={Text(when(page){
                            KotlinCandidateDestination.PROGRAM ->"Plan"
                            KotlinCandidateDestination.TRAIN ->"Train"
                            KotlinCandidateDestination.HISTORY ->"History"
                            else ->"Coach"
                        })}
                    )
                }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            savedScreens.SaveableStateProvider(destination.name) {
                when(destination) {
                    KotlinCandidateDestination.ONBOARD ->
                        NativeKotlinOnboardingCandidateScreen(gymController,
                            onContinue={destination=KotlinCandidateDestination.PROGRAM},
                            onBuildProgram={destination=KotlinCandidateDestination.STARTER})
                    KotlinCandidateDestination.GYM ->
                        NativeKotlinGymSourceCandidateScreen(gymController)
                    KotlinCandidateDestination.STARTER ->
                        NativeKotlinFirstProgramCandidateScreen(
                            starterController,
                            onOpenSavedProgram={cycleId,reviewInputs->
                                pendingFirstProgramAudit=cycleId to reviewInputs
                                destination=KotlinCandidateDestination.PROGRAM
                            })
                    KotlinCandidateDestination.PROGRAM ->
                        NativeKotlinCustomCycleCandidateScreen(cycleController,
                            onOpenIssuedWorkout={id->
                                selectedCustomWorkoutId=id
                                destination=KotlinCandidateDestination.TRAIN
                            },
                            onOpenHistory={
                                destination=KotlinCandidateDestination.HISTORY
                            },
                            onBuildProgram={destination=KotlinCandidateDestination.STARTER})
                    KotlinCandidateDestination.COACH ->
                        NativeKotlinCandidateCoachScreen(host,
                            onOpenSettings={destination=KotlinCandidateDestination.SETTINGS})
                    KotlinCandidateDestination.FUTURE ->
                        NativeKotlinFutureWeekCoachScreen(host,cycleController,
                            onOpenProgram={destination=KotlinCandidateDestination.PROGRAM},
                            onOpenIssuedWorkout={id->
                                selectedCustomWorkoutId=id
                                destination=KotlinCandidateDestination.TRAIN
                            })
                    KotlinCandidateDestination.SETTINGS ->
                        NativeKotlinCoachSettingsScreen(host,
                            onBack={destination=KotlinCandidateDestination.COACH})
                    KotlinCandidateDestination.HISTORY ->
                        NativeKotlinCandidateHistoryScreen(host,
                            onOpenProgram={destination=KotlinCandidateDestination.PROGRAM})
                    KotlinCandidateDestination.TRAIN ->
                        NativeKotlinCandidateTrainEntry(host,sessionController,
                            selectedCustomWorkoutId,
                            onOpenCustomWorkout={selectedCustomWorkoutId=it},
                            onViewFinishedHistory={
                                selectedCustomWorkoutId=null
                                destination=KotlinCandidateDestination.HISTORY
                            },
                            onReturnToProgram={
                                selectedCustomWorkoutId=null
                                destination=KotlinCandidateDestination.PROGRAM
                            })
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
    controller: NativeKotlinSessionController,
    customWorkoutId:String?,
    onOpenCustomWorkout:(String?)->Unit,
    onViewFinishedHistory:()->Unit,
    onReturnToProgram:()->Unit
) {
    var entryId by rememberSaveable { mutableStateOf("") }
    var resumable by remember(host) {
        mutableStateOf<NativeCustomResumeReview?>(null)
    }
    var reviewingResume by remember {mutableStateOf(false)}
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selectionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showIdEntry by rememberSaveable {mutableStateOf(false)}
    var showOtherWorkouts by rememberSaveable {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val valid = entryId.matches(
        Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$"))
    suspend fun refreshUnfinishedWorkouts() {
        reviewingResume=true
        resumable=null
        try {
            resumable=withContext(Dispatchers.IO) {
                runCatching {host.reviewResumableCustomWorkouts()}.getOrNull()
            }
        } finally {
            reviewingResume=false
        }
    }
    LaunchedEffect(host,customWorkoutId,selectedId) {
        // A past review is never an access grant after Finish or an athlete
        // switch. Reopen verified storage for the next explicit selection.
        resumable=null
        if(customWorkoutId==null && selectedId==null)
            refreshUnfinishedWorkouts()
    }
    if(customWorkoutId!=null) {
        NativeKotlinCustomJournalScreen(host,customWorkoutId,
            onClose={onOpenCustomWorkout(null)},
            onViewHistory=onViewFinishedHistory,
            onReturnToProgram=onReturnToProgram)
    } else if(selectedId == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PageHeading("KOTLIN COACH · ISOLATED",
                "Continue your training",
                "Resume your workout or open the next session in your saved program.")
            PursuitCard(highlighted=true) {
                Text("Your unfinished workouts",
                    style=MaterialTheme.typography.titleMedium)
                Text("Your saved sets and exercise targets are checked before you resume.",
                    style=MaterialTheme.typography.bodySmall)
                Button(enabled=!reviewingResume,onClick={
                    scope.launch {refreshUnfinishedWorkouts()}
                }) {
                    Text(if(reviewingResume)"Checking saved workouts…"
                        else "Refresh unfinished workouts")
                }
                when(val result=resumable) {
                    is NativeCustomResumeReview.Ready -> {
                        if(result.workouts.isEmpty()) {
                            Text("You have no unfinished workouts. Open your program to continue.",
                                style=MaterialTheme.typography.bodySmall)
                        } else {
                            Text("${result.workouts.size} unfinished workouts",
                                style=MaterialTheme.typography.labelMedium)
                            result.workouts.take(20).forEach { item->
                                OutlinedButton(
                                    modifier=Modifier.fillMaxWidth(),
                                    onClick={
                                        // The journal performs a separate
                                        // owner and disk validation on open.
                                        resumable=null
                                        onOpenCustomWorkout(item.workoutId)
                                    }) {
                                    Column(Modifier.fillMaxWidth(),
                                        verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                        Text(item.dayLabel,
                                            style=MaterialTheme.typography.titleSmall)
                                        Text("Week ${item.absoluteWeek} · ${item.unresolvedVisitCount} sets remaining",
                                            style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if(result.workouts.size>20)
                                Text("Showing the first 20 by authored week and day. ${result.workouts.size-20} others remain verified; more navigation is still needed.",
                                    style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    is NativeCustomResumeReview.Rejected ->
                        Text("Saved workouts could not be verified (${result.reason.name.replace('_',' ').lowercase()}). No workout opened. If Finish was interrupted, recover it from the native custom workout before retrying.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                    null -> if(!reviewingResume) Text(
                        "Saved workouts could not be read. Refresh to try again.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            PursuitCard {
                Text("Your training program",style=MaterialTheme.typography.titleMedium)
                Text("Review the next session and its exact exercises before starting.",
                    style=MaterialTheme.typography.bodySmall)
                PursuitPrimaryAction("Open my program",enabled=!reviewingResume) {
                    onReturnToProgram()
                }
            }
            TextButton(onClick={showOtherWorkouts=!showOtherWorkouts}) {
                Text(if(showOtherWorkouts)"Hide other workout tools"
                    else "Other approved workout tools")
            }
            if(showOtherWorkouts) PursuitCard {
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
                TextButton(onClick={showIdEntry=!showIdEntry}) {
                    Text(if(showIdEntry)"Hide workout ID entry" else "Open by workout ID")
                }
                if(showIdEntry) {
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
