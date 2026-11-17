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

    @Test fun newForearmAndPlateStockAreNamedAndIndependentlyVerified() {
        val catalog=StarterExerciseTranche.movements()
        val choice=kotlinGymStockChoices(catalog).associateBy{it.equipmentId}
        for((id,label,muscles) in listOf(
            Triple("wrist-lever-hammer","Wrist lever hammer (verified resistance)",
                setOf("Hammer Radial Wrist Deviation","Hammer Ulnar Wrist Deviation")),
            Triple("wrist-roller-handle","Wrist roller (verified added load)",
                setOf("Wrist Roller")),
            Triple("single-weight-plate","Individual weight plate",
                setOf("Plate Front Raise"))
        )) {
            val verified=checkNotNull(choice[id])
            assertEquals(label,verified.displayName)
            assertEquals(LoadBasis.EXERCISE_OVERRIDE,verified.basis)
            assertEquals(muscles,verified.exerciseNames.toSet())
        }
        val plate=checkNotNull(parseVerifiedStock(LoadUnit.LB,
            PhysicalStockEditorKind.EXACT_LOADS,LoadBasis.EXERCISE_OVERRIDE,
            "5,10,25","","","","",""))
        assertTrue((EquipmentLoadDomain.inspect(plate,n("25"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
        assertFalse((EquipmentLoadDomain.inspect(plate,n("20"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
        assertNotEquals(choice.getValue("dumbbell-rack").equipmentId,
            choice.getValue("single-weight-plate").equipmentId)
    }

    @Test fun plateSvendAndSpringGripperHaveSeparateGymHardwareAndLoadSources() {
        val catalog=StarterExerciseTranche.movements()
        val choices=kotlinGymStockChoices(catalog).associateBy{it.equipmentId}
        val plate=checkNotNull(choices["single-weight-plate"])
        assertEquals("Individual weight plate",plate.displayName)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,plate.basis)
        assertEquals(setOf("Plate Front Raise","Svend Press"),
            plate.exerciseNames.toSet())
        val gripper=checkNotNull(choices["hand-gripper-device"])
        assertEquals("Hand gripper (verified rated settings)",gripper.displayName)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,gripper.basis)
        assertEquals(listOf("Hand Gripper"),gripper.exerciseNames)
        val device=checkNotNull(parseVerifiedStock(LoadUnit.LB,
            PhysicalStockEditorKind.EXACT_LOADS,LoadBasis.EXERCISE_OVERRIDE,
            "60,80,100","","","","",""))
        val observed=EquipmentLoadDomain.inspect(device,n("80"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertTrue(observed.exact)
        assertFalse((EquipmentLoadDomain.inspect(device,n("75"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
        assertNotEquals(gripper.equipmentId,plate.equipmentId)
        assertNotEquals(gripper.equipmentId,
            choices.getValue("dumbbell-rack").equipmentId)
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
    private fun smith(resistance:String="15",pairs:String="45x1, 5x1",unit:LoadUnit=LoadUnit.LB,
        basis:LoadBasis=LoadBasis.SMITH_TOTAL)=parseVerifiedStock(unit,
        PhysicalStockEditorKind.SMITH_WITH_PAIRS,basis,"","","","",resistance,pairs)

    @Test fun smithEditorRequiresItsOwnCounterbalanceAndAllowsAnExplicitZero() {
        val stock=checkNotNull(smith()) as VerifiedLoadStock.Smith
        assertEquals(n("15"),stock.barWeight)
        assertEquals(LoadBasis.SMITH_TOTAL,stock.basis)
        assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stock,n("105"),LoadUnit.LB))
        assertEquals(n("0"),(checkNotNull(smith("0")) as VerifiedLoadStock.Smith).barWeight)
        for(raw in listOf("","unknown","-1","15.0001","100001"))assertNull(raw,smith(raw))
        assertNull(smith(basis=LoadBasis.BARBELL_TOTAL))
        assertNull(smith(pairs="45x1,45.0x1"))
        assertNull(smith(pairs="45x25"))
        assertNull(smith("99999","5x1"))
    }

    @Test fun onboardingSelectsEachReviewedSmithStationWithoutBorrowingAnotherBarOrStation() {
        val catalog=StarterExerciseTranche.movements()
        val reserved=catalog.mapNotNull{it.equipmentId}.toSet()
        val choices=kotlinGymStockChoices(catalog).filter{it.basis==LoadBasis.SMITH_TOTAL}
        assertTrue(choices.size>=6)
        for(choice in choices) {
            val stock=checkNotNull(onboardingStock(LoadUnit.LB,"","","15","45x1,5x1",
                "","","",reserved,choice.equipmentId))
            assertEquals(setOf(choice.equipmentId),stock.keys)
            assertEquals(smith(),stock.getValue(choice.equipmentId))
            assertFalse(stock.containsKey("straight-bar"))
            assertFalse(stock.containsKey("trap-hex-bar"))
        }
        assertNull(onboardingStock(LoadUnit.LB,"","","15","45x1","","","",reserved,"unknown-smith"))
        assertNull(onboardingStock(LoadUnit.LB,"","","","45x1","","","",reserved,"flat-smith-station"))
    }

    @Test fun smithHardwareIsNotConvertedToAGymWithAnotherUnit() {
        val kg=checkNotNull(smith("6.8","1.25x2",LoadUnit.KG)) as VerifiedLoadStock.Smith
        assertEquals(LoadUnit.KG,kg.unit)
        assertNotNull(KotlinVerifiedPlateLayoutDomain.inspect(kg,n("11.8"),LoadUnit.KG))
        assertNull(KotlinVerifiedPlateLayoutDomain.inspect(kg,n("11.8"),LoadUnit.LB))
    }

    @Test fun sourceRoundTripKeepsSmithFreeAndTrapBarMassesAndReadOnlyLayoutsIndependent() {
        val original=source()
        val athlete=checkNotNull(original.setup.reviewed)
        val physical=mapOf("flat-smith-station" to checkNotNull(smith()),
            "straight-bar" to checkNotNull(bar("45")),"trap-hex-bar" to checkNotNull(bar("55")))
        val snapshot=original.copy(setup=original.setup.copy(reviewed=athlete.copy(
            gym=athlete.gym.copy(confirmedStock=athlete.gym.confirmedStock+physical))))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish("owner-1",0,snapshot))
        val before=hashes()
        val restored=(sources().read("owner-1") as NativeAuthoringSourceRead.Found).source
        assertEquals(snapshot,restored)
        val stocks=checkNotNull(restored.setup.reviewed).gym.confirmedStock
        for((id,total) in listOf("flat-smith-station" to "105","straight-bar" to "135","trap-hex-bar" to "145")) {
            val layout=checkNotNull(KotlinVerifiedPlateLayoutDomain.inspect(stocks.getValue(id),n(total),LoadUnit.LB))
            assertEquals(total,layout.total.toPlainString())
            assertEquals(if(id=="flat-smith-station")LoadBasis.SMITH_TOTAL else LoadBasis.BARBELL_TOTAL,layout.basis)
        }
        assertEquals(before,hashes())
    }

}
