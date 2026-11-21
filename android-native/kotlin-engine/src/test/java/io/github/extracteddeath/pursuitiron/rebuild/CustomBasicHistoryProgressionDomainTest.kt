package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomBasicHistoryProgressionDomainTest {
    private fun n(value:Int)=BigDecimal(value)
    private fun load(value:Int)=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n(value))
    private fun set(value:Int=100,amp:CustomSetAmplifier?=null)=CustomAuthoredSet(
        DraftSetType.WORKING,load(value),RepBand(6,10),2,45,90,amp)
    private fun cycle(
        method:SavedProgressionMethod=SavedProgressionMethod.DOUBLE,
        overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap()
    ):CustomTrainingCycle {
        val slot=CustomExerciseSlot("main","press",ComposerRole.SECONDARY,
            listOf(set(),set()),progressionMethod=method)
        return CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.HYPERTROPHY,
            "Plan",listOf(CustomTrainingBlock(
                "base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,4,
                CustomSplitIdentity.Freeform("Custom"),
                listOf(CustomTrainingDay(0,"Upper",
                    ComposerSessionPurpose.HYPERTROPHY,listOf(slot)),
                    CustomTrainingDay(2,"Push",
                    ComposerSessionPurpose.HYPERTROPHY,listOf(
                        slot.copy(slotId="push")))))),
            weekOverrides=overrides)
    }
    private fun snapshot(
        week:Int=2,day:Int=0,machine:String?=null,
        loads:List<Int> = listOf(100,100),
        shortened:Boolean=false,amp:CustomSetAmplifier?=null
    ):CustomWorkoutDaySnapshot {
        val slotId=if(day==0)"main" else "push"
        val slot=CustomWorkoutSlotSnapshot(slotId,"press","chest","machine",
            ComposerRole.SECONDARY,null,null,true,"",
            loads.mapIndexed{index,weight->
                CustomWorkoutSetSnapshot(
                    "cycle:w"+week+":d"+day+":"+slotId+":s"+(index+1),
                    slotId,index+1,set(weight,amp))
            },machineInstanceId=machine)
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile-1","cycle",1,
                "gym",1),week,week,"base",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            day,"Day",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),360,180,
            shortSessionLimitMinutes=if(shortened)30 else null)
    }
    private fun completed(
        order:Long,week:Int=1,day:Int=0,machine:String?=null,
        reps:List<Int> = listOf(10,10),
        loads:List<Int> = listOf(100,100),
        failed:Set<Int> = emptySet(),
        shortened:Boolean=false,
        amp:CustomSetAmplifier?=null
    ):CustomCoachingHistoricalSession {
        val daySnapshot=snapshot(week,day,machine,loads,shortened,amp)
        val slot=daySnapshot.slots.single()
        val visits=slot.sets.mapIndexed{index,scheduled->
            val missed=index in failed
            CustomHistoricalVisitFact(
                slot.slotId,slot.exerciseId,scheduled.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                scheduled.prescription.load,scheduled.prescription.reps,2,
                amp?.kind,
                if(missed) KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(loads[index]),LoadUnit.LB,
                    if(missed)0 else reps[index],null,machine),
                "record-"+order+"-"+index)
        }
        val evidence=CustomWorkoutHistoricalEvidence(
            "athlete","workout-"+order,"cycle",1,"finish-"+order,3,visits,
            shortSessionLimitMinutes=if(shortened)30 else null)
        return CustomCoachingHistoricalSession(order,daySnapshot,evidence)
    }
    private fun stock()=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n(95),n(100),n(105),n(110)))
    private fun review(
        next:CustomWorkoutDaySnapshot=snapshot(),
        sessions:List<CustomCoachingHistoricalSession> = listOf(completed(1)),
        program:CustomTrainingCycle=cycle(),
        nextWorkoutId:String="future-2",
        machine:String?=null
    ):CustomBasicHistoryResult =
        CustomBasicHistoryProgressionDomain.review(CustomBasicHistoryRequest(
            program,next,nextWorkoutId,if(next.weekday==0)"main" else "push",
            sessions,machine,stock(),n(5)))

    @Test fun strictDoubleUsesOnlyCompletedSameSlotWorkAndVerifiedPhysicalStep() {
        val actual=review()
        assertTrue(actual.toString(),actual is CustomBasicHistoryResult.Reviewed)
        val result=actual as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",result.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),result.candidate.proposedWorkingLoads)
        assertEquals(setOf(0,1),result.candidate.increasedOrdinals)
        assertEquals(BasicProgressionDecision.EARNED_INCREMENT,
            result.candidate.decision)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
    }

    @Test fun dynamicProgressionNeverLevelsAHeavierSetAcrossTheWholeExercise() {
        val history=completed(1,reps=listOf(10,5))
        val result=review(program=cycle(SavedProgressionMethod.DYNAMIC),
            sessions=listOf(history))
        assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
        val candidate=(result as CustomBasicHistoryResult.Reviewed).candidate
        assertEquals(listOf(n(105),n(100)),candidate.proposedWorkingLoads)
        assertEquals(setOf(0),candidate.increasedOrdinals)
    }

    @Test fun manualLoggingHasNoHistoryDependencyOrHiddenLoadSuggestion() {
        val result=review(program=cycle(SavedProgressionMethod.MANUAL),
            sessions=emptyList())
        assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
        val ready=result as CustomBasicHistoryResult.Reviewed
        assertNull(ready.historyWorkoutId)
        assertNull(ready.completionOrder)
        assertEquals(BasicProgressionDecision.MANUAL_REPEAT,
            ready.candidate.decision)
        assertEquals(listOf(n(100),n(100)),
            ready.candidate.proposedWorkingLoads)
        assertTrue(ready.candidate.increasedOrdinals.isEmpty())
    }

    @Test fun newestFailedSameStationBlocksOlderSuccessRatherThanSkippingBack() {
        val history=listOf(completed(1),completed(2,week=2,
            reps=listOf(10,10),failed=setOf(1)))
        val result=review(next=snapshot(3),sessions=history)
        assertEquals(CustomBasicHistoryResult.Rejected(
            CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS),result)
    }

    @Test fun differentPhysicalMachinesHaveIndependentEarnedLoadHistories() {
        val earlier=completed(1,week=1,machine="A")
        val newer=completed(2,week=2,machine="B",
            reps=listOf(5,5))
        val a=review(next=snapshot(3,machine="A"),
            sessions=listOf(earlier,newer),machine="A")
        assertTrue(a.toString(),a is CustomBasicHistoryResult.Reviewed)
        assertEquals("workout-1",
            (a as CustomBasicHistoryResult.Reviewed).historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),a.candidate.proposedWorkingLoads)
        // The saved default was A, but the athlete really switched to
        // B. Physical actuals, not the former default, isolate the evidence.
        val alternate=newer.copy(snapshot=
            newer.snapshot.copy(slots=newer.snapshot.slots.map{
                it.copy(machineInstanceId="A")
            }))
        val aAfterSwitch=review(next=snapshot(3,machine="A"),
            sessions=listOf(earlier,alternate),machine="A")
        assertTrue(aAfterSwitch.toString(),
            aAfterSwitch is CustomBasicHistoryResult.Reviewed)
        assertEquals("workout-1",
            (aAfterSwitch as CustomBasicHistoryResult.Reviewed).historyWorkoutId)
        val b=review(next=snapshot(3,machine="B"),
            sessions=listOf(earlier,newer),machine="B")
        assertTrue(b.toString(),b is CustomBasicHistoryResult.Reviewed)
        assertEquals(BasicProgressionDecision.BUILD_REPS,
            (b as CustomBasicHistoryResult.Reviewed).candidate.decision)
        assertEquals(listOf(n(100),n(100)),b.candidate.proposedWorkingLoads)
        assertEquals(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY,
            (review(next=snapshot(3),
                sessions=listOf(earlier,newer)) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun issuedMachineWithNoActualStationNeverBecomesGenericOrOtherStationEvidence() {
        val unverified=completed(2,week=2,machine="B").let { session ->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map { visit ->
                    visit.copy(actual=visit.actual?.copy(machineInstanceId=null))
                }))
        }
        // Even a recorded B prescription is not proof that B was used.
        // Nor can its unverified actuals be credited to generic equipment.
        for(machine in listOf("B",null)) {
            val result=review(next=snapshot(3,machine=machine),
                sessions=listOf(unverified),machine=machine)
            assertEquals(machine.toString(),
                CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
                (result as CustomBasicHistoryResult.Rejected).reason)
        }
        // A more recent unknown station must block the older verified A
        // success instead of silently earning another A load increment.
        val priorA=completed(1,machine="A")
        val result=review(next=snapshot(3,machine="A"),
            sessions=listOf(priorA,unverified),machine="A")
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (result as CustomBasicHistoryResult.Rejected).reason)

        // Mixed known/unknown actual station within the latest visit cannot
        // be skipped as if all sets were verified on B. That would let the
        // older A workout earn an unapproved increase.
        val mixed=completed(3,week=3,machine="B").let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.mapIndexed{index,visit->
                    if(index==1) visit.copy(actual=visit.actual?.copy(
                        machineInstanceId=null)) else visit
                }))
        }
        val mixedReview=review(next=snapshot(4,machine="A"),
            sessions=listOf(priorA,mixed),machine="A")
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (mixedReview as CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun programDayAndAuthoredWeekOverridesCannotBorrowOtherNumericalOwners() {
        assertEquals(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY,
            (review(next=snapshot(2,day=2)) as
                CustomBasicHistoryResult.Rejected).reason)
        val authored=CustomWeekSlotOverride(2,0,"main",
            listOf(set(105),set(105)))
        assertEquals(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD,
            (review(program=cycle(overrides=mapOf("base" to listOf(authored))),
                next=snapshot(2,loads=listOf(105,105))) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun exactRepOnlyRoutesNeverDeriveExactTargetsFromARepBand() {
        assertEquals(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE,
            (review(program=cycle(SavedProgressionMethod.REP_TARGETS)) as
                CustomBasicHistoryResult.Rejected).reason)
        assertEquals(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE,
            (review(program=cycle(SavedProgressionMethod.LADDER)) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun sourceChangesShortSessionsTechniqueFollowupsAndLoadEditsFailClosed() {
        val changed=completed(1).let {
            it.copy(snapshot=it.snapshot.copy(stamp=it.snapshot.stamp.copy(
                cycleRevision=2)),
                evidence=it.evidence.copy(cycleRevision=2))
        }
        assertEquals(CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED,
            (review(sessions=listOf(changed)) as
                CustomBasicHistoryResult.Rejected).reason)
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(completed(1,shortened=true))) as
                CustomBasicHistoryResult.Rejected).reason)
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=4)
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(completed(1,amp=amp))) as
                CustomBasicHistoryResult.Rejected).reason)
        val wrongUnit=completed(1).let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map{v->
                    v.copy(actual=v.actual?.copy(unit=LoadUnit.KG))
                }))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(wrongUnit)) as
                CustomBasicHistoryResult.Rejected).reason)
        val changedBand=completed(1).let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{slot->slot.copy(sets=
                    slot.sets.map{it.copy(prescription=
                        it.prescription.copy(reps=RepBand(5,8)))})}),
                evidence=session.evidence.copy(visits=
                    session.evidence.visits.map{it.copy(
                        prescribedReps=RepBand(5,8))}))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(changedBand)) as
                CustomBasicHistoryResult.Rejected).reason)
        val altered=completed(1).let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.mapIndexed{i,v->
                    if(i==0)v.copy(actual=v.actual?.copy(load=n(95))) else v
                }))
        }
        assertEquals(BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY,
            (review(sessions=listOf(altered)) as
                CustomBasicHistoryResult.Rejected).evaluatorReason)
        assertEquals(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,
            (review(next=snapshot(shortened=true)) as
                CustomBasicHistoryResult.Rejected).reason)
    }
}
