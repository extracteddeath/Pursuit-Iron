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

/** Quick history shortcuts use calendar days, not fixed 24-hour intervals. */
enum class NativeHistoryPeriod { TODAY, SEVEN_DAYS, THIRTY_DAYS, THIS_MONTH }

/** Material date pickers use UTC day labels; workout dates use the device's calendar. */
object NativeHistoryDates {
    private fun day(utcDay: Long): LocalDate {
        val date=Instant.ofEpochMilli(utcDay).atZone(ZoneOffset.UTC).toLocalDate()
        require(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()==utcDay)
        return date
    }
    fun pickerDay(instant: Long,zone: ZoneId): Long = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    /**
     * One local calendar period, anchored on a supplied day for predictable
     * timezone/DST tests. This never authorizes history writes or calculates
     * training decisions; the canonical engine remains the query owner.
     */
    fun period(search: String,period: NativeHistoryPeriod,today: LocalDate,zone: ZoneId): NativeHistoryFilter {
        val first=when(period) {
            NativeHistoryPeriod.TODAY -> today
            NativeHistoryPeriod.SEVEN_DAYS -> today.minusDays(6)
            NativeHistoryPeriod.THIRTY_DAYS -> today.minusDays(29)
            NativeHistoryPeriod.THIS_MONTH -> today.withDayOfMonth(1)
        }
        val last=if(period==NativeHistoryPeriod.THIS_MONTH)today.withDayOfMonth(today.lengthOfMonth()) else today
        fun utcDay(date: LocalDate)=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return range(search,utcDay(first),utcDay(last),zone)
    }
    fun range(search: String,startUtcDay: Long,endUtcDay: Long,zone: ZoneId): NativeHistoryFilter {
        val start=day(startUtcDay);val end=day(endUtcDay);require(!end.isBefore(start))
        return NativeHistoryFilter(search,start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }
}
