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
 * Isolated, app-private correction storage. The ORIGINAL Kotlin workout stays
 * in KotlinNativeJournalFileStore and is NEVER rewritten by saved-history edits.
 *
 * The production owner must authenticate authorizeIssued against its signed-in
 * athlete and call all reads/writes inside its NativeKotlinApprovalRevisionFence.
 * This source-only port neither enrolls an athlete nor updates global history,
 * Last, PRs, or next-session state. Those need a future atomic owner transaction.
 */
enum class NativeSavedCorrectionRefusal {
    INVALID_ARGUMENT, NOT_ISSUED_OR_FINISHED, NOT_AUTHORIZED,
    CORRUPT_OR_CONFLICTING_FILE, CORRECTION_REJECTED, STORAGE_UNAVAILABLE,
    HISTORY_NOT_READY
}

sealed interface NativeSavedCorrectionFileResult {
    /** Real disk state read under the per-workout lock. */
    data class Opened(
        val state: KotlinSavedHistoryCorrectionState,
        val hasCorrectionFile: Boolean
    ) : NativeSavedCorrectionFileResult

    /** Returned only after atomic disk commit or exact lost-ack replay. */
    data class Durable(
        val state: KotlinSavedHistoryCorrectionState,
        val replayed: Boolean
    ) : NativeSavedCorrectionFileResult

    data class Rejected(
        val reason: NativeSavedCorrectionRefusal,
        val correctionReason: KotlinSavedCorrectionRefusal? = null
    ) : NativeSavedCorrectionFileResult
}

/**
 * Identify every existing Kotlin saved-edit file before enrolling history.
 * These identities still require independent original-journal replay.
 */
sealed interface NativeSavedCorrectionInventoryResult {
    data class Verified(val workoutIds: Set<String>) :
        NativeSavedCorrectionInventoryResult
    data object Rejected : NativeSavedCorrectionInventoryResult
}

private data class SavedCorrectionEnvelope(
    val ownerKey: String,
    val workoutId: String,
    val originalFingerprint: String,
    val revision: Long,
    val receipts: List<KotlinSavedSetCorrectionReceipt>
)

/** Checksummed, length-limited binary envelope. A checksum is NOT authentication. */
private object SavedCorrectionBinary {
    private const val MAGIC = 0x50494331 // PIC1
    private const val VERSION = 1
    private const val MAX_BYTES = 256_000
    private const val MAX_RECEIPTS = 512

    fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun hex(bytes: ByteArray): String = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }

    private fun DataOutputStream.optional(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.optional(): String? =
        if (readBoolean()) readUTF() else null

    /**
     * Fingerprint all immutable issued targets AND original committed receipts.
     * Binding only to workoutId would allow edits to a replaced original file.
     * The original journal is always independently opened and replay-verified
     * using the existing native store before computing this fingerprint.
     */
    fun originalFingerprint(journal: KotlinWorkoutJournalState): String {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            val issued = KotlinIssuedSnapshotCodec.encode(journal.snapshot)
            out.writeInt(issued.size)
            out.write(issued)
            // Legacy journals keep identical fingerprints. Replacement
            // journals must also bind their immutable pre-swap issuance.
            if(journal.receipts.any{
                it.operation is KotlinJournalOperation.SwapExercise
            }){
                val original=KotlinIssuedSnapshotCodec.encode(
                    journal.originalSnapshot)
                out.writeInt(original.size)
                out.write(original)
            }
            out.writeUTF(journal.storedIdentity.storageReceiptId)
            out.writeUTF(journal.storedIdentity.workoutId)
            out.writeLong(journal.storedIdentity.storedRevision)
            out.writeUTF(checkNotNull(journal.finishedByEventId))
            out.writeLong(journal.revision)
            out.writeInt(journal.receipts.size)
            for (receipt in journal.receipts) {
                out.writeLong(receipt.appliedRevision)
                val operation = receipt.operation
                out.writeUTF(operation.eventId)
                out.writeLong(operation.expectedJournalRevision)
                when (operation) {
                    is KotlinJournalOperation.Finish -> out.writeByte(1)
                    is KotlinJournalOperation.SwapExercise -> {
                        out.writeByte(3)
                        out.writeUTF(operation.previousExerciseId)
                        out.writeInt(operation.replacementTargets.size)
                        operation.replacementTargets.forEach{target->
                            val single=KotlinIssuedSnapshotCodec.encode(
                                journal.snapshot.copy(targets=listOf(target)))
                            out.writeInt(single.size)
                            out.write(single)
                        }
                    }
                    is KotlinJournalOperation.UndoSwap -> {
                        out.writeByte(4)
                        out.writeUTF(operation.swapEventId)
                    }
                    is KotlinJournalOperation.RecordSet -> {
                        out.writeByte(2)
                        out.writeUTF(operation.setKey)
                        out.writeUTF(operation.outcome.name)
                        out.optional(operation.correctsEventId)
                        val actual = operation.performed
                        out.writeBoolean(actual != null)
                        if (actual != null) {
                            out.optional(actual.load?.stripTrailingZeros()?.toPlainString())
                            out.optional(actual.unit?.name)
                            out.writeInt(actual.reps)
                            val actualRir = actual.actualRir
                            out.writeBoolean(actualRir != null)
                            if (actualRir != null) out.writeInt(actualRir)
                        }
                    }
                }
            }
        }
        return hex(digest(body.toByteArray()))
    }

    fun encode(envelope: SavedCorrectionEnvelope): ByteArray {
        require(envelope.receipts.size <= MAX_RECEIPTS)
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(envelope.ownerKey)
            out.writeUTF(envelope.workoutId)
            out.writeUTF(envelope.originalFingerprint)
            out.writeLong(envelope.revision)
            out.writeInt(envelope.receipts.size)
            for (receipt in envelope.receipts) {
                out.writeLong(receipt.appliedCorrectionRevision)
                val op = receipt.correction
                out.writeUTF(op.eventId)
                out.writeLong(op.expectedCorrectionRevision)
                out.writeUTF(op.setKey)
                out.writeUTF(op.correctsEventId)
                out.writeUTF(op.outcome.name)
                val actual = op.actual
                out.writeBoolean(actual != null)
                if (actual != null) {
                    out.optional(actual.load?.toString())
                    out.optional(actual.unit?.name)
                    out.writeInt(actual.reps)
                    val actualRir = actual.actualRir
                    out.writeBoolean(actualRir != null)
                    if (actualRir != null) out.writeInt(actualRir)
                }
            }
        }
        val payload = body.toByteArray()
        require(payload.size <= MAX_BYTES - 36)
        return ByteArrayOutputStream().also { buf ->
            DataOutputStream(buf).use { out ->
                out.writeInt(payload.size)
                out.write(payload)
                out.write(digest(payload))
            }
        }.toByteArray()
    }

    fun decode(raw: ByteArray): SavedCorrectionEnvelope {
        if (raw.size !in 40..MAX_BYTES) throw IOException("bad correction size")
        DataInputStream(ByteArrayInputStream(raw)).use { outer ->
            val length = outer.readInt()
            if (length < 8 || length > MAX_BYTES - 36 || length + 36 != raw.size)
                throw IOException("bad correction length")
            val payload = ByteArray(length)
            outer.readFully(payload)
            val expected = ByteArray(32)
            outer.readFully(expected)
            if (!MessageDigest.isEqual(digest(payload), expected))
                throw IOException("correction checksum mismatch")
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION)
                    throw IOException("unsupported correction format")
                val ownerKey = input.readUTF()
                val workoutId = input.readUTF()
                val fingerprint = input.readUTF()
                val revision = input.readLong()
                val count = input.readInt()
                if (count !in 0..MAX_RECEIPTS || revision != count.toLong())
                    throw IOException("bad correction revision")
                val receipts = ArrayList<KotlinSavedSetCorrectionReceipt>(count)
                repeat(count) {
                    val applied = input.readLong()
                    val eventId = input.readUTF()
                    val expectedRevision = input.readLong()
                    val key = input.readUTF()
                    val predecessor = input.readUTF()
                    val outcomeName = input.readUTF()
                    val outcome = KotlinJournalSetOutcome.entries.firstOrNull {
                        it.name == outcomeName
                    } ?: throw IOException("bad correction outcome")
                    val actual = if (input.readBoolean()) {
                        val load = input.optional()?.let { BigDecimal(it) }
                        val unit = input.optional()?.let { name ->
                            LoadUnit.entries.firstOrNull { it.name == name }
                                ?: throw IOException("bad correction unit")
                        }
                        val reps = input.readInt()
                        val rir = if (input.readBoolean()) input.readInt() else null
                        KotlinPerformedSet(load, unit, reps, rir)
                    } else null
                    receipts.add(KotlinSavedSetCorrectionReceipt(applied,
                        KotlinSavedSetCorrection(eventId, expectedRevision, key,
                            predecessor, outcome, actual)))
                }
                if (input.available() != 0) throw IOException("trailing correction data")
                return SavedCorrectionEnvelope(
                    ownerKey, workoutId, fingerprint, revision, receipts)
            }
        }
    }
}

