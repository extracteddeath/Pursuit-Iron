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
