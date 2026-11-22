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
/** Factual receipt-only cue for the native Kotlin UI timer; no legacy engine. */
internal data class NativeKotlinRestCue(
    val operationId:String,val seconds:Int,val exerciseIndex:Int
)

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
    autoCue: NativeKotlinRestCue? = null,
    /** Null disables persistent UI recovery for older, unscoped callers. */
    ownerId: String? = null
) {
    // A verified receipt may start rest from the previous exercise even if
    // the currently selected exercise has zero rest. Never unmount the timer
    // before observing that receipt or while its elapsed countdown is active.
    val manualRestTarget=prescribedSeconds in 1..3600
    val restStore=rememberNativeRestCountdownStore()
    val scopeKey=remember(workoutId,ownerId) {
        ownerId?.let {restStore.scopedKey(it,workoutId)}
    }
    val recovered=remember(scopeKey,latestReceiptOperationId) {
        scopeKey?.let {restStore.read(it,latestReceiptOperationId,
            SystemClock.elapsedRealtime(),System.currentTimeMillis())}
    }
    var deadline by rememberSaveable(workoutId,ownerId) {
        mutableLongStateOf(recovered?.deadlineElapsed ?:0L)
    }
    var startedElapsed by rememberSaveable(workoutId,ownerId) {
        mutableLongStateOf(recovered?.startedElapsed ?:0L)
    }
    var startedWall by rememberSaveable(workoutId,ownerId) {
        mutableLongStateOf(recovered?.startedWall ?:0L)
    }
    var activeSeconds by rememberSaveable(workoutId,ownerId) {
        mutableIntStateOf(recovered?.activeSeconds ?:0)
    }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Initialize from the *already visible* journal receipt. Re-entering
    // Train, rotating or recovering the app must not replay old rests.
    var observedEvent by rememberSaveable(workoutId,ownerId) {
        mutableStateOf(latestReceiptOperationId)
    }
    // A saved UI checkpoint can precede a durable write acknowledged while
    // the app was leaving. Baseline every fresh mount against the recovered
    // receipt, including restoration; never start that old rest on reopening.
    // An already-running saveable deadline is preserved independently.
    var receiptBaselineChecked by remember(workoutId) { mutableStateOf(false) }
    fun publish(checkpoint:NativeRestCountdownCheckpoint?) {
        deadline=checkpoint?.deadlineElapsed ?:0L
        startedElapsed=checkpoint?.startedElapsed ?:0L
        startedWall=checkpoint?.startedWall ?:0L
        activeSeconds=checkpoint?.activeSeconds ?:0
        restStore.save(scopeKey,latestReceiptOperationId,checkpoint)
    }
    fun begin(seconds: Int) {
        val elapsed=SystemClock.elapsedRealtime()
        now=elapsed
        publish(NativeRestCountdownPolicy.begin(
            seconds,elapsed,System.currentTimeMillis()))
    }
    fun adjust(seconds:Int) {
        val checkpoint=NativeRestCountdownCheckpoint(
            startedElapsed,startedWall,deadline,activeSeconds)
        val elapsed=SystemClock.elapsedRealtime()
        now=elapsed
        // An untrusted, stale or expired checkpoint cannot be extended.
        // An athlete may tap the normal issued rest target to restart it.
        if(!NativeRestCountdownPolicy.valid(
                checkpoint,elapsed,System.currentTimeMillis())) {
            publish(null)
            return
        }
        publish(NativeRestCountdownPolicy.adjust(
            checkpoint,seconds,elapsed,System.currentTimeMillis()))
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
                if(autoCue.seconds==0)publish(null)
                else begin(autoCue.seconds)
            }
        }
    }

    // A saved elapsedRealtime deadline is invalid after a device reboot.
    // Compare monotonic and wall time and bound expired historical timers.
    LaunchedEffect(deadline, startedElapsed, startedWall) {
        if (deadline <= 0L) return@LaunchedEffect
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime()
            val checkpoint=NativeRestCountdownCheckpoint(
                startedElapsed,startedWall,deadline,activeSeconds)
            if (!NativeRestCountdownPolicy.valid(
                    checkpoint,elapsed,System.currentTimeMillis())) {
                publish(null)
                break
            }
            now = elapsed
            if (elapsed >= deadline) break
            delay(250L)
        }
    }
    val checkpoint=NativeRestCountdownCheckpoint(
        startedElapsed,startedWall,deadline,activeSeconds)
    val remainingMaybe=NativeRestCountdownPolicy.remaining(
        checkpoint,now,System.currentTimeMillis())
    val valid=deadline>0L && remainingMaybe!=null
    val remaining=remainingMaybe ?:0L
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
            onLongClick = {publish(null)}
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
            if(valid && remaining>0L) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={adjust(-30)},enabled=enabled,
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("−30 s")
                    }
                    TextButton(onClick={adjust(30)},enabled=enabled &&
                        remaining<NativeRestCountdownPolicy.MAX_SECONDS,
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("+30 s")
                    }
                    TextButton(onClick={publish(null)},enabled=enabled,
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}
