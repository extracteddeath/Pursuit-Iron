package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class NativeHistoryDatesTest {
    private fun utc(day: String)=LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    @Test fun inclusiveLocalDatesCoverTwentyThreeAndTwentyFiveHourDays() {
        val zone=ZoneId.of("America/New_York")
        for((day,hours) in listOf("2026-03-08" to 23,"2026-11-01" to 25)) {
            val range=NativeHistoryDates.range("Upper",utc(day),utc(day),zone)
            val first=checkNotNull(range.fromInclusive);val until=checkNotNull(range.untilExclusive)
            assertEquals(hours*3_600_000L,until-first)
            assertEquals(LocalDate.parse(day),Instant.ofEpochMilli(first).atZone(zone).toLocalDate())
            assertEquals(LocalDate.parse(day),Instant.ofEpochMilli(until-1).atZone(zone).toLocalDate())
            assertEquals(LocalDate.parse(day).plusDays(1),Instant.ofEpochMilli(until).atZone(zone).toLocalDate())
            assertEquals(utc(day),NativeHistoryDates.pickerDay(first,zone))
            assertEquals(utc(day),NativeHistoryDates.pickerDay(until-1,zone))
        }
    }
    @Test fun leapDatesAndOppositeOffsetsRetainThePickedCalendarLabels() {
        for(name in listOf("Pacific/Kiritimati","Pacific/Pago_Pago","UTC")) {
            val zone=ZoneId.of(name)
            val filter=NativeHistoryDates.range("",utc("2024-02-29"),utc("2024-03-01"),zone)
            assertEquals(utc("2024-02-29"),NativeHistoryDates.pickerDay(checkNotNull(filter.fromInclusive),zone))
            assertEquals(utc("2024-03-01"),NativeHistoryDates.pickerDay(checkNotNull(filter.untilExclusive)-1,zone))
        }
        val zone=ZoneId.of("America/New_York")
        assertEquals(utc("2026-10-31"),NativeHistoryDates.pickerDay(Instant.parse("2026-11-01T03:59:59Z").toEpochMilli(),zone))
        assertEquals(utc("2026-11-01"),NativeHistoryDates.pickerDay(Instant.parse("2026-11-01T04:00:00Z").toEpochMilli(),zone))
    }
    @Test fun invalidDatesAndUnboundedQueriesCannotReachTheReader() {
        val zone=ZoneOffset.UTC
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryDates.range("",utc("2026-11-02"),utc("2026-11-01"),zone) }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryDates.range("",utc("2026-11-01")+1,utc("2026-11-02"),zone) }
        // Samoa skipped this calendar day when crossing the date line. The
        // picker uses this validation to disable days with no local instants.
        assertThrows(IllegalArgumentException::class.java) {
            NativeHistoryDates.range("",utc("2011-12-30"),utc("2011-12-30"),ZoneId.of("Pacific/Apia"))
        }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryFilter("x".repeat(81)) }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryFilter("bad\nquery") }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryFilter("bad\u0085query") }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryFilter(fromInclusive=20,untilExclusive=10) }
        assertThrows(IllegalArgumentException::class.java) { NativeHistoryFilter(untilExclusive=8_640_000_000_000_002L) }
        assertFalse(NativeHistoryFilter("   ").isActive)
        assertTrue(NativeHistoryFilter(fromInclusive=0).isActive)
    }
}
