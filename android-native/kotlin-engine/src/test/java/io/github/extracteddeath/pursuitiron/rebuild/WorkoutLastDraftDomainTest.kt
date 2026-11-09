package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WorkoutLastDraftDomainTest {
    private val key=WorkoutReferenceKey("current-working-1")
    private val other=WorkoutReferenceKey("current-working-2")
    private val barbell=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,
        BigDecimal("200"))
    private fun history(
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        performed:KotlinPerformedSet?=KotlinPerformedSet(
            BigDecimal("195"),LoadUnit.LB,5,null),
        basis:LoadBasis?=LoadBasis.BARBELL_TOTAL
    )=WorkoutLastReferenceResult.Ready(mapOf(key to WorkoutLastSetReference(
        "past-session",6,outcome,performed,basis)))

    @Test fun verifiedCompletedSetCopiesActualsWithoutInventingRir() {
        val result=WorkoutLastDraftDomain.reviewedActual(history(),key,barbell)
        assertEquals(BigDecimal("195"),result?.load)
        assertEquals(5,result?.reps)
        assertNull(result?.actualRir)
        assertEquals(BigDecimal("200"),barbell.value)
    }

    @Test fun lastSkippedOrFailedCannotBecomeCompletedDraft() {
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(KotlinJournalSetOutcome.SKIPPED,null),key,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(KotlinJournalSetOutcome.FAILED_ATTEMPT,
                KotlinPerformedSet(BigDecimal("195"),LoadUnit.LB,0,null)),key,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            WorkoutLastReferenceResult.Unavailable,key,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(null,key,barbell))
    }

    @Test fun exactKeyAndLoadBasisAreRequired() {
        assertNull(WorkoutLastDraftDomain.reviewedActual(history(),other,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(history(),key,
            barbell.copy(basis=LoadBasis.SMITH_TOTAL)))
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(basis=LoadBasis.DUMBBELL_PER_HAND),key,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(performed=KotlinPerformedSet(
                BigDecimal("195"),LoadUnit.KG,5,2)),key,barbell))
    }

    @Test fun bodyweightNeverCopiesExternalLoadOrInferredRir() {
        val bodyweight=DraftSetLoad.ExplicitBodyweight
        assertNull(WorkoutLastDraftDomain.reviewedActual(history(),key,bodyweight))
        val actual=KotlinPerformedSet(null,null,9,null)
        val valid=WorkoutLastDraftDomain.reviewedActual(
            history(performed=actual,basis=null),key,bodyweight)
        assertEquals(9,valid?.reps)
        assertNull(valid?.load)
        assertNull(valid?.actualRir)
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(performed=actual),key,bodyweight))
    }

    @Test fun invalidValuesAndKnownRirAreHandledExactly() {
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(performed=KotlinPerformedSet(BigDecimal("195"),LoadUnit.LB,0,2)),
            key,barbell))
        assertNull(WorkoutLastDraftDomain.reviewedActual(
            history(performed=KotlinPerformedSet(BigDecimal("195"),LoadUnit.LB,5,11)),
            key,barbell))
        val actual=WorkoutLastDraftDomain.reviewedActual(
            history(performed=KotlinPerformedSet(BigDecimal("185"),LoadUnit.LB,6,3)),
            key,barbell)
        assertEquals(3,actual?.actualRir)
        assertEquals(6,actual?.reps)
    }

    @Test fun independentFollowUpReferencesCannotBeBorrowedForMainSets() {
        val follow=WorkoutReferenceKey(key.setKey,true)
        assertNull(WorkoutLastDraftDomain.reviewedActual(history(),follow,barbell))
        val candidate=WorkoutLastReferenceResult.Ready(mapOf(follow to
            WorkoutLastSetReference("w",2,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("100"),LoadUnit.LB,4,null),
                LoadBasis.BARBELL_TOTAL)))
        assertNull(WorkoutLastDraftDomain.reviewedActual(candidate,key,barbell))
        assertEquals(4,WorkoutLastDraftDomain.reviewedActual(
            candidate,follow,barbell)?.reps)
    }
}
