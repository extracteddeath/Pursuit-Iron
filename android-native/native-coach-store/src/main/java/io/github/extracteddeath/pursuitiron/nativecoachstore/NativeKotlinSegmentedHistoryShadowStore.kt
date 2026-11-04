package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryCapacityPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Collections

/**
 * Experimental READ-ONLY segmented shadow of the authoritative v2 history.
 *
 * v2 still owns every Finish/correction/migration transaction. This class
 * never enrolls an athlete, edits a journal, or decides what history exists.
 * An owner-fenced sync copies the FULL verified v2 manifest into immutable
 * 128-entry content-addressed files, then atomically points a small index at
 * those files. A crash before root publication leaves unreferenced files,
 * not a partially published new history.
 *
 * Paged reads verify current v2 revision AND content before consulting the
 * shadow; thus these reads are NOT yet constant-time lifetime-history reads.
 * Do not use as a new production owner until two-store migration, recovery,
 * backup, cross-device and long-history policies have been certified.
 */
data class NativeSegmentedHistoryPage(
    val sourceRevision: Long,
    val sourceFingerprint: String,
    val totalCompletions: Int,
    val offsetFromNewest: Int,
    val completionsNewestFirst: List<KotlinHistoryManifestEntry>,
    val nextOffsetFromNewest: Int?
)

enum class NativeSegmentedHistoryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, SOURCE_NOT_READY, NOT_INDEXED,
    STALE_SOURCE, INVALID_PAGE, CORRUPT_INDEX, STORAGE_UNAVAILABLE
}

sealed interface NativeSegmentedHistoryResult {
    data class Indexed(val revision: Long, val count: Int, val replayed: Boolean) :
        NativeSegmentedHistoryResult
    data class Page(val page: NativeSegmentedHistoryPage) :
        NativeSegmentedHistoryResult
    data class Rejected(val reason: NativeSegmentedHistoryRefusal) :
        NativeSegmentedHistoryResult
}

