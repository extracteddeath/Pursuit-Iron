package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutVisitPart
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinExerciseHistoryRowIdentityTest {
    @Test fun stagedMyoAndDropRowsCannotDuplicateTheMainKeyInLazyHistory() {
        val keys=buildList {
            add(nativeExerciseHistoryRowKey("workout", "set",
                CustomWorkoutVisitPart.MAIN))
            for(stage in 1..5) add(nativeExerciseHistoryRowKey(
                "workout","set",CustomWorkoutVisitPart.FOLLOW_UP,stage))
            add(nativeExerciseHistoryRowKey("workout","set",null))
            add(nativeExerciseHistoryRowKey("different-workout","set",
                CustomWorkoutVisitPart.FOLLOW_UP,1))
        }
        assertEquals(keys.size,keys.distinct().size)
        assertEquals(keys[1],nativeExerciseHistoryRowKey(
            "workout","set",CustomWorkoutVisitPart.FOLLOW_UP,1))
    }
    @Test fun lengthPrefixesPreventWorkoutAndSetColonAliasing() {
        assertNotEquals(
            nativeExerciseHistoryRowKey("w:s","x",
                CustomWorkoutVisitPart.FOLLOW_UP,2),
            nativeExerciseHistoryRowKey("w","s:x",
                CustomWorkoutVisitPart.FOLLOW_UP,2))
    }
    @Test fun impossiblePresentationStageNeverBecomesALegitimateRow() {
        for(stage in listOf(0,6,-1))assertThrows(
            IllegalArgumentException::class.java) {
            nativeExerciseHistoryRowKey("w","set",
                CustomWorkoutVisitPart.FOLLOW_UP,stage)
        }
    }
}
