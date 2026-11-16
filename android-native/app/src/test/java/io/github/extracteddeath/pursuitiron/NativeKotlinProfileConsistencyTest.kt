package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class NativeKotlinProfileConsistencyTest {
    private val ny=ZoneId.of("America/New_York")
    private val today=LocalDate.of(2026,11,16)
    private fun stamp(iso:String)=Instant.parse(iso).toEpochMilli()

    @Test fun emptyVerifiedHistoryShowsEightRealCalendarWeeksAndNoInventedStreak() {
        val report=NativeKotlinProfileConsistency.fromVerifiedFinishTimes(emptyList(),today,ny)
        assertEquals(8,report.weeks.size)
        assertEquals(LocalDate.of(2026,9,28),report.weeks.first().weekStartingMonday)
        assertEquals(LocalDate.of(2026,11,16),report.weeks.last().weekStartingMonday)
        assertEquals(0,report.sessionsInWindow)
        assertEquals(0,report.activeWeeks)
        assertEquals(0,report.undatedOrInvalidSessions)
    }

    @Test fun repeatedWorkoutsSameDayCountAsSessionsButOneTrainingDay() {
        val report=NativeKotlinProfileConsistency.fromVerifiedFinishTimes(
            listOf(stamp("2026-11-12T13:00:00Z"),stamp("2026-11-12T22:00:00Z"),
                stamp("2026-11-14T18:00:00Z")),today,ny)
        val week=report.weeks.single{it.weekStartingMonday==LocalDate.of(2026,11,9)}
        assertEquals(3,week.sessions)
        assertEquals(2,week.activeDays)
        assertEquals(3,report.sessionsInWindow)
        assertEquals(1,report.activeWeeks)
    }

    @Test fun timezoneBoundaryUsesLocalFinishDayNotUtcDay() {
        // 01:30 UTC Monday is still Sunday locally.
        val report=NativeKotlinProfileConsistency.fromVerifiedFinishTimes(
            listOf(stamp("2026-11-09T01:30:00Z"),
                stamp("2026-11-09T16:00:00Z")),today,ny)
        assertEquals(1,report.weeks.single{
            it.weekStartingMonday==LocalDate.of(2026,11,2)}.sessions)
        assertEquals(1,report.weeks.single{
            it.weekStartingMonday==LocalDate.of(2026,11,9)}.sessions)
    }

    @Test fun oldUndatedInvalidAndFutureDatesAreNotInventedIntoRecentWeeks() {
        val report=NativeKotlinProfileConsistency.fromVerifiedFinishTimes(
            listOf(null,Long.MAX_VALUE,stamp("2026-08-01T18:00:00Z"),
                stamp("2026-11-18T18:00:00Z"),stamp("2026-11-16T13:00:00Z")),
            today,ny)
        assertEquals(2,report.undatedOrInvalidSessions)
        assertEquals(1,report.futureDatedSessions)
        assertEquals(1,report.sessionsInWindow)
        assertEquals(1,report.activeDaysInWindow)
        assertEquals(1,report.weeks.last().sessions)
    }

    @Test fun chartIsBoundedAndRejectsImpossibleWindowSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinProfileConsistency.fromVerifiedFinishTimes(emptyList(),today,ny,0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinProfileConsistency.fromVerifiedFinishTimes(emptyList(),today,ny,13)
        }
        val one=NativeKotlinProfileConsistency.fromVerifiedFinishTimes(
            listOf(stamp("2026-11-16T13:00:00Z")),today,ny,1)
        assertEquals(1,one.weeks.size)
        assertEquals(1,one.sessionsInWindow)
    }
}
