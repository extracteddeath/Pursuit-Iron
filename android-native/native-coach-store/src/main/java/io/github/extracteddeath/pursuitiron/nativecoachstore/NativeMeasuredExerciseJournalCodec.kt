package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * New, isolated v1 transport for source-typed hold/carry/banded-rep facts.
 * It NEVER modifies the frozen v1-v5 regular workout journals.
 *
 * This codec checks corruption, exact owner/athlete labels, logical source
 * consistency, all staged receipts and strict replay. It is NOT authentication
 * or a durable store: a native host must establish the current signed-in
 * owner, validate the issued program/gym and CAS-write the bytes atomically.
 * SHA-256 is not a MAC and cannot defend against a malicious file editor.
 */
internal object NativeMeasuredExerciseJournalCodec {
    private const val MAGIC=0x50494D31 // PIM1, disjoint from older PIJ/PICJ
    private const val VERSION=1
    const val MAX_BYTES=256_000
    private const val MAX_RECEIPTS=MeasuredExerciseJournalDomain.MAX_RECEIPTS
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun DataOutputStream.optionalText(value:String?) {
        writeBoolean(value!=null)
        if(value!=null)writeUTF(value)
    }
    private fun DataInputStream.optionalText():String? =
        if(readBoolean())readUTF() else null

    private fun DataOutputStream.optionalInt(value:Int?) {
        writeBoolean(value!=null)
        if(value!=null)writeInt(value)
    }
    private fun DataInputStream.optionalInt():Int? =
        if(readBoolean())readInt() else null

    private fun DataOutputStream.optionalLoad(value:BigDecimal?) {
        optionalText(value?.toPlainString())
    }
    private fun DataInputStream.optionalLoad():BigDecimal? =
        optionalText()?.let {
            try{BigDecimal(it)}catch(ex:NumberFormatException){
                throw IOException("invalid numeric measured load",ex)
            }
        }

    private fun DataOutputStream.stock(stock:VerifiedLoadStock?) {
        when(stock) {
            null->writeByte(0)
            is VerifiedLoadStock.Totals -> {
                writeByte(1)
                writeUTF(stock.unit.name);writeUTF(stock.basis.name)
                require(stock.loads.size in 1..512)
                writeInt(stock.loads.size)
                stock.loads.forEach{writeUTF(it.toPlainString())}
            }
            is VerifiedLoadStock.Stack -> {
                writeByte(2)
                writeUTF(stock.unit.name);writeUTF(stock.basis.name)
                writeUTF(stock.minimum.toPlainString())
                writeUTF(stock.increment.toPlainString())
                writeUTF(stock.maximum.toPlainString())
            }
            // None of the fourteen reviewed movements uses a paired bar.
            // Refuse unsupported physical inventory rather than emitting a
            // snapshot which could never be decoded and cold-recovered.
            is VerifiedLoadStock.Barbell,
            is VerifiedLoadStock.Smith ->
                throw IOException("paired bar stock is not a supported measured setup")
        }
    }
    private fun DataInputStream.loadUnit():LoadUnit {
        val name=readUTF()
        return LoadUnit.entries.singleOrNull{it.name==name}
            ?:throw IOException("unknown physical load unit")
    }

    private fun DataInputStream.loadBasis():LoadBasis {
        val value=readUTF()
        return LoadBasis.entries.singleOrNull{it.name==value}
            ?:throw IOException("unknown load basis")
    }

    private fun DataInputStream.mass():BigDecimal =
        try {BigDecimal(readUTF())}
        catch(ex:NumberFormatException){
            throw IOException("invalid physical mass",ex)
        }

    private fun DataInputStream.stock():VerifiedLoadStock? =
        when(readUnsignedByte()) {
            0->null
            1->{
                val unit=loadUnit()
                val basis=loadBasis()
                val count=readInt()
                if(count !in 1..512)throw IOException("too many stock values")
                VerifiedLoadStock.Totals(unit,basis,List(count){mass()})
            }
            2->{
                val unit=loadUnit()
                val basis=loadBasis()
                VerifiedLoadStock.Stack(unit,basis,mass(),mass(),mass())
            }
            3,4->{
                // This separate read uses a local kind so legacy journal
                // enums and numbered fields remain untouched.
                throw IOException("unverified paired bar data in measured hold/carry")
            }
            else->throw IOException("unknown physical stock encoding")
        }

