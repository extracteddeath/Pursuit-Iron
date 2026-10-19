package com.pursuitiron.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** App.js StretchFocusNote: shown on lifts the engine counts as stretch focus (isStretchFocus, engine 3). */
@Composable
fun StretchFocusNote(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PiIcons.Stretch, null, Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Stretch focus", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Loads the muscle in a lengthened position. Training at long muscle lengths is at least as effective as full-range work and may give a small extra hypertrophy edge — control the stretch, don't bounce out of the bottom.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The design's muscles worked: the lift's own muscle as a filled chip, the muscles it also trains as
 * tonal chips, biggest share first. Read out as one sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleChips(primary: String, secondary: List<String>, modifier: Modifier = Modifier, max: Int = 4) {
    val cs = MaterialTheme.colorScheme
    val sec = secondary.filter { it.isNotBlank() && !it.equals(primary, true) }.distinct().take(max)
    val desc = "Muscles worked: primary $primary" + if (sec.isNotEmpty()) "; secondary " + sec.joinToString(", ") else ""
    FlowRow(modifier.semantics(mergeDescendants = true) { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(10.dp)) {
            Text(primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        sec.forEach { m ->
            Surface(color = cs.surfaceContainerHighest, contentColor = cs.onSurface, shape = RoundedCornerShape(10.dp)) {
                Text(m, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}
