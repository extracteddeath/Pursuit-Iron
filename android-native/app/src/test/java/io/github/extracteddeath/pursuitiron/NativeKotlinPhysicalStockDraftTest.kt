package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinPhysicalStockDraftTest:NativeKotlinProgramFilesFixture() {
    private fun bar(mass:String="55",pairs:String="45x1",unit:LoadUnit=LoadUnit.LB,
        basis:LoadBasis=LoadBasis.BARBELL_TOTAL)=parseVerifiedStock(unit,
        PhysicalStockEditorKind.BARBELL_WITH_PAIRS,basis,"","","","",mass,pairs)

    @Test fun actualCatalogExposesOneTrapBarStockChoiceWithBothRelevantExercises() {
        val choices=kotlinGymStockChoices(StarterExerciseTranche.movements())
        val trap=choices.single{it.equipmentId=="trap-hex-bar"}
        assertEquals("Trap/hex bar and plates",trap.displayName)
        assertEquals(LoadBasis.BARBELL_TOTAL,trap.basis)
        assertEquals(listOf("Trap Bar Deadlift","Trap Bar Shrug"),trap.exerciseNames)
        assertNotEquals(choices.single{it.equipmentId=="straight-bar"}.equipmentId,trap.equipmentId)
    }

    @Test fun explicitBarMassAndSymmetricPairsDetermineAttainableLoads() {
        val trap=checkNotNull(bar()) as VerifiedLoadStock.Barbell
        val straight=checkNotNull(bar("45")) as VerifiedLoadStock.Barbell
        assertEquals(n("55"),trap.barWeight)
        assertEquals(mapOf(n("45") to 1),trap.pairedPlates)
        assertTrue((EquipmentLoadDomain.inspect(trap,n("145"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
        assertFalse((EquipmentLoadDomain.inspect(trap,n("135"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
        assertTrue((EquipmentLoadDomain.inspect(straight,n("135"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
        assertFalse((EquipmentLoadDomain.inspect(straight,n("145"),LoadUnit.LB) as LoadInspection.Confirmed).exact)
        val plates=checkNotNull(KotlinVerifiedPlateLayoutDomain.inspect(trap,n("145"),LoadUnit.LB))
        assertEquals(n("55"),plates.barWeight)
        assertEquals(n("145"),plates.total)
        assertEquals(listOf(KotlinPlateSide(n("45"),1)),plates.perSide)
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(trap,n("135"),LoadUnit.LB))
    }

    @Test fun missingOrInvalidBarMassNeverBecomesAFortyFivePoundDefault() {
        for(mass in listOf("","unknown","0","-1","55.0001","100001"))assertNull(mass,bar(mass))
        assertNull(bar(basis=LoadBasis.SMITH_TOTAL))
        assertNull(bar(pairs="45"))
        assertNull(bar(pairs="45x0"))
        assertNull(bar(pairs="45x1,45.0x1"))
        assertNull(bar(pairs="45x25"))
    }

    @Test fun kilogramHardwareStaysInItsConfirmedUnit() {
        val trap=checkNotNull(bar("25","20x1",LoadUnit.KG))
        val inspection=EquipmentLoadDomain.inspect(trap,n("65"),LoadUnit.KG) as LoadInspection.Confirmed
        assertTrue(inspection.exact)
        assertEquals(LoadUnit.KG,inspection.unit)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(trap,n("65"),LoadUnit.LB))
    }

    @Test fun onboardingBuildsOnlyTheChosenPhysicalBarWithNoStraightBarFallback() {
        val reserved=StarterExerciseTranche.movements().mapNotNull{
            it.equipmentId?.takeIf{_ ->it.expectedLoadBasis!=LoadBasis.EXERCISE_OVERRIDE}}.toSet()
        val trap=checkNotNull(onboardingStock(LoadUnit.LB,"","","55","45x1",
            "","","",reserved,"trap-hex-bar"))
        assertEquals(setOf("trap-hex-bar"),trap.keys)
        assertEquals(n("55"),(trap.getValue("trap-hex-bar") as VerifiedLoadStock.Barbell).barWeight)
        val straight=checkNotNull(onboardingStock(LoadUnit.LB,"","","45","45x1",
            "","","",reserved,"straight-bar"))
        assertEquals(setOf("straight-bar"),straight.keys)
        assertEquals(n("45"),(straight.getValue("straight-bar") as VerifiedLoadStock.Barbell).barWeight)
        assertNull(onboardingStock(LoadUnit.LB,"","","55","45x1",
            "","","",reserved,"unreviewed-bar"))
    }

    @Test fun onboardingCannotAcceptAnIncompleteBarOrShadowItWithAMachine() {
        val reserved=setOf("straight-bar","trap-hex-bar","ez-curl-bar")
        assertNull(onboardingStock(LoadUnit.LB,"","","","45x1","","","",reserved,"trap-hex-bar"))
        assertNull(onboardingStock(LoadUnit.LB,"","","55","","","","",reserved,"trap-hex-bar"))
        assertNull(onboardingStock(LoadUnit.LB,"","","55","45x1",
            "trap-hex-bar","20,40","",reserved,"trap-hex-bar"))
        assertNull(onboardingStock(null,"","","55","45x1","","","",reserved,"trap-hex-bar"))
    }

    @Test fun distinctBarsAndTheirMassesSurviveAnActualNativeSourceRoundTrip() {
        val original=source()
        val athlete=checkNotNull(original.setup.reviewed)
        val physical=mapOf("trap-hex-bar" to checkNotNull(bar()),
            "straight-bar" to checkNotNull(bar("45")))
        val snapshot=original.copy(setup=original.setup.copy(reviewed=athlete.copy(
            gym=athlete.gym.copy(confirmedStock=athlete.gym.confirmedStock+physical))))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(
            checkNotNull(signedIn).ownerKey,0,snapshot))
        val before=hashes()
        val restored=(sources().read(checkNotNull(signedIn).ownerKey) as NativeAuthoringSourceRead.Found).source
        assertEquals(snapshot,restored)
        val stock=checkNotNull(restored.setup.reviewed).gym.confirmedStock
        assertEquals(n("55"),(stock.getValue("trap-hex-bar") as VerifiedLoadStock.Barbell).barWeight)
        assertEquals(n("45"),(stock.getValue("straight-bar") as VerifiedLoadStock.Barbell).barWeight)
        assertEquals(before,hashes())
    }

    @Test fun conflictingBasisDeclarationsCannotCreateAnAmbiguousGymEditorChoice() {
        val actual=StarterExerciseTranche.movements().single{it.exerciseId=="trap-bar-deadlift"}
        val conflict=actual.copy(exerciseId="conflicting-trap",expectedLoadBasis=LoadBasis.SMITH_TOTAL)
        assertTrue(kotlinGymStockChoices(listOf(actual,conflict)).isEmpty())
    }

    @Test fun gymEditorUsesReviewedNamesForNewMovementsAndNeverRenamesTheirIds() {
        val movement=StarterExerciseTranche.movements().single{it.exerciseId=="snatch-deadlift"}
        assertEquals("Snatch-Grip Deadlift",kotlinExerciseLabel(movement.exerciseId))
        assertEquals("snatch-deadlift",movement.exerciseId)
    }
}
