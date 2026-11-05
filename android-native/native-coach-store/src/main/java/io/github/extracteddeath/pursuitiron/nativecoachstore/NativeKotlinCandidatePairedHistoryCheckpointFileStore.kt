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
 * An INACTIVE durable pairing checkpoint for two separately published
 * candidate artifacts: segmented root and paged workout/receipt directory.
 *
 * Its own atomic swap is the *only candidate checkpoint acknowledgement*.
 * It requires fully authenticated v2/shadow/journal parity of the root,
 * full paged-index parity, AND exact root/revision/count and on-disk directory
 * digest. A crash after only one artifact changes leaves the old checkpoint
 * stale, never a silently mixed history. It does NOT make the two artifacts
 * one atomic writer or authorize Last, PR, Finish, corrections or progression.
 *
 * The root/index still have separate publication points; a future WAL owner
 * must commit their shared intent and reconcile torn changes BEFORE this
 * checkpoint could be considered as any kind of production cutover signal.
 */
internal data class NativeCandidatePairedHistoryCheckpoint(
    val ownerKey: String,
    val athleteId: String,
    val sourceRevision: Long,
    val completionCount: Long,
    val rootDigest: String,
    val pagedDirectoryFileDigest: String
) {
    val canAuthorizeHistoryReads: Boolean get() = false
    val canAuthorizeWrites: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}
/**
 * Physical presence only, never proof the stored pair is current, readable,
 * or authoritative. Used by the candidate recovery seam to avoid silently
 * replacing a lost index that an earlier committed checkpoint referenced.
 */
