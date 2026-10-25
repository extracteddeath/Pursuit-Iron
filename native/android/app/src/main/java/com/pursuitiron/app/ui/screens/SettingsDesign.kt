@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.BuildConfig
import com.pursuitiron.app.ui.common.Hap
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.ThemePicker
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import java.util.Locale

/* The design's You → Settings (Main.dc.html profSettings): the everyday controls as one grouped list,
   every other page one row away, the banned list, and the e1RM calculator. */

/** The design's compact pill toggle (kg | lb, cm | in, Short | Standard | Long). */
@Composable
fun PillToggle(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, label: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHighest).padding(3.dp).semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (id, l) ->
            val on = id == selected
            val bg by animateColorAsState(if (on) cs.secondaryContainer else Color.Transparent, label = "pill")
            Box(Modifier.height(32.dp).clip(CircleShape).background(bg).clickable { if (!on) onSelect(id) }.semantics { role = Role.RadioButton; this.selected = on }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GroupRow(title: String, sub: String?, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant) }
        }
        trailing()
    }
}

private fun mmss(sec: Int) = "${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}"

@Composable
fun SettingsGroup(vm: AppViewModel, onGyms: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = vm.settings
    var confirmUnit by remember { mutableStateOf<String?>(null) }
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        vm.gyms?.let { g ->
            val active = g.list("gyms").firstOrNull { it.s("id") == g.s("activeGymId") }
            GroupRow("Gyms", active?.let { gymMeta(it) }) {
                Row(Modifier.height(40.dp).clip(CircleShape).background(cs.secondaryContainer).clickable(onClick = onGyms)
                    .semantics(mergeDescendants = true) { contentDescription = "Manage gyms"; role = Role.Button }.padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(active?.s("name") ?: "Gyms", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                    Icon(PiIcons.ChevronRight, null, Modifier.size(16.dp), tint = cs.onSecondaryContainer)
                }
            }
        }
        val darkNow = vm.prefs.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Theme", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text(when (vm.prefs.dark) { null -> "Follows your phone · " + (if (darkNow) "dark" else "light") + " right now"; true -> "Always dark"; false -> "Always light" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
            }
            Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), when (vm.prefs.dark) { null -> "system"; true -> "dark"; false -> "light" },
                { vm.prefs.changeDark(when (it) { "dark" -> true; "light" -> false; else -> null }) }, height = 40.dp, track = cs.surfaceContainerHighest, gap = 3.dp)
        }
        if (darkNow) GroupRow("Pure black", "True black behind everything. Easier on OLED screens and the battery") {
            Switch(vm.prefs.black, { vm.prefs.changeBlack(it) }, Modifier.semantics { contentDescription = "Pure black" })
        }
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("Color", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text(if (vm.prefs.seed.isDynamic) "Wallpaper · matches your wallpaper on Android 12 and up" else vm.prefs.seed.label + " · every theme works light and dark",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
            }
            ThemePicker(vm.prefs.seed, darkNow, { vm.prefs.changeSeed(it) }, { vm.toast(it) })
        }
        GroupRow("Units", "Loads, plates and history") {
            PillToggle(listOf("kg" to "kg", "lb" to "lb"), st.s("unit", "kg"), { confirmUnit = it }, "Units")
        }
        val lu = vm.body.s("lengthUnit", if (st.s("unit") == "lb") "in" else "cm")
        GroupRow("Body measurements", "Waist, chest, arms and thighs") {
            PillToggle(listOf("cm" to "cm", "in" to "in"), lu, { vm.setLengthUnit(it) }, "Measurement unit")
        }
        val scale = st.d("restScale", 1.0)
        val sel = when { scale < 0.9 -> "0.75"; scale > 1.1 -> "1.25"; else -> "1.0" }
        GroupRow("Rest length", "Compound ${mmss((135 * sel.toDouble()).toInt())} · isolation ${mmss((75 * sel.toDouble()).toInt())} · lifts you set keep their own") {
            PillToggle(listOf("0.75" to "Short", "1.0" to "Standard", "1.25" to "Long"), sel, { vm.updateSetting("restScale", it.toDouble()) }, "Rest length")
        }
    }
    confirmUnit?.let { to ->
        AlertDialog(onDismissRequest = { confirmUnit = null },
            title = { Text("Switch to $to?") },
            text = { Text("Your programs, training maxes, bodyweight and measurements convert to $to. Past workouts keep the unit they were logged in.") },
            confirmButton = { TextButton(onClick = { vm.updateSetting("unit", to); confirmUnit = null }) { Text("Switch") } },
            dismissButton = { TextButton(onClick = { confirmUnit = null }) { Text("Cancel") } })
    }
}

