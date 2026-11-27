package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.DataInputStream
import java.io.DataOutputStream

/** A compact common receipt payload, replayed by the existing journal reducer.
 * The encoded request is not a replacement snapshot or a permission grant. */
internal object NativeMidSessionWorkingEditCodec {
    fun write(out:DataOutputStream,request:MidSessionWorkingEdit,withMethod:Boolean=false) {
        require(withMethod || request.progressionMethod==null)
        out.writeUTF(request.workoutId);out.writeLong(request.expectedRevision)
        out.writeUTF(request.occurrenceId);out.writeInt(request.workingSetCount)
        val reps=request.reps
        out.writeBoolean(reps!=null)
        if(reps!=null){out.writeInt(reps.minimum);out.writeInt(reps.maximum)}
        val rest=request.restSeconds
        out.writeBoolean(rest!=null)
        if(rest!=null)out.writeInt(rest)
        if(withMethod) {
            val method=request.progressionMethod
            out.writeBoolean(method!=null)
            if(method!=null)out.writeUTF(method.name)
        }
    }
    fun read(input:DataInputStream,withMethod:Boolean=false):MidSessionWorkingEdit {
        val workout=input.readUTF();val revision=input.readLong()
        val occurrence=input.readUTF();val count=input.readInt()
        val reps=if(input.readBoolean())RepBand(input.readInt(),input.readInt()) else null
        val rest=if(input.readBoolean())input.readInt() else null
        val method=if(withMethod && input.readBoolean()) {
            val name=input.readUTF()
            SavedProgressionMethod.entries.singleOrNull{it.name==name}
                ?:throw java.io.IOException("invalid session progression method")
        } else null
        return MidSessionWorkingEdit(workout,revision,occurrence,count,reps,rest,method)
    }
}
