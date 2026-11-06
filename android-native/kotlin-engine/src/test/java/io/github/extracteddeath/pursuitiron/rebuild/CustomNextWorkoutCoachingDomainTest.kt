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
        assertEquals(CustomNextWorkoutCoachingStatus.NEEDS_RECORDED_EFFORT,
            review(sessions=listOf(completed(1,3),
                completed(2,4,rirs=listOf(2,null)))).status)
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


}
