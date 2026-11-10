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
 * Exact binary representation of the ENTIRE approved Kotlin workout.
 * No JavaScript snapshot, template, catalog or adapter is used to restore it.
 *
 * Decode is strict: enum names, load semantics, target ordinals, limits and
 * trailing bytes are checked. The persisted journal envelope supplies the
 * separate checksum; this codec is deliberately not an authentication token.
 */
internal object KotlinIssuedSnapshotCodec {
    private const val MAGIC=0x50495331 // PIS1
    private const val VERSION=2
    private const val MAX_TARGETS=256
    private const val MAX_BYTES=250_000
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val setKey=Regex("^[A-Za-z0-9][A-Za-z0-9_:.-]{0,99}$")
    private const val MAX_REV=9_007_199_254_740_991L
    private val MAX_LOAD=BigDecimal("100000")

    private fun validText(value:String):Boolean=id.matches(value)
    private fun validNumber(value:BigDecimal):Boolean=
        value>=BigDecimal.ZERO && value<=MAX_LOAD &&
            value.stripTrailingZeros().scale()<=3
    // Retain exact decimal scale: BigDecimal("65.0") must cold-reopen as
    // precisely "65.0", not merely the numerically equal "65".
    private fun decimal(value:BigDecimal):String=value.toPlainString()

    fun validate(snapshot:KotlinStagedWorkout) {
        val s=snapshot.source
        require(listOf(s.athleteId,s.programId,s.gymId,s.catalogRevision,
            s.coachingRulesRevision,snapshot.grantId,snapshot.workoutId)
                .all(::validText))
        require(listOf(s.athleteRevision,s.programRevision,s.gymRevision)
            .all{it in 1..MAX_REV})
        require(snapshot.calendarDay in 0..6 &&
            snapshot.dayIndex in 0..6 &&
            snapshot.estimatedMinutes in 1..1440 &&
            snapshot.targets.size in 1..MAX_TARGETS)
        require(snapshot.targets.map{it.setKey}.distinct().size==
            snapshot.targets.size)
        val omitted=snapshot.shortSessionOmittedSetKeys
        val limit=snapshot.shortSessionLimitMinutes
        if(limit==null)require(omitted.isEmpty())
        else require(limit in setOf(20,30,45) &&
            omitted.size+snapshot.targets.size<=MAX_TARGETS &&
            omitted.distinct().size==omitted.size &&
            omitted.all {setKey.matches(it)} &&
            omitted.none {key -> snapshot.targets.any {it.setKey==key}})
        for(t in snapshot.targets) {
            require(validText(t.exerciseId) && setKey.matches(t.setKey) &&
                t.ordinal in 0..255 && t.reps.minimum in 1..100 &&
                t.reps.maximum in t.reps.minimum..100 &&
                (t.targetRir==null || t.targetRir in 0..10) &&
                t.activeSeconds in 1..3600 && t.restSeconds in 0..3600)
            when(val load=t.load) {
                DraftSetLoad.ExplicitBodyweight -> Unit
                is DraftSetLoad.Verified -> require(validNumber(load.value))
            }
        }
    }

