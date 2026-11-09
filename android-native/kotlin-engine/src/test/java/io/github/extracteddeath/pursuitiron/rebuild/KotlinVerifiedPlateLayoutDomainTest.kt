package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinVerifiedPlateLayoutDomainTest {
    private fun d(value:String)=BigDecimal(value)
    private fun stock(
        bar:String="45",
        plates:Map<String,Int> = mapOf(
            "45" to 2,"25" to 1,"10" to 2,"5" to 1,"2.5" to 1)
    ):VerifiedLoadStock.Barbell =VerifiedLoadStock.Barbell(
        LoadUnit.LB,d(bar),plates.mapKeys{d(it.key)})

    @Test fun precisePairsAndRealBarWeightAreKept() {
        val layout=KotlinVerifiedPlateLayoutDomain.inspect(
            stock(),d("225"),LoadUnit.LB)
        assertNotNull(layout)
        assertEquals(0,checkNotNull(layout).total.compareTo(d("225")))
        assertEquals(0,layout.barWeight.compareTo(d("45")))
        assertEquals(LoadUnit.LB,layout.unit)
        assertEquals(1,layout.perSide.size)
        assertEquals(0,layout.perSide.first().weight.compareTo(d("45")))
        assertEquals(2,layout.perSide.first().countPerSide)
    }

    @Test fun rejectsUnknownStockAndDifferentLoadBases() {
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            null,d("225"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                listOf(d("225"))),d("225"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock(),d("225"),LoadUnit.KG))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock(),d("226"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock(),d("10"),LoadUnit.LB))
    }

    @Test fun doesNotInventMissingPlatePairs() {
        val limited=stock(plates=mapOf("45" to 1))
        assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(
            limited,d("135"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            limited,d("225"),LoadUnit.LB))
    }

    @Test fun usesConfirmedFractionalStockWithoutRounding() {
        val stock=stock("20",mapOf("1.25" to 2,"0.5" to 1))
        val layout=KotlinVerifiedPlateLayoutDomain.inspect(
            stock,d("25"),LoadUnit.LB)
        assertNotNull(layout)
        assertEquals(2,checkNotNull(layout).perSide.first().countPerSide)
        assertEquals(0,layout.perSide.first().weight.compareTo(d("1.25")))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock,d("24.501"),LoadUnit.LB))
    }

    @Test fun equalPairSolutionsPreferHeavierStock() {
        val exact=stock("20",mapOf(
            "10" to 1,"5" to 2,"2.5" to 3))
        val layout=KotlinVerifiedPlateLayoutDomain.inspect(
            exact,d("40"),LoadUnit.LB)
        assertNotNull(layout)
        assertEquals(1,checkNotNull(layout).perSide.size)
        assertEquals(0,layout.perSide.first().weight.compareTo(d("10")))
        assertEquals(1,layout.perSide.first().countPerSide)
    }

    @Test fun corruptInventoryCannotProducePlateInstructions() {
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock(plates=mapOf("5" to 200)),d("55"),LoadUnit.LB))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(
            stock(plates=mapOf("-2.5" to 1)),d("45"),LoadUnit.LB))
    }
}
