package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinHistoryFocusPolicyTest {
    @Test fun verifiedFinishedWorkoutReceivesExactFocus() {
        assertEquals("complete_2",NativeKotlinHistoryFocusPolicy.admit(
            "complete_2",listOf("complete_1","complete_2","complete_3")))
    }
    @Test fun staleOrUnknownWorkoutCannotSelectAnOlderCompletion() {
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "complete_4",listOf("complete_1","complete_2")))
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            null,listOf("complete_1")))
    }
    @Test fun savedRepetitionSheetIsBoundToTheCurrentVerifiedAthlete() {
        val repetitions=setOf("bench_press","leg_extension")
        assertEquals("bench_press",NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","athleteA","athleteA",repetitions))
        assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","athleteA","athleteB",repetitions))
        assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","athleteA",null,repetitions))
        assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press",null,"athleteA",repetitions))
        assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","","athleteA",repetitions))
    }

    @Test fun removedOrMeasuredOnlyExerciseNeverReopensARepHistorySheet() {
        val verifiedRepetitions=setOf("bench_press")
        for(stale in listOf("timed_plank","old_gym_exercise","",null)) {
            assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
                stale,"athleteA","athleteA",verifiedRepetitions))
        }
        assertNull(NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","athleteA","athleteA",emptySet()))
        assertEquals("bench_press",NativeKotlinHistoryFocusPolicy.admitExercise(
            "bench_press","athleteA","athleteA",verifiedRepetitions))
    }

    @Test fun invalidOrDuplicateIdentifiersFailClosed() {
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "workout invalid",listOf("workout invalid")))
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "complete_2",listOf("complete_2","complete_2")))
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "complete_2",emptyList()))
    }
}
