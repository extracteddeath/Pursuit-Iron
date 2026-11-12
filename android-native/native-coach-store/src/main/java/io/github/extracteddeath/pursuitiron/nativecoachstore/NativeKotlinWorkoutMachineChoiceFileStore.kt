package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinMachineAlternativesDomain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections

/**
 * Private, revisioned, per-issued-workout physical station choices.
 *
 * This is a session INPUT, never a performed set or progression record.
 * Only the owner-fenced host may read/write it after cold-opening the real
 * issued workout and checking its immutable gym, athlete and actual exercise.
 * Past journal receipts retain their own exact machineInstanceId.
 */
data class NativeWorkoutMachineChoiceSnapshot(
    val ownerId:String,
    val workoutId:String,
    val custom:Boolean,
    val gymId:String,
    val revision:Long,
    val machines:Map<String,String>
)
sealed interface NativeWorkoutMachineChoiceResult {
    data class Saved(val snapshot:NativeWorkoutMachineChoiceSnapshot):
        NativeWorkoutMachineChoiceResult
    data class Rejected(val reason:String):NativeWorkoutMachineChoiceResult
}

/**
 * Every persisted workout-machine selection must be accounted for, including
 * archived and unfinished workouts. This inventory is read-only; callers must
 * independently confirm gym, exercise and issued-workout identity.
 */
sealed interface NativeWorkoutMachineChoiceInventoryResult {
    data class Verified(val choices:List<NativeWorkoutMachineChoiceSnapshot>):
        NativeWorkoutMachineChoiceInventoryResult
    data class Rejected(val reason:String):NativeWorkoutMachineChoiceInventoryResult
}