    private fun DataOutputStream.stringSet(values:Set<String>) {
        require(values.size<=32)
        writeInt(values.size)
        values.sorted().forEach{writeUTF(it)}
    }
    private fun DataInputStream.stringSet():Set<String> {
        val count=readInt()
        if(count !in 0..32)throw IOException("oversized fixture/band roster")
        val result=(1..count).map{readUTF()}
        if(result.toSet().size!=count)throw IOException("duplicate fixture/band")
        return result.toSet()
    }
    private fun DataOutputStream.plan(p:MeasuredExerciseSetPlan) {
        require(MeasuredExerciseDomain.inspect(p) is MeasurementPlanResult.Candidate)
        writeUTF(p.workoutId);writeUTF(p.setKey);writeUTF(p.exerciseId)
        writeUTF(p.gymId);writeLong(p.gymRevision)
        writeUTF(p.physicalSetupId)
        writeInt(p.targetAmount)
        writeInt(p.sides.size)
        p.sides.sortedBy{it.ordinal}.forEach{writeUTF(it.name)}
        stringSet(p.confirmedFixtures)
        stock(p.physicalStock)
        optionalLoad(p.intendedExternalLoad)
        optionalText(p.unit?.name)
        writeBoolean(p.matchedPairConfirmed)
        optionalText(p.bandId)
        stringSet(p.gymConfirmedBandIds)
    }
    private fun DataInputStream.plan():MeasuredExerciseSetPlan {
        val workout=readUTF()
        val set=readUTF()
        val exercise=readUTF()
        val gym=readUTF()
        val revision=readLong()
        val setup=readUTF()
        val target=readInt()
        val sideCount=readInt()
        if(sideCount !in 1..2)throw IOException("invalid measured side count")
        val sides=(1..sideCount).map {
            val name=readUTF()
            MeasuredSide.entries.singleOrNull{it.name==name}
                ?:throw IOException("unknown measured side")
        }
        if(sides.toSet().size!=sides.size)
            throw IOException("duplicate measured side")
        val fixtures=stringSet()
        val stock=stock()
        val added=optionalLoad()
        val unitName=optionalText()
        val unit=unitName?.let{name->LoadUnit.entries.singleOrNull{
            it.name==name} ?:throw IOException("unknown plan unit")}
        val pair=readBoolean()
        val band=optionalText()
        val bands=stringSet()
        val p=MeasuredExerciseSetPlan(workout,set,exercise,gym,
            revision,setup,target,sides.toSet(),fixtures,stock,added,
            unit,pair,band,bands)
        if(MeasuredExerciseDomain.inspect(p) !is MeasurementPlanResult.Candidate)
            throw IOException("unverified measured set plan")
        return p
    }

    private fun DataOutputStream.actual(a:MeasuredExerciseActual?) {
        writeBoolean(a!=null)
        if(a==null)return
        writeUTF(a.physicalSetupId)
        require(a.sides.size in 1..2)
        writeInt(a.sides.size)
        a.sides.forEach{side->
            writeUTF(side.side.name);writeInt(side.amount)
        }
        optionalLoad(a.actualExternalLoad)
        optionalText(a.unit?.name)
        optionalText(a.bandId)
        optionalInt(a.elapsedSeconds)
    }
    private fun DataInputStream.actual():MeasuredExerciseActual? {
        if(!readBoolean())return null
        val setup=readUTF()
        val count=readInt()
        if(count !in 1..2)throw IOException("invalid actual-side count")
        val observed=List(count){
            val name=readUTF()
            val side=MeasuredSide.entries.singleOrNull{it.name==name}
                ?:throw IOException("unknown actual side")
            MeasuredSideResult(side,readInt())
        }
        val mass=optionalLoad()
        val unitName=optionalText()
        val unit=unitName?.let{name->LoadUnit.entries.singleOrNull{
            it.name==name} ?:throw IOException("unknown actual unit")}
        return MeasuredExerciseActual(setup,observed,mass,unit,
            optionalText(),optionalInt())
    }

