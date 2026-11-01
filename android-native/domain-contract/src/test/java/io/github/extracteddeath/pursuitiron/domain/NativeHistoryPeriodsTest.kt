package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NativeHistoryPeriodsTest {
    @Test fun shortcutsIncludeTodayAndHonorDaylightSavingTime() {
        val zone=ZoneId.of("America/New_York")
        for ((day,todayHours,weekHours) in listOf(
            Triple("2026-03-08",23L,167L),
            Triple("2026-11-01",25L,169L)
        )) {
            val today=LocalDate.parse(day)
            val one=NativeHistoryDates.period("Upper",NativeHistoryPeriod.TODAY,today,zone)
            val week=NativeHistoryDates.period("Upper",NativeHistoryPeriod.SEVEN_DAYS,today,zone)
            val thirty=NativeHistoryDates.period("Upper",NativeHistoryPeriod.THIRTY_DAYS,today,zone)
            assertEquals("Upper",one.search)
            assertEquals(todayHours*3_600_000L,checkNotNull(one.untilExclusive)-checkNotNull(one.fromInclusive))
            assertEquals(weekHours*3_600_000L,checkNotNull(week.untilExclusive)-checkNotNull(week.fromInclusive))
            for ((filter,startDay) in listOf(one to today,week to today.minusDays(6),thirty to today.minusDays(29))) {
                assertEquals(startDay,Instant.ofEpochMilli(checkNotNull(filter.fromInclusive)).atZone(zone).toLocalDate())
                assertEquals(today,Instant.ofEpochMilli(checkNotNull(filter.untilExclusive)-1).atZone(zone).toLocalDate())
            }
        }
    }

    @Test fun thisMonthUsesFullLocalCalendarMonthIncludingLeapDays() {
        val zone=ZoneId.of("Pacific/Kiritimati")
        val feb=NativeHistoryDates.period("Legs",NativeHistoryPeriod.THIS_MONTH,
            LocalDate.parse("2024-02-14"),zone)
        assertEquals("Legs",feb.search)
        assertEquals(LocalDate.parse("2024-02-01"),
            Instant.ofEpochMilli(checkNotNull(feb.fromInclusive)).atZone(zone).toLocalDate())
        assertEquals(LocalDate.parse("2024-02-29"),
            Instant.ofEpochMilli(checkNotNull(feb.untilExclusive)-1).atZone(zone).toLocalDate())
        val month=NativeHistoryDates.period("",NativeHistoryPeriod.THIS_MONTH,
            LocalDate.parse("2026-11-01"),ZoneId.of("America/New_York"))
        assertEquals(721L*3_600_000L,checkNotNull(month.untilExclusive)-checkNotNull(month.fromInclusive))
    }

    @Test fun shortcutsRejectSkippedCalendarDaysRatherThanSilentlyWidening() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeHistoryDates.period("",NativeHistoryPeriod.TODAY,
                LocalDate.parse("2011-12-30"),ZoneId.of("Pacific/Apia"))
        }
    }
}
