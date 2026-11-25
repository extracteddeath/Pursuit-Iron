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

enum class NativeAuthoringSourceRefusal {
    NOT_AUTHORIZED, INVALID_SOURCE, STALE_REVISION, CORRUPT_RECORD,
    STORAGE_UNAVAILABLE, CONFIRMATION_REQUIRED
}
sealed interface NativeAuthoringSourceRead {
    data class Found(val source: NativeCustomAuthoringSnapshot) :
        NativeAuthoringSourceRead
    data object Missing : NativeAuthoringSourceRead
    data class Revoked(val revision: Long) : NativeAuthoringSourceRead
    data class Rejected(val reason: NativeAuthoringSourceRefusal) :
        NativeAuthoringSourceRead
}
sealed interface NativeAuthoringSourceWrite {
    data class Committed(val revision: Long) : NativeAuthoringSourceWrite
    data class Rejected(val reason: NativeAuthoringSourceRefusal) :
        NativeAuthoringSourceWrite
}

sealed interface NativeSavedGymRead {
    data class Ready(val sourceRevision:Long,val active:ReviewedGymProfile,
        val inactive:List<ReviewedGymProfile>):NativeSavedGymRead {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:NativeAuthoringSourceRefusal):NativeSavedGymRead
}

private data class AuthoringFileRecord(
    val ownerKey: String, val athleteId: String,
    val revision: Long, val snapshot: NativeCustomAuthoringSnapshot?,
    val inactiveGyms:List<ReviewedGymProfile> = emptyList()
)

/**
 * Candidate private native owner of the confirmed gym + reviewed catalog
 * used to author custom-cycle drafts. Never issues a workout or grants
 * approval, even if the athlete setup review is complete.
 *
 * Uses the SAME owner OS revision fence as the approved-program authority.
 * Revocation persists a CAS tombstone. No guessed stock, no JS fallback.
 * A production app must keep ALL athlete/gym/catalog mutations behind this
 * fence; an unrelated source writer invalidates the revision guarantee.
 */
