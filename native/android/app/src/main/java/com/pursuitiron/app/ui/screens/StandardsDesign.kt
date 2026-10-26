package com.pursuitiron.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/* The design's Strength standards (Main.dc.html stdOpen): a full page with the score, the details the comparison
   uses, each lift against its five bands, and the Strength Club. */
@Composable
fun StrengthStandardsSheet(vm: AppViewModel, onClose: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.progress, vm.profile) { d = vm.strengthStandards() }
    var liftId by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val sc = vm.profile?.o("score")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val lightBars = cs.surface.luminance() > 0.5f
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
            LaunchedEffect(w) {
                w.setDimAmount(0f)
                w.statusBarColor = android.graphics.Color.TRANSPARENT; w.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= 29) { w.isStatusBarContrastEnforced = false; w.isNavigationBarContrastEnforced = false }
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = lightBars
            }
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onClose) { Icon(PiIcons.Close, "Close") }
                Text("Strength standards", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                if (sc != null) Row(Modifier.height(40.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable {
                        val text = "My Strength Score is ${sc.i("overall")} / 100 (${sc.s("level")}) on Pursuit Iron."
                        runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.semantics(mergeDescendants = true) { contentDescription = "Share my Strength Score"; role = Role.Button }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(PiIcons.Upload, null, Modifier.size(16.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (sc != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.size(88.dp).clip(RoundedCornerShape(30.dp, 30.dp, 30.dp, 10.dp)).background(cs.primary), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("${sc.i("overall")}", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 36.sp), color = cs.onPrimary)
                        Text("OF 100", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold), color = cs.onPrimary.copy(alpha = 0.85f))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sc.s("level"), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = cs.onPrimaryContainer)
                        if (sc.has("delta8w") && !sc.isNull("delta8w")) { val dl = sc.i("delta8w")
                            Text((if (dl >= 0) "+$dl" else "−${-dl}") + " in 8 wk", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer) }
                        Text("Your four movement patterns averaged against lifters of your sex, bodyweight and age.", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = cs.onPrimaryContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
                    }
                }
                val p = vm.profile
                val age = p?.optString("age")?.toIntOrNull() ?: 31
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segmented(listOf("male" to "Male", "female" to "Female"), p?.s("sex", "male") ?: "male", { vm.setProfile(sex = it) })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Age", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).clickable { vm.setProfile(age = (age - 1).coerceAtLeast(14)) }.semantics { contentDescription = "Younger"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp) }
                        Text("$age", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).clickable { vm.setProfile(age = (age + 1).coerceAtMost(90)) }.semantics { contentDescription = "Older"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
                        Text("· ${p?.s("bodyweight")?.takeIf { it.isNotBlank() }?.let { "$it ${p.s("unit")}" } ?: "no bodyweight"}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                    }
                    Text("Used only for these comparisons, and kept on this phone.", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = cs.onSurfaceVariant)
                }
                val x = d
                when {
                    x == null -> {}
                    !x.b("ready") -> Text(if (x.s("missing") == "sex") "Pick your sex above to compare each lift against lifters like you." else "Log your bodyweight on Body to compare each lift against lifters like you.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    x.list("rows").isEmpty() -> Text("Log a squat, bench, deadlift, press or row to place it.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    else -> Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val levels = x.optJSONArray("levels")
                        x.list("rows").forEach { r -> StdRow(r, levels) { liftId = r.s("id") } }
                    }
                }
                StrengthClubCard(vm)
            }
        }
        com.pursuitiron.app.ui.PageOverlays()
        }
    }
    liftId?.let { id -> LiftDetailSheet(vm, id) { liftId = null } }
}

@Composable
private fun StdRow(r: JSONObject, levels: org.json.JSONArray?, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val u = r.s("unit", "kg")
    val idx = r.i("idx")
    val th = r.optJSONArray("thresh")?.let { a -> (0 until a.length()).map { a.optDouble(it) } }.orEmpty()
    val e1 = r.d("e1rm")
    val lv = { k: Int -> levels?.optString(k).orEmpty() }
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable(onClick = onOpen)
        .semantics(mergeDescendants = true) { contentDescription = "${r.s("name")}: ${r.s("level")} at ${fmtNum(e1)} $u" }.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text("${fmtNum(e1)} $u e1RM · ${String.format(java.util.Locale.US, "%.2f", r.d("ratio"))}× bodyweight", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Text(r.s("level"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (idx >= 4) cs.onPrimary else cs.onSecondaryContainer,
                modifier = Modifier.clip(CircleShape).background(if (idx >= 4) cs.primary else cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (th.isNotEmpty()) {
            val top = th.last() * 1.12
            val pos = { v: Double -> (v / top).toFloat().coerceIn(0f, 1f) }
            val fill by animateFloatAsState(pos(e1), Motion.data(), label = "std")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val w = maxWidth
                Column {
                    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(cs.primary))
                        }
                        th.forEach { t -> Box(Modifier.offset(x = w * pos(t) - 1.dp).width(2.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background((if (e1 >= t) cs.onPrimary else cs.outline).copy(alpha = 0.8f))) }
                    }
                    Box(Modifier.fillMaxWidth().height(14.dp)) {
                        th.forEachIndexed { k, t ->
                            Text(lv(k + 1).take(3), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                                color = if (k + 1 == idx) cs.onSurface else cs.onSurfaceVariant, modifier = Modifier.offset(x = w * pos(t) - 14.dp).width(28.dp), textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
        Text(if (idx < th.size) "${fmtNum(th[idx])} $u for ${lv(idx + 1)} · ${fmtNum(Math.max(0.0, Math.round((th[idx] - e1) * 10) / 10.0))} $u to go" else "Elite — the top band",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.primary)
    }
}
