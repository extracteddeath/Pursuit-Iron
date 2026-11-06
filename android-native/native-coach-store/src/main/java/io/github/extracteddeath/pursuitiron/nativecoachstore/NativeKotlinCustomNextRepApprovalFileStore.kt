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
 * Additional athlete confirmation of exact rep adjustments for ONE future
 * custom workout slot. Never modifies the source cycle or any old journal.
 * Future issuance must independently recheck this receipt under owner fence.
 */
data class NativeCustomNextRepReceipt(
    val ownerKey:String,val athleteId:String,val workoutId:String,
    val cycleId:String,val blockId:String,val absoluteWeek:Int,
    val weekday:Int,val slotId:String,val consentRevision:Long,
    val historyRevision:Long,val fingerprint:String,
    val setKeys:List<String>,val nextMinimumReps:List<Int>,
    val readiness:CoachReadiness,val movementRestricted:Boolean,
    val maximumAuthorizedIncrease:BigDecimal
) {
    val mayIssueWorkout:Boolean get()=false
    val mayModifyCycle:Boolean get()=false
}
enum class NativeCustomNextRepRefusal {
    NOT_AUTHORIZED, INVALID_SELECTION, REVIEW_NOT_READY,
    ALREADY_PREPARED_OR_ISSUED, FINGERPRINT_CHANGED,
    STALE_CONSENT_OR_HISTORY, CONFLICTING_APPROVAL,
    CONFIRMATION_REQUIRED, CORRUPT_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomNextRepPreview {
    data class Ready(
        val receipt:NativeCustomNextRepReceipt,
        val amendment:CustomNextRepAmendment
    ):NativeCustomNextRepPreview
    data class Rejected(val reason:NativeCustomNextRepRefusal):
        NativeCustomNextRepPreview
}
sealed interface NativeCustomNextRepRead {
    data object Missing:NativeCustomNextRepRead
    data class Current(
        val receipt:NativeCustomNextRepReceipt,
        val amendment:CustomNextRepAmendment
    ):NativeCustomNextRepRead
    data class Rejected(val reason:NativeCustomNextRepRefusal):
        NativeCustomNextRepRead
}
sealed interface NativeCustomNextRepWrite {
    data class Accepted(
        val receipt:NativeCustomNextRepReceipt,val replayed:Boolean
    ):NativeCustomNextRepWrite
    data class Rejected(val reason:NativeCustomNextRepRefusal):
        NativeCustomNextRepWrite
}

class NativeKotlinCustomNextRepApprovalFileStore(
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
    private val magic=0x50495241 // PIRA
    private val version=1
    private val maxBytes=16_384
    private fun digest(bytes:ByteArray):ByteArray=
        MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hash(bytes:ByteArray):String=digest(bytes).joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun current(key:String)=
        runCatching{identity.signedInIdentity()}.getOrNull()?.takeIf{
            id.matches(key) && it.ownerKey==key && id.matches(it.athleteId)
        }
    private fun path(key:String,workoutId:String):Path=
        directory.resolve("future-rep-"+hash(
            "$key\u0000$workoutId".toByteArray(Charsets.UTF_8))+".bin")
    private fun pending(key:String,workoutId:String)=
        directory.resolve("future-rep-"+hash(
            "$key\u0000$workoutId".toByteArray(Charsets.UTF_8))+".pending")

    /** Versioned canonical bytes; no unspecified string/JSON key ordering. */
    private fun encode(receipt:NativeCustomNextRepReceipt):ByteArray {
        require(listOf(receipt.ownerKey,receipt.athleteId,
            receipt.workoutId,receipt.cycleId,receipt.blockId,
            receipt.slotId).all(id::matches))
        require(hex.matches(receipt.fingerprint) &&
            receipt.consentRevision>0 && receipt.historyRevision>0 &&
            receipt.absoluteWeek in 1..104 && receipt.weekday in 0..6 &&
            receipt.setKeys.size in 1..32 &&
            receipt.setKeys.size==receipt.nextMinimumReps.size &&
            receipt.setKeys.distinct().size==receipt.setKeys.size &&
            receipt.setKeys.all{it.length in 1..240} &&
            receipt.nextMinimumReps.all{it in 1..100} &&
            receipt.maximumAuthorizedIncrease>BigDecimal.ZERO &&
            receipt.maximumAuthorizedIncrease<=BigDecimal("100000"))
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic);out.writeInt(version)
            listOf(receipt.ownerKey,receipt.athleteId,receipt.workoutId,
                receipt.cycleId,receipt.blockId,receipt.slotId,
                receipt.fingerprint).forEach(out::writeUTF)
            out.writeInt(receipt.absoluteWeek);out.writeInt(receipt.weekday)
            out.writeLong(receipt.consentRevision)
            out.writeLong(receipt.historyRevision)
            out.writeUTF(receipt.readiness.name)
            out.writeBoolean(receipt.movementRestricted)
            out.writeUTF(receipt.maximumAuthorizedIncrease.toPlainString())
            out.writeInt(receipt.setKeys.size)
            for(i in receipt.setKeys.indices){
                out.writeUTF(receipt.setKeys[i])
                out.writeInt(receipt.nextMinimumReps[i])
            }
        }
        val bytes=payload.toByteArray()
        require(bytes.size+36<=maxBytes)
        return ByteArrayOutputStream().also{res->
            DataOutputStream(res).use{out->
                out.writeInt(bytes.size);out.write(bytes);out.write(digest(bytes))
            }
        }.toByteArray()
    }
    private fun decode(key:String,workoutId:String):NativeCustomNextRepReceipt? {
        val file=path(key,workoutId)
        if(Files.exists(pending(key,workoutId)))
            throw IOException("unresolved rep-approval pending write")
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) || Files.size(file) !in 44L..maxBytes.toLong())
            throw IOException("bad rep approval file")
        val raw=Files.readAllBytes(file)
        return DataInputStream(ByteArrayInputStream(raw)).use{inp->
            val count=inp.readInt()
            if(count !in 16..(maxBytes-36) || count+36!=raw.size)
                throw IOException("bad rep approval envelope")
            val bytes=ByteArray(count);inp.readFully(bytes)
            val expected=ByteArray(32);inp.readFully(expected)
            if(!MessageDigest.isEqual(expected,digest(bytes)))
                throw IOException("rep approval checksum mismatch")
            val data=DataInputStream(ByteArrayInputStream(bytes))
            if(data.readInt()!=magic || data.readInt()!=version)
                throw IOException("bad rep approval schema")
            val owner=data.readUTF();val athlete=data.readUTF()
            val wid=data.readUTF();val cycle=data.readUTF()
            val block=data.readUTF();val slot=data.readUTF()
            val fingerprint=data.readUTF()
            val week=data.readInt();val weekday=data.readInt()
            val consent=data.readLong();val history=data.readLong()
            val readiness=CoachReadiness.entries.singleOrNull{
                it.name==data.readUTF()
            }?:throw IOException("bad readiness")
            val restricted=data.readBoolean()
            val step=BigDecimal(data.readUTF())
            val n=data.readInt()
            if(n !in 1..32)throw IOException("bad rep count")
            val keys=ArrayList<String>(n);val reps=ArrayList<Int>(n)
            repeat(n){keys+=data.readUTF();reps+=data.readInt()}
            if(data.available()!=0)throw IOException("trailing rep approval")
            NativeCustomNextRepReceipt(owner,athlete,wid,cycle,block,
                week,weekday,slot,consent,history,fingerprint,keys,reps,
                readiness,restricted,step).also{
                encode(it) // validates ALL stored fields and physical bounds
                if(owner!=key || wid!=workoutId)
                    throw IOException("rep receipt identity collision")
            }
        }
    }
    private fun atomic(key:String,workoutId:String,receipt:NativeCustomNextRepReceipt) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("unsafe directory")
        val tmp=pending(key,workoutId)
        if(Files.exists(tmp) || Files.exists(path(key,workoutId)))
            throw IOException("rep approval already exists")
        FileChannel.open(tmp,StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE).use{ch->
            val buf=ByteBuffer.wrap(encode(receipt))
            while(buf.hasRemaining())ch.write(buf)
            ch.force(true)
        }
        try {
            Files.move(tmp,path(key,workoutId),StandardCopyOption.ATOMIC_MOVE)
        }catch(ex:AtomicMoveNotSupportedException){
            throw IOException("rep approval atomic move unsupported",ex)
        }
    }
    private fun derive(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,maxIncrease:BigDecimal
    ):NativeCustomNextRepPreview {
        fun fail(reason:NativeCustomNextRepRefusal)=
            NativeCustomNextRepPreview.Rejected(reason)
        val signed=current(key)?:return fail(NativeCustomNextRepRefusal.NOT_AUTHORIZED)
        val review=reviewer.review(key,inputs,week,weekday,slot,
            readiness,restricted,maxIncrease)
            as? NativeCustomNextCoachResult.Reviewed
            ?:return fail(NativeCustomNextRepRefusal.REVIEW_NOT_READY)
        val snap=(activation.previewActivatedWorkout(key,inputs,week,weekday)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
            ?:return fail(NativeCustomNextRepRefusal.REVIEW_NOT_READY)
        if(snap.stamp.athleteId!=signed.athleteId)
            return fail(NativeCustomNextRepRefusal.NOT_AUTHORIZED)
        val amended=CustomNextRepAmendmentDomain.propose(
            snap,slot,review.review)
            as? CustomNextRepAmendmentResult.Proposed
            ?:return fail(NativeCustomNextRepRefusal.REVIEW_NOT_READY)
        val consentReview=(activation.reviewActivatedCycle(key,inputs)
            as? NativeFutureActivatedCycleReview.Ready)
            ?:return fail(NativeCustomNextRepRefusal.REVIEW_NOT_READY)
        if(snap.stamp.cycleId!=consentReview.cycleId ||
            snap.blockId!=consentReview.blockId ||
            snap.stamp.gymCatalogSourceRevision!=consentReview.sourceRevision)
            return fail(NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY)

        val signature=ByteArrayOutputStream()
        DataOutputStream(signature).use{out->
            out.writeUTF("future-rep-approval-v1")
            out.writeUTF(key);out.writeUTF(signed.athleteId)
            out.writeUTF(workoutId);out.writeUTF(slot)
            out.writeLong(review.historyRevision)
            out.writeLong(review.approvedConsentRevision)
            out.writeUTF(consentReview.reviewFingerprint)
            out.writeUTF(readiness.name);out.writeBoolean(restricted)
            out.writeUTF(maxIncrease.stripTrailingZeros().toPlainString())
            val original=NativeKotlinCustomWorkoutSnapshotCodec.encode(snap)
            out.writeInt(original.size);out.write(original)
            out.writeUTF(review.review.latestWorkoutId!!)
            out.writeInt(amended.amendment.originalSetKeys.size)
            for(i in amended.amendment.originalSetKeys.indices){
                out.writeUTF(amended.amendment.originalSetKeys[i])
                out.writeInt(amended.amendment.nextMinimumReps[i])
            }
        }
        val receipt=NativeCustomNextRepReceipt(key,signed.athleteId,
            workoutId,snap.stamp.cycleId,snap.blockId,week,weekday,slot,
            review.approvedConsentRevision,review.historyRevision,
            hash(signature.toByteArray()),amended.amendment.originalSetKeys,
            amended.amendment.nextMinimumReps,readiness,restricted,maxIncrease)
        return NativeCustomNextRepPreview.Ready(receipt,amended.amendment)
    }

    fun preview(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,maxIncrease:BigDecimal
    ):NativeCustomNextRepPreview {
        if(!listOf(key,workoutId,slot).all(id::matches) ||
            week !in 1..104 || weekday !in 0..6)
            return NativeCustomNextRepPreview.Rejected(
                NativeCustomNextRepRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val result=derive(key,workoutId,inputs,week,weekday,slot,
                readiness,restricted,maxIncrease)
            if(result !is NativeCustomNextRepPreview.Ready)
                return@withOwnerTransaction result
            if(prepared.open(key,workoutId)!=NativeCustomPreparedRead.Missing ||
                issued.open(key,workoutId)!=NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.NOT_ISSUED))
                return@withOwnerTransaction NativeCustomNextRepPreview.Rejected(
                    NativeCustomNextRepRefusal.ALREADY_PREPARED_OR_ISSUED)
            result
        }}.getOrElse{NativeCustomNextRepPreview.Rejected(
            NativeCustomNextRepRefusal.STORAGE_UNAVAILABLE)}
    }

    fun accept(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int,slot:String,readiness:CoachReadiness,
        restricted:Boolean,maxIncrease:BigDecimal,
        expectedFingerprint:String,expectedHistoryRevision:Long,
        expectedConsentRevision:Long,shownSetKeys:List<String>,
        shownNextMinimumReps:List<Int>,athleteConfirmed:Boolean
    ):NativeCustomNextRepWrite {
        fun fail(reason:NativeCustomNextRepRefusal)=
            NativeCustomNextRepWrite.Rejected(reason)
        if(!athleteConfirmed)return fail(
            NativeCustomNextRepRefusal.CONFIRMATION_REQUIRED)
        if(!hex.matches(expectedFingerprint) ||
            expectedHistoryRevision<1 || expectedConsentRevision<1)
            return fail(NativeCustomNextRepRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val preview=preview(key,workoutId,inputs,week,weekday,slot,
                readiness,restricted,maxIncrease)
                as? NativeCustomNextRepPreview.Ready
                ?:return@withOwnerTransaction fail(
                    NativeCustomNextRepRefusal.REVIEW_NOT_READY)
            val record=preview.receipt
            if(record.historyRevision!=expectedHistoryRevision ||
                record.consentRevision!=expectedConsentRevision)
                return@withOwnerTransaction fail(
                    NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY)
            if(record.fingerprint!=expectedFingerprint ||
                record.setKeys!=shownSetKeys ||
                record.nextMinimumReps!=shownNextMinimumReps)
                return@withOwnerTransaction fail(
                    NativeCustomNextRepRefusal.FINGERPRINT_CHANGED)
            val existing=try{decode(key,workoutId)}catch(_:Exception){
                return@withOwnerTransaction fail(
                    NativeCustomNextRepRefusal.CORRUPT_RECORD)
            }
            if(existing!=null)return@withOwnerTransaction if(existing==record)
                NativeCustomNextRepWrite.Accepted(existing,true)
                else fail(NativeCustomNextRepRefusal.CONFLICTING_APPROVAL)
            if(current(key)?.athleteId!=record.athleteId)
                return@withOwnerTransaction fail(
                    NativeCustomNextRepRefusal.NOT_AUTHORIZED)
            atomic(key,workoutId,record)
            NativeCustomNextRepWrite.Accepted(record,false)
        }}.getOrElse{fail(NativeCustomNextRepRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Re-derive before ALL pre-issue uses. A revoked cycle approval, edited
     * source, corrected Finish, changed history or different workout ID
     * invalidates this receipt without deleting any factual history.
     */
    fun readCurrent(
        key:String,workoutId:String,inputs:CustomCoachAuditInputs,
        week:Int,weekday:Int
    ):NativeCustomNextRepRead {
        if(!listOf(key,workoutId).all(id::matches) ||
            week !in 1..104||weekday !in 0..6)
            return NativeCustomNextRepRead.Rejected(
                NativeCustomNextRepRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val signed=current(key)
                ?:return@withOwnerTransaction NativeCustomNextRepRead.Rejected(
                    NativeCustomNextRepRefusal.NOT_AUTHORIZED)
            val record=try{decode(key,workoutId)}catch(_:Exception){
                return@withOwnerTransaction NativeCustomNextRepRead.Rejected(
                    NativeCustomNextRepRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction NativeCustomNextRepRead.Missing
            if(record.athleteId!=signed.athleteId ||
                record.absoluteWeek!=week || record.weekday!=weekday)
                return@withOwnerTransaction NativeCustomNextRepRead.Rejected(
                    NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY)
            val now=derive(key,workoutId,inputs,week,weekday,
                record.slotId,record.readiness,record.movementRestricted,
                record.maximumAuthorizedIncrease)
                as? NativeCustomNextRepPreview.Ready
                ?:return@withOwnerTransaction NativeCustomNextRepRead.Rejected(
                    NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY)
            if(now.receipt!=record)
                return@withOwnerTransaction NativeCustomNextRepRead.Rejected(
                    NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY)
            NativeCustomNextRepRead.Current(record,now.amendment)
        }}.getOrElse{NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.STORAGE_UNAVAILABLE)}
    }
}
