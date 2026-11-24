package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** Exact input validation belongs to the pure Kotlin engine, not Compose. */
private fun performed(
    target:KotlinWorkoutTarget,
    weight:String,reps:String,rir:String,
    failed:Boolean=false
):KotlinPerformedSet? = (
    KotlinWorkoutEntryDomain.parse(target,weight,reps,rir,failed)
        as? KotlinSetEntryResult.Accepted
    )?.performed

/** One opt-in editor prefill. No issued target, journal receipt or plan changes here. */
internal data class CurrentWorkoutLevelingDraft(
    val version:Int,
    val weight:String,
    val reps:Int
)

/** Draft entries are workout-local and remain editable until a normal set receipt. */
internal data class CurrentWorkoutEditorValue(val weight:String,val reps:String)

internal val currentWorkoutEditorSaver=
    listSaver<SnapshotStateMap<String,CurrentWorkoutEditorValue>,String>(
        save={ values->values.flatMap { (key,entry)->
            listOf(key,entry.weight,entry.reps)
        }},
        restore={ parts->
            mutableStateMapOf<String,CurrentWorkoutEditorValue>().apply {
                parts.chunked(3).filter{it.size==3}.forEach {
                    this[it[0]]=CurrentWorkoutEditorValue(it[1],it[2])
                }
            }
        }
    )

/** Set key and machine ID cannot alias each other's editor drafts. */
internal fun levelEditorKey(setKey:String,machineId:String?):String =
    "${setKey.length}:$setKey:${machineId ?: "no-machine"}"

private fun humanStatus(reason:NativeJournalFileRefusal?):String=when(reason) {
    NativeJournalFileRefusal.SWAP_SOURCE_UNAVAILABLE ->
        "Swapping requires a current source-verified program and gym. Your saved workout was not changed."
    NativeJournalFileRefusal.SWAP_INVALID_TARGET ->
        "This replacement is unavailable or its newly entered targets do not match your verified gym. The saved workout was restored."
    NativeJournalFileRefusal.HISTORY_NOT_READY ->
        "Native history ownership is not verified yet. Workout changes and Finish are blocked until existing history is safely enrolled or recovered. Nothing was overwritten."
    NativeJournalFileRefusal.NOT_AUTHORIZED -> "Activate your approved local athlete before opening this workout."
    NativeJournalFileRefusal.NOT_ISSUED -> "No saved Kotlin workout exists for this selection."
    NativeJournalFileRefusal.SOURCE_CONFLICT,NativeJournalFileRefusal.ISSUE_CONFLICT ->
        "This workout was issued under different saved program details. It was not overwritten."
    NativeJournalFileRefusal.CORRUPT_RECEIPTS ->
        "Saved workout verification failed. No record was reset or overwritten."
    NativeJournalFileRefusal.JOURNAL_REJECTED ->
        "The set change was rejected. Reloaded the last durable workout state."
    NativeJournalFileRefusal.STORAGE_UNAVAILABLE ->
        "Unable to access verified native workout storage."
    NativeJournalFileRefusal.INVALID_ARGUMENT ->
        "The workout identifier was not valid."
    null -> ""
}

/**
 * Complete Kotlin-only Compose workout candidate. Its data and actions come
 * from the native revision-checked session controller, not the existing JS
 * runtime. Deliberately NOT selected from MainActivity yet: the authenticated
 * onboarding/program-approval migration is the next cutover gate.
 */
