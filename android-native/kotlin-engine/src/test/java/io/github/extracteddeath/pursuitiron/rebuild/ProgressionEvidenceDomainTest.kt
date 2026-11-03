package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class DoubleProgressionEvidenceDomainTest {
    private val band=RepBand(5,8)
    private fun n(value:Int)=BigDecimal(value)
    private fun set(index:Int,load:Int,reps:Int,rir:Int?=null,
        issuedLoad:Int?=null,issuedRir:Int?=2)=
        WorkingSetFact(index,SetOutcome.COMPLETED,n(load),reps,rir,
            issuedLoad?.let(::n),issuedRir)
    private fun assess(vararg facts:WorkingSetFact,count:Int=facts.size)=
        DoubleProgressionEvidenceDomain.assess(band,count,facts.toList())

    @Test fun reportedBrokenFourSetDoubleProgressionMustNotCreditHeaviestLoad() {
        // User-reported defect: 200x3, 205x3, 210x2, 215x2 with 5–8 target.
        val evidence=assess(set(0,200,3,2),set(1,205,3,1),
            set(2,210,2,0),set(3,215,2,0))
        assertEquals(ProgressionEvidenceSignal.BELOW_REP_FLOOR,evidence.signal)
        assertEquals(setOf(0,1,2,3),evidence.belowFloorOrdinals)
        assertTrue(evidence.mixedActualLoads)
        assertEquals(setOf(1,2,3),evidence.nearFailureOrdinals)
        assertEquals(4,evidence.completedWorkingSets)
        assertTrue(evidence.ceilingOrdinals.isEmpty())
    }

    @Test fun repeatingLastHeaviestSetNearFailureIsNotAnIncreaseSignal() {
        val evidence=assess(set(0,215,2,0),set(1,215,2,0),
            set(2,215,2,1),set(3,215,2,0))
        assertEquals(ProgressionEvidenceSignal.BELOW_REP_FLOOR,evidence.signal)
        assertFalse(evidence.mixedActualLoads)
        assertEquals(4,evidence.nearFailureOrdinals.size)
    }

    @Test fun allCeilingRepsAtDifferentLoadsDoesNotEstablishUniformCapacity() {
        val evidence=assess(set(0,200,8,2),set(1,205,8,2),
            set(2,210,8,2),set(3,215,8,2))
        assertEquals(ProgressionEvidenceSignal.MIXED_LOAD_CEILING,evidence.signal)
        assertTrue(evidence.mixedActualLoads)
    }

    @Test fun uniformCeilingIsEvidenceForReviewNotAnAutomaticPrescription() {
        val evidence=assess(set(0,200,8,2),set(1,200,8,2),
            set(2,200,8,2),set(3,200,8,1))
        assertEquals(ProgressionEvidenceSignal.UNIFORM_LOAD_CEILING_FOR_REVIEW,evidence.signal)
        assertFalse(evidence.mixedActualLoads)
        assertEquals(setOf(3),evidence.nearFailureOrdinals)
    }

    @Test fun absentRirMustNeverBeInferredFromIssuedTarget() {
        val evidence=assess(set(0,200,8,null,185,3),set(1,200,8,null,185,3))
        assertTrue(evidence.actualRirReportedOrdinals.isEmpty())
        assertTrue(evidence.nearFailureOrdinals.isEmpty())
        assertEquals(ProgressionEvidenceSignal.UNIFORM_LOAD_CEILING_FOR_REVIEW,evidence.signal)
    }

    @Test fun skipsMissingAndZeroRepFailuresDoNotBecomeSuccessfulSets() {
        val skipped=WorkingSetFact(1,SetOutcome.SKIPPED,null,null,null)
        val failed=WorkingSetFact(2,SetOutcome.FAILED_ATTEMPT,n(200),0,null)
        val evidence=assess(set(0,200,8,1),skipped,failed,count=4)
        assertEquals(ProgressionEvidenceSignal.INCOMPLETE_OR_SKIPPED,evidence.signal)
        assertEquals(1,evidence.completedWorkingSets)
        assertEquals(setOf(1),evidence.skippedOrdinals)
        assertEquals(setOf(2),evidence.failedAttemptOrdinals)
        assertEquals(setOf(3),evidence.missingOrdinals)
    }

    @Test fun bodyweightZeroRecordedLoadIsARealFactNotAWeightRecommendation() {
        val evidence=assess(set(0,0,6,null),set(1,0,6,null))
        assertEquals(ProgressionEvidenceSignal.BUILDING_REPS,evidence.signal)
        assertTrue(evidence.actualRirReportedOrdinals.isEmpty())
    }

    @Test fun rejectsInvalidOrdinalDuplicationsAndInventedEffort() {
        fun invalid(vararg facts:WorkingSetFact,count:Int=facts.size) {
            assertThrows(IllegalArgumentException::class.java) {
                assess(*facts,count=count)
            }
        }
        invalid(set(0,200,8),set(0,200,8))
        invalid(set(2,200,8),count=2)
        invalid(WorkingSetFact(0,SetOutcome.COMPLETED,n(200),8,12))
        invalid(WorkingSetFact(0,SetOutcome.COMPLETED,n(-5),8,2))
        invalid(WorkingSetFact(0,SetOutcome.SKIPPED,n(200),null,null))
        invalid(WorkingSetFact(0,SetOutcome.FAILED_ATTEMPT,n(200),1,null))
        assertThrows(IllegalArgumentException::class.java) {RepBand(8,5)}
    }
}
