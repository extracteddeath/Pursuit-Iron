package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinWorkoutDisplayPolicyTest {
    @Test fun existingLastPreferenceKeepsItsExactKeyAndMeaning() {
        val previous=mapOf("reference-mode" to "LAST")
        val read=NativeKotlinWorkoutDisplaySettings.decode(previous)
        assertEquals(WorkoutReferenceMode.LAST,read.referenceMode)
        assertEquals(WorkoutStartLayout.AUTOMATIC,read.startLayout)
        assertTrue(read.keepScreenAwake)
        assertEquals(mapOf("reference-mode" to "LAST"),previous)
    }
    @Test fun newInstallUsesTargetKeepsScreenOnAndAdaptsTheInitialView() {
        val read=NativeKotlinWorkoutDisplaySettings.decode(emptyMap<String,Any>())
        assertEquals(WorkoutReferenceMode.TARGET,read.referenceMode)
        assertTrue(read.keepScreenAwake)
        for(width in listOf(0,320,393,599))assertTrue("$width",read.startLayout.startsInFocus(width))
        for(width in listOf(600,834,1280))assertFalse("$width",read.startLayout.startsInFocus(width))
    }
    @Test fun explicitFocusAndAllSetsDoNotChangeWithScreenWidth() {
        for(width in listOf(0,320,599,600,834,1280)) {
            assertTrue(WorkoutStartLayout.FOCUS.startsInFocus(width))
            assertFalse(WorkoutStartLayout.ALL_SETS.startsInFocus(width))
        }
    }
    @Test fun corruptedOrFutureFieldsFallBackIndependentlyWithoutErasingValidChoices() {
        val read=NativeKotlinWorkoutDisplaySettings.decode(mapOf(
            "reference-mode" to 7,"start-layout" to "ALL_SETS","keep-screen-awake" to false,"future-field" to "retain"))
        assertEquals(WorkoutReferenceMode.TARGET,read.referenceMode)
        assertEquals(WorkoutStartLayout.ALL_SETS,read.startLayout)
        assertFalse(read.keepScreenAwake)
        val unknown=NativeKotlinWorkoutDisplaySettings.decode(mapOf(
            "reference-mode" to "LAST","start-layout" to "FUTURE_DOCK","keep-screen-awake" to "false"))
        assertEquals(WorkoutReferenceMode.LAST,unknown.referenceMode)
        assertEquals(WorkoutStartLayout.AUTOMATIC,unknown.startLayout)
        assertTrue(unknown.keepScreenAwake)
    }
    @Test fun storedValuesRetainAllIndependentDisplayCombinations() {
        for(reference in WorkoutReferenceMode.entries)for(layout in WorkoutStartLayout.entries)for(awake in listOf(true,false)) {
            val values=mapOf("reference-mode" to reference.name,"start-layout" to layout.name,"keep-screen-awake" to awake)
            assertEquals(NativeKotlinWorkoutDisplaySettings(reference,layout,awake),NativeKotlinWorkoutDisplaySettings.decode(values))
        }
    }
}
