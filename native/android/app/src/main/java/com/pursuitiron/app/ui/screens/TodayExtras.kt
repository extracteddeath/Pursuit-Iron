package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pursuitiron.app.ui.common.SectionTitle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmt1
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.common.volUnit
import org.json.JSONObject
import kotlinx.coroutines.launch

/* App.js INTRO_VERSION: bump when the welcome content changes so returning users see it once more. */
const val INTRO_VERSION = 1

/** App.js onboarding: what the app does, then build a program or browse templates. */
@Composable
fun IntroScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 30.dp).widthIn(max = 560.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(PiIcons.Plates, null, tint = cs.onPrimary)
            }
            Spacer(Modifier.height(22.dp))
            Kicker("Pursuit Iron · Strength Coach")
            Text("Your training,\nbuilt around you.", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text("Build around your goal, schedule, and equipment. Log what you actually did; the next prescription stays grounded in that history. Your data remains on this device unless you export it.",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            listOf(
                Triple(PiIcons.Sparkle, "Built for your setup", "Goals, days, equipment, and session length shape the program instead of forcing one template."),
                Triple(PiIcons.Check, "Log without friction", "Weight, reps, effort, rest, and the next target stay within reach while you train."),
                Triple(PiIcons.Progress, "Progress you can inspect", "Lift trends, records, volume, measurements, and cycle history stay connected to the work that created them."),
            ).forEach { (icon, title, sub) ->
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(icon, null, tint = cs.primary, modifier = Modifier.size(19.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.prefs.markIntro(INTRO_VERSION); vm.prefs.markWhatsNew(WHATS_NEW_VERSION); vm.openSetupMode("program") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Build my program") }
            OutlinedButton(onClick = { vm.prefs.markIntro(INTRO_VERSION); vm.prefs.markWhatsNew(WHATS_NEW_VERSION); vm.openSetupMode("template") }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Browse templates instead") }
            TextButton(onClick = { vm.prefs.markIntro(INTRO_VERSION); vm.prefs.markWhatsNew(WHATS_NEW_VERSION) }, modifier = Modifier.fillMaxWidth()) { Text("I already use the web app") }
        }
    }
}

/* App.js WhatsNewCard, for this app's own releases. */
const val WHATS_NEW_VERSION = 1
private val WHATS_NEW = listOf(
    "The Android app now does everything the web app does: readiness checks, warm-ups, drop sets and myo-reps, supersets, and a finish sheet that shows what you earned.",
    "Quick workouts: log off-plan, then save the session as a routine.",
    "Today suggests a fresher day, a deload when fatigue builds, or a lighter session.",
    "Tap any lift in Progress for its full chart, records and where it stands.",
)

@Composable
fun WhatsNewCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    PiCard(color = cs.secondaryContainer, radius = 28.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Sparkle, null, tint = cs.onSecondaryContainer)
            Text("What's new", style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer, modifier = Modifier.weight(1f))
        }
        WHATS_NEW.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer) }
        TextButton(onClick = { vm.prefs.markWhatsNew(WHATS_NEW_VERSION) }) { Text("Got it") }
    }
}

/* App.js DeloadAdviceCard. */
@Composable
fun DeloadCard(vm: AppViewModel, h: JSONObject) {
    val d = h.o("deload") ?: return
    val cs = MaterialTheme.colorScheme
    PiCard(color = cs.tertiaryContainer, radius = 28.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Timer, null, tint = cs.onTertiaryContainer)
            Text("A deload looks due", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer)
        }
        Text("Fatigue is outpacing recovery. A lighter week, roughly half the sets and ~10% less load, will let progress rebound.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer)
        d.strings("reasons").forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer) }
        if (h.b("canDeload")) Button(onClick = { vm.startDeload() }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) { Text("Train this week's deload now") }
    }
}

