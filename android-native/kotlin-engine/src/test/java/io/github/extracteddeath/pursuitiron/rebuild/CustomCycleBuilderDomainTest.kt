package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomCycleBuilderDomainTest {
    private fun n(v: Int) = BigDecimal(v)
    private fun set(value: Int = 185, amp: CustomSetAmplifier? = null) =
        CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(value)),
            RepBand(6, 10), 2, 45, 120, amp)
    private fun draft(): CustomTrainingCycle {
        val drop = CustomSetAmplifier(CustomAmplifierKind.DROP_SET, 8, 20,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(135)))
        val a = CustomExerciseSlot("bench-a", "bench", ComposerRole.PRIMARY,
            listOf(set(185), set(185, drop)),
            attachmentId = "straight-bar", pinned = true,
            coachNote = "Athlete-specific cue")
        val b = CustomExerciseSlot("bench-b", "bench", ComposerRole.SECONDARY,
            listOf(set(155)), pinned = false)
        val day = CustomTrainingDay(0, "Custom day",
            ComposerSessionPurpose.HYPERTROPHY, listOf(a, b))
        val block = CustomTrainingBlock("base", "My base",
            TrainingPhase.HYPERTROPHY, CustomBlockPurpose.TRAINING, 4,
            CustomSplitIdentity.Freeform("Whatever works"),
            listOf(day))
        return CustomTrainingCycle(
            "cycle", 1, "athlete", AthleteTrainingGoal.POWERBUILDING,
            "Manual cycle", listOf(block),
            mapOf("base" to listOf(
                CustomWeekSlotOverride(3, 0, "bench-a", listOf(set(200)))
            ))
        )
    }
    private fun accepted(result: CustomCycleBuilderResult) =
        result as CustomCycleBuilderResult.Proposed

    @Test fun blankAllowsUnfinishedDraftWithoutPretendingWorkoutReady() {
        val blank = CustomCycleBuilderDomain.blank(
            "c-1", "athlete", AthleteTrainingGoal.STRENGTH,
            "Blank cycle", "base", 4, TrainingPhase.STRENGTH,
            CustomSplitIdentity.Freeform("My rotation"))!!
        assertEquals(1L, blank.revision)
        assertTrue(blank.blocks.single().days.isEmpty())
        val review = CustomTrainingCycleDomain.review(blank, emptyList())
        assertNull(review.verifiedDraft)
        assertFalse(review.canIssueWorkout)
    }

    @Test fun blockDuplicationCopiesEveryAuthoredFieldAndWeekOverride() {
        val original = draft()
        val proposed = accepted(CustomCycleBuilderDomain.duplicateBlock(
            original, 1, "base", "strength", 4))
        assertEquals(2L, proposed.draft.revision)
        assertEquals(listOf("base", "strength"),
            proposed.draft.blocks.map { it.blockId })
        val first = proposed.draft.blocks.first()
        val copied = proposed.draft.blocks.last()
        assertEquals(first.copy(blockId = "strength"), copied)
        assertEquals(original.weekOverrides["base"],
            proposed.draft.weekOverrides["strength"])
        assertEquals(CustomAmplifierKind.DROP_SET,
            copied.days.single().slots.first().sets.last().amplifier?.kind)
        assertEquals("straight-bar",
            copied.days.single().slots.first().attachmentId)
        assertEquals("Athlete-specific cue",
            copied.days.single().slots.first().coachNote)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.duplicateBlock(
                original, 1, "base", "short", 2)
                as CustomCycleBuilderResult.Rejected).reason)
        assertEquals(CustomCycleBuilderRefusal.BLOCK_ID_CONFLICT,
            (CustomCycleBuilderDomain.duplicateBlock(
                original, 1, "base", "base")
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun reorderingUsesSlotIdentityEvenWhenSameExerciseAppearsTwice() {
        val before = draft()
        val accepted = accepted(CustomCycleBuilderDomain.reorderSlots(
            before, 1, "base", 0, listOf("bench-b", "bench-a")))
        val newDay = accepted.draft.blocks.single().days.single()
        assertEquals(listOf("bench-b", "bench-a"),
            newDay.slots.map { it.slotId })
        assertEquals(listOf("bench", "bench"),
            newDay.slots.map { it.exerciseId })
        assertTrue(newDay.slots.last().pinned)
        assertNotNull(newDay.slots.last().sets.last().amplifier)
        assertEquals(before.weekOverrides, accepted.draft.weekOverrides)
        assertEquals(CustomCycleBuilderRefusal.SLOT_ORDER_CONFLICT,
            (CustomCycleBuilderDomain.reorderSlots(
                before, 1, "base", 0, listOf("bench-a", "bench-a"))
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun oneSlotEditDoesNotOverwriteIdenticalExerciseInAnotherSlot() {
        val before = draft()
        val changed = accepted(CustomCycleBuilderDomain.replaceSlotSets(
            before, 1, "base", 0, "bench-a", listOf(set(200))))
        val slots = changed.draft.blocks.single().days.single().slots
        assertEquals(1, slots.first().sets.size)
        assertEquals(1, slots.last().sets.size)
        assertEquals(n(155),
            (slots.last().sets.single().load as DraftSetLoad.Verified).value)
        assertEquals(listOf(set(200)),
            changed.draft.weekOverrides["base"]!!.single().sets)
        assertEquals(2, before.blocks.single().days.single()
            .slots.first().sets.size)
    }

    @Test fun weekOverrideIsScopedAndMayBeRemovedWithoutTouchingBlock() {
        val original = draft()
        val edit = accepted(CustomCycleBuilderDomain.editWeek(
            original, 1, "base", 2, 0, "bench-b", listOf(set(185))))
        assertEquals(2, edit.draft.weekOverrides["base"]!!.size)
        val remove = accepted(CustomCycleBuilderDomain.editWeek(
            edit.draft, 2, "base", 2, 0, "bench-b", null))
        assertEquals(1, remove.draft.weekOverrides["base"]!!.size)
        assertEquals(3, remove.draft.weekOverrides["base"]!!.single().weekInBlock)
        assertEquals(original.blocks, remove.draft.blocks)
    }

    @Test fun staleEditAndStaleUndoCannotEraseAnotherAuthorChange() {
        val first = draft()
        val changed = accepted(CustomCycleBuilderDomain.replaceSlotSets(
            first, 1, "base", 0, "bench-b", listOf(set(200))))
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (CustomCycleBuilderDomain.replaceSlotSets(
                changed.draft, 1, "base", 0, "bench-b", listOf(set(185)))
                as CustomCycleBuilderResult.Rejected).reason)
        val later = accepted(CustomCycleBuilderDomain.reorderSlots(
            changed.draft, 2, "base", 0, listOf("bench-b", "bench-a")))
        assertEquals(CustomCycleBuilderRefusal.UNDO_CONFLICT,
            (CustomCycleBuilderDomain.undo(later.draft, changed.receipt)
                as CustomCycleBuilderResult.Rejected).reason)
        val undone = accepted(CustomCycleBuilderDomain.undo(
            later.draft, later.receipt))
        assertEquals(4L, undone.draft.revision)
        assertEquals(changed.draft.blocks, undone.draft.blocks)
        assertEquals(changed.draft.weekOverrides, undone.draft.weekOverrides)
    }

    @Test fun acceptedDraftIsUnaffectedByMutableInputAndUndoIsVersioned() {
        val mutable = mutableListOf(set(185))
        val before = draft()
        val created = accepted(CustomCycleBuilderDomain.replaceSlotSets(
            before, 1, "base", 0, "bench-b", mutable))
        mutable.clear()
        assertEquals(1, created.draft.blocks.single()
            .days.single().slots.last().sets.size)
        val undo = accepted(CustomCycleBuilderDomain.undo(
            created.draft, created.receipt))
        assertEquals(3L, undo.draft.revision)
        assertEquals(before.blocks, undo.draft.blocks)
        assertFalse(CustomTrainingCycleDomain.review(
            undo.draft, emptyList()).canIssueWorkout)
    }

    @Test fun fullCustomProgramCanBeBuiltFromBlankWithoutAnyGeneratedWeek() {
        val blank = CustomCycleBuilderDomain.blank(
            "blank-cycle", "athlete", AthleteTrainingGoal.POWERBUILDING,
            "Made from scratch", "phase-1", 4, TrainingPhase.HYPERTROPHY,
            CustomSplitIdentity.Freeform("Chest / Back / Legs"))!!
        val first = accepted(CustomCycleBuilderDomain.addDay(
            blank, 1, "phase-1", CustomTrainingDay(
                0, "Chest", ComposerSessionPurpose.HYPERTROPHY, emptyList())))
        val exercise = CustomExerciseSlot(
            "bench-main", "bench", ComposerRole.PRIMARY, listOf(set(185)),
            pinned = true, coachNote = "Custom user instructions")
        val second = accepted(CustomCycleBuilderDomain.addSlot(
            first.draft, 2, "phase-1", 0, exercise))
        val third = accepted(CustomCycleBuilderDomain.addSlot(
            second.draft, 3, "phase-1", 0,
            exercise.copy(slotId = "bench-second", pinned = false,
                sets = listOf(set(155))), position = 0))
        assertEquals(4L, third.draft.revision)
        assertEquals(listOf("bench-second", "bench-main"),
            third.draft.blocks.single().days.single().slots.map { it.slotId })
        val available = listOf(CustomExerciseAvailability(
            "bench", VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.SMITH_TOTAL, listOf(n(155), n(185))), false))
        val review = CustomTrainingCycleDomain.review(third.draft, available)
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW, review.status)
        assertNotNull(review.verifiedDraft)
        assertEquals("Chest / Back / Legs",
            (review.verifiedDraft!!.blocks.single().split
                as CustomSplitIdentity.Freeform).label)
        assertFalse(review.canIssueWorkout)
    }

    @Test fun removingPinnedSlotNeedsConfirmationAndCleansOnlyDanglingOverride() {
        val first = draft()
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.removeSlot(
                first, 1, "base", 0, "bench-a")
                as CustomCycleBuilderResult.Rejected).reason)
        val after = accepted(CustomCycleBuilderDomain.removeSlot(
            first, 1, "base", 0, "bench-a", confirmPinned = true))
        assertEquals(listOf("bench-b"),
            after.draft.blocks.single().days.single().slots.map { it.slotId })
        assertEquals(emptyList<CustomWeekSlotOverride>(),
            after.draft.weekOverrides["base"])
        assertEquals(1, first.weekOverrides["base"]!!.size)
        assertEquals(CustomCycleBuilderRefusal.MISSING_SLOT,
            (CustomCycleBuilderDomain.removeSlot(
                after.draft, 2, "base", 0, "bench-a", confirmPinned = true)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun cannotAddDuplicateWeekdayOrSlot() {
        val input = draft()
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.addDay(
                input, 1, "base", CustomTrainingDay(
                    0, "Duplicate", ComposerSessionPurpose.HYPERTROPHY,
                    emptyList()))
                as CustomCycleBuilderResult.Rejected).reason)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.addSlot(
                input, 1, "base", 0, input.blocks.single()
                    .days.single().slots.first())
                as CustomCycleBuilderResult.Rejected).reason)
    }


    @Test fun crossDayMoveKeepsEveryAuthoredFieldAndWeekOverride() {
        val starting = draft()
        val expanded = accepted(CustomCycleBuilderDomain.addDay(
            starting, 1, "base", CustomTrainingDay(
                2, "Pull", ComposerSessionPurpose.HYPERTROPHY, emptyList())))
        val moved = accepted(CustomCycleBuilderDomain.moveSlots(
            expanded.draft, 2, "base", 0, 2, listOf("bench-a"), 0))
        val days = moved.draft.blocks.single().days
        assertEquals(listOf("bench-b"), days.first().slots.map { it.slotId })
        val movedSlot = days.last().slots.single()
        assertEquals("bench-a", movedSlot.slotId)
        assertTrue(movedSlot.pinned)
        assertEquals("straight-bar", movedSlot.attachmentId)
        assertEquals("Athlete-specific cue", movedSlot.coachNote)
        assertEquals(CustomAmplifierKind.DROP_SET,
            movedSlot.sets.last().amplifier?.kind)
        assertEquals(2, moved.draft.weekOverrides["base"]!!.single().weekday)
        assertEquals("bench-a", moved.draft.weekOverrides["base"]!!.single().slotId)
        assertEquals(3L, moved.draft.revision)
        assertEquals(0, starting.weekOverrides["base"]!!.single().weekday)
        val availability = listOf(CustomExerciseAvailability(
            "bench", VerifiedLoadStock.Totals(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                listOf(n(135), n(155), n(185), n(200))),
            false, attachmentIds = setOf("straight-bar")))
        val reviewed = CustomTrainingCycleDomain.review(moved.draft, availability)
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW, reviewed.status)
        val thirdWeek = CustomTrainingCycleDomain.week(reviewed, 3)!!
        val scheduledSlot = thirdWeek.days.first { it.weekday == 2 }
            .slots.single()
        assertEquals("bench-a", scheduledSlot.slotId)
        assertEquals(n(200),
            (scheduledSlot.sets.single().load as DraftSetLoad.Verified).value)
        val secondWeek = CustomTrainingCycleDomain.week(reviewed, 2)!!
        assertEquals(CustomAmplifierKind.DROP_SET,
            secondWeek.days.first { it.weekday == 2 }.slots.single()
                .sets.last().amplifier?.kind)
        val undo = accepted(CustomCycleBuilderDomain.undo(
            moved.draft, moved.receipt))
        assertEquals(expanded.draft.blocks, undo.draft.blocks)
        assertEquals(expanded.draft.weekOverrides, undo.draft.weekOverrides)
    }

    @Test fun supersetCannotBeSeparatedOrMergedByCrossDayMove() {
        val original = draft()
        val joined = original.copy(blocks = original.blocks.map { block ->
            block.copy(days = block.days.map { day ->
                day.copy(slots = day.slots.map {
                    it.copy(supersetGroup = "pair")
                })
            } + CustomTrainingDay(3, "Another day",
                ComposerSessionPurpose.HYPERTROPHY, emptyList()))
        })
        val bad = CustomCycleBuilderDomain.moveSlots(
            joined, 1, "base", 0, 3, listOf("bench-a"), 0)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (bad as CustomCycleBuilderResult.Rejected).reason)
        val valid = accepted(CustomCycleBuilderDomain.moveSlots(
            joined, 1, "base", 0, 3, listOf("bench-b", "bench-a"), 0))
        assertTrue(valid.draft.blocks.single().days.first().slots.isEmpty())
        assertEquals(listOf("bench-a", "bench-b"),
            valid.draft.blocks.single().days.last().slots.map { it.slotId })
        assertEquals(setOf("pair"), valid.draft.blocks.single()
            .days.last().slots.mapNotNull { it.supersetGroup }.toSet())
        val duplicateInDestination = joined.copy(blocks =
            joined.blocks.map { block ->
                block.copy(days = block.days.map { day ->
                    if (day.weekday == 3) day.copy(slots =
                        listOf(original.blocks.single().days.single()
                            .slots.first().copy(supersetGroup = null)))
                    else day
                })
            })
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.moveSlots(
                duplicateInDestination, 1, "base", 0, 3,
                listOf("bench-a", "bench-b"), 0)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun blockTimelineCanBeExtendedAndReorderedWithoutDroppingEdits() {
        val first = draft()
        val nextBlock = CustomTrainingBlock(
            "strength-block", "Strength phase", TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING, 5,
            CustomSplitIdentity.Freeform("Bench priority"), emptyList())
        val inserted = accepted(CustomCycleBuilderDomain.insertBlock(
            first, 1, nextBlock, 0))
        assertEquals(listOf("strength-block", "base"),
            inserted.draft.blocks.map { it.blockId })
        assertEquals(first.weekOverrides, inserted.draft.weekOverrides)
        val reordered = accepted(CustomCycleBuilderDomain.reorderBlocks(
            inserted.draft, 2, listOf("base", "strength-block")))
        assertEquals(first.blocks.single(), reordered.draft.blocks.first())
        assertEquals(first.weekOverrides, reordered.draft.weekOverrides)
        assertEquals(CustomCycleBuilderRefusal.BLOCK_ID_CONFLICT,
            (CustomCycleBuilderDomain.reorderBlocks(
                reordered.draft, 3, listOf("base", "base"))
                as CustomCycleBuilderResult.Rejected).reason)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.insertBlock(
                reordered.draft, 3, nextBlock)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun dayNamesCanChangeButScheduleAndOverridesCannotSilentlyChange() {
        val first = draft()
        val edited = accepted(CustomCycleBuilderDomain.renameDay(
            first, 1, "base", 0, "Chest & Triceps"))
        assertEquals("Chest & Triceps",
            edited.draft.blocks.single().days.single().label)
        assertEquals(first.weekOverrides, edited.draft.weekOverrides)
        assertEquals(first.blocks.single().days.single().slots,
            edited.draft.blocks.single().days.single().slots)
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (CustomCycleBuilderDomain.renameDay(
                edited.draft, 1, "base", 0, "Old edit")
                as CustomCycleBuilderResult.Rejected).reason)
    }


    @Test fun authorMayRenameProgramAndEditBlockWithoutTouchingIndividualSets() {
        val input = draft()
        val first = accepted(CustomCycleBuilderDomain.editCycleMetadata(
            input, 1, "Custom powerbuilding", "Keep my selected movements"))
        val changed = accepted(CustomCycleBuilderDomain.editBlockMetadata(
            first.draft, 2, "base", "My strength phase",
            TrainingPhase.STRENGTH, CustomBlockPurpose.TRAINING, 5,
            CustomSplitIdentity.Freeform("Power day / Hypertrophy day")))
        val block = changed.draft.blocks.single()
        assertEquals("My strength phase", block.label)
        assertEquals(TrainingPhase.STRENGTH, block.phase)
        assertEquals(5, block.weeks)
        assertEquals("Custom powerbuilding", changed.draft.title)
        assertEquals("Keep my selected movements", changed.draft.authorNote)
        assertEquals(input.blocks.single().days, block.days)
        assertEquals(input.weekOverrides, changed.draft.weekOverrides)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.editBlockMetadata(
                first.draft, 2, "base", "Invalid",
                TrainingPhase.STRENGTH, CustomBlockPurpose.TRAINING, 2,
                CustomSplitIdentity.Freeform("My split"))
                as CustomCycleBuilderResult.Rejected).reason)
        val reversed = accepted(CustomCycleBuilderDomain.undo(
            changed.draft, changed.receipt))
        assertEquals(first.draft.blocks, reversed.draft.blocks)
        assertEquals(first.draft.weekOverrides, reversed.draft.weekOverrides)
    }

    @Test fun firstCustomPhaseAndSessionPurposeAreExplicitlyAuthored() {
        val blank = CustomCycleBuilderDomain.blank(
            "new-cycle", "athlete", AthleteTrainingGoal.POWERBUILDING,
            "My unique split", "first", 6, TrainingPhase.HYPERTROPHY,
            CustomSplitIdentity.Freeform("Chest and back"),
            firstBlockLabel = "My foundational block")!!
        assertEquals("My foundational block", blank.blocks.single().label)
        val added = accepted(CustomCycleBuilderDomain.addDay(
            blank, 1, "first", CustomTrainingDay(
                4, "Friday power", ComposerSessionPurpose.HYPERTROPHY,
                emptyList())))
        val changed = accepted(CustomCycleBuilderDomain.changeDayPurpose(
            added.draft, 2, "first", 4, ComposerSessionPurpose.STRENGTH_SKILL))
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,
            changed.draft.blocks.single().days.single().purpose)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.changeDayPurpose(
                changed.draft, 3, "first", 4,
                ComposerSessionPurpose.UNSPECIFIED)
                as CustomCycleBuilderResult.Rejected).reason)
    }


    @Test fun oneDayMetadataReceiptAtomicallyKeepsEveryCustomSetAndOverride() {
        val original = draft()
        val changed = accepted(CustomCycleBuilderDomain.editDayMetadata(
            original, 1, "base", 0, "Upper power",
            ComposerSessionPurpose.STRENGTH_SKILL))
        val before = original.blocks.single().days.single()
        val after = changed.draft.blocks.single().days.single()
        assertEquals("Upper power", after.label)
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL, after.purpose)
        assertEquals(before.slots, after.slots)
        assertEquals(original.weekOverrides, changed.draft.weekOverrides)
        assertEquals(2L, changed.draft.revision)
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (CustomCycleBuilderDomain.editDayMetadata(
                changed.draft, 1, "base", 0, "Stale",
                ComposerSessionPurpose.HYPERTROPHY)
                as CustomCycleBuilderResult.Rejected).reason)
        val undone = accepted(CustomCycleBuilderDomain.undo(
            changed.draft, changed.receipt))
        assertEquals(before, undone.draft.blocks.single().days.single())
    }

}
