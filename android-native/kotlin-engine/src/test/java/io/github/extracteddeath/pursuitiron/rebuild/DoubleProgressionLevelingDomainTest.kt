package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class DoubleProgressionLevelingDomainTest {
    private fun n(s: String) = BigDecimal(s)
    private val stock = VerifiedLoadStock.Totals(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
        listOf("100","105","110","115","120","125","130").map(::n))
    private val band = RepBand(5,8)
    private fun facts(vararg weights: String) =
        weights.map { LevelingSetFact(n(it),5,3) }

    @Test fun ascendingPyramidOffersOptionalRoundedStraightSetCandidate() {
        val review = DoubleProgressionLevelingDomain.review(
            facts("100","120","125","130"),band,2,stock)
            as LevelingReviewResult.Candidate
        assertEquals(n("118.75"),review.averageObservedLoad)
        assertEquals(n("120"),review.proposedStraightSetLoad)
        assertEquals(5,review.repsPerSet)
        assertEquals(4,review.setCount)
        assertFalse(review.mayModifyIssuedWorkout)
    }

    @Test fun levelingNeverIgnoresReportedRirBelowGoalOrRepFloor() {
        assertEquals(LevelingReviewResult.Rejected(
            LevelingRefusal.INSUFFICIENT_EFFORT_EVIDENCE),
            DoubleProgressionLevelingDomain.review(
                listOf(LevelingSetFact(n("100"),5,3),
                    LevelingSetFact(n("120"),5,1)),band,2,stock))
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.BELOW_REP_FLOOR),
            DoubleProgressionLevelingDomain.review(
                listOf(LevelingSetFact(n("100"),4,3),
                    LevelingSetFact(n("120"),5,3)),band,2,stock))
    }

    @Test fun refusesUnverifiedStockAndAlreadyLevelWorkouts() {
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.UNVERIFIED_STOCK),
            DoubleProgressionLevelingDomain.review(
                facts("100","120"),band,2,null))
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.ALREADY_LEVEL),
            DoubleProgressionLevelingDomain.review(
                facts("120","120","120"),band,2,stock))
    }
    @Test fun missingRirIsClearlyLabeledAsAssumptionNotMeasuredCapacity() {
        val review = DoubleProgressionLevelingDomain.review(
            listOf(LevelingSetFact(n("100"),5,null),
                LevelingSetFact(n("120"),5,null)),band,2,stock)
            as LevelingReviewResult.Candidate
        assertTrue(review.note.contains("RIR was unreported for 2"))
        assertFalse(review.mayModifyIssuedWorkout)
    }

}
