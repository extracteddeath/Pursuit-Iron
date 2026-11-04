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

}
