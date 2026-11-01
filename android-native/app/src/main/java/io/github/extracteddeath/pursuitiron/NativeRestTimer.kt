package io.github.extracteddeath.pursuitiron

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * A presentation-only rest countdown. The target comes from the pinned,
 * engine-issued workout; this component never writes a set, modifies a
 * prescription, or claims to persist workout history.
 *
 * Elapsed time keeps ticking while the screen is backgrounded. Saveable UI
 * state survives destination changes and normal Activity state restoration,
 * but is deliberately not a durable journal or a notification service.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeRestTimer(workoutId: String, prescribedSeconds: Int, enabled: Boolean) {
    if (prescribedSeconds <= 0) return
    var deadline by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var startedElapsed by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var startedWall by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var activeSeconds by rememberSaveable(workoutId) { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // A saved elapsedRealtime deadline is invalid after a device reboot.
    // Compare elapsed and wall time; clock changes also fail closed instead
    // of leaving a phantom multi-hour countdown on the user's workout.
    fun hasValidClock(elapsed: Long, wall: Long): Boolean {
        if (startedElapsed <= 0L || startedWall <= 0L ||
            elapsed < startedElapsed || wall < startedWall) return false
        val monotonicAge = elapsed - startedElapsed
        val wallAge = wall - startedWall
        return abs(wallAge - monotonicAge) <= 60_000L
    }

    LaunchedEffect(deadline, startedElapsed, startedWall) {
        if (deadline <= 0L) return@LaunchedEffect
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime()
            if (!hasValidClock(elapsed, System.currentTimeMillis())) {
                deadline = 0L
                activeSeconds = 0
                break
            }
            now = elapsed
            if (elapsed >= deadline) break
            delay(250L)
        }
    }
    val valid = deadline > 0L && hasValidClock(now, System.currentTimeMillis())
    val remaining = if (!valid) 0L else
        ((deadline - now).coerceAtLeast(0L) + 999L) / 1000L
    val time = "%d:%02d".format(remaining / 60, remaining % 60)
    val title = when {
        !valid -> "Start rest timer"
        remaining > 0L -> "Rest · $time left"
        else -> "Rest complete"
    }
    // If the user switches exercises mid-countdown, the active rest period
    // remains unchanged; the next tap uses the currently selected target.
    val supporting = if (valid)
        "Current rest: $activeSeconds s · Tap for $prescribedSeconds s · Hold to reset"
    else "Tap for $prescribedSeconds seconds · Hold to reset"

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(
            enabled = enabled,
            onClickLabel = "Start or restart rest timer",
            onLongClickLabel = "Reset rest timer",
            onClick = {
                val current = SystemClock.elapsedRealtime()
                now = current
                startedElapsed = current
                startedWall = System.currentTimeMillis()
                activeSeconds = prescribedSeconds
                deadline = current + prescribedSeconds * 1000L
            },
            onLongClick = {
                deadline = 0L
                activeSeconds = 0
            }
        ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(supporting, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
