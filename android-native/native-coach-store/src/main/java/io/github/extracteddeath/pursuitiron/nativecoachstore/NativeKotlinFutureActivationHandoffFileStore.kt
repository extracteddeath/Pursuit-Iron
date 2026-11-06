package io.github.extracteddeath.pursuitiron.nativecoachstore

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
 * Durable, owner-scoped ISSUANCE BARRIER for an approved future-block switch.
 *
 * Staging is the first step of a future source/policy transaction, not its
 * commit. The staged record intentionally blocks all NEW custom/Composer
 * issuance until an explicit same-source cancellation or a later fully
 * recovered activation transaction. Historical journals remain readable.
 *
 * No write here changes the athlete, gym, cycle, phase policy or history.
 * The eventual activation executor MUST consume the staged fingerprint,
 * source CAS, policy and outstanding-workout evidence under this same fence.
 */
data class NativeFutureActivationHandoff(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val approvalRevision:Long,
    val approvalFingerprint:String,
    val sourceRevision:Long,
    val baseSourceSha256:String,
    val proposedSourceSha256:String,
    val firstWeek:Int,
    val historyManifestRevision:Long
) {
    val mayActivate:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

enum class NativeFutureHandoffRefusal {
    NOT_AUTHORIZED, APPROVAL_NOT_CURRENT, REVIEW_CHANGED,
    ALREADY_STAGED, SOURCE_CHANGED, RECORD_CORRUPT,
    CONFIRMATION_REQUIRED, STORAGE_UNAVAILABLE
}
sealed interface NativeFutureHandoffReview {
    data object Missing:NativeFutureHandoffReview
    data class Staged(val receipt:NativeFutureActivationHandoff,
        val replayed:Boolean):NativeFutureHandoffReview
    data class Rejected(val reason:NativeFutureHandoffRefusal):
        NativeFutureHandoffReview
    data object Cancelled:NativeFutureHandoffReview
}

class NativeKotlinFutureActivationHandoffFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hash=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494648 // PIFH
    private val version=1
    private val maximum=9_007_199_254_740_991L

    private fun digest(data:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun owner(ownerKey:String)=runCatching {
        identity.signedInIdentity()
    }.getOrNull()?.takeIf {id.matches(ownerKey) && it.ownerKey==ownerKey}
    private fun ownerHash(ownerKey:String)=digest(ownerKey.toByteArray(Charsets.UTF_8))
    private fun path(ownerKey:String)=directory.resolve(
        "future-handoff-"+ownerHash(ownerKey)+".bin")
    private fun pendingPrefix(ownerKey:String)="future-handoff-"+
        ownerHash(ownerKey)+"-"

    /** Interrupted temporary writes are blockers, not invisible discarded work. */
    private fun hasPending(ownerKey:String):Boolean {
        if(!Files.exists(directory))return false
        if(Files.isSymbolicLink(directory) || !Files.isDirectory(directory))
            return true
        Files.list(directory).use{stream->
            return stream.anyMatch {entry->
                entry.fileName.toString().startsWith(pendingPrefix(ownerKey))
            }
        }
    }

    private fun encode(r:NativeFutureActivationHandoff):ByteArray {
        require(listOf(r.ownerKey,r.athleteId,r.cycleId,r.blockId).all(id::matches))
        require(r.approvalRevision in 1..maximum &&
            r.sourceRevision in 1 until maximum &&
            r.firstWeek in 2..104 && r.historyManifestRevision>=1 &&
            listOf(r.approvalFingerprint,r.baseSourceSha256,
                r.proposedSourceSha256).all(hash::matches))
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(magic);out.writeInt(version)
            listOf(r.ownerKey,r.athleteId,r.cycleId,r.blockId).forEach(out::writeUTF)
            out.writeLong(r.approvalRevision)
            out.writeUTF(r.approvalFingerprint)
            out.writeLong(r.sourceRevision)
            out.writeUTF(r.baseSourceSha256)
            out.writeUTF(r.proposedSourceSha256)
            out.writeInt(r.firstWeek)
            out.writeLong(r.historyManifestRevision)
        }
        val payload=raw.toByteArray()
        require(payload.size+36<=4096)
        return ByteArrayOutputStream().also{result->
            DataOutputStream(result).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeFutureActivationHandoff? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||
            Files.size(file) !in 44L..4096L)
            throw IOException("Invalid future handoff file")
        val bytes=Files.readAllBytes(file)
        DataInputStream(ByteArrayInputStream(bytes)).use{input->
            val n=input.readInt()
            if(n !in 8..4060 || n+36!=bytes.size)
                throw IOException("Invalid handoff envelope")
            val raw=ByteArray(n);input.readFully(raw)
            val checksum=ByteArray(32);input.readFully(checksum)
            if(!MessageDigest.isEqual(checksum,
                MessageDigest.getInstance("SHA-256").digest(raw)))
                throw IOException("Corrupt handoff checksum")
            return DataInputStream(ByteArrayInputStream(raw)).use{data->
                if(data.readInt()!=magic || data.readInt()!=version)
                    throw IOException("Unknown handoff schema")
                val owner=data.readUTF();val athlete=data.readUTF()
                val cycle=data.readUTF();val block=data.readUTF()
                val approvalRev=data.readLong();val approvalHash=data.readUTF()
                val sourceRev=data.readLong();val base=data.readUTF()
                val proposed=data.readUTF();val week=data.readInt()
                val historyRev=data.readLong()
                if(data.available()!=0)throw IOException("Trailing handoff bytes")
                NativeFutureActivationHandoff(owner,athlete,cycle,block,
                    approvalRev,approvalHash,sourceRev,base,proposed,
                    week,historyRev).also{encode(it)}
            }
        }
    }
    private fun atomicWrite(file:Path,bytes:ByteArray,ownerKey:String) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))
            throw IOException("Unsafe handoff directory")
        val temp=Files.createTempFile(directory,pendingPrefix(ownerKey),".pending")
        try {
            FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buf=ByteBuffer.wrap(bytes)
                while(buf.hasRemaining())channel.write(buf)
                channel.force(true)
            }
            try {
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE)
            } catch(e:AtomicMoveNotSupportedException) {
                throw IOException("Atomic handoff unavailable",e)
            }
        } finally {Files.deleteIfExists(temp)}
    }
    private fun readFenced(ownerKey:String):NativeFutureHandoffReview {
        val signed=owner(ownerKey)
            ?:return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        if(hasPending(ownerKey))
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.RECORD_CORRUPT)
        val stored=try{decode(path(ownerKey))}catch(_:Exception){
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.RECORD_CORRUPT)
        } ?:return NativeFutureHandoffReview.Missing
        if(stored.ownerKey!=ownerKey || stored.athleteId!=signed.athleteId)
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        return NativeFutureHandoffReview.Staged(stored,false)
    }

    fun read(ownerKey:String):NativeFutureHandoffReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            readFenced(ownerKey)
        }}.getOrElse{NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.STORAGE_UNAVAILABLE)}

    /** Fail closed for corrupt or interrupted barrier files. */
    fun blocksNewIssuance(ownerKey:String):Boolean =
        read(ownerKey)!=NativeFutureHandoffReview.Missing

    /**
     * Re-read the EXACT athlete-confirmed second consent under the OS lock.
     * A UI fingerprint or a cached preview alone can never stage this gate.
     */
    fun stage(
        ownerKey:String,cycleId:String,blockId:String,
        approvalRevision:Long,approvedFingerprint:String,
        approvals:NativeKotlinFutureActivationApprovalFileStore,
        athleteConfirmed:Boolean
    ):NativeFutureHandoffReview {
        if(!athleteConfirmed)
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.CONFIRMATION_REQUIRED)
        if(!listOf(ownerKey,cycleId,blockId).all(id::matches) ||
            !hash.matches(approvedFingerprint))
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.NOT_AUTHORIZED)
            val prior=readFenced(ownerKey)
            if(prior is NativeFutureHandoffReview.Rejected)return@withOwnerTransaction prior
            val record=(approvals.read(ownerKey,cycleId,blockId)
                as? NativeFutureApprovalRead.Found)?.takeIf{it.isCurrent}
                ?.approval ?:return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.APPROVAL_NOT_CURRENT)
            val preview=approvals.preview(ownerKey,cycleId,blockId)
                as? NativeFutureApprovalPreview.Ready
                ?:return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.APPROVAL_NOT_CURRENT)
            if(record.revision!=approvalRevision ||
                record.reviewedFingerprint!=approvedFingerprint ||
                preview.fingerprint!=approvedFingerprint ||
                preview.expectedRecordRevision!=approvalRevision)
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.REVIEW_CHANGED)
            val source=(sources.read(ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.SOURCE_CHANGED)
            if(source.athleteId!=signed.athleteId ||
                source.revision!=record.sourceRevision ||
                preview.sourcePlan.proposedSource.revision!=source.revision+1)
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.SOURCE_CHANGED)
            val next=NativeFutureActivationHandoff(ownerKey,signed.athleteId,
                cycleId,blockId,approvalRevision,approvedFingerprint,
                source.revision,
                digest(NativeKotlinCustomAuthoringSourceCodec.encode(source)),
                digest(NativeKotlinCustomAuthoringSourceCodec.encode(
                    preview.sourcePlan.proposedSource)),
                preview.boundary.firstWeek,preview.boundary.historyManifestRevision)
            if(prior is NativeFutureHandoffReview.Staged)
                return@withOwnerTransaction if(prior.receipt==next)
                    NativeFutureHandoffReview.Staged(next,true)
                else NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.ALREADY_STAGED)
            if(owner(ownerKey)!=signed)
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.NOT_AUTHORIZED)
            atomicWrite(path(ownerKey),encode(next),ownerKey)
            NativeFutureHandoffReview.Staged(next,false)
        }}.getOrElse{NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Explicit cancellation only while the original source is UNCHANGED.
     * Once an eventual executor alters the source, stale UI callbacks cannot
     * remove its issuance barrier; recovery must finish the transaction.
     */
    fun cancel(
        ownerKey:String,approvalRevision:Long,
        approvedFingerprint:String,athleteConfirmed:Boolean
    ):NativeFutureHandoffReview {
        if(!athleteConfirmed)
            return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.CONFIRMATION_REQUIRED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val found=readFenced(ownerKey)
            val current=(found as? NativeFutureHandoffReview.Staged)?.receipt
                ?:return@withOwnerTransaction found
            if(current.approvalRevision!=approvalRevision ||
                current.approvalFingerprint!=approvedFingerprint)
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.REVIEW_CHANGED)
            val source=(sources.read(ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.SOURCE_CHANGED)
            if(source.revision!=current.sourceRevision ||
                digest(NativeKotlinCustomAuthoringSourceCodec.encode(source))!=
                    current.baseSourceSha256)
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.SOURCE_CHANGED)
            // A persisted write-ahead activation intent cannot be erased
            // by an earlier UI cancellation callback. Cancel that intent
            // under the same fence FIRST, with its own source CAS proof.
            if(NativeKotlinFutureActivationCommitTransaction.hasCommitRecord(
                    directory,ownerKey))
                return@withOwnerTransaction NativeFutureHandoffReview.Rejected(
                    NativeFutureHandoffRefusal.ALREADY_STAGED)
            Files.delete(path(ownerKey))
            NativeFutureHandoffReview.Cancelled
        }}.getOrElse{NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.STORAGE_UNAVAILABLE)}
    }
}
