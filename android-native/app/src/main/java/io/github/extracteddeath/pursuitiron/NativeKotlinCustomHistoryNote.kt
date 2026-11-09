package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only HISTORY note, not a UI-owned cache or backup. The source row
 * already comes from verified global History; independently cold-open the
 * signed-in athlete's CUSTOM journal and match Finish, cycle and owner
 * before showing any private note text.
 */
@Composable
internal fun NativeKotlinCustomHistoryNote(
    host:NativeKotlinCandidateHost,
    completed:NativeKotlinMixedHistoryCompletion.Custom
) {
    val id=completed.manifest.workoutId
    var loading by remember(host,id) {mutableStateOf(true)}
    var note by remember(host,id) {mutableStateOf<String?>(null)}
    var unavailable by remember(host,id) {mutableStateOf(false)}
    LaunchedEffect(host,id,completed.manifest.finishEventId,
        completed.evidence.journalRevision) {
        loading=true
        note=null
        unavailable=false
        val opened=withContext(Dispatchers.IO) {
            runCatching{host.openIssuedCustomWorkout(id)}.getOrNull()
        } as? NativeCustomJournalResult.Durable
        val journal=opened?.issued?.journal
        val source=completed.evidence
        val valid=journal!=null &&
            opened?.issued?.athleteId==source.athleteId &&
            journal.workoutId==id &&
            source.workoutId==id &&
            journal.finishedByEventId==completed.manifest.finishEventId &&
            source.finishedEventId==completed.manifest.finishEventId &&
            journal.revision==source.journalRevision &&
            journal.snapshot.stamp.athleteId==source.athleteId &&
            journal.snapshot.stamp.cycleId==source.cycleId &&
            journal.snapshot.stamp.cycleRevision==source.cycleRevision
        if(valid) note=journal?.sessionNote
        else unavailable=true
        loading=false
    }
    when {
        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        unavailable -> Text("This workout's saved note cannot be verified. Other history remains unchanged.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)
        !note.isNullOrBlank() -> {
            PursuitSectionHeading("Saved session note",
                "Recorded with this completed custom workout · read-only")
            Text(note.orEmpty(),style=MaterialTheme.typography.bodyMedium)
        }
        else -> Text("No session note was saved for this workout.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)
    }
}