@Composable
internal fun NativeKotlinWorkoutCandidateScreen(
    controller:NativeKotlinSessionController,
    workoutId:String,
    host:NativeKotlinCandidateHost,
    onViewHistory:()->Unit={},
    onReviewNext:(String)->Unit={},
    onReturnToProgram:()->Unit={}
) {
    val displayPreferences=rememberNativeWorkoutDisplayStore()
    val display by displayPreferences.state.collectAsState()
    val initialFocus=display.startLayout.startsInFocus(LocalConfiguration.current.screenWidthDp)
    val state by controller.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var machineRoster by remember(host,workoutId) {
        mutableStateOf<NativeMachineRosterSnapshot?>(null)
    }
    var eligibleMachineExercises by remember(host,workoutId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    LaunchedEffect(host,workoutId) {
        val recovered=withContext(Dispatchers.IO) {
            Pair((host.readMachineRoster() as? NativeMachineRosterResult.Saved)?.snapshot,
                host.workoutMachineEligibleExerciseIds(workoutId,custom=false))
        }
        machineRoster=recovered.first
        eligibleMachineExercises=recovered.second
    }
    // Restore the athlete's pending workout-only swap after rotation, but
    // NEVER restore a previously fetched option as a new source grant.
    var swapSourceId by rememberSaveable(workoutId){
        mutableStateOf<String?>(null)
    }
    var swapChoices by remember(workoutId){
        mutableStateOf<List<KotlinComposerSwapChoice>>(emptyList())
    }
    var swapLoading by remember(workoutId){mutableStateOf(false)}
    // Ephemeral one-shot request: machine sheet stays owner-verified and
    // cannot reopen from a stale saved source after returning to a set.
    var openPhysicalMachineFor by remember(workoutId) {
        mutableStateOf<String?>(null)
    }
    var swapError by remember(workoutId){mutableStateOf<String?>(null)}
    var undoConfirm by remember(workoutId){mutableStateOf<String?>(null)}
    val referenceMode=display.referenceMode
    var historyExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoWarmupsOnly by rememberSaveable(workoutId){mutableStateOf(false)}
    var plateRequest by remember(workoutId) {
        mutableStateOf<NativeComposerPlateRequest?>(null)
    }
    // Uncommitted fields must survive Focus/All, previous/next, and configuration changes.
    val editorStates=rememberSaveableStateHolder()
    LaunchedEffect(workoutId) { controller.open(workoutId) }
    val presentation=state.presentation?.takeIf{it.workoutId==workoutId}
    val journal=state.journal?.takeIf{it.snapshot.workoutId==workoutId}
    val machineChoices=rememberNativeWorkoutMachineChoices(
        host,workoutId,custom=false,ready=journal!=null,
        exerciseSignature=journal?.snapshot?.targets
            ?.map { it.exerciseId }?.distinct()?.joinToString(";").orEmpty(),
        roster=machineRoster,onRosterChanged={machineRoster=it})
    // Retain only the most recent VERIFIED journal reference for an open
    // note draft. A double storage failure must not discard unsaved text.
    // The cached reference never authorizes saving or marks a note saved.
    var noteAnchor by remember(workoutId) {
        mutableStateOf<KotlinWorkoutJournalState?>(null)
    }
    LaunchedEffect(journal) { if(journal!=null)noteAnchor=journal }
    // Request verified Last facts on demand for explicit prior-session Leveling,
    // even while the workout's ordinary Target/Last display remains Target.
    // A previous-workout Leveling review is valid ONLY for the exercise and
    // exact physical machine selected when the athlete opened it.
    var priorLevelingScope by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    var lastReferenceRefresh by remember(workoutId){mutableIntStateOf(0)}
    // Opening the A/B chooser requests the same owner-verified previous-day
    // facts even if the ordinary Target/Last toggle remains on Target.
    var stationHistoryRequested by rememberSaveable(workoutId){mutableStateOf(false)}
    val lastReferences=rememberWorkoutLastReferences(host,workoutId,custom=false,
        enabled=(referenceMode==WorkoutReferenceMode.LAST ||
            priorLevelingScope!=null || stationHistoryRequested) &&
            journal!=null,
        source=journal?.snapshot?.let{WorkoutLastReferenceInput.Generated(it)},
        refreshVersion=lastReferenceRefresh)
    val running=presentation!=null && !presentation.finished
    var verifiedFinish by remember(host,workoutId){
        mutableStateOf<NativeComposerFinishHandoffResult?>(null)
    }
    var finishCheckRevision by remember(host,workoutId){mutableIntStateOf(0)}
    LaunchedEffect(host,workoutId,journal?.revision,
        journal?.finishedByEventId,finishCheckRevision) {
        verifiedFinish=null
        val stored=journal?.takeIf{it.finishedByEventId!=null}
            ?:return@LaunchedEffect
        // Never trust just a local finished flag. Read the ENTIRE signed-in
        // athlete's independently owner-verified, manifest-backed history.
        verifiedFinish=withContext(Dispatchers.IO) {
            val history=runCatching{host.reviewVerifiedKotlinHistory()}
                .getOrElse{NativeKotlinMixedHistoryReadResult.Rejected(
                    NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)}
            NativeKotlinFinishedComposerHandoff.inspect(stored,history)
        }
    }

    val view=LocalView.current
    DisposableEffect(view,running,display.keepScreenAwake) {
        val previous=view.keepScreenOn
        if(running && display.keepScreenAwake)view.keepScreenOn=true
        onDispose{view.keepScreenOn=previous}
    }

    // A stale/recreated screen cannot revive consent to a different journal.
    var finishReview by remember(workoutId) {
        mutableStateOf<NativeFinishReviewToken?>(null)
    }
    var showSessionNote by rememberSaveable(workoutId){mutableStateOf(false)}
    var pinnedExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var pendingSkip by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var pendingExerciseSkip by remember(workoutId) {
        mutableStateOf<NativeExerciseSkipRequest?>(null)
    }
    // A temporary navigation choice, not an automatic skip or an injury
    // diagnosis. Never persist a stale saved-journal revision across relaunch.
    var pendingConcern by remember(workoutId) {
        mutableStateOf<NativeMovementConcernRequest?>(null)
    }
    var pendingFail by remember(workoutId) {
        mutableStateOf<Pair<String,KotlinPerformedSet>?>(null)
    }
    var selectedKey by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    // The draft survives rotation and Focus/All while staying isolated to
    // this workout and selected physical machine. It is NEVER an approval
    // for a future program or a saved actual.
    var levelExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var levelMachineId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var levelWeight by rememberSaveable(workoutId){mutableStateOf("")}
    var levelReps by rememberSaveable(workoutId){mutableIntStateOf(0)}
    var levelSetKeys by rememberSaveable(workoutId) { mutableStateOf(emptyList<String>()) }
    var levelVersion by rememberSaveable(workoutId){mutableIntStateOf(0)}
    val currentEditors=rememberSaveable(workoutId,
        saver=currentWorkoutEditorSaver) {
        mutableStateMapOf<String,CurrentWorkoutEditorValue>()
    }
    var showAll by rememberSaveable(workoutId){mutableStateOf(!initialFocus)}
    var readiness by rememberSaveable(workoutId) {
        mutableStateOf(CoachReadiness.NORMAL)
    }
    var movementRestricted by rememberSaveable(workoutId) {
        mutableStateOf(false)
    }
    // Only move Focus when its selected set was actually resolved, removed
    // by a verified swap, or when recovering a stale cold-start selection.
    // A saved note, new coach review or unrelated journal revision cannot
    // forcibly move the athlete away from the set they were editing.
    var previouslyOpenKeys by remember(workoutId) {
        mutableStateOf<Set<String>?>(null)
    }
    LaunchedEffect(presentation?.revision) {
        val shown=presentation ?:return@LaunchedEffect
        val allShown=shown.exercises.flatMap{it.sets}
        val available=allShown.map{it.target.setKey}.toSet()
        val stillOpen=allShown.filterNot{it.isResolved}
            .map{it.target.setKey}.toSet()
        val current=selectedKey
        val changedToResolved=current!=null &&
            previouslyOpenKeys?.contains(current)==true &&
            current !in stillOpen
        val coldStale=current!=null && previouslyOpenKeys==null &&
            current !in stillOpen && stillOpen.isNotEmpty()
        if(current==null || current !in available ||
            changedToResolved || coldStale) {
            selectedKey=shown.firstUnresolvedSetKey ?:
                allShown.lastOrNull()?.target?.setKey
        }
        previouslyOpenKeys=stillOpen
    }
    // Source options are intentionally not Bundle-saveable. After an activity
    // recreation the selected exercise remains, but every replacement option
    // must be recomputed against the freshly reopened workout and gym before
    // an unsaved set editor can be confirmed.
    LaunchedEffect(host,workoutId,swapSourceId,journal?.revision) {
        val source=swapSourceId ?:return@LaunchedEffect
        val issued=journal ?:return@LaunchedEffect
        if(swapChoices.isNotEmpty() || swapLoading ||
            issued.snapshot.targets.none{it.exerciseId==source} ||
            issued.finishedByEventId!=null)return@LaunchedEffect
        swapLoading=true
        try {
            val verified=withContext(Dispatchers.IO) {
                runCatching {
                    host.reviewComposerWorkoutSwapChoices(workoutId,source)
                }.getOrDefault(emptyList())
            }
            if(swapSourceId==source) {
                if(verified.isEmpty()) {
                    swapSourceId=null
                    swapError="Saved swap review is no longer eligible. "+
                        "Reopen alternatives for this unstarted exercise."
                } else {
                    swapChoices=verified
                }
            }
        } finally {
            swapLoading=false
        }
    }
    fun reviewSwap(exerciseId:String) {
        if(state.busy || swapLoading)return
        swapLoading=true
        swapError=null
        val revision=journal?.revision
        scope.launch {
            val verified=withContext(Dispatchers.IO) {
                runCatching {
                    host.reviewComposerWorkoutSwapChoices(workoutId,exerciseId)
                }.getOrDefault(emptyList())
            }
            if(journal?.revision==revision && verified.isNotEmpty()){
                swapSourceId=exerciseId
                swapChoices=verified
            } else {
                swapError="No verified alternatives for this unstarted exercise. "+
                    "Your currently approved program and gym must match the issued workout."
            }
            swapLoading=false
        }
    }
    val allSets=presentation?.exercises?.flatMap{it.sets}.orEmpty()
    // Exactly one issued occurrence owns each stable setKey. Do not pool
    // A -> B -> A into one editor, Leveling review or set ordinal.
    val occurrenceBySetKey=presentation?.exercises.orEmpty()
        .flatMap{occurrence->occurrence.sets.map{it.target.setKey to occurrence}}
        .toMap()
    val selected=allSets.firstOrNull{it.target.setKey==selectedKey}
        ?:allSets.firstOrNull()
    val selectedOccurrence=selected?.target?.setKey?.let{occurrenceBySetKey[it]}
    val activeKeys=if(showAll)allSets else listOfNotNull(selected)
    val issuedGymId=journal?.originalSnapshot?.source?.gymId
    val activeMachineConfig=selected?.target?.exerciseId?.let {exerciseId->
        issuedGymId?.let {gymId->
            if(exerciseId in eligibleMachineExercises)
                KotlinMachineAlternativesDomain.forGymExercise(
                    machineRoster?.configurations.orEmpty(),gymId,exerciseId)
                    ?:KotlinGymMachineAlternatives(gymId,exerciseId,null,emptyList())
            else null
        }
    }
    val currentMachine=selected?.target?.exerciseId?.let{
        workoutMachineChoice(machineChoices.encoded,it,null)
    }
    val currentLevelingScope=selectedOccurrence?.sets?.firstOrNull()
        ?.target?.setKey?.let { occurrenceKey ->
            occurrenceKey+"|"+(currentMachine ?: "unassigned")
        }
    LaunchedEffect(workoutId,currentLevelingScope) {
        if(priorLevelingScope!=null &&
            priorLevelingScope!=currentLevelingScope)
            priorLevelingScope=null
    }
    // Equipment is recovered from the CURRENT owner-approved program and
    // exact selected A/B station. It is never guessed from typed values.
    val levelingExerciseId=selected?.target?.exerciseId
    var levelingStock by remember(host,workoutId,levelingExerciseId,
        currentMachine,presentation?.revision) {
        mutableStateOf<VerifiedLoadStock?>(null)
    }
    LaunchedEffect(host,workoutId,levelingExerciseId,currentMachine,
        presentation?.revision,machineChoices.ready,machineChoices.busy) {
        levelingStock=null
        val exerciseId=levelingExerciseId
        if(exerciseId!=null && journal!=null &&
            journal.finishedByEventId==null && machineChoices.ready &&
            !machineChoices.busy) {
            levelingStock=withContext(Dispatchers.IO) {
                runCatching {
                    host.reviewIssuedLevelingStock(
                        workoutId,exerciseId,currentMachine)
                }.getOrNull()
            }
        }
    }
    val expectedCoachMachineScope=if(activeMachineConfig?.machines?.isNotEmpty()==true)
        LiveMachineCoachScope.Physical(currentMachine)
    else LiveMachineCoachScope.ExerciseOnly

    // Physical machine selection changes the evidence and valid stack for
    // next-set coaching even when the workout journal revision is unchanged.
    // Trigger a fresh review on durable choice writes and hide stale advice
    // while a selection or cold recovery is still being verified.
    LaunchedEffect(workoutId,presentation?.revision,selected?.target?.setKey,
        readiness,movementRestricted,machineChoices.encoded,
        machineChoices.ready,machineChoices.busy) {
        if(machineChoices.ready && !machineChoices.busy &&
            selected?.target?.type==DraftSetType.WORKING &&
            selected.isResolved.not() && presentation?.finished==false)
            controller.reviewNextSet(selected.target.setKey,readiness,
                movementRestricted)
    }
    // Issued-target advice cannot describe an unsaved athlete-edited load or
    // rep entry. Keep those editor bytes untouched and re-review after logging.
    val hasUnsubmittedCurrentEdit=selected?.let{shown->
        NativeKotlinLiveAdviceDraftPolicy.hasUnsubmittedChange(
            shown.target.load,shown.target.reps,currentMachine,
            currentEditors[levelEditorKey(shown.target.setKey,currentMachine)])
    } ?:false
    val coach=state.liveCoach?.takeIf {
        // A pre-restriction recommendation is unsafe to keep showing while
        // a fresh owner-scoped review is in flight.
        !movementRestricted && !state.busy &&
        !hasUnsubmittedCurrentEdit && machineChoices.ready && !machineChoices.busy &&
            it.journalRevision==presentation?.revision &&
            it.setKey==selected?.target?.setKey &&
            // A result for station A may linger for one Compose frame
            // while the new B review starts. Hide it synchronously.
            it.machineScope==expectedCoachMachineScope
    }

    finishReview?.let {reviewed->
        val eligible=presentation?.canFinish==true &&
            presentation.finished==false && journal?.finishedByEventId==null
        val canCommit=NativeFinishReviewPolicy.canCommit(
            reviewed,workoutId,journal?.revision,eligible)
        AlertDialog(onDismissRequest={finishReview=null},
            title={Text("Finish this workout?")},
            text={Text(if(canCommit)
                "All prescribed sets have an outcome. Finishing saves a permanent event; it will not change target weights or automatically create another workout."
                else "Your saved workout changed or needs recovery. Close this review and check the current sets before finishing.")},
            confirmButton={
                Button(onClick={
                    if(NativeFinishReviewPolicy.canCommit(
                        reviewed,workoutId,journal?.revision,
                        presentation?.canFinish==true &&
                            presentation.finished==false &&
                            journal?.finishedByEventId==null)) {
                        finishReview=null
                        controller.finish(reviewed.revision)
                    }
                },enabled=!state.busy && canCommit){Text("Finish workout")}
            },
            dismissButton={TextButton(onClick={finishReview=null}){
                Text("Review sets")
            }})
    }

    if(showSessionNote) {
        val verifiedJournal=journal ?:noteAnchor
        if(verifiedJournal!=null)NativeKotlinSessionNoteEditor(
            workoutId=workoutId,
            committed=verifiedJournal.sessionNote,
            maxChars=KotlinWorkoutJournalDomain.MAX_SESSION_NOTE_CHARS,
            validate=KotlinWorkoutJournalDomain::validSessionNote,
            busy=state.busy,
            enabled=journal!=null && journal.finishedByEventId==null,
            error=state.unavailableReason?.let(::humanStatus)
                ?:state.journalRefusal?.let {
                    "The session note was not saved: ${it.name.replace('_',' ').lowercase()}."
                },
            onSave={controller.saveSessionNote(it)},
            onDismiss={showSessionNote=false}
        )
    }

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
        .widthIn(max=920.dp).align(Alignment.CenterHorizontally)
        .padding(horizontal=16.dp,vertical=20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageHeading("TODAY'S TRAINING",
            if(presentation?.finished==true)"Workout complete"
            else "Today's workout",
            if(presentation==null)"Workout preview"
            else "${presentation.purpose.name.replace('_',' ').lowercase()} · "+
                "${presentation.estimatedMinutes} min estimated")
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(presentation==null) {
            PursuitCard(highlighted=true) {
                Text("Choose a workout from your training plan to begin.",
                    style=MaterialTheme.typography.titleMedium)
                Text(if(state.busy)
                    "Verifying the saved workout and athlete ownership. Logging and Finish remain unavailable until recovery completes."
                else humanStatus(state.unavailableReason).ifBlank{
                    "No confirmed native workout has been loaded. Your existing app data is unchanged."
                })
                if(state.unavailableReason==NativeJournalFileRefusal.NOT_ISSUED && !state.busy) {
                    PursuitPrimaryAction("Start explicitly approved workout",
                        enabled=!state.busy){controller.startApproved(workoutId)}
                }
                OutlinedButton(onClick={controller.open(workoutId)},
                    enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth()) {Text("Reload native workout")}
            }
            return@Column
        }

        journal?.snapshot?.shortSessionLimitMinutes?.let {minutes->
            val omitted=journal.snapshot.shortSessionOmittedSetKeys.size
            PursuitCard {
                PursuitSectionHeading(
                    "$minutes-minute approved session",
                    if(omitted==0)"The complete plan fits this time preference."
                    else "$omitted original set(s) were omitted before this workout was issued."
                )
                Text(
                    if(omitted==0)
                        "All original set targets remain intact. This is not an edited training exposure."
                    else "Only the approved retained sets are included in this workout. Recorded performance and personal records remain factual; the reduced prescription is not used as proof of full-session load progression.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PursuitSessionMetrics(
            logged=presentation.completedCount+presentation.failedCount,
            skipped=presentation.skippedCount,
            remaining=presentation.remainingCount)
        LinearProgressIndicator(
            progress={if(allSets.isEmpty())0f else
                presentation.resolvedCount.toFloat()/allSets.size},
            modifier=Modifier.fillMaxWidth())
        if(journal!=null && journal.finishedByEventId==null &&
            (showAll || selected==null)) {
            OutlinedButton(
                onClick={showSessionNote=true},
                enabled=!state.busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {
                Text(if(journal.sessionNote.isBlank())
                    "Add session note" else "Edit saved session note")
            }
        } else if(journal?.finishedByEventId!=null &&
            journal.sessionNote.isNotBlank()) {
            PursuitCard {
                PursuitSectionHeading("Saved session note",
                    "Recorded with this finished workout · read-only")
                Text(journal.sessionNote,style=MaterialTheme.typography.bodyMedium)
            }
        }
        state.unavailableReason?.let {
            Text(humanStatus(it),color=MaterialTheme.colorScheme.error)
        }
        state.journalRefusal?.let {
            Text("Workout action refused: ${it.name.replace('_',' ').lowercase()}. The saved workout was refreshed.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            presentation.exercises.forEachIndexed{index,ex->
                val selectedExercise=ex.sets.any{it.target.setKey==selected?.target?.setKey}
                val repeated=presentation.exercises.count{
                    it.exerciseId==ex.exerciseId
                }>1
                val occurrenceNumber=presentation.exercises.take(index+1).count{
                    it.exerciseId==ex.exerciseId
                }
                FilterChip(selected=selectedExercise,
                    onClick={selectedKey=ex.nextUnresolvedSetKey
                        ?:ex.sets.last().target.setKey},
                    label={Text("${kotlinExerciseLabel(ex.exerciseId)}"+
                        (if(repeated)" · #$occurrenceNumber" else "")+
                        " · ${ex.resolvedCount}/${ex.sets.size}")})
            }
        }
        val focusDetail=selected?.target?.let { target ->
            val typeSets=selectedOccurrence?.sets.orEmpty().filter {
                it.target.type==target.type
            }
            val number=typeSets.indexOfFirst {
                it.target.setKey==target.setKey
            }+1
            "${if(target.type==DraftSetType.WARMUP)"Warm-up" else "Set"} $number of ${typeSets.size}"
        }
        PursuitWorkoutFocusHeader(
            exerciseLabel=if(showAll)null
                else selected?.target?.exerciseId?.let(::kotlinExerciseLabel),
            setDetail=if(showAll)null else focusDetail,
            focusOnNext=!showAll,
            onFocusChange={focus->
                displayPreferences.layout(if(focus)WorkoutStartLayout.FOCUS else WorkoutStartLayout.ALL_SETS)
                showAll=!focus
                if(focus)
                    selectedKey=presentation.firstUnresolvedSetKey ?:selectedKey
            },
            enabled=!state.busy,
            heading=when {
                presentation.finished -> "WORKOUT REVIEW"
                showAll -> "ALL SETS"
                selected?.target?.setKey==presentation.firstUnresolvedSetKey ->
                    "UP NEXT"
                else -> "SELECTED SET"
            },
            onHistory=selected?.target?.exerciseId?.let{exerciseId->
                {historyExerciseId=exerciseId}
            },
            onInfo=selected?.target?.exerciseId?.let{exerciseId->
                {infoExerciseId=exerciseId}
            },
            onSkip=selected?.takeIf {
                !it.isResolved && !presentation.finished && !showAll &&
                    machineChoices.ready && !machineChoices.busy
            }?.target?.setKey?.let { key ->
                {pendingSkip=key}
            }
        )
        if(!showAll && !presentation.finished && selected!=null) {
            NativeKotlinWorkoutFocusNavigation(
                steps=allSets.map{
                    NativeKotlinFocusStep(it.target.setKey,it.isResolved)
                },
                selected=selected.target.setKey,
                nextUnfinished=presentation.firstUnresolvedSetKey,
                enabled=!state.busy,
                onChoose={key->selectedKey=key},
                onReturnToNextUnfinished={
                    selectedKey=presentation.firstUnresolvedSetKey
                }
            )
        }
        if(!showAll && !presentation.finished && journal!=null) {
            val toolExercise=selected?.target?.exerciseId
            val toolTargets=journal.snapshot.targets.filter{it.exerciseId==toolExercise}
            NativeKotlinWorkoutTools(
                enabled=!state.busy && !swapLoading,
                onSessionNote={showSessionNote=true},
                // Occasional A/B selection belongs in Exercise Info.
                // The existing verified sheet still owns the actual choice.
                onMachine=null,
                onPinnedNote=toolExercise?.let {exerciseId->
                    {pinnedExerciseId=exerciseId}
                },
                onSwap=null,
                onWarmup=toolExercise?.takeIf {
                    toolTargets.any{it.type==DraftSetType.WARMUP}
                }?.let {exerciseId->{
                    infoWarmupsOnly=true
                    infoExerciseId=exerciseId
                }}
            )
        }
        if(!presentation.finished) {
            PursuitWorkoutReadinessPanel(
                readiness=readiness,
                onReadinessChange={readiness=it},
                movementRestricted=movementRestricted,
                onMovementRestrictedChange={movementRestricted=it},
                enabled=!state.busy,
                compactByDefault=true,
                onReportConcern=if(journal!=null) ({
                    // Restrict optional advice immediately; never auto-record
                    // a set, alter a prescription or claim an injury note
                    // was saved before athlete confirmation.
                    movementRestricted=true
                    readiness=CoachReadiness.LOW
                    pendingConcern=NativeMovementConcernRequest(
                        selected?.target?.exerciseId,journal.revision)
                }) else null
            )
        }
        if(coach!=null && !presentation.finished)PursuitCard(
            highlighted=coach.coaching.direction==
                InWorkoutDirection.FOLLOW_PLANNED_BACKOFF) {
            Text("Coach · next set",style=MaterialTheme.typography.titleMedium)
            Text(coach.coaching.message,
                style=MaterialTheme.typography.bodyMedium)
            selected?.target?.load?.let{issued->
                kotlinOptionalLiveSetAdviceLabel(coach.coaching,issued)?.let{label->
                    Text(label,style=MaterialTheme.typography.labelMedium)
                }
            }
            Text("Based on saved sets · no automatic weight or rep changes",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(hasUnsubmittedCurrentEdit && !presentation.finished) {
            Text("Unsaved set edits preserved · live advice uses recorded sets, not this draft.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PursuitWorkoutReferencePicker(referenceMode,displayPreferences::reference,
            onRetry=if(lastReferences==WorkoutLastReferenceResult.Unavailable &&
                !state.busy) ({lastReferenceRefresh++}) else null)
        // Web parity: average positive working-set editor entries in this
        // CURRENT session (including completed factual entries), then fill
        // ONLY unfinished entries. Issued set targets and future workouts
        // remain untouched. The journal still owns each actual when logged.
        if(selected!=null && journal!=null && !presentation.finished) {
            val exerciseId=selected.target.exerciseId
            val numberOfWorkingSets=selectedOccurrence?.sets.orEmpty().count {
                it.target.type==DraftSetType.WORKING
            }
            if(numberOfWorkingSets>=2) {
                var levelingExpanded by rememberSaveable(
                    workoutId,selectedOccurrence?.sets?.firstOrNull()?.target?.setKey
                ) {
                    mutableStateOf(false)
                }
                OutlinedButton(
                    onClick={
                        levelingExpanded=!levelingExpanded
                        if(!levelingExpanded)priorLevelingScope=null
                    },
                    enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    Text(if(levelingExpanded)"Hide Leveling options"
                        else "Leveling · adjust current sets")
                }
                if(levelingExpanded) {
            val workingSets=selectedOccurrence?.sets.orEmpty().filter {
                it.target.type==DraftSetType.WORKING
            }
            val levelRows=workingSets.map {set->
                val done=set.isResolved
                val saved=if(done &&
                    set.outcome==KotlinJournalSetOutcome.COMPLETED)
                    set.actual else null
                val draft=if(done)null else currentEditors[
                    levelEditorKey(set.target.setKey,currentMachine)]
                val weight=if(done) saved?.load else
                    (draft?.weight ?: initialMachineScopedLoad(
                        set.target.load,null,currentMachine)).toBigDecimalOrNull()
                val reps=if(done) saved?.reps else
                    (draft?.reps ?: set.target.reps.minimum.toString())
                        .toIntOrNull()
                val issued=set.target.load as? DraftSetLoad.Verified
                CurrentWorkoutLevelingRow(
                    setKey=set.target.setKey,load=weight,reps=reps,
                    completed=done,
                    unit=issued?.unit ?: LoadUnit.LB,
                    basis=issued?.basis ?: LoadBasis.EXERCISE_OVERRIDE,
                    machineInstanceId=if(done) saved?.machineInstanceId
                        else currentMachine)
            }
            // Leveling is a weighted-load working-set convenience, not a
            // bodyweight, mixed unit or technique substitution.
            val loadTypes=workingSets.map{it.target.load}
            val allVerified=loadTypes.all{it is DraftSetLoad.Verified}
            // Inspecting verified barbell combinations can be expensive.
            // Never enumerate plate combinations on Compose's UI thread.
            var review by remember(workoutId,currentLevelingScope,
                levelRows,levelingStock) {
                mutableStateOf<CurrentWorkoutLevelingReview?>(null)
            }
            LaunchedEffect(levelRows,levelingStock,allVerified) {
                review=withContext(Dispatchers.Default) {
                    if(allVerified && workingSets.size>=2 &&
                        loadTypes.map{(it as DraftSetLoad.Verified).basis}
                            .distinct().size==1 &&
                        loadTypes.map{(it as DraftSetLoad.Verified).unit}
                            .distinct().size==1)
                        DoubleProgressionLevelingDomain.reviewCurrentWorkout(
                            levelRows,levelingStock)
                    else CurrentWorkoutLevelingReview.Unavailable(
                        CurrentWorkoutLevelingReview.Reason.MIXED_EQUIPMENT)
                }
            }
            val candidate=review as? CurrentWorkoutLevelingReview.Ready
            if(candidate!=null) {
                val unit=(loadTypes.firstOrNull() as? DraftSetLoad.Verified)
                    ?.unit?.name?.lowercase().orEmpty()
                OutlinedButton(
                    onClick={
                        // The visible draft is the complete effect. Do not
                        // alter a saved workout, approved cycle or target.
                        if(!state.busy && machineChoices.ready &&
                            !machineChoices.busy && workingSets.any{!it.isResolved}) {
                            levelExerciseId=exerciseId
                            levelMachineId=currentMachine
                            levelSetKeys=candidate.eligibleSetKeys
                            levelWeight=candidate.roundedLoad
                                .stripTrailingZeros().toPlainString()
                            levelReps=candidate.averageReps
                            // Keep ALL invisible Focus rows in sync now.
                            // Their saveable editors apply the same explicit
                            // draft when mounted, never on workout issuance.
                            candidate.eligibleSetKeys.forEach {key->
                                currentEditors[levelEditorKey(key,currentMachine)]=
                                    CurrentWorkoutEditorValue(
                                        levelWeight,levelReps.toString())
                            }
                            levelVersion++
                        }
                    },
                    enabled=!state.busy && machineChoices.ready &&
                        !machineChoices.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    Text("Use average load · "+
                        "${candidate.roundedLoad.stripTrailingZeros().toPlainString()} "+
                        "$unit × ${candidate.averageReps}")
                }
                Text("Fills ${candidate.eligibleSetKeys.size} unfinished working set(s). "+
                    "Completed sets, warm-ups, actual RIR and future plans remain unchanged. "+
                    "Log each actual as usual.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else if((review as? CurrentWorkoutLevelingReview.Unavailable)?.reason==
                CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK &&
                levelRows.count{row->kotlinWorkingLoadEntered(row.load,row.basis)}>=2) {
                Text("To level loads, confirm the equipment increments or "+
                    "available stack weights in My gym. Your entered sets "+
                    "remain editable and nothing has been changed.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Separate from current-session averaging: this reviews the exact
            // LAST completed workout's pyramid and can only prefill unsaved
            // editors today. The existing issued prescriptions and progression
            // engine remain unchanged until actual sets are normally logged.
            if(workingSets.size>=2 && allVerified && workingSets.any{!it.isResolved}) {
                OutlinedButton(
                    onClick={priorLevelingScope=currentLevelingScope},
                    enabled=!state.busy && machineChoices.ready && !machineChoices.busy,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) { Text("Leveling · review previous workout") }
                if(priorLevelingScope==currentLevelingScope &&
                    currentLevelingScope!=null) {
                    val first=workingSets.first().target
                    val sameGoal=first.targetRir!=null && workingSets.all {
                        it.target.reps==first.reps &&
                            it.target.targetRir==first.targetRir
                    }
                    val priorReview=rememberPriorWorkoutLevelingReview(
                        currentSets=workingSets.map { set->
                            WorkoutReferenceKey(set.target.setKey) to set.target.load
                        },
                        // The evaluator needs machineSets, which the display-only
                        // Target/Last filter intentionally removes.
                        previous=lastReferences,
                        machineInstanceId=currentMachine,
                        repBand=first.reps.takeIf{sameGoal},
                        goalRir=first.targetRir?.takeIf{sameGoal},
                        verifiedStock=levelingStock
                    )
                    val prior=priorReview as? LevelingReviewResult.Candidate
                    val unfinished=workingSets.filterNot{it.isResolved}
                    // The previous observations are only displayed after the
                    // same-session/machine/goal review returned a candidate.
                    // Showing evidence and presenting a suggestion never writes.
                    val matchingLast=physicalMachineLastReferences(
                        lastReferences,currentMachine)
                    val observed=if(prior!=null)workingSets.mapNotNull { set->
                        WorkoutLastDraftDomain.reviewedActual(
                            matchingLast,WorkoutReferenceKey(set.target.setKey),
                            set.target.load)
                    } else emptyList()
                    PursuitPreviousWorkoutLevelingDialog(
                        review=priorReview,
                        historyLoading=lastReferences==null,
                        goalComparable=sameGoal,
                        stationValid=machineChoices.ready && !machineChoices.busy,
                        loadUnit=(first.load as? DraftSetLoad.Verified)
                            ?.unit?.name?.lowercase().orEmpty(),
                        observed=observed,unfinishedCount=unfinished.size,
                        busy=state.busy,
                        onDismiss={priorLevelingScope=null},
                        onApply={
                            // Only an explicit athlete tap changes unsaved drafts.
                            if(prior!=null && !state.busy && machineChoices.ready &&
                                !machineChoices.busy && unfinished.isNotEmpty()) {
                                levelExerciseId=exerciseId
                                levelMachineId=currentMachine
                                levelSetKeys=unfinished.map { it.target.setKey }
                                levelWeight=prior.proposedStraightSetLoad
                                    .stripTrailingZeros().toPlainString()
                                levelReps=prior.repsPerSet
                                unfinished.forEach { set->
                                    currentEditors[levelEditorKey(
                                        set.target.setKey,currentMachine)]=
                                        CurrentWorkoutEditorValue(
                                            levelWeight,levelReps.toString())
                                }
                                levelVersion++
                                priorLevelingScope=null
                            }
                        }
                    )
                }
            }
                }
            }
        }

        if(selected!=null && journal!=null)
            NativeKotlinPinnedExerciseControl(
                host=host,workoutId=workoutId,
                exerciseId=selected.target.exerciseId,custom=false,
                enabled=!state.busy && !presentation.finished,
                showEditAction=showAll || presentation.finished,
                externalOpen=pinnedExerciseId==selected.target.exerciseId,
                onExternalDismiss={pinnedExerciseId=null}
            )
        // Physical Machine A/B and workout-only replacement are exposed by
        // the current source-verified Exercise Info sheet (Focus and All).
        // No second full-width equipment banner interrupts the set editor.
        NativeKotlinWorkoutMachineSelector(activeMachineConfig,currentMachine,
            enabled=!state.busy && !presentation.finished &&
                machineChoices.ready && !machineChoices.busy,
            onSelect={machineId->
                val exerciseId=selected?.target?.exerciseId
                if(exerciseId!=null)machineChoices.onSelect(exerciseId,machineId)
            },
            onCreate={label,makeDefault->
                val exerciseId=selected?.target?.exerciseId
                if(exerciseId!=null)
                    machineChoices.onCreate(exerciseId,label,makeDefault)
            },onRetry=machineChoices.onRetry,
            retryEnabled=machineChoices.canRetry,
            error=machineChoices.error,
            acknowledgedCreatedMachineId=machineChoices.createdMachineId,
            priorHistory=lastReferences,
            priorWorkingKeys=selectedOccurrence?.sets.orEmpty().filter {
                it.target.type==DraftSetType.WORKING
            }.map {WorkoutReferenceKey(it.target.setKey)},
            onRequestHistory={stationHistoryRequested=true},
            externalOpen=selected!=null &&
                openPhysicalMachineFor==selected.target.exerciseId,
            onExternalOpenConsumed={openPhysicalMachineFor=null},
            showInlineTrigger=showAll || presentation.finished)
        machineChoices.error?.let{
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
            OutlinedButton(onClick=machineChoices.onRetry,
                enabled=machineChoices.canRetry && !state.busy,
                modifier=Modifier.heightIn(min=48.dp)) {
                Text("Retry machine verification")
            }
        }
        activeKeys.forEach{set->
            // Obsolete non-stack physical groups are History-only. Never
            // let them impose a machine-selection gate on live barbell/
            // dumbbell sets when the picker itself is correctly unavailable.
            val perSetMachineConfig=if(set.target.exerciseId in eligibleMachineExercises)
                issuedGymId?.let {gymId->
                    KotlinMachineAlternativesDomain.forGymExercise(
                        machineRoster?.configurations.orEmpty(),gymId,
                        set.target.exerciseId)
                }
            else null
            val perSetMachineId=perSetMachineConfig?.let {
                workoutMachineChoice(machineChoices.encoded,set.target.exerciseId,null)
            }
            // A historical skip has no recorded machine. Correcting
            // it to an attempt must require a current active station.
            val needsMachineSelection=set.actual==null &&
                perSetMachineConfig?.machines?.isNotEmpty()==true &&
                perSetMachineConfig.machines.none{
                    it.id==perSetMachineId && it.active
                }
            // Correcting an existing set must never reassign it to the
            // currently selected B machine. Null here means legacy.
            val previouslyAttempted=set.actual
            val recordedMachineId=if(set.currentReceipt!=null &&
                previouslyAttempted!=null)
                previouslyAttempted.machineInstanceId else perSetMachineId
            // SaveableStateHolder retains each physical station's UNSAVED
            // editor across Focus/All, A -> B -> A, and rotation. One station
            // cannot inherit another station's typed load. Durable actuals
            // are still authoritative after the saved receipt revision moves.
            val occurrenceTypeSets=occurrenceBySetKey[set.target.setKey]
                ?.sets.orEmpty().filter{it.target.type==set.target.type}
            editorStates.SaveableStateProvider(
                "composer-set:${set.target.setKey}:station:"+
                    (recordedMachineId?.let{"id:$it"} ?: "none")
            ) {
                if(needsMachineSelection)
                    Text("Choose an active physical machine before recording this set. "+
                        "Skipping is still allowed.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                NativeKotlinSetCard(set,presentation.finished,
                    state.busy || machineChoices.busy || !machineChoices.ready,
                    saveError=state.journalRefusal?.let {
                        "The correction was not saved. Review the saved set and retry."
                    } ?:state.unavailableReason?.let(::humanStatus),
                    machineInstanceId=recordedMachineId,
                    athleteId=journal?.snapshot?.source?.athleteId.orEmpty(),
                    workoutId=workoutId,
                    issuedGymId=issuedGymId,machineRoster=machineRoster,
                    referenceMode=referenceMode,lastReferences=lastReferences,
                    onCurrentDraftChanged={load,reps->
                        val key=levelEditorKey(set.target.setKey,recordedMachineId)
                        val next=CurrentWorkoutEditorValue(load,reps)
                        if(currentEditors[key]!=next)currentEditors[key]=next
                    },
                    levelingDraft=if(!set.isResolved &&
                        set.target.type==DraftSetType.WORKING &&
                        set.target.exerciseId==levelExerciseId &&
                        recordedMachineId==levelMachineId &&
                        levelWeight.isNotBlank() && levelReps>0 &&
                        set.target.setKey in levelSetKeys)
                        CurrentWorkoutLevelingDraft(levelVersion,levelWeight,levelReps)
                    else null,
                    position=occurrenceTypeSets.indexOf(set)+1,
                    setCount=occurrenceTypeSets.size,
                    plannedBackoffNote=NativeKotlinPlannedBackoffNote.fromIssuedTargets(
                        occurrenceBySetKey[set.target.setKey]?.sets.orEmpty(),
                        set.target),
                    showExerciseHeading=showAll || presentation.finished,
                    onHistory={historyExerciseId=set.target.exerciseId},
                    onInfo={
                        selectedKey=set.target.setKey
                        infoExerciseId=set.target.exerciseId
                    },
                    onShowPlates={load,unit->
                        plateRequest=NativeComposerPlateRequest(
                            set.target.exerciseId,load,unit)
                    },
                    onRecord={outcome,actual->
                        if(machineChoices.ready && !machineChoices.busy &&
                            (outcome==KotlinJournalSetOutcome.SKIPPED ||
                                !needsMachineSelection))
                            controller.record(set.target.setKey,outcome,
                                actual?.copy(machineInstanceId=recordedMachineId))
                    },
                    onSkip={pendingSkip=set.target.setKey},
                    onFail={attempt->
                        if(machineChoices.ready && !machineChoices.busy &&
                            !needsMachineSelection)
                            pendingFail=set.target.setKey to
                                attempt.copy(machineInstanceId=recordedMachineId)
                    })
            }
        }
        // The one source-typed Focus navigator above the set owns Previous,
        // Next and Jump to unfinished. Avoid a second competing nav row
        // below the editor, especially on narrow phones.
        // Equipment, workout-only swap, undo and skip-the-remaining-sets
        // are all explicit Exercise Info actions from Focus AND All Sets.
        // Do not put a second full-width swap card beneath the set editor.
        swapError?.let {message->
            Text("Exercise replacement not available: "+message+
                " Open Exercise Info to review another option.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
        if(presentation.finished)PursuitCard(highlighted=true) {
            Text("Workout completion is saved",
                style=MaterialTheme.typography.titleMedium)
            Text("Your planned targets, recorded sets, skips, failed attempts and corrections stay attached to this workout.")
            when(verifiedFinish) {
                is NativeComposerFinishHandoffResult.Verified -> {
                    NativeKotlinVerifiedFinishRecap(
                        host=host,workoutId=workoutId,isCustom=false)
                    Text("Your completed workout is verified in History. Your next workout remains unchanged until you review it.",
                        style=MaterialTheme.typography.bodySmall)
                    PursuitPrimaryAction("View finished workout in History",
                        enabled=!state.busy){onViewHistory()}
                    OutlinedButton(
                        onClick=onReturnToProgram,
                        enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Continue to my training plan")}
                    Text("Choose a movement to review with Coach. The current approved program determines whether its progression is eligible.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        presentation.exercises.map{it.exerciseId}.distinct()
                            .forEach{exerciseId->
                                OutlinedButton(
                                    onClick={onReviewNext(exerciseId)},
                                    enabled=!state.busy) {
                                    Text("Review ${exerciseId.replace('_',' ').replace('-',' ')}")
                                }
                            }
                    }
                }
                is NativeComposerFinishHandoffResult.Rejected ->
                    Text("Finish was recorded, but complete native History has not been verified. Recheck before advancing.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                null -> Text("Verifying committed native History…",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(verifiedFinish !is NativeComposerFinishHandoffResult.Verified)
                OutlinedButton(onClick={finishCheckRevision++},
                    enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
                    Text("Recheck committed History")
                }
        }
        OutlinedButton(onClick={controller.open(workoutId)},
            enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
            Text("Reload saved workout")
        }
    }
    if(journal!=null && journal.unresolvedSetCount>0 && journal.finishedByEventId==null)
        Column(Modifier.widthIn(max=920.dp).fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(horizontal=16.dp,vertical=8.dp)) {
            NativeKotlinWorkoutRestTimer(workoutId,
                KotlinWorkoutRestDomain.inspect(journal,selected?.target?.setKey),
                enabled=!state.busy,
                athleteId=journal.snapshot.source.athleteId)
        }
    // When all actuals/skips/failures are durably resolved, replace the
    // bottom rest bar with ONE accessible Finish action. Keeping it outside
    // the scroller means a long session never buries completion below dozens
    // of saved exercises. Confirmation and owner-fenced History still own
    // the actual Finish write; this dock cannot save automatically.
    if(presentation?.canFinish==true && presentation.finished==false &&
        journal?.finishedByEventId==null)
        Column(Modifier.widthIn(max=920.dp).fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(horizontal=16.dp,vertical=8.dp)) {
            PursuitCard(highlighted=true) {
                Text("All prescribed sets have a saved outcome",
                    style=MaterialTheme.typography.titleMedium)
                PursuitPrimaryAction("Review and finish",
                    enabled=!state.busy) {
                    finishReview=journal?.let {
                        NativeFinishReviewToken(workoutId,it.revision)
                    }
                }
            }
        }
    }

    val swapFrom=swapSourceId
    val activeSwapJournal=journal
    if(swapFrom!=null && activeSwapJournal!=null &&
        activeSwapJournal.finishedByEventId==null) {
        val sourceTargets=activeSwapJournal.snapshot.targets.filter{
            it.exerciseId==swapFrom
        }
        if(sourceTargets.isNotEmpty())
            NativeKotlinComposerSwapSheet(
                fromExerciseId=swapFrom,
                sourceTargets=sourceTargets,
                choices=swapChoices,
                busy=state.busy,
                onConfirm={replacementId,intents->
                    controller.swapExercise(swapFrom,replacementId,intents)
                    swapSourceId=null
                    swapChoices=emptyList()
                },
                onDismiss={
                    swapSourceId=null
                    swapChoices=emptyList()
                }
            )
    }
    undoConfirm?.let {swapId->
        AlertDialog(
            onDismissRequest={undoConfirm=null},
            title={Text("Undo exercise swap?")},
            text={Text("Restore the exact earlier exercise and approved "+
                "set targets. This is possible only before logging a "+
                "replacement set. Other work and the program stay unchanged.")},
            confirmButton={
                Button(onClick={
                    controller.undoExerciseSwap(swapId)
                    undoConfirm=null
                },enabled=!state.busy) {Text("Restore previous exercise")}
            },
            dismissButton={
                TextButton(onClick={undoConfirm=null},enabled=!state.busy){
                    Text("Keep replacement")
                }
            }
        )
    }
    plateRequest?.let {request->
        NativeKotlinComposerPlateSheet(host,workoutId,request,
            onDismiss={plateRequest=null})
    }
    // Factual planned set details are derived from the currently recovered
    // journal, not guessed from a movement name or the old web catalog.
    infoExerciseId?.let {exerciseId ->
        val targets=journal?.snapshot?.targets.orEmpty().filter {
            it.exerciseId==exerciseId
        }
        if(targets.isNotEmpty()) {
            val first=targets.first()
            val infoConfig=issuedGymId?.let { gymId ->
                if(exerciseId in eligibleMachineExercises)
                    KotlinMachineAlternativesDomain.forGymExercise(
                        machineRoster?.configurations.orEmpty(),gymId,exerciseId)
                        ?:KotlinGymMachineAlternatives(gymId,exerciseId,null,emptyList())
                else null
            }
            val infoMachine=workoutMachineChoice(machineChoices.encoded,exerciseId,null)
            val multipleOccurrences=presentation?.exercises.orEmpty().count {
                it.exerciseId==exerciseId
            }>1
            val infoOptions=NativeKotlinEquipmentRecoveryPolicy.review(
                activeWorkout=presentation?.finished==false && !state.busy && !swapLoading,
                hasPhysicalMachineConfig=infoConfig!=null &&
                    machineChoices.ready && !machineChoices.busy,
                hasRecordedExerciseWork=targets.any{
                    it.setKey in journal?.effectiveSets.orEmpty()
                }
            )
            val infoRemaining=journal?.let {
                WorkoutRemainingExerciseDomain.generated(it,exerciseId)
            }
            val infoTouched=targets.any{
                it.setKey in journal?.effectiveSets.orEmpty()
            }
            val infoUndo=journal?.receipts.orEmpty().asReversed()
                .mapNotNull{it.operation as? KotlinJournalOperation.SwapExercise}
                .firstOrNull{event->
                    event.replacementTargets.firstOrNull()?.exerciseId==
                        exerciseId &&
                    journal?.receipts.orEmpty().none{receipt->
                        (receipt.operation as? KotlinJournalOperation.UndoSwap)
                            ?.swapEventId==event.eventId
                    }
                }
            val infoActions=NativeWorkoutInfoActionPolicy.review(
                active=presentation?.finished==false &&
                    journal?.finishedByEventId==null &&
                    !state.busy && !swapLoading,
                hasRemainingSets=infoRemaining?.entries?.isNotEmpty()==true,
                hasActiveSwap=infoUndo!=null,
                hasRecordedWork=infoTouched)
            NativeKotlinWorkoutExerciseInfoSheet(
                exerciseId=exerciseId,
                sourceDescription="Issued generated workout · "+
                    first.role.name.lowercase().replace('_',' ')+
                    " · "+first.purpose.name.lowercase().replace('_',' '),
                sets=composerWorkoutInfoSets(targets),
                warmupsOnly=infoWarmupsOnly,
                busy=state.busy || swapLoading || machineChoices.busy,
                machineLabel=infoConfig?.machines?.firstOrNull{
                    it.id==infoMachine
                }?.label ?: "Choose or add machine",
                onChooseMachine=if(!infoWarmupsOnly && infoOptions.canChangeStation)
                    ({
                        // The Info action is not a Focus navigation request.
                        // Keep the currently edited set and its unsaved fields.
                        infoExerciseId=null
                        openPhysicalMachineFor=exerciseId
                    }) else null,
                onSwapExercise=if(!infoWarmupsOnly && !multipleOccurrences &&
                    infoOptions.canReplaceExercise)
                    ({
                        infoExerciseId=null
                        reviewSwap(exerciseId)
                    }) else null,
                onSkipRemainingExercise=if(!infoWarmupsOnly &&
                    !multipleOccurrences &&
                    infoActions.mayRequestSkipRemaining)journal?.revision?.let{revision->
                    ({
                        infoExerciseId=null
                        pendingExerciseSkip=NativeExerciseSkipRequest(
                            exerciseId,revision)
                    })
                }else null,
                onUndoWorkoutSwap=if(!infoWarmupsOnly &&
                    infoActions.mayRequestUndoSwap)infoUndo?.let{event->
                    ({
                        infoExerciseId=null
                        undoConfirm=event.eventId
                    })
                }else null,
                unavailableReason=if(multipleOccurrences && !infoWarmupsOnly)
                    "This movement appears more than once in the issued workout. "+
                    "Use each set's Skip control; bulk skip and workout-only "+
                    "swap are unavailable until occurrence-specific review is supported."
                else if(!infoWarmupsOnly &&
                    presentation?.finished==false)infoOptions.reason else null,
                outcomes=journal?.effectiveSets.orEmpty().mapNotNull { (key,receipt)->
                    (receipt.operation as? KotlinJournalOperation.RecordSet)?.let{key to it.outcome}
                }.toMap(),
                onSelectWarmup=if(state.busy || presentation?.finished!=false)null else ({key->
                    // Navigation only: leave the ordinary set writer in charge.
                    selectedKey=key
                    showAll=false
                    infoExerciseId=null
                    infoWarmupsOnly=false
                }),
                onHistory={
                    infoExerciseId=null
                    infoWarmupsOnly=false
                    historyExerciseId=exerciseId
                },
                onDismiss={infoExerciseId=null;infoWarmupsOnly=false}
            )
        }
    }
    historyExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={historyExerciseId=null})
    }
    pendingConcern?.let{request->
        val remaining=journal?.let{verified->
            request.scopeId?.let{
                WorkoutRemainingExerciseDomain.generated(verified,it)
            }
        }
        val options=NativeMovementConcernPresentation.review(
            request,journal?.revision,
            journal!=null && journal.finishedByEventId==null,remaining,
            canReviewReplacement=NativeMovementSwapEligibility.generated(
                journal,request.scopeId))
        NativeKotlinMovementConcernDialog(
            options=options,busy=state.busy,
            onWriteNote={
                if(options.current && !state.busy &&
                    journal?.revision==request.journalRevision) {
                    pendingConcern=null
                    showSessionNote=true
                }
            },
            onReviewSkip={
                val selectedRequest=options.skipRequest
                if(selectedRequest!=null && !state.busy &&
                    journal?.revision==request.journalRevision) {
                    pendingConcern=null
                    pendingExerciseSkip=selectedRequest
                }
            },
            onReviewReplacement={
                val candidate=options.replacementScopeId
                val latest=journal
                if(candidate!=null && latest?.revision==request.journalRevision &&
                    !state.busy && !swapLoading &&
                    NativeMovementSwapEligibility.generated(latest,candidate)) {
                    pendingConcern=null
                    reviewSwap(candidate)
                }
            },onDismiss={pendingConcern=null}
        )
    }
    pendingExerciseSkip?.let {request->
        val review=journal?.let {
            WorkoutRemainingExerciseDomain.generated(it,request.scopeId)
        }
        NativeKotlinRemainingExerciseSkipDialog(request,review,state.busy,
            onConfirm={
                controller.skipRemainingExercise(request.scopeId,request.revision)
                pendingExerciseSkip=null
            },onDismiss={pendingExerciseSkip=null})
    }
    val skip=allSets.firstOrNull{it.target.setKey==pendingSkip}
    if(skip!=null)AlertDialog(onDismissRequest={pendingSkip=null},
        title={Text("Skip this set?")},
        text={Column {
            Text("Skipping records an explicit outcome. You can correct it before finishing.")
            if(!skip.isResolved && journal!=null && journal.finishedByEventId==null) {
                TextButton(enabled=!state.busy,onClick={
                    pendingExerciseSkip=NativeExerciseSkipRequest(skip.target.exerciseId,journal.revision)
                    pendingSkip=null
                }) {Text("Skip remaining exercise…")}
            }
        }},
        confirmButton={Button(onClick={
            controller.record(skip.target.setKey,KotlinJournalSetOutcome.SKIPPED,null)
            pendingSkip=null
        },enabled=!state.busy){Text("Skip set")}},
        dismissButton={TextButton(onClick={pendingSkip=null}){Text("Cancel")}})
    if(pendingSkip!=null && skip==null)LaunchedEffect(pendingSkip){pendingSkip=null}
    val failed=allSets.firstOrNull{it.target.setKey==pendingFail?.first}
    if(failed!=null)AlertDialog(onDismissRequest={pendingFail=null},
        title={Text("Log a failed attempt?")},
        text={Text("This records zero completed reps, not a successful set or a progression signal.")},
        confirmButton={Button(onClick={
            // The user explicitly entered the attempted load. Never
            // fabricate an actual by copying the coach's target weight.
            controller.record(failed.target.setKey,
                KotlinJournalSetOutcome.FAILED_ATTEMPT,pendingFail?.second)
            pendingFail=null
        },enabled=!state.busy){Text("Log failed attempt")}},
        dismissButton={TextButton(onClick={pendingFail=null}){Text("Cancel")}})
}

@Composable
private fun NativeKotlinSetCard(
    set:KotlinWorkoutSetPresentation,
    finished:Boolean,
    busy:Boolean,
    saveError:String?,
    machineInstanceId:String?,
    athleteId:String,
    workoutId:String,
    issuedGymId:String?,
    machineRoster:NativeMachineRosterSnapshot?,
    referenceMode:WorkoutReferenceMode,
    lastReferences:WorkoutLastReferenceResult?,
    levelingDraft:CurrentWorkoutLevelingDraft?,
    onCurrentDraftChanged:(String,String)->Unit,
    position:Int,
    setCount:Int,
    plannedBackoffNote:String?,
    showExerciseHeading:Boolean,
    onHistory:()->Unit,
    onInfo:()->Unit,
    onShowPlates:(String,LoadUnit)->Unit,
    onRecord:(KotlinJournalSetOutcome,KotlinPerformedSet?)->Unit,
    onSkip:()->Unit,
    onFail:(KotlinPerformedSet)->Unit
) {
    val target=set.target
    val existing=set.actual
    // The independently verified workout/set/station defines the exact
    // private UI draft identity. Recovered values remain UNLOGGED input until
    // the athlete taps Complete; a saved receipt always wins over the draft.
    val draftIdentity=NativePendingWorkoutDraftIdentity.make(
        athleteId,workoutId,issuedGymId.orEmpty(),target.exerciseId,
        target.setKey,"MAIN",machineInstanceId,target.load,target.reps,target.targetRir)
    val draftBaseline=NativePendingWorkoutDraft(
        initialMachineScopedLoad(target.load,existing,machineInstanceId),
        existing?.reps?.toString() ?: target.reps.minimum.toString(),
        existing?.actualRir?.toString().orEmpty())
    val draftEditor=rememberNativePendingWorkoutDraftEditor(
        draftIdentity,set.isResolved,set.currentReceipt?.appliedRevision?.toString(),
        draftBaseline)
    val weight=draftEditor.load
    val reps=draftEditor.reps
    val effort=draftEditor.rir
    var appliedLevelVersion by rememberSaveable(
        target.setKey,set.currentReceipt?.appliedRevision,machineInstanceId
    ) {mutableIntStateOf(-1)}
    // Apply only to the unsaved editor of this exact station and set.
    // The guard prevents Focus/All toggles from overwriting later typed
    // actuals. Recording still uses the ordinary journal controller.
    LaunchedEffect(levelingDraft?.version,machineInstanceId,set.isResolved) {
        val draft=levelingDraft
        if(draft!=null && !set.isResolved &&
            appliedLevelVersion!=draft.version) {
            draftEditor.update(load=draft.weight,reps=draft.reps.toString())
            // Leveling changes only the entered load and reps, not actual RIR.
            appliedLevelVersion=draft.version
        }
    }
    // Keep nonvisible Focus/All editor drafts available for a deliberate
    // Leveling tap, including after rotation. Never report completed edits
    // as new current-session facts until they are saved as corrections.
    LaunchedEffect(target.setKey,machineInstanceId,weight,reps,
        set.isResolved,levelingDraft?.version,appliedLevelVersion) {
        // A newly mounted Focus editor may still contain its initial values
        // for one frame. Do not overwrite the already accepted Leveling
        // prefill while its versioned editor effect is being applied.
        val pendingLevel=levelingDraft?.let {
            !set.isResolved && appliedLevelVersion!=it.version
        } == true
        if(target.type==DraftSetType.WORKING && !set.isResolved &&
            !pendingLevel)onCurrentDraftChanged(weight,reps)
    }
    val actual=performed(target,weight,reps,effort)
    val failedActual=performed(target,weight,reps,effort,failed=true)
    val verifiedLoad=target.load as? DraftSetLoad.Verified
    val entry=KotlinWorkoutEntryDomain.parse(target,weight,reps,effort)
    val working=target.type==DraftSetType.WORKING
    // A correction is a separate explicit review, not a second always-open
    // logger. Close only when its exact durable receipt changes; a refused
    // write keeps the typed draft available for retry.
    var correcting by rememberSaveable(
        target.setKey,set.currentReceipt?.appliedRevision,machineInstanceId
    ) {mutableStateOf(false)}
    val entryFields:@Composable ()->Unit={
        Text(if(set.isResolved)
            "Review the values you recorded. Blank RIR stays unreported."
        else "Planned reps and load are editable starting points, not logged actuals. Leave actual RIR blank if unknown.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!set.isResolved && draftEditor.recovered) {
            Text("Recovered an unfinished entry for this exact workout and "+
                "machine. It has NOT been logged.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={draftEditor.discard()},enabled=!busy) {
                Text("Discard recovered entry")
            }
        }
        // Reuse the same keyboard and 48dp rep/RIR stepper controls as
        // other native set editors. No inferred load increments, no
        // transfer of target RIR into reported actual effort.
        if(!set.isResolved) PursuitUseLastCompletedSet(
            mode=referenceMode,
            history=physicalMachineLastReferences(lastReferences,machineInstanceId),
            key=WorkoutReferenceKey(target.setKey),
            currentLoad=target.load,
            enabled=!busy,
            onApply={last->
                // Last is already source verified for the exact set and load
                // basis; these are UNSAVED draft fields, not progression.
                draftEditor.update(
                    load=last.load?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    reps=last.reps.toString(),
                    rir=last.actualRir?.toString().orEmpty())
            }
        )
        NativeSetValueFields(
            unit=verifiedLoad?.unit?.name?.lowercase().orEmpty(),
            load=weight,
            reps=reps,
            rir=effort,
            enabled=!busy,
            onLoad={draftEditor.update(load=it)},
            onReps={draftEditor.update(reps=it)},
            onRir={draftEditor.update(rir=it)},
            showLoad=verifiedLoad!=null,
            allowFailedAttempt=existing?.reps==0,
            showFieldHelp=false
        )
        if(verifiedLoad!=null && KotlinVerifiedPlateLayoutDomain.supportsBasis(verifiedLoad.basis)) {
            val amount=weight.toBigDecimalOrNull()
            OutlinedButton(
                onClick={onShowPlates(weight,verifiedLoad.unit)},
                enabled=!busy && amount!=null &&
                    amount>=BigDecimal.ZERO && amount<=BigDecimal("100000") &&
                    amount.stripTrailingZeros().scale()<=3,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("View plates for entered weight")}
        }
        if(entry is KotlinSetEntryResult.Rejected &&
            (reps.isNotBlank() || (verifiedLoad!=null && weight.isBlank()) ||
                effort.isNotBlank())) {
            Text("Check ${entry.reason.name.replace('_',' ').lowercase()}",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
        PursuitSetEntryActions(
            completeLabel=if(set.isResolved)"Save correction" else "Complete",
            skipLabel="Skip",
            canComplete=!busy && actual!=null,
            canSkip=!busy,
            canFail=!busy && failedActual!=null,
            onComplete={onRecord(KotlinJournalSetOutcome.COMPLETED,actual)},
            onSkip=onSkip,
            onFail={failedActual?.let(onFail)}
        )
    }
    PursuitCard(highlighted=!set.isResolved && !finished) {
        if(showExerciseHeading)PursuitExerciseHeading(target.exerciseId,
            onHistory=onHistory,onInfo=onInfo)
        Text("${if(working)"Set" else "Warm-up"} $position of $setCount",
            style=MaterialTheme.typography.labelLarge)
        PursuitWorkoutSetReference(referenceMode,target.load,target.reps,target.targetRir,
            physicalMachineLastReferences(lastReferences,machineInstanceId),
            WorkoutReferenceKey(target.setKey))
        plannedBackoffNote?.let { note ->
            Text(note,style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(set.isResolved) {
            Text(nativeSavedSetCaption("Saved",set.outcome,existing,
                target.load,target.exerciseId,issuedGymId,machineRoster),
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium)
            // A first log is not an edit. Only receipt-derived correction
            // ancestry may label the current effective observation as edited.
            if(set.correctsEventId!=null)
                Text("Edited result",style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(finished) return@PursuitCard
        if(set.isResolved) {
            OutlinedButton(
                onClick={correcting=true},enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Edit saved set")}
        } else entryFields()

    }
    if(correcting && set.isResolved && !finished) {
        NativeSetEditSheet(busy=busy,onDismiss={correcting=false}) {
            PursuitSectionHeading("Edit saved set",kotlinExerciseLabel(target.exerciseId))
            Text("Saved values change only after you confirm the correction.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            saveError?.let {Text(it,color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)}
            PursuitWorkoutSetReference(referenceMode,target.load,target.reps,target.targetRir,
                physicalMachineLastReferences(lastReferences,machineInstanceId),
                WorkoutReferenceKey(target.setKey))
            entryFields()
            TextButton(enabled=!busy,onClick={correcting=false},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Keep saved set")
            }
        }
    }

}
