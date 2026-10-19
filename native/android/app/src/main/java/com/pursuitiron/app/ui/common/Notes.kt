package com.pursuitiron.app.ui.common

import androidx.compose.foundation.layout.Arrangement
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
