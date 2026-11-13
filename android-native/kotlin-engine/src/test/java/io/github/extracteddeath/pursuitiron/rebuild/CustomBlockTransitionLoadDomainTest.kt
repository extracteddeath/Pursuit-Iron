package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomBlockTransitionLoadDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n(195),n(200),n(205),n(210),n(215)))
    private fun snapshot(new:Boolean=false,loads:List<Int> =listOf(200,200),
        goal:Int?=if(new)3 else 2,reps:RepBand=RepBand(5,8),
        rest:Int=if(new)180 else 120):CustomWorkoutDaySnapshot {
        val week=if(new)5 else 4
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle",3,"gym-1",2),
            week,if(new)1 else 4,if(new)"strength" else "hypertrophy",
            if(new)TrainingPhase.STRENGTH else TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("ULPPL"),
            0,"Upper",if(new)ComposerSessionPurpose.STRENGTH_SKILL
                else ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot("main","chest_press","chest","machine",
                ComposerRole.ACCESSORY,null,null,true,"",loads.mapIndexed {i,load->
                    CustomWorkoutSetSnapshot("cycle:w$week:d0:main:s${i+1}","main",i+1,
                        CustomAuthoredSet(DraftSetType.WORKING,
                            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(load)),
                            reps,goal,60,rest))
                })),360,200)
    }
    private fun completed(snap:CustomWorkoutDaySnapshot=snapshot(),
        rirs:List<Int?> =listOf(2,2),performed:List<Int> =listOf(8,8),
        failed:Boolean=false,order:Long=1):CustomCoachingHistoricalSession {
        val visits=snap.slots.single().sets.mapIndexed {i,set->
            CustomHistoricalVisitFact("main","chest_press",set.setKey,
                CustomWorkoutVisitPart.MAIN,set.prescription.type,
                set.prescription.load,set.prescription.reps,set.prescription.targetRir,null,
                if(failed)KotlinJournalSetOutcome.FAILED_ATTEMPT
                    else KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet((set.prescription.load as DraftSetLoad.Verified).value,
                    LoadUnit.LB,if(failed)0 else performed[i],if(failed)null else rirs[i]),
                "logged-${snap.absoluteWeek}-$i")
        }
        return CustomCoachingHistoricalSession(order,snap,
            CustomWorkoutHistoricalEvidence("athlete-1","workout-${snap.absoluteWeek}",
                "cycle",3,"finish-${snap.absoluteWeek}",snap.slots.single().sets.size+1L,visits))
    }
    private fun review(next:CustomWorkoutDaySnapshot=snapshot(true,listOf(215,215)),
        history:List<CustomCoachingHistoricalSession> =listOf(completed()),
        readiness:CoachReadiness=CoachReadiness.NORMAL,restricted:Boolean=false,
        physical:VerifiedLoadStock?=stock)=CustomBlockTransitionLoadDomain.review(
            CustomBlockBaselineRequest(next,"main",history,physical),readiness,restricted)

    private fun onMachine(day:CustomWorkoutDaySnapshot,machine:String?)=
        day.copy(slots=day.slots.map{it.copy(machineInstanceId=machine)})

    private fun performedOnMachine(
        session:CustomCoachingHistoricalSession,machine:String?
    )=session.copy(evidence=session.evidence.copy(
        visits=session.evidence.visits.map{
            it.copy(actual=it.actual?.copy(machineInstanceId=machine))
        }))

    @Test fun crossBlockAAndBRequireSameIssuedAndActuallyPerformedMachine() {
        val oldA=onMachine(snapshot(loads=listOf(200,205)),"machine-a")
        val prior=completed(oldA)
        val actualA=performedOnMachine(prior,"machine-a")
        val nextA=onMachine(snapshot(true,listOf(215,215)),"machine-a")
        val allowed=review(nextA,listOf(actualA))
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,
            allowed.status)
        assertEquals(listOf(n(200),n(205)),allowed.sets.map{it.proposedLoad})
        assertFalse(allowed.mayIssueWorkout)
        assertFalse(allowed.mayApplyAutomatically)

        val nextB=onMachine(nextA,"machine-b")
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            review(nextB,listOf(actualA)).status)
        val mismatched=review(nextA,listOf(performedOnMachine(prior,"machine-b")))
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            mismatched.status)
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            mismatched.reference.status)
        assertTrue(mismatched.reference.explanation.contains("physical machine"))
        assertNull(mismatched.proposedWorkout)
        assertTrue(mismatched.sets.isEmpty())

        // Even a matching ISSUE-station A without recorded performed-machine
        // evidence is a legacy unknown, not verified machine A work.
        val unknown=review(nextA,listOf(prior))
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            unknown.status)
        assertNull(unknown.proposedWorkout)
        val legacy=review(nextA,listOf(completed(snapshot())))
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            legacy.status)

        // Exact unchanged recipe is still a read-only context reference
        // when both the issued and PERFORMED station IDs agree.
        val sameRecipe=onMachine(snapshot().copy(
            absoluteWeek=5,weekInBlock=1,blockId="new-block"),"machine-a")
        val reference=CustomBlockBaselineContinuityDomain.review(
            CustomBlockBaselineRequest(sameRecipe,"main",
                listOf(performedOnMachine(completed(oldA),"machine-a")),stock))
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            reference.status)
        val validOld=onMachine(snapshot(),"machine-a")
        val positive=CustomBlockBaselineContinuityDomain.review(
            CustomBlockBaselineRequest(sameRecipe,"main",
                listOf(performedOnMachine(completed(validOld),"machine-a")),stock))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY,
            positive.status)
        assertFalse(positive.mayApplyProgression)
    }

    @Test fun changedPhaseAndRecipeUsePerSetDemonstratedLoadsWithoutAnEarnedIncrease() {
        val previous=completed(snapshot(loads=listOf(200,205)))
        val original=snapshot(true,listOf(215,215))
        val result=review(original,listOf(previous))
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,result.status)
        assertEquals(listOf(n(200),n(205)),result.sets.map{it.proposedLoad})
        assertTrue(result.changesAuthoredLoads)
        assertEquals(listOf(5,5),result.sets.map{it.startingReps})
        assertEquals(listOf(3,3),result.sets.map{it.targetRir})
        assertEquals(listOf(n(215),n(215)),original.slots.single().sets.map{
            (it.prescription.load as DraftSetLoad.Verified).value})
        val projected=checkNotNull(result.proposedWorkout)
        assertEquals(listOf(n(200),n(205)),projected.slots.single().sets.map{
            (it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(original.slots.single().sets.map{it.prescription.reps},
            projected.slots.single().sets.map{it.prescription.reps})
        assertEquals(original.stamp,projected.stamp)
        assertEquals(previous,completed(snapshot(loads=listOf(200,205))))
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayEditCycle)
        assertFalse(result.mayModifyHistory)
        assertTrue(runCatching{(result.sets as MutableList<CustomBlockTransitionSet>).clear()}.isFailure)
    }

    @Test fun alreadyLowerAuthoredWeightIsKeptAndUnchangedRecipeUsesOrdinaryReview() {
        val result=review(snapshot(true,listOf(195,195)))
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,result.status)
        assertEquals(listOf(n(195),n(195)),result.sets.map{it.proposedLoad})
        assertFalse(result.changesAuthoredLoads)
        val same=snapshot().copy(absoluteWeek=5,weekInBlock=1,blockId="second")
        assertEquals(CustomBlockTransitionLoadStatus.NO_TRANSITION_NEEDED,review(same).status)
    }

    @Test fun originalGoalAssumptionNeverUsesTheNewBlocksGoal() {
        val previous=completed(rirs=listOf(null,2))
        val okay=review(history=listOf(previous))
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,okay.status)
        assertEquals(1,okay.assumedGoalRirSetCount)
        assertEquals(2,okay.sets.first().previousGoalRir)
        assertEquals(3,okay.sets.first().targetRir)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,okay.sets.first().effortSource)
        assertNull(okay.sets.first().previousActualRir)
        assertNull(previous.evidence.visits.first().actual!!.actualRir)
        // Original capacity evidence: 8 performed + goal 2 = 10; new demand
        // 5 starting reps + goal 6 = 11. Using the NEW goal would falsely pass.
        assertEquals(CustomBlockTransitionLoadStatus.DEMAND_EXCEEDS_EVIDENCE,
            review(snapshot(true,listOf(215,215),goal=6),listOf(previous)).status)
    }

    @Test fun missingBothEffortSourcesAndRealNearFailureNeverBorrowNewGoal() {
        assertEquals(CustomBlockTransitionLoadStatus.EFFORT_UNAVAILABLE,
            review(history=listOf(completed(snapshot(goal=null),rirs=listOf(null,null)))).status)
        for(rir in listOf(0,1))assertEquals(CustomBlockTransitionLoadStatus.NEAR_FAILURE,
            review(history=listOf(completed(rirs=listOf(rir,null)))).status)
    }

    @Test fun newDemandOrShorterRestCannotReuseTheOldLoad() {
        assertEquals(CustomBlockTransitionLoadStatus.DEMAND_EXCEEDS_EVIDENCE,
            review(snapshot(true,listOf(215,215),reps=RepBand(10,15))).status)
        assertEquals(CustomBlockTransitionLoadStatus.SHORTER_REST,
            review(snapshot(true,listOf(215,215),rest=119)).status)
        assertEquals(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,
            review(snapshot(true,listOf(215,215),goal=1)).status)
        assertEquals(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,
            review(snapshot(true,listOf(215,215),goal=null)).status)
    }

    @Test fun recoveryAndRestrictionsStillWinEvenWithStrongPerformance() {
        assertEquals(CustomBlockTransitionLoadStatus.RECOVERY_OR_RESTRICTION,
            review(readiness=CoachReadiness.LOW).status)
        assertEquals(CustomBlockTransitionLoadStatus.RECOVERY_OR_RESTRICTION,
            review(readiness=CoachReadiness.STRONG,restricted=true).status)
    }

    @Test fun finalSetRestDoesNotPretendToAffectEarlierWorkingPerformance() {
        val next=snapshot(true,listOf(215,215)).let{s->s.copy(slots=s.slots.map{slot->
            slot.copy(sets=slot.sets.mapIndexed{i,set->
                if(i==slot.sets.lastIndex)set.copy(prescription=set.prescription.copy(restSeconds=0))
                else set
            })})}
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,review(next).status)
    }

    @Test fun reorderedWorkingSetsAndChangedWarmupsNeedTheirOwnCalibration() {
        val reversed=snapshot(true).let{s->s.copy(slots=s.slots.map{
            it.copy(sets=it.sets.reversed())})}
        assertEquals(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,review(reversed).status)
        val extraWarmup=snapshot(true).let{s->s.copy(slots=s.slots.map{slot->
            slot.copy(sets=listOf(slot.sets.first().copy(setKey="extra-warmup",ordinalInSlot=3,
                prescription=slot.sets.first().prescription.copy(type=DraftSetType.WARMUP)))+slot.sets)
        })}
        assertEquals(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,review(extraWarmup).status)
    }

    @Test fun latestFailureCannotBeSkippedAndOtherDaysOrSourcesStaySeparate() {
        val earlier=snapshot().copy(absoluteWeek=3,weekInBlock=3)
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            review(history=listOf(completed(earlier),completed(failed=true,order=2))).status)
        for(next in listOf(snapshot(true).copy(weekday=2),
            snapshot(true).copy(stamp=snapshot(true).stamp.copy(gymCatalogSourceRevision=3)),
            snapshot(true).copy(split=CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER)))) {
            val result=review(next)
            assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,result.status)
            assertTrue(result.sets.isEmpty())
        }
    }

    @Test fun missingStockImpossibleWeightsAndExtraSetsCannotYieldAProposal() {
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,
            review(physical=null).status)
        assertEquals(CustomBlockTransitionLoadStatus.EQUIPMENT_UNVERIFIED,
            review(snapshot(true,listOf(211,211))).status)
        assertEquals(CustomBlockTransitionLoadStatus.UNSUPPORTED_PRESCRIPTION,
            review(snapshot(true,listOf(215,215,215))).status)
    }

    @Test fun malformedOrdinalsFailClosedInsteadOfThrowingOrDuplicatingFacts() {
        val old=snapshot().let {s->s.copy(slots=s.slots.map{slot->
            slot.copy(sets=slot.sets.map{it.copy(ordinalInSlot=1)})})}
        val result=review(history=listOf(completed(old)))
        assertEquals(CustomBlockTransitionLoadStatus.BASELINE_UNAVAILABLE,result.status)
        assertTrue(result.sets.isEmpty())
    }
}
