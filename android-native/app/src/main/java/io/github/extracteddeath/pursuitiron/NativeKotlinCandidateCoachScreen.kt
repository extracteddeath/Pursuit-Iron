package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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

/**
 * Isolated Kotlin-only coaching review. Deliberately never mounted from
 * NativeRuntimeHost, and never silently saves a plan or issues a workout.
 * The native writer independently re-reads approval, complete corrected
 * history, and every affected workout day at the moment of acceptance.
 */
@Composable
internal fun NativeKotlinCandidateCoachScreen(
    host: NativeKotlinCandidateHost
) {
    val scope=rememberCoroutineScope()
    var exerciseId by rememberSaveable { mutableStateOf("") }
    var maximumStep by rememberSaveable { mutableStateOf("5") }
    var readiness by remember { mutableStateOf(CoachReadiness.NORMAL) }
    var restricted by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var reviewed by remember {
        mutableStateOf<NativeOwnerLoadReviewResult.Review?>(null)
    }
    var notice by remember { mutableStateOf<String?>(null) }
    var savedWorkout by remember { mutableStateOf<String?>(null) }
    val step=remember(maximumStep) {
        runCatching { BigDecimal(maximumStep.trim()) }.getOrNull()
            ?.takeIf { it > BigDecimal.ZERO &&
                it <= BigDecimal("100000") &&
                it.stripTrailingZeros().scale() <= 3 }
    }
    val permitted=exerciseId.matches(
        Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$"))
    fun invalidate() {
        reviewed=null
        checked=false
        savedWorkout=null
        notice=null
    }
    val verified=reviewed?.coaching as? NativeCurrentLoadReviewResult.Reviewed
    val proposal=verified?.coaching?.takeIf {
        it.status==CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW &&
            it.candidateProgram!=null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageHeading("KOTLIN COACH · ISOLATED",
            "Review your next load",
            "A proposed program change based on your real saved native workouts. Nothing changes automatically.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Exercise and equipment",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(value=exerciseId,
                    onValueChange={ exerciseId=it.trim();invalidate() },
                    label={Text("Approved exercise ID")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=maximumStep,
                    onValueChange={ maximumStep=it;invalidate() },
                    label={Text("Maximum approved increase (gym units)")},
                    singleLine=true,modifier=Modifier.fillMaxWidth(),
                    isError=step==null)
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
            }
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
                    Text("The approved program retains ${candidate.candidateProgram.exercises.size} exercises, with the reviewed load applied to each affected visit. The prior issued workout and saved results will remain unchanged.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=checked,
                            onCheckedChange={checked=it})
                        Text("I reviewed the proposed loads and all affected days. Save a new approved Kotlin program revision.")
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
                                    "receipt-$next",selectedDay,true)
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
        Text("Candidate only · no automatic target edits, no JavaScript fallback, no production history cutover.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
