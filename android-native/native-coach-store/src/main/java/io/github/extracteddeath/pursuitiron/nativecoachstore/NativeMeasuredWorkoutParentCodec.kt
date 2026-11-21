package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Typed parent envelope for all measured sets in ONE approved workout.
 * The already-reviewed single-set codec owns quantities, physical stock,
 * side assignments and correction receipts. This parent only owns workout
 * scope, global ordering and a proposed Finish. Neither codec grants History.
 *
 * All archives are replay-validated on EVERY cold open. SHA-256 detects
 * accidental file corruption, not hostile modification or authentication.
 */
internal object NativeMeasuredWorkoutParentCodec {
    private const val MAGIC=0x50494D50 // PIMP, never PIJ, PICJ or PIM1
    private const val VERSION=1
    const val MAX_BYTES=3_000_000
    private const val MAX_SETS=64
    private const val MAX_RECEIPTS=MeasuredWorkoutJournalDomain.MAX_RECEIPTS
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun encode(stored:NativeMeasuredWorkoutStored):ByteArray {
        val journal=stored.journal
        require(listOf(stored.ownerKey,stored.athleteId,
            stored.approvalId).all(id::matches) &&
            stored.approvalRevision>=1)
        require(MeasuredWorkoutJournalDomain.replay(
            journal.approvedPlan,journal.receipts)==journal)
        require(journal.approvedPlan.sets.size in 1..MAX_SETS &&
            journal.receipts.size<=MAX_RECEIPTS)
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use {out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            out.writeUTF(stored.ownerKey);out.writeUTF(stored.athleteId)
            out.writeUTF(stored.approvalId)
            out.writeLong(stored.approvalRevision)
            out.writeInt(journal.approvedPlan.sets.size)
            journal.approvedPlan.sets.forEach{plan->
                val child=journal.bySetKey.getValue(plan.setKey)
                require(child.finishedByEventId==null)
                val bytes=NativeMeasuredExerciseJournalCodec.encode(
                    stored.ownerKey,stored.athleteId,child)
                out.writeInt(bytes.size);out.write(bytes)
            }
            out.writeLong(journal.revision)
            out.writeInt(journal.receipts.size)
            journal.receipts.forEach{receipt->
                out.writeLong(receipt.revision)
                when(val op=receipt.operation) {
                    is MeasuredWorkoutOperation.Record -> {
                        out.writeByte(1);out.writeUTF(op.eventId)
                        out.writeLong(op.expectedRevision)
                        out.writeUTF(op.setKey)
                    }
                    is MeasuredWorkoutOperation.Finish -> {
                        // Only the owner-fenced parent store's pending-ledger
                        // replay path can construct and write this receipt.
                        out.writeByte(2);out.writeUTF(op.eventId)
                        out.writeLong(op.expectedRevision)
                    }
                }
            }
        }
        val payload=body.toByteArray()
        require(payload.size+36<=MAX_BYTES)
        return ByteArrayOutputStream().also{buf->
            DataOutputStream(buf).use{out->
                out.writeInt(payload.size);out.write(payload)
                out.write(MessageDigest.getInstance("SHA-256").digest(payload))
            }
        }.toByteArray()
    }


    /**
     * Read only a bounded identity hint for an on-disk parent. This verifies
     * the full envelope hash before returning either name. It is NEVER an
     * authentication check; callers must fully replay/decode the archive,
     * verify its expected hashed filename and recheck signed-in ownership.
     */
    fun storedIdentity(bytes:ByteArray):Pair<String,String> {
        if(bytes.size !in 44..MAX_BYTES)
            throw IOException("invalid measured archive size")
        DataInputStream(ByteArrayInputStream(bytes)).use{envelope->
            val length=envelope.readInt()
            if(length !in 16..MAX_BYTES-36 || length+36!=bytes.size)
                throw IOException("invalid measured archive envelope")
            val body=ByteArray(length)
            envelope.readFully(body)
            val checksum=ByteArray(32)
            envelope.readFully(checksum)
            if(!MessageDigest.isEqual(checksum,
                    MessageDigest.getInstance("SHA-256").digest(body)))
                throw IOException("invalid measured archive checksum")
            DataInputStream(ByteArrayInputStream(body)).use{input->
                if(input.readInt()!=MAGIC || input.readInt()!=VERSION)
                    throw IOException("invalid measured archive schema")
                val owner=input.readUTF()
                val athlete=input.readUTF()
                if(!id.matches(owner) || !id.matches(athlete))
                    throw IOException("invalid measured archive identity")
                return owner to athlete
            }
        }
    }

