package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.ComposerFocus
import io.github.extracteddeath.pursuitiron.rebuild.TrainingPhase
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
 * Versioned, explicitly calibrated INPUTS to a first-program generator.
 * This file is NOT a coaching review, signed program or training grant.
 * A reader must re-run generation against verified gym/source and require
 * byte-equivalent authored cycle content before using the derived policy.
 */
data class NativeFirstProgramSeed(
    val cycleId:String,
    val title:String,
    val blockWeeks:Int,
    val sourceRevision:Long,
    val policyVersion:String,
    val phase:TrainingPhase,
    val startingLoads:Map<String,BigDecimal>,
    val bodyweightExercises:Set<String>,
    val extraFocuses:List<ComposerFocus>,
    val nonSetSeconds:Int
)

enum class NativeFirstProgramSeedRefusal {
    NOT_AUTHORIZED, INVALID_SEED, ALREADY_EXISTS, MISSING,
    CORRUPT, STORAGE_UNAVAILABLE
}

sealed interface NativeFirstProgramSeedRead {
    data class Found(val seed:NativeFirstProgramSeed):NativeFirstProgramSeedRead
    data object Missing:NativeFirstProgramSeedRead
    data class Rejected(val reason:NativeFirstProgramSeedRefusal):NativeFirstProgramSeedRead
}

sealed interface NativeFirstProgramSeedWrite {
    data object Committed:NativeFirstProgramSeedWrite
    data class Rejected(val reason:NativeFirstProgramSeedRefusal):NativeFirstProgramSeedWrite
}

/**
 * Crash-safe create-once provenance. Stored BEFORE the cycle draft under
 * the SAME owner transaction: a crash may leave an orphan provenance record,
 * but NEVER an untraceable generated draft. Reopening an orphan has no power
 * because the host additionally requires the matching complete cycle.
 *
 * An edited program is still editable: the saved seed simply stops matching.
 */
class NativeKotlinFirstProgramSeedFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494653 // PIFS
    private val version=1
    private val maxBytes=8192

    private fun owner(ownerKey:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(ownerKey))return null
        return runCatching {identity.signedInIdentity()}.getOrNull()
            ?.takeIf {it.ownerKey==ownerKey && id.matches(it.athleteId)}
    }
    private fun path(ownerKey:String,cycleId:String):Path {
        val digest=MessageDigest.getInstance("SHA-256").digest(
            (ownerKey+"\u0000"+cycleId).toByteArray(Charsets.UTF_8))
        return directory.resolve("first-program-"+digest.joinToString("") {
            "%02x".format(it.toInt() and 255)
        }+".bin")
    }
    private fun valid(s:NativeFirstProgramSeed):Boolean {
        if(!id.matches(s.cycleId) || s.title.isBlank() ||
            s.title.length>120 || s.blockWeeks !in 1..52 ||
            s.sourceRevision !in 1..9_007_199_254_740_991L ||
            !id.matches(s.policyVersion) ||
            s.nonSetSeconds !in 0..21600 ||
            s.startingLoads.size>32 || s.bodyweightExercises.size>32 ||
            s.extraFocuses.size>2 ||
            s.startingLoads.keys.intersect(s.bodyweightExercises).isNotEmpty() ||
            !s.startingLoads.keys.all(id::matches) ||
            !s.bodyweightExercises.all(id::matches))
            return false
        return s.startingLoads.values.all {
            it>BigDecimal.ZERO && it<=BigDecimal("100000") &&
                it.stripTrailingZeros().scale()<=3
        }
    }
    private fun encode(ownerKey:String,athleteId:String,
        seed:NativeFirstProgramSeed):ByteArray {
        require(id.matches(ownerKey) && id.matches(athleteId) && valid(seed))
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use {out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(ownerKey);out.writeUTF(athleteId)
            out.writeUTF(seed.cycleId);out.writeUTF(seed.title)
            out.writeInt(seed.blockWeeks);out.writeLong(seed.sourceRevision)
            out.writeUTF(seed.policyVersion);out.writeUTF(seed.phase.name)
            out.writeInt(seed.nonSetSeconds)
            val sorted=seed.startingLoads.toSortedMap()
            out.writeInt(sorted.size)
            for((name,amount) in sorted) {
                out.writeUTF(name)
                out.writeUTF(amount.stripTrailingZeros().toPlainString())
            }
            val bodyweight=seed.bodyweightExercises.sorted()
            out.writeInt(bodyweight.size)
            bodyweight.forEach(out::writeUTF)
            out.writeInt(seed.extraFocuses.size)
            seed.extraFocuses.forEach{out.writeUTF(it.name)}
        }
        val payload=body.toByteArray()
        require(payload.size+36<=maxBytes)
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also {buffer->
            DataOutputStream(buffer).use {out->
                out.writeInt(payload.size);out.write(payload);out.write(checksum)
            }
        }.toByteArray()
    }

    private fun decode(file:Path,ownerKey:String,athleteId:String,
        requestedCycleId:String):NativeFirstProgramSeed? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) || Files.size(file) !in 44L..maxBytes.toLong())
            throw IOException("Invalid first-program seed path")
        val bytes=Files.readAllBytes(file)
        val input=DataInputStream(ByteArrayInputStream(bytes))
        val size=input.readInt()
        if(size !in 8..maxBytes-36 || size+36!=bytes.size)
            throw IOException("Invalid seed length")
        val content=ByteArray(size);input.readFully(content)
        val checksum=ByteArray(32);input.readFully(checksum)
        if(!MessageDigest.isEqual(checksum,
                MessageDigest.getInstance("SHA-256").digest(content)))
            throw IOException("First-program seed checksum mismatch")
        return DataInputStream(ByteArrayInputStream(content)).use {data->
            if(data.readInt()!=magic || data.readInt()!=version ||
                data.readUTF()!=ownerKey || data.readUTF()!=athleteId)
                throw IOException("First-program owner, version or format changed")
            val cycleId=data.readUTF()
            val title=data.readUTF()
            val weeks=data.readInt()
            val revision=data.readLong()
            val policy=data.readUTF()
            val phase=TrainingPhase.valueOf(data.readUTF())
            val overhead=data.readInt()
            val n=data.readInt()
            if(n !in 0..32)throw IOException("Invalid calibrated set")
            val loads=linkedMapOf<String,BigDecimal>()
            repeat(n) {
                val id=data.readUTF()
                if(loads.put(id,BigDecimal(data.readUTF()))!=null)
                    throw IOException("Duplicate calibrated exercise")
            }
            val bwCount=data.readInt()
            if(bwCount !in 0..32)throw IOException("Invalid bodyweight set")
            val bodyweight=linkedSetOf<String>()
            repeat(bwCount) {
                if(!bodyweight.add(data.readUTF()))
                    throw IOException("Duplicate bodyweight exercise")
            }
            val focusesCount=data.readInt()
            if(focusesCount !in 0..2)throw IOException("Invalid extra focuses")
            val focuses=(0 until focusesCount).map {
                ComposerFocus.valueOf(data.readUTF())
            }
            val seed=NativeFirstProgramSeed(cycleId,title,weeks,
                revision,policy,phase,loads,bodyweight,focuses,overhead)
            if(data.available()!=0 || cycleId!=requestedCycleId ||
                !valid(seed))throw IOException("Invalid first-program seed")
            seed
        }
    }

    private fun atomic(file:Path,bytes:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))
            throw IOException("Linked first-program directory")
        val temp=Files.createTempFile(directory,"first-program-",".pending")
        try {
            FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use {channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE)
            } catch(error:AtomicMoveNotSupportedException) {
                throw IOException("Atomic first-program seed rename unavailable",error)
            }
        }finally {Files.deleteIfExists(temp)}
    }

    fun create(ownerKey:String,seed:NativeFirstProgramSeed):NativeFirstProgramSeedWrite {
        if(!valid(seed))
            return NativeFirstProgramSeedWrite.Rejected(
                NativeFirstProgramSeedRefusal.INVALID_SEED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeFirstProgramSeedWrite.Rejected(
                        NativeFirstProgramSeedRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey,seed.cycleId)
                if(Files.exists(file))
                    return@withOwnerTransaction NativeFirstProgramSeedWrite.Rejected(
                        NativeFirstProgramSeedRefusal.ALREADY_EXISTS)
                val bytes=encode(ownerKey,current.athleteId,seed)
                if(owner(ownerKey)!=current)
                    return@withOwnerTransaction NativeFirstProgramSeedWrite.Rejected(
                        NativeFirstProgramSeedRefusal.NOT_AUTHORIZED)
                atomic(file,bytes)
                NativeFirstProgramSeedWrite.Committed
            }
        }catch(_:Exception) {
            NativeFirstProgramSeedWrite.Rejected(
                NativeFirstProgramSeedRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun read(ownerKey:String,cycleId:String):NativeFirstProgramSeedRead {
        if(!id.matches(cycleId))
            return NativeFirstProgramSeedRead.Rejected(
                NativeFirstProgramSeedRefusal.INVALID_SEED)
        return try {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeFirstProgramSeedRead.Rejected(
                        NativeFirstProgramSeedRefusal.NOT_AUTHORIZED)
                val seed=try {decode(path(ownerKey,cycleId),
                    ownerKey,current.athleteId,cycleId)}
                catch(_:Exception) {
                    return@withOwnerTransaction NativeFirstProgramSeedRead.Rejected(
                        NativeFirstProgramSeedRefusal.CORRUPT)
                }
                if(owner(ownerKey)!=current)
                    NativeFirstProgramSeedRead.Rejected(
                        NativeFirstProgramSeedRefusal.NOT_AUTHORIZED)
                else if(seed==null)NativeFirstProgramSeedRead.Missing
                else NativeFirstProgramSeedRead.Found(seed)
            }
        }catch(_:Exception) {
            NativeFirstProgramSeedRead.Rejected(
                NativeFirstProgramSeedRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
