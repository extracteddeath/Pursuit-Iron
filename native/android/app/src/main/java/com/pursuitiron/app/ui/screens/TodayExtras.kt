package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
