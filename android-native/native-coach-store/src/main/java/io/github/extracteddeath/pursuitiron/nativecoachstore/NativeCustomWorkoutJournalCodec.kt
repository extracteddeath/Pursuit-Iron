package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal

/**
 * Versioned transport for complete custom workout targets and FACTUAL main
 * and amplifier receipt events. No target values are substituted for actual
 * reps, weights or RIR. Every decode replays each event using the same pure
 * Kotlin reducer; altered order/revisions/finish/visit state fail closed.
 *
 * This codec does not own authentication, issuance, journaling, filesystem
 * atomicity or history. The future native custom journal store must checksum
 * and owner-fence the encoded bytes before acknowledging an observation.
 */
internal data class NativeCustomWorkoutJournalRecord(
    val ownerKey:String,
    val journal:CustomWorkoutJournalDraft
)

internal object NativeCustomWorkoutJournalCodec {
    private const val MAGIC=0x5049434A // PICJ
    private const val VERSION=2
    const val MAX_BYTES=2_000_000
    private const val MAX_RECEIPTS=2048
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun DataOutputStream.textOptional(value:String?) {
        writeBoolean(value!=null)
        if(value!=null)writeUTF(value)
    }
    private fun DataInputStream.textOptional():String?=
        if(readBoolean())readUTF() else null
    private fun DataOutputStream.intOptional(value:Int?) {
        writeBoolean(value!=null)
        if(value!=null)writeInt(value)
    }
    private fun DataInputStream.intOptional():Int?=
        if(readBoolean())readInt() else null

    private fun DataOutputStream.performed(value:KotlinPerformedSet?) {
        writeBoolean(value!=null)
        if(value==null)return
        writeBoolean(value.load!=null)
        value.load?.let {writeUTF(it.toPlainString())}
        writeBoolean(value.unit!=null)
        value.unit?.let{writeUTF(it.name)}
        writeInt(value.reps)
        intOptional(value.actualRir)
    }
    private fun DataInputStream.performed():KotlinPerformedSet? {
        if(!readBoolean())return null
        val weight=if(readBoolean()) BigDecimal(readUTF()) else null
        val unit=if(readBoolean()){
            val raw=readUTF()
            LoadUnit.entries.singleOrNull{it.name==raw}
                ?:throw IOException("unknown actual load unit")
        }else null
        return KotlinPerformedSet(weight,unit,readInt(),intOptional())
    }
    private fun DataOutputStream.operation(event:CustomWorkoutJournalOperation) {
        when(event) {
            is CustomWorkoutJournalOperation.Record->{
                writeByte(1)
                writeUTF(event.eventId)
                writeLong(event.expectedRevision)
                writeUTF(event.visit.setKey)
                writeUTF(event.visit.part.name)
                writeUTF(event.outcome.name)
                performed(event.performed)
                textOptional(event.correctsEventId)
            }
            is CustomWorkoutJournalOperation.Finish->{
                writeByte(2)
                writeUTF(event.eventId)
                writeLong(event.expectedRevision)
            }
            is CustomWorkoutJournalOperation.Swap->{
                writeByte(3)
                writeUTF(event.eventId)
                writeLong(event.expectedRevision)
                writeUTF(event.previousSlotId)
                val slot=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(
                    event.replacement)
                writeInt(slot.size)
                write(slot)
            }
        }
    }
    private fun DataInputStream.operation():CustomWorkoutJournalOperation =
        when(readUnsignedByte()){
            1->{
                val eventId=readUTF()
                val rev=readLong()
                val setKey=readUTF()
                val partName=readUTF()
                val part=CustomWorkoutVisitPart.entries.singleOrNull{
                    it.name==partName
                }?:throw IOException("invalid workout visit part")
                val outcomeName=readUTF()
                val outcome=KotlinJournalSetOutcome.entries.singleOrNull{
                    it.name==outcomeName
                }?:throw IOException("invalid workout set outcome")
                val observed=performed()
                val corrected=textOptional()
                CustomWorkoutJournalOperation.Record(eventId,rev,
                    CustomWorkoutVisitKey(setKey,part),outcome,observed,corrected)
            }
            2->CustomWorkoutJournalOperation.Finish(readUTF(),readLong())
            3->{
                val eventId=readUTF()
                val revision=readLong()
                val previous=readUTF()
                val length=readInt()
                if(length !in 24..NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES ||
                    length>available())
                    throw IOException("invalid swap target bytes")
                val slot=ByteArray(length)
                readFully(slot)
                CustomWorkoutJournalOperation.Swap(eventId,revision,previous,
                    NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(slot))
            }
            else->throw IOException("invalid workout receipt kind")
        }

