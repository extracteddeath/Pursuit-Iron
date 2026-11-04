package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Candidate-only shadow. The verified v2 ledger remains sole Finish and
 * correction owner; these tests never fabricate completed journal facts.
 */
class NativeKotlinSegmentedHistoryShadowStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val root: Path get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity(owner, athlete)
    private val identity = NativeKotlinCurrentIdentity { signedIn }
    private fun v2() = NativeKotlinHistoryManifestFileStore(root)
    private fun fence() = NativeKotlinApprovalRevisionFence(root)
    private fun shadow() = NativeKotlinSegmentedHistoryShadowStore(
        root, v2(), fence(), identity)
    private fun permitted(o: String, a: String) =
        signedIn == NativeKotlinOwnerIdentity(o, a)
    private fun entry(i: Int, corrected: Long = 0) = KotlinHistoryManifestEntry(
        i.toLong(), "workout-$i", "stored-$i", "finish-$i", 3, corrected)
    private fun history(count: Int) = (1..count).map(::entry)
    private fun enroll(count: Int) {
        val result = v2().enrollVerifiedSnapshot(
            owner, athlete, history(count), ::permitted)
        assertTrue(result.toString(), result is NativeHistoryLedgerResult.Durable)
    }
    private fun page(offset: Int, size: Int, revision: Long = 1) =
        shadow().readPage(owner, athlete, revision, offset, size)
    private fun readPage(offset: Int, size: Int, revision: Long = 1):
        NativeSegmentedHistoryPage {
        val opened = page(offset, size, revision)
        assertTrue(opened.toString(), opened is NativeSegmentedHistoryResult.Page)
        return (opened as NativeSegmentedHistoryResult.Page).page
    }
    private fun partFiles(extension: String): List<Path> = Files.list(root).use {
        it.filter { path -> path.fileName.toString().endsWith(extension) }
            .toList()
    }
    private fun correctionAt(i: Int, originalRevision: Long = 1): Long {
        val operation = KotlinSavedSetCorrection("edit-$i", 0, "set-$i",
            "original-$i", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(java.math.BigDecimal("200"), LoadUnit.LB, 8, 2))
        val intent = NativeHistoryWriteIntent.Correct("workout-$i", operation)
        assertTrue(v2().prepare(owner, athlete, originalRevision, intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(v2().finalize(owner, athlete, intent, entry(i, 1))
            is NativeHistoryLedgerResult.Durable)
        return originalRevision + 1
    }

    @Test fun imports512AndReadsExactNewestFirstPagesAcrossSegmentBoundaries() {
        enroll(512)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 512, false),
            shadow().sync(owner, athlete))
        assertEquals(4, partFiles(".seg").size)
        assertEquals(1, partFiles(".idx").size)
        val first = readPage(0, 20)
        assertEquals((512 downTo 493).map { "workout-$it" },
            first.completionsNewestFirst.map { it.workoutId })
        assertEquals(20, first.nextOffsetFromNewest)
        val crossing = readPage(120, 128)
        assertEquals((392 downTo 265).map { "workout-$it" },
            crossing.completionsNewestFirst.map { it.workoutId })
        assertEquals(248, crossing.nextOffsetFromNewest)
        val last = readPage(510, 128)
        assertEquals(listOf(entry(2), entry(1)), last.completionsNewestFirst)
        assertNull(last.nextOffsetFromNewest)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 512, true),
            shadow().sync(owner, athlete))
        assertEquals(4, partFiles(".seg").size)
        // Cold reopen: neither metadata nor identities need a caller cache.
        assertEquals(first, readPage(0, 20))
    }

    @Test fun oldCorrectionStalesPageUntilNewManifestRevisionIsIndexed() {
        enroll(300)
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val initial = readPage(135, 64)
        assertEquals(0L, initial.completionsNewestFirst.first {
            it.workoutId == "workout-160" }.savedCorrectionRevision)
        val revision = correctionAt(160)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.STALE_SOURCE), page(135, 64))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.STALE_SOURCE), page(135, 64, revision))
        assertEquals(NativeSegmentedHistoryResult.Indexed(revision, 300, false),
            shadow().sync(owner, athlete))
        val refreshed = readPage(135, 64, revision)
        assertEquals(1L, refreshed.completionsNewestFirst.first {
            it.workoutId == "workout-160" }.savedCorrectionRevision)
        assertEquals(1, refreshed.completionsNewestFirst.count {
            it.savedCorrectionRevision > 0 })
        assertEquals(entry(300), readPage(0, 1, revision).completionsNewestFirst.single())
        assertEquals(4, partFiles(".seg").size) // 3 old + 1 changed shard
        assertNotEquals(initial.sourceFingerprint, refreshed.sourceFingerprint)
    }

    @Test fun unfinishedAuthoritativeTransactionCannotPublishOrServePages() {
        enroll(130)
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val operation = NativeHistoryWriteIntent.Finish(
            "workout-131", KotlinJournalOperation.Finish("finish-131", 0))
        assertTrue(v2().prepare(owner, athlete, 1, operation)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.SOURCE_NOT_READY), page(0, 10))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.SOURCE_NOT_READY),
            shadow().sync(owner, athlete))
        assertTrue(v2().finalize(owner, athlete, operation,
            entry(131).copy(originalJournalRevision = 1))
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.STALE_SOURCE), page(0, 10, 2))
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        assertEquals("workout-131", readPage(0, 1, 2)
            .completionsNewestFirst.single().workoutId)
    }

    @Test fun corruptedOldShardCannotBeRegeneratedByRefresh() {
        enroll(256)
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val old = partFiles(".seg").first()
        val bytes = Files.readAllBytes(old)
        bytes[bytes.size - 2] = (bytes[bytes.size - 2].toInt() xor 1).toByte()
        Files.write(old, bytes)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.CORRUPT_INDEX), shadow().sync(owner, athlete))
        // A page touching BOTH segment files must detect either corruption.
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.CORRUPT_INDEX), page(64, 128))

    }

    @Test fun unauthorizedOrInvalidScopeNeverReadsStoredSegments() {
        enroll(129)
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        signedIn = null
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.NOT_AUTHORIZED), page(0, 8))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.NOT_AUTHORIZED),
            shadow().sync(owner, athlete))
        signedIn = NativeKotlinOwnerIdentity(owner, athlete)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.INVALID_SCOPE),
            shadow().readPage("../owner", athlete, 1, 0, 8))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.INVALID_PAGE), page(-1, 8))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.INVALID_PAGE), page(0, 129))
    }

    @Test fun absentOrTamperedRootNeverSilentlyReenrollsHistory() {
        enroll(150)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.NOT_INDEXED), page(0, 3))
        assertTrue(shadow().sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val index = partFiles(".idx").single()
        val bytes = Files.readAllBytes(index)
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 1).toByte()
        Files.write(index, bytes)
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.CORRUPT_INDEX), page(0, 3))
        assertEquals(NativeSegmentedHistoryResult.Rejected(
            NativeSegmentedHistoryRefusal.CORRUPT_INDEX),
            shadow().sync(owner, athlete))
        assertArrayEquals(bytes, Files.readAllBytes(index))
    }
    @Test fun maximumLengthValidIdsFitWholeImmutableSegmentWithoutTruncation() {
        fun longId(prefix: String, index: Int) =
            (prefix + index.toString().padStart(5, '0')).padEnd(80, 'x')
        val source = (1..128).map { i -> KotlinHistoryManifestEntry(
            i.toLong(), longId("w", i), longId("s", i),
            longId("f", i), 3, 0) }
        assertTrue(v2().enrollVerifiedSnapshot(owner, athlete,
            source, ::permitted) is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 128, false),
            shadow().sync(owner, athlete))
        assertEquals(source.reversed(), readPage(0, 128).completionsNewestFirst)
        assertEquals(1, partFiles(".seg").size)
    }

}
