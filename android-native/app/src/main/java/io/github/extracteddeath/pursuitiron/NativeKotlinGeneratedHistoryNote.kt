package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A finished generated workout may show its private session note ONLY when
 * the owner-verified journal matches the independently verified History row. */
@Composable
internal fun NativeKotlinGeneratedHistoryNote(
    host:NativeKotlinCandidateHost,
    completed:NativeKotlinMixedHistoryCompletion.Composer
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
            runCatching{host.recoverWorkout(id)}.getOrNull()
        } as? NativeJournalFileResult.Durable
        val journal=opened?.journal
        val source=completed.evidence
        val verified=journal!=null &&
            journal.snapshot.workoutId==id &&
            source.workoutId==id &&
            journal.finishedByEventId==completed.manifest.finishEventId &&
            source.finishEventId==completed.manifest.finishEventId &&
            journal.revision==source.journalRevision &&
            journal.snapshot.source==source.source &&
            journal.originalSnapshot.source==source.source
        if(verified)note=journal?.sessionNote
        else unavailable=true
        loading=false
    }
    when {
        loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        unavailable -> Text("This workout's saved note cannot be verified. Other history remains unchanged.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        !note.isNullOrBlank() -> {
            PursuitSectionHeading("Saved session note",
                "Recorded with this completed generated workout · read-only")
            Text(note.orEmpty(),style=MaterialTheme.typography.bodyMedium)
        }
        else -> Text("No session note was saved for this workout.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall)
    }
}
