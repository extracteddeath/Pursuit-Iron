package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * G2: rank COMPLETE proposed cycle alternatives, never split labels alone.
 * This read-only evaluator delegates all hard constraints and athlete
 * permissions to the existing custom transition and full-cycle auditors.
 * The candidate producer remains a separate unfinished engineering gate.
 */
data class AdaptiveCycleOption(
    val optionId:String,
    val proposedCycle:CustomTrainingCycle
)

data class AdaptiveCycleOptionRequest(
    val approvedCycle:CustomTrainingCycle,
    val targetBlockId:String,
    val athleteSetup:AthleteSetupReview,
    val catalog:List<CatalogMovement>,
    val coaching:CustomCoachAuditInputs,
    val permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(),
    val alternatives:List<AdaptiveCycleOption>
)

enum class AdaptiveCycleOptionStatus {
    READY_FOR_REVIEW, UNCHANGED, PERMISSION_REQUIRED, INVALID_CYCLE,
    COACH_POLICY_REQUIRED, COACH_AUDIT_FAILED
}

data class AdaptiveCycleOptionAssessment(
    val optionId:String,
    val status:AdaptiveCycleOptionStatus,
    val transition:CustomCycleTransitionReview?,
    val coachingAudit:CustomCoachAudit?,
    /** Comparable only after full audit and permission checks pass. */
    val qualityScore:List<BigDecimal>?,
    val explanation:String
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

enum class AdaptiveCycleComparisonStatus {
    SOURCE_UNRESOLVED, NO_FEASIBLE_ALTERNATIVE, OPTIONS_COMPARED
}
data class AdaptiveCycleComparison(
    val status:AdaptiveCycleComparisonStatus,
    val sourceAudit:CustomCoachAudit,
    val evaluated:List<AdaptiveCycleOptionAssessment>,
    val recommendedOptionId:String?,
    val candidatesChecked:Int
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object AdaptiveCycleOptionReviewDomain {
    private const val MAX_OPTIONS=8
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero=BigDecimal.ZERO

    private fun better(a:List<BigDecimal>,b:List<BigDecimal>):Boolean {
        for (i in a.indices) {
            val comparison=a[i].compareTo(b[i])
            if(comparison!=0)return comparison<0
        }
        return false
    }

    /**
     * Lexicographic soft score, only for independently audited candidates:
     * (1) closeness to the current athlete/phase's authored MAV,
     * (2) avoidable circular same-lift clustering, (3) slot churn,
     * (4) uneven session duration, (5) change count.
     * Never interprets this as a scientific global optimum.
     */
    private fun score(
        source:CustomTrainingCycle,proposed:CustomTrainingCycle,
        blockId:String,audit:CustomCoachAudit,
        policy:AuthoredGoalProgramPolicy,
        changes:List<CustomCycleChange>
    ):List<BigDecimal> {
        val weeks=audit.weeks.filter {
            it.blockId==blockId && it.blockPurpose==CustomBlockPurpose.TRAINING
        }
        val deviation=weeks.fold(zero) {total,week ->
            total.add(policy.selectedBounds.fold(zero) {subtotal,bound ->
                subtotal.add((week.totalSetCredits[bound.muscle] ?: zero)
                    .subtract(bound.mav).abs())
            })
        }
        val newer=proposed.blocks.single {it.blockId==blockId}
        val older=source.blocks.single {it.blockId==blockId}
        var recovery=0
        val appearances=newer.days.flatMap {day ->
            day.slots.map {slot -> slot.exerciseId to day.weekday}
        }.groupBy({it.first},{it.second})
        for(days in appearances.values) {
            val sorted=days.distinct().sorted()
            if(sorted.size<=1)continue
            for(i in sorted.indices) {
                val gap=(if(i==sorted.lastIndex)sorted.first()+7
                    else sorted[i+1])-sorted[i]
                recovery+=when(gap) {1->100;2->20;else->0}
            }
        }
        val before=older.days.flatMap {day ->
            day.slots.map {slot ->
                day.weekday.toString()+":"+slot.slotId+":"+slot.exerciseId
            }
        }.toSet()
        val after=newer.days.flatMap {day ->
            day.slots.map {slot ->
                day.weekday.toString()+":"+slot.slotId+":"+slot.exerciseId
            }
        }.toSet()
        val churn=(before-after).size+(after-before).size
        val imbalance=weeks.sumOf {week ->
            val seconds=week.estimatedSecondsByDay.values.filterNotNull()
            if(seconds.isEmpty())0L
            else (seconds.maxOrNull() ?: 0L)-(seconds.minOrNull() ?: 0L)
        }
        return Collections.unmodifiableList(listOf(deviation,
            BigDecimal(recovery),BigDecimal(churn),BigDecimal(imbalance),
            BigDecimal(changes.size)))
    }

    fun review(request:AdaptiveCycleOptionRequest):AdaptiveCycleComparison {
        require(id.matches(request.targetBlockId))
        require(request.alternatives.size<=MAX_OPTIONS)
        require(request.alternatives.all {id.matches(it.optionId)} &&
            request.alternatives.map {it.optionId}.distinct().size==
                request.alternatives.size)
        val source=request.approvedCycle
        val sourceAudit=CustomCycleCoachAuditDomain.review(
            source,request.athleteSetup,request.catalog,request.coaching)
        val sourceBlock=source.blocks.singleOrNull {
            it.blockId==request.targetBlockId
        }
        if(sourceBlock==null ||
            sourceAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
            return AdaptiveCycleComparison(
                AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED,sourceAudit,
                emptyList(),null,0)
        val available=CustomExerciseAuthoringDomain.picker(
            request.athleteSetup,request.catalog).map {it.availability}
        val assessed=request.alternatives.map {option ->
            val change=CustomCycleTransitionDomain.review(
                source,option.proposedCycle,available,
                CustomCycleEditOrigin.COACH_SUGGESTION,request.permissions)
            // Even a permitted structural edit is not a coached program:
            // audit every proposed week and inter-block transition.
            val audit=CustomCycleCoachAuditDomain.review(
                option.proposedCycle,request.athleteSetup,
                request.catalog,request.coaching)
            val block=option.proposedCycle.blocks.singleOrNull {
                it.blockId==request.targetBlockId
            }
            val status=when {
                block==null || change.status in setOf(
                    CustomCycleTransitionStatus.INVALID_SOURCE_OR_CANDIDATE,
                    CustomCycleTransitionStatus.STALE_REVISION) ||
                    audit.status==CustomCoachAuditStatus.INVALID_SOURCE ->
                    AdaptiveCycleOptionStatus.INVALID_CYCLE
                audit.status==CustomCoachAuditStatus.COACHING_POLICY_REQUIRED ->
                    AdaptiveCycleOptionStatus.COACH_POLICY_REQUIRED
                audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ->
                    AdaptiveCycleOptionStatus.COACH_AUDIT_FAILED
                change.status==CustomCycleTransitionStatus.PERMISSION_REQUIRED ->
                    AdaptiveCycleOptionStatus.PERMISSION_REQUIRED
                change.status==CustomCycleTransitionStatus.NO_CHANGE ->
                    AdaptiveCycleOptionStatus.UNCHANGED
                else -> AdaptiveCycleOptionStatus.READY_FOR_REVIEW
            }
            val eligible=status==AdaptiveCycleOptionStatus.READY_FOR_REVIEW ||
                status==AdaptiveCycleOptionStatus.UNCHANGED
            val policy=block?.let {request.coaching.policiesByPhase[it.phase]}
            val quality=if(eligible && policy!=null)
                score(source,option.proposedCycle,request.targetBlockId,
                    audit,policy,change.differences) else null
            val explanation=when(status) {
                AdaptiveCycleOptionStatus.READY_FOR_REVIEW ->
                    "Audited option: requires explicit athlete approval."
                AdaptiveCycleOptionStatus.UNCHANGED ->
                    "Keep this block; no training targets changed."
                AdaptiveCycleOptionStatus.PERMISSION_REQUIRED ->
                    "Missing adaptive permissions: "+
                    change.blockedKinds.joinToString(",") {it.name}
                AdaptiveCycleOptionStatus.INVALID_CYCLE ->
                    "Invalid cycle identity, revision, exercises or equipment."
                AdaptiveCycleOptionStatus.COACH_POLICY_REQUIRED ->
                    "Missing phase policy or training-volume continuity limits."
                AdaptiveCycleOptionStatus.COACH_AUDIT_FAILED ->
                    "Full-cycle schedule, dose, time or continuity audit failed."
            }
            AdaptiveCycleOptionAssessment(option.optionId,status,
                change,audit,quality,explanation)
        }
        val feasible=assessed.filter {it.qualityScore!=null}
        val best=feasible.minWithOrNull(Comparator {a,b ->
            val x=checkNotNull(a.qualityScore)
            val y=checkNotNull(b.qualityScore)
            when {
                better(x,y)->-1
                better(y,x)->1
                else->a.optionId.compareTo(b.optionId)
            }
        })
        return AdaptiveCycleComparison(
            if(best==null)AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE
            else AdaptiveCycleComparisonStatus.OPTIONS_COMPARED,
            sourceAudit,Collections.unmodifiableList(assessed),
            best?.optionId,assessed.size)
    }
}
