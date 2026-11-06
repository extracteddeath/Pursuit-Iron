package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Non-destructive review for the NEXT, unlogged working set only.
 *
 * A coaching suggestion is not a new issued target or permission to overwrite
 * a user edit. Unknown actual RIR is never replaced with prescribed RIR.
 */
enum class InWorkoutDirection {
    REVIEW_RESTRICTION_OR_READINESS,
    PRESERVE_USER_EDIT,
    PRIOR_SET_UNRESOLVED,
    NEXT_SET_ALREADY_LOGGED,
    VERIFY_PLANNED_EQUIPMENT,
    CONFIRM_ZERO_LOAD_SEMANTICS,
    REVIEW_AUTHORED_LOAD_CHANGE,
    FOLLOW_PLANNED_BACKOFF,
    BUILD_REPS_AS_PLANNED,
    CONSIDER_AFTER_WORKOUT,
    REVIEW_MORE_RECOVERY,
    REVIEW_SMALLER_NEXT_LOAD,
    NO_SAFE_SMALLER_LOAD
}

data class InWorkoutReviewRequest(
    val repBand:RepBand,
    val expectedWorkingSets:Int,
    val observations:List<WorkingSetFact>,
    val nextWorkingOrdinal:Int,
    val nextIssuedLoad:BigDecimal,
    val unit:LoadUnit,
    val equipment:VerifiedLoadStock?,
    val maximumApprovedReviewStep:BigDecimal,
    val nextTargetUserEdited:Boolean,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean
)

data class InWorkoutReviewResult(
    val direction:InWorkoutDirection,
    /** One possible next weight, not a change to the issued or logged load. */
    val weightForHumanReview:BigDecimal?,
    val confidence:CoachConfidence,
    val message:String,
    val actualRirKnown:Boolean,
    val issuedTargetUnchanged:BigDecimal
) {
    init {
        require(message.isNotBlank())
        require((weightForHumanReview!=null)==
            (direction==InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD))
    }
    val canApplyAutomatically:Boolean get()=false
}

