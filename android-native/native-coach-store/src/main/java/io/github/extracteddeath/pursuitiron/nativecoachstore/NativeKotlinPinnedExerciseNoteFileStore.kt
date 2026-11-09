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
 * The four generic setup labels are user-entered text, NOT machine-specific
 * metadata. No automatic gym compatibility, attachment, or load inference.
 */
object NativePinnedExerciseNotePolicy {
    const val MAX_NOTE_CHARS=2000
    const val MAX_SETUP_VALUE_CHARS=160
    val setupKeys=setOf("attachment","seat","grip","other")
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
        setup.size<=setupKeys.size && setup.all { (key,value) ->
            key in setupKeys && value.isNotBlank() &&
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
    private val version=1
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
            out.writeInt(magic);out.writeInt(version)
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
    private fun readDisk(key:String,athlete:String,exercise:String):
        NativePinnedExerciseNote {
        if(Files.exists(pending(key,exercise)))
            throw IOException("pending exercise note write")
        val path=file(key,exercise)
        if(!Files.exists(path))return fresh(key,athlete,exercise)
        if(Files.isSymbolicLink(root)||Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path)||Files.size(path) !in 40L..maxBytes.toLong())
            throw IOException("invalid exercise note file")
        val all=Files.readAllBytes(path)
        return DataInputStream(ByteArrayInputStream(all)).use { input ->
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
                if(data.readInt()!=magic||data.readInt()!=version)
                    throw IOException("unsupported note version")
                val storedKey=data.readUTF()
                val storedAthlete=data.readUTF()
                val storedExercise=data.readUTF()
                val revision=data.readLong()
                val event=if(data.readBoolean())data.readUTF() else null
                val text=data.readUTF()
                val count=data.readInt()
                if(count !in 0..NativePinnedExerciseNotePolicy.setupKeys.size)
                    throw IOException("invalid note setup count")
                val setup=LinkedHashMap<String,String>()
                repeat(count) {
                    val field=data.readUTF()
                    val value=data.readUTF()
                    if(setup.put(field,value)!=null)
                        throw IOException("duplicate setup field")
                }
                if(data.available()!=0)throw IOException("trailing note bytes")
                NativePinnedExerciseNote(storedKey,storedAthlete,storedExercise,
                    revision,text,Collections.unmodifiableMap(setup),event).also{
                    if(!valid(it)||it.ownerKey!=key||it.athleteId!=athlete||
                        it.exerciseId!=exercise)
                        throw IOException("note owner or content mismatch")
                }
            }
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
                val current=owner(key) ?:return@withOwnerTransaction
                    NativePinnedNoteRead.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                try {NativePinnedNoteRead.Current(
                    readDisk(key,current.athleteId,exercise))}
                catch(_:Exception){NativePinnedNoteRead.Rejected(
                    NativePinnedNoteRefusal.CORRUPT_OR_PENDING)}
            }
        }.getOrElse{NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)}
    }

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
                val current=owner(key) ?:return@withOwnerTransaction
                    NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                val previous=try{readDisk(key,current.athleteId,exercise)}
                    catch(_:Exception){return@withOwnerTransaction
                        NativePinnedNoteWrite.Rejected(
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
                if(owner(key)!=current)return@withOwnerTransaction
                    NativePinnedNoteWrite.Rejected(NativePinnedNoteRefusal.NOT_AUTHORIZED)
                atomic(key,exercise,updated)
                NativePinnedNoteWrite.Saved(updated,false)
            }
        }.getOrElse {NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)}
    }
}
