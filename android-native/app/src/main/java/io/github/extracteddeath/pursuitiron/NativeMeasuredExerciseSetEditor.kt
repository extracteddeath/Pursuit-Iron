package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import android.os.SystemClock
import kotlinx.coroutines.delay
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMeasuredStoredSet
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID

/**
 * Material 3 entry for an already-issued, owner-verified measured set.
 * This is not a program author/issuer or a source of phantom History.
 * A host must supply a REAL native stored journal and persist every callback.
 */
@Composable
internal fun NativeMeasuredExerciseSetEditor(
    stored:NativeMeasuredStoredSet,
    busy:Boolean,
    onRecord:(MeasuredJournalOperation.Record)->Unit
) {
    val journal = stored.journal
    val plan = journal.immutablePlan
    key(stored.ownerKey, stored.athleteId, plan.workoutId, plan.setKey, journal.revision) {
        var editGeneration by rememberSaveable { mutableIntStateOf(0) }
        key(editGeneration) {
            MeasuredSetEditorContent(stored, busy, onRecord,
                onDiscardCorrection = { editGeneration++ })
        }
    }
}

@Composable
private fun MeasuredSetEditorContent(
    stored: NativeMeasuredStoredSet,
    busy: Boolean,
    onRecord: (MeasuredJournalOperation.Record) -> Unit,
    onDiscardCorrection: () -> Unit
) {
    val journal=stored.journal
    val plan=journal.immutablePlan
    val definition=MeasuredExerciseCatalogDomain.find(plan.exerciseId) ?:return
    val previous=journal.effectiveRecord?.operation as? MeasuredJournalOperation.Record
    val observed=previous?.actual
    val units=when(definition.kind) {
        MeasuredExerciseKind.HOLD_SECONDS ->
            listOf(MeasuredEntryUnit.SECONDS,MeasuredEntryUnit.MINUTES_SECONDS)
        MeasuredExerciseKind.CARRY_CENTIMETERS ->
            listOf(MeasuredEntryUnit.METERS,MeasuredEntryUnit.FEET,
                MeasuredEntryUnit.CENTIMETERS)
        MeasuredExerciseKind.BANDED_REPETITIONS ->listOf(MeasuredEntryUnit.REPETITIONS)
    }
    val initialUnit=if(previous==null)units.first() else when(definition.kind) {
        MeasuredExerciseKind.HOLD_SECONDS ->MeasuredEntryUnit.SECONDS
        MeasuredExerciseKind.CARRY_CENTIMETERS ->MeasuredEntryUnit.CENTIMETERS
        MeasuredExerciseKind.BANDED_REPETITIONS ->MeasuredEntryUnit.REPETITIONS
    }
    var unitName by rememberSaveable(plan.workoutId,plan.setKey,journal.revision) {
        mutableStateOf(initialUnit.name)
    }
    val unit=units.firstOrNull{it.name==unitName} ?:units.first()
    fun savedValue(side:MeasuredSide):String=observed?.sides?.firstOrNull{
        it.side==side
    }?.amount?.toString().orEmpty()
    var both by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(savedValue(MeasuredSide.BOTH))
    }
    var left by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(savedValue(MeasuredSide.LEFT))
    }
    var right by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(savedValue(MeasuredSide.RIGHT))
    }
    val loaded=definition.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
        definition.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
    var actualWeight by rememberSaveable(plan.workoutId,plan.setKey,journal.revision) {
        mutableStateOf(observed?.actualExternalLoad?.toPlainString().orEmpty())
    }
    var elapsed by rememberSaveable(plan.workoutId,plan.setKey,journal.revision) {
        mutableStateOf(observed?.elapsedSeconds?.toString().orEmpty())
    }
    var roundedApproved by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(false)
    }
    var editing by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(previous==null)
    }
    var confirmOutcome by rememberSaveable(plan.workoutId,plan.setKey,journal.revision) {
        mutableStateOf<MeasuredSetOutcome?>(null)
    }
    // The timer is a separate, explicitly operated observation aid.
    // Its value never becomes a performed set without athlete confirmation
    // and the same authenticated record callback as manual measurements.
    var stopwatchStarted by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableLongStateOf(-1L)
    }
    var stopwatchBootEpoch by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableLongStateOf(-1L)
    }
    var stopwatchStopped by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableLongStateOf(-1L)
    }
    var stopwatchSide by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf(plan.sides.sortedBy{it.ordinal}.first().name)
    }
    var stopwatchError by rememberSaveable(plan.workoutId,plan.setKey,journal.revision){
        mutableStateOf<String?>(null)
    }
    var nowMonotonic by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(stopwatchStarted) {
        while(stopwatchStarted>=0L) {
            nowMonotonic=SystemClock.elapsedRealtime()
            delay(125L)
        }
    }
    val stopwatch=MeasuredHoldStopwatchState(
        startedMonotonicMs=stopwatchStarted.takeIf{it>=0L},
        approximateBootEpochMs=stopwatchBootEpoch.takeIf{it>=0L},
        stoppedElapsedMs=stopwatchStopped.takeIf{it>=0L})
    val stopwatchDisplay=if(stopwatchStarted<0L && stopwatchStopped<0L)
        "00:00.0"
        else MeasuredHoldStopwatchDomain.display(
            stopwatch,nowMonotonic,System.currentTimeMillis())
    val stopwatchSeconds=MeasuredHoldStopwatchDomain.usableSeconds(stopwatch)
    val quantityBySide=buildMap {
        if(MeasuredSide.BOTH in plan.sides)put(MeasuredSide.BOTH,both)
        if(MeasuredSide.LEFT in plan.sides)put(MeasuredSide.LEFT,left)
        if(MeasuredSide.RIGHT in plan.sides)put(MeasuredSide.RIGHT,right)
    }
    val submitted=MeasuredExerciseEntryInput(
        quantityBySide,unit,if(loaded)actualWeight else null,
        if(loaded)plan.unit else null,plan.physicalSetupId,
        if(definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT)
            plan.bandId else null,
        if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
            elapsed else null)
    val completedReview=MeasuredExerciseEntryDomain.parse(
        plan,MeasuredSetOutcome.COMPLETED,submitted)
    val completed=completedReview as? MeasuredExerciseEntryDecision.Accepted
    // A deliberate Failed action means ZERO completed measurement. Do not
    // make athletes enter zero in every left/right/time/distance field;
    // preserve independently entered actual physical weight and band setup.
    val failedZero=if(unit==MeasuredEntryUnit.MINUTES_SECONDS)"0:00" else "0"
    val failedInput=submitted.copy(quantityBySide=plan.sides.associateWith{failedZero})
    val failed=MeasuredExerciseEntryDomain.parse(
        plan,MeasuredSetOutcome.FAILED_ATTEMPT,failedInput)
        as? MeasuredExerciseEntryDecision.Accepted
    val approximate=completed?.approximateDistanceConversion==true
    val roundingAcknowledged=!approximate || roundedApproved
    val label=when(unit) {
        MeasuredEntryUnit.SECONDS->"Seconds"
        MeasuredEntryUnit.MINUTES_SECONDS->"MM:SS"
        MeasuredEntryUnit.CENTIMETERS->"Centimeters"
        MeasuredEntryUnit.METERS->"Meters"
        MeasuredEntryUnit.FEET->"Feet"
        MeasuredEntryUnit.REPETITIONS->"Reps"
    }
    val target=when(definition.kind) {
        MeasuredExerciseKind.HOLD_SECONDS ->
            plan.targetAmount.toString()+" seconds"
        MeasuredExerciseKind.CARRY_CENTIMETERS ->
            BigDecimal(plan.targetAmount).movePointLeft(2)
                .stripTrailingZeros().toPlainString()+" meters"
        MeasuredExerciseKind.BANDED_REPETITIONS ->
            plan.targetAmount.toString()+" reps"
    }
    fun commit(outcome:MeasuredSetOutcome,actual:MeasuredExerciseActual?) {
        if(busy)return
        onRecord(MeasuredJournalOperation.Record(
            UUID.randomUUID().toString().replace("-",""),
            journal.revision,outcome,actual,previous?.eventId))
    }
    Surface(
        modifier=Modifier.fillMaxWidth(),
        shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
            Text(definition.displayName,style=MaterialTheme.typography.titleMedium)
            Text("Target "+target+" · record your actual result",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Physical setup "+plan.physicalSetupId,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(loaded)Text(
                "Target "+plan.intendedExternalLoad?.toPlainString()+" "+
                    plan.unit?.name?.lowercase()+" "+
                    if(definition.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND)
                        "per hand" else "added mass",
                style=MaterialTheme.typography.labelMedium)
            if(definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT)
                Text("Confirmed band "+plan.bandId+
                    " · no invented resistance weight",
                    style=MaterialTheme.typography.bodySmall)
            if(previous!=null && !editing) {
                val review=MeasuredWorkoutResultReviewDomain.review(plan,journal)
                Text("Saved: "+review.statusLabel,
                    style=MaterialTheme.typography.bodyMedium)
                review.actualSummary?.let { summary ->
                    Text(summary,style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled=!busy,onClick={editing=true}) {
                    Text("Edit saved result")
                }
            } else {
                if(units.size>1) {
                    FlowRow(
                        modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        units.forEach{choice->
                            FilterChip(
                                selected=choice==unit,enabled=!busy,
                                onClick={
                                    if(choice!=unit) {
                                        unitName=choice.name
                                        // A new unit never silently reinterprets
                                        // the old unsubmitted numeric text.
                                        both="";left="";right=""
                                        roundedApproved=false
                                    }
                                },
                                label={Text(when(choice) {
                                    MeasuredEntryUnit.SECONDS->"Sec"
                                    MeasuredEntryUnit.MINUTES_SECONDS->"MM:SS"
                                    MeasuredEntryUnit.CENTIMETERS->"Cm"
                                    MeasuredEntryUnit.METERS->"Meters"
                                    MeasuredEntryUnit.FEET->"Feet"
                                    MeasuredEntryUnit.REPETITIONS->"Reps"
                                })}
                            )
                        }
                    }
                    Text("Switching units clears unsaved quantities.",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(definition.kind==MeasuredExerciseKind.HOLD_SECONDS) {
                    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape=MaterialTheme.shapes.large,
                        modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(PursuitSpace.sm),
                            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                            Text("Hold stopwatch",
                                style=MaterialTheme.typography.titleMedium)
                            Text(stopwatchDisplay ?: "Time unavailable · reset",
                                style=MaterialTheme.typography.headlineMedium,
                                color=if(stopwatchDisplay==null)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface)
                            if(plan.sides.size>1) {
                                Text("Apply result to",
                                    style=MaterialTheme.typography.labelMedium)
                                FlowRow(
                                    modifier=Modifier.fillMaxWidth(),
                                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                    plan.sides.sortedBy{it.ordinal}.forEach{side->
                                        FilterChip(
                                            selected=stopwatchSide==side.name,
                                            enabled=!busy && !stopwatch.running,
                                            onClick={stopwatchSide=side.name},
                                            label={Text(side.name.lowercase()
                                                .replaceFirstChar{it.uppercase()})})
                                    }
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(
                                PursuitSpace.xs),modifier=Modifier.fillMaxWidth()) {
                                Button(enabled=!busy && !stopwatch.running,
                                    modifier=Modifier.weight(1f),onClick={
                                        val mono=SystemClock.elapsedRealtime()
                                        val state=MeasuredHoldStopwatchDomain.start(
                                            mono,System.currentTimeMillis())
                                        if(state!=null) {
                                            stopwatchStarted=checkNotNull(
                                                state.startedMonotonicMs)
                                            stopwatchBootEpoch=checkNotNull(
                                                state.approximateBootEpochMs)
                                            stopwatchStopped=-1L
                                            nowMonotonic=mono
                                            stopwatchError=null
                                        } else stopwatchError=
                                            "The stopwatch clock is unavailable."
                                    }) {Text(if(stopwatchStopped>=0L)
                                        "Restart" else "Start")}
                                OutlinedButton(enabled=!busy && stopwatch.running,
                                    modifier=Modifier.weight(1f),onClick={
                                        val stopped=MeasuredHoldStopwatchDomain.stop(
                                            stopwatch,SystemClock.elapsedRealtime(),
                                            System.currentTimeMillis())
                                        if(stopped!=null) {
                                            stopwatchStarted=-1L
                                            stopwatchBootEpoch=-1L
                                            stopwatchStopped=checkNotNull(
                                                stopped.stoppedElapsedMs)
                                            stopwatchError=null
                                        } else {
                                            stopwatchStarted=-1L
                                            stopwatchBootEpoch=-1L
                                            stopwatchStopped=-1L
                                            stopwatchError="The stopwatch clock "+
                                                "changed or expired. Enter the "+
                                                "actual hold time manually."
                                        }
                                    }) {Text("Stop")}
                            }
                            if(stopwatchSeconds!=null) {
                                OutlinedButton(
                                    enabled=!busy,
                                    modifier=Modifier.fillMaxWidth(),
                                    onClick={
                                        val text=if(unit==
                                            MeasuredEntryUnit.MINUTES_SECONDS)
                                            (stopwatchSeconds/60).toString()+":"+
                                                (stopwatchSeconds%60).toString()
                                                    .padStart(2,'0')
                                            else stopwatchSeconds.toString()
                                        when(stopwatchSide) {
                                            MeasuredSide.BOTH.name ->both=text
                                            MeasuredSide.LEFT.name ->left=text
                                            MeasuredSide.RIGHT.name ->right=text
                                        }
                                        roundedApproved=false
                                        stopwatchError=null
                                    }) {Text("Use ${stopwatchSeconds}s for "+
                                        stopwatchSide.lowercase()
                                            .replaceFirstChar{it.uppercase()})}
                            }
                            TextButton(enabled=!busy &&
                                (stopwatchStarted>=0L || stopwatchStopped>=0L),
                                onClick={
                                    stopwatchStarted=-1L
                                    stopwatchBootEpoch=-1L
                                    stopwatchStopped=-1L
                                    stopwatchError=null
                                }) {Text("Reset stopwatch")}
                            stopwatchError?.let{
                                Text(it,color=MaterialTheme.colorScheme.error,
                                    style=MaterialTheme.typography.bodySmall)
                            }
                            Text("Stopwatch seconds are rounded down. Use copies "+
                                "them into your unsaved field only; Record set "+
                                "still requires explicit confirmation.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                plan.sides.sortedBy{it.ordinal}.forEach{side->
                    val value=when(side) {
                        MeasuredSide.BOTH->both
                        MeasuredSide.LEFT->left
                        MeasuredSide.RIGHT->right
                    }
                    val sideLabel=when(side) {
                        MeasuredSide.BOTH->"Set"
                        MeasuredSide.LEFT->"Left"
                        MeasuredSide.RIGHT->"Right"
                    }
                    OutlinedTextField(
                        value=value,
                        onValueChange={next->
                            when(side) {
                                MeasuredSide.BOTH->both=next
                                MeasuredSide.LEFT->left=next
                                MeasuredSide.RIGHT->right=next
                            }
                            roundedApproved=false
                        },
                        enabled=!busy,modifier=Modifier.fillMaxWidth(),
                        singleLine=true,label={Text(sideLabel+" · "+label)},
                        keyboardOptions=KeyboardOptions(keyboardType=
                            if(unit==MeasuredEntryUnit.MINUTES_SECONDS)
                                KeyboardType.Text else KeyboardType.Decimal))
                }
                if(loaded) OutlinedTextField(
                    value=actualWeight,onValueChange={actualWeight=it},
                    enabled=!busy,modifier=Modifier.fillMaxWidth(),
                    singleLine=true,
                    label={Text("Actual weight · "+plan.unit?.name)},
                    supportingText={Text(
                        "Enter the real weight used; target is never auto-recorded.")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
                    OutlinedTextField(
                        value=elapsed,onValueChange={elapsed=it},
                        enabled=!busy,modifier=Modifier.fillMaxWidth(),
                        label={Text("Carry elapsed seconds (optional)")},
                        singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                if(completed != null && approximate) {
                    val distance = completed.convertedCentimetersBySide.entries.joinToString(" · ") { (side, cm) ->
                        when (side) {
                            MeasuredSide.BOTH -> "${cm} cm"
                            MeasuredSide.LEFT -> "Left ${cm} cm"
                            MeasuredSide.RIGHT -> "Right ${cm} cm"
                        }
                    }
                    Text("Distance to save: $distance (rounded to the nearest centimeter).",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                        Checkbox(
                            checked=roundedApproved,enabled=!busy,
                            onCheckedChange={roundedApproved=it})
                        Text("I understand the distance rounding",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
                val invalid=(completedReview as? MeasuredExerciseEntryDecision.Rejected)
                    ?.reason
                if(invalid!=null && quantityBySide.values.any{it.isNotBlank()})
                    Text(measuredInputHelp(invalid),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.error)
                Text("Only a confirmed native journal receipt saves this result.",
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(enabled=!busy && completed!=null && roundingAcknowledged,
                    modifier=Modifier.fillMaxWidth(),
                    onClick={
                        val candidate=completed ?:return@Button
                        commit(MeasuredSetOutcome.COMPLETED,candidate.actual)
                    }) {Text(if(previous==null)"Record set" else "Save correction")}
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    OutlinedButton(enabled=!busy,modifier=Modifier.weight(1f),
                        onClick={confirmOutcome=MeasuredSetOutcome.SKIPPED}) {
                        Text("Skip")
                    }
                    OutlinedButton(
                        enabled=!busy && failed!=null,
                        modifier=Modifier.weight(1f),
                        onClick={confirmOutcome=MeasuredSetOutcome.FAILED_ATTEMPT}) {
                        Text("Failed")
                    }
                }
                if(previous!=null)TextButton(
                    enabled=!busy,onClick=onDiscardCorrection) {
                    Text("Cancel correction")
                }
            }
        }
    }
    val pending=confirmOutcome
    if(pending!=null)AlertDialog(
        onDismissRequest={if(!busy)confirmOutcome=null},
        title={Text(if(pending==MeasuredSetOutcome.SKIPPED)
            "Skip this set?" else "Record a failed attempt?")},
        text={Text(if(pending==MeasuredSetOutcome.SKIPPED)
            "Skipping logs an explicit outcome, not completed work."
            else "This logs zero completed seconds, distance or reps at the "+
                "actual attempted setup and weight. It cannot earn progression.")},
        confirmButton={
            Button(enabled=!busy && (pending==MeasuredSetOutcome.SKIPPED ||
                failed!=null),
                onClick={
                    confirmOutcome=null
                    commit(pending,
                        if(pending==MeasuredSetOutcome.SKIPPED)null else failed?.actual)
                }) {Text("Confirm")}
        },
        dismissButton={
            TextButton(enabled=!busy,onClick={confirmOutcome=null}) {
                Text("Cancel")
            }
        }
    )
}


private fun measuredInputHelp(reason:MeasuredEntryRefusal):String=when(reason) {
    MeasuredEntryRefusal.UNAPPROVED_PLAN ->
        "This set needs a verified workout source."
    MeasuredEntryRefusal.MISSING_OR_EXTRA_SIDE ->
        "Enter a measured result for every required side."
    MeasuredEntryRefusal.WRONG_QUANTITY_UNIT ->
        "Choose a supported measurement unit."
    MeasuredEntryRefusal.INVALID_QUANTITY ->
        "Check the time, distance or repetition amount."
    MeasuredEntryRefusal.WRONG_PHYSICAL_SETUP ->
        "Physical equipment changed. Review the workout setup first."
    MeasuredEntryRefusal.LOAD_ENTRY_REQUIRED ->
        "Enter the weight actually used."
    MeasuredEntryRefusal.UNVERIFIED_LOAD ->
        "The entered weight is not available on this verified equipment."
    MeasuredEntryRefusal.WRONG_OR_UNVERIFIED_BAND ->
        "Select the gym-approved band setup first."
    MeasuredEntryRefusal.INVALID_ELAPSED_TIME ->
        "Optional carry elapsed time must be whole seconds."
    MeasuredEntryRefusal.INVALID_ACTUAL ->
        "This measurement does not match the issued exercise."
}
