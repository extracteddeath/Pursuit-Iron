package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * DISK-BACKED, NON-AUTHORITATIVE candidate paged identity index.
 *
 * v2 plus FULL independently replayed candidate-root parity remain the ONLY
 * sources. No public app entrypoint can insert a workout or use a null lookup
 * to authorize Finish. Persist pages separately from a checksum-framed index
 * and bind both workout and receipt trees to the exact verified candidate root.
 *
 * The file format allows >4,096 rows, but the ONLY build path is still v2
 * backed and thus presently limited to 4,096. This is an on-disk format and
 * recovery rehearsal, NOT an unlimited production index or owner cutover.
 */
internal data class NativeCandidatePagedIdentityView(
    val rootDigest: String, val sourceRevision: Long,
    val completionCount: Long, val workoutPages: Int, val receiptPages: Int
) {
    val canAuthorizeNewWorkout: Boolean get() = false
    val canPublishHistory: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}
internal enum class NativeCandidatePagedIdentityRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, UNVERIFIED_SOURCE,
    NOT_INDEXED, STALE_INDEX, CORRUPT_INDEX, STORAGE_UNAVAILABLE
}
internal sealed interface NativeCandidatePagedIdentityResult {
    data class Indexed(
        val view: NativeCandidatePagedIdentityView, val replayed: Boolean
    ) : NativeCandidatePagedIdentityResult
    data class Lookup(
        val view: NativeCandidatePagedIdentityView,
        val row: NativeCandidateIndexedWorkout?
    ) : NativeCandidatePagedIdentityResult {
        val canAuthorizeNewWorkout: Boolean get() = false
        val canAuthorizeCorrection: Boolean get() = false
    }
    data class Rejected(val reason: NativeCandidatePagedIdentityRefusal) :
        NativeCandidatePagedIdentityResult
}

