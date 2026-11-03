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
