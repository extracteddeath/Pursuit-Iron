package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * A new deload or phase is not "just a label." This domain builds and
 * audits the proposed WEEK, and compares it with the prior owned program.
 * Never issues the new week or mutates any existing workout.
 */
enum class BlockCandidateKind { RECOVERY_DELOAD, PHASE_TRANSITION }
enum class BlockCandidateStatus {
    READY_FOR_HUMAN_REVIEW,
    NO_SUPPORTING_COACHING_EVIDENCE,
    CHANGE_REQUIRES_PERMISSION,
    PROGRAM_QUALITY_UNRESOLVED
}
data class BlockAdaptPermissions(
    val adaptSplit:Boolean=false,
    val adaptSchedule:Boolean=false,
    val adaptExercises:Boolean=false,
    val adaptLoads:Boolean=false,
    /** Separate explicit authorization to remove a pinned authored movement. */
    val removePinnedExercises:Boolean=false
)
data class BlockCandidateRequest(
    val history:LongitudinalReviewRequest,
    val baseline:ComposerRequest,
    val proposed:ComposerRequest,
    val kind:BlockCandidateKind,
    val permissions:BlockAdaptPermissions,
    /** Absolute maximum authored for this specific deload, not a guessed %. */
    val maxDeloadWorkingSets:Int?=null
)
data class BlockCandidateChange(
    val splitChanged:Boolean,
    val scheduleChanged:Boolean,
    val exerciseCatalogChanged:Boolean,
    val equipmentLoadsChanged:Boolean,
    val pinnedExercisesRemoved:Set<String>
)
data class BlockCandidateReview(
    val status:BlockCandidateStatus,
    val historyDecision:LongitudinalCoachReview,
    val baselineDraft:ComposerDraft,
    val proposedDraft:ComposerDraft,
    val change:BlockCandidateChange,
    val baselineWorkingSets:Int,
    val proposedWorkingSets:Int,
    val unmetConstraints:List<String>
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayWriteWorkoutHistory:Boolean get()=false
}

/**
 * Both programs are recomputed from explicit inputs. This is a deterministic,
 * headless reviewer, not proof that the source belongs to the active user or
 * that any candidate is a clinically validated recovery intervention.
 */
