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
import java.util.Collections

/**
 * One explicitly enrolled native history manifest per authenticated athlete.
 * This is not a JS migration importer and never invents previously completed
 * workouts from a directory listing.
 *
 * The owner transaction fence must be acquired FIRST, and every Finish,
 * saved correction and owner/account mutation must join that same fence.
 * An intent is atomically prepared BEFORE a workout/correction file is
 * modified. A pending intent blocks ordinary history reads until replay and
 * finalization finish the two-file operation.
 *
 * This store cannot prove that a checksum means authentication or that a
 * foreign writer did not bypass the protocol. The cutover owner must enforce
 * sole-writer access, durable history enrollment and backup/migration gates.
 */
sealed interface NativeHistoryWriteIntent {
    val workoutId: String

    data class Finish(
        override val workoutId: String,
        val operation: KotlinJournalOperation.Finish
    ) : NativeHistoryWriteIntent

    data class Correct(
        override val workoutId: String,
        val operation: KotlinSavedSetCorrection
    ) : NativeHistoryWriteIntent

    /** Separate lossless custom journal; not a ComposerRequest workout. */
    data class CustomFinish(
        override val workoutId: String,
        val operation: CustomWorkoutJournalOperation.Finish
    ) : NativeHistoryWriteIntent

    /** Durable post-Finish correction of one factual custom visit. */
    data class CustomCorrect(
        override val workoutId: String,
        val operation: CustomSavedVisitCorrection
    ) : NativeHistoryWriteIntent
}

data class NativeHistoryLedger(
    val ownerKey: String,
    val athleteId: String,
    val revision: Long,
    val completions: List<KotlinHistoryManifestEntry>,
    val pending: NativeHistoryWriteIntent?
) {
    /** Never show a possibly stale Last/PR/progression cache while pending. */
    val readyForHistoryReads: Boolean get() = pending == null
}

enum class NativeHistoryLedgerRefusal {
    INVALID_ARGUMENT, NOT_ENROLLED, NOT_AUTHORIZED, CORRUPT_HISTORY,
    REVISION_CONFLICT, PENDING_TRANSACTION, NO_MATCHING_INTENT,
    INVALID_MANIFEST_TRANSITION, STORAGE_UNAVAILABLE
}

sealed interface NativeHistoryLedgerResult {
    data class Durable(
        val ledger: NativeHistoryLedger,
        val replayed: Boolean = false
    ) : NativeHistoryLedgerResult
    data class Rejected(val reason: NativeHistoryLedgerRefusal) :
        NativeHistoryLedgerResult
}

private object NativeHistoryLedgerBinary {
    private const val MAGIC = 0x50494831 // PIH1
    private const val VERSION = 1
    const val MAX_SESSIONS = 128
    private const val MAX_BYTES = 96_000
    private val validId = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REV = 9_007_199_254_740_991L

    fun hash(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun hex(data: ByteArray): String = data.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }

    fun validId(text: String): Boolean = validId.matches(text)

    fun check(state: NativeHistoryLedger) {
        require(validId(state.ownerKey) && validId(state.athleteId))
        require(state.revision in 1..MAX_REV)
        require(state.completions.size <= MAX_SESSIONS)
        require(state.completions.all { entry ->
            entry.completionOrder in 1..MAX_REV &&
                entry.originalJournalRevision >= 1 &&
                entry.savedCorrectionRevision >= 0 &&
                listOf(entry.workoutId, entry.storedReceiptId,
                    entry.finishEventId).all(::validId)
        })
        require(state.completions.map { it.workoutId }.distinct().size ==
            state.completions.size)
        require(state.completions.map { it.storedReceiptId }.distinct().size ==
            state.completions.size)
        require(state.completions.map { it.completionOrder } ==
            (1L..state.completions.size.toLong()).toList())
        when (val intent = state.pending) {
            null -> Unit
            is NativeHistoryWriteIntent.Finish -> require(
                validId(intent.workoutId) &&
                    validId(intent.operation.eventId) &&
                    intent.operation.expectedJournalRevision >= 0 &&
                    state.completions.none { it.workoutId == intent.workoutId }
            )
            is NativeHistoryWriteIntent.CustomFinish -> require(
                validId(intent.workoutId) &&
                    validId(intent.operation.eventId) &&
                    intent.operation.expectedRevision >= 0 &&
                    state.completions.none { it.workoutId == intent.workoutId }
            )
            is NativeHistoryWriteIntent.Correct -> require(
                validId(intent.workoutId) &&
                    validId(intent.operation.eventId) &&
                    validId(intent.operation.correctsEventId) &&
                    intent.operation.setKey.length in 1..100 &&
                    state.completions.any { it.workoutId == intent.workoutId &&
                        it.savedCorrectionRevision == intent.operation.expectedCorrectionRevision }
            )
            is NativeHistoryWriteIntent.CustomCorrect -> require(
                validId(intent.workoutId) &&
                    validId(intent.operation.eventId) &&
                    validId(intent.operation.correctsEventId) &&
                    intent.operation.visit.setKey.length in 1..160 &&
                    state.completions.any {
                        it.workoutId == intent.workoutId &&
                        it.storedReceiptId.startsWith("custom-v2-") &&
                        it.savedCorrectionRevision == intent.operation.expectedCorrectionRevision
                    }
            )
        }
    }