    private fun DataOutputStream.operation(op:MeasuredJournalOperation) {
        writeUTF(op.eventId);writeLong(op.expectedRevision)
        when(op) {
            is MeasuredJournalOperation.Record -> {
                writeByte(1);writeUTF(op.outcome.name)
                actual(op.actual);optionalText(op.correctsEventId)
            }
            is MeasuredJournalOperation.Finish ->writeByte(2)
        }
    }
    private fun DataInputStream.operation():MeasuredJournalOperation {
        val name=readUTF()
        val revision=readLong()
        return when(readUnsignedByte()) {
            1->{
                val outcomeName=readUTF()
                val outcome=MeasuredSetOutcome.entries.singleOrNull{
                    it.name==outcomeName
                } ?:throw IOException("invalid measured outcome")
                MeasuredJournalOperation.Record(name,revision,outcome,
                    actual(),optionalText())
            }
            2->MeasuredJournalOperation.Finish(name,revision)
            else->throw IOException("unknown measured receipt type")
        }
    }

    fun encode(
        ownerKey:String,
        athleteId:String,
        journal:MeasuredExerciseJournal
    ):ByteArray {
        require(id.matches(ownerKey) && id.matches(athleteId))
        require(journal.receipts.size<=MAX_RECEIPTS &&
            journal.revision==journal.receipts.size.toLong())
        require(MeasuredExerciseJournalDomain.replay(
            journal.immutablePlan,journal.receipts)==journal)
        val body=ByteArrayOutputStream()
        DataOutputStream(body).use{out->
            out.writeInt(MAGIC);out.writeInt(VERSION)
            out.writeUTF(ownerKey);out.writeUTF(athleteId)
            out.plan(journal.immutablePlan)
            out.writeLong(journal.revision)
            out.optionalText(journal.finishedByEventId)
            out.writeInt(journal.receipts.size)
            journal.receipts.forEach{
                out.writeLong(it.revision);out.operation(it.operation)
            }
        }
        val raw=body.toByteArray()
        require(raw.size+36<=MAX_BYTES)
        return ByteArrayOutputStream().also {buf->
            DataOutputStream(buf).use{out->
                out.writeInt(raw.size)
                out.write(raw)
                out.write(MessageDigest.getInstance("SHA-256").digest(raw))
            }
        }.toByteArray()
    }

    /** Exact expected owner and athlete MUST come from the external native
     * identity authority; contents alone never authenticate a user. */
    fun decode(
        bytes:ByteArray,
        expectedOwnerKey:String,
        expectedAthleteId:String
    ):MeasuredExerciseJournal {
        if(!id.matches(expectedOwnerKey) || !id.matches(expectedAthleteId) ||
            bytes.size !in 44..MAX_BYTES)
            throw IOException("invalid measured archive identity/length")
        val envelope=DataInputStream(ByteArrayInputStream(bytes))
        val size=envelope.readInt()
        if(size !in 8..(MAX_BYTES-36) || size+36!=bytes.size)
            throw IOException("invalid measured archive size")
        val payload=ByteArray(size)
        envelope.readFully(payload)
        val digest=ByteArray(32)
        envelope.readFully(digest)
        if(!MessageDigest.isEqual(digest,
                MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("measured archive checksum mismatch")
        try {
            DataInputStream(ByteArrayInputStream(payload)).use{input->
                if(input.readInt()!=MAGIC || input.readInt()!=VERSION)
                    throw IOException("unknown measured archive version")
                if(input.readUTF()!=expectedOwnerKey ||
                    input.readUTF()!=expectedAthleteId)
                    throw IOException("wrong measured athlete/owner")
                val p=input.plan()
                val revision=input.readLong()
                val finish=input.optionalText()
                val count=input.readInt()
                if(count !in 0..MAX_RECEIPTS || revision!=count.toLong())
                    throw IOException("invalid measured receipt count")
                val receipts=List(count){
                    MeasuredJournalReceipt(input.readLong(),input.operation())
                }
                if(input.available()!=0)
                    throw IOException("trailing measured journal data")
                val recovered=MeasuredExerciseJournalDomain.replay(p,receipts)
                    ?:throw IOException("measured receipts cannot be replayed")
                if(recovered.revision!=revision ||
                    recovered.finishedByEventId!=finish)
                    throw IOException("unverified measured journal state")
                return recovered
            }
        } catch(ex:Exception) {
            if(ex is IOException)throw ex
            throw IOException("unreadable typed measured archive",ex)
        }
    }
}
