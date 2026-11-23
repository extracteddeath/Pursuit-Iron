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

    @Test fun savedExerciseSearchAppliesOnlyToItsOwnVerifiedAthlete() {
        assertEquals("bench",NativeKotlinHistoryFocusPolicy.admitSearch(
            "bench","athleteA","athleteA"))
        assertEquals("",NativeKotlinHistoryFocusPolicy.admitSearch(
            "bench","athleteA","athleteB"))
        assertEquals("",NativeKotlinHistoryFocusPolicy.admitSearch(
            "bench","athleteA",null))
        assertEquals("",NativeKotlinHistoryFocusPolicy.admitSearch(
            "bench",null,"athleteA"))
        assertEquals("",NativeKotlinHistoryFocusPolicy.admitSearch(
            "bench","","athleteA"))
        assertEquals("",NativeKotlinHistoryFocusPolicy.admitSearch(
            "","athleteA","athleteA"))
        // A routine same-owner refresh must retain both the literal query
        // and its case; the browser, not this policy, decides text matching.
        assertEquals("DB Press",NativeKotlinHistoryFocusPolicy.admitSearch(
            "DB Press","athleteA","athleteA"))
    }

    @Test fun exerciseHistoryShowsEveryRecordedMachineUntilExplicitFilter() {
        val stations=listOf("gymA" to "machineA","gymA" to "machineB",
            "gymB" to "machineA")
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            true,"athleteA","athleteA","gymA","machineA",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,null,"athleteA","gymA","machineA",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteB","gymA","machineA",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA",null,"gymA","machineA",stations))
        assertEquals("gymA" to "machineB",
            NativeKotlinHistoryFocusPolicy.admitStation(
                false,"athleteA","athleteA","gymA","machineB",stations))
        assertEquals("gymB" to "machineA",
            NativeKotlinHistoryFocusPolicy.admitStation(
                false,"athleteA","athleteA","gymB","machineA",stations))
    }

    @Test fun removedOrAmbiguousStationNeverMasksVerifiedHistory() {
        val stations=listOf("gymA" to "machineA","gymA" to "machineB")
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteA","gymA","retired",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteA","","machineA",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteA","gymA","",stations))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteA","gymA","machineA",
            stations+("gymA" to "machineA")))
        assertNull(NativeKotlinHistoryFocusPolicy.admitStation(
            false,"athleteA","athleteA","gymA","machineA",emptyList()))
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
