package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeKotlinHistoryCorrectionActualTest {
    private val issued = DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.MACHINE_STACK, BigDecimal("20"))

    @Test fun editingNumbersKeepsTheRecordedStationForCompletedAndFailedSets() {
        val original = KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 8, 2, "machine-b")
        val edited = kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.COMPLETED, "25", "6", "", original, issued)
        assertEquals(original.copy(load = BigDecimal("25"), reps = 6, actualRir = null), edited)
        val failed = kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.FAILED_ATTEMPT, "25", "6", "2", original, issued)
        assertEquals(original.copy(load = BigDecimal("25"), reps = 0, actualRir = null), failed)
        assertNull(kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.SKIPPED, "", "", "", original, issued))
    }

    @Test fun completedSetsMustPassJournalValidationAndBodyweightAttemptsNeedNoWeight() {
        assertNull(kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.COMPLETED, "25", "0", "", null, issued))
        assertNull(kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.COMPLETED, "", "8", "", null, issued))
        assertEquals(KotlinPerformedSet(null, null, 0, null),
            kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.FAILED_ATTEMPT, "", "8", "2", null,
                DraftSetLoad.ExplicitBodyweight))
    }

    @Test fun addingAnActualWithoutPriorEvidenceLeavesTheStationUnknown() {
        assertEquals(KotlinPerformedSet(BigDecimal("25"), LoadUnit.LB, 6, 1),
            kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.COMPLETED, "25", "6", "1", null, issued))
    }
}
