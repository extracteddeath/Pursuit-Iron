package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Exact reviewer provenance, never a guessed physical machine from a name. */
class CatalogPhysicalMachineEligibilityTest {
    private val catalog=StarterExerciseTranche.movements()
    private val byId=catalog.associateBy{it.exerciseId}

    @Test fun allFortyTwoReviewedExerciseOverrideMachinesGetAButPortableLoadsDoNot() {
        val overrides=catalog.filter {
            it.expectedLoadBasis==LoadBasis.EXERCISE_OVERRIDE
        }
        assertEquals(69,overrides.size) // 42 machines + 7 portable + 16 nominal bands + 4 neck implements
        val qualified=overrides.filter(CatalogPhysicalMachineEligibility::eligible)
        assertEquals(42,qualified.size)
        assertTrue(qualified.all{it.equipmentId!=null})
        for(id in listOf("leg_extension","seated_leg_curl",
            "machine_press","leg_press","hack_squat","lat-pulldown",
            "machine-row","incline-machine-press","machine-lat-raise",
            "machine-dip","standing_calf","machine-hip-thrust",
            "hip-abduction","adduction-machine","machine-shrug")) {
            assertTrue("Reviewer-approved machine missing A/B: "+id,
                CatalogPhysicalMachineEligibility.eligible(byId.getValue(id)))
        }
        for(id in listOf("hammer-radial-dev","hammer-ulnar-dev",
            "wrist-roller","plate-front-raise",
            "svend-press","gripper","weighted-pullup")) {
            assertFalse("Portable equipment must not create A/B: "+id,
                CatalogPhysicalMachineEligibility.eligible(byId.getValue(id)))
        }
        for(id in listOf("neck-extension","neck-curl","neck-harness","neck-lateral")) {
            assertFalse("Individually verified neck implements cannot masquerade as a physical machine A/B station: "+id,
                CatalogPhysicalMachineEligibility.eligible(byId.getValue(id)))
        }
        for(id in RatedResistanceBandDomain.reviewedExerciseIds()) {
            assertFalse("Nominal resistance bands cannot masquerade as physical A/B machines: "+id,
                CatalogPhysicalMachineEligibility.eligible(byId.getValue(id)))
        }
        assertTrue(CatalogPhysicalMachineEligibility.eligible(
            byId.getValue("low-to-high-face-pull")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(
            byId.getValue("dumbbell_crunch")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(
            byId.getValue("barbell_bench")))
    }

    @Test fun changedPhysicalImplementFamilyVariantOrReviewCannotGainAccess() {
        val reviewed=byId.getValue("machine_press")
        assertTrue(CatalogPhysicalMachineEligibility.eligible(reviewed))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            expectedLoadBasis=LoadBasis.DUMBBELL_PER_HAND,
            equipmentId="dumbbell-rack")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            equipmentId="wrist-roller-handle")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            variantId="unreviewed-machine-b")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            familyId="unknown_machine_press")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            creditReviewReference="owner-invented-policy")))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            creditStatus=CatalogCreditStatus.UNREVIEWED)))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(reviewed.copy(
            exerciseId="owner-created-machine_press")))
        val portable=byId.getValue("plate-front-raise")
        assertFalse(CatalogPhysicalMachineEligibility.eligible(portable.copy(
            equipmentId="chest-press-machine")))
    }

    @Test fun duplicateConflictedAndUnknownPhysicalExerciseIdentitiesFailClosed() {
        val reviewed=byId.getValue("leg_press")
        val unknown=reviewed.copy(exerciseId="unknown_plateload_machine",
            variantId="custom-unknown")
        val eligible=CatalogPhysicalMachineEligibility.eligibleExerciseIds(catalog)
        assertTrue("Legacy exercise-specific machine stock is not a bodyweight lift",
            "leg_press" in eligible)
        assertTrue("The same implement can be used for different exercises",
            "single-leg-press" in eligible && "leg-press-calf" in eligible)
        assertFalse("wrist-roller" in eligible)
        assertFalse("plate-front-raise" in eligible)
        assertFalse("svend-press" in eligible)
        assertFalse("gripper" in eligible)
        val conflicting=listOf(reviewed,reviewed.copy(
            expectedLoadBasis=LoadBasis.DUMBBELL_PER_HAND,
            equipmentId="dumbbell-rack"))
        assertFalse("Duplicate records cannot pick an arbitrary machine",
            "leg_press" in CatalogPhysicalMachineEligibility
                .eligibleExerciseIds(conflicting))
        assertFalse("Unknown exercise IDs cannot borrow generic hardware",
            unknown.exerciseId in CatalogPhysicalMachineEligibility
                .eligibleExerciseIds(listOf(unknown)))
        assertTrue("Known machine stock remains individually calibrated",
            KotlinMachineAlternativesDomain.confirmedStack(
                KotlinGymMachineAlternatives("gymX","leg_press","A",listOf(
                    KotlinPhysicalMachine("A","Leg Press A",unit=LoadUnit.LB,
                        increment=BigDecimal("5"),minimum=BigDecimal("50"),
                        maximum=BigDecimal("250")),
                    KotlinPhysicalMachine("B","Leg Press B",unit=LoadUnit.LB,
                        increment=BigDecimal("10"),minimum=BigDecimal("60"),
                        maximum=BigDecimal("260"))
                )),"A",LoadBasis.EXERCISE_OVERRIDE)?.increment==
                BigDecimal("5"))
    }
}
