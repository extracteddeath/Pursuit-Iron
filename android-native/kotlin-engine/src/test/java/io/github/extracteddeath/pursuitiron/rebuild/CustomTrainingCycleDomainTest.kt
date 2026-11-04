package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomTrainingCycleDomainTest {
    private fun n(i: Int) = BigDecimal(i)
    private fun stock() = VerifiedLoadStock.Totals(
        LoadUnit.LB, LoadBasis.SMITH_TOTAL,
        listOf(n(95), n(105), n(135), n(155), n(175), n(185), n(200), n(205)))
    private fun available() = listOf(
        CustomExerciseAvailability("bench", stock(), false),
        CustomExerciseAvailability("row", null, true,
            setOf("rope", "straight-bar")),
        CustomExerciseAvailability("press", stock(), false)
    )
    private fun weighted(
        load: Int, reps: Int = 8,
        amplifier: CustomSetAmplifier? = null
    ) = CustomAuthoredSet(
        DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(load)),
        RepBand(reps, reps + 2), 2, 45, 110, amplifier)
    private fun bodyweight() = CustomAuthoredSet(
        DraftSetType.WORKING, DraftSetLoad.ExplicitBodyweight,
        RepBand(8, 12), 2, 50, 65)
    private fun benchSlot(
        sets: List<CustomAuthoredSet> = listOf(weighted(185), weighted(185)),
        slotId: String = "main-press", pinned: Boolean = true
    ) = CustomExerciseSlot(slotId, "bench", ComposerRole.PRIMARY, sets,
        pinned = pinned, coachNote = "Keep shoulders stable")
    private fun rowSlot() = CustomExerciseSlot(
        "pull-1", "row", ComposerRole.SECONDARY,
        listOf(bodyweight()), attachmentId = "rope")
    private fun block(
        id: String = "base",
        weeks: Int = 4,
        phase: TrainingPhase = TrainingPhase.HYPERTROPHY,
        days: List<CustomTrainingDay> = listOf(
            CustomTrainingDay(0, "Upper A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(), rowSlot())),
            CustomTrainingDay(2, "Full body B", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(slotId = "main-press-b")))
        )
    ) = CustomTrainingBlock(
        id, "My custom $id", phase, CustomBlockPurpose.TRAINING, weeks,
        CustomSplitIdentity.Freeform("Upper / Full / Custom"), days)

    private fun cycle(
        blocks: List<CustomTrainingBlock> = listOf(
            block(), block("strength", 3, TrainingPhase.STRENGTH, listOf(
                CustomTrainingDay(0, "Strength A",
                    ComposerSessionPurpose.STRENGTH_SKILL,
                    listOf(benchSlot(listOf(weighted(200, 4),
                        weighted(185, 5), weighted(185, 5)))))
            )))
    ) = CustomTrainingCycle(
        "custom-cycle", 1, "athlete-1", AthleteTrainingGoal.POWERBUILDING,
        "My exact program", blocks)

    private fun reviewed(input: CustomTrainingCycle) =
        CustomTrainingCycleDomain.review(input, available())
    private fun valid(input: CustomTrainingCycle) =
        reviewed(input).also {
            assertEquals(it.issues.toString(),
                CustomCycleReviewStatus.READY_FOR_COACH_REVIEW, it.status)
        }

    @Test fun trulyCustomCycleKeepsWeekdayOrderAndExerciseOrderWithoutComposer() {
        val original = cycle()
        val reviewed = valid(original)
        assertFalse(reviewed.canIssueWorkout)
        assertFalse(reviewed.canPublishProgram)
        assertEquals(7, reviewed.totalWeeks)
        val first = checkNotNull(CustomTrainingCycleDomain.week(reviewed, 1))
        assertEquals("base", first.blockId)
        assertEquals(CustomSplitIdentity.Freeform("Upper / Full / Custom"),
            first.split)
        assertEquals(listOf(0, 2), first.days.map { it.weekday })
        assertEquals(listOf("bench", "row"),
            first.days.first().slots.map { it.exerciseId })
        assertEquals(listOf("main-press", "pull-1"),
            first.days.first().slots.map { it.slotId })
        assertEquals(2, first.days.first().slots.first().sets.size)
        val strength = checkNotNull(CustomTrainingCycleDomain.week(reviewed, 5))
        assertEquals("strength", strength.blockId)
        assertEquals(TrainingPhase.STRENGTH, strength.phase)
        assertEquals(1, strength.weekInBlock)
        assertEquals(3, strength.days.first().slots.first().sets.size)
        assertEquals(n(200),
            (strength.days.first().slots.first().sets.first().load
                as DraftSetLoad.Verified).value)
        assertNull(CustomTrainingCycleDomain.week(reviewed, 8))
    }

    @Test fun weekOverrideOnlyChangesIntendedSlotAndWeek() {
        val initial = cycle()
        val change = CustomWeekSlotOverride(
            3, 0, "main-press", listOf(weighted(200), weighted(200),
                weighted(185)))
        val withOverride = initial.copy(
            weekOverrides = mapOf("base" to listOf(change)))
        val review = valid(withOverride)
        val second = checkNotNull(CustomTrainingCycleDomain.week(review, 2))
        val third = checkNotNull(CustomTrainingCycleDomain.week(review, 3))
        val fourth = checkNotNull(CustomTrainingCycleDomain.week(review, 4))
        assertEquals(2, second.days.first().slots.first().sets.size)
        assertEquals(3, third.days.first().slots.first().sets.size)
        assertEquals(2, fourth.days.first().slots.first().sets.size)
        assertEquals("row", third.days.first().slots.last().exerciseId)
        assertEquals(2, review.verifiedDraft!!.blocks.first()
            .days.first().slots.first().sets.size)
        assertNull(CustomTrainingCycleDomain.week(review, 0))
    }

    @Test fun customAmplifierAndRestAreNotLostAcrossBlocks() {
        val amplifier = CustomSetAmplifier(CustomAmplifierKind.DROP_SET, 8, 20,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(135)))
        val withDrop = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(listOf(weighted(185, 8, amplifier)))))
        ))))
        val result = valid(withDrop)
        val restored = CustomTrainingCycleDomain.week(result, 1)!!
            .days.single().slots.single().sets.single()
        assertEquals(amplifier, restored.amplifier)
        assertEquals(110, restored.restSeconds)
        assertEquals(20, restored.amplifier!!.followUpRestSeconds)
    }

    @Test fun unverifiedLoadAndImpossibleDropWeightAreRejected() {
        val missing = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(listOf(weighted(199)))))
        ))))
        assertTrue(reviewed(missing).issues.any {
            it.kind == CustomCycleIssueKind.INATTAINABLE_LOAD
        })
        val impossibleDrop = CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            5, 15, DraftSetLoad.Verified(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(205)))
        val invalid = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(listOf(weighted(185, 8, impossibleDrop)))))
        ))))
        assertTrue(reviewed(invalid).issues.any {
            it.kind == CustomCycleIssueKind.INVALID_SET
        })
        assertNull(CustomTrainingCycleDomain.week(reviewed(invalid), 1))
    }

    @Test fun missingEquipmentAttachmentAndBodyweightPermissionFailClosed() {
        val custom = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(), rowSlot().copy(attachmentId = "unavailable")))
        ))))
        assertTrue(reviewed(custom).issues.any {
            it.kind == CustomCycleIssueKind.UNAVAILABLE_ATTACHMENT
        })
        val denied = CustomTrainingCycleDomain.review(custom.copy(
            blocks = listOf(block(days = listOf(
                CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                    listOf(rowSlot().copy(attachmentId = null)))
            )))
        ), listOf(CustomExerciseAvailability("row", null, false)))
        assertTrue(denied.issues.any {
            it.kind == CustomCycleIssueKind.UNAVAILABLE_EXERCISE
        })
        assertNull(denied.verifiedDraft)
    }

    @Test fun invalidAndDuplicateWeekOverridesNeverSilentlyApply() {
        val input = cycle()
        val value = CustomWeekSlotOverride(3, 0, "main-press",
            listOf(weighted(200)))
        val duplicate = input.copy(weekOverrides =
            mapOf("base" to listOf(value, value.copy(sets =
                listOf(weighted(175))))))
        assertTrue(reviewed(duplicate).issues.any {
            it.kind == CustomCycleIssueKind.INVALID_WEEK_OVERRIDE
        })
        val missingSlot = input.copy(weekOverrides =
            mapOf("base" to listOf(value.copy(slotId = "not-present"))))
        assertTrue(reviewed(missingSlot).issues.any {
            it.kind == CustomCycleIssueKind.INVALID_WEEK_OVERRIDE
        })
        val unknownBlock = input.copy(weekOverrides =
            mapOf("not-a-block" to listOf(value)))
        assertTrue(reviewed(unknownBlock).issues.any {
            it.kind == CustomCycleIssueKind.INVALID_WEEK_OVERRIDE
        })
    }

    @Test fun threeWaySupersetAllowedSingletonOrFourNotAllowed() {
        val a = benchSlot().copy(supersetGroup = "pair-1")
        val b = rowSlot().copy(supersetGroup = "pair-1")
        val one = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(a))
        ))))
        assertTrue(reviewed(one).issues.any {
            it.kind == CustomCycleIssueKind.INVALID_SUPERSET
        })
        val pair = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "A", ComposerSessionPurpose.HYPERTROPHY,
                listOf(a, b))
        ))))
        valid(pair)
    }

    @Test fun verifiedDraftIsolatedFromMutableCallerLists() {
        val list = mutableListOf(weighted(185), weighted(185))
        val mutableSlots = mutableListOf(benchSlot(list))
        val days = mutableListOf(CustomTrainingDay(
            0, "A", ComposerSessionPurpose.HYPERTROPHY, mutableSlots))
        val blocks = mutableListOf(block(days = days))
        val original = cycle(blocks)
        val review = valid(original)
        list.clear()
        mutableSlots.clear()
        days.clear()
        blocks.clear()
        val restored = CustomTrainingCycleDomain.week(review, 1)!!
        assertEquals(2, restored.days.single().slots.single().sets.size)
        assertEquals("bench", restored.days.single().slots.single().exerciseId)
        assertEquals("base", restored.blockId)
    }

    @Test fun blankCustomSessionAndBadTitlesReturnActionableIssues() {
        val input = cycle(listOf(block(days = listOf(
            CustomTrainingDay(0, "", ComposerSessionPurpose.UNSPECIFIED,
                emptyList())
        ))))
        val result = reviewed(input)
        assertEquals(CustomCycleReviewStatus.UNRESOLVED, result.status)
        assertNull(result.verifiedDraft)
        assertTrue(result.issues.any { it.kind == CustomCycleIssueKind.INVALID_DAY })
        assertTrue(result.issues.any { it.kind == CustomCycleIssueKind.EMPTY_SESSION })
    }
}