internal enum class NativeCandidateCheckpointPresence {
    ABSENT, PRESENT, INVALID
}
internal enum class NativeCandidatePairedHistoryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, UNVERIFIED_SOURCE,
    NOT_CHECKPOINTED, STALE_CHECKPOINT, CORRUPT_CHECKPOINT,
    STORAGE_UNAVAILABLE
}
internal sealed interface NativeCandidatePairedHistoryResult {
    data class Verified(
        val checkpoint: NativeCandidatePairedHistoryCheckpoint,
        val replayed: Boolean
    ) : NativeCandidatePairedHistoryResult
    data class Rejected(val reason: NativeCandidatePairedHistoryRefusal) :
        NativeCandidatePairedHistoryResult
}
internal class NativeKotlinCandidatePairedHistoryCheckpointFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val root: NativeKotlinCandidateSegmentedRootFileStore,
    private val index: NativeKotlinCandidatePagedIdentityFileStore,
    private val beforeCheckpointPublicationForTest: (() -> Unit)? = null,
    private val afterCheckpointPublicationForTest: (() -> Unit)? = null
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    private companion object {
        const val MAGIC = 0x50494350 // PICP
        const val VERSION = 1
        const val MAX_BYTES = 2048
        const val MAX_REVISION = 9_007_199_254_740_991L
    }
    private fun reject(r: NativeCandidatePairedHistoryRefusal) =
        NativeCandidatePairedHistoryResult.Rejected(r)
    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun checkpointPath(owner: String, athlete: String) =
        directory.resolve("candidate-paired-history-" +
            hex(hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))) +
            ".cseal")
    private fun guarded(owner: String, athlete: String,
        operation: () -> NativeCandidatePairedHistoryResult
    ): NativeCandidatePairedHistoryResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return reject(NativeCandidatePairedHistoryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    reject(NativeCandidatePairedHistoryRefusal.NOT_AUTHORIZED)
                else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe paired checkpoint directory")
                    operation()
                }
            }
        }.getOrElse {
            reject(NativeCandidatePairedHistoryRefusal.STORAGE_UNAVAILABLE)
        }
    }
    private fun encode(c: NativeCandidatePairedHistoryCheckpoint): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSION)
            d.writeUTF(c.ownerKey); d.writeUTF(c.athleteId)
            d.writeLong(c.sourceRevision); d.writeLong(c.completionCount)
            d.writeUTF(c.rootDigest); d.writeUTF(c.pagedDirectoryFileDigest)
        }
        val bytes = out.toByteArray()
        if (bytes.size > MAX_BYTES - 36) throw IOException("large checkpoint")
        val frame = ByteArrayOutputStream()
        DataOutputStream(frame).use { d ->
            d.writeInt(bytes.size); d.write(bytes); d.write(hash(bytes))
        }
        return frame.toByteArray()
    }
    private fun decode(bytes: ByteArray, owner: String, athlete: String):
        NativeCandidatePairedHistoryCheckpoint {
        if (bytes.size !in 40..MAX_BYTES)
            throw IOException("bad checkpoint frame size")
        val frame = DataInputStream(ByteArrayInputStream(bytes))
        val size = frame.readInt()
        if (size < 8 || size + 36 != bytes.size)
            throw IOException("bad checkpoint length")
        val body = ByteArray(size)
        frame.readFully(body)
        val recorded = ByteArray(32)
        frame.readFully(recorded)
        if (!MessageDigest.isEqual(hash(body), recorded))
            throw IOException("checkpoint checksum failed")
        val d = DataInputStream(ByteArrayInputStream(body))
        if (d.readInt() != MAGIC || d.readInt() != VERSION)
            throw IOException("unknown checkpoint schema")
        val c = NativeCandidatePairedHistoryCheckpoint(
            d.readUTF(), d.readUTF(), d.readLong(), d.readLong(),
            d.readUTF(), d.readUTF())
        if (d.available() != 0 ||
            c.ownerKey != owner || c.athleteId != athlete ||
            c.sourceRevision !in 1..MAX_REVISION ||
            c.completionCount !in 0 until MAX_REVISION ||
            !digest.matches(c.rootDigest) ||
            !digest.matches(c.pagedDirectoryFileDigest))
            throw IOException("invalid checkpoint scope or revision")
        return c
    }
    private fun read(owner: String, athlete: String):
        NativeCandidatePairedHistoryCheckpoint? {
        val path = checkpointPath(owner, athlete)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(path) > MAX_BYTES.toLong())
            throw IOException("unsafe checkpoint file")
        return decode(Files.readAllBytes(path), owner, athlete)
    }
    private fun atomicWrite(path: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(directory, "candidate-checkpoint-", ".pending")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun current(owner: String, athlete: String):
        NativeCandidatePairedHistoryCheckpoint? {
        val root = (root.reviewAuthenticatedV2Parity(owner, athlete)
            as? NativeCandidateHistoryRootResult.Reviewed)?.view
            ?: return null
        val indexView = (index.audit(owner, athlete)
            as? NativeCandidatePagedIdentityResult.Indexed)?.view
            ?: return null
        if (root.head.ownerKey != owner || root.head.athleteId != athlete ||
            root.head.rootDigest != indexView.rootDigest ||
            root.head.revision != indexView.sourceRevision ||
            root.head.completionCount != indexView.completionCount)
            return null
        val fileDigest = index.verifiedDirectoryFileDigest(owner, athlete)
            ?: return null
        return NativeCandidatePairedHistoryCheckpoint(
            owner, athlete, root.head.revision, root.head.completionCount,
            root.head.rootDigest, fileDigest)
    }

    /**
     * Only checks whether a checksum-valid checkpoint exists, not source
     * freshness. INVALID includes revoked identity, unsafe files and tamper.
     * A recovery caller MUST separately perform full replay parity.
     */
    internal fun presence(owner: String, athlete: String):
        NativeCandidateCheckpointPresence {
        if (!id.matches(owner) || !id.matches(athlete))
            return NativeCandidateCheckpointPresence.INVALID
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    NativeCandidateCheckpointPresence.INVALID
                else if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) &&
                    (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)))
                    NativeCandidateCheckpointPresence.INVALID
                else if (read(owner, athlete) == null)
                    NativeCandidateCheckpointPresence.ABSENT
                else NativeCandidateCheckpointPresence.PRESENT
            }
        }.getOrElse { NativeCandidateCheckpointPresence.INVALID }
    }

    /** Seal only a fully proven pair; an old stale seal may be superseded. */
    fun checkpoint(owner: String, athlete: String):
        NativeCandidatePairedHistoryResult = guarded(owner, athlete) {
        val previous = try { read(owner, athlete) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePairedHistoryRefusal.CORRUPT_CHECKPOINT)
        }
        val verified = current(owner, athlete)
            ?: return@guarded reject(NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE)
        if (previous == verified)
            return@guarded NativeCandidatePairedHistoryResult.Verified(
                verified, true)
        if (previous != null &&
            (previous.sourceRevision >= verified.sourceRevision ||
                previous.completionCount > verified.completionCount))
            return@guarded reject(NativeCandidatePairedHistoryRefusal.STALE_CHECKPOINT)
        return@guarded try {
            beforeCheckpointPublicationForTest?.invoke()
            atomicWrite(checkpointPath(owner, athlete), encode(verified))
            afterCheckpointPublicationForTest?.invoke()
            NativeCandidatePairedHistoryResult.Verified(verified, false)
        } catch (_: Exception) {
            reject(NativeCandidatePairedHistoryRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** A valid OLD seal is stale after either artifact changes. */
    fun review(owner: String, athlete: String):
        NativeCandidatePairedHistoryResult = guarded(owner, athlete) {
        val saved = try { read(owner, athlete) } catch (_: Exception) {
            return@guarded reject(NativeCandidatePairedHistoryRefusal.CORRUPT_CHECKPOINT)
        } ?: return@guarded reject(NativeCandidatePairedHistoryRefusal.NOT_CHECKPOINTED)
        val now = current(owner, athlete)
            ?: return@guarded reject(NativeCandidatePairedHistoryRefusal.UNVERIFIED_SOURCE)
        if (saved != now)
            return@guarded reject(NativeCandidatePairedHistoryRefusal.STALE_CHECKPOINT)
        NativeCandidatePairedHistoryResult.Verified(saved, true)
    }
}
