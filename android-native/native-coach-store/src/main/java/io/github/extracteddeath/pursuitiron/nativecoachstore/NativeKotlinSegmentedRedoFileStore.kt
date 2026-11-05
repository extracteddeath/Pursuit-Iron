package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Collections

/**
 * Durable, owner-scoped candidate write-ahead REDO for a future segmented
 * ledger. It has deliberately NO root-publication, commit, discard or cutover
 * API. Only the existing v2 ledger and its verified journal transactions are
 * history authorities.
 *
 * The caller's base head, uniqueness lookup and resolved journal evidence are
 * NOT trusted by this test seam. A production single writer must bind those
 * values to its authenticated disk index and source-journal replay before it
 * can ever use staged patches. Do not derive history, Last, PR or progression
 * from the WAL.
 *
 * Under the shared owner fence:
 * prepare -> fsynced checksummed WAL -> replay-checked proposed patch ->
 * fsynced checksummed WAL. On process death, read() restores the exact
 * intent and patch, never silently drops either.
 */
data class NativeSegmentedRedoRecord(
    val base: NativeSegmentedWriteHead,
    val intent: NativeHistoryWriteIntent,
    val target: KotlinHistoryManifestEntry?,
    val stagedPatch: NativeSegmentedWritePatch?
) {
    val canPublishHistory: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}

enum class NativeSegmentedRedoRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_PREPARED,
    PENDING_TRANSACTION, CONFLICT, INVALID_PROPOSAL,
    CORRUPT_WAL, STORAGE_UNAVAILABLE
}

sealed interface NativeSegmentedRedoResult {
    data class Durable(
        val redo: NativeSegmentedRedoRecord,
        val replayed: Boolean
    ) : NativeSegmentedRedoResult
    data class Rejected(val reason: NativeSegmentedRedoRefusal) :
        NativeSegmentedRedoResult
}

