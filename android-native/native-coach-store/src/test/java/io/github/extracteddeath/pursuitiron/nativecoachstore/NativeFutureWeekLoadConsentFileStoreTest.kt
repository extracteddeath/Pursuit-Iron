package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeFutureWeekLoadConsentFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private var currentIdentity:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{currentIdentity}
    private val coach=CustomCoachAuditInputs()
    private val limit=BigDecimal("5")
    private var now:NativeFutureWeekConsentCandidate?=
        NativeFutureWeekConsentCandidate(
            "cycle-1","block-2","a".repeat(64),31,8,4,2,3,
            BigDecimal("200"),BigDecimal("205"))

    private fun store()=NativeKotlinFutureWeekLoadConsentFileStore(
        root,fence,identity,
        {_,_,_,_,_,_,_,_,_,_,_->now})
    private fun preview()=store().preview(
        "owner-1","cycle-1","block-2",coach,9,1,"bench",
        CoachReadiness.NORMAL,false,limit)
    private fun accept(fingerprint:String="a".repeat(64),revision:Long=0,
        confirmed:Boolean=true)=store().accept(
        "owner-1","cycle-1","block-2",coach,9,1,"bench",
        CoachReadiness.NORMAL,false,limit,
        fingerprint,revision,confirmed)
    private fun read()=store().readCurrent(
        "owner-1","cycle-1","block-2",coach,9,1,"bench")
    private fun accepted()=accept() as NativeFutureWeekConsentWrite.Accepted
    private fun recordFile()=Files.list(root).use {stream->
        stream.filter{it.fileName.toString()
            .startsWith("future-week-consent-")&&
            it.fileName.toString().endsWith(".bin")}
            .findFirst().orElseThrow()
    }

    @Test fun athleteConfirmationPersistsWithoutGrantingIssuance() {
        assertEquals(NativeFutureWeekConsentRead.Missing,read())
        assertEquals(NativeFutureWeekConsentPreview.Ready(
            "a".repeat(64),0,BigDecimal("200"),BigDecimal("205")),preview())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.CONFIRMATION_REQUIRED),
            accept(confirmed=false))
        assertEquals(NativeFutureWeekConsentRead.Missing,read())
        val first=accepted().receipt
        assertEquals(1L,first.recordRevision)
        assertEquals("a".repeat(64),first.reviewFingerprint)
        assertFalse(first.mayApplyAutomatically)
        assertFalse(first.mayIssueWorkout)
        assertFalse(first.mayChangeHistory)
        assertEquals(NativeFutureWeekConsentRead.Current(first),read())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.STALE_REVISION),accept())
    }

    @Test fun changedEvidenceRevocationsAndStaleCallbacksFailClosed() {
        val first=accepted().receipt
        now=now!!.copy(historyRevision=32,fingerprint="b".repeat(64))
        assertEquals(NativeFutureWeekConsentRead.Rejected(
            NativeFutureWeekConsentRefusal.STALE_REVIEW),read())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.REVIEW_CHANGED),
            accept(revision=1))
        now=now!!.copy(historyRevision=31,fingerprint="a".repeat(64))
        assertEquals(NativeFutureWeekConsentRead.Current(first),read())
        assertEquals(NativeFutureWeekConsentWrite.Revoked(2),
            store().revoke("owner-1","cycle-1","block-2",9,1,"bench",1))
        assertEquals(NativeFutureWeekConsentRead.Revoked(2),read())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.STALE_REVISION),accept(revision=1))
        assertEquals(3L,(accept(revision=2) as
            NativeFutureWeekConsentWrite.Accepted).receipt.recordRevision)
    }

    @Test fun coachingUnavailableAccountChangedAndInvalidSelectionCannotApprove() {
        now=null
        assertEquals(NativeFutureWeekConsentPreview.Rejected(
            NativeFutureWeekConsentRefusal.REVIEW_UNAVAILABLE),preview())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.STALE_REVIEW),accept())
        now=NativeFutureWeekConsentCandidate(
            "cycle-1","block-2","a".repeat(64),31,8,4,2,3,
            BigDecimal("200"),BigDecimal("205"))
        currentIdentity=NativeKotlinOwnerIdentity("owner-1","athlete-2")
        assertEquals("athlete-2",accepted().receipt.athleteId)
        currentIdentity=null
        assertEquals(NativeFutureWeekConsentRead.Rejected(
            NativeFutureWeekConsentRefusal.NOT_AUTHORIZED),read())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.NOT_AUTHORIZED),
            accept(revision=1))
        assertEquals(NativeFutureWeekConsentPreview.Rejected(
            NativeFutureWeekConsentRefusal.INVALID_SELECTION),
            store().preview("owner-1","cycle-1","block-2",
                coach,0,1,"bench",CoachReadiness.NORMAL,false,limit))
    }

    @Test fun changedAthleteCannotInspectOrReplaceEarlierOwnersConsent() {
        val first=accepted().receipt
        currentIdentity=NativeKotlinOwnerIdentity("owner-1","athlete-2")
        assertEquals(NativeFutureWeekConsentPreview.Rejected(
            NativeFutureWeekConsentRefusal.NOT_AUTHORIZED),preview())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.NOT_AUTHORIZED),
            accept(revision=first.recordRevision))
        assertEquals(NativeFutureWeekConsentRead.Rejected(
            NativeFutureWeekConsentRefusal.NOT_AUTHORIZED),read())
        currentIdentity=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeFutureWeekConsentRead.Current(first),read())
    }

    @Test fun corruptedDiskAndInterruptedAtomicRenameCannotRestoreConsent() {
        accepted()
        val file=recordFile()
        val bytes=Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 0x1).toByte()
        Files.write(file,bytes)
        assertEquals(NativeFutureWeekConsentRead.Rejected(
            NativeFutureWeekConsentRefusal.CORRUPT_RECORD),read())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.CORRUPT_RECORD),accept(revision=1))
        // A partial queued write never looks like a clean existing receipt.
        val pending=file.resolveSibling(
            file.fileName.toString().removeSuffix(".bin")+".pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeFutureWeekConsentRead.Rejected(
            NativeFutureWeekConsentRefusal.CORRUPT_RECORD),read())
    }

    @Test fun proposalCannotExceedAuthorizedPhysicalIncrease() {
        now=now!!.copy(proposedLoad=BigDecimal("215"))
        assertEquals(NativeFutureWeekConsentPreview.Rejected(
            NativeFutureWeekConsentRefusal.REVIEW_UNAVAILABLE),preview())
        assertEquals(NativeFutureWeekConsentWrite.Rejected(
            NativeFutureWeekConsentRefusal.STALE_REVIEW),accept())
    }
}
