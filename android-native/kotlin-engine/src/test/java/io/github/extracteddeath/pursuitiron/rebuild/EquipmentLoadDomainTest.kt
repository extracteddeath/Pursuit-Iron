package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class EquipmentLoadDomainTest {
    private fun n(raw: String) = BigDecimal(raw)
    private fun verified(stock: VerifiedLoadStock, value: String, unit: LoadUnit = stock.unit):
        LoadInspection.Confirmed {
        return EquipmentLoadDomain.inspect(stock, n(value), unit) as LoadInspection.Confirmed
    }

    @Test fun symmetricPairsRequireExactVerifiedPlates() {
        val bar = VerifiedLoadStock.Barbell(
            LoadUnit.LB, n("45"), mapOf(n("2.5") to 2, n("5") to 2, n("25") to 1)
        )
        val full = verified(bar, "115")
        assertEquals(LoadBasis.BARBELL_TOTAL, full.basis)
        assertTrue(full.exact)
        assertEquals(n("115"), full.atOrBelow)
        assertEquals(n("120"), full.nextAbove)

        val odd = verified(bar, "47")
        assertFalse(odd.exact)
        assertEquals(n("45"), odd.atOrBelow)
        assertEquals(n("50"), odd.nextAbove)
        assertFalse(verified(bar, "57.5").exact)
        assertTrue(verified(bar, "105").exact)
        assertFalse(verified(bar, "0").exact)
        assertNull(verified(bar, "0").atOrBelow)
        assertEquals(n("45"), verified(bar, "0").nextAbove)
    }

    @Test fun confirmedDumbbellValuesArePerHandNotDoubled() {
        val dumbbells = VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND, listOf(n("25"), n("30"), n("35")))
        val result = verified(dumbbells, "32.5")
        assertEquals(LoadBasis.DUMBBELL_PER_HAND, result.basis)
        assertFalse(result.exact)
        assertEquals(n("30"), result.atOrBelow)
        assertEquals(n("35"), result.nextAbove)
        assertTrue(verified(dumbbells, "35").exact)
    }

    @Test fun stackUsesExactDecimalsWithoutPhantomIntermediateWeights() {
        val cable = VerifiedLoadStock.Stack(LoadUnit.KG,LoadBasis.CABLE_STACK,
            minimum=n("2.5"),increment=n("2.5"),maximum=n("40"))
        assertTrue(verified(cable, "22.5").exact)
        val off = verified(cable, "23")
        assertFalse(off.exact)
        assertEquals(n("22.5"),off.atOrBelow)
        assertEquals(n("25.0"),off.nextAbove)
        val high = verified(cable, "50")
        assertEquals(n("40.0"),high.atOrBelow)
        assertNull(high.nextAbove)
    }

    @Test fun unknownUnitsAndUnconfirmedStockCannotCreateWeights() {
        val smith = VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n("40"),n("45"),n("50")))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(smith,n("45"),LoadUnit.KG))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK),
            EquipmentLoadDomain.inspect(null,n("0"),LoadUnit.LB))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.INVALID_INPUT),
            EquipmentLoadDomain.inspect(smith,n("38.5001"),LoadUnit.LB))
        assertFalse(verified(smith,"38.5").exact)
        assertEquals(n("40"),verified(smith,"38.5").nextAbove)
    }

    @Test fun malformedStockDoesNotReturnInventedOptions() {
        for(stock in listOf(
            VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),mapOf(n("5") to -1)),
            VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
                mapOf(n("2.5") to 1,n("2.50") to 1)),
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(n("25"),n("25.0"))),
            VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.CABLE_STACK,
                n("10"),n("0"),n("80")),
            VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
                n("10"),n("0.001"),n("500"))
        )) {
            assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK),
                EquipmentLoadDomain.inspect(stock,n("40"),LoadUnit.LB))
        }
    }

    @Test fun everyReportedBracketIsPhysicallyAttainableAndMonotonic() {
        val bar = VerifiedLoadStock.Barbell(LoadUnit.KG,n("20"),
            mapOf(n("1.25") to 2,n("2.5") to 1,n("10") to 2))
        var lastLower: BigDecimal? = null
        for (whole in 0..60) {
            val result=verified(bar, whole.toString())
            if (lastLower!=null && result.atOrBelow!=null)
                assertTrue(result.atOrBelow!! >= lastLower)
            if (result.atOrBelow!=null)assertTrue(result.atOrBelow!! <= result.entered)
            if (result.nextAbove!=null)assertTrue(result.nextAbove!! > result.entered)
            if (result.exact)assertEquals(result.entered,result.atOrBelow)
            lastLower=result.atOrBelow
        }
    }
}
