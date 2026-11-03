package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * End-to-end review gate for a catalog-backed adaptive block candidate.
 *
 * Ensures the same reviewed movement identity survives from a gym's exact
 * exercise selections through weekly composition, the phase/deload decision
 * and full set-level loading/effort/time prescription.
 *
 * Proposals are NOT automatically applied, even when every gate passes.
 */
data class CatalogBlockRequest(
    val history: LongitudinalReviewRequest,
    val baseline: CatalogWeekRequest,
    val proposed: CatalogWeekRequest,
    val baselinePolicies: List<ExercisePrescriptionPolicy>,
    val proposedPolicies: List<ExercisePrescriptionPolicy>,
    val kind: BlockCandidateKind,
    val permissions: BlockAdaptPermissions,
    val maximumDeloadWorkingSets: Int? = null
)

enum class CatalogBlockStatus {
    BASELINE_CATALOG_UNRESOLVED,
    PROPOSED_CATALOG_UNRESOLVED,
    EXERCISE_IDENTITY_CHANGED,
    BLOCK_OR_PRESCRIPTION_UNRESOLVED,
    READY_FOR_HUMAN_REVIEW
}

data class CatalogBlockReview(
    val status: CatalogBlockStatus,
    val baselineCatalog: CatalogWeekReview,
    val proposedCatalog: CatalogWeekReview,
    /** Exists only after both exercise catalogs and identities pass. */
    val fullPrescription: PrescribedBlockReview?,
    val problems: List<String>
) {
    val mayWriteTrainingHistory: Boolean get()=false
    val mayIssueNextWorkout: Boolean get()=false
    val mayReplaceActiveTrainingEngine: Boolean get()=false
}

object CoachCatalogBlockDomain {
    private fun identity(spec:CatalogMovement):List<Any?> = listOf(
        spec.familyId,spec.variantId,spec.attachmentId,
        spec.pattern,spec.role,spec.permittedFocus,
        spec.equipmentId,spec.expectedLoadBasis,spec.requiredFacilityIds,
        spec.sourcePracticalReps
    )

    fun review(input:CatalogBlockRequest):CatalogBlockReview {
        val baseline=CoachExerciseCatalogDomain.review(input.baseline)
        val proposed=CoachExerciseCatalogDomain.review(input.proposed)
        val problems=mutableListOf<String>()
        fun result(status:CatalogBlockStatus,full:PrescribedBlockReview?=null):
            CatalogBlockReview=CatalogBlockReview(
                status,baseline,proposed,full,
                Collections.unmodifiableList(problems.toList()))

        if(baseline.status!=CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW) {
            problems+="The previous week's exercise catalog or constraints are not ready for comparison."
            problems.addAll(baseline.issues.map {it.exerciseId+": "+it.explanation})
            return result(CatalogBlockStatus.BASELINE_CATALOG_UNRESOLVED)
        }
        if(proposed.status!=CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW) {
            problems+="The new week's exercise catalog or constraints are not ready for review."
            problems.addAll(proposed.issues.map {it.exerciseId+": "+it.explanation})
            return result(CatalogBlockStatus.PROPOSED_CATALOG_UNRESOLVED)
        }

        val beforeById=input.baseline.catalog.associateBy {it.exerciseId}
        val afterById=input.proposed.catalog.associateBy {it.exerciseId}
        val shared=input.baseline.chosen.map {it.exerciseId}.toSet()
            .intersect(input.proposed.chosen.map {it.exerciseId}.toSet())
        for(id in shared.sorted()) {
            val before=checkNotNull(beforeById[id])
            val after=checkNotNull(afterById[id])
            if(identity(before)!=identity(after))
                problems+="The stable ID $id changed movement family, variant, attachment, role, allowed day focus, source rep envelope, equipment meaning, or required facilities. Use a new exercise ID and explicit substitution review."
        }
        if(problems.isNotEmpty())
            return result(CatalogBlockStatus.EXERCISE_IDENTITY_CHANGED)

        val block=BlockCandidateRequest(
            input.history,
            checkNotNull(baseline.composedRequest),
            checkNotNull(proposed.composedRequest),
            input.kind,input.permissions,input.maximumDeloadWorkingSets
        )
        val prescribed=CoachPrescribedBlockDomain.review(
            PrescribedBlockRequest(block,input.baselinePolicies,input.proposedPolicies))
        problems.addAll(prescribed.unresolvedReasons)
        return result(
            if(prescribed.status==PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW)
                CatalogBlockStatus.READY_FOR_HUMAN_REVIEW
            else CatalogBlockStatus.BLOCK_OR_PRESCRIPTION_UNRESOLVED,
            prescribed)
    }
}
