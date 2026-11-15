package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class SmithPairedPlateStockTest {
    private fun n(s:String)=BigDecimal(s)
    private fun smith(bar:String="15",pairs:Map<String,Int> = mapOf("45" to 1,"5" to 1))=
        VerifiedLoadStock.Smith(LoadUnit.LB,n(bar),pairs.mapKeys{n(it.key)})
    private fun inspect(stock:VerifiedLoadStock,amount:String)=
        EquipmentLoadDomain.inspect(stock,n(amount),stock.unit) as LoadInspection.Confirmed

    @Test fun confirmedCounterbalanceAndPairsKeepSmithTotalsSeparateFromFreeBars() {
        val smith=smith()
        val free=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),smith.pairedPlates)
        assertEquals(LoadBasis.SMITH_TOTAL,inspect(smith,"105").basis)
        assertTrue(inspect(smith,"105").exact)
        assertFalse(inspect(smith,"135").exact)
        assertTrue(inspect(free,"135").exact)
        assertFalse(inspect(free,"105").exact)
        val layout=checkNotNull(KotlinVerifiedPlateLayoutDomain.inspect(smith,n("105"),LoadUnit.LB))
        assertEquals(LoadBasis.SMITH_TOTAL,layout.basis)
        assertEquals(n("15"),layout.barWeight)
        assertEquals(listOf(KotlinPlateSide(n("45"),1)),layout.perSide)
        assertEquals(LoadBasis.BARBELL_TOTAL,checkNotNull(
            KotlinVerifiedPlateLayoutDomain.inspect(free,n("135"),LoadUnit.LB)).basis)
    }

    @Test fun zeroResistanceIsExplicitAndDoesNotBecomeBodyweightOrAFortyFivePoundBar() {
        val stock=smith("0")
        val empty=checkNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("0"),LoadUnit.LB))
        assertEquals(LoadBasis.SMITH_TOTAL,empty.basis)
        assertEquals(n("0"),empty.barWeight)
        assertTrue(empty.perSide.isEmpty())
        assertEquals(n("90"),checkNotNull(
            KotlinVerifiedPlateLayoutDomain.inspect(stock,n("90"),LoadUnit.LB)).total)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("45"),LoadUnit.LB))
    }

    @Test fun exactLegacySmithTotalsNeverBecomeAnInventedPhysicalInventory() {
        val old=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("105")))
        assertTrue(inspect(old,"105").exact)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(old,n("105"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(null,n("105"),LoadUnit.LB))
    }

    @Test fun kilogramCounterbalanceAndFractionalPlatesAreExactWithoutConversions() {
        val kg=VerifiedLoadStock.Smith(LoadUnit.KG,n("6.8"),mapOf(n("1.25") to 2))
        val layout=checkNotNull(KotlinVerifiedPlateLayoutDomain.inspect(kg,n("11.8"),LoadUnit.KG))
        assertEquals(LoadUnit.KG,layout.unit)
        assertEquals(listOf(KotlinPlateSide(n("1.25"),2)),layout.perSide)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(kg,n("11.8"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(kg,n("11.801"),LoadUnit.KG))
    }

    @Test fun eachSmithMachineKeepsItsOwnCounterbalanceAndPlateSupply() {
        val a=smith("15",mapOf("45" to 1))
        val b=smith("25",mapOf("25" to 2))
        assertTrue(inspect(a,"105").exact)
        assertFalse(inspect(b,"105").exact)
        assertTrue(inspect(b,"125").exact)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(a,n("125"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(b,n("105"),LoadUnit.LB))
    }

    @Test fun invalidOrExcessiveStockFailsClosedBeforeRenderingAnyPlateInstructions() {
        val bad=listOf(smith("-1"),smith("0.0001"),smith("100001"),
            smith(pairs=emptyMap()),smith(pairs=mapOf("45" to 0)),
            smith(pairs=mapOf("45" to 25)),smith(pairs=mapOf("-5" to 1)),
            smith(pairs=mapOf("0" to 1)),smith(pairs=mapOf("5" to 1,"5.0" to 1)),
            smith("99999",mapOf("5" to 1)))
        for(stock in bad) {
            assertEquals(stock.toString(),LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK),
                EquipmentLoadDomain.inspect(stock,n("15"),LoadUnit.LB))
            assertNull(stock.toString(),KotlinVerifiedPlateLayoutDomain.inspect(stock,n("15"),LoadUnit.LB))
        }
    }

    @Test fun suppliedMutablePlateCountsAreRevalidatedRatherThanTrusted() {
        val pairs=linkedMapOf(n("5") to 1)
        val stock=VerifiedLoadStock.Smith(LoadUnit.LB,n("15"),pairs)
        assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("25"),LoadUnit.LB))
        pairs[n("5")]=-1
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("25"),LoadUnit.LB))
    }

    @Test fun exhaustiveSmallInventoryOracleChecksAttainabilityBracketsAndEveryRenderedCount() {
        for(bar in listOf("0","6.8","15"))for(a in 1..3)for(b in 1..3) {
            val stock=VerifiedLoadStock.Smith(LoadUnit.KG,n(bar),mapOf(n("1.25") to a,n("2.5") to b))
            // Independent enumeration of real pairs; no production solver is used.
            val expected=(0..a).flatMap{x->(0..b).map{y->
                n(bar)+n("2.5")*BigDecimal(x)+n("5")*BigDecimal(y)}}.toSortedSet()
            for(step in 0..120) {
                val amount=BigDecimal(step).divide(n("4"))
                val checked=EquipmentLoadDomain.inspect(stock,amount,LoadUnit.KG) as LoadInspection.Confirmed
                assertEquals(expected.any{it.compareTo(amount)==0},checked.exact)
                assertEquals(expected.lastOrNull{it<=amount}?.stripTrailingZeros(),checked.atOrBelow?.stripTrailingZeros())
                assertEquals(expected.firstOrNull{it>amount}?.stripTrailingZeros(),checked.nextAbove?.stripTrailingZeros())
                val layout=KotlinVerifiedPlateLayoutDomain.inspect(stock,amount,LoadUnit.KG)
                assertEquals(checked.exact,layout!=null)
                if(layout!=null) {
                    assertEquals(LoadBasis.SMITH_TOTAL,layout.basis)
                    assertTrue(layout.perSide.all{it.countPerSide in 1..stock.pairedPlates.getValue(it.weight)})
                    val total=layout.barWeight+layout.perSide.fold(BigDecimal.ZERO){sum,p->
                        sum+p.weight*BigDecimal(2*p.countPerSide)}
                    assertEquals(0,amount.compareTo(total))
                }
            }
            // The fractional resistance need not coincide with a quarter step.
            expected.forEach{assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,it,LoadUnit.KG))}
        }
    }
}
