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

    private fun optionalReview(proposal:String?,issued:String="215")=
        InWorkoutReviewResult(
            if(proposal==null) InWorkoutDirection.REVIEW_MORE_RECOVERY
            else InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            proposal?.let(::BigDecimal),CoachConfidence.LIMITED,
            "Review only. No changes are applied.",false,BigDecimal(issued))

    @Test fun generatedAndCustomCoachProposalsUseExactIssuedPhysicalConvention() {
        val advice=optionalReview("210.000")
        val smith=target(LoadBasis.SMITH_TOTAL,"215.00")
        val db=target(LoadBasis.DUMBBELL_PER_HAND,"215")
        assertEquals(
            "Optional review only: 210 lb · total Smith weight. " +
                "Issued target 215 lb · total Smith weight remains unchanged. " +
                "A different actual must be entered and confirmed in the set editor.",
            kotlinOptionalLiveSetAdviceLabel(advice,smith))
        assertTrue(kotlinOptionalLiveSetAdviceLabel(advice,db)!!
            .contains("210 lb · per hand"))
        assertEquals(BigDecimal("215.00"),smith.value)
        assertEquals(BigDecimal("210.000"),advice.weightForHumanReview)
    }

    @Test fun proposalFormattingHidesAStaleOrNonPhysicalTargetInsteadOfInventingAWeight() {
        val advice=optionalReview("210")
        assertNull(kotlinOptionalLiveSetAdviceLabel(advice,
            target(LoadBasis.SMITH_TOTAL,"220")))
        assertNull(kotlinOptionalLiveSetAdviceLabel(advice,
            DraftSetLoad.ExplicitBodyweight))
        assertNull(kotlinOptionalLiveSetAdviceLabel(optionalReview(null),
            target(LoadBasis.SMITH_TOTAL,"215")))
    }

    @Test fun independentUnitAndMachineConventionsAreNeverConverted() {
        val advice=optionalReview("37.5","40")
        val result=kotlinOptionalLiveSetAdviceLabel(advice,
            target(LoadBasis.CABLE_STACK,"40",LoadUnit.KG))
        assertTrue(result.orEmpty().contains("37.5 kg · cable stack"))
        assertTrue(result.orEmpty().contains(
            "Issued target 40 kg · cable stack remains unchanged"))
        assertFalse(result.orEmpty().contains("lb"))
    }
}
