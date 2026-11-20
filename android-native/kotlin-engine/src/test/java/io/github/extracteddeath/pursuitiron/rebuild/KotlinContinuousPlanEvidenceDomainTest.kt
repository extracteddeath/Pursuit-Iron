package io.github.extracteddeath.pursuitiron.rebuild

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class KotlinContinuousPlanEvidenceDomainTest {
    private val digest="a".repeat(64)
    private val requestDigest="b".repeat(64)
    private val schedule=setOf(0,2,4)
    private val start=LocalDate.of(2026,11,2)
    private val expectedIds=(0..2).map{week->"visit-$week-0"}

    private fun week(
        weekIndex:Int,
        coverage:KotlinObservedWeekCoverage=
            KotlinObservedWeekCoverage.FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY
    ):KotlinObservedScheduleWeek {
        val monday=start.plusWeeks(weekIndex.toLong())
        val sessions=listOf(0,2,4).mapIndexed{index,day->
            KotlinObservedScheduledSession(
                "visit-$weekIndex-$day",index,day,
                monday.plusDays(day.toLong()),day,
                true,true)
        }
        return KotlinObservedScheduleWeek(
            monday,sessions,schedule,schedule,emptySet(),coverage)
    }

    private fun linked(
        ids:List<String> =expectedIds,
        status:KotlinFuturePerformanceCellStatus=
            KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
    ):KotlinFuturePerformanceLink=KotlinFuturePerformanceLink(
        requestDigest,digest,listOf(KotlinFuturePerformanceCell(
            0,0,"bench-slot","bench",null,status,
            KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,ids)))

    private fun weeks(
        all:List<KotlinObservedScheduleWeek> =
            (0..2).map(::week),
        undated:List<String> =emptyList(),
        reversedClock:Boolean=false
    )=KotlinObservedScheduleWeeks(digest,"America/New_York",
        schedule,all,undated,reversedClock)

    private fun review(
        link:KotlinFuturePerformanceLink=linked(),
        observation:KotlinObservedScheduleWeeks=weeks()
    ):KotlinContinuousPlanCellEvidence {
        val result=KotlinContinuousPlanEvidenceDomain.review(
            link,observation) as KotlinContinuousPlanEvidenceResult.Reviewed
        assertEquals(digest,result.summary.sourceReplayDigest)
        assertEquals(requestDigest,result.summary.generatedRequestDigest)
        assertFalse(result.summary.mayApplyAutomatically)
        assertFalse(result.summary.mayRecommendPhasePromotion)
        assertFalse(result.summary.mayAssumeProgramAdherence)
        return result.summary.cells.single()
    }

    @Test fun threeDistinctCompleteConsecutiveLocalWeeksOnlyInviteReview() {
        val candidate=review()
        assertEquals(
            KotlinContinuousPlanEvidenceState
                .EXPLORATORY_REVIEW_ONLY_ACTIVATION_REQUIRED,candidate.state)
        assertEquals(expectedIds,candidate.supportingWorkoutIds)
        assertEquals(listOf(start,start.plusWeeks(1),start.plusWeeks(2)),
            candidate.supportingWeekMondays)
        assertFalse(candidate.mayChangeWorkingLoad)
        assertFalse(candidate.mayChangeRepRange)
        assertFalse(candidate.mayChangeWorkingSets)
        assertFalse(candidate.mayChangeExercise)
    }

    @Test fun threeWorkoutsInOneWeekAreNotThreeWeeksOfProgress() {
        val one=week(0)
        val visits=listOf(0,1,2).map{index->
            KotlinObservedScheduledSession(
                "many-$index",0,0,start,0,true,true)
        }
        val overloaded=one.copy(sessions=one.sessions+visits,
            coverage=KotlinObservedWeekCoverage
                .PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY)
        val candidate=review(linked(listOf("many-0","many-1","many-2")),
            weeks(listOf(overloaded)))
        assertEquals(KotlinContinuousPlanEvidenceState
            .INSUFFICIENT_DISTINCT_DATED_WEEKS,candidate.state)
        assertFalse(candidate.mayChangeRepRange)
    }

    @Test fun missingShortenedUndatedAndClockConflictsBlockCrossWeekConfidence() {
        val partial=week(1,KotlinObservedWeekCoverage
            .PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY)
        assertEquals(KotlinContinuousPlanEvidenceState
            .OBSERVED_WEEKS_NOT_COMPLETE,
            review(observation=weeks(listOf(week(0),partial,week(2)))).state)
        assertEquals(KotlinContinuousPlanEvidenceState
            .OBSERVED_WEEKS_NOT_COMPLETE,
            review(observation=weeks(
                (0..2).map(::week),undated=listOf("older-undated"))).state)
        assertEquals(KotlinContinuousPlanEvidenceState
            .OBSERVED_WEEKS_NOT_COMPLETE,
            review(observation=weeks(
                (0..2).map(::week),reversedClock=true)).state)
    }

    @Test fun missingInterveningWeekAndNewerPartialWeekHoldOldEvidence() {
        val separated=weeks(listOf(week(0),week(2),week(4)))
        val ids=listOf("visit-0-0","visit-2-0","visit-4-0")
        assertEquals(KotlinContinuousPlanEvidenceState
            .NONCONSECUTIVE_OR_STALE_WEEKS,
            review(linked(ids),separated).state)
        val later=week(3,KotlinObservedWeekCoverage
            .PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY)
        assertEquals(KotlinContinuousPlanEvidenceState
            .NONCONSECUTIVE_OR_STALE_WEEKS,
            review(observation=weeks((0..2).map(::week)+later)).state)
    }

    @Test fun changedDayIncompleteHistoryOrUnfavorableGateCannotBePromoted() {
        assertEquals(KotlinContinuousPlanEvidenceState
            .NO_EXACT_REPEATED_PERFORMANCE,
            review(linked(status=KotlinFuturePerformanceCellStatus
                .HISTORY_GUARDED)).state)
        assertEquals(KotlinContinuousPlanEvidenceState
            .INSUFFICIENT_DISTINCT_DATED_WEEKS,
            review(linked(listOf("visit-0-0","visit-1-2","visit-2-0"))).state)
        assertEquals(KotlinContinuousPlanEvidenceState
            .INSUFFICIENT_DISTINCT_DATED_WEEKS,
            review(linked(listOf("visit-0-0","visit-1-0","unknown"))).state)
    }

    @Test fun digestAndContradictoryWeekMembershipFailClosed() {
        assertEquals(KotlinContinuousPlanEvidenceResult.Rejected(
            KotlinContinuousPlanEvidenceRefusal.SOURCE_REPLAY_MISMATCH),
            KotlinContinuousPlanEvidenceDomain.review(
                linked(),weeks().copy(sourceReplayDigest="c".repeat(64))))
        val duplicated=week(0).copy(sessions=week(0).sessions+
            week(1).sessions.take(1))
        assertEquals(KotlinContinuousPlanEvidenceResult.Rejected(
            KotlinContinuousPlanEvidenceRefusal.INVALID_HISTORY_WEEK_JOIN),
            KotlinContinuousPlanEvidenceDomain.review(linked(),
                weeks(listOf(duplicated,week(1),week(2)))))
    }
}
