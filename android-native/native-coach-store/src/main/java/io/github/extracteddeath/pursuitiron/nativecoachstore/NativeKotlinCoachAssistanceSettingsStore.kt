package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CoachAssistancePolicy
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
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Authoritative, owner-scoped, cold-recoverable coaching opt-outs.
 * Missing record keeps the existing STANDARD behavior. Bad or interrupted
 * records NEVER silently turn coaching back on. */
data class NativeCoachAssistanceSettings(
    val ownerKey:String,val athleteId:String,val revision:Long,
    val policy:CoachAssistancePolicy
)
enum class NativeCoachSettingsRefusal {
    NOT_AUTHORIZED,REVISION_CONFLICT,INVALID_SELECTION,CORRUPT_OR_PENDING,
    STORAGE_UNAVAILABLE
}
sealed interface NativeCoachSettingsRead {
    data class Current(val settings:NativeCoachAssistanceSettings):
        NativeCoachSettingsRead
    data class Rejected(val reason:NativeCoachSettingsRefusal):
        NativeCoachSettingsRead
}
sealed interface NativeCoachSettingsWrite {
    data class Saved(val settings:NativeCoachAssistanceSettings):
        NativeCoachSettingsWrite
    data class Rejected(val reason:NativeCoachSettingsRefusal):
        NativeCoachSettingsWrite
}

class NativeKotlinCoachAssistanceSettingsStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x5049434f // PICO
    private val version=1
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun owner(key:String)=runCatching{identity.signedInIdentity()}
        .getOrNull()?.takeIf{it.ownerKey==key&&id.matches(key)&&id.matches(it.athleteId)}
    private fun prefix(key:String)= "coach-assistance-"+
        sha(key.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    private fun path(key:String)=root.resolve(prefix(key)+".bin")
    private fun pending(key:String)=root.resolve(prefix(key)+".pending")
    private fun encode(r:NativeCoachAssistanceSettings):ByteArray {
        require(id.matches(r.ownerKey)&&id.matches(r.athleteId))
        require(r.revision in 1..9_007_199_254_740_991L)
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(r.ownerKey);out.writeUTF(r.athleteId)
            out.writeLong(r.revision)
            out.writeBoolean(r.policy.coachingSuggestionsEnabled)
            out.writeBoolean(r.policy.fatigueAdaptationEnabled)
            out.writeBoolean(r.policy.progressionSuggestionsEnabled)
        }
        val bytes=payload.toByteArray()
        return ByteArrayOutputStream().also{all->
            DataOutputStream(all).use{out->
                out.writeInt(bytes.size);out.write(bytes);out.write(sha(bytes))
            }
        }.toByteArray()
    }
    private fun readDisk(key:String,athlete:String):NativeCoachAssistanceSettings {
        if(Files.exists(pending(key)))
            throw IOException("unresolved coaching settings transaction")
        val file=path(key)
        if(!Files.exists(file))return NativeCoachAssistanceSettings(
            key,athlete,0,CoachAssistancePolicy.STANDARD)
        if(Files.isSymbolicLink(root)||Files.isSymbolicLink(file)||
            !Files.isRegularFile(file)||Files.size(file) !in 44L..512L)
            throw IOException("invalid coaching settings file")
        val bytes=Files.readAllBytes(file)
        return DataInputStream(ByteArrayInputStream(bytes)).use{inp->
            val size=inp.readInt()
            if(size !in 16..476||size+36!=bytes.size)
                throw IOException("invalid settings length")
            val body=ByteArray(size);inp.readFully(body)
            val sum=ByteArray(32);inp.readFully(sum)
            if(!MessageDigest.isEqual(sha(body),sum))
                throw IOException("invalid settings checksum")
            DataInputStream(ByteArrayInputStream(body)).use{data->
                if(data.readInt()!=magic||data.readInt()!=version)
                    throw IOException("invalid settings version")
                val ownerKey=data.readUTF()
                val athleteId=data.readUTF()
                val revision=data.readLong()
                val policy=CoachAssistancePolicy(
                    data.readBoolean(),data.readBoolean(),data.readBoolean())
                if(data.available()!=0)throw IOException("trailing settings bytes")
                NativeCoachAssistanceSettings(ownerKey,athleteId,
                    revision,policy).also{
                    encode(it)
                    if(it.ownerKey!=key||it.athleteId!=athlete)
                        throw IOException("coaching settings owner mismatch")
                }
            }
        }
    }
    private fun writeAtomic(key:String,value:NativeCoachAssistanceSettings) {
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root))throw IOException("unsafe directory")
        val tmp=pending(key)
        if(Files.exists(tmp))throw IOException("pending coaching settings")
        try{
            FileChannel.open(tmp,StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE).use{channel->
                val bytes=ByteBuffer.wrap(encode(value))
                while(bytes.hasRemaining())channel.write(bytes)
                channel.force(true)
            }
            Files.move(tmp,path(key),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        }catch(e:Exception){
            // Unresolved pending state deliberately remains fail-closed.
            throw IOException("could not durably write coaching settings",e)
        }
    }
    fun read(key:String):NativeCoachSettingsRead =
        runCatching{fence.withOwnerTransaction(key){
            val current=owner(key)
                ?:return@withOwnerTransaction NativeCoachSettingsRead.Rejected(
                    NativeCoachSettingsRefusal.NOT_AUTHORIZED)
            try{NativeCoachSettingsRead.Current(readDisk(key,current.athleteId))}
            catch(_:Exception){NativeCoachSettingsRead.Rejected(
                NativeCoachSettingsRefusal.CORRUPT_OR_PENDING)}
        }}.getOrElse{NativeCoachSettingsRead.Rejected(
            NativeCoachSettingsRefusal.STORAGE_UNAVAILABLE)}

    /** CAS write; no duplicate setting fields in workout or gym stores. */
    fun save(
        key:String,expectedRevision:Long,policy:CoachAssistancePolicy
    ):NativeCoachSettingsWrite {
        if(expectedRevision<0 || expectedRevision>=9_007_199_254_740_991L)
            return NativeCoachSettingsWrite.Rejected(
                NativeCoachSettingsRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(key){
            val current=owner(key)
                ?:return@withOwnerTransaction NativeCoachSettingsWrite.Rejected(
                    NativeCoachSettingsRefusal.NOT_AUTHORIZED)
            val old=try{readDisk(key,current.athleteId)}catch(_:Exception){
                return@withOwnerTransaction NativeCoachSettingsWrite.Rejected(
                    NativeCoachSettingsRefusal.CORRUPT_OR_PENDING)
            }
            if(old.revision!=expectedRevision)
                return@withOwnerTransaction NativeCoachSettingsWrite.Rejected(
                    NativeCoachSettingsRefusal.REVISION_CONFLICT)
            val updated=old.copy(revision=old.revision+1,policy=policy)
            if(owner(key)!=current)
                return@withOwnerTransaction NativeCoachSettingsWrite.Rejected(
                    NativeCoachSettingsRefusal.NOT_AUTHORIZED)
            writeAtomic(key,updated)
            NativeCoachSettingsWrite.Saved(updated)
        }}.getOrElse{NativeCoachSettingsWrite.Rejected(
            NativeCoachSettingsRefusal.STORAGE_UNAVAILABLE)}
    }
}
