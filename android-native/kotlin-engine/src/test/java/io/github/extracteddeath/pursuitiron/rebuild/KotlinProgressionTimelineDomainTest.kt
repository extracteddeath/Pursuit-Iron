package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinProgressionTimelineDomainTest {
    private fun n(value: Int) = BigDecimal(value)
    private val baseSource = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 4, "gym-1", 1, "catalog-2", "rules-3")
    private val stock = VerifiedLoadStock.Totals(
        LoadUnit.LB, LoadBasis.SMITH_TOTAL,
        listOf(n(195), n(200), n(205), n(210), n(215)))

    private fun workout(
        index: Int,
        reps: List<Int> = listOf(8, 8, 8, 8),
        actualRir: List<Int?> = listOf(2, 2, 2, 2),
        loads: List<Int> = listOf(200, 200, 200, 200),
        source: KotlinTrainingSourceRevision = baseSource,
        repBand: RepBand = RepBand(5, 8),
        purpose: ComposerSessionPurpose = ComposerSessionPurpose.HYPERTROPHY,
        failedAt: Int? = null,
        correctedReps: Pair<Int, Int>? = null,
        exercise: String = "bench",
        dayIndex: Int = 0,
        machineId: String? = null,
        machinesBySet: List<String?>? = null,
        skipAll:Boolean=false
    ): KotlinWorkoutJournalState {
        require(reps.size == 4 && actualRir.size == 4 && loads.size == 4)
        val id = "workout-$index"
        val targets = reps.indices.map { i ->
            KotlinWorkoutTarget(
                exercise, ComposerRole.PRIMARY, purpose, "slot-$index-$i",
                DraftSetType.WORKING, i,
                DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(200)),
                repBand, 3, 60, 120
            )
        }
        val snapshot = KotlinStagedWorkout(
            source, "grant-$index", id, dayIndex, dayIndex, purpose, 48, targets)
        var journal = KotlinWorkoutJournalDomain.begin(
            snapshot, KotlinStoredWorkoutIdentity("stored-$index", id, source, 1))
        for (i in reps.indices) {
            val failed = i == failedAt
            val operation = KotlinJournalOperation.RecordSet(
                "event-$index-$i", journal.revision, targets[i].setKey,
                if(skipAll) KotlinJournalSetOutcome.SKIPPED
                else if (failed) KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                if(skipAll) null else KotlinPerformedSet(n(loads[i]), LoadUnit.LB,
                    if (failed) 0 else reps[i],
                    if (failed) null else actualRir[i],machinesBySet?.get(i) ?: machineId)
            )
            journal = (KotlinWorkoutJournalDomain.reduce(journal, operation)
                as KotlinJournalDecision.Proposed).state
        }
        if (correctedReps != null) {
            val (ordinal, correctReps) = correctedReps
            val correction = KotlinJournalOperation.RecordSet(
                "correction-$index", journal.revision, targets[ordinal].setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(loads[ordinal]), LoadUnit.LB,
                    correctReps, actualRir[ordinal],machinesBySet?.get(ordinal) ?: machineId),
                correctsEventId = "event-$index-$ordinal"
            )
            journal = (KotlinWorkoutJournalDomain.reduce(journal, correction)
                as KotlinJournalDecision.Proposed).state
        }
        return (KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.Finish("finish-$index", journal.revision))
            as KotlinJournalDecision.Proposed).state
    }

    private fun request(
        workouts: List<KotlinWorkoutJournalState>,
        historyRevision: Long = 10,
        currentHistoryRevision: Long = historyRevision,
        equipment: VerifiedLoadStock? = stock
    ) = KotlinProgressionTimelineRequest(
        "athlete-1", "bench", historyRevision, currentHistoryRevision,
        workouts.mapIndexed { i, journal ->
            KotlinChronologicalWorkout(i.toLong() + 1, journal)
        }, equipment, n(5)
    )

    @Test fun machineAAndBRemainIndependentEvenInSameExerciseAndDay() {
        val a1=workout(101,loads=listOf(200,200,200,200),machineId="A")
        val b1=workout(102,loads=listOf(140,140,140,140),machineId="B")
        val a2=workout(103,loads=listOf(200,200,200,200),machineId="A")
        val both=listOf(a1,b1,a2)
        val reviewA=KotlinProgressionTimelineDomain.review(
            request(both).copy(requestedMachineInstanceId="A"))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,reviewA.status)
        assertEquals(listOf("workout-101","workout-103"),reviewA.comparedWorkoutIds)
        val reviewB=KotlinProgressionTimelineDomain.review(
            request(both).copy(requestedMachineInstanceId="B"))
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,reviewB.status)
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,
            KotlinProgressionTimelineDomain.review(request(both)).status)
        val mixed=workout(104,machinesBySet=listOf("A","B","A","B"))
        assertEquals(KotlinTimelineReviewStatus.MIXED_PHYSICAL_MACHINE,
            KotlinProgressionTimelineDomain.review(request(listOf(a1,mixed))).status)
    }

    @Test fun unknownAndLegacyMachineSessionsFenceOutCherryPickedAProgression() {
        val a1=workout(301,machineId="A")
        val a2=workout(302,machineId="A")
        val skipped=workout(303,skipAll=true)
        val a3=workout(304,machineId="A")
        val a4=workout(305,machineId="A")
        fun review(sessions:List<KotlinWorkoutJournalState>)=
            KotlinProgressionTimelineDomain.review(
                request(sessions).copy(requestedMachineInstanceId="A"))
        val before=review(listOf(a1,a2))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,before.status)
        val afterUnknown=review(listOf(a1,a2,skipped,a3))
        assertEquals(KotlinTimelineReviewStatus.UNRESOLVED_PHYSICAL_MACHINE,
            afterUnknown.status)
        assertEquals(listOf("workout-302","workout-304"),
            afterUnknown.comparedWorkoutIds)
        assertNull(afterUnknown.progression)
        assertFalse(afterUnknown.canIssueTargets)
        // Two fresh, explicitly verified A sessions supersede an earlier
        // unknown workout without permanently banning A's progression.
        val recovered=review(listOf(a1,a2,skipped,a3,a4))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,recovered.status)
        assertEquals(listOf("workout-304","workout-305"),recovered.comparedWorkoutIds)
        val unresolvedLatest=review(listOf(a1,a2,skipped))
        assertEquals(KotlinTimelineReviewStatus.UNRESOLVED_PHYSICAL_MACHINE,
            unresolvedLatest.status)

        val legacy=workout(306,machineId=null)
        assertEquals(KotlinTimelineReviewStatus.UNRESOLVED_PHYSICAL_MACHINE,
            review(listOf(a1,a2,legacy,a3)).status)
        // An identifiable B session is unrelated, so it must not block A.
        val knownB=workout(307,machineId="B")
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,
            review(listOf(a1,a2,knownB,a3)).status)
        val mixed=workout(308,machinesBySet=listOf("A","B","A","B"))
        assertEquals(KotlinTimelineReviewStatus.UNRESOLVED_PHYSICAL_MACHINE,
            review(listOf(a1,a2,mixed,a3)).status)
    }

    @Test fun postFinishMachineCorrectionChangesComparableProgressionScope() {
        val prior=workout(201,machineId="A")
        val wronglyTagged=workout(202,machineId="A")
        var corrected=KotlinSavedHistoryCorrectionDomain.begin(wronglyTagged)
        wronglyTagged.snapshot.targets.forEachIndexed {index,target->
            val original=wronglyTagged.effectiveSets.getValue(target.setKey).operation
                as KotlinJournalOperation.RecordSet
            val fix=KotlinSavedSetCorrection("fix-machine-$index",
                corrected.correctionRevision,target.setKey,original.eventId,
                KotlinJournalSetOutcome.COMPLETED,
                original.performed?.copy(machineInstanceId="B"))
            corrected=(KotlinSavedHistoryCorrectionDomain.reduce(corrected,fix)
                as KotlinSavedCorrectionDecision.Proposed).state
        }
        val correctedEvidence=KotlinWorkoutHistoryEvidenceDomain.projectSaved(corrected)
            as KotlinHistoryProjectionResult.Candidate
        assertEquals(setOf("B"),correctedEvidence.evidence.sets.mapNotNull{
            it.actual?.machineInstanceId}.toSet())
        val timeline=request(listOf(prior,wronglyTagged)).copy(completedWorkouts=listOf(
            KotlinChronologicalWorkout(1,prior),
            KotlinChronologicalWorkout(2,wronglyTagged,corrected)))
        val forA=KotlinProgressionTimelineDomain.review(
            timeline.copy(requestedMachineInstanceId="A"))
        val forB=KotlinProgressionTimelineDomain.review(
            timeline.copy(requestedMachineInstanceId="B"))
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,forA.status)
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,forB.status)
        assertEquals(listOf("workout-201"),forA.comparedWorkoutIds)
        assertEquals(listOf("workout-202"),forB.comparedWorkoutIds)
    }

    @Test fun successiveCompletedWorkoutsYieldReviewOnlyPhysicalStepAndFatigue() {
        val review = KotlinProgressionTimelineDomain.review(
            request(listOf(workout(1), workout(2))))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW, review.status)
        assertEquals(listOf("workout-1", "workout-2"), review.comparedWorkoutIds)
        assertEquals(4, review.latestFactCount)
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            review.progression?.reason)
        assertEquals(n(205), review.progression?.reviewCandidate)
        assertEquals(FatiguePattern.REPS_STABLE, review.latestFatigue?.pattern)
        assertFalse(review.canIssueTargets)
        assertFalse(review.canChangeProgram)
    }

    @Test fun latestBadWorkoutBlocksCherryPickingEarlierSuccesses() {
        val last = workout(3, reps = listOf(3, 3, 2, 2),
            actualRir = listOf(2, 1, 0, 0),
            loads = listOf(200, 205, 210, 215))
        val review = KotlinProgressionTimelineDomain.review(
            request(listOf(workout(1), workout(2), last)))
        assertEquals(listOf("workout-2", "workout-3"),
            review.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,
            review.progression?.reason)
        assertNull(review.progression?.reviewCandidate)
        assertEquals(FatiguePattern.MIXED_LOADS, review.latestFatigue?.pattern)
    }

    @Test fun correctedEarlierSuccessInvalidatesProgressionWithoutRemovingAudit() {
        val original = workout(2)
        val corrected = workout(2, correctedReps = 3 to 4)
        val originalReview = KotlinProgressionTimelineDomain.review(
            request(listOf(workout(1), original)))
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            originalReview.progression?.reason)
        val correctedReview = KotlinProgressionTimelineDomain.review(
            request(listOf(workout(1), corrected), historyRevision = 11))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,
            correctedReview.status)
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,
            correctedReview.progression?.reason)
        assertNull(correctedReview.progression?.reviewCandidate)
        assertEquals(6, corrected.receipts.size)
        assertEquals("correction-2",
            (corrected.effectiveSets.getValue("slot-2-3").operation
                as KotlinJournalOperation.RecordSet).eventId)
        assertEquals("event-2-3", corrected.receipts[3].operation.eventId)
        assertEquals(KotlinTimelineReviewStatus.STALE_HISTORY,
            KotlinProgressionTimelineDomain.review(request(
                listOf(workout(1), original), historyRevision = 10,
                currentHistoryRevision = 11)).status)
    }

    @Test fun unreportedRirUsesOriginalGoalWhileRecentFailedAttemptStillBlocks() {
        val missing = KotlinProgressionTimelineDomain.review(request(
            listOf(workout(1), workout(2,
                actualRir = listOf(null, null, null, null)))))
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            missing.progression?.reason)
        assertEquals(4,missing.progression?.assumedGoalRirSetCount)
        assertEquals(0,missing.progression?.latest?.actualRirReportedOrdinals?.size)
        val failed = KotlinProgressionTimelineDomain.review(request(
            listOf(workout(1), workout(2, failedAt = 3))))
        assertEquals(IncreaseReviewReason.INCOMPLETE_SESSION,
            failed.progression?.reason)
        assertEquals(setOf(3), failed.progression?.latest?.failedAttemptOrdinals)
    }

    @Test fun changedEquipmentProgramAndRepRangesBlockUnsafeComparisons() {
        val base = workout(1)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_GYM_OR_COACH_RULES,
            KotlinProgressionTimelineDomain.review(request(listOf(
                base, workout(2, source = baseSource.copy(gymRevision = 2)))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_PROGRAM_OR_CATALOG,
            KotlinProgressionTimelineDomain.review(request(listOf(
                base, workout(2, source = baseSource.copy(catalogRevision = "catalog-3")))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_PROGRAM_OR_CATALOG,
            KotlinProgressionTimelineDomain.review(request(listOf(
                base, workout(2, source = baseSource.copy(programRevision = 5)))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_ATHLETE_PROFILE,
            KotlinProgressionTimelineDomain.review(request(listOf(
                base, workout(2, source = baseSource.copy(athleteRevision = 3)))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_SET_PRESCRIPTION,
            KotlinProgressionTimelineDomain.review(request(listOf(
                base, workout(2, repBand = RepBand(6, 10)))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_LOAD_BASIS,
            KotlinProgressionTimelineDomain.review(request(
                listOf(base, workout(2)),
                equipment = VerifiedLoadStock.Totals(
                    LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n(200), n(205))))).status)
    }

    @Test fun tamperingDuplicatesAndChronologicalDisorderFailClosed() {
        val first = workout(1)
        val second = workout(2)
        val ordinary = request(listOf(first, second))
        assertEquals(KotlinTimelineReviewStatus.DUPLICATE_WORKOUT,
            KotlinProgressionTimelineDomain.review(
                request(listOf(first, first))).status)
        assertEquals(KotlinTimelineReviewStatus.INVALID_COMPLETION_ORDER,
            KotlinProgressionTimelineDomain.review(ordinary.copy(
                completedWorkouts = ordinary.completedWorkouts.reversed())).status)
        assertEquals(KotlinTimelineReviewStatus.WRONG_ATHLETE,
            KotlinProgressionTimelineDomain.review(request(listOf(
                first, workout(2, source = baseSource.copy(athleteId = "other")))))
                .status)
        assertEquals(KotlinTimelineReviewStatus.UNVERIFIED_JOURNAL,
            KotlinProgressionTimelineDomain.review(request(listOf(
                first, second.copy(effectiveSets = emptyMap())))).status)
        assertEquals(KotlinTimelineReviewStatus.STALE_HISTORY,
            KotlinProgressionTimelineDomain.review(request(
                listOf(first, second), currentHistoryRevision = 11)).status)
        assertEquals(KotlinTimelineReviewStatus.INVALID_REQUEST,
            KotlinProgressionTimelineDomain.review(
                ordinary.copy(maximumAuthorizedIncrease = BigDecimal("0"))).status)
    }

    @Test fun onlyOneExerciseOccurrenceCannotBorrowAnotherExercisesSuccess() {
        val other = workout(1, exercise = "row")
        val latest = workout(2)
        val review = KotlinProgressionTimelineDomain.review(
            request(listOf(other, latest)))
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,
            review.status)
        assertEquals(listOf("workout-2"), review.comparedWorkoutIds)
        assertNull(review.progression)
    }

    @Test fun interleavedStrengthAndHypertrophyHaveIndependentEvidence() {
        // The full authenticated timeline is not filtered by the caller.
        // The REVIEWER alone chooses the latest two matching-purpose sessions.
        val history=listOf(
            workout(1,purpose=ComposerSessionPurpose.HYPERTROPHY),
            workout(2,purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                repBand=RepBand(3,5),reps=listOf(3,3,3,3)),
            workout(3,purpose=ComposerSessionPurpose.HYPERTROPHY),
            workout(4,purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                repBand=RepBand(3,5),reps=listOf(3,3,3,3)),
            workout(5,purpose=ComposerSessionPurpose.HYPERTROPHY))
        val unscoped=KotlinProgressionTimelineDomain.review(request(history))
        assertEquals(KotlinTimelineReviewStatus.CHANGED_SESSION_PURPOSE,unscoped.status)
        val volume=KotlinProgressionTimelineDomain.review(
            request(history).copy(
                requestedPurpose=ComposerSessionPurpose.HYPERTROPHY))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,volume.status)
        assertEquals(listOf("workout-3","workout-5"),volume.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            volume.progression?.reason)
        assertEquals(n(205),volume.progression?.reviewCandidate)
        assertFalse(volume.canIssueTargets)
        val strength=KotlinProgressionTimelineDomain.review(
            request(history).copy(
                requestedPurpose=ComposerSessionPurpose.STRENGTH_SKILL))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,strength.status)
        assertEquals(listOf("workout-2","workout-4"),strength.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.STILL_BUILDING_REPS,
            strength.progression?.reason)
        assertNull(strength.progression?.reviewCandidate)
    }

    @Test fun latestFailureWithinRequestedPurposeCannotBeSkipped() {
        val failed=workout(5,purpose=ComposerSessionPurpose.HYPERTROPHY,
            reps=listOf(3,3,2,2),
            actualRir=listOf(2,1,0,0),
            loads=listOf(200,205,210,215))
        val history=listOf(
            workout(1),
            workout(2,purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                repBand=RepBand(3,5),reps=listOf(3,3,3,3)),
            workout(3),
            workout(4,purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                repBand=RepBand(3,5),reps=listOf(3,3,3,3)),
            failed)
        val review=KotlinProgressionTimelineDomain.review(
            request(history).copy(requestedPurpose=
                ComposerSessionPurpose.HYPERTROPHY))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,review.status)
        assertEquals(listOf("workout-3","workout-5"),review.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,
            review.progression?.reason)
        assertNull(review.progression?.reviewCandidate)
        assertEquals(FatiguePattern.MIXED_LOADS,review.latestFatigue?.pattern)
    }

    @Test fun purposeSelectionPreservesFullHistoryValidationAndRevisions() {
        val first=workout(1,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val middle=workout(2,purpose=ComposerSessionPurpose.STRENGTH_SKILL,
            repBand=RepBand(3,5),reps=listOf(3,3,3,3))
        val last=workout(3,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val scoped=request(listOf(first,middle,last)).copy(
            requestedPurpose=ComposerSessionPurpose.HYPERTROPHY)
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,
            KotlinProgressionTimelineDomain.review(scoped).status)
        assertEquals(KotlinTimelineReviewStatus.UNVERIFIED_JOURNAL,
            KotlinProgressionTimelineDomain.review(request(listOf(
                first,middle.copy(effectiveSets=emptyMap()),last)).copy(
                requestedPurpose=ComposerSessionPurpose.HYPERTROPHY)).status)
        assertEquals(KotlinTimelineReviewStatus.STALE_HISTORY,
            KotlinProgressionTimelineDomain.review(
                scoped.copy(currentHistoryRevision=11)).status)
        val changed=workout(3,purpose=ComposerSessionPurpose.HYPERTROPHY,
            source=baseSource.copy(programRevision=5))
        assertEquals(KotlinTimelineReviewStatus.CHANGED_PROGRAM_OR_CATALOG,
            KotlinProgressionTimelineDomain.review(request(
                listOf(first,middle,changed)).copy(
                requestedPurpose=ComposerSessionPurpose.HYPERTROPHY)).status)
        assertEquals(KotlinTimelineReviewStatus.INVALID_REQUEST,
            KotlinProgressionTimelineDomain.review(scoped.copy(
                requestedPurpose=ComposerSessionPurpose.UNSPECIFIED)).status)
        val insufficient=KotlinProgressionTimelineDomain.review(request(
            listOf(first,middle)).copy(
                requestedPurpose=ComposerSessionPurpose.STRENGTH_SKILL))
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,
            insufficient.status)
        assertEquals(listOf("workout-2"),insufficient.comparedWorkoutIds)
        assertNull(insufficient.progression)
    }


    @Test fun aMissingCompletionOrdinalCannotBeHiddenAsACompleteHistory() {
        val first=workout(1)
        val second=workout(2)
        val third=workout(3)
        val source=request(listOf(first,second,third))
        val omitted=source.copy(completedWorkouts=listOf(
            KotlinChronologicalWorkout(41,first),
            KotlinChronologicalWorkout(43,third)))
        assertEquals(KotlinTimelineReviewStatus.INCOMPLETE_COMPLETION_WINDOW,
            KotlinProgressionTimelineDomain.review(omitted).status)
        val complete=source.copy(completedWorkouts=listOf(
            KotlinChronologicalWorkout(41,first),
            KotlinChronologicalWorkout(42,second),
            KotlinChronologicalWorkout(43,third)))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,
            KotlinProgressionTimelineDomain.review(complete).status)
    }

    @Test fun purposeFilteringCannotHideAnInterveningSourceRevision() {
        val old=workout(1)
        val intervening=workout(2,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL,
            source=baseSource.copy(programRevision=7),
            repBand=RepBand(3,5),reps=listOf(3,3,3,3))
        val newest=workout(3)
        val reviewed=KotlinProgressionTimelineDomain.review(
            request(listOf(old,intervening,newest)).copy(
                requestedPurpose=ComposerSessionPurpose.HYPERTROPHY))
        assertEquals(KotlinTimelineReviewStatus.CHANGED_INTERVENING_SOURCE,
            reviewed.status)
        assertEquals(listOf("workout-1","workout-3"),
            reviewed.comparedWorkoutIds)
        assertNull(reviewed.progression)
        assertFalse(reviewed.canIssueTargets)
    }


    @Test fun matchingPurposeDifferentDaysHaveIndependentProgression() {
        // Both days are HYPERTROPHY. Partition by authenticated approved
        // day index, not by the exercise ID or broad coaching purpose alone.
        val pushFailure=listOf(3,3,2,2)
        val history=listOf(
            workout(1,dayIndex=0),
            workout(2,dayIndex=1,reps=pushFailure,
                actualRir=listOf(2,1,0,0)),
            workout(3,dayIndex=0),
            workout(4,dayIndex=1,reps=pushFailure,
                actualRir=listOf(2,1,0,0)),
            workout(5,dayIndex=0)
        )
        val complete=request(history)
        val upper=KotlinProgressionTimelineDomain.review(complete.copy(
            requestedPurpose=ComposerSessionPurpose.HYPERTROPHY,
            requestedDayIndex=0))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,upper.status)
        assertEquals(listOf("workout-3","workout-5"),upper.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            upper.progression?.reason)
        assertEquals(n(205),upper.progression?.reviewCandidate)

        val push=KotlinProgressionTimelineDomain.review(complete.copy(
            requestedPurpose=ComposerSessionPurpose.HYPERTROPHY,
            requestedDayIndex=1))
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW,push.status)
        assertEquals(listOf("workout-2","workout-4"),push.comparedWorkoutIds)
        assertEquals(IncreaseReviewReason.BELOW_REP_FLOOR,
            push.progression?.reason)
        assertNull(push.progression?.reviewCandidate)
        assertFalse(upper.canIssueTargets)
        assertFalse(push.canIssueTargets)

        // Older callers without an authenticated day identity remain
        // diagnostic, not an approval to merge the two progressions.
        val unscoped=KotlinProgressionTimelineDomain.review(complete)
        assertEquals(listOf("workout-4","workout-5"),
            unscoped.comparedWorkoutIds)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_TRAINING_DAY,
            unscoped.status)
        assertNull(unscoped.progression)
    }

    @Test fun daySelectionCannotHideMissingHistoryOrAnInterveningRevision() {
        val source=request(listOf(
            workout(1,dayIndex=0),
            workout(2,dayIndex=1,
                source=baseSource.copy(programRevision=7)),
            workout(3,dayIndex=0)
        )).copy(requestedDayIndex=0)
        val changed=KotlinProgressionTimelineDomain.review(source)
        assertEquals(KotlinTimelineReviewStatus.CHANGED_INTERVENING_SOURCE,
            changed.status)
        assertNull(changed.progression)

        val incomplete=source.copy(completedWorkouts=listOf(
            source.completedWorkouts.first(),source.completedWorkouts.last()))
        assertEquals(KotlinTimelineReviewStatus.INCOMPLETE_COMPLETION_WINDOW,
            KotlinProgressionTimelineDomain.review(incomplete).status)
        assertEquals(KotlinTimelineReviewStatus.INVALID_REQUEST,
            KotlinProgressionTimelineDomain.review(source.copy(
                requestedDayIndex=7)).status)
        val one=KotlinProgressionTimelineDomain.review(request(listOf(
            workout(1,dayIndex=0),workout(2,dayIndex=1)
        )).copy(requestedDayIndex=1))
        assertEquals(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,
            one.status)
        assertEquals(listOf("workout-2"),one.comparedWorkoutIds)
    }

}
