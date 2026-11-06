package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomCycleChangeKind
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
 * Two distinct athlete decisions: accepting a FUTURE cycle draft does not
 * authorize transitioning the CURRENT source. This second, owner-fenced
 * approval records explicit consent after the entire completed-history,
 * custom-issue and Composer-issue activation preflight has been rechecked.
 *
 * This is a CRASH-SAFE APPROVAL CHECKPOINT ONLY. It never changes the active
 * athlete, gym, cycle, coach policy, issued session, or history. A future
 * atomic source transition must separately re-audit and consume this receipt.
 */
data class NativeFutureActivationApproval(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val revision:Long,
    val draftRevision:Long,
    val sourceRevision:Long,
    val historyManifestRevision:Long,
    val firstWeek:Int,
    val permissionRevision:Long,
    val reviewedFingerprint:String
) {
    val mayActivateProfile:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

enum class NativeFutureApprovalRefusal {
    NOT_AUTHORIZED, NOT_READY, PERMISSION_NOT_CURRENT,
    REVIEW_CHANGED, REVISION_CONFLICT, CONFIRMATION_REQUIRED,
    CORRUPT_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeFutureApprovalPreview {
    data class Ready(
        val boundary:NativeFutureActivationReview.BoundaryReviewed,
        val fingerprint:String,
        val expectedRecordRevision:Long,
        /** Fully codable, review-only future source. No source write occurs. */
        val sourcePlan:NativeFutureActivationSourcePlan
    ):NativeFutureApprovalPreview
    data class Rejected(val reason:NativeFutureApprovalRefusal):
        NativeFutureApprovalPreview
}
sealed interface NativeFutureApprovalRead {
    data object Missing:NativeFutureApprovalRead
    data class Found(val approval:NativeFutureActivationApproval,
        val isCurrent:Boolean):NativeFutureApprovalRead
    data class Revoked(val revision:Long):NativeFutureApprovalRead
    data class Rejected(val reason:NativeFutureApprovalRefusal):
        NativeFutureApprovalRead
}
sealed interface NativeFutureApprovalWrite {
    data class Committed(val approval:NativeFutureActivationApproval):
        NativeFutureApprovalWrite
    data class Revoked(val revision:Long):NativeFutureApprovalWrite
    data class Rejected(val reason:NativeFutureApprovalRefusal):
        NativeFutureApprovalWrite
}

private data class NativeFutureApprovalDiskRecord(
    val owner:String,
    val athlete:String,
    val cycle:String,
    val block:String,
    val revision:Long,
    val approval:NativeFutureActivationApproval?
)

/** Scoped, checksummed and atomic. The owner-fence serializes all CAS writers. */
class NativeKotlinFutureActivationApprovalFileStore internal constructor(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    // Test seams can supply deterministic BOUNDARY evidence; production host
    // always supplies the real owner-fenced fully verified preflight below.
    // No checkpoint produced by either path can authorize source mutation.
    private val boundaryReview:(String,String,String)->NativeFutureActivationReview,
    private val futureContexts:NativeKotlinFutureBlockContextFileStore,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val permissions:NativeKotlinAdaptiveCyclePermissionFileStore
) {
    constructor(
        directory:Path,
        fence:NativeKotlinApprovalRevisionFence,
        identity:NativeKotlinCurrentIdentity,
        boundary:NativeKotlinFutureBlockActivationPreflight,
        futureContexts:NativeKotlinFutureBlockContextFileStore,
        drafts:NativeCustomCycleDraftFileStore,
        sources:NativeKotlinCustomAuthoringSourceFileStore,
        permissions:NativeKotlinAdaptiveCyclePermissionFileStore
    ):this(directory,fence,identity,
        {owner,cycle,block->boundary.inspect(owner,cycle,block)},
        futureContexts,drafts,sources,permissions)
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val hash=Regex("^[a-f0-9]{64}$")
    private val maximum=9_007_199_254_740_991L
    private val magic=0x50494143 // PIAC
    private val version=1

    private fun owner(key:String):NativeKotlinOwnerIdentity? =
        runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{id.matches(key)&&it.ownerKey==key&&id.matches(it.athleteId)}
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun path(owner:String,cycle:String,block:String)=directory.resolve(
        "future-activation-"+sha(
            "$owner\u0000$cycle\u0000$block".toByteArray(Charsets.UTF_8))+".bin")

    private fun encode(record:NativeFutureApprovalDiskRecord):ByteArray {
        require(listOf(record.owner,record.athlete,record.cycle,
            record.block).all(id::matches) &&
            record.revision in 1..maximum)
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(record.owner);out.writeUTF(record.athlete)
            out.writeUTF(record.cycle);out.writeUTF(record.block)
            out.writeLong(record.revision)
            out.writeBoolean(record.approval!=null)
            record.approval?.let{r->
                require(r.ownerKey==record.owner &&
                    r.athleteId==record.athlete &&
                    r.cycleId==record.cycle && r.blockId==record.block &&
                    r.revision==record.revision &&
                    hash.matches(r.reviewedFingerprint))
                out.writeLong(r.draftRevision);out.writeLong(r.sourceRevision)
                out.writeLong(r.historyManifestRevision)
                out.writeInt(r.firstWeek);out.writeLong(r.permissionRevision)
                out.writeUTF(r.reviewedFingerprint)
            }
        }
        val bytes=raw.toByteArray()
        require(bytes.size+36<=4096)
        return ByteArrayOutputStream().also{output->
            DataOutputStream(output).use{out->
                out.writeInt(bytes.size);out.write(bytes)
                out.write(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeFutureApprovalDiskRecord? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
            Files.size(file) !in 44L..4096L)
            throw IOException("Invalid future approval file")
        val input=DataInputStream(ByteArrayInputStream(Files.readAllBytes(file)))
        val n=input.readInt()
        if(n !in 8..4060 || n+36!=Files.size(file).toInt())
            throw IOException("Invalid future approval envelope")
        val raw=ByteArray(n);input.readFully(raw)
        val checksum=ByteArray(32);input.readFully(checksum)
        if(!MessageDigest.isEqual(checksum,
            MessageDigest.getInstance("SHA-256").digest(raw)))
            throw IOException("Future approval checksum mismatch")
        return DataInputStream(ByteArrayInputStream(raw)).use{data->
            if(data.readInt()!=magic || data.readInt()!=version)
                throw IOException("Unknown future approval schema")
            val o=data.readUTF();val a=data.readUTF()
            val c=data.readUTF();val b=data.readUTF()
            val rev=data.readLong()
            if(!listOf(o,a,c,b).all(id::matches) || rev !in 1..maximum)
                throw IOException("Invalid future approval scope")
            val approval=if(data.readBoolean()){
                val draftRev=data.readLong();val sourceRev=data.readLong()
                val historyRev=data.readLong();val firstWeek=data.readInt()
                val permissionRev=data.readLong();val fingerprint=data.readUTF()
                if(draftRev !in 1..maximum || sourceRev !in 1..maximum ||
                    historyRev !in 1..maximum || firstWeek !in 2..104 ||
                    permissionRev !in 1..maximum || !hash.matches(fingerprint))
                    throw IOException("Invalid approval provenance")
                NativeFutureActivationApproval(o,a,c,b,rev,
                    draftRev,sourceRev,historyRev,firstWeek,
                    permissionRev,fingerprint)
            }else null
            if(data.available()!=0)throw IOException("Trailing approval bytes")
            NativeFutureApprovalDiskRecord(o,a,c,b,rev,approval)
        }
    }
    private fun atomicWrite(file:Path,bytes:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("Unsafe approval folder")
        val temporary=Files.createTempFile(directory,"future-activation-", ".pending")
        try {
            FileChannel.open(temporary,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try{
                Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("Atomic future consent unsupported",ex)
            }
        }finally{Files.deleteIfExists(temporary)}
    }

    /** Must be called inside owner fence; never consumes UI-supplied evidence. */
    private fun evidence(
        ownerKey:String,cycleId:String,blockId:String
    ):Triple<NativeFutureActivationApproval,NativeFutureActivationReview.BoundaryReviewed,NativeFutureActivationSourcePlan>? {
        val signed=owner(ownerKey)?:return null
        val future=(futureContexts.read(ownerKey,cycleId,blockId)
            as? NativeFutureBlockContextRead.Found)?.receipt ?:return null
        val source=(sources.read(ownerKey)
            as? NativeAuthoringSourceRead.Found)?.source ?:return null
        val draft=(drafts.read(ownerKey,cycleId)
            as? NativeCustomDraftReadResult.Found)?.draft ?:return null
        val rights=(permissions.read(ownerKey,cycleId)
            as? NativeAdaptivePermissionRead.Found)?.receipt ?:return null
        if(future.athleteId!=signed.athleteId ||
            future.sourceRevision!=source.revision ||
            rights.athleteId!=signed.athleteId ||
            // All changed future split, schedule, frequency and exercise
            // decisions must remain athlete-permitted at this boundary.
            !rights.allowedKinds.containsAll(setOf(
                CustomCycleChangeKind.SPLIT,CustomCycleChangeKind.SCHEDULE,
                CustomCycleChangeKind.DAY_COUNT,CustomCycleChangeKind.EXERCISES)))
            return null
        val reviewed=(boundaryReview(ownerKey,cycleId,blockId)
            as? NativeFutureActivationReview.BoundaryReviewed)?:return null
        // This receipt is the proof used by the NEXT source-activation
        // transaction, not an arbitrary "ready" flag. Check the boundary's
        // owner-independent identity and arithmetic against this exact
        // accepted future block, even if a future adapter regresses.
        // An implausible or cross-cycle preflight must never be signed.
        if(reviewed.cycleId!=cycleId || reviewed.blockId!=blockId ||
            reviewed.firstWeek !in 2..104 ||
            reviewed.precedingWeeks!=reviewed.firstWeek-1 ||
            reviewed.historyManifestRevision<1 ||
            reviewed.verifiedPriorSessions<reviewed.precedingWeeks)
            return null
        val plan=NativeFutureActivationSourcePlanDomain.prepare(
            source,future,reviewed,draft)?:return null
        val scope=ByteArrayOutputStream()
        DataOutputStream(scope).use{out->
            // Consent fingerprint schema v2: includes both prospective
            // source bytes and the full policy/time payload, not labels.
            out.writeInt(2)
            listOf(ownerKey,signed.athleteId,cycleId,blockId,
                future.reviewedFingerprint).forEach{out.writeUTF(it)}
            out.writeUTF(NativeFutureBlockContextCodec.coachingContentFingerprint(
                future.future))
            out.writeLong(future.draftRevision)
            out.writeLong(source.revision)
            out.writeLong(rights.revision)
            rights.allowedKinds.map{it.name}.sorted().let{names->
                out.writeInt(names.size);names.forEach{out.writeUTF(it)}
            }
            out.writeLong(reviewed.historyManifestRevision)
            out.writeInt(reviewed.firstWeek)
            out.writeInt(reviewed.precedingWeeks)
            out.writeInt(reviewed.verifiedPriorSessions)
            // Bind explicit next source bytes to this athlete review.
            // Source changes cannot retain an old approval fingerprint.
            out.writeUTF(sha(
                NativeKotlinCustomAuthoringSourceCodec.encode(plan.proposedSource)))
        }
        val fingerprint=sha(scope.toByteArray())
        return NativeFutureActivationApproval(ownerKey,signed.athleteId,
            cycleId,blockId,0L,future.draftRevision,source.revision,
            reviewed.historyManifestRevision,reviewed.firstWeek,
            rights.revision,fingerprint).let {Triple(it,reviewed,plan)}
    }
    private fun stored(
        ownerKey:String,cycleId:String,blockId:String
    ):NativeFutureApprovalDiskRecord? {
        val previous=decode(path(ownerKey,cycleId,blockId))
        if(previous!=null &&
            (previous.owner!=ownerKey || previous.cycle!=cycleId ||
                previous.block!=blockId))
            throw IOException("Cross-scope approval file")
        return previous
    }
    private fun validScope(ownerKey:String,cycleId:String,blockId:String)=
        owner(ownerKey)!=null &&
            listOf(cycleId,blockId).all(id::matches)

    /** A reviewable fingerprint binds current source, rights and history. */
    fun preview(
        ownerKey:String,cycleId:String,blockId:String
    ):NativeFutureApprovalPreview {
        if(!validScope(ownerKey,cycleId,blockId))
            return NativeFutureApprovalPreview.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val current=owner(ownerKey) ?:return@withOwnerTransaction NativeFutureApprovalPreview.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val record=stored(ownerKey,cycleId,blockId)
                if(record!=null && record.athlete!=current.athleteId)
                    return@withOwnerTransaction NativeFutureApprovalPreview.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val (candidate,bound,plan)=evidence(ownerKey,cycleId,blockId)
                    ?:return@withOwnerTransaction NativeFutureApprovalPreview.Rejected(
                        NativeFutureApprovalRefusal.NOT_READY)
                NativeFutureApprovalPreview.Ready(bound,
                    candidate.reviewedFingerprint,record?.revision?:0L,
                    plan)
            }
        }.getOrElse{NativeFutureApprovalPreview.Rejected(
            NativeFutureApprovalRefusal.STORAGE_UNAVAILABLE)}
    }
    /** Exact reviewed fingerprint and fresh athlete confirmation are mandatory. */
    fun confirm(
        ownerKey:String,cycleId:String,blockId:String,
        expectedRevision:Long,reviewedFingerprint:String,
        athleteConfirmed:Boolean
    ):NativeFutureApprovalWrite {
        if(!athleteConfirmed)return NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.CONFIRMATION_REQUIRED)
        if(!hash.matches(reviewedFingerprint) || expectedRevision !in 0 until maximum)
            return NativeFutureApprovalWrite.Rejected(
                NativeFutureApprovalRefusal.REVIEW_CHANGED)
        if(!validScope(ownerKey,cycleId,blockId))
            return NativeFutureApprovalWrite.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signed=owner(ownerKey)?:return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val previous=stored(ownerKey,cycleId,blockId)
                if(previous!=null && previous.athlete!=signed.athleteId)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                if((previous?.revision?:0L)!=expectedRevision)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.REVISION_CONFLICT)
                val (candidate,_,_)=evidence(ownerKey,cycleId,blockId)
                    ?:return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_READY)
                if(candidate.reviewedFingerprint!=reviewedFingerprint)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.REVIEW_CHANGED)
                if(owner(ownerKey)!=signed)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val committed=candidate.copy(revision=expectedRevision+1)
                atomicWrite(path(ownerKey,cycleId,blockId),encode(
                    NativeFutureApprovalDiskRecord(ownerKey,signed.athleteId,
                        cycleId,blockId,committed.revision,committed)))
                NativeFutureApprovalWrite.Committed(committed)
            }
        }.getOrElse{NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.STORAGE_UNAVAILABLE)}
    }
    /** A receipt survives cold launch but cannot bypass a fresh boundary scan. */
    fun read(ownerKey:String,cycleId:String,blockId:String):NativeFutureApprovalRead {
        if(!validScope(ownerKey,cycleId,blockId))
            return NativeFutureApprovalRead.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signed=owner(ownerKey)?:return@withOwnerTransaction NativeFutureApprovalRead.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val previous=stored(ownerKey,cycleId,blockId)
                    ?:return@withOwnerTransaction NativeFutureApprovalRead.Missing
                if(previous.athlete!=signed.athleteId)
                    return@withOwnerTransaction NativeFutureApprovalRead.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val receipt=previous.approval
                    ?:return@withOwnerTransaction NativeFutureApprovalRead.Revoked(
                        previous.revision)
                val now=evidence(ownerKey,cycleId,blockId)?.first
                NativeFutureApprovalRead.Found(receipt,
                    now!=null &&
                        now.reviewedFingerprint==receipt.reviewedFingerprint &&
                        now.draftRevision==receipt.draftRevision &&
                        now.sourceRevision==receipt.sourceRevision &&
                        now.historyManifestRevision==receipt.historyManifestRevision &&
                        now.permissionRevision==receipt.permissionRevision &&
                        now.firstWeek==receipt.firstWeek)
            }
        }.getOrElse{NativeFutureApprovalRead.Rejected(
            NativeFutureApprovalRefusal.STORAGE_UNAVAILABLE)}
    }
    /** Revisioned tombstone prevents stale user callbacks restoring consent. */
    fun revoke(
        ownerKey:String,cycleId:String,blockId:String,expectedRevision:Long
    ):NativeFutureApprovalWrite {
        if(!validScope(ownerKey,cycleId,blockId))
            return NativeFutureApprovalWrite.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signed=owner(ownerKey)?:return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                val previous=stored(ownerKey,cycleId,blockId)
                if(previous==null || previous.athlete!=signed.athleteId ||
                    previous.revision!=expectedRevision ||
                    previous.approval==null || expectedRevision !in 1 until maximum)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.REVISION_CONFLICT)
                if(owner(ownerKey)!=signed)
                    return@withOwnerTransaction NativeFutureApprovalWrite.Rejected(
                        NativeFutureApprovalRefusal.NOT_AUTHORIZED)
                atomicWrite(path(ownerKey,cycleId,blockId),encode(
                    NativeFutureApprovalDiskRecord(ownerKey,signed.athleteId,
                        cycleId,blockId,expectedRevision+1,null)))
                NativeFutureApprovalWrite.Revoked(expectedRevision+1)
            }
        }.getOrElse{NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.STORAGE_UNAVAILABLE)}
    }
}
