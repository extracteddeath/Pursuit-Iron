package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomCoachAuditInputs
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
 * Athlete signoff for a FULLY APPLIED future-block activation.
 *
 * This is a durable review/consent boundary, never a workout grant. New
 * Composer and custom issuance remain blocked by the activation handoff.
 * A later issuer must verify this consent AND install the precise scoped
 * policy atomically before clearing the barrier. Historical issued sessions
 * retain their previous immutable authorization.
 */
data class NativeActivatedCycleSignoff(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val firstWeek:Int,
    val sourceRevision:Long,
    val recordRevision:Long,
    val reviewFingerprint:String
) {
    val mayIssueWorkout:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

enum class NativeActivatedSignoffRefusal {
    NOT_AUTHORIZED, REVIEW_NOT_READY, FINGERPRINT_CHANGED,
    REVISION_CONFLICT, CONFIRMATION_REQUIRED, RECORD_CORRUPT,
    STORAGE_UNAVAILABLE
}
sealed interface NativeActivatedSignoffRead {
    data object Missing:NativeActivatedSignoffRead
    data class Found(val receipt:NativeActivatedCycleSignoff,
        val isCurrent:Boolean):NativeActivatedSignoffRead
    data class Revoked(val revision:Long):NativeActivatedSignoffRead
    data class Rejected(val reason:NativeActivatedSignoffRefusal):
        NativeActivatedSignoffRead
}
sealed interface NativeActivatedSignoffWrite {
    data class Committed(val receipt:NativeActivatedCycleSignoff):
        NativeActivatedSignoffWrite
    data class Revoked(val revision:Long):NativeActivatedSignoffWrite
    data class Rejected(val reason:NativeActivatedSignoffRefusal):
        NativeActivatedSignoffWrite
}

private data class ActivatedSignoffDisk(
    val owner:String,val athlete:String,val revision:Long,
    val consent:NativeActivatedCycleSignoff?
)

class NativeKotlinActivatedCycleSignoffFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val activation:NativeKotlinFutureActivationCommitTransaction
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hex=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494153 // PIAS
    private val version=1
    private val maxRevision=9_007_199_254_740_991L

    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun owner(key:String):NativeKotlinOwnerIdentity? =
        runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{id.matches(key) && it.ownerKey==key && id.matches(it.athleteId)}
    private fun prefix(key:String)="activated-cycle-signoff-"+hash(key.toByteArray(Charsets.UTF_8))
    private fun path(key:String)=directory.resolve(prefix(key)+".bin")

    /**
     * Corruption and interrupted temp writes fail closed. The app must never
     * silently treat an unreadable consent as an absent one.
     */
    private fun pending(key:String):Boolean {
        if(!Files.exists(directory))return false
        if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory))
            throw IOException("Unsafe signoff directory")
        Files.list(directory).use{stream->
            return stream.anyMatch{it.fileName.toString().startsWith(prefix(key)+"-")}
        }
    }

    private fun encode(record:ActivatedSignoffDisk):ByteArray {
        require(id.matches(record.owner)&&id.matches(record.athlete))
        require(record.revision in 1..maxRevision)
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(record.owner);out.writeUTF(record.athlete)
            out.writeLong(record.revision)
            out.writeBoolean(record.consent!=null)
            record.consent?.let{r->
                require(r.ownerKey==record.owner && r.athleteId==record.athlete)
                require(r.recordRevision==record.revision)
                require(id.matches(r.cycleId)&&id.matches(r.blockId))
                require(r.firstWeek in 2..104 && r.sourceRevision in 2..maxRevision)
                require(hex.matches(r.reviewFingerprint))
                out.writeUTF(r.cycleId);out.writeUTF(r.blockId)
                out.writeInt(r.firstWeek);out.writeLong(r.sourceRevision)
                out.writeUTF(r.reviewFingerprint)
            }
        }
        val payload=raw.toByteArray()
        require(payload.size+36<=4096)
        return ByteArrayOutputStream().also{dest->
            DataOutputStream(dest).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }

    private fun decode(key:String):ActivatedSignoffDisk? {
        if(pending(key))throw IOException("Interrupted signoff write")
        val file=path(key)
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||
            Files.size(file) !in 44L..4096L)
            throw IOException("Bad signoff file")
        val bytes=Files.readAllBytes(file)
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            val length=input.readInt()
            if(length !in 8..4060 || length+36!=bytes.size)
                throw IOException("Invalid signoff envelope")
            val payload=ByteArray(length);input.readFully(payload)
            val checksum=ByteArray(32);input.readFully(checksum)
            if(!MessageDigest.isEqual(checksum,
                MessageDigest.getInstance("SHA-256").digest(payload)))
                throw IOException("Signoff checksum mismatch")
            DataInputStream(ByteArrayInputStream(payload)).use{data->
                if(data.readInt()!=magic||data.readInt()!=version)
                    throw IOException("Unsupported signoff version")
                val owner=data.readUTF();val athlete=data.readUTF()
                val revision=data.readLong()
                if(!id.matches(owner)||!id.matches(athlete)||
                    revision !in 1..maxRevision)
                    throw IOException("Invalid signoff scope")
                val consent=if(data.readBoolean()){
                    NativeActivatedCycleSignoff(owner,athlete,
                        data.readUTF(),data.readUTF(),data.readInt(),
                        data.readLong(),revision,data.readUTF())
                } else null
                if(data.available()!=0)throw IOException("Trailing signoff data")
                ActivatedSignoffDisk(owner,athlete,revision,consent)
                    .also{encode(it)}
            }
        }
    }

    private fun atomic(record:ActivatedSignoffDisk) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory))
            throw IOException("Unsafe signoff directory")
        val temporary=Files.createTempFile(directory,prefix(record.owner)+"-", ".pending")
        try{
            val bytes=encode(record)
            FileChannel.open(temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buf=ByteBuffer.wrap(bytes)
                while(buf.hasRemaining())channel.write(buf)
                channel.force(true)
            }
            try {
                Files.move(temporary,path(record.owner),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("Atomic signoff unsupported",ex)
            }
        }finally{Files.deleteIfExists(temporary)}
    }

    private fun current(
        key:String,inputs:CustomCoachAuditInputs
    ):NativeActivatedCycleSignoff? {
        val signed=owner(key)?:return null
        val review=activation.reviewActivatedCycle(key,inputs)
            as? NativeFutureActivatedCycleReview.Ready ?:return null
        if(!hex.matches(review.reviewFingerprint))return null
        return NativeActivatedCycleSignoff(key,signed.athleteId,
            review.cycleId,review.blockId,review.firstWeek,
            review.sourceRevision,0L,review.reviewFingerprint)
    }

    /** Read without audit inputs is never proof of present-day validity. */
    fun read(
        ownerKey:String,inputs:CustomCoachAuditInputs?=null
    ):NativeActivatedSignoffRead =
        try{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeActivatedSignoffRead.Rejected(
                    NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
            val stored=try{decode(ownerKey)}catch(_:Exception){
                return@withOwnerTransaction NativeActivatedSignoffRead.Rejected(
                    NativeActivatedSignoffRefusal.RECORD_CORRUPT)
            }?:return@withOwnerTransaction NativeActivatedSignoffRead.Missing
            if(stored.owner!=signed.ownerKey||stored.athlete!=signed.athleteId)
                return@withOwnerTransaction NativeActivatedSignoffRead.Rejected(
                    NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
            val receipt=stored.consent
                ?:return@withOwnerTransaction NativeActivatedSignoffRead.Revoked(
                    stored.revision)
            val candidate=inputs?.let{current(ownerKey,it)}
            NativeActivatedSignoffRead.Found(receipt,
                candidate!=null &&
                candidate.copy(recordRevision=receipt.recordRevision)==receipt)
        }}catch(_:Exception){NativeActivatedSignoffRead.Rejected(
            NativeActivatedSignoffRefusal.STORAGE_UNAVAILABLE)}

    /** Explicit consent + expected review fingerprint + CAS, all under lock. */
    fun confirm(
        ownerKey:String,expectedRevision:Long,
        expectedFingerprint:String,inputs:CustomCoachAuditInputs,
        athleteConfirmed:Boolean
    ):NativeActivatedSignoffWrite {
        if(!athleteConfirmed)return NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.CONFIRMATION_REQUIRED)
        if(expectedRevision !in 0 until maxRevision ||
            !hex.matches(expectedFingerprint))
            return NativeActivatedSignoffWrite.Rejected(
                NativeActivatedSignoffRefusal.FINGERPRINT_CHANGED)
        return try{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
            val stored=try{decode(ownerKey)}catch(_:Exception){
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.RECORD_CORRUPT)
            }
            if(stored!=null &&
                (stored.owner!=signed.ownerKey||stored.athlete!=signed.athleteId))
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
            if((stored?.revision?:0L)!=expectedRevision)
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.REVISION_CONFLICT)
            val candidate=current(ownerKey,inputs)
                ?:return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.REVIEW_NOT_READY)
            if(candidate.reviewFingerprint!=expectedFingerprint ||
                owner(ownerKey)!=signed)
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.FINGERPRINT_CHANGED)
            val accepted=candidate.copy(recordRevision=expectedRevision+1)
            atomic(ActivatedSignoffDisk(ownerKey,signed.athleteId,
                accepted.recordRevision,accepted))
            NativeActivatedSignoffWrite.Committed(accepted)
        }}catch(_:Exception){NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Revisioned tombstone blocks delayed accept callbacks. */
    fun revoke(
        ownerKey:String,expectedRevision:Long
    ):NativeActivatedSignoffWrite =
        try{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
            val prior=try{decode(ownerKey)}catch(_:Exception){
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.RECORD_CORRUPT)
            }
            if(prior==null || prior.owner!=signed.ownerKey ||
                prior.athlete!=signed.athleteId || prior.consent==null ||
                prior.revision!=expectedRevision ||
                expectedRevision !in 1 until maxRevision)
                return@withOwnerTransaction NativeActivatedSignoffWrite.Rejected(
                    NativeActivatedSignoffRefusal.REVISION_CONFLICT)
            atomic(ActivatedSignoffDisk(ownerKey,signed.athleteId,
                expectedRevision+1,null))
            NativeActivatedSignoffWrite.Revoked(expectedRevision+1)
        }}catch(_:Exception){NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.STORAGE_UNAVAILABLE)}
}
