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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(deadline) {
        if (deadline <= 0L) return@LaunchedEffect
        now = SystemClock.elapsedRealtime()
        while (isActive && now < deadline) {
            delay(250L)
            now = SystemClock.elapsedRealtime()
        }
    }
    val remaining = if (deadline <= 0L) 0L else
        ((deadline - now).coerceAtLeast(0L) + 999L) / 1000L
    val time = "%d:%02d".format(remaining / 60, remaining % 60)
    val title = when {
        deadline <= 0L -> "Start rest timer"
        remaining > 0L -> "Rest · $time left"
        else -> "Rest complete"
    }
    val supporting = if (deadline > 0L)
        "Tap to restart for $prescribedSeconds seconds · Hold to reset"
    else "Tap for $prescribedSeconds seconds · Hold to reset"

    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(
            enabled = enabled,
            onClickLabel = "Start or restart rest timer",
            onLongClickLabel = "Reset rest timer",
            onClick = {
                val current = SystemClock.elapsedRealtime()
                now = current
                deadline = current + prescribedSeconds * 1000L
            },
            onLongClick = { deadline = 0L }
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
