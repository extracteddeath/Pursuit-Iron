package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomCycleTransitionDomainTest {
    private fun n(i: Int) = BigDecimal(i)
    private val inventory = listOf(
        CustomExerciseAvailability("bench",
            VerifiedLoadStock.Totals(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                listOf(n(95), n(105), n(135), n(155), n(185), n(200))), false),
        CustomExerciseAvailability("press",
            VerifiedLoadStock.Totals(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                listOf(n(95), n(105), n(135), n(155), n(185), n(200))), false)
    )
    private fun set(
        load: Int = 185, reps: Int = 8, rir: Int = 2, rest: Int = 110,
        amp: CustomSetAmplifier? = null
    ) = CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(load)),
        RepBand(reps, reps + 2), rir, 45, rest, amp)
    private fun slot(exercise: String = "bench") =
        CustomExerciseSlot("main", exercise, ComposerRole.PRIMARY,
            listOf(set(), set()), pinned = true)
    private fun cycle(exercise: String = "bench") =
        CustomTrainingCycle(
            "custom-1", 3, "athlete-1", AthleteTrainingGoal.POWERBUILDING,
            "Hand-built cycle", listOf(CustomTrainingBlock(
                "build", "Build", TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING, 4,
                CustomSplitIdentity.Freeform("My split"),
                listOf(CustomTrainingDay(0, "Upper A",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(slot(exercise))))
            ))
        )
    private fun transition(
        original: CustomTrainingCycle,
        proposal: CustomTrainingCycle,
        permissions: CustomCycleCoachPermissions = CustomCycleCoachPermissions(),
        origin: CustomCycleEditOrigin = CustomCycleEditOrigin.COACH_SUGGESTION
    ) = CustomCycleTransitionDomain.review(
        original, proposal, inventory, origin, permissions)

    @Test fun autoCoachCannotReplacePinnedCustomMovementJustByChangingPhase() {
        val original = cycle()
        val base = original.blocks.single()
        val changed = original.copy(revision = 4, blocks = listOf(
            base.copy(phase = TrainingPhase.STRENGTH, days = listOf(
                base.days.single().copy(slots = listOf(slot("press")))
            ))
        ))
        val denied = transition(original, changed,
            CustomCycleCoachPermissions(adaptPhase = true, adaptExercises = true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED, denied.status)
        assertEquals(setOf(CustomCycleChangeKind.REMOVE_PINNED),
            denied.blockedKinds)
        assertTrue(denied.differences.any {
            it.kind == CustomCycleChangeKind.EXERCISES
        })
        val allowed = transition(original, changed,
            CustomCycleCoachPermissions(adaptPhase = true,
                adaptExercises = true, removePinned = true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        assertFalse(allowed.mayApplyAutomatically)
    }

    @Test fun volumeRepsRirRestLoadsAndAmplifierAreIndependentPermissions() {
        val original = cycle()
        val initialBlock = original.blocks.single()
        val originalDay = initialBlock.days.single()
        val amplifier = CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            6, 15, DraftSetLoad.Verified(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(135)))
        val modified = slot().copy(sets = listOf(
            set(load = 200, reps = 6, rir = 1, rest = 140, amp = amplifier),
            set(load = 155)))
        val changed = original.copy(revision = 4, blocks =
            listOf(initialBlock.copy(days = listOf(
                originalDay.copy(slots = listOf(modified))))))
        val denial = transition(original, changed)
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED, denial.status)
        assertTrue(denial.blockedKinds.containsAll(setOf(
            CustomCycleChangeKind.LOADS, CustomCycleChangeKind.REPS,
            CustomCycleChangeKind.RIR, CustomCycleChangeKind.REST,
            CustomCycleChangeKind.AMPLIFIERS)))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original, changed, CustomCycleCoachPermissions(
                adaptLoads = true, adaptReps = true, adaptRir = true,
                adaptRest = true, adaptAmplifiers = true)).status)
    }

    @Test fun weeklyOverridesCannotChangeWithoutSeparatePermission() {
        val original = cycle()
        val changed = original.copy(revision = 4, weekOverrides =
            mapOf("build" to listOf(CustomWeekSlotOverride(
                2, 0, "main", listOf(set(load = 200), set(load = 200))
            ))))
        val refused = transition(original, changed)
        assertTrue(CustomCycleChangeKind.WEEK_OVERRIDES in refused.blockedKinds)
        val allowed = transition(original, changed,
            CustomCycleCoachPermissions(adaptWeekOverrides = true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        val oldWeek = CustomTrainingCycleDomain.week(
            CustomTrainingCycleDomain.review(original, inventory), 2)!!
        val newWeek = CustomTrainingCycleDomain.week(
            allowed.proposedReview, 2)!!
        assertEquals(n(185),
            (oldWeek.days.single().slots.single().sets.first().load
                as DraftSetLoad.Verified).value)
        assertEquals(n(200),
            (newWeek.days.single().slots.single().sets.first().load
                as DraftSetLoad.Verified).value)
    }

    @Test fun userCanManuallyEditWithoutCoachPermissionButStillNeedsReview() {
        val old = cycle()
        val candidate = old.copy(revision = 4, blocks = listOf(
            old.blocks.single().copy(weeks = 5,
                split = CustomSplitIdentity.Freeform("My own split B"))
        ))
        val reviewed = transition(old, candidate,
            origin = CustomCycleEditOrigin.ATHLETE_EDIT)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertTrue(reviewed.blockedKinds.isEmpty())
        assertTrue(reviewed.differences.any {
            it.kind == CustomCycleChangeKind.SPLIT
        })
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayModifyHistory)
    }

    @Test fun staleRevisionAndInvalidExerciseAlwaysFailEvenForAthleteEdit() {
        val old = cycle()
        val badRevision = old.copy(title = "Changed but stale")
        assertEquals(CustomCycleTransitionStatus.STALE_REVISION,
            transition(old, badRevision,
                origin = CustomCycleEditOrigin.ATHLETE_EDIT).status)
        val incompatible = old.copy(revision = 4, blocks = listOf(
            old.blocks.single().copy(days = listOf(
                old.blocks.single().days.single().copy(
                    slots = listOf(slot("unknown-exercise"))
                )
            ))
        ))
        assertEquals(CustomCycleTransitionStatus.INVALID_SOURCE_OR_CANDIDATE,
            transition(old, incompatible,
                origin = CustomCycleEditOrigin.ATHLETE_EDIT).status)
        assertFalse(transition(old, incompatible,
            origin = CustomCycleEditOrigin.ATHLETE_EDIT)
            .mayApplyAutomatically)
    }

    @Test fun unchangedCycleIsNotMistakenForAnIssuedNewProgram() {
        val original = cycle()
        val proposal = original.copy(revision = 4)
        val result = transition(original, proposal)
        assertEquals(CustomCycleTransitionStatus.NO_CHANGE, result.status)
        assertTrue(result.differences.isEmpty())
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun droppingPinnedSessionRequiresExplicitDestructivePermission() {
        val original = cycle()
        val newDay = CustomTrainingDay(2, "Other day",
            ComposerSessionPurpose.HYPERTROPHY, listOf(slot("press")))
        val proposal = original.copy(revision = 4, blocks = listOf(
            original.blocks.single().copy(days = listOf(newDay))))
        val review = transition(original, proposal,
            CustomCycleCoachPermissions(adaptSchedule = true,
                adaptExercises = true))
        assertTrue(CustomCycleChangeKind.REMOVE_PINNED in review.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            review.status)
    }
}
