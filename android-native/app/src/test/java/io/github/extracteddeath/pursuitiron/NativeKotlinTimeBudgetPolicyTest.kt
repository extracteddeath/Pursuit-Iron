package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.KotlinShortSessionStatus
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinTimeBudgetPolicyTest {
    @Test fun opensWithFullWorkoutAndOnlyUsesFallbackForReadOnlyPreview() {
        assertNull(NativeKotlinTimeBudgetPolicy.initialSelectionMinutes)
        assertEquals(45,NativeKotlinTimeBudgetPolicy.previewMinutes(null))
        assertTrue(NativeKotlinTimeBudgetPolicy.reviewMatchesSelection(null,45))
        assertTrue(NativeKotlinTimeBudgetPolicy.mayOpen(null,null,false))
    }

    @Test fun changingTimeBudgetRejectsStalePreviewBeforeShowingOrAccepting() {
        for(minutes in listOf(20,30,45)) {
            assertEquals(minutes,NativeKotlinTimeBudgetPolicy.previewMinutes(minutes))
            assertTrue(NativeKotlinTimeBudgetPolicy.reviewMatchesSelection(minutes,minutes))
            for(other in listOf(20,30,45)) {
                if(other!=minutes)assertFalse(
                    NativeKotlinTimeBudgetPolicy.reviewMatchesSelection(minutes,other))
            }
        }
        assertFalse(NativeKotlinTimeBudgetPolicy.reviewMatchesSelection(null,30))
        assertFalse(NativeKotlinTimeBudgetPolicy.reviewMatchesSelection(30,45))
    }

    @Test fun shorterChoiceRequiresExplicitEligibleReviewedStatus() {
        for(minutes in listOf(20,30,45)) {
            assertFalse(NativeKotlinTimeBudgetPolicy.mayOpen(minutes,null,false))
            assertTrue(NativeKotlinTimeBudgetPolicy.mayOpen(minutes,
                KotlinShortSessionStatus.ALREADY_FITS,false))
            assertTrue(NativeKotlinTimeBudgetPolicy.mayOpen(minutes,
                KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW,false))
            assertTrue(NativeKotlinTimeBudgetPolicy.mayOpen(minutes,null,true))
        }
    }
}
