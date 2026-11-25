package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomSignoffRead
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomPrepareRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomNextCycleSlot
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeBlockLoadConsentKind
import java.math.BigDecimal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeFutureWeekProposalResult
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeFutureWeekProposalRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeFutureWeekDraftPreview
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeFutureWeekDraftDecision
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeFutureWeekDraftRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun <T> CycleChoiceRow(
    label: String, values: List<T>, selected: T,
    caption: (T) -> String, onSelect: (T) -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { option ->
            FilterChip(selected = option == selected,
                onClick = { onSelect(option) }, label = { Text(caption(option)) })
        }
    }
}

/**
 * Keep an optional advanced technique visibly attached to its issued set
 * without repeating an open, full-width protocol card for every set. The
 * small summary is factual prescription, not a logged follow-up or consent.
 * Expanded advice is read-only and keyed to the issued set identity.
 */
@Composable
private fun NativeKotlinSetTechniqueSummary(
    amp: CustomSetAmplifier,
    ordinal: Int,
    setKey: String
) {
    var showDetails by rememberSaveable(setKey) { mutableStateOf(false) }
    val technique=AdvancedAmplifierProtocolDomain.template(amp.kind)
    val count=if(amp.followUpRounds>1)
        "${amp.followUpRounds} ${AdvancedAmplifierProtocolDomain.stageNoun(amp.kind,plural=true)}"
    else "1 follow-up"
    val label=if(amp.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
        "Last working set · Lengthened partials · ${amp.followUpReps} partial reps · no rest"
    else "${technique.title} · $count · ${amp.followUpReps} reps · ${amp.followUpRestSeconds}s rest"
    Column(Modifier.fillMaxWidth(),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Row(Modifier.fillMaxWidth(),
            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            Text(label,modifier=Modifier.weight(1f),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={showDetails=!showDetails},
                modifier=Modifier.heightIn(min=48.dp).semantics {
                    contentDescription=(if(showDetails)"Hide" else "Show")+
                        " ${technique.title} details for set $ordinal"
                }) {
                Text(if(showDetails)"Less" else "Details")
            }
        }
        if(showDetails) Surface(
            color=MaterialTheme.colorScheme.surfaceContainerLow,
            shape=MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(PursuitSpace.sm),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                Text("Set $ordinal · ${technique.title}",
                    style=MaterialTheme.typography.labelLarge)
                if(amp.kind==CustomAmplifierKind.DROP_SET &&
                    amp.followUpRounds==2) {
                    for(stage in 1..2) {
                        val load=AdvancedAmplifierProtocolDomain.dropStageLoad(amp,stage)
                            as? DraftSetLoad.Verified
                        Text("Drop $stage · "+(load?.let{
                            "${it.value.toPlainString()} ${it.unit} · "+
                                kotlinLoadBasisLabel(it.basis)
                        }?:"unverified load · cannot issue"),
                            style=MaterialTheme.typography.bodySmall)
                    }
                } else Text(when(val extra=amp.followUpLoad) {
                    null -> "Same confirmed load"
                    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
                    is DraftSetLoad.Verified ->
                        "${extra.value.toPlainString()} ${extra.unit} · "+
                            kotlinLoadBasisLabel(extra.basis)
                },style=MaterialTheme.typography.bodySmall)
                Text(amp.followUpActiveSeconds?.let{
                    "${it}s added work planned"
                } ?: "Additional work time not authored",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(technique.techniqueCue,
                    style=MaterialTheme.typography.bodySmall)
                Text(AdvancedAmplifierProtocolDomain.stageReviewCue(amp),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun pretty(value: Enum<*>) =
    value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Plan's actual return-to-workout path. Only a cold-opened, still issued and
 * unfinished generated Kotlin journal may show here. A button tap performs a
 * SECOND owner/approval recheck, so an old UI result is never an entry grant.
 * No workout is created, issued, recorded or altered by viewing this card.
 */
@Composable
private fun NativeVerifiedGeneratedWorkoutResumeCard(
    host:NativeKotlinCandidateHost,
    onResume:(String)->Unit
) {
    val scope=rememberCoroutineScope()
    var verifiedId by remember(host){mutableStateOf<String?>(null)}
    var checking by remember(host){mutableStateOf(true)}
    var opening by remember(host){mutableStateOf(false)}
    var refusal by remember(host){mutableStateOf<String?>(null)}
    LaunchedEffect(host) {
        checking=true
        verifiedId=withContext(Dispatchers.IO) {
            runCatching{currentUnfinishedGeneratedWorkoutId(host)}.getOrNull()
        }
        checking=false
    }
    val id=verifiedId
    if(id!=null)PursuitCard(highlighted=true) {
        Text("Your active workout",
            style=MaterialTheme.typography.titleLarge)
        Text("Your unfinished Kotlin workout is saved. Continue logging "+
            "with the same approved session and recorded sets.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        PursuitPrimaryAction("Resume generated workout",
            enabled=!checking && !opening) {
            if(!opening) {
                opening=true
                refusal=null
                scope.launch {
                    try {
                        val reopened=withContext(Dispatchers.IO) {
                            runCatching {
                                currentUnfinishedGeneratedWorkoutId(host)
                            }.getOrNull()
                        }
                        if(id==verifiedId && reopened==id)
                            onResume(id)
                        else {
                            verifiedId=null
                            refusal="That workout is no longer the current "+
                                "approved unfinished session. Open Train "+
                                "or review your saved program."
                        }
                    } finally {
                        opening=false
                    }
                }
            }
        }
        refusal?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
    } else if(refusal!=null)Text(checkNotNull(refusal),
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.error)
}

/**
 * The native Plan workspace for athlete-authored and generated Kotlin cycles.
 * Uses NativeKotlinCustomCycleController and its verified durable drafts.
 *
 * Training decisions and exercise prescriptions are not created from this
 * UI. A saved draft cannot issue a session or change history.
 */
@Composable
internal fun NativeKotlinCustomCycleCandidateScreen(
    controller: NativeKotlinCustomCycleController,
    initialCycleId: String? = null,
    onOpenIssuedWorkout:(String)->Unit = {},
    onResumeGeneratedWorkout:(String)->Unit = {},
    onOpenHistory:()->Unit = {},
    onBuildProgram:()->Unit = {},
    permissionHost:NativeKotlinCandidateHost?=null,
    programSwapReview:NativeKotlinProgramSwapHandoff?=null,
    onProgramSwapReviewDismiss:()->Unit={},
    onManagePhysicalMachine:(String)->Unit={}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val programPicker by controller.programPicker.collectAsStateWithLifecycle()
    val draft = state.draft
    val busy = state.busy
    val calibrationLocked=state.calibrationReceipt!=null ||
        state.preparedWorkout?.let{it.calibrationApprovalFingerprint!=null||it.resetToAuthored}==true
    var openId by rememberSaveable { mutableStateOf(initialCycleId.orEmpty()) }
    var showAdvancedId by rememberSaveable {mutableStateOf(false)}
    var creatingBlank by rememberSaveable {mutableStateOf(false)}
    var showProgramEditor by rememberSaveable(draft?.athleteId,draft?.cycleId) {mutableStateOf(false)}
    val reviewedMachineGymId=state.reviewedSourceSetup?.reviewed?.gym?.gymId
    var planMachineReview by remember(permissionHost,draft?.athleteId,draft?.cycleId) {
        mutableStateOf<NativePlanMachineReview?>(null)
    }
    LaunchedEffect(permissionHost,draft?.athleteId,draft?.cycleId,
        reviewedMachineGymId,state.sourceRevision) {
        // Never retain another owner's gym roster after account/program changes.
        planMachineReview=null
        val athleteId=draft?.athleteId
        val gymId=reviewedMachineGymId
        if(permissionHost!=null && athleteId!=null && gymId!=null) {
            planMachineReview=withContext(Dispatchers.IO) {
                permissionHost.reviewPlanPhysicalMachines(athleteId,gymId)
            }
        }
    }
    var showProgramMetadata by rememberSaveable(draft?.athleteId,draft?.cycleId) {mutableStateOf(false)}
    var supersetEditor by rememberSaveable(stateSaver=NativeKotlinSupersetRequestSaver) {
        mutableStateOf<NativeKotlinSupersetEditRequest?>(null)
    }
    LaunchedEffect(draft?.cycleId,draft?.athleteId) {
        if(draft!=null && supersetEditor?.let{it.cycleId!=draft.cycleId || it.athleteId!=draft.athleteId}==true)
            supersetEditor=null
    }
    var expandedBlockId by rememberSaveable(draft?.athleteId,draft?.cycleId) {
        mutableStateOf(draft?.blocks?.firstOrNull()?.blockId)
    }
    var expandedDayKey by rememberSaveable(draft?.athleteId,draft?.cycleId) {
        mutableStateOf(draft?.blocks?.firstOrNull()?.let { block ->
            block.days.minByOrNull{it.weekday}?.let{day->
                programDayKey(block.blockId,day.weekday)
            }
        })
    }
    LaunchedEffect(draft?.cycleId,draft?.revision) {
        val latest=draft ?:return@LaunchedEffect
        if(expandedBlockId!=null && latest.blocks.none{
            it.blockId==expandedBlockId
        }) {
            expandedBlockId=latest.blocks.firstOrNull()?.blockId
            expandedDayKey=latest.blocks.firstOrNull()?.days
                ?.minByOrNull{it.weekday}?.let{day->
                    programDayKey(checkNotNull(expandedBlockId),day.weekday)
                }
        } else if(expandedDayKey!=null && latest.blocks.none{block->
            block.days.any{day->
                programDayKey(block.blockId,day.weekday)==expandedDayKey
            }
        })expandedDayKey=null
    }
    val pageScroll=rememberScrollState()
    LaunchedEffect(draft?.cycleId,showProgramEditor) {
        // Same page, distinct tasks: the full editor should start at its top
        // even when the athlete selected a deep day in the prior outline.
        pageScroll.scrollTo(0)
    }
    val policyDrafts=rememberSaveableStateHolder()
    var retainedPolicyKey by rememberSaveable {mutableStateOf<String?>(null)}
    val policyKey=draft?.let{"policy:${it.athleteId}:${it.cycleId}:${it.revision}:${state.sourceRevision}"}
    LaunchedEffect(policyKey) {
        if(policyKey!=null) {
            retainedPolicyKey?.takeIf{it!=policyKey}?.let{policyDrafts.removeState(it)}
            retainedPolicyKey=policyKey
        }
    }
    val policyReady=state.coachingAudit?.status==
        CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
        state.coachingAudit?.issues?.isEmpty()==true &&
        state.auditSourceRevision!=null &&
        state.auditSourceRevision==state.sourceRevision &&
        state.coachingAudit?.auditedCycleRevision==draft?.revision
    val signed=(state.signoff as? NativeCustomSignoffRead.Found)?.isCurrent==true
    val currentSelection=state.nextCycleSlot?.takeIf{
        state.nextSlotCycleRevision==draft?.revision && policyReady && signed
    }
    var offeredInitialPolicyEditor by rememberSaveable(draft?.athleteId,draft?.cycleId){mutableStateOf(false)}
    LaunchedEffect(draft?.cycleId,busy,policyReady) {
        // Offer missing rules once when opening a program. A read-only browse
        // or Cancel must not reopen an editor the athlete deliberately closed.
        if(draft!=null && !busy && !offeredInitialPolicyEditor) {
            offeredInitialPolicyEditor=true
            if(!policyReady)showProgramEditor=true
        }
    }
    LaunchedEffect(initialCycleId) {
        if (!initialCycleId.isNullOrBlank()) controller.open(initialCycleId)
    }
    LaunchedEffect(draft?.cycleId) {
        if (draft != null) openId = draft.cycleId
    }

    var title by rememberSaveable { mutableStateOf("") }
    var split by rememberSaveable { mutableStateOf("") }
    var phaseTitle by rememberSaveable { mutableStateOf("") }
    var weeks by rememberSaveable { mutableStateOf("4") }
    var goal by rememberSaveable { mutableStateOf(AthleteTrainingGoal.POWERBUILDING) }
    var phase by rememberSaveable { mutableStateOf(TrainingPhase.HYPERTROPHY) }

    var editingCycle by remember { mutableStateOf(false) }
    var addingDayFor by remember { mutableStateOf<String?>(null) }
    var addingBlock by remember { mutableStateOf(false) }
    var editingBlock by remember { mutableStateOf<String?>(null) }
    var editingDay by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    // Destructive preview state is transient. It is not an approval token,
    // and cannot survive a different athlete or custom program selection.
    var removeDayReview by remember(draft?.athleteId,draft?.cycleId) {
        mutableStateOf<NativePlanDayRemovalReview?>(null)
    }
    var removeBlockReview by remember(draft?.athleteId,draft?.cycleId) {
        mutableStateOf<NativePlanBlockRemovalReview?>(null)
    }
    var confirmConsent by remember { mutableStateOf(false) }
    var inspectedWeek by rememberSaveable { mutableStateOf("1") }
    var inspectedWeekday by rememberSaveable { mutableIntStateOf(0) }
    var customShortMinutes by rememberSaveable(
        draft?.cycleId,draft?.revision,inspectedWeek,inspectedWeekday
    ) {mutableIntStateOf(30)}
    var coachMaxStep by rememberSaveable {mutableStateOf("5")}
    var coachReadiness by remember {mutableStateOf(CoachReadiness.NORMAL)}
    var coachRestricted by rememberSaveable {mutableStateOf(false)}
    var dayCoachingVisible by remember {mutableStateOf(false)}
    var recoveryFocusRequested by remember(draft?.athleteId,draft?.cycleId,draft?.revision,state.sourceRevision) {
        mutableStateOf(false)
    }
    var basicProgressionVisible by remember {mutableStateOf(false)}
    val futureWeekScope=rememberCoroutineScope()
    var futureWeekBusy by remember {mutableStateOf(false)}
    var futureWeekVersion by remember {mutableLongStateOf(0)}
    var futureWeekSlotId by remember {mutableStateOf<String?>(null)}
    var futureWeekOption by remember {
        mutableStateOf<NativeFutureWeekProposalResult?>(null)
    }
    var futureWeekSavePreview by remember {
        mutableStateOf<NativeFutureWeekDraftPreview?>(null)
    }
    var futureWeekSaveDecision by remember {
        mutableStateOf<NativeFutureWeekDraftDecision?>(null)
    }
    var confirmFutureWeekSave by remember {mutableStateOf(false)}
    LaunchedEffect(draft?.revision,state.sourceRevision,state.authoredPreview,
        state.reviewedCoachInputs,coachMaxStep,coachReadiness,coachRestricted,
        inspectedWeek,inspectedWeekday) {
        dayCoachingVisible=false
        basicProgressionVisible=false
        futureWeekVersion++
        futureWeekSlotId=null
        futureWeekOption=null
        futureWeekBusy=false
        futureWeekSavePreview=null
        futureWeekSaveDecision=null
        confirmFutureWeekSave=false
        controller.clearCalibrationOffer()
    }
    // Slot identity is stable and separate from the exercise catalog ID.
    // Null slot ID denotes authoring a genuinely new exercise visit.
    var exerciseEditor by remember {
        mutableStateOf<Triple<String, Int, String?>?>(null)
    }
    /** Only explicitly approved program edits may change exercise identity. */
    var swapExerciseEditor by remember {
        mutableStateOf<Triple<String,Int,String>?>(null)
    }
    var swapSearchSuggestion by remember {
        mutableStateOf<String?>(null)
    }
    // A removal review is intentionally transient: relaunching the app
    // cannot silently inherit permission to discard a program slot.
    var removeExerciseReview by remember(draft?.athleteId,draft?.cycleId) {
        mutableStateOf<NativeKotlinPlanExerciseRemovalReview?>(null)
    }
    var crossDayMoveScope by remember(draft?.athleteId,draft?.cycleId) {
        mutableStateOf<NativeKotlinPlanCrossDayMoveScope?>(null)
    }
    var weekExerciseEditor by remember(draft?.cycleId,draft?.revision) {
        mutableStateOf<Triple<Int,Int,String>?>(null)
    }
    /** Prefill only a freshly validated V3 suggestion; never silently save. */
    var weekEditFromCoach by remember(draft?.cycleId,draft?.revision) {
        mutableStateOf(false)
    }
    LaunchedEffect(state.weekEditReview) {
        if(state.weekEditReview!=null)weekExerciseEditor=null
    }

    programPicker?.let { picker ->
        key(picker.token) {
            NativeKotlinProgramPickerSheet(picker,draft?.cycleId,
                onDismiss=controller::dismissProgramPicker,
                onRefresh=controller::findSavedPrograms,onOpen=controller::openSavedProgram)
        }
    }

    val dayEditorAnchor=remember { BringIntoViewRequester() }
    val renderDayEditor:@Composable (CustomTrainingBlock,CustomTrainingDay)->Unit={block,day->
        draft?.let { current ->
            NativeKotlinPlanDayEditor(current,block,day,busy,
                allowSupersets=state.reviewedSourceSetup?.reviewed?.allowSupersets==true,
                onEditExercise={slot->exerciseEditor=Triple(block.blockId,day.weekday,slot)},
                onSwapExercise={slot->swapExerciseEditor=Triple(block.blockId,day.weekday,slot)},
                onMoveExercise={slot,direction->
                    controller.moveExercise(block.blockId,day.weekday,slot,direction)
                },
                onRemoveExercise={slot->
                    removeExerciseReview=NativeKotlinPlanExerciseRemoval.inspect(
                        current,block.blockId,day.weekday,slot)
                },
                onMoveToDay={slot->
                    crossDayMoveScope=NativeKotlinPlanCrossDayMoveScope(
                        current.athleteId,current.cycleId,current.revision,
                        block.blockId,day.weekday,slot)
                },
                onEditSuperset={group->
                    supersetEditor=controller.supersetEditRequest(block.blockId,day.weekday,group)
                },
                onEditDay={editingDay=block.blockId to day.weekday},
                onAddExercise={exerciseEditor=Triple(block.blockId,day.weekday,null)},
                onRemoveDay={
                    removeDayReview=NativeKotlinPlanStructureRemoval.day(
                        current,block.blockId,day.weekday)
                },
                machineReview=planMachineReview,
                onManageMachine=onManagePhysicalMachine,
                enableProgressionEditing=true,
                onProgressionChoice={slotId,method->
                    controller.chooseExerciseProgression(
                        current,block.blockId,day.weekday,slotId,method)
                })
        }
    }
    val renderNavigator:@Composable (Boolean)->Unit={wide->
        draft?.let { current ->
            NativeKotlinPlanEditorNavigator(current,busy,expandedBlockId,expandedDayKey,wide,
                onToggleBlock={blockId->
                    if(expandedBlockId==blockId)expandedBlockId=null else {
                        expandedBlockId=blockId
                        val block=current.blocks.singleOrNull { it.blockId==blockId }
                        if(block?.days?.none {
                            programDayKey(blockId,it.weekday)==expandedDayKey
                        }==true)expandedDayKey=block.days.minByOrNull { it.weekday }?.let {
                            programDayKey(blockId,it.weekday)
                        }
                    }
                },
                onSelectDay={expandedDayKey=it},
                onEditBlock={editingBlock=it},
                onDuplicateBlock=controller::duplicateBlock,
                onMoveBlock=controller::reorderBlock,
                onAddDay={addingDayFor=it},onAddBlock={addingBlock=true},
                onRemoveBlock={blockId->
                    removeBlockReview=NativeKotlinPlanStructureRemoval.block(
                        current,blockId)
                },
                dayContent=renderDayEditor)
        }
    }
    NativeKotlinPlanWorkspace(
        showNavigator=showProgramEditor && draft!=null && !state.fixedNamedDraft && !state.tieredDraft,
        navigationKey=draft?.let { it.athleteId to it.cycleId },
        scrollState=pageScroll,
        navigator={
            PursuitSectionHeading("Program outline","Choose a day to edit")
            renderNavigator(true)
        }
    ) { twoPane ->
        LaunchedEffect(twoPane,expandedDayKey,showProgramEditor) {
            if(twoPane && showProgramEditor && resolveNativePlanEditorDay(draft,expandedDayKey)!=null) {
                // Wait until the new heading has been laid out after a selection
                // or a window resize. Do not scroll on every draft revision.
                withFrameNanos { }
                dayEditorAnchor.bringIntoView()
            }
        }
        PageHeading("YOUR TRAINING PLAN",
            if (draft == null) "Your programs" else draft.title,
            if(draft==null)"Build a program around the way you train."
            else "${pretty(draft.goal)} · ${draft.blocks.sumOf{it.weeks}} weeks")
        if(draft!=null)OutlinedButton(onClick=controller::findSavedPrograms,
            enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Programs")}
        permissionHost?.let {
            NativeVerifiedGeneratedWorkoutResumeCard(it,onResumeGeneratedWorkout)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.programSelectionNotice?.let {
            Text(it,style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(draft==null || state.refusal!=null || showProgramEditor)state.message?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=if(state.refusal==null)MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error)
        }

        if (draft == null) {
            PursuitCard(highlighted = true) {
                Text("Your saved programs",
                    style = MaterialTheme.typography.titleMedium)
                Text("Open a verified program by name. Your edits, blocks and exercise choices are preserved.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {controller.refreshSavedPrograms()},
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {Text("Refresh saved programs")}
                NativeKotlinSavedProgramList(state.savedDrafts,state.savedProgramsOwner,
                    currentCycleId=null,busy=busy,loaded=state.savedDraftsLoaded,
                    onOpen=controller::openSavedProgram)
                TextButton(onClick={showAdvancedId=!showAdvancedId},
                    enabled=!busy) {
                    Text(if(showAdvancedId)"Hide program ID entry"
                        else "Advanced: open by program ID")
                }
                if(showAdvancedId) {
                    OutlinedTextField(openId, {openId=it},
                        label={Text("Program ID")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedButton(onClick={controller.open(openId.trim())},
                        enabled=!busy && openId.isNotBlank(),
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Open program by ID")
                    }
                }
            }
            PursuitCard {
                Text("Build a program from your setup",
                    style=MaterialTheme.typography.titleMedium)
                Text("Choose exercises and starting weights, then review a complete first block.",
                    style=MaterialTheme.typography.bodySmall)
                PursuitPrimaryAction("Build a new program",enabled=!busy) {
                    onBuildProgram()
                }
                TextButton(onClick={creatingBlank=!creatingBlank},enabled=!busy) {
                    Text(if(creatingBlank)"Hide custom builder" else "Create a custom program instead")
                }
            }
            if(creatingBlank) {
                PursuitCard(highlighted = true) {
                    Text("Start from blank", style = MaterialTheme.typography.titleLarge)
                    Text("Choose your own split, phases, and training days. No template will overwrite your selections.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(title, { title = it },
                        label = { Text("Program name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    CycleChoiceRow("Training goal", AthleteTrainingGoal.entries,
                        goal, ::pretty) { goal = it }
                    OutlinedTextField(split, { split = it },
                        label = { Text("Your split name") },
                        placeholder = { Text("Chest / Back / Legs") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phaseTitle, { phaseTitle = it },
                        label = { Text("First block name") },
                        placeholder = { Text("Foundation") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    CycleChoiceRow("First phase", TrainingPhase.entries,
                        phase, ::pretty) { phase = it }
                    OutlinedTextField(weeks, { weeks = it },
                        label = { Text("Weeks in first block (1–52)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            controller.create(title.trim(), goal,
                                weeks.toIntOrNull() ?: 0, phase,
                                phaseTitle.trim(), split.trim())
                        },
                        enabled = !busy && title.isNotBlank() &&
                            phaseTitle.isNotBlank() && split.isNotBlank() &&
                            (weeks.toIntOrNull() in 1..52),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create and save my draft") }
                }
            }
        } else if(state.tieredDraft) {
            NativeTieredInitialExecutionPanel(state,controller,onOpenIssuedWorkout)
        } else if(state.fixedNamedDraft) {
            NativeFixedNamedExecutionPanel(state,controller,onOpenIssuedWorkout)
        } else {
            if(signed && policyReady)
                NativeFixedNamedSelectionPanel(draft,state,controller)
            programSwapReview?.let {request->
                val matched=NativeKotlinProgramSwapHandoffDomain
                    .canOpenExactEdit(request,draft)
                PursuitCard(highlighted=true) {
                    Text("Review a workout swap for your program",
                        style=MaterialTheme.typography.titleMedium)
                    Text(
                        "${kotlinExerciseLabel(request.originalExerciseId)} "+
                        "→ ${kotlinExerciseLabel(
                            request.workoutReplacementExerciseId)} · "+
                        "week ${request.absoluteWeek}, "+
                        "${customWeekdays.getOrElse(request.weekday){"day"}}",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("That exercise change affected one workout only. "+
                        "Updating this program is a separate decision: "+
                        "re-enter new weights, reps, rest and techniques, "+
                        "then review the full training cycle before approving.",
                        style=MaterialTheme.typography.bodySmall)
                    if(!matched)
                        Text("The program revision, original slot or "+
                            "protected week overrides no longer match the "+
                            "issued workout. Automatic selection is blocked; "+
                            "inspect the saved day and its week overrides "+
                            "manually before making any new program edit.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                    if(matched)
                        Button(
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth(),
                            onClick={
                                showProgramEditor=true
                                expandedBlockId=request.blockId
                                expandedDayKey=programDayKey(
                                    request.blockId,request.weekday)
                                swapSearchSuggestion=
                                    request.workoutReplacementExerciseId
                                swapExerciseEditor=Triple(
                                    request.blockId,request.weekday,
                                    request.originalSlotId)
                                onProgramSwapReviewDismiss()
                            }
                        ){Text("Review this program-day replacement")}
                    TextButton(
                        enabled=!busy,
                        onClick=onProgramSwapReviewDismiss
                    ){Text("Keep my saved program unchanged")}
                }
            }
            PursuitCard(highlighted=true) {
                Text(if(currentSelection is NativeCustomNextCycleSlot.ResumeIssued)
                    "Continue your workout" else "Next workout in your program",
                    style=MaterialTheme.typography.titleLarge)
                Text(if(currentSelection is NativeCustomNextCycleSlot.ResumeIssued)
                    "Your saved sets are ready. Pick up where you left off."
                    else "Follow your saved program, one workout at a time.",
                    style=MaterialTheme.typography.bodySmall)
                if(!signed)PursuitPrimaryAction("Review program approval",
                    enabled=!busy&&policyReady) {confirmConsent=true}
                OutlinedButton(
                    onClick=controller::findNextCycleSession,
                    enabled=!busy&&policyReady&&signed,
                    modifier=Modifier.fillMaxWidth()
                ) { Text("Find my next workout") }
                if(!signed||!policyReady)
                    Text(if(policyReady)"Your program review is ready. Confirm it before choosing a workout."
                        else "Complete the training review below before approving your program.",
                        style=MaterialTheme.typography.bodySmall)
                val selection=currentSelection
                when(selection) {
                    is NativeCustomNextCycleSlot.Next,
                    is NativeCustomNextCycleSlot.Prepared -> {
                        val week=when(selection) {
                            is NativeCustomNextCycleSlot.Next ->selection.absoluteWeek
                            is NativeCustomNextCycleSlot.Prepared ->selection.absoluteWeek
                            else ->0
                        }
                        val weekday=when(selection) {
                            is NativeCustomNextCycleSlot.Next ->selection.weekday
                            is NativeCustomNextCycleSlot.Prepared ->selection.weekday
                            else ->-1
                        }
                        val label=when(selection) {
                            is NativeCustomNextCycleSlot.Next ->selection.dayLabel
                            is NativeCustomNextCycleSlot.Prepared ->selection.dayLabel
                            else ->""
                        }
                        Text("Week ${week} · ${customWeekdays.getOrElse(weekday){"Day"}} · ${label}",
                            style=MaterialTheme.typography.titleMedium)
                        NativeKotlinNextSessionBrief(draft,week,weekday)
                        Button(onClick={
                            inspectedWeek=week.toString()
                            inspectedWeekday=weekday
                            controller.verifyConsentBoundDay(week,weekday)
                        },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                            Text("Verify this exact workout")
                        }
                        val preflight=state.consentBoundPreview?.session
                        if(preflight?.absoluteWeek==week &&
                            preflight.weekday==weekday &&
                            preflight.stamp.cycleRevision==draft.revision) {
                            when(selection) {
                                is NativeCustomNextCycleSlot.Next ->
                                    Button(
                                        enabled=!busy&&state.preparedWorkout==null,
                                        onClick={
                                            controller.prepareConsentBoundDay(
                                                week,weekday)
                                        },
                                        modifier=Modifier.fillMaxWidth()
                                    ) {Text("Prepare this workout")}
                                is NativeCustomNextCycleSlot.Prepared ->
                                    OutlinedButton(
                                        enabled=!busy,
                                        onClick={
                                            controller.reopenPreparedWorkout(
                                                selection.workoutId)
                                        },modifier=Modifier.fillMaxWidth()
                                    ) {Text("Reopen saved preparation")}
                                else -> {}
                            }
                        }
                    }
                    is NativeCustomNextCycleSlot.ResumeIssued -> {
                        Text("Unfinished workout · week ${selection.absoluteWeek}, ${selection.dayLabel}",
                            style=MaterialTheme.typography.titleMedium)
                        Button(
                            enabled=!busy,
                            onClick={onOpenIssuedWorkout(selection.workoutId)},
                            modifier=Modifier.fillMaxWidth()
                        ) { Text("Resume my workout") }
                    }
                    NativeCustomNextCycleSlot.CycleComplete ->
                        Text("You have finished every workout in this training cycle.",
                            style=MaterialTheme.typography.bodyMedium)
                    is NativeCustomNextCycleSlot.Rejected -> {
                        Text(selection.reason,
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            enabled=!busy,
                            onClick=onOpenHistory,
                            modifier=Modifier.fillMaxWidth()
                        ) {Text("Open history setup and recovery")}
                    }
                    null -> {}
                }
            }
            // Change rights are durable and separately approved by the
            // athlete, not inferred from a selected split name or coach badge.
            // The permission store grants only review rights for this cycle.
            permissionHost?.let { ownerHost ->
                NativeKotlinAdaptiveCyclePermissionsPanel(
                    ownerHost,draft.cycleId,draft.athleteId,draft.revision)
            }
            // The full plan is a primary Plan action, not buried beneath
            // coaching/calibration. Keep its actual saved exercise outline
            // visible before optional training-change proposals.
            OutlinedButton(onClick={showProgramEditor=!showProgramEditor},
                enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text(if(showProgramEditor)"Close full program editor"
                    else "Open full program editor")
            }
            // Switching modes reveals the requested content immediately.
            // Do not force the athlete to scroll past the whole outline and
            // optional calibration panel to reach the actual editor.
            if(!showProgramEditor) {
                NativeKotlinCustomCycleOverview(draft,currentSelection,
                    onEditDay={blockId,weekday->
                        // Navigation selects the genuine authored day only;
                        // all edits still go through the native controller.
                        expandedBlockId=blockId
                        expandedDayKey=programDayKey(blockId,weekday)
                        showProgramEditor=true
                    },
                    onEditExercise={blockId,weekday,slotId->
                        expandedBlockId=blockId
                        expandedDayKey=programDayKey(blockId,weekday)
                        showProgramEditor=true
                        exerciseEditor=Triple(blockId,weekday,slotId)
                    })
                permissionHost?.let{ ownerHost ->
                    NativeKotlinFirstBlockCalendarPanel(
                        host=ownerHost,cycleId=draft.cycleId,
                        athleteId=draft.athleteId,cycleRevision=draft.revision,
                        sourceRevision=state.sourceRevision,
                        firstWeekday=draft.blocks.firstOrNull()?.days?.minOfOrNull { it.weekday },
                        nextSlot=currentSelection)
                    NativeKotlinApprovedBlockCalendarPanel(
                        host=ownerHost,cycleId=draft.cycleId,
                        athleteId=draft.athleteId,cycleRevision=draft.revision,
                        sourceRevision=state.sourceRevision,
                        nextSlot=currentSelection)
                }
                NativeKotlinCycleBlockComparisonPanel(
                    cycle=draft,
                    currentAudit=state.coachingAudit?.takeIf {
                        state.auditSourceRevision!=null &&
                        state.auditSourceRevision==state.sourceRevision &&
                        it.auditedCycleRevision==draft.revision
                    },
                    onEditBlock={blockId->
                        expandedBlockId=blockId
                        expandedDayKey=draft.blocks.firstOrNull {
                            it.blockId==blockId
                        }?.days?.minByOrNull{it.weekday}?.let{
                            programDayKey(blockId,it.weekday)
                        }
                        showProgramEditor=true
                    },
                    enabled=!busy,
                    repairReview=state.strengthRepairReview,
                    canRequestStrengthRepair=permissionHost!=null &&
                        state.coachingSuggestionsEnabled &&
                        state.reviewedCoachInputs!=null &&
                        state.coachingAudit?.structuralReview?.verifiedDraft==draft &&
                        state.auditSourceRevision!=null &&
                        state.auditSourceRevision==state.sourceRevision,
                    onRequestStrengthRepair=if(permissionHost!=null)
                        ({blockId,maximumWorkingSets->
                            controller.reviewStrengthVolumeRepair(
                                blockId,maximumWorkingSets)
                        }) else null,
                    onAcceptStrengthRepair=if(permissionHost!=null)
                        ({controller.acceptStrengthVolumeRepair(true)}) else null,
                    menuRecoveryReview=state.strengthMenuRecoveryReview,
                    canRequestMenuRecovery=permissionHost!=null &&
                        state.coachingSuggestionsEnabled &&
                        state.reviewedCoachInputs!=null &&
                        state.coachingAudit?.structuralReview?.verifiedDraft==draft &&
                        state.auditSourceRevision!=null &&
                        state.auditSourceRevision==state.sourceRevision,
                    onRequestMenuRecovery=if(permissionHost!=null)
                        ({blockId,maxExercises->
                            controller.reviewStrengthExerciseMenu(
                                blockId,maxExercises)
                        }) else null,
                    onAcceptMenuRecovery=if(permissionHost!=null)
                        ({controller.acceptStrengthExerciseMenu(true)}) else null
                )
                if(permissionHost!=null)NativeRecoveryRecipePanel(draft,state,controller,busy,
                    focusRequested=recoveryFocusRequested,onFocusConsumed={recoveryFocusRequested=false})
                NativeKotlinCustomCycleCalibrationPanel(
                    controller,state,onOpenIssuedWorkout)
            }
            // Incomplete custom programs keep their existing editor and policy
            // controls visible. Reviewed programs open them only on request.
            if(showProgramEditor) {
            PursuitCard(highlighted = true) {
                PursuitSectionHeading("Edit your program",
                    "${draft.blocks.size} blocks · ${draft.blocks.sumOf { it.weeks }} weeks · " +
                    "${draft.blocks.sumOf { it.days.size }} scheduled training days")
                if(draft.authorNote.isNotBlank())
                    Text(draft.authorNote,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={showProgramMetadata=!showProgramMetadata},
                    modifier=Modifier.heightIn(min=48.dp)) {
                    Text(if(showProgramMetadata)"Hide advanced program details"
                        else "Advanced program details")
                }
                if(showProgramMetadata) {
                    Text("Saved revision ${draft.revision} · Cycle ID ${draft.cycleId}",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingCycle = true },
                        enabled = !busy) { Text("Name & notes") }
                    OutlinedButton(onClick = { controller.undo() },
                        enabled = !busy && state.canUndo) { Text("Undo last edit") }
                    OutlinedButton(onClick = { controller.open(draft.cycleId) },
                        enabled = !busy) { Text("Reload") }
                }
            }
            if(!twoPane)renderNavigator(false) else {
                val selected=resolveNativePlanEditorDay(draft,expandedDayKey)
                PursuitCard {
                    if(selected==null) {
                        PursuitSectionHeading("Choose a training day",
                            "Select a day in the program outline to edit its exercises.")
                    } else {
                        Box(Modifier.fillMaxWidth()
                            .bringIntoViewRequester(dayEditorAnchor)
                            .semantics { liveRegion=LiveRegionMode.Polite }) {
                            PursuitSectionHeading(
                                "${customWeekdays.getOrElse(selected.day.weekday) { "Day" }} · ${selected.day.label}",
                                "${selected.block.label} · ${pretty(selected.day.purpose)}")
                        }
                        renderDayEditor(selected.block,selected.day)
                    }
                }
            }
            PursuitCard {
                Text("Exact native week and workout preview",
                    style = MaterialTheme.typography.titleLarge)
                Text("Inspect the actual authored day, including individual sets and week overrides. No generic workout composer changes your exercises or weight. This is preview only.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    inspectedWeek,{ inspectedWeek=it.take(3) },
                    label={Text("Absolute week in cycle (1–${draft.blocks.sumOf {it.weeks}})")},
                    singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    modifier=Modifier.fillMaxWidth()
                )
                val weekNumber=inspectedWeek.toIntOrNull()
                var relative=weekNumber ?: -1
                val block= if(relative<1) null else
                    draft.blocks.firstOrNull{
                        if(relative>it.weeks){relative-=it.weeks;false}
                        else true
                    }
                val validDays=block?.days?.map {it.weekday}?.sorted().orEmpty()
                if(block!=null) {
                    Text("Resolved block: ${block.label} · week ${relative}",
                        style=MaterialTheme.typography.bodySmall)
                    CycleChoiceRow("Workout day",
                        validDays,inspectedWeekday,
                        {customWeekdays.getOrElse(it){"Day"}}) {
                        inspectedWeekday=it
                    }
                }
                val validWeek=weekNumber!=null &&
                    weekNumber in 1..draft.blocks.sumOf {it.weeks} &&
                    inspectedWeekday in validDays
                Button(
                    onClick={
                        val week=weekNumber
                        if(week!=null)controller.previewAuthoredDay(
                            week,inspectedWeekday)
                    },
                    enabled=!busy && validWeek,
                    modifier=Modifier.fillMaxWidth()
                ) {Text("Preview exact authored workout")}
                if(state.coachingAudit?.status ==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                    state.signoff is NativeCustomSignoffRead.Found) {
                    OutlinedButton(
                        onClick={
                            val week=weekNumber
                            if(week!=null) controller.verifyConsentBoundDay(
                                week,inspectedWeekday)
                        },
                        enabled=!busy && validWeek,
                        modifier=Modifier.fillMaxWidth()
                    ) {Text("Verify exact consent and session")}
                }
                if(validWeek &&
                    state.consentBoundPreview?.session?.absoluteWeek==weekNumber &&
                    state.consentBoundPreview?.session?.weekday==inspectedWeekday &&
                    state.consentBoundPreview?.session?.stamp?.cycleRevision==draft.revision) {
                    HorizontalDivider()
                    PursuitSectionHeading(
                        "Shorter-session review",
                        "A safe, read-only preview for this exact signed custom week."
                    )
                    CycleChoiceRow("Time available",
                        listOf(45,30,20),customShortMinutes,
                        {"$it min"}) { customShortMinutes=it }
                    OutlinedButton(
                        enabled=!busy && validWeek,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                        onClick={
                            val week=weekNumber
                            if(week!=null)controller.reviewCustomShorterDay(
                                week,inspectedWeekday,customShortMinutes)
                        }
                    ) {Text("Review optional cuts · do not change workout")}
                    val timeReview=state.customShortReview?.takeIf{
                        it.absoluteWeek==weekNumber &&
                        it.weekday==inspectedWeekday &&
                        it.review.requestedMinutes==customShortMinutes &&
                        it.review.source==state.consentBoundPreview?.session?.stamp
                    }?.review
                    if(timeReview!=null) {
                        val canFit=timeReview.status in setOf(
                            CustomShortSessionStatus.ALREADY_FITS,
                            CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW)
                        Text(
                            if(canFit)"Time review: ${pretty(timeReview.status)}"
                            else "Not safe to shorten: ${pretty(timeReview.status)}",
                            color=if(canFit)MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.titleSmall
                        )
                        val original=timeReview.originalEstimatedSeconds
                        val shortened=timeReview.reviewedEstimatedSeconds
                        if(original!=null && shortened!=null) {
                            Text(
                                "Conservative declared-time estimate: " +
                                    "${(original+59L)/60L} → ${(shortened+59L)/60L} min · " +
                                    "${timeReview.omittedWorkingSets} working sets omitted",
                                style=MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(timeReview.explanation,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        val changedSlots=timeReview.slotImpacts.filter {
                            it.omittedWorkingSets>0 || it.omittedWarmupSets>0
                        }
                        if(changedSlots.isNotEmpty()) {
                            HorizontalDivider()
                            PursuitSectionHeading("What changes in this session",
                                if(canFit)
                                    "Compare exact retained work before saving a shorter custom session."
                                else "These proposed cuts still cannot meet the chosen time limit. Nothing can be approved.")
                            val originalSets=timeReview.slotImpacts.sumOf {
                                it.originalWorkingSets
                            }
                            val retainedSets=timeReview.slotImpacts.sumOf {
                                it.retainedWorkingSets
                            }
                            Text("$retainedSets of $originalSets working sets retained · ${timeReview.omittedWorkingSets} omitted",
                                style=MaterialTheme.typography.labelLarge)
                            changedSlots.forEach {impact->
                                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text("${kotlinExerciseLabel(impact.exerciseId)} · Exercise ${impact.originalPosition}",
                                        style=MaterialTheme.typography.titleSmall)
                                    Text("${impact.retainedWorkingSets} of ${impact.originalWorkingSets} working sets kept · ${impact.omittedWorkingSets} omitted",
                                        style=MaterialTheme.typography.bodySmall)
                                    if(impact.omittedWarmupSets>0)
                                        Text("${impact.omittedWarmupSets} warm-up set(s) omitted with this exercise",
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(impact.supersetGroup!=null)
                                        Text("Linked superset · partners are changed together",
                                            style=MaterialTheme.typography.labelSmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else if(canFit) Text(
                            "All originally authored working sets are retained.",
                            style=MaterialTheme.typography.bodySmall)
                        Text("This review cannot alter the program or any previously issued workout. To use a shorter session, explicitly approve a separate saved preparation; the native issuer will independently verify it again.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(canFit && state.preparedWorkout==null) {
                            PursuitPrimaryAction(
                                "Approve and save ${customShortMinutes}-minute custom workout",
                                enabled=!busy
                            ) {
                                val week=weekNumber
                                if(week!=null)controller.prepareConsentBoundShorterDay(
                                    week,inspectedWeekday,customShortMinutes)
                            }
                        }
                    }
                }
                if(state.calibrationReceipt==null && validWeek && state.consentBoundPreview?.session?.absoluteWeek==weekNumber &&
                    state.consentBoundPreview?.session?.weekday==inspectedWeekday) {
                    OutlinedButton(
                        onClick={
                            if(weekNumber!=null)
                                controller.prepareConsentBoundDay(
                                    weekNumber,inspectedWeekday)
                        },
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                    ) {Text("Save immutable workout preparation")}
                    Text("This stores the exact reviewed sets for future native issuance. It cannot start, log or finish a workout yet.",
                        style=MaterialTheme.typography.bodySmall)
                }
                state.preparationRefusal?.let {
                    Text("Preparation not saved: ${pretty(it)}",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                    if(it==NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED)
                        Text("The authored weights are higher than the previous same-day workout. " +
                            "Review the suggested repeat or choose the heavier authored targets below. " +
                            "A failed previous workout does not earn a weight increase.",
                            style=MaterialTheme.typography.bodySmall)
                }
                state.preparedWorkout?.let { prepared ->
                    Text("Prepared ID: ${prepared.workoutId}",
                        style=MaterialTheme.typography.bodySmall)
                    val timeChoice=prepared.snapshot.shortSessionLimitMinutes
                    Text(
                        if(timeChoice==null)
                            "Full authored session · saved exact targets; not yet issued."
                        else "${timeChoice}-minute approved custom preparation · " +
                            "${prepared.snapshot.shortSessionOmittedSetKeys.size} original set(s) omitted before issuance. Saved targets remain reviewable.",
                        style=MaterialTheme.typography.bodySmall
                    )
                    // Only a saved, exact-week unissued short preparation
                    // can offer this two-step return to the original Full.
                    if(timeChoice!=null && prepared.snapshot.stamp.cycleId==draft.cycleId &&
                        prepared.snapshot.stamp.cycleRevision==draft.revision &&
                        prepared.snapshot.absoluteWeek==weekNumber &&
                        prepared.snapshot.weekday==inspectedWeekday) {
                        OutlinedButton(
                            onClick=controller::reviewCustomTimeUndo,
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        ) {Text("Restore original Full workout")}
                        state.customTimeUndo?.takeIf {
                            it.shortened==prepared &&
                                it.full.stamp==prepared.snapshot.stamp &&
                                it.full.absoluteWeek==prepared.snapshot.absoluteWeek &&
                                it.full.weekday==prepared.snapshot.weekday
                        }?.let {ready->
                            NativeKotlinCustomTimeRestorePanel(
                                ready,busy,
                                onConfirm=controller::acceptCustomTimeUndo,
                                onCancel=controller::dismissCustomTimeUndoReview)
                        }
                    }
                }
                state.authoredPreviewRefusal?.let {
                    Text("Cannot preview · ${pretty(it)}",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodyMedium)
                }
                state.authoredPreview?.let { session ->
                    HorizontalDivider()
                    Text("Week ${session.absoluteWeek} · ${customWeekdays.getOrElse(session.weekday){"Day"}} · ${session.dayLabel}",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Block: ${session.blockId} · ${pretty(session.phase)} · ${pretty(session.sessionPurpose)}",
                        style=MaterialTheme.typography.bodySmall)
                    Text("Exact known set/rest: ${session.knownSetAndRestSeconds}s · non-set time: ${session.declaredNonSetSeconds?.toString()?.plus("s") ?: "not declared"}",
                        style=MaterialTheme.typography.bodySmall)
                    Text(if(state.consentBoundPreview!=null)
                        "Consent-bound preflight passed; this is STILL NOT an issued workout."
                        else "Draft preview: NOT a reviewed workout grant.",
                        style=MaterialTheme.typography.bodySmall)
                    val maxStep=runCatching{BigDecimal(coachMaxStep.trim())}
                        .getOrNull()?.takeIf{it>BigDecimal.ZERO && it<=BigDecimal("10000") &&
                            it.stripTrailingZeros().scale()<=3}
                    val basicPlan=remember(draft, session.blockId, session.weekInBlock) {
                        BasicProgramProgressionPlanDomain.review(draft,
                            previewWeek = ProgramProgressionWeek(session.blockId, session.weekInBlock))
                            as? ProgramProgressionPlan.Ready
                    }
                    val basicSlots=basicPlan?.assignments?.filter{
                        it.key.blockId==session.blockId &&
                            it.key.weekday==session.weekday &&
                            it.savedMethod!=null &&
                            (it.route in setOf(ProgramProgressionRoute.BASIC_WEIGHTED,
                                ProgramProgressionRoute.MANUAL,
                                ProgramProgressionRoute.E1RM_COACH_REVIEW) ||
                                it.route == ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS)
                    }?.associateBy { it.key.slotId }.orEmpty()
                    val loadStepSlots = basicSlots.filterValues { assignment ->
                        assignment.route == ProgramProgressionRoute.E1RM_COACH_REVIEW ||
                            (assignment.route == ProgramProgressionRoute.BASIC_WEIGHTED &&
                                assignment.basicStrategy != BasicProgressionStrategy.REP_TARGET_PROGRESSION)
                    }.keys
                    if(state.reviewedCoachInputs!=null &&
                        (state.progressionCoachingEnabled || loadStepSlots.isNotEmpty()) &&
                        !calibrationLocked) {
                        OutlinedTextField(value=coachMaxStep,enabled=!busy,
                            onValueChange={coachMaxStep=it;dayCoachingVisible=false;basicProgressionVisible=false},
                            label={Text("Maximum load increase · gym units")},
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                            isError=maxStep==null,singleLine=true,
                            modifier=Modifier.fillMaxWidth())
                        if(state.progressionCoachingEnabled) {
                            if(state.fatigueCoachingEnabled) {
                                CycleChoiceRow("How are you feeling?",CoachReadiness.entries.toList(),
                                    coachReadiness,{pretty(it)}) {
                                        if(!busy){coachReadiness=it;dayCoachingVisible=false}
                                    }
                            }
                            Row {
                                Checkbox(checked=coachRestricted,enabled=!busy,
                                    onCheckedChange={coachRestricted=it;dayCoachingVisible=false})
                                Text("Movement is painful or restricted")
                            }
                        }
                    }
                    val supersetOrder=session.slots.mapNotNull{it.supersetGroup}.distinct()
                    session.slots.forEach { slot ->
                        HorizontalDivider()
                        Text(kotlinExerciseLabel(slot.exerciseId),
                            style=MaterialTheme.typography.titleSmall)
                        Text(pretty(slot.role) +
                            (slot.attachmentId?.let{" · "+it.replace('_',' ').replace('-',' ')} ?: "") +
                            (slot.supersetGroup?.let{" · Superset ${supersetOrder.indexOf(it)+1}"} ?: "") +
                            (if(slot.pinned)" · Pinned" else ""),
                            style=MaterialTheme.typography.bodySmall)
                        if(showProgramMetadata)Text("Slot ${slot.slotId}",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(slot.coachNote.isNotBlank())
                            Text(slot.coachNote,
                                style=MaterialTheme.typography.bodySmall)
                        slot.sets.forEach { set ->
                            val target=set.prescription
                            val load=kotlinIssuedLoadLabel(target.load)
                            Text("Set ${set.ordinalInSlot} · ${pretty(target.type)} · $load · ${target.repTargetLabel()} · RIR ${target.targetRir?.toString() ?: "not set"}",
                                style=MaterialTheme.typography.bodyMedium)
                            Text("${target.activeSeconds}s work · ${target.restSeconds}s rest",
                                style=MaterialTheme.typography.bodySmall)
                            target.amplifier?.let { amp ->
                                key(set.setKey) {
                                    NativeKotlinSetTechniqueSummary(
                                        amp=amp,ordinal=set.ordinalInSlot,setKey=set.setKey)
                                }
                            }
                        }
                        if(state.reviewedCoachInputs!=null && state.progressionCoachingEnabled && !calibrationLocked) {
                            OutlinedButton(enabled=!busy && maxStep!=null &&
                                session.stamp.cycleRevision==draft.revision,
                                onClick={
                                    dayCoachingVisible=true
                                    controller.reviewAuthoredDayCoaching(session.absoluteWeek,
                                        session.weekday,slot.slotId,coachReadiness,
                                        coachRestricted,checkNotNull(maxStep))
                                }){Text("Review next-session reps and weights")}
                        }
                        if(state.reviewedCoachInputs!=null && signed &&
                            slot.slotId in basicSlots && !calibrationLocked &&
                            !session.prescriptionEditedForTime &&
                            session.shortSessionLimitMinutes==null &&
                            state.preparedWorkout==null) {
                            val basicStep = if (slot.slotId in loadStepSlots) maxStep else BigDecimal.ZERO
                            OutlinedButton(
                                enabled=!busy && basicStep!=null &&
                                    session.stamp.cycleRevision==draft.revision,
                                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                                onClick={
                                    basicProgressionVisible=true
                                    controller.reviewAuthoredDayBasicProgression(
                                        session.absoluteWeek,session.weekday,slot.slotId,
                                        checkNotNull(basicStep))
                                }
                            ) {Text(if(basicSlots[slot.slotId]?.route==ProgramProgressionRoute.E1RM_COACH_REVIEW)
                                "Review e1RM progression" else "Preview basic progression")}
                            state.basicDayProgression?.takeIf{result->
                                basicProgressionVisible &&
                                state.basicDayProgressionSlotId==slot.slotId &&
                                result.next.copy(
                                    declaredNonSetSeconds=session.declaredNonSetSeconds)==session &&
                                result.next.stamp.cycleRevision==draft.revision &&
                                result.next.stamp.gymCatalogSourceRevision==state.sourceRevision
                            }?.let { reviewed ->
                                NativeKotlinBasicProgressionPreview(reviewed)
                                // Only a genuine, current and exact-source
                                // recommendation may open the week editor.
                                // The editor still requires a separate
                                // Use reviewed targets tap, full-cycle
                                // comparison and renewed program consent.
                                val matchingNext = currentSelection is
                                    NativeCustomNextCycleSlot.Next &&
                                    currentSelection.absoluteWeek==session.absoluteWeek &&
                                    currentSelection.weekday==session.weekday
                                val changedWorkingSets = if(matchingNext &&
                                    NativeKotlinBasicProgressionPreviewShape.canRender(
                                        reviewed.next.slots,reviewed.projection))
                                    CustomBasicProgressionDraftDomain.forWeekEdit(
                                        slot.slotId,slot.sets.map {it.prescription},
                                        reviewed.projection)
                                else null
                                if(changedWorkingSets!=null) {
                                    OutlinedButton(
                                        enabled=!busy && signed &&
                                            state.preparedWorkout==null &&
                                            !calibrationLocked,
                                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                                        onClick={
                                            weekEditFromCoach=false
                                            weekExerciseEditor=Triple(
                                                session.absoluteWeek,
                                                session.weekday,slot.slotId)
                                        }
                                    ) {Text("Review suggested week changes")}
                                    Text("Opens the week editor without changing your program. Choose whether to copy the suggestion, compare the full cycle, then explicitly approve the new program.",
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedButton(enabled=!busy && session.stamp.cycleRevision==draft.revision,
                            onClick={
                                weekEditFromCoach=false
                                weekExerciseEditor=Triple(
                                    session.absoluteWeek,session.weekday,slot.slotId)
                            }) {
                            Text("Compare an edit for week ${session.absoluteWeek}")
                        }
                    }
                    state.dayCoaching?.takeIf {reviewed->
                        dayCoachingVisible && reviewed.nextWorkout?.let {next->
                            next.copy(declaredNonSetSeconds=session.declaredNonSetSeconds)==session
                        }==true && session.stamp.cycleRevision==draft.revision &&
                            session.stamp.gymCatalogSourceRevision==state.sourceRevision
                    }?.let {reviewed->
                        NativeKotlinCustomDayCoachingSummary(reviewed,session.absoluteWeek)
                        val recoverySignal=state.fatigueCoachingEnabled &&
                            reviewed.review.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_TREND_V3 &&
                            reviewed.review.status==CustomNextWorkoutCoachingStatus.HOLD_MARKED_SAME_DAY_CAPACITY_DROP
                        if(recoverySignal) {
                            Text("Recovery review recommended before increasing this movement's targets. The matched workouts show a performance decline, not a diagnosis of chronic fatigue.",
                                style=MaterialTheme.typography.bodyMedium,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            val finalTrainingWeek=draft.blocks.lastOrNull()?.purpose==CustomBlockPurpose.TRAINING &&
                                session.blockId==draft.blocks.lastOrNull()?.blockId &&
                                session.absoluteWeek==draft.blocks.sumOf{it.weeks}
                            if(finalTrainingWeek && permissionHost!=null &&
                                state.preparedWorkout==null && !calibrationLocked &&
                                state.coachingAudit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                                state.auditSourceRevision==state.sourceRevision) {
                                Text("You can review an additional recovery week after this final training week. This will not change the current session.",
                                    style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(enabled=!busy && !futureWeekBusy,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                                    onClick={
                                        showProgramEditor=false
                                        recoveryFocusRequested=true
                                    }) {Text("Review recovery week options")}
                            } else Text("Review rest, readiness and current session demands. No mid-cycle deload or load change has been automatically scheduled.",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        if(!recoverySignal && permissionHost!=null && maxStep!=null &&
                            reviewed.selectedSlotId!=null && policyReady && signed &&
                            state.preparedWorkout==null && !calibrationLocked) {
                            Text("Check whether this change also works across the complete cycle.",
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled=!busy&&!futureWeekBusy,onClick={
                                val host=permissionHost
                                val chosen=checkNotNull(reviewed.selectedSlotId)
                                val version=++futureWeekVersion
                                futureWeekBusy=true
                                futureWeekSlotId=chosen
                                futureWeekOption=null
                                futureWeekSavePreview=null
                                futureWeekSaveDecision=null
                                confirmFutureWeekSave=false
                                futureWeekScope.launch {
                                    val result=withContext(Dispatchers.IO) {
                                        runCatching {
                                            host.reviewOwnerVerifiedFutureWeekProgression(
                                                draft.cycleId,session.absoluteWeek,
                                                session.weekday,chosen,coachReadiness,
                                                coachRestricted,checkNotNull(maxStep),
                                                reviewed.review.repForecastPolicy)
                                        }.getOrElse {
                                            NativeFutureWeekProposalResult.Rejected(
                                                NativeFutureWeekProposalRefusal.STORAGE_UNAVAILABLE)
                                        }
                                    }
                                    if(version==futureWeekVersion) {
                                        futureWeekOption=result
                                        futureWeekBusy=false
                                    }
                                }
                            }) { Text(if(futureWeekBusy)"Checking full cycle…"
                                else "Check full-cycle progression") }
                            if(futureWeekSlotId==reviewed.selectedSlotId) {
                                when(val option=futureWeekOption) {
                                    is NativeFutureWeekProposalResult.Reviewed -> {
                                        Text(option.proposal.explanation,
                                            style=MaterialTheme.typography.bodySmall)
                                        val changed=option.proposal.candidate?.weekOverrides
                                            ?.get(session.blockId).orEmpty().singleOrNull{
                                                it.weekInBlock==session.weekInBlock &&
                                                it.weekday==session.weekday &&
                                                it.slotId==reviewed.selectedSlotId
                                            }
                                        changed?.let {edit->
                                            val original=session.slots.single { it.slotId==edit.slotId }
                                            original.sets.zip(edit.sets).forEach { (before,after) ->
                                                if(before.prescription!=after) {
                                                    Text("Set ${before.ordinalInSlot} · ${kotlinIssuedLoadLabel(after.load)} · ${after.repTargetLabel()}",
                                                        style=MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                        Text("This is a review. Nothing changes until you separately confirm a saved draft.",
                                            style=MaterialTheme.typography.bodySmall)
                                        if(option.proposal.status==
                                                ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW) {
                                            OutlinedButton(
                                                enabled=!busy&&!futureWeekBusy,
                                                onClick={
                                                    val host=permissionHost
                                                    val chosen=checkNotNull(reviewed.selectedSlotId)
                                                    val version=++futureWeekVersion
                                                    futureWeekBusy=true
                                                    futureWeekSavePreview=null
                                                    futureWeekSaveDecision=null
                                                    futureWeekScope.launch {
                                                        val fresh=withContext(Dispatchers.IO) {
                                                            runCatching{
                                                                host.previewOwnerVerifiedFutureWeekDraft(
                                                                    draft.cycleId,session.absoluteWeek,
                                                                    session.weekday,chosen,coachReadiness,
                                                                    coachRestricted,checkNotNull(maxStep),
                                                                    reviewed.review.repForecastPolicy)
                                                            }.getOrElse{
                                                                NativeFutureWeekDraftPreview.Rejected(
                                                                    NativeFutureWeekDraftRefusal.STORAGE_UNAVAILABLE)
                                                            }
                                                        }
                                                        if(version==futureWeekVersion) {
                                                            futureWeekSavePreview=fresh
                                                            futureWeekBusy=false
                                                        }
                                                    }
                                                }){Text("Review saving this change")}
                                        }
                                        when(val pending=futureWeekSavePreview) {
                                            is NativeFutureWeekDraftPreview.Ready -> {
                                                Text("Save only the reviewed future-week targets as a new program draft. Your previous program signoff becomes outdated; review and sign the revised cycle before training from it.",
                                                    style=MaterialTheme.typography.bodySmall)
                                                Button(enabled=!busy&&!futureWeekBusy,
                                                    onClick={confirmFutureWeekSave=true}) {
                                                    Text("Confirm draft change")
                                                }
                                                if(confirmFutureWeekSave) {
                                                    AlertDialog(
                                                        onDismissRequest={
                                                            confirmFutureWeekSave=false
                                                        },
                                                        title={Text("Save this future-week change?")},
                                                        text={Text("Only your next unissued week's reviewed targets will be changed. Completed workouts stay unchanged. The program must be signed again before a new workout can be issued.")},
                                                        confirmButton={
                                                            TextButton(onClick={
                                                                confirmFutureWeekSave=false
                                                                val chosen=checkNotNull(reviewed.selectedSlotId)
                                                                val version=++futureWeekVersion
                                                                futureWeekBusy=true
                                                                futureWeekSaveDecision=null
                                                                futureWeekScope.launch {
                                                                    val outcome=withContext(Dispatchers.IO) {
                                                                        runCatching {
                                                                            permissionHost.acceptOwnerVerifiedFutureWeekDraft(
                                                                                draft.cycleId,session.absoluteWeek,
                                                                                session.weekday,chosen,coachReadiness,
                                                                                coachRestricted,checkNotNull(maxStep),
                                                                                reviewed.review.repForecastPolicy,
                                                                                pending.fingerprint,true)
                                                                        }.getOrElse{
                                                                            NativeFutureWeekDraftDecision.Rejected(
                                                                                NativeFutureWeekDraftRefusal.STORAGE_UNAVAILABLE)
                                                                        }
                                                                    }
                                                                    if(version==futureWeekVersion) {
                                                                        futureWeekSaveDecision=outcome
                                                                        futureWeekBusy=false
                                                                        if(outcome is NativeFutureWeekDraftDecision.Saved)
                                                                            controller.reopenAfterConfirmedFutureWeekSave(
                                                                                draft.cycleId)
                                                                    }
                                                                }
                                                            }) {Text("Save unsigned draft")}
                                                        },
                                                        dismissButton={
                                                            TextButton(onClick={
                                                                confirmFutureWeekSave=false
                                                            }){Text("Cancel")}
                                                        }
                                                    )
                                                }
                                            }
                                            is NativeFutureWeekDraftPreview.Rejected ->
                                                Text("Cannot save this proposal: "+
                                                    pretty(pending.reason),
                                                    style=MaterialTheme.typography.bodySmall,
                                                    color=MaterialTheme.colorScheme.error)
                                            null -> {}
                                        }
                                        when(val decision=futureWeekSaveDecision) {
                                            is NativeFutureWeekDraftDecision.Saved ->
                                                Text("Updated draft saved. Review and sign the revised cycle before training.",
                                                    style=MaterialTheme.typography.bodySmall)
                                            is NativeFutureWeekDraftDecision.Rejected ->
                                                Text("Draft was not changed: "+
                                                    pretty(decision.reason),
                                                    color=MaterialTheme.colorScheme.error,
                                                    style=MaterialTheme.typography.bodySmall)
                                            null -> {}
                                        }
                                    }
                                    is NativeFutureWeekProposalResult.Rejected ->
                                        Text("Full-cycle review unavailable: "+
                                            pretty(option.reason),
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.error)
                                    null -> {}
                                }
                            }
                        }
                        val readyRepEdit=reviewed.selectedSlotId?.let {chosen->
                            (CustomNextRepAmendmentDomain.propose(
                                session,chosen,reviewed.review)
                                as? CustomNextRepAmendmentResult.Proposed)
                                ?.amendment
                        }
                        if(readyRepEdit!=null && state.preparedWorkout==null &&
                            reviewed.review.repForecastPolicy!=
                                CustomRepForecastPolicy.LEGACY_STEP_V1) {
                            Text("Suggestions are not automatically applied. To change this week's minimum reps, review an explicit week edit, then re-audit and approve the updated program.",
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled=!busy,
                                onClick={
                                    weekEditFromCoach=true
                                    weekExerciseEditor=Triple(
                                        session.absoluteWeek,session.weekday,
                                        checkNotNull(reviewed.selectedSlotId))
                                }) {
                                Text("Review suggested set targets")
                            }
                        }
                        reviewed.sameBlockLoadContinuity?.takeIf {
                            it.status==CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT &&
                                it.changesAuthoredLoads
                        }?.let {carry->
                            HorizontalDivider()
                            Text("Earlier approved weights differ from this week's plan",
                                style=MaterialTheme.typography.titleMedium)
                            Text("This is a history-verified, read-only repeat suggestion. " +
                                "It is not a progression increase or an issued workout. " +
                                "A separately audited and accepted workout change is still required.",
                                style=MaterialTheme.typography.bodySmall)
                            carry.sets.forEach {set->
                                Text("Set ${set.ordinal}: last issued ${set.lastIssuedLoad.toPlainString()} " +
                                    "→ suggested repeat ${set.proposedLoad.toPlainString()} " +
                                    "${set.unit.name.lowercase()} (authored ${set.authoredLoad.toPlainString()})",
                                    style=MaterialTheme.typography.bodyMedium)
                            }
                            Text(carry.explanation,
                                style=MaterialTheme.typography.bodySmall)
                        }
                        reviewed.reducedBaselineProgression?.let {earned->
                            if(earned.status==CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW) {
                                Text("Earned increase from your reduced baseline",
                                    style=MaterialTheme.typography.titleSmall)
                                earned.sets.forEach{set->
                                    Text("Set ${set.ordinal}: ${set.baselineLoad} → ${set.proposedLoad} ${set.unit.name.lowercase()}",
                                        style=MaterialTheme.typography.bodyMedium)
                                }
                                Text("Your two latest same-day workouts support the next equipment step. Review it before accepting.",
                                    style=MaterialTheme.typography.bodySmall)
                                if(state.calibrationReview==null&&state.calibrationReceipt==null&&state.preparedWorkout==null)
                                    Button(enabled=!busy&&maxStep!=null,onClick={
                                        controller.auditBlockCalibration(coachReadiness,coachRestricted,checkNotNull(maxStep),
                                            NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE)
                                    }){Text("Audit earned weight increase")}
                            }
                        }
                        if((reviewed.transitionStartingLoads?.changesAuthoredLoads==true ||
                            reviewed.sameBlockLoadContinuity?.let {
                                it.status==CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT &&
                                    it.changesAuthoredLoads
                            }==true) &&
                            state.calibrationReview==null && state.calibrationReceipt==null && state.preparedWorkout==null) {
                            Button(enabled=!busy&&maxStep!=null,onClick={
                                controller.auditBlockCalibration(coachReadiness,coachRestricted,checkNotNull(maxStep))
                            }){Text("Audit this workout change")}
                        }
                    }
                }
            }
            PursuitCard(highlighted = true) {
                Text("Audit your full cycle",
                    style = MaterialTheme.typography.titleLarge)
                Text("Review every scheduled week and every authored override against your actual gym and confirmed training constraints. Missing coach-authored dose and time policies are shown as blockers—not invented targets.",
                    style = MaterialTheme.typography.bodyMedium)
                policyDrafts.SaveableStateProvider(checkNotNull(policyKey)) {
                NativeKotlinCustomCoachPolicyEditor(
                    cycle=draft,
                    source=state.reviewedSourceSetup,
                    sourceRevision=state.sourceRevision,
                    busy=busy,
                    reviewedInputs=state.reviewedCoachInputs,
                    observedEvidence=state.authoredWorkloadEvidence?.takeIf{
                        it.auditedCycleRevision==draft.revision &&
                            state.evidenceSourceRevision==state.sourceRevision
                    },
                    onInspectEvidence={
                        controller.inspectAuthoredWorkloadEvidence()
                    },
                    onDraftChanged={controller.markPolicyInputsChanged()},
                    onDiscardDraft={controller.open(draft.cycleId)},
                    onReview={reviewedInputs->
                        controller.auditFullCycle(reviewedInputs)
                    })
                }
                OutlinedButton(
                    onClick={controller.auditFullCycle()},
                    enabled=!busy,
                    modifier=Modifier.fillMaxWidth()
                ) {Text("Diagnose missing policy requirements")}
                state.coachingAudit?.let { audit ->
                    val coachActions=remember(audit,state.coachingSuggestionsEnabled) {
                        if(state.coachingSuggestionsEnabled)
                            CustomCoachActionReviewDomain.review(audit)
                        else null
                    }
                    var showCoachChoices by remember(audit) {mutableStateOf(false)}
                    var showEveryFinding by remember(audit) {mutableStateOf(false)}
                    HorizontalDivider()
                    Text("Coach-guided program review",
                        style=MaterialTheme.typography.titleLarge)
                    Text("Based on the verified authored weeks and actual reviewed policy. No suggested path automatically changes sets, exercises, splits, or approved targets.",
                        style=MaterialTheme.typography.bodySmall)
                    when(val guidance=coachActions) {
                        null ->
                            Text("Coaching suggestions are off in your verified native settings. Full-cycle factual auditing and manual custom editing remain available.",
                                style=MaterialTheme.typography.bodySmall)
                        is CustomCoachActionReviewResult.Rejected ->
                            Text("Actionable coaching requires verified source, structure and training evidence: ${pretty(guidance.reason)}.",
                                color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.bodySmall)
                        is CustomCoachActionReviewResult.Reviewed -> {
                            val findings=guidance.review.findings
                            if(findings.isEmpty()) {
                                Text("No additional coaching findings. This review does not issue or authorize a workout.",
                                    style=MaterialTheme.typography.bodyMedium)
                            } else {
                                Text("${findings.size} verified findings and observations",
                                    style=MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick={showCoachChoices=!showCoachChoices},
                                    enabled=!busy) {
                                    Text(if(showCoachChoices)"Hide coaching choices"
                                        else "Review possible corrections")
                                }
                                if(showCoachChoices) {
                                    findings.take(if(showEveryFinding) findings.size else 12).forEach {finding->
                                        HorizontalDivider()
                                        val status=when(finding.status) {
                                            CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION ->
                                                "Confirmed policy violation"
                                            CustomCoachFindingStatus.COACH_POLICY_BLOCKER ->
                                                "Needs reviewed policy"
                                            CustomCoachFindingStatus.OBSERVED_CHANGE ->
                                                "Observed change — not a safety finding"
                                        }
                                        Text(status,style=MaterialTheme.typography.labelMedium,
                                            color=if(finding.status==CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(finding.title,style=MaterialTheme.typography.titleSmall)
                                        Text(finding.location,
                                            style=MaterialTheme.typography.labelSmall)
                                        Text(finding.explanation,
                                            style=MaterialTheme.typography.bodySmall)
                                        finding.comparison?.let{
                                            Text(it,style=MaterialTheme.typography.bodySmall)
                                        }
                                        finding.options.forEachIndexed{index,choice->
                                            Text("${index+1}. ${choice.title}",
                                                style=MaterialTheme.typography.labelMedium)
                                            Text(choice.explanation,
                                                style=MaterialTheme.typography.bodySmall)
                                        }
                                        finding.absoluteWeek?.let {week ->
                                            OutlinedButton(enabled=!busy,onClick={
                                                inspectedWeek=week.toString()
                                                val block=draft.blocks.firstOrNull {candidate->
                                                    candidate.blockId==finding.blockId
                                                }
                                                val weekday=block?.days?.firstOrNull()?.weekday
                                                if(weekday!=null) {
                                                    inspectedWeekday=weekday
                                                    controller.previewAuthoredDay(week,weekday)
                                                }
                                            }) {
                                                Text("Inspect week $week in the authored preview")
                                            }
                                        }
                                    }
                                    if(findings.size>12) {
                                        TextButton(enabled=!busy,
                                            onClick={showEveryFinding=!showEveryFinding}) {
                                            Text(if(showEveryFinding)
                                                "Show first 12 findings"
                                            else "Inspect ${findings.size-12} remaining findings")
                                        }
                                    }
                                    if(findings.size>=96)
                                        Text("The review is capped at 96 findings. Re-audit after resolving these constraints to inspect remaining issues.",
                                            style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Result · ${pretty(audit.status)}",
                        style = MaterialTheme.typography.titleMedium)
                    Text("${audit.weeks.size} weeks inspected · ${audit.issues.size} unresolved items · audited revision ${audit.auditedCycleRevision}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("This review never approves a program or issues a workout. An athlete sign-off, revision-bound coach policy and distinct native workout issuance are required.",
                        style = MaterialTheme.typography.bodySmall)
                    if (audit.issues.isEmpty()) {
                        Text("No issues found in the supplied policies, but this is still a review-only draft.",
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        audit.issues.take(18).forEach { issue ->
                            Text(pretty(issue.kind) + " · " + issue.location,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error)
                            Text(issue.explanation,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (audit.issues.size > 18)
                            Text("${audit.issues.size - 18} more issues. Resolve the displayed prerequisites and rerun the audit.",
                                style = MaterialTheme.typography.bodySmall)
                    }
                    if (audit.weeks.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Actual authored weekly workload",
                            style = MaterialTheme.typography.titleMedium)
                        audit.weeks.take(8).forEach { week ->
                            Text("Week ${week.absoluteWeek} · ${week.phase.name.lowercase().replace('_',' ')} · ${week.workingSets} working sets",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Known set/rest seconds: " +
                                week.knownSecondsByDay.entries.joinToString(" · ") {
                                    "${customWeekdays.getOrElse(it.key) { "Day" }} ${it.value}s"
                                },style = MaterialTheme.typography.bodySmall)
                        }
                        if (audit.weeks.size > 8)
                            Text("Showing the first eight weeks here; all ${audit.weeks.size} were audited.",
                                style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            PursuitCard {
                Text("Your exact-program consent",
                    style = MaterialTheme.typography.titleLarge)
                when(val consent=state.signoff) {
                    is NativeCustomSignoffRead.Found ->
                        Text(
                            if(consent.isCurrent)
                                "Athlete consent is current for this exact cycle, gym and reviewed coaching policy."
                            else "A previous athlete consent exists, but its content is not currently verified. Re-audit before relying on it.",
                            style = MaterialTheme.typography.bodyMedium)
                    is NativeCustomSignoffRead.Revoked ->
                        Text("Previous athlete consent revoked at receipt revision ${consent.recordRevision}.",
                            style = MaterialTheme.typography.bodyMedium)
                    NativeCustomSignoffRead.Missing, null ->
                        Text("No athlete consent saved for this custom cycle.",
                            style = MaterialTheme.typography.bodyMedium)
                    is NativeCustomSignoffRead.Rejected ->
                        Text("Native consent record could not be verified.",
                            color = MaterialTheme.colorScheme.error)
                }
                Text("A full coach-authored dose policy and actual session-time evidence are required first. Consent is not authorization to start a workout.",
                    style = MaterialTheme.typography.bodySmall)
                if(state.signoff is NativeCustomSignoffRead.Found)
                    OutlinedButton(onClick = { controller.revokeAthleteConsent() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Revoke my saved cycle consent")
                    }
            }
            PursuitCard {
                Text("Program management",
                    style = MaterialTheme.typography.titleMedium)
                Text("Changes to your program need a fresh review before you use them in a workout.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { deleteConfirm = true },
                    enabled = !busy) { Text("Delete this saved draft") }
            }
            }
        }
    }

    if(confirmConsent && draft!=null) {
        AlertDialog(
            onDismissRequest={if(!busy)confirmConsent=false},
            title={Text("Approve your program")},
            text={
                Text("Approve the reviewed exercises, sets and training days in ${draft.title}. Your current program and gym settings are checked again before saving. You will review each workout before starting it.")
            },
            confirmButton={
                Button(onClick={
                    confirmConsent=false
                    controller.confirmAthleteConsent(true)
                },enabled=!busy) {
                    Text("I approve this reviewed cycle")
                }
            },
            dismissButton={
                TextButton(onClick={confirmConsent=false},enabled=!busy) {
                    Text("Cancel")
                }
            }
        )
    }

    val current = draft
    if (current != null) {
        removeDayReview?.let{review->
            val stillCurrent=NativeKotlinPlanStructureRemoval.day(
                current,review.blockId,review.weekday)==review
            NativeKotlinPlanDayRemovalSheet(
                review,busy,stillCurrent,
                onConfirm={
                    if(stillCurrent && !busy)controller.removeReviewedDay(review)
                    removeDayReview=null
                },
                onDismiss={removeDayReview=null}
            )
        }
        removeBlockReview?.let{review->
            val stillCurrent=NativeKotlinPlanStructureRemoval.block(
                current,review.blockId)==review
            NativeKotlinPlanBlockRemovalSheet(
                review,busy,stillCurrent,
                onConfirm={
                    if(stillCurrent && !busy)controller.removeReviewedBlock(review)
                    removeBlockReview=null
                },
                onDismiss={removeBlockReview=null}
            )
        }
        state.weekEditReview?.let {review ->
            NativeKotlinCustomCycleWeekEditSheet(review,busy,
                onSave={controller.acceptWeekEdit()},
                onDismiss={controller.dismissWeekEditReview()})
        }
        weekExerciseEditor?.takeIf {state.weekEditReview==null}?.let { (absoluteWeek,weekday,slotId) ->
            val session=state.authoredPreview?.takeIf {
                it.absoluteWeek==absoluteWeek && it.weekday==weekday &&
                    it.stamp.cycleRevision==current.revision
            }
            val selected=session?.slots?.firstOrNull {it.slotId==slotId}
            val base=current.blocks.firstOrNull {it.blockId==session?.blockId}
                ?.days?.firstOrNull {it.weekday==weekday}
                ?.slots?.firstOrNull {it.slotId==slotId}
            if(session!=null && selected!=null && base!=null) {
                val amended=if(weekEditFromCoach) {
                    val reviewed=state.dayCoaching?.takeIf {
                        it.review.repForecastPolicy!=CustomRepForecastPolicy.LEGACY_STEP_V1 &&
                            it.selectedSlotId==slotId &&
                            it.review.status==CustomNextWorkoutCoachingStatus.BUILD_REPS &&
                            it.nextWorkout?.copy(
                                declaredNonSetSeconds=session.declaredNonSetSeconds)==session
                    }
                    reviewed?.let {
                        (CustomNextRepAmendmentDomain.propose(
                            session,slotId,it.review)
                            as? CustomNextRepAmendmentResult.Proposed)
                            ?.amendment?.amended
                    }
                } else null
                val initial=amended?.slots?.singleOrNull {
                    it.slotId==slotId
                }?.sets?.map{it.prescription}
                    ?:selected.sets.map{it.prescription}
                key(current.revision,absoluteWeek,weekday,slotId,weekEditFromCoach) {
                    var comparisonRequested by remember {mutableStateOf(false)}
                    NativeKotlinCustomExerciseSheet(busy=busy,
                        available=controller.availableExerciseChoices(),
                        existing=base.copy(sets=initial),
                        scopeLabel="Week $absoluteWeek only · ${customWeekdays[weekday]}"+
                            if(amended!=null)" · coach proposal for review" else "",
                        basicReference=state.basicDayProgression?.takeIf { result ->
                            !weekEditFromCoach && basicProgressionVisible &&
                                currentSelection is NativeCustomNextCycleSlot.Next &&
                                currentSelection.absoluteWeek==absoluteWeek && currentSelection.weekday==weekday &&
                                state.preparedWorkout==null && !calibrationLocked &&
                                state.basicDayProgressionSlotId==slotId &&
                                result.next.copy(declaredNonSetSeconds=session.declaredNonSetSeconds)==session &&
                                result.next.stamp.gymCatalogSourceRevision==state.sourceRevision &&
                                when(val projection=result.projection) {
                                    is CustomBasicHistoryResult.Reviewed ->
                                        projection.candidate.increasedOrdinals.isNotEmpty() ||
                                            projection.candidate.changedRepOrdinals.isNotEmpty()
                                    is CustomBasicHistoryResult.UnloadedReviewed ->
                                        projection.candidate.advancedOrdinals.isNotEmpty()
                                    else -> false
                                }
                        },
                        saveLabel="Compare full-cycle effects",
                        feedback=state.message?.takeIf {comparisonRequested && !busy},
                        onSave={_,sets,_ ->
                            comparisonRequested=true
                            controller.previewWeekSets(absoluteWeek,weekday,slotId,sets)
                        },
                        onRestore=if(current.weekOverrides[session.blockId].orEmpty().any {
                            it.weekInBlock==session.weekInBlock && it.weekday==weekday && it.slotId==slotId
                        }) ({
                            comparisonRequested=true
                            controller.previewWeekSets(absoluteWeek,weekday,slotId,null)
                        }) else null,
                        onDismiss={weekExerciseEditor=null})
                }
            }
        }
        supersetEditor?.let {request->
            if(request.cycleId==current.cycleId && request.athleteId==current.athleteId)
                NativeKotlinSupersetEditorSheet(request,current,
                    allowSupersets=state.reviewedSourceSetup?.reviewed?.allowSupersets==true,
                    sourceRevision=state.sourceRevision,busy=busy,message=state.message,
                    needsSourceReload=state.authoringRefusal==CustomAuthoringRefusal.SETUP_UNRESOLVED,
                    available=state.availableChoices,ideasEnabled=state.coachingSuggestionsEnabled,
                    onSave={controller.saveSuperset(request,it)},onDismiss={supersetEditor=null},
                    onReload={supersetEditor=null;controller.open(current.cycleId)})
        }
        swapExerciseEditor?.let { target ->
            val (blockId,weekday,slotId)=target
            val day=current.blocks.firstOrNull {it.blockId==blockId}
                ?.days?.firstOrNull {it.weekday==weekday}
            val source=day?.slots?.firstOrNull {it.slotId==slotId}
            if(source==null) {
                LaunchedEffect(target) {swapExerciseEditor=null}
            } else {
                key(target,current.cycleId) {
                    NativeKotlinCustomExerciseSheet(
                        busy=busy,
                        available=controller.availableExerciseChoices(),
                        existing=null,
                        replacing=source,
                        initialSearchQuery=swapSearchSuggestion.orEmpty(),
                        otherDayExerciseIds=day?.slots?.filter{
                            it.slotId!=slotId && it.sets.any{set->
                                set.type==DraftSetType.WORKING
                            }
                        }?.map{it.exerciseId},
                        // Only use the exact unchanged saved block. One
                        // week-specific override makes future frequency
                        // uncertain, so revert to the day-only ranking.
                        otherWeekdayExerciseIds=current.blocks
                            .singleOrNull{it.blockId==blockId}
                            ?.takeIf{block->
                                current.weekOverrides[blockId].orEmpty().isEmpty() &&
                                    block.days.map{it.weekday}.distinct().size==
                                        block.days.size
                            }?.days?.filter{it.weekday!=weekday}
                            ?.map{otherDay->
                                otherDay.slots.filter{slot->
                                    slot.sets.any{set->set.type==DraftSetType.WORKING}
                                }.map{it.exerciseId}
                            },
                        scopeLabel="Program day · ${customWeekdays[weekday]} · ${blockId}",
                        onSave={exerciseId,sets,note->
                            controller.swapReviewedExercise(
                                blockId,weekday,slotId,exerciseId,sets,note)
                            swapExerciseEditor=null
                            swapSearchSuggestion=null
                        },
                        onDismiss={
                            swapExerciseEditor=null
                            swapSearchSuggestion=null
                        }
                    )
                }
            }
        }

        exerciseEditor?.let { target ->
            val (blockId, weekday, slotId) = target
            val selectedSlot = current.blocks.firstOrNull {
                it.blockId == blockId
            }?.days?.firstOrNull {
                it.weekday == weekday
            }?.slots?.firstOrNull { it.slotId == slotId }
            if (slotId == null || selectedSlot != null) {
                key(target) {
                    NativeKotlinCustomExerciseSheet(
                        busy = busy,
                        available = controller.availableExerciseChoices(),
                        existing = selectedSlot,
                        onSave = { exerciseId, sets, note ->
                            if (slotId == null)
                                controller.addReviewedExercise(
                                    blockId, weekday, exerciseId, sets, note)
                            else
                                controller.editReviewedExerciseSets(
                                    blockId, weekday, slotId, sets)
                            exerciseEditor = null
                        },
                        onDismiss = { exerciseEditor = null }
                    )
                }
            }
        }

        if (editingCycle) {
            var editedTitle by remember(current.cycleId, editingCycle) {
                mutableStateOf(current.title)
            }
            var editedNote by remember(current.cycleId, editingCycle) {
                mutableStateOf(current.authorNote)
            }
            AlertDialog(onDismissRequest = { if (!busy) editingCycle = false },
                title = { Text("Program name and notes") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(editedTitle, { editedTitle = it },
                            label = { Text("Program name") })
                        OutlinedTextField(editedNote, { editedNote = it },
                            label = { Text("Your coaching instructions") },
                            minLines = 2)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        controller.editMetadata(editedTitle.trim(), editedNote)
                        editingCycle = false
                    }, enabled = !busy && editedTitle.isNotBlank()) {
                        Text("Save changes")
                    }
                },
                dismissButton = { TextButton(onClick = { editingCycle = false }) {
                    Text("Cancel")
                } })
        }

        addingDayFor?.let { blockId ->
            val block = current.blocks.firstOrNull { it.blockId == blockId }
            if (block != null) {
                var weekday by remember(blockId) {
                    mutableIntStateOf((0..6).firstOrNull { n ->
                        block.days.none { it.weekday == n }
                    } ?: 0)
                }
                var label by remember(blockId) { mutableStateOf("") }
                var purpose by remember(blockId) {
                    mutableStateOf(ComposerSessionPurpose.HYPERTROPHY)
                }
                AlertDialog(onDismissRequest = { if (!busy) addingDayFor = null },
                    title = { Text("Add training day") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CycleChoiceRow("Weekday", (0..6).toList(), weekday,
                                { customWeekdays[it] }) { weekday = it }
                            OutlinedTextField(label, { label = it },
                                label = { Text("Name your day") },
                                placeholder = { Text("Upper A / Arms") })
                            CycleChoiceRow("Session purpose",
                                ComposerSessionPurpose.entries.filter {
                                    it != ComposerSessionPurpose.UNSPECIFIED
                                }, purpose, ::pretty) { purpose = it }
                        }
                    },
                    confirmButton = { Button(onClick = {
                        controller.addDay(blockId, weekday, label.trim(), purpose)
                        addingDayFor = null
                    }, enabled = !busy && label.isNotBlank() &&
                        block.days.none { it.weekday == weekday }) {
                        Text("Save day")
                    } },
                    dismissButton = { TextButton(onClick = { addingDayFor = null }) {
                        Text("Cancel")
                    } })
            }
        }

        editingDay?.let { (blockId, weekday) ->
            val day = current.blocks.firstOrNull { it.blockId == blockId }
                ?.days?.firstOrNull { it.weekday == weekday }
            if (day != null) {
                var label by remember(blockId, weekday) { mutableStateOf(day.label) }
                var purpose by remember(blockId, weekday) { mutableStateOf(day.purpose) }
                AlertDialog(onDismissRequest = { if (!busy) editingDay = null },
                    title = { Text(customWeekdays[weekday]) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(label, { label = it },
                                label = { Text("Training day name") })
                            CycleChoiceRow("Purpose",
                                ComposerSessionPurpose.entries.filter {
                                    it != ComposerSessionPurpose.UNSPECIFIED
                                }, purpose, ::pretty) { purpose = it }
                            Text("Both fields save together. Existing exercise slots, week overrides and prescribed sets stay unchanged.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            controller.editDay(blockId, weekday, label.trim(), purpose)
                            editingDay = null
                        }, enabled = !busy && label.isNotBlank() &&
                            (label != day.label || purpose != day.purpose)) {
                            Text("Save day")
                        }
                    },
                    dismissButton = { TextButton(onClick = { editingDay = null }) {
                        Text("Close")
                    } })
            }
        }

        if (addingBlock || editingBlock != null) {
            val existing = current.blocks.firstOrNull { it.blockId == editingBlock }
            var blockName by remember(editingBlock, addingBlock) {
                mutableStateOf(existing?.label.orEmpty())
            }
            var phaseSelected by remember(editingBlock, addingBlock) {
                mutableStateOf(existing?.phase ?: TrainingPhase.HYPERTROPHY)
            }
            var purposeSelected by remember(editingBlock, addingBlock) {
                mutableStateOf(existing?.purpose ?: CustomBlockPurpose.TRAINING)
            }
            var numWeeks by remember(editingBlock, addingBlock) {
                mutableStateOf(existing?.weeks?.toString() ?: "4")
            }
            var customSplit by remember(editingBlock, addingBlock) {
                mutableStateOf(existing?.split?.let(::nativePlanSplitLabel).orEmpty())
            }
            AlertDialog(onDismissRequest = {
                if (!busy) { addingBlock = false; editingBlock = null }
            }, title = { Text(if (existing == null) "Add training block" else "Edit training block") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(blockName, { blockName = it },
                            label = { Text("Block name") })
                        CycleChoiceRow("Phase", TrainingPhase.entries,
                            phaseSelected, ::pretty) { phaseSelected = it }
                        CycleChoiceRow("Block purpose", CustomBlockPurpose.entries,
                            purposeSelected, ::pretty) { purposeSelected = it }
                        OutlinedTextField(numWeeks, { numWeeks = it },
                            label = { Text("Weeks") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number))
                        OutlinedTextField(customSplit, { customSplit = it },
                            label = { Text("Custom split") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (existing != null) controller.editBlock(
                            existing.blockId, blockName.trim(), phaseSelected,
                            purposeSelected, numWeeks.toIntOrNull() ?: 0,
                            customSplit.trim())
                        else controller.addBlock(blockName.trim(),
                            phaseSelected, numWeeks.toIntOrNull() ?: 0,
                            customSplit.trim())
                        editingBlock = null
                        addingBlock = false
                    }, enabled = !busy && blockName.isNotBlank() &&
                        customSplit.isNotBlank() &&
                        (numWeeks.toIntOrNull() in 1..52)) { Text("Save block") }
                },
                dismissButton = { TextButton(onClick = {
                    editingBlock = null; addingBlock = false
                }) { Text("Cancel") } })
        }

        crossDayMoveScope?.let {scope->
            // Destinations are derived from the exact CURRENT draft. An
            // old request may remain visible for explanation, but can never
            // apply after a revision, owner or program change.
            val matching=current.athleteId==scope.athleteId &&
                current.cycleId==scope.cycleId &&
                current.revision==scope.cycleRevision
            val sourceBlock=current.blocks.singleOrNull{
                it.blockId==scope.blockId
            }
            val sourceDay=sourceBlock?.days?.singleOrNull{
                it.weekday==scope.fromWeekday
            }
            var destinationWeekday by remember(scope) {mutableIntStateOf(-1)}
            val preview=if(matching && destinationWeekday in 0..6)
                NativeKotlinPlanCrossDayMove.inspect(
                    current,scope.blockId,scope.fromWeekday,
                    scope.selectedSlotId,destinationWeekday)
            else null
            NativeEditSheet(
                busy=busy,
                onDismiss={crossDayMoveScope=null},
                footer={
                    Button(
                        onClick={
                            preview?.let{controller.moveExerciseAcrossDays(it)}
                            crossDayMoveScope=null
                        },
                        enabled=!busy && preview!=null,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Confirm move to selected day")}
                    TextButton(
                        onClick={crossDayMoveScope=null},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) {Text("Keep original training day")}
                }
            ) {
                Text("Move exercise to another day",
                    style=MaterialTheme.typography.titleLarge)
                if(!matching || sourceBlock==null || sourceDay==null) {
                    Text("This program or exercise changed. Close and reopen "+
                        "the move request for the latest saved draft.",
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.error)
                } else {
                    Text("From ${sourceDay.label} · ${sourceBlock.label}. "+
                        "Choose the destination within this block; nothing "+
                        "moves until you confirm.",
                        style=MaterialTheme.typography.bodyMedium)
                    val destinations=sourceBlock.days.filter{
                        it.weekday!=scope.fromWeekday
                    }.sortedBy{it.weekday}
                    if(destinations.isEmpty())Text(
                        "Add another training day to this block before moving exercises.",
                        style=MaterialTheme.typography.bodyMedium)
                    destinations.forEach {day->
                        val option=NativeKotlinPlanCrossDayMove.inspect(
                            current,scope.blockId,scope.fromWeekday,
                            scope.selectedSlotId,day.weekday)
                        FilterChip(
                            selected=destinationWeekday==day.weekday,
                            onClick={destinationWeekday=day.weekday},
                            enabled=!busy && option!=null,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                            label={Text("${customWeekdays.getOrElse(day.weekday){"Day"}} · "+
                                day.label+(if(option==null)" · unavailable" else ""))}
                        )
                    }
                    if(preview!=null) {
                        HorizontalDivider()
                        PursuitSectionHeading("Exact movement review",
                            "${preview.fromDayLabel} → ${preview.toDayLabel}")
                        Text(preview.movedExerciseIds.joinToString(" / "){
                            kotlinExerciseLabel(it)
                        },style=MaterialTheme.typography.titleMedium)
                        Text(
                            "Move ${preview.movedSlotIds.size} exact exercise slot(s), "+
                                "${preview.baseWorkingSets} base working sets and "+
                                "${preview.baseWarmupSets} warm-ups to the END of the "+
                                "selected day in this block. Pinned slots: "+
                                "${preview.pinnedCount}.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(preview.supersetGroup!=null)Text(
                            "The entire linked superset moves together, "+
                                "with its original round members and order.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(preview.overrideCount>0)Text(
                            "${preview.overrideCount} existing week-specific "+
                                "prescription(s) follow these same slots to "+
                                "week(s) ${preview.overrideWeeks.joinToString(", ")}. "+
                                "Their ${preview.overrideWorkingSets} working "+
                                "sets are preserved, not reset.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(preview.remainingSourceSlots==0)Text(
                            "The original training day will be empty until "+
                                "you add another exercise.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                        Text(
                            "Other days, source loads, exercise IDs, notes, "+
                                "issued sessions and completed History remain "+
                                "untouched. The new draft requires full review "+
                                "and consent; Undo is available after saving.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        removeExerciseReview?.let { review ->
            // An owner, program, revision, day or slot change invalidates
            // an older confirmation. The writer independently repeats this
            // check against its CURRENT durable candidate before Save.
            val stillCurrent=NativeKotlinPlanExerciseRemoval.inspect(
                current,review.blockId,review.weekday,review.slotId)==review
            AlertDialog(
                onDismissRequest={if(!busy)removeExerciseReview=null},
                title={Text("Remove exercise from program?")},
                text={
                    Column(
                        Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
                    ) {
                        Text(kotlinExerciseLabel(review.exerciseId),
                            style=MaterialTheme.typography.titleMedium)
                        Text("${review.dayLabel} · ${review.blockLabel} · ${review.blockWeeks} weeks",
                            style=MaterialTheme.typography.bodyMedium)
                        Text(
                            "Removes ${review.baseWorkingSets} base working set(s) and "+
                                "${review.baseWarmupSets} warm-up set(s) from this day "+
                                "in every week of the block.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(review.overriddenWeeks.isNotEmpty())Text(
                            "Also discards ${review.overriddenWeeks.size} authored week-specific "+
                                "prescription(s) for week(s) "+
                                review.overriddenWeeks.joinToString(", ") +
                                " (${review.overrideWorkingSets} exception working sets).",
                            style=MaterialTheme.typography.bodyMedium,
                            color=MaterialTheme.colorScheme.error)
                        if(review.pinned)Text(
                            "This is a pinned exercise. Removing it is an explicit "+
                                "change to your authored exercise selection.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(review.remainingExercises==0)Text(
                            "This day will have no exercises. Add a movement before "+
                                "requesting a new workout.",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodyMedium)
                        Text(
                            "Only this draft slot and its own future-week overrides "+
                                "will be removed. Other exercises, issued workouts, "+
                                "saved actuals and completed History stay unchanged. "+
                                "The edited cycle needs a new review and approval. "+
                                "You can Undo immediately after a successful save.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!stillCurrent)Text(
                            "This program changed. Close and open Remove again "+
                                "to review the latest saved prescription.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton={
                    Button(
                        onClick={
                            if(stillCurrent) {
                                controller.removeExercise(review)
                                removeExerciseReview=null
                            }
                        },
                        enabled=!busy && stillCurrent
                    ) {Text(if(review.pinned)"Remove pinned exercise"
                        else "Remove exercise")}
                },
                dismissButton={
                    TextButton(onClick={removeExerciseReview=null},enabled=!busy) {
                        Text("Keep exercise")
                    }
                }
            )
        }

        if (deleteConfirm) AlertDialog(
            onDismissRequest = { if (!busy) deleteConfirm = false },
            title = { Text("Delete this draft?") },
            text = { Text("This removes the saved custom draft only. Historical workouts and any separately approved program remain unchanged. Stale autosaves cannot restore it.") },
            confirmButton = {
                Button(onClick = {
                    controller.delete(); deleteConfirm = false
                }, enabled = !busy) { Text("Delete draft") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) {
                Text("Keep draft")
            } })
    }
}


/** Explicit athlete choices; the host derives reference kind and verifies
 * owner, signed ordinary base, physical stock and every selected week. */
@Composable
private fun NativeFixedNamedSelectionPanel(draft:CustomTrainingCycle,
    state:NativeKotlinCustomCycleState,controller:NativeKotlinCustomCycleController) {
    var expanded by remember(draft.cycleId,draft.revision){mutableStateOf(false)}
    var blockId by remember(draft.cycleId,draft.revision){mutableStateOf(draft.blocks.firstOrNull()?.blockId.orEmpty())}
    val block=draft.blocks.firstOrNull{it.blockId==blockId}
    var weekday by remember(blockId){mutableStateOf(block?.days?.firstOrNull()?.weekday ?: -1)}
    val day=block?.days?.firstOrNull{it.weekday==weekday}
    var slotId by remember(blockId,weekday){mutableStateOf(day?.slots?.firstOrNull()?.slotId.orEmpty())}
    val slot=day?.slots?.firstOrNull{it.slotId==slotId}
    var scheme by remember(draft.cycleId,draft.revision){mutableStateOf(NamedPercentageScheme.FIVE_THREE_ONE)}
    var tier by remember{mutableStateOf(PercentageTier.PRIMARY)}
    var role by remember{mutableStateOf(PercentageDayRole.TEXAS_VOLUME)}
    var from by remember(blockId){mutableStateOf("1")}
    var through by remember(blockId){mutableStateOf(block?.weeks?.toString().orEmpty())}
    var reference by remember(blockId,weekday,slotId,scheme){mutableStateOf("")}
    var maximumIncrease by remember(blockId,weekday,slotId){mutableStateOf("")}
    var choices by remember(draft.cycleId,draft.revision){mutableStateOf(emptyList<NativeFixedNamedChoice>())}
    var tierChoices by remember(draft.cycleId,draft.revision){mutableStateOf(emptyList<NativeTieredInitialChoice>())}
    val busy=state.busy
    PursuitCard {
        TextButton(onClick={expanded=!expanded},enabled=!busy){
            Text(if(expanded)"Close specialist progression" else "Choose specialist progression")
        }
        if(expanded && block!=null && day!=null && slot!=null) {
            Text("Review a named recipe for selected exercise slots and weeks. Add all intended choices before saving the revised program.",
                style=MaterialTheme.typography.bodySmall)
            CycleChoiceRow("Block",draft.blocks.map{it.blockId},blockId,
                {id->draft.blocks.first{it.blockId==id}.label}){blockId=it}
            CycleChoiceRow("Training day",block.days.map{it.weekday},weekday,
                {d->block.days.first{it.weekday==d}.label}){weekday=it}
            CycleChoiceRow("Exercise",day.slots.map{it.slotId},slotId,
                {id->kotlinExerciseLabel(day.slots.first{it.slotId==id}.exerciseId)}){slotId=it}
            if(choices.isEmpty() && tierChoices.isEmpty())
                CycleChoiceRow("Protocol",NamedPercentageScheme.entries,scheme,::pretty){scheme=it}
            else Text("Protocol: ${pretty(scheme)} · Remove selected references to change",
                style=MaterialTheme.typography.labelLarge)
            CycleChoiceRow("Tier",PercentageTier.entries,tier,::pretty){tier=it}
            if(scheme==NamedPercentageScheme.TEXAS)
                CycleChoiceRow("Texas day",PercentageDayRole.entries.filter{it!=PercentageDayRole.STANDARD},
                    role,::pretty){role=it}
            val unit=(slot.sets.firstOrNull{it.type==DraftSetType.WORKING}?.load as? DraftSetLoad.Verified)?.unit
            OutlinedTextField(reference,{reference=it},singleLine=true,
                label={Text((if(scheme==NamedPercentageScheme.GZCLP)"Initial working load" else pretty(NamedPercentageProtocolDomain.referenceKind(scheme)))+" · "+(unit?.name?.lowercase()?:"unit unavailable"))},
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                modifier=Modifier.fillMaxWidth())
            if(scheme==NamedPercentageScheme.GZCLP) {
                Text("Add a separate reference for each GZCLP exercise and training day. Slots on the same day share one workout. Review all targets before saving.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(from,{from=it},singleLine=true,label={Text("Initial block week")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(maximumIncrease,{maximumIncrease=it},singleLine=true,
                    label={Text("Maximum authorized increase · "+(unit?.name?.lowercase()?:"unit unavailable"))},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                val week=from.toIntOrNull()
                val load=reference.toBigDecimalOrNull()
                val increase=maximumIncrease.toBigDecimalOrNull()
                val blockOffset=draft.blocks.takeWhile{it.blockId!=blockId}.sumOf{it.weeks}
                val absoluteWeek=blockOffset+(week?:0)
                val choice=if(week!=null && week in 1..block.weeks && load!=null && load.signum()>0 &&
                    increase!=null && increase.signum()>0 && unit!=null)
                    NativeTieredInitialChoice(absoluteWeek,weekday,slotId,tier,load,increase)
                else null
                val alreadyAdded=tierChoices.any{it.absoluteWeek in (blockOffset+1)..(blockOffset+block.weeks) &&
                    it.weekday==weekday && it.slotId==slotId}
                Button(onClick={
                    choice?.let{tierChoices=tierChoices+it}
                    reference=""
                    maximumIncrease=""
                },enabled=!busy && choice!=null && !alreadyAdded && tierChoices.size<104,
                    modifier=Modifier.fillMaxWidth()){Text("Add tier reference")}
                if(alreadyAdded)Text("This slot already has an initial reference in this block. Remove it to change the reference.",
                    style=MaterialTheme.typography.bodySmall)
                Text("${tierChoices.size} selected tier references",style=MaterialTheme.typography.labelLarge)
                tierChoices.forEachIndexed{index,selected->
                    var offset=0
                    val selectedBlock=draft.blocks.firstOrNull{b->
                        val found=selected.absoluteWeek in (offset+1)..(offset+b.weeks)
                        offset+=b.weeks
                        found
                    }
                    val exercise=selectedBlock?.days?.firstOrNull{it.weekday==selected.weekday}
                        ?.slots?.firstOrNull{it.slotId==selected.slotId}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(exercise?.let{kotlinExerciseLabel(it.exerciseId)}?:selected.slotId,
                                style=MaterialTheme.typography.labelLarge)
                            Text("Week ${selected.absoluteWeek} · ${customWeekdays.getOrElse(selected.weekday){"Day"}} · ${pretty(selected.tier)}",
                                style=MaterialTheme.typography.bodySmall)
                            val selectedUnit=(exercise?.sets?.firstOrNull{it.type==DraftSetType.WORKING}?.load as? DraftSetLoad.Verified)?.unit
                            Text("${selected.initialLoad.toPlainString()} ${selectedUnit?.name.orEmpty()} · Increase limit ${selected.maximumAuthorizedIncrease.toPlainString()}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={tierChoices=tierChoices.filterIndexed{i,_->i!=index}},enabled=!busy){Text("Remove")}
                    }
                }
                Button(onClick={controller.reviewTieredInitialChoices(tierChoices)},
                    enabled=!busy && tierChoices.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                    Text("Review initial tier targets")
                }
                state.message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
                val shown=state.tieredReferenceBatch?.takeIf{review->
                    review.selections.map{NativeTieredInitialChoice.from(it)}==tierChoices
                }
                if(shown!=null) {
                    shown.individual.forEach{review->
                        val src=review.source
                        val selection=review.selection
                        Text("Week ${selection.absoluteWeek} · ${customWeekdays.getOrElse(selection.weekday){"Day"}} · ${kotlinExerciseLabel(review.request.scope.exerciseId)}",
                            style=MaterialTheme.typography.labelLarge)
                        val edit=shown.draft.weekOverrides[src.blockId].orEmpty()
                            .singleOrNull{it.weekInBlock==src.weekInBlock && it.weekday==selection.weekday && it.slotId==selection.slotId}
                        edit?.sets?.forEach{set->
                            Text("${kotlinIssuedLoadLabel(set.load)} · ${set.repTargetLabel()}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick={controller.acceptTieredInitialChoices(shown)},enabled=!busy,
                        modifier=Modifier.fillMaxWidth()){Text("Confirm references and save unsigned revision")}
                }
            } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                OutlinedTextField(from,{from=it},singleLine=true,label={Text("Block week from")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
                OutlinedTextField(through,{through=it},singleLine=true,label={Text("Through")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))
            }
            val first=from.toIntOrNull()
            val last=through.toIntOrNull()
            val value=reference.toBigDecimalOrNull()
            val offset=draft.blocks.takeWhile{it.blockId!=blockId}.sumOf{it.weeks}
            val newChoices=if(first!=null && last!=null && first in 1..block.weeks &&
                last in first..block.weeks && value!=null && value.signum()>0 && unit!=null)
                (first..last).map{week->NativeFixedNamedChoice(offset+week,weekday,slotId,scheme,tier,
                    if(scheme==NamedPercentageScheme.TEXAS)role else PercentageDayRole.STANDARD,value)}
            else emptyList()
            Button(onClick={choices=choices+newChoices},enabled=!busy && newChoices.isNotEmpty() &&
                choices.size+newChoices.size<=104 &&
                newChoices.none{n->choices.any{it.absoluteWeek==n.absoluteWeek && it.weekday==n.weekday && it.slotId==n.slotId}},
                modifier=Modifier.fillMaxWidth()){Text("Add selected weeks")}
            Text("${choices.size} selected slot-weeks",style=MaterialTheme.typography.labelLarge)
            choices.forEachIndexed{index,choice->
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Week ${choice.absoluteWeek} · ${customWeekdays.getOrElse(choice.weekday){"Day"}} · ${pretty(choice.scheme)} · ${choice.reference.toPlainString()}",
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={choices=choices.filterIndexed{i,_->i!=index}},enabled=!busy){Text("Remove")}
                }
            }
            Button(onClick={controller.reviewFixedNamedChoices(choices)},
                enabled=!busy && choices.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Review specialist targets")}
            state.message?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
            val shown=state.fixedNamedBatch?.takeIf{review->
                review.selections.map{NativeFixedNamedChoice(it.absoluteWeek,it.weekday,it.slotId,
                    it.scheme,it.tier,it.dayRole,it.reference)}==choices
            }
            if(shown!=null) {
                shown.individual.forEach{review->
                    val src=review.source
                    Text("Week ${review.selection.absoluteWeek} · ${kotlinExerciseLabel(review.request.scope.exerciseId)}",
                        style=MaterialTheme.typography.labelLarge)
                    val edit=review.proposal.proposal.draft.weekOverrides[src.blockId].orEmpty()
                        .singleOrNull{it.weekInBlock==src.weekInBlock && it.weekday==src.weekday && it.slotId==src.slotId}
                    edit?.sets?.forEach{set->
                        Text("${kotlinIssuedLoadLabel(set.load)} · ${set.repTargetLabel()}",style=MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick={controller.acceptFixedNamedChoices(shown)},enabled=!busy,
                    modifier=Modifier.fillMaxWidth()){Text("Confirm references and save unsigned revision")}
            }
            }
        }
    }
}

/** Initial-tier approval is never an ordinary or fixed workout grant. */
@Composable
private fun NativeTieredInitialExecutionPanel(state:NativeKotlinCustomCycleState,
    controller:NativeKotlinCustomCycleController,onOpenIssuedWorkout:(String)->Unit) {
    val review=state.tieredInitialReview
    PursuitCard(highlighted=true) {
        Text(if(state.tieredCalendar!=null)"GZCLP · Next workout" else "GZCLP · Initial tier review",
            style=MaterialTheme.typography.titleLarge)
        state.message?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
        state.tieredReceipts.forEach {
            Text(kotlinExerciseLabel(it.exerciseId),style=MaterialTheme.typography.titleMedium)
            Text("Week ${it.selection.absoluteWeek} · ${pretty(it.selection.tier)}",
                style=MaterialTheme.typography.labelLarge)
            Text("Initial reference: ${it.selection.initialLoad.toPlainString()} ${it.unit} · ${kotlinLoadBasisLabel(it.basis)}",
                style=MaterialTheme.typography.bodyMedium)
            Text("Maximum authorized increase: ${it.selection.maximumAuthorizedIncrease.toPlainString()} ${it.unit}",
                style=MaterialTheme.typography.bodySmall)
        }
        when(review) {
            is io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedNamedReview.Ready -> {
                Text("Revised cycle · ${review.audit.weeks.size} weeks audited")
                if(state.tieredReceipts.isNotEmpty())
                    Button(onClick={controller.approveTieredInitialCycle(review)},
                        enabled=!state.busy,modifier=Modifier.fillMaxWidth()){
                        Text(if(state.tieredReceipts.size>1)"Approve all reviewed references" else "Approve reviewed initial tier")
                    }
            }
            is io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedNamedReview.Signed -> {
                Text("Initial tier approval verified.",style=MaterialTheme.typography.labelLarge)
                val next=state.nextCycleSlot
                when(next) {
                    is NativeCustomNextCycleSlot.ResumeIssued ->
                        Button(onClick={onOpenIssuedWorkout(next.workoutId)},enabled=!state.busy,
                            modifier=Modifier.fillMaxWidth()){Text("Resume initial workout")}
                    is NativeCustomNextCycleSlot.Next,is NativeCustomNextCycleSlot.Prepared -> {
                        val position=when(next) {
                            is NativeCustomNextCycleSlot.Next -> next.absoluteWeek to next.weekday
                            is NativeCustomNextCycleSlot.Prepared -> next.absoluteWeek to next.weekday
                            else -> null
                        }
                        if(state.tieredReceipts.any{it.selection.absoluteWeek==position?.first &&
                            it.selection.weekday==position?.second})
                            OutlinedButton(onClick=controller::previewTieredInitialWorkout,enabled=!state.busy,
                                modifier=Modifier.fillMaxWidth()){Text("Verify initial workout")}
                        else Text("The next scheduled day requires supporting-day or multi-thread transition integration.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    is NativeCustomNextCycleSlot.Rejected -> Text(next.reason,color=MaterialTheme.colorScheme.error)
                    else -> {}
                }
                state.tieredInitialPreview?.let{shown->
                    Text("Week ${shown.session.absoluteWeek} · ${shown.session.dayLabel}",
                        style=MaterialTheme.typography.titleMedium)
                    shown.session.slots.forEach{slot->
                        Text(kotlinExerciseLabel(slot.exerciseId),style=MaterialTheme.typography.labelLarge)
                        slot.sets.forEach{set->
                            Text("${kotlinIssuedLoadLabel(set.prescription.load)} · ${set.prescription.repTargetLabel()}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(state.tieredInitialPrepared!=null)
                        Button(onClick=controller::issueTieredInitialWorkout,enabled=!state.busy,
                            modifier=Modifier.fillMaxWidth()){Text("Issue this exact workout")}
                    else if(next is NativeCustomNextCycleSlot.Next)
                        Button(onClick=controller::prepareTieredInitialWorkout,enabled=!state.busy,
                            modifier=Modifier.fillMaxWidth()){Text("Prepare reviewed workout")}
                }
            }
            else -> {}
        }
        // Calendar discovery is separate from specialist workout approval.
        // In particular a recovered Prepared slot here is not an issue grant.
        when(val calendar=state.tieredCalendar) {
            is NativeCustomNextCycleSlot.Next ->
                Text("Next scheduled: Week ${calendar.absoluteWeek} · ${calendar.dayLabel}",
                    style=MaterialTheme.typography.bodyMedium)
            is NativeCustomNextCycleSlot.Prepared ->
                Text("Saved preparation: Week ${calendar.absoluteWeek} · ${calendar.dayLabel}",
                    style=MaterialTheme.typography.bodyMedium)
            is NativeCustomNextCycleSlot.ResumeIssued -> {
                Text("Unfinished workout: Week ${calendar.absoluteWeek} · ${calendar.dayLabel}",
                    style=MaterialTheme.typography.bodyMedium)
                Button(onClick={onOpenIssuedWorkout(calendar.workoutId)},enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth()){Text("Resume workout")}
            }
            NativeCustomNextCycleSlot.CycleComplete ->
                Text("All scheduled days have verified completion.",style=MaterialTheme.typography.bodyMedium)
            else -> {}
        }
        if(state.tieredSupportAvailable) {
            OutlinedButton(onClick=controller::previewTieredSupportWorkout,enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()){Text("Verify supporting workout")}
            state.tieredSupportPreview?.let{shown->
                Text("Week ${shown.session.absoluteWeek} · ${shown.session.dayLabel}",
                    style=MaterialTheme.typography.titleMedium)
                shown.session.slots.forEach{slot->
                    Text(kotlinExerciseLabel(slot.exerciseId),style=MaterialTheme.typography.labelLarge)
                    slot.sets.forEach{set->
                        Text("${kotlinIssuedLoadLabel(set.prescription.load)} · ${set.prescription.repTargetLabel()}",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(state.tieredSupportPrepared!=null)
                    Button(onClick=controller::issueTieredSupportWorkout,enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth()){Text("Issue this exact workout")}
                else if(state.tieredCalendar is NativeCustomNextCycleSlot.Next)
                    Button(onClick=controller::prepareTieredSupportWorkout,enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth()){Text("Prepare reviewed workout")}
            }
        } else if(state.tieredTransitionAvailable) {
            NativeTieredTransitionControls(state,controller)
        } else if(state.tieredCalendar is NativeCustomNextCycleSlot.Next ||
            state.tieredCalendar is NativeCustomNextCycleSlot.Prepared) {
            Text("This scheduled day is outside the approved tier block.",style=MaterialTheme.typography.bodySmall)
        }
        Text("Initial approval does not start a workout. Supporting-day targets remain authored; tier transitions require separate review.",
            style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=controller::refreshTieredInitialCycle,enabled=!state.busy,
            modifier=Modifier.fillMaxWidth()){Text("Refresh tier review")}
    }
}

/** Review the engine-derived tier targets before consent and all workout
 * targets before preparation. A recovery or reset is an explicit choice. */
@Composable
private fun NativeTieredTransitionControls(state:NativeKotlinCustomCycleState,
    controller:NativeKotlinCustomCycleController) {
    var recovery by remember(state.draft?.cycleId,state.tieredCalendar){mutableStateOf(false)}
    val review=state.tieredTransitionReview
    val candidate=review?.nextTierCandidate
    val multi=review?.nextMultiTierCandidate
    val multiThread=state.tieredReceipts.size>1
    val position=when(val slot=state.tieredCalendar) {
        is NativeCustomNextCycleSlot.Next -> slot.absoluteWeek to slot.weekday
        is NativeCustomNextCycleSlot.Prepared -> slot.absoluteWeek to slot.weekday
        else -> null
    }
    val due=state.tieredReceipts.filter{r->
        val block=state.draft?.blocks?.singleOrNull{it.blockId==r.blockId}
        position!=null && block!=null && position.second==r.selection.weekday &&
            position.first in (r.selection.absoluteWeek+1)..(r.selection.absoluteWeek+block.weeks-r.weekInBlock)
    }
    var recoverySlots by remember(state.draft?.cycleId,state.tieredCalendar){mutableStateOf(emptySet<String>())}
    var resetSlots by remember(multi){mutableStateOf(emptySet<String>())}
    var confirmedRecoverySlots by remember(multi){mutableStateOf(emptySet<String>())}
    val shown=state.tieredTransitionPreview
    val stale=state.staleTieredTransition
    var resetConfirmed by remember(candidate,stale){mutableStateOf(false)}
    var recoveryConfirmed by remember(candidate,stale){mutableStateOf(false)}
    if(shown==null) {
        if(multiThread) {
            due.forEach{root->
                val id=root.selection.slotId
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked=id in recoverySlots,onCheckedChange={
                        recoverySlots=if(it)recoverySlots+id else recoverySlots-id
                    },enabled=!state.busy)
                    Text("Recovery · "+kotlinExerciseLabel(root.exerciseId))
                }
            }
        } else Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked=recovery,onCheckedChange={recovery=it},enabled=!state.busy)
            Text("Review a recovery workout instead")
        }
        OutlinedButton(onClick={
            if(multiThread)controller.previewTieredTransition(false,recoverySlots)
            else controller.previewTieredTransition(recovery)
        },enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Review next tier workout")}
    }
    stale?.let{old->
        Text("Previous approved targets",style=MaterialTheme.typography.titleMedium)
        old.previousTargets.forEach{set->
            Text("${kotlinIssuedLoadLabel(set.load)} · ${set.repTargetLabel()}",
                style=MaterialTheme.typography.bodySmall)
        }
        old.previousTargetsBySlot.forEach{(id,sets)->
            val root=state.tieredReceipts.firstOrNull{it.selection.slotId==id}
            Text(root?.let{kotlinExerciseLabel(it.exerciseId)}?:id,style=MaterialTheme.typography.labelLarge)
            sets.forEach{set->
                Text("${kotlinIssuedLoadLabel(set.load)} · ${set.repTargetLabel()}",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if(old.preparation!=null)
            Text("The old prepared workout cannot be issued after this history correction.",
                style=MaterialTheme.typography.bodyMedium)
        Text("Corrected targets",style=MaterialTheme.typography.titleMedium)
    }
    candidate?.let{next->
        Text("Week ${next.selection.absoluteWeek} · Tier stage ${next.result.state.stage+1}",
            style=MaterialTheme.typography.titleMedium)
        Text(if(next.selection.recovery)"Recovery exposure · no earned increase"
            else pretty(next.result.transitions.lastOrNull()?.decision ?: TieredLinearDecision.INITIAL),
            style=MaterialTheme.typography.bodyMedium)
        next.result.targets.forEach{target->
            Text("${target.load.toPlainString()} ${next.request.scope.unit} · ${target.minimumReps}${if(target.repOut)"+" else ""} reps",
                style=MaterialTheme.typography.bodySmall)
        }
        if(shown==null && stale?.preparation!=null) {
            OutlinedButton(onClick=controller::discardStaleTieredPreparation,enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()){Text("Discard unissued preparation")}
        } else if(shown==null) {
            if(next.requiresExplicitResetReview) {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked=resetConfirmed,onCheckedChange={resetConfirmed=it},enabled=!state.busy)
                    Text("Confirm the reviewed tier reset")
                }
            }
            if(next.selection.recovery) {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked=recoveryConfirmed,onCheckedChange={recoveryConfirmed=it},enabled=!state.busy)
                    Text("Confirm this recovery exposure")
                }
            }
            Button(onClick={controller.approveTieredTransition(resetConfirmed,recoveryConfirmed)},
                enabled=!state.busy && (!next.requiresExplicitResetReview || resetConfirmed) &&
                    (!next.selection.recovery || recoveryConfirmed),modifier=Modifier.fillMaxWidth()){
                Text(if(stale!=null)"Replace approval with corrected targets" else "Approve exact tier targets")
            }
        }
    }
    multi?.let{next->
        Text("Week ${next.selection.absoluteWeek} · ${next.snapshot.dayLabel}",style=MaterialTheme.typography.titleMedium)
        next.slots.forEach{slot->
            val id=slot.evidence.key.slotId
            Text(kotlinExerciseLabel(slot.evidence.request.scope.exerciseId)+" · Tier stage ${slot.result.state.stage+1}",
                style=MaterialTheme.typography.labelLarge)
            Text(if(slot.evidence.request.recovery)"Recovery exposure · no earned increase"
                else pretty(slot.result.transitions.lastOrNull()?.decision ?: TieredLinearDecision.INITIAL),
                style=MaterialTheme.typography.bodyMedium)
            slot.result.targets.forEach{target->
                Text("${target.load.toPlainString()} ${slot.evidence.request.scope.unit} · ${target.minimumReps}${if(target.repOut)"+" else ""} reps",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(shown==null && slot.requiresExplicitResetReview)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked=id in resetSlots,onCheckedChange={resetSlots=if(it)resetSlots+id else resetSlots-id},enabled=!state.busy)
                Text("Confirm this tier reset")
            }
            if(shown==null && slot.evidence.request.recovery)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked=id in confirmedRecoverySlots,onCheckedChange={
                    confirmedRecoverySlots=if(it)confirmedRecoverySlots+id else confirmedRecoverySlots-id
                },enabled=!state.busy)
                Text("Confirm this recovery exposure")
            }
        }
        if(shown==null && stale?.preparation!=null)
            OutlinedButton(onClick=controller::discardStaleTieredPreparation,enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()){Text("Discard unissued preparation")}
        else if(shown==null)Button(onClick={controller.approveTieredTransition(false,false,resetSlots,confirmedRecoverySlots)},
            enabled=!state.busy && resetSlots==next.slots.filter{it.requiresExplicitResetReview}.map{it.evidence.key.slotId}.toSet() &&
                confirmedRecoverySlots==next.selection.recoverySlotIds,modifier=Modifier.fillMaxWidth()){
            Text(if(stale!=null)"Replace approval with corrected targets" else "Approve exact tier targets")
        }
    }
    shown?.let{verified->
        Text("Approved workout · ${verified.session.dayLabel}",style=MaterialTheme.typography.titleMedium)
        verified.session.slots.forEach{slot->
            Text(kotlinExerciseLabel(slot.exerciseId),style=MaterialTheme.typography.labelLarge)
            slot.sets.forEach{set->
                Text("${kotlinIssuedLoadLabel(set.prescription.load)} · ${set.prescription.repTargetLabel()}",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        if(state.tieredTransitionPrepared!=null)
            Button(onClick=controller::issueTieredTransitionWorkout,enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()){Text("Issue this exact workout")}
        else if(state.tieredCalendar is NativeCustomNextCycleSlot.Next)
            Button(onClick=controller::prepareTieredTransitionWorkout,enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()){Text("Prepare reviewed workout")}
    }
}

/** Dedicated fixed-protocol consent and issuance surface. The ordinary
 * editor and issuer are not used for a saved specialist revision. */
@Composable
private fun NativeFixedNamedExecutionPanel(state:NativeKotlinCustomCycleState,
    controller:NativeKotlinCustomCycleController,onOpenIssuedWorkout:(String)->Unit) {
    val busy=state.busy
    val review=state.fixedNamedReview
    PursuitCard(highlighted=true) {
        Text("Specialist progression",style=MaterialTheme.typography.titleLarge)
        state.message?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
        when(review) {
            is io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedNamedReview.Ready -> {
                Text("Review revised program · ${review.audit.weeks.size} weeks audited")
                review.receipts.forEach{receipt->
                    Text("Week ${receipt.selection.absoluteWeek} · ${pretty(receipt.selection.scheme)} · ${receipt.selection.reference.toPlainString()}",
                        style=MaterialTheme.typography.bodySmall)
                }
                Button(onClick={controller.approveFixedNamedCycle(review)},enabled=!busy,
                    modifier=Modifier.fillMaxWidth()){Text("Approve this reviewed specialist cycle")}
            }
            is io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedNamedReview.Signed -> {
                val next=state.nextCycleSlot
                Text("Current signed specialist cycle",style=MaterialTheme.typography.labelLarge)
                when(next) {
                    is NativeCustomNextCycleSlot.ResumeIssued ->
                        Button(onClick={onOpenIssuedWorkout(next.workoutId)},enabled=!busy,
                            modifier=Modifier.fillMaxWidth()){Text("Resume issued workout")}
                    is NativeCustomNextCycleSlot.Next,is NativeCustomNextCycleSlot.Prepared ->
                        Button(onClick=controller::previewNextFixedNamedWorkout,enabled=!busy,
                            modifier=Modifier.fillMaxWidth()){Text("Verify next specialist workout")}
                    is NativeCustomNextCycleSlot.Rejected -> Text(next.reason,color=MaterialTheme.colorScheme.error)
                    NativeCustomNextCycleSlot.CycleComplete -> Text("All scheduled workouts completed.")
                    null -> {}
                }
                val shown=state.fixedNamedPreview
                if(shown!=null) {
                    Text("Week ${shown.session.absoluteWeek} · ${shown.session.dayLabel}",
                        style=MaterialTheme.typography.titleMedium)
                    shown.session.slots.forEach{slot->
                        Text(kotlinExerciseLabel(slot.exerciseId),style=MaterialTheme.typography.labelLarge)
                        slot.sets.forEach{set->
                            Text("${kotlinIssuedLoadLabel(set.prescription.load)} · ${set.prescription.repTargetLabel()}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(state.fixedNamedPrepared!=null)
                        Button(onClick=controller::issueNextFixedNamedWorkout,enabled=!busy,
                            modifier=Modifier.fillMaxWidth()){Text("Issue this exact workout")}
                    else if(next is NativeCustomNextCycleSlot.Next)
                        Button(onClick=controller::prepareNextFixedNamedWorkout,enabled=!busy,
                            modifier=Modifier.fillMaxWidth()){Text("Prepare reviewed workout")}
                }
                state.fixedNamedIssuedId?.let{id->
                    Button(onClick={onOpenIssuedWorkout(id)},enabled=!busy,
                        modifier=Modifier.fillMaxWidth()){Text("Open issued workout in Train")}
                }
                TextButton(onClick=controller::revokeFixedNamedCycle,enabled=!busy){
                    Text("Revoke specialist consent")
                }
            }
            else -> Text("Saved specialist approval is unavailable. No new workout can start.")
        }
        OutlinedButton(onClick=controller::refreshFixedNamedCycle,enabled=!busy,
            modifier=Modifier.fillMaxWidth()){Text("Refresh saved specialist review")}
    }
}
