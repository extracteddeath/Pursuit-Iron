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
 * INACTIVE candidate-only recovery intent for the post-v2-commit mirror.
 *
 * A PREPARED intent pins a replay-verified SOURCE root revision to exactly
 * one athlete before the paged index is refreshed. ACKNOWLEDGED binds the
 * exact final persisted root+index checkpoint, so a lost acknowledgment
 * can be retried without accepting a changed root or a missing old seal.
 *
 * This is NOT a production history WAL. It cannot Finish/correct a journal,
 * publish a segmented root, authorize new workout IDs, or transfer authority.
 * The caller MUST independently prove the target against current v2 and
 * full generated/custom journal+correction replay under the same owner fence.
 */
internal enum class NativeCandidateRecoveryIntentPhase { PREPARED, ACKNOWLEDGED }
internal data class NativeCandidateRecoveryIntent(
    val ownerKey: String, val athleteId: String,
    val sourceRevision: Long, val completionCount: Long,
    val rootDigest: String, val phase: NativeCandidateRecoveryIntentPhase,
    val acknowledgedDirectoryDigest: String?
)
internal enum class NativeCandidateRecoveryIntentRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_PREPARED, PENDING_CONFLICT,
    STALE_SOURCE, LOST_ACKNOWLEDGED_SEAL, CORRUPT_INTENT,
    UNVERIFIED_CHECKPOINT, STORAGE_UNAVAILABLE
}
internal sealed interface NativeCandidateRecoveryIntentResult {
    data class Durable(
        val intent: NativeCandidateRecoveryIntent,
        val replayed: Boolean
    ) : NativeCandidateRecoveryIntentResult {
        val canAuthorizeHistoryWrites: Boolean get() = false
        val canIssueProgression: Boolean get() = false
    }
    data class Rejected(val reason: NativeCandidateRecoveryIntentRefusal) :
        NativeCandidateRecoveryIntentResult
}

