package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ProgressionEffortDomainTest {
    private fun set(actual: Int?=null, goal: Int?=2, outcome: SetOutcome=SetOutcome.COMPLETED)=
        WorkingSetFact(0,outcome,BigDecimal("200"),8,actual,BigDecimal("200"),goal)

    @Test fun originalGoalMayGuideProgressionWhileActualEffortStaysNull() {
        val fact=set()
        assertEquals(ProgressionEffort(2,ProgressionEffortSource.ASSUMED_GOAL),
            ProgressionEffortDomain.resolve(fact))
        assertNull(fact.actualRir)
        assertEquals(2,fact.issuedRir)
        val factual=DoubleProgressionEvidenceDomain.assess(RepBand(5,8),1,listOf(fact))
        assertTrue(factual.actualRirReportedOrdinals.isEmpty())
        assertTrue(factual.nearFailureOrdinals.isEmpty())
    }

    @Test fun explicitZeroAndOtherActualReportsAlwaysOverrideTheGoal() {
        assertEquals(ProgressionEffort(0,ProgressionEffortSource.REPORTED),
            ProgressionEffortDomain.resolve(set(actual=0,goal=3)))
        assertEquals(ProgressionEffort(4,ProgressionEffortSource.REPORTED),
            ProgressionEffortDomain.resolve(set(actual=4,goal=0)))
    }

    @Test fun missingBothSourcesAndUnfinishedSetsNeverReceiveDefaultEffort() {
        assertEquals(ProgressionEffort(null,ProgressionEffortSource.UNAVAILABLE),
            ProgressionEffortDomain.resolve(set(goal=null)))
        for(outcome in listOf(SetOutcome.SKIPPED,SetOutcome.FAILED_ATTEMPT))
            assertEquals(ProgressionEffort(null,ProgressionEffortSource.UNAVAILABLE),
                ProgressionEffortDomain.resolve(set(goal=3,outcome=outcome)))
    }

    @Test fun assumedLowGoalIsNotFactualNearFailureAndInvalidEffortIsRejected() {
        val fact=set(goal=0)
        assertEquals(0,ProgressionEffortDomain.resolve(fact).rir)
        assertTrue(DoubleProgressionEvidenceDomain.assess(RepBand(5,8),1,
            listOf(fact)).nearFailureOrdinals.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            ProgressionEffortDomain.forCompletedSet(null,11)
        }
    }
}
