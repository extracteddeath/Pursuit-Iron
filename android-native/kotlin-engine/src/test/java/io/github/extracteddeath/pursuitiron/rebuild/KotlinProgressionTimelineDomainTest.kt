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
        exercise: String = "bench"
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
            source, "grant-$index", id, 0, 0, purpose, 48, targets)
        var journal = KotlinWorkoutJournalDomain.begin(
            snapshot, KotlinStoredWorkoutIdentity("stored-$index", id, source, 1))
        for (i in reps.indices) {
            val failed = i == failedAt
            val operation = KotlinJournalOperation.RecordSet(
                "event-$index-$i", journal.revision, targets[i].setKey,
                if (failed) KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(loads[i]), LoadUnit.LB,
                    if (failed) 0 else reps[i],
                    if (failed) null else actualRir[i])
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
                    correctReps, actualRir[ordinal]),
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

    @Test fun unreportedRirOrRecentFailedAttemptNeverQualifies() {
        val missing = KotlinProgressionTimelineDomain.review(request(
            listOf(workout(1), workout(2,
                actualRir = listOf(null, null, null, null)))))
        assertEquals(IncreaseReviewReason.ACTUAL_RIR_MISSING,
            missing.progression?.reason)
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

}
