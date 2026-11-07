package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** One athlete-entered correction, compared using the SAME full-cycle policy. */
data class CustomCoachWeekEditReview(
    val absoluteWeek: Int,
    val weekday: Int,
    val slotId: String,
    val proposal: CustomCycleBuilderResult.Proposed,
    val before: CustomCoachAudit,
    val after: CustomCoachAudit,
    val originalSets: List<CustomAuthoredSet>,
    val proposedSets: List<CustomAuthoredSet>,
    val restoresBlockSets: Boolean,
    val resolvedIssues: List<CustomCoachIssue>,
    val introducedIssues: List<CustomCoachIssue>,
    val remainingIssues: List<CustomCoachIssue>
) {
    val mayIssueWorkout: Boolean get() = false
    val mayApprovePolicy: Boolean get() = false
}

enum class CustomCoachWeekEditRefusal {
    STALE_REVISION, SOURCE_UNVERIFIED, INVALID_SELECTION,
    INVALID_SETS, NO_CHANGE, REVIEW_INCOMPLETE
}

sealed interface CustomCoachWeekEditResult {
    data class Reviewed(val review: CustomCoachWeekEditReview): CustomCoachWeekEditResult
    data class Rejected(
        val reason: CustomCoachWeekEditRefusal,
        val authoringReason: CustomAuthoringRefusal? = null
    ): CustomCoachWeekEditResult
}

/**
 * No target is suggested here. The athlete supplies exact sets or explicitly
 * restores the block's sets for one week/day/slot. All other authored weeks,
 * exercise identities, pins, loads and overrides survive the existing builder.
 * Both audits use the same athlete, catalog, dose/time and continuity inputs.
 * Re-auditing the WHOLE cycle exposes effects on the following training week,
 * including transitions across recovery weeks and phase boundaries.
 *
 * This produces an undoable draft proposal, never a save or workout grant.
 * The native owner must revalidate source and draft before an explicit save.
 */
object CustomCoachWeekEditDomain {
    fun review(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        absoluteWeek: Int,
        weekday: Int,
        slotId: String,
        replacement: List<CustomAuthoredSet>?,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>,
        inputs: CustomCoachAuditInputs = CustomCoachAuditInputs()
    ): CustomCoachWeekEditResult {
        fun reject(reason: CustomCoachWeekEditRefusal) =
            CustomCoachWeekEditResult.Rejected(reason)
        if (current.revision != expectedRevision)
            return reject(CustomCoachWeekEditRefusal.STALE_REVISION)
        val before = CustomCycleCoachAuditDomain.review(current, setup, catalog, inputs)
        if (!complete(before))
            return reject(CustomCoachWeekEditRefusal.SOURCE_UNVERIFIED)
        val structure = checkNotNull(before.structuralReview)
        val original = checkNotNull(structure.verifiedDraft)
        val week = CustomTrainingCycleDomain.week(structure, absoluteWeek)
            ?: return reject(CustomCoachWeekEditRefusal.INVALID_SELECTION)
        val slot = week.days.firstOrNull { it.weekday == weekday }
            ?.slots?.firstOrNull { it.slotId == slotId }
            ?: return reject(CustomCoachWeekEditRefusal.INVALID_SELECTION)
        val base = original.blocks.first { it.blockId == week.blockId }
            .days.first { it.weekday == weekday }.slots.first { it.slotId == slotId }
        val hasOverride = original.weekOverrides[week.blockId].orEmpty().any {
            it.weekInBlock == week.weekInBlock && it.weekday == weekday && it.slotId == slotId
        }
        if ((replacement == null && !hasOverride) || replacement == slot.sets)
            return reject(CustomCoachWeekEditRefusal.NO_CHANGE)
        // Reuse physical stock, attachment and amplifier validation. An
        // override must meet exactly the same rules as an authored base set.
        val authored = CustomExerciseAuthoringDomain.replaceSelectedSets(
            original, expectedRevision, week.blockId, weekday, slotId,
            replacement ?: base.sets, setup, catalog, week.weekInBlock)
        if (authored is CustomAuthoringResult.Rejected)
            return CustomCoachWeekEditResult.Rejected(
                CustomCoachWeekEditRefusal.INVALID_SETS, authored.reason)
        val proposed = if (replacement == null)
            CustomCycleBuilderDomain.editWeek(original, expectedRevision,
                week.blockId, week.weekInBlock, weekday, slotId, null)
                as? CustomCycleBuilderResult.Proposed
        else (authored as CustomAuthoringResult.Accepted).proposal
        if (proposed == null)
            return reject(CustomCoachWeekEditRefusal.INVALID_SELECTION)
        val after = CustomCycleCoachAuditDomain.review(proposed.draft, setup, catalog, inputs)
        if (!complete(after))
            return reject(CustomCoachWeekEditRefusal.REVIEW_INCOMPLETE)
        val revisedWeek = checkNotNull(CustomTrainingCycleDomain.week(
            checkNotNull(after.structuralReview), absoluteWeek))
        val revisedSets = revisedWeek.days.first { it.weekday == weekday }
            .slots.first { it.slotId == slotId }.sets
        // An unchanged issue with updated amounts remains unresolved. Do not
        // claim a correction succeeded just because its explanation changed.
        val beforeKeys = before.issues.map { it.kind to it.location }.toSet()
        val afterKeys = after.issues.map { it.kind to it.location }.toSet()
        fun frozen(issues: List<CustomCoachIssue>) = Collections.unmodifiableList(issues)
        return CustomCoachWeekEditResult.Reviewed(CustomCoachWeekEditReview(
            absoluteWeek, weekday, slotId, proposed, before, after,
            slot.sets, revisedSets, replacement == null,
            frozen(before.issues.filter { (it.kind to it.location) !in afterKeys }),
            frozen(after.issues.filter { (it.kind to it.location) !in beforeKeys }),
            frozen(after.issues.filter { (it.kind to it.location) in beforeKeys })))
    }

    private fun complete(audit: CustomCoachAudit): Boolean =
        audit.issues.size < 256 &&
            audit.issues.none { it.kind == CustomCoachIssueKind.REVIEW_CAP_EXCEEDED } &&
            CustomCoachPolicyEvidenceDomain.review(audit) is CustomCoachPolicyEvidenceReview.Reviewed
}
