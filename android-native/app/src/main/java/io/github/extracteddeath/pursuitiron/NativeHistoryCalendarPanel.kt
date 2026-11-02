package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.extracteddeath.pursuitiron.domain.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Material calendar; enabled dates contain saved workouts in the complete month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeHistoryCalendarPanel(state: NativeRuntimeState,onLoad: (NativeHistoryCalendarQuery)->Unit,
    onOpen: (String)->Unit,onReload: ()->Unit) {
    val query=checkNotNull(state.historyCalendarQuery)
    val calendar=state.historyCalendar?.takeIf { it.query==query }
    val enabled=!state.historyBusy && !state.workoutBusy
    val zone=ZoneId.systemDefault()
    NativeHistoryFilters(query.filter,enabled,{onLoad(query.copy(search=it.search,zone=zone))},calendar=true)
    if(state.historyBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
    key(query.month,query.zone.id) {
        // Material caches date enablement by SelectableDates identity. Replace
        // this policy when a new revision arrives or an action becomes busy.
        val dates=remember(calendar,enabled) { object: SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long)=enabled && calendar?.workoutsOn(utcTimeMillis)?.isNotEmpty()==true
        } }
        val picker=rememberDatePickerState(initialDisplayedMonthMillis=query.firstUtcDay,
            yearRange=NativeHistoryCalendarQuery.YEARS,selectableDates=dates)
        LaunchedEffect(picker.displayedMonthMillis,enabled,zone.id) {
            val month=NativeHistoryCalendarQuery.pickerMonth(picker.displayedMonthMillis)
            if(enabled && (month!=query.month || zone!=query.zone))onLoad(query.copy(month=month,zone=zone))
        }
        DatePicker(state=picker,showModeToggle=false,title=null,headline=null,modifier=Modifier.fillMaxWidth())
        Text("Available dates have saved workouts. Choose a date to review them.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={onLoad(query.copy(month=YearMonth.now(zone),zone=zone))},enabled=enabled) { Text("Go to this month") }
        if(calendar!=null && NativeHistoryCalendarQuery.pickerMonth(picker.displayedMonthMillis)==query.month) {
            Text("${calendar.entries.size} workouts · ${calendar.activeDays} training days this month",
                style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
            if(calendar.entries.isEmpty()) {
                PursuitCard {
                    Text(if(query.search.isBlank())"No saved workouts this month." else "No matching workouts this month.")
                    Text("${calendar.unfilteredTotal} saved workouts on this device.",style=MaterialTheme.typography.bodySmall)
                }
            } else {
                val selected=picker.selectedDateMillis
                val workouts=selected?.let(calendar::workoutsOn).orEmpty()
                if(workouts.isEmpty())Text("Choose an available date above.")
                else {
                    val date=Instant.ofEpochMilli(checkNotNull(selected)).atZone(ZoneOffset.UTC).toLocalDate()
                    SectionLabel(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
                    Text("${workouts.size} saved workouts",style=MaterialTheme.typography.bodySmall)
                    workouts.forEach { NativeHistorySummaryCard(it,enabled,onOpen) }
                }
            }
        }
    }
    state.historyError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    TextButton(onClick=onReload,enabled=enabled) { Text("Reload calendar") }
}
