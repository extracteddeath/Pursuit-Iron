package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
    onReviewNext:(String)->Unit={}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var machineRoster by remember(host,workoutId) {
        mutableStateOf<NativeMachineRosterSnapshot?>(null)
    }
    LaunchedEffect(host,workoutId) {
        machineRoster=withContext(Dispatchers.IO) {
            (host.readMachineRoster() as? NativeMachineRosterResult.Saved)?.snapshot
        }
    }
    var swapSourceId by remember(workoutId){mutableStateOf<String?>(null)}
    var swapChoices by remember(workoutId){
        mutableStateOf<List<KotlinComposerSwapChoice>>(emptyList())
    }
    var swapLoading by remember(workoutId){mutableStateOf(false)}
    var swapError by remember(workoutId){mutableStateOf<String?>(null)}
    var undoConfirm by remember(workoutId){mutableStateOf<String?>(null)}
    var referenceMode by rememberWorkoutReferenceMode()
    var historyExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
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
            ?.map { it.exerciseId }?.distinct()?.joinToString(";").orEmpty())
    // Retain only the most recent VERIFIED journal reference for an open
    // note draft. A double storage failure must not discard unsaved text.
    // The cached reference never authorizes saving or marks a note saved.
    var noteAnchor by remember(workoutId) {
        mutableStateOf<KotlinWorkoutJournalState?>(null)
    }
    LaunchedEffect(journal) { if(journal!=null)noteAnchor=journal }
    val lastReferences=rememberWorkoutLastReferences(host,workoutId,custom=false,
        enabled=referenceMode==WorkoutReferenceMode.LAST && journal!=null)
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
    DisposableEffect(view,running) {
        val previous=view.keepScreenOn
        if(running)view.keepScreenOn=true
        onDispose{view.keepScreenOn=previous}
    }

    var showFinishConfirm by rememberSaveable(workoutId){mutableStateOf(false)}
    var showSessionNote by rememberSaveable(workoutId){mutableStateOf(false)}
    var pendingSkip by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var pendingFail by remember(workoutId) {
        mutableStateOf<Pair<String,KotlinPerformedSet>?>(null)
    }
    var selectedKey by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var showAll by rememberSaveable(workoutId){mutableStateOf(false)}
    var readiness by rememberSaveable(workoutId) {
        mutableStateOf(CoachReadiness.NORMAL)
    }
    var movementRestricted by rememberSaveable(workoutId) {
        mutableStateOf(false)
    }
    LaunchedEffect(presentation?.revision) {
        if(presentation!=null)
            selectedKey=presentation.firstUnresolvedSetKey ?:
                presentation.exercises.lastOrNull()?.sets?.lastOrNull()?.target?.setKey
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
    val selected=allSets.firstOrNull{it.target.setKey==selectedKey}
        ?:allSets.firstOrNull()
    val activeKeys=if(showAll)allSets else listOfNotNull(selected)
    val issuedGymId=journal?.originalSnapshot?.source?.gymId
    val activeMachineConfig=selected?.target?.exerciseId?.let {exerciseId->
        issuedGymId?.let {gymId->
            KotlinMachineAlternativesDomain.forGymExercise(
                machineRoster?.configurations.orEmpty(),gymId,exerciseId)
        }
    }
    val currentMachine=selected?.target?.exerciseId?.let{
        workoutMachineChoice(machineChoices.encoded,it,null)
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
    val coach=state.liveCoach?.takeIf {
        // A pre-restriction recommendation is unsafe to keep showing while
        // a fresh owner-scoped review is in flight.
        !movementRestricted && !state.busy &&
        machineChoices.ready && !machineChoices.busy &&
            it.journalRevision==presentation?.revision &&
            it.setKey==selected?.target?.setKey &&
            // A result for station A may linger for one Compose frame
            // while the new B review starts. Hide it synchronously.
            it.machineScope==expectedCoachMachineScope
    }

    if(showFinishConfirm && presentation?.canFinish==true) {
        AlertDialog(onDismissRequest={showFinishConfirm=false},
            title={Text("Finish this workout?")},
            text={Text("All prescribed sets have an outcome. Finishing saves a permanent event; it will not change target weights or automatically create another workout.")},
            confirmButton={
                Button(onClick={
                    showFinishConfirm=false
                    controller.finish()
                },enabled=!state.busy){Text("Finish workout")}
            },
            dismissButton={TextButton(onClick={showFinishConfirm=false}){
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
            if(presentation==null)"Native session preview · no JavaScript"
            else "${presentation.purpose.name.replace('_',' ').lowercase()} · "+
                "${presentation.estimatedMinutes} min estimated")
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(presentation==null) {
            PursuitCard(highlighted=true) {
                Text("Open a workout issued by the independent Kotlin coach.",
                    style=MaterialTheme.typography.titleMedium)
                Text(humanStatus(state.unavailableReason).ifBlank{
                    "No confirmed native workout has been loaded. Your existing app data is unchanged."
                })
                if(state.unavailableReason==NativeJournalFileRefusal.NOT_ISSUED) {
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
        if(journal!=null && journal.finishedByEventId==null) {
            OutlinedButton(
                onClick={showSessionNote=true},
                enabled=!state.busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {
                Text(if(journal.sessionNote.isBlank())
                    "Add session note" else "Edit saved session note")
            }
        } else if(journal?.sessionNote?.isNotBlank()==true) {
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
            presentation.exercises.forEach{ex->
                val selectedExercise=ex.sets.any{it.target.setKey==selected?.target?.setKey}
                FilterChip(selected=selectedExercise,
                    onClick={selectedKey=ex.nextUnresolvedSetKey
                        ?:ex.sets.last().target.setKey},
                    label={Text("${kotlinExerciseLabel(ex.exerciseId)} · ${ex.resolvedCount}/${ex.sets.size}")})
            }
        }
        val focusDetail=selected?.target?.let { target ->
            val typeSets=allSets.filter {
                it.target.exerciseId==target.exerciseId &&
                    it.target.type==target.type
            }
            val number=typeSets.indexOfFirst {
                it.target.setKey==target.setKey
            }+1
            "${if(target.type==DraftSetType.WARMUP)"Warm-up" else "Set"} $number of ${typeSets.size}"
        }
        PursuitWorkoutFocusHeader(
            exerciseLabel=selected?.target?.exerciseId?.let(::kotlinExerciseLabel),
            setDetail=focusDetail,
            focusOnNext=!showAll,
            onFocusChange={showAll=!it},
            enabled=!state.busy,
            heading=when {
                presentation.finished -> "WORKOUT REVIEW"
                selected?.target?.setKey==presentation.firstUnresolvedSetKey ->
                    "UP NEXT"
                else -> "SELECTED SET"
            },
            onHistory=selected?.target?.exerciseId?.let{exerciseId->
                {historyExerciseId=exerciseId}
            },
            onInfo=selected?.target?.exerciseId?.let{exerciseId->
                {infoExerciseId=exerciseId}
            }
        )
        if(!presentation.finished) {
            PursuitWorkoutReadinessPanel(
                readiness=readiness,
                onReadinessChange={readiness=it},
                movementRestricted=movementRestricted,
                onMovementRestrictedChange={movementRestricted=it},
                enabled=!state.busy,
                onReportConcern=if(journal!=null) ({
                    // Restrict optional advice immediately; never auto-record
                    // a set, alter a prescription or claim an injury note
                    // was saved before athlete confirmation.
                    movementRestricted=true
                    readiness=CoachReadiness.LOW
                    showSessionNote=true
                }) else null
            )
        }
        if(coach!=null && !presentation.finished)PursuitCard(
            highlighted=coach.coaching.direction==
                InWorkoutDirection.FOLLOW_PLANNED_BACKOFF) {
            Text("Coach · next set",style=MaterialTheme.typography.titleMedium)
            Text(coach.coaching.message,
                style=MaterialTheme.typography.bodyMedium)
            coach.coaching.weightForHumanReview?.let{weight->
                Text("Optional review: ${weight.stripTrailingZeros().toPlainString()} "+
                    "${selected?.target?.load.let{
                        (it as? DraftSetLoad.Verified)?.unit?.name?.lowercase()
                            ?: ""
                    }} · your original target is unchanged.",
                    style=MaterialTheme.typography.labelMedium)
            }
            Text("Based on saved sets · no automatic weight or rep changes",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PursuitWorkoutReferencePicker(referenceMode,{referenceMode=it})
        // Leveling is an explicit inspection of recorded working sets.
        // Never treat the mean of a pyramid as an approved future load.
        if(selected!=null && journal!=null) {
            val exerciseWorkingSets=allSets.filter {
                it.target.exerciseId==selected.target.exerciseId &&
                    it.target.type==DraftSetType.WORKING
            }
            val completedWorking=exerciseWorkingSets.filter {
                it.outcome==KotlinJournalSetOutcome.COMPLETED && it.actual?.load!=null
            }
            val loads=completedWorking.mapNotNull { it.actual?.load }
            val mixedLoads=loads.map { it.stripTrailingZeros() }.distinct().size>1
            val sameLoadUnit=completedWorking.map { it.actual?.unit }.distinct().size==1
            // Different physical stations cannot share one leveling baseline.
            val machineIds=completedWorking.map { it.actual?.machineInstanceId }.distinct()
            val samePhysicalStation=machineIds.size==1 &&
                (activeMachineConfig?.machines?.isNotEmpty()!=true ||
                    machineIds.singleOrNull()!=null)
            var showLevelingReview by remember(workoutId,selected.target.exerciseId) {
                mutableStateOf(false)
            }
            if(exerciseWorkingSets.size>=2) {
                OutlinedButton(
                    onClick={showLevelingReview=true},
                    enabled=mixedLoads && samePhysicalStation && sameLoadUnit &&
                        completedWorking.size==exerciseWorkingSets.size && !state.busy
                ) { Text("Leveling · review pyramid") }
                if(!sameLoadUnit && completedWorking.size>1)
                    Text("Leveling requires one consistent weight unit.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!samePhysicalStation && completedWorking.size>1)
                    Text("Leveling requires sets from the same physical machine. "+
                        "Machine A/B histories must remain separate.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(completedWorking.size!=exerciseWorkingSets.size)
                    Text("Complete every working set before reviewing Leveling. "+
                        "Skipped and failed attempts are not usable evidence.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!mixedLoads)
                    Text("Leveling is available after completing a mixed-load working-set session.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(showLevelingReview) {
                // Explicit stock entry is local to this review. Never infer
                // machine increments from a recorded weight or unit.
                var verifiedStockInput by remember(workoutId,selected.target.exerciseId) {
                    mutableStateOf("")
                }
                val enteredStock=remember(verifiedStockInput,completedWorking) {
                    val values=verifiedStockInput.split(",").map { it.trim() }
                    val parsed=values.map { runCatching { BigDecimal(it) }.getOrNull() }
                    val unit=completedWorking.firstOrNull()?.actual?.unit
                    if(values.size in 2..100 && parsed.all { it!=null } &&
                        parsed.filterNotNull().all { it>BigDecimal.ZERO } &&
                        parsed.filterNotNull().distinct().size==values.size &&
                        unit!=null)
                        VerifiedLoadStock.Totals(unit,LoadBasis.EXERCISE_OVERRIDE,
                            parsed.filterNotNull())
                    else null
                }
                val facts=completedWorking.mapNotNull { set ->
                    val actual=set.actual ?: return@mapNotNull null
                    actual.load?.let { LevelingSetFact(it,actual.reps,actual.actualRir) }
                }
                // A displayed candidate is informational, never an issued edit.
                // Until an authenticated program-edit API exists, there is no
                // approval button that could falsely imply persistence.
                val issuedBands=exerciseWorkingSets.map { it.target.reps }.distinct()
                val issuedRirs=exerciseWorkingSets.map { it.target.targetRir }.distinct()
                val review=if(facts.size==exerciseWorkingSets.size &&
                    samePhysicalStation && sameLoadUnit && mixedLoads &&
                    issuedBands.size==1 && issuedRirs.size==1 &&
                    issuedRirs.singleOrNull()!=null)
                    DoubleProgressionLevelingDomain.review(
                        facts,issuedBands.single(),issuedRirs.single()!!,enteredStock)
                else LevelingReviewResult.Rejected(LevelingRefusal.INCOMPLETE_OR_INVALID)
                AlertDialog(
                    onDismissRequest={showLevelingReview=false},
                    title={Text("Review double-progression leveling")},
                    text={
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Recorded working sets (not targets):")
                            completedWorking.forEach { set ->
                                val actual=set.actual
                                Text("${actual?.load?.stripTrailingZeros()?.toPlainString()} × "+
                                    "${actual?.reps} · RIR ${actual?.actualRir?.toString() ?: "not reported"}")
                            }
                            OutlinedTextField(
                                value=verifiedStockInput,
                                onValueChange={verifiedStockInput=it},
                                label={Text("Verified available loads (comma-separated)")},
                                supportingText={Text("Enter actual available machine or implement loads; "+
                                    "not an estimated increment.")},
                                singleLine=false
                            )
                            when(val candidate=review) {
                                is LevelingReviewResult.Candidate -> {
                                    Text("Observed mean: "+
                                        candidate.averageObservedLoad.toPlainString())
                                    Text("Available load for review: "+
                                        candidate.proposedStraightSetLoad.toPlainString()+
                                        " × ${candidate.repsPerSet} reps, "+
                                        "${candidate.setCount} sets")
                                    Text(candidate.note)
                                }
                                is LevelingReviewResult.Rejected ->
                                    Text("No supported leveling candidate: ${candidate.reason}. "+
                                        "Verify inventory and completed sets.")
                            }
                            if(issuedBands.size!=1 || issuedRirs.size!=1 ||
                                issuedRirs.singleOrNull()==null)
                                Text("Leveling requires one consistent issued rep band "+
                                    "and a verified goal RIR across the working sets.")
                            Text("No change has been applied. A future-program edit "+
                                "requires explicit approval and durable persistence.")
                        }
                    },
                    confirmButton={
                        TextButton(onClick={showLevelingReview=false}) {
                            Text("Keep existing plan")
                        }
                    }
                )
            }
        }

        if(selected!=null && journal!=null)
            NativeKotlinPinnedExerciseControl(
                host=host,workoutId=workoutId,
                exerciseId=selected.target.exerciseId,custom=false,
                enabled=!state.busy && !presentation.finished
            )
        NativeKotlinWorkoutMachineSelector(activeMachineConfig,currentMachine,
            enabled=!state.busy && !presentation.finished &&
                machineChoices.ready && !machineChoices.busy,
            onSelect={machineId->
                val exerciseId=selected?.target?.exerciseId
                if(exerciseId!=null)machineChoices.onSelect(exerciseId,machineId)
            })
        machineChoices.error?.let{
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
        activeKeys.forEach{set->
            val perSetMachineConfig=issuedGymId?.let {gymId->
                KotlinMachineAlternativesDomain.forGymExercise(
                    machineRoster?.configurations.orEmpty(),gymId,
                    set.target.exerciseId)
            }
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
                    machineInstanceId=recordedMachineId,
                    referenceMode=referenceMode,lastReferences=lastReferences,
                    position=allSets.filter{it.target.exerciseId==set.target.exerciseId &&
                        it.target.type==set.target.type}.indexOf(set)+1,
                    setCount=allSets.count{it.target.exerciseId==set.target.exerciseId &&
                        it.target.type==set.target.type},
                    showExerciseHeading=showAll || presentation.finished,
                    onHistory={historyExerciseId=set.target.exerciseId},
                    onInfo={infoExerciseId=set.target.exerciseId},
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
        if(!showAll && selected!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                val index=allSets.indexOfFirst{it.target.setKey==selected.target.setKey}
                OutlinedButton(onClick={selectedKey=allSets[index-1].target.setKey},
                    enabled=index>0) {Text("Previous set")}
                OutlinedButton(onClick={selectedKey=allSets[index+1].target.setKey},
                    enabled=index in 0 until allSets.lastIndex) {Text("Next set")}
            }
        }

        // Prioritize the actual set and one-tap reference first. A live
        // exercise swap remains available below it, never hidden or applied
        // automatically while the current set is being logged.
        val chosenExerciseId=selected?.target?.exerciseId
        if(chosenExerciseId!=null && journal!=null && !presentation.finished){
            val touched=journal.snapshot.targets.filter{
                it.exerciseId==chosenExerciseId
            }.any{it.setKey in journal.effectiveSets}
            val swapReceipt=journal.receipts.asReversed()
                .mapNotNull{
                    it.operation as? KotlinJournalOperation.SwapExercise
                }.firstOrNull{event->
                    event.replacementTargets.firstOrNull()?.exerciseId==
                        chosenExerciseId &&
                    journal.receipts.none{receipt->
                        (receipt.operation as? KotlinJournalOperation.UndoSwap)
                            ?.swapEventId==event.eventId
                    }
                }
            PursuitCard {
                Text("Exercise options",
                    style=MaterialTheme.typography.titleMedium)
                Text("Swap only this workout. The future program, "+
                    "other saved sets and past weights stay unchanged.",
                    style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={reviewSwap(chosenExerciseId)},
                        enabled=!state.busy && !swapLoading && !touched,
                        modifier=Modifier.weight(1f)) {
                        Text(if(swapLoading)"Reviewing…" else "Swap exercise")
                    }
                    if(swapReceipt!=null)
                        OutlinedButton(onClick={
                            undoConfirm=swapReceipt.eventId
                        },enabled=!state.busy && !touched,
                            modifier=Modifier.weight(1f)){
                            Text("Undo swap")
                        }
                }
                if(touched)
                    Text("This exercise has logged sets. Replacement is "+
                        "locked to protect its recorded history.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                swapError?.let {
                    Text(it,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.error)
                }
            }
        }
        if(presentation.canFinish)PursuitCard(highlighted=true) {
            Text("All sets have a saved outcome",
                style=MaterialTheme.typography.titleMedium)
            PursuitPrimaryAction("Review and finish",enabled=!state.busy){
                showFinishConfirm=true
            }
        }
        if(presentation.finished)PursuitCard(highlighted=true) {
            Text("Workout completion is saved",
                style=MaterialTheme.typography.titleMedium)
            Text("Your original targets and every actual, skip, failure and correction remain in the Kotlin journal.")
            when(verifiedFinish) {
                is NativeComposerFinishHandoffResult.Verified -> {
                    Text("Finish verified in committed native History. Your next workout remains unchanged until you explicitly review it.",
                        style=MaterialTheme.typography.bodySmall)
                    PursuitPrimaryAction("View finished workout in History",
                        enabled=!state.busy){onViewHistory()}
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
                enabled=!state.busy)
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
            NativeKotlinWorkoutExerciseInfoSheet(
                exerciseId=exerciseId,
                sourceDescription="Issued generated workout · "+
                    first.role.name.lowercase().replace('_',' ')+
                    " · "+first.purpose.name.lowercase().replace('_',' '),
                sets=composerWorkoutInfoSets(targets),
                onHistory={
                    infoExerciseId=null
                    historyExerciseId=exerciseId
                },
                onDismiss={infoExerciseId=null}
            )
        }
    }
    historyExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={historyExerciseId=null})
    }
    val skip=allSets.firstOrNull{it.target.setKey==pendingSkip}
    if(skip!=null)AlertDialog(onDismissRequest={pendingSkip=null},
        title={Text("Skip this set?")},
        text={Text("Skipping records an explicit outcome. You can correct it before finishing.")},
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
    machineInstanceId:String?,
    referenceMode:WorkoutReferenceMode,
    lastReferences:WorkoutLastReferenceResult?,
    position:Int,
    setCount:Int,
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
    // Key the unsaved editor to its PHYSICAL station. Selecting B must never
    // carry an A load into a new attempt, while a redraw of A cannot blank
    // a value the athlete has already typed.
    var weight by rememberSaveable(
        target.setKey,set.currentReceipt?.appliedRevision,machineInstanceId
    ) {
        mutableStateOf(initialMachineScopedLoad(target.load,existing,machineInstanceId))
    }
    var reps by rememberSaveable(target.setKey,set.currentReceipt?.appliedRevision){
        mutableStateOf(existing?.reps?.takeIf{it>0}?.toString()
            ?:target.reps.minimum.toString())
    }
    var effort by rememberSaveable(target.setKey,set.currentReceipt?.appliedRevision){
        mutableStateOf(existing?.actualRir?.toString().orEmpty())
    }
    val actual=performed(target,weight,reps,effort)
    val failedActual=performed(target,weight,reps,effort,failed=true)
    val verifiedLoad=target.load as? DraftSetLoad.Verified
    val entry=KotlinWorkoutEntryDomain.parse(target,weight,reps,effort)
    val working=target.type==DraftSetType.WORKING
    PursuitCard(highlighted=!set.isResolved && !finished) {
        if(showExerciseHeading)PursuitExerciseHeading(target.exerciseId,
            onHistory=onHistory,onInfo=onInfo)
        Text("${if(working)"Set" else "Warm-up"} $position of $setCount",
            style=MaterialTheme.typography.labelLarge)
        PursuitWorkoutSetReference(referenceMode,target.load,target.reps,target.targetRir,
            physicalMachineLastReferences(lastReferences,machineInstanceId),
            WorkoutReferenceKey(target.setKey))
        if(set.isResolved) {
            Text("Saved: ${set.outcome?.name?.replace('_',' ')?.lowercase()}"+
                (existing?.let{" · ${it.reps} reps"+
                    (it.load?.let{amount->" at ${amount.stripTrailingZeros().toPlainString()}"}?:"")+
                    (it.actualRir?.let{rir->" · ${rir} actual RIR"}?: " · actual RIR unknown")
                } ?: "")+
                (existing?.machineInstanceId?.let{" · machine $it"} ?: ""),
                color=MaterialTheme.colorScheme.primary,
                style=MaterialTheme.typography.bodyMedium)
        }
        if(finished) return@PursuitCard
        Text("Planned reps and load are editable starting points, not logged actuals. Leave actual RIR blank if unknown.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                weight=last.load?.stripTrailingZeros()?.toPlainString().orEmpty()
                reps=last.reps.toString()
                effort=last.actualRir?.toString().orEmpty()
            }
        )
        NativeSetValueFields(
            unit=verifiedLoad?.unit?.name?.lowercase().orEmpty(),
            load=weight,
            reps=reps,
            rir=effort,
            enabled=!busy,
            onLoad={weight=it},
            onReps={reps=it},
            onRir={effort=it},
            showLoad=verifiedLoad!=null,
            showFieldHelp=false
        )
        if(verifiedLoad?.basis==LoadBasis.BARBELL_TOTAL) {
            val amount=weight.toBigDecimalOrNull()
            OutlinedButton(
                onClick={onShowPlates(weight,verifiedLoad.unit)},
                enabled=!busy && amount!=null &&
                    amount>=BigDecimal.ZERO && amount<=BigDecimal("100000") &&
                    amount.stripTrailingZeros().scale()<=3,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("View plates for entered barbell weight")}
        }
        if(entry is KotlinSetEntryResult.Rejected &&
            (reps.isNotBlank() || (verifiedLoad!=null && weight.isBlank()) ||
                effort.isNotBlank())) {
            Text("Check ${entry.reason.name.replace('_',' ').lowercase()}",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
        PursuitPrimaryAction(
            if(set.isResolved)"Save corrected set" else "Complete set",
            enabled=!busy && actual!=null
        ) {onRecord(KotlinJournalSetOutcome.COMPLETED,actual)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onSkip,enabled=!busy){Text("Skip")}
            TextButton(onClick={failedActual?.let(onFail)},
                enabled=!busy && failedActual!=null){Text("Failed attempt")}
        }
    }
}