    fun encode(snapshot:KotlinStagedWorkout):ByteArray {
        validate(snapshot)
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            val s=snapshot.source
            val version=if(snapshot.shortSessionLimitMinutes==null)1 else VERSION
            out.writeInt(MAGIC);out.writeInt(version)
            out.writeUTF(s.athleteId);out.writeLong(s.athleteRevision)
            out.writeUTF(s.programId);out.writeLong(s.programRevision)
            out.writeUTF(s.gymId);out.writeLong(s.gymRevision)
            out.writeUTF(s.catalogRevision);out.writeUTF(s.coachingRulesRevision)
            out.writeUTF(snapshot.grantId);out.writeUTF(snapshot.workoutId)
            out.writeInt(snapshot.calendarDay);out.writeInt(snapshot.dayIndex)
            out.writeUTF(snapshot.purpose.name);out.writeLong(snapshot.estimatedMinutes)
            out.writeInt(snapshot.targets.size)
            for(t in snapshot.targets) {
                out.writeUTF(t.exerciseId);out.writeUTF(t.role.name)
                out.writeUTF(t.purpose.name);out.writeUTF(t.setKey)
                out.writeUTF(t.type.name);out.writeInt(t.ordinal)
                when(val load=t.load) {
                    DraftSetLoad.ExplicitBodyweight -> out.writeBoolean(false)
                    is DraftSetLoad.Verified -> {
                        out.writeBoolean(true)
                        out.writeUTF(load.unit.name);out.writeUTF(load.basis.name)
                        out.writeUTF(decimal(load.value))
                    }
                }
                out.writeInt(t.reps.minimum);out.writeInt(t.reps.maximum)
                val rir=t.targetRir
                out.writeBoolean(rir!=null)
                if(rir!=null)out.writeInt(rir)
                out.writeInt(t.activeSeconds);out.writeInt(t.restSeconds)
            }
            // Version 1 remains byte-identical for every existing workout.
            if(version>=2) {
                out.writeInt(checkNotNull(snapshot.shortSessionLimitMinutes))
                out.writeInt(snapshot.shortSessionOmittedSetKeys.size)
                snapshot.shortSessionOmittedSetKeys.forEach(out::writeUTF)
            }
        }
        return bytes.toByteArray().also {
            require(it.size<=MAX_BYTES)
        }
    }

    private inline fun <reified T:Enum<T>> DataInputStream.enum():T {
        val raw=readUTF()
        return enumValues<T>().firstOrNull{it.name==raw}
            ?:throw IOException("unknown enum")
    }

    fun decode(raw:ByteArray):KotlinStagedWorkout {
        if(raw.size !in 8..MAX_BYTES)throw IOException("invalid snapshot size")
        return try {
            DataInputStream(ByteArrayInputStream(raw)).use { input ->
                if(input.readInt()!=MAGIC)
                    throw IOException("unknown snapshot format")
                val version=input.readInt()
                if(version !in 1..VERSION)
                    throw IOException("unsupported snapshot format")
                val source=KotlinTrainingSourceRevision(
                    input.readUTF(),input.readLong(),input.readUTF(),input.readLong(),
                    input.readUTF(),input.readLong(),input.readUTF(),input.readUTF()
                )
                val grant=input.readUTF()
                val workout=input.readUTF()
                val calendarDay=input.readInt()
                val dayIndex=input.readInt()
                val purpose=input.enum<ComposerSessionPurpose>()
                val minutes=input.readLong()
                val count=input.readInt()
                if(count !in 1..MAX_TARGETS)throw IOException("invalid target count")
                val targets=ArrayList<KotlinWorkoutTarget>(count)
                repeat(count) {
                    val exercise=input.readUTF()
                    val role=input.enum<ComposerRole>()
                    val setPurpose=input.enum<ComposerSessionPurpose>()
                    val key=input.readUTF()
                    val type=input.enum<DraftSetType>()
                    val ordinal=input.readInt()
                    val load=if(input.readBoolean()) {
                        DraftSetLoad.Verified(
                            input.enum<LoadUnit>(),
                            input.enum<LoadBasis>(),
                            BigDecimal(input.readUTF())
                        )
                    } else DraftSetLoad.ExplicitBodyweight
                    val band=RepBand(input.readInt(),input.readInt())
                    val rir=if(input.readBoolean())input.readInt() else null
                    val active=input.readInt()
                    val rest=input.readInt()
                    targets.add(KotlinWorkoutTarget(
                        exercise,role,setPurpose,key,type,ordinal,load,band,
                        rir,active,rest
                    ))
                }
                val limit=if(version>=2)input.readInt() else null
                val omitted=if(version>=2) {
                    val n=input.readInt()
                    if(n !in 0..MAX_TARGETS)throw IOException("invalid omission count")
                    Collections.unmodifiableList(List(n){input.readUTF()})
                } else emptyList()
                if(input.available()!=0)throw IOException("trailing snapshot data")
                KotlinStagedWorkout(source,grant,workout,calendarDay,dayIndex,
                    purpose,minutes,Collections.unmodifiableList(targets),
                    limit,omitted).also(::validate)
            }
        } catch(ex:Exception) {
            throw IOException("invalid issued Kotlin workout",ex)
        }
    }
}
