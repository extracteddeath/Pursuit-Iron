package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeFinishReviewPolicyTest {
    private val reviewed=NativeFinishReviewToken("workout-123",8L)

    @Test fun acceptsOnlyTheExactReviewedWorkoutRevision() {
        assertTrue(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",8L,true))
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",9L,true))
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",7L,true))
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-456",8L,true))
    }

    @Test fun recoveryAndIncompleteWorkNeverAuthorizeFinish() {
        assertFalse(NativeFinishReviewPolicy.canCommit(
            null,"workout-123",8L,true))
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",null,true))
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",8L,false))
    }

    @Test fun aNewReviewIsRequiredAfterAnySavedRevisionChange() {
        val afterCorrection=9L
        assertFalse(NativeFinishReviewPolicy.canCommit(
            reviewed,"workout-123",afterCorrection,true))
        assertTrue(NativeFinishReviewPolicy.canCommit(
            reviewed.copy(revision=afterCorrection),
            "workout-123",afterCorrection,true))
    }
}
