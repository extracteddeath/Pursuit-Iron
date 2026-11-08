package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinCustomNextCycleSessionReviewTest {
    private fun cycle()=CustomTrainingCycle(
        cycleId="cycle",revision=3,athleteId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,title="First program",
        blocks=listOf(CustomTrainingBlock(
            "base","Base",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,2,
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
            listOf(0,2,4).map{day->
                CustomTrainingDay(day,"Day "+day,
                    ComposerSessionPurpose.HYPERTROPHY,emptyList())
            })))
    private fun fact(w:Int,day:Int,issued:Boolean=true,completed:Boolean=true,
        rev:Long=3)=NativeCustomCycleSessionFact(
        "w"+w+"d"+day,w,day,rev,issued,completed)

    @Test fun emptyEnrolledHistoryProposesOnlyFirstScheduledWorkout() {
        assertEquals(NativeCustomNextCycleSlot.Next(1,0,"Day 0"),
            NativeCustomNextCycleSlotDomain.select(cycle(),emptyList()))
    }
    @Test fun selectsNextAcrossWeekBoundaryAfterFinishedSessions() {
        val first=listOf(fact(1,0),fact(1,2))
        assertEquals(NativeCustomNextCycleSlot.Next(1,4,"Day 4"),
            NativeCustomNextCycleSlotDomain.select(cycle(),first))
        assertEquals(NativeCustomNextCycleSlot.Next(2,0,"Day 0"),
            NativeCustomNextCycleSlotDomain.select(cycle(),
                first+fact(1,4)))
        val all=(1..2).flatMap{w->listOf(0,2,4).map{d->fact(w,d)}}
        assertEquals(NativeCustomNextCycleSlot.CycleComplete,
            NativeCustomNextCycleSlotDomain.select(cycle(),all))
    }
    @Test fun issuedIncompleteOutranksPreparingAnotherWorkout() {
        val facts=listOf(fact(1,0),
            fact(1,2,issued=true,completed=false))
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued(
            "w1d2",1,2,"Day 2"),
            NativeCustomNextCycleSlotDomain.select(cycle(),facts))
        val prepared=listOf(fact(1,0),
            fact(1,2,issued=false,completed=false))
        assertEquals(NativeCustomNextCycleSlot.Prepared(
            "w1d2",1,2,"Day 2"),
            NativeCustomNextCycleSlotDomain.select(cycle(),prepared))
    }
    @Test fun outstandingIssuedTrainingAlwaysResumesBeforeASeparateUnstartedDay() {
        val laterIssued=fact(2,4,issued=true,completed=false)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued(
            laterIssued.workoutId,2,4,"Day 4"),
            NativeCustomNextCycleSlotDomain.select(cycle(),listOf(laterIssued)))
        val laterPrepared=fact(2,2,issued=false,completed=false)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued(
            laterIssued.workoutId,2,4,"Day 4"),
            NativeCustomNextCycleSlotDomain.select(
                cycle(),listOf(laterPrepared,laterIssued)))
    }
    @Test fun noLongerScheduledDaysMustNotSilentlyDisappear() {
        val ghost=fact(1,1)
        assertTrue(NativeCustomNextCycleSlotDomain.select(
            cycle(),listOf(ghost)) is NativeCustomNextCycleSlot.Rejected)
    }

    @Test fun repeatedOrStaleCycleRecordsRequireManualSelection() {
        assertTrue(NativeCustomNextCycleSlotDomain.select(
            cycle(),listOf(fact(1,0,rev=2)))
            is NativeCustomNextCycleSlot.Rejected)
        assertTrue(NativeCustomNextCycleSlotDomain.select(
            cycle(),listOf(fact(1,0),fact(1,0)))
            is NativeCustomNextCycleSlot.Rejected)
        assertTrue(NativeCustomNextCycleSlotDomain.select(
            cycle(),listOf(fact(1,0,issued=false,completed=true)))
            is NativeCustomNextCycleSlot.Rejected)
    }
}
