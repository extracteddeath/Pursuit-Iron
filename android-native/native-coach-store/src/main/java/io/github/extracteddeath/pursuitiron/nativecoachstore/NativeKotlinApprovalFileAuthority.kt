package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinTrainingSourceRevision
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Production host MUST implement this from a trusted native authenticated
 * session (never a parameter provided by the workout screen).
 * Changing accounts or signing out must use the SAME owner revision fence.
 */
fun interface NativeKotlinCurrentIdentity {
    fun signedInIdentity():NativeKotlinOwnerIdentity?
}
data class NativeKotlinOwnerIdentity(
    val ownerKey:String,val athleteId:String
)

enum class NativeApprovalWriteRefusal {
    NOT_AUTHORIZED, STALE_REVISION, MALFORMED_APPROVAL,
    CORRUPT_EXISTING_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeApprovalWriteResult {
    data class Committed(val revision:Long):NativeApprovalWriteResult
    data class Rejected(val reason:NativeApprovalWriteRefusal):NativeApprovalWriteResult
}

private data class ApprovalFileRecord(
    val ownerKey:String,
    val athleteId:String,
    val revision:Long,
    val approved:NativeKotlinWorkoutApproval?
)

/**
 * Offline, Kotlin-only, fail-closed owner of the complete *accepted* program,
 * day selection, gym stock, policy rules and receipt revision.
 *
 * Every publish/revoke is atomic CAS under the SAME per-athlete OS lock used
 * for workout issuance. Cold-open of an issued workout requires only current
 * athlete identity, not the current program (which can evolve).
 *
 * The file checksum is corruption detection, NOT cryptographic authentication.
 * This implementation remains an isolated integration candidate: the app
 * must supply trusted NativeKotlinCurrentIdentity, use the same fence for
 * account/grant/program/gym mutations, protect the private file root, and
 * validate device power-loss semantics before production integration.
 */
class NativeKotlinApprovalFileAuthority(
    private val privateDirectory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
):NativeKotlinWorkoutAuthority {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494146 // PIAF
    private val version=1
    private val maxFileBytes=NativeKotlinApprovalCodec.MAX_BYTES+4_096

    private fun active(ownerKey:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(ownerKey))return null
        val now=runCatching{identity.signedInIdentity()}.getOrNull()
            ?:return null
        return now.takeIf {it.ownerKey==ownerKey &&
            id.matches(it.athleteId)}
    }

