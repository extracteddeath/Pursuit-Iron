package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryCompletion
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Read-only controls over the same verified mixed timeline as the session list. */
@Composable
internal fun NativeKotlinHistoryDateBrowser(
    months:List<NativeKotlinHistoryMonth>,matchingSessions:List<NativeKotlinMixedHistoryCompletion>,
    selection:NativeKotlinHistoryDateSelection,zone:ZoneId,locale:Locale,
    onSelect:(NativeKotlinHistoryDateSelection)->Unit
) {
    val dated=months.filter{it.month!=null}
    val undated=months.firstOrNull{it.month==null}
    var choosingMonth by rememberSaveable{mutableStateOf(false)}
    var visibleMonthCount by rememberSaveable{mutableStateOf(24)}
    val selected=selection as? NativeKotlinHistoryDateSelection.Month
    val selectedMonth=selected?.month
    val monthIndex=dated.indexOfFirst{it.month==selectedMonth}
    PursuitCard {
        PursuitSectionHeading("Browse workouts","Choose all workouts, a recorded month or undated sessions.")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            FilterChip(selected=selection==NativeKotlinHistoryDateSelection.All,
                onClick={onSelect(NativeKotlinHistoryDateSelection.All)},
                modifier=Modifier.heightIn(min=48.dp),label={Text("All workouts")})
            if(dated.isNotEmpty())FilterChip(selected=selected!=null,
                onClick={if(selected==null)onSelect(NativeKotlinHistoryDateSelection.Month(dated.first().month!!))},
                modifier=Modifier.heightIn(min=48.dp),label={Text("Calendar")})
            if(undated!=null)FilterChip(selected=selection==NativeKotlinHistoryDateSelection.Undated,
                onClick={onSelect(NativeKotlinHistoryDateSelection.Undated)},
                modifier=Modifier.heightIn(min=48.dp),label={Text("Undated (${undated.sessions.size})")})
        }
        if(selected!=null) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={onSelect(NativeKotlinHistoryDateSelection.Month(dated[monthIndex+1].month!!))},
                    enabled=monthIndex>=0 && monthIndex<dated.lastIndex,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,contentDescription="Earlier recorded month")
                }
                TextButton(onClick={visibleMonthCount=24;choosingMonth=true},modifier=Modifier.weight(1f).heightIn(min=48.dp)
                    .semantics{contentDescription="Choose recorded month, ${selected.month.format(DateTimeFormatter.ofPattern("LLLL yyyy",locale))}"}) {
                    Text(selected.month.format(DateTimeFormatter.ofPattern("LLLL yyyy",locale)))
                }
                IconButton(onClick={onSelect(NativeKotlinHistoryDateSelection.Month(dated[monthIndex-1].month!!))},
                    enabled=monthIndex>0,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,contentDescription="Later recorded month")
                }
            }
            val firstDay=WeekFields.of(locale).firstDayOfWeek
            val calendar=remember(matchingSessions,selected.month,zone,firstDay) {
                nativeHistoryCalendar(matchingSessions,selected.month,zone,firstDay)
            }
            Text("Dates use your device’s time zone. Counts show saved workouts matching your exercise search.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            NativeKotlinHistoryMonthGrid(calendar,selected.day,locale) {day->
                onSelect(NativeKotlinHistoryDateSelection.Month(selected.month,
                    day.takeUnless{it==selected.day}))
            }
            if(selected.day!=null)TextButton(onClick={onSelect(NativeKotlinHistoryDateSelection.Month(selected.month))},
                modifier=Modifier.heightIn(min=48.dp)) {Text("Show whole month")}
        }
        if(selection==NativeKotlinHistoryDateSelection.Undated || dated.isEmpty())
            Text("These workouts have no recorded Finish date. They stay available here without being assigned to a calendar day.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(choosingMonth)NativeEditSheet(busy=false,onDismiss={choosingMonth=false}) {
        PursuitSectionHeading("Choose a recorded month","Only months with saved dated workouts are listed.")
        TextButton(onClick={choosingMonth=false},modifier=Modifier.heightIn(min=48.dp)){Text("Close")}
        dated.take(visibleMonthCount).forEach{section->
            FilterChip(selected=section.month==selectedMonth,
                onClick={onSelect(NativeKotlinHistoryDateSelection.Month(section.month!!));choosingMonth=false},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                label={Text("${section.title(locale)} · ${section.sessions.size} saved workouts")})
        }
        if(visibleMonthCount<dated.size)TextButton(onClick={visibleMonthCount+=24},
            modifier=Modifier.heightIn(min=48.dp)) {Text("Show more months")}
    }
}

@Composable
private fun NativeKotlinHistoryMonthGrid(calendar:NativeKotlinHistoryCalendar,selected:LocalDate?,
    locale:Locale,onSelect:(LocalDate)->Unit
) {
    val dateFormat=remember(locale){DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)}
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Seven 48dp targets need 336dp. Large text and narrow phones get
        // full-width recorded-day choices, rather than squeezed touch areas.
        if(maxWidth<336.dp || LocalDensity.current.fontScale>=1.3f) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                Text("Recorded days",style=MaterialTheme.typography.labelLarge)
                calendar.sessionsByDate.toSortedMap(reverseOrder()).forEach{(day,sessions)->
                    FilterChip(selected=day==selected,onClick={onSelect(day)},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                        label={Text("${day.format(dateFormat)} · ${sessions.size} saved ${if(sessions.size==1)"workout" else "workouts"}")})
                }
            }
        } else Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
            Row(Modifier.fillMaxWidth()) {
                calendar.weekdays.forEach{weekday->
                    Box(Modifier.weight(1f).heightIn(min=32.dp),contentAlignment=Alignment.Center) {
                        Text(weekday.getDisplayName(TextStyle.SHORT_STANDALONE,locale),
                            style=MaterialTheme.typography.labelSmall,
                            modifier=Modifier.semantics{contentDescription=weekday.getDisplayName(TextStyle.FULL,locale)})
                    }
                }
            }
            calendar.weeks.forEach{week->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach{day->
                        if(day==null)Spacer(Modifier.weight(1f).height(48.dp))
                        else {
                            val count=calendar.sessionsByDate[day].orEmpty().size
                            val isSelected=day==selected
                            Surface(selected=isSelected,onClick={onSelect(day)},enabled=count>0 || isSelected,
                                modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics(mergeDescendants=true) {
                                    contentDescription="${day.format(dateFormat)}, $count saved ${if(count==1)"workout" else "workouts"}"
                                },shape=MaterialTheme.shapes.small,
                                color=if(isSelected)MaterialTheme.colorScheme.primaryContainer
                                    else if(count>0)MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor=if(isSelected)MaterialTheme.colorScheme.onPrimaryContainer
                                    else if(count>0)MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant) {
                                Column(Modifier.padding(PursuitSpace.xxs),horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.Center) {
                                    Text(day.dayOfMonth.toString(),style=MaterialTheme.typography.bodyMedium)
                                    if(count>0)Text(count.toString(),style=MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(calendar.sessionsByDate.isEmpty())Text("No saved dated workouts match this month and exercise search.",
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
