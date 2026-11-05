package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Candidate WAL tests assert *durable preparation and deterministic recovery*,
 * never that staged proposed history has been published to an athlete.
 */
class NativeKotlinSegmentedRedoFileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity(owner, athlete)
    private val identity = NativeKotlinCurrentIdentity { signedIn }
    private fun store() = NativeKotlinSegmentedRedoFileStore(
        root, NativeKotlinApprovalRevisionFence(root), identity)
    private fun head(count: Long, revision: Long = 7) =
        NativeSegmentedWriteHead(owner, athlete, revision, count, "a".repeat(64))
    private fun entry(i: Long, edits: Long = 0, custom: Boolean = false) =
        KotlinHistoryManifestEntry(i, "workout-$i",
            if (custom) "custom-v2-stored-$i" else "stored-$i",
            "finish-$i", 2, edits)
    private fun segment(h: NativeSegmentedWriteHead, first: Long, count: Int) =
        NativeSegmentedWriteSegment(h.rootDigest, first,
            (first until first + count).map(::entry))
    private fun finish(i: Long) = NativeHistoryWriteIntent.Finish(
        "workout-$i", KotlinJournalOperation.Finish("finish-$i", 1))
    private fun customFinish(i: Long) = NativeHistoryWriteIntent.CustomFinish(
        "workout-$i", CustomWorkoutJournalOperation.Finish("finish-$i", 1))
    private fun correct(i: Long) = NativeHistoryWriteIntent.Correct(
        "workout-$i", KotlinSavedSetCorrection("edit-$i", 0,
            "set-$i", "event-$i", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("195.5"), LoadUnit.LB, 6, 2)))
    private fun customCorrect(i: Long) = NativeHistoryWriteIntent.CustomCorrect(
        "workout-$i", CustomSavedVisitCorrection("edit-$i", 0,
            CustomWorkoutVisitKey("set-$i", CustomWorkoutVisitPart.MAIN),
            "event-$i", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("35"), LoadUnit.LB, 8, 1)))
    private fun durable(r: NativeSegmentedRedoResult) =
        (r as NativeSegmentedRedoResult.Durable)
    private fun storedWal() = Files.list(root).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".wal") }
            .findFirst().orElseThrow()
    }

    @Test fun pendingFinishSurvivesColdReopenAndStagedPatchIsIdempotent() {
        val h = head(128)
        val intent = finish(129)
        val original = store().prepare(owner, athlete, h, intent, null)
        assertFalse(durable(original).replayed)
        assertNull(durable(original).redo.stagedPatch)
        val reopened = store().read(owner, athlete)
        assertTrue(durable(reopened).replayed)
        assertEquals(durable(original).redo, durable(reopened).redo)
        assertTrue(durable(store().prepare(owner, athlete, h, intent, null)).replayed)
        val page = segment(h, 129, 0)
        val staged = durable(store().stage(owner, athlete, page, entry(129)))
        assertFalse(staged.replayed)
        assertEquals(129L, staged.redo.stagedPatch!!.nextCompletionCount)
        assertEquals(129L, staged.redo.stagedPatch!!.replaceSegmentFirstOrdinal)
        assertFalse(staged.redo.canPublishHistory)
        assertFalse(staged.redo.canTransferAuthority)
        assertFalse(staged.redo.canIssueProgression)
        assertEquals(staged.redo, durable(store().read(owner, athlete)).redo)
        assertTrue(durable(store().stage(owner, athlete, page, entry(129))).replayed)
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.PENDING_TRANSACTION),
            store().prepare(owner, athlete, h, finish(130), null))
    }

    @Test fun historicalCorrectionKeepsExactActualsThroughDurableRedo() {
        val h = head(512)
        val intent = correct(160)
        val old = entry(160)
        val stage1 = store().prepare(owner, athlete, h, intent, old)
        assertTrue(stage1 is NativeSegmentedRedoResult.Durable)
        assertEquals(intent, durable(store().read(owner, athlete)).redo.intent)
        val patch = durable(store().stage(owner, athlete,
            segment(h, 129, 128), old.copy(savedCorrectionRevision = 1)))
            .redo.stagedPatch!!
        assertEquals(129L, patch.replaceSegmentFirstOrdinal)
        assertEquals(512L, patch.nextCompletionCount)
        assertEquals(old.copy(savedCorrectionRevision = 1), patch.completions[31])
        assertEquals(intent, patch.consumedIntent)
        val reopened = durable(store().read(owner, athlete)).redo
        assertEquals(BigDecimal("195.5"),
            (reopened.intent as NativeHistoryWriteIntent.Correct)
                .operation.actual!!.load)
        assertEquals(6, (reopened.intent as NativeHistoryWriteIntent.Correct)
            .operation.actual!!.reps)
        assertEquals(patch, reopened.stagedPatch)
    }

    @Test fun customFinishAndCustomCorrectionAreDurablyRepresented() {
        val h = head(1)
        val f = customFinish(2)
        assertTrue(store().prepare(owner, athlete, h, f, null)
            is NativeSegmentedRedoResult.Durable)
        assertTrue(store().stage(owner, athlete, segment(h, 1, 1),
            entry(2, custom = true)) is NativeSegmentedRedoResult.Durable)
        assertEquals(f, durable(store().read(owner, athlete)).redo.intent)

        // Different athlete uses a physically different WAL and separate fence.
        signedIn = NativeKotlinOwnerIdentity("owner-2", athlete)
        val h2 = NativeSegmentedWriteHead("owner-2", athlete, 8, 2, "b".repeat(64))
        val correction = customCorrect(2)
        val target = entry(2, custom = true)
        assertTrue(store().prepare("owner-2", athlete, h2, correction, target)
            is NativeSegmentedRedoResult.Durable)
        val old = NativeSegmentedWriteSegment(h2.rootDigest, 1,
            listOf(entry(1), target))
        val staged = durable(store().stage("owner-2", athlete, old,
            target.copy(savedCorrectionRevision = 1))).redo
        assertEquals(1L, staged.stagedPatch!!.replaceSegmentFirstOrdinal)
        val restored = durable(store().read("owner-2", athlete)).redo
        assertEquals(correction, restored.intent)
        assertEquals(1L, restored.stagedPatch!!.completions[1].savedCorrectionRevision)
        assertEquals(2, Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".wal") }.count()
        })
    }

    @Test fun revokedAccountAndCorruptWalFailClosedWithoutOverwrite() {
        val h = head(128)
        assertTrue(store().prepare(owner, athlete, h, finish(129), null)
            is NativeSegmentedRedoResult.Durable)
        signedIn = null
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.NOT_AUTHORIZED), store().read(owner, athlete))
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.NOT_AUTHORIZED), store().stage(
                owner, athlete, segment(h, 129, 0), entry(129)))
        signedIn = NativeKotlinOwnerIdentity(owner, athlete)
        val path = storedWal()
        val bytes = Files.readAllBytes(path)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(path, bytes)
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.CORRUPT_WAL), store().read(owner, athlete))
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.CORRUPT_WAL),
            store().prepare(owner, athlete, h, finish(129), null))
        assertArrayEquals(bytes, Files.readAllBytes(path))
    }

    @Test fun invalidOrSwappedProposalCannotReplacePreparedRedo() {
        val h = head(150)
        val intent = correct(10)
        val target = entry(10)
        val prepared = durable(store().prepare(owner, athlete, h, intent, target))
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.INVALID_PROPOSAL), store().stage(
                owner, athlete, segment(h, 1, 128),
                target.copy(savedCorrectionRevision = 3)))
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.INVALID_PROPOSAL), store().stage(
                owner, athlete,
                segment(h, 1, 128).copy(sourceRootDigest = "b".repeat(64)),
                target.copy(savedCorrectionRevision = 1)))
        assertEquals(prepared.redo, durable(store().read(owner, athlete)).redo)
        assertEquals(NativeSegmentedRedoResult.Rejected(
            NativeSegmentedRedoRefusal.INVALID_SCOPE),
            store().read("../owner", athlete))
    }

    @Test fun beyond4096CanBeStagedWithoutTouchingOldHistoryLedger() {
        val v2 = NativeKotlinHistoryManifestFileStore(root)
        assertTrue(v2.enrollVerifiedSnapshot(owner, athlete, emptyList()) { a, b ->
            a == owner && b == athlete
        } is NativeHistoryLedgerResult.Durable)
        val before = v2.open(owner, athlete)
        val h = head(4096, 100)
        val intent = finish(4097)
        assertTrue(store().prepare(owner, athlete, h, intent, null)
            is NativeSegmentedRedoResult.Durable)
        val result = durable(store().stage(owner, athlete, segment(h, 4097, 0),
            entry(4097)))
        assertEquals(4097L, result.redo.stagedPatch!!.nextCompletionCount)
        assertEquals(before, v2.open(owner, athlete))
        // Candidate staging is never a substitute for real journal receipts.
        assertFalse(result.redo.canPublishHistory)
    }
}
