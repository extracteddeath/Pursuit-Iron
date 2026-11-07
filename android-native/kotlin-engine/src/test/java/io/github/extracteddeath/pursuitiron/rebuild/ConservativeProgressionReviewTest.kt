package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ConservativeProgressionReviewTest {
    private fun n(v:Int)=BigDecimal(v)
    private val reps=RepBand(5,8)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
        listOf(n(195),n(200),n(205),n(210),n(215),n(220)))
    private fun workout(
        id:String,
        loads:List<Int> = listOf(200,200,200,200),
        performed:List<Int> = listOf(8,8,8,8),
        rir:List<Int?> = listOf(2,2,2,2),
        targetRir:Int?=3
    ):ProgressionWorkoutEvidence {
        require(loads.size==4 && performed.size==4 && rir.size==4)
        return ProgressionWorkoutEvidence(id,"bb-bench",LoadUnit.LB,reps,4,
            loads.indices.map { index -> WorkingSetFact(index,SetOutcome.COMPLETED,
                n(loads[index]),performed[index],rir[index],n(200),targetRir) })
    }
    private val one=workout("week-one")
    private fun review(
        old:ProgressionWorkoutEvidence=one,
        next:ProgressionWorkoutEvidence=workout("week-two"),
        inventory:VerifiedLoadStock?=stock,
        cap:BigDecimal=n(5)
    )=ConservativeProgressionReview.assess(old,next,inventory,cap)

    @Test fun twoComparableWorkoutsWithActualEffortAndPhysicalStockOfferOnlyReview() {
        val decision=review()
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,decision.reason)
        assertTrue(decision.canOfferIncrease)
        assertEquals(n(200),decision.currentObservedLoad)
        assertEquals(n(205),decision.reviewCandidate)
        assertEquals(4,decision.previous.completedWorkingSets)
        assertEquals(4,decision.latest.completedWorkingSets)
        // Original immutable facts have not been reinterpreted or overwritten.
        assertEquals(n(200),one.observations.last().actualLoad)
    }

    @Test fun reportedBrokenDoubleProgressionCannotPromoteLast215Pounds() {
        val actual=workout("week-two",loads=listOf(200,205,210,215),
            performed=listOf(3,3,2,2),rir=listOf(2,1,0,0))
        val decision=review(next=actual)
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,decision.reason)
        assertFalse(decision.canOfferIncrease)
        assertNull(decision.reviewCandidate)
        assertNull(decision.currentObservedLoad)
        assertEquals(setOf(0,1,2,3),decision.latest.belowFloorOrdinals)
    }

    @Test fun lowRepsAtHeaviestUniformLoadCannotTriggerIncrease() {
        val actual=workout("week-two",loads=listOf(215,215,215,215),
            performed=listOf(2,2,2,2),rir=listOf(0,0,1,0))
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,review(next=actual).reason)
    }

    @Test fun mixedLoadCeilingsDoNotProveAllSetCapacityAtHeaviestLoad() {
        val actual=workout("week-two",loads=listOf(200,205,210,215))
        assertEquals(IncreaseReviewReason.MIXED_ACTUAL_LOADS,review(next=actual).reason)
        assertNull(review(next=actual).reviewCandidate)
    }

    @Test fun missesPreviousEvidenceOrChangesBaselineRequireAnotherComparableSession() {
        val previous=workout("week-one",performed=listOf(8,8,8,7))
        assertEquals(IncreaseReviewReason.NEEDS_SECOND_COMPARABLE_SESSION,
            review(old=previous).reason)
        val increasedEarly=workout("week-two",loads=listOf(205,205,205,205))
        assertEquals(IncreaseReviewReason.NEEDS_SECOND_COMPARABLE_SESSION,
            review(next=increasedEarly).reason)
        assertEquals(IncreaseReviewReason.STILL_BUILDING_REPS,
            review(next=workout("week-two",performed=listOf(8,8,7,6))).reason)
    }

    @Test fun missingActualRirUsesGoalAsDisclosedAssumptionWithoutChangingFacts() {
        val missing=workout("week-two",rir=listOf(null,null,null,null),targetRir=3)
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,review(next=missing).reason)
        assertEquals(n(205),review(next=missing).reviewCandidate)
        assertEquals(4,review(next=missing).assumedGoalRirSetCount)
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            review(old=workout("week-one",rir=listOf(null,2,2,2))).reason)
        assertEquals(0,review(next=missing).latest.actualRirReportedOrdinals.size)
        assertTrue(missing.observations.all {it.actualRir==null && it.issuedRir==3})
        val both=workout("week-one",rir=List(4){null})
        assertEquals(8,review(old=both,next=missing).assumedGoalRirSetCount)
        val unavailable=missing.copy(observations=missing.observations.map {it.copy(issuedRir=null)})
        assertEquals(IncreaseReviewReason.ACTUAL_RIR_MISSING,review(next=unavailable).reason)
        assertNull(review(next=unavailable).reviewCandidate)
    }

    @Test fun actualEffortOverridesGoalAndMissedRepsNeverEarnAnAssumedIncrease() {
        val explicit=workout("week-two",rir=listOf(null,null,null,0),targetRir=3)
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,review(next=explicit).reason)
        assertNull(review(next=explicit).reviewCandidate)
        val missed=workout("week-two",performed=listOf(8,8,8,4),rir=List(4){null})
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,review(next=missed).reason)
        assertNull(review(next=missed).reviewCandidate)
        val littleReserve=workout("week-two",rir=List(4){null},targetRir=1)
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,review(next=littleReserve).reason)
    }

    @Test fun nearFailureInEitherWorkoutBlocksEvenTwoAllCeilingSessions() {
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,
            review(next=workout("week-two",rir=listOf(2,2,2,1))).reason)
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,
            review(old=workout("week-one",rir=listOf(2,2,1,2))).reason)
    }

    @Test fun skippedAndZeroRepAttemptsStaySeparateAndNeverEarnIncrease() {
        val skipped=workout("week-two").copy(observations=
            workout("week-two").observations.map {
                if(it.workingOrdinal==1) WorkingSetFact(1,SetOutcome.SKIPPED,null,null,null)
                else it
            })
        val blocked=review(next=skipped)
        assertEquals(IncreaseReviewReason.INCOMPLETE_SESSION,blocked.reason)
        assertEquals(setOf(1),blocked.latest.skippedOrdinals)
        val failed=skipped.copy(observations=skipped.observations.map {
            if(it.workingOrdinal==1) WorkingSetFact(1,SetOutcome.FAILED_ATTEMPT,n(200),0,null)
            else it
        })
        assertEquals(IncreaseReviewReason.INCOMPLETE_SESSION,review(next=failed).reason)
        assertEquals(setOf(1),review(next=failed).latest.failedAttemptOrdinals)
    }

    @Test fun completedLowerWeightCannotPretendToMasterHigherIssuedTarget() {
        val previous=workout("week-one").copy(observations=
            workout("week-one").observations.map {
                it.copy(issuedLoad=n(205))
            })
        val latest=workout("week-two").copy(observations=
            workout("week-two").observations.map {
                it.copy(issuedLoad=n(205))
            })
        val result=review(old=previous,next=latest)
        assertEquals(IncreaseReviewReason.ISSUED_LOAD_NOT_COMPLETED,
            result.reason)
        assertNull(result.reviewCandidate)
        assertFalse(result.canOfferIncrease)
        // A single earlier altered target invalidates the independent
        // success requirement; do not cherry-pick the better latest visit.
        assertEquals(IncreaseReviewReason.ISSUED_LOAD_NOT_COMPLETED,
            review(old=previous).reason)
        assertEquals(IncreaseReviewReason.ISSUED_LOAD_NOT_COMPLETED,
            review(next=latest).reason)
        assertEquals(IncreaseReviewReason.ISSUED_LOAD_NOT_COMPLETED,
            review(next=workout("week-two").copy(observations=
                workout("week-two").observations.map {
                    it.copy(issuedLoad=null)
                })).reason)
        // No synthetic modification of actual facts or their target RIR.
        assertEquals(List(4){n(200)},
            latest.observations.map {it.actualLoad})
        assertEquals(List(4){n(205)},
            latest.observations.map {it.issuedLoad})
    }

    @Test fun unconfirmedWrongUnitOffIncrementAndGiantJumpNeverInventLoads() {
        assertEquals(IncreaseReviewReason.CONFIRMED_INVENTORY_REQUIRED,
            review(inventory=null).reason)
        val kg=VerifiedLoadStock.Totals(LoadUnit.KG,LoadBasis.SMITH_TOTAL,listOf(n(200),n(205)))
        assertEquals(IncreaseReviewReason.CONFIRMED_INVENTORY_REQUIRED,
            review(inventory=kg).reason)
        val off=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n(195),n(205)))
        assertEquals(IncreaseReviewReason.CURRENT_LOAD_UNVERIFIED,
            review(inventory=off).reason)
        val noMore=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n(195),n(200)))
        assertEquals(IncreaseReviewReason.NO_HIGHER_VERIFIED_LOAD,
            review(inventory=noMore).reason)
        val large=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n(200),n(220)))
        assertEquals(IncreaseReviewReason.INCREMENT_EXCEEDS_USER_LIMIT,
            review(inventory=large).reason)
    }

    @Test fun zeroLoadCannotBecomeUnreviewedBodyweightOrAssistanceIncrease() {
        val zero=workout("week-one",loads=listOf(0,0,0,0))
        val latest=workout("week-two",loads=listOf(0,0,0,0))
        val known=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
            listOf(n(0),n(5),n(10)))
        assertEquals(IncreaseReviewReason.ZERO_LOAD_NEEDS_EXERCISE_SEMANTICS,
            review(old=zero,next=latest,inventory=known).reason)
    }

    @Test fun invalidSessionComparisonsAndUnapprovedStepCannotPass() {
        assertThrows(IllegalArgumentException::class.java) {
            review(old=one,next=one)
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(next=workout("week-two").copy(exerciseId="lat-pulldown"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(next=workout("week-two").copy(targetReps=RepBand(6,10)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(next=workout("week-two").copy(expectedWorkingSets=3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(cap=n(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            review(cap=BigDecimal("1.0001"))
        }
    }
}
