package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coach explanations use authenticated model outcomes, not debug DTO strings.
 * A refused progression is a normal outcome: no workout target was changed. */
private fun nativeNextRepExplanation(result:NativeOwnerNextSetResult?):String? =
    when(result) {
        is NativeOwnerNextSetResult.Review -> when(val review=result.candidate) {
            is NativeCurrentRepPrescriptionResult.Reviewed -> when(
                val proposed=review.proposal) {
                is AdaptiveSetPrescriptionResult.Candidate -> null
                is AdaptiveSetPrescriptionResult.Rejected -> proposed.detail
            }
            is NativeCurrentRepPrescriptionResult.Rejected -> when(review.reason) {
                NativeCurrentRepPrescriptionRefusal.NO_RECORDED_EXERCISE ->
                    "This movement has no finished native workout yet. Complete an approved session before reviewing progression."
                NativeCurrentRepPrescriptionRefusal.CUSTOM_WORKOUT_IS_LATEST ->
                    "Your latest recorded set was in a custom cycle. Review its next week from My Program; this generated-program coach does not borrow that history."
                NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT ->
                    "The last recorded sets, prescribed loads or exercise details are not comparable to this workout. Review the training log first."
                NativeCurrentRepPrescriptionRefusal.EXERCISE_SOURCE_CHANGED ->
                    "Your approved program or gym changed. Build a comparable session history before progressing reps."
                NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED ->
                    "The previous workout used a different training day or purpose. Keep those progression histories separate."
                NativeCurrentRepPrescriptionRefusal.INVALID_SOURCE_OR_HISTORY ->
                    "The approved program and native workout history could not be matched safely. Refresh before reviewing again."
            }
        }
        is NativeOwnerNextSetResult.HistoryRejected -> when(result.reason) {
            NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION ->
                "A Finish or saved correction is still pending recovery. Reopen Train and History before reviewing progression."
            NativeKotlinMixedHistoryRefusal.NOT_ENROLLED ->
                "Native Kotlin history is not enrolled. Existing web workouts are not automatically included in this coach."
            else ->
                "Your saved training history could not be fully verified. No targets were changed."
        }
        is NativeOwnerNextSetResult.Rejected -> when(result.reason) {
            NativeOwnerNextSetRefusal.NO_CURRENT_APPROVAL ->
                "Approve a Kotlin training program before requesting a next-session prescription."
            NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT ->
                "Your approved next workout is already issued or has a conflicting journal. Open Train to resume its original targets."
            NativeOwnerNextSetRefusal.GYM_SOURCE_UNAVAILABLE,
            NativeOwnerNextSetRefusal.GYM_SOURCE_CHANGED ->
                "Your approved workout and current gym equipment no longer match. Check equipment and approve the updated program."
            NativeOwnerNextSetRefusal.ASSISTANCE_DISABLED ->
                "Progression assistance is off. You can still train with your authored targets."
            else ->
                "The current athlete or approved workout could not be verified. No changes were saved."
        }
        null -> "No verified next-session suggestion is available."
    }

/**
 * Isolated Kotlin-only coaching review. Deliberately never mounted from
 * NativeRuntimeHost, and never silently saves a plan or issues a workout.
 * The native writer independently re-reads approval, complete corrected
 * history, and every affected workout day at the moment of acceptance.
 */
/**
 * Choice of training scope, separate from progression decisions. Navigation
 * never authors a weight, applies a suggested week or changes a workout.
 */
@Composable
private fun PursuitCoachDestinations(
    onOpenProgram:()->Unit,onOpenFuture:()->Unit
) {
    PursuitSectionHeading("Your training decisions",
        "Your saved plan and future cycle reviews are separate from exercise-specific progressions.")
    PursuitDestinationRow(
        title="Current training plan",
        description="Review your next workout and edit the complete saved program.",
        onClick=onOpenProgram,
        icon={Icon(Icons.Filled.FitnessCenter,contentDescription=null)})
    PursuitDestinationRow(
        title="Future weeks and blocks",
        description="Review optional adaptations before approving any changes.",
        onClick=onOpenFuture,
        icon={Icon(Icons.Filled.CalendarMonth,contentDescription=null)})
}

