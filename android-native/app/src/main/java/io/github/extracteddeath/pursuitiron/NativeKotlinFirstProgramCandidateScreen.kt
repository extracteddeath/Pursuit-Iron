package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Isolated Kotlin Lab first-program review. No legacy authoring, no inferred
 * weights, no preview-to-approval shortcut. Shares the existing Kotlin host.
 */
internal data class NativeFirstProgramState(
    val source:NativeCustomAuthoringSnapshot?=null,
    val busy:Boolean=false,
    val message:String?=null,
    val preview:FirstProgramStartReview?=null,
    val reviewedInput:FirstProgramStartRequest?=null,
    val canSaveDraft:Boolean=false,
    val savedProgramId:String?=null,
    val eligibleMovements:List<FirstProgramMovementOption> = emptyList(),
    val enteredLoads:Map<String,String> =emptyMap(),
    val selectedBodyweightIds:Set<String> =emptySet(),
    val phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
    val extraFocuses:List<ComposerFocus> =emptyList(),
    val setupMinutes:Int=15
)
internal class NativeKotlinFirstProgramController(
    private val host:NativeKotlinCandidateHost
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val pending=AtomicBoolean(false)
    private val current=MutableStateFlow(NativeFirstProgramState())
    val state=current.asStateFlow()

    private fun run(action:()->Unit) {
        if(!pending.compareAndSet(false,true))return
        current.value=current.value.copy(busy=true,message=null)
        scope.launch {
            try {action()}
            catch(_:Exception) {
                current.value=current.value.copy(
                    preview=null,
                    message="The first-program review failed. No program or workout was changed.")
            } finally {
                current.value=current.value.copy(busy=false)
                pending.set(false)
            }
        }
    }

    fun open()=run {
        when(val read=host.readCustomAuthoringSource()) {
            is NativeAuthoringSourceRead.Found -> {
                val before=current.value
                val source=read.source
                val phase=if(before.source?.athleteId==source.athleteId)
                    before.phase
                else when(source.setup.reviewed?.goal) {
                    AthleteTrainingGoal.STRENGTH->TrainingPhase.STRENGTH
                    else->TrainingPhase.HYPERTROPHY
                }
                val retained=before.source?.let {
                    it.athleteId==source.athleteId && it.revision==source.revision
                }==true
                val newState=before.copy(source=source,
                    phase=phase,
                    enteredLoads=if(retained)before.enteredLoads else emptyMap(),
                    selectedBodyweightIds=if(retained)before.selectedBodyweightIds
                        else emptySet(),
                    extraFocuses=if(retained)before.extraFocuses else emptyList(),
                    preview=null,reviewedInput=null,canSaveDraft=false,
                    savedProgramId=null,eligibleMovements=emptyList(),
                    message="Verified setup loaded. Choose your own initial working weights; equipment inventory is not a strength test.")
                val first=CoachFirstProgramStartDomain.review(
                    FirstProgramStartRequest(source.setup,source.catalog,
                        emptyMap(),emptySet(),phase))
                current.value=newState.copy(preview=first,
                    eligibleMovements=first.eligibleMovements)
            }
            else ->current.value=current.value.copy(
                source=null,preview=null,
                message="No active verified athlete and gym source. Complete or resume native setup first.")
        }
    }
    fun enterLoad(id:String,raw:String) {
        val s=current.value
        if(s.busy || s.source==null)return
        current.value=s.copy(
            enteredLoads=if(raw.isBlank())s.enteredLoads-id
                else s.enteredLoads+(id to raw),
            preview=null,reviewedInput=null,canSaveDraft=false,
            savedProgramId=null,message=null)
    }
    fun toggleBodyweight(id:String) {
        val s=current.value
        if(s.busy || s.source==null)return
        current.value=s.copy(
            selectedBodyweightIds=if(id in s.selectedBodyweightIds)
                s.selectedBodyweightIds-id else s.selectedBodyweightIds+id,
            preview=null,reviewedInput=null,canSaveDraft=false,
            savedProgramId=null,message=null)
    }
    fun choosePhase(value:TrainingPhase) {
        val s=current.value
        if(!s.busy)current.value=s.copy(phase=value,preview=null,
            reviewedInput=null,canSaveDraft=false,savedProgramId=null,message=null)
    }
    fun chooseSetupMinutes(value:Int) {
        val s=current.value
        if(!s.busy && value in setOf(10,15,20,30))
            current.value=s.copy(setupMinutes=value,preview=null,reviewedInput=null,
                canSaveDraft=false,savedProgramId=null,message=null)
    }
    fun addFocus(value:ComposerFocus) {
        val s=current.value
        if(!s.busy && s.extraFocuses.size<2)
            current.value=s.copy(extraFocuses=s.extraFocuses+value,
                preview=null,reviewedInput=null,canSaveDraft=false,
                savedProgramId=null,message=null)
    }
    fun removeLastFocus() {
        val s=current.value
        if(!s.busy && s.extraFocuses.isNotEmpty())
            current.value=s.copy(extraFocuses=s.extraFocuses.dropLast(1),
                preview=null,reviewedInput=null,canSaveDraft=false,
                savedProgramId=null,message=null)
    }
    fun preview()=run {
        val initial=current.value
        val source=initial.source ?:return@run
        val before=host.readCustomAuthoringSource()
        val saved=(before as? NativeAuthoringSourceRead.Found)?.source
        if(saved!=source) {
            current.value=initial.copy(source=null,preview=null,
                message="Your saved gym or profile changed. Reload before reviewing a new program.")
            return@run
        }
        val selected=LinkedHashMap<String,BigDecimal>()
        for((id,raw) in initial.enteredLoads) {
            val number=runCatching {BigDecimal(raw.trim())}.getOrNull()
            if(number==null) {
                current.value=initial.copy(preview=null,
                    message="Enter a valid number for ${id}; invalid weights cannot be used.")
                return@run
            }
            selected[id]=number
        }
        val input=FirstProgramStartRequest(
            source.setup,source.catalog,selected,
            initial.selectedBodyweightIds,initial.phase,
            initial.extraFocuses,nonSetSecondsPerDay=initial.setupMinutes*60)
        val result=CoachFirstProgramStartDomain.review(input)
        val conversion=if(result.status==
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)
            CoachFirstProgramDraftConversionDomain.propose(
                result,source.setup,source.catalog,
                "first-preview","My first program",4)
        else null
        val after=(host.readCustomAuthoringSource()
            as? NativeAuthoringSourceRead.Found)?.source
        if(after!=source) {
            current.value=initial.copy(source=null,preview=null,
                message="Your gym or program source changed during review. Nothing was saved.")
            return@run
        }
        current.value=initial.copy(preview=result,
            reviewedInput=input,
            canSaveDraft=conversion?.status==
                FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,
            message=when(result.status) {
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW ->
                    "An auditable first-program draft was generated. It is not saved or approved; review its muscle coverage, sets and intended effort."
                FirstProgramStartStatus.CALIBRATION_REQUIRED ->
                    "Choose and calibrate initial exercises first."
                else -> "Your choices need review before this draft can become a program."
            })
    }
    /** Explicit create-only save: the host recomputes the exact shown week. */
    fun saveReviewedDraft()=run {
        val before=current.value
        val source=before.source ?:return@run
        val input=before.reviewedInput ?:return@run
        val shown=before.preview ?:return@run
        if(!before.canSaveDraft || before.savedProgramId!=null)return@run
        val id="first-"+UUID.randomUUID().toString().replace("-","")
        val result=host.saveReviewedFirstProgramDraft(
            input,shown,source.revision,id,"My first program",4,true)
        if(result is NativeCustomDraftWriteResult.Committed &&
            host.readCustomCycleDraft(id) is NativeCustomDraftReadResult.Found) {
            current.value=before.copy(savedProgramId=id,canSaveDraft=false,
                message="Saved your first-program draft with its exact sets and weights. Open it from My program > Find my saved programs. It still needs full-cycle coaching audit and approval before training.")
        } else {
            current.value=before.copy(canSaveDraft=false,
                message="Saving the first program was refused or could not be verified. No workout was issued; reload your saved gym and preview again.")
        }
    }

    override fun close(){scope.cancel()}
}
private fun prettyFirstProgram(value:String)=value.lowercase().replace('_',' ')
    .replaceFirstChar{it.uppercase()}


