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
import androidx.compose.runtime.saveable.Saver
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

/** A journal receipt and its rest projection can reach Compose in separate
 * recompositions. A cue must remain eligible until that exact receipt is
 * acknowledged by the timer; neither an older cue nor a remounted prior
 * receipt may restart an elapsed countdown. */
internal object NativeRestReceiptCuePolicy {
    fun newCue(
        observedReceiptId:String?,
        latestReceiptId:String?,
        cue:NativeKotlinRestCue?
    ):NativeKotlinRestCue? =
        cue?.takeIf {
            latestReceiptId!=null && latestReceiptId!=observedReceiptId &&
                it.operationId==latestReceiptId
        }
}

private val restCheckpointSaver = Saver<NativeRestCountdownCheckpoint?, String>(
    save = { it?.let(NativeRestCountdownPolicy::encode) },
    restore = NativeRestCountdownPolicy::decode
)

/**
 * A presentation-only rest countdown. The target comes from the pinned,
 * engine-issued workout; this component never writes a set, modifies a
 * prescription, or claims to persist workout history.
 *
 * The scoped checkpoint is recovered against the latest workout receipt
 * on each mount. Unscoped preview callers retain activity-local state.
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
    var active by if (scopeKey != null) {
        remember(scopeKey) {
            mutableStateOf(restStore.read(scopeKey,latestReceiptOperationId,
                SystemClock.elapsedRealtime(),System.currentTimeMillis()))
        }
    } else {
        key(workoutId,ownerId) {
            rememberSaveable(stateSaver=restCheckpointSaver) {
                mutableStateOf<NativeRestCountdownCheckpoint?>(null)
            }
        }
    }
    var now by remember(workoutId,ownerId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var observedEvent by remember(workoutId,ownerId) { mutableStateOf(latestReceiptOperationId) }
    fun publish(checkpoint:NativeRestCountdownCheckpoint?) {
        active=checkpoint
        restStore.save(scopeKey,latestReceiptOperationId,checkpoint)
    }
    fun begin(seconds: Int) {
        val elapsed=SystemClock.elapsedRealtime()
        now=elapsed
        publish(NativeRestCountdownPolicy.begin(
            seconds,elapsed,System.currentTimeMillis()))
    }
    fun adjust(seconds:Int) {
        val checkpoint=active ?: return
        val elapsed=SystemClock.elapsedRealtime()
        now=elapsed
        publish(NativeRestCountdownPolicy.adjust(
            checkpoint,seconds,elapsed,System.currentTimeMillis()))
    }
    LaunchedEffect(workoutId,ownerId,latestReceiptOperationId,autoCue,enabled) {
        if(enabled && latestReceiptOperationId != null &&
            latestReceiptOperationId!=observedEvent) {
            val confirmedCue=NativeRestReceiptCuePolicy.newCue(
                observedEvent,latestReceiptOperationId,autoCue)
            if(confirmedCue==null) {
                // A skip/correction has no rest cue. Clear stale rest from
                // the previous receipt, but leave this ID unacknowledged:
                // a delayed matching cue may still arrive in a later frame.
                publish(null)
            } else {
                observedEvent=latestReceiptOperationId
                // Immediate amplifier stages clear the preceding countdown.
                if(confirmedCue.seconds==0)publish(null)
                else begin(confirmedCue.seconds)
            }
        }
    }

    // A saved elapsedRealtime deadline is invalid after a device reboot.
    // Compare monotonic and wall time and bound expired historical timers.
    LaunchedEffect(workoutId,ownerId,active) {
        val checkpoint=active ?: return@LaunchedEffect
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime()
            if (!NativeRestCountdownPolicy.valid(checkpoint,elapsed,System.currentTimeMillis())) {
                publish(null)
                break
            }
            now = elapsed
            if (elapsed >= checkpoint.deadlineElapsed) break
            delay(250L)
        }
    }
    val remainingMaybe=active?.let {
        NativeRestCountdownPolicy.remaining(it,now,System.currentTimeMillis())
    }
    val valid=remainingMaybe!=null
    val activeSeconds=active?.activeSeconds ?: 0
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
                        activeSeconds<NativeRestCountdownPolicy.MAX_SECONDS,
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
