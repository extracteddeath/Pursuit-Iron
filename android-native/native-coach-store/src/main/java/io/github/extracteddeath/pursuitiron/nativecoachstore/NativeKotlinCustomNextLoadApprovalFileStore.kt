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

/** Stored approval is NOT an issued weight. Every use rechecks current
 * consent, exact inventory, history and approved cycle under owner OS fence. */
data class NativeCustomNextLoadReceipt(
    val ownerKey:String,val athleteId:String,val workoutId:String,
    val cycleId:String,val blockId:String,val week:Int,val weekday:Int,
    val slotId:String,val consentRevision:Long,val historyRevision:Long,
    val fingerprint:String,val setKeys:List<String>,
    val originalLoad:BigDecimal,val proposedLoad:BigDecimal,
    val unit:LoadUnit,val basis:LoadBasis,
    val readiness:CoachReadiness,val movementRestricted:Boolean,
    val maximumAuthorizedIncrease:BigDecimal
) {
    val mayIssueWorkout:Boolean get()=false
    val mayOverwriteHistory:Boolean get()=false
}
enum class NativeCustomLoadRefusal {
    NOT_AUTHORIZED,INVALID_SELECTION,REVIEW_NOT_READY,
    ALREADY_PREPARED_OR_ISSUED,CONFIRMATION_REQUIRED,
    FINGERPRINT_CHANGED,STALE_HISTORY_OR_CONSENT,
    CONFLICTING_APPROVAL,CORRUPT_RECORD,STORAGE_UNAVAILABLE
}
sealed interface NativeCustomLoadPreview {
    data class Ready(
        val receipt:NativeCustomNextLoadReceipt,
        val amendment:CustomNextLoadAmendment
    ):NativeCustomLoadPreview
    data class Rejected(val reason:NativeCustomLoadRefusal):
        NativeCustomLoadPreview
}
sealed interface NativeCustomLoadRead {
    data object Missing:NativeCustomLoadRead
    data class Current(
        val receipt:NativeCustomNextLoadReceipt,
        val amendment:CustomNextLoadAmendment
    ):NativeCustomLoadRead
    data class Rejected(val reason:NativeCustomLoadRefusal):
        NativeCustomLoadRead
}
sealed interface NativeCustomLoadWrite {
    data class Accepted(val receipt:NativeCustomNextLoadReceipt,
        val replayed:Boolean):NativeCustomLoadWrite
    data class Rejected(val reason:NativeCustomLoadRefusal):
        NativeCustomLoadWrite
}

/** A separate physical-load approval, never mixed into the rep consent
 * receipt; the native issuer can explicitly prohibit simultaneous edits. */
class NativeKotlinCustomNextLoadApprovalFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val activation:NativeKotlinFutureActivationCommitTransaction,
    private val reviewer:NativeKotlinCustomNextWorkoutReview,
    private val prepared:NativeKotlinCustomWorkoutPreparedStore,
    private val issued:NativeKotlinCustomWorkoutJournalFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hex=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494c41 // PILA
    private val version=1
    private val maxBytes=16_384
    private val zero=BigDecimal.ZERO
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hash(bytes:ByteArray)=digest(bytes).joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun norm(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun owner(key:String)=runCatching{identity.signedInIdentity()}
        .getOrNull()?.takeIf{id.matches(key)&&it.ownerKey==key&&id.matches(it.athleteId)}
    private fun stem(key:String,workoutId:String)="future-load-"+
        hash("$key\u0000$workoutId".toByteArray(Charsets.UTF_8))
    private fun path(key:String,workoutId:String)=directory.resolve(
        stem(key,workoutId)+".bin")
    private fun pending(key:String,workoutId:String)=directory.resolve(
        stem(key,workoutId)+".pending")
    private fun validLoad(v:BigDecimal)=v>zero&&v<=BigDecimal("100000")&&
        v.stripTrailingZeros().scale()<=3

    private fun encode(r:NativeCustomNextLoadReceipt):ByteArray {
        require(listOf(r.ownerKey,r.athleteId,r.workoutId,r.cycleId,
            r.blockId,r.slotId).all(id::matches))
        require(hex.matches(r.fingerprint)&&r.consentRevision>0&&
            r.historyRevision>0&&r.week in 1..104&&r.weekday in 0..6&&
            r.setKeys.size in 1..32&&r.setKeys.distinct().size==r.setKeys.size&&
            r.setKeys.all{it.length in 1..240}&&
            validLoad(r.originalLoad)&&validLoad(r.proposedLoad)&&
            r.proposedLoad>r.originalLoad&&
            validLoad(r.maximumAuthorizedIncrease)&&
            r.proposedLoad.subtract(r.originalLoad)<=r.maximumAuthorizedIncrease)
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic);out.writeInt(version)
            listOf(r.ownerKey,r.athleteId,r.workoutId,r.cycleId,r.blockId,
                r.slotId,r.fingerprint).forEach(out::writeUTF)
            out.writeInt(r.week);out.writeInt(r.weekday)
            out.writeLong(r.consentRevision);out.writeLong(r.historyRevision)
            out.writeUTF(r.originalLoad.toPlainString())
            out.writeUTF(r.proposedLoad.toPlainString())
            out.writeUTF(r.unit.name);out.writeUTF(r.basis.name)
            out.writeUTF(r.readiness.name);out.writeBoolean(r.movementRestricted)
            out.writeUTF(r.maximumAuthorizedIncrease.toPlainString())
            out.writeInt(r.setKeys.size)
            r.setKeys.forEach(out::writeUTF)
        }
        val bytes=payload.toByteArray()
        require(bytes.size+36<=maxBytes)
        return ByteArrayOutputStream().also{wrapped->
            DataOutputStream(wrapped).use{out->
                out.writeInt(bytes.size);out.write(bytes);out.write(digest(bytes))
            }
        }.toByteArray()
    }
    private fun decode(key:String,workoutId:String):NativeCustomNextLoadReceipt? {
        val file=path(key,workoutId)
        if(Files.exists(pending(key,workoutId)))
            throw IOException("interrupted load approval")
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||Files.size(file) !in 44L..maxBytes.toLong())
            throw IOException("invalid load approval")
        val raw=Files.readAllBytes(file)
        return DataInputStream(ByteArrayInputStream(raw)).use{inp->
            val size=inp.readInt()
            if(size !in 16..maxBytes-36||size+36!=raw.size)
                throw IOException("bad load envelope")
            val bytes=ByteArray(size);inp.readFully(bytes)
            val proof=ByteArray(32);inp.readFully(proof)
            if(!MessageDigest.isEqual(proof,digest(bytes)))
                throw IOException("load checksum mismatch")
            val data=DataInputStream(ByteArrayInputStream(bytes))
            if(data.readInt()!=magic||data.readInt()!=version)
                throw IOException("load schema mismatch")
            val names=List(7){data.readUTF()}
            val week=data.readInt();val weekday=data.readInt()
            val consent=data.readLong();val history=data.readLong()
            val original=BigDecimal(data.readUTF())
            val proposed=BigDecimal(data.readUTF())
            val unitName=data.readUTF();val basisName=data.readUTF()
            val readinessName=data.readUTF()
            val unit=LoadUnit.entries.singleOrNull{it.name==unitName}
                ?:throw IOException("unknown load unit")
            val basis=LoadBasis.entries.singleOrNull{it.name==basisName}
                ?:throw IOException("unknown load basis")
            val readiness=CoachReadiness.entries.singleOrNull{it.name==readinessName}
                ?:throw IOException("unknown readiness")
            val restricted=data.readBoolean()
            val limit=BigDecimal(data.readUTF())
            val count=data.readInt()
            if(count !in 1..32)throw IOException("bad set count")
            val keys=List(count){data.readUTF()}
            if(data.available()!=0)throw IOException("trailing load approval")
            NativeCustomNextLoadReceipt(names[0],names[1],names[2],names[3],
                names[4],week,weekday,names[5],consent,history,names[6],
                keys,original,proposed,unit,basis,readiness,restricted,limit)
                .also{ r->
                    encode(r)
                    if(r.ownerKey!=key||r.workoutId!=workoutId)
                        throw IOException("wrong owner/workout")
                }
        }
    }
    private fun atomic(key:String,workoutId:String,r:NativeCustomNextLoadReceipt) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("unsafe directory")
        val temp=pending(key,workoutId)
        if(Files.exists(temp)||Files.exists(path(key,workoutId)))
            throw IOException("load approval conflict")
        FileChannel.open(temp,StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE).use{ch->
            val bytes=ByteBuffer.wrap(encode(r))
            while(bytes.hasRemaining())ch.write(bytes)
            ch.force(true)
        }
        try{Files.move(temp,path(key,workoutId),StandardCopyOption.ATOMIC_MOVE)}
        catch(e:AtomicMoveNotSupportedException){
            throw IOException("load approval atomic rename required",e)
        }
    }

    private fun derive(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,limit:BigDecimal
    ):NativeCustomLoadPreview {
        fun denied(r:NativeCustomLoadRefusal)=NativeCustomLoadPreview.Rejected(r)
        val signed=owner(key)?:return denied(NativeCustomLoadRefusal.NOT_AUTHORIZED)
        val reviewed=reviewer.review(key,inputs,week,weekday,slot,
            readiness,restricted,limit) as? NativeCustomNextCoachResult.Reviewed
            ?:return denied(NativeCustomLoadRefusal.REVIEW_NOT_READY)
        val snap=(activation.previewActivatedWorkout(key,inputs,week,weekday)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
            ?:return denied(NativeCustomLoadRefusal.REVIEW_NOT_READY)
        if(snap.stamp.athleteId!=signed.athleteId)
            return denied(NativeCustomLoadRefusal.NOT_AUTHORIZED)
        val proposed=CustomNextLoadAmendmentDomain.propose(
            snap,slot,reviewed.review,reviewed.verifiedStock,limit)
            as? CustomNextLoadAmendmentResult.Proposed
            ?:return denied(NativeCustomLoadRefusal.REVIEW_NOT_READY)
        val consent=activation.reviewActivatedCycle(key,inputs)
            as? NativeFutureActivatedCycleReview.Ready
            ?:return denied(NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
        if(consent.cycleId!=snap.stamp.cycleId||
            consent.blockId!=snap.blockId||
            consent.sourceRevision!=snap.stamp.gymCatalogSourceRevision)
            return denied(NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
        val adjustment=proposed.amendment
        val proof=ByteArrayOutputStream()
        DataOutputStream(proof).use{out->
            out.writeUTF("future-custom-load-approval-v1")
            out.writeUTF(key);out.writeUTF(signed.athleteId)
            out.writeUTF(workoutId);out.writeUTF(slot)
            out.writeLong(reviewed.historyRevision)
            out.writeLong(reviewed.approvedConsentRevision)
            out.writeLong(reviewed.assistanceRevision)
            out.writeUTF(consent.reviewFingerprint)
            out.writeUTF(readiness.name);out.writeBoolean(restricted)
            out.writeUTF(norm(limit).toPlainString())
            val original=NativeKotlinCustomWorkoutSnapshotCodec.encode(snap)
            out.writeInt(original.size);out.write(original)
            out.writeUTF(reviewed.review.latestWorkoutId!!)
            out.writeUTF(adjustment.previousLoad.toPlainString())
            out.writeUTF(adjustment.proposedLoad.toPlainString())
            out.writeUTF(adjustment.unit.name);out.writeUTF(adjustment.basis.name)
            out.writeInt(adjustment.workingSetKeys.size)
            adjustment.workingSetKeys.forEach(out::writeUTF)
        }
        return NativeCustomLoadPreview.Ready(NativeCustomNextLoadReceipt(
            key,signed.athleteId,workoutId,snap.stamp.cycleId,snap.blockId,
            week,weekday,slot,reviewed.approvedConsentRevision,
            reviewed.historyRevision,hash(proof.toByteArray()),
            adjustment.workingSetKeys,adjustment.previousLoad,
            adjustment.proposedLoad,adjustment.unit,adjustment.basis,
            readiness,restricted,norm(limit)),adjustment)
    }
    private fun unissued(key:String,workoutId:String):Boolean =
        prepared.open(key,workoutId)==NativeCustomPreparedRead.Missing&&
        issued.open(key,workoutId)==NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED)
    fun preview(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,limit:BigDecimal
    ):NativeCustomLoadPreview {
        if(!listOf(key,workoutId,slot).all(id::matches)||
            week !in 1..104||weekday !in 0..6)
            return NativeCustomLoadPreview.Rejected(NativeCustomLoadRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val derived=derive(key,workoutId,inputs,week,weekday,slot,
                readiness,restricted,limit)
            if(derived !is NativeCustomLoadPreview.Ready)
                return@withOwnerTransaction derived
            if(!unissued(key,workoutId))
                return@withOwnerTransaction NativeCustomLoadPreview.Rejected(
                    NativeCustomLoadRefusal.ALREADY_PREPARED_OR_ISSUED)
            derived
        }}.getOrElse{NativeCustomLoadPreview.Rejected(
            NativeCustomLoadRefusal.STORAGE_UNAVAILABLE)}
    }
    fun accept(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,limit:BigDecimal,
        expectedFingerprint:String,expectedHistoryRevision:Long,
        expectedConsentRevision:Long,shownSetKeys:List<String>,
        shownOriginalLoad:BigDecimal,shownProposedLoad:BigDecimal,
        athleteConfirmed:Boolean
    ):NativeCustomLoadWrite {
        fun no(reason:NativeCustomLoadRefusal)=NativeCustomLoadWrite.Rejected(reason)
        if(!athleteConfirmed)return no(NativeCustomLoadRefusal.CONFIRMATION_REQUIRED)
        if(!hex.matches(expectedFingerprint)||expectedHistoryRevision<1||
            expectedConsentRevision<1)
            return no(NativeCustomLoadRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val offer=preview(key,workoutId,inputs,week,weekday,slot,
                readiness,restricted,limit) as? NativeCustomLoadPreview.Ready
                ?:return@withOwnerTransaction no(NativeCustomLoadRefusal.REVIEW_NOT_READY)
            val current=offer.receipt
            if(current.historyRevision!=expectedHistoryRevision||
                current.consentRevision!=expectedConsentRevision)
                return@withOwnerTransaction no(NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
            if(current.fingerprint!=expectedFingerprint||
                current.setKeys!=shownSetKeys||
                current.originalLoad.compareTo(shownOriginalLoad)!=0||
                current.proposedLoad.compareTo(shownProposedLoad)!=0)
                return@withOwnerTransaction no(NativeCustomLoadRefusal.FINGERPRINT_CHANGED)
            val existing=try{decode(key,workoutId)}catch(_:Exception){
                return@withOwnerTransaction no(NativeCustomLoadRefusal.CORRUPT_RECORD)
            }
            if(existing!=null)return@withOwnerTransaction if(existing==current)
                NativeCustomLoadWrite.Accepted(existing,true)
                else no(NativeCustomLoadRefusal.CONFLICTING_APPROVAL)
            if(owner(key)?.athleteId!=current.athleteId)
                return@withOwnerTransaction no(NativeCustomLoadRefusal.NOT_AUTHORIZED)
            atomic(key,workoutId,current)
            NativeCustomLoadWrite.Accepted(current,false)
        }}.getOrElse{no(NativeCustomLoadRefusal.STORAGE_UNAVAILABLE)}
    }
    fun readCurrent(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int
    ):NativeCustomLoadRead {
        if(!listOf(key,workoutId).all(id::matches)||
            week !in 1..104||weekday !in 0..6)
            return NativeCustomLoadRead.Rejected(NativeCustomLoadRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=owner(key)?:return@withOwnerTransaction NativeCustomLoadRead.Rejected(
                NativeCustomLoadRefusal.NOT_AUTHORIZED)
            val saved=try{decode(key,workoutId)}catch(_:Exception){
                return@withOwnerTransaction NativeCustomLoadRead.Rejected(
                    NativeCustomLoadRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction NativeCustomLoadRead.Missing
            if(saved.athleteId!=signed.athleteId||
                saved.week!=week||saved.weekday!=weekday)
                return@withOwnerTransaction NativeCustomLoadRead.Rejected(
                    NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
            val now=derive(key,workoutId,inputs,week,weekday,saved.slotId,
                saved.readiness,saved.movementRestricted,
                saved.maximumAuthorizedIncrease)
                as? NativeCustomLoadPreview.Ready
                ?:return@withOwnerTransaction NativeCustomLoadRead.Rejected(
                    NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
            if(saved!=now.receipt)
                return@withOwnerTransaction NativeCustomLoadRead.Rejected(
                    NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT)
            NativeCustomLoadRead.Current(saved,now.amendment)
        }}.getOrElse{NativeCustomLoadRead.Rejected(
            NativeCustomLoadRefusal.STORAGE_UNAVAILABLE)}
    }
}