/* App.js MuscleRecoveryCard. */
@Composable
fun RecoveryCard(vm: AppViewModel, h: JSONObject) {
    val r = h.o("recovery") ?: return
    val parts = r.list("parts"); if (parts.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp, padding = 18.dp) {
        Text("Muscle recovery", style = MaterialTheme.typography.titleMedium)
        Text("Estimated freshness from your recent sessions", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        parts.take(6).forEach { m ->
            val v = m.i("readiness")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.s("label"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                ProgressBar(v / 100f, Modifier.weight(1f), height = 10.dp, color = if (v < 50) cs.error else if (v < 85) cs.tertiary else cs.primary)
                Text("$v%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
            }
        }
        if (r.b("restAdvised")) {
            Text("Most muscles are still fatigued. Rest, or do a light active-recovery session: reduced volume and easy effort to drive blood flow without adding fatigue.",
                style = MaterialTheme.typography.bodySmall)
            if (h.b("canLight")) FilledTonalButton(onClick = { vm.startLight() }, modifier = Modifier.fillMaxWidth()) { Text("Do a light session instead") }
        } else {
            val ready = r.strings("ready")
            if (ready.isNotEmpty()) Text("Fresh & ready: ${ready.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = cs.primary)
        }
    }
}

/* App.js WeeklyRecapCard. */
@Composable
fun RecapCard(h: JSONObject) {
    val r = h.o("recap") ?: return
    val cs = MaterialTheme.colorScheme
    val u = h.s("unit", "kg")
    fun delta(cur: Double, prev: Double): String? = if (prev <= 0) null else ((cur - prev) / prev * 100).toInt().let { if (it > 0) "▲$it%" else if (it < 0) "▼${-it}%" else "—" }
    PiCard(radius = 28.dp, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (r.i("prs") > 0) Text("${r.i("prs")} PR${if (r.i("prs") == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Workouts", "${r.i("count")}", null),
                Triple("Sets", "${r.i("sets")}", delta(r.d("sets"), r.d("prevSets"))),
                Triple("Volume", "${fmt1(r.d("vol") / 1000.0)} ${volUnit(u)}", delta(r.d("vol"), r.d("prevVol"))),
            ).forEach { (k, v, dl) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(10.dp)) {
                    Text(v, style = MaterialTheme.typography.titleMedium)
                    Text(k + (dl?.let { "  $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
        r.o("topMuscle")?.let { t -> Text("Most trained: ${t.s("label")} · ${t.i("sets")} sets", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

/* App.js StrengthSnapshotCard: the score, or the prompt that unlocks it. */
@Composable
fun StrengthTeaser(vm: AppViewModel, h: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val score = vm.profile?.o("score")
    PiCard(radius = 28.dp, padding = 18.dp, onClick = { vm.goTab(com.pursuitiron.app.Tab.You) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Trophy, null, tint = cs.primary)
            Text("Strength score", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
        if (score != null) {
            Text("${score.i("overall")}", style = MaterialTheme.typography.displaySmall, color = cs.primary)
            Text(score.s("level"), style = MaterialTheme.typography.bodyMedium)
        } else Text(if (h.b("needsBodyweight")) "Add your bodyweight on your profile to unlock your score and strength standards." else "Log a main lift to get scored.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/* The design's welcome back: after a break, one easier session (two working sets per lift at 90%)
   before the plan resumes. "Train as planned" hides it until the next break. */
@Composable
fun WelcomeBackCard(vm: AppViewModel, r: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val days = r.i("daysOff"); val pct = Math.round(r.d("factor") * 100); val sets = r.i("sets")
    PiCard(color = cs.primaryContainer, radius = 28.dp, padding = 20.dp) {
        Kicker("Welcome back", cs.onPrimaryContainer)
        Text("$days days off", style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
        Text("Ease back in with a re-entry session: $sets working sets per lift at $pct% of today's loads. Your full plan resumes next session.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startReentry() }, modifier = Modifier.height(48.dp)) { Text("Start re-entry") }
            TextButton(onClick = { vm.prefs.skipReentry(r.optLong("lastDate")); vm.toast("Full plan it is. Ease off if anything feels off.") }, modifier = Modifier.height(48.dp)) {
                Text("Train as planned", color = cs.onPrimaryContainer)
            }
        }
    }
}

/* The design's rest day hero: the schedule has nothing today, so Today leads with recovery and when
   the next session is. Training anyway, or an off-plan quick workout, stays one tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestDayHero(vm: AppViewModel, r: JSONObject) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 36.dp, bottomStart = 12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.material3.Surface(color = cs.onSecondaryContainer, contentColor = cs.secondaryContainer, shape = RoundedCornerShape(999.dp)) {
                Text("REST DAY", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recover today", style = MaterialTheme.typography.displaySmall)
                val sore = r.strings("recovering")
                Text(listOfNotNull(
                    r.s("when").takeIf { it.isNotBlank() }?.let { "${r.s("nextLabel")} is $it." },
                    if (sore.isNotEmpty()) sore.take(3).joinToString(", ") + (if (sore.size == 1) " is" else " are") + " still recovering." else null,
                ).joinToString(" "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(0.85f))
            }
            val rows = r.list("rows")
            if (rows.isNotEmpty()) androidx.compose.material3.Surface(color = cs.surface, contentColor = cs.onSurface, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { m ->
                        val v = m.i("readiness")
                        Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "${m.s("label")} $v percent recovered" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(m.s("label"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp), maxLines = 1)
                            ProgressBar(v / 100f, Modifier.weight(1f), height = 8.dp, color = if (v < 70) cs.tertiary else cs.primary)
                            Text("$v%", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp))
                        }
                    }
                }
            }
            listOf("A 20–30 minute walk speeds recovery more than lying still.", "Sleep is when the training turns into strength. Aim for seven hours or more.").forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•"); Text(tip, style = MaterialTheme.typography.bodyMedium) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.openPreview() }, modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.onSecondaryContainer, contentColor = cs.secondaryContainer)) {
                    Text("Train ${r.s("nextLabel")} now", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { vm.startQuick() }, modifier = Modifier.height(48.dp)) { Text("Quick workout", color = cs.onSecondaryContainer) }
            }
        }
    }
}

/* ---------------------------------------------------------------- the design's Today pieces */

/** "6-week streak": consecutive 7-day windows with a workout (App.js), so planned rest never breaks it. */
@Composable
fun StreakLine(h: JSONObject) {
    val n = h.i("streakWeeks"); if (n <= 0) return
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(PiIcons.Flame, null, Modifier.size(18.dp), tint = cs.primary)
        Text("$n-week streak", style = MaterialTheme.typography.titleSmall)
        Text("· a week counts with one workout in it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** Block progress: one segment per week, the current one part-filled, the deload week marked. */
@Composable
fun BlockProgressCard(vm: AppViewModel, h: JSONObject) {
    val b = h.o("block") ?: return
    val cs = MaterialTheme.colorScheme
    val wk = b.i("weekIndex"); val total = b.i("weeksTotal").coerceAtLeast(1)
    val segs = if (b.b("endless")) 0 else total + if (b.b("deload")) 1 else 0
    val days = h.list("weekDays"); val doneFrac = if (days.isEmpty()) 0f else days.count { it.s("status") == "done" } / days.size.toFloat()
    val title = when {
        b.b("endless") -> if (b.b("inDeload")) "Continuous · deload" else "Continuous · week $wk"
        b.b("inDeload") -> "Deload week"
        else -> "Week $wk of $total" + (b.s("goal").takeIf { it.isNotBlank() }?.let { " · $it block" } ?: "")
    }
    PiCard(radius = 24.dp, padding = 16.dp, onClick = { vm.goTab(com.pursuitiron.app.Tab.Plan) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Plan", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Icon(PiIcons.ChevronRight, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        }
        if (segs > 0) Row(Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = "$title, ${Math.round(doneFrac * 100)} percent of this week done" },
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..segs).forEach { w ->
                val dl = b.b("deload") && w == segs
                val fill = when { w < wk -> 1f; w == wk -> doneFrac.coerceAtLeast(0.08f); else -> 0f }
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(if (dl) cs.tertiaryContainer else cs.surfaceContainerHighest)) {
                    if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(cs.primary))
                }
            }
        }
    }
}

/** Monday-first week strip: workouts logged (check), today (ring), scheduled days (dot). */
@Composable
fun WeekStrip(h: JSONObject) {
    val strip = h.list("weekStrip"); if (strip.size != 7) return
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 24.dp, padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            strip.forEach { d ->
                val done = d.b("done"); val today = d.b("today")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "${d.s("dow")} ${d.i("date")}" + (if (today) ", today" else "") + (if (done) ", trained" else if (d.b("scheduled")) ", scheduled" else "")
                    }) {
                    Text(d.s("dow"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Box(Modifier.size(40.dp).clip(if (done) RoundedCornerShape(12.dp) else CircleShape)
                        .background(if (done) cs.primary else cs.surfaceContainerLow)
                        .then(if (today && !done) Modifier.border(2.dp, cs.primary, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                        if (done) Icon(PiIcons.Check, null, Modifier.size(18.dp), tint = cs.onPrimary)
                        else Text("${d.i("date")}", style = MaterialTheme.typography.labelLarge, color = if (today) cs.primary else if (d.b("past")) cs.onSurfaceVariant else cs.onSurface)
                    }
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (d.b("scheduled") && !done) cs.tertiary else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
    }
}

/** The design's "Today's game plan": the coach notes for the next session (App.js coachFacts). */
@Composable
fun CoachCard(h: JSONObject) {
    val notes = h.list("coach"); if (notes.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var all by remember { mutableStateOf(false) }
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Progress, null, Modifier.size(20.dp), tint = cs.primary)
            Text("Today's game plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (notes.size > 1) Text("${notes.size} notes", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        (if (all) notes else notes.take(3)).forEach { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icon = when (f.s("icon")) { "deload", "fatigue" -> PiIcons.Shield; "recal", "cal" -> PiIcons.Redo; "pr" -> PiIcons.Trophy; else -> PiIcons.Sparkle }
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(16.dp), tint = cs.onPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(f.s("title"), style = MaterialTheme.typography.titleSmall)
                    Text(f.s("text"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    f.s("evidence").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                }
            }
        }
        if (notes.size > 3) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${notes.size}") }
    }
}

/** "This week": the program's days with their status, the next one highlighted. */
@Composable
fun ThisWeekList(vm: AppViewModel, h: JSONObject) {
    val days = h.list("weekDays"); if (days.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("This week")
        Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEach { d ->
                val st = d.s("status"); val today = st == "today"; val done = st == "done"
                Row(Modifier.fillMaxWidth().background(if (today) cs.primaryContainer else cs.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(if (done) RoundedCornerShape(14.dp) else CircleShape).background(if (done || today) cs.primary else cs.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text(d.s("weekday").ifBlank { "D${days.indexOf(d) + 1}" }, style = MaterialTheme.typography.labelMedium, color = if (done || today) cs.onPrimary else cs.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.s("label"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${d.i("exercises")} exercises · ${d.i("sets")} sets", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Text(when (st) { "done" -> "Done"; "today" -> "Up next"; else -> "Planned" }, style = MaterialTheme.typography.labelMedium, color = if (today) cs.onPrimaryContainer else cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** App.js needsBackup as the design's banner: back up straight from Today, or hide it for a week. */
@Composable
fun BackupBanner(vm: AppViewModel, b: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportText() ?: return@launch
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { vm.markBackup(); vm.toast("Backup saved") }.onFailure { vm.toast("Couldn't save the backup") }
        }
    }
    val last = b.optLong("lastBackup").takeIf { it > 0 }
    val days = last?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
    val n = b.i("sessionsSince")
    androidx.compose.material3.Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PiIcons.Download, null)
            Column(Modifier.weight(1f)) {
                Text("Backup recommended", style = MaterialTheme.typography.titleSmall)
                Text((if (days != null) "Last backup $days days ago" else "No backup yet") + (if (n > 0) " · $n session${if (n == 1) "" else "s"} since" else ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { save.launch("pursuit-iron-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.json") },
                colors = ButtonDefaults.buttonColors(containerColor = cs.onTertiaryContainer, contentColor = cs.tertiaryContainer)) { Text("Back up") }
            androidx.compose.material3.IconButton(onClick = { vm.prefs.hideBackupNudge() }) { Icon(PiIcons.Close, "Hide for a week", Modifier.size(18.dp)) }
        }
    }
}
