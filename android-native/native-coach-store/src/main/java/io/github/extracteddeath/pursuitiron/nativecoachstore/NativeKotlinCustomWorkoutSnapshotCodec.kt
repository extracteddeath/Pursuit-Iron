package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Collections

/**
 * Versioned, SELF-CONTAINED custom session snapshot. Unlike the Composer
 * journal codec, this preserves authored slot identity, exact individual
 * sets, full advanced-set follow-ups, variant IDs and all week provenance.
 *
 * Transport and cold-restore only. It is NOT a workout issuance grant and
 * must never be decoded directly into an executable journal without a
 * current owner/consent/history transaction.
 */
internal object NativeKotlinCustomWorkoutSnapshotCodec {
    private const val MAGIC=0x50494357 // PICW
    private const val VERSION=3
    private const val SLOT_V3_MAGIC=0x50495333 // PIS3
    const val MAX_BYTES=256_000
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val labelLimit=2000
    private const val MAX_TARGETS=256
    private const val MAX_SLOTS=32
    private val maxRevision=9_007_199_254_740_991L
    private val zero=BigDecimal.ZERO
    private val maxWeight=BigDecimal("100000")

    private inline fun <reified T:Enum<T>> DataInputStream.kind():T {
        val raw=readUTF()
        return enumValues<T>().firstOrNull{it.name==raw}
            ?:throw IOException("unknown custom workout enum")
    }
    private fun DataOutputStream.kind(value:Enum<*>)=writeUTF(value.name)
    private fun DataOutputStream.decimal(value:BigDecimal)=
        writeUTF(value.toPlainString())
    private fun DataInputStream.decimal()=BigDecimal(readUTF())
    private fun DataOutputStream.optional(value:String?) {
        writeBoolean(value!=null)
        if(value!=null)writeUTF(value)
    }
    private fun DataInputStream.optional():String?=
        if(readBoolean())readUTF() else null
    private fun DataOutputStream.optionalInt(value:Int?) {
        writeBoolean(value!=null)
        if(value!=null)writeInt(value)
    }
    private fun DataInputStream.optionalInt():Int?=
        if(readBoolean())readInt() else null
    private fun DataOutputStream.load(value:DraftSetLoad) {
        when(value) {
            DraftSetLoad.ExplicitBodyweight->writeByte(0)
            is DraftSetLoad.Verified -> {
                writeByte(1)
                kind(value.unit)
                kind(value.basis)
                decimal(value.value)
            }
        }
    }
    private fun DataInputStream.load():DraftSetLoad=when(readUnsignedByte()) {
        0->DraftSetLoad.ExplicitBodyweight
        1->DraftSetLoad.Verified(kind(),kind(),decimal())
        else->throw IOException("unknown authored load")
    }
    private fun DataOutputStream.authored(set:CustomAuthoredSet,version:Int) {
        kind(set.type)
        load(set.load)
        writeInt(set.reps.minimum)
        writeInt(set.reps.maximum)
        optionalInt(set.targetRir)
        writeInt(set.activeSeconds)
        writeInt(set.restSeconds)
        writeBoolean(set.amplifier!=null)
        set.amplifier?.let{ amp ->
            kind(amp.kind)
            writeInt(amp.followUpReps)
            writeInt(amp.followUpRestSeconds)
            writeBoolean(amp.followUpLoad!=null)
            amp.followUpLoad?.let { load(it) }
            if(version>=3)optionalInt(amp.followUpActiveSeconds)
        }
    }
    private fun DataInputStream.authored(version:Int):CustomAuthoredSet {
        val type=kind<DraftSetType>()
        val load=load()
        val reps=RepBand(readInt(),readInt())
        val rir=optionalInt()
        val active=readInt()
        val rest=readInt()
        val amp=if(readBoolean()) {
            val type=kind<CustomAmplifierKind>()
            val reps=readInt()
            val rest=readInt()
            val follow=if(readBoolean())load() else null
            CustomSetAmplifier(type,reps,rest,follow,
                if(version>=3)optionalInt() else null)
        } else null
        return CustomAuthoredSet(type,load,reps,rir,active,rest,amp)
    }
    private fun DataOutputStream.slot(slot:CustomWorkoutSlotSnapshot,version:Int) {
        writeUTF(slot.slotId)
        writeUTF(slot.exerciseId)
        writeUTF(slot.familyId)
        writeUTF(slot.variantId)
        kind(slot.role)
        optional(slot.attachmentId)
        optional(slot.supersetGroup)
        writeBoolean(slot.pinned)
        writeUTF(slot.coachNote)
        writeInt(slot.sets.size)
        slot.sets.forEach {set->
            writeUTF(set.setKey)
            writeUTF(set.slotId)
            writeInt(set.ordinalInSlot)
            authored(set.prescription,version)
        }
    }
    private fun DataInputStream.slot(version:Int):CustomWorkoutSlotSnapshot {
        val slotId=readUTF()
        val exerciseId=readUTF()
        val familyId=readUTF()
        val variantId=readUTF()
        val role=kind<ComposerRole>()
        val attachment=optional()
        val group=optional()
        val pinned=readBoolean()
        val note=readUTF()
        val count=readInt()
        if(count !in 1..20)throw IOException("invalid swap slot set count")
        val sets=List(count) {
            CustomWorkoutSetSnapshot(readUTF(),readUTF(),readInt(),authored(version))
        }
        return CustomWorkoutSlotSnapshot(slotId,exerciseId,familyId,
            variantId,role,attachment,group,pinned,note,
            Collections.unmodifiableList(sets))
    }
    /** Legacy journal Swap payloads remain unframed. Timed amplifiers use
     * an explicit v3 frame for exact ACTIVE follow-up seconds on cold replay. */
    internal fun encodeSlot(value:CustomWorkoutSlotSnapshot):ByteArray =
        ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use { out->
                val timed=value.sets.any {
                    it.prescription.amplifier?.followUpActiveSeconds!=null
                }
                if(timed){out.writeInt(SLOT_V3_MAGIC);out.writeInt(VERSION)}
                out.slot(value,if(timed)VERSION else 1)
            }
        }.toByteArray().also { require(it.size in 24..MAX_BYTES) }
    internal fun decodeSlot(bytes:ByteArray):CustomWorkoutSlotSnapshot {
        if(bytes.size !in 24..MAX_BYTES)throw IOException("invalid swap slot size")
        return DataInputStream(ByteArrayInputStream(bytes)).use { input->
            input.mark(8)
            val framed=input.readInt()==SLOT_V3_MAGIC
            val version=if(framed)input.readInt() else {
                input.reset();1
            }
            if(version !in listOf(1,VERSION))
                throw IOException("unsupported swap slot version")
            val slot=input.slot(version)
            if(input.available()!=0)throw IOException("trailing swap slot bytes")
            slot
        }
    }

    private fun DataOutputStream.split(split:CustomSplitIdentity) {
        when(split) {
            is CustomSplitIdentity.Named -> {
                writeByte(0);kind(split.split)
            }
            is CustomSplitIdentity.Freeform -> {
                writeByte(1);writeUTF(split.label)
            }
        }
    }
    private fun DataInputStream.split():CustomSplitIdentity=when(readUnsignedByte()) {
        0->CustomSplitIdentity.Named(kind<ComposerSplit>())
        1->CustomSplitIdentity.Freeform(readUTF())
        else->throw IOException("unknown custom split")
    }

    private fun validate(snapshot:CustomWorkoutDaySnapshot) {
        val stamp=snapshot.stamp
        require(listOf(stamp.athleteId,stamp.cycleId,stamp.gymId,
            snapshot.blockId).all(id::matches))
        require(stamp.athleteProfileRevision.isNotBlank() &&
            stamp.athleteProfileRevision.length<=80)
        require(stamp.cycleRevision in 1..maxRevision &&
            stamp.gymCatalogSourceRevision in 1..maxRevision)
        require(snapshot.absoluteWeek in 1..104 &&
            snapshot.weekInBlock in 1..52 &&
            snapshot.weekday in 0..6)
        require(snapshot.dayLabel.isNotBlank() &&
            snapshot.dayLabel.length<=120)
        require(snapshot.slots.isNotEmpty() &&
            snapshot.slots.size<=MAX_SLOTS)
        require(snapshot.slots.map{it.slotId}.distinct().size==
            snapshot.slots.size)
        require(snapshot.slots.sumOf{it.sets.size} in 1..MAX_TARGETS)
        val limit=snapshot.shortSessionLimitMinutes
        val omitted=snapshot.shortSessionOmittedSetKeys
        require(limit==null && omitted.isEmpty() ||
            limit in setOf(20,30,45) &&
            omitted.size+snapshot.slots.sumOf{it.sets.size}<=MAX_TARGETS &&
            omitted.distinct().size==omitted.size &&
            omitted.all{ key ->
                key.length in 16..256 &&
                key.startsWith(
                    "${snapshot.stamp.cycleId}:w${snapshot.absoluteWeek}:d${snapshot.weekday}:") &&
                Regex("^[A-Za-z0-9_:.-]{16,256}$").matches(key)
            } &&
            omitted.none{key ->snapshot.slots.any{slot ->
                slot.sets.any{it.setKey==key}
            }})
        require(snapshot.knownSetAndRestSeconds in 1..10_000_000L)
        require(snapshot.declaredNonSetSeconds?.let { it in 0..21600 } != false)
        (snapshot.split as? CustomSplitIdentity.Freeform)?.let { split ->
            require(split.label.isNotBlank() && split.label.length<=80)
        }
        var duration=0L
        val seen=mutableSetOf<String>()
        for(slot in snapshot.slots) {
            require(listOf(slot.slotId,slot.exerciseId,slot.familyId,
                slot.variantId).all(id::matches))
            require(slot.attachmentId?.let { id.matches(it) } != false)
            require(slot.supersetGroup?.let { id.matches(it) } != false)
            require(slot.coachNote.length<=1000)
            require(slot.sets.isNotEmpty() && slot.sets.size<=20)
            for((index,set) in slot.sets.withIndex()) {
                require(set.slotId==slot.slotId)
                require(set.ordinalInSlot==index+1)
                require(set.setKey=="${stamp.cycleId}:w${snapshot.absoluteWeek}:d${snapshot.weekday}:${slot.slotId}:s${index+1}")
                require(set.setKey.length<=256 && seen.add(set.setKey))
                val p=set.prescription
                require(p.reps.minimum in 1..100 &&
                    p.reps.maximum in p.reps.minimum..100)
                require(p.targetRir?.let { it in 0..10 } != false)
                require(p.activeSeconds in 1..3600 &&
                    p.restSeconds in 0..3600)
                require(p.type!=DraftSetType.WARMUP || p.amplifier==null)
                fun checkLoad(load:DraftSetLoad) {
                    if(load is DraftSetLoad.Verified)require(
                        load.value>zero && load.value<=maxWeight &&
                        load.value.stripTrailingZeros().scale()<=3)
                }
                checkLoad(p.load)
                p.amplifier?.let{ amp ->
                    require(amp.followUpReps in 1..100 &&
                        amp.followUpRestSeconds in 0..3600 &&
                        (amp.followUpActiveSeconds==null ||
                            amp.followUpActiveSeconds in 1..3600))
                    amp.followUpLoad?.let(::checkLoad)
                    if(amp.kind==CustomAmplifierKind.DROP_SET) {
                        val original=p.load as? DraftSetLoad.Verified
                        val next=amp.followUpLoad as? DraftSetLoad.Verified
                        require(original!=null && next!=null &&
                            original.unit==next.unit &&
                            original.basis==next.basis &&
                            next.value<original.value)
                    }
                }
                duration+=p.activeSeconds+p.restSeconds+
                    (p.amplifier?.followUpRestSeconds?:0)+
                    (p.amplifier?.followUpActiveSeconds?:0)
            }
        }
        require(duration==snapshot.knownSetAndRestSeconds)
        for(group in snapshot.slots.mapNotNull {it.supersetGroup}.distinct()) {
            require(snapshot.slots.count{it.supersetGroup==group} in 2..3)
        }
    }
    fun encode(snapshot:CustomWorkoutDaySnapshot):ByteArray {
        validate(snapshot)
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use{out->
            val version=if(snapshot.slots.any{slot->slot.sets.any{
                    it.prescription.amplifier?.followUpActiveSeconds!=null
                }})VERSION else if(snapshot.shortSessionLimitMinutes!=null)2 else 1
            out.writeInt(MAGIC);out.writeInt(version)
            val stamp=snapshot.stamp
            out.writeUTF(stamp.athleteId)
            out.writeUTF(stamp.athleteProfileRevision)
            out.writeUTF(stamp.cycleId)
            out.writeLong(stamp.cycleRevision)
            out.writeUTF(stamp.gymId)
            out.writeLong(stamp.gymCatalogSourceRevision)
            out.writeInt(snapshot.absoluteWeek)
            out.writeInt(snapshot.weekInBlock)
            out.writeUTF(snapshot.blockId)
            out.kind(snapshot.phase)
            out.kind(snapshot.blockPurpose)
            out.split(snapshot.split)
            out.writeInt(snapshot.weekday)
            out.writeUTF(snapshot.dayLabel)
            out.kind(snapshot.sessionPurpose)
            out.writeLong(snapshot.knownSetAndRestSeconds)
            out.optionalInt(snapshot.declaredNonSetSeconds)
            out.writeInt(snapshot.slots.size)
            snapshot.slots.forEach {out.slot(it,version)}
            // Keep every historical full custom snapshot byte-identical.
            if(version>=2) {
                if(version>=3)out.optionalInt(snapshot.shortSessionLimitMinutes)
                else out.writeInt(checkNotNull(snapshot.shortSessionLimitMinutes))
                out.writeInt(snapshot.shortSessionOmittedSetKeys.size)
                snapshot.shortSessionOmittedSetKeys.forEach(out::writeUTF)
            }
        }
        return output.toByteArray().also {
            require(it.size in 16..MAX_BYTES)
        }
    }
    fun decode(bytes:ByteArray):CustomWorkoutDaySnapshot {
        require(bytes.size in 16..MAX_BYTES)
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use{input->
                if(input.readInt()!=MAGIC)
                    throw IOException("unsupported custom session magic")
                val version=input.readInt()
                if(version !in 1..VERSION)
                    throw IOException("unsupported custom session snapshot")
                val stamp=CustomWorkoutSourceStamp(
                    input.readUTF(),input.readUTF(),input.readUTF(),
                    input.readLong(),input.readUTF(),input.readLong())
                val absoluteWeek=input.readInt()
                val inBlock=input.readInt()
                val blockId=input.readUTF()
                val phase=input.kind<TrainingPhase>()
                val purpose=input.kind<CustomBlockPurpose>()
                val split=input.split()
                val weekday=input.readInt()
                val dayLabel=input.readUTF()
                val sessionPurpose=input.kind<ComposerSessionPurpose>()
                val duration=input.readLong()
                val declared=input.optionalInt()
                val size=input.readInt()
                if(size !in 1..MAX_SLOTS)throw IOException("invalid number of slots")
                var totalSets=0
                val slots=List(size) {
                    input.slot(version).also {slot->
                        totalSets+=slot.sets.size
                        if(totalSets>MAX_TARGETS)
                            throw IOException("invalid set count")
                    }
                }
                val timeLimit=when(version){
                    3->input.optionalInt()
                    2->input.readInt()
                    else->null
                }
                val omissions=if(version>=2){
                    val count=input.readInt()
                    if(count !in 0..MAX_TARGETS)
                        throw IOException("invalid custom omission count")
                    Collections.unmodifiableList(List(count){input.readUTF()})
                } else emptyList()
                if(input.available()!=0)
                    throw IOException("trailing custom snapshot data")
                CustomWorkoutDaySnapshot(stamp,absoluteWeek,inBlock,blockId,
                    phase,purpose,split,weekday,dayLabel,sessionPurpose,
                    Collections.unmodifiableList(slots),duration,declared,
                    timeLimit,omissions
                ).also(::validate)
            }
        }catch(error:Exception){
            throw IOException("invalid custom workout snapshot",error)
        }
    }
}
