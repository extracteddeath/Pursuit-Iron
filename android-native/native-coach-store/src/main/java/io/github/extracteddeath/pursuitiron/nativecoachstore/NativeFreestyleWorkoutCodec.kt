package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Versioned actual-only journal. The same factual-set codec as custom
 * journals preserves decimal scale, absent RIR and physical machine ID.
 * Cached state is never serialized as evidence; cold open replays receipts. */
internal object NativeFreestyleWorkoutCodec {
    private const val MAGIC=0x50494657
    private const val VERSION=1
    const val MAX_BYTES=2_000_000
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun DataOutputStream.optional(value:String?) {
        writeBoolean(value!=null);if(value!=null)writeUTF(value)
    }
    private fun DataInputStream.optional():String?=if(readBoolean())readUTF() else null
    private fun DataOutputStream.strings(items:List<String>) {
        writeInt(items.size);items.forEach(::writeUTF)
    }
    private fun DataInputStream.strings(max:Int):List<String> {
        val size=readInt();require(size in 0..max)
        return List(size){readUTF()}
    }
    private fun DataOutputStream.operation(op:KotlinFreestyleOperation) {
        writeByte(when(op) {
            is KotlinFreestyleOperation.Add->1
            is KotlinFreestyleOperation.Reorder->2
            is KotlinFreestyleOperation.Record->3
            is KotlinFreestyleOperation.Finish->4
            is KotlinFreestyleOperation.CorrectSaved->5
        })
        writeUTF(op.eventId);writeLong(op.expectedRevision)
        when(op) {
            is KotlinFreestyleOperation.Add -> with(op.exercise) {
                writeUTF(slotId);writeUTF(exerciseId);writeUTF(familyId);writeUTF(variantId)
                optional(attachmentId);optional(unit?.name);optional(basis?.name)
                strings(routineSetTypes.map{it.name})
            }
            is KotlinFreestyleOperation.Reorder->strings(op.slotIds)
            is KotlinFreestyleOperation.Record->writeRecord(op)
            is KotlinFreestyleOperation.CorrectSaved->writeRecord(op.record)
            is KotlinFreestyleOperation.Finish->Unit
        }
    }
    private fun DataOutputStream.writeRecord(op:KotlinFreestyleOperation.Record) {
        writeUTF(op.slotId);writeUTF(op.setKey);writeUTF(op.type.name)
        writeUTF(op.outcome.name)
        NativeCustomWorkoutJournalCodec.writeActual(this,op.actual)
        optional(op.correctsEventId)
    }
    private fun DataInputStream.readRecord(event:String,revision:Long)=
        KotlinFreestyleOperation.Record(event,revision,readUTF(),readUTF(),
            DraftSetType.valueOf(readUTF()),KotlinJournalSetOutcome.valueOf(readUTF()),
            NativeCustomWorkoutJournalCodec.readActual(this),optional())

    private fun DataInputStream.operation():KotlinFreestyleOperation {
        val tag=readUnsignedByte();val event=readUTF();val revision=readLong()
        return when(tag) {
            1->KotlinFreestyleOperation.Add(event,revision,KotlinFreestyleExercise(
                readUTF(),readUTF(),readUTF(),readUTF(),optional(),
                optional()?.let{LoadUnit.valueOf(it)},optional()?.let{LoadBasis.valueOf(it)},
                strings(20).map{DraftSetType.valueOf(it)}))
            2->KotlinFreestyleOperation.Reorder(event,revision,strings(32))
            3->readRecord(event,revision)
            5->KotlinFreestyleOperation.CorrectSaved(readRecord(event,revision))
            4->KotlinFreestyleOperation.Finish(event,revision)
            else->throw IOException("unknown freestyle receipt")
        }
    }

    fun encode(owner:String,workout:KotlinFreestyleWorkout):ByteArray {
        require(id.matches(owner))
        require(KotlinFreestyleWorkoutDomain.replay(workout.workoutId,workout.source,workout.receipts)==workout)
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use{out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            out.writeUTF(owner);out.writeUTF(workout.workoutId)
            with(workout.source) {
                out.writeUTF(athleteId);out.writeUTF(gymId)
                out.writeUTF(profileRevision);out.writeLong(gymRevision)
            }
            out.writeInt(workout.receipts.size)
            workout.receipts.forEach{receipt->
                out.writeLong(receipt.revision);out.operation(receipt.operation)
            }
        }
        val payload=body.toByteArray()
        NativeWorkoutJournalByteBudget.requireFits(payload.size+36,MAX_BYTES,
            if(workout.finishedByEventId==null)0 else null)
        return ByteArrayOutputStream().also{frame->
            DataOutputStream(frame).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }

    fun decode(bytes:ByteArray,owner:String,athlete:String,workoutId:String?=null):KotlinFreestyleWorkout {
        require(bytes.size in 44..MAX_BYTES && id.matches(owner))
        val frame=DataInputStream(ByteArrayInputStream(bytes))
        val size=frame.readInt()
        require(size in 8..MAX_BYTES-36 && size+36==bytes.size)
        val payload=ByteArray(size);frame.readFully(payload)
        val hash=ByteArray(32);frame.readFully(hash)
        require(MessageDigest.isEqual(hash,MessageDigest.getInstance("SHA-256").digest(payload)))
        return DataInputStream(ByteArrayInputStream(payload)).use{input->
            require(input.readInt()==MAGIC && input.readInt()==VERSION)
            require(input.readUTF()==owner)
            val archivedWorkoutId=input.readUTF()
            require(id.matches(archivedWorkoutId) && (workoutId==null || workoutId==archivedWorkoutId))
            val source=KotlinFreestyleSource(input.readUTF(),input.readUTF(),
                input.readUTF(),input.readLong())
            require(source.athleteId==athlete)
            val count=input.readInt()
            require(count in 0..KotlinWorkoutJournalDomain.MAX_RECEIPTS)
            val receipts=List(count){KotlinFreestyleReceipt(input.readLong(),input.operation())}
            require(input.available()==0)
            checkNotNull(KotlinFreestyleWorkoutDomain.replay(archivedWorkoutId,source,receipts))
        }
    }
}
