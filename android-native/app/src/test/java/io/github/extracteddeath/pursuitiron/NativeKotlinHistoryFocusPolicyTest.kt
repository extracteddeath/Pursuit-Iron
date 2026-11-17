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
    @Test fun invalidOrDuplicateIdentifiersFailClosed() {
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "workout invalid",listOf("workout invalid")))
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "complete_2",listOf("complete_2","complete_2")))
        assertNull(NativeKotlinHistoryFocusPolicy.admit(
            "complete_2",emptyList()))
    }
}
