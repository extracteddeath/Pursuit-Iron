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
        for (outcome in listOf(KotlinJournalSetOutcome.COMPLETED, KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            val edited = kotlinHistoryCorrectionActual(outcome, "25", "6", "", original, issued)
            assertEquals(original.copy(load = BigDecimal("25"), reps = 6, actualRir = null), edited)
        }
        assertNull(kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.SKIPPED, "", "", "", original, issued))
    }

    @Test fun addingAnActualWithoutPriorEvidenceLeavesTheStationUnknown() {
        assertEquals(KotlinPerformedSet(BigDecimal("25"), LoadUnit.LB, 6, 1),
            kotlinHistoryCorrectionActual(KotlinJournalSetOutcome.COMPLETED, "25", "6", "1", null, issued))
    }
}
