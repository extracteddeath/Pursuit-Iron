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
 * A durable ATHLETE ACCEPTANCE of one already-audited, future-only load option.
 * This receipt is NEVER an adapted draft, active program, workout grant, or
 * history amendment. Every subsequent use must re-review source, history,
 * permissions, coaching settings, inventory and exact proposal fingerprint.
 */
data class NativeFutureWeekLoadConsentReceipt(
    val ownerKey:String,val athleteId:String,
    val cycleId:String,val blockId:String,val recordRevision:Long,
    val absoluteWeek:Int,val weekday:Int,val slotId:String,
    val historyRevision:Long,val draftRevision:Long,val sourceRevision:Long,
    val settingsRevision:Long,val permissionRevision:Long,
    val reviewFingerprint:String,
    val previousLoad:BigDecimal,val proposedLoad:BigDecimal,
    val readiness:CoachReadiness,val movementRestricted:Boolean,
    val maximumAuthorizedIncrease:BigDecimal
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

enum class NativeFutureWeekConsentRefusal {
    INVALID_SELECTION, NOT_AUTHORIZED, REVIEW_UNAVAILABLE,
    CONFIRMATION_REQUIRED, REVIEW_CHANGED, STALE_REVISION,
    STALE_REVIEW, CORRUPT_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeFutureWeekConsentPreview {
    data class Ready(val fingerprint:String,val recordRevision:Long,
        val previousLoad:BigDecimal,val proposedLoad:BigDecimal):
        NativeFutureWeekConsentPreview
    data class Rejected(val reason:NativeFutureWeekConsentRefusal):
        NativeFutureWeekConsentPreview
}
sealed interface NativeFutureWeekConsentWrite {
    data class Accepted(val receipt:NativeFutureWeekLoadConsentReceipt):
        NativeFutureWeekConsentWrite
    data class Revoked(val recordRevision:Long):NativeFutureWeekConsentWrite
    data class Rejected(val reason:NativeFutureWeekConsentRefusal):
        NativeFutureWeekConsentWrite
}
sealed interface NativeFutureWeekConsentRead {
    data object Missing:NativeFutureWeekConsentRead
    data class Current(val receipt:NativeFutureWeekLoadConsentReceipt):
        NativeFutureWeekConsentRead
    data class Revoked(val recordRevision:Long):NativeFutureWeekConsentRead
    data class Rejected(val reason:NativeFutureWeekConsentRefusal):
        NativeFutureWeekConsentRead
}

internal data class NativeFutureWeekConsentCandidate(
    val cycleId:String,val blockId:String,val fingerprint:String,
    val historyRevision:Long,val draftRevision:Long,val sourceRevision:Long,
    val settingsRevision:Long,val permissionRevision:Long,
    val previousLoad:BigDecimal,val proposedLoad:BigDecimal
)
private data class NativeFutureWeekConsentDisk(
    val revision:Long,val receipt:NativeFutureWeekLoadConsentReceipt?
)

class NativeKotlinFutureWeekLoadConsentFileStore internal constructor(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val offer:(
        String,CustomCoachAuditInputs,String,String,Int,Int,String,
        CoachReadiness,Boolean,BigDecimal
    )->NativeFutureWeekConsentCandidate?
) {
    constructor(
        directory:Path,fence:NativeKotlinApprovalRevisionFence,
        identity:NativeKotlinCurrentIdentity,
        reviewer:NativeKotlinCustomFutureWeekLoadOptionReview
    ):this(directory,fence,identity,
        {key,inputs,cycle,block,week,weekday,slot,readiness,restricted,limit->
            (reviewer.review(key,inputs,week,weekday,slot,
                readiness,restricted,limit)
                as? NativeFutureWeekLoadOptionResult.Ready)?.takeIf{
                it.option.proposedCycle.cycleId==cycle &&
                    it.option.proposedCycle.blocks.any{b->b.blockId==block}
            }?.let{
                NativeFutureWeekConsentCandidate(
                    cycle,block,it.reviewFingerprint,it.historyRevision,
                    it.draftRevision,it.sourceRevision,it.settingsRevision,
                    it.permissionRevision,it.previousLoad,it.candidateLoad)
            }
        })

    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hex=Regex("^[a-f0-9]{64}$")
    private val maxRevision=9_007_199_254_740_991L
    private val magic=0x50494657 // PIFW
    private val version=1
    private val maxSize=8192
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes)
    private fun name(key:String,cycle:String,block:String,week:Int,day:Int,slot:String)=
        "future-week-consent-"+hash(
            "$key\u0000$cycle\u0000$block\u0000$week\u0000$day\u0000$slot"
                .toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun file(key:String,cycle:String,block:String,week:Int,day:Int,slot:String)=
        directory.resolve(name(key,cycle,block,week,day,slot)+".bin")
    private fun pending(file:Path)=file.resolveSibling(
        file.fileName.toString().removeSuffix(".bin")+".pending")
    private fun owner(key:String)=runCatching{identity.signedInIdentity()}
        .getOrNull()?.takeIf{id.matches(key)&&it.ownerKey==key&&
            id.matches(it.athleteId)}
    private fun valid(key:String,cycle:String,block:String,week:Int,day:Int,slot:String)=
        listOf(key,cycle,block,slot).all(id::matches)&&week in 1..104&&day in 0..6
    private fun norm(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun validReceipt(r:NativeFutureWeekLoadConsentReceipt)=
        listOf(r.ownerKey,r.athleteId,r.cycleId,r.blockId,r.slotId)
            .all(id::matches)&&
        r.recordRevision in 1..maxRevision &&
        r.absoluteWeek in 1..104&&r.weekday in 0..6&&
        r.historyRevision>=0&&r.draftRevision>0&&r.sourceRevision>0&&
        r.settingsRevision>=0&&r.permissionRevision>0&&
        hex.matches(r.reviewFingerprint)&&
        r.previousLoad>=BigDecimal.ZERO&&r.proposedLoad>r.previousLoad&&
        r.maximumAuthorizedIncrease>BigDecimal.ZERO&&
        r.proposedLoad.subtract(r.previousLoad)<=r.maximumAuthorizedIncrease

    private fun encode(d:NativeFutureWeekConsentDisk):ByteArray {
        require(d.revision in 1..maxRevision)
        require(d.receipt==null ||
            (d.receipt.recordRevision==d.revision&&validReceipt(d.receipt)))
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(magic);out.writeInt(version);out.writeLong(d.revision)
            out.writeBoolean(d.receipt!=null)
            d.receipt?.let{r->
                listOf(r.ownerKey,r.athleteId,r.cycleId,r.blockId,r.slotId,
                    r.reviewFingerprint).forEach(out::writeUTF)
                out.writeInt(r.absoluteWeek);out.writeInt(r.weekday)
                listOf(r.historyRevision,r.draftRevision,r.sourceRevision,
                    r.settingsRevision,r.permissionRevision).forEach(out::writeLong)
                out.writeUTF(norm(r.previousLoad).toPlainString())
                out.writeUTF(norm(r.proposedLoad).toPlainString())
                out.writeUTF(r.readiness.name)
                out.writeBoolean(r.movementRestricted)
                out.writeUTF(norm(r.maximumAuthorizedIncrease).toPlainString())
            }
        }
        val bytes=raw.toByteArray()
        require(bytes.size+36<=maxSize)
        return ByteArrayOutputStream().also {wrapped->
            DataOutputStream(wrapped).use{out->
                out.writeInt(bytes.size);out.write(bytes);out.write(hash(bytes))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeFutureWeekConsentDisk? {
        if(Files.exists(pending(file)))throw IOException("Interrupted consent write")
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||Files.size(file) !in 44L..maxSize.toLong())
            throw IOException("Invalid future-week consent")
        val raw=Files.readAllBytes(file)
        val inp=DataInputStream(ByteArrayInputStream(raw))
        val length=inp.readInt()
        if(length !in 16..maxSize-36||length+36!=raw.size)
            throw IOException("Invalid future-week consent envelope")
        val bytes=ByteArray(length);inp.readFully(bytes)
        val checksum=ByteArray(32);inp.readFully(checksum)
        if(!MessageDigest.isEqual(checksum,hash(bytes)))
            throw IOException("Future-week consent checksum mismatch")
        return DataInputStream(ByteArrayInputStream(bytes)).use{input->
            if(input.readInt()!=magic||input.readInt()!=version)
                throw IOException("Unknown future-week consent version")
            val revision=input.readLong()
            val receipt=if(input.readBoolean()) {
                val names=List(6){input.readUTF()}
                val week=input.readInt();val day=input.readInt()
                val revs=List(5){input.readLong()}
                val previous=BigDecimal(input.readUTF())
                val proposed=BigDecimal(input.readUTF())
                val readinessName=input.readUTF()
                val readiness=CoachReadiness.entries.singleOrNull{
                    it.name==readinessName
                }
                val restricted=input.readBoolean()
                val maximum=BigDecimal(input.readUTF())
                NativeFutureWeekLoadConsentReceipt(
                    names[0],names[1],names[2],names[3],revision,
                    week,day,names[4],revs[0],revs[1],revs[2],revs[3],revs[4],
                    names[5],previous,proposed,
                    readiness?:throw IOException("Unknown readiness"),
                    restricted,maximum)
            }else null
            if(input.available()!=0||revision !in 1..maxRevision||
                (receipt!=null&&!validReceipt(receipt)))
                throw IOException("Malformed future-week consent")
            NativeFutureWeekConsentDisk(revision,receipt)
        }
    }
    private fun atomic(file:Path,d:NativeFutureWeekConsentDisk) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("Unsafe consent directory")
        val temp=pending(file)
        if(Files.exists(temp))throw IOException("Unresolved consent write")
        FileChannel.open(temp,StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE).use{channel->
            val buffer=ByteBuffer.wrap(encode(d))
            while(buffer.hasRemaining())channel.write(buffer)
            channel.force(true)
        }
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)}
        catch(e:AtomicMoveNotSupportedException){
            throw IOException("Atomic consent rename required",e)
        }
    }
    private fun candidate(
        key:String,inputs:CustomCoachAuditInputs,cycle:String,block:String,
        week:Int,day:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,limit:BigDecimal
    ):NativeFutureWeekConsentCandidate? {
        val c=offer(key,inputs,cycle,block,week,day,slot,readiness,restricted,limit)
            ?:return null
        return c.takeIf{it.cycleId==cycle&&it.blockId==block&&
            hex.matches(it.fingerprint)&&it.historyRevision>=0&&
            it.draftRevision>0&&it.sourceRevision>0&&
            it.settingsRevision>=0&&it.permissionRevision>0&&
            it.previousLoad>=BigDecimal.ZERO&&
            it.proposedLoad>it.previousLoad&&
            it.proposedLoad.subtract(it.previousLoad)<=limit}
    }
    private fun receipt(
        owner:NativeKotlinOwnerIdentity,cycle:String,block:String,
        revision:Long,week:Int,day:Int,slot:String,
        readiness:CoachReadiness,restricted:Boolean,limit:BigDecimal,
        c:NativeFutureWeekConsentCandidate
    )=NativeFutureWeekLoadConsentReceipt(
        owner.ownerKey,owner.athleteId,cycle,block,revision,week,day,slot,
        c.historyRevision,c.draftRevision,c.sourceRevision,c.settingsRevision,
        c.permissionRevision,c.fingerprint,norm(c.previousLoad),
        norm(c.proposedLoad),readiness,restricted,norm(limit))

    fun preview(
        key:String,cycle:String,block:String,
        inputs:CustomCoachAuditInputs,week:Int,day:Int,slot:String,
        readiness:CoachReadiness,restricted:Boolean,limit:BigDecimal
    ):NativeFutureWeekConsentPreview {
        fun no(r:NativeFutureWeekConsentRefusal)=NativeFutureWeekConsentPreview.Rejected(r)
        if(!valid(key,cycle,block,week,day,slot)||limit<=BigDecimal.ZERO)
            return no(NativeFutureWeekConsentRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val path=file(key,cycle,block,week,day,slot)
            val current=try{decode(path)}catch(_:Exception){
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.CORRUPT_RECORD)
            }
            if(current?.receipt!=null &&
                (current.receipt.ownerKey!=key ||
                    current.receipt.athleteId!=signed.athleteId))
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val c=candidate(key,inputs,cycle,block,week,day,slot,
                readiness,restricted,limit)
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.REVIEW_UNAVAILABLE)
            if(owner(key)!=signed)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            NativeFutureWeekConsentPreview.Ready(
                c.fingerprint,current?.revision?:0,norm(c.previousLoad),
                norm(c.proposedLoad))
        }}.getOrElse{no(NativeFutureWeekConsentRefusal.STORAGE_UNAVAILABLE)}
    }

    fun accept(
        key:String,cycle:String,block:String,
        inputs:CustomCoachAuditInputs,week:Int,day:Int,slot:String,
        readiness:CoachReadiness,restricted:Boolean,limit:BigDecimal,
        expectedFingerprint:String,expectedRecordRevision:Long,
        athleteConfirmed:Boolean
    ):NativeFutureWeekConsentWrite {
        fun no(r:NativeFutureWeekConsentRefusal)=NativeFutureWeekConsentWrite.Rejected(r)
        if(!athleteConfirmed)return no(NativeFutureWeekConsentRefusal.CONFIRMATION_REQUIRED)
        if(!valid(key,cycle,block,week,day,slot)||
            !hex.matches(expectedFingerprint)||limit<=BigDecimal.ZERO||
            expectedRecordRevision !in 0 until maxRevision)
            return no(NativeFutureWeekConsentRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val path=file(key,cycle,block,week,day,slot)
            val previous=try{decode(path)}catch(_:Exception){
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.CORRUPT_RECORD)
            }
            if(previous?.receipt!=null &&
                (previous.receipt.ownerKey!=key ||
                    previous.receipt.athleteId!=signed.athleteId))
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            if((previous?.revision?:0)!=expectedRecordRevision)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.STALE_REVISION)
            val c=candidate(key,inputs,cycle,block,week,day,slot,
                readiness,restricted,limit)
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.STALE_REVIEW)
            if(c.fingerprint!=expectedFingerprint)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.REVIEW_CHANGED)
            if(owner(key)!=signed)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val item=receipt(signed,cycle,block,expectedRecordRevision+1,
                week,day,slot,readiness,restricted,limit,c)
            atomic(path,NativeFutureWeekConsentDisk(item.recordRevision,item))
            NativeFutureWeekConsentWrite.Accepted(item)
        }}.getOrElse{no(NativeFutureWeekConsentRefusal.STORAGE_UNAVAILABLE)}
    }
    /** A receipt is current only while a fresh full-cycle review reproduces it. */
    fun readCurrent(
        key:String,cycle:String,block:String,
        inputs:CustomCoachAuditInputs,week:Int,day:Int,slot:String
    ):NativeFutureWeekConsentRead {
        fun no(r:NativeFutureWeekConsentRefusal)=NativeFutureWeekConsentRead.Rejected(r)
        if(!valid(key,cycle,block,week,day,slot))
            return no(NativeFutureWeekConsentRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val saved=try{decode(file(key,cycle,block,week,day,slot))}
                catch(_:Exception){return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.CORRUPT_RECORD)}
                ?:return@withOwnerTransaction NativeFutureWeekConsentRead.Missing
            val r=saved.receipt?:return@withOwnerTransaction
                NativeFutureWeekConsentRead.Revoked(saved.revision)
            if(r.ownerKey!=key||r.athleteId!=signed.athleteId||
                r.cycleId!=cycle||r.blockId!=block||
                r.absoluteWeek!=week||r.weekday!=day||r.slotId!=slot)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val c=candidate(key,inputs,cycle,block,week,day,slot,r.readiness,
                r.movementRestricted,r.maximumAuthorizedIncrease)
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.STALE_REVIEW)
            val now=receipt(signed,cycle,block,r.recordRevision,week,day,slot,
                r.readiness,r.movementRestricted,r.maximumAuthorizedIncrease,c)
            if(now!=r)return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.STALE_REVIEW)
            if(owner(key)!=signed)return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            NativeFutureWeekConsentRead.Current(r)
        }}.getOrElse{no(NativeFutureWeekConsentRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Durable revocation; an old confirmation callback cannot resurrect it. */
    fun revoke(
        key:String,cycle:String,block:String,
        week:Int,day:Int,slot:String,expectedRecordRevision:Long
    ):NativeFutureWeekConsentWrite {
        fun no(r:NativeFutureWeekConsentRefusal)=NativeFutureWeekConsentWrite.Rejected(r)
        if(!valid(key,cycle,block,week,day,slot)||
            expectedRecordRevision !in 1 until maxRevision)
            return no(NativeFutureWeekConsentRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val path=file(key,cycle,block,week,day,slot)
            val previous=try{decode(path)}catch(_:Exception){
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.STALE_REVISION)
            if(previous.revision!=expectedRecordRevision ||
                previous.receipt?.athleteId!=signed.athleteId)
                return@withOwnerTransaction no(
                    NativeFutureWeekConsentRefusal.STALE_REVISION)
            if(owner(key)!=signed)return@withOwnerTransaction no(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
            val revision=expectedRecordRevision+1
            atomic(path,NativeFutureWeekConsentDisk(revision,null))
            NativeFutureWeekConsentWrite.Revoked(revision)
        }}.getOrElse{no(NativeFutureWeekConsentRefusal.STORAGE_UNAVAILABLE)}
    }
}
