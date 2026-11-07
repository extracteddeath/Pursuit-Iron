@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONArray
import org.json.JSONObject

/* The design's Create wizard (Main.dc.html wz, web: App.js builder): one question a screen, a progress bar, Back
   and Next, a live "Your setup" summary, and "Generate with these settings" once the essentials are answered.
   From the guided setup it also asks units, experience and equipment; from the Plan it uses the active gym. */

private val WZ = object {
    val trophy = PiIcons.icon("wz-trophy", "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4")
    val cal = PiIcons.icon("wz-cal", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4")
    val clock = PiIcons.icon("wz-clock", "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z")
    val rep = PiIcons.icon("wz-rep", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val layers = PiIcons.icon("wz-layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val flame = PiIcons.icon("wz-flame", "M12 22c4 0 7-3 7-7 0-5-5-7-5-12-3 2-6 6-6 9-1-1-2-2-2-4-1 2-1 4-1 6 0 5 3 8 7 8z")
    val zap = PiIcons.icon("wz-zap", "M13 2 4 14h7l-1 8 9-12h-7z")
    val wand = PiIcons.icon("wz-wand", "M15 4V2M15 10V8M11 6h2M17 6h2M4 20 14 10")
    val pencil = PiIcons.icon("wz-pencil", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val battery = PiIcons.icon("wz-battery", "M3 8h15v8H3zM21 11v2M6 11v2")
    val dumb = PiIcons.icon("wz-dumb", "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11")
}

private val SESS = listOf("s20" to "Up to 20 minutes", "s40" to "20 to 40 minutes", "s60" to "40 to 60 minutes", "s90" to "60 to 90 minutes", "s120" to "90 to 120 minutes", "s120p" to "Over 120 minutes")
private val PRESETS = listOf(
    "Full gym" to listOf("barbell", "dumbbell", "bench", "cable", "machine", "smith", "ezbar", "pullup", "dip", "kettlebell", "bands"),
    "Barbell + rack" to listOf("barbell", "bench", "ezbar", "pullup"),
    "Dumbbells + bench" to listOf("dumbbell", "bench", "pullup"),
    "Dumbbells only" to listOf("dumbbell"),
    "Bodyweight" to emptyList(),
)
private val EXP = listOf(Triple("none", "New to lifting", "Brand new to lifting"), Triple("beginner", "Beginner", "< 1 year training"), Triple("intermediate", "Intermediate", "1–3 years training"), Triple("advanced", "Advanced", "3+ years, dialled-in"))
private val GOALS = mapOf("hypertrophy" to "Hypertrophy", "strength" to "Strength", "both" to "Strength & Size")
private val SESS_SHORT = mapOf("s20" to "≤20 min", "s40" to "20–40", "s60" to "40–60", "s90" to "60–90", "s120" to "90–120", "s120p" to "120+")
private val SES_MIN = mapOf("s20" to 20, "s40" to 40, "s60" to 60, "s90" to 90, "s120" to 120, "s120p" to 150)
private val SUBHEAD = mapOf("approach" to "TRAINING APPROACH", "session" to "SESSION LENGTH", "constraints" to "GYM LIMITS", "name" to "NAME",
    "units" to "UNITS", "progression" to "PROGRESSION", "length" to "BLOCK LENGTH", "deload" to "DELOAD WEEK")

/** An optional group on a setup page: its title and current answer, opened in place. */
@Composable
private fun WzFold(title: String, summary: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.semantics(mergeDescendants = true) { role = Role.Button; stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(if (open) PiIcons.ChevronUp else PiIcons.ChevronDown, null, tint = cs.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = Motion.expandIn, exit = Motion.shrinkOut) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/**
 * The prototype's ranked splits (Main.dc.html builder + guided setup result), on the real engine: every split this gym can
 * run at these days is built with your answers and scored (core rankSplit). The list fills in as each one is scored;
 * the hero card is the best fit (or your pick), with its score, why, the four measures and its week; the rest are ranked
 * below with score bars; splits the gym can't run are listed last with what they need.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RankedSplits(vm: AppViewModel, config: JSONObject, chosen: String?, onPick: (String) -> Unit, onTop: (String?) -> Unit, goalLabel: String, cyc: Boolean) {
    val cs = MaterialTheme.colorScheme
    val cfgKey = config.toString()
    var plan by remember(cfgKey) { mutableStateOf<JSONObject?>(null) }
    val rows = remember(cfgKey) { androidx.compose.runtime.mutableStateListOf<JSONObject>() }
    var done by remember(cfgKey) { mutableIntStateOf(0) }
    LaunchedEffect(cfgKey) {
        val pl = vm.rankSplitsPlan(config) ?: return@LaunchedEffect
        plan = pl
        val ids = pl.strings("ids")
        ids.forEach { id ->
            vm.rankSplit(config, id)?.takeIf { !it.b("failed") }?.let { rows.add(it) }
            done++
            onTop(rows.maxWithOrNull(compareBy<JSONObject>({ it.optDouble("raw") }, { if (it.s("id") == pl.s("enginePick")) 1 else 0 }))?.s("id"))
        }
    }
    val total = plan?.strings("ids")?.size ?: 0
    val ranked = rows.sortedWith(compareByDescending<JSONObject> { it.optDouble("raw") }.thenByDescending { if (it.s("id") == plan?.s("enginePick")) 1 else 0 }.thenBy { it.s("name") })
    val scoring = plan == null || done < total
    if (scoring) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) {
        Text(if (plan == null) "Finding the splits that fit…" else "Building and scoring ${minOf(done + 1, total)} of $total splits…", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        // M3 Expressive wavy progress: it ripples while the splits are being built, then settles flat.
        @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
        androidx.compose.material3.LinearWavyProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
    }
    if (ranked.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
            androidx.compose.material3.ContainedLoadingIndicator(Modifier.size(56.dp))
        }
        return
    }
    val top = ranked.first()
    val hero = ranked.firstOrNull { it.s("id") == chosen } ?: top
    val heroRank = ranked.indexOf(hero) + 1
    // Hero: the best fit, or the split you picked
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val sc by androidx.compose.animation.core.animateFloatAsState(hero.i("score") / 100f, Motion.data(), label = "sc")
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                // M3 Expressive wavy ring: the score as a determinate wavy progress indicator.
                @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
                androidx.compose.material3.CircularWavyProgressIndicator(progress = { sc }, modifier = Modifier.fillMaxSize(),
                    color = cs.primary, trackColor = cs.onPrimaryContainer.copy(alpha = 0.15f))
                Text("${hero.i("score")}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = cs.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(if (hero === top) "BEST FIT OF ${ranked.size}" else "YOUR PICK · #$heroRank OF ${ranked.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp), color = cs.primary)
                Text(hero.s("name"), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp), color = cs.onPrimaryContainer)
                Text("${plan?.i("days") ?: hero.i("sessions")} days · ${SESS_SHORT[hero.s("session")] ?: ""} min · $goalLabel" + if (hero.s("id") == plan?.s("enginePick")) " · our default pick" else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
        // The four measures behind the score
        val parts = hero.o("parts")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("coverage" to "Muscles", "frequency" to "How often", "fit" to "Time", "goal" to "Goal").forEach { (k, l) ->
                val v = parts?.optInt(k) ?: 0
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$l $v of 100" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(l, style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("$v", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f), maxLines = 1)
                    }
                    com.pursuitiron.app.ui.common.ProgressBar(v / 100f, Modifier.fillMaxWidth(), height = 6.dp, color = cs.primary)
                }
            }
        }
        // Three reasons up front; the rest on request, so the week strip stays in view without scrolling.
        var allWhy by remember(hero.s("id")) { mutableStateOf(false) }
        val why = hero.strings("why")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (allWhy) why else why.take(3)).forEach { w -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(cs.primary))
                Text(w, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp), color = cs.onPrimaryContainer)
            } }
            if (why.size > 3) Text(if (allWhy) "Fewer reasons" else "${why.size - 3} more reason${if (why.size - 3 == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge, color = cs.primary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { allWhy = !allWhy }.semantics { role = Role.Button }.heightIn(min = 40.dp).padding(horizontal = 4.dp).wrapContentHeight())
        }
        if (hero.b("sessionMoved")) Text("Runs at ${SESS_SHORT[hero.s("session")]} min sessions; your length doesn’t fit it.", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
        // The week it lays out
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            hero.list("week").forEach { w ->
                val on = w.b("on")
                Column(Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).background(if (on) cs.surface else cs.onPrimaryContainer.copy(alpha = 0.06f)).padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    Text(w.s("wd"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(w.s("name"), style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp, lineHeight = 12.sp),
                        color = if (on) cs.onSurface else cs.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
    // Every split, ranked
    WzKicker("ALL ${ranked.size} SPLITS, RANKED" + if (scoring) " · SCORING…" else "", Modifier.padding(top = 10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ranked.forEachIndexed { k, x ->
            val on = x.s("id") == hero.s("id")
            val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "rk")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bg).border(if (on) 2.dp else 0.dp, if (on) cs.primary else Color.Transparent, RoundedCornerShape(20.dp))
                .clickable { onPick(x.s("id")) }.semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on; contentDescription = "Rank ${k + 1}: ${x.s("name")}, score ${x.i("score")}. ${x.s("tagline")}. ${x.s("line")}" }
                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).clip(if (k < 3) RoundedCornerShape(11.dp) else CircleShape).background(if (k == 0) cs.primary else if (k < 3) cs.primaryContainer else cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Text("${k + 1}", style = MaterialTheme.typography.labelLarge, color = if (k == 0) cs.onPrimary else if (k < 3) cs.onPrimaryContainer else cs.onSurface)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${x.i("score")}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (k == 0) cs.primary else cs.onSurface)
                    }
                    val w by androidx.compose.animation.core.animateFloatAsState(x.i("score") / 100f, Motion.data(), label = "rw")
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(w).fillMaxHeight().clip(CircleShape).background(if (k == 0) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.45f)))
                    }
                    // What the split is, then its numbers: several methods share a first week, so the tagline is what tells them apart.
                    x.s("tagline").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp), color = cs.onSurface, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    Text(x.s("line") + if (x.list("under").isNotEmpty()) " · low: " + x.strings("under").joinToString(", ") else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
    val blocked = plan?.list("blocked").orEmpty()
    if (blocked.isNotEmpty()) WzFold("${blocked.size} can’t run with your gym", "Add equipment in your gym to open ${if (blocked.size == 1) "it" else "them"} up") {
        blocked.forEach { b -> Column {
            Text(b.s("name"), style = MaterialTheme.typography.titleSmall)
            Text(if (b.s("kind") == "lifts") "Needs equipment for: " + b.strings("items").joinToString(", ") else "Not enough exercises with your equipment" + (b.strings("items").takeIf { it.isNotEmpty() }?.let { " for ${it.joinToString(", ")}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        } }
    }
    Text("Each split is actually built from your answers, then scored on how well it covers every muscle, how often each one is trained, how well sessions fit your time, and how much of the work suits your goal.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
}

/* B894: an unfinished cycle setup stays on this device; Discard starts the wizard fresh. */
@Composable
fun SetupScreen(vm: AppViewModel) {
    var gen by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.key(gen) { SetupBody(vm) { vm.prefs.putSetupDraft(null); gen++; vm.toast("Setup discarded") } }
}

@Composable
private fun SetupBody(vm: AppViewModel, onDiscard: () -> Unit) {
    val opts = vm.setup
    val cs = MaterialTheme.colorScheme
    val full = vm.setupFull
    val activeGym = vm.gyms?.let { g -> g.list("gyms").firstOrNull { it.s("id") == g.s("activeGymId") } }
    val gymEquip = activeGym?.optJSONArray("equipment")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.filter { it != "bodyweight" } ?: PRESETS[0].second
    // A recovered cycle setup (not for the guided setup or a template pick, which start fresh)
    val d0 = remember { if (full || vm.setupMode == "template") null else vm.prefs.setupDraft?.let { runCatching { JSONObject(it) }.getOrNull() } }
    // Answers
    var chose by remember { mutableStateOf(d0.b("chose")) }
    var cyc by remember { mutableStateOf(d0 != null || vm.setupMode == "cycle") }
    var seen by remember { mutableStateOf(d0 != null) }
    var tplOpen by remember { mutableStateOf(vm.setupMode == "template") }
    var step by remember { mutableIntStateOf(d0.i("step")) }
    var unit by remember { mutableStateOf(d0.s("unit").ifBlank { vm.settings.s("unit", "kg") }) }
    var name by remember { mutableStateOf(d0.s("name")) }
    var exp by remember { mutableStateOf(d0.s("experience").ifBlank { vm.settings.s("experience").let { if (it == "novice") "none" else it }.ifBlank { "intermediate" } }) }
    var goal by remember { mutableStateOf(d0.s("goal", "hypertrophy")) }
    var approach by remember { mutableStateOf(d0.s("approach", "standard")) }
    var days by remember { mutableIntStateOf(d0.i("days", 4)) }
    var split by remember { mutableStateOf(d0.s("split").ifBlank { null }) }
    var session by remember { mutableStateOf(d0.s("session", "s60")) }
    var equip by remember { mutableStateOf(if (full) PRESETS[0].second.toSet() else gymEquip.toSet()) }
    var noRack by remember { mutableStateOf(d0.b("noRack")) }
    var noBodyweight by remember { mutableStateOf(d0.b("noBodyweight")) }
    var noSupersets by remember { mutableStateOf(d0.b("noSupersets")) }
    var barbellCap by remember { mutableStateOf(d0?.takeIf { it.has("barbellCap") && !it.isNull("barbellCap") }?.optInt("barbellCap")) }
    val focus = remember { mutableStateMapOf<String, Int>().apply { d0.o("focus")?.let { f -> f.keys().forEach { k -> put(k, f.optInt(k)) } } } }
    val reduce = remember { mutableStateListOf<String>().apply { addAll(d0.strings("reduce")) } }
    var progression by remember { mutableStateOf("auto") }
    var weeks by remember { mutableIntStateOf(4) }
    var endless by remember { mutableStateOf(false) }
    var deload by remember { mutableStateOf(d0.b("deload")) }
    var template by remember { mutableStateOf(d0.s("template").ifBlank { vm.cycles.list("templates").firstOrNull()?.s("id") ?: "powerbuilding" }) }
    val custom = remember { mutableStateListOf<CustomBlock>().apply {
        val saved = d0.list("custom").map { customBlockOf(it) }
        addAll(saved.ifEmpty { listOf(CustomBlock("hypertrophy_accumulation", 6), CustomBlock("strength_accumulation", 5), CustomBlock("peak", 3)) }) } }
    var adapt by remember { mutableStateOf(d0.b("adapt")) }
    var adaptSplit by remember { mutableStateOf(d0?.optBoolean("adaptSplit", true) ?: true) }
    // Every answer of an unfinished cycle setup is written as it changes, so closing or a restart picks it up again
    val draftJson = if (full || !cyc || !seen) null else JSONObject().put("chose", chose).put("step", step).put("unit", unit).put("name", name).put("experience", exp)
        .put("goal", goal).put("approach", approach).put("days", days).put("split", split ?: "").put("session", session).put("noRack", noRack)
        .put("noBodyweight", noBodyweight).put("noSupersets", noSupersets).put("barbellCap", barbellCap ?: JSONObject.NULL)
        .put("focus", JSONObject().apply { focus.forEach { (k, v) -> put(k, v) } }).put("reduce", JSONArray(reduce.toList())).put("deload", deload)
        .put("template", template).put("custom", customBlocksJson(custom)).put("adapt", adapt).put("adaptSplit", adaptSplit).toString()
    LaunchedEffect(draftJson) { if (draftJson != null) vm.prefs.putSetupDraft(draftJson) else if (seen && !cyc) vm.prefs.putSetupDraft(null) }
    var revOpen by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }

    // Steps, as the design orders them
    val sk = listOf("units", "name", "experience", "goal", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "progression", "length", "deload")
    val ck = listOf("cycletype", "units", "name", "experience", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "deload")
    var keys = if (cyc) (if (template == "custom") listOf("cycletype", "goal") + ck.drop(1) else ck) else sk
    if (!full) keys = keys.filter { it != "units" && it != "experience" && it != "equipment" }
    if (endless && !cyc) keys = keys.filter { it != "deload" }
    /* The prototype's flow (Main.dc.html guided setup): a few questions a screen, ending on every split built and ranked
       for you. Every option the long wizard had is still here, grouped onto the page it belongs to. */
    val pageOf = listOf(listOf("cycletype"), listOf("goal", "approach"), listOf("experience"), listOf("days", "session"), listOf("equipment", "constraints"),
        listOf("focus", "reduce"), listOf("split", "name", "units", "progression", "length", "deload"))
    val steps: List<List<String>> = if (chose) pageOf.map { pg -> pg.filter { it in keys } }.filter { it.isNotEmpty() } else listOf(listOf("mode"))
    val at = step.coerceIn(0, steps.size - 1)
    val page = steps[at]
    val key = page.first()
    val pageIdx = { k: String -> steps.indexOfFirst { k in it } }

    val eq = (if (full) equip else gymEquip.toSet()).let { if (noRack) it + "no-rack" else it }
    val focusTotal = focus.values.sum()
    fun config(): JSONObject = JSONObject()
        .put("unit", unit).put("goal", goal).put("experience", exp).put("days", days).put("session", session)
        .put("equipment", JSONArray(eq.toList())).put("volumeApproach", approach)
        .put("noBodyweight", noBodyweight).put("noSupersets", noSupersets).put("barbellCap", barbellCap ?: JSONObject.NULL)
        .put("focus", JSONObject().apply { focus.forEach { (k, v) -> if (v > 0) put(k, v) } })
        .put("reduce", JSONArray(reduce.filter { (focus[it] ?: 0) == 0 }))
        .apply { if (name.isNotBlank()) put("name", name.trim().take(60)) }
        .apply { if (!cyc) { if (endless) put("endless", true).put("weeks", 5).put("deload", true).put("progression", "auto") else put("weeks", weeks).put("deload", deload).put("progression", progression) } else put("deload", deload) }
    // What the ranking is built from: the training answers only. Name, block length, deload and progression sit on the
    // ranked page itself, and typing a name must not throw away and rebuild every split's score.
    fun rankConfig(): JSONObject = config().apply { listOf("name", "weeks", "deload", "progression", "endless").forEach { remove(it) } }
    LaunchedEffect(goal, exp, days, session, equip, noRack) { pick = vm.recommend(config()) }
    var avail by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(goal, exp, days, session, equip, noRack, noBodyweight) { avail = vm.splitOptions(config()) }
    val splitOk = { id: String? -> id != null && (avail?.optJSONObject(id)?.optBoolean("ok", true) ?: true) }

    val sessIdx = { id: String? -> SESS.indexOfFirst { it.first == id } }
    val splits = opts.list("splits").filter { sp -> sp.optJSONArray("days")?.let { a -> (0 until a.length()).any { a.optInt(it) == days } } ?: true }
    val fits = { sp: JSONObject, ses: String -> val i = sessIdx(ses); (sp.s("minSession").let { it.isBlank() || it == "null" || i >= sessIdx(it) }) && (sp.s("maxSession").let { it.isBlank() || it == "null" || i <= sessIdx(it) }) }
    var rankTop by remember { mutableStateOf<String?>(null) }
    val chosenSplit = split?.takeIf { s -> splits.any { it.s("id") == s } && splitOk(s) } ?: rankTop?.takeIf { p -> splits.any { it.s("id") == p } && splitOk(p) } ?: pick?.takeIf { p -> splits.any { it.s("id") == p } && splitOk(p) }
        ?: splits.firstOrNull { fits(it, session) && splitOk(it.s("id")) }?.s("id") ?: splits.firstOrNull { splitOk(it.s("id")) }?.s("id")
    val sd = splits.firstOrNull { it.s("id") == chosenSplit }
    val sessChoices = SESS.filter { sd == null || fits(sd, it.first) }
    val effSession = if (sessChoices.any { it.first == session }) session else sessChoices.firstOrNull()?.first ?: session

    val nameOk = name.isNotBlank()
    val cycleOk = !cyc || template != "custom" || (custom.size in 2..8 && custom.all { it.weeks in 1..24 } && custom.sumOf { it.weeks } <= 104)
    val canNext = when { "cycletype" in page -> cycleOk; "split" in page -> chosenSplit != null; else -> true }
    val essEnd = listOf("days", "session").map { pageIdx(it) }.firstOrNull { it > -1 } ?: -1
    // Once the schedule is answered you can jump straight to the ranked splits; the later pages are optional.
    val canEarly = key != "mode" && canNext && essEnd > -1 && at >= essEnd && at < steps.size - 1
    fun finish() {
        if (chosenSplit == null) return
        val c = config().put("split", chosenSplit).put("session", effSession)
        if (!nameOk) c.put("name", (sd?.s("name") ?: "My program").take(60))
        if (cyc) vm.createCycle(c, template, if (template == "custom") customBlocksJson(custom) else null, adapt, adapt && adaptSplit) else vm.createProgram(c)
    }
    fun next() {
        if (!canNext) return
        when {
            key == "mode" -> { chose = true; step = 0; tplOpen = false; if (cyc) seen = true }
            at < steps.size - 1 -> { step = at + 1; revOpen = false }
            else -> finish()
        }
    }
    fun back() {
        when {
            !chose && at == 0 -> { if (cyc && seen && !full) vm.toast("Cycle setup kept on this device"); vm.closeSetup() }
            chose && at == 0 -> { chose = false; step = 0 }
            else -> step = at - 1
        }
    }
    BackHandler { back() }

    val pageHeads = mapOf(
        "goal" to ("What are you training for?" to "Shapes your reps, rest and which splits rank highest."),
        "experience" to ("How long have you lifted?" to "Sets how much work you get each week. Newer lifters do best practicing each lift often."),
        "days" to ("When can you train?" to "Days a week, and how long a session can run."),
        "equipment" to ("Where do you train?" to "Only exercises you can actually do get programmed. Bodyweight is on by default and can be excluded below."),
        "constraints" to ("Gym limits" to "Rules for how a workout uses the gym. They’re always followed."),
        "focus" to ("Anything to prioritise?" to "One point gives a muscle a bit more work; two makes it a focus. You can skip this."),
        "split" to ((if (cyc) "Your best split" else "Your best split") to "Every split that fits $days days, built from your answers and ranked on how well it fits you."),
    )
    val heads = mapOf(
        "mode" to ("What do you want to build?" to "Both ask the same questions about your training — a cycle just chains multiple blocks together."),
        "units" to ("Which units do you train in?" to "Used everywhere — plates, targets, history. You can change it later in Settings."),
        "cycletype" to ("Which cycle do you want to run?" to "Each phase is planned around its goal, then later blocks can adapt from the workouts you actually complete."),
        "name" to ((if (cyc) "Name your cycle" else "Name your program") to "You can rename it any time after it’s built."),
        "experience" to ("What’s your experience level?" to "This scales your weekly set volume sensibly."),
        "goal" to ("What’s your main goal?" to "Drives rep ranges, set counts and progression."),
        "approach" to ("Training approach" to "How much volume per exercise. Both build muscle — pick what fits your recovery and time."),
        "days" to ("How many days per week?" to "Only splits that fit this frequency are shown."),
        "split" to ("Pick your split" to "Choose how to spread $days training days across the week."),
        "session" to ("How much time per session?" to "Choose a capacity band, not a quota. Pursuit uses the time when another set is worth its fatigue cost."),
        "equipment" to ("What equipment do you have?" to "Only exercises you can actually perform get programmed. Bodyweight is available by default and can be excluded below."),
        "constraints" to ("Workout constraints" to "Choose how the workout is allowed to use gym space. These are firm generation constraints, not suggestions."),
        "focus" to ("Focus on any muscles?" to "Prioritise these muscles within your time and equipment limits. One point raises priority; two points marks a stronger specialization target."),
        "reduce" to ("Any muscles to reduce?" to "Aim for maintenance work on these muscles. Shared compound lifts and fixed program prescriptions may limit the reduction. Focused muscles are hidden here."),
        "progression" to ("How should progression work?" to "Auto chooses the progression that fits each exercise and this block, then re-checks it from your training history. Manual keeps the weekly prescription in your hands."),
        "length" to ("Block length" to "Pick a fixed block that resets on a set schedule, or go continuous — training that auto-progresses forever and manages its own deloads."),
        "deload" to ("Add a deload week?" to "A deload adds a lighter recovery week at the end."),
    )

    // Build and score the splits in the background once the schedule is known: by the time you reach the ranked page
    // most of them are cached and it opens ready. Any answer that changes the build restarts it after a short pause.
    val warmCfg = rankConfig().toString()
    LaunchedEffect(warmCfg, key) {
        if (key in setOf("mode", "cycletype", "goal", "experience", "split")) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        val c = JSONObject(warmCfg)
        val pl = vm.rankSplitsPlan(c) ?: return@LaunchedEffect
        pl.strings("ids").forEach { vm.rankSplit(c, it) }
    }
    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding()) {
        // Top bar: back, title, step count, progress
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { back() }) { Icon(PiIcons.ChevronLeft, "Back") }
                Text(if (key == "mode") "Create" else if (cyc) "Create Cycle" else "Create Program", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                // Count the answers that matter first: "1/15" counted the optional tail too and made setup look twice as long.
                Text(if (key == "mode") "" else "${at + 1}/${steps.size}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = cs.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
            }
            val prog by animateFloatAsState(if (key == "mode") 0.06f else (at + 1f) / steps.size, Motion.data(), label = "wz")
            Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(cs.surfaceContainerHighest)
                .semantics { contentDescription = "Setup progress, step ${at + 1} of ${steps.size}" }) {
                Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(CircleShape).background(cs.primary))
            }
        }
        // The question
        AnimatedContent(at to cyc, transitionSpec = {
            val fwd = targetState.first >= initialState.first
            (slideInHorizontally(Motion.spatial()) { if (fwd) it / 6 else -it / 6 } + fadeIn(Motion.enter(250))) togetherWith (slideOutHorizontally(Motion.spatial()) { if (fwd) -it / 6 else it / 6 } + fadeOut(Motion.exit(120)))
        }, label = "wzStep", modifier = Modifier.weight(1f)) { (pi, _) ->
            val pg = steps[pi.coerceIn(0, steps.size - 1)]; val k0 = pg.first()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 24.dp).widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (h, sub) = pageHeads[k0] ?: heads[k0] ?: ("" to "")
                if (d0 != null) DraftBanner("Picking up your unfinished cycle setup. It stays on this device until you build or discard it.", onDiscard)
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(h, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp), modifier = Modifier.semantics { heading() })
                    Text(if (k0 == "deload") "Your block runs ${if (cyc) "its first block's" else "$weeks"} weeks. A deload is a lighter week at the end so you recover." else sub,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                @Composable fun body(k: String) { when (k) {
                    "mode" -> {
                        WzOption("Single program", "One training block built around your goal, schedule, and gym.", WZ.dumb, !cyc) { cyc = false }
                        WzOption("Training cycle", "Multiple blocks that progress from one phase to the next over time.", WZ.layers, cyc) { cyc = true }
                        // App.js "Custom split · Build your own": you pick every lift; the engine coaches it.
                        var ownOpen by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp)).clickable { ownOpen = true }
                            .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(com.pursuitiron.app.ui.common.PiIcons.Pencil, null, tint = cs.primary)
                            Column(Modifier.weight(1f)) {
                                Text("Build your own", style = MaterialTheme.typography.titleMedium)
                                Text("Pick every exercise yourself. The app still sets weights and reps, progresses each lift, plans the easy week and builds the next block.",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            Icon(com.pursuitiron.app.ui.common.PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
                        }
                        if (ownOpen) BuildOwnDialog(onClose = { ownOpen = false }) { name, n -> ownOpen = false; vm.buildOwn(name, n) }
                        Box(Modifier.padding(top = 6.dp).height(44.dp).clip(CircleShape).background(cs.tertiaryContainer).clickable { tplOpen = !tplOpen }
                            .semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                            Text(if (tplOpen) "Hide templates" else "Start from a template · ${vm.templates?.list("items")?.size ?: 0}",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onTertiaryContainer)
                        }
                        AnimatedVisibility(tplOpen, enter = Motion.expandIn, exit = Motion.shrinkOut) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { TemplatePicker(vm) } }
                    }
                    "units" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("kg" to "Kilograms", "lb" to "Pounds").forEach { (u, l) -> WzSeg("$l · $u", unit == u, Modifier.weight(1f)) { unit = u } }
                    }
                    "cycletype" -> CycleTemplatePicker(vm, template, { template = it }, custom, adapt, { adapt = it }, adaptSplit, { adaptSplit = it }, days)
                    "name" -> OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().semantics { contentDescription = if (cyc) "Cycle name" else "Program name" }, singleLine = true,
                        placeholder = { Text(if (cyc) "e.g. Summer Training Cycle" else (sd?.s("name") ?: "e.g. Summer Hypertrophy Block")) }, shape = RoundedCornerShape(18.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    "experience" -> EXP.forEach { (id, l, s) -> WzOption(l, s, WZ.trophy, exp == id) { exp = id } }
                    "goal" -> listOf(Triple("hypertrophy", "Build muscle & size", WZ.layers), Triple("strength", "Get strong, lower reps", WZ.flame), Triple("both", "Heavy main lifts, hypertrophy accessories", WZ.zap))
                        .forEach { (id, s, ic) -> WzOption(GOALS[id]!!, s, ic, goal == id) { goal = id } }
                    "approach" -> {
                        WzOption("Standard volume", "Balanced weekly volume and recovery. The best default for most lifters.", WZ.layers, approach == "standard") { approach = "standard" }
                        WzOption("Minimalist · high intensity", "Fewer hard sets taken closer to failure. Faster sessions with less total volume.", WZ.flame, approach == "minimalist") { approach = "minimalist" }
                    }
                    "days" -> {
                        // The prototype's big day chips; the count of splits that fit updates under them.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..6).forEach { d ->
                                val on = days == d
                                val r by animateDpAsState(if (on) 20.dp else 32.dp, Motion.spatialFast(), label = "dr")
                                val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, Motion.effects(), label = "db")
                                Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(r)).background(bg).clickable { days = d; split = null }
                                    .semantics { role = Role.RadioButton; selected = on; contentDescription = "$d days a week" }, contentAlignment = Alignment.Center) {
                                    Text("$d", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = if (on) cs.onPrimary else cs.onSurface)
                                }
                            }
                        }
                        val n = opts.list("splits").count { sp -> sp.optJSONArray("days")?.let { a -> (0 until a.length()).any { a.optInt(it) == days } } ?: true }
                        Text("$n splits fit $days days. All of them get built and ranked for you at the end.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    "split" -> RankedSplits(vm, rankConfig(), chosenSplit, { split = it }, { rankTop = it }, GOALS[goal] ?: "", cyc)
                    "session" -> {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SESS.forEach { (id, l) ->
                                val on = session == id
                                val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, Motion.effects(), label = "sb")
                                Box(Modifier.height(52.dp).clip(RoundedCornerShape(if (on) 16.dp else 26.dp)).background(bg).clickable { session = id }
                                    .semantics { role = Role.RadioButton; selected = on; contentDescription = l }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                    Text(SESS_SHORT[id] ?: l, style = MaterialTheme.typography.titleMedium, color = if (on) cs.onPrimary else cs.onSurface)
                                }
                            }
                        }
                        val ws = Math.round(days * (SES_MIN[session] ?: 60) / 60.0 * 17)
                        Text("About $ws hard sets a week. A ceiling, not a quota: a set is added only when it’s worth the fatigue.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    "equipment" -> EquipmentStep(opts, equip, { equip = it }, noRack, { noRack = it }, noBodyweight, { noBodyweight = it })
                    "constraints" -> {
                        WzKicker("SUPERSETS")
                        Text("Pairing two exercises with opposing muscles cuts rest time, so more work fits in the same session. It needs both stations free.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WzSeg("Use supersets", !noSupersets, Modifier.weight(1f)) { noSupersets = false }
                            WzSeg("No supersets", noSupersets, Modifier.weight(1f)) { noSupersets = true }
                        }
                        WzKicker("BARBELL LIMIT", Modifier.padding(top = 14.dp))
                        Text("Cap barbell movements per session so a busy commercial gym (racks, platforms) doesn’t bottleneck your workout — dumbbell, machine & cable lifts are used instead." +
                            if ("barbell" !in eq) " Applies once you add a barbell." else "", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf<Pair<Int?, String>>(null to "No limit", 1 to "1", 2 to "2", 3 to "3").forEach { (v, l) -> WzSeg(l, barbellCap == v, Modifier.weight(1f)) { barbellCap = v } }
                        }
                        val need = sd?.optInt("minBarbells", 0) ?: 0
                        if (barbellCap != null && need > 0 && barbellCap!! < need) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text("Most sessions here use $need or more barbell lifts", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = cs.onTertiaryContainer)
                            Text("${sd?.s("name")} is built around its main barbell lifts, so a limit of $barbellCap cannot be met on most days. Raise the limit, or pick a program that leans on machines and dumbbells.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
                        }
                    }
                    "focus" -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Focus points used", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("$focusTotal / 5", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (focusTotal >= 5) cs.primary else cs.onSurface)
                        }
                        opts.list("parts").forEach { p ->
                            val id = p.s("id"); val v = focus[id] ?: 0
                            val bg by animateColorAsState(if (v > 0) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "f")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.s("label"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                    if (id == "neck") Text("Rarely fits — most sessions have no room for direct neck work", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                WzStep("−", v > 0, "Decrease ${p.s("label")} focus") { val nv = (v - 1).coerceAtLeast(0); if (nv == 0) focus.remove(id) else focus[id] = nv }
                                Text("$v", style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
                                WzStep("+", v < 2 && focusTotal < 5, "Increase ${p.s("label")} focus") { focus[id] = v + 1; reduce.remove(id) }
                            }
                        }
                    }
                    "reduce" -> opts.list("parts").filter { (focus[it.s("id")] ?: 0) == 0 }.forEach { p ->
                        val id = p.s("id"); val on = id in reduce
                        WzCheckRow(p.s("label"), null, on, RoundedCornerShape(18.dp)) { if (on) reduce.remove(id) else reduce.add(id) }
                    }
                    "progression" -> {
                        WzOption("Auto · choose per exercise", "Picks how each lift progresses and adjusts as you train", WZ.wand, progression == "auto") { progression = "auto" }
                        WzOption("Manual weekly targets", "You set the weekly sets and reps; loads still progress per lift", WZ.pencil, progression == "manual") { progression = "manual" }
                    }
                    "length" -> {
                        WzOption("Endless — continuous", "No fixed end. Weight and sets progress every session, and a lighter week drops in when you stall or get run down.", WZ.rep, endless) { endless = true }
                        listOf(4 to "Short block — quick progress, simpler methods", 6 to "Balanced block — more room for advanced progression when it fits", 8 to "Long block — gradual ramp", 10 to "Longest build-up, for advanced lifters")
                            .forEach { (w, s) -> WzOption("$w weeks", s, WZ.cal, !endless && weeks == w) { weeks = w; endless = false } }
                    }
                    "deload" -> {
                        WzOption("Yes, add a deload", "Week ${(if (cyc) 0 else weeks) + 1} · lighter weights and fewer sets", WZ.battery, deload) { deload = true }
                        WzOption("No deload", "Train through the whole block", WZ.flame, !deload) { deload = false }
                    }
                } }
                // The page: its first question in full, the rest under small headings, optional ones folded.
                val sumOf = mapOf(
                    "constraints" to listOf(if (noSupersets) "No supersets" else "Supersets on", barbellCap?.let { "$it barbell lifts a day" } ?: "No barbell limit").joinToString(" · "),
                    "reduce" to (reduce.filter { (focus[it] ?: 0) == 0 }.size.takeIf { it > 0 }?.let { "$it muscle${if (it == 1) "" else "s"}" } ?: "None"),
                    "options" to listOfNotNull(if (!cyc) (if (progression == "manual") "Manual targets" else "Auto progression") else null,
                        if (!cyc) (if (endless) "Endless" else "$weeks weeks") else null, if (!(endless && !cyc)) (if (deload) "+ deload" else "no deload") else null).joinToString(" · "))
                val folded = setOf("constraints", "reduce", "progression", "length", "deload")
                pg.forEachIndexed { ix, k ->
                    when {
                        ix == 0 -> body(k)
                        k == "constraints" && pg.size > 1 -> WzFold("Gym limits", sumOf["constraints"]!!) { body(k) }
                        k == "reduce" -> WzFold("Train less (optional)", sumOf["reduce"]!!) { body(k) }
                        k in setOf("progression", "length", "deload") -> if (pg.firstOrNull { it in setOf("progression", "length", "deload") } == k)
                            WzFold(if (cyc) "Deload" else "Program options", sumOf["options"]!!) { pg.filter { it in setOf("progression", "length", "deload") }.forEach { fk ->
                                WzKicker(SUBHEAD[fk] ?: "", Modifier.padding(top = 6.dp)); body(fk) } }
                        else -> { WzKicker(SUBHEAD[k] ?: "", Modifier.padding(top = 14.dp)); body(k) }
                    }
                }
                // Your setup (web: WizardReviewPane)
                val rows = buildList {
                    add("Format" to if (chose) (if (cyc) "Training cycle" else "Single program") else "Choose a format")
                    add("Name" to name.trim().ifBlank { "Not named yet" })
                    add("Goal" to if (chose) GOALS[goal]!! else "After choosing format")
                    if (!full) add("Experience" to (EXP.firstOrNull { it.first == exp }?.second ?: exp) + " · from your profile")
                    add("Schedule" to "$days days per week")
                    add("Split" to (sd?.s("name") ?: "Not selected"))
                    add("Session length" to (SESS.firstOrNull { it.first == effSession }?.second ?: "—"))
                    val nEq = eq.count { it != "no-rack" }
                    add("Equipment" to (if (nEq == 0) "Bodyweight only" else "$nEq selected types") + if (full) "" else " · ${activeGym?.s("name") ?: "your gym"}")
                    add("Training style" to if (approach == "minimalist") "Minimalist · high intensity" else "Standard volume")
                    add("Barbell limit" to (barbellCap?.let { "$it per day" } ?: "No daily limit"))
                    add("Supersets" to if (noSupersets) "Disabled" else "Allowed")
                    add("Bodyweight exercises" to if (noBodyweight) "Disabled" else "Allowed")
                    if (!cyc) { add("Block" to if (endless) "Endless" else "$weeks weeks" + if (deload) " + deload" else ""); add("Progression" to if (progression == "manual") "Manual weekly targets" else "Auto") }
                    val labelOf = { pid: String -> opts.list("parts").firstOrNull { it.s("id") == pid }?.s("label") ?: pid }
                    focus.filter { it.value > 0 }.takeIf { it.isNotEmpty() }?.let { f -> add("Emphasize" to f.entries.joinToString(", ") { labelOf(it.key) + if (it.value > 1) " ×${it.value}" else "" }) }
                    reduce.filter { (focus[it] ?: 0) == 0 }.takeIf { it.isNotEmpty() }?.let { r -> add("Reduce" to r.joinToString(", ") { labelOf(it) }) }
                }
                // The ranked page shows the split itself; the settings summary belongs to the questions.
                if ("split" !in pg && "cycletype" !in pg) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(46.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { revOpen = !revOpen }
                    .semantics { role = Role.Button; stateDescription = if (revOpen) "Expanded" else "Collapsed" }, contentAlignment = Alignment.Center) {
                    Text(if (revOpen) "Hide your setup" else "Your setup · ${rows.size} settings", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                AnimatedVisibility(revOpen && "split" !in pg && "cycletype" !in pg, enter = Motion.expandIn, exit = Motion.shrinkOut) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text("YOUR SETUP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp), color = cs.primary)
                            Text(if (chose && cyc) "Cycle overview" else "Program overview", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            Text(if (key == "mode") "Choose a program or a multi-phase cycle to begin." else "Step ${at + 1} of ${steps.size}. Your choices update here as you go.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant)
                        }
                        Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            rows.forEach { (k2, v2) ->
                                Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(k2, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(v2, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                                }
                            }
                        }
                        Text("This is a summary of your settings, not a generated workout. Exercise selection, working sets and progression are finalized when you build.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        // Bottom bar
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.widthIn(min = 112.dp).height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { back() }.semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(if (at == 0 && !chose) "Cancel" else "Back", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                val bg by animateColorAsState(if (canNext) cs.primary else cs.surfaceContainerHighest, Motion.effects(), label = "next")
                Box(Modifier.weight(1f).height(54.dp).clip(CircleShape).background(bg).clickable(enabled = canNext) { next() }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Text(when { key == "mode" -> "Continue"; at == steps.size - 1 -> if (cyc) "Build this cycle" else "Build this program"; else -> "Next" },
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = if (canNext) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
            if (canEarly) {
                val left = steps.size - 1 - at
                Text("Skip to your ranked splits" + if (left > 1) " · ${left - 1} optional ${if (left - 1 == 1) "page" else "pages"}" else "",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { step = steps.size - 1; revOpen = false }.padding(vertical = 8.dp))
            }
        }
    }
}

/** The design's option card: icon tile, label and supporting text; selected squares off with a primary outline. */
@Composable
private fun WzOption(label: String, sub: String?, icon: ImageVector, on: Boolean, enabled: Boolean = true, note: String? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 22.dp else 18.dp, Motion.spatialFast(), label = "opt")
    val ir by animateDpAsState(if (on) 14.dp else 20.dp, Motion.spatialFast(), label = "optI")
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "optBg")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(bg).border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(r))
        .alpha(if (enabled) 1f else 0.55f).clickable(enabled = enabled, onClick = onClick).semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on; if (!enabled) disabled() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(ir)).background(if (on) cs.primary else cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = (if (on) cs.onSecondaryContainer else cs.onSurface).copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
            if (!note.isNullOrBlank()) Text(note, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = if (enabled) cs.primary else cs.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun WzKicker(t: String, modifier: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun WzSeg(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 14.dp else 24.dp, Motion.spatialFast(), label = "seg")
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, Motion.effects(), label = "segBg")
    Box(modifier.height(48.dp).clip(RoundedCornerShape(r)).background(bg).clickable(onClick = onClick).semantics { role = Role.RadioButton; selected = on }, contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = if (on) cs.onPrimary else cs.onSurface)
    }
}

@Composable
private fun WzStep(sym: String, enabled: Boolean, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = desc; role = Role.Button }, contentAlignment = Alignment.Center) { Text(sym, fontSize = 20.sp) }
}

