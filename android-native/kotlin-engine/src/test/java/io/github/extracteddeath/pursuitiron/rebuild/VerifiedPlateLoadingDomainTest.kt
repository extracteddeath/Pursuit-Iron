package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class VerifiedPlateLoadingDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val barbell=VerifiedLoadStock.Barbell(
        LoadUnit.LB,n("45"),
        mapOf(n("45") to 2,n("25") to 2,n("10") to 4,
            n("5") to 4,n("2.5") to 4)
    )
    private fun ready(target:String):VerifiedPlateBreakdown =
        (VerifiedPlateLoadingDomain.review(barbell,n(target),LoadUnit.LB)
            as VerifiedPlateLoadReview.Ready).breakdown

    @Test fun exactBarbellTotalUsesOnlyConfirmedPairsAndPrefersFewer() {
        val plan=ready("225.0")
        assertTrue(plan.exact)
        assertEquals(n("225"),plan.achievableTotal)
        assertEquals(n("225"),plan.targetTotal)
        assertEquals(n("45"),plan.barWeight)
        assertEquals(listOf(VerifiedPlatePair(n("45"),2)),plan.perSide)
        val onePlate=ready("135")
        assertEquals(listOf(VerifiedPlatePair(n("45"),1)),onePlate.perSide)
        assertEquals(n("45"),onePlate.barWeight)
        assertEquals(LoadUnit.LB,onePlate.unit)
    }

    @Test fun unattainableTargetIsClearlyLabeledLowerOptionNeverAutoApplied() {
        val alternative=ready("227")
        assertFalse(alternative.exact)
        assertEquals(n("227"),alternative.targetTotal)
        assertEquals(n("225"),alternative.achievableTotal)
        assertEquals(listOf(VerifiedPlatePair(n("45"),2)),alternative.perSide)
        assertTrue(alternative.achievableTotal<alternative.targetTotal)
    }

    @Test fun mixedStockAndZeroPlateBarAreHandledAsVerified() {
        val mixed=ready("200")
        assertEquals(n("200"),mixed.achievableTotal)
        assertEquals(n("77.5"),
            mixed.perSide.fold(BigDecimal.ZERO){total,pair->
                total.add(pair.weightPerPlate.multiply(BigDecimal(pair.pairs)))
            })
        val empty=ready("45")
        assertTrue(empty.exact)
        assertTrue(empty.perSide.isEmpty())
    }

    @Test fun rejectsMissingInvalidOrMismatchedInventoryWithoutDefaultBarWeight() {
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.NO_BAR_STOCK),
            VerifiedPlateLoadingDomain.review(null,n("225"),LoadUnit.LB))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.UNIT_MISMATCH),
            VerifiedPlateLoadingDomain.review(barbell,n("225"),LoadUnit.KG))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.BELOW_EMPTY_BAR),
            VerifiedPlateLoadingDomain.review(barbell,n("40"),LoadUnit.LB))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.INVALID_TARGET),
            VerifiedPlateLoadingDomain.review(barbell,n("-1"),LoadUnit.LB))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.INVALID_INVENTORY),
            VerifiedPlateLoadingDomain.review(barbell.copy(
                pairedPlates=mapOf(n("45") to -1)),n("225"),LoadUnit.LB))
    }

    @Test fun smithAndPerHandStockCannotMasqueradeAsBarbellPairs() {
        val smith=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n("115"),n("120"),n("125")))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.INCOMPATIBLE_BASIS),
            VerifiedPlateLoadingDomain.review(smith,n("120"),LoadUnit.LB))
        val dumbbells=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("40"),n("45")))
        assertEquals(VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.INCOMPATIBLE_BASIS),
            VerifiedPlateLoadingDomain.review(dumbbells,n("45"),LoadUnit.LB))
    }
}
