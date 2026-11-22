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

    @Test fun exactRepTargetAndSavedMethodAreSeparateReviewedChanges() {
        val original=cycle()
        val block=original.blocks.single()
        val day=block.days.single()
        fun changedSlot(target:CustomExerciseSlot)=original.copy(
            revision=4,blocks=listOf(block.copy(days=listOf(
                day.copy(slots=listOf(target))))))
        val exact=changedSlot(slot().copy(sets=slot().sets.map{
            it.copy(exactRepTarget=9)
        }))
        val repDenied=transition(original,exact)
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,repDenied.status)
        assertEquals(setOf(CustomCycleChangeKind.REPS),repDenied.blockedKinds)
        assertTrue(repDenied.differences.any{
            it.kind==CustomCycleChangeKind.REPS && it.explanation.contains("exact")
        })
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,exact,CustomCycleCoachPermissions(
                adaptReps=true)).status)

        val method=changedSlot(slot().copy(
            progressionMethod=SavedProgressionMethod.DYNAMIC))
        val denied=transition(original,method)
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.PROGRESSION_METHOD),
            denied.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,method,CustomCycleCoachPermissions(
                adaptProgressionMethod=true)).status)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,method,origin=CustomCycleEditOrigin.ATHLETE_EDIT).status)

        val incompatible=changedSlot(slot().copy(
            progressionMethod=SavedProgressionMethod.BODYWEIGHT_REPS))
        assertEquals(CustomCycleTransitionStatus.INVALID_SOURCE_OR_CANDIDATE,
            transition(original,incompatible,CustomCycleCoachPermissions(
                adaptProgressionMethod=true)).status)
    }

    @Test fun retainedRepMethodRequiresEveryTargetAfterAnEdit() {
        val template = cycle()
        val block = template.blocks.single()
        val day = block.days.single()
        val slot = day.slots.single().copy(progressionMethod = SavedProgressionMethod.REP_TARGETS)
        fun withTargets(revision: Long, lastTarget: Int?) = template.copy(
            revision = revision,
            blocks = listOf(block.copy(days = listOf(day.copy(slots = listOf(
                slot.copy(sets = listOf(set().copy(exactRepTarget = 8),
                    set().copy(exactRepTarget = lastTarget)))
            )))))
        )
        val original = withTargets(3, 9)
        val permissions = CustomCycleCoachPermissions(adaptReps = true)
        for (origin in CustomCycleEditOrigin.values()) {
            assertEquals(CustomCycleTransitionStatus.INVALID_SOURCE_OR_CANDIDATE,
                transition(original, withTargets(4, null), permissions, origin).status)
            assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
                transition(original, withTargets(4, 10), permissions, origin).status)
        }
    }

    @Test fun exactRepWeekOverrideNeedsExplicitRepAndWeekPermissions() {
        val source=cycle()
        val before=source.blocks.single().days.single().slots.single().sets
        val edited=source.copy(revision=4,weekOverrides=mapOf(
            "build" to listOf(CustomWeekSlotOverride(
                2,0,"main",before.map{it.copy(exactRepTarget=9)}))))
        val refused=transition(source,edited)
        assertEquals(setOf(CustomCycleChangeKind.WEEK_OVERRIDES,
            CustomCycleChangeKind.REPS),refused.blockedKinds)
        assertEquals(setOf(CustomCycleChangeKind.REPS),
            transition(source,edited,CustomCycleCoachPermissions(
                adaptWeekOverrides=true)).blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(source,edited,CustomCycleCoachPermissions(
                adaptWeekOverrides=true,adaptReps=true)).status)
    }

    @Test fun appendedBlockCannotChangeMethodUnderBlocksPermissionAlone() {
        val original=cycle()
        val first=original.blocks.single()
        val extra=first.copy(blockId="next",label="Next",days=listOf(
            first.days.single().copy(slots=listOf(
                slot().copy(progressionMethod=SavedProgressionMethod.DOUBLE)))))
        val proposed=original.copy(revision=4,blocks=listOf(first,extra))
        val denied=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true))
        assertEquals(setOf(CustomCycleChangeKind.PROGRESSION_METHOD),
            denied.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,proposed,CustomCycleCoachPermissions(
                adaptBlocks=true,adaptProgressionMethod=true)).status)
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
        val overrideOnly=transition(original,changed,
            CustomCycleCoachPermissions(adaptWeekOverrides=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            overrideOnly.status)
        assertEquals(setOf(CustomCycleChangeKind.LOADS),
            overrideOnly.blockedKinds)
        val allowed = transition(original, changed,
            CustomCycleCoachPermissions(adaptWeekOverrides = true,
                adaptLoads=true))
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

    @Test fun newWeekOverrideCannotChangeRepRirSetCountRestOrAmplifierWithoutPermissions() {
        val source=cycle()
        val amplifier=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            6,15,DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(135)))
        val changed=source.copy(revision=4,weekOverrides=mapOf(
            "build" to listOf(CustomWeekSlotOverride(
                3,0,"main",listOf(
                    set(reps=6,rir=1,rest=145,amp=amplifier),
                    set(reps=6,rir=1,rest=145),
                    set(reps=6,rir=1,rest=145))))))
        val unchecked=transition(source,changed,
            CustomCycleCoachPermissions(adaptWeekOverrides=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            unchecked.status)
        assertEquals(setOf(
            CustomCycleChangeKind.SET_COUNT,CustomCycleChangeKind.REPS,
            CustomCycleChangeKind.RIR,CustomCycleChangeKind.REST,
            CustomCycleChangeKind.AMPLIFIERS,CustomCycleChangeKind.LOADS),
            unchecked.blockedKinds)
        val reviewed=transition(source,changed,
            CustomCycleCoachPermissions(adaptWeekOverrides=true,
                adaptSetCount=true,adaptReps=true,adaptRir=true,
                adaptRest=true,adaptAmplifiers=true,adaptLoads=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertFalse(reviewed.mayIssueWorkout)
    }

    @Test fun deletingWeekOverrideCannotRestoreUnauthorizedTemplateLoad() {
        val base=cycle()
        val withOverride=base.copy(weekOverrides=mapOf(
            "build" to listOf(CustomWeekSlotOverride(
                2,0,"main",listOf(set(load=200),set(load=200))))))
        val revert=base.copy(revision=4)
        val noLoadPermission=transition(withOverride,revert,
            CustomCycleCoachPermissions(adaptWeekOverrides=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            noLoadPermission.status)
        assertEquals(setOf(CustomCycleChangeKind.LOADS),
            noLoadPermission.blockedKinds)
        val allowed=transition(withOverride,revert,
            CustomCycleCoachPermissions(adaptWeekOverrides=true,
                adaptLoads=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
    }

    @Test fun redundantWeekOverrideStillNeedsOverrideButNotLoadPermission() {
        val original=cycle()
        val candidate=original.copy(revision=4,weekOverrides=mapOf(
            "build" to listOf(CustomWeekSlotOverride(
                3,0,"main",original.blocks.single().days.single()
                    .slots.single().sets))))
        val noLoads=transition(original,candidate,
            CustomCycleCoachPermissions(adaptWeekOverrides=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            noLoads.status)
        assertTrue(noLoads.blockedKinds.isEmpty())
        assertTrue(noLoads.differences.none {
            it.kind==CustomCycleChangeKind.LOADS
        })
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

    @Test fun addingTrainingDaysRequiresSeparateDayCountScheduleAndExercisePermissions() {
        val original=cycle()
        val source=original.blocks.single()
        val extraDay=CustomTrainingDay(2,"Push B",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomExerciseSlot("extra","press",ComposerRole.PRIMARY,
                listOf(set(),set()),pinned=false)))
        val candidate=original.copy(revision=4,blocks=listOf(
            source.copy(days=source.days+extraDay)))
        val onlySplitAndBlocks=transition(original,candidate,
            CustomCycleCoachPermissions(adaptSplit=true,adaptBlocks=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            onlySplitAndBlocks.status)
        assertTrue(onlySplitAndBlocks.blockedKinds.containsAll(setOf(
            CustomCycleChangeKind.DAY_COUNT,CustomCycleChangeKind.SCHEDULE,
            CustomCycleChangeKind.EXERCISES)))
        val missingDays=transition(original,candidate,
            CustomCycleCoachPermissions(adaptSplit=true,adaptBlocks=true,
                adaptSchedule=true,adaptExercises=true))
        assertEquals(setOf(CustomCycleChangeKind.DAY_COUNT),
            missingDays.blockedKinds)
        val missingExercises=transition(original,candidate,
            CustomCycleCoachPermissions(adaptDayCount=true,
                adaptSchedule=true))
        assertEquals(setOf(CustomCycleChangeKind.EXERCISES),
            missingExercises.blockedKinds)
        val approvedForReview=transition(original,candidate,
            CustomCycleCoachPermissions(adaptDayCount=true,
                adaptSchedule=true,adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            approvedForReview.status)
        assertTrue(approvedForReview.blockedKinds.isEmpty())
        assertFalse(approvedForReview.mayApplyAutomatically)
        assertFalse(approvedForReview.mayIssueWorkout)
        assertEquals(1,original.blocks.single().days.size)
    }

    @Test fun sameDayCountSplitChangesRequireOnlySplitPermission() {
        val original=cycle()
        val candidate=original.copy(revision=4,blocks=listOf(
            original.blocks.single().copy(
                split=CustomSplitIdentity.Freeform("Split alternative"))))
        val review=transition(original,candidate,
            CustomCycleCoachPermissions(adaptSplit=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            review.status)
        assertTrue(review.differences.any {
            it.kind==CustomCycleChangeKind.SPLIT
        })
        assertTrue(review.differences.none {
            it.kind==CustomCycleChangeKind.DAY_COUNT ||
                it.kind==CustomCycleChangeKind.SCHEDULE
        })
        assertFalse(review.mayApplyAutomatically)
    }

    @Test fun droppingUnpinnedDayNeedsDayCountAndExercisePermissions() {
        val original=cycle()
        val originalBlock=original.blocks.single()
        val addedDay=CustomTrainingDay(2,"Extra",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomExerciseSlot("extra","press",ComposerRole.PRIMARY,
                listOf(set(),set()),pinned=false)))
        val withExtra=original.copy(blocks=listOf(
            originalBlock.copy(days=originalBlock.days+addedDay)))
        val candidate=withExtra.copy(revision=4,blocks=listOf(originalBlock))
        val denied=transition(withExtra,candidate,
            CustomCycleCoachPermissions(adaptSchedule=true,adaptDayCount=true))
        assertEquals(setOf(CustomCycleChangeKind.EXERCISES),
            denied.blockedKinds)
        val allowed=transition(withExtra,candidate,
            CustomCycleCoachPermissions(adaptSchedule=true,adaptDayCount=true,
                adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        assertFalse(allowed.mayModifyHistory)
    }


    @Test fun newlyAddedBlockRequiresItsActualSplitDayCountAndExercisePermissions() {
        val original=cycle()
        val previous=original.blocks.single()
        val newDay=CustomTrainingDay(2,"Push",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomExerciseSlot("accessory","press",
                ComposerRole.PRIMARY,listOf(set(),set()),pinned=false)))
        val added=previous.copy(blockId="strength",label="Strength",
            phase=TrainingPhase.STRENGTH,
            split=CustomSplitIdentity.Freeform("Another split"),
            days=previous.days+newDay)
        val proposed=original.copy(revision=4,
            blocks=listOf(previous,added))
        val denied=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,
            denied.status)
        assertTrue(denied.blockedKinds.containsAll(setOf(
            CustomCycleChangeKind.PHASE,CustomCycleChangeKind.SPLIT,
            CustomCycleChangeKind.DAY_COUNT,CustomCycleChangeKind.SCHEDULE,
            CustomCycleChangeKind.EXERCISES)))
        val allowed=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true,
                adaptPhase=true,adaptSplit=true,adaptDayCount=true,
                adaptSchedule=true,adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        assertFalse(allowed.mayApplyAutomatically)
        assertFalse(allowed.mayModifyHistory)
    }
    @Test fun newBlockCannotReplacePinnedExerciseWithOnlyAdaptExercisesPermission() {
        val original=cycle()
        val reference=original.blocks.single()
        val changedSlot=slot("press").copy(pinned=false)
        val added=reference.copy(blockId="strength",label="Strength",
            days=listOf(reference.days.single().copy(slots=listOf(changedSlot))))
        val proposed=original.copy(revision=4,blocks=listOf(reference,added))
        val denied=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true,adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.REMOVE_PINNED),
            denied.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,proposed,CustomCycleCoachPermissions(
                adaptBlocks=true,adaptExercises=true,removePinned=true)).status)
        assertTrue(original.blocks.single().days.single().slots.single().pinned)
    }

    @Test fun newBlockCannotDropEntirePinnedDayThroughSchedulePermission() {
        val original=cycle()
        val reference=original.blocks.single()
        val replacement=CustomTrainingDay(2,"Other training day",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomExerciseSlot("alternative","press",
                ComposerRole.PRIMARY,listOf(set(),set()),pinned=false)))
        val added=reference.copy(blockId="alternative",days=listOf(replacement))
        val proposed=original.copy(revision=4,blocks=listOf(reference,added))
        val denied=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true,adaptSchedule=true,
                adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertTrue(CustomCycleChangeKind.REMOVE_PINNED in denied.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,proposed,CustomCycleCoachPermissions(
                adaptBlocks=true,adaptSchedule=true,adaptExercises=true,
                removePinned=true)).status)
    }

    @Test fun unpinningSameExerciseRequiresExplicitDestructivePermission() {
        val original=cycle()
        val block=original.blocks.single()
        val day=block.days.single()
        val changed=original.copy(revision=4,blocks=listOf(
            block.copy(days=listOf(day.copy(
                slots=listOf(day.slots.single().copy(pinned=false)))))))
        val denied=transition(original,changed,
            CustomCycleCoachPermissions(adaptExercises=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.REMOVE_PINNED),
            denied.blockedKinds)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(original,changed,CustomCycleCoachPermissions(
                adaptExercises=true,removePinned=true)).status)
    }

    @Test fun newBlockPreservesAthleteExerciseNotesUnlessCueChangesArePermitted() {
        val original=cycle()
        val existing=original.blocks.single()
        val before=existing.days.single()
        val annotated=before.copy(slots=listOf(
            before.slots.single().copy(coachNote="Avoid deep stretch")))
        val reviewedBase=original.copy(blocks=listOf(existing.copy(
            days=listOf(annotated))))
        val source=reviewedBase.blocks.single()
        val replacement=annotated.copy(slots=listOf(
            annotated.slots.single().copy(coachNote="Different cue")))
        val proposed=reviewedBase.copy(revision=4,blocks=listOf(source,
            source.copy(blockId="next",days=listOf(replacement))))
        val denied=transition(reviewedBase,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.CUES),denied.blockedKinds)
        val allowed=transition(reviewedBase,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true,adaptCues=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        assertEquals("Avoid deep stretch",
            reviewedBase.blocks.single().days.single().slots.single().coachNote)
    }

    @Test fun addingSameStructureBlockOnlyRequiresBlockPermission() {
        val original=cycle()
        val prior=original.blocks.single()
        val proposed=original.copy(revision=4,blocks=listOf(prior,
            prior.copy(blockId="repeat",label="Repeat coached work")))
        val reviewed=transition(original,proposed,
            CustomCycleCoachPermissions(adaptBlocks=true))
        assertEquals(reviewed.differences.toString(),
            CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertEquals(setOf(CustomCycleChangeKind.BLOCKS),
            reviewed.differences.map {it.kind}.toSet())
    }

    private fun withMachine(source:CustomTrainingCycle,station:String):CustomTrainingCycle =
        source.copy(blocks=source.blocks.map{block->
            block.copy(days=block.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(machineInstanceId=station)
                })
            })
        })

    @Test fun changingPhysicalMachineIsNeverMisreportedAsNoChange() {
        val old=withMachine(cycle(),"machine-a")
        val candidate=withMachine(old.copy(revision=4),"machine-b")
        val denied=transition(old,candidate)
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.ATTACHMENTS),denied.blockedKinds)
        assertTrue(denied.differences.any{
            it.location.endsWith(".machine") &&
                it.explanation.contains("Machine A/B")
        })
        val allowed=transition(old,candidate,
            CustomCycleCoachPermissions(adaptAttachments=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
        assertFalse(allowed.mayApplyAutomatically)
        assertFalse(allowed.mayModifyHistory)
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(old,candidate,
                origin=CustomCycleEditOrigin.ATHLETE_EDIT).status)
    }

    @Test fun addedBlockCannotSmuggleDifferentMachineThroughBlockPermission() {
        val old=withMachine(cycle(),"machine-a")
        val prior=old.blocks.single()
        val changedSlot=prior.days.single().slots.single()
            .copy(machineInstanceId="machine-b")
        val newBlock=prior.copy(blockId="strength-b",label="Next block",
            days=listOf(prior.days.single().copy(slots=listOf(changedSlot))))
        val candidate=old.copy(revision=4,blocks=listOf(prior,newBlock))
        val denied=transition(old,candidate,
            CustomCycleCoachPermissions(adaptBlocks=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.ATTACHMENTS),denied.blockedKinds)
        val allowed=transition(old,candidate,
            CustomCycleCoachPermissions(adaptBlocks=true,adaptAttachments=true))
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            allowed.status)
    }

    @Test fun equivalentDecimalLoadScaleIsNotAProgramOrAmplifierEdit() {
        val firstDrop=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(155))
        val secondDrop=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(135))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,6,15,firstDrop,
            followUpRounds=2,secondDropLoad=secondDrop)
        val original=cycle().let{base->
            val block=base.blocks.single()
            val day=block.days.single()
            base.copy(blocks=listOf(block.copy(days=listOf(day.copy(
                slots=listOf(day.slots.single().copy(
                    sets=listOf(set(amp=amp),set()))))))))
        }
        val baseBlock=original.blocks.single()
        val baseDay=baseBlock.days.single()
        val first=baseDay.slots.single().sets.first()
        val modified=first.copy(
            load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                BigDecimal("185.000")),
            amplifier=amp.copy(
                followUpLoad=firstDrop.copy(value=BigDecimal("155.00")),
                secondDropLoad=secondDrop.copy(value=BigDecimal("135.000"))))
        val candidate=original.copy(revision=4,blocks=listOf(
            baseBlock.copy(days=listOf(baseDay.copy(slots=listOf(
                baseDay.slots.single().copy(
                    sets=listOf(modified,baseDay.slots.single().sets.last()))))))))
        val review=transition(original,candidate)
        assertEquals(review.differences.toString(),
            CustomCycleTransitionStatus.NO_CHANGE,review.status)
        assertTrue(review.differences.isEmpty())
        assertFalse(review.mayModifyHistory)
    }

    @Test fun differentSecondDropWeightRequiresLoadAndAmplifierPermissions() {
        val first=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(155))
        val second=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(135))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,6,15,first,
            followUpRounds=2,secondDropLoad=second)
        val initial=cycle().let{source->
            val block=source.blocks.single()
            val day=block.days.single()
            source.copy(blocks=listOf(block.copy(days=listOf(day.copy(
                slots=listOf(day.slots.single().copy(
                    sets=listOf(set(amp=amp),set()))))))))
        }
        val oldBlock=initial.blocks.single()
        val oldDay=oldBlock.days.single()
        val oldSlot=oldDay.slots.single()
        val changedFirst=oldSlot.sets.first().copy(
            amplifier=amp.copy(secondDropLoad=second.copy(value=n(105))))
        val candidate=initial.copy(revision=4,blocks=listOf(oldBlock.copy(
            days=listOf(oldDay.copy(slots=listOf(oldSlot.copy(
                sets=listOf(changedFirst,oldSlot.sets.last()))))))))
        val denied=transition(initial,candidate,
            CustomCycleCoachPermissions(adaptAmplifiers=true))
        assertEquals(CustomCycleTransitionStatus.PERMISSION_REQUIRED,denied.status)
        assertEquals(setOf(CustomCycleChangeKind.LOADS),denied.blockedKinds)
        assertTrue(denied.differences.any{
            it.kind==CustomCycleChangeKind.LOADS &&
                it.location.endsWith(".amplifier.secondDrop")
        })
        assertEquals(CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW,
            transition(initial,candidate,
                CustomCycleCoachPermissions(adaptLoads=true,
                    adaptAmplifiers=true)).status)
    }

}
