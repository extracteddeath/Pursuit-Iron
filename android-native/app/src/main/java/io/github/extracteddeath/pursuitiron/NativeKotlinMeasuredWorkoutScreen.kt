package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Kotlin-only athlete-facing entry into already verified typed parent journals.
 * Phase one intentionally admits UNWEIGHTED timed holds only. Carries, loaded
 * holds and bands must get their own exact physical stock/set-up editors, not
 * an invented 0-lb rep target or an auto-approved machine.
 */
@Composable
internal fun NativeKotlinMeasuredWorkoutScreen(
    host:NativeKotlinCandidateHost,
    onClose:()->Unit,
    onViewHistory:(String)->Unit
) {
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var sourceRead by remember(host){mutableStateOf<NativeAuthoringSourceRead?>(null)}
    var stored by remember(host){mutableStateOf<NativeMeasuredWorkoutStored?>(null)}
    var message by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedId by rememberSaveable{mutableStateOf("plank")}
    var target by rememberSaveable{mutableStateOf("30")}
    var setCount by rememberSaveable{mutableIntStateOf(2)}
    var review by remember{mutableStateOf<NativeMeasuredConsentResult.Preview?>(null)}
    var finishDialog by remember{mutableStateOf(false)}
    var menuOpen by remember{mutableStateOf(false)}
    var workoutId by rememberSaveable {
        mutableStateOf("measured-"+UUID.randomUUID().toString().replace("-",""))
    }
    suspend fun reload() {
        val read=withContext(Dispatchers.IO){host.openMeasuredWorkout(workoutId)}
        stored=(read as? NativeMeasuredWorkoutStoreResult.Durable)?.stored
    }
    LaunchedEffect(host,workoutId) {
        sourceRead=withContext(Dispatchers.IO){host.reviewMeasuredAuthoringSource()}
        reload()
    }
    BackHandler(enabled=!busy){onClose()}
    val source=(sourceRead as? NativeAuthoringSourceRead.Found)?.source
    val setup=source?.setup?.reviewed
    val gym=setup?.gym
    val fixtures=(gym?.availableFacilities.orEmpty()+
        gym?.availableAttachments.orEmpty()).toSet()
    val confirmed=fixtures+
        (if("pullup-bar" in fixtures)setOf("pull-up-bar") else emptySet())+
        (if("wall-support" in fixtures)setOf("stable-wall") else emptySet())
    val options=MeasuredExerciseCatalogDomain.all.filter { definition ->
        definition.loadKind==MeasuredLoadKind.UNWEIGHTED &&
            definition.kind==MeasuredExerciseKind.HOLD_SECONDS &&
            setup!=null && setup.allowBodyweight &&
            definition.exerciseId !in setup.excludedExerciseIds &&
            definition.requiredFixtures.all{it in confirmed}
    }
    val definition=options.firstOrNull{it.exerciseId==selectedId}
    val quantity=target.toIntOrNull()
    val station=definition?.requiredFixtures?.firstOrNull()?.let {
        when {
            it=="pull-up-bar" && "pullup-bar" in fixtures -> "pullup-bar"
            it=="stable-wall" && "wall-support" in fixtures -> "wall-support"
            else -> it
        }
    } ?: "floor"
    val sides=when(definition?.sideRule) {
        MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
        MeasuredSideRule.LEFT_AND_RIGHT ->
            setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
        else -> emptySet()
    }
    val candidate=if(definition!=null && source!=null && gym!=null &&
        quantity!=null && quantity in 1..3600 && setCount in 1..4)
        MeasuredWorkoutPlan(workoutId,gym.gymId,source.revision,
            (1..setCount).map { number ->
                MeasuredExerciseSetPlan(workoutId,"set-"+number,
                    definition.exerciseId,gym.gymId,source.revision,
                    station,quantity,sides,definition.requiredFixtures)
            })
        else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(PursuitSpace.md),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Measured workout",style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy,onClick=onClose){Text("Minimize")}
        }
        if(stored==null) {
            Text("Timed holds use real seconds, with left and right sides where "+
                "required. No repetitions, loads or PRs are invented.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            when(sourceRead) {
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is NativeAuthoringSourceRead.Found -> {
                    if(options.isEmpty())Text("No timed hold is currently "+
                        "supported by your verified gym and training setup.",
                        color=MaterialTheme.colorScheme.error)
                    else {
                        Box {
                            OutlinedButton(enabled=!busy,onClick={menuOpen=true}) {
                                Text(definition?.displayName ?: "Choose a hold")
                            }
                            DropdownMenu(expanded=menuOpen,
                                onDismissRequest={menuOpen=false}) {
                                options.forEach { choice ->
                                    DropdownMenuItem(text={Text(choice.displayName)},
                                        onClick={
                                            selectedId=choice.exerciseId
                                            review=null;message=null;menuOpen=false
                                        })
                                }
                            }
                        }
                        OutlinedTextField(value=target,onValueChange={
                            target=it;review=null},
                            label={Text("Target seconds per set")},
                            enabled=!busy,modifier=Modifier.fillMaxWidth(),
                            singleLine=true,keyboardOptions=KeyboardOptions(
                                keyboardType=KeyboardType.Number))
                        Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            (1..4).forEach{number->
                                FilterChip(selected=setCount==number,
                                    enabled=!busy,
                                    onClick={setCount=number;review=null},
                                    label={Text(number.toString()+
                                        if(number==1)" set" else " sets")})
                            }
                        }
                        Text("Verified physical setup: "+station,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(enabled=!busy && candidate!=null,
                            modifier=Modifier.fillMaxWidth(),onClick={
                                val proposal=candidate ?:return@Button
                                val sourceRevision=source?.revision ?:return@Button
                                busy=true;message=null
                                scope.launch {
                                    try {
                                        val result=withContext(Dispatchers.IO) {
                                            host.previewMeasuredWorkoutConsent(
                                                proposal,sourceRevision)
                                        }
                                        review=result as? NativeMeasuredConsentResult.Preview
                                        if(result is NativeMeasuredConsentResult.Rejected)
                                            message="Review refused: "+result.reason.name
                                    } catch(_:Exception) {
                                        message="Measured source review unavailable."
                                    } finally{busy=false}
                                }
                            }) {Text("Review measured workout")}
                    }
                    OutlinedButton(enabled=!busy,
                        modifier=Modifier.fillMaxWidth(),onClick={
                            busy=true;message=null
                            scope.launch {
                                try {
                                    val result=withContext(Dispatchers.IO) {
                                        host.issueMeasuredWorkout(workoutId)
                                    }
                                    if(result is NativeMeasuredWorkoutStoreResult.Durable)
                                        stored=result.stored
                                    else message="No existing approved session "+
                                        "was available to resume."
                                } catch(_:Exception) {
                                    message="Approved session could not be reopened."
                                } finally{busy=false}
                            }
                        }) {Text("Resume an already-approved session")}
                    Text("Loaded holds, carries and bands need their verified "+
                        "equipment-specific editor and are not yet selectable here.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Text("Current athlete and gym could not be verified. "+
                    "Review your saved native setup before creating a session.",
                    color=MaterialTheme.colorScheme.error)
            }
        } else {
            val current=checkNotNull(stored)
            val journal=current.journal
            if(journal.finishedByEventId!=null) {
                Text("This workout is already committed to History.")
                Button(onClick={onViewHistory(workoutId)}) {
                    Text("View completed workout")
                }
                OutlinedButton(onClick={
                    workoutId="measured-"+UUID.randomUUID().toString()
                        .replace("-","")
                    stored=null;review=null;message=null
                }) {Text("Create another measured workout")}
            } else {
                Text(journal.bySetKey.values.count{
                    it.effectiveRecord!=null
                }.toString()+" of "+journal.approvedPlan.sets.size+
                    " measured sets recorded",
                    style=MaterialTheme.typography.titleMedium)
                journal.approvedPlan.sets.forEach{set->
                    val child=journal.bySetKey.getValue(set.setKey)
                    NativeMeasuredExerciseSetEditor(
                        NativeMeasuredStoredSet(current.ownerKey,
                            current.athleteId,current.approvalId,
                            current.approvalRevision,child),
                        busy=busy,onRecord={change->
                            busy=true;message=null
                            scope.launch {
                                try {
                                    val outcome=withContext(Dispatchers.IO) {
                                        host.recordMeasuredWorkoutSet(workoutId,
                                            MeasuredWorkoutOperation.Record(
                                                change.eventId,journal.revision,
                                                set.setKey,change.outcome,
                                                change.actual,change.correctsEventId))
                                    }
                                    if(outcome is NativeMeasuredWorkoutStoreResult.Durable)
                                        stored=outcome.stored
                                    else {
                                        reload()
                                        message="Set was refused or changed. "+
                                            "The latest saved journal was reloaded."
                                    }
                                } catch(_:Exception) {
                                    reload()
                                    message="Set update unverified; no save claimed."
                                } finally{busy=false}
                            }
                        })
                }
                Button(enabled=!busy && journal.canProposeFinish,
                    modifier=Modifier.fillMaxWidth(),
                    onClick={finishDialog=true}) {Text("Finish workout")}
                Text("Finish is saved in the same verified native History "+
                    "ledger as generated and custom workouts.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let{Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall)}
    }
    review?.let{candidateReview->
        AlertDialog(onDismissRequest={if(!busy)review=null},
            title={Text("Approve exact measured session?")},
            text={Text("Confirm "+candidateReview.plan.sets.size+
                " real timed sets from your verified gym. The actual "+
                "results are entered separately. No PR or program changes.")},
            confirmButton={
                Button(enabled=!busy,onClick={
                    review=null;busy=true;message=null
                    scope.launch {
                        try {
                            val issued=withContext(Dispatchers.IO) {
                                val confirmation=host.confirmMeasuredWorkoutConsent(
                                    candidateReview.plan,candidateReview.sourceRevision,true)
                                if(confirmation is NativeMeasuredConsentResult.Approved)
                                    host.issueMeasuredWorkout(workoutId) else null
                            }
                            if(issued is NativeMeasuredWorkoutStoreResult.Durable)
                                stored=issued.stored
                            else message="Approval or issuance was refused. "+
                                "No measured workout is reported open."
                        } catch(_:Exception) {
                            message="Measured issuance could not be verified."
                        } finally{busy=false}
                    }
                }){Text("Confirm & start")}
            },dismissButton={
                TextButton(enabled=!busy,onClick={review=null}){Text("Cancel")}
            })
    }
    if(finishDialog && stored!=null)AlertDialog(
        onDismissRequest={if(!busy)finishDialog=false},
        title={Text("Finish measured workout?")},
        text={Text("Only your saved actual seconds and set outcomes "+
            "will enter verified History.")},
        confirmButton={
            Button(enabled=!busy,onClick={
                val previous=stored ?:return@Button
                finishDialog=false;busy=true;message=null
                scope.launch {
                    try {
                        val result=withContext(Dispatchers.IO) {
                            host.finishMeasuredWorkout(workoutId,
                                MeasuredWorkoutOperation.Finish(
                                    "finish-"+UUID.randomUUID().toString()
                                        .replace("-",""),previous.journal.revision))
                        }
                        if(result is NativeMeasuredFinishResult.Committed) {
                            val completedId=workoutId
                            workoutId="measured-"+UUID.randomUUID().toString()
                                .replace("-","")
                            stored=null
                            onViewHistory(completedId)
                        } else {
                            reload()
                            message="Finish was not verified. Your saved sets "+
                                "have not been reported completed."
                        }
                    } catch(_:Exception) {
                        reload()
                        message="Finish could not be verified. Retry after recovery."
                    } finally{busy=false}
                }
            }) {Text("Finish & save")}
        },dismissButton={
            TextButton(enabled=!busy,onClick={finishDialog=false}) {
                Text("Keep logging")
            }
        })
}
