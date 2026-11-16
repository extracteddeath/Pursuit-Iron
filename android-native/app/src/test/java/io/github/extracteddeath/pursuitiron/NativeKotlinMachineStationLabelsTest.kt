package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPhysicalMachine
import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinMachineStationLabelsTest {
    private val stations=listOf(
        "private_gym_a" to "station_a",
        "private_gym_b" to "station_b",
        "private_gym_b" to "station_a"
    )

    private val saved=NativeMachineRosterSnapshot("athlete_1",2L,listOf(
        KotlinGymMachineAlternatives(
            "private_gym_b","machine_chest_press","station_b",
            listOf(KotlinPhysicalMachine("station_a","Upstairs press"),
                KotlinPhysicalMachine("station_b","Hammer Strength"))
        )
    ))

    @Test fun exactPhysicalMachineShowsSavedNameButNeverInternalIds() {
        val label=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_b","station_b",stations,saved)
        assertEquals("Gym 2 · Hammer Strength",label)
        assertFalse(label.contains("private_gym"))
        assertFalse(label.contains("station_b"))
    }

    @Test fun unknownMachineUsesStableOrdinalNotRawIdentifier() {
        val label=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_b","station_b",stations,null)
        assertEquals("Gym 2 · Machine 2 (name unavailable)",label)
        assertFalse(label.contains("station_b"))
    }

    @Test fun differentExerciseOrGymCannotBorrowMachineName() {
        val otherExercise=NativeKotlinMachineStationLabels.label(
            "machine_shoulder_press","private_gym_b","station_b",stations,saved)
        val otherGym=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_a","station_a",stations,saved)
        assertEquals("Gym 2 · Machine 2 (name unavailable)",otherExercise)
        assertEquals("Gym 1 · Machine 1 (name unavailable)",otherGym)
    }

    @Test fun missingPhysicalMachineDoesNotSuggestTheCurrentDefault() {
        val label=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_b",null,stations,saved)
        assertEquals("Gym 2 · Earlier records (machine unspecified)",label)
    }

    @Test fun unrecordedGymDoesNotBorrowAConfiguredStation() {
        val label=NativeKotlinMachineStationLabels.label(
            "machine_chest_press",null,"station_b",stations,saved)
        assertEquals("Gym not recorded · Machine 1 (name unavailable)",label)
    }

    @Test fun numberingDoesNotDependOnHistoryIterationOrder() {
        val forward=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_b","station_b",stations,null)
        val reverse=NativeKotlinMachineStationLabels.label(
            "machine_chest_press","private_gym_b","station_b",stations.reversed(),null)
        assertEquals(forward,reverse)
    }
}
