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
        if (slot.sets.any { it.slotId != slotId || it.setKey.isBlank() } ||
            slot.sets.map { it.setKey }.distinct().size != slot.sets.size ||
            slot.sets.map { it.ordinalInSlot } != (1..slot.sets.size).toList())
            return false
        return CustomBasicProgressionDraftDomain.reviewedSets(
            slotId, slot.sets.map { it.prescription }, projection
        ) != null
    }
}
