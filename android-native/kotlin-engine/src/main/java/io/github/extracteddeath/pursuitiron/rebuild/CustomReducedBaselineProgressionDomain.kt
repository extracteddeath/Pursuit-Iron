package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomReducedBaselineProgressionStatus {
    BASELINE_NOT_VERIFIED, TWO_ADJACENT_WORKOUTS_REQUIRED,
    EVIDENCE_NOT_COMPARABLE, PROGRESSION_NOT_EARNED,
    CANDIDATE_EXCEEDS_AUTHORED_LOAD, READY_FOR_REVIEW
}
data class CustomReducedBaselineLoadSet(
    val setKey:String,val ordinal:Int,val authoredLoad:BigDecimal,
    val baselineLoad:BigDecimal,val proposedLoad:BigDecimal,
    val unit:LoadUnit,val basis:LoadBasis
)
data class CustomReducedBaselineProgressionReview(
    val status:CustomReducedBaselineProgressionStatus,
    val baseline:CustomSameBlockLoadReview,
    val progression:CustomNextWorkoutCoachingReview?,
    val amendment:CustomNextLoadAmendment?,
    val sets:List<CustomReducedBaselineLoadSet>,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val mayEditCycle:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

/**
 * Reuse the ordinary custom progression and exact-inventory amendment rules
 * against a VERIFIED virtual next workout at its last issued lower load.
 * Original authored targets and historical snapshots are never rewritten.
 * This bounded return stops at the authored load; a later increase above
 * that load needs its own ordinary progression/acceptance review.
 */
object CustomReducedBaselineProgressionDomain {
    fun review(request:CustomNextWorkoutCoachingRequest):CustomReducedBaselineProgressionReview {
        val baselineRequest=CustomBlockBaselineRequest(request.next,request.slotId,
            request.completed,request.verifiedStock)
        val baseline=CustomSameBlockLoadContinuityDomain.review(baselineRequest,
            request.readiness,request.movementRestricted)
        fun result(status:CustomReducedBaselineProgressionStatus,why:String,
            progression:CustomNextWorkoutCoachingReview?=null,
            amendment:CustomNextLoadAmendment?=null,
            sets:List<CustomReducedBaselineLoadSet> = emptyList()
        )=CustomReducedBaselineProgressionReview(status,baseline,progression,amendment,
            Collections.unmodifiableList(sets.toList()),why)
        val virtual=baseline.proposedWorkout
        if(baseline.status!=CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT||virtual==null)
            return result(CustomReducedBaselineProgressionStatus.BASELINE_NOT_VERIFIED,
                baseline.explanation)

        // Select adjacent scheduled appearances from the COMPLETE same-day
        // history before comparing exercises, recipes or favorable outcomes.
        val sameDay=request.completed.filter{
            it.snapshot.stamp.athleteId==request.next.stamp.athleteId&&
                it.snapshot.stamp.cycleId==request.next.stamp.cycleId&&
                it.snapshot.weekday==request.next.weekday
        }
        val lastTwo=sameDay.takeLast(2)
        if(lastTwo.size!=2||lastTwo[1].evidence.workoutId!=baseline.previousWorkoutId||
            lastTwo[0].snapshot.absoluteWeek!=request.next.absoluteWeek-2||
            lastTwo[0].snapshot.weekInBlock!=request.next.weekInBlock-2||
            sameDay.count{it.snapshot.absoluteWeek==request.next.absoluteWeek-2}!=1)
            return result(CustomReducedBaselineProgressionStatus.TWO_ADJACENT_WORKOUTS_REQUIRED,
                "Two unique adjacent same-day workouts at the reduced baseline are required.")

        // Also verify the earlier day's complete variant, set order, warmups,
        // rest, effort and receipts. The regular progression reviewer below
        // independently requires both issued/actual working loads to match.
        val earlier=CustomSameBlockLoadContinuityDomain.review(
            baselineRequest.copy(next=lastTwo[1].snapshot,
                completed=request.completed.filter{it.completionOrder<lastTwo[1].completionOrder}),
            request.readiness,request.movementRestricted)
        if(earlier.status!=CustomSameBlockLoadStatus.NO_LOWER_VERIFIED_BASELINE||
            earlier.previousWorkoutId!=lastTwo[0].evidence.workoutId)
            return result(CustomReducedBaselineProgressionStatus.EVIDENCE_NOT_COMPARABLE,
                "The preceding same-day workout is not a complete comparable baseline. "+earlier.explanation)

        val progression=CustomNextWorkoutCoachingDomain.review(request.copy(next=virtual))
        val proposed=CustomNextLoadAmendmentDomain.propose(virtual,request.slotId,
            progression,request.verifiedStock,request.maximumAuthorizedIncrease)
            as? CustomNextLoadAmendmentResult.Proposed
            ?:return result(CustomReducedBaselineProgressionStatus.PROGRESSION_NOT_EARNED,
                progression.explanation,progression)
        val amendment=proposed.amendment
        if(progression.comparedWorkoutIds!=lastTwo.map{it.evidence.workoutId}||
            amendment.workingSetKeys!=baseline.sets.map{it.setKey})
            return result(CustomReducedBaselineProgressionStatus.EVIDENCE_NOT_COMPARABLE,
                "The progression evidence does not name the exact two baseline workouts.",progression)
        if(baseline.sets.any{amendment.proposedLoad>it.authoredLoad})
            return result(CustomReducedBaselineProgressionStatus.CANDIDATE_EXCEEDS_AUTHORED_LOAD,
                "This baseline-return policy cannot exceed any current authored working weight.",progression)
        val sets=baseline.sets.map{set->CustomReducedBaselineLoadSet(set.setKey,set.ordinal,
            set.authoredLoad,set.lastIssuedLoad,amendment.proposedLoad,set.unit,set.basis)}
        return result(CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW,
            "Two adjacent same-day workouts support the next verified equipment step from the reduced baseline. "+
                "Review and accept this one workout before issuance. "+progression.explanation,
            progression,amendment,sets)
    }
}
