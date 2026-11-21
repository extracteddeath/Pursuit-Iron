package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val approvedDateLabel=DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

/**
 * Native Plan's *explicit* future-block start-date review. No date is
 * derived from older completed sessions. The host reopens the owner,
 * applied source, saved program revision and exact future-block consent
 * on every disk operation. This panel never issues, moves or skips sets.
 */
@Composable
internal fun NativeKotlinApprovedBlockCalendarPanel(
    host:NativeKotlinCandidateHost,
    cycleId:String,
    athleteId:String,
    cycleRevision:Long,
    sourceRevision:Long?,
    nextSlot:NativeCustomNextCycleSlot?
) {
    var activated by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<NativeFutureCommitReview?>(null)
    }
    var saved by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<NativeActivatedBlockCalendarRead?>(null)
    }
    var localMonday by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<LocalDate?>(null)
    }
    var selectedMonday by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<LocalDate?>(null)
    }
    var proposed by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<NativeActivatedBlockCalendarPreview.Ready?>(null)
    }
    var saving by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf(false)
    }
    var loading by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf(true)
    }
    var message by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<String?>(null)
    }
    var editing by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf(false)
    }
    var scheduledDay by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<NativeActivatedBlockScheduledDayReview?>(null)
    }
    val scope=rememberCoroutineScope()

    suspend fun refresh() {
        loading=true
        proposed=null
        val actual=withContext(Dispatchers.IO) {
            val commit=host.readFutureBlockActivationCommit()
            val applied=(commit as? NativeFutureCommitReview.Applied)?.context
            if(applied?.cycleId!=cycleId)
                Triple(commit,null,null)
            else {
                val source=(host.readCustomAuthoringSource() as?
                    NativeAuthoringSourceRead.Found)?.source
                val zone=source?.setup?.reviewed?.timezoneId
                    ?.let{runCatching{ZoneId.of(it)}.getOrNull()}
                val monday=zone?.let{
                    LocalDate.now(it).with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }
                Triple(commit,host.readApprovedFutureBlockCalendar(
                    cycleId,applied.blockId),monday)
            }
        }
        activated=actual.first
        saved=actual.second
        localMonday=actual.third
        if(selectedMonday==null) {
            selectedMonday=actual.third?.plusWeeks(1)
        }
        loading=false
    }

    LaunchedEffect(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        activated=null
        saved=null
        localMonday=null
        selectedMonday=null
        message=null
        editing=false
        scheduledDay=null
        refresh()
    }

    val applied=(activated as? NativeFutureCommitReview.Applied)?.context
        ?.takeIf{it.cycleId==cycleId}
    val existing=(saved as? NativeActivatedBlockCalendarRead.Found)
        ?.calendar?.takeIf{it.athleteId==athleteId &&
            it.cycleId==cycleId &&
            it.sourceRevision==sourceRevision}
    val nextWeekAndDay=when(nextSlot) {
        is NativeCustomNextCycleSlot.Next->nextSlot.absoluteWeek to nextSlot.weekday
        is NativeCustomNextCycleSlot.Prepared->nextSlot.absoluteWeek to nextSlot.weekday
        is NativeCustomNextCycleSlot.ResumeIssued->nextSlot.absoluteWeek to nextSlot.weekday
        else->null
    }

    LaunchedEffect(host,athleteId,cycleId,cycleRevision,sourceRevision,
        existing?.approvalFingerprint,nextWeekAndDay) {
        scheduledDay=null
        val present=existing
        val position=nextWeekAndDay
        if(present!=null && position!=null) {
            scheduledDay=withContext(Dispatchers.IO) {
                host.reviewApprovedFutureBlockDay(
                    cycleId,present.blockId,position.first,position.second)
            }
        }
    }

    // No future source was applied: do not show an invented plan start date.
    if(applied==null)return

    PursuitCard {
        PursuitSectionHeading("Approved block calendar",
            "The selected Monday applies only to this already activated training block.")
        if(loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if(existing!=null) {
            Text("Block ${existing.blockId} · first absolute week ${existing.firstAbsoluteWeek}",
                style=MaterialTheme.typography.bodyMedium)
            Text("Starts ${existing.firstWeekMonday.format(approvedDateLabel)} · ${existing.timezoneId}",
                style=MaterialTheme.typography.titleMedium)
            Text("Your approved start Monday is saved. It will not move automatically after a missed or late workout.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(nextWeekAndDay!=null)when(val dated=scheduledDay) {
                is NativeActivatedBlockScheduledDayReview.Scheduled ->
                    Text("Next cycle slot: ${dated.scheduledLocalDate.format(approvedDateLabel)} (scheduled only, not marked missed or completed).",
                        style=MaterialTheme.typography.bodyMedium)
                is NativeActivatedBlockScheduledDayReview.Rejected ->
                    Text("The next cycle slot is outside this verified block calendar. No date has been assigned.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                null -> Text("Checking scheduled day…",
                    style=MaterialTheme.typography.bodySmall)
            }
        } else if(saved is NativeActivatedBlockCalendarRead.Rejected) {
            Text("The saved calendar could not be verified. A stale or changed program cannot reuse this date.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        } else {
            Text("No start date is approved for ${applied.blockId}. The training engine will not infer one from finished workouts.",
                style=MaterialTheme.typography.bodyMedium)
            Text("Choosing a calendar date does not authorize a workout, change weights or rep targets, or release an activation handoff.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={editing=!editing},enabled=!saving,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(if(editing)"Close date selection" else "Choose block start Monday")
            }
            val monday=localMonday
            if(editing && monday!=null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (0..12).forEach{offset->
                        val date=monday.plusWeeks(offset.toLong())
                        FilterChip(selected=selectedMonday==date,
                            enabled=!saving,
                            onClick={selectedMonday=date;message=null},
                            label={Text(date.format(approvedDateLabel))})
                    }
                }
                Button(onClick={
                    val day=selectedMonday ?:return@Button
                    saving=true
                    scope.launch {
                        val preview=withContext(Dispatchers.IO) {
                            host.previewApprovedFutureBlockCalendar(
                                cycleId,applied.blockId,day)
                        }
                        proposed=preview as? NativeActivatedBlockCalendarPreview.Ready
                        message=if(proposed==null)
                            "The calendar preview was refused: ${(preview as? NativeActivatedBlockCalendarPreview.Rejected)?.reason?.name ?: "not verified"}. Nothing was saved."
                        else null
                        saving=false
                    }
                },enabled=!saving && selectedMonday!=null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(if(saving)"Checking…" else "Review this start date")
                }
            } else if(editing) {
                Text("An athlete timezone could not be verified. No date can be chosen.",
                    color=MaterialTheme.colorScheme.error)
            }
        }
        if(message!=null)Text(checkNotNull(message),
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }

    proposed?.let{offer->
        AlertDialog(onDismissRequest={if(!saving)proposed=null},
            title={Text("Approve block start?")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${offer.proposed.firstWeekMonday.format(approvedDateLabel)} · ${offer.proposed.timezoneId}")
                    Text("Block ${offer.proposed.blockId} · absolute week ${offer.proposed.firstAbsoluteWeek}")
                    Text("Confirming saves this date for the exact current program and schedule. It does not mark earlier sessions complete, reschedule missed workouts, or change training targets.")
                }
            },
            confirmButton={
                Button(onClick={
                    saving=true
                    scope.launch {
                        val outcome=withContext(Dispatchers.IO) {
                            host.confirmApprovedFutureBlockCalendar(
                                cycleId,offer.proposed.blockId,
                                offer.proposed.firstWeekMonday,
                                offer.fingerprint,true)
                        }
                        proposed=null
                        if(outcome is NativeActivatedBlockCalendarWrite.Committed) {
                            refresh()
                            message="Start Monday approved and reread from private storage."
                            editing=false
                        } else {
                            message="Date approval refused: ${(outcome as? NativeActivatedBlockCalendarWrite.Rejected)?.reason?.name ?: "not verified"}. No change saved."
                        }
                        saving=false
                    }
                },enabled=!saving){Text("Approve date")}
            },
            dismissButton={
                TextButton(onClick={proposed=null},enabled=!saving) {
                    Text("Cancel")
                }
            })
    }
}
