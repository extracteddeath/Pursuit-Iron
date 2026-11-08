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

    @Test fun finishedEarlierRevisionKeepsWeekOneCompletedAfterReapprovingProgram() {
        val amended=cycle().copy(revision=4)
        val firstWeek=listOf(fact(1,0,rev=3),fact(1,2,rev=3),
            fact(1,4,rev=3))
        assertEquals(NativeCustomNextCycleSlot.Next(2,0,"Day 0"),
            NativeCustomNextCycleSlotDomain.select(amended,firstWeek))
        assertEquals(NativeCustomNextCycleSlot.Next(2,2,"Day 2"),
            NativeCustomNextCycleSlotDomain.select(amended,
                firstWeek+fact(2,0,rev=4)))
        val all=(1..2).flatMap{week->listOf(0,2,4).map{day->
            fact(week,day,rev=if(week==1)3 else 4)
        }}
        assertEquals(NativeCustomNextCycleSlot.CycleComplete,
            NativeCustomNextCycleSlotDomain.select(amended,all))
    }

    @Test fun unfinishedOldRevisionRequiresExplicitResumeBeforeAnotherDay() {
        val revised=cycle().copy(revision=4)
        for(stale in listOf(
            fact(1,2,rev=3,issued=true,completed=false),
            fact(1,2,rev=3,issued=false,completed=false))) {
            val decision=NativeCustomNextCycleSlotDomain.select(revised,
                listOf(fact(1,0,rev=3),stale))
            assertTrue(decision.toString(),
                decision is NativeCustomNextCycleSlot.Rejected)
            assertTrue((decision as NativeCustomNextCycleSlot.Rejected)
                .reason.contains("unfinished workout"))
        }
        // Current-revision incomplete work still outranks the next day.
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued("w1d2",1,2,"Day 2"),
            NativeCustomNextCycleSlotDomain.select(revised,listOf(
                fact(1,0,rev=3),
                fact(1,2,rev=4,issued=true,completed=false))))
    }

    @Test fun earlierCompletedSessionCannotReoccupyDeletedDayOrInventRevision() {
        val revised=cycle().copy(revision=4)
        assertTrue(NativeCustomNextCycleSlotDomain.select(revised,
            listOf(fact(1,1,rev=3))) is NativeCustomNextCycleSlot.Rejected)
        for(invalid in listOf(0L,5L)) {
            assertTrue(NativeCustomNextCycleSlotDomain.select(revised,
                listOf(fact(1,0,rev=invalid))) is NativeCustomNextCycleSlot.Rejected)
        }
        assertTrue(NativeCustomNextCycleSlotDomain.select(revised,
            listOf(fact(1,0,rev=3),fact(1,0,rev=4)))
            is NativeCustomNextCycleSlot.Rejected)
        assertTrue(NativeCustomNextCycleSlotDomain.select(revised,
            listOf(fact(1,0,issued=false,completed=true,rev=3)))
            is NativeCustomNextCycleSlot.Rejected)
    }
}
