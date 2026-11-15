package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinKettlebellStockTest {
    private val reserved=StarterExerciseTranche.movements().mapNotNull{
        it.equipmentId?.takeIf{_ ->it.expectedLoadBasis!=LoadBasis.EXERCISE_OVERRIDE}}.toSet()
    private fun stock(singles:String="",pairs:String="",db:String="")=onboardingStock(
        LoadUnit.KG,db,"","","","","","",reserved,
        kettlebells=singles,kettlebellPairs=pairs)
    @Test fun onboardingKeepsSingleBellsMatchingPairsAndDumbbellsIndependent() {
        val onlySingle=checkNotNull(stock("16,24"))
        assertEquals(setOf("single-kettlebell"),onlySingle.keys)
        val both=checkNotNull(stock("12,16,24","12,16","10,20"))
        assertEquals(listOf(BigDecimal("12"),BigDecimal("16")),
            (both.getValue("matched-kettlebell-pair") as VerifiedLoadStock.Totals).loads)
        assertEquals(LoadBasis.KETTLEBELL_EACH,both.getValue("single-kettlebell").basis)
        assertEquals(LoadBasis.KETTLEBELL_EACH,both.getValue("matched-kettlebell-pair").basis)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,both.getValue("dumbbell-rack").basis)
        assertEquals(setOf("matched-kettlebell-pair"),checkNotNull(stock(pairs="16")).keys)
    }
    @Test fun incompleteInvalidOrDuplicatedBellWeightsCannotBecomeReviewedStock() {
        for(invalid in listOf("0","-16","16,16.0","16,","16kg","one")) {
            assertNull(invalid,stock(invalid))
            assertNull(invalid,stock(pairs=invalid))
        }
        assertTrue(checkNotNull(stock()).isEmpty())
        assertNull(parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.VERIFIED_STACK,
            LoadBasis.KETTLEBELL_EACH,"","8","4","24","",""))
    }
    @Test fun gymChoicesAndSharedUnitsDescribeOneBellForBothConventions() {
        val choices=kotlinGymStockChoices(StarterExerciseTranche.movements())
        assertEquals("Kettlebell (single)",choices.single{it.equipmentId=="single-kettlebell"}.displayName)
        val pair=choices.single{it.equipmentId=="matched-kettlebell-pair"}
        assertEquals("Kettlebells (matching pair)",pair.displayName)
        assertEquals(listOf("Kettlebell Gorilla Row"),pair.exerciseNames)
        assertEquals("per kettlebell",kotlinLoadBasisLabel(pair.basis))
        assertEquals(" per kettlebell",kotlinLoadBasisSuffix(pair.basis))
        assertEquals("per hand",kotlinLoadBasisLabel(LoadBasis.DUMBBELL_PER_HAND))
        assertEquals("",kotlinLoadBasisSuffix(LoadBasis.BARBELL_TOTAL))
        val parsed=parseVerifiedStock(LoadUnit.KG,PhysicalStockEditorKind.EXACT_LOADS,
            LoadBasis.KETTLEBELL_EACH,"12,16,24","","","","","") as VerifiedLoadStock.Totals
        assertEquals(listOf(BigDecimal("12"),BigDecimal("16"),BigDecimal("24")),parsed.loads)
    }
    @Test fun machinesCannotShadowEitherReviewedKettlebellIdentity() {
        for(id in listOf("single-kettlebell","matched-kettlebell-pair"))
            assertNull(onboardingStock(LoadUnit.KG,"","","","",id,"16,24","",reserved))
    }
}