@Composable
private fun WzCheckRow(label: String, sub: String?, on: Boolean, shape: RoundedCornerShape? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "chk")
    Row(Modifier.fillMaxWidth().then(if (shape != null) Modifier.clip(shape) else Modifier).background(bg).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { role = Role.Checkbox; stateDescription = if (on) "Checked" else "Not checked" }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(if (on) cs.primary else Color.Transparent).border(2.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (on) Icon(PiIcons.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun EquipmentStep(opts: JSONObject?, equip: Set<String>, onEquip: (Set<String>) -> Unit, noRack: Boolean, onNoRack: (Boolean) -> Unit, noBw: Boolean, onNoBw: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESETS.forEach { (l, ids) ->
            val on = ids.toSet() == equip
            Box(Modifier.height(38.dp).clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainerLow).clickable { onEquip(ids.toSet()) }
                .semantics { role = Role.Button; selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onPrimary else cs.onSurface)
            }
        }
    }
    Text(if (equip.isEmpty()) "Bodyweight only" else "${equip.size} selected types", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
    opts.list("equipment").filter { it.s("id") != "bodyweight" && it.s("id") != "no-rack" }.groupBy { it.s("cat").ifBlank { "Other" } }.forEach { (cat, items) ->
        Row(Modifier.padding(top = 6.dp)) {
            Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${items.count { it.s("id") in equip }} of ${items.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { e -> val id = e.s("id"); val on = id in equip; WzCheckRow(e.s("label"), null, on) { onEquip(if (on) equip - id else equip + id) } }
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if ("barbell" in equip) WzCheckRow("No squat rack", "Skips lifts taken from a rack or safeties — squats, bench and standing presses. Barbell work from the floor stays.", noRack) { onNoRack(!noRack) }
        WzCheckRow("Skip bodyweight exercises", "No push-ups, planks, bodyweight squats, etc. — only loaded movements.", noBw) { onNoBw(!noBw) }
    }
}

/** The design's draft banner (Main.dc.html peDraftOn / cycRecovered / wzRecovered): tertiary status with Discard. */
@Composable
fun DraftBanner(text: String, onDiscard: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.tertiaryContainer).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onTertiaryContainer, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedButton(onClick = onDiscard, modifier = Modifier.height(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, cs.onTertiaryContainer)) {
            Text("Discard", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onTertiaryContainer)
        }
    }
}


/** Build your own: a name and how many days a week; the days start empty and are filled in the editor. */
@Composable
private fun BuildOwnDialog(onClose: () -> Unit, onBuild: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(3) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose,
        title = { Text("Build your own") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Program name") }, placeholder = { Text("My program") })
                Text("Days a week", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..6).forEach { n ->
                        val on = n == days
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { days = n }.semantics { role = Role.RadioButton; selected = on; contentDescription = "$n day${if (n == 1) "" else "s"} a week" },
                            contentAlignment = Alignment.Center) {
                            Text("$n", style = MaterialTheme.typography.titleMedium, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Text("Each day starts empty. You add the lifts next, and can rename, add or remove days any time.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { androidx.compose.material3.Button(onClick = { onBuild(name.trim().ifBlank { "My program" }, days) }) { Text("Start building") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cancel") } })
}
