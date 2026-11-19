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
import java.math.BigDecimal
import java.util.UUID

/**
 * Kotlin-only athlete-facing entry into already verified typed parent journals.
 * All 14 typed exercise identities can now be explicitly authored if the
 * actual athlete/gym source verifies their fixtures and physical stock.
 * They are not automatically inserted into adaptive cycles or masqueraded
 * as a zero-pound repetition prescription.
 */
@Composable
internal fun NativeKotlinMeasuredWorkoutScreen(
    host:NativeKotlinCandidateHost,
    resumeWorkoutId:String?,
    onClose:()->Unit,
    onViewHistory:(String)->Unit
) {
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var sourceRead by remember(host){mutableStateOf<NativeAuthoringSourceRead?>(null)}
    var stored by remember(host){mutableStateOf<NativeMeasuredWorkoutStored?>(null)}
    var message by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedId by rememberSaveable{mutableStateOf("plank")}
    var selectedBandId by rememberSaveable{mutableStateOf("")}
    var selectedSide by rememberSaveable{mutableStateOf("LEFT")}
    var targetExternalLoad by rememberSaveable{mutableStateOf("")}
    var matchedPairConfirmed by rememberSaveable{mutableStateOf(false)}
    var target by rememberSaveable{mutableStateOf("30")}
    var setCount by rememberSaveable{mutableIntStateOf(2)}
    var review by remember{mutableStateOf<NativeMeasuredConsentResult.Preview?>(null)}
    var finishDialog by remember{mutableStateOf(false)}
    var menuOpen by remember{mutableStateOf(false)}
    var workoutId by rememberSaveable(resumeWorkoutId) {
        mutableStateOf(resumeWorkoutId ?:
            "measured-"+UUID.randomUUID().toString().replace("-",""))
    }
    // Draft values are saveable UI intent only. Approved sessions and
    // recorded facts live exclusively in owner-verified native files.
    var queuedDrafts by rememberSaveable(workoutId) {
        mutableStateOf(emptyList<String>())
    }
    var draftSourceKey by rememberSaveable(workoutId) {
        mutableStateOf("")
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

    val options=if(setup!=null)MeasuredSessionAuthoringDomain.available(setup)
        else emptyList()
    val definition=options.firstOrNull{it.exerciseId==selectedId}
    val banded=definition?.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
    val loaded=definition?.loadKind in setOf(
        MeasuredLoadKind.VERIFIED_ADDED_LOAD,MeasuredLoadKind.VERIFIED_PER_HAND)
    val verifiedBands=gym?.availableAttachments.orEmpty()
        .filter{it.startsWith("band-") && it.length>5}.sorted()
    val verifiedBand=selectedBandId.takeIf{banded && it in verifiedBands}
    val targetWeight=if(loaded)runCatching{
        BigDecimal(targetExternalLoad.trim())
    }.getOrNull() else null
    val verifiedStock=definition?.exerciseId?.let{
        MeasuredPhysicalStockSource.equipmentIdFor(it)
    }?.let{gym?.confirmedStock?.get(it)}
    val exactLoad=if(loaded && targetWeight!=null && gym!=null) {
        val checked=EquipmentLoadDomain.inspect(
            verifiedStock,targetWeight,gym.unit)
        checked is LoadInspection.Confirmed && checked.exact
    } else !loaded
    // A carry is entered in meters but stored as exact centimeters.
    // Reject fractional centimeters rather than silently rounding them.
    val quantity=if(definition?.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
        runCatching{BigDecimal(target.trim()).multiply(BigDecimal("100"))
            .intValueExact()}.getOrNull()
        else target.toIntOrNull()
    val currentSelection=if(definition!=null && quantity!=null)
        MeasuredSessionSelection(definition.exerciseId,quantity,setCount,
            singleSide=if(definition.sideRule==
                MeasuredSideRule.EXPLICIT_SINGLE_SIDE)
                if(selectedSide=="RIGHT")MeasuredSide.RIGHT else MeasuredSide.LEFT
                else null,
            targetExternalLoad=targetWeight,
            bandId=verifiedBand,
            matchedPairConfirmed=definition.exerciseId=="farmers" &&
                matchedPairConfirmed)
        else null
    val sourceKey=source?.let{
        it.ownerKey+"|"+it.athleteId+"|"+it.revision+"|"+
            it.setup.reviewed?.gym?.gymId
    }
    val draftMatches=queuedDrafts.isEmpty() ||
        (sourceKey!=null && sourceKey==draftSourceKey)
    val parsedDrafts=queuedDrafts.map{
        MeasuredSessionAuthoringDomain.restoreDraft(it)
    }
    val draftsValid=queuedDrafts.size==parsedDrafts.count{it!=null}
    val selections=parsedDrafts.filterNotNull()
    val queuedCandidate=if(queuedDrafts.isNotEmpty() && draftMatches &&
        draftsValid && source!=null && setup!=null)
        MeasuredSessionAuthoringDomain.compose(
            workoutId,source.revision,setup,selections) else null
    val singleCandidate=if(currentSelection!=null && source!=null &&
        setup!=null)
        MeasuredSessionAuthoringDomain.compose(
            workoutId,source.revision,setup,listOf(currentSelection))
        else null
    val appendable=if(singleCandidate!=null && draftMatches &&
        draftsValid && source!=null && setup!=null)
        MeasuredSessionAuthoringDomain.compose(workoutId,source.revision,
            setup,selections+checkNotNull(currentSelection))!=null
        else false
    val candidate=if(queuedDrafts.isEmpty())singleCandidate
        else queuedCandidate
    val station=singleCandidate?.sets?.firstOrNull()?.physicalSetupId
        ?:definition?.requiredFixtures?.firstOrNull() ?: "floor"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(PursuitSpace.md),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Measured workout",style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy,onClick=onClose){Text("Minimize")}
        }
        if(stored==null) {
            Text("Record real hold seconds, carry distance, or banded "+
                "repetitions. Actual equipment and loads are verified, "+
                "never guessed or converted into automatic PRs.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            when(sourceRead) {
                null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is NativeAuthoringSourceRead.Found -> {
                    if(options.isEmpty())Text("No measured movement is currently "+
                        "supported by your verified gym and training setup.",
                        color=MaterialTheme.colorScheme.error)
                    else {
                        Box {
                            OutlinedButton(enabled=!busy,onClick={menuOpen=true}) {
                                Text(definition?.displayName ?: "Choose a measured exercise")
                            }
                            DropdownMenu(expanded=menuOpen,
                                onDismissRequest={menuOpen=false}) {
                                options.forEach { choice ->
                                    DropdownMenuItem(text={Text(choice.displayName)},
                                        onClick={
                                            selectedId=choice.exerciseId
                                            target=when(choice.kind) {
                                                MeasuredExerciseKind.BANDED_REPETITIONS -> "10"
                                                MeasuredExerciseKind.CARRY_CENTIMETERS -> "20"
                                                else -> "30"
                                            }
                                            targetExternalLoad=""
                                            matchedPairConfirmed=false
                                            review=null;message=null;menuOpen=false
                                        })
                                }
                            }
                        }
                        OutlinedTextField(value=target,onValueChange={
                            target=it;review=null},
                            label={Text(when(definition?.kind) {
                                MeasuredExerciseKind.BANDED_REPETITIONS ->
                                    "Target repetitions per set"
                                MeasuredExerciseKind.CARRY_CENTIMETERS ->
                                    "Target carry distance (meters)"
                                else -> "Target seconds per set"
                            })},
                            enabled=!busy,modifier=Modifier.fillMaxWidth(),
                            singleLine=true,keyboardOptions=KeyboardOptions(
                                keyboardType=if(definition?.kind==
                                    MeasuredExerciseKind.CARRY_CENTIMETERS)
                                    KeyboardType.Decimal else KeyboardType.Number))
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
                        if(definition?.sideRule==MeasuredSideRule.EXPLICIT_SINGLE_SIDE) {
                            Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                listOf("LEFT","RIGHT").forEach{side->
                                    FilterChip(selected=selectedSide==side,
                                        enabled=!busy,onClick={
                                            selectedSide=side;review=null
                                        },label={Text(side.lowercase()
                                            .replaceFirstChar{it.uppercase()})})
                                }
                            }
                        }
                        if(loaded) {
                            Text("Select the actual externally added weight, "+
                                "not bodyweight or a calculated system load. "+
                                "Only physically verified selectable weights may be used.",
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedTextField(value=targetExternalLoad,onValueChange={
                                targetExternalLoad=it;review=null
                            },label={Text("Target load ("+gym?.unit+")"+
                                if(definition?.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND)
                                    " per hand" else " added mass")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Decimal),
                                singleLine=true,enabled=!busy,
                                modifier=Modifier.fillMaxWidth())
                            if(targetExternalLoad.isNotBlank() && !exactLoad)
                                Text("This load is not an exact value in the "+
                                    "confirmed gym equipment stock.",
                                    color=MaterialTheme.colorScheme.error,
                                    style=MaterialTheme.typography.bodySmall)
                            if(definition?.exerciseId=="farmers")
                                Row {
                                    Checkbox(checked=matchedPairConfirmed,
                                        enabled=!busy,onCheckedChange={
                                            matchedPairConfirmed=it;review=null
                                        })
                                    Text("I physically confirmed two matching "+
                                        "dumbbells at this weight",
                                        style=MaterialTheme.typography.bodySmall)
                                }
                        }
                        if(banded) {
                            Text("Select the exact resistance band saved in your gym. "+
                                "Band resistance is not converted to pounds.",
                                style=MaterialTheme.typography.bodySmall)
                            verifiedBands.forEach{bandId->
                                FilterChip(selected=selectedBandId==bandId,
                                    enabled=!busy,
                                    onClick={
                                        selectedBandId=bandId
                                        review=null;message=null
                                    },label={Text(bandId.removePrefix("band-")
                                        .replace('-',' '))})
                            }
                        } else if("bands" in gym?.availableFacilities.orEmpty() &&
                            "verified-band-pushup-setup" in
                                (gym?.availableFacilities.orEmpty()+
                                 gym?.availableAttachments.orEmpty()) &&
                            verifiedBands.isEmpty()) {
                            Text("To log banded push-ups, save a distinct "+
                                "band-* ID in the gym's available attachments.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(queuedDrafts.isNotEmpty()) {
                            Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
                                shape=MaterialTheme.shapes.large,
                                modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(PursuitSpace.md),
                                    verticalArrangement=Arrangement.spacedBy(
                                        PursuitSpace.xs)) {
                                    Text("Your measured session",
                                        style=MaterialTheme.typography.titleMedium)
                                    if(!draftMatches || !draftsValid ||
                                        queuedCandidate==null) {
                                        Text("This unsaved draft no longer matches "+
                                            "the verified athlete, gym, equipment or "+
                                            "source revision. Clear it to start again.",
                                            color=MaterialTheme.colorScheme.error,
                                            style=MaterialTheme.typography.bodySmall)
                                    } else {
                                        selections.forEachIndexed{index,item->
                                            val name=MeasuredExerciseCatalogDomain
                                                .find(item.exerciseId)?.displayName
                                                ?:item.exerciseId
                                            Text("${index+1}. $name · "+
                                                "${item.sets} sets",
                                                style=MaterialTheme.typography.bodyMedium)
                                        }
                                        Text("${queuedCandidate.sets.size} total "+
                                            "typed sets. The current editor is not "+
                                            "included until you tap Add exercise.",
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(
                                        PursuitSpace.xs)) {
                                        TextButton(enabled=!busy,onClick={
                                            queuedDrafts=queuedDrafts.dropLast(1)
                                            if(queuedDrafts.isEmpty())draftSourceKey=""
                                            review=null;message=null
                                        }) {Text("Remove last")}
                                        TextButton(enabled=!busy,onClick={
                                            queuedDrafts=emptyList()
                                            draftSourceKey=""
                                            review=null;message=null
                                        }) {Text("Clear draft")}
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            modifier=Modifier.fillMaxWidth(),
                            enabled=!busy && appendable,
                            onClick={
                                val current=currentSelection
                                    ?:return@OutlinedButton
                                val currentKey=sourceKey
                                    ?:return@OutlinedButton
                                if(queuedDrafts.isEmpty())draftSourceKey=currentKey
                                queuedDrafts=queuedDrafts+
                                    MeasuredSessionAuthoringDomain.saveDraft(current)
                                review=null;message=null
                            }) {Text("Add exercise to session")}
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
                            }) {Text(if(queuedDrafts.isEmpty())
                                "Review current exercise"
                                else "Review ${candidate?.sets?.size ?: 0} queued sets")}
                    }
                    Text("Already confirmed a session? Return to Train to "+
                        "resume its owner-verified saved approval or "+
                        "unfinished workout. This editor never guesses a "+
                        "saved workout ID.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Loaded holds and carries require verified "+
                        "dumbbell-rack or single-weight-plate stock and "+
                        "their real fixtures in your saved gym. They never "+
                        "reuse an unverified machine or calculated bodyweight.",
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
                " exact measured sets from your verified gym. The actual "+
                "results are entered separately. No automatic PR, weight "+
                "equivalent or program changes.")},
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
