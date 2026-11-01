package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryDates
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryFilter
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun calendarLabel(utcDay: Long)=Instant.ofEpochMilli(utcDay).atZone(ZoneOffset.UTC).toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Drafts survive rotation. Only Apply, Use dates or Clear issues a read. */
@Composable
internal fun NativeHistoryFilters(applied: NativeHistoryFilter,enabled: Boolean,onApply: (NativeHistoryFilter)->Unit) {
    val zone=ZoneId.systemDefault()
    val keyboard=LocalSoftwareKeyboardController.current
    var search by rememberSaveable(applied.search) { mutableStateOf(applied.search) }
    var start by rememberSaveable(applied.fromInclusive,zone.id) {
        mutableStateOf(applied.fromInclusive?.let { NativeHistoryDates.pickerDay(it,zone) })
    }
    var end by rememberSaveable(applied.untilExclusive,zone.id) {
        mutableStateOf(applied.untilExclusive?.let { NativeHistoryDates.pickerDay(it-1,zone) })
    }
    var showDates by rememberSaveable { mutableStateOf(false) }
    val draft=if(start!=null && end!=null)NativeHistoryDates.range(search.trim(),checkNotNull(start),checkNotNull(end),zone)
        else NativeHistoryFilter(search.trim())
    fun apply(filter: NativeHistoryFilter) { keyboard?.hide();onApply(filter) }
    PursuitCard {
        OutlinedTextField(value=search,onValueChange={ value ->
            if(value.length<=80 && value.none(Char::isISOControl))search=value
        },label={Text("Find a workout")},supportingText={Text("Program or day name")},
            enabled=enabled,singleLine=true,modifier=Modifier.fillMaxWidth(),
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
            keyboardActions=KeyboardActions(onSearch={if(enabled && draft!=applied)apply(draft)}))
        Text("Quick dates",style=MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(
                NativeHistoryPeriod.TODAY to "Today",
                NativeHistoryPeriod.SEVEN_DAYS to "7 days",
                NativeHistoryPeriod.THIRTY_DAYS to "30 days",
                NativeHistoryPeriod.THIS_MONTH to "This month"
            ).forEach { (period,label) ->
                val quick=NativeHistoryDates.period(search.trim(),period,LocalDate.now(zone),zone)
                FilterChip(
                    selected=applied.fromInclusive==quick.fromInclusive &&
                        applied.untilExclusive==quick.untilExclusive,
                    enabled=enabled,
                    onClick={
                        start=quick.fromInclusive?.let { NativeHistoryDates.pickerDay(it,zone) }
                        end=quick.untilExclusive?.let { NativeHistoryDates.pickerDay(it-1,zone) }
                        apply(quick)
                    },
                    label={Text(label)}
                )
            }
        }
        OutlinedButton(onClick={keyboard?.hide();showDates=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
            Text(if(start!=null && end!=null)"${calendarLabel(checkNotNull(start))} – ${calendarLabel(checkNotNull(end))}"
                else "Choose date range")
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={search="";start=null;end=null;apply(NativeHistoryFilter())},
                enabled=enabled && (draft.isActive || applied.isActive || search.isNotEmpty())) { Text("Clear filters") }
            FilledTonalButton(onClick={apply(draft)},enabled=enabled && draft!=applied) { Text("Apply filters") }
        }
    }
    if(showDates)NativeHistoryDatePicker(start,end,zone,enabled,onDismiss={showDates=false}) { first,last ->
        start=first;end=last;showDates=false
        apply(NativeHistoryDates.range(search.trim(),first,last,zone))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeHistoryDatePicker(start: Long?,end: Long?,zone: ZoneId,enabled: Boolean,onDismiss: ()->Unit,
    onChoose: (Long,Long)->Unit) {
    val dates=remember(zone.id) { object: SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long)=runCatching {
            NativeHistoryDates.range("",utcTimeMillis,utcTimeMillis,zone)
        }.isSuccess
    } }
    val picker=rememberDateRangePickerState(initialSelectedStartDateMillis=start,initialSelectedEndDateMillis=end,
        selectableDates=dates)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Workout dates",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text("Cancel") }
                }
                DateRangePicker(state=picker,modifier=Modifier.weight(1f).fillMaxWidth())
                val first=picker.selectedStartDateMillis;val last=picker.selectedEndDateMillis
                Button(onClick={onChoose(checkNotNull(first),checkNotNull(last))},
                    enabled=enabled && first!=null && last!=null,modifier=Modifier.fillMaxWidth()) { Text("Use dates") }
            }
        }
    }
}
