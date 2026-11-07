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
                    preview=null,
                    message="Verified setup loaded. Choose your own initial working weights; equipment inventory is not a strength test.")
                val first=CoachFirstProgramStartDomain.review(
                    FirstProgramStartRequest(source.setup,source.catalog,
                        emptyMap(),emptySet(),phase))
                current.value=newState.copy(preview=first)
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
            preview=null,message=null)
    }
    fun toggleBodyweight(id:String) {
        val s=current.value
        if(s.busy || s.source==null)return
        current.value=s.copy(
            selectedBodyweightIds=if(id in s.selectedBodyweightIds)
                s.selectedBodyweightIds-id else s.selectedBodyweightIds+id,
            preview=null,message=null)
    }
    fun choosePhase(value:TrainingPhase) {
        val s=current.value
        if(!s.busy)current.value=s.copy(phase=value,preview=null,message=null)
    }
    fun chooseSetupMinutes(value:Int) {
        val s=current.value
        if(!s.busy && value in setOf(10,15,20,30))
            current.value=s.copy(setupMinutes=value,preview=null,message=null)
    }
    fun addFocus(value:ComposerFocus) {
        val s=current.value
        if(!s.busy && s.extraFocuses.size<2)
            current.value=s.copy(extraFocuses=s.extraFocuses+value,
                preview=null,message=null)
    }
    fun removeLastFocus() {
        val s=current.value
        if(!s.busy && s.extraFocuses.isNotEmpty())
            current.value=s.copy(extraFocuses=s.extraFocuses.dropLast(1),
                preview=null,message=null)
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
        val result=CoachFirstProgramStartDomain.review(
            FirstProgramStartRequest(source.setup,source.catalog,
                selected,initial.selectedBodyweightIds,
                initial.phase,initial.extraFocuses,
                nonSetSecondsPerDay=initial.setupMinutes*60))
        val after=(host.readCustomAuthoringSource()
            as? NativeAuthoringSourceRead.Found)?.source
        if(after!=source) {
            current.value=initial.copy(source=null,preview=null,
                message="Your gym or program source changed during review. Nothing was saved.")
            return@run
        }
        current.value=initial.copy(preview=result,
            message=when(result.status) {
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW ->
                    "An auditable first-program draft was generated. It is not saved or approved; review its muscle coverage, sets and intended effort."
                FirstProgramStartStatus.CALIBRATION_REQUIRED ->
                    "Choose and calibrate initial exercises first."
                else -> "Your choices need review before this draft can become a program."
            })
    }
    override fun close(){scope.cancel()}
}
private fun prettyFirstProgram(value:String)=value.replace('_',' ')
    .replaceFirstChar{it.uppercase()}

@Composable
internal fun NativeKotlinFirstProgramCandidateScreen(
    controller:NativeKotlinFirstProgramController
) {
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) {controller.open()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageHeading("KOTLIN COACH · ISOLATED",
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
                Text("Estimated warm-up / setup / exercise transitions per session",
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
            PursuitCard {
                Text("Select and calibrate exercises",
                    style=MaterialTheme.typography.titleLarge)
                Text("Enter an attainable first-session weight for each movement you want. Use the displayed loading basis; for dumbbells the value is per hand. Leave unused exercises blank. The coach will not choose the lightest gym weight as your working weight.",
                    style=MaterialTheme.typography.bodySmall)
                val options=state.preview?.eligibleMovements.orEmpty()
                if(options.isEmpty())Text(
                    "No eligible curated movements are available yet. Check your gym inventory, facilities and bodyweight preferences.",
                    style=MaterialTheme.typography.bodySmall)
                options.forEach { option ->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(prettyFirstProgram(option.exerciseId),
                            style=MaterialTheme.typography.titleSmall)
                        if(option.needsWeightCalibration) {
                            OutlinedTextField(
                                value=state.enteredLoads[option.exerciseId].orEmpty(),
                                onValueChange={controller.enterLoad(option.exerciseId,it)},
                                singleLine=true,enabled=!state.busy,
                                label={Text("Starting load (${option.unit.name.lowercase()}; ${prettyFirstProgram(option.basis?.name ?: "unknown basis")})")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Decimal),
                                modifier=Modifier.fillMaxWidth())
                        } else {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked=option.exerciseId in
                                    state.selectedBodyweightIds,
                                    enabled=!state.busy,
                                    onCheckedChange={controller.toggleBodyweight(option.exerciseId)})
                                Text("I can perform this bodyweight movement",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Button(onClick=controller::preview,enabled=!state.busy,
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Review my proposed first week")
                }
            }
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
                                Text("${prettyFirstProgram(visit.exerciseId)} · ${visit.sets} sets",
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
                            Text("${prettyFirstProgram(exercise.exerciseId)}: ${working.size} × ${range} · goal RIR ${target?.targetRir ?: "?"}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("This is a read-only candidate, not yet a saved or executable program. A separate full-cycle review, load validation and explicit approval must happen before workouts can be issued.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
