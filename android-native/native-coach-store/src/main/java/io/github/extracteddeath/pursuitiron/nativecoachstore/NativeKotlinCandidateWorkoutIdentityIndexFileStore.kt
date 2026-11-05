package io.github.extracteddeath.pursuitiron.nativecoachstore

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
 * Non-authoritative workout/receipt identity index for an ISOLATED candidate
 * segmented root. Only a completely replay-verified v2/candidate parity
 * snapshot can build it. The root digest and revision bind its contents to
 * exactly one candidate snapshot. A stale or malformed index cannot authorize
 * a new Finish, correction, history read, PR or progression decision.
 *
 * This first bounded format is a migration rehearsal, NOT a production
 * scalable unique index. Each lookup deliberately replays full source parity;
 * no O(1) lifetime-history or independent ownership guarantee is claimed.
 */
internal data class NativeCandidateIndexedWorkout(
    val workoutId: String,
    val storedReceiptId: String,
    val completionOrder: Long
)

internal data class NativeCandidateIdentityIndexView(
    val rootDigest: String,
    val sourceRevision: Long,
    val indexedCompletions: Int
) {
    val canAuthorizeWrites: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}

internal enum class NativeCandidateIdentityIndexRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, UNVERIFIED_SOURCE, NOT_INDEXED,
    STALE_INDEX, CORRUPT_INDEX, STORAGE_UNAVAILABLE
}

internal sealed interface NativeCandidateIdentityIndexResult {
    data class Indexed(
        val view: NativeCandidateIdentityIndexView,
        val replayed: Boolean
    ) : NativeCandidateIdentityIndexResult
    data class Lookup(
        val view: NativeCandidateIdentityIndexView,
        val match: NativeCandidateIndexedWorkout?
    ) : NativeCandidateIdentityIndexResult {
        // A null match is not proof usable by a production single writer.
        val canAuthorizeNewWorkout: Boolean get() = false
    }
    data class Rejected(val reason: NativeCandidateIdentityIndexRefusal) :
        NativeCandidateIdentityIndexResult
}

