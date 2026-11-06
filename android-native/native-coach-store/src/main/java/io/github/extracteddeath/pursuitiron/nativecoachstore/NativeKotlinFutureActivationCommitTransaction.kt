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
import io.github.extracteddeath.pursuitiron.rebuild.CustomCoachAudit
import io.github.extracteddeath.pursuitiron.rebuild.CustomCoachAuditInputs
import io.github.extracteddeath.pursuitiron.rebuild.CustomCoachAuditStatus
import io.github.extracteddeath.pursuitiron.rebuild.CustomCycleCoachAuditDomain

/**
 * Owner-fenced WRITE-AHEAD activation of a second-consented future block.
 *
 * Before changing the active source, persist the exact prospective setup,
 * full phase policy, weekday overhead, and current approval/handoff scope.
 * Cold recovery must either replay that SAME source CAS or find the exact
 * proposed source already durable. A conflicting source never gets overwritten.
 *
 * This does not issue a workout or clear the handoff's issuance barrier.
 * The next phase must re-audit/sign off the newly active cycle and install
 * the corresponding policy/issuance authority before it can release issuance.
 */
data class NativeFutureActivatedCoaching(
    val cycleId:String,
    val blockId:String,
    val firstWeek:Int,
    val sourceRevision:Long,
    val policy:io.github.extracteddeath.pursuitiron.rebuild.AuthoredGoalProgramPolicy,
    val nonSetSecondsByWeekday:Map<Int,Int>
) {
    val mayIssueWorkout:Boolean get()=false
}
enum class NativeFutureCommitRefusal {
    NOT_AUTHORIZED, HANDOFF_MISSING, APPROVAL_STALE,
    SOURCE_CONFLICT, INTENT_CORRUPT, INTENT_CONFLICT,
    STORAGE_UNAVAILABLE
}
sealed interface NativeFutureCommitReview {
    data object Missing:NativeFutureCommitReview
    data object Cancelled:NativeFutureCommitReview
    data class Prepared(val cycleId:String,val blockId:String):
        NativeFutureCommitReview
    data class Applied(val context:NativeFutureActivatedCoaching,
        val replayed:Boolean):NativeFutureCommitReview
    data class Rejected(val reason:NativeFutureCommitRefusal):
        NativeFutureCommitReview
}
sealed interface NativeFutureActivatedCycleReview {
    data class Ready(
        val audit:CustomCoachAudit,val cycleId:String,val blockId:String,
        val sourceRevision:Long,val firstWeek:Int
    ):NativeFutureActivatedCycleReview {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Unresolved(val audit:CustomCoachAudit):
        NativeFutureActivatedCycleReview
    data class Rejected(val reason:NativeFutureCommitRefusal):
        NativeFutureActivatedCycleReview
}
private data class NativeFutureCommitIntent(
    val handoff:NativeFutureActivationHandoff,
    val baseSourceBytes:ByteArray,
    val prospectiveBundle:ByteArray
)

class NativeKotlinFutureActivationCommitTransaction(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val contexts:NativeKotlinFutureBlockContextFileStore,
    private val approvals:NativeKotlinFutureActivationApprovalFileStore,
    private val handoffs:NativeKotlinFutureActivationHandoffFileStore,
    /** Deterministic crash seams, never supplied by the production host. */
    private val afterIntentPersisted:()->Unit={},
    private val afterSourcePublished:()->Unit={}
) {
    private val hash=Regex("^[a-f0-9]{64}$")
    private val max=9_007_199_254_740_991L
    private val magic=0x50494654 // PIFT
    private val version=2
    private val maxFile=NativeFutureBlockContextCodec.MAX_BYTES+
        NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES+8192
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun owner(key:String)=runCatching{identity.signedInIdentity()}
        .getOrNull()?.takeIf{it.ownerKey==key}
    private fun path(key:String)=directory.resolve(
        "future-activation-commit-"+sha(key.toByteArray(Charsets.UTF_8))+".bin")
    private fun pendingPrefix(key:String)="future-activation-commit-"+
        sha(key.toByteArray(Charsets.UTF_8))+"-"

    companion object {
        internal fun hasCommitRecord(directory:Path,ownerKey:String):Boolean {
            val name=MessageDigest.getInstance("SHA-256")
                .digest(ownerKey.toByteArray(Charsets.UTF_8))
                .joinToString(""){"%02x".format(it.toInt() and 255)}
            if(!Files.exists(directory))return false
            if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory))
                return true
            Files.list(directory).use{stream->
                return stream.anyMatch{entry->
                    entry.fileName.toString().startsWith("future-activation-commit-"+name)
                }
            }
        }
    }
    private fun encode(intent:NativeFutureCommitIntent):ByteArray {
        val h=intent.handoff
        require(h.sourceRevision in 1 until max &&
            h.approvalRevision in 1..max &&
            h.firstWeek in 2..104 && h.historyManifestRevision in 1..max &&
            listOf(h.approvalFingerprint,h.baseSourceSha256,
                h.proposedSourceSha256).all(hash::matches))
        require(intent.prospectiveBundle.size in 44..NativeFutureBlockContextCodec.MAX_BYTES)
        require(intent.baseSourceBytes.size in
            16..NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES)
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(magic);out.writeInt(version)
            listOf(h.ownerKey,h.athleteId,h.cycleId,h.blockId).forEach{out.writeUTF(it)}
            out.writeLong(h.approvalRevision);out.writeUTF(h.approvalFingerprint)
            out.writeLong(h.sourceRevision);out.writeUTF(h.baseSourceSha256)
            out.writeUTF(h.proposedSourceSha256);out.writeInt(h.firstWeek)
            out.writeLong(h.historyManifestRevision)
            out.writeInt(intent.baseSourceBytes.size)
            out.write(intent.baseSourceBytes)
            out.writeInt(intent.prospectiveBundle.size)
            out.write(intent.prospectiveBundle)
        }
        val bytes=raw.toByteArray()
        require(bytes.size+36<=maxFile)
        return ByteArrayOutputStream().also{result->
            DataOutputStream(result).use{out->
                out.writeInt(bytes.size);out.write(bytes)
                out.write(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeFutureCommitIntent? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||
            Files.size(file) !in 44L..maxFile.toLong())
            throw IOException("Invalid activation intent file")
        val bytes=Files.readAllBytes(file)
        val input=DataInputStream(ByteArrayInputStream(bytes))
        val count=input.readInt()
        if(count !in 8..maxFile-36 || count+36!=bytes.size)
            throw IOException("Invalid activation envelope")
        val raw=ByteArray(count);input.readFully(raw)
        val checksum=ByteArray(32);input.readFully(checksum)
        if(!MessageDigest.isEqual(checksum,
            MessageDigest.getInstance("SHA-256").digest(raw)))
            throw IOException("Activation intent checksum mismatch")
        return DataInputStream(ByteArrayInputStream(raw)).use{data->
            if(data.readInt()!=magic||data.readInt()!=version)
                throw IOException("Unknown activation intent schema")
            val h=NativeFutureActivationHandoff(
                data.readUTF(),data.readUTF(),data.readUTF(),data.readUTF(),
                data.readLong(),data.readUTF(),data.readLong(),data.readUTF(),
                data.readUTF(),data.readInt(),data.readLong())
            val baseLength=data.readInt()
            if(baseLength !in 16..NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES ||
                baseLength>data.available())throw IOException("Invalid base source")
            val baseBytes=ByteArray(baseLength);data.readFully(baseBytes)
            val n=data.readInt()
            if(n !in 44..NativeFutureBlockContextCodec.MAX_BYTES ||
                n>data.available())throw IOException("Invalid prospective payload")
            val bundle=ByteArray(n);data.readFully(bundle)
            if(data.available()!=0)throw IOException("Trailing activation bytes")
            val intent=NativeFutureCommitIntent(h,baseBytes,bundle)
            // Validate both envelopes and their explicit source hashes.
            encode(intent);validate(intent)
            intent
        }
    }
    private data class Decoded(
        val original:NativeCustomAuthoringSnapshot,
        val proposed:NativeCustomAuthoringSnapshot,
        val future:NativeFutureBlockContextReceipt
    )
    private fun validate(intent:NativeFutureCommitIntent):Decoded {
        val h=intent.handoff
        val original=NativeKotlinCustomAuthoringSourceCodec.decode(
            intent.baseSourceBytes)
        val (saved,futureSource)=NativeFutureBlockContextCodec.decode(
            intent.prospectiveBundle)
        val receipt=saved.receipt
        val proposed=futureSource.copy(revision=h.sourceRevision+1)
        val originalFingerprint=saved.sourceSha
        if(receipt.ownerKey!=h.ownerKey ||
            receipt.athleteId!=h.athleteId ||
            receipt.cycleId!=h.cycleId || receipt.blockId!=h.blockId ||
            receipt.sourceRevision!=h.sourceRevision ||
            sha(NativeKotlinCustomAuthoringSourceCodec.encode(proposed))!=
                h.proposedSourceSha256 ||
            originalFingerprint!=h.baseSourceSha256 ||
            sha(intent.baseSourceBytes)!=h.baseSourceSha256 ||
            original.revision!=h.sourceRevision ||
            original.ownerKey!=h.ownerKey ||
            original.athleteId!=h.athleteId ||
            futureSource.catalog!=original.catalog)
            throw IOException("Intent provenance mismatch")
        // Retain exact old athlete context for a full-cycle review after
        // the active source changes. Prior completed blocks are never
        // retroactively judged using the future split/calendar.
        return Decoded(original,proposed,receipt)
    }
    private fun atomic(file:Path,bytes:ByteArray,key:String) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("Unsafe activation folder")
        val temporary=Files.createTempFile(directory,pendingPrefix(key),".pending")
        try {
            FileChannel.open(temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val b=ByteBuffer.wrap(bytes)
                while(b.hasRemaining())channel.write(b)
                channel.force(true)
            }
            try{
                Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE)
            }catch(e:AtomicMoveNotSupportedException){
                throw IOException("Atomic activation intent unsupported",e)
            }
        }finally{Files.deleteIfExists(temporary)}
    }
    private fun intent(key:String):NativeFutureCommitIntent? {
        if(hasCommitRecord(directory,key)&&!Files.exists(path(key)))
            throw IOException("Abandoned activation intent")
        if(Files.exists(directory)&&Files.list(directory).use{stream->
            stream.anyMatch{it.fileName.toString().startsWith(pendingPrefix(key))}
        })throw IOException("Interrupted activation file")
        return decode(path(key))
    }
    private fun resolved(
        key:String,h:NativeFutureActivationHandoff,
        saved:NativeFutureCommitIntent,
        replayed:Boolean
    ):NativeFutureCommitReview {
        if(saved.handoff!=h)
            return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.INTENT_CONFLICT)
        val decoded=try{validate(saved)}catch(_:Exception){
            return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.INTENT_CORRUPT)
        }
        val current=(sources.read(key) as? NativeAuthoringSourceRead.Found)?.source
            ?:return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.SOURCE_CONFLICT)
        if(current.athleteId!=h.athleteId ||
            current.ownerKey!=key)
            return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
        val currentSha=sha(NativeKotlinCustomAuthoringSourceCodec.encode(current))
        if(currentSha==h.proposedSourceSha256 &&
            current==decoded.proposed)
            return NativeFutureCommitReview.Applied(
                NativeFutureActivatedCoaching(h.cycleId,h.blockId,h.firstWeek,
                    current.revision,decoded.future.future.policy,
                    decoded.future.future.nonSetSecondsByWeekday),replayed)
        if(currentSha!=h.baseSourceSha256 ||
            current.revision!=h.sourceRevision)
            return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.SOURCE_CONFLICT)
        return NativeFutureCommitReview.Prepared(h.cycleId,h.blockId)
    }
    fun read(ownerKey:String):NativeFutureCommitReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(owner(ownerKey)==null)return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
            val h=(handoffs.read(ownerKey) as? NativeFutureHandoffReview.Staged)
                ?.receipt ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.HANDOFF_MISSING)
            val saved=intent(ownerKey) ?:return@withOwnerTransaction NativeFutureCommitReview.Missing
            resolved(ownerKey,h,saved,true)
        }}.getOrElse{NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.INTENT_CORRUPT)}

    /**
     * Safe rollback only BEFORE the source CAS and only by explicit athlete
     * confirmation. Clear the write-ahead intent first, then the handoff;
     * a crash between those operations still leaves the issuance barrier.
     */
    fun abortBeforeSourceApplied(
        ownerKey:String,approvalRevision:Long,approvedFingerprint:String,
        athleteConfirmed:Boolean
    ):NativeFutureCommitReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(!athleteConfirmed || !hash.matches(approvedFingerprint))
                return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.NOT_AUTHORIZED)
            val staged=(handoffs.read(ownerKey) as? NativeFutureHandoffReview.Staged)
                ?.receipt ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.HANDOFF_MISSING)
            if(staged.approvalRevision!=approvalRevision ||
                staged.approvalFingerprint!=approvedFingerprint)
                return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.APPROVAL_STALE)
            val saved=intent(ownerKey)
            if(saved!=null) {
                if(resolved(ownerKey,staged,saved,true) !is
                    NativeFutureCommitReview.Prepared)
                    return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.SOURCE_CONFLICT)
                Files.delete(path(ownerKey))
            }
            when(handoffs.cancel(ownerKey,approvalRevision,approvedFingerprint,true)){
                NativeFutureHandoffReview.Cancelled->NativeFutureCommitReview.Cancelled
                else->NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.SOURCE_CONFLICT)
            }
        }}.getOrElse{NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.STORAGE_UNAVAILABLE)}

    /**
     * Recheck the ENTIRE authored cycle after applying the future profile.
     * The original reviewed setup is preserved in the durable activation
     * intent; the accepted future setup/policy applies only to its named
     * block. This is still a read-only, non-issuance review, not a signoff.
     *
     * No cached "approved" UI state or unbounded caller-supplied session
     * estimate may skip the native full-cycle continuity/dose audit.
     */
    fun reviewActivatedCycle(
        ownerKey:String,inputs:CustomCoachAuditInputs
    ):NativeFutureActivatedCycleReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.NOT_AUTHORIZED)
            val handoff=(handoffs.read(ownerKey) as? NativeFutureHandoffReview.Staged)
                ?.receipt ?:return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.HANDOFF_MISSING)
            val intent=intent(ownerKey)
                ?:return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.INTENT_CONFLICT)
            if(resolved(ownerKey,handoff,intent,true) !is NativeFutureCommitReview.Applied)
                return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.SOURCE_CONFLICT)
            val decoded=validate(intent)
            val (bundle,_)=NativeFutureBlockContextCodec.decode(
                intent.prospectiveBundle)
            val draft=(drafts.read(ownerKey,handoff.cycleId)
                as? NativeCustomDraftReadResult.Found)?.draft
                ?:return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.APPROVAL_STALE)
            val draftHash=sha(NativeCustomCycleDraftCodec.encode(draft))
            if(draftHash!=bundle.draftSha ||
                draft.revision!=decoded.future.draftRevision ||
                draft.athleteId!=signed.athleteId ||
                owner(ownerKey)!=signed)
                return@withOwnerTransaction NativeFutureActivatedCycleReview.Rejected(
                    NativeFutureCommitRefusal.APPROVAL_STALE)
            val audit=CustomCycleCoachAuditDomain.review(
                draft,decoded.original.setup,decoded.original.catalog,inputs,
                mapOf(handoff.blockId to decoded.future.future))
            if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                audit.issues.isNotEmpty() ||
                audit.prospectiveBlockIds!=setOf(handoff.blockId))
                NativeFutureActivatedCycleReview.Unresolved(audit)
            else NativeFutureActivatedCycleReview.Ready(
                audit,handoff.cycleId,handoff.blockId,
                decoded.proposed.revision,handoff.firstWeek)
        }}.getOrElse{NativeFutureActivatedCycleReview.Rejected(
            NativeFutureCommitRefusal.STORAGE_UNAVAILABLE)}

    /** Idempotent under crash replay; never changes an already-applied source. */
    fun commit(ownerKey:String):NativeFutureCommitReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.NOT_AUTHORIZED)
            val handoff=(handoffs.read(ownerKey) as? NativeFutureHandoffReview.Staged)
                ?.receipt ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.HANDOFF_MISSING)
            var saved=intent(ownerKey)
            if(saved==null){
                // Fresh real boundary, verified custom+Composer inventories,
                // second consent and the exact reviewed source are all checked
                // AGAIN while holding the owner fence.
                val approved=(approvals.read(ownerKey,handoff.cycleId,handoff.blockId)
                    as? NativeFutureApprovalRead.Found)?.takeIf{it.isCurrent}
                    ?.approval ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.APPROVAL_STALE)
                val preview=approvals.preview(ownerKey,handoff.cycleId,handoff.blockId)
                    as? NativeFutureApprovalPreview.Ready
                    ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.APPROVAL_STALE)
                val base=(sources.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.SOURCE_CONFLICT)
                val future=(contexts.read(ownerKey,handoff.cycleId,handoff.blockId)
                    as? NativeFutureBlockContextRead.Found)?.receipt
                    ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.APPROVAL_STALE)
                val draft=(drafts.read(ownerKey,handoff.cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.APPROVAL_STALE)
                val proposal=preview.sourcePlan.proposedSource
                if(approved.revision!=handoff.approvalRevision ||
                    approved.reviewedFingerprint!=handoff.approvalFingerprint ||
                    preview.fingerprint!=handoff.approvalFingerprint ||
                    preview.boundary.firstWeek!=handoff.firstWeek ||
                    preview.boundary.historyManifestRevision!=handoff.historyManifestRevision ||
                    base.revision!=handoff.sourceRevision ||
                    sha(NativeKotlinCustomAuthoringSourceCodec.encode(base))!=
                        handoff.baseSourceSha256 ||
                    sha(NativeKotlinCustomAuthoringSourceCodec.encode(proposal))!=
                        handoff.proposedSourceSha256 ||
                    future.draftRevision!=draft.revision ||
                    future.sourceRevision!=base.revision ||
                    signed.athleteId!=handoff.athleteId)
                    return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                        NativeFutureCommitRefusal.APPROVAL_STALE)
                val draftHash=sha(NativeCustomCycleDraftCodec.encode(draft))
                val bundle=NativeFutureBlockContextCodec.encode(
                    NativeFutureBlockRecord(future,draftHash,
                        handoff.baseSourceSha256),base)
                saved=NativeFutureCommitIntent(handoff,
                    NativeKotlinCustomAuthoringSourceCodec.encode(base),bundle)
                validate(saved!!)
                atomic(path(ownerKey),encode(saved!!),ownerKey)
                afterIntentPersisted()
            }
            val stable=checkNotNull(saved)
            val state=resolved(ownerKey,handoff,stable,true)
            if(state !is NativeFutureCommitReview.Prepared)
                return@withOwnerTransaction state
            // A staged intent can be replayed only after all source/boundary
            // checks remain current. History may have changed during a crash.
            val current=(approvals.read(ownerKey,handoff.cycleId,handoff.blockId)
                as? NativeFutureApprovalRead.Found)?.takeIf{it.isCurrent}
                ?.approval
            val preview=approvals.preview(ownerKey,handoff.cycleId,handoff.blockId)
                as? NativeFutureApprovalPreview.Ready
            if(current?.revision!=handoff.approvalRevision ||
                current.reviewedFingerprint!=handoff.approvalFingerprint ||
                preview?.fingerprint!=handoff.approvalFingerprint ||
                preview.sourcePlan.proposedSource!=validate(stable).proposed ||
                owner(ownerKey)!=signed)
                return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.APPROVAL_STALE)
            val proposed=validate(stable).proposed
            val result=sources.publish(ownerKey,handoff.sourceRevision,proposed)
            if(result!=NativeAuthoringSourceWrite.Committed(proposed.revision))
                return@withOwnerTransaction NativeFutureCommitReview.Rejected(
                    NativeFutureCommitRefusal.SOURCE_CONFLICT)
            afterSourcePublished()
            val applied=resolved(ownerKey,handoff,stable,false)
            if(applied is NativeFutureCommitReview.Applied)applied
            else NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.SOURCE_CONFLICT)
        }}.getOrElse{NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.STORAGE_UNAVAILABLE)}
}
