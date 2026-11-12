package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.*
import java.math.BigDecimal
import java.nio.file.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
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
    companion object {
        // @Synchronized protects one object only. The app can open independent
        // store instances after a process restart, in a background worker, or
        // from another owner-fenced service. Same-process calls need a shared
        // mutex before the OS lock (Java disallows overlapping JVM file locks).
        private val localCasLocks=ConcurrentHashMap<Path,Any>()
    }
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
            stream.writeInt(2) // V1 rosters decode without calibrated stack limits
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
                    machine.unit?.let {stream.writeUTF(it.name)}
                    stream.writeBoolean(machine.increment!=null)
                    machine.increment?.let {
                        stream.writeUTF(it.stripTrailingZeros().toPlainString())
                    }
                    stream.writeBoolean(machine.description!=null)
                    if(machine.description!=null)stream.writeUTF(machine.description)
                    stream.writeBoolean(machine.minimum!=null)
                    machine.minimum?.let{stream.writeUTF(it.stripTrailingZeros().toPlainString())}
                    stream.writeBoolean(machine.maximum!=null)
                    machine.maximum?.let{stream.writeUTF(it.stripTrailingZeros().toPlainString())}
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
        require(inp.readInt()==0x50494D41)
        val version=inp.readInt()
        require(version in 1..2)
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
                val minimum=if(version>=2 && inp.readBoolean())
                    BigDecimal(inp.readUTF()) else null
                val maximum=if(version>=2 && inp.readBoolean())
                    BigDecimal(inp.readUTF()) else null
                KotlinPhysicalMachine(machineId,label,active,unit,increment,
                    description,minimum,maximum)
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

    /**
     * The read/compare/write is ONE transaction across separately constructed
     * stores and processes. The higher-level athlete fence still authenticates
     * the caller and enforces current-gym edits; this lower-level lock cannot
     * be skipped by another store instance that knows the same private root.
     */
    @Synchronized fun save(
        ownerId:String,expectedRevision:Long,
        configs:List<KotlinGymMachineAlternatives>
    ):NativeMachineRosterResult {
        if(!id.matches(ownerId))
            return NativeMachineRosterResult.Rejected("INVALID_OWNER")
        return runCatching {
            Files.createDirectories(root)
            val lockPath=key(ownerId).toAbsolutePath().normalize()
                .resolveSibling(key(ownerId).fileName.toString()+".lock")
            val monitor=localCasLocks.computeIfAbsent(lockPath){Any()}
            synchronized(monitor) {
                FileChannel.open(lockPath,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE).use{channel ->
                    channel.lock().use {saveWithinLock(ownerId,expectedRevision,configs)}
                }
            }
        }.getOrElse {NativeMachineRosterResult.Rejected("STORAGE_UNAVAILABLE")}
    }

    private fun saveWithinLock(
        ownerId:String,expectedRevision:Long,
        configs:List<KotlinGymMachineAlternatives>
    ):NativeMachineRosterResult {
        // Re-read UNDER the file lock: no stale first writer can be reused.
        val prior=read(ownerId)
        if(prior !is NativeMachineRosterResult.Saved)return prior
        if(prior.snapshot.revision!=expectedRevision)
            return NativeMachineRosterResult.Rejected("STALE_REVISION")
        val changed=NativeMachineRosterSnapshot(ownerId,expectedRevision+1,configs)
        val bytes=runCatching {encode(changed)}
            .getOrElse{return NativeMachineRosterResult.Rejected("INVALID_ROSTER")}
        return runCatching {
            val target=key(ownerId)
            val temp=Files.createTempFile(root,"machine-roster-", ".pending")
            try {
                // Force the complete versioned, checksummed file to disk
                // before publishing it as the one current roster.
                FileChannel.open(temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING).use{out ->
                    val buffer=ByteBuffer.wrap(bytes)
                    while(buffer.hasRemaining())out.write(buffer)
                    out.force(true)
                }
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
