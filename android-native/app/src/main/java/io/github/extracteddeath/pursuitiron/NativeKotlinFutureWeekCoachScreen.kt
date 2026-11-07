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
 * ISOLATED, Kotlin-only Future Week surface. No JS/WebView or production path.
 * Native owner-fenced stores, not this UI's state, decide every permission.
 * A saved approval is not an issued workout; issue requires its own explicit
 * choice, a cold-recoverable preparation and complete native revalidation.
 */
@Composable
internal fun NativeKotlinFutureWeekCoachScreen(
    host:NativeKotlinCandidateHost,
    cycleController:NativeKotlinCustomCycleController,
    onOpenProgram:()->Unit
) {
    val scope=rememberCoroutineScope()
    val cycleState by cycleController.state.collectAsState()
    var activation by remember(host) {
        mutableStateOf<NativeFutureActivatedCoaching?>(null)
    }
    var activationNotice by remember(host) {mutableStateOf<String?>(null)}
    var loading by remember(host) {mutableStateOf(true)}
    var busy by remember {mutableStateOf(false)}
    var absoluteWeek by rememberSaveable {mutableIntStateOf(1)}
    var weekday by rememberSaveable {mutableIntStateOf(0)}
    var slotId by rememberSaveable {mutableStateOf("")}
    var maxStepText by rememberSaveable {mutableStateOf("5")}
    var readiness by remember {mutableStateOf(CoachReadiness.NORMAL)}
    var restricted by rememberSaveable {mutableStateOf(false)}
    var session by remember {mutableStateOf<CustomWorkoutDaySnapshot?>(null)}
    var offer by remember {mutableStateOf<NativeFutureWeekLoadOptionResult.Ready?>(null)}
    var consentPreview by remember {mutableStateOf<NativeFutureWeekConsentPreview.Ready?>(null)}
    var accepted by remember {mutableStateOf<NativeFutureWeekLoadConsentReceipt?>(null)}
    var handoff by remember {
        mutableStateOf<NativeAcceptedFutureWeekLoadHandoff.Ready?>(null)
    }
    var prepared by remember {mutableStateOf<NativeCustomPreparedWorkout?>(null)}
    var issued by remember {mutableStateOf<NativeCustomIssuedJournal?>(null)}
    var confirmAcceptance by remember {mutableStateOf(false)}
    var confirmIssue by remember {mutableStateOf(false)}
    var notice by remember {mutableStateOf<String?>(null)}
    var assistance by remember {mutableStateOf<CoachAssistancePolicy?>(null)}

    fun invalidate() {
        session=null;slotId="";offer=null;consentPreview=null;accepted=null
        handoff=null;prepared=null;issued=null;confirmAcceptance=false
        confirmIssue=false;notice=null
    }
    fun clearOffer() {
        offer=null;consentPreview=null;accepted=null;handoff=null
        prepared=null;issued=null;confirmAcceptance=false;confirmIssue=false
        notice=null
    }
    LaunchedEffect(host) {
        loading=true
        val result=withContext(Dispatchers.IO) {
            runCatching {
                host.readFutureBlockActivationCommit() to
                    host.readCoachAssistance()
            }.getOrNull()
        }
        activation=(result?.first as? NativeFutureCommitReview.Applied)?.context
        assistance=(result?.second as? NativeCoachSettingsRead.Current)
            ?.settings?.policy
        activationNotice=if(activation==null)
            "No committed future training block was found. Complete the native activation and athlete signoff before reviewing a future-week load."
        else null
        loading=false
    }
    val block=cycleState.draft?.blocks?.singleOrNull {
        it.blockId==activation?.blockId &&
            cycleState.draft?.cycleId==activation?.cycleId
    }
    val savedInputs=cycleState.reviewedCoachInputs?.takeIf {
        cycleState.coachingAudit!=null &&
            cycleState.coachingAudit?.auditedCycleRevision==
                cycleState.draft?.revision &&
            cycleState.auditSourceRevision!=null
    }
    val allowed=activation!=null && block!=null && savedInputs!=null &&
        assistance?.offersProgression==true
    val maxStep=remember(maxStepText) {
        runCatching{BigDecimal(maxStepText.trim())}.getOrNull()
            ?.takeIf{it>BigDecimal.ZERO && it<=BigDecimal("10000") &&
                it.stripTrailingZeros().scale()<=3}
    }
    LaunchedEffect(activation?.cycleId,activation?.blockId,block?.blockId) {
        absoluteWeek=activation?.firstWeek ?: 1
        weekday=block?.days?.firstOrNull()?.weekday ?: 0
        invalidate()
    }
    val active=activation
    val contextInputs=savedInputs
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        PageHeading("KOTLIN COACH · ISOLATED",
            "Future-week progression",
            "Review an earned change to one future workout. Your authored cycle and completed workouts stay unchanged.")
        if(loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if(active==null) {
            PursuitCard {Text(activationNotice ?: "Activation not available.")}
        } else {
            PursuitCard {
                Text("Activated training block",
                    style=MaterialTheme.typography.titleMedium)
                Text("${active.cycleId} · ${active.blockId}",
                    style=MaterialTheme.typography.bodyMedium)
                Text("Starts in week ${active.firstWeek}. The athlete-approved source revision is ${active.sourceRevision}.",
                    style=MaterialTheme.typography.bodySmall)
                if(block==null) {
                    Text("Open the exact saved custom cycle to match this activation.",
                        color=MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick=onOpenProgram){Text("Open my program")}
                } else if(contextInputs==null) {
                    Text("The coach needs a current, source-backed full-cycle audit. Open your custom program, run its real coaching review, then return here. No default volume or timing policy will be invented.",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick=onOpenProgram){Text("Review coaching policy")}
                } else if(assistance?.offersProgression!=true) {
                    Text("Progression coaching is off. Continue the authored workout without suggestions.")
                }
            }
        }
        if(active!=null && block!=null) {
            PursuitCard {
                Text("Select the future training day",
                    style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (active.firstWeek until (active.firstWeek+block.weeks))
                        .filter{it in 1..104}.forEach{week->
                        FilterChip(selected=absoluteWeek==week,
                            onClick={if(!busy) {
                                absoluteWeek=week;invalidate()
                            }},enabled=!busy,
                            label={Text("Week $week")})
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    block.days.forEach{day->
                        FilterChip(selected=weekday==day.weekday,
                            onClick={if(!busy) {
                                weekday=day.weekday;invalidate()
                            }},enabled=!busy,
                            label={Text(day.label)})
                    }
                }
                Button(enabled=allowed&&!busy,onClick={
                    val inputs=contextInputs ?:return@Button
                    val cycle=active.cycleId
                    invalidate()
                    busy=true
                    scope.launch {
                        val result=withContext(Dispatchers.IO) {
                            runCatching {
                                host.previewActivatedFutureWorkout(
                                    inputs,absoluteWeek,weekday)
                            }.getOrNull()
                        }
                        session=(result as? CustomWorkoutSnapshotResult.Preview)
                            ?.snapshot?.takeIf {
                                it.stamp.cycleId==cycle &&
                                it.blockId==active.blockId &&
                                it.absoluteWeek==absoluteWeek &&
                                it.weekday==weekday
                            }
                        notice=if(session==null)
                            "The source-backed workout could not be verified: ${result ?: "storage unavailable"}. No change was made."
                        else null
                        busy=false
                    }
                }) {Text(if(busy)"Checking workout…" else "Inspect exact workout")}
                session?.let {snap->
                    Text(snap.dayLabel,style=MaterialTheme.typography.titleMedium)
                    Text("Select one reviewed exercise slot. Other exercises and training days are not changed.",
                        style=MaterialTheme.typography.bodySmall)
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        snap.slots.forEach {slot->
                            FilterChip(selected=slotId==slot.slotId,
                                enabled=!busy,
                                onClick={slotId=slot.slotId;clearOffer()},
                                label={Text("${slot.exerciseId.replace('_',' ')} · ${slot.slotId}")})
                        }
                    }
                }
            }
        }
        if(session!=null && allowed && contextInputs!=null && active!=null) {
            PursuitCard {
                Text("Coaching safety limits",
                    style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(value=maxStepText,
                    enabled=!busy,
                    onValueChange={maxStepText=it;clearOffer()},
                    isError=maxStep==null,
                    label={Text("Maximum approved increase · gym units")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                if(assistance?.offersFatigueAdvice==true) {
                    Text("How are you feeling?",
                        style=MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        CoachReadiness.entries.forEach {value->
                            FilterChip(selected=readiness==value,enabled=!busy,
                                onClick={readiness=value;clearOffer()},
                                label={Text(value.name.lowercase()
                                    .replaceFirstChar{it.uppercase()})})
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=restricted,enabled=!busy,
                        onCheckedChange={restricted=it;clearOffer()})
                    Text("Movement is painful or restricted")
                }
                Button(enabled=!busy&&slotId.isNotBlank()&&maxStep!=null,
                    onClick={
                        val chosen=slotId
                        val increase=checkNotNull(maxStep)
                        clearOffer()
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO) {
                                runCatching {
                                    val proposal=host.previewFutureCycleWeekLoadOption(
                                        absoluteWeek,weekday,chosen,contextInputs,
                                        readiness,restricted,increase)
                                    val consent=if(proposal is NativeFutureWeekLoadOptionResult.Ready)
                                        host.previewFutureWeekLoadConsent(
                                            active.cycleId,active.blockId,
                                            absoluteWeek,weekday,chosen,
                                            contextInputs,readiness,restricted,increase)
                                    else null
                                    proposal to consent
                                }.getOrNull()
                            }
                            val proposal=result?.first as? NativeFutureWeekLoadOptionResult.Ready
                            val consent=result?.second as? NativeFutureWeekConsentPreview.Ready
                            if(proposal!=null && consent!=null &&
                                proposal.reviewFingerprint==consent.fingerprint &&
                                proposal.previousLoad.compareTo(consent.previousLoad)==0 &&
                                proposal.candidateLoad.compareTo(consent.proposedLoad)==0) {
                                offer=proposal;consentPreview=consent;notice=null
                            } else {
                                notice="No currently earned load change: ${result?.first ?: "verification unavailable"}. Nothing was saved."
                            }
                            busy=false
                        }
                    }) {Text(if(busy)"Rechecking history…" else "Review future load")}
                offer?.let {candidate->
                    HorizontalDivider()
                    Text("Earned weight increase",
                        style=MaterialTheme.typography.titleLarge)
                    Text("${candidate.previousLoad.stripTrailingZeros().toPlainString()} → ${candidate.candidateLoad.stripTrailingZeros().toPlainString()}",
                        style=MaterialTheme.typography.headlineSmall)
                    Text("Week ${candidate.affectedAbsoluteWeek} · ${candidate.affectedSlotId} · verified against ${candidate.comparedWorkoutIds.size} recorded workouts.",
                        style=MaterialTheme.typography.bodySmall)
                    Text("Only the selected future-week working sets can change. The full cycle, stock, permissions, and saved corrections were audited.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy&&consentPreview!=null,
                        onClick={confirmAcceptance=true}) {
                        Text("Review and accept this load")}
                }
                accepted?.let {receipt->
                    HorizontalDivider()
                    Text("Athlete acceptance saved · revision ${receipt.recordRevision}",
                        style=MaterialTheme.typography.titleMedium)
                    OutlinedButton(enabled=!busy,onClick={
                        busy=true
                        scope.launch{
                            val check=withContext(Dispatchers.IO){
                                runCatching{host.previewAcceptedFutureWeekLoad(
                                    active.cycleId,active.blockId,
                                    absoluteWeek,weekday,receipt.slotId,
                                    contextInputs)}.getOrNull()
                            }
                            handoff=check as? NativeAcceptedFutureWeekLoadHandoff.Ready
                            notice=if(handoff==null)
                                "Accepted proposal is no longer current: ${check ?: "unavailable"}. Review again; no workout changed."
                            else null
                            busy=false
                        }
                    }){Text("Review accepted workout")}
                    if(prepared==null) {
                        TextButton(enabled=!busy,onClick={
                            busy=true
                            scope.launch {
                                val result=withContext(Dispatchers.IO){
                                    runCatching {
                                        host.revokeFutureWeekLoadConsent(
                                            active.cycleId,active.blockId,
                                            absoluteWeek,weekday,receipt.slotId,
                                            receipt.recordRevision)
                                    }.getOrNull()
                                }
                                if(result is NativeFutureWeekConsentWrite.Revoked){
                                    clearOffer()
                                    notice="Future-week consent revoked. The original custom training cycle is unchanged."
                                } else notice="Revocation refused: ${result ?: "storage unavailable"}."
                                busy=false
                            }
                        }){Text("Revoke future-week consent")}
                    }
                }
                handoff?.let {valid->
                    HorizontalDivider()
                    Text("Exact workout change",
                        style=MaterialTheme.typography.titleMedium)
                    val original=valid.original.slots.singleOrNull{
                        it.slotId==valid.slotId}
                    val projected=valid.projected.slots.singleOrNull{
                        it.slotId==valid.slotId}
                    original?.sets?.zip(projected?.sets.orEmpty())?.forEachIndexed{
                        index,pair->
                        val old=(pair.first.prescription.load as? DraftSetLoad.Verified)
                            ?.value
                        val changed=(pair.second.prescription.load as? DraftSetLoad.Verified)
                            ?.value
                        Text("Set ${index+1}: ${old ?: "unverified"} → ${changed ?: "unverified"} · ${pair.second.prescription.reps.minimum}–${pair.second.prescription.reps.maximum} reps",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                    Text("No changes to reps, RIR, warmups, other slots or the authored cycle.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy&&prepared==null,
                        onClick={
                            busy=true
                            val workoutId="custom-"+UUID.randomUUID().toString()
                            scope.launch {
                                val result=withContext(Dispatchers.IO){
                                    runCatching{host.prepareActivatedFutureWorkout(
                                        workoutId,active.cycleId,
                                        absoluteWeek,weekday,contextInputs,
                                        valid.slotId)}.getOrNull()
                                }
                                prepared=(result as? NativeCustomPrepareResult.Prepared)
                                    ?.workout?.takeIf{
                                        it.futureWeekApprovalFingerprint==valid.fingerprint &&
                                        it.futureWeekSlotId==valid.slotId
                                    }
                                notice=if(prepared==null)
                                    "Preparation refused: ${result ?: "storage unavailable"}. No workout issued."
                                else "Exact accepted workout prepared and saved. Issuance still requires a separate confirmation."
                                busy=false
                            }
                        }){Text("Prepare this approved workout")}
                }
                prepared?.let {workout->
                    Text("Prepared · ${workout.workoutId}",
                        style=MaterialTheme.typography.labelLarge)
                    if(issued==null)
                        Button(enabled=!busy,onClick={confirmIssue=true}) {
                            Text("Issue reviewed workout")}
                }
                issued?.let {journal->
                    Text("Issued native custom journal · ${journal.journal.workoutId}",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Targets are now immutable. Custom journal set logging and Finish still require full on-device UI integration; the legacy workout screen is not a fallback.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        notice?.let {
            Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
        }
        Text("Candidate journey only · all review, consent, preparation and issuance decisions are independently checked by the Kotlin owner.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)
    }
    if(confirmAcceptance && active!=null && contextInputs!=null) {
        val offered=offer;val stamp=consentPreview
        AlertDialog(onDismissRequest={if(!busy)confirmAcceptance=false},
            title={Text("Accept future-week weight?")},
            text={Text("Apply exactly ${offered?.previousLoad} → ${offered?.candidateLoad} to the future-week working sets for ${offered?.affectedSlotId}. Your existing cycle and all issued workouts stay unchanged. This does not start training.")},
            confirmButton={
                Button(enabled=!busy&&offered!=null&&stamp!=null,
                    onClick={
                        confirmAcceptance=false
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO) {
                                runCatching{
                                    host.acceptFutureWeekLoadConsent(
                                        active.cycleId,active.blockId,
                                        absoluteWeek,weekday,slotId,
                                        contextInputs,readiness,restricted,
                                        checkNotNull(maxStep),
                                        checkNotNull(stamp).fingerprint,
                                        stamp.recordRevision,true)
                                }.getOrNull()
                            }
                            accepted=(result as? NativeFutureWeekConsentWrite.Accepted)
                                ?.receipt
                            notice=if(accepted==null)
                                "Acceptance refused: ${result ?: "storage unavailable"}. Re-run the review."
                            else "Future-week load accepted. Review the exact session before preparing."
                            if(accepted==null){offer=null;consentPreview=null}
                            busy=false
                        }
                    }){Text("Accept exact weight")}
            },dismissButton={
                TextButton(enabled=!busy,onClick={confirmAcceptance=false}){
                    Text("Cancel")}
            })
    }
    if(confirmIssue && active!=null && contextInputs!=null) {
        val current=prepared
        AlertDialog(onDismissRequest={if(!busy)confirmIssue=false},
            title={Text("Issue immutable workout?")},
            text={Text("This creates a native journal for ${current?.workoutId}. Every accepted target is rechecked against its current source and consent. Once issued, targets cannot be rewritten.")},
            confirmButton={
                Button(enabled=!busy&&current!=null,
                    onClick={
                        confirmIssue=false
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO){
                                runCatching{host.issueActivatedFutureWorkout(
                                    checkNotNull(current).workoutId,
                                    active.cycleId,absoluteWeek,weekday,
                                    contextInputs)}.getOrNull()
                            }
                            issued=(result as? NativeCustomJournalResult.Durable)
                                ?.issued
                            notice=if(issued==null)
                                "Issuance refused: ${result ?: "storage unavailable"}. Your prepared workout remains stored."
                            else "Workout issued. Existing training history was not rewritten."
                            busy=false
                        }
                    }){Text("Issue this workout")}
            },dismissButton={
                TextButton(enabled=!busy,onClick={confirmIssue=false}){
                    Text("Cancel")}
            })
    }
}
