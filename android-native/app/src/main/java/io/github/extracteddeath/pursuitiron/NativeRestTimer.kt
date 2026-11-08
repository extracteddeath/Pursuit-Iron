package io.github.extracteddeath.pursuitiron

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import io.github.extracteddeath.pursuitiron.domain.NativeRestCue
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
internal fun NativeRestTimer(
    workoutId: String,
    prescribedSeconds: Int,
    enabled: Boolean,
    latestReceiptOperationId: String? = null,
    autoCue: NativeRestCue? = null
) {
    // A verified receipt may start rest from the previous exercise even if
    // the currently selected exercise has zero rest. Never unmount the timer
    // before observing that receipt or while its elapsed countdown is active.
    val manualRestTarget=prescribedSeconds in 1..3600
    var deadline by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var startedElapsed by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var startedWall by rememberSaveable(workoutId) { mutableLongStateOf(0L) }
    var activeSeconds by rememberSaveable(workoutId) { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Initialize from the *already visible* journal receipt. Re-entering
    // Train, rotating or recovering the app must not replay old rests.
    var observedEvent by rememberSaveable(workoutId) {
        mutableStateOf(latestReceiptOperationId)
    }
    // A saved UI checkpoint can precede a durable write acknowledged while
    // the app was leaving. Baseline every fresh mount against the recovered
    // receipt, including restoration; never start that old rest on reopening.
    // An already-running saveable deadline is preserved independently.
    var receiptBaselineChecked by remember(workoutId) { mutableStateOf(false) }
    fun begin(seconds: Int) {
        if(seconds !in 1..3600)return
        val elapsed=SystemClock.elapsedRealtime()
        now=elapsed
        startedElapsed=elapsed
        startedWall=System.currentTimeMillis()
        activeSeconds=seconds
        deadline=elapsed+seconds*1000L
    }
    LaunchedEffect(latestReceiptOperationId,enabled) {
        if(!receiptBaselineChecked) {
            observedEvent=latestReceiptOperationId
            receiptBaselineChecked=true
        } else if(enabled && latestReceiptOperationId != null && latestReceiptOperationId!=observedEvent) {
            observedEvent=latestReceiptOperationId
            // Skipped/corrected sets don't produce an auto cue. The journal
            // receipt, not a pending Log click, is what starts the countdown.
            if(autoCue?.operationId==latestReceiptOperationId) {
                // An explicitly immediate Kotlin follow-up must not inherit
                // a countdown still running from the preceding normal set.
                if(autoCue.seconds==0) {
                    deadline=0L
                    activeSeconds=0
                } else begin(autoCue.seconds)
            }
        }
    }

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
    // Manual restart follows the CURRENT exercise's issued rest target,
    // not an invented fallback borrowed from the last completed movement.
    // A zero-rest exercise can still DISPLAY the preceding saved set's timer.
    val supporting = when {
        !manualRestTarget -> "Rest from last saved set · No rest target on this exercise"
        valid -> "Tap to restart at $prescribedSeconds s · Hold to clear"
        else -> "Tap to start $prescribedSeconds s · Hold to clear"
    }
    // Keep saveable state and receipt effects mounted even when no rest is
    // currently displayed. Never expose an inert tap action for a zero target.
    if(!manualRestTarget && (!valid || remaining==0L)) return
    val timerModifier=if(manualRestTarget)
        Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(
            enabled = enabled,
            onClickLabel = "Start or restart rest timer",
            onLongClickLabel = "Reset rest timer",
            onClick = { begin(prescribedSeconds) },
            onLongClick = {
                deadline = 0L
                activeSeconds = 0
            }
        ) else Modifier.fillMaxWidth().heightIn(min = 64.dp)

    Surface(
        modifier = timerModifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            if(valid && activeSeconds>0) {
                LinearProgressIndicator(
                    progress={ (remaining.toFloat()/activeSeconds).coerceIn(0f,1f) },
                    modifier=Modifier.fillMaxWidth())
            }
            Text(supporting, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            if(!manualRestTarget && enabled) TextButton(onClick = {
                deadline = 0L
                activeSeconds = 0
            }) { Text("Clear rest") }
        }
    }
}
