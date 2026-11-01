package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentation
import io.github.extracteddeath.pursuitiron.domain.OutlinePresentationPolicy
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Fixed synthetic session from the real JS generator; no personal store or logging. */
@Composable
internal fun SyntheticCanonicalWorkoutPanel(reader: CanonicalSyntheticOutlineReader?) {
    val splits = listOf("full_body" to "Full body",
        "upper_lower" to "Upper / lower", "ulppl" to "ULPPL")
    var split by remember(reader) { mutableStateOf("full_body") }
    var dayIndex by remember(reader) { mutableIntStateOf(0) }
    var revision by remember(reader) { mutableIntStateOf(0) }
    var request by remember(reader) { mutableStateOf<Triple<String,Int,Int>?>(null) }
    var outline by remember(reader) { mutableStateOf<WorkoutOutline?>(null) }
    var loading by remember(reader) { mutableStateOf(false) }
    var failed by remember(reader) { mutableStateOf(false) }

    LaunchedEffect(reader, request) {
        val choice = request ?: return@LaunchedEffect
        if (reader == null) return@LaunchedEffect
        loading = true
        failed = false
        val result = try {
            withContext(Dispatchers.IO) {
                reader.inspectSyntheticOutline(choice.first, choice.second)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Canonical synthetic test unavailable"))
        }
        if (isActive) {
            val value = result.getOrNull()
            outline = value?.takeIf {
                OutlinePresentationPolicy.inspect(it) is OutlinePresentation.SyntheticOnly
            }
            failed = outline == null
            loading = false
        }
    }

    SectionLabel("Canonical engine explorer", "Synthetic only")
    PursuitCard {
        PreviewLabel("ENGINE TEST · NOT YOUR WORKOUT", emphasis = true)
        Text("Generate an example using the locally packaged canonical JS engine. " +
            "No personal program, history or completed set is involved.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            splits.forEach { (key, label) ->
                PreviewChoice(label, split == key) {
                    split = key
                    dayIndex = 0
                    request = null
                    outline = null
                    loading = false
                    failed = false
                }
            }
        }
        val count = when (split) {
            "full_body" -> 3
            "upper_lower" -> 4
            else -> 5
        }
        Text("Synthetic training day", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(count) { index ->
                PreviewChoice("Day " + (index + 1), dayIndex == index) {
                    dayIndex = index
                    request = null
                    outline = null
                    loading = false
                    failed = false
                }
            }
        }
        Button(
            onClick = {
                outline = null
                failed = false
                revision++
                request = Triple(split, dayIndex, revision)
            },
            enabled = reader != null && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (reader == null) "Canonical engine bundle not packaged"
                else if (loading) "Generating test outline"
                else "Generate synthetic canonical outline")
        }
        if (loading) CircularProgressIndicator()
        if (reader == null) Text(
            "No local canonical engine is packaged in this preview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (failed) Text(
            "Canonical outline verification failed. No exercise output was accepted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (outline != null && request?.first == split && request?.second == dayIndex) {
        WorkoutOutlinePanel(outline = outline)
    }
}
