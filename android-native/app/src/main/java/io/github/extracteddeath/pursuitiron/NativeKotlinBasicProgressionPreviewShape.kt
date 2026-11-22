package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * One fail-closed presentation fence for read-only basic progression previews.
 * The engine/owner is still the ONLY source of candidate numbers. This gate
 * checks that the candidate still describes exactly the signed next-day slot
 * and set shape before Compose indexes into either list. No load or rep change
 * is authorized here, and stale data must never turn into a partial proposal.
 */
internal object NativeKotlinBasicProgressionPreviewShape {
    fun canRender(
        slots: List<CustomWorkoutSlotSnapshot>,
        projection: CustomBasicHistoryResult
    ): Boolean {
        if (projection is CustomBasicHistoryResult.Rejected) return true
        val slotId = when (projection) {
            is CustomBasicHistoryResult.ManualBodyweight -> projection.slotId
            is CustomBasicHistoryResult.UnloadedReviewed -> projection.slotId
            is CustomBasicHistoryResult.Reviewed -> projection.slotId
            is CustomBasicHistoryResult.Rejected -> return true
        }
        val slot = slots.singleOrNull { it.slotId == slotId } ?: return false
        if (slot.sets.isEmpty() ||
            slot.sets.map { it.setKey }.distinct().size != slot.sets.size ||
            slot.sets.map { it.ordinalInSlot } != (1..slot.sets.size).toList())
            return false
        val working = slot.sets.filter {
            it.prescription.type == DraftSetType.WORKING
        }
        if (working.isEmpty()) return false
        return when (projection) {
            is CustomBasicHistoryResult.ManualBodyweight ->
                working.all { it.prescription.load == DraftSetLoad.ExplicitBodyweight }
            is CustomBasicHistoryResult.UnloadedReviewed -> {
                val candidate = projection.candidate
                working.all { it.prescription.load == DraftSetLoad.ExplicitBodyweight } &&
                    candidate.proposedExactReps.size == working.size &&
                    candidate.advancedOrdinals.all { it in working.indices } &&
                    candidate.proposedExactReps.withIndex().all { (index, reps) ->
                        reps in working[index].prescription.reps.minimum..
                            working[index].prescription.reps.maximum
                    }
            }
            is CustomBasicHistoryResult.Reviewed -> {
                val candidate = projection.candidate
                val proposedReps = candidate.proposedRepTargets
                working.all { it.prescription.load is DraftSetLoad.Verified } &&
                    candidate.proposedWorkingLoads.size == working.size &&
                    working.indices.all { index ->
                        val load = working[index].prescription.load as DraftSetLoad.Verified
                        KotlinSetNumberAuthorityDomain.validPrescribedLoad(load.value,load.basis) &&
                            KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                                candidate.proposedWorkingLoads[index],load.basis)
                    } &&
                    candidate.increasedOrdinals.all { it in working.indices } &&
                    candidate.changedRepOrdinals.all { it in working.indices } &&
                    (candidate.dynamicSetDecisions.isEmpty() ||
                        candidate.dynamicSetDecisions.size == working.size) &&
                    (proposedReps == null && candidate.changedRepOrdinals.isEmpty() ||
                        proposedReps != null && proposedReps.size == working.size &&
                            proposedReps.withIndex().all { (index, reps) ->
                                reps in working[index].prescription.reps.minimum..
                                    working[index].prescription.reps.maximum
                            })
            }
            is CustomBasicHistoryResult.Rejected -> true
        }
    }
}