internal class NativeKotlinCandidatePagedIdentityFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val v2: NativeKotlinHistoryManifestFileStore,
    private val candidate: NativeKotlinCandidateSegmentedRootFileStore
) {
    private data class PageRef(
        val first: String, val last: String,
        val contentDigest: String, val fileDigest: String,
        val count: Int
    )
    private data class DiskIndex(
        val owner: String, val athlete: String, val rootDigest: String,
        val sourceRevision: Long, val count: Long,
        val workouts: List<PageRef>, val receipts: List<PageRef>
    )
    private data class Verified(
        val directory: NativeSegmentedIdentityPageDirectory,
        val index: DiskIndex
    )
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    private companion object {
        const val INDEX_MAGIC = 0x50495044 // PIPD
        const val PAGE_MAGIC = 0x50495047 // PIPG
        const val VERSION = 1
        const val MAX_INDEX_BYTES = 16_777_216
        const val MAX_PAGE_BYTES = 65_536
        const val MAX_REVISION = 9_007_199_254_740_991L
    }
    private fun reject(r: NativeCandidatePagedIdentityRefusal) =
        NativeCandidatePagedIdentityResult.Rejected(r)
    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    private fun scope(owner: String, athlete: String) =
        hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))
    private fun indexPath(owner: String, athlete: String) =
        directory.resolve("candidate-paged-identity-" + scope(owner, athlete) + ".pidx")
    private fun pagePath(owner: String, athlete: String, digest: String) =
        directory.resolve("candidate-paged-identity-" + scope(owner, athlete) +
            "-" + digest + ".pidpage")
    private fun authorized(owner: String, athlete: String) = runCatching {
        identity.signedInIdentity() == NativeKotlinOwnerIdentity(owner, athlete)
    }.getOrDefault(false)
    private fun safeDirectory() {
        Files.createDirectories(directory)
        if (Files.isSymbolicLink(directory) ||
            !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw IOException("unsafe paged index directory")
    }
    private fun guarded(
        owner: String, athlete: String,
        block: () -> NativeCandidatePagedIdentityResult
    ): NativeCandidatePagedIdentityResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return reject(NativeCandidatePagedIdentityRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    reject(NativeCandidatePagedIdentityRefusal.NOT_AUTHORIZED)
                else {
                    safeDirectory()
                    block()
                }
            }
        }.getOrElse { reject(NativeCandidatePagedIdentityRefusal.STORAGE_UNAVAILABLE) }
    }
    private fun wrap(body: ByteArray, bound: Int): ByteArray {
        if (body.size > bound - 36) throw IOException("paged object too large")
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(body.size)
            d.write(body)
            d.write(MessageDigest.getInstance("SHA-256").digest(body))
        }
        return out.toByteArray()
    }
    private fun unwrap(bytes: ByteArray, bound: Int): DataInputStream {
        if (bytes.size !in 40..bound) throw IOException("invalid envelope")
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val size = input.readInt()
        if (size < 4 || size + 36 != bytes.size)
            throw IOException("invalid envelope size")
        val body = ByteArray(size)
        input.readFully(body)
        val signature = ByteArray(32)
        input.readFully(signature)
        if (!MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(body), signature))
            throw IOException("paged checksum mismatch")
        return DataInputStream(ByteArrayInputStream(body))
    }
    private fun readBounded(file: Path, bound: Int): ByteArray {
        if (Files.isSymbolicLink(file) ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(file) > bound.toLong()) throw IOException("unsafe paged file")
        return Files.readAllBytes(file).also {
            if (it.size > bound) throw IOException("oversized paged file")
        }
    }
    private fun atomicWrite(path: Path, bytes: ByteArray) {
        val tmp = Files.createTempFile(directory, "candidate-paged-", ".pending")
        try {
            FileChannel.open(tmp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }
    private fun DataOutputStream.row(row: NativeCandidateIndexedWorkout) {
        writeUTF(row.workoutId)
        writeUTF(row.storedReceiptId)
        writeLong(row.completionOrder)
    }
    private fun DataInputStream.row() = NativeCandidateIndexedWorkout(
        readUTF(), readUTF(), readLong())

    private fun encodePage(
        owner: String, athlete: String, kind: Int,
        page: NativeSegmentedIdentityPage
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { d ->
            d.writeInt(PAGE_MAGIC); d.writeInt(VERSION)
            d.writeUTF(owner); d.writeUTF(athlete)
            d.writeInt(kind); d.writeInt(page.rows.size)
            page.rows.forEach { d.row(it) }
        }
        return wrap(output.toByteArray(), MAX_PAGE_BYTES)
    }
    private fun publishPage(
        owner: String, athlete: String, kind: Int,
        page: NativeSegmentedIdentityPage
    ): PageRef {
        val bytes = encodePage(owner, athlete, kind, page)
        val fileDigest = hash(bytes)
        val path = pagePath(owner, athlete, fileDigest)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!readBounded(path, MAX_PAGE_BYTES).contentEquals(bytes))
                throw IOException("immutable page collision")
        } else atomicWrite(path, bytes)
        return PageRef(page.firstKey, page.lastKey, page.contentDigest,
            fileDigest, page.rows.size)
    }
    private fun loadPage(
        owner: String, athlete: String, kind: Int, ref: PageRef
    ): NativeSegmentedIdentityPage {
        val bytes = readBounded(pagePath(owner, athlete, ref.fileDigest),
            MAX_PAGE_BYTES)
        if (hash(bytes) != ref.fileDigest) throw IOException("page hash mismatch")
        val d = unwrap(bytes, MAX_PAGE_BYTES)
        if (d.readInt() != PAGE_MAGIC || d.readInt() != VERSION ||
            d.readUTF() != owner || d.readUTF() != athlete ||
            d.readInt() != kind || d.readInt() != ref.count)
            throw IOException("paged scope mismatch")
        val rows = (0 until ref.count).map { d.row() }
        if (d.available() != 0) throw IOException("paged trailing bytes")
        return NativeSegmentedIdentityPage(ref.first, ref.last,
            ref.contentDigest, rows)
    }
    private fun DataOutputStream.refs(values: List<PageRef>) {
        writeInt(values.size)
        values.forEach {
            writeUTF(it.first); writeUTF(it.last)
            writeUTF(it.contentDigest); writeUTF(it.fileDigest)
            writeInt(it.count)
        }
    }
    private fun encodeIndex(index: DiskIndex): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { d ->
            d.writeInt(INDEX_MAGIC); d.writeInt(VERSION)
            d.writeUTF(index.owner); d.writeUTF(index.athlete)
            d.writeUTF(index.rootDigest); d.writeLong(index.sourceRevision)
            d.writeLong(index.count)
            d.refs(index.workouts); d.refs(index.receipts)
        }
        return wrap(body.toByteArray(), MAX_INDEX_BYTES)
    }
    private fun DataInputStream.refs(count: Long): List<PageRef> {
        val pageCount = readInt()
        if (pageCount < 0 || pageCount > MAX_INDEX_BYTES / 80 ||
            pageCount.toLong() > count ||
            (count == 0L) != (pageCount == 0))
            throw IOException("invalid page descriptor count")
        return (0 until pageCount).map {
            PageRef(readUTF(), readUTF(), readUTF(), readUTF(), readInt()).also { ref ->
                if (!id.matches(ref.first) || !id.matches(ref.last) ||
                    ref.first > ref.last || !digest.matches(ref.contentDigest) ||
                    !digest.matches(ref.fileDigest) ||
                    ref.count !in 1..NativeSegmentedIdentityPageDomain.PAGE_SIZE)
                    throw IOException("invalid page descriptor")
            }
        }
    }
    private fun readIndex(owner: String, athlete: String): DiskIndex? {
        val path = indexPath(owner, athlete)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        val d = unwrap(readBounded(path, MAX_INDEX_BYTES), MAX_INDEX_BYTES)
        if (d.readInt() != INDEX_MAGIC || d.readInt() != VERSION)
            throw IOException("unknown paged index version")
        val o = d.readUTF()
        val a = d.readUTF()
        val root = d.readUTF()
        val revision = d.readLong()
        val count = d.readLong()
        if (o != owner || a != athlete || !digest.matches(root) ||
            revision !in 1..MAX_REVISION || count !in 0 until MAX_REVISION)
            throw IOException("invalid paged index owner/root")
        val workouts = d.refs(count)
        val receipts = d.refs(count)
        if (d.available() != 0 ||
            workouts.sumOf { it.count.toLong() } != count ||
            receipts.sumOf { it.count.toLong() } != count)
            throw IOException("incomplete index directory")
        return DiskIndex(owner, athlete, root, revision, count, workouts, receipts)
    }
    private fun readVerified(index: DiskIndex): Verified {
        val workouts = index.workouts.map { loadPage(index.owner, index.athlete, 1, it) }
        val receipts = index.receipts.map { loadPage(index.owner, index.athlete, 2, it) }
        val view = NativeSegmentedIdentityPageDirectory(index.owner,
            index.athlete, index.rootDigest, index.sourceRevision,
            index.count, workouts, receipts)
        if (NativeSegmentedIdentityPageDomain.audit(view)
            !is NativeSegmentedIdentityPageDecision.Prepared)
            throw IOException("nonmatching index trees")
        return Verified(view, index)
    }
    private fun currentSource(owner: String, athlete: String):
        Pair<NativeCandidateHistoryRootView, List<KotlinHistoryManifestEntry>>? {
        val root = (candidate.reviewAuthenticatedV2Parity(owner, athlete)
            as? NativeCandidateHistoryRootResult.Reviewed)?.view
            ?: return null
        val current = (v2.open(owner, athlete)
            as? NativeHistoryLedgerResult.Durable)?.ledger
            ?: return null
        if (!current.readyForHistoryReads || current.pending != null ||
            current.ownerKey != owner || current.athleteId != athlete ||
            current.revision != root.head.revision ||
            current.completions.size.toLong() != root.head.completionCount)
            return null
        return root to current.completions
    }
    private fun directory(
        owner: String, athlete: String,
        root: NativeCandidateHistoryRootView,
        entries: List<KotlinHistoryManifestEntry>
    ): NativeSegmentedIdentityPageDirectory? =
        (NativeSegmentedIdentityPageDomain.prepare(owner, athlete,
            root.head.rootDigest, root.head.revision,
            root.head.completionCount, entries)
            as? NativeSegmentedIdentityPageDecision.Prepared)?.directory
    private fun view(d: NativeSegmentedIdentityPageDirectory) =
        NativeCandidatePagedIdentityView(d.sourceRootDigest,
            d.sourceRevision, d.completionCount, d.workoutPages.size,
            d.receiptPages.size)
    private fun matchesSource(
        stored: NativeSegmentedIdentityPageDirectory,
        canonical: List<KotlinHistoryManifestEntry>
    ): Boolean {
        val ordered = stored.workoutPages.flatMap { it.rows }.sortedBy {
            it.completionOrder
        }
        return ordered.size == canonical.size &&
            ordered.withIndex().all { (i, row) ->
                val fact = canonical[i]
                row.completionOrder == fact.completionOrder &&
                    row.workoutId == fact.workoutId &&
                    row.storedReceiptId == fact.storedReceiptId
            }
    }

    /**
     * Source-backed incremental layout: reuses original content-addressed
     * identity pages for saved corrections and projects a single new Finish
     * with the bounded 128-row pure append planner. Always compare the
     * resulting complete identity rows against the independently replayed
     * v2 source; a caller-supplied absence is NEVER a write permission.
     *
     * Rebuilding source parity remains O(lifetime history) in this
     * inactive rehearsal; this only avoids replacing unchanged DISK pages.
     */
    private fun projectedLayout(
        prior: Verified?,
        root: NativeCandidateHistoryRootView,
        canonical: List<KotlinHistoryManifestEntry>,
        full: NativeSegmentedIdentityPageDirectory
    ): NativeSegmentedIdentityPageDirectory {
        val before = prior?.directory ?: return full
        if (before.sourceRevision >= root.head.revision ||
            root.head.revision != before.sourceRevision + 1)
            return full
        val projected: NativeSegmentedIdentityPageDirectory? = when {
            before.completionCount == root.head.completionCount &&
                matchesSource(before, canonical) ->
                before.copy(sourceRootDigest = root.head.rootDigest,
                    sourceRevision = root.head.revision)
            before.completionCount + 1 == root.head.completionCount &&
                canonical.isNotEmpty() &&
                matchesSource(before, canonical.dropLast(1)) ->
                (NativeSegmentedIdentityPageDomain.proposeAppend(
                    before, before.sourceRootDigest, before.sourceRevision,
                    root.head.rootDigest, root.head.revision, canonical.last())
                    as? NativeSegmentedIdentityPageDecision.Prepared)?.directory
            else -> null
        }
        return if (projected != null &&
            NativeSegmentedIdentityPageDomain.audit(projected)
                is NativeSegmentedIdentityPageDecision.Prepared &&
            matchesSource(projected, canonical)) projected else full
    }

    fun build(owner: String, athlete: String):
        NativeCandidatePagedIdentityResult = guarded(owner, athlete) {
        val (root, entries) = currentSource(owner, athlete)
            ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE)
        val desired = directory(owner, athlete, root, entries)
            ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE)
        val previous = try { readIndex(owner, athlete) }
            catch (_: Exception) {
                return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
            }
        val old = if (previous == null) null
            else try { readVerified(previous) } catch (_: Exception) {
                return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
            }
        if (old != null && old.index.sourceRevision >= root.head.revision) {
            if (old.index.sourceRevision == root.head.revision &&
                old.index.rootDigest == root.head.rootDigest &&
                matchesSource(old.directory, entries))
                return@guarded NativeCandidatePagedIdentityResult.Indexed(
                    view(old.directory), true)
            return@guarded reject(NativeCandidatePagedIdentityRefusal.STALE_INDEX)
        }
        val layout = projectedLayout(old, root, entries, desired)
        // Each old immutable page is already checksum-verified by readVerified.
        // Preserve its exact disk digest when the complete typed page digest
        // is unchanged; only affected page(s) need new files.
        val oldWorkouts = old?.index?.workouts?.associateBy { it.contentDigest }
            ?: emptyMap()
        val oldReceipts = old?.index?.receipts?.associateBy { it.contentDigest }
            ?: emptyMap()
        return@guarded try {
            val indexed = DiskIndex(owner, athlete, root.head.rootDigest,
                root.head.revision, root.head.completionCount,
                layout.workoutPages.map { page ->
                    oldWorkouts[page.contentDigest] ?:
                        publishPage(owner, athlete, 1, page)
                },
                layout.receiptPages.map { page ->
                    oldReceipts[page.contentDigest] ?:
                        publishPage(owner, athlete, 2, page)
                })
            atomicWrite(indexPath(owner, athlete), encodeIndex(indexed))
            NativeCandidatePagedIdentityResult.Indexed(view(layout), false)
        } catch (_: Exception) {
            reject(NativeCandidatePagedIdentityRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun audit(owner: String, athlete: String):
        NativeCandidatePagedIdentityResult = guarded(owner, athlete) {
        val (root, entries) = currentSource(owner, athlete)
            ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE)
        val index = try { readIndex(owner, athlete) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
        } ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.NOT_INDEXED)
        if (index.rootDigest != root.head.rootDigest ||
            index.sourceRevision != root.head.revision ||
            index.count != root.head.completionCount)
            return@guarded reject(NativeCandidatePagedIdentityRefusal.STALE_INDEX)
        val stored = try { readVerified(index) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
        }
        if (!matchesSource(stored.directory, entries))
            return@guarded reject(NativeCandidatePagedIdentityRefusal.STALE_INDEX)
        NativeCandidatePagedIdentityResult.Indexed(view(stored.directory), true)
    }

    private fun lookup(
        owner: String, athlete: String, key: String, workout: Boolean
    ): NativeCandidatePagedIdentityResult = guarded(owner, athlete) {
        if (!id.matches(key))
            return@guarded reject(NativeCandidatePagedIdentityRefusal.INVALID_SCOPE)
        val (root, entries) = currentSource(owner, athlete)
            ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.UNVERIFIED_SOURCE)
        val index = try { readIndex(owner, athlete) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
        } ?: return@guarded reject(NativeCandidatePagedIdentityRefusal.NOT_INDEXED)
        if (index.rootDigest != root.head.rootDigest ||
            index.sourceRevision != root.head.revision ||
            index.count != root.head.completionCount)
            return@guarded reject(NativeCandidatePagedIdentityRefusal.STALE_INDEX)
        val stored = try { readVerified(index) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
        }
        if (!matchesSource(stored.directory, entries))
            return@guarded reject(NativeCandidatePagedIdentityRefusal.STALE_INDEX)
        val match = if (workout) NativeSegmentedIdentityPageDomain.findWorkout(
            stored.directory, owner, athlete, index.rootDigest,
            index.sourceRevision, key)
        else NativeSegmentedIdentityPageDomain.findReceipt(
            stored.directory, owner, athlete, index.rootDigest,
            index.sourceRevision, key)
        val row = (match as? NativeSegmentedIdentityPageDecision.Located)?.row
            ?: if (match is NativeSegmentedIdentityPageDecision.Rejected)
                return@guarded reject(NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
            else null
        NativeCandidatePagedIdentityResult.Lookup(view(stored.directory), row)
    }

    /**
     * Fingerprint of the currently replay-verified DISK directory, not a
     * caller-supplied root digest. This is candidate evidence only.
     * Calling the full audit before reading closes stale/missing-page gaps.
     */
    internal fun verifiedDirectoryFileDigest(owner: String, athlete: String):
        String? {
        if (audit(owner, athlete) !is NativeCandidatePagedIdentityResult.Indexed)
            return null
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete)) return@withOwnerTransaction null
                hash(readBounded(indexPath(owner, athlete), MAX_INDEX_BYTES))
            }
        }.getOrNull()
    }

    fun findWorkout(owner: String, athlete: String, workoutId: String) =
        lookup(owner, athlete, workoutId, true)

    fun findReceipt(owner: String, athlete: String, receiptId: String) =
        lookup(owner, athlete, receiptId, false)
}
