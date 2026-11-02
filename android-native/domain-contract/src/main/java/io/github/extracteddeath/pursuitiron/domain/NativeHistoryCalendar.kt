package io.github.extracteddeath.pursuitiron.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections

/** A local calendar query; it makes no training or persistence decisions. */
data class NativeHistoryCalendarQuery(val month: YearMonth,val zone: ZoneId,val search: String="") {
    init { require(month.year in YEARS);NativeHistoryFilter(search) }
    val firstUtcDay: Long get()=month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val filter: NativeHistoryFilter get()=NativeHistoryDates.range(search,firstUtcDay,
        month.atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),zone)
    companion object {
        val YEARS=1900..2100
        fun pickerMonth(utcDay: Long): YearMonth {
            val date=Instant.ofEpochMilli(utcDay).atZone(ZoneOffset.UTC).toLocalDate()
            require(date.dayOfMonth==1 && date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()==utcDay)
            return YearMonth.from(date)
        }
    }
}

/** Complete month, assembled from canonical pages of the same owned revision. */
class NativeHistoryCalendar internal constructor(val query: NativeHistoryCalendarQuery,val snapshotSha256: String,
    val unfilteredTotal: Int,rows: List<NativeHistorySummary>) {
    val entries: List<NativeHistorySummary> = Collections.unmodifiableList(rows.toList())
    private val days=rows.groupBy { NativeHistoryDates.pickerDay(it.date,query.zone) }
        .mapValues { (_,values)->Collections.unmodifiableList(values.toList()) }
    val activeDays: Int get()=days.size
    fun workoutsOn(utcDay: Long): List<NativeHistorySummary> = days[utcDay] ?: emptyList()
}