internal class NativeKotlinCandidateWorkoutIdentityIndexFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val v2: NativeKotlinHistoryManifestFileStore,
    private val candidate: NativeKotlinCandidateSegmentedRootFileStore
) {
    private data class Snapshot(
        val owner: String,
        val athlete: String,
        val view: NativeCandidateIdentityIndexView,
        val rows: List<NativeCandidateIndexedWorkout>
    )
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")

    companion object {
        private const val MAGIC = 0x50494958 // PIIX (candidate index only)
        private const val VERSION = 1
        // Bounds v2's at-most-4,096-entry migration source, not all future
        // segmented history. Production needs paged / content-addressed IDs.
        private const val MAX_ENTRIES = 4096
        private const val MAX_BYTES = 2_097_152
    }

    private fun deny(reason: NativeCandidateIdentityIndexRefusal) =
        NativeCandidateIdentityIndexResult.Rejected(reason)

    private fun authorized(owner: String, athlete: String) = runCatching {
        identity.signedInIdentity() == NativeKotlinOwnerIdentity(owner, athlete)
    }.getOrDefault(false)

    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun indexPath(owner: String, athlete: String) = directory.resolve(
        "candidate-identity-" +
            hex(hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))) +
            ".idx"
    )

    private fun guarded(owner: String, athlete: String,
        operation: () -> NativeCandidateIdentityIndexResult
    ): NativeCandidateIdentityIndexResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeCandidateIdentityIndexRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    deny(NativeCandidateIdentityIndexRefusal.NOT_AUTHORIZED)
                else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe candidate directory")
                    operation()
                }
            }
        }.getOrElse {
            deny(NativeCandidateIdentityIndexRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private fun view(root: NativeCandidateHistoryRootView) =
        NativeCandidateIdentityIndexView(
            root.head.rootDigest, root.head.revision,
            root.head.completionCount.toInt()
        )

    private fun verifiedRoot(owner: String, athlete: String):
        NativeCandidateHistoryRootView? =
        (candidate.reviewAuthenticatedV2Parity(owner, athlete)
            as? NativeCandidateHistoryRootResult.Reviewed)?.view

    private fun encode(snapshot: Snapshot): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSION)
            d.writeUTF(snapshot.owner); d.writeUTF(snapshot.athlete)
            d.writeLong(snapshot.view.sourceRevision)
            d.writeUTF(snapshot.view.rootDigest)
            d.writeInt(snapshot.rows.size)
            snapshot.rows.forEach { row ->
                d.writeUTF(row.workoutId)
                d.writeUTF(row.storedReceiptId)
                d.writeLong(row.completionOrder)
            }
        }
        val bytes = body.toByteArray()
        if (bytes.size > MAX_BYTES - 36) throw IOException("large index")
        val framed = ByteArrayOutputStream()
        DataOutputStream(framed).use {
            it.writeInt(bytes.size); it.write(bytes); it.write(hash(bytes))
        }
        return framed.toByteArray()
    }

    private fun decode(bytes: ByteArray, owner: String, athlete: String): Snapshot {
        if (bytes.size !in 40..MAX_BYTES) throw IOException("bad index bounds")
        val frame = DataInputStream(ByteArrayInputStream(bytes))
        val count = frame.readInt()
        if (count < 8 || count + 36 != bytes.size)
            throw IOException("bad index frame")
        val body = ByteArray(count)
        frame.readFully(body)
        val signature = ByteArray(32)
        frame.readFully(signature)
        if (!MessageDigest.isEqual(hash(body), signature))
            throw IOException("index checksum mismatch")
        val d = DataInputStream(ByteArrayInputStream(body))
        if (d.readInt() != MAGIC || d.readInt() != VERSION ||
            d.readUTF() != owner || d.readUTF() != athlete)
            throw IOException("index scope or version mismatch")
        val revision = d.readLong()
        val rootDigest = d.readUTF()
        val n = d.readInt()
        if (revision !in 1..9_007_199_254_740_991L ||
            !digest.matches(rootDigest) || n !in 0..MAX_ENTRIES)
            throw IOException("invalid index metadata")
        val rows = (0 until n).map {
            NativeCandidateIndexedWorkout(d.readUTF(), d.readUTF(), d.readLong())
        }
        if (d.available() != 0 ||
            rows.any {
                !id.matches(it.workoutId) ||
                !id.matches(it.storedReceiptId) ||
                it.completionOrder !in 1..n.toLong()
            } ||
            rows.map { it.workoutId } != rows.map { it.workoutId }.sorted() ||
            rows.map { it.workoutId }.distinct().size != n ||
            rows.map { it.storedReceiptId }.distinct().size != n ||
            rows.map { it.completionOrder }.distinct().size != n)
            throw IOException("nonunique or unsorted index")
        return Snapshot(owner, athlete,
            NativeCandidateIdentityIndexView(rootDigest, revision, n), rows)
    }

    private fun read(owner: String, athlete: String): Snapshot? {
        val path = indexPath(owner, athlete)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(path) > MAX_BYTES.toLong())
            throw IOException("unsafe index file")
        return decode(Files.readAllBytes(path), owner, athlete)
    }

    private fun write(owner: String, athlete: String, bytes: ByteArray) {
        val temp = Files.createTempFile(directory, "candidate-identity-", ".pending")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, indexPath(owner, athlete),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** Publish an auxiliary index from a COMPLETE authenticated history. */
    fun build(owner: String, athlete: String): NativeCandidateIdentityIndexResult =
        guarded(owner, athlete) {
            val root = verifiedRoot(owner, athlete)
                ?: return@guarded deny(
                    NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE)
            val canonical = (v2.open(owner, athlete)
                as? NativeHistoryLedgerResult.Durable)?.ledger
                ?: return@guarded deny(
                    NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE)
            if (!canonical.readyForHistoryReads ||
                canonical.ownerKey != owner || canonical.athleteId != athlete ||
                canonical.revision != root.head.revision ||
                canonical.completions.size.toLong() != root.head.completionCount ||
                canonical.completions.size > MAX_ENTRIES)
                return@guarded deny(
                    NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE)
            val rows = canonical.completions.map {
                NativeCandidateIndexedWorkout(
                    it.workoutId, it.storedReceiptId, it.completionOrder)
            }.sortedBy { it.workoutId }
            if (rows.map { it.workoutId }.distinct().size != rows.size ||
                rows.map { it.storedReceiptId }.distinct().size != rows.size)
                return@guarded deny(
                    NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE)
            val desired = Snapshot(owner, athlete, view(root), rows)
            val existing = try { read(owner, athlete) } catch (_: Exception) {
                return@guarded deny(NativeCandidateIdentityIndexRefusal.CORRUPT_INDEX)
            }
            if (existing == desired)
                return@guarded NativeCandidateIdentityIndexResult.Indexed(
                    desired.view, true)
            // Never rewrite a root-different index at the SAME or older
            // revision: that is conflicting history, not a routine refresh.
            if (existing != null &&
                existing.view.sourceRevision >= desired.view.sourceRevision)
                return@guarded deny(NativeCandidateIdentityIndexRefusal.STALE_INDEX)
            try { write(owner, athlete, encode(desired)) } catch (_: Exception) {
                return@guarded deny(
                    NativeCandidateIdentityIndexRefusal.STORAGE_UNAVAILABLE)
            }
            NativeCandidateIdentityIndexResult.Indexed(desired.view, false)
        }

    private fun lookup(owner: String, athlete: String, key: String,
        receipt: Boolean
    ): NativeCandidateIdentityIndexResult = guarded(owner, athlete) {
        if (!id.matches(key))
            return@guarded deny(NativeCandidateIdentityIndexRefusal.INVALID_SCOPE)
        val root = verifiedRoot(owner, athlete)
            ?: return@guarded deny(
                NativeCandidateIdentityIndexRefusal.UNVERIFIED_SOURCE)
        val snapshot = try { read(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateIdentityIndexRefusal.CORRUPT_INDEX)
        } ?: return@guarded deny(NativeCandidateIdentityIndexRefusal.NOT_INDEXED)
        if (snapshot.view != view(root))
            return@guarded deny(NativeCandidateIdentityIndexRefusal.STALE_INDEX)
        val match = if (receipt) snapshot.rows.firstOrNull {
            it.storedReceiptId == key
        } else snapshot.rows.binarySearchBy(key) { it.workoutId }.let {
            if (it >= 0) snapshot.rows[it] else null
        }
        NativeCandidateIdentityIndexResult.Lookup(snapshot.view, match)
    }

    fun findWorkout(owner: String, athlete: String, workoutId: String) =
        lookup(owner, athlete, workoutId, false)

    fun findReceipt(owner: String, athlete: String, receiptId: String) =
        lookup(owner, athlete, receiptId, true)
}
