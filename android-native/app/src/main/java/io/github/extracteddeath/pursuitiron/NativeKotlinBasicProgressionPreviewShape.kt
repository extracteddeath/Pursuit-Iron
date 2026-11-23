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

/**
 * Read-only "why this weight?" wording. The shared Kotlin physical
 * authority has ALREADY accepted the adjacent implement setting; this
 * formatter must not inspect equipment again, infer a different increment,
 * or claim a proposal was issued. Provenance is shown only after the
 * signed-slot review gate has validated the proposal.
 */
internal object NativeKotlinBasicLoadProofText {
    fun describe(fact:KotlinFutureLoadFact):String {
        val proof=fact.physicalProof
        val unit=if(fact.unit==LoadUnit.LB)"lb" else "kg"
        fun plain(value:java.math.BigDecimal)=
            value.stripTrailingZeros().toPlainString()
        return when(proof.reason) {
            KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING ->
                "+${plain(proof.absoluteChange)} $unit · verified next equipment setting ${plain(proof.verifiedCandidate)} $unit"
            KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_LOWER_ASSISTANCE ->
                "${plain(proof.absoluteChange)} $unit less assistance · verified setting ${plain(proof.verifiedCandidate)} $unit"
            KotlinFutureLoadPhysicalReason.VERIFIED_LOWER_SETTING_HISTORY_REQUIRED ->
                "Review return to ${plain(proof.verifiedCandidate)} $unit · earlier completed performance must be verified"
        }
    }
}
