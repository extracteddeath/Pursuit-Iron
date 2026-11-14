package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class OnboardingMachineStockDomainTest {
    private fun n(raw:String)=BigDecimal(raw)
    private val blocked=setOf(
        "straight-bar","cable-station","dumbbell-rack",
        "special-smith-station"
    )

    @Test fun multiplePhysicalMachinesStaySeparateAcrossCanonicalDraftAndVerifiedStock() {
        val first=checkNotNull(OnboardingMachineStockDomain.add(
            "","chest-press-machine","20,25.0,30",blocked))
        val second=checkNotNull(OnboardingMachineStockDomain.add(
            first,"leg-extension-machine","50,60,70",blocked))
        val parsed=checkNotNull(OnboardingMachineStockDomain.parse(second))
        assertEquals(2,parsed.size)
        assertEquals(listOf("chest-press-machine","leg-extension-machine"),
            parsed.map{it.equipmentId})
        assertEquals(listOf(n("20"),n("25"),n("30")),
            parsed.first().exactLoads)
        val stock=checkNotNull(OnboardingMachineStockDomain.verifiedStock(
            second,LoadUnit.KG,blocked))
        assertEquals(2,stock.size)
        assertEquals(LoadUnit.KG,stock.getValue("chest-press-machine").unit)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,
            stock.getValue("chest-press-machine").basis)
        assertTrue(EquipmentLoadDomain.inspect(
            stock.getValue("chest-press-machine"),n("25"),LoadUnit.KG)
            is LoadInspection.Confirmed)
        val leg=EquipmentLoadDomain.inspect(
            stock.getValue("leg-extension-machine"),n("25"),LoadUnit.KG)
            as LoadInspection.Confirmed
        assertFalse(leg.exact)
        assertEquals(n("50"),leg.nextAbove)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(stock.getValue("chest-press-machine"),
                n("25"),LoadUnit.LB))
    }

    @Test fun noMachineMayShadowAnotherStockIdentityOrDuplicateAnotherMachine() {
        for(id in blocked)assertNull(id,OnboardingMachineStockDomain.add(
            "",id,"20,25",blocked))
        val first=checkNotNull(OnboardingMachineStockDomain.add(
            "","chest-press-machine","20,25",blocked))
        assertNull(OnboardingMachineStockDomain.add(
            first,"chest-press-machine","5,10",blocked))
        assertNull(OnboardingMachineStockDomain.parse(
            "chest-press-machine=20;chest-press-machine=25"))
        assertNull(OnboardingMachineStockDomain.verifiedStock(
            "special-smith-station=20",LoadUnit.LB,blocked))
        assertNull(OnboardingMachineStockDomain.add(
            first,"invalid machine","5,10",blocked))
        assertNull(OnboardingMachineStockDomain.add(
            first,"other-machine","5,10",blocked+("other-machine")))
    }

    @Test fun onlyActualExactPhysicalLoadsAreAcceptedWithoutUnitConversionOrRounding() {
        for(raw in listOf("","25,25.0","-5,20","100001","10.0001",
            "10,NotANumber","20,","1e100","20,,30")) {
            assertNull(raw,OnboardingMachineStockDomain.exactLoads(raw))
            assertNull(raw,OnboardingMachineStockDomain.add(
                "","valid-machine",raw,blocked))
        }
        assertEquals(listOf(n("2.5"),n("5")),
            OnboardingMachineStockDomain.exactLoads("2.500, 5.0"))
        assertNull(OnboardingMachineStockDomain.parse(
            "valid-machine=5;malformed record"))
        assertNull(OnboardingMachineStockDomain.parse(
            "valid-machine=5=10"))
    }

    @Test fun removingOrEditingOneMachineNeverChangesOtherMachineWeights() {
        val before=checkNotNull(OnboardingMachineStockDomain.add(
            "","chest-press-machine","50,60",blocked))
        val both=checkNotNull(OnboardingMachineStockDomain.add(
            before,"leg-extension-machine","20,25",blocked))
        val withoutFirst=checkNotNull(OnboardingMachineStockDomain.remove(
            both,"chest-press-machine"))
        assertEquals(listOf("leg-extension-machine"),
            checkNotNull(OnboardingMachineStockDomain.parse(withoutFirst))
                .map{it.equipmentId})
        assertEquals(listOf(n("20"),n("25")),
            checkNotNull(OnboardingMachineStockDomain.parse(withoutFirst))
                .single().exactLoads)
        val edited=checkNotNull(OnboardingMachineStockDomain.add(
            withoutFirst,"chest-press-machine","70,80",blocked))
        assertEquals(listOf(n("20"),n("25")),
            checkNotNull(OnboardingMachineStockDomain.parse(edited))
                .first().exactLoads)
        assertNull(OnboardingMachineStockDomain.remove(edited,"missing"))
    }

    @Test fun boundedInventoryRejectsTwentyFifthMachineWithoutAlteringSavedTwentyFour() {
        var raw=""
        for(index in 1..OnboardingMachineStockDomain.MAX_MACHINES) {
            raw=checkNotNull(OnboardingMachineStockDomain.add(
                raw,"machine-$index","10,15",blocked))
        }
        assertEquals(24,checkNotNull(OnboardingMachineStockDomain.parse(raw)).size)
        assertNull(OnboardingMachineStockDomain.add(raw,"machine-25","20",blocked))
        assertEquals(24,checkNotNull(OnboardingMachineStockDomain.parse(raw)).size)
    }
}