/** Per-workout CAS file store; corrections are not merged into workout journals. */
class KotlinNativeSavedCorrectionFileStore(
    private val directory: Path,
    private val workoutStore: KotlinNativeJournalFileStore
) {
    companion object { private val processMutex = Any() }
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun fileFor(ownerKey: String, workoutId: String): Path {
        val key = (ownerKey + "\u0000" + workoutId).toByteArray(Charsets.UTF_8)
        return directory.resolve("correction-" + SavedCorrectionBinary.hex(
            SavedCorrectionBinary.digest(key)) + ".bin")
    }

    private fun <T> locked(
        ownerKey: String, workoutId: String, action: (Path) -> T
    ): T {
        Files.createDirectories(directory)
        val file = fileFor(ownerKey, workoutId)
        val lockFile = file.resolveSibling(file.fileName.toString() + ".lock")
        return synchronized(processMutex) {
            FileChannel.open(lockFile, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { action(file) }
            }
        }
    }

    private fun atomicReplace(path: Path, bytes: ByteArray) {
        val pending = Files.createTempFile(directory, "correction-", ".pending")
        try {
            FileChannel.open(pending, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (error: AtomicMoveNotSupportedException) {
                throw IOException("atomic correction writes unsupported", error)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    private fun recover(
        file: Path, owner: String, workoutId: String,
        authorizeIssued: (String, KotlinTrainingSourceRevision) -> Boolean
    ): NativeSavedCorrectionFileResult {
        val original = workoutStore.openStored(owner, workoutId, authorizeIssued)
        if (original !is NativeJournalFileResult.Durable) {
            val refusal = (original as NativeJournalFileResult.Rejected).reason
            return NativeSavedCorrectionFileResult.Rejected(when (refusal) {
                NativeJournalFileRefusal.NOT_AUTHORIZED ->
                    NativeSavedCorrectionRefusal.NOT_AUTHORIZED
                NativeJournalFileRefusal.NOT_ISSUED ->
                    NativeSavedCorrectionRefusal.NOT_ISSUED_OR_FINISHED
                else -> NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE
            })
        }
        val journal = original.journal
        val pristine = runCatching {
            KotlinSavedHistoryCorrectionDomain.begin(journal)
        }.getOrNull() ?: return NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.NOT_ISSUED_OR_FINISHED)
        if (!Files.exists(file)) return NativeSavedCorrectionFileResult.Opened(
            pristine, false)

        val saved = runCatching {
            if (Files.size(file) > 256_000) throw IOException("oversize correction")
            SavedCorrectionBinary.decode(Files.readAllBytes(file))
        }.getOrNull() ?: return NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE)

        if (saved.ownerKey != owner || saved.workoutId != workoutId ||
            saved.originalFingerprint != SavedCorrectionBinary.originalFingerprint(journal))
            return NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE)

        val replay = KotlinSavedHistoryCorrectionDomain.replay(
            journal, saved.receipts, saved.revision)
        if (replay !is KotlinSavedCorrectionDecision.Proposed)
            return NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE)
        return NativeSavedCorrectionFileResult.Opened(replay.state, true)
    }

    /**
     * Scan the entire physical correction inventory. Cannot silently omit a
     * correction for a workout not named by a caller. Corrupt foreign files
     * block the inventory because their owner cannot be established.
     */
    fun inventoryOwner(ownerKey: String): NativeSavedCorrectionInventoryResult {
        if (!id.matches(ownerKey)) return NativeSavedCorrectionInventoryResult.Rejected
        return runCatching<NativeSavedCorrectionInventoryResult> {
            Files.createDirectories(directory)
            val files = Files.list(directory).use { stream ->
                stream.filter { file ->
                    file.fileName.toString().startsWith("correction-") &&
                        file.fileName.toString().endsWith(".bin")
                }.iterator().asSequence().toList()
            }
            if (files.size > 2048)
                return@runCatching NativeSavedCorrectionInventoryResult.Rejected
            val pending = Files.list(directory).use { stream ->
                stream.anyMatch { file ->
                    file.fileName.toString().startsWith("correction-") &&
                        file.fileName.toString().endsWith(".pending")
                }
            }
            if (pending)
                return@runCatching NativeSavedCorrectionInventoryResult.Rejected
            val ids = linkedSetOf<String>()
            for (file in files) {
                val envelope = runCatching {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
                        Files.size(file) > 256_000L)
                        throw IOException("unsafe saved correction file")
                    SavedCorrectionBinary.decode(Files.readAllBytes(file))
                }.getOrNull() ?: return@runCatching NativeSavedCorrectionInventoryResult.Rejected
                if (!id.matches(envelope.ownerKey) ||
                    !id.matches(envelope.workoutId) ||
                    envelope.workoutId.isEmpty() ||
                    file.fileName.toString() !=
                        fileFor(envelope.ownerKey, envelope.workoutId)
                            .fileName.toString())
                    return@runCatching NativeSavedCorrectionInventoryResult.Rejected
                if (envelope.ownerKey == ownerKey)
                    ids.add(envelope.workoutId)
            }
            NativeSavedCorrectionInventoryResult.Verified(ids)
        }.getOrElse { NativeSavedCorrectionInventoryResult.Rejected }
    }

    fun openStored(
        ownerKey: String, workoutId: String,
        authorizeIssued: (String, KotlinTrainingSourceRevision) -> Boolean
    ): NativeSavedCorrectionFileResult {
        if (!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey, workoutId) { file ->
                recover(file, ownerKey, workoutId, authorizeIssued)
            }
        }.getOrElse {
            NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun appendStored(
        ownerKey: String, workoutId: String, correction: KotlinSavedSetCorrection,
        authorizeIssued: (String, KotlinTrainingSourceRevision) -> Boolean
    ): NativeSavedCorrectionFileResult {
        if (!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey, workoutId) { file ->
                val existing = recover(file, ownerKey, workoutId, authorizeIssued)
                if (existing !is NativeSavedCorrectionFileResult.Opened)
                    return@locked existing
                val next = KotlinSavedHistoryCorrectionDomain.reduce(
                    existing.state, correction)
                if (next !is KotlinSavedCorrectionDecision.Proposed) {
                    val reason = (next as KotlinSavedCorrectionDecision.Rejected).reason
                    return@locked NativeSavedCorrectionFileResult.Rejected(
                        NativeSavedCorrectionRefusal.CORRECTION_REJECTED, reason)
                }
                if (!next.replayed) {
                    val envelope = SavedCorrectionEnvelope(
                        ownerKey, workoutId,
                        SavedCorrectionBinary.originalFingerprint(
                            existing.state.originalFinishedJournal),
                        next.state.correctionRevision, next.state.receipts)
                    atomicReplace(file, SavedCorrectionBinary.encode(envelope))
                }
                NativeSavedCorrectionFileResult.Durable(next.state, next.replayed)
            }
        }.getOrElse {
            NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
