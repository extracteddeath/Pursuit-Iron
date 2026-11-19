package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Bounded source-audited response to a same-split 8→3 Strength exercise loss.
 * Reuses real source accessory/secondary prescriptions, never guessed sets,
 * loads, rep targets or exercise variants. The existing full-cycle auditor
 * remains the sole authority for time, dose, equipment and permission.
 */
data class StrengthMenuRecoveryRequest(
    val review:AdaptiveCycleOptionRequest,
    val maximumExercisesPerDay:Int,
    val maximumRestoredPerDay:Int,
    val optionId:String="review_source_strength_menu"
)
data class StrengthMenuRestoredExercise(
    val weekday:Int,
    val sourceSlotId:String,
    val proposedSlotId:String,
    val exerciseId:String,
    val workingSets:Int
)
enum class StrengthMenuRecoveryStatus {
    SOURCE_UNVERIFIED, PERMISSION_REQUIRED, NO_REVIEWABLE_SOURCE,
    PREVIEW_ONLY, NOT_FEASIBLE, READY_FOR_ATHLETE_REVIEW
}
data class StrengthMenuRecoveryDraft(
    val status:StrengthMenuRecoveryStatus,
    val proposedCycle:CustomTrainingCycle?,
    val restored:List<StrengthMenuRestoredExercise>,
    val explanation:String
) {
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}
data class StrengthMenuRecoveryReview(
    val status:StrengthMenuRecoveryStatus,
    val restored:List<StrengthMenuRestoredExercise>,
    val comparison:AdaptiveCycleComparison,
    val reviewedOption:AdaptiveCycleOption?,
    val explanation:String
) {
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

object StrengthExerciseMenuRecoveryDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /**
     * Produces an UNSIGNED, read-only draft for inspection/tests. The
     * externally supplied audit can NEVER authorize a saved program;
     * propose() rederives the complete coach audit from the current source.
     */
    fun preview(input:StrengthMenuRecoveryRequest,
        sourceAudit:CustomCoachAudit):StrengthMenuRecoveryDraft {
        fun reject(status:StrengthMenuRecoveryStatus,why:String)=
            StrengthMenuRecoveryDraft(status,null,emptyList(),why)
        require(input.maximumExercisesPerDay in 1..32 &&
            input.maximumRestoredPerDay in 1..8 &&
            id.matches(input.optionId))
        val req=input.review
        require(req.alternatives.isEmpty() &&
            req.prospectiveByOptionId.isEmpty() &&
            req.sourceProspectiveByBlockId.isEmpty()) {
            "Recovery must not smuggle a new schedule or another option."
        }
        if(!req.permissions.adaptExercises)
            return reject(StrengthMenuRecoveryStatus.PERMISSION_REQUIRED,
                "The athlete must enable Adapt exercises.")
        val cycle=req.approvedCycle
        val structural=sourceAudit.structuralReview
        if(sourceAudit.auditedCycleRevision!=cycle.revision ||
            sourceAudit.status==CustomCoachAuditStatus.INVALID_SOURCE ||
            structural?.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            structural.verifiedDraft!=cycle)
            return reject(StrengthMenuRecoveryStatus.SOURCE_UNVERIFIED,
                "Exact saved-cycle structural and source revision proof is missing.")
        // The true baseline may be separated from Strength by a reviewed
        // deload or performance-test block. An adjacent-block comparison
        // would mistake the low-volume bridge for the athlete's last normal
        // Hypertrophy week. Reuse the same verified source-boundary owner
        // that protects generated future Strength workouts.
        val boundary=StrengthRosterBoundaryGuardDomain.review(
            structural,req.targetBlockId)
        if(boundary.status==StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED)
            return reject(StrengthMenuRecoveryStatus.SOURCE_UNVERIFIED,
                "The verified final hypertrophy and strength-entry rosters are unavailable.")
        val priorExit=boundary.sourceExitWeek
        val last=priorExit?.let {
            CustomTrainingCycleDomain.week(structural,it)
        }
        val previous=cycle.blocks.singleOrNull{it.blockId==last?.blockId}
        val target=cycle.blocks.singleOrNull{it.blockId==req.targetBlockId}
        if(previous==null || target==null || last==null ||
            previous.phase!=TrainingPhase.HYPERTROPHY ||
            target.phase!=TrainingPhase.STRENGTH ||
            previous.purpose!=CustomBlockPurpose.TRAINING ||
            target.purpose!=CustomBlockPurpose.TRAINING ||
            previous.split!=target.split ||
            previous.days.map{it.weekday}.toSet()!=
                target.days.map{it.weekday}.toSet() ||
            cycle.weekOverrides[target.blockId].orEmpty().isNotEmpty() ||
            boundary.status!=
                StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW)
            return reject(StrengthMenuRecoveryStatus.NO_REVIEWABLE_SOURCE,
                "Only source-verified same-split Hypertrophy exit to Strength entry collapses without target overrides are eligible.")
        val sourceDays=last.days.associateBy{it.weekday}
        val catalog=req.catalog.associateBy{it.exerciseId}
        if(catalog.size!=req.catalog.size)
            return reject(StrengthMenuRecoveryStatus.SOURCE_UNVERIFIED,
                "Exercise IDs are ambiguous.")
        // Restoration is DAY-SPECIFIC. A movement retained on Wednesday
        // does not restore the lost Monday exposure. The existing exact
        // whole-cycle reviewer must assess frequency, recovery, and weekly
        // volume before an athlete can accept the proposal.
        val restored=mutableListOf<StrengthMenuRestoredExercise>()
        val futureDays=target.days.map{day->
            val dropped=boundary.affectedDays
                .singleOrNull{it.weekday==day.weekday}
                ?.missingExerciseIds.orEmpty()
            val room=(input.maximumExercisesPerDay-day.slots.size)
                .coerceAtLeast(0).coerceAtMost(input.maximumRestoredPerDay)
            val visited=mutableSetOf<String>()
            val usedIds=day.slots.map{it.slotId}.toMutableSet()
            val additions=sourceDays[day.weekday]?.slots.orEmpty().filter{slot->
                val spec=catalog[slot.exerciseId]
                slot.exerciseId in dropped &&
                    day.slots.none {it.exerciseId==slot.exerciseId} &&
                    visited.add(slot.exerciseId) &&
                    slot.role in setOf(ComposerRole.SECONDARY,ComposerRole.ACCESSORY) &&
                    spec!=null &&
                    spec.role==slot.role &&
                    spec.creditStatus==CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                    !spec.creditReviewReference.isNullOrBlank() &&
                    slot.supersetGroup==null &&
                    slot.sets.isNotEmpty() &&
                    slot.sets.all{it.type==DraftSetType.WORKING &&
                        it.amplifier==null}
            }.take(room).mapIndexed{indexInDay,slot->
                var ordinal=indexInDay+1
                var slotId="source_"+day.weekday+"_"+ordinal
                while(slotId in usedIds) {
                    ordinal++
                    slotId="source_"+day.weekday+"_"+ordinal
                }
                usedIds+=slotId
                restored+=StrengthMenuRestoredExercise(day.weekday,
                    slot.slotId,slotId,slot.exerciseId,slot.sets.size)
                // Exact existing physical load and target, distinct block slot.
                slot.copy(slotId=slotId)
            }
            day.copy(slots=day.slots+additions)
        }
        if(restored.isEmpty())
            return reject(StrengthMenuRecoveryStatus.NO_REVIEWABLE_SOURCE,
                "No reviewed, physically auditable source exercises fit the explicit capacity.")
        val draft=cycle.copy(revision=cycle.revision+1,
            blocks=cycle.blocks.map{
                if(it.blockId==target.blockId)it.copy(days=futureDays)
                else it
            })
        return StrengthMenuRecoveryDraft(StrengthMenuRecoveryStatus.PREVIEW_ONLY,
            draft,Collections.unmodifiableList(restored.toList()),
            "This unsaved candidate retains exact source prescriptions. It is NOT a cleared Strength workout.")
    }

    fun propose(input:StrengthMenuRecoveryRequest):StrengthMenuRecoveryReview {
        val initial=AdaptiveCycleOptionReviewDomain.review(input.review)
        val preview=preview(input,initial.sourceAudit)
        val proposal=preview.proposedCycle
            ?:return StrengthMenuRecoveryReview(preview.status,emptyList(),
                initial,null,preview.explanation)
        val option=AdaptiveCycleOption(input.optionId,proposal)
        val result=AdaptiveCycleOptionReviewDomain.review(
            input.review.copy(alternatives=listOf(option)))
        val accepted=result.evaluated.singleOrNull{
            it.optionId==input.optionId &&
                it.status==AdaptiveCycleOptionStatus.READY_FOR_REVIEW
        }!=null
        return StrengthMenuRecoveryReview(
            if(accepted)StrengthMenuRecoveryStatus.READY_FOR_ATHLETE_REVIEW
            else StrengthMenuRecoveryStatus.NOT_FEASIBLE,
            preview.restored,result,if(accepted)option else null,
            if(accepted)"Fully audited source menu; requires explicit athlete approval."
            else "Full-cycle dose, equipment, time, recovery or permission audit rejected this menu. No saved plan was edited.")
    }
}
