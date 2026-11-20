package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class KotlinObservedScheduleWeekDomainTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"cycle-1",4,"gym-1",3,"catalog-5","rules-1")
    private val zone="America/New_York"
    private val weekdays=setOf(0,2,4)
    private fun millis(date:String):Long=LocalDate.parse(date)
        .atTime(12,0).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()

    private fun ledger(days:List<Int>,
        shortened:Set<Int> =emptySet()):KotlinProgramHistoryReadout {
        val sorted=weekdays.sorted()
        val workouts=days.mapIndexed {i,weekday->
            KotlinProgramHistoricalWorkout(
                "workout-${i+1}",sorted.indexOf(weekday),weekday,
                "finished-${i+1}",2,0,1,0,0,
                ComposerSessionPurpose.HYPERTROPHY,
                i in shortened)
        }
        val facts=workouts.map {w->
            KotlinProgramHistoricalWorkingSet(
                w.workoutId,w.dayIndex,w.calendarDay,
                "bench",ComposerRole.PRIMARY,w.purpose,
                w.workoutId+":set",0,
                DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("25")),
                RepBand(8,12),2,KotlinJournalSetOutcome.COMPLETED,
                BigDecimal("25"),LoadUnit.LB,11,2,null,
                "receipt-"+w.workoutId,null,
                KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY)
        }
        return KotlinProgramHistoryReadout(
            2,"athlete-1","cycle-1",source,workouts,facts,
            "a".repeat(64))
    }

    private fun evaluate(days:List<Int>,dates:List<String?>,
        shortened:Set<Int> =emptySet(),
        orders:Map<String,Long>?=null):KotlinObservedWeekResult {
        val ledger=ledger(days,shortened)
        val byTime=ledger.workouts.mapIndexed{i,w->
            w.workoutId to dates[i]?.let(::millis)
        }.toMap()
        val byOrder=orders ?:ledger.workouts.mapIndexed{i,w->
            w.workoutId to (i+1).toLong()
        }.toMap()
        return KotlinObservedScheduleWeekDomain.review(
            ledger,byTime,byOrder,weekdays,zone)
    }

    @Test fun threeActualDatedScheduledDaysOnlyEstablishObservedCoverage() {
        val result=evaluate(listOf(0,2,4),listOf(
            "2026-11-16","2026-11-18","2026-11-20"))
            as KotlinObservedWeekResult.Reviewed
        val evidence=result.evidence
        val week=evidence.weeks.single()
        assertEquals(LocalDate.of(2026,11,16),week.monday)
        assertEquals(weekdays,week.fullyRecordedWeekdays)
        assertEquals(KotlinObservedWeekCoverage.FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY,
            week.coverage)
        assertTrue(week.duplicateWeekdays.isEmpty())
        assertTrue(evidence.undatedWorkoutIds.isEmpty())
        assertFalse(week.canProveProgramAdherence)
        assertFalse(evidence.canApproveVolumeAdaptation)
        assertFalse(evidence.canPromotePhase)
        assertFalse(evidence.canAssumeMissedWeeks)
    }

    @Test fun missingSkippedOrDuplicatedScheduledWorkRemainsPartial() {
        val missing=evaluate(listOf(0,2),
            listOf("2026-11-16","2026-11-18"))
            as KotlinObservedWeekResult.Reviewed
        assertEquals(KotlinObservedWeekCoverage.PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY,
            missing.evidence.weeks.single().coverage)
        assertEquals(setOf(0,2),
            missing.evidence.weeks.single().fullyRecordedWeekdays)
        val shortened=evaluate(listOf(0,2,4),listOf(
            "2026-11-16","2026-11-18","2026-11-20"),
            shortened=setOf(1)) as KotlinObservedWeekResult.Reviewed
        assertEquals(setOf(0,4),
            shortened.evidence.weeks.single().fullyRecordedWeekdays)
        assertEquals(KotlinObservedWeekCoverage.PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY,
            shortened.evidence.weeks.single().coverage)
        val doubled=evaluate(listOf(0,0,2,4),listOf(
            "2026-11-16","2026-11-16","2026-11-18","2026-11-20"))
            as KotlinObservedWeekResult.Reviewed
        assertEquals(setOf(0),doubled.evidence.weeks.single().duplicateWeekdays)
        assertEquals(KotlinObservedWeekCoverage.PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY,
            doubled.evidence.weeks.single().coverage)
    }

    @Test fun undatedOrWrongLocalWeekdayDoesNotPretendCompleteWeek() {
        val unknown=evaluate(listOf(0,2,4),listOf(
            "2026-11-16",null,"2026-11-20"))
            as KotlinObservedWeekResult.Reviewed
        assertEquals(listOf("workout-2"),
            unknown.evidence.undatedWorkoutIds)
        assertEquals(KotlinObservedWeekCoverage.CHRONOLOGY_UNVERIFIED,
            unknown.evidence.weeks.single().coverage)

        val wrongDay=evaluate(listOf(0,2,4),listOf(
            "2026-11-17","2026-11-18","2026-11-20"))
            as KotlinObservedWeekResult.Reviewed
        assertEquals(KotlinObservedWeekCoverage.PARTIAL_OR_INCOMPARABLE_FOR_REVIEW_ONLY,
            wrongDay.evidence.weeks.single().coverage)
        assertFalse(wrongDay.evidence.weeks.single().sessions.first()
            .matchesScheduledWeekday)
        assertEquals(setOf(2,4),
            wrongDay.evidence.weeks.single().fullyRecordedWeekdays)
    }

    @Test fun contradictoryFinishClockAndTimeZoneBoundaryFailConservatively() {
        val reversed=evaluate(listOf(0,2,4),listOf(
            "2026-11-20","2026-11-18","2026-11-16"))
            as KotlinObservedWeekResult.Reviewed
        assertTrue(reversed.evidence.hasConflictingCompletionOrder)
        assertEquals(KotlinObservedWeekCoverage.CHRONOLOGY_UNVERIFIED,
            reversed.evidence.weeks.single().coverage)

        // Monday 00:30 UTC is still Sunday evening in New York. The
        // athlete's local week, never UTC date, owns weekday identity.
        val utcInstant=java.time.Instant.parse("2026-11-23T00:30:00Z")
            .toEpochMilli()
        val mondayLedger=ledger(listOf(0))
        val noGuess=KotlinObservedScheduleWeekDomain.review(
            mondayLedger,mapOf("workout-1" to utcInstant),
            mapOf("workout-1" to 1L),weekdays,zone)
            as KotlinObservedWeekResult.Reviewed
        assertEquals(LocalDate.of(2026,11,16),
            noGuess.evidence.weeks.single().monday)
        assertEquals(6,noGuess.evidence.weeks.single().sessions.single()
            .observedWeekday)
        assertFalse(noGuess.evidence.weeks.single().sessions.single()
            .matchesScheduledWeekday)
    }

    @Test fun futureDayChangePreservesActualDatesWithoutBorrowingOldSchedule() {
        val historical=ledger(listOf(0,2,4))
        val dates=mapOf("workout-1" to millis("2026-11-16"),
            "workout-2" to millis("2026-11-18"),
            "workout-3" to millis("2026-11-20"))
        val ordinals=mapOf("workout-1" to 1L,
            "workout-2" to 2L,"workout-3" to 3L)
        // The athlete's reviewed FUTURE week uses Thursday, not the
        // previous Wednesday. Do not reject authenticated historical
        // completions or pretend that old Wednesday was future Thursday.
        val result=KotlinObservedScheduleWeekDomain.review(
            historical,dates,ordinals,setOf(0,3,4),zone)
            as KotlinObservedWeekResult.Reviewed
        val observed=result.evidence
        assertFalse(observed.sourceScheduleCompatible)
        assertEquals(listOf("workout-2"),
            observed.incompatibleSourceWorkoutIds)
        assertEquals(setOf(0,3,4),observed.scheduledWeekdays)
        val week=observed.weeks.single()
        assertEquals(KotlinObservedWeekCoverage.SOURCE_SCHEDULE_INCOMPATIBLE,
            week.coverage)
        assertTrue(week.fullyRecordedWeekdays.isEmpty())
        assertEquals(LocalDate.of(2026,11,18),
            week.sessions.single{it.workoutId=="workout-2"}.localCompletionDate)
        assertTrue(observed.undatedWorkoutIds.isEmpty())
        assertFalse(observed.canAssumeMissedWeeks)
        assertFalse(observed.canApproveVolumeAdaptation)
        assertFalse(observed.canPromotePhase)
    }

    @Test fun malformedIssuedDayIndexIsStillRejectedNotTreatedAsScheduleChange() {
        val historical=ledger(listOf(0,2,4)).copy(
            workouts=ledger(listOf(0,2,4)).workouts.mapIndexed{i,w->
                if(i==1)w.copy(dayIndex=8) else w
            })
        assertEquals(KotlinObservedWeekResult.Rejected(
            KotlinObservedWeekRefusal.INVALID_SOURCE_OR_SCHEDULE),
            KotlinObservedScheduleWeekDomain.review(
                historical,historical.workouts.associate{it.workoutId to null},
                historical.workouts.mapIndexed{i,w->
                    w.workoutId to (i+1).toLong()
                }.toMap(),weekdays,zone))
    }

    @Test fun incompleteTimestampsOrOrderingAreNotAcceptableInputs() {
        val original=ledger(listOf(0,2,4))
        val orders=mapOf("workout-1" to 1L,
            "workout-2" to 2L,"workout-3" to 3L)
        assertEquals(KotlinObservedWeekResult.Rejected(
            KotlinObservedWeekRefusal.INVALID_TIME_MAPPING),
            KotlinObservedScheduleWeekDomain.review(
                original,mapOf("workout-1" to millis("2026-11-16")),
                orders,weekdays,zone))
        assertEquals(KotlinObservedWeekResult.Rejected(
            KotlinObservedWeekRefusal.INVALID_TIME_MAPPING),
            KotlinObservedScheduleWeekDomain.review(
                original,original.workouts.associate{it.workoutId to null},
                orders.mapValues{1L},weekdays,zone))
        assertEquals(KotlinObservedWeekResult.Rejected(
            KotlinObservedWeekRefusal.INVALID_SOURCE_OR_SCHEDULE),
            KotlinObservedScheduleWeekDomain.review(
                original,original.workouts.associate{it.workoutId to null},
                orders,weekdays,"not-a-real-timezone"))
    }
}
