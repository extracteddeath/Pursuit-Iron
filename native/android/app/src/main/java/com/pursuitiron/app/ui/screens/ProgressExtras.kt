@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONArray
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A line chart of a lift's estimated 1RM, one point per session (App.js lift detail chart). */
@Composable
fun E1rmChart(points: List<JSONObject>, unit: String, goal: Double?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val vals = points.map { it.d("e1rm") }
    if (vals.isEmpty()) return
    val lo = (listOfNotNull(vals.min(), goal).min() * 0.95)
    val hi = (listOfNotNull(vals.max(), goal).max() * 1.03).coerceAtLeast(lo + 1)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Estimated 1RM over ${vals.size} sessions, from ${fmtNum(vals.first())} to ${fmtNum(vals.last())} $unit" }) {
            val w = size.width; val h = size.height
            val x = { i: Int -> if (vals.size == 1) w / 2 else i * w / (vals.size - 1) }
            val y = { v: Double -> (h - ((v - lo) / (hi - lo) * h)).toFloat() }
            listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(cs.outlineVariant.copy(alpha = 0.5f), Offset(0f, h * f), Offset(w, h * f), 1.dp.toPx()) }
            goal?.let { g -> drawLine(cs.tertiary, Offset(0f, y(g)), Offset(w, y(g)), 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
            val path = Path()
            vals.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, cs.primary, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            vals.forEachIndexed { i, v -> drawCircle(cs.primary, 4.dp.toPx(), Offset(x(i), y(v))) }
        }
        Row {
            Text(fmtNum(Math.round(lo).toDouble()), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${fmtNum(Math.round(hi).toDouble())} $unit", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun LiftDetailSheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    var d by remember(id) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(id) { d = vm.liftDetail(id) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val x = d ?: run { Text("Loading…"); return@Column }
            Kicker(x.s("partLabel").replaceFirstChar { it.uppercase() })
            Text(x.s("name"), style = MaterialTheme.typography.headlineSmall)
            PinToggle(vm, id)
            LiftPage(vm, id, x) { scope.launch { kotlinx.coroutines.delay(200); d = vm.liftDetail(id) } }

        }
    }
}

/* App.js StrengthComparisonCard. */
@Composable
fun StrengthStandardsCard(vm: AppViewModel, onLift: (String) -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.progress, vm.profile) { d = vm.strengthStandards() }
    val x = d ?: return
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Trophy, null, tint = cs.primary)
            Text("Strength standards", style = MaterialTheme.typography.titleMedium)
        }
        if (!x.b("ready")) {
            Text(if (x.s("missing") == "sex") "Set your sex on your profile to compare each lift against lifters like you." else "Add your bodyweight on your profile to compare each lift against lifters like you.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            TextButton(onClick = { vm.goTab(com.pursuitiron.app.Tab.You) }) { Text("Open profile") }
            return@PiCard
        }
        Text("Compared with ${x.s("cohort")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        val rows = x.list("rows")
        if (rows.isEmpty()) Text("Log a squat, bench, deadlift, press or row to place it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).let { m -> m }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onLift(r.s("id")) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.weight(1f)) {
                        Text(r.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                    }
                    Text(r.s("level"), style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.align(Alignment.CenterVertically))
                }
                ProgressBar(((r.i("idx") + r.d("within")) / 6.0).toFloat().coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 8.dp)
                r.s("next").takeIf { it.isNotBlank() }?.let { nx -> Text(if (r.d("add") > 0.5) "+${fmtNum(r.d("add"))} ${r.s("unit")} e1RM → $nx" else "On the cusp of $nx", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
            }
        }
    }
}

/* App.js HistoryEditSheet: correct what was performed; the workout's place in the plan stays fixed. */
private class EditSet(w: String, r: String, rir: String, val sub: Boolean) { var w by mutableStateOf(w); var r by mutableStateOf(r); var rir by mutableStateOf(rir) }
private class EditLift(val id: String, val name: String, sets: List<EditSet>) { val sets = mutableStateListOf<EditSet>().apply { addAll(sets) } }

