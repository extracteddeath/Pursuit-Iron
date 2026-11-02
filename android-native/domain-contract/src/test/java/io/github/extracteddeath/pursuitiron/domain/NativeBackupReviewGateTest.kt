package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Synthetic ownership races; never opens an Android document or store writer. */
class NativeBackupReviewGateTest {
    private val current = "a".repeat(64)
    private val selected = "b".repeat(64)
    private val hypothetical = "c".repeat(64)
    private val empty=NativeBackupMergeCounts(0,0,0,0,0)
    private val added=NativeBackupMergeCounts(0,1,0,0,0)
    private val preview=NativeBackupMergePreview(
        current,selected,hypothetical,512,empty,added,added,0,0,0,0,0,0,0,
        emptyList()
    )
    private val fingerprint=UnverifiedBackupFingerprint(100,selected)
    private fun ticket()=NativeBackupReviewTicket.open(preview,fingerprint)

    @Test fun exactSecondSelectionIsSingleUseEvenIfRepeated() {
        val gate=NativeBackupReviewGate()
        val token=gate.beginPreview()
        assertTrue(gate.registerPreview(token,ticket()))
        val claimed=checkNotNull(gate.claimReselection())
        assertNull(gate.claimReselection())
        assertTrue(gate.completeReselection(claimed,fingerprint,preview))
        assertFalse(gate.completeReselection(claimed,fingerprint,preview))
        assertNull(gate.claimReselection())
    }

    @Test fun cancellationOrBadReselectionBurnsTicketAndRequiresNewReview() {
        val gate=NativeBackupReviewGate()
        val token=gate.beginPreview()
        assertTrue(gate.registerPreview(token,ticket()))
        val failed=checkNotNull(gate.claimReselection())
        assertFalse(gate.completeReselection(failed,
            UnverifiedBackupFingerprint(99,selected),preview))
        assertNull(gate.claimReselection())
        val next=gate.beginPreview()
        assertTrue(gate.registerPreview(next,ticket()))
        val cancelled=checkNotNull(gate.claimReselection())
        gate.discard()
        assertFalse(gate.completeReselection(cancelled,fingerprint,preview))
        assertNull(gate.claimReselection())
    }

    @Test fun newPreviewInvalidatesLateResultAndEarlierReselection() {
        val gate=NativeBackupReviewGate()
        val first=gate.beginPreview()
        val second=gate.beginPreview()
        assertFalse(gate.registerPreview(first,ticket()))
        assertTrue(gate.registerPreview(second,ticket()))
        val old=checkNotNull(gate.claimReselection())
        val newer=gate.beginPreview()
        assertFalse(gate.completeReselection(old,fingerprint,preview))
        assertFalse(gate.isCurrent(old.epoch))
        assertTrue(gate.registerPreview(newer,ticket()))
        assertTrue(gate.completeReselection(checkNotNull(gate.claimReselection()),
            fingerprint,preview))
    }

    @Test fun storeChangeOrScreenDismissalRevokesReviewAndInFlightRead() {
        val gate=NativeBackupReviewGate()
        val before=gate.beginPreview()
        gate.discard()
        assertFalse(gate.registerPreview(before,ticket()))
        val after=gate.beginPreview()
        assertTrue(gate.registerPreview(after,ticket()))
        val claim=checkNotNull(gate.claimReselection())
        gate.discard()
        assertFalse(gate.completeReselection(claim,fingerprint,preview))
        assertNull(gate.claimReselection())
    }

    @Test fun lateFailureCannotClearAnotherCommittedReview() {
        val gate=NativeBackupReviewGate()
        val oldVersion=gate.beginPreview()
        assertTrue(gate.registerPreview(oldVersion,ticket()))
        val oldAttempt=checkNotNull(gate.claimReselection())
        val newerVersion=gate.beginPreview()
        assertTrue(gate.registerPreview(newerVersion,ticket()))
        gate.abandonReselection(oldAttempt)
        assertTrue(gate.isCurrent(newerVersion))
        assertNotNull(gate.claimReselection())
    }

    @Test fun stalePreviewCannotBeRegisteredAfterUserDiscardsIt() {
        val gate=NativeBackupReviewGate()
        val pending=gate.beginPreview()
        gate.discard()
        assertFalse(gate.registerPreview(pending,ticket()))
        assertNull(gate.claimReselection())
    }

    @Test fun closedOwnerNeverCreatesReviewsOrVerifications() {
        val gate=NativeBackupReviewGate()
        val token=gate.beginPreview()
        assertTrue(gate.registerPreview(token,ticket()))
        val claimed=checkNotNull(gate.claimReselection())
        gate.close()
        assertFalse(gate.isCurrent(token))
        assertNull(gate.claimReselection())
        assertFalse(gate.completeReselection(claimed,fingerprint,preview))
        assertTrue(runCatching { gate.beginPreview() }.isFailure)
        assertFalse(gate.registerPreview(token,ticket()))
    }
}