object CoachBlockCandidateDomain {
    fun review(request:BlockCandidateRequest):BlockCandidateReview {
        require(request.maxDeloadWorkingSets==null ||
            request.maxDeloadWorkingSets in 1..2048)
        require(request.kind!=BlockCandidateKind.RECOVERY_DELOAD ||
            request.maxDeloadWorkingSets!=null)
        require(request.kind!=BlockCandidateKind.PHASE_TRANSITION ||
            request.maxDeloadWorkingSets==null)
        val decision=LongitudinalCoachDomain.review(request.history)
        val currentPhase=request.history.chronologicalWeeks.last().phase
        require(request.baseline.phase==currentPhase)
        val original=CoachWeekComposer.compose(request.baseline)
        val next=CoachWeekComposer.compose(request.proposed)

        val originalById=request.baseline.exercises.associateBy {it.exerciseId}
        val proposedById=request.proposed.exercises.associateBy {it.exerciseId}
        val oldIds=originalById.keys
        val newIds=proposedById.keys
        val catalogChanged=oldIds!=newIds ||
            oldIds.intersect(newIds).any {id ->
                originalById.getValue(id).copy(
                    equipment=proposedById.getValue(id).equipment
                ) != proposedById.getValue(id)
            }
        val loadsChanged=oldIds.intersect(newIds).any {id ->
            originalById.getValue(id).equipment != proposedById.getValue(id).equipment
        }
        val removedPinned=oldIds.filter {id ->
            val oldRequired=originalById.getValue(id).requiredVisits
            oldRequired>0 && (id !in newIds ||
                proposedById.getValue(id).requiredVisits<oldRequired)
        }.toSet()
        val splitChanged=request.baseline.split!=request.proposed.split
        val scheduleChanged=request.baseline.days.map {it.calendarDay to it.focus}!=
            request.proposed.days.map {it.calendarDay to it.focus}
        val changes=BlockCandidateChange(splitChanged,scheduleChanged,
            catalogChanged,loadsChanged,Collections.unmodifiableSet(removedPinned))
        val issues=mutableListOf<String>()

        if(splitChanged && !request.permissions.adaptSplit)
            issues+="Changing the training split requires explicit permission."
        if(scheduleChanged && !request.permissions.adaptSchedule)
            issues+="Changing the calendar days or day focus requires explicit permission."
        if(catalogChanged && !request.permissions.adaptExercises)
            issues+="Changing exercise choices, roles or movement credits requires explicit permission."
        if(loadsChanged && !request.permissions.adaptLoads)
            issues+="Changing the verified exercise loading setup requires explicit permission."
        if(removedPinned.isNotEmpty() &&
            !request.permissions.removePinnedExercises)
            issues+="Removing a pinned custom exercise requires separate permission."

        val phaseExpectation=when(request.kind) {
            BlockCandidateKind.RECOVERY_DELOAD ->
                decision.direction==BlockReviewDirection.REVIEW_DELOAD_WITH_ATHLETE &&
                    request.proposed.phase==currentPhase
            BlockCandidateKind.PHASE_TRANSITION ->
                decision.direction==
                    BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION &&
                    decision.nextPhaseForReview==request.proposed.phase
        }
        if(!phaseExpectation)issues+="The recorded weeks do not justify this specific block change."
        val sessionPurposes=request.proposed.sessionIntents
        if(sessionPurposes.size!=request.proposed.days.size ||
            sessionPurposes.any {it.purpose==ComposerSessionPurpose.UNSPECIFIED})
            issues+="Every proposed session needs an explicit coaching purpose."
        if(request.kind==BlockCandidateKind.PHASE_TRANSITION &&
            request.proposed.phase==TrainingPhase.STRENGTH &&
            sessionPurposes.none {
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                    it.requiredPrimaryExerciseId!=null
            })
            issues+="A strength transition requires an explicitly planned primary skill lift."

        val before=original.week.exposures.count {
            it.kind==ExposureSetKind.WORKING
        }
        val after=next.week.exposures.count {
            it.kind==ExposureSetKind.WORKING
        }
        if(request.kind==BlockCandidateKind.RECOVERY_DELOAD &&
            (after>=before ||
                after>checkNotNull(request.maxDeloadWorkingSets)))
            issues+="The deload candidate must have fewer working sets and satisfy the explicitly authored deload maximum."
        val beforeScope=original.audit.muscles.keys
        val nextScope=next.audit.muscles.keys
        if(beforeScope!=nextScope)
            issues+="Review the same complete muscle target scope for both the baseline and proposed week."
        if(request.kind==BlockCandidateKind.RECOVERY_DELOAD &&
            beforeScope.intersect(nextScope).any {muscle ->
                next.audit.muscles.getValue(muscle).effectiveWorkingSets>
                    original.audit.muscles.getValue(muscle).effectiveWorkingSets
            })issues+="A deload cannot silently increase a muscle's working-set dose."
        if(original.status!=ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW)
            issues+="The baseline program has unresolved constraints; do not compare an unreliable source."
        if(next.status!=ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW)
            issues+="The proposed week has unresolved dose, timing, recovery or required-exercise constraints."

        val status=when {
            !phaseExpectation -> BlockCandidateStatus.NO_SUPPORTING_COACHING_EVIDENCE
            (splitChanged && !request.permissions.adaptSplit) ||
                (scheduleChanged && !request.permissions.adaptSchedule) ||
                (catalogChanged && !request.permissions.adaptExercises) ||
                (loadsChanged && !request.permissions.adaptLoads) ||
                (removedPinned.isNotEmpty() &&
                    !request.permissions.removePinnedExercises) ->
                BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION
            issues.isNotEmpty()->BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED
            else->BlockCandidateStatus.READY_FOR_HUMAN_REVIEW
        }
        return BlockCandidateReview(status,decision,original,next,changes,
            before,after,Collections.unmodifiableList(issues))
    }
}
