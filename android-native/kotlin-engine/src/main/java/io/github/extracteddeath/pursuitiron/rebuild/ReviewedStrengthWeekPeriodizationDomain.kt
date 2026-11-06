package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * G3 review-only, explicit week-by-week strength-block periodization.
 *
 * The supplied set prescriptions are coach/athlete-authored proposals, not
 * predictions from a logged RIR or unverified one-rep maximum. Exact physical
 * loading, 16-muscle dose/frequency, every projected week, recovery and
 * independent editing permissions are checked by the authoritative native
 * cycle reviewers. Existing overrides are preserved, never overwritten.
 */
data class ReviewedStrengthWeekPeriodizationRequest(
    val cycleReview:AdaptiveCycleOptionRequest,
    val proposedWeeks:List<CustomWeekSlotOverride>,
    val optionId:String="reviewed_strength_week_progression"
)

enum class ReviewedStrengthWeekPeriodizationStatus {
    INVALID_REQUEST, PROTECTED_WEEK, SOURCE_UNRESOLVED,
    PERMISSION_REQUIRED, COACH_AUDIT_FAILED, READY_FOR_ATHLETE_REVIEW
}

data class ReviewedStrengthWeekPeriodizationResult(
    val status:ReviewedStrengthWeekPeriodizationStatus,
    val comparison:AdaptiveCycleComparison?,
    val option:AdaptiveCycleOption?,
    val coveredWeeks:Set<Int>,
    val explanation:String
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}

object ReviewedStrengthWeekPeriodizationDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_WEEK_EDITS=64
    private const val MAX_WEEKS=8

    fun propose(request:ReviewedStrengthWeekPeriodizationRequest):
        ReviewedStrengthWeekPeriodizationResult {
        val source=request.cycleReview
        fun refuse(
            status:ReviewedStrengthWeekPeriodizationStatus,reason:String,
            comparison:AdaptiveCycleComparison?=null
        )=ReviewedStrengthWeekPeriodizationResult(
            status,comparison,null,emptySet(),reason)
        require(source.alternatives.isEmpty() &&
            source.prospectiveByOptionId.isEmpty()) {
            "Periodization reviews their own complete alternative, not a prebuilt option."
        }
        val cycle=source.approvedCycle
        val block=cycle.blocks.singleOrNull {
            it.blockId==source.targetBlockId
        }
        if(!id.matches(request.optionId) ||
            cycle.revision>=9_007_199_254_740_990L ||
            block==null || block.phase!=TrainingPhase.STRENGTH ||
            block.purpose!=CustomBlockPurpose.TRAINING ||
            block.weeks !in 2..MAX_WEEKS ||
            request.proposedWeeks.size !in 2..MAX_WEEK_EDITS)
            return refuse(ReviewedStrengthWeekPeriodizationStatus.INVALID_REQUEST,
                "A future strength block of two to eight weeks and two or more explicit weekly prescriptions is required.")
        val offered=request.proposedWeeks
        val selected=offered.map {Triple(it.weekInBlock,it.weekday,it.slotId)}
        val original=cycle.weekOverrides[block.blockId].orEmpty()
        val existing=original.map {
            Triple(it.weekInBlock,it.weekday,it.slotId)
        }.toSet()
        if(offered.any {edit->
            edit.weekInBlock !in 1..block.weeks ||
                edit.sets.isEmpty() || edit.sets.size>20 ||
                block.days.none {day->
                    day.weekday==edit.weekday &&
                        day.slots.any {it.slotId==edit.slotId}
                }
        })
            return refuse(ReviewedStrengthWeekPeriodizationStatus.INVALID_REQUEST,
                "Each proposed week must identify an actual source slot and nonempty exact authored sets.")
        if(selected.toSet().size!=selected.size ||
            selected.any {it in existing})
            return refuse(ReviewedStrengthWeekPeriodizationStatus.PROTECTED_WEEK,
                "An authored week/weekday/slot prescription already exists or an edit is duplicated; never replace it silently.")
        // Do not populate an invisible no-op override: this must alter at
        // least one issued target, rather than merely changing a metadata key.
        val changes=offered.count {edit->
            val originalSet=block.days.single {
                it.weekday==edit.weekday
            }.slots.single {it.slotId==edit.slotId}.sets
            originalSet!=edit.sets
        }
        if(changes==0)
            return refuse(ReviewedStrengthWeekPeriodizationStatus.INVALID_REQUEST,
                "No effective training prescription was changed.")
        val proposed=cycle.copy(revision=cycle.revision+1,
            weekOverrides=Collections.unmodifiableMap(
                cycle.weekOverrides+(block.blockId to
                    Collections.unmodifiableList(original+offered))))
        val option=AdaptiveCycleOption(request.optionId,proposed)
        val review=AdaptiveCycleOptionReviewDomain.review(
            source.copy(alternatives=listOf(option)))
        val assessment=review.evaluated.singleOrNull()
        val status=when {
            assessment==null ->
                ReviewedStrengthWeekPeriodizationStatus.SOURCE_UNRESOLVED
            assessment.status==AdaptiveCycleOptionStatus.PERMISSION_REQUIRED ->
                ReviewedStrengthWeekPeriodizationStatus.PERMISSION_REQUIRED
            assessment.status==AdaptiveCycleOptionStatus.READY_FOR_REVIEW &&
                review.recommendedOptionId==request.optionId ->
                ReviewedStrengthWeekPeriodizationStatus.READY_FOR_ATHLETE_REVIEW
            else ->ReviewedStrengthWeekPeriodizationStatus.COACH_AUDIT_FAILED
        }
        if(status!=ReviewedStrengthWeekPeriodizationStatus.READY_FOR_ATHLETE_REVIEW)
            return refuse(status,
                assessment?.explanation ?:
                    "The existing source cycle is not eligible for audited periodization.",
                review)
        return ReviewedStrengthWeekPeriodizationResult(status,
            review,option,Collections.unmodifiableSet(offered.map {
                it.weekInBlock
            }.toSet()),
            "Exact weekly periodization has passed the full cycle, individual prescription permissions and every training week's reviewed dose, timing and continuity audit. Athlete approval is still required.")
    }
}
