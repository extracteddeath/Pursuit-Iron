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
    val offer=state.calibrationReview?.takeIf {
        it.original.stamp.cycleRevision==state.draft?.revision &&
            it.original.stamp.gymCatalogSourceRevision==state.sourceRevision &&
            (state.calibrationReceipt!=null ||
                it.original.copy(declaredNonSetSeconds=state.authoredPreview?.declaredNonSetSeconds)==state.authoredPreview)
    }
    val prepared=state.preparedWorkout?.takeIf {
        it.calibrationApprovalFingerprint!=null||it.resetToAuthored
    }
    val reset=state.calibrationReset
    LaunchedEffect(offer?.receipt?.fingerprint,prepared?.workoutId,reset?.preparedFingerprint) {
        confirmAccept=false;confirmIssue=false;confirmReset=false
    }
    val readyPolicy=state.reviewedCoachInputs!=null
    PursuitCard {
        Text("Reviewed starting weights",style=MaterialTheme.typography.titleLarge)
        OutlinedButton(enabled=!state.busy,onClick=controller::findPendingCalibrations) {
            Text("Find saved approvals and preparations")
        }
        state.pendingCalibrationAcceptances.forEach {receipt->
            OutlinedButton(enabled=!state.busy&&readyPolicy,onClick={controller.resumeAcceptedCalibration(receipt)}) {
                Text("Recheck accepted weights · week ${receipt.absoluteWeek}")
            }
        }
        if(state.pendingCalibrationAcceptances.isNotEmpty()&&!readyPolicy)
            Text("Run your current full-cycle audit to recheck a saved acceptance.",style=MaterialTheme.typography.bodySmall)
        state.pendingCalibrations.forEach {workout->
            OutlinedButton(enabled=!state.busy,onClick={controller.resumePreparedCalibration(workout.workoutId)}) {
                Text("Reopen week ${workout.snapshot.absoluteWeek} · ${workout.snapshot.dayLabel}")
            }
        }
        offer?.let {
            Text("All ${it.auditedWeeks} cycle weeks audited",
                style=MaterialTheme.typography.titleMedium)
            it.receipt.setKeys.indices.forEach {i->
                val basis=if(it.receipt.basis==LoadBasis.DUMBBELL_PER_HAND)" per hand" else ""
                Text("Working set ${i+1}: ${it.receipt.authoredLoads[i]} → ${it.receipt.proposedLoads[i]} ${it.receipt.unit.name.lowercase()}$basis")
            }
            if(state.calibrationReceipt==null) {
                Button(enabled=!state.busy,onClick={confirmAccept=true}){Text("Review and accept starting weights")}
            } else {
                Text("Athlete acceptance saved. Your authored cycle is unchanged.",
                    style=MaterialTheme.typography.bodySmall)
                if(prepared==null) Button(enabled=!state.busy&&readyPolicy,
                    onClick=controller::prepareBlockCalibration){Text("Prepare accepted workout")}
            }
        }
        prepared?.let {workout->
            HorizontalDivider()
            Text("Prepared · week ${workout.snapshot.absoluteWeek} · ${workout.snapshot.dayLabel}",
                style=MaterialTheme.typography.titleMedium)
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
                if(workout.calibrationApprovalFingerprint!=null)OutlinedButton(
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
        onDismissRequest={if(!state.busy)confirmAccept=false},title={Text("Accept these starting weights?")},
        text={Text("Save only the displayed per-set weight changes for week ${offer.receipt.absoluteWeek}, ${offer.original.dayLabel}. This does not start a workout or edit your cycle.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmAccept=false;controller.acceptBlockCalibration()}){Text("Accept exact weights")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmAccept=false}){Text("Cancel")}})
    if(confirmIssue&&prepared!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmIssue=false},title={Text("Issue this exact workout?")},
        text={Text("Recheck saved history, consent and the prepared targets for ${prepared.snapshot.dayLabel}. Once issued, targets cannot be rewritten.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmIssue=false;controller.issueBlockCalibration()}){Text("Issue workout")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmIssue=false}){Text("Cancel")}})
    if(confirmReset&&reset!=null)AlertDialog(
        onDismissRequest={if(!state.busy)confirmReset=false},title={Text("Restore authored targets?")},
        text={Text("Replace this unissued preparation with exactly the original targets shown above. Re-enabling coaching will not restore the old calibration automatically.")},
        confirmButton={Button(enabled=!state.busy,onClick={confirmReset=false;controller.acceptCalibrationReset()}){Text("Restore exact targets")}},
        dismissButton={TextButton(enabled=!state.busy,onClick={confirmReset=false}){Text("Cancel")}})
}
