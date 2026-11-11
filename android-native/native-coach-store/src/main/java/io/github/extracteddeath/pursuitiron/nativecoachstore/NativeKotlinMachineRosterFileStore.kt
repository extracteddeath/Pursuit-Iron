package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.*
import java.math.BigDecimal
import java.nio.file.*
import java.security.MessageDigest

/**
 * Private offline store for the athlete's gym-scoped machine A/B roster.
 * A default is only a FUTURE workout starting choice. Past journal receipt
 * machine IDs are never changed by edits to this file.
 *
 * Caller must supply private filesDir-derived root and authenticate the owner.
 */
data class NativeMachineRosterSnapshot(
    val ownerId:String,
    val revision:Long,
    val configurations:List<KotlinGymMachineAlternatives>
)
sealed interface NativeMachineRosterResult {
    data class Saved(val snapshot:NativeMachineRosterSnapshot):NativeMachineRosterResult
    data class Rejected(val reason:String):NativeMachineRosterResult
}
class NativeKotlinMachineRosterFileStore(private val root:Path) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun key(ownerId:String):Path {
        require(id.matches(ownerId))
        val hash=MessageDigest.getInstance("SHA-256")
            .digest(ownerId.toByteArray()).joinToString(""){"%02x".format(it)}
        return root.resolve("machine-roster-$hash.bin")
    }
    private fun writeBody(snapshot:NativeMachineRosterSnapshot):ByteArray {
        require(snapshot.revision in 0..9_007_199_254_740_990L)
        require(snapshot.configurations.size<=256 &&
            snapshot.configurations.all {KotlinMachineAlternativesDomain.valid(it)} &&
            snapshot.configurations.map {it.gymId to it.exerciseId}.distinct().size==
                snapshot.configurations.size)
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.writeInt(0x50494D41) // PIMA
            stream.writeInt(1)
            stream.writeUTF(snapshot.ownerId)
            stream.writeLong(snapshot.revision)
            stream.writeInt(snapshot.configurations.size)
            snapshot.configurations.forEach { config ->
                stream.writeUTF(config.gymId)
                stream.writeUTF(config.exerciseId)
                stream.writeBoolean(config.defaultMachineId!=null)
                if(config.defaultMachineId!=null)stream.writeUTF(config.defaultMachineId)
                stream.writeInt(config.machines.size)
                config.machines.forEach { machine ->
                    stream.writeUTF(machine.id)
                    stream.writeUTF(machine.label)
                    stream.writeBoolean(machine.active)
                    stream.writeBoolean(machine.unit!=null)
                    if(machine.unit!=null)stream.writeUTF(machine.unit.name)
                    stream.writeBoolean(machine.increment!=null)
                    if(machine.increment!=null)
                        stream.writeUTF(machine.increment.stripTrailingZeros().toPlainString())
                    stream.writeBoolean(machine.description!=null)
                    if(machine.description!=null)stream.writeUTF(machine.description)
                }
            }
        }
        val bytes=out.toByteArray()
        require(bytes.size<=256_000)
        return bytes
    }
    private fun encode(snapshot:NativeMachineRosterSnapshot):ByteArray {
        val body=writeBody(snapshot)
        val digest=MessageDigest.getInstance("SHA-256").digest(body)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeInt(body.size);it.write(body);it.write(digest)
            }
        }.toByteArray()
    }
    private fun decode(bytes:ByteArray):NativeMachineRosterSnapshot {
        require(bytes.size in 44..256_036)
        val stream=DataInputStream(ByteArrayInputStream(bytes))
        val length=stream.readInt()
        require(length in 8..256_000 && length+36==bytes.size)
        val body=ByteArray(length);stream.readFully(body)
        val digest=ByteArray(32);stream.readFully(digest)
        require(MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(body),digest))
        val inp=DataInputStream(ByteArrayInputStream(body))
        require(inp.readInt()==0x50494D41 && inp.readInt()==1)
        val ownerId=inp.readUTF()
        require(id.matches(ownerId))
        val revision=inp.readLong()
        val count=inp.readInt()
        require(count in 0..256)
        val configs=List(count) {
            val gymId=inp.readUTF();val exerciseId=inp.readUTF()
            val default=if(inp.readBoolean())inp.readUTF() else null
            val machines=List(inp.readInt().also {require(it in 0..32)}) {
                val machineId=inp.readUTF()
                val label=inp.readUTF()
                val active=inp.readBoolean()
                val unit=if(inp.readBoolean())LoadUnit.valueOf(inp.readUTF()) else null
                val increment=if(inp.readBoolean())BigDecimal(inp.readUTF()) else null
                val description=if(inp.readBoolean())inp.readUTF() else null
                KotlinPhysicalMachine(machineId,label,active,unit,increment,description)
            }
            KotlinGymMachineAlternatives(gymId,exerciseId,default,machines)
                .also { require(KotlinMachineAlternativesDomain.valid(it)) }
        }
        require(inp.available()==0)
        return NativeMachineRosterSnapshot(ownerId,revision,configs)
            .also { require(writeBody(it).isNotEmpty()) }
    }
    fun read(ownerId:String):NativeMachineRosterResult = runCatching {
        val path=key(ownerId)
        if(!Files.exists(path))NativeMachineRosterResult.Saved(
            NativeMachineRosterSnapshot(ownerId,0,emptyList()))
        else {
            val snapshot=decode(Files.readAllBytes(path))
            if(snapshot.ownerId!=ownerId) NativeMachineRosterResult.Rejected("OWNER_MISMATCH")
            else NativeMachineRosterResult.Saved(snapshot)
        }
    }.getOrElse {NativeMachineRosterResult.Rejected("UNREADABLE_OR_INVALID")}

    /** CAS edit; never overwrites on a stale revision. */
    @Synchronized fun save(
        ownerId:String,expectedRevision:Long,
        configs:List<KotlinGymMachineAlternatives>
    ):NativeMachineRosterResult {
        val prior=read(ownerId)
        if(prior !is NativeMachineRosterResult.Saved)
            return prior
        if(prior.snapshot.revision!=expectedRevision)
            return NativeMachineRosterResult.Rejected("STALE_REVISION")
        val changed=NativeMachineRosterSnapshot(ownerId,expectedRevision+1,configs)
        val bytes=runCatching {encode(changed)}
            .getOrElse {return NativeMachineRosterResult.Rejected("INVALID_ROSTER")}
        return runCatching {
            Files.createDirectories(root)
            val target=key(ownerId)
            val temp=Files.createTempFile(root,"machine-roster-", ".pending")
            try {
                Files.write(temp,bytes)
                try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)}
                catch(_:AtomicMoveNotSupportedException) {
                    Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {Files.deleteIfExists(temp)}
            NativeMachineRosterResult.Saved(changed)
        }.getOrElse {NativeMachineRosterResult.Rejected("STORAGE_UNAVAILABLE")}
    }
}
