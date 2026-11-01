package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class SyntheticSetReviewNavigationTest {
    private fun exercise(id: String, sets: Int) = WorkoutOutlineExercise(
        exerciseId = id, name = "Synthetic " + id, role = "accessory",
        sets = sets, repMin = 6, repMax = 10, rirMin = 1, rirMax = 3,
        restSeconds = 120.0, progressionStyle = "double"
    )
    private val sample = WorkoutOutline(
        source = WorkoutOutlineCodec.SOURCE,
        readOnly = true, syntheticOnly = true, split = "full_body", dayIndex = 0,
        sessionId = "synthetic-session", day = "monday",
        estimatedMinutes = 50.0, maxMinutes = 60.0,
        exercises = listOf(exercise("bench", 2), exercise("row", 4), exercise("curl", 1))
    )
    private val approved =
        OutlinePresentationPolicy.inspect(sample) as OutlinePresentation.SyntheticOnly

    @Test fun visitsEverySetInOrderWithoutGuessingIndividualTargets() {
        var cursor = SyntheticSetCursor(0,0)
        val visited = mutableListOf<Pair<Int,Int>>()
        val overall = mutableListOf<Int>()
        while (true) {
            val point = checkNotNull(SyntheticSetReviewNavigation.inspect(approved, cursor))
            visited += point.exerciseNumber to point.setNumber
            overall += point.overallSetNumber
            assertEquals(7, point.totalWorkoutSets)
            assertEquals(point.exercise.sets, point.totalExerciseSets)
            cursor = SyntheticSetReviewNavigation.next(approved, cursor) ?: break
        }
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2,
            2 to 3, 2 to 4, 3 to 1),visited)
        assertEquals((1..7).toList(),overall)
        assertNull(SyntheticSetReviewNavigation.next(approved,cursor))
        assertEquals(SyntheticSetCursor(2,0),cursor)
    }

    @Test fun previousCrossesExerciseBoundaryWithCorrectSetCounts() {
        assertNull(SyntheticSetReviewNavigation.previous(approved,SyntheticSetCursor(0,0)))
        assertEquals(SyntheticSetCursor(0,1),
            SyntheticSetReviewNavigation.previous(approved,SyntheticSetCursor(1,0)))
        assertEquals(SyntheticSetCursor(1,3),
            SyntheticSetReviewNavigation.previous(approved,SyntheticSetCursor(2,0)))
        assertEquals(SyntheticSetCursor(1,2),
            SyntheticSetReviewNavigation.previous(approved,SyntheticSetCursor(1,3)))
        assertEquals(SyntheticSetCursor(1,0),
            SyntheticSetReviewNavigation.jumpToExercise(approved,1))
    }

    @Test fun malformedOrOutOfRangeCursorsNeverProduceOrAdvanceSets() {
        for (bad in listOf(
            SyntheticSetCursor(-1,0),SyntheticSetCursor(0,-1),
            SyntheticSetCursor(3,0),SyntheticSetCursor(0,2),
            SyntheticSetCursor(2,1)
        )) {
            assertNull(SyntheticSetReviewNavigation.inspect(approved,bad))
            assertNull(SyntheticSetReviewNavigation.next(approved,bad))
            assertNull(SyntheticSetReviewNavigation.previous(approved,bad))
        }
        assertNull(SyntheticSetReviewNavigation.jumpToExercise(approved,-1))
        assertNull(SyntheticSetReviewNavigation.jumpToExercise(approved,99))
        assertEquals(2, sample.exercises[0].sets)
        assertEquals(4, sample.exercises[1].sets)
    }
}
