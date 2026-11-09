package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
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
    COACH("Coach"),FUTURE("Future weeks"),HISTORY("History"),PROGRESS("Progress"),PROFILE("Profile"),SETTINGS("Settings"),TRAIN("Train")
}

private val primaryDestinations=listOf(
    KotlinCandidateDestination.PROGRAM,KotlinCandidateDestination.TRAIN,
    KotlinCandidateDestination.HISTORY,KotlinCandidateDestination.PROGRESS,
    KotlinCandidateDestination.COACH
)

private fun candidateTabLabel(page:KotlinCandidateDestination):String=when(page) {
    KotlinCandidateDestination.PROGRAM -> "Plan"
    KotlinCandidateDestination.TRAIN -> "Train"
    KotlinCandidateDestination.HISTORY -> "History"
    KotlinCandidateDestination.PROGRESS -> "Progress"
    else -> "Coach"
}

private fun candidateTabSelected(
    destination:KotlinCandidateDestination,page:KotlinCandidateDestination
):Boolean = destination==page ||
    page==KotlinCandidateDestination.PROGRAM &&
        destination in listOf(KotlinCandidateDestination.STARTER,
            KotlinCandidateDestination.FUTURE) ||
    page==KotlinCandidateDestination.COACH &&
        destination==KotlinCandidateDestination.SETTINGS

@Composable
private fun CandidateTabIcon(page:KotlinCandidateDestination) {
    when(page) {
        KotlinCandidateDestination.PROGRAM ->
            Icon(Icons.Filled.Home,contentDescription=null)
        KotlinCandidateDestination.TRAIN ->
            Icon(Icons.Filled.FitnessCenter,contentDescription=null)
        KotlinCandidateDestination.HISTORY ->
            Icon(Icons.Filled.History,contentDescription=null)
        KotlinCandidateDestination.PROGRESS ->
            Icon(Icons.Filled.Insights,contentDescription=null)
        else -> Icon(Icons.Filled.Person,contentDescription=null)
    }
}

