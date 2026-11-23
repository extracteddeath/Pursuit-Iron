package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryCompletion
import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Presentation of recorded native facts, never inferred from file dates,
 * correction times, program weeks or session order. */
internal data class NativeKotlinHistoryMonth(
    val month:YearMonth?,val sessions:List<NativeKotlinMixedHistoryCompletion>
) {
    val key:String get()=month?.toString() ?:"undated"
    fun title(locale:Locale):String=month?.format(DateTimeFormatter.ofPattern("LLLL yyyy",locale))
        ?:"Date not recorded"
}

internal fun nativeHistoryMonths(sessions:List<NativeKotlinMixedHistoryCompletion>,
    zone:ZoneId):List<NativeKotlinHistoryMonth> =
    sessions.groupBy {entry->entry.manifest.completedAtEpochMillis?.let{
        YearMonth.from(Instant.ofEpochMilli(it).atZone(zone))
    }}.entries.sortedWith(compareByDescending<Map.Entry<YearMonth?,List<NativeKotlinMixedHistoryCompletion>>>{it.key})
        .map{NativeKotlinHistoryMonth(it.key,it.value.sortedByDescending{entry->entry.manifest.completionOrder})}

internal fun nativeHistoryDateLabel(entry:KotlinHistoryManifestEntry,zone:ZoneId,
    locale:Locale):String?=entry.completedAtEpochMillis?.let {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT)
        .withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(it))
}

/** Exercise visits use the recorded Finish clock, not their receipt order as a date. */
internal fun nativeExerciseHistorySessionTitle(entry:KotlinHistoryManifestEntry,
    zone:ZoneId,locale:Locale):String =
    "Session ${entry.completionOrder} · ${nativeHistoryDateLabel(entry,zone,locale) ?: "Date not recorded"}"

internal fun nativeHistoryRecordedDate(entry:KotlinHistoryManifestEntry,zone:ZoneId):LocalDate?=
    entry.completedAtEpochMillis?.let {Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}

/** A date browser filters verified records only. Unknown dates never become
 * rest days, streak gaps or inferred calendar entries. */
internal sealed interface NativeKotlinHistoryDateSelection {
    data object All:NativeKotlinHistoryDateSelection
    data object Undated:NativeKotlinHistoryDateSelection
    data class Month(val month:YearMonth,val day:LocalDate?=null):NativeKotlinHistoryDateSelection {
        init {require(day==null || YearMonth.from(day)==month)}
    }
}

/** Saved UI date strings are hints, not training authority. A removed
 * month or training date must not hide all verified workouts after a reload,
 * correction or changed local time zone. Keep a valid month in view even if
 * its previously selected day no longer contains a recorded Finish. */
internal fun nativeHistoryDateSelection(key:String,dayKey:String?,
    available:List<NativeKotlinHistoryMonth>,zone:ZoneId):NativeKotlinHistoryDateSelection = when(key) {
    "all" -> NativeKotlinHistoryDateSelection.All
    "undated" -> if(available.any{it.month==null})NativeKotlinHistoryDateSelection.Undated
        else NativeKotlinHistoryDateSelection.All
    else -> {
        val month=runCatching {YearMonth.parse(key)}.getOrNull()
        val section=available.singleOrNull{it.month==month}
        if(month==null || section==null)NativeKotlinHistoryDateSelection.All
        else {
            val validDay=dayKey?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
                ?.takeIf{selectedDay->
                    YearMonth.from(selectedDay)==month && section.sessions.any{entry->
                        nativeHistoryRecordedDate(entry.manifest,zone)==selectedDay
                    }
                }
            NativeKotlinHistoryDateSelection.Month(month,validDay)
        }
    }
}

internal fun nativeHistorySessionsInSelection(sessions:List<NativeKotlinMixedHistoryCompletion>,
    selection:NativeKotlinHistoryDateSelection,zone:ZoneId):List<NativeKotlinMixedHistoryCompletion> =
    sessions.filter {entry->when(selection) {
        NativeKotlinHistoryDateSelection.All -> true
        NativeKotlinHistoryDateSelection.Undated -> entry.manifest.completedAtEpochMillis==null
        is NativeKotlinHistoryDateSelection.Month -> nativeHistoryRecordedDate(entry.manifest,zone)?.let {
            YearMonth.from(it)==selection.month && (selection.day==null || it==selection.day)
        } ?:false
    }}

internal data class NativeKotlinHistoryCalendar(
    val month:YearMonth,val weekdays:List<DayOfWeek>,val weeks:List<List<LocalDate?>>,
    val sessionsByDate:Map<LocalDate,List<NativeKotlinMixedHistoryCompletion>>
)

internal fun nativeHistoryCalendar(sessions:List<NativeKotlinMixedHistoryCompletion>,
    month:YearMonth,zone:ZoneId,firstDay:DayOfWeek):NativeKotlinHistoryCalendar {
    val byDate=sessions.mapNotNull {entry->
        nativeHistoryRecordedDate(entry.manifest,zone)?.takeIf{YearMonth.from(it)==month}
            ?.let{it to entry}
    }.groupBy({it.first},{it.second})
        .mapValues{(_,entries)->entries.sortedByDescending{it.manifest.completionOrder}}
    val leading=Math.floorMod(month.atDay(1).dayOfWeek.value-firstDay.value,7)
    val cellCount=((leading+month.lengthOfMonth()+6)/7)*7
    val weeks=(0 until cellCount).map{index->
        (index-leading+1).takeIf{it in 1..month.lengthOfMonth()}?.let(month::atDay)
    }.chunked(7)
    return NativeKotlinHistoryCalendar(month,(0..6).map{firstDay.plus(it.toLong())},weeks,byDate)
}
