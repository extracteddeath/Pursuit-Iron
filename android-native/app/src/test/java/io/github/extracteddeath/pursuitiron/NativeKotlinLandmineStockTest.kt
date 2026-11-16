package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinLandmineStockTest {
    private val reserved=StarterExerciseTranche.movements().mapNotNull{
        it.equipmentId?.takeIf{_ ->it.expectedLoadBasis!=LoadBasis.EXERCISE_OVERRIDE}}.toSet()
    private fun stock(loads:String)=onboardingStock(LoadUnit.KG,"","","","","","","",reserved,landmineLoads=loads)
    @Test fun enteredPlateTotalsIncludeAnExplicitUnloadedBarWithoutCreatingFreeBarOrMachineStock() {
        val parsed=checkNotNull(stock("0,5,7.5,10"))
        assertEquals(setOf("landmine-station"),parsed.keys)
        val physical=parsed.getValue("landmine-station") as VerifiedLoadStock.Totals
        assertEquals(LoadBasis.LANDMINE_ADDED_PLATES,physical.basis)
        assertEquals(listOf("0","5","7.5","10").map{BigDecimal(it)},physical.loads)
        assertTrue((EquipmentLoadDomain.inspect(physical,BigDecimal.ZERO,LoadUnit.KG) as LoadInspection.Confirmed).exact)
        assertTrue(checkNotNull(stock("")).isEmpty())
        for(bad in listOf("-1","0,0.0","10,","bar+10","ten"))assertNull(bad,stock(bad))
        assertNull(onboardingStock(LoadUnit.KG,"","","","","landmine-station","10,20","",reserved))
    }
    @Test fun calibrationAndBothLevelingEditorsRecognizeOnlyExplicitAddedPlateZeros() {
        assertTrue(kotlinWorkingLoadEntered(BigDecimal("0.0"),LoadBasis.LANDMINE_ADDED_PLATES))
        assertTrue(kotlinWorkingLoadEntered(BigDecimal("5"),LoadBasis.LANDMINE_ADDED_PLATES))
        assertFalse(kotlinWorkingLoadEntered(BigDecimal("-1"),LoadBasis.LANDMINE_ADDED_PLATES))
        assertFalse(kotlinWorkingLoadEntered(null,LoadBasis.LANDMINE_ADDED_PLATES))
        assertFalse(kotlinWorkingLoadEntered(BigDecimal.ZERO,null))
        for(basis in LoadBasis.entries.filterNot{it.permitsZeroAddedLoad})
            assertFalse(kotlinWorkingLoadEntered(BigDecimal.ZERO,basis))
    }
    @Test fun gymEditorNamesTheSharedStationAndOnlyAllowsExactAddedPlateTotals() {
        val choice=kotlinGymStockChoices(StarterExerciseTranche.movements()).single{it.equipmentId=="landmine-station"}
        assertEquals("Landmine (bar and anchored pivot)",choice.displayName)
        assertEquals(3,choice.exerciseNames.size)
        assertEquals("landmine added plates",kotlinLoadBasisLabel(choice.basis))
        assertEquals(" added plates",kotlinLoadBasisSuffix(choice.basis))
        val parsed=parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.EXACT_LOADS,
            choice.basis,"0,5,10","","","","","") as VerifiedLoadStock.Totals
        assertEquals(BigDecimal.ZERO,parsed.loads.first())
        assertNull(parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.EXACT_LOADS,
            choice.basis,"","","","","",""))
        assertNull(parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.VERIFIED_STACK,
            choice.basis,"","0","5","20","",""))
        assertNull(parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.BARBELL_WITH_PAIRS,
            choice.basis,"","","","","20","5x2"))
    }
}
