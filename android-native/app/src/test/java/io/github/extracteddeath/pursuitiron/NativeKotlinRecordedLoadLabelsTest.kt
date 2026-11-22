package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinRecordedLoadLabelsTest {
    private fun target(basis:LoadBasis,value:String="25",unit:LoadUnit=LoadUnit.LB)=
        DraftSetLoad.Verified(unit,basis,BigDecimal(value))
    private fun actual(value:String?,unit:LoadUnit?)=
        KotlinPerformedSet(value?.let(::BigDecimal),unit,8,null)

    @Test fun planOutlineKeepsTheExactTargetInsideItsAuthoredRange() {
        val set=CustomAuthoredSet(DraftSetType.WORKING,target(LoadBasis.DUMBBELL_PER_HAND),
            RepBand(8,12),2,40,90,exactRepTarget=10)
        assertEquals("Set 2 · Working · 25 lb · per hand · 10 reps (8–12 range) · RIR 2 · 90s rest",
            authoredSetDescription(set,2))
        val ranged=set.copy(load=DraftSetLoad.ExplicitBodyweight,exactRepTarget=null)
        assertEquals("Set 1 · Working · Bodyweight · 8–12 reps · RIR 2 · 90s rest",
            authoredSetDescription(ranged,1))
    }

    @Test fun dumbbellsAndKettlebellsRemainPerImplementWithoutDoubling() {
        val db=target(LoadBasis.DUMBBELL_PER_HAND)
        val bell=target(LoadBasis.KETTLEBELL_EACH)
        val performed=actual("25",LoadUnit.LB)
        assertEquals("25 lb · per hand",kotlinIssuedLoadLabel(db))
        assertEquals("25 lb · per kettlebell",kotlinIssuedLoadLabel(bell))
        assertEquals("25 lb · per hand",kotlinActualLoadLabel(performed,db))
        assertEquals("25 lb · per kettlebell",kotlinActualLoadLabel(performed,bell))
        assertEquals(BigDecimal("25"),performed.load)
        assertNull(performed.actualRir)
    }
    @Test fun landmineZeroAddedPlatesNeverBecomesZeroTotalBarbellMass() {
        val load=target(LoadBasis.LANDMINE_ADDED_PLATES,"0")
        assertEquals("0 lb · landmine added plates",kotlinIssuedLoadLabel(load))
        assertEquals("0 lb · landmine added plates",kotlinActualLoadLabel(actual("0",LoadUnit.LB),load))
        assertEquals("45 lb · total barbell weight",kotlinIssuedLoadLabel(target(LoadBasis.BARBELL_TOTAL,"45")))
        assertEquals("15 lb · total Smith weight",kotlinIssuedLoadLabel(target(LoadBasis.SMITH_TOTAL,"15")))
    }
    @Test fun actualFractionsAndMachineConventionsAreDisplayedWithoutRoundingOrConversion() {
        val observed=actual("38.125",LoadUnit.KG)
        assertEquals("38.125 kg · cable stack",kotlinActualLoadLabel(observed,
            target(LoadBasis.CABLE_STACK,"40",LoadUnit.KG)))
        assertEquals("38.125 kg · machine stack",kotlinActualLoadLabel(observed,
            target(LoadBasis.MACHINE_STACK,"40",LoadUnit.KG)))
        assertEquals("38.125 kg · exercise load",kotlinActualLoadLabel(observed,
            target(LoadBasis.EXERCISE_OVERRIDE,"40",LoadUnit.KG)))
        assertEquals(BigDecimal("38.125"),observed.load)
    }
    @Test fun missingOrDifferentActualUnitCannotBorrowTheTargetsConvention() {
        val issued=target(LoadBasis.DUMBBELL_PER_HAND,"25")
        assertEquals("20 unit unreported",kotlinActualLoadLabel(actual("20",null),issued))
        assertEquals("20 kg",kotlinActualLoadLabel(actual("20",LoadUnit.KG),issued))
        assertEquals("Load not reported",kotlinActualLoadLabel(actual(null,LoadUnit.LB),issued))
        assertEquals("Load not reported",kotlinActualLoadLabel(actual(null,null),issued))
    }
    @Test fun bodyweightRequiresTheExplicitIssuedBodyweightConvention() {
        val issued=DraftSetLoad.ExplicitBodyweight
        assertEquals("Bodyweight",kotlinIssuedLoadLabel(issued))
        assertEquals("Bodyweight",kotlinActualLoadLabel(actual(null,null),issued))
        assertEquals("Load not reported",kotlinActualLoadLabel(actual(null,LoadUnit.LB),issued))
        assertEquals("10 lb",kotlinActualLoadLabel(actual("10",LoadUnit.LB),issued))
    }
    @Test fun reportedActualNeverDisplaysTheTargetLoadInstead() {
        val issued=target(LoadBasis.BARBELL_TOTAL,"135")
        val observed=actual("130.00",LoadUnit.LB)
        assertEquals("135 lb · total barbell weight",kotlinIssuedLoadLabel(issued))
        assertEquals("130 lb · total barbell weight",kotlinActualLoadLabel(observed,issued))
        assertEquals(BigDecimal("130.00"),observed.load)
        assertEquals(BigDecimal("135"),issued.value)
    }
}