private data class SetRow(val key: String, val title: String, val sub: String, val icon: ImageVector, val bg: Color, val fg: Color)

private val ICON_PREFS = PiIcons.icon("set-prefs", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
private val ICON_BELL = PiIcons.icon("set-bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0")
private val ICON_DB = PiIcons.icon("set-db", "M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6")
private val ICON_COMFORT = PiIcons.icon("set-comfort", "M12 4a2 2 0 1 0 0 .01M5 8l7 2 7-2M12 10v5l-3 6M12 15l3 6")
private val ICON_CUES = PiIcons.icon("set-cues", "M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7")
private val ICON_CUES_TEST = PiIcons.icon("cue-test", "M11 5 6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14")
private val ICON_BACKUP = PiIcons.icon("set-backup", "M12 3v12M7 10l5 5 5-5M4 19h16")

@Composable
fun SettingsRows(vm: AppViewModel, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = vm.settings
    val exp = mapOf("none" to "New to lifting", "novice" to "New to lifting", "beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced")[st.s("experience")] ?: "Intermediate"
    val rem = st.o("reminders")
    val wd = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val remSub = if (rem?.b("enabled") == true) {
        val (h, m) = rem.s("time", "18:00").split(":").map { it.toIntOrNull() ?: 0 }
        val days = rem.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty().sortedBy { (it + 6) % 7 }
        "${if (h % 12 == 0) 12 else h % 12}:${String.format(Locale.US, "%02d", m)} ${if (h < 12) "am" else "pm"} on ${days.joinToString(", ") { wd[it] }}"
    } else "Off"
    val inj = st.o("injuries")
    val rows = listOf(
        SetRow("setup", "Guided setup", "A few questions → a program built for you", PiIcons.Sparkle, cs.primaryContainer, cs.onPrimaryContainer),
        SetRow("backup", "Backup & restore", "${st.i("sessions")} sessions · save a file or bring one back", ICON_BACKUP, cs.secondaryContainer, cs.onSecondaryContainer),
        SetRow("whatsnew", "What’s new", "v${BuildConfig.VERSION_NAME}", PiIcons.Sparkle, cs.tertiaryContainer, cs.onTertiaryContainer),
        SetRow("loading", "Plates & bars", "The plates you own and your bars", PiIcons.Library, cs.secondaryContainer, cs.onSecondaryContainer),
        SetRow("exercises", "Your exercises", if (vm.customs.isEmpty()) "Create lifts that work everywhere" else "${vm.customs.size} custom lift${if (vm.customs.size == 1) "" else "s"}", PiIcons.Plus, cs.primaryContainer, cs.onPrimaryContainer),
        SetRow("comfort", "Comfort & accessibility", "${mapOf(1f to "Default", 1.1f to "Large", 1.2f to "Larger")[vm.prefs.zoom] ?: "Default"} text · haptics ${if (st.b("haptics", true)) "on" else "off"}", ICON_COMFORT, cs.tertiaryContainer, cs.onTertiaryContainer),
        SetRow("cues", "Rest timer & cues", "${mapOf("off" to "No sound", "tones" to "Tones", "voice" to "Voice")[vm.prefs.cues] ?: "Tones"} · ${if (vm.prefs.duck) "music lowers" else "music pauses"}", ICON_CUES, cs.primaryContainer, cs.onPrimaryContainer),
        SetRow("training", "Training preferences", "$exp · effort as ${if (st.s("loadMode") == "rpe") "RPE" else "reps in reserve"}", ICON_PREFS, cs.primaryContainer, cs.onPrimaryContainer),
        SetRow("reminders", "Reminders", remSub, ICON_BELL, cs.secondaryContainer, cs.onSecondaryContainer),
        SetRow("injury", "Injury log", "${if ((inj?.i("active") ?: 0) > 0) "${inj!!.i("active")} active" else "Nothing active"} · ${inj?.i("resolved") ?: 0} resolved", PiIcons.Shield, cs.tertiaryContainer, cs.onTertiaryContainer),
        SetRow("data", "Data & storage", "CSV export, import from Strong or Hevy, clear or reset", ICON_DB, cs.secondaryContainer, cs.onSecondaryContainer),
        SetRow("help", "Help & about", "Report a bug, privacy, the research behind it", PiIcons.Help, cs.surfaceContainerHighest, cs.onSurface),
    )
    Column {
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { onOpen(r.key) }.semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(r.bg), contentAlignment = Alignment.Center) { Icon(r.icon, null, Modifier.size(20.dp), tint = r.fg) }
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                    Text(r.sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                }
                Icon(PiIcons.ChevronRight, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            }
        }
    }
    if (!vm.rest.exactAllowed()) {
        val ctx = LocalContext.current
        Column(Modifier.padding(top = 16.dp).clip(RoundedCornerShape(24.dp))) {
            MenuRow(PiIcons.Timer, "Rest alert on the second", "Let Pursuit Iron use exact alarms so the rest-over alert isn't late with the screen off", cs.tertiaryContainer, cs.onTertiaryContainer) {
                if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
            }
        }
    }
    }
}

