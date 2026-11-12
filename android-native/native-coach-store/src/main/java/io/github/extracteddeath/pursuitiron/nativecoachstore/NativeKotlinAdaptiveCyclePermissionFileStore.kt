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
 * An explicitly athlete-approved set of adaptation rights for ONE cycle.
 * A screen-supplied CustomCycleCoachPermissions is NEVER durable authority.
 * Rights grant permission to review proposed changes, NOT a license to
 * automatically edit an approved program, issue a workout or alter history.
 */
data class NativeAdaptiveCyclePermissionReceipt(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val revision:Long,
    val allowedKinds:Set<CustomCycleChangeKind>
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}
enum class NativeAdaptivePermissionRefusal {
    NOT_AUTHORIZED, NOT_CONFIRMED, INVALID_GRANT, STALE_REVISION,
    CORRUPT_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeAdaptivePermissionRead {
    data class Found(val receipt:NativeAdaptiveCyclePermissionReceipt):
        NativeAdaptivePermissionRead
    data object Missing:NativeAdaptivePermissionRead
    data class Revoked(val revision:Long):NativeAdaptivePermissionRead
    data class Rejected(val reason:NativeAdaptivePermissionRefusal):
        NativeAdaptivePermissionRead
}
sealed interface NativeAdaptivePermissionWrite {
    data class Committed(val revision:Long):NativeAdaptivePermissionWrite
    data class Rejected(val reason:NativeAdaptivePermissionRefusal):
        NativeAdaptivePermissionWrite
}
private data class AdaptivePermissionRecord(
    val owner:String,val athlete:String,val cycle:String,val revision:Long,
    val kinds:Set<CustomCycleChangeKind>?
)

/** Every historical grant and revocation belongs to the same owner.
 * Revocation records must survive a cold restore; missing != revoked.
 * These records do NOT authorize any automatic program modification.
 */
internal data class NativeAdaptivePermissionInventoryFact(
    val cycleId:String,val revision:Long,
    val allowedKinds:Set<CustomCycleChangeKind>?,
    val revoked:Boolean
)
internal sealed interface NativeAdaptivePermissionInventory {
    data class Verified(val records:List<NativeAdaptivePermissionInventoryFact>):
        NativeAdaptivePermissionInventory
    data object InvalidOrForeign:NativeAdaptivePermissionInventory
}

/** Versioned checksum, cross-process owner fence and atomic revision CAS. */
class NativeKotlinAdaptiveCyclePermissionFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494350 // PICP
    private val version=1
    private val maxBytes=4096
    private val maxRevision=9_007_199_254_740_991L
    private fun owner(key:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(key))return null
        return runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{it.ownerKey==key && id.matches(it.athleteId)}
    }
    private fun path(owner:String,cycle:String):Path {
        val key=MessageDigest.getInstance("SHA-256")
            .digest((owner+"\u0000"+cycle).toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return directory.resolve("adapt-permission-$key.bin")
    }
    private fun encode(record:AdaptivePermissionRecord):ByteArray {
        val stream=ByteArrayOutputStream()
        DataOutputStream(stream).use {out ->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(record.owner);out.writeUTF(record.athlete)
            out.writeUTF(record.cycle);out.writeLong(record.revision)
            out.writeBoolean(record.kinds!=null)
            record.kinds?.let{kinds ->
                out.writeInt(kinds.size)
                kinds.sortedBy{it.name}.forEach{out.writeUTF(it.name)}
            }
        }
        val payload=stream.toByteArray()
        require(payload.size+36<=maxBytes)
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also {bytes ->
            DataOutputStream(bytes).use{out->
                out.writeInt(payload.size);out.write(payload);out.write(checksum)
            }
        }.toByteArray()
    }
    private fun decode(file:Path):AdaptivePermissionRecord? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) ||
            Files.size(file) !in 44L..maxBytes.toLong())
            throw IOException("Invalid permission record file")
        val bytes=Files.readAllBytes(file)
        val head=DataInputStream(ByteArrayInputStream(bytes))
        val length=head.readInt()
        if(length !in 8..maxBytes-36 || length+36!=bytes.size)
            throw IOException("Invalid permission envelope")
        val payload=ByteArray(length);head.readFully(payload)
        val expected=ByteArray(32);head.readFully(expected)
        if(!MessageDigest.isEqual(expected,
            MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("Permission checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use{input->
                if(input.readInt()!=magic || input.readInt()!=version)
                    throw IOException("Unrecognized permission version")
                val owner=input.readUTF();val athlete=input.readUTF()
                val cycle=input.readUTF();val revision=input.readLong()
                if(!id.matches(owner)||!id.matches(athlete)||!id.matches(cycle)||
                    revision !in 1..maxRevision)
                    throw IOException("Invalid permission scope")
                val kinds=if(input.readBoolean()) {
                    val count=input.readInt()
                    if(count !in 0..CustomCycleChangeKind.entries.size)
                        throw IOException("Invalid permission count")
                    val names=(0 until count).map{input.readUTF()}
                    if(names.distinct().size!=count ||
                        names.any{name->CustomCycleChangeKind.entries.none{
                            it.name==name}})
                        throw IOException("Unknown or duplicate permissions")
                    names.map{name->CustomCycleChangeKind.valueOf(name)}.toSet()
                } else null
                if(input.available()!=0)throw IOException("Trailing permission data")
                AdaptivePermissionRecord(owner,athlete,cycle,revision,kinds)
            }
        }catch(ex:Exception){
            throw IOException("Unreadable permission record",ex)
        }
    }
    private fun atomicWrite(file:Path,content:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))
            throw IOException("Unsafe permission directory")
        val pending=Files.createTempFile(directory,"adapt-permission-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val data=ByteBuffer.wrap(content)
                while(data.hasRemaining())channel.write(data)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(_:AtomicMoveNotSupportedException){
                throw IOException("Atomic permission replacement unavailable")
            }
        }finally{Files.deleteIfExists(pending)}
    }
    fun read(ownerKey:String,cycleId:String):NativeAdaptivePermissionRead {
        if(owner(ownerKey)==null || !id.matches(cycleId))
            return NativeAdaptivePermissionRead.Rejected(
                NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeAdaptivePermissionRead.Rejected(
                        NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                val previous=try{decode(path(ownerKey,cycleId))}catch(_:Exception){
                    return@withOwnerTransaction NativeAdaptivePermissionRead.Rejected(
                        NativeAdaptivePermissionRefusal.CORRUPT_RECORD)
                }
                when {
                    owner(ownerKey)!=current ->
                        NativeAdaptivePermissionRead.Rejected(
                            NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                    previous==null->NativeAdaptivePermissionRead.Missing
                    previous.owner!=ownerKey ||
                        previous.athlete!=current.athleteId ||
                        previous.cycle!=cycleId ->
                        NativeAdaptivePermissionRead.Rejected(
                            NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                    previous.kinds==null->
                        NativeAdaptivePermissionRead.Revoked(previous.revision)
                    else->NativeAdaptivePermissionRead.Found(
                        NativeAdaptiveCyclePermissionReceipt(
                            ownerKey,current.athleteId,cycleId,previous.revision,
                            previous.kinds))
                }
            }
        }.getOrElse{NativeAdaptivePermissionRead.Rejected(
            NativeAdaptivePermissionRefusal.STORAGE_UNAVAILABLE)}
    }
    /**
     * Exhaustive owner-fenced physical inventory. Ordinary read(cycleId)
     * legitimately reports Missing for a different cycle; a clean restore
     * must reject a corrupt, foreign, renamed, or pending grant rather
     * than invisibly replacing a saved athlete decision with defaults.
     */
    internal fun inventoryOwner(ownerKey:String):
        NativeAdaptivePermissionInventory = runCatching {
        fence.withOwnerTransaction(ownerKey) {
            val signed=owner(ownerKey)
                ?:return@withOwnerTransaction NativeAdaptivePermissionInventory.InvalidOrForeign
            if(!Files.exists(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                return@withOwnerTransaction NativeAdaptivePermissionInventory.Verified(
                    emptyList())
            require(!Files.isSymbolicLink(directory) &&
                Files.isDirectory(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))
            val physical=Files.list(directory).use {stream->
                stream.filter {it.fileName.toString().startsWith(
                    "adapt-permission-")}.toList()
            }
            require(physical.size<=4096)
            val seen=mutableSetOf<String>()
            val name=Regex("^adapt-permission-[a-f0-9]{64}\\.bin$")
            val records=mutableListOf<NativeAdaptivePermissionInventoryFact>()
            for(file in physical.sortedBy{it.fileName.toString()}) {
                require(name.matches(file.fileName.toString()) &&
                    !Files.isSymbolicLink(file) &&
                    Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                    Files.size(file) in 44L..maxBytes.toLong())
                val stored=decode(file) ?:error("Missing physical grant")
                require(stored.owner==ownerKey &&
                    stored.athlete==signed.athleteId &&
                    stored.revision>0 &&
                    path(ownerKey,stored.cycle)==file &&
                    seen.add(stored.cycle))
                records.add(NativeAdaptivePermissionInventoryFact(
                    stored.cycle,stored.revision,stored.kinds,
                    revoked=stored.kinds==null))
            }
            NativeAdaptivePermissionInventory.Verified(records)
        }
    }.getOrElse {NativeAdaptivePermissionInventory.InvalidOrForeign}

    /** Replacing a grant (including removing rights) always needs consent. */
    fun confirm(
        ownerKey:String,cycleId:String,expectedRevision:Long,
        allowedKinds:Set<CustomCycleChangeKind>,athleteConfirmed:Boolean
    ):NativeAdaptivePermissionWrite =
        write(ownerKey,cycleId,expectedRevision,allowedKinds,
            athleteConfirmed,revoking=false)

    fun revoke(ownerKey:String,cycleId:String,expectedRevision:Long):
        NativeAdaptivePermissionWrite =
        write(ownerKey,cycleId,expectedRevision,null,
            athleteConfirmed=true,revoking=true)

    private fun write(
        ownerKey:String,cycleId:String,expectedRevision:Long,
        kinds:Set<CustomCycleChangeKind>?,athleteConfirmed:Boolean,
        revoking:Boolean
    ):NativeAdaptivePermissionWrite {
        fun reject(reason:NativeAdaptivePermissionRefusal)=
            NativeAdaptivePermissionWrite.Rejected(reason)
        if(!athleteConfirmed)return reject(
            NativeAdaptivePermissionRefusal.NOT_CONFIRMED)
        if(owner(ownerKey)==null)return reject(
            NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        if(!id.matches(cycleId) ||
            expectedRevision !in 0 until maxRevision ||
            (!revoking && kinds==null))
            return reject(NativeAdaptivePermissionRefusal.INVALID_GRANT)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction reject(
                        NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey,cycleId)
                val previous=try{decode(file)}catch(_:Exception){
                    return@withOwnerTransaction reject(
                        NativeAdaptivePermissionRefusal.CORRUPT_RECORD)
                }
                if(previous!=null &&
                    (previous.owner!=ownerKey ||
                    previous.athlete!=current.athleteId ||
                    previous.cycle!=cycleId))
                    return@withOwnerTransaction reject(
                        NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                if((previous?.revision?:0L)!=expectedRevision ||
                    (revoking && previous?.kinds==null))
                    return@withOwnerTransaction reject(
                        NativeAdaptivePermissionRefusal.STALE_REVISION)
                if(owner(ownerKey)!=current)
                    return@withOwnerTransaction reject(
                        NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
                val next=expectedRevision+1
                atomicWrite(file,encode(AdaptivePermissionRecord(
                    ownerKey,current.athleteId,cycleId,next,kinds)))
                NativeAdaptivePermissionWrite.Committed(next)
            }
        }.getOrElse{reject(NativeAdaptivePermissionRefusal.STORAGE_UNAVAILABLE)}
    }
}
