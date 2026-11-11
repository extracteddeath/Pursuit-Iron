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
 * The snapshot and identity are supplied by the native issuance authority.
 * From v2 the same atomic envelope stores the WHOLE immutable issued snapshot
 * and all receipts, so cold recovery never needs the old JavaScript engine.
 * Authentication of a current athlete and approved plan is still a separate
 * app-owner cutover gate.
 */
enum class NativeJournalFileRefusal {
    INVALID_ARGUMENT, NOT_ISSUED, ISSUE_CONFLICT, SOURCE_CONFLICT,
    CORRUPT_RECEIPTS, JOURNAL_REJECTED, STORAGE_UNAVAILABLE,
    NOT_AUTHORIZED, HISTORY_NOT_READY, SWAP_SOURCE_UNAVAILABLE,
    SWAP_INVALID_TARGET
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

/**
 * Complete physical native-journal inventory. This is NOT a chronology:
 * journal files have no authenticated completion timestamp or order.
 * All discovered files are replayed, including unfinished sessions.
 */
sealed interface NativeKotlinJournalInventoryResult {
    data class Verified(val journals: List<KotlinWorkoutJournalState>) :
        NativeKotlinJournalInventoryResult
    data class Rejected(val reason: NativeKotlinJournalInventoryRefusal) :
        NativeKotlinJournalInventoryResult
}
enum class NativeKotlinJournalInventoryRefusal {
    INVALID_OWNER, UNREADABLE_OR_UNEXPECTED_FILE, UNAUTHORIZED_WORKOUT,
    WRONG_ATHLETE, TOO_MANY_FILES
}

private data class SavedReceipts(
    val ownerKey: String,
    val snapshotHash: String,
    val snapshot: KotlinStagedWorkout,
    val identity: KotlinStoredWorkoutIdentity,
    val receipts: List<KotlinJournalReceipt>,
    val revision: Long,
    val finishId: String?,
    val originalSnapshot:KotlinStagedWorkout=snapshot
)

private object NativeJournalBinary {
    private const val MAGIC = 0x50494A32 // "PIJ2" (not a legacy JS journal)
    private const val VERSION = 5
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

    /** Hash EVERY issued set field using the strict snapshot codec. */
    fun snapshotHash(snapshot: KotlinStagedWorkout): String =
        hash(KotlinIssuedSnapshotCodec.encode(snapshot))

