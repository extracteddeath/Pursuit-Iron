package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomNextWorkoutCoachingDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private fun band()=RepBand(5,8)
    private fun load(v:Int)=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(v))
    private fun set(v:Int=200)=CustomAuthoredSet(
        DraftSetType.WORKING,load(v),band(),2,60,120)
    private fun snapshot(
        week:Int,day:Int=0,exercise:String="chest_press",
        loads:List<Int> = listOf(200,200)
    ):CustomWorkoutDaySnapshot {
        val slot=CustomWorkoutSlotSnapshot(
            "main",exercise,"chest","machine",ComposerRole.ACCESSORY,
            null,null,true,"Coach",
            loads.mapIndexed {index,weight->
                CustomWorkoutSetSnapshot(
                    "cycle:w$week:d$day:main:s${index+1}","main",
                    index+1,set(weight))
            })
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle",3,
                "gym-1",2),week,week,"build",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("ULPPL"),
            day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),360,200)
    }
    private fun completed(
        order:Long,week:Int,day:Int=0,
        reps:List<Int> = listOf(8,8),
        rirs:List<Int?> = listOf(2,2),
        issued:List<Int> = listOf(200,200),
        actual:List<Int> = issued,
        exercise:String = "chest_press",
        failed:Set<Int> = emptySet()
    ):CustomCoachingHistoricalSession {
        val snapshot=snapshot(week,day,exercise,issued)
        val visits=snapshot.slots.single().sets.mapIndexed{index,prescribed ->
            val fail=index in failed
            val fact=KotlinPerformedSet(n(actual[index]),LoadUnit.LB,
                if(fail)0 else reps[index],if(fail)null else rirs[index])
            CustomHistoricalVisitFact("main",exercise,prescribed.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                prescribed.prescription.load,band(),2,null,
                if(fail)KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                fact,"log-${week}-${day}-${index}")
        }
        return CustomCoachingHistoricalSession(order,snapshot,
            CustomWorkoutHistoricalEvidence("athlete-1",
                "workout-${week}-${day}","cycle",3,
                "finish-${week}-${day}",3,visits))
    }
    private fun stock()=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n(195),n(200),n(205),n(210),n(215)))
    private fun review(
        next:CustomWorkoutDaySnapshot=snapshot(5),
        sessions:List<CustomCoachingHistoricalSession> = listOf(
            completed(1,3),completed(2,4)),
        stock:VerifiedLoadStock?=stock(),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,
        step:Int=5
    )=CustomNextWorkoutCoachingDomain.review(
        CustomNextWorkoutCoachingRequest(next,"main",sessions,
            stock,n(step),readiness,restricted))

    @Test fun twoStraightSetCeilingSessionsEarnOnlyNextPhysicallyVerifiedIncrement() {
        val result=review()
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            result.status)
        assertEquals(n(200),result.observedStraightSetLoad)
        assertEquals(n(205),result.loadReviewCandidate)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            result.comparedWorkoutIds)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayEditProgram)
        assertFalse(result.mayChangeHistory)
        assertNull(review(step=2).loadReviewCandidate)
        assertNull(review(stock=null).loadReviewCandidate)
    }

    @Test fun upperAndPushAppearancesCannotEarnEachOthersWeightIncrease() {
        val history=listOf(
            completed(1,3,0),completed(2,3,2),
            completed(3,4,0,reps=listOf(5,7)),
            completed(4,4,2))
        val upper=review(next=snapshot(5,0),sessions=history)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,upper.status)
        assertNull(upper.loadReviewCandidate)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            upper.comparedWorkoutIds)
        assertEquals(listOf(6,8),upper.nextSetSuggestions.map{
            it.proposedNextReps
        })
        val push=review(next=snapshot(5,2),sessions=history)
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            push.status)
        assertEquals(n(205),push.loadReviewCandidate)
        assertEquals(listOf("workout-3-2","workout-4-2"),
            push.comparedWorkoutIds)
    }

    @Test fun oldDoubleProgressionFailureNeverRaisesAllSetsToHeaviestSet() {
        // Mirrors the legacy bug: 200x3, 205x3, 210x2, 215x2
        // with a 5-8 rep target must not prescribe 215 for everything.
        val poor=completed(2,4,reps=listOf(3,2,2,2),
            rirs=listOf(0,0,0,0),
            issued=listOf(200,205,210,215),
            actual=listOf(200,205,210,215))
        val next=snapshot(5,loads=listOf(200,205,210,215))
        val first=completed(1,3,reps=listOf(3,3,2,2),
            rirs=listOf(0,0,0,0),
            issued=listOf(200,205,210,215))
        val result=review(next=next,sessions=listOf(first,poor))
        assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
            result.status)
        assertNull(result.loadReviewCandidate)
        assertTrue(result.nextSetSuggestions.isEmpty())
    }

    @Test fun oneRepAtATimeWithoutInventedEffortOrNearFailureProgression() {
        val stable=review(sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(5,6))))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,stable.status)
        assertEquals(listOf(6,7),stable.nextSetSuggestions.map{
            it.proposedNextReps
        })
        assertEquals(1,stable.nextSetSuggestions.maxOf{
            it.proposedNextReps-it.observedReps
        })
        val assumed=review(sessions=listOf(completed(1,3),
            completed(2,4,rirs=listOf(2,null))))
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,assumed.status)
        assertEquals(n(205),assumed.loadReviewCandidate)
        assertEquals(1,assumed.assumedGoalRirSetCount)
        val unreported=assumed.nextSetSuggestions[1]
        assertNull(unreported.actualRir)
        assertEquals(2,unreported.progressionRir)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,unreported.effortSource)
        assertTrue(assumed.explanation.contains("assumes their original goal RIR"))
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_NEAR_FAILURE,
            review(sessions=listOf(completed(1,3),
                completed(2,4,rirs=listOf(2,1)))).status)
        assertEquals(CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
            review(readiness=CoachReadiness.LOW).status)
    }

    @Test fun completedFutureWeekCannotCoachAnAlreadyTrainedEarlierWeek() {
        val completedNext=review(
            next=snapshot(4),sessions=listOf(
                completed(1,3,reps=listOf(5,5)),
                completed(2,4,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
            completedNext.status)
        assertNull(completedNext.loadReviewCandidate)
        assertTrue(completedNext.nextSetSuggestions.isEmpty())

        // Even if an older completed week looks eligible, a later same-day
        // completion must not be silently skipped to justify coaching.
        val late=review(
            next=snapshot(5),sessions=listOf(
                completed(1,3,reps=listOf(8,8)),
                completed(2,6,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
            late.status)
        assertEquals("workout-6-0",late.latestWorkoutId)
        assertTrue(late.nextSetSuggestions.isEmpty())
    }

    @Test fun customRepAmendmentUsesAuthenticGoalAssumptionsWithoutInventingActualRir() {
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(null,null))
        val original=snapshot(5)
        val result=review(next=original,sessions=listOf(completed(1,3),latest))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        val amended=CustomNextRepAmendmentDomain.propose(original,"main",result)
        assertTrue(amended.toString(),amended is CustomNextRepAmendmentResult.Proposed)
        assertEquals(listOf(6,7),(amended as CustomNextRepAmendmentResult.Proposed).amendment.nextMinimumReps)
        assertTrue(result.nextSetSuggestions.all {it.actualRir==null && it.progressionRir==2})
        assertTrue(latest.evidence.visits.all {it.actual?.actualRir==null})
        assertTrue(amended.amendment.amended.slots.single().sets.all {it.prescription.targetRir==2})
        val forged=result.copy(nextSetSuggestions=result.nextSetSuggestions.mapIndexed {i,s->
            if(i==0)s.copy(progressionRir=5) else s
        })
        assertEquals(CustomNextRepAmendmentResult.Rejected(CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(original,"main",forged))
    }

    @Test fun futureCompletionOnDifferentWeekdayDoesNotBlockIndependentDay() {
        val result=review(next=snapshot(5,0),
            sessions=listOf(
                completed(1,3,0,reps=listOf(5,5)),
                completed(2,5,2,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(6,6),result.nextSetSuggestions.map{
            it.proposedNextReps})
        assertEquals(listOf("workout-3-0"),result.comparedWorkoutIds)
    }

    @Test fun duplicateFinishedWorkoutIdentityInvalidatesProgressionEvidence() {
        val earlier=completed(1,3)
        val duplicate=completed(2,4).let{
            it.copy(evidence=it.evidence.copy(
                workoutId=earlier.evidence.workoutId))
        }
        val result=review(sessions=listOf(earlier,duplicate))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            result.status)
        assertNull(result.loadReviewCandidate)
        assertTrue(result.nextSetSuggestions.isEmpty())
    }

    @Test fun alteredHistoricalPrescriptionCannotEarnRepsOrNextLoad() {
        val earlier=completed(1,3)
        val latest=completed(2,4)
        val changedLoad=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedLoad=load(205)) else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,changedLoad)).status)
        val changedReps=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedReps=RepBand(6,8)) else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,changedReps)).status)
        val alteredPrior=earlier.copy(evidence=earlier.evidence.copy(
            visits=earlier.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedRir=0) else visit
            }))
        val result=review(sessions=listOf(alteredPrior,latest))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            result.status)
        assertNull(result.loadReviewCandidate)
        val inventedEvent=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(effectiveEventId="") else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,inventedEvent)).status)
    }

    @Test fun incompleteRecentDayAndChangedExerciseOrSourceNeverCherryPickSuccess() {
        val mostRecent=completed(3,4,reps=listOf(0,0),
            failed=setOf(0,1),rirs=listOf(null,null))
        val missed=review(next=snapshot(5),
            sessions=listOf(completed(1,2),completed(2,3),mostRecent))
        assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
            missed.status)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            missed.comparedWorkoutIds)
        val changed=review(sessions=listOf(completed(1,3),
            completed(2,4,exercise="other_press")))
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_DAY_OR_EXERCISE,
            changed.status)
        val changedPrescription=review(
            next=snapshot(5,loads=listOf(205,205)))
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
            changedPrescription.status)
    }
    @Test fun approvedRepAmendmentChangesOnlyTheNextSetsMinimums() {
        val next=snapshot(5)
        val reviewed=review(next=next,sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(6,5)) ))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,reviewed.status)
        val offer=CustomNextRepAmendmentDomain.propose(next,"main",reviewed)
        assertTrue(offer.toString(),offer is CustomNextRepAmendmentResult.Proposed)
        val amended=(offer as CustomNextRepAmendmentResult.Proposed).amendment
        assertEquals(listOf(7,6),amended.nextMinimumReps)
        assertEquals(next.slots.single().sets.map{it.setKey},
            amended.originalSetKeys)
        assertEquals(listOf(7,6),amended.amended.slots.single().sets.map{
            it.prescription.reps.minimum})
        assertEquals(listOf(8,8),amended.amended.slots.single().sets.map{
            it.prescription.reps.maximum})
        assertEquals(listOf(5,5),next.slots.single().sets.map{
            it.prescription.reps.minimum})
        assertEquals(next.stamp,amended.amended.stamp)
        assertEquals(next.slots.single().sets.map{it.prescription.load},
            amended.amended.slots.single().sets.map{it.prescription.load})
        assertFalse(amended.mayIssueWorkout)
        assertFalse(amended.mayChangeCycle)
    }

    @Test fun tamperedRepOfferAndLowReadinessCannotCreateApprovedAmendment() {
        val next=snapshot(5)
        val reviewed=review(next=next,sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(5,6))))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(next,"main",
                reviewed.copy(nextSetSuggestions=
                    reviewed.nextSetSuggestions.mapIndexed{i,value->
                        if(i==0)value.copy(proposedNextReps=8) else value
                    })))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.INVALID_TARGET_SLOT),
            CustomNextRepAmendmentDomain.propose(next,"another-slot",reviewed))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.NOT_AN_ELIGIBLE_REP_REVIEW),
            CustomNextRepAmendmentDomain.propose(next,"main",
                review(next=next,readiness=CoachReadiness.LOW)))
    }

    @Test fun nextWeekAfterApprovedRepFloorStillReceivesCoaching() {
        val latest=completed(2,5,reps=listOf(6,6))
        val raised=latest.copy(
            snapshot=latest.snapshot.copy(slots=latest.snapshot.slots.map{
                slot->slot.copy(sets=slot.sets.map{set->set.copy(
                    prescription=set.prescription.copy(reps=RepBand(6,8)))
                })
            }),
            evidence=latest.evidence.copy(visits=latest.evidence.visits.map{
                it.copy(prescribedReps=RepBand(6,8))
            }))
        val result=review(next=snapshot(6),sessions=listOf(
            completed(1,4,reps=listOf(5,5)),raised))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(7,7),result.nextSetSuggestions.map{
            it.proposedNextReps})
        assertEquals(listOf("cycle:w6:d0:main:s1","cycle:w6:d0:main:s2"),
            result.nextSetSuggestions.map{it.setKey})
    }


    @Test fun verifiedLoadAmendmentOnlyChangesWorkingSetsForThisDay() {
        val planned=snapshot(5)
        val reviewed=review(next=planned)
        val approved=CustomNextLoadAmendmentDomain.propose(
            planned,"main",reviewed,stock(),n(5))
        assertTrue(approved.toString(),approved is CustomNextLoadAmendmentResult.Proposed)
        val changed=(approved as CustomNextLoadAmendmentResult.Proposed).amendment
        assertEquals(n(200),changed.previousLoad)
        assertEquals(n(205),changed.proposedLoad)
        assertEquals(LoadUnit.LB,changed.unit)
        assertEquals(LoadBasis.MACHINE_STACK,changed.basis)
        assertEquals(planned.slots.single().sets.map{it.setKey},
            changed.workingSetKeys)
        assertEquals(listOf(n(205),n(205)),
            changed.amended.slots.single().sets.map{
                (it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(planned.slots.single().sets.map{it.prescription.reps},
            changed.amended.slots.single().sets.map{it.prescription.reps})
        assertEquals(listOf(n(200),n(200)),planned.slots.single().sets.map{
            (it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(planned.stamp,changed.amended.stamp)
        assertFalse(changed.mayIssueWorkout)
        assertFalse(changed.mayEditCycle)
    }

    @Test fun unavailableStepMixedLoadsAndNearFailureCannotBecomeWeightApprovals() {
        val planned=snapshot(5)
        val success=review(next=planned)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                success,VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.MACHINE_STACK,listOf(n(200),n(210))),n(5)))
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.INVALID_INCREMENT),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                success,stock(),n(2)))
        val unsafe=review(next=snapshot(5,loads=listOf(200,215)),
            sessions=listOf(
                completed(1,3,issued=listOf(200,215),actual=listOf(200,215)),
                completed(2,4,issued=listOf(200,215),actual=listOf(200,215))))
        assertNull(unsafe.loadReviewCandidate)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(
                snapshot(5,loads=listOf(200,215)),"main",unsafe,stock(),n(15)))
        val near=review(sessions=listOf(
            completed(1,3),completed(2,4,rirs=listOf(2,1))))
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                near,stock(),n(5)))
    }

    @Test fun weekdaySpecificLoadReviewCannotIncreaseAnotherDaysBench() {
        val history=listOf(
            completed(1,3,day=0),completed(2,3,day=2),
            completed(3,4,day=0,reps=listOf(7,8)),
            completed(4,4,day=2))
        val upper=review(next=snapshot(5,0),sessions=history)
        val push=review(next=snapshot(5,2),sessions=history)
        assertNull(upper.loadReviewCandidate)
        assertEquals(n(205),push.loadReviewCandidate)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(
                snapshot(5,0),"main",upper,stock(),n(5)))
        assertTrue(CustomNextLoadAmendmentDomain.propose(
            snapshot(5,2),"main",push,stock(),n(5))
            is CustomNextLoadAmendmentResult.Proposed)
    }



    private fun blockEntry(
        week:Int=5,day:Int=0
    )=snapshot(week,day).copy(
        blockId="new-block",weekInBlock=1)

    private fun baseline(
        next:CustomWorkoutDaySnapshot=blockEntry(),
        sessions:List<CustomCoachingHistoricalSession> =
            listOf(completed(1,4)),
        physical:VerifiedLoadStock?=stock()
    )=CustomBlockBaselineContinuityDomain.review(
        CustomBlockBaselineRequest(next,"main",sessions,physical))

    @Test fun adjacentBlocksMayCarryExactObservedContextButNeverEarnAnIncrease() {
        val latest=completed(2,4,rirs=listOf(null,2))
        val carried=baseline(sessions=listOf(completed(1,3),latest))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY,carried.status)
        assertEquals("workout-4-0",carried.previousWorkoutId)
        assertEquals("build",carried.previousBlockId)
        assertEquals("main",carried.previousSlotId)
        assertEquals(listOf(8,8),carried.priorWorkingSets.map{it.performedReps})
        assertEquals(listOf(null,2),carried.priorWorkingSets.map{it.actualRir})
        assertEquals(listOf(n(200),n(200)),carried.priorWorkingSets.map{it.priorLoad})
        assertFalse(carried.mayApplyProgression)
        assertFalse(carried.mayChangePrescription)
        assertFalse(carried.mayIssueWorkout)
        assertFalse(carried.mayModifyHistory)
        assertTrue(runCatching {
            (carried.priorWorkingSets as MutableList<CustomBlockBaselineSet>).clear()
        }.isFailure)
        // Coaching still rejects both rep/weight promotion across blocks.
        val coached=review(next=blockEntry(),
            sessions=listOf(completed(1,3),latest))
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,coached.status)
        assertNull(coached.loadReviewCandidate)
        assertTrue(coached.nextSetSuggestions.isEmpty())
        assertNull(latest.evidence.visits.first().actual?.actualRir)
    }

    @Test fun phaseAndPurposeChangesExposeOnlyFactsButNewSplitsRequireNewBaseline() {
        val current=blockEntry()
        val phase=baseline(next=current.copy(phase=TrainingPhase.STRENGTH))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY,
            phase.status)
        assertEquals(listOf(8,8),phase.priorWorkingSets.map{it.performedReps})
        assertFalse(phase.mayApplyProgression)
        val purpose=baseline(next=current.copy(
            sessionPurpose=ComposerSessionPurpose.STRENGTH_SKILL))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY,
            purpose.status)
        assertNull(purpose.priorWorkingSets.firstOrNull{it.actualRir==null})
        assertFalse(purpose.mayIssueWorkout)
        assertEquals(CustomBlockBaselineStatus.PHASE_PURPOSE_OR_SPLIT_CHANGED,
            baseline(next=current.copy(split=CustomSplitIdentity.Freeform("PPL"))).status)
        assertEquals(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
            baseline(next=current.copy(stamp=current.stamp.copy(
                gymCatalogSourceRevision=3))).status)
        assertEquals(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
            baseline(next=current.copy(stamp=current.stamp.copy(
                athleteProfileRevision="new-profile"))).status)
    }

    @Test fun newWeightsRepsAndEquipmentCannotBorrowOldBlockWorkingTargets() {
        val current=blockEntry()
        fun amendedSets(change:(CustomAuthoredSet)->CustomAuthoredSet)=
            current.copy(slots=current.slots.map {slot ->
                slot.copy(sets=slot.sets.map {set ->
                    set.copy(prescription=change(set.prescription))
                })
            })
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(load=load(205))}).status)
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(reps=RepBand(6,10))}).status)
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(targetRir=3)}).status)
        assertEquals(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
            baseline(physical=null).status)
        assertEquals(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
            baseline(physical=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.MACHINE_STACK,listOf(n(205),n(210)))).status)
        val distinctVariant=current.copy(slots=current.slots.map{
            it.copy(variantId="cable")
        })
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(next=distinctVariant).status)
        val anotherMovement=current.copy(slots=current.slots.map {
            it.copy(exerciseId="other_chest_press")
        })
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(next=anotherMovement).status)
    }

    @Test fun failedLatestSameDayCannotCherryPickEarlierPerfectExposure() {
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),
                completed(2,4,failed=setOf(0)))).status)
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),
                completed(2,4,reps=listOf(4,8)))).status)
        val altered=completed(2,4)
        val mismatch=altered.copy(evidence=altered.evidence.copy(
            visits=altered.evidence.visits.mapIndexed{index,visit ->
                if(index==0)visit.copy(actual=visit.actual?.copy(load=n(205)))
                else visit
            }))
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),mismatch)).status)
        val moved=completed(2,4,exercise="different_machine")
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(sessions=listOf(completed(1,3),moved)).status)
    }

    private fun starting(
        next:CustomWorkoutDaySnapshot=blockEntry(),
        sessions:List<CustomCoachingHistoricalSession> = listOf(completed(1,4)),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,physical:VerifiedLoadStock?=stock()
    )=CustomBlockStartingLoadDomain.review(
        CustomBlockBaselineRequest(next,"main",sessions,physical),
        readiness,restricted)

    @Test fun newBlockStartingRecommendationKeepsEachSetsOwnApprovedWeight() {
        val prior=completed(1,4,issued=listOf(200,215))
        val next=blockEntry().copy(slots=blockEntry().slots.map {slot->
            slot.copy(sets=slot.sets.mapIndexed {index,set->
                set.copy(prescription=set.prescription.copy(
                    load=load(if(index==0)200 else 215)))
            })
        })
        val result=starting(next=next,sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,result.status)
        assertEquals(listOf(n(200),n(215)),result.startingSets.map{it.load})
        assertEquals(next.slots.single().sets.map{it.setKey},
            result.startingSets.map{it.setKey})
        assertEquals(listOf(1,2),result.startingSets.map{it.ordinal})
        assertTrue(result.startingSets.all{it.reps==band() && it.goalRir==2})
        assertFalse(result.mayApplyProgression)
        assertFalse(result.mayChangePrescription)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
        assertTrue(runCatching {
            (result.startingSets as MutableList<CustomBlockStartingSet>).clear()
        }.isFailure)
        assertEquals(listOf(n(200),n(215)),prior.evidence.visits.map{it.actual!!.load})
    }

    @Test fun unreportedEffortUsesOnlyOriginalGoalAndKeepsActualBlank() {
        val prior=completed(1,4,rirs=listOf(null,2))
        val result=starting(sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,result.status)
        assertEquals(1,result.assumedGoalRirSetCount)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,
            result.startingSets.first().effortSource)
        assertEquals(ProgressionEffortSource.REPORTED,
            result.startingSets.last().effortSource)
        assertNull(result.startingSets.first().actualRir)
        assertNull(prior.evidence.visits.first().actual!!.actualRir)
        assertTrue(result.explanation.contains("original goal RIR"))
    }

    @Test fun realNearFailureOverridesGoalAndBlocksStartingRecommendation() {
        for(rir in listOf(0,1)) {
            val result=starting(sessions=listOf(
                completed(1,4,rirs=listOf(rir,null))))
            assertEquals(CustomBlockStartingLoadStatus.NEAR_FAILURE_REQUIRES_REVIEW,
                result.status)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun noEffortSourceNeverBecomesAnAssumedStartingWeight() {
        fun withoutGoals(snapshot:CustomWorkoutDaySnapshot)=snapshot.copy(
            slots=snapshot.slots.map {slot->slot.copy(sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(targetRir=null))
            })})
        val original=completed(1,4,rirs=listOf(null,null))
        val prior=original.copy(snapshot=withoutGoals(original.snapshot),
            evidence=original.evidence.copy(visits=original.evidence.visits.map {
                it.copy(prescribedRir=null)
            }))
        val result=starting(next=withoutGoals(blockEntry()),sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.EFFORT_UNAVAILABLE,result.status)
        assertEquals(0,result.assumedGoalRirSetCount)
        assertTrue(result.startingSets.isEmpty())
        assertTrue(prior.evidence.visits.all{it.actual!!.actualRir==null})
    }

    @Test fun newBlockRecoveryAndMovementRestrictionTakePrecedence() {
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            starting(readiness=CoachReadiness.LOW).status)
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            starting(readiness=CoachReadiness.STRONG,restricted=true).status)
        assertEquals(starting().startingSets,
            starting(readiness=CoachReadiness.STRONG).startingSets)
    }

    @Test fun phaseAndPurposeReferencesNeverConvertIntoNewStartingWeights() {
        for(next in listOf(blockEntry().copy(phase=TrainingPhase.STRENGTH),
            blockEntry().copy(sessionPurpose=ComposerSessionPurpose.STRENGTH_SKILL))) {
            val result=starting(next=next)
            assertEquals(CustomBlockStartingLoadStatus.CHANGED_TRAINING_CONTEXT,
                result.status)
            assertEquals(2,result.baseline.priorWorkingSets.size)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun changedOrFailedLatestWorkCannotBecomeAStartingLoadRecommendation() {
        val current=blockEntry()
        val changed=current.copy(slots=current.slots.map {slot->
            slot.copy(sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(load=load(205)))
            })
        })
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            starting(next=changed).status)
        for(latest in listOf(completed(2,4,failed=setOf(0)),
            completed(2,4,reps=listOf(4,8)),
            completed(2,4,exercise="different_machine"))) {
            val result=starting(sessions=listOf(completed(1,3),latest))
            assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,result.status)
            assertTrue(result.startingSets.isEmpty())
        }
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            starting(physical=null).status)
    }

    @Test fun startingWeightsCannotBorrowOtherDaysOrSkipAnUntrainedWeek() {
        for(sessions in listOf(listOf(completed(1,4,day=2)),
            listOf(completed(1,3)),
            listOf(completed(1,4),completed(2,5)))) {
            val result=starting(sessions=sessions)
            assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,result.status)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun noBorrowingBetweenWeekdaysOrAfterMissingWeekOrFinishedTarget() {
        val otherDay=completed(1,4,2)
        assertEquals(CustomBlockBaselineStatus.NO_PRIOR_SAME_WEEKDAY,
            baseline(sessions=listOf(otherDay)).status)
        assertEquals(CustomBlockBaselineStatus.GAP_OR_SAME_BLOCK,
            baseline(sessions=listOf(completed(1,3))).status)
        assertEquals(CustomBlockBaselineStatus.NOT_BLOCK_ENTRY,
            baseline(next=blockEntry().copy(weekInBlock=2)).status)
        val finished=completed(2,5)
        assertEquals(CustomBlockBaselineStatus.TARGET_ALREADY_TRAINED,
            baseline(sessions=listOf(completed(1,4),finished)).status)
        val duplicate=completed(2,4).copy(completionOrder=1)
        assertEquals(CustomBlockBaselineStatus.INVALID_HISTORY,
            baseline(sessions=listOf(completed(1,3),duplicate)).status)
    }


    @Test fun optionalTargetRirV2CanRecommendSeveralRepsWithoutOneRepCap() {
        val older=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(5,4))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(older,latest),stock(),n(5),CoachReadiness.NORMAL,false,
            CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,result.repForecastPolicy)
        assertEquals(listOf(8,8),result.nextSetSuggestions.map{it.proposedNextReps})
        assertTrue(result.forecastLimitations.contains(
            RepForecastLimitation.CHRONIC_FATIGUE_NOT_ESTIMATED))
        assertNull(result.loadReviewCandidate)
        val amendment=CustomNextRepAmendmentDomain.propose(
            snapshot(5),"main",result) as CustomNextRepAmendmentResult.Proposed
        assertEquals(listOf(8,8),amendment.amendment.nextMinimumReps)
        assertTrue(amendment.amendment.amended.slots.single().sets.all {
            it.prescription.reps==RepBand(8,8) &&
                (it.prescription.load as DraftSetLoad.Verified).value==n(200)
        })
        val legacy=review(sessions=listOf(older,latest))
        assertEquals(CustomRepForecastPolicy.LEGACY_STEP_V1,legacy.repForecastPolicy)
        assertEquals(listOf(6,7),legacy.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(listOf(5,6),latest.evidence.visits.map{it.actual!!.reps})
    }

    @Test fun targetRirV2NeverTurnsAssumedEffortIntoExtraHeadroom() {
        val previous=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(null,null))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(previous,latest),stock(),n(5),CoachReadiness.NORMAL,false,
            CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,result.status)
        assertEquals(listOf(5,6),result.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(2,result.assumedGoalRirSetCount)
        assertTrue(result.nextSetSuggestions.all {it.actualRir==null})
        assertTrue(CustomNextRepAmendmentDomain.propose(snapshot(5),"main",result)
            is CustomNextRepAmendmentResult.Rejected)
    }

    @Test fun v2AmendmentRejectsForgedTargetsAboveObservedRirHeadroom() {
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(4,3))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(completed(1,3),latest),stock(),n(5),
            CoachReadiness.NORMAL,false,CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(listOf(7,7),result.nextSetSuggestions.map{it.proposedNextReps})
        val forged=result.copy(nextSetSuggestions=result.nextSetSuggestions.mapIndexed {
            i,value-> if(i==0)value.copy(proposedNextReps=8) else value
        })
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(snapshot(5),"main",forged))
    }
}
