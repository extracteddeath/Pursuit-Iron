package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
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
    private const val VERSION = 8
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
    private fun <T> DataOutputStream.items(values: Collection<T>, put: (T) -> Unit) {
        writeInt(values.size)
        values.forEach(put)
    }
    private fun <T> DataInputStream.items(max: Int, get: () -> T): List<T> {
        val length = readInt()
        if (length !in 0..max) throw IOException("Custom draft collection bound")
        return Collections.unmodifiableList(List(length) { get() })
    }
    private fun DataOutputStream.slot(value: CustomExerciseSlot) {
        writeUTF(value.slotId)
        writeUTF(value.exerciseId)
        enum(value.role)
        items(value.sets) { NativeAuthoredSetCodec.write(this, it) }
        optionalString(value.attachmentId)
        optionalString(value.supersetGroup)
        writeBoolean(value.pinned)
        writeUTF(value.coachNote)
        optionalString(value.machineInstanceId)
        writeBoolean(value.progressionMethod != null)
        value.progressionMethod?.let { enum(it) }
    }
    private fun DataInputStream.slot(version:Int): CustomExerciseSlot =
        CustomExerciseSlot(readUTF(), readUTF(), enum<ComposerRole>(),
            items(20) { NativeAuthoredSetCodec.read(this,version>=8) }, optionalString(), optionalString(),
            readBoolean(), readUTF(),
            optionalString(),
            if(readBoolean())
                enum<SavedProgressionMethod>() else null)

    private fun DataOutputStream.day(value: CustomTrainingDay) {
        writeInt(value.weekday)
        writeUTF(value.label)
        enum(value.purpose)
        items(value.slots) { slot(it) }
    }
    private fun DataInputStream.day(version:Int): CustomTrainingDay =
        CustomTrainingDay(readInt(), readUTF(), enum<ComposerSessionPurpose>(),
            items(32) { slot(version) })

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
    private fun DataInputStream.block(version:Int): CustomTrainingBlock =
        CustomTrainingBlock(readUTF(), readUTF(), enum<TrainingPhase>(),
            enum<CustomBlockPurpose>(), readInt(), split(), items(7) { day(version) })

    private fun DataOutputStream.writeOverride(value: CustomWeekSlotOverride) {
        writeInt(value.weekInBlock)
        writeInt(value.weekday)
        writeUTF(value.slotId)
        items(value.sets) { NativeAuthoredSetCodec.write(this, it) }
    }
    private fun DataInputStream.readOverride(version:Int): CustomWeekSlotOverride =
        CustomWeekSlotOverride(readInt(), readInt(), readUTF(), items(20) { NativeAuthoredSetCodec.read(this,version>=8) })

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
                ((block.split as? CustomSplitIdentity.Freeform)?.label?.length
                    ?.let { it in 1..80 } ?: true) &&
                block.days.all { day ->
                    day.weekday in 0..6 && day.label.length in 1..80 &&
                        day.slots.size <= 32 &&
                        day.slots.map { it.slotId }.distinct().size == day.slots.size &&
                        day.slots.all { slot ->
                            id.matches(slot.slotId) && id.matches(slot.exerciseId) &&
                                (slot.attachmentId?.let { id.matches(it) } ?: true) &&
                                (slot.supersetGroup?.let { id.matches(it) } ?: true) &&
                                slot.coachNote.length <= 1000 &&
                                (slot.machineInstanceId?.let { id.matches(it) } ?: true) &&
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
                    writeUTF(key); items(edits) { writeOverride(it) }
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
                    if(readInt()!=MAGIC)throw IOException("Unknown custom draft magic")
                    val version=readInt()
                    if(version !in 7..VERSION)
                        throw IOException("Unknown custom draft version")
                    val cycleId = readUTF()
                    val revision = readLong()
                    val athleteId = readUTF()
                    val goal = enum<AthleteTrainingGoal>()
                    val title = readUTF()
                    val note = readUTF()
                    val blocks = items(26) { block(version) }
                    val entries = items(26) {
                        readUTF() to items(3000) { readOverride(version) }
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