    /** Validate the COMPLETE event history, not just its cached map. */
    private fun replay(
        snapshot:CustomWorkoutDaySnapshot,
        workoutId:String,
        fingerprint:String,
        receipts:List<CustomWorkoutJournalReceipt>,
        claimedRevision:Long,
        claimedFinished:String?
    ):CustomWorkoutJournalDraft {
        var current=CustomWorkoutJournalDomain.begin(
            workoutId,snapshot,fingerprint)
        for((index,receipt) in receipts.withIndex()) {
            if(receipt.appliedRevision!=(index+1).toLong())
                throw IOException("out-of-order journal receipt revision")
            val result=CustomWorkoutJournalDomain.reduce(
                current,receipt.operation)
            if(result !is CustomWorkoutJournalDecision.Proposed ||
                result.replayed || result.draft.revision!=receipt.appliedRevision)
                throw IOException("invalid factual custom journal history")
            current=result.draft
        }
        if(current.revision!=claimedRevision ||
            current.finishedByEventId!=claimedFinished)
            throw IOException("journal revision or finish mismatch")
        return current
    }

    fun encode(record:NativeCustomWorkoutJournalRecord):ByteArray {
        require(id.matches(record.ownerKey))
        val state=record.journal
        require(id.matches(state.workoutId))
        require(state.receipts.size<=MAX_RECEIPTS)
        val replayed=replay(state.originalSnapshot,state.workoutId,
            state.preparedConsentFingerprint,state.receipts,
            state.revision,state.finishedByEventId)
        require(replayed==state) {
            "Journal effective state differs from receipt replay"
        }
        // Preserve byte-for-byte v1 encoding for all previously issued,
        // unswapped workouts. The history-manifest Finish digest includes
        // these bytes, so eagerly re-encoding old journals as v2 would break
        // otherwise valid historical receipt identities.
        val swapped=state.receipts.any{
            it.operation is CustomWorkoutJournalOperation.Swap
        }
        require(swapped || state.originalSnapshot==state.snapshot)
        val version=if(swapped) VERSION else 1
        val snapshot=NativeKotlinCustomWorkoutSnapshotCodec.encode(state.snapshot)
        val original=if(swapped)
            NativeKotlinCustomWorkoutSnapshotCodec.encode(state.originalSnapshot)
            else snapshot
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use{data->
            data.writeInt(MAGIC);data.writeInt(version)
            data.writeUTF(record.ownerKey)
            data.writeUTF(state.workoutId)
            data.writeUTF(state.preparedConsentFingerprint)
            data.writeInt(original.size);data.write(original)
            if(version>=2) {
                data.writeInt(snapshot.size);data.write(snapshot)
            }
            data.writeLong(state.revision)
            data.textOptional(state.finishedByEventId)
            data.writeInt(state.receipts.size)
            state.receipts.forEach{
                data.writeLong(it.appliedRevision)
                data.operation(it.operation)
            }
        }
        return out.toByteArray().also{
            require(it.size in 16..MAX_BYTES)
        }
    }

    fun decode(bytes:ByteArray):NativeCustomWorkoutJournalRecord {
        if(bytes.size !in 16..MAX_BYTES)throw IOException("invalid journal size")
        return try{
            DataInputStream(ByteArrayInputStream(bytes)).use{data->
                val header=data.readInt()
                val version=data.readInt()
                if(header!=MAGIC || version !in 1..VERSION)
                    throw IOException("unsupported custom journal format")
                val owner=data.readUTF()
                val workout=data.readUTF()
                val fingerprint=data.readUTF()
                if(!id.matches(owner) || !id.matches(workout))
                    throw IOException("invalid custom journal identity")
                val snapshotSize=data.readInt()
                if(snapshotSize !in 16..NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES ||
                    snapshotSize>data.available())
                    throw IOException("invalid journal target size")
                val targetBytes=ByteArray(snapshotSize)
                data.readFully(targetBytes)
                val original=NativeKotlinCustomWorkoutSnapshotCodec.decode(
                    targetBytes)
                val snapshot=if(version>=2) {
                    val size=data.readInt()
                    if(size !in 16..NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES ||
                        size>data.available())
                        throw IOException("invalid effective journal target")
                    val content=ByteArray(size)
                    data.readFully(content)
                    NativeKotlinCustomWorkoutSnapshotCodec.decode(content)
                } else original
                val revision=data.readLong()
                val finish=data.textOptional()
                val count=data.readInt()
                if(count !in 0..MAX_RECEIPTS || count>data.available()/9)
                    throw IOException("invalid custom journal receipt count")
                val receipts=List(count) {
                    CustomWorkoutJournalReceipt(data.readLong(),data.operation())
                }
                if(data.available()!=0)
                    throw IOException("trailing custom workout journal bytes")
                val result=replay(
                    original,workout,fingerprint,receipts,revision,finish)
                if(result.snapshot!=snapshot)
                    throw IOException("stored workout swap state differs from events")
                NativeCustomWorkoutJournalRecord(owner,result)
            }
        }catch(ex:Exception){
            throw IOException("invalid stored custom workout journal",ex)
        }
    }
}
