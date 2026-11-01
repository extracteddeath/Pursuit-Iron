package io.github.extracteddeath.pursuitiron

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.extracteddeath.pursuitiron.domain.CurrentReadOnlyPlanSource
import io.github.extracteddeath.pursuitiron.domain.CurrentReadOnlyPlanGateway
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyPlanPresentationSession
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramPresentation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Ephemeral native Plan host, prepared for a future authoritative current-store
 * adapter. This host never treats a selected backup as the current program.
 * The caller must forward every canonical store change via storeChangeKey.
 */
@Composable
internal fun rememberNativeCurrentPlanPresentation(
    selectedProgramId: String?,
    source: CurrentReadOnlyPlanSource,
    storeChangeKey: Long
): ReadOnlyProgramPresentation {
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(source) {
        ReadOnlyPlanPresentationSession(CurrentReadOnlyPlanGateway(source))
    }
    // Drop old data during recomposition itself, before lifecycle effects run.
    // This also handles navigation Crossfade retaining an outgoing screen.
    var presentation by remember(session, selectedProgramId, storeChangeKey) {
        mutableStateOf<ReadOnlyProgramPresentation>(ReadOnlyProgramPresentation.Unavailable)
    }
    var requestVersion by remember(session, selectedProgramId, storeChangeKey) {
        mutableIntStateOf(0)
    }
    var sourceObserved by remember(session) { mutableStateOf(false) }
    val invalidations = remember(session) { Channel<Unit>(Channel.CONFLATED) }

    // A current store with no live change subscription is NOT authorized for
    // display. Callbacks may arrive off-main: invalidate the thread-safe
    // session first, then marshal Compose state changes via a channel.
    DisposableEffect(source, session, selectedProgramId) {
        val active = AtomicBoolean(true)
        // Never subscribe while Today, Train or Insights is selected.
        val subscription = if (selectedProgramId == null) null else runCatching {
            source.subscribeCanonicalChanges {
                if (active.get()) {
                    session.onCanonicalStoreChanged()
                    invalidations.trySend(Unit)
                }
            }
        }.getOrNull()
        sourceObserved = selectedProgramId != null && subscription != null
        if (selectedProgramId != null && subscription == null) {
            session.onBackground()
            presentation = ReadOnlyProgramPresentation.Unavailable
        }
        onDispose {
            active.set(false)
            sourceObserved = false
            runCatching { subscription?.close() }
            session.onBackground()
        }
    }
    LaunchedEffect(session, invalidations) {
        for (ignored in invalidations) {
            presentation = ReadOnlyProgramPresentation.Unavailable
            requestVersion++
        }
    }

    DisposableEffect(lifecycleOwner, session, selectedProgramId, storeChangeKey) {
        fun clear() {
            session.onBackground()
            presentation = ReadOnlyProgramPresentation.Unavailable
            requestVersion++
        }
        fun enter() {
            if (selectedProgramId == null) {
                clear()
            } else {
                session.onForeground(selectedProgramId)
                presentation = ReadOnlyProgramPresentation.Unavailable
                requestVersion++
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> enter()
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> clear()
                else -> Unit
            }
        }
        // LifecycleRegistry advances new observers to the current state.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clear()
        }
    }
    LaunchedEffect(session, requestVersion, sourceObserved) {
        if (!sourceObserved) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { session.refresh() }
        if (coroutineContext.isActive && session.current() === result) {
            presentation = result
        }
    }
    // A canonical revision callback invalidates session.current() before its
    // UI-channel event is processed, so no subsequent recomposition can reuse
    // a previously approved but now-stale presentation.
    return if (sourceObserved && session.current() === presentation)
        presentation else ReadOnlyProgramPresentation.Unavailable
}
