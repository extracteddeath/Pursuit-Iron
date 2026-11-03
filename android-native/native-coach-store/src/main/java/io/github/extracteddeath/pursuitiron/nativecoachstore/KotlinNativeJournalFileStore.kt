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
 * Isolated Android-owned prototype for an OFFLINE journal persistence boundary.
 *
 * This component has no app dependency or JS runtime. A future authenticated
 * native owner must pass a PRIVATE filesDir-derived folder and authorize each
 * (athlete, workout, immutable issued snapshot, stored identity) before calls.
 * SHA-256 detects accidental corruption; it does NOT authenticate a caller
 * or protect against a malicious actor who can modify private app files.
 *
 * The snapshot and identity are supplied by the native issuance authority;
 * the journal file contains only immutable receipt events and their scope
 * fingerprint. Native-issued snapshot persistence is a SEPARATE cutover gate.
 */
enum class NativeJournalFileRefusal {
    INVALID_ARGUMENT, NOT_ISSUED, ISSUE_CONFLICT, SOURCE_CONFLICT,
    CORRUPT_RECEIPTS, JOURNAL_REJECTED, STORAGE_UNAVAILABLE
}

sealed interface NativeJournalFileResult {
    data class Durable(
        val journal: KotlinWorkoutJournalState,
        /** True only when this exact operation was previously committed. */
        val replayed: Boolean
    ) : NativeJournalFileResult
    data class Rejected(
        val reason: NativeJournalFileRefusal,
        val journalReason: KotlinJournalRefusal? = null
    ) : NativeJournalFileResult
}

private data class SavedReceipts(
    val ownerKey: String,
    val snapshotHash: String,
    val identity: KotlinStoredWorkoutIdentity,
    val receipts: List<KotlinJournalReceipt>,
    val revision: Long,
    val finishId: String?
)

private object NativeJournalBinary {
    private const val MAGIC = 0x50494A31 // "PIJ1"
    private const val VERSION = 1
    private const val MAX_BYTES = 2_000_000
    private const val MAX_EVENTS = 2048

    fun hash(raw: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }

    private fun DataOutputStream.optional(value: String?) {
        writeBoolean(value != null)
        if(value != null) writeUTF(value)
    }
    private fun DataInputStream.optional(): String? =
        if(readBoolean()) readUTF() else null

