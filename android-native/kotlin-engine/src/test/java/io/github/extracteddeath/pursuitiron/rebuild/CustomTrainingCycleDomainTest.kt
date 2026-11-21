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

    @Test fun savedProgressionMustPassOneCanonicalReviewBeforeDraftIsAccepted() {
        val starting=benchSlot().copy(
            progressionMethod=SavedProgressionMethod.WAVE)
        val strength=block(phase=TrainingPhase.STRENGTH,days=listOf(
            CustomTrainingDay(0,"Strength A",ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(starting))))
        val program=cycle(listOf(strength))
        // A four-week Wave cannot claim a saved progression with only
        // one later week's load authored. The program must fail *here*,
        // before an athlete is allowed to sign or issue it.
        val missing=program.copy(weekOverrides=mapOf("base" to listOf(
            CustomWeekSlotOverride(2,0,"main-press",
                listOf(weighted(200),weighted(200))))))
        val denied=reviewed(missing)
        assertEquals(CustomCycleReviewStatus.UNRESOLVED,denied.status)
        assertNull(denied.verifiedDraft)
        assertTrue(denied.issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SLOT &&
                it.location=="progression" && it.explanation.contains("Wave")
        })
        val full=program.copy(weekOverrides=mapOf("base" to listOf(
            CustomWeekSlotOverride(2,0,"main-press",
                listOf(weighted(200),weighted(200))),
            CustomWeekSlotOverride(3,0,"main-press",
                listOf(weighted(175),weighted(175))),
            CustomWeekSlotOverride(4,0,"main-press",
                listOf(weighted(205),weighted(205)))
        )))
        assertEquals(SavedProgressionMethod.WAVE,
            valid(full).verifiedDraft!!.blocks.single().days.single()
                .slots.single().progressionMethod)
        // A bodyweight movement is a valid authored source, but it can
        // never carry a specialist estimated-barbell-max method.
        val impossible=cycle(listOf(block(
            phase=TrainingPhase.STRENGTH,days=listOf(
                CustomTrainingDay(0,"Pull",ComposerSessionPurpose.STRENGTH_SKILL,
                    listOf(rowSlot().copy(role=ComposerRole.PRIMARY,
                        progressionMethod=SavedProgressionMethod.E1RM)))))))
        val noMax=reviewed(impossible)
        assertTrue(noMax.issues.toString(),noMax.issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SLOT &&
                it.location=="progression"
        })
        assertNull(noMax.verifiedDraft)
        // Valid hand-authored mixed strength schemes with NO named
        // progression remain first-class, not silently flattened.
        valid(cycle())
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

    @Test fun restPauseStageIntentSurvivesCustomCycleReviewWithoutInventingLoads() {
        fun program(amp:CustomSetAmplifier)=cycle(listOf(block(days=listOf(
            CustomTrainingDay(0,"A",ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(listOf(weighted(185,8,amp)))))
        ))))
        val authored=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        val ready=valid(program(authored))
        val restored=checkNotNull(CustomTrainingCycleDomain.week(ready,1))
            .days.single().slots.single().sets.single()
        assertEquals(authored,restored.amplifier)
        assertNull(restored.amplifier?.followUpLoad)
        assertTrue(reviewed(program(authored.copy(followUpRounds=4)))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(authored.copy(followUpLoad=
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(135)))))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
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
    @Test fun stagedClustersSurviveCustomCycleAndFailClosedOnUnverifiedOrUnplannedPatterns() {
        fun program(amp:CustomSetAmplifier)=cycle(listOf(block(days=listOf(
            CustomTrainingDay(0,"Quality",ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(benchSlot(listOf(weighted(185,4,amp)))))
        ))))
        val authored=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,
            2,30,followUpRounds=4)
        val ready=valid(program(authored))
        val restored=checkNotNull(CustomTrainingCycleDomain.week(ready,1))
            .days.single().slots.single().sets.single()
        assertEquals(authored,restored.amplifier)
        assertNull(restored.amplifier?.followUpLoad)
        assertEquals(4,restored.amplifier?.followUpRounds)
        assertTrue(reviewed(program(authored.copy(followUpRounds=6)))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(authored.copy(followUpReps=5)))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(authored.copy(followUpRestSeconds=14)))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(authored.copy(followUpLoad=
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(155)))))
            .issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
    }

    @Test fun lengthenedPartialsAreOnlyAllowedOnFinalWorkingSetAndNeverExtraFullSet() {
        val partials=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,
            3,0,followUpActiveSeconds=15)
        fun proposed(sets:List<CustomAuthoredSet>)=cycle(listOf(block(days=listOf(
            CustomTrainingDay(0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(sets)))
        ))))
        val regular=weighted(185)
        val last=weighted(185,amplifier=partials)
        val review=valid(proposed(listOf(regular,last)))
        val week=checkNotNull(CustomTrainingCycleDomain.week(review,1))
        val actual=week.days.single().slots.single().sets
        assertNull(actual.first().amplifier)
        assertEquals(partials,actual.last().amplifier)
        assertEquals(2,actual.count{it.type==DraftSetType.WORKING})
        assertEquals(3,actual.last().amplifier?.followUpReps)
        assertTrue(reviewed(proposed(listOf(last,regular))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET
        })
        assertTrue(reviewed(proposed(listOf(
            regular,weighted(185,amplifier=partials.copy(followUpRounds=2))
        ))).issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(proposed(listOf(
            regular,weighted(185,amplifier=partials.copy(followUpRestSeconds=10))
        ))).issues.any{it.kind==CustomCycleIssueKind.INVALID_SET})
        val warmup=weighted(185,amplifier=partials).copy(type=DraftSetType.WARMUP)
        assertTrue(reviewed(proposed(listOf(warmup,regular))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET
        })
        val override=proposed(listOf(regular,last)).copy(
            weekOverrides=mapOf("base" to listOf(
                CustomWeekSlotOverride(2,0,"main-press",listOf(last,regular)))))
        assertTrue(reviewed(override).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET
        })
    }

    @Test fun twoDropCycleKeepsIndependentVerifiedPhysicalLoadsAcrossWeeks() {
        fun load(n:Int)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.SMITH_TOTAL,BigDecimal(n))
        val two=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            8,10,followUpLoad=load(155),followUpRounds=2,
            secondDropLoad=load(135))
        fun program(amp:CustomSetAmplifier)=cycle(listOf(block(days=listOf(
            CustomTrainingDay(0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
                listOf(benchSlot(listOf(weighted(185,8,amp)))))
        ))))
        val reviewed=valid(program(two))
        val observed=checkNotNull(CustomTrainingCycleDomain.week(reviewed,1))
            .days.single().slots.single().sets.single().amplifier
        assertEquals(two,observed)
        assertEquals(load(155),observed?.followUpLoad)
        assertEquals(load(135),observed?.secondDropLoad)
        assertEquals(2,observed?.followUpRounds)
        assertEquals(observed,CustomTrainingCycleDomain.week(reviewed,3)
            ?.days?.single()?.slots?.single()?.sets?.single()?.amplifier)
        assertTrue(reviewed(program(two.copy(secondDropLoad=null))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(two.copy(secondDropLoad=load(155)))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(two.copy(secondDropLoad=load(175)))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(two.copy(secondDropLoad=load(150)))).issues.any{
            it.kind==CustomCycleIssueKind.INATTAINABLE_LOAD})
        assertTrue(reviewed(program(two.copy(followUpRounds=3))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET})
        assertTrue(reviewed(program(two.copy(followUpRestSeconds=20))).issues.any{
            it.kind==CustomCycleIssueKind.INVALID_SET})
        val one=two.copy(followUpRounds=1,secondDropLoad=null)
        val historic=valid(program(one))
        assertEquals(one,CustomTrainingCycleDomain.week(historic,1)
            ?.days?.single()?.slots?.single()?.sets?.single()?.amplifier)
    }

}