@Composable
fun BannedSection(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val banned = vm.settings.list("banned")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Banned exercises", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() })
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (banned.isEmpty()) Text("None. Ban a lift from its detail sheet and it never comes back in swaps.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(16.dp))
            banned.forEach { b ->
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(b.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Box(Modifier.height(40.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { vm.setBan(b.s("id"), false) }
                        .semantics { contentDescription = "Unban ${b.s("name")}"; role = Role.Button }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Unban", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

/** The design's e1RM calculator: load, reps and reps in reserve, and the loads for common rep targets. */
@Composable
fun E1rmCalcCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val u = vm.settings.s("unit", "kg")
    val lb = u == "lb"
    var load by remember(u) { mutableFloatStateOf(if (lb) 225f else 100f) }
    var reps by remember { mutableIntStateOf(5) }
    var rir by remember { mutableIntStateOf(1) }
    val e1 = load * (1 + (reps + rir) / 30.0)
    val inc = if (lb) 5.0 else 2.5
    val snap = { v: Double, s: Double -> Math.round(v / s) * s }
    val sliderColors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("e1RM calculator", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Estimated 1RM", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f).padding(bottom = 8.dp))
                AnimatedContent(fmtNum(snap(e1, 0.5)), transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "e1") {
                    Text(it, style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp))
                }
                Text(" $u", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("Load · ${fmtNum(load.toDouble())} $u", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            val (mn, mx) = if (lb) 45f to 550f else 20f to 250f
            Slider(load, { load = (snap(it.toDouble(), inc)).toFloat() }, valueRange = mn..mx, colors = sliderColors, modifier = Modifier.semantics { contentDescription = "Load in $u" })
            Text("Reps · $reps", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Slider(reps.toFloat(), { reps = Math.round(it) }, valueRange = 1f..15f, steps = 13, colors = sliderColors, modifier = Modifier.semantics { contentDescription = "Reps" })
            Text("Reps in reserve · $rir", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            Slider(rir.toFloat(), { rir = Math.round(it) }, valueRange = 0f..4f, steps = 3, colors = sliderColors, modifier = Modifier.semantics { contentDescription = "Reps in reserve" })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(95 to "2–3", 85 to "5–6", 75 to "8–10", 65 to "12–15").forEach { (p, r) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$p%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
                        Text(fmtNum(snap(e1 * p / 100, inc)), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        Text("$r reps", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** The design's Comfort & accessibility panel. */
@Composable
fun ComfortSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val st = vm.settings
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comfort & accessibility", style = MaterialTheme.typography.headlineSmall)
            Text("TEXT SIZE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            Segmented(listOf("1.0" to "Default", "1.1" to "Large", "1.2" to "Larger"), vm.prefs.zoom.toString(), { vm.prefs.changeZoom(it.toFloat()) })
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(14.dp)) {
                Text("Bench press", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Text("4 × 6–8 @2 RIR · Last 80×8", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
            }
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ComfortRow("Haptics", "Taps when you log a set, hit a PR or rest ends", st.b("haptics", true)) { vm.updateSetting("haptics", it); if (it) vm.hap(Hap.Log) }
                ComfortRow("Keep screen on", "Stays awake while a workout is open", vm.prefs.keepAwake) { vm.prefs.changeKeepAwake(it) }
                ComfortRow("Grip mode", "Bigger set controls and stronger taps for sweaty hands", vm.prefs.grip) { vm.prefs.changeGrip(it) }
                ComfortRow("Auto-start rest timer", if (st.b("restAutoStart", true)) "Rest starts by itself after each set" else "Rest waits paused after a set · tap ▶ to start it", st.b("restAutoStart", true)) { vm.updateSetting("restAutoStart", it) }
                ComfortRow("Weight & reps steppers", if (vm.prefs.steppers) "−/+ beside each value · hold to run it up" else "Tap a value to edit it · turn on for −/+ beside each value", vm.prefs.steppers) { vm.prefs.changeSteppers(it) }
            }
            Text("HAPTIC PATTERNS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(Triple("Set logged", "One short tap", Hap.Log), Triple("New PR", "Two doubles and a long", Hap.Pr), Triple("Rest over", "Long, gap, long", Hap.Rest),
                    Triple("Warning", "Two firm pulses", Hap.Warn), Triple("Undo", "Two light ticks", Hap.Undo), Triple("Workout saved", "A rising triple", Hap.Done)).forEach { (l, sub, h) ->
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            h.pattern.forEachIndexed { k, v ->
                                if (k % 2 == 0) Box(Modifier.width((v / 5).coerceAtLeast(3L).toInt().dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(cs.primary))
                                else Box(Modifier.width((v / 10).coerceAtLeast(2L).toInt().dp).height(2.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(l, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Box(Modifier.height(36.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { vm.hap(h) }.semantics { contentDescription = "Feel the $l pattern"; role = Role.Button }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Feel it", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComfortRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked, onChange, Modifier.semantics { contentDescription = label })
    }
}

@Composable
fun WhatsNewSheet(vm: AppViewModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { vm.prefs.markWhatsNew(WHATS_NEW_VERSION); onClose() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What’s new in v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            WhatsNewList()
        }
    }
}


/** The design's Rest timer & cues panel. */
@Composable
fun CuesSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Rest timer & cues", style = MaterialTheme.typography.headlineSmall)
            Text("WHEN REST ENDS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            Segmented(listOf("off" to "Off", "tones" to "Tones", "voice" to "Voice"), vm.prefs.cues, { vm.prefs.changeCues(it) })
            Text(when (vm.prefs.cues) { "off" -> "Only the vibration when rest ends."; "voice" -> "Says \"Rest over\" and the next exercise at zero."; else -> "Three short beeps from 3 seconds, then a higher one at zero." },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
            Column(Modifier.clip(RoundedCornerShape(20.dp))) {
                ComfortRow("Lower music instead of pausing", "Music dips for the cue, then comes straight back", vm.prefs.duck) { vm.prefs.changeDuck(it) }
            }
            Row(Modifier.height(44.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { vm.testCue() }.semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(ICON_CUES_TEST, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
                Text("Test the cue", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
            }
        }
    }
}
