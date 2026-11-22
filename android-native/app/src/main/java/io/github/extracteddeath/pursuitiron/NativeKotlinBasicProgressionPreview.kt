package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

private fun CustomBasicHistoryResult.Rejected.previewExplanation(): String {
    when (evaluatorReason) {
        BasicProgressionRefusal.STOCK_NOT_VERIFIED ->
            return "Verify the available loads for this exercise in your gym settings."
        BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY ->
            return "The logged and prescribed weights differ. A matching completed session is needed."
        BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS ->
            return "A complete set of working-set results is needed for this progression."
        BasicProgressionRefusal.REP_TARGETS_NOT_VERIFIED ->
            return "Set an exact rep target within the approved range for every working set."
        BasicProgressionRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION ->
            return "Check the progression method for this exercise’s counterweight setting."
        BasicProgressionRefusal.UNSUPPORTED_STRAIGHT_SET_SCHEME ->
            return "This progression requires the same weight across working sets."
        BasicProgressionRefusal.HISTORY_NOT_COMPARABLE ->
            return "A matching completed workout is needed for this exercise and training day."
        BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD ->
            return "Check the prescribed weights and the maximum load increment."
        null -> Unit
    }
    return when (reason) {
        CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE ->
            "This workout no longer matches the approved program. Refresh your preview."
        CustomBasicHistoryRefusal.NO_SAVED_BASIC_METHOD ->
            "Choose a basic progression method from the program editor first."
        CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD ->
            "Exact authored weekly targets or a specialist progression already control this workout."
        CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE ->
            "This method needs exact per-set rep targets, not just a rep range."
        CustomBasicHistoryRefusal.TARGET_ALREADY_TRAINED ->
            "This training day has already been logged for this week or a later week. Select the next untrained week."
        CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY ->
            "No comparable finished workout has been verified for this training day and machine."
        CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE ->
            "The latest comparable workout does not qualify for a basic increase. Keep the current targets."
        CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED ->
            "Program or gym details changed. A new comparable baseline is needed."
        CustomBasicHistoryRefusal.INVALID_HISTORY ->
            "Your workout history needs another verification before suggesting a progression."
    }
}

/** Plan-only, source-verified BASIC suggestion. It does not create a
 * replacement workout or a hidden save/accept action. No guessed 1RM/RIR
 * or invented physical weights are shown. */
@Composable
internal fun NativeKotlinBasicProgressionPreview(
    review:NativeCustomBasicNextReadResult.Reviewed
) {
    val projection=review.projection
    if (!NativeKotlinBasicProgressionPreviewShape.canRender(review.next.slots,projection)) {
        Text("The saved workout no longer matches this progression preview. Refresh the Plan before reviewing changes.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    fun workingSets(slotId: String) = review.next.slots.single { it.slotId == slotId }
        .sets.filter { it.prescription.type == DraftSetType.WORKING }

    fun repLabel(current: Int?, proposed: Int, changed: Boolean): String =
        if (changed && current != null) "$current → $proposed reps" else "$proposed reps"

    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Text("Basic progression",style=MaterialTheme.typography.titleSmall)
        when(projection) {
            is CustomBasicHistoryResult.ManualBodyweight -> {
                Text("Manual logging · bodyweight", style=MaterialTheme.typography.bodySmall)
                workingSets(projection.slotId).forEachIndexed { index, set ->
                    Text("Working set ${index + 1} · ${set.prescription.repTargetLabel()}",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
            is CustomBasicHistoryResult.UnloadedReviewed -> {
                val working = workingSets(projection.slotId)
                Text("Bodyweight",
                    style=MaterialTheme.typography.bodySmall)
                projection.candidate.proposedExactReps.forEachIndexed {index,reps ->
                    val changed=index in projection.candidate.advancedOrdinals
                    val target = working[index].prescription.exactRepTarget
                    Text("Working set ${index+1} · ${repLabel(target, reps, changed)}",
                        style=MaterialTheme.typography.bodyMedium)
                }
                if(projection.candidate.atRepCeiling)
                    Text("All sets are at the rep ceiling. Any harder variation or added weight needs separate review.",
                        style=MaterialTheme.typography.bodySmall)
            }
            is CustomBasicHistoryResult.Rejected -> {
                Text(projection.previewExplanation(),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is CustomBasicHistoryResult.Reviewed -> {
                val candidate=projection.candidate
                val assisted = candidate.strategy == BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
                val description=when(candidate.decision) {
                    BasicProgressionDecision.MANUAL_REPEAT ->
                        "Manual logging: keep your exact prescribed targets."
                    BasicProgressionDecision.BUILD_REPS ->
                        "Keep the current working weights and build completed reps."
                    BasicProgressionDecision.EARNED_INCREMENT ->
                        if(assisted)
                            "A lower counterweight means less assistance, making this assisted movement harder."
                        else "A verified next equipment step is available for the qualifying set(s)."
                    BasicProgressionDecision.EARNED_REP_TARGET ->
                        "Completed work supports a new per-set rep target, not an automatic weight change."
                    BasicProgressionDecision.REP_CEILING_REACHED ->
                        "Every set reached the top of its rep range. Repeat these targets."
                    BasicProgressionDecision.MAXIMUM_LOAD_REACHED ->
                        if (assisted) "You reached the lowest assisted setting. Keep this counterweight or choose an unassisted exercise in your program."
                        else "You reached the highest verified load for the qualifying sets."
                    BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL ->
                        "The next physical step exceeds the selected increment limit."
                }
                if (candidate.dynamicSetDecisions.isEmpty())
                    Text(description,style=MaterialTheme.typography.bodySmall)
                val working = workingSets(projection.slotId)
                candidate.proposedWorkingLoads.forEachIndexed { index, weight ->
                    val prescription = working[index].prescription
                    val original = prescription.load as DraftSetLoad.Verified
                    val proposed = kotlinIssuedLoadLabel(original.copy(value = weight))
                    val load = if (index in candidate.increasedOrdinals)
                        "${original.value.stripTrailingZeros().toPlainString()} → $proposed"
                    else proposed
                    val reps = candidate.proposedRepTargets?.getOrNull(index)?.let {
                        " · " + repLabel(prescription.exactRepTarget, it, index in candidate.changedRepOrdinals)
                    } ?: " · " + prescription.repTargetLabel()
                    val reason = when (candidate.dynamicSetDecisions.getOrNull(index)) {
                        BasicProgressionDecision.BUILD_REPS -> " · build to ${prescription.reps.maximum} reps"
                        BasicProgressionDecision.MAXIMUM_LOAD_REACHED -> " · highest available load"
                        BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL -> " · next step exceeds your limit"
                        BasicProgressionDecision.EARNED_INCREMENT -> " · ${prescription.reps.maximum}-rep ceiling met"
                        else -> ""
                    }
                    Text("Working set ${index+1} · $load$reps$reason",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val manual = projection is CustomBasicHistoryResult.ManualBodyweight ||
            (projection is CustomBasicHistoryResult.Reviewed &&
                projection.candidate.decision == BasicProgressionDecision.MANUAL_REPEAT)
        Text(if (manual) "Targets from your saved program."
            else "Read-only suggestion from verified workout history. Refresh after logging or editing a workout.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
