@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.Screen
import com.pursuitiron.app.Tab
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.screens.LibraryScreen
import com.pursuitiron.app.ui.screens.PlanScreen
import com.pursuitiron.app.ui.screens.ProgramEditorScreen
import com.pursuitiron.app.ui.screens.ProgressScreen
import com.pursuitiron.app.ui.screens.SetupScreen
import com.pursuitiron.app.ui.screens.SummaryScreen
import com.pursuitiron.app.ui.screens.TodayScreen
import com.pursuitiron.app.ui.screens.WorkoutScreen
import com.pursuitiron.app.ui.screens.YouScreen
import kotlinx.coroutines.delay

/** True at 600dp and up: rail instead of bar, two columns, dialogs instead of bottom sheets. */
val LocalWide = compositionLocalOf { false }

private val tabs = listOf(
    Triple(Tab.Today, "Today", PiIcons.Today), Triple(Tab.Plan, "Plan", PiIcons.Plan),
    Triple(Tab.Progress, "Progress", PiIcons.Progress), Triple(Tab.Library, "Exercises", PiIcons.Library),
    Triple(Tab.You, "You", PiIcons.You),
)

@Composable
fun PiApp(vm: AppViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when {
            vm.startupError != null -> StartupError(vm.startupError!!)
            !vm.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 600.dp
                CompositionLocalProvider(LocalWide provides wide) { Shell(vm, wide) }
            }
        }
    }
}

@Composable
private fun Shell(vm: AppViewModel, wide: Boolean) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                when (targetState) {
                    Screen.Workout, Screen.Setup, Screen.Editor -> (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(160))
                    else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Tabs -> if (vm.home?.b("empty") == true && vm.prefs.seenIntro < com.pursuitiron.app.ui.screens.INTRO_VERSION) com.pursuitiron.app.ui.screens.IntroScreen(vm) else Tabs(vm, wide)
                Screen.Workout -> WorkoutScreen(vm)
                Screen.Summary -> SummaryScreen(vm)
                Screen.Setup -> SetupScreen(vm)
                Screen.Editor -> ProgramEditorScreen(vm)
            }
        }
        SnackHost(vm, Modifier.align(Alignment.BottomCenter))
        ImportDialog(vm)
        com.pursuitiron.app.ui.screens.CsvImportDialog(vm)
        com.pursuitiron.app.ui.screens.TrainingMaxSheet(vm)
        com.pursuitiron.app.ui.screens.PendingUpdateDialog(vm)
        com.pursuitiron.app.ui.screens.InjuryJournalSheet(vm)
        vm.busy?.let { BusyScrim(it) }
    }
    BackHandler(enabled = vm.screen == Screen.Workout) { vm.minimise() }
    BackHandler(enabled = vm.screen == Screen.Setup) { vm.closeSetup() }
    BackHandler(enabled = vm.screen == Screen.Summary) { vm.closeSummary() }
    BackHandler(enabled = vm.screen == Screen.Tabs && vm.tab != Tab.Today) { vm.goTab(Tab.Today) }
}

@Composable
private fun Tabs(vm: AppViewModel, wide: Boolean) {
    val content: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            AnimatedContent(targetState = vm.tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                when (t) {
                    Tab.Today -> TodayScreen(vm)
                    Tab.Plan -> PlanScreen(vm)
                    Tab.Progress -> ProgressScreen(vm)
                    Tab.Library -> LibraryScreen(vm)
                    Tab.You -> YouScreen(vm)
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Spacer(Modifier.weight(1f))
                tabs.forEach { (t, label, icon) ->
                    NavigationRailItem(selected = vm.tab == t, onClick = { vm.goTab(t) }, icon = { Icon(icon, null) }, label = { Text(label) }, alwaysShowLabel = true)
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.weight(1f)) {
                content(Modifier.weight(1f))
                MiniBar(vm, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).navigationBarsPadding())
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            content(Modifier.weight(1f))
            MiniBar(vm, Modifier)
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { (t, label, icon) ->
                    NavigationBarItem(selected = vm.tab == t, onClick = { vm.goTab(t) }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
        }
    }
}

/** The running workout, shrunk: tap to resume. */
@Composable
private fun MiniBar(vm: AppViewModel, modifier: Modifier) {
    val live = vm.home?.o("live") ?: return
    val done = live.i("done"); val total = live.i("total")
    val elapsed = ((vm.now - live.optLong("startedAt")) / 1000).coerceAtLeast(0)
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.primaryContainer)
            .clickable { vm.resume() }.semantics { contentDescription = "Resume workout, $done of $total sets done" }
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("WORKOUT IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
            Text(live.s("dayLabel"), style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressBar(if (total > 0) done.toFloat() / total else 0f, Modifier.padding(top = 6.dp), height = 4.dp, track = cs.surface)
        }
        Text(com.pursuitiron.app.ui.common.mmss(elapsed), style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer)
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
            Icon(PiIcons.ChevronDown, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp).then(Modifier))
        }
    }
}

@Composable
private fun SnackHost(vm: AppViewModel, modifier: Modifier) {
    val sn = vm.snack ?: return
    LaunchedEffect(sn.id) { delay(if (sn.undo != null) 6000 else 3500); vm.clearSnack() }
    val wide = LocalWide.current
    val bottom = when {
        vm.screen == Screen.Workout && !wide -> if (vm.session?.o("rest") != null) 176.dp else 96.dp
        vm.screen == Screen.Tabs && !wide -> 96.dp
        else -> 24.dp
    }
    Snackbar(
        modifier = modifier.padding(start = 12.dp, end = 12.dp, bottom = bottom).navigationBarsPadding().widthIn(max = 480.dp),
        shape = RoundedCornerShape(16.dp),
        action = sn.undo?.let { u -> { TextButton(onClick = { vm.clearSnack(); u() }) { Text("Undo") } } },
    ) { Text(sn.text) }
}

@Composable
private fun ImportDialog(vm: AppViewModel) {
    val offer = vm.importOffer ?: return
    AlertDialog(
        onDismissRequest = { vm.importOffer = null },
        title = { Text(if (offer.source == "web app") "Bring your training over?" else "Import this backup?") },
        text = {
            Text("${offer.sessions} workouts and ${offer.programs} programs from the ${offer.source}. " +
                "Nothing on this phone is deleted: matching workouts merge, and the newer copy of each wins." +
                if (offer.live != null) " Your workout in progress comes too." else "")
        },
        confirmButton = { Button(onClick = { vm.acceptImport() }) { Text("Bring it over") } },
        dismissButton = { TextButton(onClick = { vm.importOffer = null }) { Text("Not now") } },
    )
}

/** A long engine job: dims the app, swallows taps, and says what is happening. */
@Composable
private fun BusyScrim(label: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.scrim.copy(alpha = 0.45f))
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center) {
        Row(Modifier.padding(32.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 24.dp, vertical = 20.dp)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite; contentDescription = "$label, please wait" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Text("$label…", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun StartupError(msg: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("The training engine didn't start", style = MaterialTheme.typography.headlineSmall)
        Text("Your data is safe on this phone. Close the app and open it again. If this keeps happening, update Android System WebView from the Play Store.", style = MaterialTheme.typography.bodyLarge)
        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Reused by screens: a standard scrolling page gutter. */
fun Modifier.pagePadding(wide: Boolean): Modifier = this.padding(horizontal = if (wide) 32.dp else 16.dp)