class NativeKotlinWorkoutMachineChoiceFileStore(private val root:Path) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val maxRevision=9_007_199_254_740_990L
    private val magic=0x50494D43 // PIMC, v1
    private fun valid(snapshot:NativeWorkoutMachineChoiceSnapshot)=
        id.matches(snapshot.ownerId) &&
        id.matches(snapshot.workoutId) &&
        id.matches(snapshot.gymId) &&
        snapshot.revision in 0..maxRevision &&
        snapshot.machines.size<=128 &&
        snapshot.machines.all { (exercise,machine)->
            id.matches(exercise) && KotlinMachineAlternativesDomain.validInstanceId(machine)
        }

    private fun key(ownerId:String,workoutId:String,custom:Boolean):Path {
        require(id.matches(ownerId) && id.matches(workoutId))
        val raw="$ownerId|$workoutId|$custom"
        val hash=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString(""){"%02x".format(it)}
        return root.resolve("workout-machine-$hash.bin")
    }

    private fun encode(snapshot:NativeWorkoutMachineChoiceSnapshot):ByteArray {
        require(valid(snapshot))
        val raw=ByteArrayOutputStream()
        DataOutputStream(raw).use {out->
            out.writeInt(magic)
            out.writeInt(1)
            out.writeUTF(snapshot.ownerId)
            out.writeUTF(snapshot.workoutId)
            out.writeBoolean(snapshot.custom)
            out.writeUTF(snapshot.gymId)
            out.writeLong(snapshot.revision)
            out.writeInt(snapshot.machines.size)
            snapshot.machines.toSortedMap().forEach { (exercise,machine)->
                out.writeUTF(exercise);out.writeUTF(machine)
            }
        }
        val body=raw.toByteArray()
        require(body.size<=65_536)
        val hash=MessageDigest.getInstance("SHA-256").digest(body)
        return ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use{out->
                out.writeInt(body.size);out.write(body);out.write(hash)
            }
        }.toByteArray()
    }

    private fun decode(bytes:ByteArray):NativeWorkoutMachineChoiceSnapshot {
        require(bytes.size in 44..65_572)
        val stream=DataInputStream(ByteArrayInputStream(bytes))
        val size=stream.readInt()
        require(size in 8..65_536 && size+36==bytes.size)
        val body=ByteArray(size);stream.readFully(body)
        val digest=ByteArray(32);stream.readFully(digest)
        require(MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(body),digest))
        val inp=DataInputStream(ByteArrayInputStream(body))
        require(inp.readInt()==magic && inp.readInt()==1)
        val owner=inp.readUTF()
        val workout=inp.readUTF()
        val custom=inp.readBoolean()
        val gym=inp.readUTF()
        val revision=inp.readLong()
        val count=inp.readInt()
        require(count in 0..128)
        val machineChoices=linkedMapOf<String,String>()
        repeat(count) {
            val exercise=inp.readUTF()
            val machine=inp.readUTF()
            require(machineChoices.put(exercise,machine)==null)
        }
        require(inp.available()==0)
        return NativeWorkoutMachineChoiceSnapshot(
            owner,workout,custom,gym,revision,
            Collections.unmodifiableMap(machineChoices)
        ).also{require(valid(it))}
    }

    fun read(
        ownerId:String,workoutId:String,custom:Boolean,gymId:String
    ):NativeWorkoutMachineChoiceResult = runCatching {
        require(id.matches(gymId))
        val file=key(ownerId,workoutId,custom)
        if(!Files.exists(file))
            NativeWorkoutMachineChoiceResult.Saved(
                NativeWorkoutMachineChoiceSnapshot(ownerId,workoutId,custom,gymId,
                    0,emptyMap()))
        else {
            val recovered=decode(Files.readAllBytes(file))
            if(recovered.ownerId!=ownerId || recovered.workoutId!=workoutId ||
                recovered.custom!=custom || recovered.gymId!=gymId)
                NativeWorkoutMachineChoiceResult.Rejected("WORKOUT_SCOPE_CHANGED")
            else NativeWorkoutMachineChoiceResult.Saved(recovered)
        }
    }.getOrElse{NativeWorkoutMachineChoiceResult.Rejected("UNREADABLE_OR_INVALID")}

    /**
     * Complete physical inventory of all native machine-choice records.
     * A valid but foreign owner's record, an orphan, pending write, renamed
     * file or corrupt checksum must never vanish from a restore audit.
     * Caller holds the existing native owner transaction fence.
     */
    internal fun inventoryOwner(ownerId:String):
        NativeWorkoutMachineChoiceInventoryResult = runCatching {
        require(id.matches(ownerId))
        if(!Files.exists(root,java.nio.file.LinkOption.NOFOLLOW_LINKS))
            return@runCatching NativeWorkoutMachineChoiceInventoryResult.Verified(
                emptyList())
        require(!Files.isSymbolicLink(root) &&
            Files.isDirectory(root,java.nio.file.LinkOption.NOFOLLOW_LINKS))
        val files=Files.list(root).use { stream->
            stream.filter{it.fileName.toString().startsWith("workout-machine-")}
                .toList()
        }
        require(files.size<=4096)
        val snapshots=mutableListOf<NativeWorkoutMachineChoiceSnapshot>()
        val names=mutableSetOf<String>()
        for(file in files.sortedBy{it.fileName.toString()}) {
            val name=file.fileName.toString()
            require(Regex("^workout-machine-[a-f0-9]{64}\\.bin$").matches(name))
            require(!Files.isSymbolicLink(file) &&
                Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                Files.size(file) in 44L..65_572L)
            val snapshot=decode(Files.readAllBytes(file))
            require(snapshot.ownerId==ownerId && snapshot.revision>0L &&
                key(snapshot.ownerId,snapshot.workoutId,snapshot.custom)==file)
            require(names.add(snapshot.workoutId+"|"+snapshot.custom))
            snapshots.add(snapshot)
        }
        NativeWorkoutMachineChoiceInventoryResult.Verified(snapshots)
    }.getOrElse {
        NativeWorkoutMachineChoiceInventoryResult.Rejected(
            "UNVERIFIED_OR_UNRECOGNIZED_WORKOUT_MACHINE_FILE")
    }

    /** CAS under caller's owner fence; a full cold read precedes every write. */
    @Synchronized fun save(
        ownerId:String,workoutId:String,custom:Boolean,gymId:String,
        expectedRevision:Long,machines:Map<String,String>
    ):NativeWorkoutMachineChoiceResult {
        val prior=read(ownerId,workoutId,custom,gymId)
        if(prior !is NativeWorkoutMachineChoiceResult.Saved)return prior
        if(prior.snapshot.revision!=expectedRevision)
            return NativeWorkoutMachineChoiceResult.Rejected("STALE_REVISION")
        val proposed=NativeWorkoutMachineChoiceSnapshot(
            ownerId,workoutId,custom,gymId,expectedRevision+1,
            LinkedHashMap(machines))
        val encoded=runCatching{encode(proposed)}.getOrElse{
            return NativeWorkoutMachineChoiceResult.Rejected("INVALID_SELECTION")
        }
        return runCatching{
            Files.createDirectories(root)
            val target=key(ownerId,workoutId,custom)
            val temp=Files.createTempFile(root,"workout-machine-", ".pending")
            try {
                Files.write(temp,encoded)
                try {Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)}
                catch(_:AtomicMoveNotSupportedException) {
                    Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {Files.deleteIfExists(temp)}
            NativeWorkoutMachineChoiceResult.Saved(proposed.copy(
                machines=Collections.unmodifiableMap(LinkedHashMap(machines))))
        }.getOrElse{NativeWorkoutMachineChoiceResult.Rejected("STORAGE_UNAVAILABLE")}
    }
}
