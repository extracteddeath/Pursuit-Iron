package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Real issued set targets remain factual; only next-coach RANGE is restored. */
class KotlinLongitudinalRepEvidenceDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("25"),n("30"),n("35")))
    private val band=RepBand(10,15)
    private fun historical(
        issued:List<RepBand> = listOf(
            RepBand(14,14),RepBand(14,14),RepBand(12,12)),
        loads:List<String> = List(3){"30"},
        effort:Int?=2,
        actualRir:List<Int?> = listOf(3,3,3)
    ):KotlinExerciseHistoryEvidence {
        val sets=issued.mapIndexed {i,reps->
            KotlinHistoricalSetFact(
                "db-curl","db-curl:d1:s$i",DraftSetType.WORKING,i,
                DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,n(loads[i])),
                reps,effort,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(loads[i]),LoadUnit.LB,
                    listOf(14,14,12)[i],actualRir[i]),
                "event-$i",null)
        }
        return KotlinExerciseHistoryEvidence("db-curl",sets,
            KotlinHistoryProgressionGate.MIXED_REP_BANDS,null,null)
    }
    private fun result(
        data:KotlinExerciseHistoryEvidence=historical(),
        expected:RepBand=band,
        physical:VerifiedLoadStock?=stock,
        effort:Int?=2,
        workoutId:String="last-finished"
    )=KotlinLongitudinalRepEvidenceDomain.review(
        data,workoutId,expected,3,n("30"),physical,LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,effort)

    @Test fun exactHistoricalPersonalizedSetsNormalizeOnlyPracticalEnvelope() {
        val history=historical()
        val before=history.workingSets.map {it.issuedReps}
        val review=result(history) as LongitudinalRepEvidenceResult.Comparable
        assertTrue(review.normalizedPersonalizedTargets)
        assertEquals(before,review.exactIssuedReps)
        assertEquals(band,review.session.targetReps)
        assertEquals("last-finished",review.session.workoutId)
        assertEquals(listOf(14,14,12),
            review.session.observations.map {it.actualReps})
        assertEquals(listOf(3,3,3),
            review.session.observations.map {it.actualRir})
        assertEquals(List(3){n("30")},
            review.session.observations.map {it.issuedLoad})
        assertFalse(review.mayEditHistory)
        assertFalse(review.mayIssueWorkout)
        assertEquals(before,history.workingSets.map {it.issuedReps})
    }

    @Test fun equalNarrowTargetsAreAlsoNormalizedAcrossNextWeek() {
        val equal=historical(issued=List(3){RepBand(12,12)})
        val normalized=result(equal) as LongitudinalRepEvidenceResult.Comparable
        assertTrue(normalized.normalizedPersonalizedTargets)
        assertEquals(band,normalized.session.targetReps)
        val broad=historical(issued=List(3){band})
        val ordinary=result(broad) as LongitudinalRepEvidenceResult.Comparable
        assertFalse(ordinary.normalizedPersonalizedTargets)
        assertEquals(band,ordinary.session.targetReps)
    }

    @Test fun noAuthorMixedTopBackoffOrOutOfRangeTargetsMasqueradeAsPersonalized() {
        val attempts=listOf(
            historical(issued=listOf(band,RepBand(14,14),RepBand(12,12))),
            historical(issued=listOf(RepBand(14,14),RepBand(16,16),RepBand(12,12))),
            historical(issued=listOf(RepBand(8,12),RepBand(14,14),RepBand(12,12))),
            historical(issued=listOf(RepBand(14,15),RepBand(14,14),RepBand(12,12)))
        )
        for(history in attempts) {
            assertEquals(LongitudinalRepEvidenceResult.Rejected(
                LongitudinalRepEvidenceRefusal.UNRELATED_MIXED_REP_SCHEME),
                result(history))
        }
    }

    @Test fun changedLoadsEquipmentAndTargetRirCannotBorrowPreviousProgression() {
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.CHANGED_EQUIPMENT_OR_ISSUED_LOAD),
            result(historical(loads=listOf("30","35","30"))))
        val contradictory=historical()
        val changed=contradictory.workingSets.mapIndexed {i,set->
            if(i==0)set.copy(actual=set.actual!!.copy(unit=LoadUnit.KG))
            else set
        }
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.CHANGED_EQUIPMENT_OR_ISSUED_LOAD),
            result(contradictory.copy(workingSets=changed)))
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.CHANGED_TARGET_EFFORT),
            result(historical(effort=3)))
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.UNVERIFIED_PHYSICAL_STOCK),
            result(physical=null))
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.UNVERIFIED_PHYSICAL_STOCK),
            result(physical=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,listOf(n("25"),n("35")))))
    }

    @Test fun wrongOrderMissingSetOrEmptyCompletedWorkoutRefusesAnyCoachProof() {
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.NO_SETS_OR_INVALID_ORDER),
            result(workoutId=""))
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.NO_SETS_OR_INVALID_ORDER),
            result(historical().copy(workingSets=historical().workingSets.take(2))))
        val duplicated=historical().workingSets.toMutableList()
        duplicated[1]=duplicated[1].copy(originalOrdinal=0)
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.NO_SETS_OR_INVALID_ORDER),
            result(historical().copy(workingSets=duplicated)))
    }

    @Test fun nativeBackoffNormalizationPreservesEachVerifiedIssuedLoad() {
        val record=historical(loads=listOf("30","30","25"))
        val result=KotlinLongitudinalRepEvidenceDomain.review(
            record,"backoff-completed",band,3,n("30"),stock,LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,2,
            listOf(n("30"),n("30"),n("25")))
        assertTrue(result.toString(),
            result is LongitudinalRepEvidenceResult.Comparable)
        val saved=result as LongitudinalRepEvidenceResult.Comparable
        assertEquals(listOf(n("30"),n("30"),n("25")),
            saved.session.observations.map{it.issuedLoad})
        assertEquals(listOf(n("30"),n("30"),n("25")),
            saved.session.observations.map{it.actualLoad})
        assertTrue(saved.normalizedPersonalizedTargets)
        assertEquals(record.workingSets.map{it.issuedReps},
            saved.exactIssuedReps)
        // A journal's actual issue is not a license to change new targets.
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.CHANGED_EQUIPMENT_OR_ISSUED_LOAD),
            KotlinLongitudinalRepEvidenceDomain.review(
                historical(loads=listOf("30","30","30")),
                "backoff-completed",band,3,n("30"),stock,LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,2,
                listOf(n("30"),n("30"),n("25"))))
        assertEquals(LongitudinalRepEvidenceResult.Rejected(
            LongitudinalRepEvidenceRefusal.UNVERIFIED_PHYSICAL_STOCK),
            KotlinLongitudinalRepEvidenceDomain.review(
                record,"backoff-completed",band,3,n("30"),stock,LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,2,
                listOf(n("30"),n("30"),n("27.5"))))
    }

}
