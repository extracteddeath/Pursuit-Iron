package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryCompletion
import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import java.time.Instant
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
