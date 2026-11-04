package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * A separate, incomplete, athlete-authored program is NOT an approval.
 * Never permit this draft store to issue workouts or mutate approved targets.
 */
enum class NativeCustomDraftRefusal {
    NOT_AUTHORIZED, INVALID_DRAFT, STALE_REVISION, CORRUPT_RECORD,
    STORAGE_UNAVAILABLE
}
sealed interface NativeCustomDraftReadResult {
    data class Found(val draft: CustomTrainingCycle) : NativeCustomDraftReadResult
    data object Missing : NativeCustomDraftReadResult
    data class Deleted(val revision: Long) : NativeCustomDraftReadResult
    data class Rejected(val reason: NativeCustomDraftRefusal) : NativeCustomDraftReadResult
}
sealed interface NativeCustomDraftWriteResult {
    data class Committed(val revision: Long) : NativeCustomDraftWriteResult
    data class Rejected(val reason: NativeCustomDraftRefusal) : NativeCustomDraftWriteResult
}
private data class CustomDraftRecord(
    val ownerKey: String,
    val athleteId: String,
    val cycleId: String,
    val revision: Long,
    val draft: CustomTrainingCycle?
)

/**
 * Native-owned, checksum-checked, revision-CAS draft persistence.
 *
 * This experimental repository shares the SAME OS lock as accepted-program
 * revisions and Kotlin issuance, but it is not wired into Compose yet.
 * A valid saved draft is NEVER accepted as a coaching-approved program.
 *
 * Atomic file replacement preserves the old record until the new one is
 * fully written; a tombstone retains the revision so a stale autosave cannot
 * resurrect a deleted program. Explicit authorization for the actual device
 * owner is supplied by NativeKotlinCurrentIdentity.
 */
