package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

/** Saved routine shape only. One bounded, checksummed owner+athlete library.
 * No actuals, targets, historical receipt edits or gym approval are encoded. */
internal object NativeFreestyleRoutineCodec {
    private const val MAGIC=0x50494652
    private const val VERSION=1
    const val MAX_BYTES=2_000_000
    const val MAX_ROUTINES=128
    const val MAX_REVISION=9_007_199_254_740_991L
    private fun valid(owner:NativeKotlinOwnerIdentity,library:NativeFreestyleRoutineLibrary):Boolean =
        library.revision in 1..MAX_REVISION && library.routines.size<=MAX_ROUTINES &&
            library.routines.map{it.routineId}.distinct().size==library.routines.size &&
            library.routines.all{it.athleteId==owner.athleteId && KotlinFreestyleWorkoutDomain.validRoutine(it)}

    fun encode(owner:NativeKotlinOwnerIdentity,library:NativeFreestyleRoutineLibrary):ByteArray {
        require(valid(owner,library))
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use{out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            out.writeUTF(owner.ownerKey);out.writeUTF(owner.athleteId)
            out.writeLong(library.revision);out.writeInt(library.routines.size)
            library.routines.forEach{routine->
                out.writeUTF(routine.routineId);out.writeUTF(routine.name)
                out.writeInt(routine.slots.size)
                routine.slots.forEach{slot->
                    out.writeUTF(slot.exerciseId);out.writeInt(slot.setTypes.size)
                    slot.setTypes.forEach{out.writeUTF(it.name)}
                }
            }
        }
        val payload=body.toByteArray()
        require(payload.size+36<=MAX_BYTES)
        return ByteArrayOutputStream().also{frame->
            DataOutputStream(frame).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }

    fun decode(bytes:ByteArray,owner:NativeKotlinOwnerIdentity):NativeFreestyleRoutineLibrary {
        require(bytes.size in 44..MAX_BYTES)
        val frame=DataInputStream(ByteArrayInputStream(bytes))
        val size=frame.readInt()
        require(size in 8..MAX_BYTES-36 && size+36==bytes.size)
        val payload=ByteArray(size);frame.readFully(payload)
        val hash=ByteArray(32);frame.readFully(hash)
        require(MessageDigest.isEqual(hash,MessageDigest.getInstance("SHA-256").digest(payload)))
        return DataInputStream(ByteArrayInputStream(payload)).use{input->
            require(input.readInt()==MAGIC && input.readInt()==VERSION)
            require(input.readUTF()==owner.ownerKey && input.readUTF()==owner.athleteId)
            val revision=input.readLong();val count=input.readInt()
            require(count in 0..MAX_ROUTINES)
            val routines=List(count) {
                val id=input.readUTF();val name=input.readUTF();val slots=input.readInt()
                require(slots in 1..32)
                KotlinFreestyleRoutine(id,owner.athleteId,name,Collections.unmodifiableList(List(slots){
                    val exercise=input.readUTF();val sets=input.readInt()
                    require(sets in 1..20)
                    KotlinFreestyleRoutineSlot(exercise,Collections.unmodifiableList(List(sets){
                        DraftSetType.valueOf(input.readUTF())
                    }))
                }))
            }
            require(input.available()==0)
            NativeFreestyleRoutineLibrary(revision,Collections.unmodifiableList(routines)).also{
                require(valid(owner,it))
            }
        }
    }
}
