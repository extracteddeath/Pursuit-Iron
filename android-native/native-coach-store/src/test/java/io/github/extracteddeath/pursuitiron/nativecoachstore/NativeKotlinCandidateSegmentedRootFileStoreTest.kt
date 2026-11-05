package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests candidate copy-on-write root publication ONLY. No workout journal or
 * athlete history becomes production-authoritative through these operations.
 */
class NativeKotlinCandidateSegmentedRootFileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val root: Path get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity(owner, athlete)
    private val identity = NativeKotlinCurrentIdentity { signedIn }
    private val fence by lazy { NativeKotlinApprovalRevisionFence(root) }
    private val v2 by lazy { NativeKotlinHistoryManifestFileStore(root) }
    private val composer by lazy { KotlinNativeJournalFileStore(root) }
    private val correction by lazy {
        KotlinNativeSavedCorrectionFileStore(root, composer)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(root, fence, identity)
    }
    private val authoring by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root, fence, identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root, fence, identity, drafts, authoring)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence, identity, drafts, authoring, signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root, fence, identity, preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root, fence, identity, prepared, preflight, v2, composer)
    }
    private val authority = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null
        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = ownerKey == owner && source.athleteId == athlete
    }
    private val replay by lazy {
        NativeKotlinMixedHistoryReadCoordinator(
            v2, composer, correction, custom, authority, identity, fence)
    }
    private val shadow by lazy {
        NativeKotlinSegmentedHistoryShadowStore(root, v2, fence, identity)
    }
    private val handoff by lazy {
        NativeKotlinSegmentedHistoryHandoffGate(
            v2, shadow, replay, fence, identity)
    }
    private fun wal() = NativeKotlinSegmentedRedoFileStore(root, fence, identity)
    private fun store() = NativeKotlinCandidateSegmentedRootFileStore(
        root, fence, identity, v2, handoff, wal())
    private fun receiptVerifier() = NativeKotlinIndependentHistoryReceiptVerifier(
        composer, correction, custom,
        NativeKotlinCustomSavedCorrectionFileStore(
            root, fence, identity, custom, v2),
        authority, fence, identity)
    private fun identityIndex() =
        NativeKotlinCandidateWorkoutIdentityIndexFileStore(
            root, fence, identity, v2, store())
    private fun pagedIndex() = NativeKotlinCandidatePagedIdentityFileStore(
        root, fence, identity, v2, store())
    private fun pairedCheckpoint(
        before: (() -> Unit)? = null,
        after: (() -> Unit)? = null
    ) = NativeKotlinCandidatePairedHistoryCheckpointFileStore(
        root, fence, identity, store(), pagedIndex(), before, after)
    private fun reconciler(
        afterIndex: (() -> Unit)? = null,
        afterCheckpoint: (() -> Unit)? = null
    ) = NativeKotlinCandidateHistoryMirrorRecoveryCoordinator(
        fence, identity, store(), pagedIndex(), pairedCheckpoint(),
        afterIndex, afterCheckpoint)
    private fun result(r: NativeCandidateHistoryRootResult) =
        (r as NativeCandidateHistoryRootResult.Reviewed).view
    private fun rejection(reason: NativeCandidateHistoryRootRefusal) =
        NativeCandidateHistoryRootResult.Rejected(reason)
    private fun entry(i: Long) =
        KotlinHistoryManifestEntry(i, "workout-$i",
            "stored-$i", "finish-$i", 2, 0)
    private fun finish(i: Long) = NativeHistoryWriteIntent.Finish(
        "workout-$i", KotlinJournalOperation.Finish("finish-$i", 1))
    private fun enrollEmpty() {
        assertTrue(v2.enroll(owner, athlete) { o, a ->
            signedIn == NativeKotlinOwnerIdentity(o, a)
        } is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 0, false),
            shadow.sync(owner, athlete))
    }
    private fun boot(): NativeCandidateHistoryRootView {
        enrollEmpty()
        val built = store().bootstrapFromVerifiedV2(owner, athlete)
        assertTrue(built.toString(), built is NativeCandidateHistoryRootResult.Reviewed)
        return result(built)
    }
    private fun stagedAppend(view: NativeCandidateHistoryRootView,
        number: Long = 1
    ) {
        val intent = finish(number)
        assertTrue(wal().prepare(owner, athlete, view.head, intent, null)
            is NativeSegmentedRedoResult.Durable)
        val segment = NativeSegmentedWriteSegment(
            view.head.rootDigest, number, emptyList())
        assertTrue(wal().stage(owner, athlete, segment, entry(number))
            is NativeSegmentedRedoResult.Durable)
    }
    private fun candidateFiles(ext: String): List<Path> = Files.list(root).use {
        it.filter { f -> f.fileName.toString().endsWith(ext) }.toList()
    }

    @Test fun rootAcknowledgmentDigestBindsEveryCorrectionFieldNotJustEventId() {
        val candidate=store()
        val baseIntent=NativeHistoryWriteIntent.Correct("workout-1",
            KotlinSavedSetCorrection("edit-1", 0, "set-1",
                "event-1", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(java.math.BigDecimal("200"), LoadUnit.LB, 6, 2)))
        fun patch(intent: NativeHistoryWriteIntent) = NativeSegmentedWritePatch(
            "a".repeat(64), 1, 2, 1, 1,
            listOf(entry(1).copy(savedCorrectionRevision = 1)), intent)
        val basePatch=patch(baseIntent)
        val hash=candidate.patchDigest(basePatch)
        assertEquals(64,hash.length)
        assertEquals(hash,candidate.patchDigest(basePatch))
        fun changed(correction: KotlinSavedSetCorrection) =
            candidate.patchDigest(patch(baseIntent.copy(operation=correction)))
        val original=baseIntent.operation
        assertNotEquals(hash,changed(original.copy(
            actual=original.actual!!.copy(reps=5))))
        assertNotEquals(hash,changed(original.copy(
            actual=original.actual!!.copy(load=java.math.BigDecimal("205")))))
        assertNotEquals(hash,changed(original.copy(
            actual=original.actual!!.copy(actualRir=1))))
        assertNotEquals(hash,changed(original.copy(setKey="other-set")))
        assertNotEquals(hash,changed(original.copy(correctsEventId="other-event")))
        assertNotEquals(hash,changed(original.copy(
            outcome=KotlinJournalSetOutcome.SKIPPED,actual=null)))
        assertNotEquals(hash,changed(original.copy(expectedCorrectionRevision=1)))
        assertNotEquals(hash,changed(original.copy(eventId="edit-2")))
        assertNotEquals(hash,candidate.patchDigest(basePatch.copy(
            consumedIntent=NativeHistoryWriteIntent.Finish("workout-1",
                KotlinJournalOperation.Finish("edit-1",1)))))
    }

    @Test fun rootAcknowledgmentDigestBindsCustomAmplifierVisitAndFinishRevision() {
        val candidate=store()
        val visit=CustomWorkoutVisitKey("set-1",CustomWorkoutVisitPart.MAIN)
        val original=CustomSavedVisitCorrection("edit-1",0,visit,"event-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(java.math.BigDecimal("25"),LoadUnit.LB,8,2))
        fun hash(intent: NativeHistoryWriteIntent) =
            candidate.patchDigest(NativeSegmentedWritePatch(
                "a".repeat(64),1,2,1,1,
                listOf(entry(1).copy(savedCorrectionRevision=1)),intent))
        val base=hash(NativeHistoryWriteIntent.CustomCorrect("workout-1",original))
        assertNotEquals(base,hash(NativeHistoryWriteIntent.CustomCorrect(
            "workout-1",original.copy(visit=CustomWorkoutVisitKey(
                "set-1",CustomWorkoutVisitPart.FOLLOW_UP)))))
        assertNotEquals(base,hash(NativeHistoryWriteIntent.CustomCorrect(
            "workout-1",original.copy(performed=original.performed!!.copy(reps=7)))))
        assertNotEquals(base,hash(NativeHistoryWriteIntent.CustomCorrect(
            "workout-1",original.copy(expectedCorrectionRevision=1))))
        assertNotEquals(base,hash(NativeHistoryWriteIntent.Correct("workout-1",
            KotlinSavedSetCorrection("edit-1",0,"set-1","event-1",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(java.math.BigDecimal("25"),LoadUnit.LB,8,2)))))
        val finished=hash(NativeHistoryWriteIntent.Finish("workout-1",
            KotlinJournalOperation.Finish("finish-1",1)))
        assertNotEquals(finished,hash(NativeHistoryWriteIntent.Finish("workout-1",
            KotlinJournalOperation.Finish("finish-1",2))))
        val customFinished=hash(NativeHistoryWriteIntent.CustomFinish("workout-1",
            CustomWorkoutJournalOperation.Finish("finish-1",1)))
        assertNotEquals(customFinished,hash(NativeHistoryWriteIntent.CustomFinish(
            "workout-1",CustomWorkoutJournalOperation.Finish("finish-1",2))))
        assertNotEquals(finished,customFinished)
    }

    @Test fun noUnenrolledOrPartialHistoryMayCreateCandidateRoot() {
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED),
            store().audit(owner, athlete))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.BOOTSTRAP_DENIED),
            store().bootstrapFromVerifiedV2(owner, athlete))
        assertTrue(candidateFiles(".root").isEmpty())
        // Onboarding or an absent UI history list cannot bypass v2 enrollment.
    }

    @Test fun candidateIdentityIndexIsDurableButNeverAuthoritative() {
        val initial = boot()
        val missing = identityIndex().findWorkout(owner, athlete, "workout-1")
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.NOT_INDEXED), missing)
        val written = identityIndex().build(owner, athlete)
            as NativeCandidateIdentityIndexResult.Indexed
        assertEquals(initial.head.rootDigest, written.view.rootDigest)
        assertFalse(written.view.canAuthorizeWrites)
        assertFalse(written.view.canIssueProgression)
        assertFalse(written.view.canTransferAuthority)
        assertFalse(written.replayed)
        assertTrue((identityIndex().build(owner, athlete)
            as NativeCandidateIdentityIndexResult.Indexed).replayed)
        val absent = identityIndex().findWorkout(owner, athlete, "unknown")
            as NativeCandidateIdentityIndexResult.Lookup
        assertNull(absent.match)
        assertFalse(absent.canAuthorizeNewWorkout)
        val files = candidateFiles(".idx").filter {
            it.fileName.toString().startsWith("candidate-identity-")
        }
        assertEquals(1, files.size)
        val bytes = Files.readAllBytes(files.single())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(files.single(), bytes)
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.CORRUPT_INDEX),
            identityIndex().findWorkout(owner, athlete, "unknown"))
        // A damaged identity index must not be silently rebuilt or reset.
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.CORRUPT_INDEX),
            identityIndex().build(owner, athlete))
        signedIn = null
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.NOT_AUTHORIZED),
            identityIndex().build(owner, athlete))
    }

    @Test fun candidateIndexRejectsForgedHistoryAndRefreshesOnlyAfterRealFinish() {
        val initial = boot()
        assertTrue(identityIndex().build(owner, athlete)
            is NativeCandidateIdentityIndexResult.Indexed)
        stagedAppend(initial)
        assertTrue(store().publishStagedForCandidateReview(owner, athlete)
            is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE),
            identityIndex().build(owner, athlete))
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE),
            identityIndex().findWorkout(owner, athlete, "workout-1"))
        // An independently proven Finish uses a fresh candidate root.
        // The synthetic candidate cannot be repaired by an index write.
    }

    @Test fun mirrorReconciliationCreatesOneVerifiedPairAndOnlyAcknowledgesExactRetry() {
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_ROOT),
            reconciler().reconcile(owner, athlete))
        val original=boot()
        val saved=reconciler().reconcile(owner, athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent
        assertFalse(saved.replayed)
        assertEquals(original.head.rootDigest,saved.checkpoint.rootDigest)
        assertEquals(0L,saved.checkpoint.completionCount)
        assertFalse(saved.canAuthorizeWrites)
        assertFalse(saved.canIssueProgression)
        assertFalse(saved.canTransferAuthority)
        assertEquals(1,candidateFiles(".pidx").size)
        assertEquals(1,candidateFiles(".cseal").size)
        val retry=reconciler().reconcile(owner, athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent
        assertTrue(retry.replayed)
        assertEquals(saved.checkpoint,retry.checkpoint)
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.INVALID_SCOPE),
            reconciler().reconcile("invalid/owner",athlete))
        signedIn=null
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.NOT_AUTHORIZED),
            reconciler().reconcile(owner,athlete))
    }

    @Test fun mirrorReconciliationRecoversCrashBetweenIndexAndCheckpoint() {
        boot()
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.STORAGE_UNAVAILABLE),
            reconciler(afterIndex={
                throw IllegalStateException("process dies after index")
            }).reconcile(owner,athlete))
        assertEquals(1,candidateFiles(".pidx").size)
        assertTrue(candidateFiles(".cseal").isEmpty())
        val recovered=reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent
        assertFalse(recovered.replayed)
        assertEquals(1L,recovered.checkpoint.sourceRevision)
        assertEquals(1,candidateFiles(".cseal").size)
        assertTrue((reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent).replayed)
    }

    @Test fun mirrorReconciliationRecoversLostAckButCannotReplaceCommittedIndex() {
        boot()
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.STORAGE_UNAVAILABLE),
            reconciler(afterCheckpoint={
                throw IllegalStateException("acknowledgement lost")
            }).reconcile(owner,athlete))
        assertEquals(1,candidateFiles(".cseal").size)
        assertTrue((reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent).replayed)
        Files.delete(candidateFiles(".pidx").single())
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.LOST_COMMITTED_INDEX),
            reconciler().reconcile(owner,athlete))
        assertTrue(candidateFiles(".pidx").isEmpty())
        assertEquals(1,candidateFiles(".cseal").size)
    }

    @Test fun mirrorReconciliationUpdatesRootIndexAndCheckpointAfterRealFinish() {
        val initial=boot()
        val before=reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent
        val saved=realComposerCompletion()
        val intent=finish(1)
        assertTrue(wal().prepare(owner,athlete,initial.head,intent,null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner,athlete,
            NativeSegmentedWriteSegment(initial.head.rootDigest,1,emptyList()),
            saved) is NativeSegmentedRedoResult.Durable)
        assertTrue(v2.prepare(owner,athlete,1,intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2.finalize(owner,athlete,intent,saved)
            is NativeHistoryLedgerResult.Durable)
        // The candidate root cannot be inferred from a v2 commit alone.
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_ROOT),
            reconciler().reconcile(owner,athlete))
        assertTrue(store().publishReplayVerifiedV2Mirror(
            owner,athlete,replay,receiptVerifier())
            is NativeCandidateHistoryRootResult.Reviewed)
        assertTrue(shadow.sync(owner,athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val ready=reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent
        assertFalse(ready.replayed)
        assertEquals(2L,ready.checkpoint.sourceRevision)
        assertEquals(1L,ready.checkpoint.completionCount)
        assertNotEquals(before.checkpoint.rootDigest,ready.checkpoint.rootDigest)
        assertNotEquals(before.checkpoint.pagedDirectoryFileDigest,
            ready.checkpoint.pagedDirectoryFileDigest)
        assertEquals(1L,(pagedIndex().findWorkout(owner,athlete,"workout-1")
            as NativeCandidatePagedIdentityResult.Lookup).row?.completionOrder)
        assertTrue((reconciler().reconcile(owner,athlete)
            as NativeCandidateMirrorRecoveryResult.Consistent).replayed)
        assertEquals(1L,(v2.open(owner,athlete)
            as NativeHistoryLedgerResult.Durable).ledger.completions.size.toLong())
    }

    @Test fun mirrorReconciliationRefusesCorruptCheckpointAndIndexWithoutRepair() {
        boot()
        reconciler().reconcile(owner,athlete)
        val page=candidateFiles(".pidx").single()
        val bytes=Files.readAllBytes(page)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(page,bytes)
        assertEquals(NativeCandidateMirrorRecoveryResult.Rejected(
            NativeCandidateMirrorRecoveryRefusal.CORRUPT_INDEX),
            reconciler().reconcile(owner,athlete))
        // Failed reconciliation did not clear a previously committed seal.
        assertEquals(1,candidateFiles(".cseal").size)
        assertEquals(1,candidateFiles(".pidx").size)
    }

    @Test fun pairedCheckpointFailsClosedUntilBothCandidateArtifactsVerified() {
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.NOT_CHECKPOINTED),
            pairedCheckpoint().review(owner, athlete))
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE),
            pairedCheckpoint().checkpoint(owner, athlete))
        val rootView = boot()
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE),
            pairedCheckpoint().checkpoint(owner, athlete))
        assertTrue(pagedIndex().build(owner, athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val sealed = pairedCheckpoint().checkpoint(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified
        assertFalse(sealed.replayed)
        assertEquals(rootView.head.rootDigest, sealed.checkpoint.rootDigest)
        assertEquals(0L, sealed.checkpoint.completionCount)
        assertEquals(1L, sealed.checkpoint.sourceRevision)
        assertEquals(64, sealed.checkpoint.pagedDirectoryFileDigest.length)
        assertFalse(sealed.checkpoint.canAuthorizeHistoryReads)
        assertFalse(sealed.checkpoint.canAuthorizeWrites)
        assertFalse(sealed.checkpoint.canIssueProgression)
        assertFalse(sealed.checkpoint.canTransferAuthority)
        assertTrue((pairedCheckpoint().review(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified).replayed)
        assertTrue((pairedCheckpoint().checkpoint(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified).replayed)
        assertEquals(1, candidateFiles(".cseal").size)
        signedIn = null
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.NOT_AUTHORIZED),
            pairedCheckpoint().review(owner, athlete))
        signedIn = NativeKotlinOwnerIdentity(owner, athlete)
        val bytes = Files.readAllBytes(candidateFiles(".cseal").single())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(candidateFiles(".cseal").single(), bytes)
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.CORRUPT_CHECKPOINT),
            pairedCheckpoint().review(owner, athlete))
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.CORRUPT_CHECKPOINT),
            pairedCheckpoint().checkpoint(owner, athlete))
    }

    @Test fun pairedCheckpointCrashBeforePublicationAndLostAckDoNotInventHistory() {
        boot()
        assertTrue(pagedIndex().build(owner, athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val before = pairedCheckpoint(before = {
            throw IllegalStateException("crash before seal")
        })
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.STORAGE_UNAVAILABLE),
            before.checkpoint(owner, athlete))
        assertTrue(candidateFiles(".cseal").isEmpty())
        val after = pairedCheckpoint(after = {
            throw IllegalStateException("lost seal acknowledgement")
        })
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.STORAGE_UNAVAILABLE),
            after.checkpoint(owner, athlete))
        assertEquals(1, candidateFiles(".cseal").size)
        val recovered = pairedCheckpoint().checkpoint(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified
        assertTrue(recovered.replayed)
        assertEquals(recovered.checkpoint,
            (pairedCheckpoint().review(owner, athlete)
                as NativeCandidatePairedHistoryResult.Verified).checkpoint)
        assertEquals(0L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable).ledger.completions.size.toLong())
    }

    @Test fun pairedCheckpointRefusesTornRootIndexAndCanSealExactNewCommit() {
        val first = boot()
        assertTrue(pagedIndex().build(owner, athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val old = (pairedCheckpoint().checkpoint(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified).checkpoint
        val saved = realComposerCompletion()
        val intent = finish(1)
        assertTrue(wal().prepare(owner, athlete, first.head, intent, null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(first.head.rootDigest, 1, emptyList()),
            saved) is NativeSegmentedRedoResult.Durable)
        assertTrue(v2.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2.finalize(owner, athlete, intent, saved)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE),
            pairedCheckpoint().review(owner, athlete))
        assertTrue(store().publishReplayVerifiedV2Mirror(
            owner, athlete, replay, receiptVerifier())
            is NativeCandidateHistoryRootResult.Reviewed)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE),
            pairedCheckpoint().checkpoint(owner, athlete))
        assertTrue(pagedIndex().build(owner, athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        assertEquals(NativeCandidatePairedHistoryResult.Rejected(
            NativeCandidatePairedHistoryRefusal.STALE_CHECKPOINT),
            pairedCheckpoint().review(owner, athlete))
        val next = pairedCheckpoint().checkpoint(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified
        assertFalse(next.replayed)
        assertEquals(2L, next.checkpoint.sourceRevision)
        assertEquals(1L, next.checkpoint.completionCount)
        assertNotEquals(old.rootDigest, next.checkpoint.rootDigest)
        assertNotEquals(old.pagedDirectoryFileDigest,
            next.checkpoint.pagedDirectoryFileDigest)
        assertTrue((pairedCheckpoint().review(owner, athlete)
            as NativeCandidatePairedHistoryResult.Verified).replayed)
    }

    @Test fun pagedIndexColdReopenAndCorruptionNeverAuthorizeNewFinish() {
        val candidate=boot()
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.NOT_INDEXED),
            pagedIndex().findWorkout(owner,athlete,"workout-1"))
        val built=pagedIndex().build(owner,athlete)
            as NativeCandidatePagedIdentityResult.Indexed
        assertEquals(candidate.head.rootDigest,built.view.rootDigest)
        assertEquals(0L,built.view.completionCount)
        assertFalse(built.view.canAuthorizeNewWorkout)
        assertFalse(built.view.canPublishHistory)
        assertFalse(built.view.canIssueProgression)
        assertFalse(built.view.canTransferAuthority)
        assertTrue((pagedIndex().build(owner,athlete)
            as NativeCandidatePagedIdentityResult.Indexed).replayed)
        assertTrue(pagedIndex().audit(owner,athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val absent=pagedIndex().findWorkout(owner,athlete,"workout-1")
            as NativeCandidatePagedIdentityResult.Lookup
        assertNull(absent.row)
        assertFalse(absent.canAuthorizeNewWorkout)
        assertFalse(absent.canAuthorizeCorrection)
        assertEquals(1,candidateFiles(".pidx").size)
        val indexFile=candidateFiles(".pidx").single()
        val bytes=Files.readAllBytes(indexFile)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(indexFile,bytes)
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX),
            pagedIndex().findReceipt(owner,athlete,"receipt-1"))
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX),
            pagedIndex().build(owner,athlete))
        signedIn=null
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.NOT_AUTHORIZED),
            pagedIndex().audit(owner,athlete))
    }

    @Test fun pagedIndexCannotMirrorSyntheticCandidateFinish() {
        val initial=boot()
        assertTrue(pagedIndex().build(owner,athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        stagedAppend(initial)
        assertTrue(store().publishStagedForCandidateReview(owner,athlete)
            is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE),
            pagedIndex().build(owner,athlete))
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE),
            pagedIndex().findWorkout(owner,athlete,"workout-1"))
    }

    @Test fun zeroHistoryBootstrapRequiresReplayGateAndDoesNotReset() {
        val initial = boot()
        assertEquals(1L, initial.head.revision)
        assertEquals(0L, initial.head.completionCount)
        assertEquals(0, initial.verifiedSegmentCount)
        assertFalse(initial.canPublishHistory)
        assertFalse(initial.canIssueProgression)
        assertFalse(initial.canTransferAuthority)
        assertEquals(initial, result(store().audit(owner, athlete)))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT),
            store().bootstrapFromVerifiedV2(owner, athlete))
        assertEquals(1, candidateFiles(".root").size)
    }

    @Test fun atomicCopyOnWriteCommitIsReopenableAndLostAckIsIdempotent() {
        val initial = boot()
        stagedAppend(initial)
        val before = result(store().audit(owner, athlete))
        assertEquals(0L, before.head.completionCount)
        val committed = store().publishStagedForCandidateReview(owner, athlete)
        assertTrue(committed.toString(),
            committed is NativeCandidateHistoryRootResult.Reviewed)
        val current = result(committed)
        assertEquals(2L, current.head.revision)
        assertEquals(1L, current.head.completionCount)
        assertEquals(1, current.verifiedSegmentCount)
        assertEquals(initial.head.rootDigest, current.lastParentDigest)
        assertNotEquals(initial.head.rootDigest, current.head.rootDigest)
        assertEquals(1, candidateFiles(".shard").size)
        assertEquals(current, result(store().audit(owner, athlete)))
        val restarted = store().publishStagedForCandidateReview(owner, athlete)
        assertTrue((restarted as NativeCandidateHistoryRootResult.Reviewed).replayed)
        assertEquals(current, result(restarted))
        assertEquals(1, candidateFiles(".shard").size)
        assertEquals(1L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        assertTrue((wal().read(owner, athlete)
            as NativeSegmentedRedoResult.Durable).redo.stagedPatch != null)
    }

    @Test fun parityPreflightBlocksPendingRedoAndSyntheticCandidateOnlyHistory() {
        val initial = boot()
        val empty = store().reviewAuthenticatedV2Parity(owner, athlete)
        assertTrue(empty.toString(),
            empty is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(initial, result(empty))
        assertFalse(result(empty).canTransferAuthority)
        assertFalse(result(empty).canPublishHistory)
        assertFalse(result(empty).canIssueProgression)

        stagedAppend(initial)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED),
            store().reviewAuthenticatedV2Parity(owner, athlete))
        assertEquals(initial, result(store().audit(owner, athlete)))

        // The test-only candidate publisher cannot authorize fabricated
        // history, even when the root and immutable shards are well formed.
        assertTrue(store().publishStagedForCandidateReview(owner, athlete)
            is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE),
            store().reviewAuthenticatedV2Parity(owner, athlete))
        assertEquals(1L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable).ledger.revision)
    }

    @Test fun parityPreflightRequiresCurrentOwnerAndUnmodifiedRoot() {
        val initial = boot()
        signedIn = null
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.NOT_AUTHORIZED),
            store().reviewAuthenticatedV2Parity(owner, athlete))
        signedIn = NativeKotlinOwnerIdentity(owner, athlete)
        assertEquals(initial,
            result(store().reviewAuthenticatedV2Parity(owner, athlete)))
        val rootFile = candidateFiles(".root").single()
        val bytes = Files.readAllBytes(rootFile)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(rootFile, bytes)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT),
            store().reviewAuthenticatedV2Parity(owner, athlete))
    }

    @Test fun preparedButUnstagedRedoNeverPublishesRoot() {
        val initial = boot()
        assertTrue(wal().prepare(owner, athlete, initial.head,
            finish(1), null) is NativeSegmentedRedoResult.Durable)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED),
            store().publishStagedForCandidateReview(owner, athlete))
        assertEquals(initial, result(store().audit(owner, athlete)))
        assertTrue(candidateFiles(".shard").isEmpty())
    }

    @Test fun staleCandidateBaseCannotPublishAndCannotEraseCurrentRoot() {
        val initial = boot()
        val forged = initial.head.copy(rootDigest = "f".repeat(64))
        val intent = finish(1)
        assertTrue(wal().prepare(owner, athlete, forged, intent, null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(forged.rootDigest, 1, emptyList()), entry(1))
            is NativeSegmentedRedoResult.Durable)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT),
            store().publishStagedForCandidateReview(owner, athlete))
        assertEquals(initial, result(store().audit(owner, athlete)))
    }

    @Test fun corruptedRootOrImmutableShardFailsClosed() {
        val initial = boot()
        stagedAppend(initial)
        assertTrue(store().publishStagedForCandidateReview(owner, athlete)
            is NativeCandidateHistoryRootResult.Reviewed)
        val shard = candidateFiles(".shard").single()
        val bytes = Files.readAllBytes(shard)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(shard, bytes)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.CORRUPT_SEGMENT),
            store().audit(owner, athlete))
        val rootFile = candidateFiles(".root").single()
        val rootBytes = Files.readAllBytes(rootFile)
        rootBytes[rootBytes.lastIndex] =
            (rootBytes.last().toInt() xor 1).toByte()
        Files.write(rootFile, rootBytes)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT),
            store().audit(owner, athlete))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT),
            store().bootstrapFromVerifiedV2(owner, athlete))
    }

    @Test fun ownerRevocationMakesAllCandidatePublicationInaccessible() {
        val initial = boot()
        stagedAppend(initial)
        signedIn = null
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.NOT_AUTHORIZED),
            store().publishStagedForCandidateReview(owner, athlete))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.NOT_AUTHORIZED),
            store().audit(owner, athlete))
        signedIn = NativeKotlinOwnerIdentity(owner, athlete)
        assertEquals(initial, result(store().audit(owner, athlete)))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.INVALID_SCOPE),
            store().audit("../owner", athlete))
    }
    @Test fun interruptionAfterShardBeforeRootLeavesRecoverableOrphan() {
        val initial = boot()
        stagedAppend(initial)
        var injected = false
        val crashed = NativeKotlinCandidateSegmentedRootFileStore(
            root, fence, identity, v2, handoff, wal(),
            beforeRootPublicationForTest = {
                injected = true
                throw IllegalStateException("simulated power interruption")
            })
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.STORAGE_UNAVAILABLE),
            crashed.publishStagedForCandidateReview(owner, athlete))
        assertTrue(injected)
        assertEquals(initial, result(store().audit(owner, athlete)))
        assertEquals(1, candidateFiles(".shard").size)
        assertTrue((wal().read(owner, athlete)
            as NativeSegmentedRedoResult.Durable).redo.stagedPatch != null)
        // Reuse existing immutable bytes, then swap root only once.
        val restored = store().publishStagedForCandidateReview(owner, athlete)
        assertTrue(restored.toString(),
            restored is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(1L, result(restored).head.completionCount)
        assertEquals(1, candidateFiles(".shard").size)
        assertTrue((store().publishStagedForCandidateReview(owner, athlete)
            as NativeCandidateHistoryRootResult.Reviewed).replayed)
    }

    @Test fun differentSameEventCorrectionWalCannotReplayAcknowledgmentOfCommittedCorrection() {
        // Explicit engineering fault injection: replace WAL bytes with a
        // different, independently checksummed staged record from the same
        // candidate root. The older event-ID-only patch fingerprint would
        // falsely acknowledge both corrections as the same committed edit.
        val initial=boot()
        stagedAppend(initial)
        val committed=result(store().publishStagedForCandidateReview(owner,athlete))
        assertEquals(1L,committed.head.completionCount)
        val original=entry(1)
        val walFile=candidateFiles(".wal").single {
            it.fileName.toString().startsWith("candidate-segmented-redo-")
        }
        Files.delete(walFile) // test-only: simulated WAL namespace reset
        fun stageVariant(reps:Int):ByteArray {
            val correction=NativeHistoryWriteIntent.Correct("workout-1",
                KotlinSavedSetCorrection("shared-edit",0,"set-1","set-event-1",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(java.math.BigDecimal("200"),LoadUnit.LB,reps,2)))
            assertTrue(wal().prepare(owner,athlete,committed.head,correction,original)
                is NativeSegmentedRedoResult.Durable)
            assertTrue(wal().stage(owner,athlete,
                NativeSegmentedWriteSegment(committed.head.rootDigest,1,
                    listOf(original)),original.copy(savedCorrectionRevision=1))
                is NativeSegmentedRedoResult.Durable)
            return Files.readAllBytes(walFile)
        }
        val sixReps=stageVariant(6)
        Files.delete(walFile)
        val fiveReps=stageVariant(5)
        assertFalse(sixReps.contentEquals(fiveReps))
        Files.write(walFile,sixReps)
        val accepted=result(store().publishStagedForCandidateReview(owner,athlete))
        assertEquals(3L,accepted.head.revision)
        // A stale/conflicting WAL is not proof of a successful replay.
        Files.write(walFile,fiveReps)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT),
            store().publishStagedForCandidateReview(owner,athlete))
        assertEquals(accepted,result(store().audit(owner,athlete)))
        // The genuine WAL remains idempotently retryable after cold reopen.
        Files.write(walFile,sixReps)
        val genuine=store().publishStagedForCandidateReview(owner,athlete)
        assertTrue(genuine is NativeCandidateHistoryRootResult.Reviewed)
        assertTrue((genuine as NativeCandidateHistoryRootResult.Reviewed).replayed)
        assertEquals(accepted,result(genuine))
    }

    @Test fun lostAcknowledgementAfterRootSwapIsRecognizedOnRestart() {
        val initial = boot()
        stagedAppend(initial)
        val crashed = NativeKotlinCandidateSegmentedRootFileStore(
            root, fence, identity, v2, handoff, wal(),
            afterRootPublicationForTest = {
                throw IllegalStateException("simulated ack loss")
            })
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.STORAGE_UNAVAILABLE),
            crashed.publishStagedForCandidateReview(owner, athlete))
        val current = result(store().audit(owner, athlete))
        assertEquals(2L, current.head.revision)
        assertEquals(1L, current.head.completionCount)
        assertTrue((store().publishStagedForCandidateReview(owner, athlete)
            as NativeCandidateHistoryRootResult.Reviewed).replayed)
        assertEquals(current, result(store().audit(owner, athlete)))
    }

    private fun realComposerCompletion(): KotlinHistoryManifestEntry {
        val source = KotlinTrainingSourceRevision(
            athlete, 2, "program-1", 3, "gym-1", 3,
            "catalog-1", "rules-1")
        val target = KotlinWorkoutTarget(
            "bench", ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY, "set-1",
            DraftSetType.WORKING, 0,
            DraftSetLoad.Verified(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                java.math.BigDecimal("200")),
            RepBand(5, 8), 2, 60, 90)
        val snapshot = KotlinStagedWorkout(
            source, "grant-1", "workout-1", 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, listOf(target))
        val stored = KotlinStoredWorkoutIdentity(
            "stored-1", "workout-1", source, 1)
        assertTrue(composer.issue(owner, snapshot, stored)
            is NativeJournalFileResult.Durable)
        val record = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.RecordSet(
                "set-event-1", 0, "set-1",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(java.math.BigDecimal("200"),
                    LoadUnit.LB, 8, 2)
            ), { o, sourceRevision ->
                authority.canAccessIssuedWorkout(o, sourceRevision)
            })
        assertTrue(record is NativeJournalFileResult.Durable)
        val finish = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.Finish(
                "finish-1", (record as NativeJournalFileResult.Durable).journal.revision
            ), { o, sourceRevision ->
                authority.canAccessIssuedWorkout(o, sourceRevision)
            })
        assertTrue(finish is NativeJournalFileResult.Durable)
        val journal = (finish as NativeJournalFileResult.Durable).journal
        return KotlinHistoryManifestEntry(
            1, "workout-1", journal.storedIdentity.storageReceiptId,
            checkNotNull(journal.finishedByEventId), journal.revision, 0)
    }

    @Test fun historicalCorrectionCopiesOneShardButKeepsV2AsSourceOwner() {
        val saved = realComposerCompletion()
        assertTrue(v2.enrollVerifiedSnapshot(owner, athlete,
            listOf(saved)) { o, a ->
                signedIn == NativeKotlinOwnerIdentity(o, a)
            } is NativeHistoryLedgerResult.Durable)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val rootView = result(store().bootstrapFromVerifiedV2(owner, athlete))
        val correctionIntent = NativeHistoryWriteIntent.Correct(
            "workout-1", KotlinSavedSetCorrection(
                "edit-1", 0, "set-1", "set-event-1",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(java.math.BigDecimal("195"),
                    LoadUnit.LB, 6, 1)))
        assertTrue(wal().prepare(owner, athlete,
            rootView.head, correctionIntent, saved)
            is NativeSegmentedRedoResult.Durable)
        val originalShard = NativeSegmentedWriteSegment(
            rootView.head.rootDigest, 1, listOf(saved))
        val corrected = saved.copy(savedCorrectionRevision = 1)
        assertTrue(wal().stage(owner, athlete, originalShard, corrected)
            is NativeSegmentedRedoResult.Durable)
        val published = store().publishStagedForCandidateReview(owner, athlete)
        assertTrue(published.toString(),
            published is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(1L, result(published).head.completionCount)
        assertEquals(2, candidateFiles(".shard").size)
        assertEquals(1L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        assertEquals(0L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable)
            .ledger.completions.single().savedCorrectionRevision)
        assertFalse(result(published).canPublishHistory)
    }

    @Test fun duplicateReceiptFailsBeforeAnyCandidateRootMutation() {
        val saved = realComposerCompletion()
        assertTrue(v2.enrollVerifiedSnapshot(
            owner, athlete, listOf(saved)) { o, a ->
                signedIn == NativeKotlinOwnerIdentity(o, a)
            } is NativeHistoryLedgerResult.Durable)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val original = result(store().bootstrapFromVerifiedV2(owner, athlete))
        val operation = finish(2)
        assertTrue(wal().prepare(owner, athlete, original.head, operation, null)
            is NativeSegmentedRedoResult.Durable)
        val collision = entry(2).copy(storedReceiptId = saved.storedReceiptId)
        val attempted = wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(original.head.rootDigest, 1, listOf(saved)),
            collision)
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.INVALID_PROPOSAL), attempted)
        assertEquals(original, result(store().audit(owner, athlete)))
        assertEquals(1, candidateFiles(".shard").size)
    }

    @Test fun replayVerifiedMirrorRefusesUncommittedSyntheticFinish() {
        val initial = boot()
        stagedAppend(initial)
        val result = store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier())
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE),
            result)
        assertEquals(initial, this.result(store().audit(owner, athlete)))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED),
            store().reviewAuthenticatedV2Parity(owner, athlete))
        assertTrue(candidateFiles(".shard").isEmpty())
    }

    @Test fun replayVerifiedMirrorAcceptsExactDurableComposerFinish() {
        val initial = boot()
        val saved = realComposerCompletion()
        val intent = finish(1)
        assertTrue(wal().prepare(owner, athlete, initial.head, intent, null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(initial.head.rootDigest, 1, emptyList()),
            saved) is NativeSegmentedRedoResult.Durable)
        assertTrue(v2.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2.finalize(owner, athlete, intent, saved)
            is NativeHistoryLedgerResult.Durable)
        val accepted = store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier())
        assertTrue(accepted.toString(),
            accepted is NativeCandidateHistoryRootResult.Reviewed)
        val view = this.result(accepted)
        // The candidate check also requires the independently reviewed v2
        // shadow to catch up to the newly finalized source revision.
        assertTrue(shadow.sync(owner, athlete) is NativeSegmentedHistoryResult.Indexed)
        assertEquals(view,
            this.result(store().reviewAuthenticatedV2Parity(owner, athlete)))
        assertEquals(2L, view.head.revision)
        assertEquals(1L, view.head.completionCount)
        assertEquals(1L, (v2.open(owner, athlete)
            as NativeHistoryLedgerResult.Durable).ledger.completions.size.toLong())
        assertTrue((store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier())
            as NativeCandidateHistoryRootResult.Reviewed).replayed)
    }

    @Test fun pagedIndexRefreshesOnlyAfterRealComposerFinishAndBothPageTreesStayComplete() {
        val initial = boot()
        assertTrue(pagedIndex().build(owner,athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val saved = realComposerCompletion()
        val intent = finish(1)
        assertTrue(wal().prepare(owner,athlete,initial.head,intent,null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner,athlete,
            NativeSegmentedWriteSegment(initial.head.rootDigest,1,emptyList()),
            saved) is NativeSegmentedRedoResult.Durable)
        assertTrue(v2.prepare(owner,athlete,1,intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2.finalize(owner,athlete,intent,saved)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE),
            pagedIndex().build(owner,athlete))
        assertTrue(store().publishReplayVerifiedV2Mirror(
            owner,athlete,replay,receiptVerifier())
            is NativeCandidateHistoryRootResult.Reviewed)
        assertTrue(shadow.sync(owner,athlete)
            is NativeSegmentedHistoryResult.Indexed)
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.STALE_INDEX),
            pagedIndex().findWorkout(owner,athlete,"workout-1"))

        val built=pagedIndex().build(owner,athlete)
            as NativeCandidatePagedIdentityResult.Indexed
        assertEquals(1L,built.view.completionCount)
        assertEquals(2L,built.view.sourceRevision)
        assertEquals(1,built.view.workoutPages)
        assertEquals(1,built.view.receiptPages)
        assertFalse(built.view.canAuthorizeNewWorkout)
        val byWorkout=pagedIndex().findWorkout(owner,athlete,"workout-1")
            as NativeCandidatePagedIdentityResult.Lookup
        val byReceipt=pagedIndex().findReceipt(owner,athlete,saved.storedReceiptId)
            as NativeCandidatePagedIdentityResult.Lookup
        assertEquals(1L,byWorkout.row?.completionOrder)
        assertEquals(byWorkout.row,byReceipt.row)
        assertFalse(byReceipt.canAuthorizeCorrection)
        assertTrue((pagedIndex().build(owner,athlete)
            as NativeCandidatePagedIdentityResult.Indexed).replayed)
        assertEquals(2,candidateFiles(".pidpage").size)

        // Corrupt the OTHER tree. A workout lookup must refuse even though
        // its own page and the directory checksum are still intact.
        val receiptFile=candidateFiles(".pidpage").last()
        // Either page's corruption must invalidate the full paired index.
        val pageBytes=Files.readAllBytes(receiptFile)
        pageBytes[pageBytes.lastIndex]=(pageBytes.last().toInt() xor 1).toByte()
        Files.write(receiptFile,pageBytes)
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX),
            pagedIndex().findWorkout(owner,athlete,"workout-1"))
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX),
            pagedIndex().build(owner,athlete))
    }

    @Test fun replayVerifiedMirrorRejectsFabricatedV2ReceiptWithoutJournal() {
        val initial = boot()
        stagedAppend(initial)
        val intent = finish(1)
        assertTrue(v2.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2.finalize(owner, athlete, intent, entry(1))
            is NativeHistoryLedgerResult.Durable)
        // A valid v2 checksum and segment patch are insufficient evidence.
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE),
            store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier()))
        assertEquals(initial, result(store().audit(owner, athlete)))
    }

    @Test fun replayVerifiedMirrorRejectsPendingV2EvenWithFinishedJournal() {
        val initial = boot()
        val saved = realComposerCompletion()
        val intent = finish(1)
        assertTrue(wal().prepare(owner, athlete, initial.head, intent, null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(initial.head.rootDigest, 1, emptyList()),
            saved) is NativeSegmentedRedoResult.Durable)
        assertTrue(v2.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE),
            store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier()))
        assertEquals(initial, result(store().audit(owner, athlete)))
    }

    @Test fun replayVerifiedMirrorAcceptsSavedCorrectionOnlyAfterCanonicalCommit() {
        val original = realComposerCompletion()
        assertTrue(v2.enrollVerifiedSnapshot(owner, athlete,
            listOf(original)) { o, a ->
                signedIn == NativeKotlinOwnerIdentity(o, a)
            } is NativeHistoryLedgerResult.Durable)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val initial = result(store().bootstrapFromVerifiedV2(owner, athlete))
        assertTrue(identityIndex().build(owner, athlete)
            is NativeCandidateIdentityIndexResult.Indexed)
        assertTrue(pagedIndex().build(owner, athlete)
            is NativeCandidatePagedIdentityResult.Indexed)
        val originalPageCount = candidateFiles(".pidpage").size
        assertEquals(2, originalPageCount)
        val previousPageFiles = candidateFiles(".pidpage")
            .associate { path ->
                path.fileName.toString() to Files.readAllBytes(path).toList()
            }
        val previousDirectory = Files.readAllBytes(
            candidateFiles(".pidx").single()).toList()
        val operation = KotlinSavedSetCorrection(
            "edit-1", 0, "set-1", "set-event-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(java.math.BigDecimal("195"), LoadUnit.LB, 6, 1))
        val intent = NativeHistoryWriteIntent.Correct("workout-1", operation)
        val corrected = original.copy(savedCorrectionRevision = 1)
        assertTrue(wal().prepare(owner, athlete, initial.head, intent, original)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(wal().stage(owner, athlete,
            NativeSegmentedWriteSegment(initial.head.rootDigest, 1,
                listOf(original)), corrected)
            is NativeSegmentedRedoResult.Durable)
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE),
            store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier()))
        assertTrue(v2.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(correction.appendStored(owner, "workout-1",
            operation) { who, source ->
                authority.canAccessIssuedWorkout(who, source)
            } is NativeSavedCorrectionFileResult.Durable)
        assertTrue(v2.finalize(owner, athlete, intent, corrected)
            is NativeHistoryLedgerResult.Durable)
        val mirrored = store().publishReplayVerifiedV2Mirror(owner, athlete, replay, receiptVerifier())
        assertTrue(mirrored.toString(),
            mirrored is NativeCandidateHistoryRootResult.Reviewed)
        assertEquals(2L, result(mirrored).head.revision)
        assertEquals(1L, result(mirrored).head.completionCount)
        assertEquals(2, candidateFiles(".shard").size)
        assertTrue(shadow.sync(owner, athlete) is NativeSegmentedHistoryResult.Indexed)
        assertEquals(this.result(mirrored),
            this.result(store().reviewAuthenticatedV2Parity(owner, athlete)))
        assertEquals(NativeCandidatePagedIdentityResult.Rejected(
            NativeCandidatePagedIdentityRefusal.STALE_INDEX),
            pagedIndex().findWorkout(owner, athlete, "workout-1"))
        val updatedPaged = pagedIndex().build(owner, athlete)
            as NativeCandidatePagedIdentityResult.Indexed
        assertEquals(2L, updatedPaged.view.sourceRevision)
        assertEquals(1L, updatedPaged.view.completionCount)
        assertEquals(originalPageCount, candidateFiles(".pidpage").size)
        assertEquals(previousPageFiles, candidateFiles(".pidpage")
            .associate { path ->
                path.fileName.toString() to Files.readAllBytes(path).toList()
            })
        assertNotEquals(previousDirectory,
            Files.readAllBytes(candidateFiles(".pidx").single()).toList())
        val pagedWorkout = pagedIndex().findWorkout(owner, athlete, "workout-1")
            as NativeCandidatePagedIdentityResult.Lookup
        val pagedReceipt = pagedIndex().findReceipt(
            owner, athlete, original.storedReceiptId)
            as NativeCandidatePagedIdentityResult.Lookup
        assertEquals(pagedWorkout.row, pagedReceipt.row)
        assertEquals(1L, pagedWorkout.row?.completionOrder)
        assertEquals(original.storedReceiptId, pagedWorkout.row?.storedReceiptId)
        assertFalse(updatedPaged.view.canIssueProgression)
        assertFalse(pagedWorkout.canAuthorizeNewWorkout)
        assertEquals(NativeCandidateIdentityIndexResult.Rejected(
            NativeCandidateIdentityIndexRefusal.STALE_INDEX),
            identityIndex().findWorkout(owner, athlete, "workout-1"))
        val refreshed = identityIndex().build(owner, athlete)
        assertTrue(refreshed.toString(),
            refreshed is NativeCandidateIdentityIndexResult.Indexed)
        val byWorkout = identityIndex().findWorkout(owner, athlete, "workout-1")
            as NativeCandidateIdentityIndexResult.Lookup
        val byReceipt = identityIndex().findReceipt(
            owner, athlete, original.storedReceiptId)
            as NativeCandidateIdentityIndexResult.Lookup
        assertEquals(byWorkout.match, byReceipt.match)
        assertEquals(1L, byWorkout.match?.completionOrder)
        assertEquals(original.storedReceiptId, byWorkout.match?.storedReceiptId)
        assertFalse(byWorkout.canAuthorizeNewWorkout)
    }

}
