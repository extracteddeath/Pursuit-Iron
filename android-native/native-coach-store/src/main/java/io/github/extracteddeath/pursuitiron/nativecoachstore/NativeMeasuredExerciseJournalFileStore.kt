package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
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
 * A CURRENT, independently approved measurement, sourced from the active
 * native workout authority under the shared athlete/approval revision fence.
 * Workout screens never construct this authorization or choose their own
 * consent/version. The caller supplying this source must also verify that the
 * set is a member of the exact approved parent workout and current gym,
 * including source ID, equipment, band and physical station.
 *
 * No native program builder currently supplies this proof for the 14
 * non-repetition exercises. Until it does, this store cannot issue them.
 */
data class NativeMeasuredApprovedSet(
    val plan:MeasuredExerciseSetPlan,
    val approvalId:String,
    val approvalRevision:Long
)

fun interface NativeMeasuredApprovedSetSource {
    fun currentApprovedSet(
        ownerKey:String,athleteId:String,workoutId:String,setKey:String
    ):NativeMeasuredApprovedSet?
}

data class NativeMeasuredStoredSet(
    val ownerKey:String,
    val athleteId:String,
    val approvalId:String,
    val approvalRevision:Long,
    val journal:MeasuredExerciseJournal
) {
    /** Durable individual set facts, NOT a completed History transaction. */
    val isIssued:Boolean get()=true
    val canFinishWorkout:Boolean get()=false
    val mayPublishHistory:Boolean get()=false
}

enum class NativeMeasuredStoreRefusal {
    INVALID_ID, NOT_AUTHORIZED, APPROVAL_UNAVAILABLE,
    NOT_ISSUED, CORRUPT_RECORD, STALE_OR_INVALID_RECEIPT,
    HISTORY_TRANSACTION_REQUIRED, STORAGE_UNAVAILABLE
}

sealed interface NativeMeasuredStoreResult {
    data class Durable(
        val stored:NativeMeasuredStoredSet,
        val replayed:Boolean
    ):NativeMeasuredStoreResult
    data class Rejected(
        val reason:NativeMeasuredStoreRefusal,
        val journalReason:MeasurementRefusal?=null
    ):NativeMeasuredStoreResult
}

/**
 * Offline, owner-fenced, CAS-revisioned sidecar for the 14 typed exercises.
 * Exact plans are selected by an approved native source, never by the UI.
 * Journal records and their original issuance metadata survive process death.
 * No separate JavaScript engine, alternate load representation or automatic
 * conversion from seconds/meters/band resistance into repetition history.
 *
 * Finish is purposely absent until a native multi-set parent journal/history
 * transaction exists. This is a storage integration candidate, not a beta
 * permission to expose these exercises as usable training.
 *
 * The application must supply a private no-backup root, a trusted current
 * identity, and an approved-plan source that participates in the SAME fence
 * for account, program and gym updates. SHA-256 detects corruption, not a
 * malicious actor who can rewrite the private file.
 */
class NativeMeasuredExerciseJournalFileStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val approvals:NativeMeasuredApprovedSetSource
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494D53 // PIMS, different from PIM1 / PIJ / PICJ
    private val version=1
    private val maxBytes=NativeMeasuredExerciseJournalCodec.MAX_BYTES+4096

    private fun active(owner:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(owner))return null
        return runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{it.ownerKey==owner && id.matches(it.athleteId)}
    }
    private fun file(owner:String,workout:String,set:String):Path {
        val hash=MessageDigest.getInstance("SHA-256").digest(
            (owner+"\u0000"+workout+"\u0000"+set).toByteArray(Charsets.UTF_8)
        ).joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("measured-issued-$hash.bin")
    }
    private fun encode(stored:NativeMeasuredStoredSet):ByteArray {
        require(listOf(stored.ownerKey,stored.athleteId,
            stored.approvalId).all(id::matches) &&
            stored.approvalRevision>=1 &&
            stored.journal.finishedByEventId==null)
        val payload=NativeMeasuredExerciseJournalCodec.encode(
            stored.ownerKey,stored.athleteId,stored.journal)
        val data=ByteArrayOutputStream()
        DataOutputStream(data).use{out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(stored.ownerKey);out.writeUTF(stored.athleteId)
            out.writeUTF(stored.approvalId);out.writeLong(stored.approvalRevision)
            out.writeInt(payload.size);out.write(payload)
        }
        val body=data.toByteArray()
        require(body.size+36<=maxBytes)
        return ByteArrayOutputStream().also{buf->
            DataOutputStream(buf).use{out->
                out.writeInt(body.size);out.write(body)
                out.write(MessageDigest.getInstance("SHA-256").digest(body))
            }
        }.toByteArray()
    }

    private fun decode(
        path:Path,owner:String,athlete:String,workout:String,set:String
    ):NativeMeasuredStoredSet? {
        if(!Files.exists(path))return null
        if(Files.isSymbolicLink(path) || !Files.isRegularFile(path) ||
            Files.size(path) !in 44L..maxBytes.toLong())
            throw IOException("invalid measured journal file")
        val bytes=Files.readAllBytes(path)
        DataInputStream(ByteArrayInputStream(bytes)).use{envelope->
            val size=envelope.readInt()
            if(size !in 16..maxBytes-36 || size+36!=bytes.size)
                throw IOException("invalid measured journal size")
            val body=ByteArray(size);envelope.readFully(body)
            val sum=ByteArray(32);envelope.readFully(sum)
            if(!MessageDigest.isEqual(sum,
                    MessageDigest.getInstance("SHA-256").digest(body)))
                throw IOException("measured journal checksum mismatch")
            DataInputStream(ByteArrayInputStream(body)).use{input->
                if(input.readInt()!=magic || input.readInt()!=version)
                    throw IOException("unknown measured journal file schema")
                val savedOwner=input.readUTF()
                val savedAthlete=input.readUTF()
                val approvalId=input.readUTF()
                val approvalRevision=input.readLong()
                val length=input.readInt()
                if(savedOwner!=owner || savedAthlete!=athlete ||
                    !id.matches(approvalId) || approvalRevision<1 ||
                    length !in 44..NativeMeasuredExerciseJournalCodec.MAX_BYTES ||
                    length>input.available())
                    throw IOException("untrusted measured journal metadata")
                val journalBytes=ByteArray(length);input.readFully(journalBytes)
                if(input.available()!=0)
                    throw IOException("trailing measured journal metadata")
                val journal=NativeMeasuredExerciseJournalCodec.decode(
                    journalBytes,owner,athlete)
                if(journal.immutablePlan.workoutId!=workout ||
                    journal.immutablePlan.setKey!=set ||
                    journal.finishedByEventId!=null)
                    throw IOException("unexpected measured workout identity or Finish")
                return NativeMeasuredStoredSet(owner,athlete,
                    approvalId,approvalRevision,journal)
            }
        }
    }

    private fun commit(file:Path,bytes:ByteArray) {
        require(bytes.size in 44..maxBytes)
        if(Files.isSymbolicLink(root))throw IOException("unsafe measured store directory")
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root) || !Files.isDirectory(root) ||
            Files.isSymbolicLink(file))
            throw IOException("unsafe measured journal path")
        val temporary=Files.createTempFile(root,"measured-written-",".pending")
        try {
            FileChannel.open(temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch(ex:AtomicMoveNotSupportedException) {
                throw IOException("atomic measured journal commit unavailable",ex)
            }
        } finally {Files.deleteIfExists(temporary)}
        if(!Files.readAllBytes(file).contentEquals(bytes))
            throw IOException("measured journal commit verification failed")
    }

    private fun existing(
        owner:String,athlete:String,workout:String,set:String
    ):NativeMeasuredStoreResult {
        val stored=try{decode(file(owner,workout,set),owner,athlete,workout,set)}
            catch(_:Exception){return NativeMeasuredStoreResult.Rejected(
                NativeMeasuredStoreRefusal.CORRUPT_RECORD)}
            ?:return NativeMeasuredStoreResult.Rejected(
                NativeMeasuredStoreRefusal.NOT_ISSUED)
        return NativeMeasuredStoreResult.Durable(stored,true)
    }

    fun open(
        ownerKey:String,workoutId:String,setKey:String
    ):NativeMeasuredStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId) || !id.matches(setKey))
            return NativeMeasuredStoreResult.Rejected(NativeMeasuredStoreRefusal.INVALID_ID)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signedIn=active(ownerKey) ?:
                    return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
                existing(ownerKey,signedIn.athleteId,workoutId,setKey)
            }
        }.getOrElse{NativeMeasuredStoreResult.Rejected(
            NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * First issuance asks the trusted owner for the CURRENT approved plan.
     * Existing sessions reopen after later legitimate program/gym edits, but
     * may never be regenerated with a different target or source.
     */
    fun issue(
        ownerKey:String,workoutId:String,setKey:String
    ):NativeMeasuredStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId) || !id.matches(setKey))
            return NativeMeasuredStoreResult.Rejected(NativeMeasuredStoreRefusal.INVALID_ID)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signedIn=active(ownerKey) ?:
                    return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
                val prior=existing(ownerKey,signedIn.athleteId,workoutId,setKey)
                if(prior !is NativeMeasuredStoreResult.Rejected ||
                    prior.reason!=NativeMeasuredStoreRefusal.NOT_ISSUED)
                    return@withOwnerTransaction prior
                val approval=approvals.currentApprovedSet(ownerKey,
                    signedIn.athleteId,workoutId,setKey)
                    ?:return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
                val plan=approval.plan
                if(plan.workoutId!=workoutId || plan.setKey!=setKey ||
                    !id.matches(approval.approvalId) ||
                    approval.approvalRevision<1 ||
                    MeasuredExerciseDomain.inspect(plan) !is MeasurementPlanResult.Candidate)
                    return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
                val initial=MeasuredExerciseJournalDomain.begin(plan)
                    ?:return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.APPROVAL_UNAVAILABLE)
                val record=NativeMeasuredStoredSet(ownerKey,signedIn.athleteId,
                    approval.approvalId,approval.approvalRevision,initial)
                val path=file(ownerKey,workoutId,setKey)
                commit(path,encode(record))
                val verified=decode(path,ownerKey,signedIn.athleteId,workoutId,setKey)
                    ?:return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)
                if(verified!=record)
                    return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)
                NativeMeasuredStoreResult.Durable(verified,false)
            }
        }.getOrElse{NativeMeasuredStoreResult.Rejected(
            NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Persist one independently entered set observation, skip or correction.
     * A retry with the same receipt ID is acknowledged only if the ENTIRE
     * operation matches. No UI call can append Finish to this sidecar.
     */
    fun record(
        ownerKey:String,workoutId:String,setKey:String,
        operation:MeasuredJournalOperation.Record
    ):NativeMeasuredStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId) || !id.matches(setKey))
            return NativeMeasuredStoreResult.Rejected(NativeMeasuredStoreRefusal.INVALID_ID)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signedIn=active(ownerKey) ?:
                    return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                        NativeMeasuredStoreRefusal.NOT_AUTHORIZED)
                val prior=existing(ownerKey,signedIn.athleteId,workoutId,setKey)
                val old=(prior as? NativeMeasuredStoreResult.Durable)?.stored
                    ?:return@withOwnerTransaction prior
                when(val candidate=MeasuredExerciseJournalDomain.reduce(
                    old.journal,operation)) {
                    is MeasuredJournalDecision.Rejected ->
                        NativeMeasuredStoreResult.Rejected(
                            NativeMeasuredStoreRefusal.STALE_OR_INVALID_RECEIPT,
                            candidate.reason)
                    is MeasuredJournalDecision.Proposed -> {
                        if(candidate.replayed)
                            return@withOwnerTransaction NativeMeasuredStoreResult.Durable(
                                old,true)
                        val next=old.copy(journal=candidate.journal)
                        val path=file(ownerKey,workoutId,setKey)
                        commit(path,encode(next))
                        val verified=decode(path,ownerKey,signedIn.athleteId,
                            workoutId,setKey)
                            ?:return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                                NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)
                        if(verified!=next)
                            return@withOwnerTransaction NativeMeasuredStoreResult.Rejected(
                                NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)
                        NativeMeasuredStoreResult.Durable(verified,false)
                    }
                }
            }
        }.getOrElse{NativeMeasuredStoreResult.Rejected(
            NativeMeasuredStoreRefusal.STORAGE_UNAVAILABLE)}
    }
}
