package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPhysicalMachine
import io.github.extracteddeath.pursuitiron.rebuild.StarterExerciseTranche
import io.github.extracteddeath.pursuitiron.rebuild.CatalogPhysicalMachineEligibility
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinPlanMachineReviewTest {
    private fun review(vararg configs:KotlinGymMachineAlternatives,
        eligible:Set<String> = setOf("machine_press")) = NativePlanMachineReview(
        gymId="gym-a", eligibleExerciseIds=eligible,
        roster=NativeMachineRosterSnapshot(
            ownerId="current-owner",revision=6,
            configurations=configs.toList()))

    @Test fun planExplainsPhysicalABForReviewedExerciseSpecificStations() {
        val eligible=CatalogPhysicalMachineEligibility.eligibleExerciseIds(
            StarterExerciseTranche.movements())
        val leg=KotlinGymMachineAlternatives("gym-a","leg_press","B",listOf(
            KotlinPhysicalMachine("A","Leg Press A"),
            KotlinPhysicalMachine("B","Leg Press B")))
        val gym=review(leg,eligible=eligible)
        assertEquals("2 active physical machines at this gym · Default: Leg Press B",
            nativePlanMachineDescription(gym,"leg_press"))
        assertEquals("No physical machines saved at this gym yet.",
            nativePlanMachineDescription(gym,"machine_press"))
        assertNull(nativePlanMachineDescription(gym,"plate-front-raise"))
        assertNull(nativePlanMachineDescription(gym,"barbell_bench"))
    }

    @Test fun showsOnlyVerifiedCurrentGymMachinesAndTheirDefault() {
        val a=KotlinPhysicalMachine("a","Machine A")
        val b=KotlinPhysicalMachine("b","Machine B")
        val current=KotlinGymMachineAlternatives(
            "gym-a","machine_press","b",listOf(a,b))
        val foreign=KotlinGymMachineAlternatives(
            "gym-b","machine_press","foreign",
            listOf(KotlinPhysicalMachine("foreign","Other gym")))
        val facts=nativePlanMachineDescription(review(foreign,current),"machine_press")
        assertEquals("2 active physical machines at this gym · Default: Machine B",facts)
        assertNull(nativePlanMachineDescription(review(foreign,current),"barbell_bench"))
        assertNull(nativePlanMachineDescription(null,"machine_press"))
    }

    @Test fun doesNotInventDefaultOrBorrowInactiveStation() {
        val inactive=KotlinPhysicalMachine("a","Machine A",active=false)
        val active=KotlinPhysicalMachine("b","Machine B")
        val saved=KotlinGymMachineAlternatives(
            "gym-a","machine_press","a",listOf(inactive,active))
        assertEquals("1 active physical machine at this gym · Default not verified",
            nativePlanMachineDescription(review(saved),"machine_press"))
        assertEquals("No physical machines saved at this gym yet.",
            nativePlanMachineDescription(review(),"machine_press"))
        assertEquals("No active physical machines at this gym.",
            nativePlanMachineDescription(review(saved.copy(machines=listOf(inactive))),
                "machine_press"))
    }

    @Test fun corruptDuplicatedRosterGroupNeedsGymReviewNotAnArbitraryWinner() {
        val group=KotlinGymMachineAlternatives("gym-a","machine_press",null,
            listOf(KotlinPhysicalMachine("a","Machine A")))
        assertEquals("Machine A/B choices need review in Gym.",
            nativePlanMachineDescription(review(group,group),"machine_press"))
    }
}
