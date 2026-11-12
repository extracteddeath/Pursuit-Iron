package io.github.extracteddeath.pursuitiron.nativecoachstore

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
import java.util.Collections

/**
 * Athlete-owned, exercise-ID-scoped reusable notes. These are PERSONAL setup
 * observations, NOT an exercise catalog, coach prescription, or journal set
 * operation. The host must independently verify that the exact exercise is
 * present in the signed-in athlete's current native issued workout.
 *
 * Setup keys can be authored by the reviewed catalog or saved earlier by the
 * athlete. Unknown old keys must survive unrelated edits and remain labeled
 * as previous settings, NEVER silently discarded or interpreted as loads.
 */
object NativePinnedExerciseNotePolicy {
    const val MAX_NOTE_CHARS=2000
    const val MAX_SETUP_VALUE_CHARS=200
    const val MAX_SETUP_FIELDS=32
    /** Original v1 schema; still accepted on disk without alteration. */
    val setupKeys=setOf("attachment","seat","grip","other")
    private val reservedKeys=setOf("__proto__","prototype","constructor")
    fun validSetupKey(key:String):Boolean =
        key.length in 1..64 &&
        key !in reservedKeys &&
        validText(key,64) &&
        key.none {it=='\n'||it=='\r'||it=='\t'}
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val event=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,127}$")
    fun validExercise(idValue:String)=id.matches(idValue)
    fun validEvent(eventId:String)=event.matches(eventId)
    fun validText(value:String,max:Int):Boolean {
        if(value.length>max)return false
        var index=0
        while(index<value.length) {
            val char=value[index]
            if(char=='\u0000' || char.code<32 &&
                char!='\n' && char!='\r' && char!='\t')return false
            if(Character.isHighSurrogate(char)) {
                if(index+1>=value.length ||
                    !Character.isLowSurrogate(value[index+1]))return false
                index+=2
            } else {
                if(Character.isLowSurrogate(char))return false
                index++
            }
        }
        return true
    }
    fun validContent(note:String,setup:Map<String,String>):Boolean =
        validText(note,MAX_NOTE_CHARS) &&
        setup.size<=MAX_SETUP_FIELDS && setup.all { (key,value) ->
            validSetupKey(key) && value.isNotBlank() &&
                validText(value,MAX_SETUP_VALUE_CHARS)
        }
}

data class NativePinnedExerciseNote(
    val ownerKey:String,
    val athleteId:String,
    val exerciseId:String,
    val revision:Long,
    val text:String,
    val setup:Map<String,String>,
    val lastEventId:String?
) {
    /** Only author-written notes. No workout target or coach input. */
    val hasContent:Boolean get()=text.isNotBlank() || setup.isNotEmpty()
}

enum class NativePinnedNoteRefusal {
    NOT_AUTHORIZED, INVALID_EXERCISE, INVALID_CONTENT, REVISION_CONFLICT,
    EVENT_ID_COLLISION, CORRUPT_OR_PENDING, STORAGE_UNAVAILABLE
}
sealed interface NativePinnedNoteRead {
    data class Current(val note:NativePinnedExerciseNote):NativePinnedNoteRead
    data class Rejected(val reason:NativePinnedNoteRefusal):NativePinnedNoteRead
}
sealed interface NativePinnedNoteWrite {
    data class Saved(val note:NativePinnedExerciseNote,val replayed:Boolean):
        NativePinnedNoteWrite
    data class Rejected(val reason:NativePinnedNoteRefusal):NativePinnedNoteWrite
}

/** An exhaustive saved-note inventory is not an exercise-catalog audit.
 * Notes and older setup keys may legitimately outlive the original lift.
 * All physical note files must nonetheless belong to this exact athlete.
 */
internal sealed interface NativePinnedNoteInventory {
    data class Verified(val notes:List<NativePinnedExerciseNote>):
        NativePinnedNoteInventory
    data object InvalidOrUnrecognized:NativePinnedNoteInventory
}

/**
 * Single-exercise files avoid whole-map conflicts when another exercise's
 * note is edited. Atomic per-owner lock + per-exercise CAS prevent concurrent
 * overwrites. Persisted bytes include owner, athlete, exact exercise, event
 * identity, source-independent content, and SHA-256 corruption detection.
 *
 * Missing record is an empty note. Malformed/partially committed records are
 * NEVER interpreted as empty, and no migration from JS history is implied.
 */