    /** Hash ALL immutable issued target fields so targets cannot drift silently. */
    fun snapshotHash(snapshot: KotlinStagedWorkout): String {
        val buffer=ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            with(out) {
                val s=snapshot.source
                writeUTF(s.athleteId);writeLong(s.athleteRevision)
                writeUTF(s.programId);writeLong(s.programRevision)
                writeUTF(s.gymId);writeLong(s.gymRevision)
                writeUTF(s.catalogRevision);writeUTF(s.coachingRulesRevision)
                writeUTF(snapshot.grantId);writeUTF(snapshot.workoutId)
                writeInt(snapshot.calendarDay);writeInt(snapshot.dayIndex)
                writeUTF(snapshot.purpose.name);writeLong(snapshot.estimatedMinutes)
                writeInt(snapshot.targets.size)
                for(t in snapshot.targets) {
                    writeUTF(t.exerciseId);writeUTF(t.role.name)
                    writeUTF(t.purpose.name);writeUTF(t.setKey)
                    writeUTF(t.type.name);writeInt(t.ordinal)
                    when(val load=t.load) {
                        DraftSetLoad.ExplicitBodyweight -> writeBoolean(false)
                        is DraftSetLoad.Verified -> {
                            writeBoolean(true)
                            writeUTF(load.unit.name);writeUTF(load.basis.name)
                            writeUTF(load.value.stripTrailingZeros().toPlainString())
                        }
                    }
                    writeInt(t.reps.minimum);writeInt(t.reps.maximum)
                    val targetRir=t.targetRir
                    writeBoolean(targetRir != null)
                    if(targetRir != null)writeInt(targetRir)
                    writeInt(t.activeSeconds);writeInt(t.restSeconds)
                }
            }
        }
        return hash(buffer.toByteArray())
    }

    fun encode(saved: SavedReceipts): ByteArray {
        require(saved.receipts.size<=MAX_EVENTS)
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            with(out) {
                writeInt(MAGIC);writeInt(VERSION)
                writeUTF(saved.ownerKey);writeUTF(saved.snapshotHash)
                writeUTF(saved.identity.storageReceiptId)
                writeUTF(saved.identity.workoutId)
                writeLong(saved.identity.storedRevision)
                writeLong(saved.revision)
                optional(saved.finishId)
                writeInt(saved.receipts.size)
                for(receipt in saved.receipts) {
                    writeLong(receipt.appliedRevision)
                    val op=receipt.operation
                    writeUTF(op.eventId);writeLong(op.expectedJournalRevision)
                    when(op) {
                        is KotlinJournalOperation.Finish -> writeByte(1)
                        is KotlinJournalOperation.RecordSet -> {
                            writeByte(2)
                            writeUTF(op.setKey);writeUTF(op.outcome.name)
                            optional(op.correctsEventId)
                            val performed=op.performed
                            writeBoolean(performed != null)
                            if(performed != null) {
                                val actualLoad=performed.load
                                writeBoolean(actualLoad!=null)
                                if(actualLoad!=null)
                                    writeUTF(actualLoad.stripTrailingZeros().toPlainString())
                                optional(performed.unit?.name)
                                writeInt(performed.reps)
                                val actualRir=performed.actualRir
                                writeBoolean(actualRir!=null)
                                if(actualRir!=null)writeInt(actualRir)
                            }
                        }
                    }
                }
            }
        }
        val payload=body.toByteArray()
        require(payload.size<=MAX_BYTES-36)
        val digest=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(payload.size)
                out.write(payload)
                out.write(digest)
            }
        }.toByteArray()
    }

    fun decode(raw: ByteArray, source: KotlinTrainingSourceRevision): SavedReceipts {
        if(raw.size !in 40..MAX_BYTES)throw IOException("invalid journal size")
        DataInputStream(ByteArrayInputStream(raw)).use { stream ->
            val size=stream.readInt()
            if(size<8 || size>MAX_BYTES-36 || size+36!=raw.size)
                throw IOException("malformed journal envelope")
            val data=ByteArray(size)
            stream.readFully(data)
            val expected=ByteArray(32)
            stream.readFully(expected)
            if(!MessageDigest.isEqual(
                    MessageDigest.getInstance("SHA-256").digest(data),expected))
                throw IOException("journal checksum mismatch")
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                with(input) {
                    if(readInt()!=MAGIC || readInt()!=VERSION)
                        throw IOException("unknown journal encoding")
                    val ownerKey=readUTF()
                    val snapshotHash=readUTF()
                    val receiptId=readUTF()
                    val workoutId=readUTF()
                    val issuedRevision=readLong()
                    val revision=readLong()
                    val finishId=optional()
                    val count=readInt()
                    if(count !in 0..MAX_EVENTS || revision!=count.toLong())
                        throw IOException("invalid journal receipt count")
                    val receipts=ArrayList<KotlinJournalReceipt>(count)
                    repeat(count) {
                        val applied=readLong()
                        val eventId=readUTF()
                        val expectedRevision=readLong()
                        val operation=when(readUnsignedByte()) {
                            1 -> KotlinJournalOperation.Finish(eventId,expectedRevision)
                            2 -> {
                                val setKey=readUTF()
                                val outcomeName=readUTF()
                                val outcome=KotlinJournalSetOutcome.entries
                                    .firstOrNull { it.name==outcomeName }
                                if(outcome==null)throw IOException("unknown set outcome")
                                val corrects=optional()
                                val performed=if(readBoolean()) {
                                    val load=if(readBoolean())
                                        BigDecimal(readUTF()) else null
                                    val unit=optional()?.let { name ->
                                        LoadUnit.entries.firstOrNull { it.name==name }
                                            ?:throw IOException("unknown load unit")
                                    }
                                    val reps=readInt()
                                    val rir=if(readBoolean())readInt() else null
                                    KotlinPerformedSet(load,unit,reps,rir)
                                } else null
                                KotlinJournalOperation.RecordSet(eventId,expectedRevision,
                                    setKey,outcome,performed,corrects)
                            }
                            else -> throw IOException("unknown journal operation")
                        }
                        receipts.add(KotlinJournalReceipt(applied,operation))
                    }
                    if(available()!=0)throw IOException("trailing journal bytes")
                    return SavedReceipts(ownerKey,snapshotHash,
                        KotlinStoredWorkoutIdentity(receiptId,workoutId,source,
                            issuedRevision),receipts,revision,finishId)
                }
            }
        }
    }
}

/**
 * Uses a dedicated OS file lock and atomic replace for every issue/append.
 * There is deliberately no unsafe, non-atomic write fallback. A filesystem
 * error returns a refusal, never an optimistic success animation.
 */
class KotlinNativeJournalFileStore(private val privateDirectory: Path) {
    companion object {
        // JVM file locks are process-wide: two threads otherwise receive an
        // OverlappingFileLockException instead of waiting for their turn.
        // One short in-process mutex plus the OS lock also serializes writers
        // created from different KotlinNativeJournalFileStore instances.
        private val processMutex=Any()
    }
    private val ownerId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun path(ownerKey:String,workoutId:String): Path {
        val digest=NativeJournalBinary.hash(
            (ownerKey+"\u0000"+workoutId).toByteArray(Charsets.UTF_8))
        return privateDirectory.resolve("journal-$digest.bin")
    }