@Composable
internal fun NativeKotlinCandidateCoachScreen(
    host: NativeKotlinCandidateHost,
    preferredExerciseId:String?=null,
    onOpenSettings:()->Unit={},
    onOpenProgram:()->Unit={},
    onOpenFuture:()->Unit={},
    onOpenTrain:(String)->Unit={}
) {
    val scope=rememberCoroutineScope()
    var exerciseId by rememberSaveable { mutableStateOf("") }
    var exerciseChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var choicesLoading by remember { mutableStateOf(true) }
    var assistance by remember(host) {mutableStateOf<CoachAssistancePolicy?>(null)}
    var maximumStep by rememberSaveable { mutableStateOf("5") }
    var readiness by remember { mutableStateOf(CoachReadiness.NORMAL) }
    var restricted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var reviewed by remember {
        mutableStateOf<NativeOwnerLoadReviewResult.Review?>(null)
    }
    var nextRepReview by remember {
        mutableStateOf<NativeOwnerNextSetResult.Review?>(null)
    }
    var nextRepNotice by remember { mutableStateOf<String?>(null) }
    var repAccepted by remember { mutableStateOf(false) }
    var repApprovedWorkout by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var savedWorkout by remember { mutableStateOf<String?>(null) }
    val step=remember(maximumStep) {
        runCatching { BigDecimal(maximumStep.trim()) }.getOrNull()
            ?.takeIf { it > BigDecimal.ZERO &&
                it <= BigDecimal("100000") &&
                it.stripTrailingZeros().scale() <= 3 }
    }
    val permitted=exerciseId in exerciseChoices &&
        exerciseId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$"))
    fun invalidate() {
        reviewed=null
        nextRepReview=null
        nextRepNotice=null
        repAccepted=false
        repApprovedWorkout=null
        checked=false
        savedWorkout=null
        notice=null
    }
    LaunchedEffect(host,preferredExerciseId) {
        choicesLoading=true
        val saved=runCatching {
            withContext(Dispatchers.IO){host.readCoachAssistance()}
        }.getOrNull()
        assistance=(saved as? NativeCoachSettingsRead.Current)?.settings?.policy
        val choices=if(assistance?.offersProgression==true)runCatching {
            withContext(Dispatchers.IO) {
                host.currentVerifiedCoachExerciseChoices()
            }
        }.getOrDefault(emptyList()) else emptyList()
        exerciseChoices=choices
        // Finished History/workout may suggest only a FOCUS movement. Never
        // grant access from a passed exercise ID: require the CURRENT owned
        // program + equipment choices independently verified by the host.
        val selected=preferredExerciseId?.takeIf{it in choices}
            ?:exerciseId.takeIf{it in choices}
            ?:choices.firstOrNull().orEmpty()
        if(exerciseId!=selected) {
            exerciseId=selected
            invalidate()
        }
        choicesLoading=false
    }
    val verified=reviewed?.coaching as? NativeCurrentLoadReviewResult.Reviewed
    val repCandidate=(nextRepReview?.candidate as?
        NativeCurrentRepPrescriptionResult.Reviewed)?.proposal
        as? AdaptiveSetPrescriptionResult.Candidate
    val proposal=verified?.coaching?.takeIf {
        it.status==CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW &&
            it.candidateProgram!=null
    }

    if(assistance?.offersProgression!=true) {
        PursuitScreenContent {
            PageHeading("YOUR COACH",
                if(choicesLoading) "Checking coach settings…" else "Train your way",
                "Your authored workouts, history, and Finish stay available even when coaching is off.")
            PursuitFormSection("Coaching preferences",
                "No progression proposals will be shown or accepted while the relevant coach switches are off.") {
                Text(when {
                    choicesLoading -> "Reading your saved preferences…"
                    assistance==null -> "Your settings could not be verified. Suggestions stay disabled."
                    !checkNotNull(assistance).coachingSuggestionsEnabled ->
                        "Manual training is active. Workouts keep their authored weight and rep targets."
                    else -> "Progression recommendations are off. You can still follow and complete your program."
                },style=MaterialTheme.typography.bodyMedium)
                Button(onClick=onOpenSettings) {Text("Coaching settings")}
            }
            PursuitCoachDestinations(onOpenProgram,onOpenFuture)
        }
        return
    }
    PursuitScreenContent {
        PageHeading("YOUR COACH",
            "Your coaching plan",
            "Review optional suggestions based on verified workouts. Nothing changes automatically.")
        PursuitCoachDestinations(onOpenProgram,onOpenFuture)
        OutlinedButton(onClick=onOpenSettings) {Text("Coaching settings")}
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Choose an approved exercise",
                    style=MaterialTheme.typography.titleMedium)
                if(preferredExerciseId!=null && preferredExerciseId in exerciseChoices) {
                    Text("From your finished workout: ${preferredExerciseId.replace('_',' ').replace('-',' ')}. Current program and training-day evidence are rechecked for each review.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else if(preferredExerciseId!=null && !choicesLoading) {
                    Text("This earlier movement is not available in your CURRENT approved generated program. Select an eligible exercise below. Custom-cycle next-week reviews remain in My Program.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    choicesLoading -> Text("Checking your current native program and gym…")
                    exerciseChoices.isEmpty() ->
                        Text("No current verified Kotlin program and gym catalog. Complete your native gym setup and approve a program before reviewing progression.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        exerciseChoices.forEach { id ->
                            FilterChip(selected=exerciseId==id,
                                onClick={exerciseId=id;invalidate()},
                                label={Text(kotlinExerciseLabel(id))})
                        }
                    }
                }
                OutlinedTextField(value=maximumStep,
                    onValueChange={ maximumStep=it;invalidate() },
                    label={Text("Maximum approved increase (gym units)")},
                    singleLine=true,modifier=Modifier.fillMaxWidth(),
                    isError=step==null)
                if(assistance?.offersFatigueAdvice==true) {
                    Text("Readiness",style=MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CoachReadiness.entries.forEach { value ->
                            FilterChip(
                                selected=readiness==value,
                                onClick={readiness=value;invalidate()},
                                label={Text(value.name.lowercase().replaceFirstChar {
                                    it.uppercase()
                                })})
                        }
                    }
                } else {
                    Text("Fatigue-aware advice is off. Verified completed sets still guide optional progression.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=restricted,
                        onCheckedChange={restricted=it;invalidate()})
                    Text("Movement is currently restricted or painful")
                }
                Button(
                    enabled=!busy && permitted && step!=null,
                    onClick={
                        invalidate()
                        busy=true
                        scope.launch {
                            val result=runCatching {
                                withContext(Dispatchers.IO) {
                                    host.reviewCurrentNativeLoad(
                                        exerciseId,checkNotNull(step),readiness,restricted)
                                }
                            }
                            reviewed=result.getOrNull() as? NativeOwnerLoadReviewResult.Review
                            val proof=reviewed?.coaching as?
                                NativeCurrentLoadReviewResult.Reviewed
                            selectedDay=proof?.coaching?.candidateProgram
                                ?.days?.indices?.firstOrNull() ?: 0
                            notice=when {
                                result.isFailure ->
                                    "Native history review failed. No changes were saved."
                                proof==null -> "No eligible verified coaching review: ${result.getOrNull()}"
                                proof.coaching.status!=
                                    CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW ->
                                    proof.coaching.explanation
                                else -> null
                            }
                            busy=false
                        }
                    }
                ) {Text(if(busy)"Verifying native history…" else "Review coaching change")}
                OutlinedButton(
                    enabled=!busy && permitted,
                    onClick={
                        nextRepReview=null
                        nextRepNotice=null
                        repAccepted=false
                        repApprovedWorkout=null
                        busy=true
                        scope.launch {
                            val outcome=runCatching {
                                withContext(Dispatchers.IO) {
                                    host.reviewNextNativeSetTargets(
                                        exerciseId,readiness,restricted)
                                }
                            }
                            nextRepReview=outcome.getOrNull()
                                as? NativeOwnerNextSetResult.Review
                            nextRepNotice=if(outcome.isFailure)
                                "Unable to verify saved workout history. No targets changed."
                            else nativeNextRepExplanation(outcome.getOrNull())
                            busy=false
                        }
                    }
                ) {Text("Preview next working-set reps")}
            }
        }

        repCandidate?.let { candidate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Next-session rep preview",
                        style=MaterialTheme.typography.titleLarge)
                    Text(
                        if(candidate.decisionKind.isRepeat)
                            "Repeat your previously prescribed targets. This keeps your set goals without claiming a new progression."
                        else "Review each set at its intended effort. One or several rep goals may change based on your recorded performance.",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("Built from the most recent verified, corrected workout. This is a preview only; already-issued sessions are never changed.",
                        style=MaterialTheme.typography.bodySmall)
                    if(candidate.assumedGoalRirSetCount>0)
                        Text(ProgressionEffortDomain.assumptionNote(candidate.assumedGoalRirSetCount).trim(),
                            style=MaterialTheme.typography.bodySmall)
                    if(candidate.forecastLimitations.isNotEmpty())
                        Text("These estimates use your last workout. Rest, earlier sets and recovery can change what is achievable. Stop at your target RIR if fewer reps are available.",
                            style=MaterialTheme.typography.bodySmall)
                    val sets=candidate.workout.targets.filter {
                        it.exerciseId==candidate.exerciseId &&
                            it.type==DraftSetType.WORKING
                    }.sortedBy {it.ordinal}
                    sets.forEachIndexed {index,set ->
                        val before=candidate.observedReps[index]
                        val after=candidate.newRepTargets[index]
                        val sameLoad=set.load as? DraftSetLoad.Verified
                        val targetText=if(candidate.decisionKind.isRepeat)
                            "Set ${index+1}: ${before} completed · repeat ${after}-rep goal"
                        else "Set ${index+1}: ${before} completed → ${after}-rep goal"
                        Text(targetText,
                            style=MaterialTheme.typography.titleMedium)
                        Text("Load: ${sameLoad?.value ?: "bodyweight"} ${sameLoad?.unit?.name ?: ""} · Target RIR: ${set.targetRir ?: "not issued"} · Rest: ${set.restSeconds}s",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Text("No changes to weights, warmups, other exercises or workout history. Saving creates a NEW immutable workout grant; it never edits an issued session.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=repAccepted,
                            onCheckedChange={repAccepted=it})
                        Text("I reviewed each exact rep goal and authorize a new native workout prescription.")
                    }
                    Button(
                        enabled=repAccepted && !busy,
                        onClick={
                            val shown=nextRepReview
                            val review=shown?.candidate
                                as? NativeCurrentRepPrescriptionResult.Reviewed
                            if(shown!=null && review!=null) {
                                val nextId=UUID.randomUUID().toString()
                                val intent=NativeKotlinAcceptedRepIntent(
                                    candidate.exerciseId,
                                    shown.approvalRevision,review.historyRevision,
                                    candidate.workout.workoutId,
                                    candidate.historicalWorkoutId,
                                    candidate.editedSetKey,
                                    candidate.observedReps,
                                    candidate.newRepTargets,
                                    "workout-$nextId",
                                    "grant-$nextId",
                                    "receipt-$nextId",true,
                                    candidate.decisionKind)
                                busy=true
                                scope.launch {
                                    val result=runCatching {
                                        withContext(Dispatchers.IO) {
                                            host.acceptReviewedNativeRepTargets(
                                                intent,readiness,restricted)
                                        }
                                    }
                                    val written=result.getOrNull()
                                        as? NativeKotlinRepAcceptanceResult.Committed
                                    repApprovedWorkout=written?.approvedWorkoutId
                                    nextRepNotice=if(written!=null)
                                        "Your reviewed set targets are saved under a new native grant. They will be rechecked against corrected history at workout issuance. Open Train to issue the new workout."
                                    else
                                        "The set-target approval was not saved: ${result.getOrNull() ?: "storage unavailable"}. Review again before accepting."
                                    nextRepReview=null
                                    repAccepted=false
                                    busy=false
                                }
                            }
                        }
                    ) {Text(if(busy)"Saving rep targets…" else "Accept set targets")}

                }
            }
        }
        nextRepNotice?.let {
            Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }

        proposal?.let { candidate ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Verified coaching proposal",
                        style=MaterialTheme.typography.titleLarge)
                    Text(candidate.explanation)
                    Text("Current: ${candidate.originalLoad}  →  Proposed: ${candidate.candidateLoad}",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Change: ${candidate.kind?.name?.replace('_',' ')}")
                    Text("Affects days: ${candidate.affectedDayIndices.map { it+1 }.joinToString()}",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("Program revision: current approval ${reviewed?.approvalStorageRevision}; new approval is only saved after confirmation.",
                        style=MaterialTheme.typography.bodySmall)
                    Text("Choose the NEXT workout day",
                        style=MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        checkNotNull(candidate.candidateProgram).days.forEachIndexed {
                            index,day ->
                            FilterChip(selected=selectedDay==index,
                                onClick={selectedDay=index;checked=false},
                                label={Text("Day ${index+1} · ${day.focus.name.replace('_',' ')}")})
                        }
                    }
                    Text("The approved program retains ${checkNotNull(candidate.candidateProgram).exercises.size} exercises, with the reviewed load applied to each affected visit. The prior issued workout and saved results will remain unchanged.",
                        style=MaterialTheme.typography.bodySmall)
                    if(verified?.supersedesAcceptedRepTargets==true)
                        Text("This load change replaces the pending personalized rep-only grant. The new load begins with the approved rep range; saved workout results remain unchanged.",
                            style=MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=checked,
                            onCheckedChange={checked=it})
                        Text(if(verified?.supersedesAcceptedRepTargets==true)
                            "I reviewed the load change and all affected days, and agree to replace my pending personalized rep targets."
                        else "I reviewed the proposed loads and all affected days. Save a new approved Kotlin program revision.")
                    }
                    Button(enabled=checked && !busy && step!=null,
                        onClick={
                            val proof=verified
                            val summary=proposal
                            val displayed=reviewed
                            if(proof!=null && summary!=null &&
                                displayed!=null && step!=null) {
                                busy=true
                                notice=null
                                val next=UUID.randomUUID().toString()
                                val intent=NativeKotlinCoachLoadAcceptanceIntent(
                                    exerciseId,
                                    displayed.approvalStorageRevision,
                                    proof.historyRevision,
                                    proof.comparedWorkoutIds,
                                    checkNotNull(summary.kind),
                                    checkNotNull(summary.originalLoad),
                                    checkNotNull(summary.candidateLoad),
                                    summary.affectedDayIndices,
                                    "workout-$next","grant-$next",
                                    "receipt-$next",selectedDay,true,
                                    proof.supersedesAcceptedRepTargets)
                                scope.launch {
                                    val result=runCatching {
                                        withContext(Dispatchers.IO) {
                                            host.acceptReviewedNativeLoad(
                                                intent,checkNotNull(step),
                                                readiness,restricted)
                                        }
                                    }
                                    val success=result.getOrNull()
                                        as? NativeKotlinCoachAcceptanceResult.Committed
                                    savedWorkout=success?.approvedWorkoutId
                                    notice=if(success!=null)
                                        "New native program revision saved. Your next workout is approved but not yet issued. Open it from Train."
                                    else "Could not save: ${result.getOrNull() ?: "storage unavailable"}. Re-run the review before accepting."
                                    reviewed=null
                                    checked=false
                                    busy=false
                                }
                            }
                        }
                    ) {Text(if(busy)"Saving revision…" else "Accept new program revision")}
                }
            }
        }
        notice?.let {
            Text(it,color=if(savedWorkout==null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)
        }
        savedWorkout?.let {
            Text("Approved workout: $it",style=MaterialTheme.typography.labelLarge)
        }
        // Once a NEW immutable approval was durably accepted, make the
        // ordinary Train entry reachable without copying proposal facts
        // into the UI or issuing any workout here. Train independently
        // revalidates the exact approval, owner and native history.
        val approvedId=repApprovedWorkout ?: savedWorkout
        if(approvedId!=null)
            PursuitPrimaryAction("Open approved workout in Train",enabled=!busy) {
                onOpenTrain(approvedId)
            }
        Text("Candidate only · no automatic target edits, no JavaScript fallback, no production history cutover.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
