package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeBarbellPlateBreakdownTest {
    private val inventory=checkNotNull(NativeGymInventoryCodec.fromDraft(
        mapOf("bar" to "45","plates" to "2.5x2, 5x2, 25x1"),"lb").json)

    @Test fun exactVerifiedPairsAreSymmetricAndPreferFewerPlates() {
        val setup=checkNotNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","115"))
        assertEquals("45",setup.barWeight)
        assertEquals("115",setup.totalLoad)
        assertEquals("lb",setup.unit)
        assertEquals(listOf(NativePlatePairCount("25",1),
            NativePlatePairCount("5",2)),setup.perSide)
        assertEquals(listOf(NativePlatePairCount("25",1),
            NativePlatePairCount("5",1)),
            NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","105")?.perSide)
        assertEquals(emptyList<NativePlatePairCount>(),
            NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","45")?.perSide)
    }

    @Test fun unlistedTotalsMissingStockOrWrongUnitsNeverProduceImaginaryPlates() {
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(null,"lb","115"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"kg","115"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","47"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","115.001"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","20"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","99999"))
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(inventory,"lb","invalid"))
        val dumbbells=NativeGymInventoryCodec.fromDraft(
            mapOf("dumbbells" to "15, 20, 25"),"lb").json
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(dumbbells,"lb","45"))
    }

    @Test fun decimalPhysicalStockAndOtherVerifiedFamiliesStayExact() {
        val mixed=checkNotNull(NativeGymInventoryCodec.fromDraft(
            mapOf("bar" to "20","plates" to "1.25x2, 2.5x1",
                "override:lat-pulldown" to "exact: 30, 40"),"kg",
            setOf("lat-pulldown")).json)
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(mixed,"kg","27.5"))
        val setup=checkNotNull(NativeBarbellPlateBreakdownPolicy.inspect(
            mixed,"kg","27.5",setOf("lat-pulldown")))
        assertEquals(listOf(NativePlatePairCount("2.5",1),
            NativePlatePairCount("1.25",1)),setup.perSide)
        assertNull(NativeBarbellPlateBreakdownPolicy.inspect(
            mixed,"kg","27.501",setOf("lat-pulldown")))
    }
}