class NativeCustomCycleDraftFileStore(
    private val privateDirectory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic = 0x50494346 // PICF
    private val version = 1
    private val maxFileBytes = NativeCustomCycleDraftCodec.MAX_BYTES + 4_096

    private fun active(ownerKey: String): NativeKotlinOwnerIdentity? {
        if (!id.matches(ownerKey)) return null
        val current = runCatching { identity.signedInIdentity() }.getOrNull()
            ?: return null
        return current.takeIf {
            it.ownerKey == ownerKey && id.matches(it.athleteId)
        }
    }

    private fun path(ownerKey: String, cycleId: String): Path {
        val source = (ownerKey + "\u0000" + cycleId).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(source)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return privateDirectory.resolve("draft-$hash.bin")
    }

    private fun encode(record: CustomDraftRecord): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { output ->
            output.writeInt(magic)
            output.writeInt(version)
            output.writeUTF(record.ownerKey)
            output.writeUTF(record.athleteId)
            output.writeUTF(record.cycleId)
            output.writeLong(record.revision)
            output.writeBoolean(record.draft != null)
            record.draft?.let { draft ->
                val bytes = NativeCustomCycleDraftCodec.encode(draft)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
        }
        val bytes = payload.toByteArray()
        require(bytes.size + 36 <= maxFileBytes)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(bytes.size)
                out.write(bytes)
                out.write(sha)
            }
        }.toByteArray()
    }

    private fun decode(file: Path): CustomDraftRecord? {
        if (!Files.exists(file)) return null
        if (Files.isSymbolicLink(file)) throw IOException("Linked draft path")
        val length = Files.size(file)
        if (length !in 44L..maxFileBytes.toLong())
            throw IOException("Invalid draft file size")
        val bytes = Files.readAllBytes(file)
        val stream = DataInputStream(ByteArrayInputStream(bytes))
        val payloadLength = stream.readInt()
        if (payloadLength !in 8..(maxFileBytes - 36) ||
            payloadLength + 36 != bytes.size)
            throw IOException("Invalid draft envelope")
        val payload = ByteArray(payloadLength)
        stream.readFully(payload)
        val checksum = ByteArray(32)
        stream.readFully(checksum)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        if (!MessageDigest.isEqual(checksum, actual))
            throw IOException("Custom draft checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                if (input.readInt() != magic || input.readInt() != version)
                    throw IOException("Unknown custom draft file format")
                val owner = input.readUTF()
                val athlete = input.readUTF()
                val cycleId = input.readUTF()
                val revision = input.readLong()
                if (!id.matches(owner) || !id.matches(athlete) ||
                    !id.matches(cycleId) ||
                    revision !in 1..9_007_199_254_740_991L)
                    throw IOException("Invalid draft owner or revision")
                val draft = if (input.readBoolean()) {
                    val size = input.readInt()
                    if (size !in 16..NativeCustomCycleDraftCodec.MAX_BYTES ||
                        size > input.available())
                        throw IOException("Invalid custom draft payload length")
                    val data = ByteArray(size)
                    input.readFully(data)
                    NativeCustomCycleDraftCodec.decode(data)
                } else null
                if (input.available() != 0 || (draft != null &&
                    (draft.athleteId != athlete || draft.cycleId != cycleId ||
                        draft.revision != revision)))
                    throw IOException("Conflicting custom draft metadata")
                CustomDraftRecord(owner, athlete, cycleId, revision, draft)
            }
        } catch (error: Exception) {
            throw IOException("Invalid persisted custom draft", error)
        }
    }

    private fun atomicWrite(file: Path, bytes: ByteArray) {
        Files.createDirectories(privateDirectory)
        if (Files.isSymbolicLink(privateDirectory))
            throw IOException("Linked private directory")
        val pending = Files.createTempFile(privateDirectory, "draft-", ".pending")
        try {
            FileChannel.open(pending, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val data = ByteBuffer.wrap(bytes)
                while (data.hasRemaining()) channel.write(data)
                channel.force(true)
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IOException("Draft storage requires atomic rename", error)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    fun read(ownerKey: String, cycleId: String): NativeCustomDraftReadResult {
        if (!id.matches(cycleId) || active(ownerKey) == null)
            return NativeCustomDraftReadResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val owner = active(ownerKey)
                    ?: return@withOwnerTransaction NativeCustomDraftReadResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                val record = try { decode(path(ownerKey, cycleId)) }
                    catch (_: Exception) {
                        return@withOwnerTransaction NativeCustomDraftReadResult.Rejected(
                            NativeCustomDraftRefusal.CORRUPT_RECORD)
                    }
                if (active(ownerKey) != owner)
                    NativeCustomDraftReadResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                else if (record == null)
                    NativeCustomDraftReadResult.Missing
                else if (record.ownerKey != ownerKey ||
                    record.athleteId != owner.athleteId ||
                    record.cycleId != cycleId)
                    NativeCustomDraftReadResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                else if (record.draft == null)
                    NativeCustomDraftReadResult.Deleted(record.revision)
                else NativeCustomDraftReadResult.Found(record.draft)
            }
        } catch (_: Exception) {
            NativeCustomDraftReadResult.Rejected(
                NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** Store exactly the next author's draft version, never a workout grant. */
    fun save(
        ownerKey: String, cycleId: String, expectedRevision: Long,
        draft: CustomTrainingCycle
    ): NativeCustomDraftWriteResult =
        commit(ownerKey, cycleId, expectedRevision, draft, deleting = false)

    /** Versioned tombstone prevents queued offline autosaves restoring a delete. */
    fun delete(
        ownerKey: String, cycleId: String, expectedRevision: Long
    ): NativeCustomDraftWriteResult =
        commit(ownerKey, cycleId, expectedRevision, null, deleting = true)

    private fun commit(
        ownerKey: String, cycleId: String, expectedRevision: Long,
        draft: CustomTrainingCycle?, deleting: Boolean
    ): NativeCustomDraftWriteResult {
        if (!id.matches(cycleId) || expectedRevision !in 0..9_007_199_254_740_990L ||
            (!deleting && (draft == null || draft.cycleId != cycleId ||
                draft.revision != expectedRevision + 1)))
            return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.INVALID_DRAFT)
        if (active(ownerKey) == null)
            return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val owner = active(ownerKey)
                    ?: return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                if (draft != null && draft.athleteId != owner.athleteId)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                val file = path(ownerKey, cycleId)
                val previous = try { decode(file) } catch (_: Exception) {
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.CORRUPT_RECORD)
                }
                if (previous != null &&
                    (previous.ownerKey != ownerKey ||
                        previous.athleteId != owner.athleteId ||
                        previous.cycleId != cycleId))
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                if ((previous?.revision ?: 0L) != expectedRevision ||
                    (deleting && previous == null))
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.STALE_REVISION)
                val next = CustomDraftRecord(ownerKey, owner.athleteId,
                    cycleId, expectedRevision + 1, draft)
                val bytes = try { encode(next) } catch (_: Exception) {
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.INVALID_DRAFT)
                }
                if (active(ownerKey) != owner)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                atomicWrite(file, bytes)
                NativeCustomDraftWriteResult.Committed(next.revision)
            }
        } catch (_: Exception) {
            NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
