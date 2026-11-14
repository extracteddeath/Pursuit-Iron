package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
    val scope=rememberCoroutineScope()
    var machineRoster by remember(host,workoutId){
        mutableStateOf<NativeMachineRosterSnapshot?>(null)
    }
    LaunchedEffect(host,workoutId) {
        machineRoster=withContext(Dispatchers.IO) {
            (host.readMachineRoster() as? NativeMachineRosterResult.Saved)?.snapshot
        }
    }
    val workoutScroll=rememberScrollState()
    var showFinishedSets by rememberSaveable(workoutId){mutableStateOf(false)}
    var focusOnNext by rememberSaveable(workoutId){mutableStateOf(true)}
    var showSessionNote by rememberSaveable(workoutId){mutableStateOf(false)}
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
    var levelVersion by rememberSaveable(workoutId){mutableIntStateOf(0)}
    // A previous completed workout is an optional source-verified review.
    // Only a deliberate athlete tap may populate current unsaved editors.
    var priorLevelingSlotId by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    var referenceMode by rememberWorkoutReferenceMode()
    var historyExerciseId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var infoSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
    var plateRequest by remember(workoutId){
        mutableStateOf<NativeCustomPlateRequest?>(null)
    }
    var swapSlotId by rememberSaveable(workoutId){mutableStateOf<String?>(null)}
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
            ?.map{it.exerciseId}?.distinct()?.joinToString(";").orEmpty())
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
    var finishConfirm by remember(host,workoutId){mutableStateOf(false)}
    // An explicit current-visit action only. It never authorizes a stored
    // skip, and cannot move to another visit while a confirmation is open.
    var pendingQuickSkip by remember(workoutId) {
        mutableStateOf<CustomWorkoutVisitKey?>(null)
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
    DisposableEffect(view,running) {
        val previous=view.keepScreenOn
        if(running)view.keepScreenOn=true
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
        outcome:KotlinJournalSetOutcome,performed:KotlinPerformedSet?
    ) {
        if(busy || !machineChoices.ready || machineChoices.busy)return
        val current=journal ?:return
        val visit=CustomWorkoutVisitKey(setKey,part)
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
    val lastReferences=rememberWorkoutLastReferences(host,workoutId,custom=true,
        enabled=(referenceMode==WorkoutReferenceMode.LAST ||
            priorLevelingSlotId!=null) && current!=null)
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
                val activeFocus=nextVisit.takeIf {
                    focusOnNext && !finished && current.finishedByEventId==null
                }
                val mainTotal=current.snapshot.slots.sumOf{it.sets.size}
                val mainLogged=current.snapshot.slots.sumOf {slot->
                    slot.sets.count {set->
                        CustomWorkoutVisitKey(set.setKey,
                            CustomWorkoutVisitPart.MAIN) in current.effectiveVisits
                    }
                }
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
                    Text("${mainLogged} of ${mainTotal} sets recorded",
                        style=MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(
                        progress={if(mainTotal==0)0f else
                            mainLogged.toFloat()/mainTotal.toFloat()},
                        modifier=Modifier.fillMaxWidth())
                    if(!finished && current.finishedByEventId==null) {
                        OutlinedButton(
                            onClick={noteError=null;showSessionNote=true},
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        ) {
                            Text(if(current.sessionNote.isBlank())
                                "Add session note" else "Edit saved session note")
                        }
                    } else if(current.sessionNote.isNotBlank()) {
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
                    val upcoming=nextVisit?.let {visit->
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
                        if(nextVisit?.part==CustomWorkoutVisitPart.FOLLOW_UP)
                            "Amplifier follow-up · $label $number of ${sameType.size}"
                        else
                            "$label $number of ${sameType.size}"
                    }
                    PursuitWorkoutFocusHeader(
                        exerciseLabel=upcoming?.let {
                            kotlinExerciseLabel(it.first.exerciseId)
                        },
                        setDetail=focusDetail,
                        focusOnNext=focusOnNext,
                        onFocusChange={focusOnNext=it},
                        enabled=!busy,
                        onHistory=upcoming?.first?.exerciseId?.let{exerciseId->
                            {historyExerciseId=exerciseId}
                        },
                        onInfo=upcoming?.first?.slotId?.let{slotId->
                            {infoSlotId=slotId}
                        },
                        onSkip=nextVisit?.takeIf {
                            machineChoices.ready && !machineChoices.busy
                        }?.let { visit ->
                            {pendingQuickSkip=visit}
                        }
                    )
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
                            showSessionNote=true
                        }
                    )
                }
                if(!verifiedFinishedInHistory || showFinishedSets) {
                PursuitWorkoutReferencePicker(referenceMode,{referenceMode=it})
                current.snapshot.slots.filter {slot->
                    activeFocus==null ||
                        slot.sets.any{it.setKey==activeFocus.setKey}
                }.forEach {slot->
                    // The immutable issued-workout gym owns this choice.
                    val config=KotlinMachineAlternativesDomain.forGymExercise(
                        machineRoster?.configurations.orEmpty(),
                        current.originalSnapshot.stamp.gymId,slot.exerciseId)
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
                    NativeKotlinWorkoutMachineSelector(config,selectedMachineId,
                        enabled=!busy && !finished && current.finishedByEventId==null &&
                            machineChoices.ready && !machineChoices.busy,
                        onSelect={machineId->
                            machineChoices.onSelect(slot.exerciseId,machineId)
                        })
                    machineChoices.error?.let{
                        Text(it,style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.error)
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
                            currentRows.count{row->row.load?.let{it>BigDecimal.ZERO}==true}>=2)
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
                        NativeKotlinPinnedExerciseControl(
                            host=host,workoutId=workoutId,
                            exerciseId=slot.exerciseId,custom=true,
                            enabled=!busy && !finished &&
                                current.finishedByEventId==null
                        )
                        if(!finished && current.finishedByEventId==null) {
                            val hasRecordedWork=slot.sets.any{set ->
                                current.receipts.any{receipt ->
                                    (receipt.operation as? CustomWorkoutJournalOperation.Record)
                                        ?.visit?.setKey==set.setKey
                                }
                            }
                            OutlinedButton(
                                enabled=!busy && slot.supersetGroup==null &&
                                    !hasRecordedWork,
                                modifier=Modifier.heightIn(min=48.dp),
                                onClick={reviewSwap(slot)}
                            ) {Text("Swap exercise")}
                            val undoableSwap=current.receipts.asReversed()
                                .mapNotNull {receipt->
                                    receipt.operation as?
                                        CustomWorkoutJournalOperation.Swap
                                }.firstOrNull {swap->
                                    swap.replacement.slotId==slot.slotId &&
                                        current.receipts.none{receipt->
                                            (receipt.operation as?
                                                CustomWorkoutJournalOperation.UndoSwap)
                                                ?.swapEventId==swap.eventId
                                        }
                                }
                            if(undoableSwap!=null && !hasRecordedWork &&
                                slot.supersetGroup==null) {
                                OutlinedButton(
                                    enabled=!busy,
                                    modifier=Modifier.heightIn(min=48.dp),
                                    onClick={
                                        undoSwapConfirmEventId=undoableSwap.eventId
                                    }) {Text("Undo swap")}
                            }
                            if(hasRecordedWork || slot.supersetGroup!=null)
                                Text(if(hasRecordedWork)
                                    "This exercise has recorded work. Its original history is protected; live swapping is only available before its first set."
                                    else "Unlink this superset in your program before replacing a linked exercise.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                                existing=observed,locked=busy||finished||!machineChoices.ready||machineChoices.busy,
                                machineInstanceId=mainMachineId,
                                referenceMode=referenceMode,lastReferences=lastReferences,
                                levelingDraft=if(observed==null &&
                                    set.prescription.type==DraftSetType.WORKING &&
                                    slot.slotId==levelSlotId &&
                                    mainMachineId==levelMachineId &&
                                    levelWeight.isNotBlank() && levelReps>0 &&
                                    (currentEditors[levelEditorKey(
                                        set.setKey,mainMachineId)]?.weight
                                        ?:initialMachineScopedLoad(
                                            set.prescription.load,null,mainMachineId))
                                        .toBigDecimalOrNull()
                                        ?.let{it>BigDecimal.ZERO}==true)
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
                                initiallyExpanded=nextVisit==mainKey,
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
                                val follower=current.effectiveVisits[
                                    CustomWorkoutVisitKey(
                                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)]
                                editorStates.SaveableStateProvider("follow:${set.setKey}") {
                                NativeKotlinCustomVisitEditor(
                                    set=set,part=CustomWorkoutVisitPart.FOLLOW_UP,
                                    existing=follower,locked=busy||finished||!machineChoices.ready||machineChoices.busy,
                                    machineInstanceId=if(follower!=null)
                                        (follower.operation as?
                                            CustomWorkoutJournalOperation.Record)
                                            ?.performed?.machineInstanceId
                                    else if(main!=null)main.performed?.machineInstanceId
                                    else selectedMachineId,
                                    referenceMode=referenceMode,lastReferences=lastReferences,
                                    position=slot.sets.filter{it.prescription.type==set.prescription.type}
                                        .indexOf(set)+1,
                                    setCount=slot.sets.count{it.prescription.type==set.prescription.type},
                                    initiallyExpanded=nextVisit==CustomWorkoutVisitKey(
                                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP),
                                    onRecord={outcome,actual->
                                        record(set.setKey,
                                            CustomWorkoutVisitPart.FOLLOW_UP,
                                            outcome,actual?.copy(
                                                machineInstanceId=if(follower!=null)
                                                    (follower.operation as?
                                                        CustomWorkoutJournalOperation.Record)
                                                        ?.performed?.machineInstanceId
                                                else if(main!=null)main.performed?.machineInstanceId
                                                else selectedMachineId))
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
                if(!verifiedFinishedInHistory)PursuitCard {
                    Text("Finish workout",
                        style=MaterialTheme.typography.titleMedium)
                    Text(if(current.canProposeFinish)
                        "Every set and amplifier follow-up has a recorded outcome. Finish to save this workout in History."
                    else "Log or skip the remaining sets and amplifier follow-ups before finishing.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(
                        enabled=!busy&&current.canProposeFinish&&!finished,
                        onClick={finishConfirm=true}) {
                        Text("Review Finish")}
                    if(!verifiedFinishedInHistory)
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
                KotlinWorkoutRestDomain.inspect(current,nextVisit),enabled=!busy)
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
                NativeKotlinWorkoutExerciseInfoSheet(
                    exerciseId=slot.exerciseId,
                    sourceDescription="Issued custom workout · "+
                        slot.role.name.lowercase().replace('_',' ')+
                        (if(slot.supersetGroup!=null)" · superset linked" else "")+
                        (if(slot.pinned)" · pinned" else ""),
                    sets=customWorkoutInfoSets(slot.sets),
                    note=slot.coachNote.takeIf{it.isNotBlank()},
                    onHistory={
                        infoSlotId=null
                        historyExerciseId=slot.exerciseId
                    },
                    onDismiss={infoSlotId=null}
                )
            }
    }
    historyExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={historyExerciseId=null})
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
            text={Text(if(stillNext)
                "Skipping records an explicit outcome for this ${if(quickSkip.part==CustomWorkoutVisitPart.FOLLOW_UP) "amplifier follow-up" else "set"}. You can correct it before Finish."
            else "The saved workout advanced while this confirmation was open. Dismiss and review the new next entry; no set was changed.")},
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
                            KotlinJournalSetOutcome.SKIPPED,null)
                }) {Text("Confirm skip")}
            },
            dismissButton={
                TextButton(onClick={pendingQuickSkip=null}) {Text("Keep training")}
            }
        )
    }
    val selected=journal
    if(finishConfirm&&selected!=null) {
        AlertDialog(onDismissRequest={if(!busy)finishConfirm=false},
            title={Text("Finish and save this workout?")},
            text={Text("Save the recorded results for this workout to History. You can review and correct saved sets there.")},
            confirmButton={
                Button(enabled=!busy&&selected.canProposeFinish,
                    onClick={
                        finishConfirm=false
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO){
                                runCatching{
                                    host.finishIssuedCustomWorkout(workoutId,
                                        CustomWorkoutJournalOperation.Finish(
                                            "custom-finish-"+UUID.randomUUID(),
                                            selected.revision))
                                }.getOrNull()
                            }
                            finished=result is NativeCustomFinishResult.Committed
                            verifiedFinishedInHistory=if(finished)
                                verifyCommittedHistory()
                            else false
                            error=if(finished && verifiedFinishedInHistory)null else
                                if(finished)
                                    "Finish was committed, but the history read could not be independently verified. Use recovery before advancing."
                                else
                                "Finish not committed: ${result ?: "storage unavailable"}. The workout remains recoverable."
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
                TextButton(enabled=!busy,onClick={finishConfirm=false}) {
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
    machineInstanceId:String?,
    referenceMode:WorkoutReferenceMode,
    lastReferences:WorkoutLastReferenceResult?,
    position:Int,
    setCount:Int,
    initiallyExpanded:Boolean,
    onRecord:(KotlinJournalSetOutcome,KotlinPerformedSet?)->Unit,
    onShowPlates:(String,LoadUnit,LoadBasis)->Unit,
    levelingDraft:CurrentWorkoutLevelingDraft?=null,
    onCurrentDraftChanged:(String,String)->Unit={_,_->}
) {
    val prescription=set.prescription
    val follower=prescription.amplifier
    val target=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
        follower?.followUpLoad ?:prescription.load
    else prescription.load
    val verified=target as? DraftSetLoad.Verified
    val saved=existing?.operation as? CustomWorkoutJournalOperation.Record
    var editing by rememberSaveable(set.setKey,part.name,existing?.appliedRevision) {
        mutableStateOf(saved==null && initiallyExpanded)
    }
    // Skips and failed attempts are durable factual observations, not
    // ordinary navigation. Confirm before writing to the owned journal.
    var pendingExceptionalOutcome by rememberSaveable(
        set.setKey,part.name,existing?.appliedRevision
    ) {mutableStateOf<KotlinJournalSetOutcome?>(null)}
    LaunchedEffect(locked) {
        if(locked)pendingExceptionalOutcome=null
    }
    val reveal=remember(set.setKey,part){BringIntoViewRequester()}
    LaunchedEffect(set.setKey,part,initiallyExpanded,locked,existing?.appliedRevision) {
        if(initiallyExpanded && saved==null && !locked) {
            editing=true
            // A long workout can contain dozens of sets. Bring the next
            // unrecorded one into view after each durable save.
            reveal.bringIntoView()
        }
    }
    // These values are editable targets, NEVER synthesized performed facts.
    // They become recorded actuals only after the athlete taps Complete.
    var actualReps by rememberSaveable(set.setKey,part.name,existing?.appliedRevision) {
        mutableStateOf(saved?.performed?.reps?.toString() ?:
            (if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps else prescription.reps.minimum)
                ?.toString().orEmpty())
    }
    // A/B station changes invalidate only this unsaved editor baseline,
    // never the issued prescription or a durable actual. No asynchronous
    // effect may erase an in-progress athlete edit during recomposition.
    var actualLoad by rememberSaveable(
        set.setKey,part.name,existing?.appliedRevision,machineInstanceId
    ) {
        mutableStateOf(initialMachineScopedLoad(target,saved?.performed,machineInstanceId))
    }
    var actualRir by rememberSaveable(set.setKey,part.name,existing?.appliedRevision) {
        mutableStateOf(saved?.performed?.actualRir?.toString().orEmpty())
    }
    var appliedLevelVersion by rememberSaveable(
        set.setKey,part.name,existing?.appliedRevision,machineInstanceId
    ) {mutableIntStateOf(-1)}
    LaunchedEffect(levelingDraft?.version,machineInstanceId,existing?.appliedRevision) {
        val draft=levelingDraft
        if(part==CustomWorkoutVisitPart.MAIN && saved==null &&
            prescription.type==DraftSetType.WORKING && draft!=null &&
            appliedLevelVersion!=draft.version) {
            actualLoad=draft.weight
            actualReps=draft.reps.toString()
            // Preserve entered actual RIR, which Leveling never owns.
            appliedLevelVersion=draft.version
        }
    }
    LaunchedEffect(set.setKey,part,machineInstanceId,actualLoad,actualReps,
        saved,levelingDraft?.version,appliedLevelVersion) {
        val pendingLevel=levelingDraft?.let {
            saved==null && appliedLevelVersion!=it.version
        }==true
        if(part==CustomWorkoutVisitPart.MAIN && saved==null &&
            prescription.type==DraftSetType.WORKING && !pendingLevel)
            onCurrentDraftChanged(actualLoad,actualReps)
    }
    val weight=if(verified==null)null else
        runCatching{BigDecimal(actualLoad.trim())}.getOrNull()
            ?.takeIf{it>=BigDecimal.ZERO&&it<=BigDecimal("100000")&&
                it.stripTrailingZeros().scale()<=3}
    val reps=actualReps.trim().toIntOrNull()?.takeIf{it in 1..100}
    val effort=if(actualRir.isBlank())null else
        actualRir.trim().toIntOrNull()?.takeIf{it in 0..10}
    val effortValid=actualRir.isBlank()||effort!=null
    val loadValid=verified==null||weight!=null
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal),
        verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        val label=if(part==CustomWorkoutVisitPart.MAIN)
            "${if(prescription.type==DraftSetType.WARMUP)"Warm-up" else "Set"} $position of $setCount"
        else "Amplifier · ${follower?.kind?.name?.replace('_',' ')}"
        Text(label,style=MaterialTheme.typography.titleMedium)
        if(part==CustomWorkoutVisitPart.FOLLOW_UP && follower!=null)
            Text(
                "Planned follow-up: ${follower.followUpReps} reps · " +
                    (follower.followUpActiveSeconds?.let{
                        "$it s additional active work"
                    }?:"additional active work time not authored") +
                    " · ${follower.followUpRestSeconds}s intra-set rest",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        if(editing || initiallyExpanded) {
            val referenceReps=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps?.let{RepBand(it,it)} ?:prescription.reps
            else prescription.reps
            PursuitWorkoutSetReference(referenceMode,target,referenceReps,
                if(part==CustomWorkoutVisitPart.FOLLOW_UP)null else prescription.targetRir,
                physicalMachineLastReferences(lastReferences,machineInstanceId),WorkoutReferenceKey(set.setKey,
                    part==CustomWorkoutVisitPart.FOLLOW_UP))
        }
        if(saved!=null&&!editing){
            Text("Recorded: ${saved.outcome.name.replace('_',' ').lowercase()}" +
                (saved.performed?.let{" · ${it.reps} reps" +
                    (it.load?.let{load->" at $load"} ?: "")+
                    (it.actualRir?.let{rir->" · $rir RIR"} ?: "")} ?: ""),
                style=MaterialTheme.typography.bodyMedium)
            TextButton(enabled=!locked,onClick={editing=true}) {
                Text("Correct recorded visit")}
        } else if(!locked){
            if(!editing) {
                OutlinedButton(onClick={editing=true}){Text("Log this set")}
            } else {
            Text("Edit the values to match your set. Blank RIR stays unreported.",
                style=MaterialTheme.typography.bodySmall)
            if(saved==null) PursuitUseLastCompletedSet(
                mode=referenceMode,
                history=physicalMachineLastReferences(lastReferences,machineInstanceId),
                key=WorkoutReferenceKey(
                    set.setKey,part==CustomWorkoutVisitPart.FOLLOW_UP),
                currentLoad=target,
                enabled=!locked,
                onApply={last->
                    actualLoad=last.load?.stripTrailingZeros()?.toPlainString().orEmpty()
                    actualReps=last.reps.toString()
                    actualRir=last.actualRir?.toString().orEmpty()
                }
            )
            NativeSetValueFields(
                unit=verified?.unit?.name?.lowercase().orEmpty(),
                load=actualLoad,
                reps=actualReps,
                rir=actualRir,
                enabled=!locked,
                onLoad={actualLoad=it},
                onReps={actualReps=it},
                onRir={actualRir=it},
                showLoad=verified!=null,
                loadError=actualLoad.isNotBlank()&&weight==null,
                repsError=actualReps.isNotBlank()&&reps==null,
                rirError=!effortValid,
                showFieldHelp=false
            )
            if(verified?.basis==LoadBasis.BARBELL_TOTAL) {
                OutlinedButton(
                    onClick={onShowPlates(actualLoad,verified.unit,verified.basis)},
                    enabled=!locked && weight!=null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {
                    Text("View plates for entered barbell weight")
                }
            }
            val completeButton:@Composable (Modifier)->Unit={buttonModifier->
                Button(enabled=loadValid&&reps!=null&&effortValid,
                    modifier=buttonModifier,
                    onClick={
                        onRecord(KotlinJournalSetOutcome.COMPLETED,
                            KotlinPerformedSet(weight,verified?.unit,
                                checkNotNull(reps),effort))
                    }){Text("Complete")}
            }
            val skipButton:@Composable (Modifier)->Unit={buttonModifier->
                OutlinedButton(modifier=buttonModifier,onClick={
                    pendingExceptionalOutcome=KotlinJournalSetOutcome.SKIPPED
                }){Text("Skip")}
            }
            val failedButton:@Composable (Modifier)->Unit={buttonModifier->
                OutlinedButton(enabled=loadValid,modifier=buttonModifier,
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
            TextButton(onClick={editing=false}) {
                Text(if(saved!=null)"Cancel correction" else "Collapse set")
            }
            }
        }
    }
    val pending=pendingExceptionalOutcome
    if(pending!=null && !locked) AlertDialog(
        onDismissRequest={pendingExceptionalOutcome=null},
        title={Text(if(pending==KotlinJournalSetOutcome.SKIPPED)
            "Skip this visit?" else "Log a failed attempt?")},
        text={Text(if(pending==KotlinJournalSetOutcome.SKIPPED)
            "Skipping records an explicit outcome. It is not a completed set, and you can correct it before Finish."
        else
            "A failed attempt records zero completed reps at your entered attempted load. It does not count as successful work or earned progression.")},
        confirmButton={
            Button(enabled=pending==KotlinJournalSetOutcome.SKIPPED||loadValid,
                onClick={
                    pendingExceptionalOutcome=null
                    when(pending) {
                        KotlinJournalSetOutcome.SKIPPED->
                            onRecord(KotlinJournalSetOutcome.SKIPPED,null)
                        KotlinJournalSetOutcome.FAILED_ATTEMPT->
                            onRecord(KotlinJournalSetOutcome.FAILED_ATTEMPT,
                                KotlinPerformedSet(weight,verified?.unit,0,null))
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