class NativeKotlinCustomAuthoringSourceFileStore(
    private val privateDirectory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494347 // PICG
    private val version=1
    private val maxInactiveGyms=15
    private val maxFileBytes=NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES+4096

    private fun active(ownerKey: String): NativeKotlinOwnerIdentity? {
        if (!id.matches(ownerKey)) return null
        val identityNow=runCatching { identity.signedInIdentity() }.getOrNull()
            ?: return null
        return identityNow.takeIf {
            it.ownerKey==ownerKey && id.matches(it.athleteId)
        }
    }
    private fun path(ownerKey:String):Path {
        val hashed=MessageDigest.getInstance("SHA-256")
            .digest(ownerKey.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return privateDirectory.resolve("authoring-source-$hashed.bin")
    }

    private fun encode(record: AuthoringFileRecord):ByteArray {
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use { o ->
            o.writeInt(magic);o.writeInt(if(record.inactiveGyms.isEmpty())version else 2)
            o.writeUTF(record.ownerKey);o.writeUTF(record.athleteId)
            o.writeLong(record.revision)
            o.writeBoolean(record.snapshot != null)
            record.snapshot?.let {
                val bytes=NativeKotlinCustomAuthoringSourceCodec.encode(it)
                // Independently re-review persisted athlete/gym input.
                // Caller-supplied "READY" cannot forge accepted stock.
                require(NativeKotlinCustomAuthoringSourceCodec.decode(bytes)==it)
                o.writeInt(bytes.size);o.write(bytes)
            }
            if(record.inactiveGyms.isNotEmpty()) {
                val snapshot=checkNotNull(record.snapshot)
                val active=checkNotNull(snapshot.setup.reviewed).gym
                require(record.inactiveGyms.size<=maxInactiveGyms &&
                    record.inactiveGyms.map{it.gymId}.distinct().size==record.inactiveGyms.size &&
                    record.inactiveGyms.none{it.gymId==active.gymId})
                o.writeInt(record.inactiveGyms.size)
                record.inactiveGyms.sortedBy{it.gymId}.forEach{gym->
                    require(NativeKotlinGymCatalogEditingDomain.reviewedGym(snapshot,
                        NativeKotlinGymCatalogEditingDomain.asGymDraft(gym))==gym)
                    NativeKotlinCustomAuthoringSourceCodec.writeSavedGym(o,gym)
                }
            }
        }
        val payload=output.toByteArray()
        require(payload.size+36<=maxFileBytes)
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { o ->
                o.writeInt(payload.size);o.write(payload);o.write(checksum)
            }
        }.toByteArray()
    }

    private fun decode(file:Path):AuthoringFileRecord? {
        if (!Files.exists(file)) return null
        if (Files.isSymbolicLink(file)) throw IOException("Linked authoring source")
        if (Files.size(file) !in 44L..maxFileBytes.toLong())
            throw IOException("Invalid authoring source size")
        val envelope=Files.readAllBytes(file)
        val header=DataInputStream(ByteArrayInputStream(envelope))
        val length=header.readInt()
        if (length !in 8..maxFileBytes-36 || length+36!=envelope.size)
            throw IOException("Invalid authoring source envelope")
        val payload=ByteArray(length);header.readFully(payload)
        val checksum=ByteArray(32);header.readFully(checksum)
        if (!MessageDigest.isEqual(checksum,
                MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("Authoring source checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                val magicRead=data.readInt();val versionRead=data.readInt()
                if (magicRead!=magic || versionRead !in 1..2)
                    throw IOException("Unknown authoring file version")
                val owner=data.readUTF();val athlete=data.readUTF()
                val revision=data.readLong()
                if (!id.matches(owner) || !id.matches(athlete) ||
                    revision !in 1..9_007_199_254_740_991L)
                    throw IOException("Invalid authoring source identity")
                val snapshot=if (data.readBoolean()) {
                    val size=data.readInt()
                    if (size !in 16..NativeKotlinCustomAuthoringSourceCodec.MAX_BYTES ||
                        size>data.available())
                        throw IOException("Invalid authoring snapshot size")
                    val content=ByteArray(size)
                    data.readFully(content)
                    NativeKotlinCustomAuthoringSourceCodec.decode(content)
                } else null
                val inactive=if(versionRead==2) {
                    val current=checkNotNull(snapshot)
                    val active=checkNotNull(current.setup.reviewed).gym
                    val count=data.readInt()
                    require(count in 1..maxInactiveGyms)
                    val gyms=List(count) {
                        val gym=NativeKotlinCustomAuthoringSourceCodec.readSavedGym(data)
                        checkNotNull(NativeKotlinGymCatalogEditingDomain.reviewedGym(current,gym))
                    }
                    require(gyms.map{it.gymId}.distinct().size==gyms.size &&
                        gyms.none{it.gymId==active.gymId})
                    java.util.Collections.unmodifiableList(gyms)
                } else emptyList()
                if (data.available()!=0 ||
                    (snapshot!=null && (snapshot.ownerKey!=owner ||
                        snapshot.athleteId!=athlete ||
                        snapshot.revision!=revision)))
                    throw IOException("Authoring identity or revision mismatch")
                AuthoringFileRecord(owner,athlete,revision,snapshot,inactive)
            }
        } catch(ex:Exception) {
            throw IOException("Unreadable authoring source",ex)
        }
    }

    private fun writeAtomically(file:Path, bytes:ByteArray) {
        Files.createDirectories(privateDirectory)
        if(Files.isSymbolicLink(privateDirectory))
            throw IOException("Unsafe authoring directory")
        val pending=Files.createTempFile(privateDirectory,
            "authoring-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch(ex:AtomicMoveNotSupportedException) {
                throw IOException("Atomic authoring source write unavailable",ex)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    /** An absent or revoked source never yields fabricated gym options. */
    fun read(ownerKey:String):NativeAuthoringSourceRead {
        if(active(ownerKey)==null)return NativeAuthoringSourceRead.Rejected(
            NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val owner=active(ownerKey)
                    ?: return@withOwnerTransaction NativeAuthoringSourceRead.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                val record=try {decode(path(ownerKey))} catch(_:Exception) {
                    return@withOwnerTransaction NativeAuthoringSourceRead.Rejected(
                        NativeAuthoringSourceRefusal.CORRUPT_RECORD)
                }
                if(active(ownerKey)!=owner)
                    NativeAuthoringSourceRead.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                else if(record==null)
                    NativeAuthoringSourceRead.Missing
                else if(record.ownerKey!=ownerKey ||
                    record.athleteId!=owner.athleteId)
                    NativeAuthoringSourceRead.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                else if(record.snapshot==null)
                    NativeAuthoringSourceRead.Revoked(record.revision)
                else NativeAuthoringSourceRead.Found(record.snapshot)
            }
        } catch(_:Exception) {
            NativeAuthoringSourceRead.Rejected(
                NativeAuthoringSourceRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun readSavedGyms(ownerKey:String):NativeSavedGymRead {
        fun deny(reason:NativeAuthoringSourceRefusal)=NativeSavedGymRead.Rejected(reason)
        val signed=active(ownerKey)?:return deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            if(active(ownerKey)!=signed)return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            val record=try{decode(path(ownerKey))}catch(_:Exception){
                return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            if(record.ownerKey!=ownerKey || record.athleteId!=signed.athleteId ||
                active(ownerKey)!=signed)
                return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            val gym=record.snapshot?.setup?.reviewed?.gym
                ?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            NativeSavedGymRead.Ready(record.revision,gym,record.inactiveGyms)
        }}.getOrElse{deny(NativeAuthoringSourceRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Explicit creation+selection, or selection of an already confirmed gym.
     * Both operations reopen the current athlete/catalog and preserve all
     * unrelated facts. No unit conversion, stock copying or workout mutation. */
    fun addAndSelectGym(ownerKey:String,expectedRevision:Long,gym:GymProfileDraft,
        athleteConfirmed:Boolean):NativeAuthoringSourceWrite =
        if(!athleteConfirmed)NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.CONFIRMATION_REQUIRED)
        else gym.gymId?.let{changeGym(ownerKey,expectedRevision,it,gym,true)}
            ?:NativeAuthoringSourceWrite.Rejected(NativeAuthoringSourceRefusal.INVALID_SOURCE)

    fun selectSavedGym(ownerKey:String,expectedRevision:Long,gymId:String,
        athleteConfirmed:Boolean):NativeAuthoringSourceWrite =
        changeGym(ownerKey,expectedRevision,gymId,null,athleteConfirmed)

    private fun changeGym(ownerKey:String,expectedRevision:Long,gymId:String,
        newGym:GymProfileDraft?,athleteConfirmed:Boolean):NativeAuthoringSourceWrite {
        fun deny(reason:NativeAuthoringSourceRefusal)=NativeAuthoringSourceWrite.Rejected(reason)
        if(!athleteConfirmed)return deny(NativeAuthoringSourceRefusal.CONFIRMATION_REQUIRED)
        if(!id.matches(gymId))return deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
        val signed=active(ownerKey)?:return deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            if(active(ownerKey)!=signed)return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            val record=try{decode(path(ownerKey))}catch(_:Exception){
                return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            if(record.ownerKey!=ownerKey || record.athleteId!=signed.athleteId)
                return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            if(record.revision!=expectedRevision)return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.STALE_REVISION)
            val current=record.snapshot
                ?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            val activeGym=checkNotNull(current.setup.reviewed).gym
            val existing=record.inactiveGyms.singleOrNull{it.gymId==gymId}
            if(activeGym.gymId==gymId || (newGym!=null && existing!=null))
                return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            val target=newGym ?:existing?.let(NativeKotlinGymCatalogEditingDomain::asGymDraft)
                ?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            val proposal=NativeKotlinGymCatalogEditingDomain.switchGym(current,expectedRevision,target)
                as? NativeGymCatalogEdit.Proposed
                ?:return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.INVALID_SOURCE)
            if(active(ownerKey)!=signed)return@withOwnerTransaction deny(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            publish(ownerKey,expectedRevision,proposal.source)
        }}.getOrElse{deny(NativeAuthoringSourceRefusal.STORAGE_UNAVAILABLE)}
    }

    fun publish(
        ownerKey:String, expectedRevision:Long,
        snapshot:NativeCustomAuthoringSnapshot
    ):NativeAuthoringSourceWrite =
        commit(ownerKey,expectedRevision,snapshot,deleting=false)

    fun revoke(ownerKey:String, expectedRevision:Long):NativeAuthoringSourceWrite =
        commit(ownerKey,expectedRevision,null,deleting=true)

    private fun commit(
        ownerKey:String, expectedRevision:Long,
        snapshot:NativeCustomAuthoringSnapshot?, deleting:Boolean
    ):NativeAuthoringSourceWrite {
        if(expectedRevision !in 0..9_007_199_254_740_990L ||
            (!deleting && (snapshot==null ||
                snapshot.ownerKey!=ownerKey ||
                snapshot.revision!=expectedRevision+1)))
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.INVALID_SOURCE)
        if(active(ownerKey)==null)return NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val owner=active(ownerKey)
                    ?:return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                if(snapshot!=null && snapshot.athleteId!=owner.athleteId)
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey)
                val previous=try {decode(file)} catch(_:Exception) {
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.CORRUPT_RECORD)
                }
                if(previous!=null && (previous.ownerKey!=ownerKey ||
                    previous.athleteId!=owner.athleteId))
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                if((previous?.revision?:0L)!=expectedRevision ||
                    (deleting && previous==null))
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.STALE_REVISION)
                // Preserve the prior physical gym whenever an explicit
                // reviewed source publication changes the active identity.
                // The single atomic source envelope owns both active and
                // inactive stock; a stale edit can never resurrect either.
                val inactive=if(snapshot==null)emptyList() else {
                    val newGym=snapshot.setup.reviewed?.gym
                        ?:return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                            NativeAuthoringSourceRefusal.INVALID_SOURCE)
                    val priorGym=previous?.snapshot?.setup?.reviewed?.gym
                    val retained=previous?.inactiveGyms.orEmpty().filter{it.gymId!=newGym.gymId}
                    (retained+listOfNotNull(priorGym?.takeIf{it.gymId!=newGym.gymId}))
                        .sortedBy{it.gymId}
                }
                val next=AuthoringFileRecord(
                    ownerKey,owner.athleteId,expectedRevision+1,snapshot,inactive)
                val bytes=try {encode(next)} catch(_:Exception) {
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.INVALID_SOURCE)
                }
                if(active(ownerKey)!=owner)
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                writeAtomically(file,bytes)
                NativeAuthoringSourceWrite.Committed(next.revision)
            }
        } catch(_:Exception) {
            NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
