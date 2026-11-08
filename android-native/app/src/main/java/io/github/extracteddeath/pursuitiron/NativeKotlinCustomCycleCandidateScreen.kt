package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomSignoffRead
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomPrepareRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomNextCycleSlot
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeBlockLoadConsentKind
import java.math.BigDecimal

private val customWeekdays = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday",
    "Friday", "Saturday", "Sunday"
)

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

private fun pretty(value: Enum<*>) =
    value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun splitLabel(split: CustomSplitIdentity): String = when (split) {
    is CustomSplitIdentity.Freeform -> split.label
    is CustomSplitIdentity.Named -> pretty(split.split)
}

/**
 * Preview of the genuinely athlete-authored, offline native custom cycle
 * workflow. Uses ONLY NativeKotlinCustomCycleController and its verified
 * durable drafts. This screen is deliberately NOT connected to the legacy
 * MainActivity route or the existing JavaScript engine.
 *
 * Training decisions and exercise prescriptions are not created from this
 * UI. A saved draft cannot issue a session or change history.
 */
@Composable
internal fun NativeKotlinCustomCycleCandidateScreen(
    controller: NativeKotlinCustomCycleController,
    initialCycleId: String? = null,
    onOpenIssuedWorkout:(String)->Unit = {},
    onOpenHistory:()->Unit = {},
    onBuildProgram:()->Unit = {}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val busy = state.busy
    val calibrationLocked=state.calibrationReceipt!=null ||
        state.preparedWorkout?.let{it.calibrationApprovalFingerprint!=null||it.resetToAuthored}==true
    var openId by rememberSaveable { mutableStateOf(initialCycleId.orEmpty()) }
    var showAdvancedId by rememberSaveable {mutableStateOf(false)}
    var creatingBlank by rememberSaveable {mutableStateOf(false)}
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
    var confirmConsent by remember { mutableStateOf(false) }
    var inspectedWeek by rememberSaveable { mutableStateOf("1") }
    var inspectedWeekday by rememberSaveable { mutableIntStateOf(0) }
    var coachMaxStep by rememberSaveable {mutableStateOf("5")}
    var coachReadiness by remember {mutableStateOf(CoachReadiness.NORMAL)}
    var coachRestricted by rememberSaveable {mutableStateOf(false)}
    var dayCoachingVisible by remember {mutableStateOf(false)}
    LaunchedEffect(draft?.revision,state.sourceRevision,state.authoredPreview,
        state.reviewedCoachInputs,coachMaxStep,coachReadiness,coachRestricted,
        inspectedWeek,inspectedWeekday) {
        dayCoachingVisible=false
        controller.clearCalibrationOffer()
    }
    // Slot identity is stable and separate from the exercise catalog ID.
    // Null slot ID denotes authoring a genuinely new exercise visit.
    var exerciseEditor by remember {
        mutableStateOf<Triple<String, Int, String?>?>(null)
    }
    var weekExerciseEditor by remember(draft?.cycleId,draft?.revision) {
        mutableStateOf<Triple<Int,Int,String>?>(null)
    }
    /** Prefill only a freshly validated V2 suggestion; never silently save. */
    var weekEditFromCoach by remember(draft?.cycleId,draft?.revision) {
        mutableStateOf(false)
    }
    LaunchedEffect(state.weekEditReview) {
        if(state.weekEditReview!=null)weekExerciseEditor=null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading("KOTLIN COACH · ISOLATED",
            if (draft == null) "Your programs" else draft.title,
            "Custom programs and cycles · saved privately on this device")
        Text(when {
            state.issuedCalibrationId!=null -> "Workout ready to log"
            state.preparedWorkout!=null -> "Targets prepared · workout not started"
            state.calibrationReceipt!=null -> "Starting weights saved · workout not started"
            draft!=null -> "Your authored cycle · review changes before starting"
            else -> "Create and review your own training cycle"
        },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (state.refusal == null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error)
        }

        if (draft == null) {
            PursuitCard(highlighted = true) {
                Text("Your saved programs",
                    style = MaterialTheme.typography.titleMedium)
                Text("Open a verified program by name. Your edits, blocks and exercise choices are preserved.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {controller.findSavedPrograms()},
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {Text("Refresh saved programs")}
                if(state.savedDraftsLoaded && state.savedDrafts.isEmpty()) {
                    Text("No saved custom programs for this athlete yet.",
                        style=MaterialTheme.typography.bodySmall)
                }
                state.savedDrafts.forEach { saved ->
                    OutlinedButton(
                        onClick={controller.open(saved.cycleId)},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(saved.title,style=MaterialTheme.typography.titleSmall)
                            Text("${saved.blocks} blocks · saved revision ${saved.revision}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
        } else {
            PursuitCard(highlighted = true) {
                Text("Custom authoring", style = MaterialTheme.typography.titleMedium)
                Text("${draft.blocks.size} blocks · ${draft.blocks.sumOf { it.weeks }} scheduled weeks · revision ${draft.revision}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Cycle ID: ${draft.cycleId}",
                    style = MaterialTheme.typography.bodySmall)
                Text(draft.authorNote.ifBlank { "No author notes" },
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editingCycle = true },
                        enabled = !busy) { Text("Name & notes") }
                    OutlinedButton(onClick = { controller.undo() },
                        enabled = !busy && state.canUndo) { Text("Undo last edit") }
                    OutlinedButton(onClick = { controller.open(draft.cycleId) },
                        enabled = !busy) { Text("Reload") }
                    OutlinedButton(onClick = { controller.findSavedPrograms() },
                        enabled = !busy) { Text("Other programs") }
                }
            }
            PursuitCard(highlighted=true) {
                Text("Next workout in your program",
                    style=MaterialTheme.typography.titleLarge)
                Text("Choose the next session from your verified training history. An unfinished issued workout is resumed, never silently replaced. This is the order of your saved program, not an inferred date.",
                    style=MaterialTheme.typography.bodySmall)
                val policyReady=state.coachingAudit?.status==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                    state.coachingAudit?.issues?.isEmpty()==true
                val signed=(state.signoff as? NativeCustomSignoffRead.Found)
                    ?.isCurrent==true
                OutlinedButton(
                    onClick=controller::findNextCycleSession,
                    enabled=!busy&&policyReady&&signed,
                    modifier=Modifier.fillMaxWidth()
                ) { Text("Find my next workout") }
                if(!signed||!policyReady)
                    Text("First review this complete training cycle and confirm its exact saved prescription below.",
                        style=MaterialTheme.typography.bodySmall)
                val selection=state.nextCycleSlot?.takeIf{
                    state.nextSlotCycleRevision==draft.revision &&
                        policyReady && signed
                }
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
                        Text("All scheduled workouts in this saved cycle have verified completed history.",
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
            NativeKotlinCustomCycleCalibrationPanel(
                controller,state,onOpenIssuedWorkout)
            draft.blocks.forEachIndexed { index, block ->
                key(block.blockId) {
                    PursuitCard {
                        Text("${index + 1}. ${block.label}",
                            style = MaterialTheme.typography.titleLarge)
                        Text("${pretty(block.phase)} · ${block.weeks} weeks · ${splitLabel(block.split)}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("${block.days.size} scheduled days · ${pretty(block.purpose)}",
                            style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editingBlock = block.blockId },
                                enabled = !busy) { Text("Edit block") }
                            TextButton(onClick = { controller.duplicateBlock(block.blockId) },
                                enabled = !busy) { Text("Duplicate") }
                            TextButton(onClick = { controller.reorderBlock(block.blockId, -1) },
                                enabled = !busy && index > 0) { Text("Earlier") }
                            TextButton(onClick = { controller.reorderBlock(block.blockId, 1) },
                                enabled = !busy && index < draft.blocks.lastIndex) {
                                Text("Later")
                            }
                        }
                        block.days.sortedBy { it.weekday }.forEach { day ->
                            HorizontalDivider()
                            Text("${customWeekdays.getOrElse(day.weekday) { "Day" }} · ${day.label}",
                                style = MaterialTheme.typography.titleMedium)
                            Text("${pretty(day.purpose)} · ${day.slots.size} exercises",
                                style = MaterialTheme.typography.bodySmall)
                            if (day.slots.isNotEmpty()) {
                                day.slots.forEachIndexed { slotIndex, slot ->
                                    Text("${slot.exerciseId} · ${slot.sets.size} authored sets" +
                                        if (slot.pinned) " · Pinned" else "",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Row(Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { exerciseEditor = Triple(
                                                block.blockId, day.weekday, slot.slotId) },
                                            enabled = !busy
                                        ) { Text("Edit sets") }
                                        TextButton(onClick = {
                                            controller.moveExercise(
                                                block.blockId, day.weekday,
                                                slot.slotId, -1)
                                        }, enabled = !busy && slotIndex > 0) {
                                            Text("Earlier")
                                        }
                                        TextButton(onClick = {
                                            controller.moveExercise(
                                                block.blockId, day.weekday,
                                                slot.slotId, 1)
                                        }, enabled = !busy &&
                                            slotIndex < day.slots.lastIndex) {
                                            Text("Later")
                                        }
                                    }
                                }
                            } else {
                                Text("No exercises yet. This day is an unfinished draft.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    editingDay = block.blockId to day.weekday
                                }, enabled = !busy) { Text("Edit day") }
                                TextButton(onClick = {
                                    exerciseEditor = Triple(
                                        block.blockId, day.weekday, null)
                                }, enabled = !busy && day.slots.size < 32) {
                                    Text("Add exercise")
                                }
                            }
                        }
                        OutlinedButton(onClick = { addingDayFor = block.blockId },
                            enabled = !busy && block.days.size < 7,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Add a custom training day")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { addingBlock = true },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Add a training block")
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
                    Text("Cold-recoverable exact targets; still not issued. No training journal was written.",
                        style=MaterialTheme.typography.bodySmall)
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
                    if(state.reviewedCoachInputs!=null && state.progressionCoachingEnabled && !calibrationLocked) {
                        OutlinedTextField(value=coachMaxStep,enabled=!busy,
                            onValueChange={coachMaxStep=it;dayCoachingVisible=false},
                            label={Text("Maximum load increase · gym units")},
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                            isError=maxStep==null,singleLine=true,
                            modifier=Modifier.fillMaxWidth())
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
                    session.slots.forEach { slot ->
                        HorizontalDivider()
                        Text("${slot.exerciseId} · ${slot.variantId}",
                            style=MaterialTheme.typography.titleSmall)
                        Text("Slot ${slot.slotId} · ${pretty(slot.role)}" +
                            (slot.attachmentId?.let{" · Attachment $it"} ?: "") +
                            (slot.supersetGroup?.let{" · Superset $it"} ?: "") +
                            (if(slot.pinned)" · Pinned" else ""),
                            style=MaterialTheme.typography.bodySmall)
                        if(slot.coachNote.isNotBlank())
                            Text(slot.coachNote,
                                style=MaterialTheme.typography.bodySmall)
                        slot.sets.forEach { set ->
                            val target=set.prescription
                            val load=when(val weight=target.load) {
                                DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
                                is DraftSetLoad.Verified ->
                                    "${weight.value.toPlainString()} ${weight.unit} · ${pretty(weight.basis)}"
                            }
                            Text("Set ${set.ordinalInSlot} · ${pretty(target.type)} · $load · ${target.reps.minimum}–${target.reps.maximum} reps · RIR ${target.targetRir?.toString() ?: "not set"}",
                                style=MaterialTheme.typography.bodyMedium)
                            Text("${target.activeSeconds}s work · ${target.restSeconds}s rest",
                                style=MaterialTheme.typography.bodySmall)
                            target.amplifier?.let {amp ->
                                Text("${pretty(amp.kind)} · ${amp.followUpReps} follow-up reps · ${amp.followUpRestSeconds}s intra-set rest · " +
                                    (when(val extra=amp.followUpLoad) {
                                        null -> "Same confirmed load"
                                        DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
                                        is DraftSetLoad.Verified ->
                                            "${extra.value.toPlainString()} ${extra.unit} · ${pretty(extra.basis)}"
                                    }),
                                    style=MaterialTheme.typography.bodySmall)
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
                    }
                    state.dayCoaching?.takeIf {reviewed->
                        dayCoachingVisible && reviewed.nextWorkout?.let {next->
                            next.copy(declaredNonSetSeconds=session.declaredNonSetSeconds)==session
                        }==true && session.stamp.cycleRevision==draft.revision &&
                            session.stamp.gymCatalogSourceRevision==state.sourceRevision
                    }?.let {reviewed->
                        NativeKotlinCustomDayCoachingSummary(reviewed,session.absoluteWeek)
                        val readyRepEdit=reviewed.selectedSlotId?.let {chosen->
                            (CustomNextRepAmendmentDomain.propose(
                                session,chosen,reviewed.review)
                                as? CustomNextRepAmendmentResult.Proposed)
                                ?.amendment
                        }
                        if(readyRepEdit!=null && state.preparedWorkout==null &&
                            reviewed.review.repForecastPolicy==
                                CustomRepForecastPolicy.TARGET_RIR_V2) {
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
                NativeKotlinCustomCoachPolicyEditor(
                    cycle=draft,
                    source=state.reviewedSourceSetup,
                    sourceRevision=state.sourceRevision,
                    busy=busy,
                    observedEvidence=state.authoredWorkloadEvidence?.takeIf{
                        it.auditedCycleRevision==draft.revision &&
                            state.evidenceSourceRevision==state.sourceRevision
                    },
                    onInspectEvidence={
                        controller.inspectAuthoredWorkloadEvidence()
                    },
                    onDraftChanged={controller.markPolicyInputsChanged()},
                    onReview={reviewedInputs->
                        controller.auditFullCycle(reviewedInputs)
                    })
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
                val ready = state.coachingAudit?.status ==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                    state.coachingAudit?.issues?.isEmpty()==true &&
                    state.auditSourceRevision != null &&
                    state.coachingAudit?.auditedCycleRevision == draft.revision
                Button(onClick = { confirmConsent=true },
                    enabled = !busy && ready,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Review and sign this exact cycle")
                }
                if(state.signoff is NativeCustomSignoffRead.Found)
                    OutlinedButton(onClick = { controller.revokeAthleteConsent() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Revoke my saved cycle consent")
                    }
            }
            PursuitCard {
                Text("Approval and issuance remain separate",
                    style = MaterialTheme.typography.titleMedium)
                Text("Your entire custom program—including supersets, amplifiers and week-specific overrides—must be preserved through a separately validated athlete sign-off and Kotlin-only workout issuer. The old composer snapshot cannot silently replace your authored sets.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { deleteConfirm = true },
                    enabled = !busy) { Text("Delete this saved draft") }
            }
        }
    }

    if(confirmConsent && draft!=null) {
        AlertDialog(
            onDismissRequest={if(!busy)confirmConsent=false},
            title={Text("Confirm your exact custom program")},
            text={
                Text("You're consenting to the reviewed version of ${draft.title}, revision ${draft.revision}. The complete gym/catalog and authored coaching policy are rechecked before saving. This does not issue, begin, or approve a workout.")
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
                        it.review.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2 &&
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
                mutableStateOf(existing?.split?.let(::splitLabel).orEmpty())
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
