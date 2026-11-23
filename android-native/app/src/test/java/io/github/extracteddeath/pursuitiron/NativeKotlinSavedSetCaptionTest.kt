package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Same factual observation must read consistently in generated and custom
 * workout receipts. No new number or weight conversion is permitted here. */
class NativeKotlinSavedSetCaptionTest {
    private fun issued(basis:LoadBasis,weight:String="25",unit:LoadUnit=LoadUnit.LB)=
        DraftSetLoad.Verified(unit,basis,BigDecimal(weight))
    private fun actual(weight:String?,unit:LoadUnit?,reps:Int=8,rir:Int?=null)=
        KotlinPerformedSet(weight?.let(::BigDecimal),unit,reps,rir)
    private fun caption(
        actual:KotlinPerformedSet?,target:DraftSetLoad,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    )=nativeSavedSetCaption("Saved",outcome,actual,target,
        "machine_press","gym_a",null)

    @Test fun generatedAndCustomUseExactPerImplementAndMeasuredActual() {
        assertEquals("Saved: completed · 8 reps · at 38.125 kg · per hand · 1 actual RIR",
            caption(actual("38.125",LoadUnit.KG,rir=1),
                issued(LoadBasis.DUMBBELL_PER_HAND,"40",LoadUnit.KG)))
        assertEquals("Saved: completed · 8 reps · at 25 lb · per kettlebell · actual RIR unreported",
            caption(actual("25",LoadUnit.LB),
                issued(LoadBasis.KETTLEBELL_EACH)))
        assertEquals("Saved: completed · 8 reps · at 0 lb · landmine added plates · actual RIR unreported",
            caption(actual("0.00",LoadUnit.LB),
                issued(LoadBasis.LANDMINE_ADDED_PLATES,"0")))
    }

    @Test fun machineStackAndAssistanceRemainExactPhysicalConventions() {
        assertTrue(caption(actual("50",LoadUnit.LB),
            issued(LoadBasis.MACHINE_STACK,"55")).contains(
                "at 50 lb · machine stack"))
        assertTrue(caption(actual("70",LoadUnit.LB),
            issued(LoadBasis.ASSISTANCE_COUNTERWEIGHT,"75")).contains(
                "at 70 lb · assistance counterweight (more = easier)"))
    }

    @Test fun missingWeightedLoadAndUnitMismatchNeverBecomeBodyweight() {
        val weighted=issued(LoadBasis.DUMBBELL_PER_HAND)
        val missing=caption(actual(null,null),weighted)
        assertTrue(missing,missing.contains("load not reported"))
        assertFalse(missing,missing.contains("bodyweight"))
        val missingUnit=caption(actual("25",null),weighted)
        assertTrue(missingUnit,missingUnit.contains("at 25 unit unreported"))
        assertFalse(missingUnit,missingUnit.contains("per hand"))
        val differentUnit=caption(actual("20",LoadUnit.KG),weighted)
        assertTrue(differentUnit,differentUnit.contains("at 20 kg"))
        assertFalse(differentUnit,differentUnit.contains("per hand"))
    }

    @Test fun onlyExplicitIssuedBodyweightCanBeCalledBodyweight() {
        val issued=DraftSetLoad.ExplicitBodyweight
        assertEquals("Saved: completed · 12 reps · bodyweight · actual RIR unreported",
            caption(actual(null,null,reps=12),issued))
        val unknown=caption(actual(null,LoadUnit.LB,reps=12),issued)
        assertTrue(unknown,unknown.contains("load not reported"))
        assertFalse(unknown,unknown.contains("bodyweight"))
    }

    @Test fun skippedAndFailedRecordsNeverInventACompletedSet() {
        val target=issued(LoadBasis.BARBELL_TOTAL,"135")
        assertEquals("Saved: skipped",caption(null,target,
            KotlinJournalSetOutcome.SKIPPED))
        val failed=caption(actual("135",LoadUnit.LB,reps=0),target,
            KotlinJournalSetOutcome.FAILED_ATTEMPT)
        assertTrue(failed,failed.startsWith("Saved: failed attempt · 0 reps"))
        assertTrue(failed,failed.contains("at 135 lb · total barbell weight"))
    }
}
