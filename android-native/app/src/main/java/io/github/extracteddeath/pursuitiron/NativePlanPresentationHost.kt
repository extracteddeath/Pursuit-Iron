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
    LaunchedEffect(session, requestVersion) {
        val result = session.refresh()
        if (coroutineContext.isActive && session.current() === result) {
            presentation = result
        }
    }
    return presentation
}