class NativeKotlinSegmentedRedoFileStore(
    private val directory: Path,
    private val ownerFence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    companion object {
        private const val MAGIC = 0x50495741 // PIWA (candidate only)
        private const val VERSION = 1
        private const val MAX_BYTES = 131_072
        private const val MAX_REVISION = 9_007_199_254_740_991L
    }

    private fun deny(reason: NativeSegmentedRedoRefusal) =
        NativeSegmentedRedoResult.Rejected(reason)

    private fun authorized(owner: String, athlete: String) = runCatching {
        identity.signedInIdentity() == NativeKotlinOwnerIdentity(owner, athlete)
    }.getOrDefault(false)

    private fun path(owner: String, athlete: String): Path {
        val value = MessageDigest.getInstance("SHA-256").digest(
            (owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))
        val hex = value.joinToString("") { "%02x".format(it.toInt() and 255) }
        return directory.resolve("candidate-segmented-redo-$hex.wal")
    }

    private fun guarded(
        owner: String, athlete: String,
        work: () -> NativeSegmentedRedoResult
    ): NativeSegmentedRedoResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeSegmentedRedoRefusal.INVALID_SCOPE)
        return runCatching {
            ownerFence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete)) {
                    deny(NativeSegmentedRedoRefusal.NOT_AUTHORIZED)
                } else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe redo directory")
                    work()
                }
            }
        }.getOrElse { deny(NativeSegmentedRedoRefusal.STORAGE_UNAVAILABLE) }
    }

    private fun hash(body: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(body)

    private fun DataOutputStream.entry(e: KotlinHistoryManifestEntry) {
        writeLong(e.completionOrder); writeUTF(e.workoutId)
        writeUTF(e.storedReceiptId); writeUTF(e.finishEventId)
        writeLong(e.originalJournalRevision); writeLong(e.savedCorrectionRevision)
    }
    private fun DataInputStream.entry() = KotlinHistoryManifestEntry(
        readLong(), readUTF(), readUTF(), readUTF(), readLong(), readLong())

    private fun DataOutputStream.performed(p: KotlinPerformedSet?) {
        writeBoolean(p != null)
        if (p != null) {
            writeBoolean(p.load != null)
            if (p.load != null) writeUTF(p.load.toPlainString())
            writeBoolean(p.unit != null)
            if (p.unit != null) writeUTF(p.unit.name)
            writeInt(p.reps)
            writeBoolean(p.actualRir != null)
            if (p.actualRir != null) writeInt(p.actualRir)
        }
    }
    private fun DataInputStream.performed(): KotlinPerformedSet? {
        if (!readBoolean()) return null
        val load = if (readBoolean()) BigDecimal(readUTF()) else null
        val unit = if (readBoolean()) LoadUnit.valueOf(readUTF()) else null
        val reps = readInt()
        val rir = if (readBoolean()) readInt() else null
        return KotlinPerformedSet(load, unit, reps, rir)
    }

    private fun DataOutputStream.intent(value: NativeHistoryWriteIntent) {
        when (value) {
            is NativeHistoryWriteIntent.Finish -> {
                writeByte(1); writeUTF(value.workoutId)
                writeUTF(value.operation.eventId)
                writeLong(value.operation.expectedJournalRevision)
            }
            is NativeHistoryWriteIntent.Correct -> {
                writeByte(2); writeUTF(value.workoutId)
                val c = value.operation
                writeUTF(c.eventId); writeLong(c.expectedCorrectionRevision)
                writeUTF(c.setKey); writeUTF(c.correctsEventId)
                writeUTF(c.outcome.name); performed(c.actual)
            }
            is NativeHistoryWriteIntent.CustomFinish -> {
                writeByte(3); writeUTF(value.workoutId)
                writeUTF(value.operation.eventId)
                writeLong(value.operation.expectedRevision)
            }
            is NativeHistoryWriteIntent.CustomCorrect -> {
                writeByte(4); writeUTF(value.workoutId)
                val c = value.operation
                writeUTF(c.eventId); writeLong(c.expectedCorrectionRevision)
                writeUTF(c.visit.setKey); writeUTF(c.visit.part.name)
                writeUTF(c.correctsEventId); writeUTF(c.outcome.name)
                performed(c.performed)
            }
        }
    }
    private fun DataInputStream.intent(): NativeHistoryWriteIntent {
        val kind = readUnsignedByte()
        val workout = readUTF()
        return when (kind) {
            1 -> NativeHistoryWriteIntent.Finish(
                workout, KotlinJournalOperation.Finish(readUTF(), readLong()))
            2 -> NativeHistoryWriteIntent.Correct(
                workout, KotlinSavedSetCorrection(
                    readUTF(), readLong(), readUTF(), readUTF(),
                    KotlinJournalSetOutcome.valueOf(readUTF()), performed()))
            3 -> NativeHistoryWriteIntent.CustomFinish(
                workout, CustomWorkoutJournalOperation.Finish(readUTF(), readLong()))
            4 -> NativeHistoryWriteIntent.CustomCorrect(
                workout, CustomSavedVisitCorrection(
                    readUTF(), readLong(),
                    CustomWorkoutVisitKey(readUTF(),
                        CustomWorkoutVisitPart.valueOf(readUTF())),
                    readUTF(), KotlinJournalSetOutcome.valueOf(readUTF()),
                    performed()))
            else -> throw IOException("unknown redo intent")
        }
    }

    private fun encode(record: NativeSegmentedRedoRecord): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSION)
            d.writeUTF(record.base.ownerKey); d.writeUTF(record.base.athleteId)
            d.writeLong(record.base.revision)
            d.writeLong(record.base.completionCount)
            d.writeUTF(record.base.rootDigest)
            d.intent(record.intent)
            d.writeBoolean(record.target != null)
            if (record.target != null) d.entry(record.target)
            d.writeBoolean(record.stagedPatch != null)
            record.stagedPatch?.let { patch ->
                d.writeUTF(patch.priorRootDigest)
                d.writeLong(patch.expectedHeadRevision)
                d.writeLong(patch.nextHeadRevision)
                d.writeLong(patch.nextCompletionCount)
                d.writeLong(patch.replaceSegmentFirstOrdinal)
                d.writeInt(patch.completions.size)
                patch.completions.forEach(d::entry)
                d.intent(patch.consumedIntent)
            }
        }
        val bytes = output.toByteArray()
        if (bytes.size > MAX_BYTES - 36) throw IOException("redo too large")
        val framed = ByteArrayOutputStream()
        DataOutputStream(framed).use { d ->
            d.writeInt(bytes.size); d.write(bytes); d.write(hash(bytes))
        }
        return framed.toByteArray()
    }

    private fun decode(bytes: ByteArray): NativeSegmentedRedoRecord {
        if (bytes.size !in 40..MAX_BYTES) throw IOException("invalid redo size")
        val framing = DataInputStream(ByteArrayInputStream(bytes))
        val length = framing.readInt()
        if (length < 8 || length + 36 != bytes.size)
            throw IOException("invalid redo frame")
        val body = ByteArray(length)
        framing.readFully(body)
        val checksum = ByteArray(32)
        framing.readFully(checksum)
        if (!MessageDigest.isEqual(hash(body), checksum))
            throw IOException("redo checksum mismatch")
        val d = DataInputStream(ByteArrayInputStream(body))
        if (d.readInt() != MAGIC || d.readInt() != VERSION)
            throw IOException("unsupported redo")
        val base = NativeSegmentedWriteHead(d.readUTF(), d.readUTF(),
            d.readLong(), d.readLong(), d.readUTF())
        val intent = d.intent()
        val target = if (d.readBoolean()) d.entry() else null
        val staged = if (d.readBoolean()) {
            val priorDigest = d.readUTF()
            val expected = d.readLong()
            val next = d.readLong()
            val nextCount = d.readLong()
            val first = d.readLong()
            val count = d.readInt()
            if (count !in 1..NativeSegmentedHistoryWritePlanDomain.SEGMENT_SIZE)
                throw IOException("invalid patch count")
            val entries = (0 until count).map { d.entry() }
            val consumed = d.intent()
            NativeSegmentedWritePatch(priorDigest, expected, next, nextCount,
                first, Collections.unmodifiableList(entries), consumed)
        } else null
        if (d.available() != 0) throw IOException("trailing redo bytes")
        return NativeSegmentedRedoRecord(base, intent, target, staged).also {
            validate(it)
        }
    }

    private fun validate(record: NativeSegmentedRedoRecord) {
        val base = record.base
        if (!id.matches(base.ownerKey) || !id.matches(base.athleteId) ||
            base.revision !in 1..MAX_REVISION ||
            base.completionCount !in 0 until MAX_REVISION ||
            !digest.matches(base.rootDigest) || base.pending != null)
            throw IOException("invalid base")
        val plan = NativeSegmentedHistoryWritePlanDomain.prepare(
            base, base.revision, record.intent, record.target)
        if (plan !is NativeSegmentedWriteDecision.Prepared)
            throw IOException("invalid prepared intent")
        record.stagedPatch?.let { patch ->
            if (patch.priorRootDigest != base.rootDigest ||
                patch.expectedHeadRevision != base.revision ||
                patch.nextHeadRevision != base.revision + 1 ||
                patch.nextCompletionCount !in base.completionCount..base.completionCount + 1 ||
                patch.consumedIntent != record.intent ||
                patch.completions.size !in
                    1..NativeSegmentedHistoryWritePlanDomain.SEGMENT_SIZE)
                throw IOException("invalid proposed patch")
            val ordinal = if (record.target == null)
                base.completionCount + 1
            else record.target.completionOrder
            val first = ((ordinal - 1) /
                NativeSegmentedHistoryWritePlanDomain.SEGMENT_SIZE) *
                NativeSegmentedHistoryWritePlanDomain.SEGMENT_SIZE + 1
            if (patch.replaceSegmentFirstOrdinal != first ||
                patch.completions.withIndex().any { (i, e) ->
                    e.completionOrder != first + i
                })
                throw IOException("incorrect patch ordinals")
        }
    }

    private fun fromDisk(file: Path): NativeSegmentedRedoRecord? {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(file) ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(file) > MAX_BYTES.toLong())
            throw IOException("unsafe redo file")
        return decode(Files.readAllBytes(file))
    }

    private fun atomicWrite(file: Path, record: NativeSegmentedRedoRecord) {
        val bytes = encode(record)
        val temp = Files.createTempFile(directory, "candidate-segmented-", ".pending")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    private fun safelyRead(file: Path): NativeSegmentedRedoResult {
        return try {
            val result = fromDisk(file)
                ?: return deny(NativeSegmentedRedoRefusal.NOT_PREPARED)
            NativeSegmentedRedoResult.Durable(result, true)
        } catch (_: Exception) {
            deny(NativeSegmentedRedoRefusal.CORRUPT_WAL)
        }
    }

    fun read(owner: String, athlete: String): NativeSegmentedRedoResult =
        guarded(owner, athlete) { safelyRead(path(owner, athlete)) }

    /**
     * This is a rehearsal-only prepare: caller-provided root is NOT authority.
     * The on-disk record will never be automatically cleared or promoted.
     */
    fun prepare(
        owner: String, athlete: String,
        base: NativeSegmentedWriteHead,
        intent: NativeHistoryWriteIntent,
        target: KotlinHistoryManifestEntry?
    ): NativeSegmentedRedoResult = guarded(owner, athlete) {
        if (base.ownerKey != owner || base.athleteId != athlete ||
            base.pending != null)
            return@guarded deny(NativeSegmentedRedoRefusal.CONFLICT)
        val file = path(owner, athlete)
        val existing = try { fromDisk(file) } catch (_: Exception) {
            return@guarded deny(NativeSegmentedRedoRefusal.CORRUPT_WAL)
        }
        if (existing != null)
            return@guarded if (existing.base == base &&
                existing.intent == intent && existing.target == target)
                NativeSegmentedRedoResult.Durable(existing, true)
            else deny(NativeSegmentedRedoRefusal.PENDING_TRANSACTION)
        if (NativeSegmentedHistoryWritePlanDomain.prepare(
                base, base.revision, intent, target)
            !is NativeSegmentedWriteDecision.Prepared)
            return@guarded deny(NativeSegmentedRedoRefusal.INVALID_PROPOSAL)
        val record = NativeSegmentedRedoRecord(base, intent, target, null)
        atomicWrite(file, record)
        NativeSegmentedRedoResult.Durable(record, false)
    }

    /**
     * Only stages a PURE proposed patch; it does not commit a new segment,
     * validate original journals, or publish a new root. Unverified source
     * data can never become authoritative training history through this API.
     */
    fun stage(
        owner: String, athlete: String,
        segment: NativeSegmentedWriteSegment,
        resolved: KotlinHistoryManifestEntry
    ): NativeSegmentedRedoResult = guarded(owner, athlete) {
        val file = path(owner, athlete)
        val previous = try { fromDisk(file) } catch (_: Exception) {
            return@guarded deny(NativeSegmentedRedoRefusal.CORRUPT_WAL)
        } ?: return@guarded deny(NativeSegmentedRedoRefusal.NOT_PREPARED)
        val planned = NativeSegmentedHistoryWritePlanDomain.finalize(
            previous.base.copy(pending = previous.intent),
            previous.intent, previous.target, segment, resolved)
        val patch = (planned as? NativeSegmentedWriteDecision.ProposedPatch)?.patch
            ?: return@guarded deny(NativeSegmentedRedoRefusal.INVALID_PROPOSAL)
        if (previous.stagedPatch != null)
            return@guarded if (previous.stagedPatch == patch)
                NativeSegmentedRedoResult.Durable(previous, true)
            else deny(NativeSegmentedRedoRefusal.CONFLICT)
        val updated = previous.copy(stagedPatch = patch)
        validate(updated)
        atomicWrite(file, updated)
        NativeSegmentedRedoResult.Durable(updated, false)
    }
}