@Composable
fun HistoryEditSheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    var draft by remember(id) { mutableStateOf<JSONObject?>(null) }
    val lifts = remember(id) { mutableStateListOf<EditLift>() }
    var minutes by remember(id) { mutableStateOf("") }
    var dateText by remember(id) { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    LaunchedEffect(id) {
        val d = vm.historyEditDraft(id) ?: return@LaunchedEffect
        draft = d
        minutes = if (d.has("durationMin") && !d.isNull("durationMin")) d.s("durationMin") else ""
        dateText = fmt.format(Date(d.optLong("date")))
        lifts.clear(); lifts.addAll(d.list("lifts").map { l -> EditLift(l.s("id"), l.s("name"), l.list("sets").map { st -> EditSet(st.s("w"), st.s("r"), st.s("rir"), st.b("sub")) }) })
    }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val d = draft ?: run { Text("Loading…"); return@Column }
            val u = d.s("unit")
            Text("Correct workout log", style = MaterialTheme.typography.headlineSmall)
            Text(d.s("dayLabel"), color = cs.onSurfaceVariant)
            Text("Fix what you actually performed. Program, day and week stay locked so correcting a number cannot move the workout to a different place in the plan.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dateText, { dateText = it.take(16) }, Modifier.weight(1f), singleLine = true, label = { Text("Date (YYYY-MM-DD HH:MM)") })
                OutlinedTextField(minutes, { minutes = it.filter { c -> c.isDigit() }.take(3) }, Modifier.width(100.dp), singleLine = true, label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            lifts.toList().forEach { lf ->
                PiCard(color = cs.surfaceContainer, radius = 20.dp, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lf.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { lifts.remove(lf) }) { Text("Remove lift", color = cs.error) }
                    }
                    lf.sets.toList().forEachIndexed { k, st ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (st.sub) "↳" else "${k + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(20.dp))
                            OutlinedTextField(st.w, { st.w = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, Modifier.weight(1f), singleLine = true, label = { Text(u) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(st.r, { st.r = it.filter { c -> c.isDigit() }.take(3) }, Modifier.weight(0.8f), singleLine = true, label = { Text("Reps") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(st.rir, { st.rir = it.filter { c -> c.isDigit() || c == '.' }.take(3) }, Modifier.weight(0.7f), singleLine = true, label = { Text("RIR") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            IconButton(onClick = { lf.sets.remove(st); if (lf.sets.isEmpty()) lifts.remove(lf) }) { Icon(PiIcons.Close, "Remove set ${k + 1}", tint = cs.error) }
                        }
                    }
                    TextButton(onClick = { val last = lf.sets.lastOrNull(); lf.sets.add(EditSet(last?.w ?: "", last?.r ?: "", "", false)) }) { Text("+ Add set") }
                }
            }
            if (lifts.isEmpty()) Text("No sets remain. Delete the whole workout instead if this session should not exist.", color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
                Button(onClick = {
                    val date = runCatching { fmt.parse(dateText)?.time }.getOrNull() ?: d.optLong("date")
                    val arr = JSONArray()
                    lifts.forEach { lf -> arr.put(JSONObject().put("id", lf.id).put("sets", JSONArray().apply { lf.sets.forEach { st -> put(JSONObject().put("w", st.w).put("r", st.r).put("rir", st.rir).put("sub", st.sub)) } })) }
                    vm.editHistory(id, date, minutes.toIntOrNull(), arr); onClose()
                }, enabled = lifts.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp)) { Text("Save correction") }
            }
        }
    }
}

/** App.js shareSession → drawRecapCard: a 1080×1350 image of the workout, shared through the system sheet. */
fun shareWorkoutImage(context: Context, card: JSONObject, cs: ColorScheme) {
    val W = 1080; val H = 1350
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(cs.surface.toArgb())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, maxW: Float = W - 2 * 80f) {
        p.color = color; p.textSize = size; p.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        var t = s
        while (t.length > 1 && p.measureText(t) > maxW) t = t.dropLast(2) + "…"
        c.drawText(t, x, y, p)
    }
    val u = card.s("unit", "kg")
    text("PURSUIT IRON", 80f, 130f, 34f, cs.primary.toArgb(), true)
    text(card.s("label"), 80f, 215f, 76f, cs.onSurface.toArgb(), true)
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(card.optLong("date")))
    text(listOf(card.s("programName"), date).filter { it.isNotBlank() }.joinToString(" · "), 80f, 275f, 34f, cs.onSurfaceVariant.toArgb())
    val tiles = listOf("Sets" to "${card.i("doneSets")}", "Volume" to "${Math.round(card.d("volume"))} $u", "Time" to "${card.i("durMin").coerceAtLeast(1)}m")
    val tw = (W - 160f - 2 * 24f) / 3
    tiles.forEachIndexed { k, (label, v) ->
        val x0 = 80f + k * (tw + 24f)
        p.color = cs.surfaceContainerHigh.toArgb(); c.drawRoundRect(RectF(x0, 330f, x0 + tw, 500f), 36f, 36f, p)
        text(v, x0 + 30f, 420f, 56f, cs.primary.toArgb(), true, tw - 60f)
        text(label, x0 + 30f, 470f, 30f, cs.onSurfaceVariant.toArgb(), false, tw - 60f)
    }
    var y = 580f
    card.o("mainE1rm")?.let { m ->
        text("ESTIMATED 1RM · ${m.s("name").uppercase()}", 80f, y, 30f, cs.onSurfaceVariant.toArgb(), true); y += 80f
        text("${m.i("est")} $u", 80f, y, 72f, cs.primary.toArgb(), true); y += 60f
    }
    val prs = card.strings("prs")
    if (prs.isNotEmpty()) { y += 20f; text("${prs.size} PR${if (prs.size == 1) "" else "s"}: ${prs.joinToString(", ")}", 80f, y, 34f, cs.tertiary.toArgb(), true); y += 50f }
    y += 30f
    card.list("tops").forEach { t ->
        if (y > H - 120f) return@forEach
        text(t.s("name"), 80f, y, 38f, cs.onSurface.toArgb(), false, W - 160f - 260f)
        p.textSize = 38f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.color = cs.onSurface.toArgb()
        c.drawText(t.s("set"), W - 80f - p.measureText(t.s("set")), y, p)
        y += 62f
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, "pursuit-iron-workout.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share workout").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}


/* The design opens strength standards from the strength score on the profile, full height. */
@Composable
fun StrengthStandardsSheet(vm: AppViewModel, onClose: () -> Unit) {
    var liftId by remember { mutableStateOf<String?>(null) }
    liftId?.let { id -> LiftDetailSheet(vm, id) { liftId = null } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StrengthStandardsCard(vm) { liftId = it }
        }
    }
}
