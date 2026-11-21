package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishResult
import org.junit.Assert.*
import org.junit.Test

class NativeCustomFinishFeedbackTest {
    @Test fun lostAcknowledgementNeverClaimsFinishWasRolledBack() {
        val message=checkNotNull(NativeCustomFinishFeedback.message(null,false))
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
                    NativeCustomFinishFeedback.message(result,verified))
                assertTrue(reason.toString(),message.length in 50..250)
                assertFalse(reason.toString(),message.contains(reason.name))
                assertFalse(reason.toString(),message.contains("not committed"))
            }
        }
    }

    @Test fun inconclusiveHistoryAndIncompleteSetsGetDifferentActions() {
        val incomplete=checkNotNull(NativeCustomFinishFeedback.message(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.SESSION_NOT_READY),false))
        assertTrue(incomplete.contains("recorded sets"))
        val notEnrolled=checkNotNull(NativeCustomFinishFeedback.message(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_ENROLLED),false))
        assertTrue(notEnrolled.contains("History setup"))
        val storage=checkNotNull(NativeCustomFinishFeedback.message(
            NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.STORAGE_UNAVAILABLE),false))
        assertTrue(storage.contains("may have completed"))
        assertTrue(storage.contains("recover History"))
    }
}
