package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** The native owner rechecks every displayed action; no UI badge issues targets. */
@Composable
internal fun NativeKotlinCustomCycleCalibrationPanel(
    controller:NativeKotlinCustomCycleController,state:NativeKotlinCustomCycleState,
    onOpenIssuedWorkout:(String)->Unit
) {
    var confirmAccept by remember{mutableStateOf(false)}
    var confirmIssue by remember{mutableStateOf(false)}
    var confirmReset by remember{mutableStateOf(false)}
    var confirmAuthoredReturn by remember{mutableStateOf(false)}
    val authoredReturn=state.authoredReturnReview?.takeIf{
        it.authored.stamp.cycleRevision==state.draft?.revision&&
            it.authored.stamp.gymCatalogSourceRevision==state.sourceRevision&&
            (it.authored==state.preparedWorkout?.snapshot||
                it.authored.copy(declaredNonSetSeconds=state.authoredPreview?.declaredNonSetSeconds)==state.authoredPreview)
    }
    val offer=state.calibrationReview?.takeIf {
        it.original.stamp.cycleRevision==state.draft?.revision &&
            it.original.stamp.gymCatalogSourceRevision==state.sourceRevision &&
            (state.calibrationReceipt!=null ||
                it.original.copy(declaredNonSetSeconds=state.authoredPreview?.declaredNonSetSeconds)==state.authoredPreview)
    }
    val ordinaryPrepared=state.preparedWorkout?.takeIf {
        it.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
            it.calibrationApprovalFingerprint==null &&
            it.authoredReturnFingerprint==null &&
            it.loadApprovalFingerprint==null &&
            it.repApprovalFingerprint==null &&
            it.futureWeekApprovalFingerprint==null &&
            !it.resetToAuthored &&
            state.consentBoundPreview!=null &&
            state.consentBoundPreview.session==it.snapshot &&
            state.consentBoundPreview.receipt.recordRevision==it.consentRevision &&
            state.consentBoundPreview.receipt.fingerprintSha256==it.consentFingerprint &&
            state.draft?.revision==it.snapshot.stamp.cycleRevision
    }
    val prepared=state.preparedWorkout?.takeIf {
        it.calibrationApprovalFingerprint!=null||it.resetToAuthored||it.authoredReturnFingerprint!=null
    } ?:ordinaryPrepared
    val reset=state.calibrationReset
    LaunchedEffect(offer?.receipt?.fingerprint,prepared,reset?.preparedFingerprint,authoredReturn?.fingerprint) {
        confirmAccept=false;confirmIssue=false;confirmReset=false;confirmAuthoredReturn=false
    }
    val readyPolicy=state.reviewedCoachInputs!=null
    PursuitCard {
        Text("Reviewed workout weights",style=MaterialTheme.typography.titleLarge)
        OutlinedButton(enabled=!state.busy,onClick=controller::findPendingCalibrations) {
            Text("Find saved approvals and preparations")
        }
        state.pendingCalibrationAcceptances.forEach {receipt->
            OutlinedButton(enabled=!state.busy&&readyPolicy,onClick={controller.resumeAcceptedCalibration(receipt)}) {
                Text((if(receipt.kind==NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE)
                    "Recheck earned weights" else "Recheck accepted weights")+" · week ${receipt.absoluteWeek}")
            }
        }
        if(state.pendingCalibrationAcceptances.isNotEmpty()&&!readyPolicy)
            Text("Run your current full-cycle audit to recheck a saved acceptance.",style=MaterialTheme.typography.bodySmall)
        state.pendingCalibrations.forEach {workout->
            OutlinedButton(enabled=!state.busy,onClick={controller.reopenPreparedWorkout(workout.workoutId)}) {
                Text("Reopen week ${workout.snapshot.absoluteWeek} · ${workout.snapshot.dayLabel}")
            }
        }
        val reviewedDay=state.dayCoaching?.takeIf{review->
            review.nextWorkout?.copy(declaredNonSetSeconds=state.authoredPreview?.declaredNonSetSeconds)==
                state.authoredPreview&&review.nextWorkout!=null
        }
        if((reviewedDay?.authoredLoadReversion?.requiresAthleteReview==true||
                prepared?.authoredReturnFingerprint!=null||
                state.preparationRefusal==NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED)&&
            state.issuedCalibrationId==null) {
            OutlinedButton(enabled=!state.busy&&readyPolicy,
                onClick=controller::reviewAuthoredLoadReturn){Text("Review heavier authored weights")}
        }
        authoredReturn?.let {review->
            HorizontalDivider()
            Text("Choose authored weights",style=MaterialTheme.typography.titleMedium)
            Text("These weights are higher than the previous same-day workout. Choosing them does not mean an increase was earned.",
                style=MaterialTheme.typography.bodySmall)
            review.higherSlots.forEach {slot->
                Text(slot.exerciseId.replace('_',' '),style=MaterialTheme.typography.titleSmall)
                slot.higherSets.forEach {set->
                    val basis=if(set.basis==LoadBasis.DUMBBELL_PER_HAND)" per hand" else ""
                    Text("Set ${set.ordinal}: previously prescribed ${set.lastIssuedLoad} → authored ${set.authoredLoad} ${set.unit.name.lowercase()}$basis",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
            Text("All ${review.auditedWeeks} cycle weeks checked. Your performed sets remain in history.",
                style=MaterialTheme.typography.bodySmall)
            Button(enabled=!state.busy&&readyPolicy,onClick={confirmAuthoredReturn=true}) {
                Text("Choose these authored targets")
            }
        }
        offer?.let {
            val earned=it.receipt.kind==NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE
            Text("All ${it.auditedWeeks} cycle weeks audited",
                style=MaterialTheme.typography.titleMedium)
            if(earned) {
                Text("Earned increase from the reduced baseline",style=MaterialTheme.typography.titleMedium)
                Text("Two comparable same-day workouts support this next equipment step.",
                    style=MaterialTheme.typography.bodySmall)
                if(it.receipt.assumedGoalRirSetCount>0)
                    Text("Unreported RIR used the original goal for ${it.receipt.assumedGoalRirSetCount} sets. Actual RIR remains unreported.",
                        style=MaterialTheme.typography.bodySmall)
            }
            it.receipt.setKeys.indices.forEach {i->
                val basis=if(it.receipt.basis==LoadBasis.DUMBBELL_PER_HAND)" per hand" else ""
                val from=if(earned)it.receipt.baselineLoads[i] else it.receipt.authoredLoads[i]
                Text("Working set ${i+1}: $from → ${it.receipt.proposedLoads[i]} ${it.receipt.unit.name.lowercase()}$basis")
            }
            if(state.calibrationReceipt==null) {
                Button(enabled=!state.busy,onClick={confirmAccept=true}) {
                    Text(if(earned)"Review and accept earned weights" else "Review and accept starting weights")
                }
            } else {
                Text("Athlete acceptance saved. Your authored cycle is unchanged.",
                    style=MaterialTheme.typography.bodySmall)
                if(prepared==null) Button(enabled=!state.busy&&readyPolicy,
                    onClick=controller::prepareBlockCalibration){Text("Prepare accepted workout")}
            }
        }
        prepared?.let {workout->
            HorizontalDivider()
            if(workout==ordinaryPrepared)
                Text("Regular workout · no special load-calibration change required",
                    style=MaterialTheme.typography.bodyMedium)
            Text("Prepared · week ${workout.snapshot.absoluteWeek} · ${workout.snapshot.dayLabel}",
                style=MaterialTheme.typography.titleMedium)
            if(workout.authoredReturnFingerprint!=null)Text("Your confirmed choice of heavier authored weights. History will be checked again before issuance.",
                style=MaterialTheme.typography.bodySmall)
            workout.snapshot.slots.forEach {slot->
                Text(slot.exerciseId.replace('_',' '),style=MaterialTheme.typography.titleSmall)
                slot.sets.forEach {set->
                    val load=set.prescription.load as? DraftSetLoad.Verified
                    val basis=if(load?.basis==LoadBasis.DUMBBELL_PER_HAND)" per hand" else ""
                    Text("Set ${set.ordinalInSlot}: ${load?.value ?: "bodyweight"} ${load?.unit?.name?.lowercase().orEmpty()}$basis · ${set.prescription.reps.minimum}–${set.prescription.reps.maximum} reps · goal ${set.prescription.targetRir} RIR",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            if(!readyPolicy)Text("Run the current full-cycle audit before issuing or returning to authored targets.",
                style=MaterialTheme.typography.bodySmall)
            if(state.issuedCalibrationId==null) {
                Button(enabled=!state.busy&&readyPolicy,onClick={confirmIssue=true}){Text("Issue this exact workout")}
                if(workout.calibrationApprovalFingerprint!=null||workout.authoredReturnFingerprint!=null)OutlinedButton(
                    enabled=!state.busy&&readyPolicy,onClick=controller::reviewCalibrationReset) {
                    Text("Review original authored targets")
                }
            }
        }
        reset?.let {
            HorizontalDivider()
            Text("Original authored workout",style=MaterialTheme.typography.titleMedium)
            it.authored.slots.forEach {slot->slot.sets.forEach {set->
                val load=set.prescription.load as? DraftSetLoad.Verified
                val basis=if(load?.basis==LoadBasis.DUMBBELL_PER_HAND)" per hand" else ""
                Text("${slot.exerciseId.replace('_',' ')} · set ${set.ordinalInSlot}: ${load?.value ?: "bodyweight"} ${load?.unit?.name?.lowercase().orEmpty()}$basis",
                    style=MaterialTheme.typography.bodySmall)
            }}
            OutlinedButton(enabled=!state.busy,onClick={confirmReset=true}){Text("Return to these authored targets")}
        }
        state.issuedCalibrationId?.let {id->
            Text("Workout issued. Targets are immutable.",style=MaterialTheme.typography.bodySmall)
            Button(enabled=!state.busy,onClick={onOpenIssuedWorkout(id)}){Text("Open workout logger")}
        }
    }
    if(confirmAccept&&offer!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmAccept=false},title={Text(
            if(offer.receipt.kind==NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE)
                "Accept this earned weight increase?" else "Accept these starting weights?")},
        text={Text("Save only the displayed per-set weight changes for week ${offer.receipt.absoluteWeek}, ${offer.original.dayLabel}. This does not start a workout or edit your cycle.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmAccept=false;controller.acceptBlockCalibration()}){Text("Accept exact weights")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmAccept=false}){Text("Cancel")}})
    if(confirmIssue&&prepared!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmIssue=false},title={Text("Issue this exact workout?")},
        text={Text("Recheck saved history, consent and the prepared targets for ${prepared.snapshot.dayLabel}. Once issued, targets cannot be rewritten.")},
        confirmButton={Button(enabled=!state.busy,onClick={
            confirmIssue=false
            if(prepared==ordinaryPrepared)controller.issueOrdinaryPreparedWorkout()
            else controller.issueBlockCalibration()
        }){Text("Issue workout")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmIssue=false}){Text("Cancel")}})
    if(confirmReset&&reset!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmReset=false},title={Text("Restore authored targets?")},
        text={Text("Replace this unissued preparation with exactly the original targets shown above. Re-enabling coaching will not restore the old calibration automatically.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmReset=false;controller.acceptCalibrationReset()}){Text("Restore exact targets")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmReset=false}){Text("Cancel")}})
    if(confirmAuthoredReturn&&authoredReturn!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmAuthoredReturn=false},title={Text("Choose heavier authored targets?")},
        text={Text("Confirm the displayed increases for week ${authoredReturn.authored.absoluteWeek}, ${authoredReturn.authored.dayLabel}. Save this one workout with its exact authored targets. This does not mark the increases as earned or start the workout.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmAuthoredReturn=false;controller.acceptAuthoredLoadReturn()}){Text("Confirm authored targets")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmAuthoredReturn=false}){Text("Cancel")}})
}