    fun decode(
        bytes:ByteArray,expectedOwner:String,expectedAthlete:String
    ):NativeMeasuredWorkoutStored {
        if(!id.matches(expectedOwner) || !id.matches(expectedAthlete) ||
            bytes.size !in 44..MAX_BYTES)
            throw IOException("invalid measured parent identity or length")
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use{envelope->
                val size=envelope.readInt()
                if(size !in 16..MAX_BYTES-36 || size+36!=bytes.size)
                    throw IOException("measured parent envelope length mismatch")
                val body=ByteArray(size);envelope.readFully(body)
                val hash=ByteArray(32);envelope.readFully(hash)
                if(!MessageDigest.isEqual(hash,
                    MessageDigest.getInstance("SHA-256").digest(body)))
                    throw IOException("measured parent checksum mismatch")
                DataInputStream(ByteArrayInputStream(body)).use{input->
                    if(input.readInt()!=MAGIC || input.readInt()!=VERSION)
                        throw IOException("unsupported measured parent schema")
                    if(input.readUTF()!=expectedOwner ||
                        input.readUTF()!=expectedAthlete)
                        throw IOException("wrong measured parent account")
                    val approvalId=input.readUTF()
                    val approvalRevision=input.readLong()
                    if(!id.matches(approvalId) || approvalRevision<1)
                        throw IOException("invalid measured parent approval")
                    val count=input.readInt()
                    if(count !in 1..MAX_SETS)
                        throw IOException("invalid measured parent set count")
                    val children=List(count) {
                        val size=input.readInt()
                        if(size !in 44..NativeMeasuredExerciseJournalCodec.MAX_BYTES ||
                            size>input.available())
                            throw IOException("invalid child archive length")
                        val childBytes=ByteArray(size);input.readFully(childBytes)
                        NativeMeasuredExerciseJournalCodec.decode(
                            childBytes,expectedOwner,expectedAthlete)
                    }
                    if(children.any{it.finishedByEventId!=null})
                        throw IOException("measured child cannot finish independently")
                    val plans=children.map{it.immutablePlan}
                    val first=plans.first()
                    val bundle=MeasuredWorkoutPlan(first.workoutId,
                        first.gymId,first.gymRevision,plans)
                    val original=MeasuredWorkoutJournalDomain.begin(bundle)
                        ?:throw IOException("conflicting measured parent sources")
                    val events=children.flatMap{child->
                        child.receipts.map{receipt->
                            val record=receipt.operation
                                as? MeasuredJournalOperation.Record
                                ?:throw IOException("unexpected child Finish")
                            (child.immutablePlan.setKey to record.eventId) to record
                        }
                    }.toMap()
                    if(events.size!=children.sumOf{it.receipts.size})
                        throw IOException("duplicate event ID across measured sets")
                    val revision=input.readLong()
                    val receiptCount=input.readInt()
                    if(receiptCount !in 0..MAX_RECEIPTS ||
                        revision!=receiptCount.toLong())
                        throw IOException("invalid parent receipt count")
                    val receipts=List(receiptCount) {
                        val applied=input.readLong()
                        val kind=input.readUnsignedByte()
                        val eventId=input.readUTF()
                        val expected=input.readLong()
                        val operation=when(kind) {
                            1 -> {
                                val setKey=input.readUTF()
                                val recorded=events[setKey to eventId]
                                    ?:throw IOException("parent references unknown child receipt")
                                MeasuredWorkoutOperation.Record(eventId,
                                    expected,setKey,recorded.outcome,
                                    recorded.actual,recorded.correctsEventId)
                            }
                            2 -> MeasuredWorkoutOperation.Finish(eventId,expected)
                            else -> throw IOException("unknown measured parent operation")
                        }
                        MeasuredWorkoutReceipt(applied,operation)
                    }
                    if(input.available()!=0)
                        throw IOException("trailing measured parent data")
                    val recovered=MeasuredWorkoutJournalDomain.replay(bundle,
                        receipts) ?:throw IOException("measured parent replay failed")
                    if(recovered.revision!=revision ||
                        recovered.bySetKey!=original.bySetKey+
                            children.associate{
                                it.immutablePlan.setKey to it
                            })
                        throw IOException("measured child/parent receipts diverged")
                    return NativeMeasuredWorkoutStored(expectedOwner,
                        expectedAthlete,approvalId,approvalRevision,recovered)
                }
            }
        } catch(ex:Exception) {
            if(ex is IOException)throw ex
            throw IOException("unreadable measured parent archive",ex)
        }
    }
}
