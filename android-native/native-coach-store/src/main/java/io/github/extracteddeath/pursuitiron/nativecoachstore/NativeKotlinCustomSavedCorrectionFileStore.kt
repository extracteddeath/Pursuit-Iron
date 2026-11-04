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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Lossless, independently replayed saved corrections for FINISHED custom
 * workouts. Neither this file nor its SHA-256 checksum authenticates its owner.
 * An authenticated owner fence, a matching immutable original journal, and
 * a prepared shared-history redo intent are all mandatory for a write.
 */
internal data class NativeCustomSavedCorrectionEnvelope(
    val ownerKey: String,
    val workoutId: String,
    val originalReceiptId: String,
    val revision: Long,
    val receipts: List<CustomSavedVisitCorrectionReceipt>
)

internal object NativeCustomSavedCorrectionCodec {
    private const val MAGIC = 0x50494353 // PICS
    private const val VERSION = 1
    const val MAX_BYTES = 256_000
    private const val MAX_RECEIPTS = 512
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val receiptId = Regex("^custom-v2-[a-f0-9]{64}$")

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun DataOutputStream.optional(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }
    private fun DataInputStream.optional(): String? =
        if (readBoolean()) readUTF() else null

    private fun DataOutputStream.performed(value: KotlinPerformedSet?) {
        writeBoolean(value != null)
        if (value == null) return
        optional(value.load?.toPlainString())
        optional(value.unit?.name)
        writeInt(value.reps)
        writeBoolean(value.actualRir != null)
        value.actualRir?.let { writeInt(it) }
    }
    private fun DataInputStream.performed(): KotlinPerformedSet? {
        if (!readBoolean()) return null
        val load = optional()?.let { BigDecimal(it) }
        val unit = optional()?.let { raw ->
            LoadUnit.entries.singleOrNull { it.name == raw }
                ?: throw IOException("unsupported actual unit")
        }
        val reps = readInt()
        val rir = if (readBoolean()) readInt() else null
        return KotlinPerformedSet(load, unit, reps, rir)
    }

    fun encode(value: NativeCustomSavedCorrectionEnvelope): ByteArray {
        require(id.matches(value.ownerKey) && id.matches(value.workoutId))
        require(receiptId.matches(value.originalReceiptId))
        require(value.receipts.size <= MAX_RECEIPTS &&
            value.revision == value.receipts.size.toLong())
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeUTF(value.ownerKey); out.writeUTF(value.workoutId)
            out.writeUTF(value.originalReceiptId)
            out.writeLong(value.revision)
            out.writeInt(value.receipts.size)
            for (r in value.receipts) {
                out.writeLong(r.appliedCorrectionRevision)
                val op = r.correction
                out.writeUTF(op.eventId)
                out.writeLong(op.expectedCorrectionRevision)
                out.writeUTF(op.visit.setKey)
                out.writeUTF(op.visit.part.name)
                out.writeUTF(op.correctsEventId)
                out.writeUTF(op.outcome.name)
                out.performed(op.performed)
            }
        }
        val payload = body.toByteArray()
        require(payload.size in 16..(MAX_BYTES - 36))
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(payload.size)
                out.write(payload)
                out.write(sha(payload))
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): NativeCustomSavedCorrectionEnvelope {
        if (bytes.size !in 40..MAX_BYTES)
            throw IOException("invalid custom corrections size")
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { outer ->
                val size = outer.readInt()
                if (size !in 16..(MAX_BYTES - 36) || size + 36 != bytes.size)
                    throw IOException("invalid custom correction frame")
                val payload = ByteArray(size)
                outer.readFully(payload)
                val digest = ByteArray(32)
                outer.readFully(digest)
                if (!MessageDigest.isEqual(digest, sha(payload)))
                    throw IOException("custom correction checksum mismatch")
                DataInputStream(ByteArrayInputStream(payload)).use { data ->
                    if (data.readInt() != MAGIC || data.readInt() != VERSION)
                        throw IOException("unsupported custom correction format")
                    val owner = data.readUTF()
                    val workout = data.readUTF()
                    val original = data.readUTF()
                    val revision = data.readLong()
                    val count = data.readInt()
                    if (!id.matches(owner) || !id.matches(workout) ||
                        !receiptId.matches(original) ||
                        count !in 0..MAX_RECEIPTS || revision != count.toLong())
                        throw IOException("invalid custom correction scope/revision")
                    val receipts = List(count) {
                        val applied = data.readLong()
                        val eventId = data.readUTF()
                        val expected = data.readLong()
                        val setKey = data.readUTF()
                        val partName = data.readUTF()
                        val part = CustomWorkoutVisitPart.entries.singleOrNull {
                            it.name == partName
                        }
                        val predecessor = data.readUTF()
                        val outcomeName = data.readUTF()
                        val outcome = KotlinJournalSetOutcome.entries.singleOrNull {
                            it.name == outcomeName
                        }
                        if (part == null || outcome == null)
                            throw IOException("unknown correction visit/outcome")
                        CustomSavedVisitCorrectionReceipt(applied,
                            CustomSavedVisitCorrection(eventId, expected,
                                CustomWorkoutVisitKey(setKey, part),
                                predecessor, outcome, data.performed()))
                    }
                    if (data.available() != 0)
                        throw IOException("trailing custom correction bytes")
                    NativeCustomSavedCorrectionEnvelope(
                        owner, workout, original, revision, receipts)
                }
            }
        } catch (e: Exception) {
            throw IOException("invalid stored custom saved corrections", e)
        }
    }
}

enum class NativeCustomSavedCorrectionRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_FINISHED, MISSING_HISTORY,
    WRONG_HISTORY_INTENT, JOURNAL_MISMATCH, CORRUPT_FILE,
    INVALID_CORRECTION, STORAGE_UNAVAILABLE
}

sealed interface NativeCustomSavedCorrectionResult {
    data class Opened(
        val state: CustomSavedHistoryCorrectionState,
        val hasCorrectionFile: Boolean
    ) : NativeCustomSavedCorrectionResult
    data class Durable(
        val state: CustomSavedHistoryCorrectionState,
        val replayed: Boolean
    ) : NativeCustomSavedCorrectionResult
    data class Rejected(val reason: NativeCustomSavedCorrectionRefusal) :
        NativeCustomSavedCorrectionResult
}

/**
 * All methods share the gym/program/consent/journal/manifest OS owner fence.
 * appendPending is internal: no UI or random caller may save a correction
 * without the EXACT CustomCorrect intent already durably prepared.
 */
class NativeKotlinCustomSavedCorrectionFileStore(
    private val root: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val custom: NativeKotlinCustomWorkoutJournalFileStore,
    private val history: NativeKotlinHistoryManifestFileStore
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun authorized(owner: String): NativeKotlinOwnerIdentity? =
        runCatching { identity.signedInIdentity() }.getOrNull()?.takeIf {
            it.ownerKey == owner && id.matches(it.athleteId)
        }

    private fun file(owner: String, workoutId: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            (owner + "\u0000" + workoutId).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return root.resolve("corrected-custom-$digest.bin")
    }

    private fun atomic(file: Path, bytes: ByteArray) {
        Files.createDirectories(root)
        if (Files.isSymbolicLink(root) ||
            (Files.exists(file) && Files.isSymbolicLink(file)))
            throw IOException("unsafe custom correction directory or file")
        val pending = Files.createTempFile(root, "corrected-custom-", ".pending")
        try {
            FileChannel.open(pending, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (ex: AtomicMoveNotSupportedException) {
                throw IOException("atomic custom correction writes unavailable", ex)
            }
        } finally { Files.deleteIfExists(pending) }
    }

    private fun load(
        owner: String, workoutId: String,
        who: NativeKotlinOwnerIdentity
    ): NativeCustomSavedCorrectionResult {
        val original = (custom.open(owner, workoutId)
            as? NativeCustomJournalResult.Durable)?.issued?.journal
            ?: return NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.NOT_FINISHED)
        if (original.finishedByEventId == null ||
            original.snapshot.stamp.athleteId != who.athleteId ||
            CustomWorkoutHistoryEvidenceDomain.project(original)
                !is CustomHistoryEvidenceResult.Candidate)
            return NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.NOT_FINISHED)
        val originalReceipt = NativeCustomFinishReceipt.compute(
            owner, who.athleteId, original)
        val file = file(owner, workoutId)
        if (!Files.exists(file)) return NativeCustomSavedCorrectionResult.Opened(
            CustomSavedHistoryCorrectionDomain.begin(original), false)
        return try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
                Files.size(file) !in 40L..NativeCustomSavedCorrectionCodec.MAX_BYTES.toLong())
                throw IOException("invalid custom correction file")
            val decoded = NativeCustomSavedCorrectionCodec.decode(
                Files.readAllBytes(file))
            if (decoded.ownerKey != owner || decoded.workoutId != workoutId ||
                decoded.originalReceiptId != originalReceipt)
                throw IOException("original custom workout replaced")
            val result = CustomSavedHistoryCorrectionDomain.replay(
                original, decoded.receipts, decoded.revision)
            val state = (result as? CustomSavedCorrectionDecision.Proposed)
                ?.state ?: throw IOException("invalid custom correction replay")
            if (state.correctionRevision == 0L)
                throw IOException("empty persisted correction revision")
            NativeCustomSavedCorrectionResult.Opened(state, true)
        } catch (_: Exception) {
            NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.CORRUPT_FILE)
        }
    }

    fun open(owner: String, workoutId: String): NativeCustomSavedCorrectionResult {
        if (!id.matches(owner) || !id.matches(workoutId))
            return NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val who = authorized(owner)
                    ?: return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.NOT_AUTHORIZED)
                load(owner, workoutId, who)
            }
        }.getOrElse { NativeCustomSavedCorrectionResult.Rejected(
            NativeCustomSavedCorrectionRefusal.STORAGE_UNAVAILABLE) }
    }

    internal fun appendPending(
        owner: String, workoutId: String,
        operation: CustomSavedVisitCorrection
    ): NativeCustomSavedCorrectionResult {
        if (!id.matches(owner) || !id.matches(workoutId))
            return NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val who = authorized(owner)
                    ?: return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.NOT_AUTHORIZED)
                val ledger = (history.open(owner, who.athleteId)
                    as? NativeHistoryLedgerResult.Durable)?.ledger
                    ?: return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.MISSING_HISTORY)
                if (ledger.pending != NativeHistoryWriteIntent.CustomCorrect(
                        workoutId, operation))
                    return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.WRONG_HISTORY_INTENT)
                val existing = ledger.completions.singleOrNull {
                    it.workoutId == workoutId &&
                        it.storedReceiptId.startsWith("custom-v2-")
                } ?: return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                    NativeCustomSavedCorrectionRefusal.MISSING_HISTORY)
                val opened = load(owner, workoutId, who)
                val previous = (opened as? NativeCustomSavedCorrectionResult.Opened)
                    ?.state ?: return@withOwnerTransaction opened
                if (NativeCustomFinishReceipt.compute(owner, who.athleteId,
                        previous.originalFinishedJournal) != existing.storedReceiptId ||
                    existing.originalJournalRevision !=
                        previous.originalFinishedJournal.revision ||
                    existing.finishEventId !=
                        previous.originalFinishedJournal.finishedByEventId)
                    return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.JOURNAL_MISMATCH)
                val attempt = CustomSavedHistoryCorrectionDomain.reduce(
                    previous, operation)
                val proposal = attempt as? CustomSavedCorrectionDecision.Proposed
                    ?: return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.INVALID_CORRECTION)
                if (proposal.replayed)
                    return@withOwnerTransaction NativeCustomSavedCorrectionResult.Durable(
                        previous, true)
                if (previous.correctionRevision != existing.savedCorrectionRevision ||
                    !authorized(owner).let { it == who })
                    return@withOwnerTransaction NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.JOURNAL_MISMATCH)
                val bytes = NativeCustomSavedCorrectionCodec.encode(
                    NativeCustomSavedCorrectionEnvelope(owner, workoutId,
                        existing.storedReceiptId, proposal.state.correctionRevision,
                        proposal.state.receipts))
                atomic(file(owner, workoutId), bytes)
                // Acknowledge only a persisted, checksum/replay-verified reopen.
                val after = load(owner, workoutId, who)
                val persisted = (after as? NativeCustomSavedCorrectionResult.Opened)
                    ?.state
                if (persisted != proposal.state)
                    NativeCustomSavedCorrectionResult.Rejected(
                        NativeCustomSavedCorrectionRefusal.STORAGE_UNAVAILABLE)
                else NativeCustomSavedCorrectionResult.Durable(persisted, false)
            }
        }.getOrElse { NativeCustomSavedCorrectionResult.Rejected(
            NativeCustomSavedCorrectionRefusal.STORAGE_UNAVAILABLE) }
    }
}
