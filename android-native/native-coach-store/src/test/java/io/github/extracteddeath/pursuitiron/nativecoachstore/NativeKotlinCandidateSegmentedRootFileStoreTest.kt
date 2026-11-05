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

    @Test fun noUnenrolledOrPartialHistoryMayCreateCandidateRoot() {
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED),
            store().audit(owner, athlete))
        assertEquals(rejection(NativeCandidateHistoryRootRefusal.BOOTSTRAP_DENIED),
            store().bootstrapFromVerifiedV2(owner, athlete))
        assertTrue(candidateFiles(".root").isEmpty())
        // Onboarding or an absent UI history list cannot bypass v2 enrollment.
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
}