@Composable
internal fun NativeKotlinCandidateJourney(
    themeId:String=PursuitThemes.DEFAULT_ID,
    onThemeSelected:(String)->Unit={}
) {
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
    var selectedGeneratedWorkoutId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var coachRequestedExerciseId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var progressRequestedExerciseId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var progressRequestedTrendExerciseId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var progressReturn by rememberSaveable {
        mutableStateOf(KotlinCandidateDestination.HISTORY)
    }
    val savedScreens=rememberSaveableStateHolder()
    var pendingFirstProgramAudit by remember {
        mutableStateOf<Pair<String,CustomCoachAuditInputs>?>(null)
    }
    // A workout swap is NEVER program consent. Keep an explicit read-only
    // handoff until the athlete reviews the exact saved cycle/day/slot.
    var pendingProgramSwapReview by remember {
        mutableStateOf<NativeKotlinProgramSwapHandoff?>(null)
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
            else if(pendingProgramSwapReview!=null)
                cycleController.open(checkNotNull(pendingProgramSwapReview).cycleId)
            else cycleController.openSavedTraining()
        }
    }
    var moreExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var trainScreenExpanded by remember { mutableStateOf(false) }
    var profileReturn by rememberSaveable {
        mutableStateOf(KotlinCandidateDestination.PROGRAM)
    }
    var settingsReturn by rememberSaveable {
        mutableStateOf(KotlinCandidateDestination.COACH)
    }
    fun navigate(to:KotlinCandidateDestination) {
        navigationTouched=true
        if(to==KotlinCandidateDestination.PROFILE &&
            destination!=KotlinCandidateDestination.PROFILE)
            profileReturn=destination
        if(to==KotlinCandidateDestination.SETTINGS &&
            destination!=KotlinCandidateDestination.SETTINGS)
            settingsReturn=destination
        if(to==KotlinCandidateDestination.COACH)
            coachRequestedExerciseId=null // ordinary tab, not a saved-set handoff
        if(to==KotlinCandidateDestination.PROGRESS) {
            progressRequestedExerciseId=null // ordinary tab, not a pinned lift
            progressRequestedTrendExerciseId=null
        }
        destination=to
    }
    // Physical Back follows the visible detail route, not the app launcher.
    // Never intercept an active workout: its independent save/minimize and
    // durable-journal recovery controls remain the only workout owners.
    BackHandler(enabled=destination==KotlinCandidateDestination.PROFILE ||
        destination==KotlinCandidateDestination.SETTINGS ||
        (destination==KotlinCandidateDestination.PROGRESS &&
            (progressRequestedExerciseId!=null ||
                progressRequestedTrendExerciseId!=null))) {
        when(destination) {
            KotlinCandidateDestination.PROFILE -> destination=profileReturn
            KotlinCandidateDestination.SETTINGS -> destination=settingsReturn
            KotlinCandidateDestination.PROGRESS -> {
                progressRequestedExerciseId=null
                progressRequestedTrendExerciseId=null
                destination=progressReturn
            }
            else -> Unit
        }
    }
    // Five ordinary destinations stay within a thumb's reach. Setup,
    // generator, gym, and future-block tools remain in the explicit menu.
    // This is a real Compose navigation shell, not a new session owner.
    // Adaptive Material navigation: bottom destinations on phones,
    // accessible rail on tablet/foldable widths; no global chrome over sets.
    val activeWorkout=destination==KotlinCandidateDestination.TRAIN &&
        (selectedCustomWorkoutId!=null || selectedGeneratedWorkoutId!=null ||
            trainScreenExpanded)
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide=maxWidth>=600.dp
    Scaffold(
        topBar={
            if(!activeWorkout)Surface(tonalElevation=1.dp) {
                Row(Modifier.fillMaxWidth()
                    .padding(horizontal=16.dp,vertical=6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pursuit Iron",style=MaterialTheme.typography.titleMedium)
                        Text(destination.label,
                            style=MaterialTheme.typography.labelMedium,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(destination!=KotlinCandidateDestination.PROFILE) {
                        IconButton(onClick={navigate(KotlinCandidateDestination.PROFILE)}) {
                            Icon(Icons.Filled.Person,
                                contentDescription="Open athlete profile")
                        }
                    }
                    Box {
                        IconButton(onClick={moreExpanded=true},
                            enabled=destination!=KotlinCandidateDestination.TRAIN ||
                                selectedCustomWorkoutId==null) {
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
                            DropdownMenuItem(text={Text("Appearance")},onClick={
                                moreExpanded=false
                                appearanceExpanded=true
                            })
                        }
                    }
                }
            }
        },
        bottomBar={
            if(!wide && !activeWorkout)NavigationBar {
                primaryDestinations.forEach { page ->
                    NavigationBarItem(
                        selected=candidateTabSelected(destination,page),
                        onClick={navigate(page)},
                        icon={CandidateTabIcon(page)},
                        label={Text(candidateTabLabel(page))}
                    )
                }
            }
        }
    ) { insets ->
        Row(Modifier.fillMaxSize().padding(insets)) {
            if(wide && !activeWorkout)NavigationRail {
                primaryDestinations.forEach { page ->
                    NavigationRailItem(
                        selected=candidateTabSelected(destination,page),
                        onClick={navigate(page)},
                        icon={CandidateTabIcon(page)},
                        label={Text(candidateTabLabel(page))}
                    )
                }
            }
            Box(Modifier.weight(1f)) {
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
                            programSwapReview=pendingProgramSwapReview,
                            onProgramSwapReviewDismiss={
                                pendingProgramSwapReview=null
                            },
                            onOpenIssuedWorkout={id->
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=id
                                destination=KotlinCandidateDestination.TRAIN
                            },
                            onOpenHistory={
                                destination=KotlinCandidateDestination.HISTORY
                            },
                            onBuildProgram={destination=KotlinCandidateDestination.STARTER},
                            permissionHost=host)
                    KotlinCandidateDestination.COACH ->
                        NativeKotlinCandidateCoachScreen(host,
                            preferredExerciseId=coachRequestedExerciseId,
                            onOpenSettings={navigate(KotlinCandidateDestination.SETTINGS)},
                            onOpenProgram={navigate(KotlinCandidateDestination.PROGRAM)},
                            onOpenFuture={navigate(KotlinCandidateDestination.FUTURE)},
                            onOpenHistory={navigate(KotlinCandidateDestination.HISTORY)},
                            onOpenTrain={id->
                                selectedGeneratedWorkoutId=id
                                selectedCustomWorkoutId=null
                                destination=KotlinCandidateDestination.TRAIN
                            })
                    KotlinCandidateDestination.FUTURE ->
                        NativeKotlinFutureWeekCoachScreen(host,cycleController,
                            onOpenProgram={destination=KotlinCandidateDestination.PROGRAM},
                            onOpenIssuedWorkout={id->
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=id
                                destination=KotlinCandidateDestination.TRAIN
                            })
                    KotlinCandidateDestination.SETTINGS ->
                        NativeKotlinCoachSettingsScreen(host,
                            themeId=themeId,
                            onOpenAppearance={appearanceExpanded=true},
                            onBack={destination=settingsReturn})
                    KotlinCandidateDestination.PROFILE ->
                        NativeKotlinProfileScreen(host,themeId,
                            onBack={destination=profileReturn},
                            onPlan={navigate(KotlinCandidateDestination.PROGRAM)},
                            onProgress={navigate(KotlinCandidateDestination.PROGRESS)},
                            onProgressLift={exerciseId->
                                // Profile emits only source-matched barbell
                                // favorites or verified logged exercise pins.
                                // Progress independently verifies general
                                // exercise IDs against the current owner history.
                                if(exerciseId.isNotBlank()) {
                                    progressRequestedExerciseId=exerciseId
                                    progressRequestedTrendExerciseId=null
                                    progressReturn=KotlinCandidateDestination.PROFILE
                                    destination=KotlinCandidateDestination.PROGRESS
                                }
                            },
                            onGym={navigate(KotlinCandidateDestination.GYM)},
                            onSettings={navigate(KotlinCandidateDestination.SETTINGS)},
                            onAppearance={appearanceExpanded=true})
                    KotlinCandidateDestination.PROGRESS ->
                        NativeKotlinStrengthStandardsScreen(host,
                            preferredExerciseId=progressRequestedExerciseId,
                            preferredTrendExerciseId=progressRequestedTrendExerciseId)
                    KotlinCandidateDestination.HISTORY ->
                        NativeKotlinCandidateHistoryScreen(host,
                            onOpenProgram={destination=KotlinCandidateDestination.PROGRAM},
                            onOpenCoach={exerciseId->
                                coachRequestedExerciseId=exerciseId
                                destination=KotlinCandidateDestination.COACH
                            },
                            onOpenProgress={
                                progressRequestedExerciseId=null
                                progressRequestedTrendExerciseId=null
                                destination=KotlinCandidateDestination.PROGRESS
                            },
                            onOpenExerciseTrend={exerciseId->
                                progressRequestedExerciseId=null
                                progressRequestedTrendExerciseId=exerciseId
                                progressReturn=KotlinCandidateDestination.HISTORY
                                destination=KotlinCandidateDestination.PROGRESS
                            })
                    KotlinCandidateDestination.TRAIN ->
                        NativeKotlinCandidateTrainEntry(host,sessionController,
                            selectedCustomWorkoutId,selectedGeneratedWorkoutId,
                            onOpenCustomWorkout={
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=it
                            },
                            onClearGeneratedWorkout={selectedGeneratedWorkoutId=null},
                            onActiveWorkoutChanged={trainScreenExpanded=it},
                            onViewFinishedHistory={
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=null
                                destination=KotlinCandidateDestination.HISTORY
                            },
                            onReviewNext={exerciseId->
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=null
                                coachRequestedExerciseId=exerciseId
                                destination=KotlinCandidateDestination.COACH
                            },
                            onReturnToProgram={
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=null
                                destination=KotlinCandidateDestination.PROGRAM
                            },
                            onReviewSwapInProgram={request->
                                selectedGeneratedWorkoutId=null
                                selectedCustomWorkoutId=null
                                pendingProgramSwapReview=request
                                destination=KotlinCandidateDestination.PROGRAM
                            })
                }
            }
            }
        }
    }
    }
    if(appearanceExpanded) {
        NativeAppearanceSheet(
            selectedId=themeId,
            onSelect=onThemeSelected,
            onDismiss={appearanceExpanded=false}
        )
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
    generatedWorkoutId:String?,
    onOpenCustomWorkout:(String?)->Unit,
    onClearGeneratedWorkout:()->Unit,
    onActiveWorkoutChanged:(Boolean)->Unit,
    onViewFinishedHistory:()->Unit,
    onReviewNext:(String)->Unit,
    onReturnToProgram:()->Unit,
    onReviewSwapInProgram:(NativeKotlinProgramSwapHandoff)->Unit
) {
    var entryId by rememberSaveable { mutableStateOf("") }
    var resumable by remember(host) {
        mutableStateOf<NativeCustomResumeReview?>(null)
    }
    var reviewingResume by remember {mutableStateOf(false)}
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(customWorkoutId,selectedId) {
        onActiveWorkoutChanged(customWorkoutId!=null || selectedId!=null)
    }
    LaunchedEffect(generatedWorkoutId) {
        if(generatedWorkoutId!=null && selectedId!=generatedWorkoutId)
            selectedId=generatedWorkoutId
    }
    var searching by remember { mutableStateOf(false) }
    var selectionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showIdEntry by rememberSaveable {mutableStateOf(false)}
    var showOtherWorkouts by rememberSaveable {mutableStateOf(false)}
    // Minimize removes the logger from composition. Preserve its timer and
    // unsubmitted fields per exact workout, then revalidate native storage
    // when reopening. Another workout must never inherit that UI state.
    val workoutScreens=rememberSaveableStateHolder()
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
        workoutScreens.SaveableStateProvider("custom:$customWorkoutId") {
        NativeKotlinCustomJournalScreen(host,customWorkoutId,
            onClose={onOpenCustomWorkout(null)},
            onViewHistory=onViewFinishedHistory,
            onReturnToProgram=onReturnToProgram,
            onReviewSwapInProgram=onReviewSwapInProgram)
        }
    } else if(selectedId == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PageHeading("TRAIN",
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
                Text("Workout in progress",
                    style=MaterialTheme.typography.labelLarge)
                TextButton(onClick={
                    controller.clear()
                    selectedId=null
                    onClearGeneratedWorkout()
                }) { Text("Other workout") }
            }
            Box(Modifier.weight(1f)) {
                workoutScreens.SaveableStateProvider("composer:$selectedId") {
                NativeKotlinWorkoutCandidateScreen(
                    controller,checkNotNull(selectedId),host,
                    onViewHistory={
                        controller.clear()
                        selectedId=null
                        onViewFinishedHistory()
                    },
                    onReviewNext={exerciseId->
                        controller.clear()
                        selectedId=null
                        onReviewNext(exerciseId)
                    })
                }
            }
        }
    }
}