    private fun path(ownerKey:String):Path {
        val digest=MessageDigest.getInstance("SHA-256")
            .digest(ownerKey.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return privateDirectory.resolve("approved-$digest.bin")
    }

    private fun read(file:Path):ApprovalFileRecord? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file))throw IOException("approval path is a link")
        val length=Files.size(file)
        if(length !in 44L..maxFileBytes.toLong())
            throw IOException("unexpected approval file size")
        val envelope=Files.readAllBytes(file)
        val stream=DataInputStream(ByteArrayInputStream(envelope))
        val payloadSize=stream.readInt()
        if(payloadSize<8 || payloadSize>maxFileBytes-36 ||
            payloadSize+36!=envelope.size)
            throw IOException("bad approval length")
        val payload=ByteArray(payloadSize)
        stream.readFully(payload)
        val checksum=ByteArray(32)
        stream.readFully(checksum)
        val actual=MessageDigest.getInstance("SHA-256").digest(payload)
        if(!MessageDigest.isEqual(checksum,actual))
            throw IOException("approval checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                if(data.readInt()!=magic || data.readInt()!=version)
                    throw IOException("unknown approval file version")
                val owner=data.readUTF()
                val athlete=data.readUTF()
                val revision=data.readLong()
                if(revision !in 1..9_007_199_254_740_991L ||
                    !id.matches(owner) || !id.matches(athlete))
                    throw IOException("invalid approval metadata")
                val present=data.readBoolean()
                val approval=if(present) {
                    val size=data.readInt()
                    if(size !in 8..NativeKotlinApprovalCodec.MAX_BYTES ||
                        size>data.available())
                        throw IOException("invalid accepted program payload")
                    val bytes=ByteArray(size)
                    data.readFully(bytes)
                    NativeKotlinApprovalCodec.decode(bytes)
                } else null
                if(data.available()!=0)throw IOException("approval file trailing bytes")
                if(approval!=null && (approval.ownerKey!=owner ||
                    approval.athleteId!=athlete ||
                    approval.storageRevision!=revision))
                    throw IOException("approval scope conflict")
                ApprovalFileRecord(owner,athlete,revision,approval)
            }
        } catch(ex:Exception) {
            throw IOException("unreadable accepted program",ex)
        }
    }

    private fun encode(record:ApprovalFileRecord):ByteArray {
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(record.ownerKey);out.writeUTF(record.athleteId)
            out.writeLong(record.revision)
            out.writeBoolean(record.approved!=null)
            record.approved?.let { approved ->
                val bytes=NativeKotlinApprovalCodec.encode(approved)
                out.writeInt(bytes.size);out.write(bytes)
            }
        }
        val bytes=payload.toByteArray()
        require(bytes.size+36<=maxFileBytes)
        val checksum=MessageDigest.getInstance("SHA-256").digest(bytes)
        return ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use {out->
                out.writeInt(bytes.size);out.write(bytes);out.write(checksum)
            }
        }.toByteArray()
    }

    private fun atomicWrite(file:Path,bytes:ByteArray) {
        Files.createDirectories(privateDirectory)
        val pending=Files.createTempFile(privateDirectory,
            "approval-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val data=ByteBuffer.wrap(bytes)
                while(data.hasRemaining())channel.write(data)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("approval filesystem lacks atomic rename",ex)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    /** Expected zero means first-ever approval; each update increments CAS. */
    fun publish(
        ownerKey:String,
        expectedRevision:Long,
        approval:NativeKotlinWorkoutApproval
    ):NativeApprovalWriteResult =
        commit(ownerKey,expectedRevision,approval)

    /** Tombstone the CURRENT grant; do NOT delete old issued workouts. */
    fun revokeCurrentGrant(
        ownerKey:String,expectedRevision:Long
    ):NativeApprovalWriteResult =
        commit(ownerKey,expectedRevision,null)

    private fun commit(
        ownerKey:String,expectedRevision:Long,
        approval:NativeKotlinWorkoutApproval?
    ):NativeApprovalWriteResult {
        if(expectedRevision<0 || expectedRevision>=9_007_199_254_740_991L)
            return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=active(ownerKey)
                    ?:return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                if(approval!=null && (approval.ownerKey!=ownerKey ||
                    approval.athleteId!=current.athleteId ||
                    approval.storageRevision!=expectedRevision+1))
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                val file=path(ownerKey)
                val previous=try{read(file)}catch(_:Exception){
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.CORRUPT_EXISTING_RECORD)
                }
                if(previous!=null && (previous.ownerKey!=ownerKey ||
                    previous.athleteId!=current.athleteId))
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                if((previous?.revision?:0L)!=expectedRevision)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.STALE_REVISION)
                val next=ApprovalFileRecord(ownerKey,current.athleteId,
                    expectedRevision+1,approval)
                val bytes=try{encode(next)}catch(_:Exception){
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                }
                // Recheck sign-in just before commit while holding same fence
                // as program changes and workout issuance.
                if(active(ownerKey)!=current)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                atomicWrite(file,bytes)
                NativeApprovalWriteResult.Committed(next.revision)
            }
        }.getOrElse {
            NativeApprovalWriteResult.Rejected(NativeApprovalWriteRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** No caller-supplied approvals; only private disk and current sign-in. */
    override fun currentApprovedWorkout(
        ownerKey:String,workoutId:String
    ):NativeKotlinWorkoutApproval? =
        runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val active=active(ownerKey) ?:return@withOwnerTransaction null
                val record=read(path(ownerKey))
                    ?:return@withOwnerTransaction null
                if(record.ownerKey!=ownerKey ||
                    record.athleteId!=active.athleteId)
                    return@withOwnerTransaction null
                val approval=record.approved
                    ?:return@withOwnerTransaction null
                approval.takeIf {
                    it.grant.workoutId==workoutId &&
                    it.currentSource==it.approvedSource &&
                    it.athleteId==active.athleteId &&
                    it.storageRevision==record.revision
                }
            }
        }.getOrNull()

    /** Old issue remains owned after program update or grant revocation. */
    override fun canAccessIssuedWorkout(
        ownerKey:String,source:KotlinTrainingSourceRevision
    ):Boolean =
        runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val active=active(ownerKey)
                active!=null && active.athleteId==source.athleteId &&
                    run {
                        val record=read(path(ownerKey))
                        // If no approval file remains (legacy recovery),
                        // do not guess ownership based on user-entered IDs.
                        record!=null && record.ownerKey==ownerKey &&
                            record.athleteId==active.athleteId
                    }
            }
        }.getOrDefault(false)
}
