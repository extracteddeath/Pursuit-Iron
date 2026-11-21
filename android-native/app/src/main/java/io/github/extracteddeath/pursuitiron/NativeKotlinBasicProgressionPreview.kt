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
    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Text("Basic progression",style=MaterialTheme.typography.titleSmall)
        when(projection) {
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
                val description=when(candidate.decision) {
                    BasicProgressionDecision.MANUAL_REPEAT ->
                        "Manual logging: keep your exact prescribed targets."
                    BasicProgressionDecision.BUILD_REPS ->
                        "Keep the current working weights and build completed reps."
                    BasicProgressionDecision.EARNED_INCREMENT ->
                        if(candidate.strategy==BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION)
                            "A lower counterweight means less assistance, making this assisted movement harder."
                        else "A verified next equipment step is available for the qualifying set(s)."
                    BasicProgressionDecision.EARNED_REP_TARGET ->
                        "Completed work supports a new per-set rep target, not an automatic weight change."
                    BasicProgressionDecision.MAXIMUM_LOAD_REACHED ->
                        "The verified equipment has no available higher step."
                    BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL ->
                        "The next physical step exceeds the selected increment limit."
                }
                Text(description,style=MaterialTheme.typography.bodySmall)
                val selected=review.next.slots.singleOrNull {
                    it.slotId==projection.slotId
                }
                val working=selected?.sets?.filter{
                    it.prescription.type==DraftSetType.WORKING
                }.orEmpty()
                candidate.proposedWorkingLoads.forEachIndexed {index,weight ->
                    val original=working.getOrNull(index)?.prescription?.load
                        as? DraftSetLoad.Verified
                    val reps=candidate.proposedRepTargets?.getOrNull(index)
                    val changed=index in candidate.increasedOrdinals
                    val repChanged=index in candidate.changedRepOrdinals
                    val unit=original?.unit?.name ?: "units"
                    val assisted=candidate.strategy==
                        BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
                    val loadLabel=if(changed)
                        "Proposed ${weight.toPlainString()} $unit"+
                            (if(assisted)" counterweight · less assistance" else "")
                    else "${weight.toPlainString()} $unit"+
                        (if(assisted)" counterweight" else "")
                    val repLabel=if(reps!=null)
                        " · ${if(repChanged) "proposed " else ""}$reps reps"
                    else ""
                    Text("Working set ${index+1}: $loadLabel$repLabel",
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Read-only suggestion from verified workout history. Refresh after any new workout or history edit. Nothing has been issued, saved or changed.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
