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
    private const val VERSION=10
    private const val SLOT_MAGIC=0x50495333 // PIS3
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
            NativeAuthoredSetCodec.write(this, set.prescription,version>=9)
        }
        optional(slot.machineInstanceId)
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
            CustomWorkoutSetSnapshot(readUTF(),readUTF(),readInt(),NativeAuthoredSetCodec.read(this,version>=8,version>=9))
        }
        val machine=optional()
        return CustomWorkoutSlotSnapshot(slotId,exerciseId,familyId,
            variantId,role,attachment,group,pinned,note,
            Collections.unmodifiableList(sets),machine)
    }
    internal fun encodeSlot(value:CustomWorkoutSlotSnapshot):ByteArray =
        ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use { out->
                out.writeInt(SLOT_MAGIC)
                val version=if(value.sets.any{it.prescription.percentageIntent!=null}) 9 else 8
                out.writeInt(version)
                out.slot(value,version)
            }
        }.toByteArray().also { require(it.size in 24..MAX_BYTES) }
    internal fun decodeSlot(bytes:ByteArray):CustomWorkoutSlotSnapshot {
        if(bytes.size !in 24..MAX_BYTES)throw IOException("invalid swap slot size")
        return DataInputStream(ByteArrayInputStream(bytes)).use { input->
            if (input.readInt() != SLOT_MAGIC)
                throw IOException("unsupported swap slot magic")
            val version=input.readInt()
            if(version !in 7..9)
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

    private fun validate(snapshot:CustomWorkoutDaySnapshot,allowMidSessionKeys:Boolean=false) {
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
            require(slot.machineInstanceId?.let {
                KotlinMachineAlternativesDomain.validInstanceId(it)
            } != false)
            require(slot.coachNote.length<=1000)
            require(slot.sets.isNotEmpty() && slot.sets.size<=20)
            val lastWorking=slot.sets.indexOfLast{
                it.prescription.type==DraftSetType.WORKING
            }
            for((index,set) in slot.sets.withIndex()) {
                require(set.slotId==slot.slotId)
                require(set.ordinalInSlot==index+1)
                require(set.setKey=="${stamp.cycleId}:w${snapshot.absoluteWeek}:d${snapshot.weekday}:${slot.slotId}:s${index+1}" ||
                    (allowMidSessionKeys && set.prescription.type==DraftSetType.WORKING &&
                        MidSessionWorkingEditDomain.isAddedSetKey(set.setKey)))
                require(set.setKey.length<=256 && seen.add(set.setKey))
                val p=set.prescription
                require(p.percentageIntent?.let{it.validForSet(p) && it.weekInBlock==snapshot.weekInBlock}!=false)
                require(p.reps.minimum in 1..100 &&
                    p.reps.maximum in p.reps.minimum..100)
                require(p.exactRepTarget==null ||
                    (p.type==DraftSetType.WORKING &&
                        p.exactRepTarget in p.reps.minimum..p.reps.maximum))
                require(p.targetRir?.let { it in 0..10 } != false)
                require(p.activeSeconds in 1..3600 &&
                    p.restSeconds in 0..3600)
                require(p.type!=DraftSetType.WARMUP || p.amplifier==null)
                require(p.amplifier?.kind!=CustomAmplifierKind.LENGTHENED_PARTIALS ||
                    (p.type==DraftSetType.WORKING && index==lastWorking)) {
                    "Partial repetition extension must stay on the final working set"
                }
                fun checkLoad(load:DraftSetLoad) {
                    if(load is DraftSetLoad.Verified)require(
                        (load.value>zero || load.basis.permitsZeroAddedLoad && load.value.signum()==0) &&
                        load.value<=maxWeight &&
                        load.value.stripTrailingZeros().scale()<=3)
                }
                checkLoad(p.load)
                p.amplifier?.let{ amp ->
                    require(amp.followUpReps in 1..100 &&
                        amp.followUpRestSeconds in 0..3600 &&
                        // Share the authored protocol authority with the
                        // Kotlin engine. A previous myo-only codec guard
                        // rejected valid staged rest-pause and clusters on
                        // issuance, recovery, swapping and journal replay.
                        // Do not create a second divergent technique policy.
                        AdvancedAmplifierProtocolDomain.validStagedFollowUps(amp) &&
                        amp.followUpActiveSeconds?.let{
                            it in 1..3600
                        }!=false)
                    amp.followUpLoad?.let(::checkLoad)
                    amp.secondDropLoad?.let(::checkLoad)
                    if(amp.kind==CustomAmplifierKind.DROP_SET &&
                        !AdvancedAmplifierProtocolDomain.validDropSequence(
                            p.load,amp))
                        throw IllegalArgumentException(
                            "Each drop requires a distinct strictly reduced load")
                    if(amp.kind==CustomAmplifierKind.DROP_SET) {
                        val original=p.load as? DraftSetLoad.Verified
                        val next=amp.followUpLoad as? DraftSetLoad.Verified
                        require(original!=null && next!=null &&
                            original.unit==next.unit &&
                            original.basis==next.basis &&
                            next.value<original.value)
                    }
                }
                // Validate the precise same authored seconds used by
                // custom workout Preview, weekly dose audit and Swap / Undo.
                // Int multiplication once let a forged large stage-rest field
                // wrap into a small, plausible saved duration.
                duration+=checkNotNull(CustomWorkoutTimingDomain.setSeconds(p))
            }
        }
        require(duration==snapshot.knownSetAndRestSeconds)
        for(group in snapshot.slots.mapNotNull {it.supersetGroup}.distinct()) {
            require(snapshot.slots.count{it.supersetGroup==group} in 2..3)
        }
    }
    fun encode(snapshot:CustomWorkoutDaySnapshot,allowMidSessionKeys:Boolean=false):ByteArray {
        validate(snapshot,allowMidSessionKeys)
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use{out->
            out.writeInt(MAGIC)
            val version=if(snapshot.slots.any{slot->slot.sets.any{MidSessionWorkingEditDomain.isAddedSetKey(it.setKey)}}) VERSION
                else if(snapshot.slots.any{slot->slot.sets.any{it.prescription.percentageIntent!=null}}) 9 else 8
            out.writeInt(version)
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
            out.optionalInt(snapshot.shortSessionLimitMinutes)
            out.writeInt(snapshot.shortSessionOmittedSetKeys.size)
            snapshot.shortSessionOmittedSetKeys.forEach(out::writeUTF)
        }
        return output.toByteArray().also {
            require(it.size in 16..MAX_BYTES)
        }
    }
    fun decode(bytes:ByteArray,allowMidSessionKeys:Boolean=false):CustomWorkoutDaySnapshot {
        require(bytes.size in 16..MAX_BYTES)
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use{input->
                if(input.readInt()!=MAGIC)
                    throw IOException("unsupported custom session magic")
                val version=input.readInt()
                if(version !in 7..VERSION || version==VERSION && !allowMidSessionKeys)
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
                val timeLimit = input.optionalInt()
                val count = input.readInt()
                if (count !in 0..MAX_TARGETS)
                    throw IOException("invalid custom omission count")
                val omissions = Collections.unmodifiableList(List(count) { input.readUTF() })
                if(input.available()!=0)
                    throw IOException("trailing custom snapshot data")
                CustomWorkoutDaySnapshot(stamp,absoluteWeek,inBlock,blockId,
                    phase,purpose,split,weekday,dayLabel,sessionPurpose,
                    Collections.unmodifiableList(slots),duration,declared,
                    timeLimit,omissions
                ).also{validate(it,allowMidSessionKeys && version==VERSION)}
            }
        }catch(error:Exception){
            throw IOException("invalid custom workout snapshot",error)
        }
    }
}