/**
 * A real first-program selection surface: only explicit athlete calibrations
 * enter the Kotlin coach. Physical stock is consulted for immediate feedback,
 * never to guess a working weight or silently round a user's entry.
 */
@Composable
internal fun NativeFirstProgramCalibrationPanel(
    state:NativeFirstProgramState,
    onLoadChanged:(String,String)->Unit,
    onBodyweightChanged:(String)->Unit,
    onReview:()->Unit
) {
    val source=state.source
    val reviewed=source?.setup?.reviewed
    val movementById=remember(source?.revision) {
        source?.catalog?.associateBy{it.exerciseId}.orEmpty()
    }
    val selectedIds=state.enteredLoads.keys+state.selectedBodyweightIds
    val invalidIds=state.enteredLoads.filter{(id,raw)->
        val option=state.eligibleMovements.firstOrNull{it.exerciseId==id}
        val movement=movementById[id]
        val stock=movement?.equipmentId?.let{reviewed?.gym?.confirmedStock?.get(it)}
        val parsed=runCatching{BigDecimal(raw.trim())}.getOrNull()
        val inspection=if(option!=null && parsed!=null)
            EquipmentLoadDomain.inspect(stock,parsed,option.unit) else null
        parsed==null || parsed<=BigDecimal.ZERO ||
            inspection !is LoadInspection.Confirmed || !inspection.exact ||
            inspection.basis!=option?.basis
    }.keys
    var query by rememberSaveable(source?.athleteId,source?.revision) {
        mutableStateOf("")
    }
    var selectedOnly by rememberSaveable(source?.athleteId,source?.revision) {
        mutableStateOf(false)
    }
    var expandedCategory by rememberSaveable(source?.athleteId,source?.revision) {
        mutableStateOf<String?>(null)
    }
    val canChooseMore=selectedIds.size<32
    val canReview=!state.busy && source!=null &&
        selectedIds.size in 1..32 && invalidIds.isEmpty()
    val options=state.eligibleMovements.filter{option->
        (!selectedOnly || option.exerciseId in selectedIds) &&
            (query.isBlank() ||
                kotlinExerciseLabel(option.exerciseId).contains(query,true) ||
                option.exerciseId.contains(query,true) ||
                movementById[option.exerciseId]?.familyId
                    ?.replace('_',' ')?.contains(query,true)==true)
    }
    val grouped=options.groupBy{movementById[it.exerciseId]?.pattern?.name
        ?: "OTHER"}
    PursuitCard {
        PursuitSectionHeading(
            "Choose your exercises and starting loads",
            "Calibrate only movements you can perform. The Kotlin engine builds the week from these choices and the confirmed gym.")
        Text("${selectedIds.size} of 32 exercises chosen · ${state.eligibleMovements.size} available in this gym",
            style=MaterialTheme.typography.bodyMedium)
        if(invalidIds.isNotEmpty())
            Text("${invalidIds.size} selected weights need correction before review.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        Text("Use a weight you can realistically lift for the prescribed reps. A weight existing in your gym does not prove it is appropriate for you.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value=query,
            onValueChange={query=it.take(100)},
            singleLine=true,modifier=Modifier.fillMaxWidth(),
            label={Text("Search exercise name or pattern")})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            FilterChip(selected=!selectedOnly,onClick={selectedOnly=false},
                label={Text("All available")})
            FilterChip(selected=selectedOnly,onClick={selectedOnly=true},
                label={Text("My selected (${selectedIds.size})")})
        }
        Button(onClick=onReview,
            enabled=canReview,
            modifier=Modifier.fillMaxWidth()) {
            Text("Review my proposed first week")
        }
        if(selectedIds.isEmpty())
            Text("Choose a few exercises to begin. Include a push, a pull, lower-body movements and the accessories you need; the coach checks weekly coverage.",
                style=MaterialTheme.typography.bodyMedium)
        if(selectedIds.size>=32)
            Text("32 exercises is the current maximum for one first-program review. Remove one to choose another.",
                style=MaterialTheme.typography.bodySmall)
        if(grouped.isEmpty())
            Text(if(selectedOnly)"No exercises selected yet. Switch to All available to add some."
                else "No matching eligible exercises. Check the search or gym inventory.",
                style=MaterialTheme.typography.bodySmall)
        for((category,entries) in grouped) {
            val expanded=category==expandedCategory
            val displayed=if(expanded || query.isNotBlank() || selectedOnly)
                entries else {
                val first=entries.take(4)
                first+entries.drop(4).filter{it.exerciseId in selectedIds}
            }
            val totalSelected=entries.count{it.exerciseId in selectedIds}
            HorizontalDivider()
            Text(prettyFirstProgram(category)+
                " · ${totalSelected} selected / ${entries.size} available",
                style=MaterialTheme.typography.titleMedium)
            displayed.forEach{option->
                val id=option.exerciseId
                val movement=movementById[id]
                val stock=movement?.equipmentId?.let {
                    reviewed?.gym?.confirmedStock?.get(it)
                }
                val raw=state.enteredLoads[id].orEmpty()
                val typed=runCatching{BigDecimal(raw.trim())}.getOrNull()
                val inspection=if(typed!=null)
                    EquipmentLoadDomain.inspect(stock,typed,option.unit)
                    else null
                val exact=typed!=null && typed>BigDecimal.ZERO &&
                    inspection is LoadInspection.Confirmed &&
                    inspection.exact && inspection.basis==option.basis
                Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                    Text(kotlinExerciseLabel(id),
                        style=MaterialTheme.typography.titleSmall)
                    if(option.needsWeightCalibration) {
                        OutlinedTextField(
                            value=raw,
                            onValueChange={onLoadChanged(id,it)},
                            enabled=!state.busy && (id in selectedIds || canChooseMore),
                            singleLine=true,
                            label={Text("First working load (${option.unit.name.lowercase()}; ${prettyFirstProgram(option.basis?.name ?: "unknown basis")})")},
                            supportingText={
                                when {
                                    raw.isBlank() ->
                                        Text("Enter your real first-session load; do not assume the lightest weight.")
                                    exact ->
                                        Text("Exact verified gym load. This does not guarantee achievable reps.")
                                    typed==null ->
                                        Text("Enter a valid number.",color=MaterialTheme.colorScheme.error)
                                    typed<=BigDecimal.ZERO ->
                                        Text("Working load must be greater than zero.",
                                            color=MaterialTheme.colorScheme.error)
                                    inspection is LoadInspection.Confirmed -> {
                                        Text("This load is not in your verified equipment inventory.",
                                            color=MaterialTheme.colorScheme.error)
                                    }
                                    else -> Text("Equipment or units could not be verified.",
                                        color=MaterialTheme.colorScheme.error)
                                }
                            },
                            isError=raw.isNotBlank()&&!exact,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                            modifier=Modifier.fillMaxWidth())
                        if(raw.isNotBlank() && !exact &&
                            inspection is LoadInspection.Confirmed) {
                            val choices=listOfNotNull(
                                inspection.atOrBelow,inspection.nextAbove)
                                .distinct().filter{it>BigDecimal.ZERO}
                            choices.forEach{choice->
                                TextButton(
                                    enabled=!state.busy,
                                    onClick={onLoadChanged(id,choice.toPlainString())}
                                ) {
                                    Text("Use confirmed ${choice.toPlainString()} ${option.unit.name.lowercase()} instead")
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                            Checkbox(
                                checked=id in state.selectedBodyweightIds,
                                enabled=!state.busy && (id in selectedIds || canChooseMore),
                                onCheckedChange={onBodyweightChanged(id)})
                            Text("I can perform this bodyweight movement",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if(query.isBlank() && !selectedOnly && entries.size>displayed.size) {
                TextButton(onClick={expandedCategory=category}) {
                    Text("Show all ${entries.size} ${prettyFirstProgram(category).lowercase()} exercises")
                }
            }
            if(expanded && entries.size>4 && query.isBlank() && !selectedOnly)
                TextButton(onClick={expandedCategory=null}) {
                    Text("Show fewer ${prettyFirstProgram(category).lowercase()} exercises")
                }
        }
        if(grouped.isNotEmpty()) {
            Button(onClick=onReview,enabled=canReview,
                modifier=Modifier.fillMaxWidth()) {
                Text("Review my proposed first week")
            }
        }
    }
}

@Composable
internal fun NativeKotlinFirstProgramCandidateScreen(
    controller:NativeKotlinFirstProgramController,
    onOpenSavedProgram:(String,CustomCoachAuditInputs)->Unit={_,_->}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var confirmingSave by remember {mutableStateOf(false)}
    LaunchedEffect(controller) {controller.open()}
    PursuitScreenContent {
        PageHeading("YOUR PROGRAM",
            "Build your first program",
            "Review a training week from your saved choices and physical gym.")
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {
            Text(it,style=MaterialTheme.typography.bodySmall)
        }
        if(state.source==null) {
            PursuitCard {
                Text("Confirm your native profile and gym first.",
                    style=MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick=controller::open,
                    enabled=!state.busy) {Text("Reload saved setup")}
            }
        } else {
            val athlete=state.source?.setup?.reviewed
            PursuitCard(highlighted=true) {
                Text("Your training",style=MaterialTheme.typography.titleLarge)
                if(athlete!=null) {
                    Text("${prettyFirstProgram(athlete.goal.name)} · ${prettyFirstProgram(athlete.experience.name)} · ${prettyFirstProgram(athlete.split.name)} · ${athlete.trainingWeekdays.size} days/week",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("Time preference: ${athlete.sessionTimeBucket.minimumMinutes}–${athlete.sessionTimeBucket.preferredMaximumMinutes?.toString() ?: "open"} minutes. The session window is a preference, not an automatic hard cap.",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text("First phase",style=MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TrainingPhase.entries.forEach { phase ->
                        FilterChip(selected=state.phase==phase,
                            onClick={controller.choosePhase(phase)},
                            label={Text(prettyFirstProgram(phase.name))})
                    }
                }
                Text("General warm-up, setup and transitions per session",
                    style=MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(10,15,20,30).forEach { minutes ->
                        FilterChip(selected=state.setupMinutes==minutes,
                            onClick={controller.chooseSetupMinutes(minutes)},
                            label={Text("${minutes} min")})
                    }
                }
                Text("These are versioned starting assumptions. No personal MEV/MRV, fatigue tolerance, or achievable weight is inferred from experience alone.",
                    style=MaterialTheme.typography.bodySmall)
                Text("Specific lighter sets appear separately in the workout and its time estimate when your gym has a suitable weight.",
                    style=MaterialTheme.typography.bodySmall)
            }
            if(athlete!=null) {
                val extraChoices=when(athlete.split) {
                    ComposerSplit.UPPER_LOWER->listOf(ComposerFocus.UPPER,ComposerFocus.LOWER)
                    ComposerSplit.PUSH_PULL_LEGS->listOf(
                        ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
                    ComposerSplit.ULPPL->listOf(
                        ComposerFocus.UPPER,ComposerFocus.LOWER,
                        ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
                    ComposerSplit.FULL_BODY->emptyList()
                }
                val required=when(athlete.split) {
                    ComposerSplit.FULL_BODY->0
                    ComposerSplit.UPPER_LOWER->athlete.trainingWeekdays.size%2
                    ComposerSplit.PUSH_PULL_LEGS->athlete.trainingWeekdays.size%3
                    ComposerSplit.ULPPL->athlete.trainingWeekdays.size-5
                }
                if(required>0)PursuitCard {
                    Text("Extra split-day focus (${state.extraFocuses.size}/${required})",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Choose the additional day focus instead of assuming which muscle group needs more attention.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        extraChoices.forEach { focus ->
                            OutlinedButton(
                                enabled=!state.busy &&
                                    state.extraFocuses.size<required,
                                onClick={controller.addFocus(focus)}
                            ) {Text(prettyFirstProgram(focus.name))}
                        }
                    }
                    if(state.extraFocuses.isNotEmpty()) {
                        Text(state.extraFocuses.joinToString(" + "){prettyFirstProgram(it.name)})
                        TextButton(onClick=controller::removeLastFocus) {
                            Text("Undo last extra day")
                        }
                    }
                }
            }
            NativeFirstProgramCalibrationPanel(
                state=state,
                onLoadChanged=controller::enterLoad,
                onBodyweightChanged=controller::toggleBodyweight,
                onReview=controller::preview
            )
            state.preview?.let {review ->
                PursuitCard(highlighted=true) {
                    Text("First-program review",style=MaterialTheme.typography.titleLarge)
                    Text(prettyFirstProgram(review.status.name),
                        style=MaterialTheme.typography.titleMedium)
                    if(review.unsupportedMuscles.isNotEmpty()) {
                        Text("Direct muscle coverage not established for: "+
                            review.unsupportedMuscles.joinToString {
                                prettyFirstProgram(it.name)
                            },style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.error)
                    }
                    review.issues.take(12).forEach { issue ->
                        Text(issue,style=MaterialTheme.typography.bodySmall)
                    }
                    val scheduled=review.generation?.candidate?.catalogReview?.draft
                    if(scheduled!=null) {
                        Text("${scheduled.visits.size} exercise visits · ${scheduled.sessions.size} training days",
                            style=MaterialTheme.typography.bodyMedium)
                        scheduled.sessions.forEach {day ->
                            Text("Day ${day.dayIndex+1} · ${prettyFirstProgram(day.purpose.name)}",
                                style=MaterialTheme.typography.titleSmall)
                            day.orderedVisits.forEach { visit ->
                                Text("${kotlinExerciseLabel(visit.exerciseId)} · ${visit.sets} sets",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    review.prescription?.sessions?.forEach {session ->
                        Text("Day ${session.dayIndex+1} · estimated ${session.estimatedMinutes ?: "?"} min",
                            style=MaterialTheme.typography.titleSmall)
                        session.exercises.forEach {exercise ->
                            val working=exercise.sets.filter {
                                it.type==DraftSetType.WORKING
                            }
                            val target=working.firstOrNull()
                            val range=target?.targetReps?.let {
                                "${it.minimum}–${it.maximum}"
                            } ?: "unresolved"
                            Text("${kotlinExerciseLabel(exercise.exerciseId)}: ${working.size} × ${range} · goal RIR ${target?.targetRir ?: "?"}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(state.canSaveDraft) {
                        Button(onClick={confirmingSave=true},
                            enabled=!state.busy,
                            modifier=Modifier.fillMaxWidth()) {
                            Text("Save this first-program draft")
                        }
                    } else if(review.status==
                        FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW &&
                        state.savedProgramId==null) {
                        Text("A new draft still needs the native cycle-structure review before saving.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    state.savedProgramId?.let { id ->
                        val savedPolicy=state.source?.setup?.let{setup->
                            CoachFirstProgramDraftConversionDomain
                                .policyForInitialFullCycleReview(review,setup)
                        }
                        Text("First-program draft saved and verified.",
                            style=MaterialTheme.typography.bodyMedium)
                        if(savedPolicy!=null) {
                            Button(
                                onClick={onOpenSavedProgram(id,savedPolicy)},
                                enabled=!state.busy,
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Text("Continue to full-cycle review")
                            }
                        } else {
                            Text("Open My program and re-audit the saved draft. The original review policy is no longer available.",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("A saved first-program draft is not an approved or executable workout. Full-cycle review and explicit workout issuance remain separate.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(confirmingSave)AlertDialog(
        onDismissRequest={if(!state.busy)confirmingSave=false},
        title={Text("Save this reviewed training draft?")},
        text={Text("Save the displayed four-week first block, including its exact chosen weights, sets, reps and effort targets, as a new native program draft. This will not overwrite any previous program or issue a workout. You will review the complete cycle before training.")},
        confirmButton={Button(
            enabled=!state.busy && state.canSaveDraft,
            onClick={
                confirmingSave=false
                controller.saveReviewedDraft()
            }) {Text("Save new draft")}},
        dismissButton={TextButton(onClick={confirmingSave=false}) {
            Text("Cancel")
        }}
    )
}