    private fun <T> locked(
        ownerKey:String,
        workoutId:String,
        action:(Path)->T
    ): T {
        Files.createDirectories(privateDirectory)
        val file=path(ownerKey,workoutId)
        val lock=file.resolveSibling(file.fileName.toString()+".lock")
        return synchronized(processMutex) {
            FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE)
                .use { channel ->
                    channel.lock().use { action(file) }
                }
        }
    }

    private fun atomicWrite(file:Path,bytes:ByteArray) {
        val pending=Files.createTempFile(privateDirectory,
            "native-journal-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                var remaining=ByteBuffer.wrap(bytes)
                while(remaining.hasRemaining()) channel.write(remaining)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch(ex:AtomicMoveNotSupportedException) {
                throw IOException("atomic journal writes unsupported",ex)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    private fun checkInputs(
        ownerKey:String,
        snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity
    ): Boolean = ownerId.matches(ownerKey) &&
        runCatching { KotlinWorkoutJournalDomain.begin(snapshot,identity) }.isSuccess

    private fun recover(
        file:Path,ownerKey:String,snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity
    ): NativeJournalFileResult {
        if(!Files.exists(file))
            return NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_ISSUED)
        val envelope=try {
            if(Files.size(file)>2_000_000L)throw IOException("oversize journal")
            NativeJournalBinary.decode(Files.readAllBytes(file),snapshot.source)
        } catch(_:Exception) {
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        }
        if(envelope.ownerKey!=ownerKey || envelope.identity!=identity ||
            envelope.snapshotHash!=NativeJournalBinary.snapshotHash(snapshot))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.SOURCE_CONFLICT)
        val recovered=KotlinWorkoutJournalReplayDomain.replay(
            snapshot,identity,envelope.receipts,envelope.revision,envelope.finishId)
        if(recovered !is KotlinJournalReplayResult.Recovered)
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        return NativeJournalFileResult.Durable(recovered.journal,true)
    }

    /** Must only be called AFTER the native issuance owner authorizes and
     * pins the full snapshot in an independent durable transaction. */
    fun issue(
        ownerKey:String,snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity
    ): NativeJournalFileResult {
        if(!checkInputs(ownerKey,snapshot,identity))
            return NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,identity.workoutId) { file ->
                if(Files.exists(file)) {
                    val previous=recover(file,ownerKey,snapshot,identity)
                    if(previous is NativeJournalFileResult.Durable)previous
                    else if(previous is NativeJournalFileResult.Rejected &&
                        previous.reason==NativeJournalFileRefusal.NOT_ISSUED)previous
                    else NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.ISSUE_CONFLICT)
                } else {
                    val initial=KotlinWorkoutJournalDomain.begin(snapshot,identity)
                    val envelope=SavedReceipts(ownerKey,
                        NativeJournalBinary.snapshotHash(snapshot),identity,
                        emptyList(),0,null)
                    atomicWrite(file,NativeJournalBinary.encode(envelope))
                    NativeJournalFileResult.Durable(initial,false)
                }
            }
        }.getOrElse { NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE) }
    }

    /** Cold-open an EXISTING journal; this never silently issues one. */
    fun open(
        ownerKey:String,snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity
    ): NativeJournalFileResult {
        if(!checkInputs(ownerKey,snapshot,identity))
            return NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,identity.workoutId) { file ->
                recover(file,ownerKey,snapshot,identity)
            }
        }.getOrElse { NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE) }
    }

    /** Atomic CAS, with receipt replay for lost acknowledgments. */
    fun append(
        ownerKey:String,snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity,
        operation:KotlinJournalOperation
    ): NativeJournalFileResult {
        if(!checkInputs(ownerKey,snapshot,identity))
            return NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,identity.workoutId) { file ->
                val prior=recover(file,ownerKey,snapshot,identity)
                if(prior !is NativeJournalFileResult.Durable)return@locked prior
                val decision=KotlinWorkoutJournalDomain.reduce(prior.journal,operation)
                if(decision is KotlinJournalDecision.Rejected)
                    return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.JOURNAL_REJECTED,decision.reason)
                decision as KotlinJournalDecision.Proposed
                if(!decision.replayed) {
                    val next=decision.state
                    val envelope=SavedReceipts(ownerKey,
                        NativeJournalBinary.snapshotHash(snapshot),identity,
                        next.receipts,next.revision,next.finishedByEventId)
                    atomicWrite(file,NativeJournalBinary.encode(envelope))
                }
                NativeJournalFileResult.Durable(decision.state,decision.replayed)
            }
        }.getOrElse { NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE) }
    }
}