class NativeKotlinSegmentedHistoryShadowStore(
    private val directory: Path,
    private val v2: NativeKotlinHistoryManifestFileStore,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private data class SegmentRef(val firstOrdinal: Long, val count: Int,
                                  val sha: String)
    private data class Index(
        val owner: String, val athlete: String, val sourceRevision: Long,
        val count: Int, val fingerprint: String, val segments: List<SegmentRef>
    )
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digestPattern = Regex("^[a-f0-9]{64}$")

    companion object {
        private const val INDEX_MAGIC = 0x50495349 // PISI
        private const val SEGMENT_MAGIC = 0x50495347 // PISG
        private const val VERSION = 1
        private const val SEGMENT_SIZE = 128
        private const val MAX_INDEX_BYTES = 65_536
        // 128 legal 80-character IDs per field can exceed 32 KiB.\n        private const val MAX_SEGMENT_BYTES = 65_536
        private const val MAX_REVISION = 9_007_199_254_740_991L
    }

    private fun rejected(reason: NativeSegmentedHistoryRefusal) =
        NativeSegmentedHistoryResult.Rejected(reason)

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun scope(owner: String, athlete: String) =
        hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))
    private fun indexPath(owner: String, athlete: String) =
        directory.resolve("segmented-history-" + scope(owner, athlete) + ".idx")
    private fun segmentPath(owner: String, athlete: String, digest: String) =
        directory.resolve("segmented-history-" + scope(owner, athlete) +
            "-" + digest + ".seg")

    private fun authorized(owner: String, athlete: String): Boolean =
        runCatching { identity.signedInIdentity() }.getOrNull()?.let {
            it.ownerKey == owner && it.athleteId == athlete
        } == true

    private fun safeDirectory() {
        Files.createDirectories(directory)
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory))
            throw IOException("unsafe private history directory")
    }

    private fun readBounded(path: Path, bound: Int): ByteArray {
        if (Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(path) > bound.toLong()) throw IOException("invalid file")
        return Files.readAllBytes(path).also {
            if (it.size > bound) throw IOException("oversize file")
        }
    }

    private fun wrap(body: ByteArray, maxSize: Int): ByteArray {
        if (body.size > maxSize - 36) throw IOException("oversize history object")
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use {
            it.writeInt(body.size)
            it.write(body)
            it.write(MessageDigest.getInstance("SHA-256").digest(body))
        }
        return output.toByteArray()
    }

    private fun unwrap(encoded: ByteArray, maxSize: Int): DataInputStream {
        if (encoded.size !in 40..maxSize) throw IOException("bad envelope")
        val input = DataInputStream(ByteArrayInputStream(encoded))
        val count = input.readInt()
        if (count < 4 || count + 36 != encoded.size)
            throw IOException("invalid framing")
        val payload = ByteArray(count)
        input.readFully(payload)
        val checksum = ByteArray(32)
        input.readFully(checksum)
        if (!MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(payload), checksum))
            throw IOException("checksum mismatch")
        return DataInputStream(ByteArrayInputStream(payload))
    }

    private fun DataOutputStream.writeEntry(e: KotlinHistoryManifestEntry) {
        writeLong(e.completionOrder)
        writeUTF(e.workoutId)
        writeUTF(e.storedReceiptId)
        writeUTF(e.finishEventId)
        writeLong(e.originalJournalRevision)
        writeLong(e.savedCorrectionRevision)
    }

    private fun DataInputStream.readEntry() = KotlinHistoryManifestEntry(
        readLong(), readUTF(), readUTF(), readUTF(), readLong(), readLong())

    private fun validEntry(e: KotlinHistoryManifestEntry) =
        e.completionOrder in 1..MAX_REVISION &&
            e.originalJournalRevision in 1..MAX_REVISION &&
            e.savedCorrectionRevision in 0..MAX_REVISION &&
            id.matches(e.workoutId) && id.matches(e.storedReceiptId) &&
            id.matches(e.finishEventId)

    private fun sourceFingerprint(ledger: NativeHistoryLedger): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeUTF(ledger.ownerKey)
            data.writeUTF(ledger.athleteId)
            data.writeLong(ledger.revision)
            data.writeInt(ledger.completions.size)
            ledger.completions.forEach { data.writeEntry(it) }
        }
        return hash(output.toByteArray())
    }

    private fun encodeSegment(
        owner: String, athlete: String, first: Long,
        entries: List<KotlinHistoryManifestEntry>
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(SEGMENT_MAGIC)
            data.writeInt(VERSION)
            data.writeUTF(owner)
            data.writeUTF(athlete)
            data.writeLong(first)
            data.writeInt(entries.size)
            entries.forEach { data.writeEntry(it) }
        }
        return wrap(output.toByteArray(), MAX_SEGMENT_BYTES)
    }

    private fun parseSegment(
        owner: String, athlete: String, ref: SegmentRef
    ): List<KotlinHistoryManifestEntry> {
        val path = segmentPath(owner, athlete, ref.sha)
        val bytes = readBounded(path, MAX_SEGMENT_BYTES)
        if (hash(bytes) != ref.sha) throw IOException("segment identity mismatch")
        val data = unwrap(bytes, MAX_SEGMENT_BYTES)
        if (data.readInt() != SEGMENT_MAGIC || data.readInt() != VERSION ||
            data.readUTF() != owner || data.readUTF() != athlete ||
            data.readLong() != ref.firstOrdinal ||
            data.readInt() != ref.count) throw IOException("segment scope mismatch")
        val entries = (0 until ref.count).map { data.readEntry() }
        if (data.available() != 0 ||
            entries.withIndex().any { (index, e) ->
                !validEntry(e) || e.completionOrder != ref.firstOrdinal + index
            }) throw IOException("invalid segment entries")
        return entries
    }

    private fun encodeIndex(index: Index): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(INDEX_MAGIC)
            data.writeInt(VERSION)
            data.writeUTF(index.owner)
            data.writeUTF(index.athlete)
            data.writeLong(index.sourceRevision)
            data.writeInt(index.count)
            data.writeUTF(index.fingerprint)
            data.writeInt(index.segments.size)
            for (segment in index.segments) {
                data.writeLong(segment.firstOrdinal)
                data.writeInt(segment.count)
                data.writeUTF(segment.sha)
            }
        }
        return wrap(output.toByteArray(), MAX_INDEX_BYTES)
    }

    private fun parseIndex(owner: String, athlete: String): Index? {
        val path = indexPath(owner, athlete)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        val data = unwrap(readBounded(path, MAX_INDEX_BYTES), MAX_INDEX_BYTES)
        if (data.readInt() != INDEX_MAGIC || data.readInt() != VERSION)
            throw IOException("unknown index format")
        val storedOwner = data.readUTF()
        val storedAthlete = data.readUTF()
        val revision = data.readLong()
        val count = data.readInt()
        val fingerprint = data.readUTF()
        val segmentCount = data.readInt()
        if (storedOwner != owner || storedAthlete != athlete ||
            revision !in 1..MAX_REVISION ||
            count !in 0..KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS ||
            !digestPattern.matches(fingerprint) ||
            segmentCount != (count + SEGMENT_SIZE - 1) / SEGMENT_SIZE)
            throw IOException("invalid index metadata")
        val descriptors = (0 until segmentCount).map { i ->
            SegmentRef(data.readLong(), data.readInt(), data.readUTF()).also {
                val expectedCount = minOf(SEGMENT_SIZE, count - i * SEGMENT_SIZE)
                if (it.firstOrdinal != i.toLong() * SEGMENT_SIZE + 1 ||
                    it.count != expectedCount || !digestPattern.matches(it.sha))
                    throw IOException("invalid index segment bounds")
            }
        }
        if (data.available() != 0) throw IOException("trailing index bytes")
        return Index(owner, athlete, revision, count, fingerprint, descriptors)
    }

    private fun atomicWrite(path: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(directory, "history-segment-", ".pending")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buf = ByteBuffer.wrap(bytes)
                while (buf.hasRemaining()) channel.write(buf)
                channel.force(true)
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IOException("atomic publication unsupported", error)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    /** Existing immutable files must match exactly, never be repaired in place. */
    private fun publishSegment(
        owner: String, athlete: String, descriptor: SegmentRef, bytes: ByteArray
    ) {
        val path = segmentPath(owner, athlete, descriptor.sha)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!readBounded(path, MAX_SEGMENT_BYTES).contentEquals(bytes))
                throw IOException("immutable segment mismatch")
        } else atomicWrite(path, bytes)
    }

    private fun currentLedger(owner: String, athlete: String): NativeHistoryLedger? =
        (v2.open(owner, athlete) as? NativeHistoryLedgerResult.Durable)?.ledger
            ?.takeIf { it.ownerKey == owner && it.athleteId == athlete &&
                it.readyForHistoryReads && it.revision in 1..MAX_REVISION &&
                it.completions.size <= KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS }

    private fun guarded(
        owner: String, athlete: String,
        work: () -> NativeSegmentedHistoryResult
    ): NativeSegmentedHistoryResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return rejected(NativeSegmentedHistoryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    rejected(NativeSegmentedHistoryRefusal.NOT_AUTHORIZED)
                else {
                    safeDirectory()
                    work()
                }
            }
        }.getOrElse { rejected(NativeSegmentedHistoryRefusal.STORAGE_UNAVAILABLE) }
    }

    /**
     * Shadow import / refresh ONLY. A pending authoritative redo prevents
     * publication, and an existing corrupt index cannot be reset by sync.
     */
    fun sync(owner: String, athlete: String): NativeSegmentedHistoryResult =
        guarded(owner, athlete) {
            val ledger = currentLedger(owner, athlete)
                ?: return@guarded rejected(NativeSegmentedHistoryRefusal.SOURCE_NOT_READY)
            val existing = try { parseIndex(owner, athlete) }
                catch (_: Exception) {
                    return@guarded rejected(NativeSegmentedHistoryRefusal.CORRUPT_INDEX)
                }
            val fingerprint = sourceFingerprint(ledger)
            if (existing != null &&
                (ledger.revision < existing.sourceRevision ||
                    ledger.revision == existing.sourceRevision &&
                    (existing.fingerprint != fingerprint ||
                        existing.count != ledger.completions.size)))
                return@guarded rejected(NativeSegmentedHistoryRefusal.STALE_SOURCE)
            // Refuse missing/tampered previously published shards before any
            // refresh. Never silently re-create a lost source of historical facts.
            if (existing != null) {
                try {
                    for (ref in existing.segments)
                        parseSegment(owner, athlete, ref)
                } catch (_: Exception) {
                    return@guarded rejected(NativeSegmentedHistoryRefusal.CORRUPT_INDEX)
                }
            }
            if (existing != null && existing.sourceRevision == ledger.revision)
                return@guarded NativeSegmentedHistoryResult.Indexed(
                    ledger.revision, ledger.completions.size, true)

            val references = ArrayList<SegmentRef>()
            for ((i, chunk) in ledger.completions.chunked(SEGMENT_SIZE).withIndex()) {
                val first = i.toLong() * SEGMENT_SIZE + 1
                val bytes = encodeSegment(owner, athlete, first, chunk)
                val reference = SegmentRef(first, chunk.size, hash(bytes))
                publishSegment(owner, athlete, reference, bytes)
                references.add(reference)
            }
            val next = Index(owner, athlete, ledger.revision,
                ledger.completions.size, fingerprint, references)
            atomicWrite(indexPath(owner, athlete), encodeIndex(next))
            NativeSegmentedHistoryResult.Indexed(
                ledger.revision, ledger.completions.size, false)
        }

    /**
     * Newest-first exact page. Only selected segment payloads are opened, but
     * v2's COMPLETE manifest is still rechecked for every request.
     */
    fun readPage(
        owner: String, athlete: String,
        expectedSourceRevision: Long, offsetFromNewest: Int, pageSize: Int
    ): NativeSegmentedHistoryResult = guarded(owner, athlete) {
        if (pageSize !in 1..SEGMENT_SIZE || offsetFromNewest < 0)
            return@guarded rejected(NativeSegmentedHistoryRefusal.INVALID_PAGE)
        val ledger = currentLedger(owner, athlete)
            ?: return@guarded rejected(NativeSegmentedHistoryRefusal.SOURCE_NOT_READY)
        val index = try { parseIndex(owner, athlete) }
            catch (_: Exception) {
                return@guarded rejected(NativeSegmentedHistoryRefusal.CORRUPT_INDEX)
            } ?: return@guarded rejected(NativeSegmentedHistoryRefusal.NOT_INDEXED)
        if (expectedSourceRevision != ledger.revision ||
            index.sourceRevision != ledger.revision ||
            index.count != ledger.completions.size ||
            index.fingerprint != sourceFingerprint(ledger))
            return@guarded rejected(NativeSegmentedHistoryRefusal.STALE_SOURCE)
        if (offsetFromNewest > index.count)
            return@guarded rejected(NativeSegmentedHistoryRefusal.INVALID_PAGE)
        val count = minOf(pageSize, index.count - offsetFromNewest)
        val opened = HashMap<Int, List<KotlinHistoryManifestEntry>>()
        val values = try {
            (0 until count).map { pageIndex ->
                val originalIndex = index.count - offsetFromNewest - pageIndex - 1
                val segmentIndex = originalIndex / SEGMENT_SIZE
                val segment = opened.getOrPut(segmentIndex) {
                    parseSegment(owner, athlete, index.segments[segmentIndex])
                }
                segment[originalIndex % SEGMENT_SIZE]
            }
        } catch (_: Exception) {
            return@guarded rejected(NativeSegmentedHistoryRefusal.CORRUPT_INDEX)
        }
        // Revalidate exact source identities even for the requested page.
        if (values.withIndex().any { (i, value) ->
                value != ledger.completions[index.count - offsetFromNewest - i - 1]
            }) return@guarded rejected(NativeSegmentedHistoryRefusal.STALE_SOURCE)
        val end = offsetFromNewest + count
        NativeSegmentedHistoryResult.Page(NativeSegmentedHistoryPage(
            index.sourceRevision, index.fingerprint, index.count,
            offsetFromNewest, Collections.unmodifiableList(values),
            if (end < index.count) end else null
        ))
    }
}
