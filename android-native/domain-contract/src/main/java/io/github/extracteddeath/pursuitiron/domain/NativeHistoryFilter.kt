package io.github.extracteddeath.pursuitiron.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Presentation query only. Canonical JS owns matching and history projection. */
data class NativeHistoryFilter(val search: String="",val fromInclusive: Long?=null,val untilExclusive: Long?=null) {
    init {
        require(search.length<=80 && search.none(Char::isISOControl))
        require(fromInclusive==null || fromInclusive in -8_640_000_000_000_000L..8_640_000_000_000_000L)
        require(untilExclusive==null || untilExclusive in -8_640_000_000_000_000L..8_640_000_000_000_001L)
        require(fromInclusive==null || untilExclusive==null || fromInclusive<untilExclusive)
    }
    val isActive: Boolean get()=search.isNotBlank() || fromInclusive!=null || untilExclusive!=null
}

/** Material date pickers use UTC day labels; workout dates use the device's calendar. */
object NativeHistoryDates {
    private fun day(utcDay: Long): LocalDate {
        val date=Instant.ofEpochMilli(utcDay).atZone(ZoneOffset.UTC).toLocalDate()
        require(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()==utcDay)
        return date
    }
    fun pickerDay(instant: Long,zone: ZoneId): Long = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun range(search: String,startUtcDay: Long,endUtcDay: Long,zone: ZoneId): NativeHistoryFilter {
        val start=day(startUtcDay);val end=day(endUtcDay);require(!end.isBefore(start))
        return NativeHistoryFilter(search,start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
}