internal class NativeKotlinCandidateMirrorRecoveryIntentFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val checkpoint: NativeKotlinCandidatePairedHistoryCheckpointFileStore
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    private companion object {
        const val MAGIC = 0x50495249 // PIRI
        const val VERSION = 1
        const val MAX_BYTES = 2048
        const val MAX_REVISION = 9_007_199_254_740_991L
    }
    private fun deny(r: NativeCandidateRecoveryIntentRefusal) =
        NativeCandidateRecoveryIntentResult.Rejected(r)
    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun path(owner: String, athlete: String) =
        directory.resolve("candidate-recovery-intent-" +
            hex(hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))) +
            ".rintent")
    private fun valid(intent: NativeCandidateRecoveryIntent) =
        id.matches(intent.ownerKey) && id.matches(intent.athleteId) &&
            digest.matches(intent.rootDigest) &&
            intent.sourceRevision in 1..MAX_REVISION &&
            intent.completionCount in 0 until MAX_REVISION &&
            (intent.phase == NativeCandidateRecoveryIntentPhase.PREPARED &&
                intent.acknowledgedDirectoryDigest == null ||
                intent.phase == NativeCandidateRecoveryIntentPhase.ACKNOWLEDGED &&
                intent.acknowledgedDirectoryDigest != null &&
                digest.matches(intent.acknowledgedDirectoryDigest))
    private fun guarded(
        owner: String, athlete: String,
        action: () -> NativeCandidateRecoveryIntentResult
    ): NativeCandidateRecoveryIntentResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeCandidateRecoveryIntentRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    deny(NativeCandidateRecoveryIntentRefusal.NOT_AUTHORIZED)
                else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe intent directory")
                    action()
                }
            }
        }.getOrElse { deny(NativeCandidateRecoveryIntentRefusal.STORAGE_UNAVAILABLE) }
    }
    private fun encode(record: NativeCandidateRecoveryIntent): ByteArray {
        if (!valid(record)) throw IOException("invalid recovery intent")
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSION)
            d.writeUTF(record.ownerKey); d.writeUTF(record.athleteId)
            d.writeLong(record.sourceRevision); d.writeLong(record.completionCount)
            d.writeUTF(record.rootDigest); d.writeUTF(record.phase.name)
            d.writeBoolean(record.acknowledgedDirectoryDigest != null)
            record.acknowledgedDirectoryDigest?.let { d.writeUTF(it) }
        }
        val bytes = out.toByteArray()
        if (bytes.size > MAX_BYTES - 36) throw IOException("oversized intent")
        val frame = ByteArrayOutputStream()
        DataOutputStream(frame).use { d ->
            d.writeInt(bytes.size); d.write(bytes); d.write(hash(bytes))
        }
        return frame.toByteArray()
    }
    private fun decode(bytes: ByteArray, owner: String, athlete: String):
        NativeCandidateRecoveryIntent {
        if (bytes.size !in 40..MAX_BYTES) throw IOException("bad intent size")
        val d = DataInputStream(ByteArrayInputStream(bytes))
        val size = d.readInt()
        if (size < 8 || size + 36 != bytes.size)
            throw IOException("bad intent framing")
        val body = ByteArray(size)
        d.readFully(body)
        val check = ByteArray(32)
        d.readFully(check)
        if (!MessageDigest.isEqual(hash(body), check))
            throw IOException("intent digest mismatch")
        val input = DataInputStream(ByteArrayInputStream(body))
        if (input.readInt() != MAGIC || input.readInt() != VERSION)
            throw IOException("invalid intent format")
        val record = NativeCandidateRecoveryIntent(
            input.readUTF(), input.readUTF(), input.readLong(), input.readLong(),
            input.readUTF(), NativeCandidateRecoveryIntentPhase.valueOf(input.readUTF()),
            if (input.readBoolean()) input.readUTF() else null)
        if (input.available() != 0 || !valid(record) ||
            record.ownerKey != owner || record.athleteId != athlete)
            throw IOException("invalid intent scope/phase")
        return record
    }
    private fun fromDisk(owner: String, athlete: String):
        NativeCandidateRecoveryIntent? {
        val file = path(owner, athlete)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(file) ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(file) > MAX_BYTES.toLong())
            throw IOException("unsafe recovery intent")
        return decode(Files.readAllBytes(file), owner, athlete)
    }
    private fun atomicWrite(file: Path, record: NativeCandidateRecoveryIntent) {
        val bytes = encode(record)
        val temporary = Files.createTempFile(directory, "candidate-intent-", ".pending")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
    fun read(owner: String, athlete: String): NativeCandidateRecoveryIntentResult =
        guarded(owner, athlete) {
            val saved = try { fromDisk(owner, athlete) } catch (_: Exception) {
                return@guarded deny(NativeCandidateRecoveryIntentRefusal.CORRUPT_INTENT)
            } ?: return@guarded deny(NativeCandidateRecoveryIntentRefusal.NOT_PREPARED)
            NativeCandidateRecoveryIntentResult.Durable(saved, true)
        }

    /** Caller-provided target must first pass the independent replay gate. */
    fun prepare(
        owner: String, athlete: String,
        revision: Long, count: Long, rootDigest: String
    ): NativeCandidateRecoveryIntentResult = guarded(owner, athlete) {
        val next = NativeCandidateRecoveryIntent(owner, athlete,
            revision, count, rootDigest,
            NativeCandidateRecoveryIntentPhase.PREPARED, null)
        if (!valid(next))
            return@guarded deny(NativeCandidateRecoveryIntentRefusal.INVALID_SCOPE)
        val existing = try { fromDisk(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateRecoveryIntentRefusal.CORRUPT_INTENT)
        }
        if (existing != null) {
            if (existing.sourceRevision == revision &&
                existing.completionCount == count &&
                existing.rootDigest == rootDigest)
                return@guarded NativeCandidateRecoveryIntentResult.Durable(existing, true)
            if (existing.phase != NativeCandidateRecoveryIntentPhase.ACKNOWLEDGED)
                return@guarded deny(
                    NativeCandidateRecoveryIntentRefusal.PENDING_CONFLICT)
            if (revision <= existing.sourceRevision ||
                count < existing.completionCount ||
                rootDigest == existing.rootDigest)
                return@guarded deny(
                    NativeCandidateRecoveryIntentRefusal.STALE_SOURCE)
            // A previously acknowledged seal cannot disappear or change
            // while a new root is being enrolled.
            val checkpointOnDisk = checkpoint.savedCheckpoint(owner, athlete)
            val prior = (checkpointOnDisk as?
                NativeCandidateSavedCheckpoint.Present)?.value
                ?: return@guarded deny(
                    NativeCandidateRecoveryIntentRefusal.LOST_ACKNOWLEDGED_SEAL)
            if (prior.ownerKey != owner || prior.athleteId != athlete ||
                prior.sourceRevision != existing.sourceRevision ||
                prior.completionCount != existing.completionCount ||
                prior.rootDigest != existing.rootDigest ||
                prior.pagedDirectoryFileDigest !=
                    existing.acknowledgedDirectoryDigest)
                return@guarded deny(
                    NativeCandidateRecoveryIntentRefusal.LOST_ACKNOWLEDGED_SEAL)
        }
        return@guarded try {
            atomicWrite(path(owner, athlete), next)
            NativeCandidateRecoveryIntentResult.Durable(next, false)
        } catch (_: Exception) {
            deny(NativeCandidateRecoveryIntentRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** Full source+root+index checkpoint parity remains mandatory. */
    fun acknowledge(
        owner: String, athlete: String,
        verified: NativeCandidatePairedHistoryCheckpoint
    ): NativeCandidateRecoveryIntentResult = guarded(owner, athlete) {
        val old = try { fromDisk(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateRecoveryIntentRefusal.CORRUPT_INTENT)
        } ?: return@guarded deny(NativeCandidateRecoveryIntentRefusal.NOT_PREPARED)
        if (old.sourceRevision != verified.sourceRevision ||
            old.completionCount != verified.completionCount ||
            old.rootDigest != verified.rootDigest ||
            old.ownerKey != verified.ownerKey || old.athleteId != verified.athleteId)
            return@guarded deny(
                NativeCandidateRecoveryIntentRefusal.PENDING_CONFLICT)
        val certified = (checkpoint.review(owner, athlete)
            as? NativeCandidatePairedHistoryResult.Verified)?.checkpoint
            ?: return@guarded deny(
                NativeCandidateRecoveryIntentRefusal.UNVERIFIED_CHECKPOINT)
        if (certified != verified)
            return@guarded deny(
                NativeCandidateRecoveryIntentRefusal.UNVERIFIED_CHECKPOINT)
        val updated = old.copy(
            phase = NativeCandidateRecoveryIntentPhase.ACKNOWLEDGED,
            acknowledgedDirectoryDigest = verified.pagedDirectoryFileDigest)
        if (old == updated)
            return@guarded NativeCandidateRecoveryIntentResult.Durable(old, true)
        return@guarded try {
            atomicWrite(path(owner, athlete), updated)
            NativeCandidateRecoveryIntentResult.Durable(updated, false)
        } catch (_: Exception) {
            deny(NativeCandidateRecoveryIntentRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
