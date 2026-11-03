package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Integrates the independently validated block-change evidence, composed
 * week, and exact session prescriptions. Does not authorize any write.
 *
 * A deload can pass a coarse weekly-set audit while silently becoming
 * harder through exercise-level loads, rest or targets. This layer rejects
 * that contradiction using ACTUAL set-level prescriptions and time audits.
 */
enum class PrescribedBlockStatus {
    READY_FOR_HUMAN_REVIEW,
    BLOCK_CHANGE_UNRESOLVED,
    BASELINE_PRESCRIPTION_UNRESOLVED,
    NEW_PRESCRIPTION_UNRESOLVED,
    RECOVERY_COMPARISON_UNRESOLVED
}

data class PrescribedBlockRequest(
    val block:BlockCandidateRequest,
    val baselinePolicies:List<ExercisePrescriptionPolicy>,
    val proposedPolicies:List<ExercisePrescriptionPolicy>
)

data class MuscleDoseComparison(
    val muscle:TrainingMuscle,
    val baseline:BigDecimal,
    val proposed:BigDecimal,
    val change:BigDecimal
)

data class PrescribedBlockReview(
    val status:PrescribedBlockStatus,
    val coarseReview:BlockCandidateReview,
    val baseline:SessionPrescriptionDraft,
    val proposed:SessionPrescriptionDraft,
    val doseComparisons:List<MuscleDoseComparison>,
    val unresolvedReasons:List<String>
) {
    /** Explicitly keep all actual targets, histories and custom settings owned. */
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

object CoachPrescribedBlockDomain {
    private data class LoggedPlanLoad(
        val unit:LoadUnit?,val basis:LoadBasis?,val value:BigDecimal?
    )

    private fun loads(draft:SessionPrescriptionDraft):
        Map<String,List<DraftPrescribedSet>> =
        draft.sessions.flatMap {it.exercises}.groupBy {it.exerciseId}
            .mapValues {(_,exercises)->
                exercises.flatMap {it.sets}.filter {it.type==DraftSetType.WORKING}
            }

    private fun observedLoad(set:DraftPrescribedSet):LoggedPlanLoad =
        when(val l=set.load) {
            is DraftSetLoad.Verified ->LoggedPlanLoad(l.unit,l.basis,l.value)
            DraftSetLoad.ExplicitBodyweight ->LoggedPlanLoad(null,null,null)
        }

    private fun deloadReview(
        baseline:SessionPrescriptionDraft,
        proposed:SessionPrescriptionDraft
    ):List<String> {
        val notes=linkedSetOf<String>()
        val baselineLoads=loads(baseline)
        val proposedLoads=loads(proposed)
        for((exerciseId,nextSets) in proposedLoads) {
            val previous=baselineLoads[exerciseId]
            if(previous==null) {
                notes+="A new exercise has no comparable previous loading or effort history for a deload."
                continue
            }
            val oldByKey=previous.associateBy {it.stableSetKey}
            for(next in nextSets) {
                val old=oldByKey[next.stableSetKey] ?:previous.maxByOrNull {set->
                    (set.load as? DraftSetLoad.Verified)?.value ?:BigDecimal.ZERO
                }
                if(old==null)continue
                val was=observedLoad(old)
                val now=observedLoad(next)
                if(was.unit!=now.unit || was.basis!=now.basis) {
                    notes+="Deload loading has incompatible equipment meaning for $exerciseId."
                    continue
                }
                if(was.value!=null && now.value!=null &&
                    now.value>was.value) {
                    notes+="Deload working load increased for $exerciseId."
                }
                if(old.targetRir!=null && next.targetRir!=null &&
                    next.targetRir<old.targetRir)
                    notes+="Deload target effort became harder for $exerciseId."
                if(old.targetRir!=null && next.targetRir==null)
                    notes+="The proposed deload removed the previous effort target for $exerciseId."
                if(next.restSeconds<old.restSeconds)
                    notes+="Deload rest was shortened for $exerciseId; reduced volume alone does not justify shorter recovery."
                // A smaller rep range alone is not proof of reduced fatigue,
                // especially when actual RIR is missing.
                if(next.targetReps.maximum<old.targetReps.minimum &&
                    (old.targetRir==null || next.targetRir==null ||
                        next.targetRir<=old.targetRir))
                    notes+="Deload rep targets changed to heavier-style work without evidence of lower effort for $exerciseId."
            }
        }
        return notes.toList()
    }

    fun review(input:PrescribedBlockRequest):PrescribedBlockReview {
        val block=CoachBlockCandidateDomain.review(input.block)
        // Recompute each program from the request. Never trust a supplied
        // 'review-ready' object from a client, and never use a partial audit.
        val before=CoachSessionPrescriptionDomain.draft(
            input.block.baseline,input.baselinePolicies)
        val next=CoachSessionPrescriptionDomain.draft(
            input.block.proposed,input.proposedPolicies)
        val issues=linkedSetOf<String>()
        issues.addAll(block.unmetConstraints)
        if(before.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            before.finalAudit==null)
            issues+="The baseline has missing or infeasible set-level prescriptions."
        if(next.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            next.finalAudit==null)
            issues+="The new week has missing or infeasible set-level prescriptions."
        val comparisons=if(before.finalAudit!=null && next.finalAudit!=null)
            TrainingMuscle.entries.map {muscle->
                val b=before.finalAudit.muscles.getValue(muscle).effectiveWorkingSets
                val n=next.finalAudit.muscles.getValue(muscle).effectiveWorkingSets
                MuscleDoseComparison(muscle,b,n,n.subtract(b))
            } else emptyList()
        val recoveryIssues=mutableListOf<String>()
        if(input.block.kind==BlockCandidateKind.RECOVERY_DELOAD &&
            before.finalAudit!=null && next.finalAudit!=null) {
            val beforeWork=before.sessions.flatMap {it.exercises}
                .flatMap {it.sets}.count {it.type==DraftSetType.WORKING}
            val nextWork=next.sessions.flatMap {it.exercises}
                .flatMap {it.sets}.count {it.type==DraftSetType.WORKING}
            if(nextWork>=beforeWork ||
                nextWork>checkNotNull(input.block.maxDeloadWorkingSets))
                recoveryIssues+="The prescribed recovery week does not meet the declared working-set reduction."
            if(comparisons.any {it.change>BigDecimal.ZERO})
                recoveryIssues+="At least one muscle receives more prescribed work during the deload."
            recoveryIssues.addAll(deloadReview(before,next))
        }
        issues.addAll(recoveryIssues)
        val status=when {
            block.status!=BlockCandidateStatus.READY_FOR_HUMAN_REVIEW ->
                PrescribedBlockStatus.BLOCK_CHANGE_UNRESOLVED
            before.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
                before.finalAudit==null ->
                PrescribedBlockStatus.BASELINE_PRESCRIPTION_UNRESOLVED
            next.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
                next.finalAudit==null ->
                PrescribedBlockStatus.NEW_PRESCRIPTION_UNRESOLVED
            recoveryIssues.isNotEmpty() ->
                PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED
            else -> PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW
        }
        return PrescribedBlockReview(status,block,before,next,
            Collections.unmodifiableList(comparisons),
            Collections.unmodifiableList(issues.toList()))
    }
}
