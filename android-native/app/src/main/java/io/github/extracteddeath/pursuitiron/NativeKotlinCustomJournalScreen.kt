package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct Kotlin CUSTOM journal: never translates an authored workout through
 * the generic composer, JS engine, or an unverified UI cache. A Record always
 * uses the latest durable revision and real submitted observations. Finish
 * enters the native history redo transaction, not the screen's local state.
 */
@Composable
internal fun NativeKotlinCustomJournalScreen(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    onClose:()->Unit,
    onViewHistory:()->Unit={},
    onReturnToProgram:()->Unit={},
    onReviewSwapInProgram:(NativeKotlinProgramSwapHandoff)->Unit={}
) {
    val displayPreferences=rememberNativeWorkoutDisplayStore()
    val display by displayPreferences.state.collectAsState()
    val initialFocus=display.startLayout.startsInFocus(LocalConfiguration.current.screenWidthDp)
    val scope=rememberCoroutineScope()
    var machineRoster by remember(host,workoutId){
        mutableStateOf<NativeMachineRosterSnapshot?>(null)
    }
    var eligibleMachineExercises by remember(host,workoutId) {
        mutableStateOf<Set<String>>(emptySet())
    }
    LaunchedEffect(host,workoutId) {
        val recovered=withContext(Dispatchers.IO) {
            Pair((host.readMachineRoster() as? NativeMachineRosterResult.Saved)?.snapshot,
                host.workoutMachineEligibleExerciseIds(workoutId,custom=true))
        }
        machineRoster=recovered.first
        eligibleMachineExercises=recovered.second
    }
    val workoutScroll=rememberScrollState()
    var showFinishedSets by rememberSaveable(workoutId){mutableStateOf(false)}
    var focusOnNext by rememberSaveable(workoutId){mutableStateOf(initialFocus)}
    // Custom Focus supports reviewing past and future main/follow-up visits
    // without changing the journal's next unresolved visit or coaching.
    // Only serializable values survive Activity recreation. A swapped/removed
    // set key can never be promoted into a valid active focus.
    var reviewSetKey by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var reviewPart by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var reviewStage by rememberSaveable(workoutId){mutableIntStateOf(1)}
    var showSessionNote by rememberSaveable(workoutId){mutableStateOf(false)}
    var pinnedSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    // Only a read-only navigation destination, scoped to this issued workout.
    var supersetRoundGroup by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    val editorStates=rememberSaveableStateHolder()
    // One Kotlin Leveling behavior for generated and custom set editors.
    val currentEditors=rememberSaveable(workoutId,
        saver=currentWorkoutEditorSaver) {
        mutableStateMapOf<String,CurrentWorkoutEditorValue>()
    }
    var levelSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var levelMachineId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var levelWeight by rememberSaveable(workoutId){mutableStateOf("")}
    var levelReps by rememberSaveable(workoutId){mutableIntStateOf(0)}
    var levelSetKeys by rememberSaveable(workoutId) { mutableStateOf(emptyList<String>()) }
    var levelVersion by rememberSaveable(workoutId){mutableIntStateOf(0)}
    // A previous completed workout is an optional source-verified review.
    // Only a deliberate athlete tap may populate current unsaved editors.
    var priorLevelingSlotId by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    val referenceMode=display.referenceMode
    var historyExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoWarmupsOnly by rememberSaveable(workoutId){mutableStateOf(false)}
    var plateRequest by remember(workoutId){
        mutableStateOf<NativeCustomPlateRequest?>(null)
    }
    var swapSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var openPhysicalMachineForSlot by remember(workoutId){
        mutableStateOf<String?>(null)
    }
    var undoSwapConfirmEventId by remember(workoutId){mutableStateOf<String?>(null)}
    var swapChoices by remember(workoutId) {
        mutableStateOf<List<CustomExercisePickerOption>>(emptyList())
    }
    var journal by remember(host,workoutId) {
        mutableStateOf<CustomWorkoutJournalDraft?>(null)
    }
    val machineChoices=rememberNativeWorkoutMachineChoices(
        host,workoutId,custom=true,ready=journal!=null,
        exerciseSignature=journal?.snapshot?.slots
            ?.map{it.exerciseId}?.distinct()?.joinToString(";").orEmpty(),
        roster=machineRoster,onRosterChanged={machineRoster=it})
    var busy by remember(host,workoutId){mutableStateOf(false)}
    // The system Back gesture and visible Minimize are the same navigation.
    // Consume Back during an in-flight disk write instead of dropping the
    // journal screen and risking a stale late callback.
    BackHandler {
        if(!busy)onClose()
    }
    var loading by remember(host,workoutId){mutableStateOf(true)}
    var error by remember(host,workoutId){mutableStateOf<String?>(null)}
    var noteError by remember(host,workoutId){mutableStateOf<String?>(null)}
    var finished by remember(host,workoutId){mutableStateOf(false)}
    // Finish consent is scoped to the precise journal revision on review.
    var finishReview by remember(host,workoutId){
        mutableStateOf<NativeFinishReviewToken?>(null)
    }
    // An explicit current-visit action only. It never authorizes a stored
    // skip, and cannot move to another visit while a confirmation is open.
    var pendingQuickSkip by remember(workoutId) {
        mutableStateOf<CustomWorkoutVisitKey?>(null)
    }
    var pendingExerciseSkip by remember(workoutId) {
        mutableStateOf<NativeExerciseSkipRequest?>(null)
    }
    // One session-only safety guide; the durable custom journal remains
    // the sole owner of any later confirmed set outcome.
    var pendingConcern by remember(workoutId) {
        mutableStateOf<NativeMovementConcernRequest?>(null)
    }
    var verifiedFinishedInHistory by remember(host,workoutId){
        mutableStateOf(false)
    }
    var readiness by rememberSaveable(workoutId){mutableStateOf(CoachReadiness.NORMAL)}
    var movementRestricted by rememberSaveable(workoutId){mutableStateOf(false)}
    var liveCoach by remember(host,workoutId){
        mutableStateOf<LiveSetCoachResult.Reviewed?>(null)
    }
    // Reveal success only after the independent committed-History read.
    LaunchedEffect(workoutId,verifiedFinishedInHistory) {
        if(verifiedFinishedInHistory)workoutScroll.animateScrollTo(0)
    }
    val view=LocalView.current
    val running=journal!=null && !finished && journal?.finishedByEventId==null
    DisposableEffect(view,running,display.keepScreenAwake) {
        val previous=view.keepScreenOn
        if(running && display.keepScreenAwake)view.keepScreenOn=true
        onDispose{view.keepScreenOn=previous}
    }

    suspend fun verifyCommittedHistory():Boolean {
        val found=withContext(Dispatchers.IO) {
            runCatching {host.reviewVerifiedKotlinHistory()}.getOrNull()
        }
        return (found as? NativeKotlinMixedHistoryReadResult.Review)
            ?.timeline?.completions?.any {
                it is NativeKotlinMixedHistoryCompletion.Custom &&
                    it.manifest.workoutId==workoutId &&
                    it.evidence.workoutId==workoutId
            } ==true
    }

    fun refresh() {
        if(busy)return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching {host.openIssuedCustomWorkout(workoutId)}.getOrNull()
            }
            val saved=result as? NativeCustomJournalResult.Durable
            journal=saved?.issued?.journal
            finished=saved?.issued?.journal?.finishedByEventId!=null
            verifiedFinishedInHistory=if(finished)verifyCommittedHistory()
                else false
            error=if(saved==null)
                "This saved workout could not be verified. Try reloading it."
            else null
            loading=false;busy=false
        }
    }
    LaunchedEffect(host,workoutId){refresh()}

    // Reopening a live custom swap after rotation must fetch a NEW source
    // review. Never save an earlier picker list as if it were authorization.
    LaunchedEffect(host,workoutId,swapSlotId,journal?.revision) {
        val requested=swapSlotId ?:return@LaunchedEffect
        val active=journal ?:return@LaunchedEffect
        if(swapChoices.isNotEmpty())return@LaunchedEffect
        val slot=active.snapshot.slots.firstOrNull { it.slotId==requested }
        if(slot==null) {
            swapSlotId=null
            error="The saved exercise was replaced. Reopen a current swap review."
            return@LaunchedEffect
        }
        val eligible=active.finishedByEventId==null &&
            slot.supersetGroup==null && slot.sets.none { set ->
                active.receipts.any { receipt ->
                    (receipt.operation as? CustomWorkoutJournalOperation.Record)
                        ?.visit?.setKey==set.setKey
                }
            }
        if(!eligible) {
            swapSlotId=null
            error="The saved exercise changed or already has recorded work. Reopen a current swap review."
            return@LaunchedEffect
        }
        val source=withContext(Dispatchers.IO) {
            (runCatching { host.readCustomAuthoringSource() }.getOrNull()
                as? NativeAuthoringSourceRead.Found)?.source
        }
        val reviewed=source?.setup?.reviewed
        val stamp=active.originalSnapshot.stamp
        val ready=source!=null && reviewed!=null &&
            source.athleteId==stamp.athleteId &&
            source.revision==stamp.gymCatalogSourceRevision &&
            reviewed.athleteProfileId==stamp.athleteId &&
            reviewed.profileRevision==stamp.athleteProfileRevision &&
            reviewed.gym.gymId==stamp.gymId
        val alternatives=if(ready)checkNotNull(source).let {
            CustomExerciseAuthoringDomain.picker(it.setup,it.catalog)
        } else emptyList()
        if(swapSlotId==requested && journal?.revision==active.revision) {
            if(alternatives.any { it.exerciseId!=slot.exerciseId }) {
                swapChoices=alternatives
            } else {
                swapSlotId=null
                error="The gym or exercise source changed. Reopen alternatives to review current choices."
            }
        }
    }

    fun record(
        setKey:String,part:CustomWorkoutVisitPart,
        outcome:KotlinJournalSetOutcome,performed:KotlinPerformedSet?,
        stage:Int=1
    ) {
        if(busy || !machineChoices.ready || machineChoices.busy)return
        val current=journal ?:return
        val visit=CustomWorkoutVisitKey(setKey,part,stage)
        val prior=current.effectiveVisits[visit]
        val correction=prior?.operation?.eventId
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching {
                    host.recordIssuedCustomSet(workoutId,
                        CustomWorkoutJournalOperation.Record(
                            "custom-set-"+UUID.randomUUID(),
                            current.revision,visit,outcome,performed,
                            correction))
                }.getOrNull()
            }
            if(result is NativeCustomJournalResult.Durable) {
                journal=result.issued.journal;error=null
                // Follow the next unlogged visit after a manually focused
                // entry was actually saved. Rejected disk writes retain
                // both the selected visit and the editable draft.
                if(reviewSetKey==visit.setKey && reviewPart==visit.part.name &&
                    reviewStage==visit.stage) {
                    reviewSetKey=null
                    reviewPart=null
                }
            } else {
                error="Your set could not be saved. The last saved version has been reloaded; check it before trying again."
                val recovery=withContext(Dispatchers.IO){
                    runCatching{host.openIssuedCustomWorkout(workoutId)}.getOrNull()
                }
                journal=(recovery as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
            }
            busy=false
        }
    }
    fun skipRemainingExercise(request:NativeExerciseSkipRequest) {
        val current=journal ?:return
        if(busy || current.finishedByEventId!=null ||
            current.revision!=request.revision)return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching {
                    host.skipRemainingCustomExercise(workoutId,request.revision,request.scopeId)
                }.getOrNull()
            }
            if(result is NativeCustomJournalResult.Durable) {
                journal=result.issued.journal
                error=null
                reviewSetKey=null
                reviewPart=null
            } else {
                error="Skipping stopped. Review the saved entries before skipping the rest."
                val recovered=withContext(Dispatchers.IO) {
                    runCatching{host.openIssuedCustomWorkout(workoutId)}.getOrNull()
                }
                journal=(recovered as? NativeCustomJournalResult.Durable)?.issued?.journal
            }
            busy=false
        }
    }

    fun saveSessionNote(text:String) {
        if(busy)return
        val current=journal ?:return
        if(current.finishedByEventId!=null)return
        busy=true
        noteError=null
        scope.launch {
            val operation=CustomWorkoutJournalOperation.SessionNote(
                "custom-note-"+UUID.randomUUID(),current.revision,text)
            val result=withContext(Dispatchers.IO) {
                runCatching {
                    host.saveIssuedCustomSessionNote(workoutId,operation)
                }.getOrNull()
            }
            if(result is NativeCustomJournalResult.Durable) {
                journal=result.issued.journal
                noteError=null
            } else {
                noteError="The session note was not saved. The draft is still here; review it before trying again."
                val recovered=withContext(Dispatchers.IO) {
                    runCatching{host.openIssuedCustomWorkout(workoutId)}.getOrNull()
                }
                // A second storage failure must NOT unmount the note
                // sheet or destroy the athlete's unsaved text draft.
                // Show the refusal and retain the last verified journal.
                val reopened=(recovered as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
                if(reopened!=null)journal=reopened
            }
            busy=false
        }
    }

    fun reviewSwap(slot:CustomWorkoutSlotSnapshot) {
        if(busy)return
        val active=journal ?:return
        if(active.finishedByEventId!=null || slot.supersetGroup!=null ||
            slot.sets.any {set->
                active.receipts.any {receipt->
                    (receipt.operation as? CustomWorkoutJournalOperation.Record)
                        ?.visit?.setKey==set.setKey
                }
            })return
        busy=true
        scope.launch {
            val source=(withContext(Dispatchers.IO) {
                runCatching{host.readCustomAuthoringSource()}.getOrNull()
            } as? NativeAuthoringSourceRead.Found)?.source
            val reviewed=source?.setup?.reviewed
            val stamp=active.originalSnapshot.stamp
            val ready=source!=null && reviewed!=null &&
                source.athleteId==stamp.athleteId &&
                source.revision==stamp.gymCatalogSourceRevision &&
                reviewed.athleteProfileId==stamp.athleteId &&
                reviewed.profileRevision==stamp.athleteProfileRevision &&
                reviewed.gym.gymId==stamp.gymId
            val options=if(ready)checkNotNull(source).let{
                CustomExerciseAuthoringDomain.picker(it.setup,it.catalog)
            } else emptyList()
            if(journal?.revision==active.revision && ready &&
                options.any {it.exerciseId!=slot.exerciseId}) {
                swapChoices=options
                swapSlotId=slot.slotId
                error=null
            } else {
                error="Live swapping requires the same reviewed gym and source revision as this issued workout, plus at least one verified alternative. The original workout is unchanged."
            }
            busy=false
        }
    }
    fun confirmSwap(
        slotId:String,exerciseId:String,
        sets:List<CustomAuthoredSet>,note:String
    ) {
        if(busy)return
        val active=journal ?:return
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO){
                runCatching{host.swapIssuedCustomExercise(
                    workoutId,"live-swap-"+UUID.randomUUID(),
                    active.revision,slotId,exerciseId,sets,note)
                }.getOrNull()
            }
            if(result is NativeCustomJournalResult.Durable) {
                journal=result.issued.journal
                error=null
            } else {
                val refusal=result as? NativeCustomJournalResult.Rejected
                error=when(refusal?.reason) {
                    NativeCustomJournalRefusal.SWAP_SOURCE_UNAVAILABLE ->
                        "The gym or source revision changed. No swap was saved. Recheck the original workout and gym."
                    NativeCustomJournalRefusal.SWAP_TARGET_UNAVAILABLE ->
                        "This exercise, equipment or attachment is not currently verified. Nothing was changed."
                    NativeCustomJournalRefusal.SWAP_INVALID_SETS ->
                        "Check the new load, rep range, technique and available increments. No targets were changed."
                    else -> "Swap was not saved: ${refusal?.journalReason ?: refusal?.reason ?: "storage unavailable"}. The last saved workout will be reopened."
                }
                val restored=withContext(Dispatchers.IO){
                    runCatching{host.openIssuedCustomWorkout(workoutId)}.getOrNull()
                }
                val recovered=(restored as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
                if(recovered!=null)journal=recovered
            }
            if(result is NativeCustomJournalResult.Durable ||
                (result as? NativeCustomJournalResult.Rejected)?.reason==
                    NativeCustomJournalRefusal.SWAP_SOURCE_UNAVAILABLE) {
                swapSlotId=null
                swapChoices=emptyList()
            }
            busy=false
        }
    }
    fun confirmUndoSwap(swapEventId:String) {
        if(busy)return
        val active=journal ?:return
        undoSwapConfirmEventId=null
        busy=true
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching {host.undoIssuedCustomExerciseSwap(
                    workoutId,"undo-swap-"+UUID.randomUUID(),
                    active.revision,swapEventId)}.getOrNull()
            }
            if(result is NativeCustomJournalResult.Durable) {
                journal=result.issued.journal
                error=null
            } else {
                val refused=result as? NativeCustomJournalResult.Rejected
                error=when(refused?.journalReason) {
                    CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED ->
                        "The replacement has recorded work and cannot be undone. No work was changed."
                    CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE ->
                        "That replacement is no longer active or was already undone."
                    CustomWorkoutJournalRefusal.STALE_REVISION ->
                        "Your workout changed. Reload the saved version before trying again."
                    else ->
                        "Undo was not saved. The last durable workout will be reloaded."
                }
                val restored=withContext(Dispatchers.IO) {
                    runCatching {host.openIssuedCustomWorkout(workoutId)}
                        .getOrNull()
                }
                journal=(restored as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
            }
            busy=false
        }
    }
    val current=journal
    if(showSessionNote && current!=null)NativeKotlinCustomSessionNoteSheet(
        journal=current,
        busy=busy,
        error=noteError,
        onSave={saveSessionNote(it)},
        onDismiss={showSessionNote=false;noteError=null}
    )
    var lastReferenceRefresh by remember(workoutId){mutableIntStateOf(0)}
    var stationHistoryRequested by rememberSaveable(workoutId){mutableStateOf(false)}
    val lastReferences=rememberWorkoutLastReferences(host,workoutId,custom=true,
        enabled=(referenceMode==WorkoutReferenceMode.LAST ||
            priorLevelingSlotId!=null || stationHistoryRequested) && current!=null,
        source=current?.snapshot?.let{WorkoutLastReferenceInput.Custom(it)},
        refreshVersion=lastReferenceRefresh)
    val nextVisit=current?.let{CustomWorkoutJournalDomain.nextUnresolvedVisit(it)}
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(workoutScroll)
        .widthIn(max=920.dp).align(Alignment.CenterHorizontally)
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            Text("YOUR WORKOUT",style=MaterialTheme.typography.labelLarge)
            TextButton(enabled=!busy,onClick=onClose){Text("Minimize")}
        }
        if(loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            if(current==null) {
                PursuitCard {
                    Text("Workout unavailable",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Reopen your saved workout from Plan, or try loading it again.")
                    Button(enabled=!busy,onClick={refresh()}){Text("Try again")}
                }
            } else {
                // Focus is presentation-only: it changes neither the owned
                // journal nor the active prescription. Show every entry when
                // no unresolved visit remains so Finish can be reviewed.
                val availableVisits=CustomWorkoutVisitFocusDomain.visibleOrder(current)
                val executionSteps=remember(current.snapshot) {
                    CustomWorkoutExecutionDomain.orderedSets(current.snapshot)
                }
                val requestedPart=CustomWorkoutVisitPart.entries.firstOrNull {
                    it.name==reviewPart
                }
                val requestedFocus=reviewSetKey?.let{key->
                    requestedPart?.let{part->
                        CustomWorkoutVisitKey(key,part,reviewStage)
                    }
                }?.takeIf{it in availableVisits}
                val activeFocus=(requestedFocus ?: nextVisit).takeIf {
                    focusOnNext && !finished && current.finishedByEventId==null
                }
                val visitProgress=CustomWorkoutVisitFocusDomain.progress(current)
                // Only committed observations can affect advice. A new set,
                // correction, readiness selection or in-flight write clears
                // the old projection; canceled callbacks cannot restore it.
                LaunchedEffect(host,workoutId,current.revision,nextVisit,
                    readiness,movementRestricted,busy,
                    machineChoices.encoded,machineChoices.ready,machineChoices.busy) {
                    liveCoach=null
                    if(busy || !machineChoices.ready || machineChoices.busy ||
                        current.finishedByEventId!=null ||
                        nextVisit?.part!=CustomWorkoutVisitPart.MAIN)
                        return@LaunchedEffect
                    // Advisory storage failure is not a failed set or
                    // workout. The owner-verified journal remains editable,
                    // and no stale advice is reused. A cancelled screen must
                    // not publish a delayed coaching result after navigation.
                    val result=try {
                        withContext(Dispatchers.IO) {
                            host.reviewLiveCustomNextSet(workoutId,nextVisit.setKey,
                                readiness,movementRestricted,BigDecimal("5"))
                        } as? LiveSetCoachResult.Reviewed
                    } catch(cancelled:CancellationException) {
                        throw cancelled
                    } catch(_:Exception) {
                        null
                    }
                    if(journal?.revision==current.revision && !busy &&
                        result?.journalRevision==current.revision &&
                        result.setKey==nextVisit.setKey)
                        liveCoach=result
                }
                PageHeading("TODAY'S TRAINING",
                    current.snapshot.dayLabel,
                    "Week ${current.snapshot.absoluteWeek} · ${current.snapshot.phase.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}}")
                current.originalSnapshot.shortSessionLimitMinutes?.let {minutes->
                    val omitted=current.originalSnapshot.shortSessionOmittedSetKeys.size
                    PursuitCard {
                        PursuitSectionHeading(
                            "$minutes-minute saved custom session",
                            if(omitted==0)
                                "The full authored session fits the selected time."
                            else "$omitted optional authored set(s) were omitted before issuance."
                        )
                        Text(
                            if(omitted==0)
                                "Your original set targets are unchanged."
                            else "Only the reviewed retained sets count toward this workout. Omitted sets were not skipped, failed or completed. Your original custom program is unchanged.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PursuitCard(highlighted=true) {
                    Text("${visitProgress.recordedVisits} of ${visitProgress.requiredVisits} entries recorded",
                        style=MaterialTheme.typography.titleLarge)
                    Text(
                        "${visitProgress.recordedMainSets} of ${visitProgress.totalMainSets} main sets" +
                            if(visitProgress.requiredFollowUps>0)
                                " · ${visitProgress.recordedFollowUps} of ${visitProgress.requiredFollowUps} amplifier follow-ups"
                            else "",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(
                        progress={visitProgress.fraction},
                        modifier=Modifier.fillMaxWidth())
                    if(!finished && current.finishedByEventId==null && !focusOnNext) {
                        OutlinedButton(
                            onClick={noteError=null;showSessionNote=true},
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        ) {
                            Text(if(current.sessionNote.isBlank())
                                "Add session note" else "Edit saved session note")
                        }
                    } else if((finished || current.finishedByEventId!=null) &&
                        current.sessionNote.isNotBlank()) {
                        Text("Saved session note",
                            style=MaterialTheme.typography.labelLarge,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(current.sessionNote,
                            style=MaterialTheme.typography.bodyMedium)
                    }
                    if(verifiedFinishedInHistory) {
                        Text("Workout saved",style=MaterialTheme.typography.headlineSmall,
                            color=MaterialTheme.colorScheme.primary)
                        Text("Your results are saved in workout history.",
                            style=MaterialTheme.typography.bodyMedium)
                        NativeKotlinVerifiedFinishRecap(
                            host=host,workoutId=workoutId,isCustom=true)
                        PursuitPrimaryAction("View finished workout in history",enabled=!busy,
                            onClick=onViewHistory)
                        OutlinedButton(onClick=onReturnToProgram,enabled=!busy,
                            modifier=Modifier.fillMaxWidth()) {
                            Text("Continue to my next workout")
                        }
                        val programReviews=NativeKotlinProgramSwapHandoffDomain
                            .review(current)
                        if(programReviews.isNotEmpty()){
                            HorizontalDivider()
                            Text("Use a workout swap in future training?",
                                style=MaterialTheme.typography.titleMedium)
                            Text("Your workout replacement was saved only "+
                                "for this session. Open the exact program "+
                                "day for a separate exercise review. New "+
                                "sets, gym availability, week overrides "+
                                "and full-cycle audit still require approval.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            programReviews.forEach{request->
                                OutlinedButton(
                                    enabled=!busy,
                                    modifier=Modifier.fillMaxWidth(),
                                    onClick={onReviewSwapInProgram(request)}
                                ){
                                    Text("Review ${kotlinExerciseLabel(
                                        request.workoutReplacementExerciseId
                                    )} in my program")
                                }
                            }
                        }
                        TextButton(onClick={showFinishedSets=!showFinishedSets}) {
                            Text(if(showFinishedSets)"Hide recorded sets" else "View recorded sets")
                        }
                    } else if(finished||current.finishedByEventId!=null) {
                        Text("Finish is saved. History verification needs to be completed before you advance.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("${current.unresolvedVisitCount} entries left. Confirm each set to save it.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(enabled=!busy,
                        onClick={refresh()}){Text("Refresh saved workout")}
                }
                if(!finished && current.finishedByEventId==null) {
                    val upcoming=activeFocus?.let {visit->
                        current.snapshot.slots.firstOrNull {slot->
                            slot.sets.any{it.setKey==visit.setKey}
                        }?.let{slot->slot to slot.sets.first{it.setKey==visit.setKey}}
                    }
                    val focusDetail=upcoming?.let { (slot,set) ->
                        // Warm-ups, main working sets and amplifier visits
                        // share one slot, but the display ordinal must follow
                        // the same set-type group as the actual set editor.
                        val sameType=slot.sets.filter {
                            it.prescription.type==set.prescription.type
                        }
                        val number=sameType.indexOfFirst {
                            it.setKey==set.setKey
                        }+1
                        val label=if(set.prescription.type==DraftSetType.WARMUP)
                            "Warm-up" else "Set"
                        if(activeFocus?.part==CustomWorkoutVisitPart.FOLLOW_UP)
                            set.prescription.amplifier?.let {
                                AdvancedAmplifierProtocolDomain.stageLabel(
                                    it,activeFocus.stage)
                            } ?: "Amplifier follow-up"

                        else
                            "$label $number of ${sameType.size}"
                    }
                    PursuitWorkoutFocusHeader(
                        exerciseLabel=upcoming?.let {
                            kotlinExerciseLabel(it.first.exerciseId)
                        },
                        setDetail=focusDetail,
                        focusOnNext=focusOnNext,
                        onFocusChange={
                            displayPreferences.layout(if(it)WorkoutStartLayout.FOCUS else WorkoutStartLayout.ALL_SETS)
                            focusOnNext=it
                            if(it) {
                                reviewSetKey=null
                                reviewPart=null
                            }
                        },
                        enabled=!busy,
                        heading=when {
                            !focusOnNext -> "ALL SETS"
                            requestedFocus!=null -> "REVIEW SET"
                            else -> "UP NEXT"
                        },
                        onHistory=upcoming?.first?.exerciseId?.let{exerciseId->
                            {historyExerciseId=exerciseId}
                        },
                        onInfo=upcoming?.first?.slotId?.let{slotId->
                            {infoSlotId=slotId}
                        },
                        onSkip=activeFocus?.takeIf {
                            it==nextVisit && machineChoices.ready &&
                                !machineChoices.busy
                        }?.let { visit ->
                            {pendingQuickSkip=visit}
                        }
                    )
                    val focusedRound=executionSteps.firstOrNull {
                        it.set.setKey==activeFocus?.setKey
                    }?.round
                    if(focusedRound!=null) {
                        Text("Superset · round ${focusedRound.number} of ${focusedRound.total}",
                            style=MaterialTheme.typography.labelLarge,
                            color=MaterialTheme.colorScheme.primary)
                        Text(executionSteps.filter{it.round==focusedRound}.mapIndexed{index,step->
                            "${index+1}. ${kotlinExerciseLabel(current.snapshot.slots[step.slotIndex].exerciseId)}"
                        }.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        Text(if(focusedRound.setKeys.size==1)
                            "This round has one movement. Use its normal rest."
                            else "Alternate movements, then rest. Complete any amplifier follow-up before switching.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val activeSupersetGroup=upcoming?.first?.supersetGroup
                        ?.takeIf {group->
                            executionSteps.any{it.round?.groupId==group}
                        }
                    if(focusOnNext)NativeKotlinWorkoutTools(
                        enabled=!busy,
                        onSessionNote={noteError=null;showSessionNote=true},
                        // Machine A/B lives in Exercise Info, not the Focus rail.
                        onMachine=null,
                        onSuperset=activeSupersetGroup?.let {group->
                            {supersetRoundGroup=group}
                        },
                        onPinnedNote=upcoming?.first?.let {slot->
                            {pinnedSlotId=slot.slotId}
                        },
                        onSwap=null,
                        onWarmup=upcoming?.first?.takeIf {slot->
                            slot.sets.any{it.prescription.type==DraftSetType.WARMUP}
                        }?.let {slot->{
                            infoWarmupsOnly=true
                            infoSlotId=slot.slotId
                        }}
                    )
                    supersetRoundGroup?.let {group->
                        if(executionSteps.any{it.round?.groupId==group}) {
                            NativeKotlinSupersetRoundSheet(
                                journal=current,groupId=group,focus=activeFocus,
                                busy=busy,
                                onChoose={visit->
                                    // A stale sheet cannot jump to an old or
                                    // untriggered follow-up. Focus only; the
                                    // journal and program are never mutated.
                                    val latest=journal
                                    val currentVisit=visit in
                                        CustomWorkoutVisitFocusDomain.visibleOrder(current)
                                    val sameIssuedGroup=executionSteps.any{step->
                                        step.round?.groupId==group &&
                                            step.set.setKey==visit.setKey
                                    }
                                    if(!busy && latest?.revision==current.revision &&
                                        currentVisit && sameIssuedGroup) {
                                        reviewSetKey=visit.setKey
                                        reviewPart=visit.part.name
                                        reviewStage=visit.stage
                                        focusOnNext=true
                                        displayPreferences.layout(WorkoutStartLayout.FOCUS)
                                        supersetRoundGroup=null
                                    }
                                },onDismiss={supersetRoundGroup=null}
                            )
                        }
                    }
                    // Both journal types use the same typed, read-only
                    // navigation. A follow-up is a separate visit identity:
                    // it cannot become eligible just by swiping to its parent.
                    if(focusOnNext && activeFocus!=null) {
                        NativeKotlinWorkoutFocusNavigation(
                            steps=availableVisits.map{visit->
                                NativeKotlinFocusStep(
                                    visit,current.effectiveVisits.containsKey(visit))
                            },
                            selected=activeFocus,
                            nextUnfinished=nextVisit,
                            enabled=!busy,
                            onChoose={visit->
                                reviewSetKey=visit.setKey
                                reviewPart=visit.part.name
                                reviewStage=visit.stage
                            },
                            onReturnToNextUnfinished={
                                reviewSetKey=null
                                reviewPart=null
                            }
                        )
                    }
                }
                if(!finished && current.finishedByEventId==null) {
                    PursuitWorkoutReadinessPanel(
                        readiness=readiness,
                        onReadinessChange={readiness=it},
                        movementRestricted=movementRestricted,
                        onMovementRestrictedChange={movementRestricted=it},
                        enabled=!busy,
                        compactByDefault=true,
                        onReportConcern={
                            // Real notes remain a separate, explicit journal
                            // save; changing coach context never edits sets.
                            movementRestricted=true
                            readiness=CoachReadiness.LOW
                            noteError=null
                            val visit=activeFocus ?:nextVisit
                            val scope=current.snapshot.slots.singleOrNull{slot->
                                slot.sets.any{it.setKey==visit?.setKey}
                            }?.slotId
                            pendingConcern=NativeMovementConcernRequest(
                                scope,current.revision)
                        }
                    )
                }
                if(!verifiedFinishedInHistory || showFinishedSets) {
                PursuitWorkoutReferencePicker(referenceMode,displayPreferences::reference,
                    onRetry=if(lastReferences==WorkoutLastReferenceResult.Unavailable &&
                        !busy) ({lastReferenceRefresh++}) else null)
                current.snapshot.slots.filter {slot->
                    activeFocus==null ||
                        slot.sets.any{it.setKey==activeFocus.setKey}
                }.forEach {slot->
                    // The immutable issued-workout gym owns this choice.
                    val config=if(slot.exerciseId in eligibleMachineExercises)
                        KotlinMachineAlternativesDomain.forGymExercise(
                            machineRoster?.configurations.orEmpty(),
                            current.originalSnapshot.stamp.gymId,slot.exerciseId)
                            ?:KotlinGymMachineAlternatives(
                                current.originalSnapshot.stamp.gymId,
                                slot.exerciseId,null,emptyList())
                    else null
                    val selectedMachineId=workoutMachineChoice(
                        machineChoices.encoded,slot.exerciseId,null)
                    val requiresNewMachineChoice=config?.machines?.isNotEmpty()==true &&
                        config.machines.none{it.id==selectedMachineId && it.active}
                    if(requiresNewMachineChoice &&
                        !finished && current.finishedByEventId==null)
                        Text("Select an active physical machine to record new sets. "+
                            "Already recorded sets keep their original machine.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    // Exercise Info owns both workout-only replacement and
                    // same-exercise Machine A/B. No full-width duplicate in
                    // the active custom set editor.
                    NativeKotlinWorkoutMachineSelector(config,selectedMachineId,
                        enabled=!busy && !finished && current.finishedByEventId==null &&
                            machineChoices.ready && !machineChoices.busy,
                        onSelect={machineId->
                            machineChoices.onSelect(slot.exerciseId,machineId)
                        },
                        onCreate={label,makeDefault->
                            machineChoices.onCreate(slot.exerciseId,label,makeDefault)
                        },onRetry=machineChoices.onRetry,
                        retryEnabled=machineChoices.canRetry,
                        error=machineChoices.error,
                        acknowledgedCreatedMachineId=machineChoices.createdMachineId,
                        priorHistory=lastReferences,
                        priorWorkingKeys=slot.sets.filter {
                            it.prescription.type==DraftSetType.WORKING
                        }.map{WorkoutReferenceKey(it.setKey)},
                        onRequestHistory={stationHistoryRequested=true},
                        externalOpen=openPhysicalMachineForSlot==slot.slotId,
                        onExternalOpenConsumed={openPhysicalMachineForSlot=null},
                        showInlineTrigger=!focusOnNext || finished ||
                            current.finishedByEventId!=null)
                    machineChoices.error?.let{
                        Text(it,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick=machineChoices.onRetry,
                            enabled=machineChoices.canRetry && !busy && !finished,
                            modifier=Modifier.heightIn(min=48.dp)) {
                            Text("Retry machine verification")
                        }
                    }

                    // Custom program sets are leveled in THIS workout only.
                    // Main working entries count; warmups and amplifier
                    // follow-ups are never used or modified.
                    if(!finished && current.finishedByEventId==null &&
                        activeFocus?.part!=CustomWorkoutVisitPart.FOLLOW_UP) {
                        val numberOfWorkingSets=slot.sets.count {
                            it.prescription.type==DraftSetType.WORKING
                        }
                        if(numberOfWorkingSets>=2) {
                            var levelingExpanded by rememberSaveable(
                                workoutId,slot.slotId
                            ) {mutableStateOf(false)}
                            OutlinedButton(
                                onClick={
                                    levelingExpanded=!levelingExpanded
                                    if(!levelingExpanded)priorLevelingSlotId=null
                                },
                                enabled=!busy,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            ) {
                                Text(if(levelingExpanded)"Hide Leveling options"
                                    else "Leveling · adjust current sets")
                            }
                            if(levelingExpanded) {
                        val workingSets=slot.sets.filter {
                            it.prescription.type==DraftSetType.WORKING
                        }
                        val prescribed=workingSets.map{it.prescription.load}
                        val allVerified=prescribed.size>=2 &&
                            prescribed.all{it is DraftSetLoad.Verified} &&
                            prescribed.map{
                                (it as DraftSetLoad.Verified).unit
                            }.distinct().size==1 &&
                            prescribed.map{
                                (it as DraftSetLoad.Verified).basis
                            }.distinct().size==1
                        var stock by remember(host,workoutId,slot.slotId,
                            selectedMachineId,current.revision) {
                            mutableStateOf<VerifiedLoadStock?>(null)
                        }
                        LaunchedEffect(host,workoutId,slot.slotId,
                            selectedMachineId,current.revision,
                            machineChoices.ready,machineChoices.busy) {
                            stock=null
                            if(machineChoices.ready && !machineChoices.busy)
                                stock=withContext(Dispatchers.IO) {
                                    runCatching {
                                        host.reviewIssuedCustomLevelingStock(
                                            workoutId,slot.slotId,selectedMachineId)
                                    }.getOrNull()
                                }
                        }
                        val currentRows=workingSets.map{set->
                            val visit=current.effectiveVisits[
                                CustomWorkoutVisitKey(
                                    set.setKey,CustomWorkoutVisitPart.MAIN)]
                                ?.operation as? CustomWorkoutJournalOperation.Record
                            val actual=visit?.takeIf{
                                it.outcome==KotlinJournalSetOutcome.COMPLETED
                            }?.performed
                            val editor=if(visit==null)currentEditors[
                                levelEditorKey(set.setKey,selectedMachineId)]
                                else null
                            val load=if(visit!=null)actual?.load else
                                (editor?.weight ?:initialMachineScopedLoad(
                                    set.prescription.load,null,selectedMachineId))
                                    .toBigDecimalOrNull()
                            val reps=if(visit!=null)actual?.reps else
                                (editor?.reps ?:
                                    set.prescription.reps.minimum.toString())
                                    .toIntOrNull()
                            val verified=set.prescription.load as? DraftSetLoad.Verified
                            CurrentWorkoutLevelingRow(
                                set.setKey,load,reps,visit!=null,
                                verified?.unit ?:LoadUnit.LB,
                                verified?.basis ?:LoadBasis.EXERCISE_OVERRIDE,
                                if(visit!=null)actual?.machineInstanceId
                                else selectedMachineId)
                        }
                        var review by remember(workoutId,slot.slotId,
                            selectedMachineId,currentRows,stock) {
                            mutableStateOf<CurrentWorkoutLevelingReview?>(null)
                        }
                        LaunchedEffect(currentRows,stock,allVerified) {
                            review=withContext(Dispatchers.Default) {
                                if(allVerified)
                                    DoubleProgressionLevelingDomain
                                        .reviewCurrentWorkout(currentRows,stock)
                                else CurrentWorkoutLevelingReview.Unavailable(
                                    CurrentWorkoutLevelingReview.Reason.MIXED_EQUIPMENT)
                            }
                        }
                        val candidate=review as? CurrentWorkoutLevelingReview.Ready
                        if(candidate!=null) {
                            val unit=(prescribed.firstOrNull()
                                as? DraftSetLoad.Verified)
                                ?.unit?.name?.lowercase().orEmpty()
                            OutlinedButton(
                                onClick={
                                    if(!busy && machineChoices.ready &&
                                        !machineChoices.busy) {
                                        levelSlotId=slot.slotId
                                        levelMachineId=selectedMachineId
                                        levelSetKeys=candidate.eligibleSetKeys
                                        levelWeight=candidate.roundedLoad
                                            .stripTrailingZeros().toPlainString()
                                        levelReps=candidate.averageReps
                                        candidate.eligibleSetKeys.forEach{key->
                                            currentEditors[levelEditorKey(
                                                key,selectedMachineId)]=
                                                CurrentWorkoutEditorValue(
                                                    levelWeight,levelReps.toString())
                                        }
                                        levelVersion++
                                    }
                                },
                                enabled=!busy && machineChoices.ready &&
                                    !machineChoices.busy,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            ) {
                                Text("Use average load · "+
                                    "${candidate.roundedLoad.stripTrailingZeros().toPlainString()} "+
                                    "$unit × ${candidate.averageReps}")
                            }
                            Text("Prefills unfinished main working sets only. "+
                                "Saved sets, RIR, amplifiers and future "+
                                "programs stay unchanged.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if(review is CurrentWorkoutLevelingReview.Unavailable &&
                            (review as CurrentWorkoutLevelingReview.Unavailable).reason==
                                CurrentWorkoutLevelingReview.Reason.UNVERIFIED_STOCK &&
                            currentRows.count{row->kotlinWorkingLoadEntered(row.load,row.basis)}>=2)
                            Text("Confirm available weights in My gym "+
                                "to use Leveling on this machine.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)

                        val unfinished=workingSets.filter { set->
                            CustomWorkoutVisitKey(set.setKey,
                                CustomWorkoutVisitPart.MAIN) !in current.effectiveVisits
                        }
                        if(allVerified && unfinished.isNotEmpty()) {
                            OutlinedButton(
                                onClick={priorLevelingSlotId=slot.slotId},
                                enabled=!busy && machineChoices.ready &&
                                    !machineChoices.busy && !requiresNewMachineChoice,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                            ) { Text("Leveling · review previous workout") }
                        }
                        if(priorLevelingSlotId==slot.slotId && allVerified &&
                            unfinished.isNotEmpty()) {
                            val first=workingSets.first().prescription
                            val sameGoal=first.targetRir!=null &&
                                workingSets.all { set->
                                    set.prescription.reps==first.reps &&
                                        set.prescription.targetRir==first.targetRir
                                }
                            val priorReview=rememberPriorWorkoutLevelingReview(
                                currentSets=workingSets.map { set->
                                    WorkoutReferenceKey(set.setKey) to
                                        set.prescription.load
                                },
                                previous=physicalMachineLastReferences(
                                    lastReferences,selectedMachineId),
                                machineInstanceId=selectedMachineId,
                                repBand=first.reps.takeIf{
                                    sameGoal && !requiresNewMachineChoice
                                },
                                goalRir=first.targetRir?.takeIf{
                                    sameGoal && !requiresNewMachineChoice
                                },
                                verifiedStock=stock
                            )
                            val prior=priorReview as? LevelingReviewResult.Candidate
                            val matchingLast=physicalMachineLastReferences(
                                lastReferences,selectedMachineId)
                            val observed=if(prior!=null)
                                workingSets.mapNotNull { set->
                                    WorkoutLastDraftDomain.reviewedActual(
                                        matchingLast,
                                        WorkoutReferenceKey(set.setKey),
                                        set.prescription.load)
                                } else emptyList()
                            PursuitPreviousWorkoutLevelingDialog(
                                review=priorReview,
                                historyLoading=lastReferences==null,
                                goalComparable=sameGoal,
                                stationValid=machineChoices.ready &&
                                    !machineChoices.busy &&
                                    !requiresNewMachineChoice,
                                loadUnit=(first.load as? DraftSetLoad.Verified)
                                    ?.unit?.name?.lowercase().orEmpty(),
                                observed=observed,
                                unfinishedCount=unfinished.size,
                                busy=busy,
                                onDismiss={priorLevelingSlotId=null},
                                onApply={
                                    if(prior!=null && !busy &&
                                        machineChoices.ready &&
                                        !machineChoices.busy &&
                                        !requiresNewMachineChoice &&
                                        unfinished.isNotEmpty()) {
                                        levelSlotId=slot.slotId
                                        levelMachineId=selectedMachineId
                                        levelSetKeys=unfinished.map { it.setKey }
                                        levelWeight=prior.proposedStraightSetLoad
                                            .stripTrailingZeros().toPlainString()
                                        levelReps=prior.repsPerSet
                                        unfinished.forEach { set->
                                            currentEditors[levelEditorKey(
                                                set.setKey,selectedMachineId)]=
                                                CurrentWorkoutEditorValue(
                                                    levelWeight,levelReps.toString())
                                        }
                                        levelVersion++
                                        priorLevelingSlotId=null
                                    }
                                }
                            )
                        }
                            }
                        }
                    }
                    PursuitCard {
                        if(activeFocus==null)PursuitExerciseHeading(slot.exerciseId,
                            onHistory={historyExerciseId=slot.exerciseId},
                            subtitle="Working sets and saved exercise history",
                            onInfo={infoSlotId=slot.slotId})
                        val partners=current.snapshot.slots.filter {
                            slot.supersetGroup!=null && it.supersetGroup==slot.supersetGroup
                        }
                        // Skip the remainder of a movement from Exercise Info.
                        // The existing owner/revision-checked confirmation
                        // dialog is still the only route that writes skips.
                        if(activeFocus==null && partners.size in 2..3)Text(
                            "Superset · "+partners.joinToString(" · "){kotlinExerciseLabel(it.exerciseId)}+
                                ". Alternate working sets; warm up each movement first.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        NativeKotlinPinnedExerciseControl(
                            host=host,workoutId=workoutId,
                            exerciseId=slot.exerciseId,custom=true,
                            enabled=!busy && !finished &&
                                current.finishedByEventId==null,
                            showEditAction=activeFocus==null || finished,
                            externalOpen=pinnedSlotId==slot.slotId,
                            onExternalDismiss={pinnedSlotId=null}
                        )
                        // Workout-only swap and its undo now live with the
                        // verified equipment choices inside Exercise Info,
                        // rather than taking up a second training-card row.
                        if(slot.coachNote.isNotBlank())
                            Text(slot.coachNote,
                                style=MaterialTheme.typography.bodySmall)
                        slot.sets.filter{activeFocus==null ||
                            it.setKey==activeFocus.setKey}.forEach {set->
                            val mainKey=CustomWorkoutVisitKey(
                                set.setKey,CustomWorkoutVisitPart.MAIN)
                            val observed=current.effectiveVisits[mainKey]
                            // Once saved, the machine is a historical fact;
                            // selecting B later must not rewrite a prior A set.
                            val recordedMain=observed?.operation
                                as? CustomWorkoutJournalOperation.Record
                            val previouslyAttempted=recordedMain?.performed
                            val mainMachineId=if(previouslyAttempted!=null)
                                previouslyAttempted.machineInstanceId
                            else selectedMachineId
                            val expectedCoachMachineScope=
                                if(config?.machines?.isNotEmpty()==true)
                                    LiveMachineCoachScope.Physical(selectedMachineId)
                                else LiveMachineCoachScope.ExerciseOnly
                            val advice=liveCoach?.takeIf {
                                // Hide prior advice immediately after an
                                // injury restriction, before a fresh review.
                                !movementRestricted &&
                                !busy && !finished && machineChoices.ready &&
                                    !machineChoices.busy && mainKey==nextVisit &&
                                    it.workoutId==workoutId && it.setKey==set.setKey &&
                                    it.journalRevision==current.revision &&
                                    // Reject an old A review immediately when
                                    // the durable selector changes to B.
                                    it.machineScope==expectedCoachMachineScope
                            }
                            if(advice!=null) {
                                Text("Coach · next set",
                                    style=MaterialTheme.typography.titleSmall,
                                    color=MaterialTheme.colorScheme.primary)
                                Text(advice.coaching.message,
                                    style=MaterialTheme.typography.bodyMedium)
                                Text("Choose any change in the set fields. Planned targets and your entries stay unchanged.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(activeFocus?.part!=CustomWorkoutVisitPart.FOLLOW_UP)
                                // Keep each physical machine's unsaved main-set
                                // fields separate and restore them on A -> B -> A.
                                // Recorded actuals remain journal-owned.
                                editorStates.SaveableStateProvider(
                                    "main:${set.setKey}:station:"+
                                        (mainMachineId?.let{"id:$it"} ?: "none")
                                ) {
                                NativeKotlinCustomVisitEditor(
                                set=set,part=CustomWorkoutVisitPart.MAIN,
                                amplifierSummary=CustomAmplifierSetSummaryDomain.review(current,set),
                                plannedLoadNote=NativeKotlinPlannedBackoffNote.fromCustomSlot(
                                    slot.sets,set),
                                existing=observed,locked=busy||finished||!machineChoices.ready||machineChoices.busy,
                                saving=busy,saveError=error,
                                machineInstanceId=mainMachineId,
                                workoutId=workoutId,
                                athleteId=current.originalSnapshot.stamp.athleteId,
                                exerciseId=slot.exerciseId,
                                issuedGymId=current.originalSnapshot.stamp.gymId,
                                machineRoster=machineRoster,
                                referenceMode=referenceMode,lastReferences=lastReferences,
                                levelingDraft=if(observed==null &&
                                    set.prescription.type==DraftSetType.WORKING &&
                                    slot.slotId==levelSlotId &&
                                    mainMachineId==levelMachineId &&
                                    levelWeight.isNotBlank() && levelReps>0 &&
                                    set.setKey in levelSetKeys)
                                    CurrentWorkoutLevelingDraft(
                                        levelVersion,levelWeight,levelReps)
                                else null,
                                onCurrentDraftChanged={weight,reps->
                                    val key=levelEditorKey(set.setKey,mainMachineId)
                                    val entry=CurrentWorkoutEditorValue(weight,reps)
                                    if(currentEditors[key]!=entry)currentEditors[key]=entry
                                },
                                position=slot.sets.filter{it.prescription.type==set.prescription.type}
                                    .indexOf(set)+1,
                                setCount=slot.sets.count{it.prescription.type==set.prescription.type},
                                initiallyExpanded=activeFocus==mainKey,
                                onRecord={outcome,actual->
                                    // An edited SKIP had no physical station.
                                    // A completed/failed correction must use an
                                    // explicitly chosen current machine, whereas
                                    // an attempted prior set retains its own ID.
                                    val hadAttempt=(observed?.operation as?
                                        CustomWorkoutJournalOperation.Record)
                                        ?.performed!=null
                                    if(outcome==KotlinJournalSetOutcome.SKIPPED ||
                                        hadAttempt || !requiresNewMachineChoice)
                                        record(set.setKey,CustomWorkoutVisitPart.MAIN,
                                            outcome,actual?.copy(machineInstanceId=mainMachineId))
                                },
                                onShowPlates={load,unit,basis->
                                    plateRequest=NativeCustomPlateRequest(
                                        slot.exerciseId,
                                        current.originalSnapshot.stamp,
                                        load,unit,basis)
                                })
                                }
                            val main=(observed?.operation
                                as? CustomWorkoutJournalOperation.Record)
                            if(set.prescription.amplifier!=null &&
                                main?.outcome==KotlinJournalSetOutcome.COMPLETED &&
                                activeFocus?.part!=CustomWorkoutVisitPart.MAIN) {
                                val followKeys=CustomWorkoutExecutionDomain.followUpVisits(
                                    current,set)
                                followKeys.filter{activeFocus==null || it==activeFocus}
                                    .forEach { followKey ->
                                val follower=current.effectiveVisits[followKey]
                                val precedingDrop=if(followKey.stage>1 &&
                                    set.prescription.amplifier?.kind==
                                        CustomAmplifierKind.DROP_SET)
                                    current.effectiveVisits[CustomWorkoutVisitKey(
                                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP,
                                        followKey.stage-1)]
                                else null
                                editorStates.SaveableStateProvider(
                                    "follow:${set.setKey}:stage:${followKey.stage}:activation:"+
                                        (observed?.appliedRevision ?: "none")+
                                        ":previous:"+
                                        (precedingDrop?.appliedRevision ?: "none")) {
                                NativeKotlinCustomVisitEditor(
                                    set=set,part=CustomWorkoutVisitPart.FOLLOW_UP,
                                    stageOrdinal=followKey.stage,
                                    stageTotal=set.prescription.amplifier?.followUpRounds ?:1,
                                    activationPerformed=main?.performed,
                                    activationReceiptRevision=observed?.appliedRevision,
                                    precedingDropPerformed=(precedingDrop?.operation as?
                                        CustomWorkoutJournalOperation.Record)?.performed,
                                    precedingDropReceiptRevision=precedingDrop?.appliedRevision,
                                    existing=follower,locked=busy||finished||!machineChoices.ready||machineChoices.busy,
                                    saving=busy,saveError=error,
                                    machineInstanceId=if(follower!=null)
                                        (follower.operation as?
                                            CustomWorkoutJournalOperation.Record)
                                            ?.performed?.machineInstanceId
                                    else if(main!=null)main.performed?.machineInstanceId
                                    else selectedMachineId,
                                    workoutId=workoutId,
                                    athleteId=current.originalSnapshot.stamp.athleteId,
                                    exerciseId=slot.exerciseId,
                                    issuedGymId=current.originalSnapshot.stamp.gymId,
                                    machineRoster=machineRoster,
                                    referenceMode=referenceMode,lastReferences=lastReferences,
                                    position=slot.sets.filter{it.prescription.type==set.prescription.type}
                                        .indexOf(set)+1,
                                    setCount=slot.sets.count{it.prescription.type==set.prescription.type},
                                    initiallyExpanded=activeFocus==followKey,
                                    onRecord={outcome,actual->
                                        record(set.setKey,
                                            CustomWorkoutVisitPart.FOLLOW_UP,
                                            outcome,actual?.copy(
                                                machineInstanceId=if(follower!=null)
                                                    (follower.operation as?
                                                        CustomWorkoutJournalOperation.Record)
                                                        ?.performed?.machineInstanceId
                                                else if(main!=null)main.performed?.machineInstanceId
                                                else selectedMachineId),
                                            stage=followKey.stage)
                                    },
                                    onShowPlates={load,unit,basis->
                                        plateRequest=NativeCustomPlateRequest(
                                            slot.exerciseId,
                                            current.originalSnapshot.stamp,
                                            load,unit,basis)
                                    })
                                }
                                }
                            }
                        }
                    }
                }
                }
                if(!verifiedFinishedInHistory)PursuitCard {
                    // The ordinary Finish action is docked beside the rest
                    // area only after all durable required visits resolve.
                    // Interrupted historical Finish recovery remains separate
                    // and never reissues an already-completed workout.
                    Text("Interrupted Finish recovery",
                        style=MaterialTheme.typography.titleSmall)
                    Text("Use only if the app closed before saving or confirming "+
                        "the finished session. Your logged sets are preserved.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(enabled=!busy,onClick={
                            busy=true
                            scope.launch{
                                val result=withContext(Dispatchers.IO){
                                    runCatching{host.recoverPendingCustomHistory()}
                                        .getOrNull()
                                }
                                error=if(result is NativeCustomFinishResult.Committed)
                                    "Recovered the committed native Finish. Reload the journal."
                                else "No recoverable native Finish: ${result ?: "storage unavailable"}."
                                val restored=withContext(Dispatchers.IO){
                                    runCatching{host.openIssuedCustomWorkout(workoutId)}
                                        .getOrNull()
                                }
                                journal=(restored as? NativeCustomJournalResult.Durable)
                                    ?.issued?.journal
                                finished=journal?.finishedByEventId!=null
                                verifiedFinishedInHistory=if(finished)
                                    verifyCommittedHistory()
                                else false
                                busy=false
                            }
                        }){Text("Recover interrupted Finish")}
                }
            }
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall)}
        Text(if(verifiedFinishedInHistory)"You can review and correct saved sets in History."
            else "Log what you actually did. Your original targets stay available for comparison.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Fixed below the scroll, so focusing a later set cannot hide the timer
    // and the timer never overlays a set-entry control or the Finish button.
    if(current!=null && current.unresolvedVisitCount>0 && !finished)
        Column(Modifier.widthIn(max=920.dp).fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(horizontal=16.dp,vertical=8.dp)) {
            NativeKotlinWorkoutRestTimer(workoutId,
                KotlinWorkoutRestDomain.inspect(current,nextVisit),enabled=!busy,
                athleteId=current.originalSnapshot.stamp.athleteId)
        }
    // Same predictable fixed bottom placement as the generated workout.
    // The dynamic follow-up inventory (including staged myo-reps, drop sets,
    // and partials) must actually be resolved before this action appears.
    // A visible button is NOT a durable Finish event or a History receipt.
    if(current?.canProposeFinish==true &&
        current.finishedByEventId==null && !finished &&
        !verifiedFinishedInHistory)
        Column(Modifier.widthIn(max=920.dp).fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(horizontal=16.dp,vertical=8.dp)) {
            PursuitCard(highlighted=true) {
                Text("All working sets and amplifier visits are resolved",
                    style=MaterialTheme.typography.titleMedium)
                PursuitPrimaryAction("Review and finish",
                    enabled=!busy) {
                    finishReview=journal?.let {
                        NativeFinishReviewToken(workoutId,it.revision)
                    }
                }
            }
        }
    }
    swapSlotId?.let {slotId ->
        val snap=journal?.snapshot
        val slot=snap?.slots?.firstOrNull {it.slotId==slotId}
        if(slot!=null && swapChoices.isNotEmpty() &&
            journal?.finishedByEventId==null) {
            val origin=CustomExerciseSlot(
                slotId=slot.slotId,exerciseId=slot.exerciseId,
                role=slot.role,
                sets=slot.sets.map{it.prescription},
                attachmentId=slot.attachmentId,
                supersetGroup=slot.supersetGroup,
                pinned=slot.pinned,coachNote=slot.coachNote
            )
            key(workoutId,slotId) {
                NativeKotlinCustomExerciseSheet(
                    busy=busy,
                    available=swapChoices,
                    existing=null,replacing=origin,
                    scopeLabel="This workout only · no program change",
                    workoutOnlySwap=true,
                    otherDayExerciseIds=snap.slots.filter{
                        it.slotId!=slotId && it.sets.any{set->
                            set.prescription.type==DraftSetType.WORKING
                        }
                    }.map{it.exerciseId},
                    feedback=error,
                    onSave={exerciseId,sets,note->
                        confirmSwap(slotId,exerciseId,sets,note)
                    },
                    onDismiss={swapSlotId=null;swapChoices=emptyList()}
                )
            }
        }
    }
    undoSwapConfirmEventId?.let {swapEventId->
        val active=journal
        val source=(active?.receipts?.firstOrNull{
            it.operation.eventId==swapEventId
        }?.operation as? CustomWorkoutJournalOperation.Swap)
        if(source!=null && active!=null && active.finishedByEventId==null) {
            AlertDialog(
                onDismissRequest={if(!busy)undoSwapConfirmEventId=null},
                title={Text("Undo workout swap?")},
                text={Text(
                    "Undo the replacement "+kotlinExerciseLabel(
                        source.replacement.exerciseId)+
                    " and restore the previously approved exercise and "+
                    "sets. Only unstarted swaps are reversible. Other "+
                    "logged sets and your saved program stay unchanged."
                )},
                confirmButton={
                    Button(enabled=!busy,
                        onClick={confirmUndoSwap(swapEventId)}) {
                        Text("Restore previous exercise")
                    }
                },
                dismissButton={
                    TextButton(enabled=!busy,
                        onClick={undoSwapConfirmEventId=null}) {
                        Text("Keep replacement")
                    }
                }
            )
        }
    }
    plateRequest?.let {request->
        NativeKotlinCustomPlateSheet(host,request,
            onDismiss={plateRequest=null})
    }
    // Info reads only the current owner-recovered issued snapshot.
    // Closing it cannot write a set, alter a target or discard editor drafts.
    infoSlotId?.let { slotId ->
        journal?.snapshot?.slots?.firstOrNull{it.slotId==slotId}
            ?.let { slot ->
                val infoGymId=journal?.originalSnapshot?.stamp?.gymId
                val infoConfig=infoGymId?.let {gymId->
                    if(slot.exerciseId in eligibleMachineExercises)
                        KotlinMachineAlternativesDomain.forGymExercise(
                            machineRoster?.configurations.orEmpty(),gymId,slot.exerciseId)
                            ?:KotlinGymMachineAlternatives(gymId,slot.exerciseId,
                                null,emptyList())
                    else null
                }
                val infoMachine=workoutMachineChoice(
                    machineChoices.encoded,slot.exerciseId,null)
                val infoRecorded=slot.sets.any{set->
                    journal?.receipts.orEmpty().any{receipt->
                        (receipt.operation as? CustomWorkoutJournalOperation.Record)
                            ?.visit?.setKey==set.setKey
                    }
                }
                val infoOptions=NativeKotlinEquipmentRecoveryPolicy.review(
                    activeWorkout=!busy && !finished &&
                        journal?.finishedByEventId==null,
                    hasPhysicalMachineConfig=infoConfig!=null &&
                        machineChoices.ready && !machineChoices.busy,
                    hasRecordedExerciseWork=infoRecorded,
                    linkedSuperset=slot.supersetGroup!=null
                )
                val infoRemaining=journal?.let {
                    WorkoutRemainingExerciseDomain.custom(it,slot.slotId)
                }
                val infoUndo=journal?.receipts.orEmpty().asReversed()
                    .mapNotNull{it.operation as?
                        CustomWorkoutJournalOperation.Swap}
                    .firstOrNull{swap->
                        swap.replacement.slotId==slot.slotId &&
                            journal?.receipts.orEmpty().none{receipt->
                                (receipt.operation as?
                                    CustomWorkoutJournalOperation.UndoSwap)
                                    ?.swapEventId==swap.eventId
                            }
                    }
                val infoActions=NativeWorkoutInfoActionPolicy.review(
                    active=!busy && !finished &&
                        journal?.finishedByEventId==null,
                    hasRemainingSets=infoRemaining?.entries?.isNotEmpty()==true,
                    hasActiveSwap=infoUndo!=null,
                    hasRecordedWork=infoRecorded,
                    linkedSuperset=slot.supersetGroup!=null)
                NativeKotlinWorkoutExerciseInfoSheet(
                    exerciseId=slot.exerciseId,
                    sourceDescription="Issued custom workout · "+
                        slot.role.name.lowercase().replace('_',' ')+
                        (if(slot.supersetGroup!=null)" · superset linked" else "")+
                        (if(slot.pinned)" · pinned" else ""),
                    sets=customWorkoutInfoSets(slot.sets),
                    note=slot.coachNote.takeIf{it.isNotBlank()},
                    warmupsOnly=infoWarmupsOnly,
                    busy=busy || machineChoices.busy,
                    machineLabel=infoConfig?.machines?.firstOrNull{
                        it.id==infoMachine
                    }?.label ?: "Choose or add machine",
                    onChooseMachine=if(!infoWarmupsOnly && infoOptions.canChangeStation)
                        ({
                            infoSlotId=null
                            openPhysicalMachineForSlot=slot.slotId
                        }) else null,
                    onSwapExercise=if(!infoWarmupsOnly && infoOptions.canReplaceExercise)
                        ({
                            infoSlotId=null
                            reviewSwap(slot)
                        }) else null,
                    onSkipRemainingExercise=if(!infoWarmupsOnly &&
                        infoActions.mayRequestSkipRemaining)journal?.revision?.let{revision->
                        ({
                            infoSlotId=null
                            pendingExerciseSkip=NativeExerciseSkipRequest(
                                slot.slotId,revision)
                        })
                    }else null,
                    onUndoWorkoutSwap=if(!infoWarmupsOnly &&
                        infoActions.mayRequestUndoSwap)infoUndo?.let{swap->
                        ({
                            infoSlotId=null
                            undoSwapConfirmEventId=swap.eventId
                        })
                    }else null,
                    unavailableReason=if(!infoWarmupsOnly && !finished)
                        infoOptions.reason else null,
                    outcomes=journal?.effectiveVisits.orEmpty().mapNotNull { (key,receipt)->
                        if(key.part!=CustomWorkoutVisitPart.MAIN)null
                        else (receipt.operation as? CustomWorkoutJournalOperation.Record)?.let{key.setKey to it.outcome}
                    }.toMap(),
                    onSelectWarmup=if(busy || finished)null else ({key->
                        // Enter this exact main-set editor without logging it.
                        reviewSetKey=key
                        reviewPart=CustomWorkoutVisitPart.MAIN.name
                        focusOnNext=true
                        infoSlotId=null
                        infoWarmupsOnly=false
                    }),
                    onHistory={
                        infoSlotId=null
                        infoWarmupsOnly=false
                        historyExerciseId=slot.exerciseId
                    },
                    onDismiss={infoSlotId=null;infoWarmupsOnly=false}
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
                WorkoutRemainingExerciseDomain.custom(verified,it)
            }
        }
        val options=NativeMovementConcernPresentation.review(
            request,journal?.revision,
            journal?.let{it.finishedByEventId==null}==true && !finished,
            remaining,
            canReviewReplacement=NativeMovementSwapEligibility.custom(
                journal,request.scopeId))
        NativeKotlinMovementConcernDialog(
            options=options,busy=busy,
            onWriteNote={
                if(options.current && !busy &&
                    journal?.revision==request.journalRevision) {
                    pendingConcern=null
                    noteError=null
                    showSessionNote=true
                }
            },
            onReviewSkip={
                val selectedRequest=options.skipRequest
                if(selectedRequest!=null && !busy &&
                    journal?.revision==request.journalRevision) {
                    pendingConcern=null
                    pendingExerciseSkip=selectedRequest
                }
            },
            onReviewReplacement={
                val candidate=options.replacementScopeId
                val latest=journal
                val target=latest?.snapshot?.slots?.singleOrNull{
                    it.slotId==candidate
                }
                if(target!=null && latest?.revision==request.journalRevision &&
                    !busy && !finished &&
                    NativeMovementSwapEligibility.custom(latest,candidate)) {
                    pendingConcern=null
                    reviewSwap(target)
                }
            },onDismiss={pendingConcern=null}
        )
    }
    pendingExerciseSkip?.let {request->
        val review=journal?.let{WorkoutRemainingExerciseDomain.custom(it,request.scopeId)}
        NativeKotlinRemainingExerciseSkipDialog(request,review,busy,
            onConfirm={
                skipRemainingExercise(request)
                pendingExerciseSkip=null
            },onDismiss={pendingExerciseSkip=null})
    }
    val quickSkip=pendingQuickSkip
    if(quickSkip!=null) {
        // Confirm against the newest owned journal, not the set that was
        // displayed when the sheet opened. A late save cannot turn this
        // shortcut into a correction or skip a different amplifier visit.
        val stillNext=journal?.let {
            CustomWorkoutJournalDomain.nextUnresolvedVisit(it)
        }==quickSkip
        val canSkip=stillNext && !busy && !finished &&
            journal?.finishedByEventId==null &&
            machineChoices.ready && !machineChoices.busy
        AlertDialog(
            onDismissRequest={pendingQuickSkip=null},
            title={Text("Skip the next entry?")},
            text={Column {
                Text(if(stillNext)
                    "Skipping records an explicit outcome for this ${if(quickSkip.part==CustomWorkoutVisitPart.FOLLOW_UP) "amplifier follow-up" else "set"}. You can correct it before Finish."
                else "The saved workout advanced while this confirmation was open. Dismiss and review the new next entry; no set was changed.")
                val current=journal
                val slot=current?.snapshot?.slots?.singleOrNull { slot->
                    slot.sets.any{it.setKey==quickSkip.setKey}
                }
                if(current!=null && slot!=null && stillNext) {
                    TextButton(enabled=canSkip,onClick={
                        pendingExerciseSkip=NativeExerciseSkipRequest(slot.slotId,current.revision)
                        pendingQuickSkip=null
                    }) {Text("Skip remaining exercise…")}
                }
            }},
            confirmButton={
                Button(enabled=canSkip,onClick={
                    // Recheck after the dialog's composition as well.
                    val latest=journal?.let {
                        CustomWorkoutJournalDomain.nextUnresolvedVisit(it)
                    }
                    pendingQuickSkip=null
                    if(latest==quickSkip && !busy && !finished &&
                        machineChoices.ready && !machineChoices.busy)
                        record(quickSkip.setKey,quickSkip.part,
                            KotlinJournalSetOutcome.SKIPPED,null,
                            stage=quickSkip.stage)
                }) {Text("Confirm skip")}
            },
            dismissButton={
                TextButton(onClick={pendingQuickSkip=null}) {Text("Keep training")}
            }
        )
    }
    val selected=journal
    val reviewed=finishReview
    if(reviewed!=null) {
        val canCommit=NativeFinishReviewPolicy.canCommit(
            reviewed,workoutId,selected?.revision,
            selected?.canProposeFinish==true &&
                selected.finishedByEventId==null && !finished &&
                !verifiedFinishedInHistory)
        AlertDialog(onDismissRequest={if(!busy)finishReview=null},
            title={Text("Finish and save this workout?")},
            text={Text(if(canCommit)
                "Save the recorded results for this workout to History. You can review and correct saved sets there."
                else "Your saved workout changed or needs recovery. Close this review and check the current sets before finishing.")},
            confirmButton={
                Button(enabled=!busy && canCommit,
                    onClick={
                        if(!NativeFinishReviewPolicy.canCommit(
                            reviewed,workoutId,journal?.revision,
                            journal?.canProposeFinish==true &&
                                journal?.finishedByEventId==null &&
                                !finished && !verifiedFinishedInHistory))
                            return@Button
                        finishReview=null
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO){
                                runCatching{
                                    host.finishIssuedCustomWorkout(workoutId,
                                        CustomWorkoutJournalOperation.Finish(
                                            "custom-finish-"+UUID.randomUUID(),
                                            reviewed.revision))
                                }.getOrNull()
                            }
                            finished=result is NativeCustomFinishResult.Committed
                            verifiedFinishedInHistory=if(finished)
                                verifyCommittedHistory()
                            else false
                            // A lost disk acknowledgement cannot tell
                            // us whether Finish committed. Only the durable
                            // result and independently verified History may
                            // report completion; never encourage a blind retry.
                            error=NativeFinishReviewPolicy.customFinishMessage(
                                result,verifiedFinishedInHistory)
                            val saved=withContext(Dispatchers.IO){
                                runCatching{host.openIssuedCustomWorkout(workoutId)}
                                    .getOrNull()
                            }
                            journal=(saved as? NativeCustomJournalResult.Durable)
                                ?.issued?.journal
                            busy=false
                        }
                    }){Text("Commit native Finish")}
            },dismissButton={
                TextButton(enabled=!busy,onClick={finishReview=null}) {
                    Text("Keep training")}
            })
    }
}

@Composable
private fun NativeKotlinCustomVisitEditor(
    set:CustomWorkoutSetSnapshot,
    part:CustomWorkoutVisitPart,
    existing:CustomWorkoutJournalReceipt?,
    locked:Boolean,
    saving:Boolean,
    saveError:String?,
    machineInstanceId:String?,
    workoutId:String,
    athleteId:String,
    exerciseId:String,
    issuedGymId:String?,
    machineRoster:NativeMachineRosterSnapshot?,
    referenceMode:WorkoutReferenceMode,
    lastReferences:WorkoutLastReferenceResult?,
    position:Int,
    setCount:Int,
    initiallyExpanded:Boolean,
    onRecord:(KotlinJournalSetOutcome,KotlinPerformedSet?)->Unit,
    onShowPlates:(String,LoadUnit,LoadBasis)->Unit,
    levelingDraft:CurrentWorkoutLevelingDraft?=null,
    onCurrentDraftChanged:(String,String)->Unit={_,_->},
    stageOrdinal:Int=1,
    stageTotal:Int=1,
    activationPerformed:KotlinPerformedSet?=null,
    activationReceiptRevision:Long?=null,
    precedingDropPerformed:KotlinPerformedSet?=null,
    precedingDropReceiptRevision:Long?=null,
    amplifierSummary:CustomAmplifierSetSummary?=null,
    plannedLoadNote:String?=null
) {
    val prescription=set.prescription
    val follower=prescription.amplifier
    val isStagedShortRestStop=part==CustomWorkoutVisitPart.FOLLOW_UP &&
        stageTotal>1 && follower?.kind in setOf(
            CustomAmplifierKind.MYO_REPS,CustomAmplifierKind.REST_PAUSE,
            CustomAmplifierKind.CLUSTER)
    val target=if(part==CustomWorkoutVisitPart.FOLLOW_UP &&
        follower!=null)
        AdvancedAmplifierProtocolDomain.followUpLoadForStage(
            follower,prescription.load,stageOrdinal)
    else prescription.load
    val verified=target as? DraftSetLoad.Verified
    val saved=existing?.operation as? CustomWorkoutJournalOperation.Record
    var editing by rememberSaveable(set.setKey,part.name,stageOrdinal,existing?.appliedRevision) {
        mutableStateOf(saved==null && initiallyExpanded)
    }
    // Skips and failed attempts are durable factual observations, not
    // ordinary navigation. Confirm before writing to the owned journal.
    var pendingExceptionalOutcome by rememberSaveable(
        set.setKey,part.name,stageOrdinal,existing?.appliedRevision
    ) {mutableStateOf<KotlinJournalSetOutcome?>(null)}
    LaunchedEffect(locked) {
        if(locked)pendingExceptionalOutcome=null
    }
    val reveal=remember(set.setKey,part,stageOrdinal){BringIntoViewRequester()}
    LaunchedEffect(set.setKey,part,stageOrdinal,initiallyExpanded,locked,existing?.appliedRevision) {
        if(initiallyExpanded && saved==null && !locked) {
            editing=true
            // A long workout can contain dozens of sets. Bring the next
            // unrecorded one into view after each durable save.
            reveal.bringIntoView()
        }
    }
    // A/B and exercise/day/source identity are pinned to the immutable
    // ISSUED workout. This private entry cannot issue prescriptions, save
    // observations or authorize a future session after cold restart.
    val expectedReps=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
        follower?.followUpReps?.let{RepBand(it,it)} ?:prescription.reps
    else prescription.reps
    // A correction to the main activation receipt changes the source of
    // truth for every unsaved mini-set. Never reopen an old unsent draft
    // under the new activation weight or silently overwrite a typed value.
    val visitDraftIdentity=part.name+":stage:"+stageOrdinal+
        (if(part==CustomWorkoutVisitPart.FOLLOW_UP)
            ":activation:"+(activationReceiptRevision?.toString() ?: "none")+
                ":previous-drop:"+
                (precedingDropReceiptRevision?.toString() ?: "none")
        else "")
    val draftIdentity=NativePendingWorkoutDraftIdentity.make(
        athleteId,workoutId,issuedGymId.orEmpty(),exerciseId,
        set.setKey,visitDraftIdentity,machineInstanceId,target,expectedReps,
        if(part==CustomWorkoutVisitPart.FOLLOW_UP)null else prescription.targetRir)
    // One engine policy selects the source of the editable follow-up load.
    // The issued target remains immutable, while a new same-load mini-set
    // starts from the completed activation set (not the first set or a
    // historical reference). Saved observations always win on corrections.
    val followUpEntryLoad=if(part==CustomWorkoutVisitPart.FOLLOW_UP &&
        follower!=null && saved==null)
        AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
            follower,prescription.load,activationPerformed,machineInstanceId,
            stageOrdinal,precedingDropPerformed)
    else null
    val startingLoad=when(followUpEntryLoad) {
        is AmplifierFollowUpEntryLoad.RecordedActivation ->
            followUpEntryLoad.performed.load
                ?.stripTrailingZeros()?.toPlainString().orEmpty()
        is AmplifierFollowUpEntryLoad.AuthoredStage ->
            initialMachineScopedLoad(followUpEntryLoad.target,null,machineInstanceId)
        AmplifierFollowUpEntryLoad.ActivationUnavailable -> ""
        null ->initialMachineScopedLoad(target,saved?.performed,machineInstanceId)
    }
    val draftBaseline=NativePendingWorkoutDraft(
        startingLoad,
        saved?.performed?.reps?.toString() ?:
            (if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps else prescription.reps.minimum)
                ?.toString().orEmpty(),
        saved?.performed?.actualRir?.toString().orEmpty())
    val draftEditor=rememberNativePendingWorkoutDraftEditor(
        draftIdentity,existing!=null,existing?.appliedRevision?.toString(),
        draftBaseline)
    val actualLoad=draftEditor.load
    val actualReps=draftEditor.reps
    val actualRir=draftEditor.rir
    var appliedLevelVersion by rememberSaveable(
        set.setKey,part.name,stageOrdinal,existing?.appliedRevision,machineInstanceId
    ) {mutableIntStateOf(-1)}
    LaunchedEffect(levelingDraft?.version,machineInstanceId,existing?.appliedRevision) {
        val draft=levelingDraft
        if(part==CustomWorkoutVisitPart.MAIN && saved==null &&
            prescription.type==DraftSetType.WORKING && draft!=null &&
            appliedLevelVersion!=draft.version) {
            draftEditor.update(load=draft.weight,reps=draft.reps.toString())
            // Preserve entered actual RIR, which Leveling never owns.
            appliedLevelVersion=draft.version
        }
    }
    LaunchedEffect(set.setKey,part,stageOrdinal,machineInstanceId,actualLoad,actualReps,
        saved,levelingDraft?.version,appliedLevelVersion) {
        val pendingLevel=levelingDraft?.let {
            saved==null && appliedLevelVersion!=it.version
        }==true
        if(part==CustomWorkoutVisitPart.MAIN && saved==null &&
            prescription.type==DraftSetType.WORKING && !pendingLevel)
            onCurrentDraftChanged(actualLoad,actualReps)
    }
    // Generated and custom main/amplifier entries share one Kotlin parser.
    // Invalid input cannot bypass the common precision, format or RIR bounds.
    val entry=KotlinWorkoutEntryDomain.parse(target,actualLoad,actualReps,actualRir)
    val actual=(entry as? KotlinSetEntryResult.Accepted)?.performed
    val failedEntry=KotlinWorkoutEntryDomain.parse(
        target,actualLoad,actualReps,actualRir,failed=true)
    val failedActual=(failedEntry as? KotlinSetEntryResult.Accepted)?.performed
    val invalid=(entry as? KotlinSetEntryResult.Rejected)?.reason
    val entryFields:@Composable ()->Unit={
            Text("Edit the values to match your set. Blank RIR stays unreported.",
                style=MaterialTheme.typography.bodySmall)
            if(existing==null && draftEditor.recovered) {
                Text("Recovered an unfinished entry for this exact workout and "+
                    "machine. It has NOT been logged.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={draftEditor.discard()},enabled=!locked) {
                    Text("Discard recovered entry")
                }
            }
            // A historical "Last" shortcut is never a valid source for a
            // same-load activation mini-set, even if both weights happen to
            // match. Preserve athlete edits through the normal fields.
            // "Last session" is context, never an input source for any
            // amplifier. Its authored stage or saved activation owns the
            // default. Only ordinary main working sets expose Apply Last.
            if(saved==null && part==CustomWorkoutVisitPart.MAIN)
                PursuitUseLastCompletedSet(
                mode=referenceMode,
                history=physicalMachineLastReferences(lastReferences,machineInstanceId),
                key=WorkoutReferenceKey(
                    set.setKey,part==CustomWorkoutVisitPart.FOLLOW_UP),
                currentLoad=target,
                enabled=!locked,
                onApply={last->
                    draftEditor.update(
                        load=last.load?.stripTrailingZeros()?.toPlainString().orEmpty(),
                        reps=last.reps.toString(),
                        rir=last.actualRir?.toString().orEmpty())
                }
            )
            NativeSetValueFields(
                unit=verified?.unit?.name?.lowercase().orEmpty(),
                load=actualLoad,
                reps=actualReps,
                rir=actualRir,
                enabled=!locked,
                onLoad={draftEditor.update(load=it)},
                onReps={draftEditor.update(reps=it)},
                onRir={draftEditor.update(rir=it)},
                showLoad=verified!=null,
                loadError=actualLoad.isNotBlank() && invalid in setOf(
                    KotlinSetEntryRefusal.MISSING_WEIGHT,KotlinSetEntryRefusal.INVALID_WEIGHT),
                repsError=actualReps.isNotBlank() && invalid in setOf(
                    KotlinSetEntryRefusal.MISSING_REPS,KotlinSetEntryRefusal.INVALID_REPS),
                rirError=invalid==KotlinSetEntryRefusal.INVALID_RIR,
                allowFailedAttempt=saved?.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT,
                showFieldHelp=false
            )
            if(verified!=null && KotlinVerifiedPlateLayoutDomain.supportsBasis(verified.basis)) {
                OutlinedButton(
                    onClick={onShowPlates(actualLoad,verified.unit,verified.basis)},
                    enabled=!locked && failedActual!=null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    Text("View plates for entered weight")
                }
            }
            val completeButton:@Composable (Modifier)->Unit={buttonModifier->
                Button(enabled=!locked && actual!=null,
                    modifier=buttonModifier,
                    onClick={
                        onRecord(KotlinJournalSetOutcome.COMPLETED,
                            actual)
                    }){Text(if(saved!=null)"Save correction" else "Complete")}
            }
            val skipButton:@Composable (Modifier)->Unit={buttonModifier->
                OutlinedButton(enabled=!locked,modifier=buttonModifier,onClick={
                    pendingExceptionalOutcome=KotlinJournalSetOutcome.SKIPPED
                }){Text(if(isStagedShortRestStop)
                    "End sequence" else "Skip")}
            }
            val failedButton:@Composable (Modifier)->Unit={buttonModifier->
                OutlinedButton(enabled=!locked && failedActual!=null,modifier=buttonModifier,
                    onClick={pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT}) {
                    Text("Failed")
                }
            }
            val largeText=LocalDensity.current.fontScale>=1.35f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if(largeText || maxWidth<350.dp) {
                    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        completeButton(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            skipButton(Modifier.weight(1f))
                            failedButton(Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        skipButton(Modifier.weight(1f))
                        failedButton(Modifier.weight(1f))
                        completeButton(Modifier.weight(1f))
                    }
                }
            }
            TextButton(enabled=!saving,onClick={editing=false}) {
                Text(if(saved!=null)"Cancel correction" else "Collapse set")
            }
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        val label=if(part==CustomWorkoutVisitPart.MAIN)
            "${if(prescription.type==DraftSetType.WARMUP)"Warm-up" else "Set"} $position of $setCount"
        else follower?.let {
            AdvancedAmplifierProtocolDomain.stageLabel(it,stageOrdinal)
        } ?: "Amplifier follow-up"
        Text(label,style=MaterialTheme.typography.titleMedium)
        if(part==CustomWorkoutVisitPart.FOLLOW_UP)when(val origin=followUpEntryLoad) {
            is AmplifierFollowUpEntryLoad.RecordedActivation ->
                Text("Starting weight follows the logged activation set: "+
                    (origin.performed.load?.stripTrailingZeros()
                        ?.toPlainString() ?: "bodyweight")+
                    (origin.performed.unit?.let{" ${it.name.lowercase()}"} ?: "")+
                    ". Your issued target remains unchanged. "+
                    "Changing the weight ends the same-load sequence.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            AmplifierFollowUpEntryLoad.ActivationUnavailable ->
                Text("Activation weight or machine is not verified. "+
                    "No weight is filled from an earlier set.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Unit
        }
        if(part==CustomWorkoutVisitPart.MAIN)plannedLoadNote?.let {note->
            Text(note,style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Every optional technique is discoverable at the ACTUAL working
        // set before it is performed, remains attached after completion,
        // and reflects journaled stage receipts instead of invented work.
        // Keep it compact; no exercise-wide amplifier banner.
        if(part==CustomWorkoutVisitPart.MAIN && amplifierSummary!=null)
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
                shape=MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(PursuitSpace.xs),
                    verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text((if(position==setCount)"Last working set" else "Set $position")+
                        " · ${amplifierSummary.title}",
                        style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.primary)
                    Text(amplifierSummary.planned,
                        style=MaterialTheme.typography.bodySmall)
                    Text(amplifierSummary.progress,
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    amplifierSummary.actuals?.let { observed ->
                        Text(observed,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        if(part==CustomWorkoutVisitPart.FOLLOW_UP && follower!=null) {
            val protocol=AdvancedAmplifierProtocolDomain.template(follower.kind)
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
                shape=MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(PursuitSpace.xs),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    Text("${follower.followUpReps} " +
                        (if(follower.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
                            "partial reps · same load, no rest"
                        else "reps · ${follower.followUpRestSeconds}s short rest") +
                        (if(stageTotal>1)" · stage $stageOrdinal/$stageTotal" else ""),
                        style=MaterialTheme.typography.labelLarge,
                        color=MaterialTheme.colorScheme.primary)
                    if(follower.kind==CustomAmplifierKind.DROP_SET &&
                        stageTotal==2) {
                        val prescribedLoad=target as? DraftSetLoad.Verified
                        Text("Drop $stageOrdinal of $stageTotal · " +
                            (prescribedLoad?.let{
                                "${it.value.toPlainString()} ${it.unit.name.lowercase()}"
                            }?:"unverified load")+
                            " · separately logged actual weight and reps",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(stageTotal>1) {
                        LinearProgressIndicator(
                            progress={(stageOrdinal-1).toFloat()/stageTotal},
                            modifier=Modifier.fillMaxWidth())
                    }
                    Text(protocol.techniqueCue,
                        style=MaterialTheme.typography.bodySmall)
                    Text(
                        (follower.followUpActiveSeconds?.let{
                            "$it s planned follow-up work"
                        }?:"Follow-up work time not authored") +
                        (if(stageTotal>1)
                            " · Each performed stage is logged separately"
                        else " · One separately logged follow-up in this workout"),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(saved==null && (editing || initiallyExpanded)) {
            val referenceReps=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps?.let{RepBand(it,it)} ?:prescription.reps
            else prescription.reps
            PursuitWorkoutSetReference(referenceMode,target,referenceReps,
                if(part==CustomWorkoutVisitPart.FOLLOW_UP)null else prescription.targetRir,
                physicalMachineLastReferences(lastReferences,machineInstanceId),WorkoutReferenceKey(set.setKey,
                    part==CustomWorkoutVisitPart.FOLLOW_UP))
        }
        if(saved!=null){
            Text(nativeSavedSetCaption("Recorded",saved.outcome,saved.performed,
                exerciseId,issuedGymId,machineRoster),
                style=MaterialTheme.typography.bodyMedium)
            TextButton(enabled=!locked,onClick={editing=true},
                modifier=Modifier.heightIn(min=48.dp)) {
                Text("Edit saved set")}
        } else if(!locked){
            if(!editing) {
                OutlinedButton(onClick={editing=true}){Text("Log this set")}
            } else {
            entryFields()
            }
        }
    }
    // Completed/skipped/failed visits remain compact. Correction uses the
    // same explicit Material sheet as generated sets, retaining drafts if
    // persistence rejects the write and closing only for a new receipt.
    if(saved!=null && editing) {
        NativeSetEditSheet(busy=saving,onDismiss={editing=false}) {
            PursuitSectionHeading("Edit saved set",
                if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                    "Amplifier follow-up" else "Set $position of $setCount")
            Text("Saved values change only after you confirm the correction.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(part==CustomWorkoutVisitPart.MAIN)plannedLoadNote?.let {note->
                Text(note,style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            saveError?.let {Text(it,color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)}
            val referenceReps=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps?.let{RepBand(it,it)} ?:prescription.reps
            else prescription.reps
            PursuitWorkoutSetReference(referenceMode,target,referenceReps,
                if(part==CustomWorkoutVisitPart.FOLLOW_UP)null else prescription.targetRir,
                physicalMachineLastReferences(lastReferences,machineInstanceId),
                WorkoutReferenceKey(set.setKey,part==CustomWorkoutVisitPart.FOLLOW_UP))
            entryFields()
        }
    }
    val pending=pendingExceptionalOutcome
    if(pending!=null && !locked) AlertDialog(
        onDismissRequest={pendingExceptionalOutcome=null},
        title={Text(if(pending==KotlinJournalSetOutcome.SKIPPED &&
            isStagedShortRestStop)
            AdvancedAmplifierProtocolDomain.stageEarlyStopTitle(requireNotNull(follower).kind)
        else if(pending==KotlinJournalSetOutcome.SKIPPED)
            "Skip this visit?" else "Log a failed attempt?")},
        text={Text(if(pending==KotlinJournalSetOutcome.SKIPPED &&
            isStagedShortRestStop)
            AdvancedAmplifierProtocolDomain.stageEarlyStopDescription(requireNotNull(follower).kind)
        else if(pending==KotlinJournalSetOutcome.SKIPPED)
            "Skipping records an explicit outcome. It is not a completed set, and you can correct it before Finish."
        else
            "A failed attempt records zero completed reps at your entered attempted load. It does not count as successful work or earned progression.")},
        confirmButton={
            Button(enabled=!locked && (pending==KotlinJournalSetOutcome.SKIPPED||failedActual!=null),
                onClick={
                    pendingExceptionalOutcome=null
                    when(pending) {
                        KotlinJournalSetOutcome.SKIPPED->
                            onRecord(KotlinJournalSetOutcome.SKIPPED,null)
                        KotlinJournalSetOutcome.FAILED_ATTEMPT->
                            onRecord(KotlinJournalSetOutcome.FAILED_ATTEMPT,
                                failedActual)
                        else -> Unit
                    }
                }){Text(if(pending==KotlinJournalSetOutcome.SKIPPED)
                    "Confirm skip" else "Log failed attempt")}
        },
        dismissButton={
            TextButton(onClick={pendingExceptionalOutcome=null}){Text("Cancel")}
        }
    )
}
