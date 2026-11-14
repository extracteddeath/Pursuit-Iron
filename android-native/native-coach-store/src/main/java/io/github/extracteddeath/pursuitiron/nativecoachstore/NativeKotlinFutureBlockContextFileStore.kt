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
 * Crash-safe COMPANION record for an athlete-accepted future-block draft.
 *
 * The future profile and authored coaching policy survive cold restoration,
 * but this record is never a source activation, coaching signoff, grant or
 * workout issuer. When staged before the draft CAS, an interrupted operation
 * leaves an inert pending record. read() returns Found only if the currently
 * saved draft and current gym/source match exact stored SHA-256 fingerprints.
 *
 * No reader may treat Found as permission to issue; the separate native
 * source/signoff/preflight approval remains mandatory.
 */
data class NativeFutureBlockContextReceipt(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val draftRevision:Long,
    val sourceRevision:Long,
    val reviewedFingerprint:String,
    val future:ProspectiveBlockCoachingContext
) {
    val mayIssueWorkout:Boolean get()=false
    val mayActivateProfile:Boolean get()=false
}
enum class NativeFutureBlockContextRefusal {
    NOT_AUTHORIZED, INVALID_CONTEXT, CORRUPT_RECORD,
    STALE_DRAFT_OR_SOURCE, STORAGE_UNAVAILABLE
}
sealed interface NativeFutureBlockContextRead {
    data class Found(val receipt:NativeFutureBlockContextReceipt):
        NativeFutureBlockContextRead
    data object Missing:NativeFutureBlockContextRead
    data class Rejected(val reason:NativeFutureBlockContextRefusal):
        NativeFutureBlockContextRead
}
internal data class NativeFutureBlockRecord(
    val receipt:NativeFutureBlockContextReceipt,
    val draftSha:String,
    val sourceSha:String
)