object InWorkoutCoachDomain {
    private val zero=BigDecimal.ZERO
    private val maxStep=BigDecimal("100000")
    /** All verified load stock has at most three decimal places. */
    private val smallestUnit=BigDecimal("0.001")
    private fun normalized(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    fun review(request:InWorkoutReviewRequest):InWorkoutReviewResult {
        require(request.expectedWorkingSets in 2..32)
        require(request.nextWorkingOrdinal in 1 until request.expectedWorkingSets)
        require(request.maximumApprovedReviewStep>zero &&
            request.maximumApprovedReviewStep<=maxStep &&
            request.maximumApprovedReviewStep.stripTrailingZeros().scale()<=3)
        require(request.nextIssuedLoad>=zero &&
            request.nextIssuedLoad<=maxStep &&
            request.nextIssuedLoad.stripTrailingZeros().scale()<=3)
        // Validate every observation, including any already-entered future
        // target, before interpreting performance.
        DoubleProgressionEvidenceDomain.assess(request.repBand,
            request.expectedWorkingSets,request.observations)
        val last=request.observations.firstOrNull {
            it.workingOrdinal==request.nextWorkingOrdinal-1
        }
        val actualRirKnown=last?.outcome==SetOutcome.COMPLETED &&
            last.actualRir!=null
        val kept=normalized(request.nextIssuedLoad)
        fun result(kind:InWorkoutDirection,msg:String,
            confidence:CoachConfidence=CoachConfidence.LIMITED,
            newWeight:BigDecimal?=null)=InWorkoutReviewResult(kind,
                newWeight?.let(::normalized),confidence,msg,actualRirKnown,kept)

        if(request.observations.any {
            it.workingOrdinal>=request.nextWorkingOrdinal
        })return result(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            "The next or a later set is already recorded. Keep the logged facts and review edits explicitly.")
        if(request.nextTargetUserEdited)
            return result(InWorkoutDirection.PRESERVE_USER_EDIT,
                "The next set has a user-edited target. Keep it unless the athlete changes it.")
        if(request.movementRestricted || request.readiness==CoachReadiness.LOW)
            return result(InWorkoutDirection.REVIEW_RESTRICTION_OR_READINESS,
                "Review a session adjustment or alternate movement before the next set.")
        val earlier=(0 until request.nextWorkingOrdinal).map {index->
            request.observations.firstOrNull {it.workingOrdinal==index}
        }
        if(earlier.any {it==null || it.outcome!=SetOutcome.COMPLETED}) {
            return result(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
                "A prior set is skipped, failed, or missing. Do not infer a fresh working load.")
        }

        val planned=EquipmentLoadDomain.inspect(request.equipment,
            kept,request.unit)
        if(planned !is LoadInspection.Confirmed || !planned.exact)
            return result(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,
                "The planned next weight is not confirmed as physically available.")
        val previous=checkNotNull(last)
        val actual=checkNotNull(previous.actualLoad)
        if(actual.compareTo(zero)==0 || kept.compareTo(zero)==0)
            return result(InWorkoutDirection.CONFIRM_ZERO_LOAD_SEMANTICS,
                "Confirm whether zero represents bodyweight, assistance, or another loading setup.")
        val lastReps=checkNotNull(previous.actualReps)
        // When a verified original set is followed by a verified planned
        // lighter backoff, the coach should RECOGNIZE the plan rather than
        // suggest a second unscheduled reduction. Edited/unknown actuals
        // are not evidence that the authored next load was an intended backoff.
        if(actual.compareTo(kept)!=0) {
            val verifiedPrior=EquipmentLoadDomain.inspect(
                request.equipment,actual,request.unit)
            val unchangedActual=previous.issuedLoad?.compareTo(actual)==0
            if(unchangedActual && verifiedPrior is LoadInspection.Confirmed &&
                verifiedPrior.exact && kept<actual)
                return result(InWorkoutDirection.FOLLOW_PLANNED_BACKOFF,
                    "Your next set is already lighter at ${kept.toPlainString()} ${request.unit.name.lowercase()}. "+
                    if(lastReps<request.repBand.minimum)
                        "The last set reached $lastReps of ${request.repBand.minimum} minimum reps; follow the planned backoff and review recovery."
                    else if(previous.actualRir!=null && previous.actualRir<=1)
                        "The previous set was demanding. Keep the prescribed backoff and give yourself adequate recovery."
                    else "Follow the planned backoff; there is no reason to add another weight change.",
                    CoachConfidence.MODERATE)
            return result(InWorkoutDirection.REVIEW_AUTHORED_LOAD_CHANGE,
                "The next set has a different prescribed weight. Check the planned loading scheme and any edited actuals before changing it.")
        }
        if(lastReps<request.repBand.minimum) {
            val below=EquipmentLoadDomain.inspect(request.equipment,
                actual.subtract(smallestUnit),request.unit)
            val smaller=(below as? LoadInspection.Confirmed)?.atOrBelow
            if(smaller!=null &&
                actual.subtract(smaller)<=request.maximumApprovedReviewStep)
                return result(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
                    "You completed $lastReps of ${request.repBand.minimum} minimum reps. "+
                    "Consider ${smaller.toPlainString()} ${request.unit.name.lowercase()} for this next set only; nothing changes until you choose.",
                    if(actualRirKnown)CoachConfidence.MODERATE
                    else CoachConfidence.LIMITED,smaller)
            return result(InWorkoutDirection.NO_SAFE_SMALLER_LOAD,
                "You completed $lastReps of ${request.repBand.minimum} minimum reps, "+
                "but no smaller verified weight fits your approved step. Review rest and technique before continuing.")
        }
        if(previous.actualRir!=null && previous.actualRir<=1)
            return result(InWorkoutDirection.REVIEW_MORE_RECOVERY,
                "The set met the rep floor but was very demanding. Review rest and readiness before continuing.",
                CoachConfidence.MODERATE)
        if(lastReps>=request.repBand.maximum)
            return result(InWorkoutDirection.CONSIDER_AFTER_WORKOUT,
                "The rep ceiling was reached. Finish the planned sets before reviewing a possible increase.",
                if(actualRirKnown)CoachConfidence.MODERATE else CoachConfidence.LIMITED)
        return result(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            "Continue within the prescribed rep range; no mid-workout load change is supported.",
            if(actualRirKnown)CoachConfidence.MODERATE else CoachConfidence.LIMITED)
    }
}
