package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class InWorkoutCoachDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private val inventory=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.SMITH_TOTAL,listOf(n(195),n(200),n(205),n(210),n(215)))
    private fun fact(index:Int,load:Int,reps:Int,actualRir:Int?=null,
        issuedRir:Int?=3)=WorkingSetFact(index,SetOutcome.COMPLETED,
            n(load),reps,actualRir,n(load),issuedRir)
    private fun ask(
        observations:List<WorkingSetFact> =listOf(fact(0,215,2,0)),
        nextOrdinal:Int=1,nextLoad:Int=215,
        edited:Boolean=false,
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,
        stock:VerifiedLoadStock?=inventory,
        limit:Int=5
    )=InWorkoutReviewRequest(RepBand(5,8),4,observations,nextOrdinal,
        n(nextLoad),LoadUnit.LB,stock,n(limit),edited,readiness,restricted)
    private fun assess(q:InWorkoutReviewRequest)=InWorkoutCoachDomain.review(q)

    @Test fun reported215ByTwoFailureReviewsOnlyNextPhysicallyPossibleLoad() {
        val request=ask()
        val result=assess(request)
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,result.direction)
        assertEquals(n(210),result.weightForHumanReview)
        assertEquals(n(215),result.issuedTargetUnchanged)
        assertFalse(result.canApplyAutomatically)
        assertEquals(n(215),request.observations.first().actualLoad)
        assertEquals(2,request.observations.first().actualReps)
    }

    @Test fun missingRirIsNotInferredFromPrescribedEffort() {
        val review=assess(ask(observations=listOf(fact(0,215,2,null,3))))
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,review.direction)
        assertEquals(CoachConfidence.LIMITED,review.confidence)
        assertFalse(review.actualRirKnown)
        assertEquals(n(210),review.weightForHumanReview)
    }

    @Test fun aStrongSetAtRepCeilingDoesNotImmediatelyJumpWeight() {
        val result=assess(ask(observations=listOf(fact(0,215,8,3))))
        assertEquals(InWorkoutDirection.CONSIDER_AFTER_WORKOUT,result.direction)
        assertNull(result.weightForHumanReview)
        assertEquals(n(215),result.issuedTargetUnchanged)
    }

    @Test fun successfulButNearFailureMeansReviewRecoveryNotAddWeight() {
        val result=assess(ask(observations=listOf(fact(0,215,5,0))))
        assertEquals(InWorkoutDirection.REVIEW_MORE_RECOVERY,result.direction)
        assertNull(result.weightForHumanReview)
        assertTrue(result.actualRirKnown)
    }

    @Test fun actualRepsInsideRangeLeadToStableSetNotAForcedJump() {
        assertEquals(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            assess(ask(observations=listOf(fact(0,215,6,2)))).direction)
    }

    @Test fun userEditedNextTargetIsNeverOverwritten() {
        val result=assess(ask(edited=true))
        assertEquals(InWorkoutDirection.PRESERVE_USER_EDIT,result.direction)
        assertNull(result.weightForHumanReview)
        assertEquals(n(215),result.issuedTargetUnchanged)
    }

    @Test fun prePlannedBackoffCannotBeOverwrittenFromTopSetEvidence() {
        val result=assess(ask(nextLoad=205))
        assertEquals(InWorkoutDirection.FOLLOW_PLANNED_BACKOFF,result.direction)
        assertNull(result.weightForHumanReview)
        assertEquals(n(205),result.issuedTargetUnchanged)
        assertTrue(result.message.contains("205"))
        assertTrue(result.message.contains("2 of 5"))
    }

    @Test fun noPhantomLoadOrUnconfirmedNextTarget() {
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,
            assess(ask(stock=null)).direction)
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,
            assess(ask(nextLoad=214)).direction)
        assertEquals(InWorkoutDirection.NO_SAFE_SMALLER_LOAD,
            assess(ask(limit=2)).direction)
        val only=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n(215)))
        assertEquals(InWorkoutDirection.NO_SAFE_SMALLER_LOAD,
            assess(ask(stock=only)).direction)
    }

    @Test fun skippedOrFailedEarlierSetsMustStayUnresolved() {
        val skip=WorkingSetFact(0,SetOutcome.SKIPPED,null,null,null)
        val fail=WorkingSetFact(0,SetOutcome.FAILED_ATTEMPT,n(215),0,null)
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            assess(ask(observations=listOf(skip))).direction)
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            assess(ask(observations=listOf(fail))).direction)
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            assess(ask(observations=listOf(fact(0,215,5,2)),nextOrdinal=2)).direction)
    }

    @Test fun nextLoggedSetIsProtectedEvenIfReportedPreviousSetWasPoor() {
        val logged=listOf(fact(0,215,2,0),fact(1,215,2,0))
        val result=assess(ask(observations=logged))
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,result.direction)
        assertNull(result.weightForHumanReview)
    }

    @Test fun lowReadinessOrRestrictionDoesNotConvertToFakeFailure() {
        assertEquals(InWorkoutDirection.REVIEW_RESTRICTION_OR_READINESS,
            assess(ask(readiness=CoachReadiness.LOW)).direction)
        assertEquals(InWorkoutDirection.REVIEW_RESTRICTION_OR_READINESS,
            assess(ask(restricted=true)).direction)
    }

    @Test fun bodyweightOrAssistanceZeroNeedsExplicitSemantics() {
        val zero=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
            listOf(n(0),n(5),n(10)))
        val result=assess(ask(observations=listOf(fact(0,0,6)),
            nextLoad=0,stock=zero))
        assertEquals(InWorkoutDirection.CONFIRM_ZERO_LOAD_SEMANTICS,result.direction)
        assertNull(result.weightForHumanReview)
    }

    @Test fun invalidNextOrdinalAndUnrealisticPrecisionFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            assess(ask(nextOrdinal=0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assess(ask(nextOrdinal=4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assess(ask(limit=0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            assess(ask().copy(nextIssuedLoad=BigDecimal("159.5555")))
        }
    }

    @Test fun doNotMistakeAthleteEditedTopSetForVerifiedAuthoredBackoff() {
        val edited=fact(0,215,2,0).copy(issuedLoad=n(210))
        val result=assess(ask(observations=listOf(edited),nextLoad=205))
        assertEquals(InWorkoutDirection.REVIEW_AUTHORED_LOAD_CHANGE,
            result.direction)
        assertNull(result.weightForHumanReview)
        assertEquals(n(205),result.issuedTargetUnchanged)
    }

    @Test fun missingPhysicalStockCannotAuthorizeEvenPreplannedBackoffCue() {
        val result=assess(ask(nextLoad=205,stock=null))
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,result.direction)
        assertNull(result.weightForHumanReview)
    }

    @Test fun highEffortBackoffAtRepFloorHighlightsRecoveryWithoutSecondWeightDrop() {
        val review=assess(ask(observations=listOf(fact(0,215,5,0)),
            nextLoad=210))
        assertEquals(InWorkoutDirection.FOLLOW_PLANNED_BACKOFF,review.direction)
        assertTrue(review.message.contains("recovery"))
        assertNull(review.weightForHumanReview)
        assertFalse(review.canApplyAutomatically)
    }

    @Test fun coachOffersExactAttainableSmallerLoadNotInventedHalfStep() {
        val review=assess(ask())
        assertEquals(n(210),review.weightForHumanReview)
        assertTrue(review.message.contains("2 of 5"))
        assertTrue(review.message.contains("210 lb"))
        assertEquals(n(215),review.issuedTargetUnchanged)
    }

}
