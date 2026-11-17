package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.DraftSetType
import io.github.extracteddeath.pursuitiron.rebuild.KotlinJournalSetOutcome
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinWorkoutRecapDomainTest {
    private val complete=KotlinJournalSetOutcome.COMPLETED
    private val skip=KotlinJournalSetOutcome.SKIPPED
    private val failed=KotlinJournalSetOutcome.FAILED_ATTEMPT
    private fun set(id:String,type:DraftSetType=DraftSetType.WORKING,
        outcome:KotlinJournalSetOutcome=complete,followUp:Boolean=false)=
        NativeKotlinRecapVisit(id,type,followUp,outcome)

    @Test fun genuineWorkingSetsDoNotBorrowWarmupsOrAmplifierCounts() {
        val summary=checkNotNull(NativeKotlinWorkoutRecapDomain.summarize(listOf(
            set("bench"),set("bench"),set("bench",outcome=failed),
            set("bench",type=DraftSetType.WARMUP),
            set("bench",type=DraftSetType.WORKING,followUp=true),
            set("row",outcome=skip)
        )))
        assertEquals(2,summary.completedMain)
        assertEquals(1,summary.skippedMain)
        assertEquals(1,summary.failedMain)
        assertEquals(1,summary.completedWarmups)
        assertEquals(1,summary.completedAmplifiers)
        assertEquals(6,summary.totalRecordedVisits)
        assertEquals(listOf("bench","row"),summary.exercises.map{it.exerciseId})
        assertEquals(2,summary.exercises.first().completedMain)
    }

    @Test fun failedAndSkippedWarmupsAndFollowUpsAreNotSilentlyDiscarded() {
        val recap=checkNotNull(NativeKotlinWorkoutRecapDomain.summarize(listOf(
            set("squat",DraftSetType.WARMUP,skip),
            set("squat",DraftSetType.WORKING,failed,true),
            set("squat",DraftSetType.WORKING,complete)
        )))
        assertEquals(1,recap.completedMain)
        assertEquals(1,recap.skippedOther)
        assertEquals(1,recap.failedOther)
        assertEquals(0,recap.failedMain)
        assertEquals(3,recap.totalRecordedVisits)
    }

    @Test fun allSkippedWorkoutDoesNotInventCompletedSetsOrPrs() {
        val recap=checkNotNull(NativeKotlinWorkoutRecapDomain.summarize(
            listOf(set("bench",outcome=skip),set("squat",outcome=skip))))
        assertEquals(0,recap.completedMain)
        assertEquals(2,recap.skippedMain)
        assertEquals(0,recap.completedWarmups)
    }

    @Test fun outOfBoundsOrInvalidEvidenceFailsClosed() {
        assertNull(NativeKotlinWorkoutRecapDomain.summarize(emptyList()))
        assertNull(NativeKotlinWorkoutRecapDomain.summarize(
            listOf(set("bad exercise"))))
        assertNull(NativeKotlinWorkoutRecapDomain.summarize(
            List(4097){set("bench")}))
    }
    @Test fun canonicalHistoryPerformanceIsAttachedOnlyWhenCountsReconcile() {
        val counts=checkNotNull(NativeKotlinWorkoutRecapDomain.summarize(listOf(
            set("bench"),set("bench",outcome=failed),
            set("bench",DraftSetType.WARMUP),
            set("bench",DraftSetType.WORKING,complete,true)
        )))
        val canonical=io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryPerformanceSummary(
            workingSets=2,completedWorkingSets=1,skippedWorkingSets=0,
            failedWorkingSets=1,repsWithinRange=0,repsBelowMinimum=1,
            repsAboveMaximum=0,actualRirReportedWorkingSets=0,
            followUpVisits=1,completedFollowUpVisits=1)
        val attached=checkNotNull(NativeKotlinWorkoutRecapDomain.attachVerifiedPerformance(
            counts,canonical))
        assertSame(canonical,attached.performance)
        assertEquals(1,attached.completedWarmups)
        assertEquals(1,attached.completedAmplifiers)
        assertEquals(0,attached.performance?.actualRirReportedWorkingSets)
    }

    @Test fun contradictoryHistoryMetricsRefusePublishingARecap() {
        val counts=checkNotNull(NativeKotlinWorkoutRecapDomain.summarize(listOf(
            set("bench"),set("bench",outcome=skip)
        )))
        val incorrect=io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryPerformanceSummary(
            workingSets=2,completedWorkingSets=2,skippedWorkingSets=0,
            failedWorkingSets=0,repsWithinRange=1,repsBelowMinimum=0,
            repsAboveMaximum=0,actualRirReportedWorkingSets=1,
            followUpVisits=0,completedFollowUpVisits=0)
        assertNull(NativeKotlinWorkoutRecapDomain.attachVerifiedPerformance(
            counts,incorrect))
        val correct=incorrect.copy(completedWorkingSets=1,skippedWorkingSets=1,
            repsWithinRange=0,repsBelowMinimum=0,repsAboveMaximum=0,
            actualRirReportedWorkingSets=0)
        assertNotNull(NativeKotlinWorkoutRecapDomain.attachVerifiedPerformance(
            counts,correct))
        assertNull(NativeKotlinWorkoutRecapDomain.attachVerifiedPerformance(
            counts,correct.copy(completedFollowUpVisits=1,followUpVisits=1)))
    }

}
