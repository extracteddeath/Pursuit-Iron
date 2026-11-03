package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachExerciseDecisionDomainTest {
    private fun n(x:Int)=BigDecimal(x)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n(25),n(30),n(35),n(40)))
    private val ctx=CoachExerciseContext(CoachReadiness.NORMAL,false,stock,n(5))
    private fun workout(id:String,loads:List<Int> = listOf(30,30,30),
        reps:List<Int> = listOf(8,8,8),
        rir:List<Int?> = listOf(2,2,2),
        outcomes:List<SetOutcome> = List(3){SetOutcome.COMPLETED}):
        ProgressionWorkoutEvidence {
        require(loads.size==3 && reps.size==3 && rir.size==3 && outcomes.size==3)
        return ProgressionWorkoutEvidence(id,"db-bench",LoadUnit.LB,RepBand(5,8),3,
            loads.indices.map {i->
                when(outcomes[i]) {
                    SetOutcome.COMPLETED -> WorkingSetFact(i,SetOutcome.COMPLETED,n(loads[i]),reps[i],
                        rir[i],n(30),2)
                    SetOutcome.SKIPPED -> WorkingSetFact(i,SetOutcome.SKIPPED,null,null,null,
                        n(30),2)
                    SetOutcome.FAILED_ATTEMPT -> WorkingSetFact(i,SetOutcome.FAILED_ATTEMPT,
                        n(loads[i]),0,null,n(30),2)
                }
            })
    }
    private fun review(vararg recent:ProgressionWorkoutEvidence,
        context:CoachExerciseContext=ctx)=CoachExerciseDecisionDomain.review(recent.toList(),context)
    private val first=workout("first")
    private val second=workout("second")

    @Test fun repeatableWorkAtVerifiedLoadOffersHumanReviewNotNewIssuedTargets() {
        val result=review(first,second)
        assertEquals(CoachDirection.REVIEW_SMALL_INCREASE,result.direction)
        assertEquals(n(35),result.nextLoadToReview)
        assertEquals(CoachConfidence.MODERATE,result.confidence)
        assertTrue(CoachEvidenceFlag.REPEATABLE_PERFORMANCE in result.evidence)
        assertEquals(n(30),first.observations.first().actualLoad)
        assertEquals(n(30),second.observations.last().actualLoad)
    }

    @Test fun missingRirLowersConfidenceButDoesNotFreezeEveryProgression() {
        val unknown=workout("second",rir=listOf(null,null,null))
        val result=review(first,unknown)
        assertEquals(CoachDirection.REVIEW_SMALL_INCREASE,result.direction)
        assertEquals(CoachConfidence.LIMITED,result.confidence)
        assertEquals(n(35),result.nextLoadToReview)
        assertTrue(CoachEvidenceFlag.EFFORT_NOT_REPORTED in result.evidence)
        assertTrue(result.explanation.contains("confirm effort"))
        assertNull(unknown.observations.first().actualRir)
        val allMissing=workout("first",rir=listOf(null,null,null))
        assertEquals(CoachConfidence.LIMITED,
            review(allMissing,unknown).confidence)
    }

    @Test fun notAllSetsAtCeilingMeansBuildRepsRatherThanRaiseWeight() {
        val result=review(first,workout("second",reps=listOf(8,7,6)))
        assertEquals(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,result.direction)
        assertNull(result.nextLoadToReview)
    }

    @Test fun failedRepFloorDemandsReviewInsteadOfJumpingToLastHeaviest() {
        val failure=workout("second",loads=listOf(25,30,35),reps=listOf(3,3,2),
            rir=listOf(2,1,0))
        val result=review(first,failure)
        assertEquals(CoachDirection.REVIEW_WORKING_LOAD,result.direction)
        assertNull(result.nextLoadToReview)
        assertTrue(CoachEvidenceFlag.REPS_BELOW_TARGET in result.evidence)
    }

    @Test fun mixedLoadsAtRepCeilingCannotProveHeavySetCapacity() {
        val mixed=workout("second",loads=listOf(25,30,35))
        assertEquals(CoachDirection.STABILIZE_SET_LOADING,review(first,mixed).direction)
        assertNull(review(first,mixed).observedWorkingLoad)
    }

    @Test fun aSingleGoodDayShouldBeRepeatedAndNotCalledAProvenIncrease() {
        val result=review(first)
        assertEquals(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,result.direction)
        assertNull(result.nextLoadToReview)
    }

    @Test fun latestSessionCannotCherryPickAnEarlierHighPoint() {
        val bad=workout("middle",reps=listOf(8,7,6))
        val result=review(first,bad,workout("third"))
        assertEquals(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,result.direction)
        assertNull(result.nextLoadToReview)
    }

    @Test fun actualNearFailureTakesPriorityOverAppetiteForProgression() {
        val last=workout("second",rir=listOf(2,2,0))
        assertEquals(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,
            review(first,last).direction)
        assertTrue(CoachEvidenceFlag.REPORTED_NEAR_FAILURE in review(first,last).evidence)
        val prior=workout("first",rir=listOf(2,1,2))
        assertEquals(CoachDirection.REPEAT_SUCCESSFUL_PERFORMANCE,
            review(prior,second).direction)
    }

    @Test fun lowReadinessAndUserRestrictionGetContextualReviewNotPunishment() {
        val low=ctx.copy(readiness=CoachReadiness.LOW)
        val result=review(first,second,context=low)
        assertEquals(CoachDirection.PLAN_EASIER_SESSION,result.direction)
        assertNull(result.nextLoadToReview)
        assertTrue(CoachEvidenceFlag.READINESS_LOW in result.evidence)
        val restricted=ctx.copy(readiness=CoachReadiness.STRONG,movementRestricted=true)
        val other=review(first,second,context=restricted)
        assertEquals(CoachDirection.REVIEW_MOVEMENT_RESTRICTION,other.direction)
        assertTrue(CoachEvidenceFlag.USER_REPORTED_RESTRICTION in other.evidence)
    }

    @Test fun skippedFailedAndUnfinishedSessionsStayFactual() {
        val skipped=workout("second",outcomes=listOf(
            SetOutcome.COMPLETED,SetOutcome.SKIPPED,SetOutcome.COMPLETED))
        assertEquals(CoachDirection.RESOLVE_UNFINISHED_SESSION,
            review(first,skipped).direction)
        val failed=workout("second",outcomes=listOf(
            SetOutcome.COMPLETED,SetOutcome.FAILED_ATTEMPT,SetOutcome.COMPLETED))
        assertEquals(CoachDirection.REVIEW_FAILED_ATTEMPT,
            review(first,failed).direction)
    }

    @Test fun userGymStockAndMaximumStepAreNonNegotiable() {
        assertEquals(CoachDirection.VERIFY_LOADING_OPTIONS,
            review(first,second,context=ctx.copy(availableLoads=null)).direction)
        assertEquals(CoachDirection.VERIFY_LOADING_OPTIONS,
            review(first,second,context=ctx.copy(maximumStepForReview=n(2))).direction)
        val exhausted=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n(25),n(30)))
        assertEquals(CoachDirection.CONTINUE_AT_HIGHEST_AVAILABLE_LOAD,
            review(first,second,context=ctx.copy(availableLoads=exhausted)).direction)
        val off=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n(25),n(35)))
        assertEquals(CoachDirection.VERIFY_LOADING_OPTIONS,
            review(first,second,context=ctx.copy(availableLoads=off)).direction)
    }

    @Test fun zeroLoadDoesNotInheritBodyweightOrAssistanceSemantics() {
        val firstZero=workout("first",loads=listOf(0,0,0))
        val secondZero=workout("second",loads=listOf(0,0,0))
        assertEquals(CoachDirection.CONFIRM_ZERO_LOAD_MEANING,
            review(firstZero,secondZero).direction)
    }

    @Test fun incompatibleExerciseHistoryNeverCrossContaminatesCoaching() {
        assertThrows(IllegalArgumentException::class.java) {
            review(first,second.copy(exerciseId="cable-press"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(first,second.copy(targetReps=RepBand(8,12)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(first,first)
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(first,second,context=ctx.copy(maximumStepForReview=n(0)))
        }
    }
}