    private fun DataOutputStream.actual(value: KotlinPerformedSet?) {
        writeBoolean(value != null)
        if (value != null) {
            val load = value.load
            writeBoolean(load != null)
            if (load != null) writeUTF(load.toPlainString())
            val unit = value.unit
            writeBoolean(unit != null)
            if (unit != null) writeUTF(unit.name)
            writeInt(value.reps)
            val effort = value.actualRir
            writeBoolean(effort != null)
            if (effort != null) writeInt(effort)
        }
    }

    private fun DataInputStream.actual(): KotlinPerformedSet? {
        if (!readBoolean()) return null
        val load = if (readBoolean()) BigDecimal(readUTF()) else null
        val unit = if (readBoolean()) {
            val raw = readUTF()
            LoadUnit.entries.firstOrNull { it.name == raw }
                ?: throw IOException("unknown unit")
        } else null
        val reps = readInt()
        val effort = if (readBoolean()) readInt() else null
        return KotlinPerformedSet(load, unit, reps, effort)
    }

    fun encode(state: NativeHistoryLedger): ByteArray {
        check(state)
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeUTF(state.ownerKey); out.writeUTF(state.athleteId)
            out.writeLong(state.revision)
            out.writeInt(state.completions.size)
            for (entry in state.completions) {
                out.writeLong(entry.completionOrder)
                out.writeUTF(entry.workoutId)
                out.writeUTF(entry.storedReceiptId)
                out.writeUTF(entry.finishEventId)
                out.writeLong(entry.originalJournalRevision)
                out.writeLong(entry.savedCorrectionRevision)
            }
            when (val intent = state.pending) {
                null -> out.writeByte(0)
                is NativeHistoryWriteIntent.Finish -> {
                    out.writeByte(1)
                    out.writeUTF(intent.workoutId)
                    out.writeUTF(intent.operation.eventId)
                    out.writeLong(intent.operation.expectedJournalRevision)
                }
                is NativeHistoryWriteIntent.CustomFinish -> {
                    out.writeByte(3)
                    out.writeUTF(intent.workoutId)
                    out.writeUTF(intent.operation.eventId)
                    out.writeLong(intent.operation.expectedRevision)
                }
                is NativeHistoryWriteIntent.Correct -> {
                    out.writeByte(2)
                    out.writeUTF(intent.workoutId)
                    val op = intent.operation
                    out.writeUTF(op.eventId)
                    out.writeLong(op.expectedCorrectionRevision)
                    out.writeUTF(op.setKey)
                    out.writeUTF(op.correctsEventId)
                    out.writeUTF(op.outcome.name)
                    out.actual(op.actual)
                }
                is NativeHistoryWriteIntent.CustomCorrect -> {
                    out.writeByte(4)
                    out.writeUTF(intent.workoutId)
                    val op = intent.operation
                    out.writeUTF(op.eventId)
                    out.writeLong(op.expectedCorrectionRevision)
                    out.writeUTF(op.visit.setKey)
                    out.writeUTF(op.visit.part.name)
                    out.writeUTF(op.correctsEventId)
                    out.writeUTF(op.outcome.name)
                    out.actual(op.performed)
                }
            }
        }
        val data = body.toByteArray()
        require(data.size <= MAX_BYTES - 36)
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(data.size)
                out.write(data)
                out.write(hash(data))
            }
        }.toByteArray()
    }

    fun decode(raw: ByteArray): NativeHistoryLedger {
        if (raw.size !in 40..MAX_BYTES) throw IOException("invalid ledger size")
        DataInputStream(ByteArrayInputStream(raw)).use { input ->
            val length = input.readInt()
            if (length < 8 || length + 36 != raw.size)
                throw IOException("invalid ledger framing")
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val expected = ByteArray(32)
            input.readFully(expected)
            if (!MessageDigest.isEqual(hash(bytes), expected))
                throw IOException("ledger checksum mismatch")
            return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                if (data.readInt() != MAGIC || data.readInt() != VERSION)
                    throw IOException("unsupported ledger")
                val owner = data.readUTF()
                val athlete = data.readUTF()
                val revision = data.readLong()
                val count = data.readInt()
                if (count !in 0..MAX_SESSIONS) throw IOException("invalid count")
                val entries = (0 until count).map {
                    KotlinHistoryManifestEntry(
                        data.readLong(), data.readUTF(), data.readUTF(),
                        data.readUTF(), data.readLong(), data.readLong()
                    )
                }
                val pending = when (data.readUnsignedByte()) {
                    0 -> null
                    1 -> NativeHistoryWriteIntent.Finish(
                        data.readUTF(),
                        KotlinJournalOperation.Finish(data.readUTF(), data.readLong())
                    )
                    2 -> {
                        val workout = data.readUTF()
                        val event = data.readUTF()
                        val expectedRevision = data.readLong()
                        val setKey = data.readUTF()
                        val predecessor = data.readUTF()
                        val outcomeName = data.readUTF()
                        val outcome = KotlinJournalSetOutcome.entries.firstOrNull {
                            it.name == outcomeName
                        } ?: throw IOException("invalid outcome")
                        NativeHistoryWriteIntent.Correct(workout,
                            KotlinSavedSetCorrection(event, expectedRevision,
                                setKey, predecessor, outcome, data.actual()))
                    }
                    3 -> NativeHistoryWriteIntent.CustomFinish(
                        data.readUTF(),
                        CustomWorkoutJournalOperation.Finish(
                            data.readUTF(),data.readLong())
                    )
                    4 -> {
                        val workout = data.readUTF()
                        val event = data.readUTF()
                        val revision = data.readLong()
                        val setKey = data.readUTF()
                        val partName = data.readUTF()
                        val part = CustomWorkoutVisitPart.entries.singleOrNull {
                            it.name == partName
                        } ?: throw IOException("invalid custom visit part")
                        val predecessor = data.readUTF()
                        val outcomeName = data.readUTF()
                        val outcome = KotlinJournalSetOutcome.entries.singleOrNull {
                            it.name == outcomeName
                        } ?: throw IOException("invalid custom outcome")
                        NativeHistoryWriteIntent.CustomCorrect(workout,
                            CustomSavedVisitCorrection(event, revision,
                                CustomWorkoutVisitKey(setKey, part),
                                predecessor, outcome, data.actual()))
                    }
                    else -> throw IOException("unknown intent kind")
                }
                if (data.available() != 0) throw IOException("trailing ledger bytes")
                NativeHistoryLedger(
                    owner, athlete, revision,
                    Collections.unmodifiableList(entries), pending
                ).also(::check)
            }
        }
    }
}

