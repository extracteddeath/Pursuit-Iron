package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class OverloadRecoveryLoadReviewTest {
    private fun n(value: Int) = BigDecimal(value)
    private val verified = VerifiedLoadStock.Totals(
        LoadUnit.LB, LoadBasis.SMITH_TOTAL,
        listOf(190, 195, 200, 205, 210, 215, 220).map(::n))
    private fun session(
        id: String,
        loads: List<Int> = listOf(200, 200, 200, 200),
        reps: List<Int> = listOf(8, 8, 8, 8),
        rir: List<Int?> = listOf(2, 2, 2, 2),
        exercise: String = "bench",
        unit: LoadUnit = LoadUnit.LB,
        band: RepBand = RepBand(5, 8),
        reported: Boolean = true
    ): ProgressionWorkoutEvidence {
        require(loads.size == 4 && reps.size == 4 && rir.size == 4)
        val facts = loads.indices.map { index ->
            WorkingSetFact(
                index, SetOutcome.COMPLETED, n(loads[index]), reps[index],
                if (reported) rir[index] else null, n(215), 3)
        }
        return ProgressionWorkoutEvidence(id, exercise, unit, band, 4, facts)
    }
    private val demonstrated = session("before")
    private val overload = session("after", listOf(200, 205, 210, 215),
        listOf(3, 3, 2, 2), listOf(2, 1, 0, 0))
    private fun review(
        earlier: ProgressionWorkoutEvidence = demonstrated,
        latest: ProgressionWorkoutEvidence = overload,
        stock: VerifiedLoadStock? = verified,
        basis: LoadBasis = LoadBasis.SMITH_TOTAL
    ) = OverloadRecoveryLoadReviewDomain.assess(earlier, latest, stock, basis)

    @Test fun reportedOverloadReturnsOnlyToPreviouslyDemonstratedLoadForReview() {
        val result = review()
        assertEquals(OverloadRecoveryReason.RETURN_TO_DEMONSTRATED_LOAD_FOR_REVIEW,
            result.reason)
        assertEquals(n(200), result.candidateReturnLoad)
        assertEquals(n(200), result.demonstratedLoad)
        assertEquals(n(215), result.heaviestAttemptedLoad)
        assertTrue(result.canOfferForReview)
        assertFalse(result.canAutoApply)
        // A below-floor 215-lb last set cannot turn into four 215-lb targets.
        assertNotEquals(n(215), result.candidateReturnLoad)
        assertEquals(n(215), overload.observations.last().actualLoad)
    }

    @Test fun uniformHeavyNearFailureAlsoReturnsOnlyToPriorVerifiedCapacity() {
        val failed = session("after", listOf(215, 215, 215, 215),
            listOf(2, 2, 2, 2), listOf(0, 0, 1, 0))
        assertEquals(n(200), review(latest = failed).candidateReturnLoad)
    }

    @Test fun noPriorProofNeverInventsATrainingWeight() {
        val weakEarlier = session("before", reps = listOf(3, 3, 2, 2))
        assertEquals(OverloadRecoveryReason.PRIOR_CAPACITY_NOT_DEMONSTRATED,
            review(earlier = weakEarlier).reason)
        val mixedEarlier = session("before", loads = listOf(200, 205, 210, 215))
        assertNull(review(earlier = mixedEarlier).candidateReturnLoad)
        val missingEffort = session("before", reported = false)
        assertNull(review(earlier = missingEffort).candidateReturnLoad)
        val nearFailure = session("before", rir = listOf(2, 2, 2, 1))
        assertNull(review(earlier = nearFailure).candidateReturnLoad)
    }

    @Test fun aRecentNearFailureAtReturnWeightBlocksUnsafeRecoveryAdvice() {
        // Earlier 200 lb x 8 was achievable; today 200 lb x 3 at 0 RIR
        // was not. The reviewer must not prescribe/recommend 200 again
        // merely because even heavier attempts also failed.
        val recentlyFailed=session("after",listOf(200,205,210,215),
            listOf(3,3,2,2),listOf(0,1,0,0))
        val refused=review(latest=recentlyFailed)
        assertEquals(OverloadRecoveryReason.INSUFFICIENT_LATEST_EVIDENCE,
            refused.reason)
        assertEquals(n(200),refused.demonstratedLoad)
        assertEquals(n(215),refused.heaviestAttemptedLoad)
        assertNull(refused.candidateReturnLoad)
        assertFalse(refused.canOfferForReview)

        // Stopping that 200-lb set deliberately at 2 actual RIR is NOT
        // evidence of near-limit failure; retain the conservative review.
        assertEquals(n(200),review().candidateReturnLoad)
    }

    @Test fun aSuccessfulHeavySessionIsNotMisclassifiedAsOverload() {
        val high = session("after", listOf(205, 205, 205, 205),
            listOf(8, 8, 8, 8))
        assertEquals(OverloadRecoveryReason.NO_REP_FLOOR_FAILURE,
            review(latest = high).reason)
        assertNull(review(latest = high).candidateReturnLoad)
    }

    @Test fun wrongExerciseUnitOrRepBandRejectsComparisons() {
        assertEquals(OverloadRecoveryReason.INCOMPARABLE_WORKOUTS,
            review(latest = session("after", exercise = "lat-pulldown")).reason)
        assertEquals(OverloadRecoveryReason.INCOMPARABLE_WORKOUTS,
            review(latest = session("after", unit = LoadUnit.KG)).reason)
        assertEquals(OverloadRecoveryReason.INCOMPARABLE_WORKOUTS,
            review(latest = session("after", band = RepBand(6, 10))).reason)
        assertEquals(OverloadRecoveryReason.INCOMPARABLE_WORKOUTS,
            review(latest = session("before")).reason)
    }

    @Test fun incompleteOrUnknownLatestCannotBecomeConfidentAdvice() {
        val missing = overload.copy(observations = overload.observations.dropLast(1))
        assertEquals(OverloadRecoveryReason.LATEST_SESSION_INCOMPLETE,
            review(latest = missing).reason)
        val omittedRir = overload.copy(observations =
            overload.observations.mapIndexed { i, row ->
                if (i == 3) row.copy(actualRir = null) else row
            })
        assertEquals(OverloadRecoveryReason.INSUFFICIENT_LATEST_EVIDENCE,
            review(latest = omittedRir).reason)
        val dippedBelowPrior = session("after", listOf(195, 205, 210, 215),
            listOf(3, 3, 2, 2))
        assertEquals(OverloadRecoveryReason.INSUFFICIENT_LATEST_EVIDENCE,
            review(latest = dippedBelowPrior).reason)
    }

    @Test fun inventoryMustMatchExactlyAndNoPhantomWeightsAreRounded() {
        assertEquals(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
            review(stock = null).reason)
        val wrongUnit = VerifiedLoadStock.Totals(
            LoadUnit.KG, LoadBasis.SMITH_TOTAL, listOf(n(200), n(205)))
        assertEquals(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
            review(stock = wrongUnit).reason)
        val missing200 = VerifiedLoadStock.Totals(
            LoadUnit.LB, LoadBasis.SMITH_TOTAL, listOf(n(195), n(205), n(215)))
        assertEquals(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
            review(stock = missing200).reason)
        val step10 = VerifiedLoadStock.Stack(LoadUnit.LB,
            LoadBasis.MACHINE_STACK, n(190), n(10), n(240))
        assertEquals(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
            review(stock = step10).reason)
        assertEquals(n(200), review(stock = step10,
            basis = LoadBasis.MACHINE_STACK).candidateReturnLoad)
    }

    @Test fun withoutHigherAttemptLatestDoesNotCauseUnnecessaryRollback() {
        val unchanged = session("after", reps = listOf(3, 3, 2, 2))
        assertEquals(OverloadRecoveryReason.NO_HIGHER_LOAD_ATTEMPT,
            review(latest = unchanged).reason)
    }
}