class NativeKotlinPinnedExerciseNoteFileStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val magic=0x5049504e // PIPN
    private val version=2
    private val maxBytes=16_384
    private val maxRevision=9_007_199_254_740_991L
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun owner(key:String)=runCatching {identity.signedInIdentity()}
        .getOrNull()?.takeIf {
            it.ownerKey==key &&
                NativePinnedExerciseNotePolicy.validExercise(it.ownerKey) &&
                NativePinnedExerciseNotePolicy.validExercise(it.athleteId)
        }
    private fun prefix(key:String,exercise:String):String =
        "pinned-exercise-"+sha((key+":"+exercise).toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun file(key:String,exercise:String)=
        root.resolve(prefix(key,exercise)+".bin")
    private fun pending(key:String,exercise:String)=
        root.resolve(prefix(key,exercise)+".pending")
    private fun fresh(key:String,athlete:String,exercise:String)=
        NativePinnedExerciseNote(key,athlete,exercise,0,"",emptyMap(),null)
    private fun valid(note:NativePinnedExerciseNote):Boolean =
        NativePinnedExerciseNotePolicy.validExercise(note.ownerKey) &&
        NativePinnedExerciseNotePolicy.validExercise(note.athleteId) &&
        NativePinnedExerciseNotePolicy.validExercise(note.exerciseId) &&
        note.revision in 0..maxRevision &&
        (note.revision==0L && note.lastEventId==null ||
            note.revision>0L && note.lastEventId?.let{
                NativePinnedExerciseNotePolicy.validEvent(it)
            }==true) &&
        NativePinnedExerciseNotePolicy.validContent(note.text,note.setup)

    private fun encode(note:NativePinnedExerciseNote):ByteArray {
        require(valid(note))
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            // Preserve original v1 encoding for four-key notes; use v2 only
            // when the athlete has retained a catalog-specific/orphan key.
            val format=if(note.setup.keys.all {
                it in NativePinnedExerciseNotePolicy.setupKeys
            })1 else version
            out.writeInt(magic);out.writeInt(format)
            out.writeUTF(note.ownerKey)
            out.writeUTF(note.athleteId)
            out.writeUTF(note.exerciseId)
            out.writeLong(note.revision)
            out.writeBoolean(note.lastEventId!=null)
            note.lastEventId?.let(out::writeUTF)
            out.writeUTF(note.text)
            out.writeInt(note.setup.size)
            note.setup.toSortedMap().forEach{(key,value)->
                out.writeUTF(key);out.writeUTF(value)
            }
        }
        val body=bytes.toByteArray()
        require(body.size<=maxBytes-36)
        return ByteArrayOutputStream().also { stream ->
            DataOutputStream(stream).use { out ->
                out.writeInt(body.size)
                out.write(body)
                out.write(sha(body))
            }
        }.toByteArray()
    }
    // The shared decoder is used by normal cold reads and exhaustive
    // restore inventory: neither path may apply a different note format.
    private fun decode(all:ByteArray):NativePinnedExerciseNote =
        DataInputStream(ByteArrayInputStream(all)).use { input ->
            val size=input.readInt()
            if(size !in 16..maxBytes-36 || size+36!=all.size)
                throw IOException("invalid note envelope")
            val body=ByteArray(size)
            input.readFully(body)
            val digest=ByteArray(32)
            input.readFully(digest)
            if(!MessageDigest.isEqual(sha(body),digest))
                throw IOException("invalid note checksum")
            DataInputStream(ByteArrayInputStream(body)).use { data ->
                if(data.readInt()!=magic)
                    throw IOException("invalid note magic")
                val format=data.readInt()
                if(format !in 1..version)
                    throw IOException("unsupported note version")
                val storedKey=data.readUTF()
                val storedAthlete=data.readUTF()
                val storedExercise=data.readUTF()
                val revision=data.readLong()
                val event=if(data.readBoolean())data.readUTF() else null
                val text=data.readUTF()
                val count=data.readInt()
                if(count !in 0..if(format==1)
                    NativePinnedExerciseNotePolicy.setupKeys.size
                    else NativePinnedExerciseNotePolicy.MAX_SETUP_FIELDS)
                    throw IOException("invalid note setup count")
                val setup=LinkedHashMap<String,String>()
                repeat(count) {
                    val field=data.readUTF()
                    val value=data.readUTF()
                    if(format==1 && field !in NativePinnedExerciseNotePolicy.setupKeys)
                        throw IOException("unknown legacy setup field")
                    if(setup.put(field,value)!=null)
                        throw IOException("duplicate setup field")
                }
                if(data.available()!=0)throw IOException("trailing note bytes")
                NativePinnedExerciseNote(storedKey,storedAthlete,storedExercise,
                    revision,text,Collections.unmodifiableMap(setup),event).also{
                    if(!valid(it))
                        throw IOException("note content invalid")
                }
            }
        }

    private fun readDisk(key:String,athlete:String,exercise:String):
        NativePinnedExerciseNote {
        if(Files.exists(pending(key,exercise)))
            throw IOException("pending exercise note write")
        val path=file(key,exercise)
        if(!Files.exists(path))return fresh(key,athlete,exercise)
        if(Files.isSymbolicLink(root)||Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path)||Files.size(path) !in 40L..maxBytes.toLong())
            throw IOException("invalid exercise note file")
        return decode(Files.readAllBytes(path)).also {
            if(it.ownerKey!=key||it.athleteId!=athlete||
                it.exerciseId!=exercise)
                throw IOException("note owner or content mismatch")
        }
    }
    private fun atomic(key:String,exercise:String,next:NativePinnedExerciseNote) {
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root))throw IOException("unsafe note directory")
        val stage=pending(key,exercise)
        if(Files.exists(stage))throw IOException("unresolved note write")
        try{
            FileChannel.open(stage,StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE).use{channel->
                val raw=ByteBuffer.wrap(encode(next))
                while(raw.hasRemaining())channel.write(raw)
                channel.force(true)
            }
            Files.move(stage,file(key,exercise),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        }catch(error:Exception) {
            // An unresolved .pending is intentionally fail-closed.
            throw IOException("unable to commit exercise note",error)
        }
    }

    fun read(key:String,exercise:String):NativePinnedNoteRead {
        if(!NativePinnedExerciseNotePolicy.validExercise(exercise))
            return NativePinnedNoteRead.Rejected(NativePinnedNoteRefusal.INVALID_EXERCISE)
        return runCatching {
            fence.withOwnerTransaction(key) {
                val current=owner(key) ?:return@withOwnerTransaction NativePinnedNoteRead.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                try {NativePinnedNoteRead.Current(
                    readDisk(key,current.athleteId,exercise))}
                catch(_:Exception){NativePinnedNoteRead.Rejected(
                    NativePinnedNoteRefusal.CORRUPT_OR_PENDING)}
            }
        }.getOrElse{NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Owner-fenced recovery inventory; reads EVERY saved note. A normal
     * per-exercise read is allowed to return an empty default, but restore
     * must never overlook a corrupt, foreign, renamed or pending note file.
     * Only physical note records are inventoried; missing files are valid.
     */
    internal fun inventoryOwner(key:String):NativePinnedNoteInventory =
        runCatching {
            fence.withOwnerTransaction(key) {
                val current=owner(key)
                    ?:return@withOwnerTransaction NativePinnedNoteInventory.InvalidOrUnrecognized
                if(!Files.exists(root,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    return@withOwnerTransaction NativePinnedNoteInventory.Verified(
                        emptyList())
                require(!Files.isSymbolicLink(root) &&
                    Files.isDirectory(root,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                val physical=Files.list(root).use {stream->
                    stream.filter {
                        it.fileName.toString().startsWith("pinned-exercise-")
                    }.toList()
                }
                require(physical.size<=4096)
                val notes=mutableListOf<NativePinnedExerciseNote>()
                val exerciseIds=mutableSetOf<String>()
                val name=Regex("^pinned-exercise-[a-f0-9]{64}\\.bin$")
                for(path in physical.sortedBy{it.fileName.toString()}) {
                    require(name.matches(path.fileName.toString()) &&
                        !Files.isSymbolicLink(path) &&
                        Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                        Files.size(path) in 40L..maxBytes.toLong())
                    val note=decode(Files.readAllBytes(path))
                    require(note.ownerKey==key &&
                        note.athleteId==current.athleteId &&
                        note.revision>0L &&
                        file(key,note.exerciseId)==path &&
                        exerciseIds.add(note.exerciseId))
                    notes.add(note)
                }
                NativePinnedNoteInventory.Verified(
                    Collections.unmodifiableList(notes))
            }
        }.getOrElse {NativePinnedNoteInventory.InvalidOrUnrecognized}

    fun save(
        key:String,exercise:String,expectedRevision:Long,eventId:String,
        text:String,setup:Map<String,String>
    ):NativePinnedNoteWrite {
        if(!NativePinnedExerciseNotePolicy.validExercise(exercise))
            return NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.INVALID_EXERCISE)
        if(expectedRevision<0 || expectedRevision>=maxRevision ||
            !NativePinnedExerciseNotePolicy.validEvent(eventId) ||
            !NativePinnedExerciseNotePolicy.validContent(text,setup))
            return NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.INVALID_CONTENT)
        return runCatching {
            fence.withOwnerTransaction(key) {
                val current=owner(key) ?:return@withOwnerTransaction NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                val previous=try{readDisk(key,current.athleteId,exercise)}
                    catch(_:Exception){return@withOwnerTransaction NativePinnedNoteWrite.Rejected(
                            NativePinnedNoteRefusal.CORRUPT_OR_PENDING)}
                // A lost write acknowledgment must not double-apply a note.
                if(previous.lastEventId==eventId) {
                    return@withOwnerTransaction if(previous.revision==expectedRevision+1 &&
                        previous.text==text && previous.setup==setup)
                        NativePinnedNoteWrite.Saved(previous,true)
                    else NativePinnedNoteWrite.Rejected(
                        NativePinnedNoteRefusal.EVENT_ID_COLLISION)
                }
                if(previous.revision!=expectedRevision)
                    return@withOwnerTransaction NativePinnedNoteWrite.Rejected(
                        NativePinnedNoteRefusal.REVISION_CONFLICT)
                val updated=previous.copy(
                    revision=previous.revision+1,
                    text=text,
                    setup=Collections.unmodifiableMap(LinkedHashMap(setup)),
                    lastEventId=eventId)
                if(owner(key)!=current)return@withOwnerTransaction NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                atomic(key,exercise,updated)
                NativePinnedNoteWrite.Saved(updated,false)
            }
        }.getOrElse {NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)}
    }
}
