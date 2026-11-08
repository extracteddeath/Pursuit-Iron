package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class SessionFatigueEvidenceDomainTest {
    private val reps=RepBand(5,8)
    private fun session(
        performed: List<Int>,
        loads: List<Int> = List(performed.size){200},
        rir: List<Int?> = List(performed.size){null},
        prescribedRir: Int?=2
    ): List<WorkingSetFact> {
        require(performed.size==loads.size && performed.size==rir.size)
        return performed.indices.map {i ->
            WorkingSetFact(i,SetOutcome.COMPLETED,
                BigDecimal(loads[i]),performed[i],rir[i],BigDecimal(200),prescribedRir)
        }
    }
    private fun inspect(sets:List<WorkingSetFact>,count:Int=sets.size)=
        SessionFatigueEvidenceDomain.inspect(reps,count,sets)

    private val verified=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(BigDecimal(190),BigDecimal(195),BigDecimal(200)))
    private fun recorded(
        id:String,values:List<Int>,rir:List<Int?> = List(3){2},
        loads:List<Int> = List(3){200},day:String="upper-a"
    ):ProgressionWorkoutEvidence=ProgressionWorkoutEvidence(
        id,"bench",LoadUnit.LB,reps,3,values.indices.map{i->
            WorkingSetFact(i,SetOutcome.COMPLETED,BigDecimal(loads[i]),
                values[i],rir[i],BigDecimal(loads[i]),2)
        },trainingDayId=day)

    @Test fun sameDayTrendDistinguishesLaterSetAndBroadDeclines() {
        val a=recorded("a",listOf(8,8,8))
        val b=recorded("b",listOf(8,7,7))
        val c=recorded("c",listOf(8,6,6))
        val late=checkNotNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c),verified))
        assertEquals(RepeatedSessionFatigueSignal.LATER_SET_DECLINE,late.signal)
        assertEquals(setOf(1,2),late.decliningOrdinals)
        assertEquals(listOf(8,6,6),late.effortAlignedRepsBySession.last())
        assertFalse(late.mayChangeWorkout)
        val broad=checkNotNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,recorded("b",listOf(7,7,7)),
                recorded("c",listOf(6,6,6))),verified))
        assertEquals(RepeatedSessionFatigueSignal.BROAD_DECLINE,broad.signal)
        assertEquals(setOf(0,1,2),broad.decliningOrdinals)
        val oneBad=checkNotNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,recorded("b",listOf(8,8,8)),c),verified))
        assertEquals(RepeatedSessionFatigueSignal.NO_REPEATED_DECLINE,oneBad.signal)
        // A first set that fluctuates is not proof it stayed steady.
        val unstableFirst=checkNotNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,recorded("b",listOf(7,7,7)),c),verified))
        assertEquals(RepeatedSessionFatigueSignal.NO_REPEATED_DECLINE,
            unstableFirst.signal)
    }

    @Test fun fatigueTrendCannotUseMissingRirOtherDaysOrChangedStock() {
        val a=recorded("a",listOf(8,8,8))
        val b=recorded("b",listOf(8,7,7))
        val c=recorded("c",listOf(8,6,6))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c),null))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c.copy(trainingDayId="push-day")),verified))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c.copy(trainingDayId=null)),verified))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c.copy(observations=c.observations.mapIndexed{i,set->
                if(i==1)set.copy(actualRir=null) else set})),verified))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(
            listOf(a,b,c.copy(observations=c.observations.mapIndexed{i,set->
                if(i==2)set.copy(actualLoad=BigDecimal(195),
                    issuedLoad=BigDecimal(195)) else set})),verified))
        val mixed=listOf(
            recorded("a",listOf(8,8,8),loads=listOf(200,195,190)),
            recorded("b",listOf(8,7,7),loads=listOf(200,195,190)),
            recorded("c",listOf(8,6,6),loads=listOf(200,195,190)))
        assertNull(SessionFatigueEvidenceDomain.inspectRecent(mixed,verified))
        assertEquals(RepeatedSessionFatigueSignal.LATER_SET_DECLINE,
            SessionFatigueEvidenceDomain.inspectRecent(mixed,verified,
                verifiedPerSetLoading=true)?.signal)
    }

    @Test fun sameLoadDeclineRetainsEachRealSetWithoutInferringTargetRir() {
        val profile=inspect(session(listOf(8,7,6,5),rir=listOf(3,2,1,0)))
        assertEquals(FatiguePattern.REPS_DECLINING,profile.pattern)
        assertEquals(listOf(-1,-1,-1),profile.repChangesBetweenSets)
        assertEquals(-3,profile.totalRepChange)
        assertEquals(8,profile.firstSetReps)
        assertEquals(5,profile.lastSetReps)
        assertEquals(-3,profile.reportedRirChange)
        assertTrue(profile.targetFloorMisses.isEmpty())
        assertEquals(setOf(0,1,2,3),profile.actualRirReportedSets)
    }

    @Test fun unreportedRirIsUnknownEvenWhenPrescriptionHasEffortTarget() {
        val profile=inspect(session(listOf(8,7,6,5)))
        assertEquals(FatiguePattern.REPS_DECLINING,profile.pattern)
        assertNull(profile.reportedRirChange)
        assertTrue(profile.actualRirReportedSets.isEmpty())
    }

    @Test fun stableImprovingAndMixedPatternsAreDistinct() {
        assertEquals(FatiguePattern.REPS_STABLE,
            inspect(session(listOf(8,8,8,8))).pattern)
        assertEquals(listOf(0,0,0),
            inspect(session(listOf(8,8,8,8))).repChangesBetweenSets)
        assertEquals(FatiguePattern.REPS_INCREASING,
            inspect(session(listOf(5,6,7,8))).pattern)
        assertEquals(FatiguePattern.NON_MONOTONIC_REPS,
            inspect(session(listOf(8,6,7,5))).pattern)
        assertEquals(-3,inspect(session(listOf(8,6,7,5))).totalRepChange)
    }

    @Test fun mixedWeightsMustNotBeMisinterpretedAsFatigue() {
        val profile=inspect(session(listOf(8,7,6,5),loads=listOf(200,205,210,215)))
        assertEquals(FatiguePattern.MIXED_LOADS,profile.pattern)
        assertTrue(profile.repChangesBetweenSets.isEmpty())
        assertNull(profile.totalRepChange)
        assertNull(profile.reportedRirChange)
    }

    @Test fun missingSkippedAndFailedSetsDoNotCreateFakeRepTrends() {
        val base=session(listOf(8,7,6,5))
        val withoutLast=inspect(base.take(3),count=4)
        assertEquals(FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE,withoutLast.pattern)
        assertNull(withoutLast.totalRepChange)
        val skip=base.toMutableList()
        skip[1]=WorkingSetFact(1,SetOutcome.SKIPPED,null,null,null)
        assertEquals(FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE,inspect(skip).pattern)
        skip[1]=WorkingSetFact(1,SetOutcome.FAILED_ATTEMPT,BigDecimal(200),0,null)
        assertEquals(FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE,inspect(skip).pattern)
        assertEquals(FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE,
            inspect(session(listOf(8)),count=1).pattern)
    }

    @Test fun actualRepFloorMissesAreStillVisibleWithoutARecommendation() {
        val profile=inspect(session(listOf(8,6,4,3)))
        assertEquals(FatiguePattern.REPS_DECLINING,profile.pattern)
        assertEquals(setOf(2,3),profile.targetFloorMisses)
        assertEquals(-5,profile.totalRepChange)
    }

    @Test fun invalidSetObservationsStillFailClosed() {
        val set=session(listOf(8,7))
        assertThrows(IllegalArgumentException::class.java) {
            inspect(listOf(set[0],set[0]),count=2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            inspect(listOf(set[0].copy(actualRir=11),set[1]),count=2)
        }
    }
}
