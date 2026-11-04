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
 * Versioned Kotlin-only transport for athlete-authored CUSTOM cycle drafts.
 *
 * Deliberately different from the approved-program codec: a half-authored
 * draft is safely storable, but it must NEVER become an issued workout merely
 * because it was decoded or written. No JS runtime or Java serialization.
 */
internal object NativeCustomCycleDraftCodec {
    private const val MAGIC = 0x50494344 // PICD
    private const val VERSION = 1
    const val MAX_BYTES = 2_500_000
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun DataOutputStream.enum(value: Enum<*>) = writeUTF(value.name)
    private inline fun <reified T : Enum<T>> DataInputStream.enum(): T {
        val name = readUTF()
        return enumValues<T>().firstOrNull { it.name == name }
            ?: throw IOException("Unknown custom draft enum")
    }
    private fun DataOutputStream.optionalString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }
    private fun DataInputStream.optionalString(): String? =
        if (readBoolean()) readUTF() else null
    private fun DataOutputStream.optionalInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }
    private fun DataInputStream.optionalInt(): Int? =
        if (readBoolean()) readInt() else null
    private fun <T> DataOutputStream.items(values: Collection<T>, put: (T) -> Unit) {
        writeInt(values.size)
        values.forEach(put)
    }
    private fun <T> DataInputStream.items(max: Int, get: () -> T): List<T> {
        val length = readInt()
        if (length !in 0..max) throw IOException("Custom draft collection bound")
        return Collections.unmodifiableList(List(length) { get() })
    }
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
    private fun DataOutputStream.set(value: CustomAuthoredSet) {
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
            amplifier.followUpLoad?.let(::load)
        }
    }
    private fun DataInputStream.set(): CustomAuthoredSet {
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
            CustomSetAmplifier(kind, repsExtra, restExtra,
                if (readBoolean()) load() else null)
        } else null
        return CustomAuthoredSet(type, weight, reps, rir, active, rest, amplifier)
    }
    private fun DataOutputStream.slot(value: CustomExerciseSlot) {
        writeUTF(value.slotId)
        writeUTF(value.exerciseId)
        enum(value.role)
        items(value.sets) { set(it) }
        optionalString(value.attachmentId)
        optionalString(value.supersetGroup)
        writeBoolean(value.pinned)
        writeUTF(value.coachNote)
    }
    private fun DataInputStream.slot(): CustomExerciseSlot =
        CustomExerciseSlot(readUTF(), readUTF(), enum<ComposerRole>(),
            items(20) { set() }, optionalString(), optionalString(),
            readBoolean(), readUTF())

    private fun DataOutputStream.day(value: CustomTrainingDay) {
        writeInt(value.weekday)
        writeUTF(value.label)
        enum(value.purpose)
        items(value.slots) { slot(it) }
    }
    private fun DataInputStream.day(): CustomTrainingDay =
        CustomTrainingDay(readInt(), readUTF(), enum<ComposerSessionPurpose>(),
            items(32) { slot() })

    private fun DataOutputStream.split(value: CustomSplitIdentity) {
        when (value) {
            is CustomSplitIdentity.Named -> {
                writeByte(0); enum(value.split)
            }
            is CustomSplitIdentity.Freeform -> {
                writeByte(1); writeUTF(value.label)
            }
        }
    }
    private fun DataInputStream.split(): CustomSplitIdentity =
        when (readUnsignedByte()) {
            0 -> CustomSplitIdentity.Named(enum<ComposerSplit>())
            1 -> CustomSplitIdentity.Freeform(readUTF())
            else -> throw IOException("Unknown custom split")
        }

    private fun DataOutputStream.block(value: CustomTrainingBlock) {
        writeUTF(value.blockId)
        writeUTF(value.label)
        enum(value.phase)
        enum(value.purpose)
        writeInt(value.weeks)
        split(value.split)
        items(value.days) { day(it) }
    }
    private fun DataInputStream.block(): CustomTrainingBlock =
        CustomTrainingBlock(readUTF(), readUTF(), enum<TrainingPhase>(),
            enum<CustomBlockPurpose>(), readInt(), split(), items(7) { day() })

    private fun DataOutputStream.override(value: CustomWeekSlotOverride) {
        writeInt(value.weekInBlock)
        writeInt(value.weekday)
        writeUTF(value.slotId)
        items(value.sets) { set(it) }
    }
    private fun DataInputStream.override(): CustomWeekSlotOverride =
        CustomWeekSlotOverride(readInt(), readInt(), readUTF(), items(20) { set() })

    /**
     * Structural bounds only. Coach/equipment approval is ALWAYS a separate
     * authenticated review; incomplete empty days/sets may be autosaved.
     */
    private fun validate(value: CustomTrainingCycle) {
        require(id.matches(value.cycleId) && id.matches(value.athleteId))
        require(value.revision in 1..9_007_199_254_740_991L)
        require(value.title.isNotBlank() && value.title.length <= 120)
        require(value.authorNote.length <= 2000)
        require(value.blocks.size in 1..26)
        require(value.blocks.sumOf { it.weeks } in 1..104)
        require(value.blocks.map { it.blockId }.distinct().size == value.blocks.size)
        require(value.blocks.all { block ->
            id.matches(block.blockId) &&
                block.label.isNotBlank() && block.label.length <= 120 &&
                block.weeks in 1..52 && block.days.size <= 7 &&
                block.days.map { it.weekday }.distinct().size == block.days.size &&
                (block.split !is CustomSplitIdentity.Freeform ||
                    block.split.label.length in 1..80) &&
                block.days.all { day ->
                    day.weekday in 0..6 && day.label.length in 1..80 &&
                        day.slots.size <= 32 &&
                        day.slots.map { it.slotId }.distinct().size == day.slots.size &&
                        day.slots.all { slot ->
                            id.matches(slot.slotId) && id.matches(slot.exerciseId) &&
                                (slot.attachmentId == null ||
                                    id.matches(slot.attachmentId)) &&
                                (slot.supersetGroup == null ||
                                    id.matches(slot.supersetGroup)) &&
                                slot.coachNote.length <= 1000 &&
                                slot.sets.size <= 20
                        }
                }
        })
        require(value.weekOverrides.keys.all {
            key -> value.blocks.any { it.blockId == key }
        })
        require(value.weekOverrides.values.sumOf { it.size } <= 3000)
        require(value.weekOverrides.all { (blockId, overrides) ->
            val block = value.blocks.first { it.blockId == blockId }
            overrides.distinctBy {
                Triple(it.weekInBlock, it.weekday, it.slotId)
            }.size == overrides.size && overrides.all {
                it.weekInBlock in 1..block.weeks &&
                    it.weekday in 0..6 && id.matches(it.slotId) &&
                    it.sets.size <= 20
            }
        })
        // Bound the total work performed on any subsequently reviewed draft.
        require(value.blocks.sumOf {
            block -> block.days.sumOf { day ->
                day.slots.sumOf { slot -> slot.sets.size }
            }
        } <= 1500)
    }

    fun encode(value: CustomTrainingCycle): ByteArray {
        validate(value)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            with(output) {
                writeInt(MAGIC); writeInt(VERSION)
                writeUTF(value.cycleId); writeLong(value.revision)
                writeUTF(value.athleteId); enum(value.goal)
                writeUTF(value.title); writeUTF(value.authorNote)
                items(value.blocks) { block(it) }
                items(value.weekOverrides.toSortedMap().entries) { (key, edits) ->
                    writeUTF(key); items(edits) { override(it) }
                }
            }
        }
        return buffer.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): CustomTrainingCycle {
        if (bytes.size !in 16..MAX_BYTES)
            throw IOException("Invalid custom draft size")
        return try {
            val cycle = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                with(input) {
                    if (readInt() != MAGIC || readInt() != VERSION)
                        throw IOException("Unknown custom draft version")
                    val cycleId = readUTF()
                    val revision = readLong()
                    val athleteId = readUTF()
                    val goal = enum<AthleteTrainingGoal>()
                    val title = readUTF()
                    val note = readUTF()
                    val blocks = items(26) { block() }
                    val entries = items(26) {
                        readUTF() to items(3000) { override() }
                    }
                    if (entries.map { it.first }.distinct().size != entries.size ||
                        available() != 0)
                        throw IOException("Duplicate override blocks or trailing bytes")
                    CustomTrainingCycle(cycleId, revision, athleteId, goal,
                        title, blocks, Collections.unmodifiableMap(
                            entries.toMap()), note)
                }
            }
            validate(cycle)
            cycle
        } catch (error: Exception) {
            throw IOException("Invalid custom draft payload", error)
        }
    }
}
