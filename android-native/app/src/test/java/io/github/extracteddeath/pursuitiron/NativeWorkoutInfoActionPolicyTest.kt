package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeWorkoutInfoActionPolicyTest {
    @Test fun unfinishedActiveExerciseCanRequestSkipWithoutWritingAnything() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=true,
            hasActiveSwap=false,hasRecordedWork=true)
        assertTrue(result.mayRequestSkipRemaining)
        assertFalse(result.mayRequestUndoSwap)
    }

    @Test fun unloggedActiveReplacementOffersUndoAndRemainingSkip() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=true,
            hasActiveSwap=true,hasRecordedWork=false)
        assertTrue(result.mayRequestSkipRemaining)
        assertTrue(result.mayRequestUndoSwap)
    }

    @Test fun loggedReplacementNeverAllowsUndoEvenIfSomeSetsRemain() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=true,
            hasActiveSwap=true,hasRecordedWork=true)
        assertTrue(result.mayRequestSkipRemaining)
        assertFalse(result.mayRequestUndoSwap)
    }

    @Test fun linkedSupersetCannotOfferUnreviewedUndo() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=true,hasActiveSwap=true,
            hasRecordedWork=false,linkedSuperset=true)
        assertFalse(result.mayRequestUndoSwap)
    }

    @Test fun finishedPausedOrUnverifiedSourceNeverShowsActions() {
        listOf(false,true).forEach{hasHistory->
            val result=NativeWorkoutInfoActionPolicy.review(
                active=false,hasRemainingSets=true,hasActiveSwap=true,
                hasRecordedWork=hasHistory)
            assertFalse(result.mayRequestSkipRemaining)
            assertFalse(result.mayRequestUndoSwap)
        }
    }

    @Test fun completedExerciseNeverOffersSkipEvenWhenUndoIsPossible() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=false,
            hasActiveSwap=true,hasRecordedWork=false)
        assertFalse(result.mayRequestSkipRemaining)
        assertTrue(result.mayRequestUndoSwap)
    }

    @Test fun noSourceSwapEventCannotInventUndo() {
        val result=NativeWorkoutInfoActionPolicy.review(
            active=true,hasRemainingSets=true,
            hasActiveSwap=false,hasRecordedWork=false)
        assertTrue(result.mayRequestSkipRemaining)
        assertFalse(result.mayRequestUndoSwap)
    }
}
