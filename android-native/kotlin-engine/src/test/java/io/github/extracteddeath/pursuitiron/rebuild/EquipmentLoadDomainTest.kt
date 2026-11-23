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

    @Test fun lowerNeighborMatchesVerifiedInventoryAcrossSelectorAndExplicitStock() {
        val stack = VerifiedLoadStock.Stack(LoadUnit.KG, LoadBasis.MACHINE_STACK,
            n("0"), n("0.003"), n("0.010"))
        val totals = VerifiedLoadStock.Totals(stack.unit, stack.basis,
            listOf(n("0"), n("0.003"), n("0.006"), n("0.009")))
        for (stock in listOf(stack, totals)) {
            assertNull(verified(stock, "0").nextBelow)
            assertEquals(n("0"), verified(stock, "0.003").nextBelow)
            assertEquals(n("0.003"), verified(stock, "0.006").nextBelow)
            assertEquals(n("0.006"), verified(stock, "0.007").nextBelow)
            assertEquals(n("0.009"), verified(stock, "0.010").nextBelow)
        }
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
        assertEquals(n("110"), full.nextBelow)

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
        assertEquals(n("25"),off.nextAbove)
        val high = verified(cable, "50")
        assertEquals(n("40"),high.atOrBelow)
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

    @Test fun numericallyEqualVerifiedLoadsHaveStablePlainDecimalRepresentation() {
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45.0"),
            mapOf(n("2.50") to 2,n("5") to 2,n("25.0") to 1))
        val inspected=verified(bar,"115.00")
        assertEquals(n("115"),inspected.entered)
        assertEquals(n("115"),inspected.atOrBelow)
        assertEquals(n("120"),inspected.nextAbove)
        assertEquals("115",inspected.atOrBelow?.toPlainString())
        val large=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n("1000.000"),n("1250.0")))
        val nearest=verified(large,"1200")
        assertEquals("1000",nearest.atOrBelow?.toPlainString())
        assertEquals("1250",nearest.nextAbove?.toPlainString())
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
    @Test fun stackArithmeticMatchesEveryBracketInAnExplicitInventory() {
        for (minimum in listOf("0", "2.500")) for (increment in listOf("0.125", "1.25", "3")) {
            val stack = VerifiedLoadStock.Stack(LoadUnit.KG, LoadBasis.MACHINE_STACK,
                n(minimum), n(increment), n("6.120"))
            val loads = generateSequence(stack.minimum) { it + stack.increment }
                .takeWhile { it <= stack.maximum }.toList()
            val explicit = VerifiedLoadStock.Totals(stack.unit, stack.basis, loads)
            for (tick in 0..150) {
                val input = BigDecimal(tick) * n("0.05")
                assertEquals(EquipmentLoadDomain.inspect(explicit, input, stack.unit),
                    EquipmentLoadDomain.inspect(stack, input, stack.unit))
            }
        }
    }

    @Test fun largestLegalStackHasAnExactLastStopAndRejectsAnExtraPosition() {
        val stack = VerifiedLoadStock.Stack(LoadUnit.KG, LoadBasis.ASSISTANCE_COUNTERWEIGHT,
            n("0"), n("0.001"), n("99.999"))
        val top = verified(stack, "99.999")
        assertTrue(top.exact)
        assertNull(top.nextAbove)
        assertEquals(n("99.999"), verified(stack, "100").atOrBelow)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK),
            EquipmentLoadDomain.inspect(stack.copy(maximum=n("100")), n("0"), stack.unit))
    }


    @Test fun equipmentStockAndSetReceiptShareExactlyOnePhysicalNumberEnvelope() {
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            listOf(n("0"),n("10"),n("100000")))
        val valid=listOf("0","0.0000","0.001","12.1000",
            "99999.999","100000.0000","1E+3")
        val invalid=listOf("-0.001","0.0001","12.1001",
            "99999.9991","100000.001","100001","1E-4")
        for(raw in valid) {
            val value=n(raw)
            assertTrue(KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value))
            assertTrue("Equipment must use the same envelope: $raw",
                EquipmentLoadDomain.inspect(stock,value,LoadUnit.LB)
                    is LoadInspection.Confirmed)
        }
        for(raw in invalid) {
            val value=n(raw)
            assertFalse(KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value))
            assertEquals("Invalid equipment input: $raw",
                LoadInspection.Unavailable(LoadUnavailableReason.INVALID_INPUT),
                EquipmentLoadDomain.inspect(stock,value,LoadUnit.LB))
        }
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK),
            EquipmentLoadDomain.inspect(
                VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
                    n("10"),n("0.0001"),n("20")),
                n("10"),LoadUnit.LB))
    }
}
