package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Translates an already reviewed basic progression into optional athlete-edited
 * week prescriptions. It is NOT a progression evaluator or a save authority.
 *
 * Only the set fields authorized by the upstream evaluator may change.
 * The caller must separately verify the current cycle, week, gym revision,
 * movement/attachment identity and the exact original week prescriptions,
 * then route the resulting draft through the normal full-cycle review.
 */
object CustomBasicProgressionDraftDomain {
    fun forWeekEdit(
        slotId: String,
        original: List<CustomAuthoredSet>,
        projection: CustomBasicHistoryResult
    ): List<CustomAuthoredSet>? {
        if (slotId.isBlank() || original.size !in 1..20) return null
        val indexes = original.indices.filter { original[it].type == DraftSetType.WORKING }
        if (indexes.isEmpty()) return null
        val working = indexes.map { original[it] }

        return when (projection) {
            is CustomBasicHistoryResult.Reviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
                if (candidate.proposedWorkingLoads.size != working.size ||
                    candidate.proposedRepTargets?.size?.let { it != working.size } == true ||
                    working.any { it.load !is DraftSetLoad.Verified || it.amplifier != null })
                    return null

                val changedLoads = mutableSetOf<Int>()
                val changedReps = mutableSetOf<Int>()
                val edits = original.toMutableList()
                for ((workingIndex, setIndex) in indexes.withIndex()) {
                    val before = original[setIndex]
                    val oldLoad = before.load as DraftSetLoad.Verified
                    val proposedLoad = candidate.proposedWorkingLoads[workingIndex]
                    if (proposedLoad < BigDecimal.ZERO) return null
                    if (proposedLoad.compareTo(oldLoad.value) != 0)
                        changedLoads.add(workingIndex)

                    val proposedReps = candidate.proposedRepTargets?.get(workingIndex)
                        ?: before.exactRepTarget
                    if (proposedReps != null &&
                        proposedReps !in before.reps.minimum..before.reps.maximum) return null
                    if (proposedReps != before.exactRepTarget)
                        changedReps.add(workingIndex)
                    edits[setIndex] = before.copy(
                        load = oldLoad.copy(value = proposedLoad),
                        exactRepTarget = proposedReps
                    )
                }
                if (changedLoads != candidate.increasedOrdinals ||
                    changedReps != candidate.changedRepOrdinals ||
                    changedLoads.isEmpty() && changedReps.isEmpty()) null
                else edits
            }
            is CustomBasicHistoryResult.UnloadedReviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
                if (candidate.proposedExactReps.size != working.size ||
                    working.any { it.load != DraftSetLoad.ExplicitBodyweight ||
                        it.exactRepTarget == null || it.amplifier != null })
                    return null
                val advanced = mutableSetOf<Int>()
                val edits = original.toMutableList()
                for ((workingIndex, setIndex) in indexes.withIndex()) {
                    val before = original[setIndex]
                    val target = candidate.proposedExactReps[workingIndex]
                    if (target !in before.reps.minimum..before.reps.maximum) return null
                    if (target != before.exactRepTarget) advanced.add(workingIndex)
                    edits[setIndex] = before.copy(exactRepTarget = target)
                }
                if (advanced != candidate.advancedOrdinals || advanced.isEmpty()) null
                else edits
            }
            else -> null
        }
    }
}
