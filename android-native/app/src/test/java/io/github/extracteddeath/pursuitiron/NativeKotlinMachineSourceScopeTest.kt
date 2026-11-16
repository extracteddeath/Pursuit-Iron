package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPhysicalMachine
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinMachineSourceScopeTest {
    private fun group(exercise:String,gym:String="gym-one",label:String="Machine A") =
        KotlinGymMachineAlternatives(gym,exercise,"id-a",
            listOf(KotlinPhysicalMachine("id-a",label)))

    private val qualified=setOf("machine_press","cable_row")

    @Test fun sourceQualifiedPhysicalStationsMayBeAddedWithoutChangingLegacyEvidence() {
        val invalidLegacy=group("barbell_bench")
        val eligiblePrior=group("machine_press")
        val proposed=listOf(invalidLegacy,eligiblePrior,
            group("cable_row"))
        assertTrue(nativeMachineRosterSourceEligible("gym-one",qualified,
            listOf(invalidLegacy,eligiblePrior),proposed))
    }

    @Test fun newBarbellDumbbellAndUnknownMachineGroupsAreRejected() {
        val valid=group("machine_press")
        for(nonStack in listOf("barbell_bench","dumbbell_row","unknown_exercise")) {
            assertFalse(nativeMachineRosterSourceEligible("gym-one",qualified,
                listOf(valid),listOf(valid,group(nonStack))))
        }
    }

    @Test fun legacyNonStackMachineHistoriesCannotBeEditedOrDeletedSilently() {
        val old=group("dumbbell_row")
        val valid=group("machine_press")
        val initial=listOf(old,valid)
        assertFalse(nativeMachineRosterSourceEligible("gym-one",qualified,
            initial,listOf(old.copy(defaultMachineId=null),valid)))
        assertFalse(nativeMachineRosterSourceEligible("gym-one",qualified,
            initial,listOf(old.copy(machines=listOf(
                KotlinPhysicalMachine("id-a","Different stack"))),valid)))
        assertFalse(nativeMachineRosterSourceEligible("gym-one",qualified,
            initial,listOf(valid)))
        assertTrue(nativeMachineRosterSourceEligible("gym-one",qualified,
            initial,initial))
    }

    @Test fun independentlyVerifiedOtherGymsStillUseTheExistingCrossGymFence() {
        val legacyOtherGym=group("machine_press",gym="gym-two")
        assertTrue(nativeMachineRosterSourceEligible("gym-one",qualified,
            listOf(legacyOtherGym),emptyList()))
        // The host separately runs preservesOtherGyms and rejects the removal.
    }

    @Test fun noCurrentMachineProvenanceFreezesAllExistingGroups() {
        val old=group("machine_press")
        assertTrue(nativeMachineRosterSourceEligible("gym-one",emptySet(),
            listOf(old),listOf(old)))
        assertFalse(nativeMachineRosterSourceEligible("gym-one",emptySet(),
            listOf(old),listOf(old.copy(machines=listOf(
                KotlinPhysicalMachine("id-a","Machine B"))))))
    }
}