/**
 * Journal/manifest individual writes use atomic rename. Atomicity ACROSS
 * files requires prepare -> apply operation -> finalize and owner-fenced
 * recovery; this store only owns the manifest half of that protocol.
 */
class NativeKotlinHistoryManifestFileStore(private val directory: Path) {
    companion object { private val processMutex = Any() }

    private fun path(owner: String, athlete: String): Path =
        directory.resolve("history-" + NativeHistoryLedgerBinary.hex(
            NativeHistoryLedgerBinary.hash(
                (owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))) + ".bin")

    private fun <T> locked(owner: String, athlete: String, action: (Path) -> T): T {
        Files.createDirectories(directory)
        val file = path(owner, athlete)
        val lock = file.resolveSibling(file.fileName.toString() + ".lock")
        return synchronized(processMutex) {
            FileChannel.open(lock, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { action(file) }
            }
        }
    }

    private fun atomicWrite(path: Path, bytes: ByteArray) {
        val pending = Files.createTempFile(directory, "history-", ".pending")
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
                throw IOException("history atomic move unsupported", error)
            }
        } finally { Files.deleteIfExists(pending) }
    }

    private fun read(file: Path): NativeHistoryLedgerResult {
        if (!Files.exists(file)) return NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED)
        return try {
            if (Files.size(file) > 96_000L) throw IOException("oversize history")
            NativeHistoryLedgerResult.Durable(
                NativeHistoryLedgerBinary.decode(Files.readAllBytes(file)))
        } catch (_: Exception) {
            NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.CORRUPT_HISTORY)
        }
    }

    fun open(owner: String, athlete: String): NativeHistoryLedgerResult {
        if (!NativeHistoryLedgerBinary.validId(owner) ||
            !NativeHistoryLedgerBinary.validId(athlete))
            return NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(owner, athlete) { file ->
                val value = read(file)
                if (value is NativeHistoryLedgerResult.Durable &&
                    (value.ledger.ownerKey != owner || value.ledger.athleteId != athlete))
                    NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.CORRUPT_HISTORY)
                else value
            }
        }.getOrElse {
            NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** MUST be explicitly called after authenticating real native ownership. */
    internal fun enroll(
        owner: String, athlete: String,
        authorizeCurrent: (String, String) -> Boolean
    ): NativeHistoryLedgerResult {
        if (!NativeHistoryLedgerBinary.validId(owner) ||
            !NativeHistoryLedgerBinary.validId(athlete))
            return NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(owner, athlete) { file ->
                if (!authorizeCurrent(owner, athlete))
                    return@locked NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.NOT_AUTHORIZED)
                if (Files.exists(file)) return@locked read(file)
                val initial = NativeHistoryLedger(owner, athlete, 1,
                    emptyList(), null)
                atomicWrite(file, NativeHistoryLedgerBinary.encode(initial))
                NativeHistoryLedgerResult.Durable(initial)
            }
        }.getOrElse {
            NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Only the authenticated full-inventory migration coordinator may call
     * this source-level write. The caller must replay-verify EVERY existing
     * workout and saved correction under the shared owner fence first.
     * This operation never overwrites an already-created empty manifest.
     */
    internal fun enrollVerifiedSnapshot(
        owner: String, athlete: String,
        completions: List<KotlinHistoryManifestEntry>,
        authorizeCurrent: (String, String) -> Boolean
    ): NativeHistoryLedgerResult {
        if (!NativeHistoryLedgerBinary.validId(owner) ||
            !NativeHistoryLedgerBinary.validId(athlete))
            return NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(owner, athlete) { file ->
                if (!authorizeCurrent(owner, athlete))
                    return@locked NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.NOT_AUTHORIZED)
                val initial = NativeHistoryLedger(
                    owner, athlete, 1, Collections.unmodifiableList(
                        completions.toList()), null)
                if (runCatching { NativeHistoryLedgerBinary.check(initial) }.isFailure)
                    return@locked NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                if (Files.exists(file)) {
                    val found = read(file)
                    if (found !is NativeHistoryLedgerResult.Durable)
                        return@locked found
                    // Never interpret an existing manifest as replaceable.
                    return@locked if (found.ledger == initial)
                        NativeHistoryLedgerResult.Durable(initial, true)
                    else NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                }
                atomicWrite(file, NativeHistoryLedgerBinary.encode(initial))
                NativeHistoryLedgerResult.Durable(initial)
            }
        }.getOrElse {
            NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun prepare(
        owner: String, athlete: String, expectedRevision: Long,
        intent: NativeHistoryWriteIntent
    ): NativeHistoryLedgerResult = transition(owner, athlete) { old ->
        if (old.pending != null) return@transition NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.PENDING_TRANSACTION)
        if (old.revision != expectedRevision)
            return@transition NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.REVISION_CONFLICT)
        val proposal = old.copy(pending = intent)
        if (runCatching { NativeHistoryLedgerBinary.check(proposal) }.isFailure)
            return@transition NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
        NativeHistoryLedgerResult.Durable(proposal)
    }

    /**
     * Requires exact previously prepared intent AND matching evidence.
     * The transaction coordinator verifies the original/correction file
     * on disk before passing the new manifest entry.
     */
    fun finalize(
        owner: String, athlete: String,
        intent: NativeHistoryWriteIntent,
        resolved: KotlinHistoryManifestEntry
    ): NativeHistoryLedgerResult = transition(owner, athlete) { old ->
        if (old.pending != intent)
            return@transition NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.NO_MATCHING_INTENT)
        val next = when (intent) {
            is NativeHistoryWriteIntent.Finish -> {
                val ordinal = old.completions.size.toLong() + 1
                if (resolved.completionOrder != ordinal ||
                    resolved.workoutId != intent.workoutId ||
                    resolved.finishEventId != intent.operation.eventId)
                    return@transition NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                old.completions + resolved
            }
            is NativeHistoryWriteIntent.CustomFinish -> {
                val ordinal=old.completions.size.toLong()+1
                if(resolved.completionOrder!=ordinal ||
                    resolved.workoutId!=intent.workoutId ||
                    resolved.finishEventId!=intent.operation.eventId ||
                    !resolved.storedReceiptId.startsWith("custom-") ||
                    resolved.savedCorrectionRevision!=0L ||
                    resolved.originalJournalRevision!=
                        intent.operation.expectedRevision+1)
                    return@transition NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                old.completions + resolved
            }
            is NativeHistoryWriteIntent.Correct -> {
                val existing = old.completions.singleOrNull {
                    it.workoutId == intent.workoutId
                } ?: return@transition NativeHistoryLedgerResult.Rejected(
                    NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                if (resolved.copy(savedCorrectionRevision = existing.savedCorrectionRevision) !=
                    existing ||
                    resolved.savedCorrectionRevision != existing.savedCorrectionRevision + 1)
                    return@transition NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                old.completions.map { if (it.workoutId == resolved.workoutId) resolved else it }
            }
            is NativeHistoryWriteIntent.CustomCorrect -> {
                val existing = old.completions.singleOrNull {
                    it.workoutId == intent.workoutId &&
                        it.storedReceiptId.startsWith("custom-v2-")
                } ?: return@transition NativeHistoryLedgerResult.Rejected(
                    NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                if (resolved.copy(savedCorrectionRevision = existing.savedCorrectionRevision) !=
                    existing ||
                    resolved.savedCorrectionRevision != existing.savedCorrectionRevision + 1)
                    return@transition NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                old.completions.map { if (it.workoutId == resolved.workoutId) resolved else it }
            }
        }
        val updated = old.copy(revision = old.revision + 1,
            completions = Collections.unmodifiableList(next),
            pending = null)
        if (runCatching { NativeHistoryLedgerBinary.check(updated) }.isFailure)
            return@transition NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
        NativeHistoryLedgerResult.Durable(updated)
    }

    private fun transition(
        owner: String, athlete: String,
        change: (NativeHistoryLedger) -> NativeHistoryLedgerResult
    ): NativeHistoryLedgerResult {
        if (!NativeHistoryLedgerBinary.validId(owner) ||
            !NativeHistoryLedgerBinary.validId(athlete))
            return NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(owner, athlete) { file ->
                val current = read(file)
                if (current !is NativeHistoryLedgerResult.Durable)
                    return@locked current
                val original = current.ledger
                if (original.ownerKey != owner || original.athleteId != athlete)
                    return@locked NativeHistoryLedgerResult.Rejected(
                        NativeHistoryLedgerRefusal.CORRUPT_HISTORY)
                val result = change(original)
                if (result is NativeHistoryLedgerResult.Durable &&
                    result.ledger != original)
                    atomicWrite(file, NativeHistoryLedgerBinary.encode(result.ledger))
                result
            }
        }.getOrElse {
            NativeHistoryLedgerResult.Rejected(
                NativeHistoryLedgerRefusal.STORAGE_UNAVAILABLE)
        }
    }
}

/**
 * A pending redo intent deliberately returns null, preventing a stale
 * manifest from being projected by the existing history read coordinator.
 * The caller MUST inject a genuine current-owner authentication check.
 */
class NativeKotlinHistoryManifestFileAuthority(
    private val store: NativeKotlinHistoryManifestFileStore,
    private val canAccessOwner: (String, String) -> Boolean
) : NativeKotlinHistoryManifestAuthority {
    override fun currentHistoryManifest(
        ownerKey: String, athleteId: String
    ): NativeKotlinHistoryManifest? {
        if (!runCatching { canAccessOwner(ownerKey, athleteId) }.getOrDefault(false))
            return null
        val opened = store.open(ownerKey, athleteId)
        if (opened !is NativeHistoryLedgerResult.Durable ||
            !opened.ledger.readyForHistoryReads ||
            opened.ledger.completions.any {
                it.storedReceiptId.startsWith("custom-")
            })
            return null
        return NativeKotlinHistoryManifest(
            ownerKey, athleteId, opened.ledger.revision,
            opened.ledger.completions.toList()
        )
    }
}
