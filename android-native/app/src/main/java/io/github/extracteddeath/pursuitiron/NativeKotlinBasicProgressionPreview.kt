package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Plan-only, source-verified BASIC suggestion. It does not create a
 * replacement workout or a hidden save/accept action. No guessed 1RM/RIR
 * or invented physical weights are shown. */
@Composable
internal fun NativeKotlinBasicProgressionPreview(
    review:NativeCustomBasicNextReadResult.Reviewed
) {
    val projection=review.projection
    fun workingSets(slotId: String) = review.next.slots.single { it.slotId == slotId }
        .sets.filter { it.prescription.type == DraftSetType.WORKING }

    fun repLabel(current: Int?, proposed: Int, changed: Boolean): String =
        if (changed && current != null) "$current → $proposed reps" else "$proposed reps"

    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Text("Basic progression",style=MaterialTheme.typography.titleSmall)
        when(projection) {
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
                val explanation=when(projection.reason) {
                    CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE ->
                        "This workout no longer matches the approved program. Refresh your preview."
                    CustomBasicHistoryRefusal.NO_SAVED_BASIC_METHOD ->
                        "Choose a basic progression method from the program editor first."
                    CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD ->
                        "Exact authored weekly targets or a specialist progression already control this workout."
                    CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE ->
                        "This method needs exact per-set rep targets, not just a rep range."
                    CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY ->
                        "No comparable finished workout has been verified for this training day and machine."
                    CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE ->
                        "The latest comparable workout does not qualify for a basic increase. Keep the current targets."
                    CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED ->
                        "Program or gym details changed. A new comparable baseline is needed."
                    CustomBasicHistoryRefusal.INVALID_HISTORY ->
                        "Your workout history needs another verification before suggesting a progression."
                }
                Text(explanation,style=MaterialTheme.typography.bodySmall,
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
                    BasicProgressionDecision.MAXIMUM_LOAD_REACHED ->
                        if (assisted) "You reached the lowest verified counterweight."
                        else "You reached the highest verified load for the qualifying sets."
                    BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL ->
                        "The next physical step exceeds the selected increment limit."
                }
                Text(description,style=MaterialTheme.typography.bodySmall)
                val working = workingSets(projection.slotId)
                candidate.proposedWorkingLoads.forEachIndexed { index, weight ->
                    val prescription = working[index].prescription
                    val original = prescription.load as DraftSetLoad.Verified
                    val amounts = if (index in candidate.increasedOrdinals)
                        "${original.value.toPlainString()} → ${weight.toPlainString()}"
                    else weight.toPlainString()
                    val suffix = if (original.basis == LoadBasis.ASSISTANCE_COUNTERWEIGHT) " counterweight" else ""
                    val load = "$amounts ${original.unit.name.lowercase()}$suffix"
                    val reps = candidate.proposedRepTargets?.getOrNull(index)?.let {
                        " · " + repLabel(prescription.exactRepTarget, it, index in candidate.changedRepOrdinals)
                    }.orEmpty()
                    Text("Working set ${index+1} · $load$reps",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Preview only. Refresh after logging or editing a workout.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
