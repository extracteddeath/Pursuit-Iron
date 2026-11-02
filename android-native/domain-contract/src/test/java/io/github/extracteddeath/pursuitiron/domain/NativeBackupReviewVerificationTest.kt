package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeBackupReviewVerificationTest {
    private val current="a".repeat(64)
    private val selected="b".repeat(64)
    private val merged="c".repeat(64)
    private val empty=NativeBackupMergeCounts(0,0,0,0,0)
    private val added=NativeBackupMergeCounts(1,1,0,0,0)
    private fun summary(
        currentSha: String=current, incomingSha: String=selected,
        candidateSha: String=merged, flags: List<String> = emptyList(),
        conflicts: Int=0
    )=NativeBackupMergePreview(currentSha,incomingSha,candidateSha,512,
        empty,added,added,conflicts,0,0,0,0,flags)
    private fun picked(hash: String=selected,count: Long=100)=UnverifiedBackupFingerprint(count,hash)

    @Test fun identicalSecondSelectionAndCanonicalResultsMatchOnlyTheSameReview() {
        val first=summary()
        val ticket=NativeBackupReviewTicket.open(first,picked())
        assertTrue(ticket.matchesReselection(picked(),summary()))
        assertTrue(ticket.additiveCandidateOnly)
        assertFalse(ticket.matchesReselection(picked(count=101),summary()))
        assertFalse(ticket.matchesReselection(picked(hash="d".repeat(64)),summary()))
        assertFalse(ticket.matchesReselection(picked(),summary(currentSha="e".repeat(64))))
        assertFalse(ticket.matchesReselection(picked(),summary(candidateSha="f".repeat(64))))
        assertFalse(ticket.matchesReselection(picked(),summary(conflicts=1,
            flags=listOf("RECORD_CONFLICTS_REQUIRE_REVIEW"))))
    }

    @Test fun rejectForgedOrOversizedInitialEvidence() {
        assertTrue(runCatching {
            NativeBackupReviewTicket.open(summary(),picked(hash="d".repeat(64)))
        }.isFailure)
        assertTrue(runCatching {
            NativeBackupReviewTicket.open(summary(),picked(count=0))
        }.isFailure)
        assertTrue(runCatching {
            NativeBackupReviewTicket.open(summary(),picked(count=1048577))
        }.isFailure)
        assertTrue(runCatching {
            NativeBackupReviewTicket.open(summary(incomingSha="bad"),picked(hash="bad"))
        }.isFailure)
    }

    @Test fun riskyMergeCanBeReidentifiedWithoutGainingImportAuthority() {
        val risk=summary(flags=listOf("OWNED_RECORDS_REPLACED"))
        val ticket=NativeBackupReviewTicket.open(risk,picked())
        assertTrue(ticket.matchesReselection(picked(),risk))
        assertFalse(ticket.additiveCandidateOnly)
    }
}
