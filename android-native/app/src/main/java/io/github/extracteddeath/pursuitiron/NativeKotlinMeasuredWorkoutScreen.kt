package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** A missing issued journal is a recovery state, never a new-session editor. */
internal enum class NativeMeasuredWorkoutEntryState {
    VERIFYING, RECOVERY_REQUIRED, AUTHORING, RECORDING
}

internal object NativeMeasuredWorkoutEntryPolicy {
    fun resolve(
        expectsExisting:Boolean,
        lookupComplete:Boolean,
        hasVerifiedJournal:Boolean,
        verifiedNotIssued:Boolean
    ):NativeMeasuredWorkoutEntryState = when {
        !lookupComplete -> NativeMeasuredWorkoutEntryState.VERIFYING
        hasVerifiedJournal -> NativeMeasuredWorkoutEntryState.RECORDING
        expectsExisting -> NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED
        verifiedNotIssued -> NativeMeasuredWorkoutEntryState.AUTHORING
        else -> NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED
    }
}

/** Only an explicitly verified source opens measured authoring. A failed
 * fetch is never a perpetual spinner or implicit source approval. */
internal enum class NativeMeasuredSourceState {
    VERIFYING, READY, RECOVERY_REQUIRED
}

internal object NativeMeasuredSourcePolicy {
    fun resolve(lookupComplete:Boolean,hasVerifiedSource:Boolean):
        NativeMeasuredSourceState = when {
        !lookupComplete -> NativeMeasuredSourceState.VERIFYING
        hasVerifiedSource -> NativeMeasuredSourceState.READY
        else -> NativeMeasuredSourceState.RECOVERY_REQUIRED
    }
}

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
    val measuredEditorStates=rememberSaveableStateHolder()
    var busy by remember{mutableStateOf(false)}
    var sourceRead by remember(host){mutableStateOf<NativeAuthoringSourceRead?>(null)}
    var stored by remember(host){mutableStateOf<NativeMeasuredWorkoutStored?>(null)}
    var storedLookup by remember(host){mutableStateOf<NativeMeasuredWorkoutStoreResult?>(null)}
    var sourceLookupComplete by remember(host){mutableStateOf(false)}
    var message by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedId by rememberSaveable{mutableStateOf("plank")}
    var selectedBandId by rememberSaveable{mutableStateOf("")}
    var selectedSide by rememberSaveable{mutableStateOf("LEFT")}
    var targetExternalLoad by rememberSaveable{mutableStateOf("")}
    var confirmedPairKey by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable{mutableStateOf("30")}
    var setCount by rememberSaveable{mutableIntStateOf(2)}
    var review by remember{mutableStateOf<NativeMeasuredConsentResult.Preview?>(null)}
    var menuOpen by remember{mutableStateOf(false)}
    var workoutId by rememberSaveable(resumeWorkoutId) {
        mutableStateOf(resumeWorkoutId ?:
            "measured-"+UUID.randomUUID().toString().replace("-",""))
    }
    // A review is deliberately not saved through process recreation.
    var finishReview by remember(workoutId) {
        mutableStateOf<NativeFinishReviewToken?>(null)
    }
    // A verified workout cannot silently revert to new-session authoring
    // when its disk journal is missing, unreadable or owner-unverifiable.
    var expectsExisting by rememberSaveable(workoutId) {
        mutableStateOf(resumeWorkoutId==workoutId)
    }
    var lookupComplete by remember(workoutId) {mutableStateOf(false)}
    // Draft values are saveable UI intent only. Approved sessions and
    // recorded facts live exclusively in owner-verified native files.
    var queuedDrafts by rememberSaveable(workoutId) {
        mutableStateOf(emptyList<String>())
    }
    var draftSourceKey by rememberSaveable(workoutId) {
        mutableStateOf("")
    }
    // View navigation is saveable, but never used as evidence of a saved set.
    // An invalid/restored key is resolved against the REAL issued plan.
    var focusSetKey by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    var focusPickerOpen by remember {mutableStateOf(false)}
    // Browsing an entire measured session is presentation-only. Each set
    // remains keyed to its owner-verified journal and independent unsaved
    // editor; returning to Focus never invents a saved observation.
    var showAllMeasuredSets by rememberSaveable(workoutId) {
        mutableStateOf(false)
    }
    suspend fun reloadSource() {
        sourceLookupComplete=false
        sourceRead=null
        sourceRead=try {
            withContext(Dispatchers.IO){host.reviewMeasuredAuthoringSource()}
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(_:Exception) {
            null
        }
        sourceLookupComplete=true
    }
    suspend fun reload() {
        lookupComplete=false
        storedLookup=null
        // New authoring requires an EXPLICIT NOT_ISSUED receipt. A corrupt,
        // unauthorized or unreadable store must not become a new session,
        // even if the UI created the workout ID a moment ago.
        val read=try {
            withContext(Dispatchers.IO){host.openMeasuredWorkout(workoutId)}
        } catch(cancelled:CancellationException) {
            throw cancelled
        } catch(_:Exception) {
            null
        }
        storedLookup=read
        val verified=(read as? NativeMeasuredWorkoutStoreResult.Durable)?.stored
        stored=verified
        if(verified!=null)expectsExisting=true
        lookupComplete=true
    }
    LaunchedEffect(host,workoutId) {
        reloadSource()
        reload()
    }
    BackHandler { if (!busy) onClose() }
    val entryState=NativeMeasuredWorkoutEntryPolicy.resolve(
        expectsExisting,lookupComplete,stored!=null,
        (storedLookup as? NativeMeasuredWorkoutStoreResult.Rejected)
            ?.reason==NativeMeasuredWorkoutStoreRefusal.NOT_ISSUED)
    // Navigation, the set list and the Finish dialog share the same immutable
    // journal. Replay it once per selection or receipt change during rendering.
    val displayedJournal = stored?.journal
    val reviewedFocus = remember(entryState, displayedJournal, focusSetKey) {
        if (entryState == NativeMeasuredWorkoutEntryState.RECORDING &&
            displayedJournal != null && displayedJournal.finishedByEventId == null)
            MeasuredWorkoutFocusDomain.review(displayedJournal, focusSetKey)
        else null
    }
    val source=(sourceRead as? NativeAuthoringSourceRead.Found)?.source
    val sourceState=NativeMeasuredSourcePolicy.resolve(
        sourceLookupComplete,source!=null && source.setup.reviewed!=null)
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
        // Physical selector existence is not an issued-load approval:
        // a verified 0 setting must not appear valid for added/per-hand work.
        checked is LoadInspection.Confirmed && checked.exact &&
            definition?.expectedBasis?.let { basis ->
                KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                    targetWeight,basis)
            }==true
    } else !loaded
    // A carry is entered in meters but stored as exact centimeters.
    // Reject fractional centimeters rather than silently rounding them.
    val quantity=if(definition?.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
        runCatching{BigDecimal(target.trim()).multiply(BigDecimal("100"))
            .intValueExact()}.getOrNull()
        else target.toIntOrNull()
    val sourceKey=source?.let{
        it.ownerKey+"|"+it.athleteId+"|"+it.revision+"|"+
            it.setup.reviewed?.gym?.gymId
    }
    val pairKey = if (definition?.exerciseId == "farmers" && sourceKey != null &&
        targetWeight != null && exactLoad) {
        "$workoutId|$sourceKey|"+targetWeight.stripTrailingZeros().toPlainString()
    } else null
    val matchedPairConfirmed = pairKey != null && confirmedPairKey == pairKey
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

    // Split content from the fixed action area. A measured athlete with
    // dozens of saved holds/carries should not hunt for Finish in the
    // scrollable set history. This is presentation only, not a new writer.
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
        .padding(PursuitSpace.md),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Measured workout",style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=!busy,onClick=onClose){Text("Minimize")}
        }
        if(entryState==NativeMeasuredWorkoutEntryState.VERIFYING) {
            Text("Checking your saved workout before opening any editor.",
                style=MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if(entryState==NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED) {
            Text("The measured workout store could not be verified. "+
                "Only an explicit empty-store result allows starting a new "+
                "session; saved or unreadable sessions are never replaced.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodyMedium)
            Button(enabled=!busy,modifier=Modifier.fillMaxWidth(),
                onClick={
                    busy=true
                    scope.launch {
                        try {
                            reloadSource()
                            reload()
                        } finally {busy=false}
                    }
                }){Text("Retry saved workout storage")}
            OutlinedButton(enabled=!busy,modifier=Modifier.fillMaxWidth(),
                onClick=onClose){Text("Return to Train")}
        } else if(entryState==NativeMeasuredWorkoutEntryState.AUTHORING) {
            Text("Record real hold seconds, carry distance, or banded "+
                "repetitions. Actual equipment and loads are verified, "+
                "never guessed or converted into automatic PRs.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            when(sourceState) {
                NativeMeasuredSourceState.VERIFYING ->
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                NativeMeasuredSourceState.READY -> {
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
                                            confirmedPairKey=null
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
                        val setRange = MeasuredSessionAuthoringDomain.setsPerExercise
                        PreviewStepper(
                            title="Sets", value=setCount.toString(),
                            subtract={ setCount--; review=null },
                            add={ setCount++; review=null },
                            canSubtract=!busy && setCount > setRange.first,
                            canAdd=!busy && setCount < setRange.last
                        )
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
                                targetExternalLoad=it;confirmedPairKey=null;review=null
                            },label={Text("Target load ("+gym?.unit+")"+
                                if(definition?.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND)
                                    " per hand" else " added mass")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Decimal),
                                singleLine=true,enabled=!busy,
                                modifier=Modifier.fillMaxWidth())
                            if(targetExternalLoad.isNotBlank() && !exactLoad)
                                Text("Enter a positive weight that is exactly selectable "+
                                    "on the confirmed gym equipment.",
                                    color=MaterialTheme.colorScheme.error,
                                    style=MaterialTheme.typography.bodySmall)
                            if(definition?.exerciseId=="farmers")
                                Row(
                                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                                        .toggleable(value=matchedPairConfirmed,
                                            enabled=!busy && pairKey!=null,
                                            role=Role.Checkbox,
                                            onValueChange={ checked ->
                                                confirmedPairKey=if(checked) pairKey else null
                                                review=null
                                            }),
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
                                ) {
                                    Checkbox(checked=matchedPairConfirmed,
                                        enabled=!busy && pairKey!=null,onCheckedChange=null)
                                    Text("I confirmed two matching dumbbells at this weight",
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
                                    } catch(cancelled:CancellationException) {
                                        throw cancelled
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
                NativeMeasuredSourceState.RECOVERY_REQUIRED -> {
                    Text("The current athlete, gym or authoring source could "+
                        "not be verified. No new measured workout was approved "+
                        "and no saved session was changed.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodyMedium)
                    Button(enabled=!busy,onClick={
                        busy=true
                        scope.launch {
                            try {reloadSource()} finally {busy=false}
                        }
                    },modifier=Modifier.fillMaxWidth()) {
                        Text("Retry athlete and gym verification")
                    }
                    OutlinedButton(enabled=!busy,onClick=onClose,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Return to Train")
                    }
                }
            }
        } else {
            val current=checkNotNull(stored)
            val journal=current.journal
            if(journal.finishedByEventId!=null) {
                Text("A Finish receipt is saved. The shared History ledger "+
                    "must verify this exact workout before it is called completed.",
                    style=MaterialTheme.typography.bodyMedium)
                Button(enabled=!busy,onClick={
                    val requestedId=workoutId
                    val requestedFinish=journal.finishedByEventId
                    busy=true;message=null
                    scope.launch {
                        try {
                            val verified=withContext(Dispatchers.IO) {
                                host.verifyFinishedMeasuredWorkoutInHistory(
                                    requestedId)
                            }
                            // Late IO or an owner/workout switch can never
                            // hand a different recorded workout to History.
                            if(workoutId==requestedId &&
                                stored?.journal?.finishedByEventId==requestedFinish) {
                                if(verified)onViewHistory(requestedId)
                                else message="The saved Finish could not yet be "+
                                    "verified in History. Keep the session saved "+
                                    "and retry; no new completion was claimed."
                            }
                        } catch(cancelled:CancellationException) {
                            throw cancelled
                        } catch(_:Exception) {
                            if(workoutId==requestedId)
                                message="History verification is unavailable. "+
                                    "The saved Finish has not been confirmed."
                        } finally {busy=false}
                    }
                }) {Text("Verify Finish and view History")}
                OutlinedButton(enabled=!busy,onClick=onClose) {
                    Text("Return to Train")
                }
            } else {
                val focus=reviewedFocus
                if(focus==null) {
                    Text("Verified measured set navigation is unavailable. "+
                        "Leave and reopen the saved workout.",
                        color=MaterialTheme.colorScheme.error)
                } else {
                    val set=journal.approvedPlan.sets[focus.selectedOrdinal-1]
                    val child=journal.bySetKey.getValue(set.setKey)
                    val selectedReview=remember(set, child) {
                        MeasuredWorkoutResultReviewDomain.review(set, child)
                    }
                    val definition=MeasuredExerciseCatalogDomain.find(set.exerciseId)
                    Text("${focus.recordedSets} of ${focus.totalSets} sets saved",
                        style=MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress={focus.recordedSets.toFloat()/focus.totalSets},
                        modifier=Modifier.fillMaxWidth())
                    PursuitWorkoutFocusHeader(
                        exerciseLabel=definition?.displayName ?:set.exerciseId,
                        setDetail="Set ${focus.selectedOrdinal} of ${focus.totalSets} · "+
                            selectedReview.statusLabel.lowercase(),
                        focusOnNext=!showAllMeasuredSets,
                        onFocusChange={focus->showAllMeasuredSets=!focus},
                        heading="MEASURED WORKOUT",
                        enabled=!busy
                    )
                    if(selectedReview.resolved)Text(
                        "This set has a saved "+selectedReview.statusLabel.lowercase()+
                            " outcome. Review before making a correction.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(showAllMeasuredSets) {
                        PursuitCard {
                            PursuitSectionHeading("All measured sets",
                                "Review the actual saved status of every set. "+
                                    "Choosing a row never records a result.")
                            journal.approvedPlan.sets.forEachIndexed {index,planned->
                                val rowJournal=journal.bySetKey[planned.setKey]
                                val rowReview=remember(planned, rowJournal) {
                                    MeasuredWorkoutResultReviewDomain.review(planned, rowJournal)
                                }
                                val saved=rowReview.resolved
                                val name=MeasuredExerciseCatalogDomain.find(
                                    planned.exerciseId)?.displayName
                                    ?:planned.exerciseId
                                val selected=set.setKey==planned.setKey
                                if(index>0)HorizontalDivider()
                                Text("${index+1}. $name",
                                    style=MaterialTheme.typography.titleSmall)
                                Text(if(saved)"Result saved in native journal"
                                    else if(rowReview.status==
                                        MeasuredSetReviewStatus.UNVERIFIED)
                                        "Saved result cannot be verified"
                                    else "Result not recorded",
                                    color=if(saved)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.labelMedium)
                                if(saved) {
                                    Text(rowReview.statusLabel,
                                        style=MaterialTheme.typography.labelMedium)
                                    rowReview.actualSummary?.let { actual ->
                                        Text(actual,
                                            style=MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                OutlinedButton(enabled=!busy,
                                    modifier=Modifier.fillMaxWidth()
                                        .heightIn(min=48.dp),
                                    onClick={
                                        focusSetKey=planned.setKey
                                        showAllMeasuredSets=false
                                    }) {
                                    Text(when {
                                        selected -> "Focus selected set"
                                        saved -> "Review saved set"
                                        else -> "Record this set"
                                    })
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement=Arrangement.spacedBy(
                            PursuitSpace.xs),modifier=Modifier.fillMaxWidth()) {
                            OutlinedButton(enabled=!busy && focus.selectedOrdinal>1,
                                modifier=Modifier.weight(1f).heightIn(min=48.dp),
                                onClick={
                                    focusSetKey=journal.approvedPlan.sets[
                                        focus.selectedOrdinal-2].setKey
                                }) {Text("Previous")}
                            OutlinedButton(enabled=!busy &&
                                focus.selectedOrdinal<focus.totalSets,
                                modifier=Modifier.weight(1f).heightIn(min=48.dp),
                                onClick={
                                    focusSetKey=journal.approvedPlan.sets[
                                        focus.selectedOrdinal].setKey
                                }) {Text("Next")}
                        }
                        Box {
                            OutlinedButton(enabled=!busy,
                                onClick={focusPickerOpen=true},
                                modifier=Modifier.fillMaxWidth()) {
                                Text("Jump to a set")
                            }
                            DropdownMenu(expanded=focusPickerOpen,
                                onDismissRequest={focusPickerOpen=false}) {
                                journal.approvedPlan.sets.forEachIndexed{
                                    index,planned->
                                    val saved=journal.bySetKey[planned.setKey]
                                        ?.effectiveRecord!=null
                                    DropdownMenuItem(
                                        enabled=!busy,
                                        text={Text("${index+1}. "+
                                            (MeasuredExerciseCatalogDomain.find(
                                                planned.exerciseId)?.displayName
                                                ?:planned.exerciseId)+
                                            if(saved)" · saved" else " · pending")},
                                        onClick={
                                            focusSetKey=planned.setKey
                                            focusPickerOpen=false
                                        })
                                }
                            }
                        }
                    }
                    if(focus.nextUnrecordedKey!=null) {
                        val next=journal.approvedPlan.sets.firstOrNull{
                            it.setKey==focus.nextUnrecordedKey}
                        if(next!=null)
                            Text("Up next: "+
                                (MeasuredExerciseCatalogDomain.find(
                                    next.exerciseId)?.displayName
                                    ?:next.exerciseId)+
                                " · set "+
                                (journal.approvedPlan.sets.indexOf(next)+1),
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Keep each set's UNSAVED numeric input and optional
                    // running stopwatch independent across Previous/Next.
                    // The durable journal, not this saveable UI holder,
                    // remains the sole owner of recorded results.
                    if(!showAllMeasuredSets)measuredEditorStates.SaveableStateProvider(
                        "measured:$workoutId:${set.setKey}") {
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
                                    if(outcome is NativeMeasuredWorkoutStoreResult.Durable) {
                                        val next=MeasuredWorkoutFocusDomain
                                            .afterDurableRecord(journal,
                                                outcome.stored.journal,set.setKey)
                                        stored=outcome.stored
                                        if(next!=null)focusSetKey=next
                                    } else {
                                        reload()
                                        message="Set was refused or changed. "+
                                            "The latest saved journal was reloaded."
                                    }
                                } catch(cancelled:CancellationException) {
                                    throw cancelled
                                } catch(_:Exception) {
                                    reload()
                                    message="Set update unverified; no save claimed."
                                } finally{busy=false}
                            }
                        })
                    }
                }
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
    // The owner-verifiable issued journal, not a local counter, is the
    // sole source of Finish eligibility. The visible selected set must also
    // belong to that exact issued plan; recovery/authoring states have no
    // active completion action. Confirmation still invokes the existing
    // measured History transaction with an exact journal revision.
    if(entryState==NativeMeasuredWorkoutEntryState.RECORDING &&
        stored?.journal?.let{measured->
            measured.canProposeFinish &&
                measured.finishedByEventId==null &&
                reviewedFocus!=null
        }==true)
        Column(Modifier.fillMaxWidth().widthIn(max=920.dp)
            .align(Alignment.CenterHorizontally)
            .padding(horizontal=PursuitSpace.md,vertical=PursuitSpace.xs)) {
            PursuitCard(highlighted=true) {
                Text("All measured sets have saved outcomes",
                    style=MaterialTheme.typography.titleMedium)
                PursuitPrimaryAction("Review and finish",
                    enabled=!busy) {
                    finishReview=stored?.let {
                        NativeFinishReviewToken(workoutId,it.journal.revision)
                    }
                }
            }
        }
    }
    review?.let{candidateReview->
        AlertDialog(onDismissRequest={if(!busy)review=null},
            title={Text("Approve exact measured session?")},
            text={
                Column(Modifier.heightIn(max=380.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    Text("Review every set from this exact source-approved "+
                        "plan before starting. Values below are targets, "+
                        "never automatically recorded results.",
                        style=MaterialTheme.typography.bodySmall)
                    candidateReview.plan.sets.forEachIndexed {index,set->
                        val definition=MeasuredExerciseCatalogDomain.find(
                            set.exerciseId)
                        val amount=when(definition?.kind) {
                            MeasuredExerciseKind.CARRY_CENTIMETERS ->
                                BigDecimal(set.targetAmount).movePointLeft(2)
                                    .stripTrailingZeros().toPlainString()+" meters"
                            MeasuredExerciseKind.BANDED_REPETITIONS ->
                                "${set.targetAmount} reps"
                            else -> "${set.targetAmount} seconds"
                        }
                        Text("${index+1}. "+
                            (definition?.displayName ?:set.exerciseId)+
                            " · $amount",
                            style=MaterialTheme.typography.bodyMedium)
                        val approvedExternalLoad=set.intendedExternalLoad
                        val equipment=buildList {
                            if(approvedExternalLoad!=null)
                                add(approvedExternalLoad.toPlainString()+" "+
                                    set.unit+" "+
                                    if(definition?.loadKind==
                                        MeasuredLoadKind.VERIFIED_PER_HAND)
                                        "per hand" else "added")
                            if(set.bandId!=null)add("Band: "+set.bandId)
                            if(set.sides!=setOf(MeasuredSide.BOTH))
                                add("Sides: "+set.sides.joinToString{
                                    it.name.lowercase()})
                            if(set.matchedPairConfirmed)
                                add("Matching pair confirmed")
                            add("Station: "+set.physicalSetupId)
                        }
                        Text(equipment.joinToString(" · "),
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("No automatic PR, invented band weight, "+
                        "program changes or logged performances.",
                        style=MaterialTheme.typography.bodySmall)
                }
            },
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
                            if(issued is NativeMeasuredWorkoutStoreResult.Durable) {
                                stored=issued.stored
                                expectsExisting=true
                                lookupComplete=true
                            } else message="Approval or issuance was refused. "+
                                "No measured workout is reported open."
                        } catch(cancelled:CancellationException) {
                            throw cancelled
                        } catch(_:Exception) {
                            message="Measured issuance could not be verified."
                        } finally{busy=false}
                    }
                }){Text("Confirm & start")}
            },dismissButton={
                TextButton(enabled=!busy,onClick={review=null}){Text("Cancel")}
            })
    }
    finishReview?.let {reviewed->
        val canCommit=NativeFinishReviewPolicy.canCommit(
            reviewed,workoutId,stored?.journal?.revision,
            entryState==NativeMeasuredWorkoutEntryState.RECORDING &&
                stored?.journal?.canProposeFinish==true &&
                stored?.journal?.finishedByEventId==null &&
                reviewedFocus!=null)
        AlertDialog(
        onDismissRequest={if(!busy)finishReview=null},
        title={Text("Finish measured workout?")},
        text={Text(if(canCommit)
            "Only your saved actual durations, distances, repetitions, physical loads, sides and set outcomes enter verified History. No target is logged as performed work."
            else "Your saved measured workout changed or needs recovery. Close this review and check the current sets before finishing.")},
        confirmButton={
            Button(enabled=!busy && canCommit,onClick={
                val previous=stored ?:return@Button
                if(!NativeFinishReviewPolicy.canCommit(
                    reviewed,workoutId,previous.journal.revision,
                    entryState==NativeMeasuredWorkoutEntryState.RECORDING &&
                        previous.journal.canProposeFinish &&
                        previous.journal.finishedByEventId==null &&
                        MeasuredWorkoutFocusDomain.review(
                            previous.journal,focusSetKey)!=null))
                    return@Button
                finishReview=null;busy=true;message=null
                scope.launch {
                    try {
                        val result=withContext(Dispatchers.IO) {
                            host.finishMeasuredWorkout(workoutId,
                                MeasuredWorkoutOperation.Finish(
                                    "finish-"+UUID.randomUUID().toString()
                                        .replace("-",""),reviewed.revision))
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
                    } catch(cancelled:CancellationException) {
                        throw cancelled
                    } catch(_:Exception) {
                        reload()
                        message="Finish could not be verified. Retry after recovery."
                    } finally{busy=false}
                }
            }) {Text("Finish & save")}
        },dismissButton={
            TextButton(enabled=!busy,onClick={finishReview=null}) {
                Text("Keep logging")
            }
        })
    }
}