internal object NativeFutureBlockContextCodec {
    private const val MAGIC=0x50494642 // PIFB
    private const val VERSION=1
    const val MAX_BYTES=600_000
    private val sha=Regex("[a-f0-9]{64}")
    private fun DataOutputStream.decimal(value:BigDecimal)=
        writeUTF(value.stripTrailingZeros().toPlainString())
    private fun DataInputStream.decimal():BigDecimal=BigDecimal(readUTF())
    private fun DataOutputStream.policy(p:AuthoredGoalProgramPolicy) {
        writeUTF(p.policyId);writeUTF(p.revision);writeUTF(p.rationale)
        writeUTF(p.athleteId);writeUTF(p.athleteRevision)
        writeUTF(p.goal.name);writeUTF(p.experience.name)
        writeUTF(p.split.name);writeUTF(p.phase.name);writeInt(p.trainingDays)
        fun bounds(list:List<DeclaredMuscleDoseBounds>) {
            require(TrainingMuscleCoverage.isComplete(list.map{it.muscle}))
            writeInt(list.size)
            // Preserve authored dose-bound ordering on cold restoration.
            list.forEach {
                writeUTF(it.muscle.name);decimal(it.mev)
                decimal(it.mav);decimal(it.mrv)
                writeInt(it.minimumDirectDays)
            }
        }
        bounds(p.baselineBounds);bounds(p.selectedBounds)
        writeInt(p.minimumPurposeDays.size)
        p.minimumPurposeDays.toSortedMap(compareBy{it.name}).forEach {(k,v)->
            writeUTF(k.name);writeInt(v)
        }
        writeInt(p.primaryLiftMinimumDays.size)
        p.primaryLiftMinimumDays.toSortedMap().forEach {(k,v)->
            writeUTF(k);writeInt(v)
        }
    }
    // Decode enum names exactly once; reject unknown schema values.
    private inline fun <reified T:Enum<T>> DataInputStream.safeEnum():T {
        val value=readUTF()
        return enumValues<T>().firstOrNull{it.name==value}
            ?:throw IOException("Unknown enum: $value")
    }
    private fun DataInputStream.policy():AuthoredGoalProgramPolicy {
        val policyId=readUTF();val revision=readUTF();val reason=readUTF()
        val athlete=readUTF();val athleteRevision=readUTF()
        val goal=safeEnum<AthleteTrainingGoal>()
        val experience=safeEnum<AthleteExperience>()
        val split=safeEnum<ComposerSplit>()
        val phase=safeEnum<TrainingPhase>();val days=readInt()
        fun bounds():List<DeclaredMuscleDoseBounds> {
            val count=readInt()
            if(count !in 1..TrainingMuscle.entries.size)
                throw IOException("Wrong muscle coverage")
            return (0 until count).map {
                DeclaredMuscleDoseBounds(safeEnum<TrainingMuscle>(),
                    decimal(),decimal(),decimal(),readInt())
            }.also{
                if(!TrainingMuscleCoverage.isComplete(it.map{v->v.muscle}))
                    throw IOException("Incomplete or duplicate muscle coverage")
            }
        }
        val baseline=bounds();val selected=bounds()
        val purposeCount=readInt()
        if(purposeCount !in 0..ComposerSessionPurpose.entries.size)
            throw IOException("Invalid purpose count")
        val purposes=buildMap {
            repeat(purposeCount) {
                val purpose=safeEnum<ComposerSessionPurpose>()
                val number=readInt()
                if(put(purpose,number)!=null)
                    throw IOException("Duplicate session purpose")
            }
        }
        val liftCount=readInt()
        if(liftCount !in 0..256)throw IOException("Too many skills")
        val lifts=buildMap {
            repeat(liftCount) {
                val id=readUTF();val number=readInt()
                if(put(id,number)!=null)throw IOException("Duplicate skill")
            }
        }
        return AuthoredGoalProgramPolicy(policyId,revision,reason,athlete,
            athleteRevision,goal,experience,split,phase,days,baseline,
            selected,purposes,lifts)
    }
    /**
     * Exact content binding for the later SOURCE activation consent.
     * A saved future receipt can keep the same author-facing review label
     * while a phase policy, muscle target or day overhead changes. Those
     * bytes must still stale prior activation approvals.
     */
    internal fun coachingContentFingerprint(
        context:ProspectiveBlockCoachingContext
    ):String {
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use{out->
            out.writeInt(1)
            out.policy(context.policy)
            out.writeInt(context.nonSetSecondsByWeekday.size)
            context.nonSetSecondsByWeekday.toSortedMap().forEach{(day,seconds)->
                out.writeInt(day);out.writeInt(seconds)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray()).joinToString("") {
                "%02x".format(it.toInt() and 255)
            }
    }

    fun encode(record:NativeFutureBlockRecord,baseSource:NativeCustomAuthoringSnapshot):ByteArray {
        val r=record.receipt
        require(sha.matches(record.draftSha)&&sha.matches(record.sourceSha) &&
            sha.matches(r.reviewedFingerprint))
        val futureSource=NativeKotlinCustomAuthoringSourceCodec.encode(
            baseSource.copy(setup=r.future.setup))
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use {out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            out.writeUTF(r.ownerKey);out.writeUTF(r.athleteId)
            out.writeUTF(r.cycleId);out.writeUTF(r.blockId)
            out.writeLong(r.draftRevision);out.writeLong(r.sourceRevision)
            out.writeUTF(r.reviewedFingerprint)
            out.writeUTF(record.draftSha);out.writeUTF(record.sourceSha)
            out.writeInt(futureSource.size);out.write(futureSource)
            out.policy(r.future.policy)
            out.writeInt(r.future.nonSetSecondsByWeekday.size)
            r.future.nonSetSecondsByWeekday.toSortedMap().forEach{(day,time)->
                out.writeInt(day);out.writeInt(time)
            }
        }
        val payload=output.toByteArray()
        require(payload.size+36<=MAX_BYTES)
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also {bytes ->
            DataOutputStream(bytes).use {out->
                out.writeInt(payload.size);out.write(payload);out.write(checksum)
            }
        }.toByteArray()
    }
    fun decode(bytes:ByteArray):Pair<NativeFutureBlockRecord,NativeCustomAuthoringSnapshot> {
        if(bytes.size !in 44..MAX_BYTES)throw IOException("Invalid future bundle size")
        val header=DataInputStream(ByteArrayInputStream(bytes))
        val size=header.readInt()
        if(size !in 8..MAX_BYTES-36 || size+36!=bytes.size)
            throw IOException("Invalid future bundle envelope")
        val payload=ByteArray(size);header.readFully(payload)
        val digest=ByteArray(32);header.readFully(digest)
        if(!MessageDigest.isEqual(digest,
            MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("Corrupt future bundle")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use {input->
                if(input.readInt()!=MAGIC || input.readInt()!=VERSION)
                    throw IOException("Unknown future bundle version")
                val owner=input.readUTF();val athlete=input.readUTF()
                val cycle=input.readUTF();val block=input.readUTF()
                val cycleRev=input.readLong();val sourceRev=input.readLong()
                val approved=input.readUTF();val draftSha=input.readUTF()
                val sourceSha=input.readUTF()
                if(listOf(approved,draftSha,sourceSha).any{!sha.matches(it)} ||
                    cycleRev !in 1..9_007_199_254_740_991L ||
                    sourceRev !in 1..9_007_199_254_740_991L)
                    throw IOException("Invalid bundle provenance")
                val sourceLength=input.readInt()
                if(sourceLength !in 16..NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES ||
                    sourceLength>input.available())
                    throw IOException("Invalid future source size")
                val raw=ByteArray(sourceLength);input.readFully(raw)
                val futureSource=NativeKotlinCustomAuthoringSourceCodec.decode(raw)
                val policy=input.policy()
                val overheadCount=input.readInt()
                if(overheadCount !in 0..7)throw IOException("Invalid future days")
                val overhead=buildMap {
                    repeat(overheadCount) {
                        val day=input.readInt();val seconds=input.readInt()
                        if(day !in 0..6 || seconds !in 0..21600 ||
                            put(day,seconds)!=null)
                            throw IOException("Invalid future overhead")
                    }
                }
                if(input.available()!=0 || futureSource.ownerKey!=owner ||
                    futureSource.athleteId!=athlete ||
                    futureSource.revision!=sourceRev)
                    throw IOException("Future source scope mismatch")
                NativeFutureBlockRecord(
                    NativeFutureBlockContextReceipt(owner,athlete,cycle,block,
                        cycleRev,sourceRev,approved,
                        ProspectiveBlockCoachingContext(
                            futureSource.setup,policy,overhead)),
                    draftSha,sourceSha) to futureSource
            }
        }catch(ex:Exception){throw IOException("Invalid prospective record",ex)}
    }
}

class NativeKotlinFutureBlockContextFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun owner(key:String)=runCatching{identity.signedInIdentity()}.getOrNull()
        ?.takeIf{id.matches(key)&&it.ownerKey==key&&id.matches(it.athleteId)}
    private fun path(owner:String,cycle:String,block:String):Path {
        val key=MessageDigest.getInstance("SHA-256").digest(
            (owner+"\u0000"+cycle+"\u0000"+block).toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return directory.resolve("future-block-$key.bin")
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun atomicWrite(path:Path,bytes:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))throw IOException("Unsafe future directory")
        val pending=Files.createTempFile(directory,"future-block-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use {channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending,path,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(_:AtomicMoveNotSupportedException){
                throw IOException("Atomic future bundle write not available")
            }
        }finally{Files.deleteIfExists(pending)}
    }
    /**
     * Called ONLY after a fully re-audited on-screen proposal inside the
     * owner's fence, before draft CAS. If the process crashes here, read()
     * will refuse to expose this unpaired record as accepted.
     */
    internal fun stage(
        ownerKey:String,cycleId:String,blockId:String,
        proposed:CustomTrainingCycle,
        current:NativeCustomAuthoringSnapshot,
        reviewedFingerprint:String,
        future:ProspectiveBlockCoachingContext
    ):Boolean {
        if(owner(ownerKey)?.athleteId!=proposed.athleteId ||
            !listOf(cycleId,blockId).all(id::matches) ||
            proposed.cycleId!=cycleId ||
            proposed.blocks.none{it.blockId==blockId} ||
            current.ownerKey!=ownerKey || current.athleteId!=proposed.athleteId ||
            proposed.revision<=1)return false
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                if(owner(ownerKey)?.athleteId!=current.athleteId)return@withOwnerTransaction false
                val base=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)
                    ?.source ?:return@withOwnerTransaction false
                val saved=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction false
                if(base!=current || saved.revision!=proposed.revision-1)
                    return@withOwnerTransaction false
                val record=NativeFutureBlockRecord(
                    NativeFutureBlockContextReceipt(ownerKey,proposed.athleteId,
                        cycleId,blockId,proposed.revision,current.revision,
                        reviewedFingerprint,future),
                    sha(NativeCustomCycleDraftCodec.encode(proposed)),
                    sha(NativeKotlinCustomAuthoringSourceCodec.encode(current)))
                atomicWrite(path(ownerKey,cycleId,blockId),
                    NativeFutureBlockContextCodec.encode(record,current))
                true
            }
        }.getOrElse{false}
    }
    fun read(ownerKey:String,cycleId:String,blockId:String):NativeFutureBlockContextRead {
        if(owner(ownerKey)==null || !listOf(cycleId,blockId).all(id::matches))
            return NativeFutureBlockContextRead.Rejected(
                NativeFutureBlockContextRefusal.NOT_AUTHORIZED)
        return runCatching {
            fence.withOwnerTransaction(ownerKey){
                val signed=owner(ownerKey) ?:return@withOwnerTransaction NativeFutureBlockContextRead.Rejected(
                    NativeFutureBlockContextRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey,cycleId,blockId)
                if(!Files.exists(file))
                    return@withOwnerTransaction NativeFutureBlockContextRead.Missing
                if(Files.isSymbolicLink(file) ||
                    Files.size(file)>NativeFutureBlockContextCodec.MAX_BYTES)
                    return@withOwnerTransaction NativeFutureBlockContextRead.Rejected(
                        NativeFutureBlockContextRefusal.CORRUPT_RECORD)
                val (record,restored)=try{
                    NativeFutureBlockContextCodec.decode(Files.readAllBytes(file))
                }catch(_:Exception){
                    return@withOwnerTransaction NativeFutureBlockContextRead.Rejected(
                        NativeFutureBlockContextRefusal.CORRUPT_RECORD)
                }
                val receipt=record.receipt
                if(receipt.ownerKey!=ownerKey || receipt.athleteId!=signed.athleteId ||
                    receipt.cycleId!=cycleId || receipt.blockId!=blockId)
                    return@withOwnerTransaction NativeFutureBlockContextRead.Rejected(
                        NativeFutureBlockContextRefusal.NOT_AUTHORIZED)
                val draft=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                val source=(sources.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                if(draft==null || source==null ||
                    receipt.draftRevision!=draft.revision ||
                    receipt.sourceRevision!=source.revision ||
                    sha(NativeCustomCycleDraftCodec.encode(draft))!=record.draftSha ||
                    sha(NativeKotlinCustomAuthoringSourceCodec.encode(source))!=record.sourceSha ||
                    draft.blocks.none{it.blockId==blockId} ||
                    restored.copy(setup=source.setup)!=source ||
                    owner(ownerKey)!=signed)
                    NativeFutureBlockContextRead.Rejected(
                        NativeFutureBlockContextRefusal.STALE_DRAFT_OR_SOURCE)
                else NativeFutureBlockContextRead.Found(receipt)
            }
        }.getOrElse{NativeFutureBlockContextRead.Rejected(
            NativeFutureBlockContextRefusal.STORAGE_UNAVAILABLE)}
    }
}
