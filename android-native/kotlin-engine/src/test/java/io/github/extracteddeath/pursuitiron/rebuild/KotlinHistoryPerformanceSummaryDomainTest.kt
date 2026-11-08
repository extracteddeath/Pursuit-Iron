package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinHistoryPerformanceSummaryDomainTest {
    private val load = DraftSetLoad.Verified(
        LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND, BigDecimal("30"))
    private fun actual(reps: Int, rir: Int? = null) =
        KotlinPerformedSet(BigDecimal("30"), LoadUnit.LB, reps, rir)

    private fun composer(
        key: String, type: DraftSetType, goal: RepBand,
        outcome: KotlinJournalSetOutcome, result: KotlinPerformedSet?
    ) = KotlinHistoricalSetFact(
        "incline-press", key, type, 0, load, goal, 2,
        outcome, result, "receipt-$key", null
    )

    private fun custom(
        key: String, part: CustomWorkoutVisitPart, type: DraftSetType,
        goal: RepBand, outcome: KotlinJournalSetOutcome,
        result: KotlinPerformedSet?
    ) = CustomHistoricalVisitFact(
        "upper-slot", "incline-press", key, part, type,
        load, goal, if(part == CustomWorkoutVisitPart.MAIN) 2 else null,
        null, outcome, result, "receipt-$key"
    )

    @Test fun composerReportsActualRangeAndRirWithoutCountingWarmups() {
        val facts = listOf(
            composer("warm", DraftSetType.WARMUP, RepBand(5, 5),
                KotlinJournalSetOutcome.COMPLETED, actual(5)),
            composer("a", DraftSetType.WORKING, RepBand(8, 12),
                KotlinJournalSetOutcome.COMPLETED, actual(12, 2)),
            composer("b", DraftSetType.WORKING, RepBand(8, 12),
                KotlinJournalSetOutcome.COMPLETED, actual(7)),
            composer("c", DraftSetType.WORKING, RepBand(8, 12),
                KotlinJournalSetOutcome.SKIPPED, null),
            composer("d", DraftSetType.WORKING, RepBand(8, 12),
                KotlinJournalSetOutcome.FAILED_ATTEMPT, actual(0))
        )
        val result = KotlinHistoryPerformanceSummaryDomain.fromComposer(facts)
        assertEquals(4, result.workingSets)
        assertEquals(2, result.completedWorkingSets)
        assertEquals(1, result.skippedWorkingSets)
        assertEquals(1, result.failedWorkingSets)
        assertEquals(1, result.repsWithinRange)
        assertEquals(1, result.repsBelowMinimum)
        assertEquals(0, result.repsAboveMaximum)
        assertEquals(1, result.actualRirReportedWorkingSets)
        assertEquals(0, result.followUpVisits)
        assertEquals(0, result.completedFollowUpVisits)
    }

    @Test fun correctedActualsDriveTheDisplayWithoutChangingIssuedTargets() {
        val original = composer("set", DraftSetType.WORKING, RepBand(8, 12),
            KotlinJournalSetOutcome.COMPLETED, actual(9, null))
        val corrected = original.copy(actual = actual(13, 1), effectiveEventId = "corrected")
        val initial = KotlinHistoryPerformanceSummaryDomain.fromComposer(listOf(original))
        val changed = KotlinHistoryPerformanceSummaryDomain.fromComposer(listOf(corrected))
        assertEquals(1, initial.repsWithinRange)
        assertEquals(0, initial.actualRirReportedWorkingSets)
        assertEquals(0, changed.repsWithinRange)
        assertEquals(1, changed.repsAboveMaximum)
        assertEquals(1, changed.actualRirReportedWorkingSets)
        assertEquals(RepBand(8,12), corrected.issuedReps)
        assertEquals(9, original.actual?.reps)
    }

    @Test fun customAmplifiersDoNotInflateWorkingSetCounts() {
        val visits = listOf(
            custom("warm", CustomWorkoutVisitPart.MAIN, DraftSetType.WARMUP,
                RepBand(5,5), KotlinJournalSetOutcome.COMPLETED, actual(5)),
            custom("first", CustomWorkoutVisitPart.MAIN, DraftSetType.WORKING,
                RepBand(8,12), KotlinJournalSetOutcome.COMPLETED, actual(12)),
            custom("first", CustomWorkoutVisitPart.FOLLOW_UP, DraftSetType.WORKING,
                RepBand(3,3), KotlinJournalSetOutcome.COMPLETED, actual(3)),
            custom("second", CustomWorkoutVisitPart.MAIN, DraftSetType.WORKING,
                RepBand(8,12), KotlinJournalSetOutcome.COMPLETED, actual(13, 0)),
            custom("second", CustomWorkoutVisitPart.FOLLOW_UP, DraftSetType.WORKING,
                RepBand(3,3), KotlinJournalSetOutcome.SKIPPED, null)
        )
        val result = KotlinHistoryPerformanceSummaryDomain.fromCustom(visits)
        assertEquals(2, result.workingSets)
        assertEquals(2, result.completedWorkingSets)
        assertEquals(1, result.repsWithinRange)
        assertEquals(1, result.repsAboveMaximum)
        assertEquals(1, result.actualRirReportedWorkingSets)
        assertEquals(2, result.followUpVisits)
        assertEquals(1, result.completedFollowUpVisits)
    }

    @Test fun unknownEffortAndEmptyHistoryDoNotCreatePerformanceClaims() {
        val empty = KotlinHistoryPerformanceSummaryDomain.fromComposer(emptyList())
        assertEquals(0, empty.workingSets)
        assertEquals(0, empty.actualRirReportedWorkingSets)
        val missing = KotlinHistoryPerformanceSummaryDomain.fromCustom(listOf(
            custom("a", CustomWorkoutVisitPart.MAIN, DraftSetType.WORKING,
                RepBand(8,12), KotlinJournalSetOutcome.COMPLETED, actual(10))
        ))
        assertEquals(1, missing.repsWithinRange)
        assertEquals(0, missing.actualRirReportedWorkingSets)
    }
}
