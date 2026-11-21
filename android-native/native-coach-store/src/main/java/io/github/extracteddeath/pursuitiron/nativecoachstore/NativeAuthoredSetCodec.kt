package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal

internal object NativeAuthoredSetCodec {
    private fun DataOutputStream.enum(value: Enum<*>) = writeUTF(value.name)
    private inline fun <reified T : Enum<T>> DataInputStream.enum(): T {
        val name = readUTF()
        return enumValues<T>().firstOrNull { it.name == name }
            ?: throw IOException("Unknown custom draft enum")
    }
    private fun DataOutputStream.optionalInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }
    private fun DataInputStream.optionalInt(): Int? =
        if (readBoolean()) readInt() else null
    private fun DataOutputStream.load(value: DraftSetLoad) {
        when (value) {
            DraftSetLoad.ExplicitBodyweight -> writeByte(0)
            is DraftSetLoad.Verified -> {
                writeByte(1)
                enum(value.unit)
                enum(value.basis)
                writeUTF(value.value.toPlainString())
            }
        }
    }
    private fun DataInputStream.load(): DraftSetLoad = when (readUnsignedByte()) {
        0 -> DraftSetLoad.ExplicitBodyweight
        1 -> DraftSetLoad.Verified(
            enum<LoadUnit>(), enum<LoadBasis>(), BigDecimal(readUTF()))
        else -> throw IOException("Unknown custom load kind")
    }
    fun write(output: DataOutputStream, value: CustomAuthoredSet) = with(output) {
        enum(value.type)
        load(value.load)
        writeInt(value.reps.minimum)
        writeInt(value.reps.maximum)
        optionalInt(value.targetRir)
        writeInt(value.activeSeconds)
        writeInt(value.restSeconds)
        val amplifier = value.amplifier
        writeBoolean(amplifier != null)
        if (amplifier != null) {
            enum(amplifier.kind)
            writeInt(amplifier.followUpReps)
            writeInt(amplifier.followUpRestSeconds)
            writeBoolean(amplifier.followUpLoad != null)
            amplifier.followUpLoad?.let { load(it) }
            optionalInt(amplifier.followUpActiveSeconds)
            writeInt(amplifier.followUpRounds)
            writeBoolean(amplifier.secondDropLoad != null)
            amplifier.secondDropLoad?.let { load(it) }
        }
        optionalInt(value.exactRepTarget)
    }
    fun read(input: DataInputStream, hasExactTarget:Boolean=true): CustomAuthoredSet = with(input) {
        val type = enum<DraftSetType>()
        val weight = load()
        val reps = RepBand(readInt(), readInt())
        val rir = optionalInt()
        val active = readInt()
        val rest = readInt()
        val amplifier = if (readBoolean()) {
            val kind = enum<CustomAmplifierKind>()
            val repsExtra = readInt()
            val restExtra = readInt()
            val nextLoad=if(readBoolean())load() else null
            CustomSetAmplifier(kind,repsExtra,restExtra,nextLoad,
                optionalInt(),
                readInt(),
                if(readBoolean())load() else null)
        } else null
        val exact=if(hasExactTarget)optionalInt() else null
        CustomAuthoredSet(type, weight, reps, rir, active, rest, amplifier,exact)
    }
}