    fun encode(saved: SavedReceipts): ByteArray {
        require(saved.receipts.size<=MAX_EVENTS)
        val swapped=saved.receipts.any{
            it.operation is KotlinJournalOperation.SwapExercise
        }
        require(swapped || saved.originalSnapshot==saved.snapshot)
        val notes=saved.receipts.any {
            it.operation is KotlinJournalOperation.SessionNote
        }
        // Snapshot and finish digest identities for existing v2/v3 journals
        // remain byte-identical until the FIRST note receipt is appended.
        // V5 only when machine receipts exist; legacy v2/v3/v4 journals remain
        // byte-identical and can still be cold-replayed without migration.
        val machineTagged=saved.receipts.any{
            (it.operation as? KotlinJournalOperation.RecordSet)
                ?.performed?.machineInstanceId!=null
        }
        val version=if(machineTagged) VERSION else if(notes) 4 else if(swapped) 3 else 2
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            with(out) {
                writeInt(MAGIC);writeInt(version)
                writeUTF(saved.ownerKey);writeUTF(saved.snapshotHash)
                val originalBytes=KotlinIssuedSnapshotCodec.encode(saved.originalSnapshot)
                require(saved.snapshotHash==hash(originalBytes))
                writeInt(originalBytes.size);write(originalBytes)
                if(version>=3) {
                    val effective=KotlinIssuedSnapshotCodec.encode(saved.snapshot)
                    writeInt(effective.size);write(effective)
                }
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
                        is KotlinJournalOperation.SwapExercise -> {
                            writeByte(3)
                            writeUTF(op.previousExerciseId)
                            writeInt(op.replacementTargets.size)
                            op.replacementTargets.forEach {target->
                                val bytes=KotlinIssuedSnapshotCodec.encode(
                                    saved.snapshot.copy(targets=listOf(target)))
                                writeInt(bytes.size);write(bytes)
                            }
                        }
                        is KotlinJournalOperation.UndoSwap -> {
                            writeByte(4);writeUTF(op.swapEventId)
                        }
                        is KotlinJournalOperation.SessionNote -> {
                            require(KotlinWorkoutJournalDomain.validSessionNote(op.text))
                            writeByte(5);writeUTF(op.text)
                        }
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
                                if(version>=5)optional(performed.machineInstanceId)
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

    fun decode(raw: ByteArray): SavedReceipts {
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
                    if(readInt()!=MAGIC)throw IOException("unknown journal encoding")
                    val version=readInt()
                    if(version !in 2..VERSION)
                        throw IOException("unsupported journal encoding")
                    val ownerKey=readUTF()
                    val snapshotHash=readUTF()
                    val snapshotSize=readInt()
                    if(snapshotSize !in 8..250_000 || snapshotSize>available())
                        throw IOException("invalid embedded snapshot length")
                    val snapshotBytes=ByteArray(snapshotSize)
                    readFully(snapshotBytes)
                    val original=KotlinIssuedSnapshotCodec.decode(snapshotBytes)
                    if(snapshotHash!=hash(snapshotBytes))
                        throw IOException("embedded snapshot hash mismatch")
                    val snapshot=if(version>=3) {
                        val size=readInt()
                        if(size !in 8..250_000 || size>available())
                            throw IOException("invalid effective snapshot length")
                        ByteArray(size).also{readFully(it)}.let{
                            KotlinIssuedSnapshotCodec.decode(it)
                        }
                    } else original
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
                        val kind=readUnsignedByte()
                        if(version<3 && kind>=3)
                            throw IOException("swap operation in legacy journal")
                        val operation=when(kind) {
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
                                    val machine=if(version>=5)optional() else null
                                    if(machine!=null &&
                                        !KotlinMachineAlternativesDomain.validInstanceId(machine))
                                        throw IOException("invalid saved machine instance")
                                    KotlinPerformedSet(load,unit,reps,rir,machine)
                                } else null
                                KotlinJournalOperation.RecordSet(eventId,expectedRevision,
                                    setKey,outcome,performed,corrects)
                            }
                            3 -> {
                                val previous=readUTF()
                                val targetCount=readInt()
                                if(targetCount !in 1..32)
                                    throw IOException("invalid swap target count")
                                val targets=List(targetCount) {
                                    val size=readInt()
                                    if(size !in 8..250_000 || size>available())
                                        throw IOException("invalid swap target length")
                                    val bytes=ByteArray(size)
                                    readFully(bytes)
                                    val single=KotlinIssuedSnapshotCodec.decode(bytes)
                                    if(single.copy(targets=original.targets)!=original)
                                        throw IOException("swap changed workout identity")
                                    single.targets.singleOrNull()
                                        ?:throw IOException("expected one replacement set")
                                }
                                KotlinJournalOperation.SwapExercise(
                                    eventId,expectedRevision,previous,targets)
                            }
                            4 -> KotlinJournalOperation.UndoSwap(
                                eventId,expectedRevision,readUTF())
                            5 -> {
                                if(version<4)throw IOException("note in legacy journal")
                                val text=readUTF()
                                if(!KotlinWorkoutJournalDomain.validSessionNote(text))
                                    throw IOException("invalid journal note")
                                KotlinJournalOperation.SessionNote(
                                    eventId,expectedRevision,text)
                            }
                            else -> throw IOException("unknown journal operation")
                        }
                        receipts.add(KotlinJournalReceipt(applied,operation))
                    }
                    if(available()!=0)throw IOException("trailing journal bytes")
                    return SavedReceipts(ownerKey,snapshotHash,snapshot,
                        KotlinStoredWorkoutIdentity(receiptId,workoutId,
                            original.source,issuedRevision),receipts,revision,
                        finishId,original)
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
            NativeJournalBinary.decode(Files.readAllBytes(file))
        } catch(_:Exception) {
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        }
        if(envelope.ownerKey!=ownerKey || envelope.identity!=identity ||
            envelope.originalSnapshot!=snapshot ||
            envelope.snapshotHash!=NativeJournalBinary.snapshotHash(snapshot))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.SOURCE_CONFLICT)
        val recovered=KotlinWorkoutJournalReplayDomain.replay(
            snapshot,identity,envelope.receipts,envelope.revision,envelope.finishId)
        if(recovered !is KotlinJournalReplayResult.Recovered ||
            recovered.journal.snapshot!=envelope.snapshot)
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        return NativeJournalFileResult.Durable(recovered.journal,true)
    }

    /**
     * Inventory EVERY physical Kotlin-native journal, not caller-picked IDs.
     * The surrounding owner fence must prevent concurrent writers; direct
     * low-level writers outside that fence remain a release blocker.
     *
     * Corrupt files belonging to another owner also block the scan because
     * their true owner cannot be proven from their bytes. No silent omission.
     */
    fun inventoryOwner(
        ownerKey: String, athleteId: String,
        authorizeCurrent: (String, KotlinTrainingSourceRevision) -> Boolean
    ): NativeKotlinJournalInventoryResult {
        fun reject(reason: NativeKotlinJournalInventoryRefusal) =
            NativeKotlinJournalInventoryResult.Rejected(reason)
        if (!ownerId.matches(ownerKey) || !ownerId.matches(athleteId))
            return reject(NativeKotlinJournalInventoryRefusal.INVALID_OWNER)
        return runCatching {
            Files.createDirectories(privateDirectory)
            val records = Files.list(privateDirectory).use { stream ->
                stream.filter { file ->
                    val name = file.fileName.toString()
                    name.startsWith("journal-") && name.endsWith(".bin")
                }.iterator().asSequence().toList()
            }
            if (records.size > 2048)
                return@runCatching reject(
                    NativeKotlinJournalInventoryRefusal.TOO_MANY_FILES)
            // Unreconciled atomic-writing artifacts cannot be ignored.
            val pending = Files.list(privateDirectory).use { stream ->
                stream.anyMatch { file ->
                    val name = file.fileName.toString()
                    name.startsWith("native-journal-") &&
                        name.endsWith(".pending")
                }
            }
            if (pending) return@runCatching reject(
                NativeKotlinJournalInventoryRefusal.UNREADABLE_OR_UNEXPECTED_FILE)
            val found = ArrayList<KotlinWorkoutJournalState>()
            for (file in records.sortedBy { it.fileName.toString() }) {
                val saved = runCatching {
                    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
                        Files.size(file) > 2_000_000L)
                        throw IOException("unsafe journal inventory file")
                    NativeJournalBinary.decode(Files.readAllBytes(file))
                }.getOrNull() ?: return@runCatching reject(
                    NativeKotlinJournalInventoryRefusal.UNREADABLE_OR_UNEXPECTED_FILE)
                val workoutId = saved.snapshot.workoutId
                if (!ownerId.matches(saved.ownerKey) ||
                    !ownerId.matches(workoutId) ||
                    saved.identity.workoutId != workoutId ||
                    saved.identity.source != saved.snapshot.source ||
                    file.fileName.toString() !=
                        path(saved.ownerKey, workoutId).fileName.toString())
                    return@runCatching reject(
                        NativeKotlinJournalInventoryRefusal.UNREADABLE_OR_UNEXPECTED_FILE)
                if (saved.ownerKey != ownerKey) continue
                if (saved.snapshot.source.athleteId != athleteId)
                    return@runCatching reject(
                        NativeKotlinJournalInventoryRefusal.WRONG_ATHLETE)
                val restored = openStored(ownerKey, workoutId, authorizeCurrent)
                if (restored !is NativeJournalFileResult.Durable)
                    return@runCatching reject(
                        if (restored is NativeJournalFileResult.Rejected &&
                            restored.reason == NativeJournalFileRefusal.NOT_AUTHORIZED)
                            NativeKotlinJournalInventoryRefusal.UNAUTHORIZED_WORKOUT
                        else NativeKotlinJournalInventoryRefusal.UNREADABLE_OR_UNEXPECTED_FILE)
                found.add(restored.journal)
            }
            NativeKotlinJournalInventoryResult.Verified(found.toList())
        }.getOrElse {
            reject(NativeKotlinJournalInventoryRefusal.UNREADABLE_OR_UNEXPECTED_FILE)
        }
    }

    /**
     * Restore a complete issued Kotlin workout directly from private native
     * bytes. The authorization callback MUST be implemented by the native
     * authenticated account/session owner, not the caller or the coach engine.
     * It is checked while holding the store lock against the SAVED source.
     * A stale or untrusted callback must deny even if the checksum is valid.
     *
     * Old `open(ownerKey,snapshot,identity)` remains a lower-level migration
     * test API; production UI must use this owner-verified cold-open path.
     */
    fun openStored(
        ownerKey: String,
        workoutId: String,
        authorizeCurrent: (String,KotlinTrainingSourceRevision)->Boolean
    ): NativeJournalFileResult {
        if(!ownerId.matches(ownerKey) || !ownerId.matches(workoutId))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,workoutId) { file ->
                recoverSaved(file,ownerKey,workoutId,authorizeCurrent)
            }
        }.getOrElse {
            NativeJournalFileResult.Rejected(NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Append from an authenticated cold-open without receiving caller-made
     * targets, thereby avoiding any source/target drift at the UI boundary.
     * This path still has no access to the production app or to JavaScript.
     */
    fun appendStored(
        ownerKey: String,
        workoutId: String,
        operation: KotlinJournalOperation,
        authorizeCurrent: (String,KotlinTrainingSourceRevision)->Boolean
    ): NativeJournalFileResult {
        // Generic set/Finish routes must never accept UI-invented exercise
        // or target changes. Replacements have a separate trusted builder.
        if(operation is KotlinJournalOperation.SwapExercise ||
            operation is KotlinJournalOperation.UndoSwap)
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.SWAP_SOURCE_UNAVAILABLE)
        if(!ownerId.matches(ownerKey) || !ownerId.matches(workoutId))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,workoutId) { file ->
                val prior=recoverSaved(file,ownerKey,workoutId,authorizeCurrent)
                if(prior !is NativeJournalFileResult.Durable)return@locked prior
                val decision=KotlinWorkoutJournalDomain.reduce(prior.journal,operation)
                if(decision is KotlinJournalDecision.Rejected)
                    return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.JOURNAL_REJECTED,decision.reason)
                decision as KotlinJournalDecision.Proposed
                if(!decision.replayed) {
                    val next=decision.state
                    val source=next.snapshot
                    val envelope=SavedReceipts(ownerKey,
                        NativeJournalBinary.snapshotHash(next.originalSnapshot),
                        source,next.storedIdentity,next.receipts,next.revision,
                        next.finishedByEventId,next.originalSnapshot)
                    atomicWrite(file,NativeJournalBinary.encode(envelope))
                }
                NativeJournalFileResult.Durable(decision.state,decision.replayed)
            }
        }.getOrElse {
            NativeJournalFileResult.Rejected(NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Owner-approved Composer replacement/undo. The trusted factory is
     * evaluated AFTER replaying the physical disk journal under its OS lock.
     * Caller MUST also hold the approval/gym owner revision fence and
     * authenticate current source facts. Never use a UI-supplied factory.
     */
    fun appendReviewedWorkoutSwap(
        ownerKey:String,workoutId:String,
        authorizeCurrent:(String,KotlinTrainingSourceRevision)->Boolean,
        reviewed:(KotlinWorkoutJournalState)->KotlinJournalOperation?
    ):NativeJournalFileResult {
        if(!ownerId.matches(ownerKey) || !ownerId.matches(workoutId))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,workoutId){file->
                val saved=recoverSaved(file,ownerKey,workoutId,authorizeCurrent)
                if(saved !is NativeJournalFileResult.Durable)
                    return@locked saved
                val proposed=reviewed(saved.journal)
                    ?:return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.SWAP_INVALID_TARGET)
                if(proposed !is KotlinJournalOperation.SwapExercise &&
                    proposed !is KotlinJournalOperation.UndoSwap)
                    return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.SWAP_INVALID_TARGET)
                val result=KotlinWorkoutJournalDomain.reduce(
                    saved.journal,proposed)
                if(result is KotlinJournalDecision.Rejected)
                    return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.JOURNAL_REJECTED,result.reason)
                result as KotlinJournalDecision.Proposed
                if(!result.replayed){
                    val next=result.state
                    val original=next.originalSnapshot
                    val payload=SavedReceipts(
                        ownerKey,NativeJournalBinary.snapshotHash(original),
                        next.snapshot,next.storedIdentity,next.receipts,
                        next.revision,next.finishedByEventId,original)
                    atomicWrite(file,NativeJournalBinary.encode(payload))
                }
                NativeJournalFileResult.Durable(result.state,result.replayed)
            }
        }.getOrElse {NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE)}
    }

    private fun recoverSaved(
        file: Path,
        ownerKey: String,
        workoutId: String,
        authorizeCurrent: (String,KotlinTrainingSourceRevision)->Boolean
    ): NativeJournalFileResult {
        if(!Files.exists(file))return NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED)
        val saved=try {
            if(Files.size(file)>2_000_000L)throw IOException("oversize journal")
            NativeJournalBinary.decode(Files.readAllBytes(file))
        } catch(_:Exception) {
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        }
        if(saved.ownerKey!=ownerKey ||
            saved.identity.workoutId!=workoutId ||
            saved.snapshot.workoutId!=workoutId ||
            saved.identity.source!=saved.snapshot.source)
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.SOURCE_CONFLICT)
        if(!runCatching {
            authorizeCurrent(ownerKey,saved.snapshot.source)
        }.getOrDefault(false))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        val restored=KotlinWorkoutJournalReplayDomain.replay(
            saved.originalSnapshot,saved.identity,saved.receipts,
            saved.revision,saved.finishId)
        if(restored !is KotlinJournalReplayResult.Recovered ||
            restored.journal.snapshot!=saved.snapshot)
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.CORRUPT_RECEIPTS)
        return NativeJournalFileResult.Durable(restored.journal,true)
    }

    /**
     * Authorized issue: persist immutable snapshot AND the empty journal in
     * ONE atomic record. This is the production-shaped issuance API; the
     * native host must implement authorizeCurrent using real signed-in state,
     * accepted program/grant and current gym/catalog/rules revisions.
     *
     * We reject instead of creating a session when the owner is unknown.
     * The app does not yet supply this authority; this is an isolated port.
     */
    fun issueAuthorized(
        ownerKey: String,
        snapshot: KotlinStagedWorkout,
        identity: KotlinStoredWorkoutIdentity,
        authorizeCurrent: (String,KotlinTrainingSourceRevision)->Boolean
    ): NativeJournalFileResult {
        if(!checkInputs(ownerKey,snapshot,identity))
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,identity.workoutId) { file ->
                if(!runCatching {authorizeCurrent(ownerKey,snapshot.source)}
                    .getOrDefault(false))
                    return@locked NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.NOT_AUTHORIZED)
                issueLocked(file,ownerKey,snapshot,identity)
            }
        }.getOrElse {
            NativeJournalFileResult.Rejected(NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private fun issueLocked(
        file: Path,
        ownerKey: String,
        snapshot: KotlinStagedWorkout,
        identity: KotlinStoredWorkoutIdentity
    ): NativeJournalFileResult {
        if(Files.exists(file)) {
            val previous=recover(file,ownerKey,snapshot,identity)
            if(previous is NativeJournalFileResult.Durable)return previous
            return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.ISSUE_CONFLICT)
        }
        val initial=KotlinWorkoutJournalDomain.begin(snapshot,identity)
        val envelope=SavedReceipts(ownerKey,
            NativeJournalBinary.snapshotHash(snapshot),snapshot,identity,
            emptyList(),0,null)
        atomicWrite(file,NativeJournalBinary.encode(envelope))
        return NativeJournalFileResult.Durable(initial,false)
    }

    /** Must only be called AFTER the native issuance owner authorizes and
     * pins the full snapshot in an independent durable transaction. */
    internal fun issue(
        ownerKey:String,snapshot:KotlinStagedWorkout,
        identity:KotlinStoredWorkoutIdentity
    ): NativeJournalFileResult {
        if(!checkInputs(ownerKey,snapshot,identity))
            return NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT)
        return runCatching {
            locked(ownerKey,identity.workoutId) { file ->
                issueLocked(file,ownerKey,snapshot,identity)
            }
        }.getOrElse { NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE) }
    }

    /** Cold-open an EXISTING journal; this never silently issues one. */
    internal fun open(
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
    internal fun append(
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
                        NativeJournalBinary.snapshotHash(next.originalSnapshot),
                        next.snapshot,identity,next.receipts,next.revision,
                        next.finishedByEventId,next.originalSnapshot)
                    atomicWrite(file,NativeJournalBinary.encode(envelope))
                }
                NativeJournalFileResult.Durable(decision.state,decision.replayed)
            }
        }.getOrElse { NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.STORAGE_UNAVAILABLE) }
    }
}
