@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/* App.js NextWorkoutView (Build 898) as the design's Session preview: the day, its estimate and finish
   time, Time available (Full / 45 / 30 / 20, the engine's planShorterSession deciding every cut), the
   lifts as they'll run, and Start. Today only; the saved program never changes. */
@Composable
fun SessionPreviewSheet(vm: AppViewModel, t: AppViewModel.PreviewTarget, onClose: () -> Unit) {
    var minutes by remember(t) { mutableStateOf<Int?>(null) }
    var d by remember(t) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(t, minutes) { d = vm.sessionPreview(t, minutes) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val x = d ?: run { Text("Loading…", color = cs.onSurfaceVariant); return@Column }
            Column(Modifier.padding(horizontal = 4.dp)) {
                Kicker("Session preview")
                Text(x.s("dayLabel"), style = MaterialTheme.typography.headlineSmall)
                Text("${x.list("exercises").size} exercises", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val est = x.i("estimatedMinutes"); val fits = x.b("fits")
            Surface(color = if (fits) cs.primaryContainer else cs.tertiaryContainer, contentColor = if (fits) cs.onPrimaryContainer else cs.onTertiaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$est", style = MaterialTheme.typography.displaySmall)
                            Text(" min", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Text("${x.i("workingSets")} working sets", style = MaterialTheme.typography.labelLarge)
                    }
                    if (fits && est > 0) Text("Done around " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + est * 60_000L)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Kicker("Time available")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                x.list("options").forEach { o ->
                    val m = if (o.isNull("minutes")) null else o.i("minutes")
                    val on = m == minutes
                    val sub = when {
                        m == null -> "~${o.i("estimatedMinutes")}m"
                        !o.b("fits") -> "too short"
                        !o.b("changed") -> "fits"
                        else -> "−${o.i("cutSets")} sets"
                    }
                    Surface(onClick = { minutes = m }, color = if (on) cs.primary else cs.surfaceContainerHigh, contentColor = if (on) cs.onPrimary else cs.onSurface,
                        shape = RoundedCornerShape(if (on) 16.dp else 999.dp),
                        modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = o.s("label").replace("m", " minutes") + ", " + sub; this.selected = on }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(o.s("label"), style = MaterialTheme.typography.titleSmall)
                            Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }
            }
            Text(if (fits) "Today only. Keeps key lifts, warm-ups and prescribed rest. Cuts optional amplifiers and lower-priority volume first."
                else "The protected work needs about $est min. Choose a longer option or Full.",
                style = MaterialTheme.typography.bodySmall, color = if (fits) cs.onSurfaceVariant else cs.tertiary)
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                x.list("exercises").forEachIndexed { k, e ->
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(cs.surface), contentAlignment = Alignment.Center) {
                            Text("${k + 1}", style = MaterialTheme.typography.labelLarge)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(e.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.i("sets")} × ${e.s("reps")}" + (e.s("rir").takeIf { it.isNotBlank() }?.let { " @$it" } ?: "") +
                                (if (!e.isNull("cutFrom")) " · was ${e.i("cutFrom")} sets" else "") + (if (e.b("amplifierOff")) " · amplifier off" else ""),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            val skipped = x.strings("skipped")
            if (skipped.isNotEmpty()) Text("Skipped today: " + skipped.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PiIcons.Flame, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Text("Warm-ups and rests run as prescribed; a limit only removes work.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Button(onClick = { onClose(); vm.start(x.s("programId"), x.s("dayId"), x.i("weekIndex"), minutes) }, enabled = fits,
                modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(PiIcons.ChevronRight, null); Text(" Start workout") }
        }
    }
}
