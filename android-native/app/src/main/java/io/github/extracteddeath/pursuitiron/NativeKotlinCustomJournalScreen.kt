package io.github.extracteddeath.pursuitiron

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID
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
    onReturnToProgram:()->Unit={}
) {
    val scope=rememberCoroutineScope()
    var referenceMode by rememberWorkoutReferenceMode()
    val lastReferences=rememberWorkoutLastReferences(host,workoutId,custom=true,
        enabled=referenceMode==WorkoutReferenceMode.LAST)
    var journal by remember(host,workoutId) {
        mutableStateOf<CustomWorkoutJournalDraft?>(null)
    }
    var busy by remember(host,workoutId){mutableStateOf(false)}
    var loading by remember(host,workoutId){mutableStateOf(true)}
    var error by remember(host,workoutId){mutableStateOf<String?>(null)}
    var finished by remember(host,workoutId){mutableStateOf(false)}
    var finishConfirm by remember(host,workoutId){mutableStateOf(false)}
    var verifiedFinishedInHistory by remember(host,workoutId){
        mutableStateOf(false)
    }
    var readiness by rememberSaveable(workoutId){mutableStateOf(CoachReadiness.NORMAL)}
    var movementRestricted by rememberSaveable(workoutId){mutableStateOf(false)}
    var liveCoach by remember(host,workoutId){
        mutableStateOf<LiveSetCoachResult.Reviewed?>(null)
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
                "This workout is not an authenticated native custom journal: ${result ?: "storage unavailable"}."
            else null
            loading=false;busy=false
        }
    }
    LaunchedEffect(host,workoutId){refresh()}

    fun record(
        setKey:String,part:CustomWorkoutVisitPart,
        outcome:KotlinJournalSetOutcome,performed:KotlinPerformedSet?
    ) {
        if(busy)return
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
                error="Set not saved: ${result ?: "storage unavailable"}. Reloaded the latest durable journal."
                val recovery=withContext(Dispatchers.IO){
                    runCatching{host.openIssuedCustomWorkout(workoutId)}.getOrNull()
                }
                journal=(recovery as? NativeCustomJournalResult.Durable)
                    ?.issued?.journal
            }
            busy=false
        }
    }
    val current=journal
    val nextVisit=current?.let{CustomWorkoutJournalDomain.nextUnresolvedVisit(it)}
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
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
                    Text("A prepared preview is not an issued workout. No set was recorded.")
                    Button(enabled=!busy,onClick={refresh()}){Text("Retry durable open")}
                }
            } else {
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
                    readiness,movementRestricted,busy) {
                    liveCoach=null
                    if(busy || current.finishedByEventId!=null ||
                        nextVisit?.part!=CustomWorkoutVisitPart.MAIN)
                        return@LaunchedEffect
                    val result=withContext(Dispatchers.IO) {
                        host.reviewLiveCustomNextSet(workoutId,nextVisit.setKey,
                            readiness,movementRestricted,BigDecimal("5"))
                    } as? LiveSetCoachResult.Reviewed
                    if(journal?.revision==current.revision && !busy &&
                        result?.journalRevision==current.revision &&
                        result.setKey==nextVisit.setKey)
                        liveCoach=result
                }
                PageHeading("TODAY'S TRAINING",
                    current.snapshot.dayLabel,
                    "Week ${current.snapshot.absoluteWeek} · ${current.snapshot.phase.name.replace('_',' ')}")
                PursuitCard(highlighted=true) {
                    Text("${mainLogged} of ${mainTotal} sets recorded",
                        style=MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(
                        progress={if(mainTotal==0)0f else
                            mainLogged.toFloat()/mainTotal.toFloat()},
                        modifier=Modifier.fillMaxWidth())
                    Text("${current.unresolvedVisitCount} required entries remain, including amplifier follow-ups. Nothing is saved until you confirm a set.",
                        style=MaterialTheme.typography.bodySmall)
                    if(finished||current.finishedByEventId!=null) {
                        Text(if(verifiedFinishedInHistory)
                            "Workout finished and verified in committed native history."
                            else "Finish receipt exists, but committed history has not yet been verified. Recover the interrupted Finish before advancing.",
                            color=if(verifiedFinishedInHistory)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error)
                        if(verifiedFinishedInHistory) {
                            Button(
                                onClick=onReturnToProgram,
                                modifier=Modifier.fillMaxWidth()
                            ){Text("Continue to my next workout")}
                            OutlinedButton(
                                onClick=onViewHistory,
                                modifier=Modifier.fillMaxWidth()
                            ){Text("View finished workout in history")}
                        }
                    }
                    OutlinedButton(enabled=!busy,
                        onClick={refresh()}){Text("Reload saved journal")}
                }
                if(!finished) {
                    Text("How are you feeling?",style=MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CoachReadiness.entries.forEach {option->
                            FilterChip(selected=readiness==option,
                                enabled=!busy,onClick={readiness=option},
                                label={Text(option.name.lowercase().replaceFirstChar {
                                    it.uppercase()
                                })})
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=movementRestricted,enabled=!busy,
                            onCheckedChange={movementRestricted=it})
                        Text("Movement restricted")
                    }
                    if(movementRestricted)Text(
                        "Review an adjustment or alternate movement before continuing.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodyMedium)
                }
                PursuitWorkoutReferencePicker(referenceMode,{referenceMode=it})
                current.snapshot.slots.forEach {slot->
                    PursuitCard {
                        Text(kotlinExerciseLabel(slot.exerciseId),
                            style=MaterialTheme.typography.titleLarge)
                        if(slot.coachNote.isNotBlank())
                            Text(slot.coachNote,
                                style=MaterialTheme.typography.bodySmall)
                        slot.sets.forEach {set->
                            val mainKey=CustomWorkoutVisitKey(
                                set.setKey,CustomWorkoutVisitPart.MAIN)
                            val observed=current.effectiveVisits[mainKey]
                            val advice=liveCoach?.takeIf {
                                !busy && !finished && mainKey==nextVisit &&
                                    it.workoutId==workoutId && it.setKey==set.setKey &&
                                    it.journalRevision==current.revision
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
                            NativeKotlinCustomVisitEditor(
                                set=set,part=CustomWorkoutVisitPart.MAIN,
                                existing=observed,locked=busy||finished,
                                referenceMode=referenceMode,lastReferences=lastReferences,
                                position=slot.sets.filter{it.prescription.type==set.prescription.type}
                                    .indexOf(set)+1,
                                setCount=slot.sets.count{it.prescription.type==set.prescription.type},
                                initiallyExpanded=nextVisit==mainKey,
                                onRecord={outcome,actual->
                                    record(set.setKey,CustomWorkoutVisitPart.MAIN,
                                        outcome,actual)
                                })
                            val main=(observed?.operation
                                as? CustomWorkoutJournalOperation.Record)
                            if(set.prescription.amplifier!=null &&
                                main?.outcome==KotlinJournalSetOutcome.COMPLETED) {
                                val follower=current.effectiveVisits[
                                    CustomWorkoutVisitKey(
                                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)]
                                NativeKotlinCustomVisitEditor(
                                    set=set,part=CustomWorkoutVisitPart.FOLLOW_UP,
                                    existing=follower,locked=busy||finished,
                                    referenceMode=referenceMode,lastReferences=lastReferences,
                                    position=slot.sets.filter{it.prescription.type==set.prescription.type}
                                        .indexOf(set)+1,
                                    setCount=slot.sets.count{it.prescription.type==set.prescription.type},
                                    initiallyExpanded=nextVisit==CustomWorkoutVisitKey(
                                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP),
                                    onRecord={outcome,actual->
                                        record(set.setKey,
                                            CustomWorkoutVisitPart.FOLLOW_UP,
                                            outcome,actual)
                                    })
                            }
                        }
                    }
                }
                PursuitCard {
                    Text("Finish workout",
                        style=MaterialTheme.typography.titleMedium)
                    Text(if(current.canProposeFinish)
                        "Every required main set and independent amplifier visit has an actual outcome. Finish will be committed to verified native history."
                    else "Record or explicitly skip each remaining visit before finishing. An unfinished amplifier is never assumed complete.",
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
        Text("Native custom journal only. The approved set targets cannot be overwritten by coaching, UI defaults or the web app.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Fixed below the scroll, so focusing a later set cannot hide the timer
    // and the timer never overlays a set-entry control or the Finish button.
    if(current!=null && current.unresolvedVisitCount>0 && !finished)
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
            NativeKotlinWorkoutRestTimer(workoutId,
                KotlinWorkoutRestDomain.inspect(current,nextVisit),enabled=!busy)
        }
    }
    val selected=journal
    if(finishConfirm&&selected!=null) {
        AlertDialog(onDismissRequest={if(!busy)finishConfirm=false},
            title={Text("Finish and save this workout?")},
            text={Text("All ${selected.snapshot.slots.sumOf{it.sets.size}} main sets and required follow-ups have recorded outcomes. Finish permanently commits these actual results to native history.")},
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
    referenceMode:WorkoutReferenceMode,
    lastReferences:WorkoutLastReferenceResult?,
    position:Int,
    setCount:Int,
    initiallyExpanded:Boolean,
    onRecord:(KotlinJournalSetOutcome,KotlinPerformedSet?)->Unit
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
    var actualLoad by rememberSaveable(set.setKey,part.name,existing?.appliedRevision) {
        mutableStateOf(saved?.performed?.load?.toPlainString() ?:
            verified?.value?.toPlainString().orEmpty())
    }
    var actualRir by rememberSaveable(set.setKey,part.name,existing?.appliedRevision) {
        mutableStateOf(saved?.performed?.actualRir?.toString().orEmpty())
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
        if(editing || initiallyExpanded) {
            val referenceReps=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
                follower?.followUpReps?.let{RepBand(it,it)} ?:prescription.reps
            else prescription.reps
            PursuitWorkoutSetReference(referenceMode,target,referenceReps,
                if(part==CustomWorkoutVisitPart.FOLLOW_UP)null else prescription.targetRir,
                lastReferences,WorkoutReferenceKey(set.setKey,
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
            Text("Planned weight and reps are prefilled for convenience. Correct them to what you actually did before completing. Blank RIR stays unreported.",
                style=MaterialTheme.typography.bodySmall)
            if(verified!=null)
                OutlinedTextField(value=actualLoad,onValueChange={actualLoad=it},
                    label={Text("Weight · ${verified.unit.name.lowercase()}")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    singleLine=true,modifier=Modifier.fillMaxWidth(),
                    isError=actualLoad.isNotBlank()&&weight==null)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            OutlinedTextField(value=actualReps,onValueChange={actualReps=it},
                label={Text("Reps completed")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                modifier=Modifier.weight(1f),
                isError=actualReps.isNotBlank()&&reps==null)
            OutlinedTextField(value=actualRir,onValueChange={actualRir=it},
                label={Text("RIR · optional")},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                singleLine=true,modifier=Modifier.weight(1f),
                isError=!effortValid)
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=loadValid&&reps!=null&&effortValid,
                    modifier=Modifier.weight(1f),
                    onClick={
                        onRecord(KotlinJournalSetOutcome.COMPLETED,
                            KotlinPerformedSet(weight,verified?.unit,
                                checkNotNull(reps),effort))
                    }){Text("Complete")}
                OutlinedButton(modifier=Modifier.weight(1f),onClick={
                    pendingExceptionalOutcome=KotlinJournalSetOutcome.SKIPPED
                }){Text("Skip")}
                OutlinedButton(enabled=loadValid,
                    modifier=Modifier.weight(1f),
                    onClick={
                        pendingExceptionalOutcome=KotlinJournalSetOutcome.FAILED_ATTEMPT
                    }){Text("Failed")}
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
