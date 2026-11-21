package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishResult
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
    @Test fun lostAcknowledgementNeverClaimsFinishWasRolledBack() {
        val message=checkNotNull(NativeFinishReviewPolicy.customFinishMessage(null,false))
        assertTrue(message.contains("unknown"))
        assertTrue(message.contains("may already be saved"))
        assertTrue(message.contains("verify History"))
        assertFalse(message.contains("not committed"))
        assertFalse(message.contains("storage unavailable"))
    }

    @Test fun everyRefusalExplainsRecoveryWithoutLeakingStorageEnumNames() {
        for(reason in NativeCustomFinishRefusal.entries) {
            val result=NativeCustomFinishResult.Rejected(reason)
            for(verified in listOf(false,true)) {
                val message=checkNotNull(
                    NativeFinishReviewPolicy.customFinishMessage(result,verified))
                assertTrue(reason.toString(),message.length in 50..250)
                assertFalse(reason.toString(),message.contains(reason.name))
                assertFalse(reason.toString(),message.contains("not committed"))
            }
        }
    }

    @Test fun inconclusiveHistoryAndIncompleteSetsGetDifferentActions() {
        val incomplete=checkNotNull(NativeFinishReviewPolicy.customFinishMessage(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.SESSION_NOT_READY),false))
        assertTrue(incomplete.contains("recorded sets"))
        val notEnrolled=checkNotNull(NativeFinishReviewPolicy.customFinishMessage(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_ENROLLED),false))
        assertTrue(notEnrolled.contains("History setup"))
        val storage=checkNotNull(NativeFinishReviewPolicy.customFinishMessage(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.STORAGE_UNAVAILABLE),false))
        assertTrue(storage.contains("may have completed"))
        assertTrue(storage.contains("recover History"))
    }
}
