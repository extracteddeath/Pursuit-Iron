package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Native custom builder commands: partial drafts may exist, but no command
 * grants approval, saves a workout or mutates historical training outcomes.
 * Every command performs revision CAS and returns an undoable immutable
 * snapshot. A week/slot's stable identity is independent of its visual index.
 */
enum class CustomCycleBuilderRefusal {
    STALE_REVISION, INVALID_INPUT, MISSING_BLOCK, MISSING_SLOT,
    BLOCK_ID_CONFLICT, SLOT_ORDER_CONFLICT, UNDO_CONFLICT
}
data class CustomCycleDraftReceipt(
    val operation: String,
    val original: CustomTrainingCycle,
    val proposed: CustomTrainingCycle
)
sealed interface CustomCycleBuilderResult {
    data class Proposed(
        val draft: CustomTrainingCycle,
        val receipt: CustomCycleDraftReceipt
    ) : CustomCycleBuilderResult
    data class Rejected(val reason: CustomCycleBuilderRefusal) :
        CustomCycleBuilderResult
}

object CustomCycleBuilderDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /**
     * Snapshot even an incomplete, blank authoring draft. The separate
     * CustomTrainingCycleDomain.review gate verifies completed plans.
     */
    private fun freeze(input: CustomTrainingCycle): CustomTrainingCycle =
        input.copy(
            blocks = Collections.unmodifiableList(input.blocks.map { block ->
                block.copy(days = Collections.unmodifiableList(block.days.map { day ->
                    day.copy(slots = Collections.unmodifiableList(day.slots.map { slot ->
                        slot.copy(sets = Collections.unmodifiableList(
                            slot.sets.map { set ->
                                set.copy(amplifier = set.amplifier?.copy())
                            }
                        ))
                    }))
                }))
            }),
            weekOverrides = Collections.unmodifiableMap(
                input.weekOverrides.mapValues { (_, overrides) ->
                    Collections.unmodifiableList(overrides.map { override ->
                        override.copy(sets = Collections.unmodifiableList(
                            override.sets.map { it.copy(amplifier = it.amplifier?.copy()) }
                        ))
                    })
                }
            )
        )

    private fun edit(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        label: String,
        action: () -> CustomTrainingCycle?
    ): CustomCycleBuilderResult {
        if (current.revision != expectedRevision ||
            expectedRevision !in 1..9_007_199_254_740_990L)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.STALE_REVISION)
        val candidate = action() ?: return CustomCycleBuilderResult.Rejected(
            CustomCycleBuilderRefusal.INVALID_INPUT)
        if (candidate.cycleId != current.cycleId ||
            candidate.athleteId != current.athleteId ||
            candidate.revision != current.revision)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        val original = freeze(current)
        val next = freeze(candidate.copy(revision = current.revision + 1))
        return CustomCycleBuilderResult.Proposed(
            next, CustomCycleDraftReceipt(label, original, next))
    }

    /** The newly created blank draft is intentionally NOT executable. */
    fun blank(
        cycleId: String,
        athleteId: String,
        goal: AthleteTrainingGoal,
        title: String,
        firstBlockId: String,
        firstBlockWeeks: Int,
        phase: TrainingPhase,
        split: CustomSplitIdentity
    ): CustomTrainingCycle? {
        if (!id.matches(cycleId) || !id.matches(athleteId) ||
            !id.matches(firstBlockId) || title.isBlank() ||
            title.length > 120 || firstBlockWeeks !in 1..52)
            return null
        return freeze(CustomTrainingCycle(
            cycleId, 1, athleteId, goal, title,
            listOf(CustomTrainingBlock(firstBlockId, firstBlockId,
                phase, CustomBlockPurpose.TRAINING, firstBlockWeeks,
                split, emptyList()))
        ))
    }

    /** Add an entirely user-authored day to a blank or existing block. */
    fun addDay(
        current: CustomTrainingCycle, expectedRevision: Long,
        blockId: String, day: CustomTrainingDay
    ): CustomCycleBuilderResult {
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        if (day.weekday !in 0..6 || day.label.isBlank() ||
            day.label.length > 80 || block.days.size >= 7 ||
            block.days.any { it.weekday == day.weekday })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return edit(current, expectedRevision, "add-training-day") {
            current.copy(blocks = current.blocks.map {
                if (it.blockId == blockId) it.copy(days = it.days + day)
                else it
            })
        }
    }

    /**
     * Add an athlete-selected exercise at an explicit position. The user
     * owns both the slot ID and the chosen variant; no fallback replacement.
     * An empty set list may exist while editing but will fail final review.
     */
    fun addSlot(
        current: CustomTrainingCycle, expectedRevision: Long,
        blockId: String, weekday: Int, slot: CustomExerciseSlot,
        position: Int? = null
    ): CustomCycleBuilderResult {
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        val day = block.days.firstOrNull { it.weekday == weekday }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        val index = position ?: day.slots.size
        if (!id.matches(slot.slotId) || !id.matches(slot.exerciseId) ||
            day.slots.size >= 32 || index !in 0..day.slots.size ||
            day.slots.any { it.slotId == slot.slotId })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return edit(current, expectedRevision, "add-exercise-slot") {
            current.copy(blocks = current.blocks.map { candidate ->
                if (candidate.blockId != blockId) candidate else candidate.copy(
                    days = candidate.days.map { existing ->
                        if (existing.weekday != weekday) existing else existing.copy(
                            slots = existing.slots.toMutableList().also {
                                it.add(index, slot)
                            })
                    })
            })
        }
    }

    /** Optional destructive action; pin removal needs separate confirmation. */
    fun removeSlot(
        current: CustomTrainingCycle, expectedRevision: Long,
        blockId: String, weekday: Int, slotId: String,
        confirmPinned: Boolean = false
    ): CustomCycleBuilderResult {
        val day = current.blocks.firstOrNull { it.blockId == blockId }
            ?.days?.firstOrNull { it.weekday == weekday }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        val chosen = day.slots.firstOrNull { it.slotId == slotId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        if (chosen.pinned && !confirmPinned)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        // Dangling week-specific edits must never migrate to a different
        // exercise with the same array position.
        return edit(current, expectedRevision, "remove-exercise-slot") {
            current.copy(blocks = current.blocks.map { candidate ->
                if (candidate.blockId != blockId) candidate else candidate.copy(
                    days = candidate.days.map { existing ->
                        if (existing.weekday != weekday) existing else
                            existing.copy(slots = existing.slots.filterNot {
                                it.slotId == slotId
                            })
                    })
            }, weekOverrides = current.weekOverrides +
                (blockId to current.weekOverrides[blockId].orEmpty().filterNot {
                    it.weekday == weekday && it.slotId == slotId
                }))
        }
    }

    /**
     * Duplicate the entire actual block, including warmups, every set,
     * pinned identities, attachments and all per-week overrides. Refuse
     * shortening if a copy would silently discard an override.
     */
    fun duplicateBlock(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        sourceBlockId: String,
        newBlockId: String,
        weeks: Int? = null
    ): CustomCycleBuilderResult {
        if (!id.matches(newBlockId) ||
            current.blocks.any { it.blockId == newBlockId })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.BLOCK_ID_CONFLICT)
        if (current.blocks.none { it.blockId == sourceBlockId })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        return edit(current, expectedRevision, "duplicate-block") {
            val original = current.blocks.first { it.blockId == sourceBlockId }
            val copyWeeks = weeks ?: original.weeks
            val overrides = current.weekOverrides[sourceBlockId].orEmpty()
            if (copyWeeks !in 1..52 || overrides.any {
                    it.weekInBlock > copyWeeks
                } || current.blocks.size >= 26 ||
                current.blocks.sumOf { it.weeks } + copyWeeks > 104)
                null
            else current.copy(
                blocks = current.blocks + original.copy(
                    blockId = newBlockId, weeks = copyWeeks),
                weekOverrides = current.weekOverrides +
                    (newBlockId to overrides.map { it.copy() })
            )
        }
    }

    /**
     * Reorder without using exercise IDs as identity: the same movement may
     * legitimately appear in several independent slots.
     */
    fun reorderSlots(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekday: Int,
        orderedSlotIds: List<String>
    ): CustomCycleBuilderResult {
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        val day = block.days.firstOrNull { it.weekday == weekday }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        if (orderedSlotIds.size != day.slots.size ||
            orderedSlotIds.distinct().size != day.slots.size ||
            orderedSlotIds.toSet() != day.slots.map { it.slotId }.toSet())
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.SLOT_ORDER_CONFLICT)
        return edit(current, expectedRevision, "reorder-slots") {
            val lookup = day.slots.associateBy { it.slotId }
            current.copy(blocks = current.blocks.map { candidate ->
                if (candidate.blockId != blockId) candidate else candidate.copy(
                    days = candidate.days.map { existing ->
                        if (existing.weekday != weekday) existing else
                            existing.copy(slots =
                                orderedSlotIds.map { lookup.getValue(it) })
                    })
            })
        }
    }

    /**
     * Edit only ONE identified set schedule; never rewrite the same exercise
     * on other days, blocks, weeks or before/after the selected slot.
     */
    fun replaceSlotSets(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekday: Int,
        slotId: String,
        sets: List<CustomAuthoredSet>
    ): CustomCycleBuilderResult {
        if (sets.isEmpty() || sets.size > 20)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        if (block.days.none { day ->
                day.weekday == weekday && day.slots.any { it.slotId == slotId }
            }) return CustomCycleBuilderResult.Rejected(
            CustomCycleBuilderRefusal.MISSING_SLOT)
        return edit(current, expectedRevision, "replace-slot-sets") {
            current.copy(blocks = current.blocks.map { candidate ->
                if (candidate.blockId != blockId) candidate else candidate.copy(
                    days = candidate.days.map { day ->
                        if (day.weekday != weekday) day else day.copy(
                            slots = day.slots.map { slot ->
                                if (slot.slotId != slotId) slot else
                                    slot.copy(sets = sets)
                            })
                    })
            })
        }
    }

    /** Changes only one explicit block/week/day/slot override. */
    fun editWeek(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekInBlock: Int,
        weekday: Int,
        slotId: String,
        replacement: List<CustomAuthoredSet>?
    ): CustomCycleBuilderResult {
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        if (weekInBlock !in 1..block.weeks ||
            block.days.none { it.weekday == weekday &&
                it.slots.any { slot -> slot.slotId == slotId } } ||
            (replacement != null && (replacement.isEmpty() ||
                replacement.size > 20)))
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return edit(current, expectedRevision, "edit-specific-week") {
            val existing = current.weekOverrides[blockId].orEmpty()
            val unaffected = existing.filterNot { it.weekInBlock == weekInBlock &&
                it.weekday == weekday && it.slotId == slotId }
            val next = if (replacement == null) unaffected
                else unaffected + CustomWeekSlotOverride(
                    weekInBlock, weekday, slotId, replacement)
            current.copy(weekOverrides =
                current.weekOverrides + (blockId to next))
        }
    }


    /**
     * Move an athlete's exact exercise slots between authored weekdays.
     * The original slot identities, pins, per-set loads, amplifiers and notes
     * remain intact. Week-specific prescriptions follow the same slot, never
     * the old list position. A grouped superset moves atomically or not at all.
     *
     * A deliberately incomplete source day stays an editable draft but
     * cannot pass the later full cycle review until exercises are restored.
     */
    fun moveSlots(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        fromWeekday: Int,
        toWeekday: Int,
        slotIds: List<String>,
        destinationIndex: Int
    ): CustomCycleBuilderResult {
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        val source = block.days.firstOrNull { it.weekday == fromWeekday }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        val destination = block.days.firstOrNull { it.weekday == toWeekday }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        val movingIds = slotIds.toSet()
        if (fromWeekday == toWeekday || movingIds.isEmpty() ||
            movingIds.size != slotIds.size ||
            !source.slots.map { it.slotId }.toSet().containsAll(movingIds) ||
            destinationIndex !in 0..destination.slots.size ||
            destination.slots.size + movingIds.size > 32 ||
            destination.slots.any { it.slotId in movingIds })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)

        val moving = source.slots.filter { it.slotId in movingIds }
        val groupIds = moving.mapNotNull { it.supersetGroup }.toSet()
        // No orphaned or accidentally merged superset groups.
        if (source.slots.any {
                it.supersetGroup in groupIds && it.slotId !in movingIds
            } || destination.slots.any {
                it.supersetGroup != null && it.supersetGroup in groupIds
            })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)

        return edit(current, expectedRevision, "move-exercise-slots") {
            current.copy(
                blocks = current.blocks.map { candidate ->
                    if (candidate.blockId != blockId) candidate else
                        candidate.copy(days = candidate.days.map { day ->
                            when (day.weekday) {
                                fromWeekday -> day.copy(slots =
                                    day.slots.filterNot { it.slotId in movingIds })
                                toWeekday -> day.copy(slots =
                                    day.slots.toMutableList().also {
                                        it.addAll(destinationIndex, moving)
                                    })
                                else -> day
                            }
                        })
                },
                weekOverrides = current.weekOverrides +
                    (blockId to current.weekOverrides[blockId].orEmpty().map {
                        if (it.weekday == fromWeekday && it.slotId in movingIds)
                            it.copy(weekday = toWeekday)
                        else it
                    })
            )
        }
    }

    /**
     * Add a blank or explicitly authored phase at an exact timeline position.
     * Existing week overrides remain keyed to their stable block IDs.
     */
    fun insertBlock(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        block: CustomTrainingBlock,
        index: Int = current.blocks.size
    ): CustomCycleBuilderResult {
        if (!id.matches(block.blockId) ||
            current.blocks.any { it.blockId == block.blockId } ||
            current.weekOverrides.containsKey(block.blockId) ||
            block.label.isBlank() || block.label.length > 120 ||
            block.weeks !in 1..52 || current.blocks.size >= 26 ||
            current.blocks.sumOf { it.weeks } + block.weeks > 104 ||
            index !in 0..current.blocks.size ||
            block.days.size > 7 ||
            block.days.map { it.weekday }.distinct().size != block.days.size)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        return edit(current, expectedRevision, "insert-training-block") {
            current.copy(blocks = current.blocks.toMutableList().also {
                it.add(index, block)
            })
        }
    }

    /** Rearrange phases without relabeling or discarding their authored weeks. */
    fun reorderBlocks(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        orderedBlockIds: List<String>
    ): CustomCycleBuilderResult {
        if (orderedBlockIds.size != current.blocks.size ||
            orderedBlockIds.distinct().size != orderedBlockIds.size ||
            orderedBlockIds.toSet() != current.blocks.map { it.blockId }.toSet())
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.BLOCK_ID_CONFLICT)
        return edit(current, expectedRevision, "reorder-training-blocks") {
            val byId = current.blocks.associateBy { it.blockId }
            current.copy(blocks = orderedBlockIds.map { byId.getValue(it) })
        }
    }

    /** Rename one author's day without changing its stable schedule or slots. */
    fun renameDay(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekday: Int,
        newLabel: String
    ): CustomCycleBuilderResult {
        if (newLabel.isBlank() || newLabel.length > 80)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        val block = current.blocks.firstOrNull { it.blockId == blockId }
            ?: return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_BLOCK)
        if (block.days.none { it.weekday == weekday })
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.MISSING_SLOT)
        return edit(current, expectedRevision, "rename-training-day") {
            current.copy(blocks = current.blocks.map { candidate ->
                if (candidate.blockId != blockId) candidate else candidate.copy(
                    days = candidate.days.map {
                        if (it.weekday == weekday) it.copy(label = newLabel)
                        else it
                    })
            })
        }
    }

    /**
     * Undo only if the current draft still matches the exact accepted
     * result. Prior receipts cannot revert someone else's newer author edit.
     */
    fun undo(
        current: CustomTrainingCycle,
        receipt: CustomCycleDraftReceipt
    ): CustomCycleBuilderResult {
        if (current != receipt.proposed || current.revision !=
            receipt.proposed.revision)
            return CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.UNDO_CONFLICT)
        return edit(current, current.revision, "undo-" + receipt.operation) {
            receipt.original.copy(revision = current.revision)
        }
    }
}
