package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinEquipmentRecoveryPolicyTest {
    @Test fun sameExercisePhysicalMachineSwitchIsSeparateFromMovementReplacement() {
        val actions=NativeKotlinEquipmentRecoveryPolicy.review(
            activeWorkout=true,hasPhysicalMachineConfig=true,
            hasRecordedExerciseWork=false)
        assertTrue(actions.canChangeStation)
        assertTrue(actions.canReplaceExercise)
        assertNull(actions.reason)
    }

    @Test fun noMachineConfigStillAllowsVerifiedExerciseReplacement() {
        val actions=NativeKotlinEquipmentRecoveryPolicy.review(
            activeWorkout=true,hasPhysicalMachineConfig=false,
            hasRecordedExerciseWork=false)
        assertFalse(actions.canChangeStation)
        assertTrue(actions.canReplaceExercise)
    }

    @Test fun actualRecordedWorkBlocksReplacementWithoutDisablingStationSelection() {
        val actions=NativeKotlinEquipmentRecoveryPolicy.review(
            activeWorkout=true,hasPhysicalMachineConfig=true,
            hasRecordedExerciseWork=true)
        assertTrue(actions.canChangeStation)
        assertFalse(actions.canReplaceExercise)
        assertTrue(actions.reason.orEmpty().contains("saved sets"))
    }

    @Test fun linkedSupersetRequiresReviewOfWholeGroup() {
        val actions=NativeKotlinEquipmentRecoveryPolicy.review(
            activeWorkout=true,hasPhysicalMachineConfig=true,
            hasRecordedExerciseWork=false,linkedSuperset=true)
        assertTrue(actions.canChangeStation)
        assertFalse(actions.canReplaceExercise)
        assertTrue(actions.reason.orEmpty().contains("superset"))
    }

    @Test fun finishedOrUnavailableWorkoutCannotOpenEitherMutation() {
        for(hasMachine in listOf(true,false)) {
            for(recorded in listOf(true,false)) {
                val actions=NativeKotlinEquipmentRecoveryPolicy.review(
                    activeWorkout=false,hasPhysicalMachineConfig=hasMachine,
                    hasRecordedExerciseWork=recorded)
                assertFalse(actions.canChangeStation)
                assertFalse(actions.canReplaceExercise)
            }
        }
    }
}
