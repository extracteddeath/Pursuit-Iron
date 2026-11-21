package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val firstBlockDayLabel=DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

/**
 * Initial-block proposal only. Not the future-block acceptance panel:
 * there is not yet a durable, separately approved initial activation date.
 * Showing these dates may NEVER change the next-workout selection or history.
 */
@Composable
internal fun NativeKotlinFirstBlockCalendarPanel(
    host:NativeKotlinCandidateHost,
    athleteId:String,
    cycleId:String,
    cycleRevision:Long,
    sourceRevision:Long?,
    nextSlot:NativeCustomNextCycleSlot?
) {
    if(nextSlot !is NativeCustomNextCycleSlot.Next ||
        nextSlot.absoluteWeek!=1)return
    var monday by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<LocalDate?>(null)
    }
    var preview by remember(host,athleteId,cycleId,cycleRevision,sourceRevision) {
        mutableStateOf<NativeFirstBlockCalendarPreview?>(null)
    }
    LaunchedEffect(host,athleteId,cycleId,cycleRevision,sourceRevision,
        nextSlot.weekday,monday) {
        preview=null
        val observed=withContext(Dispatchers.IO) {
            val current=(host.readCustomCycleDraft(cycleId) as?
                NativeCustomDraftReadResult.Found)?.draft
            if(current==null || current.athleteId!=athleteId ||
                current.revision!=cycleRevision)
                NativeFirstBlockCalendarPreview.Rejected(
                    NativeFirstBlockCalendarRefusal.UNVERIFIED_SOURCE_OR_CYCLE)
            else host.reviewFirstBlockCalendarStart(cycleId,monday)
        }
        preview=observed
    }
    PursuitCard {
        PursuitSectionHeading(
            "First-block calendar preview",
            "Only signed Week 1 training positions are shown. A date preview is not an approved start or a workout record.")
        when(val reviewed=preview) {
            is NativeFirstBlockCalendarPreview.Ready -> {
                val plan=reviewed.proposal.takeIf {
                    it.cycleId==cycleId &&
                        it.athleteId==athleteId &&
                        it.draftRevision==cycleRevision &&
                        it.sourceRevision==sourceRevision
                }
                if(plan==null)
                    Text("The saved athlete or program changed. Reopen the plan.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                else {
                    Text("Proposed Week 1 · ${plan.selectedMonday.format(firstBlockDayLabel)}",
                        style=MaterialTheme.typography.titleSmall)
                    Text("Scheduled positions only · not activated, missed, or completed",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        plan.firstWeek.forEach { day ->
                            Text(day.localDate.format(firstBlockDayLabel),
                                style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedButton(
                        onClick={monday=plan.selectedMonday.plusWeeks(1)},
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Preview following week")
                    }
                    if(monday!=null)
                        TextButton(onClick={monday=null}) {
                            Text("Return to this week's preview")
                        }
                    Text("Initial start-date approval is not yet enabled. These previews cannot issue, move, skip, or update training.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is NativeFirstBlockCalendarPreview.Rejected ->
                Text("No verified Week 1 calendar can be proposed (${reviewed.reason.name.lowercase().replace('_',' ')}). Nothing has been scheduled.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            null -> Text("Verifying the signed program, history and training calendar…",
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
