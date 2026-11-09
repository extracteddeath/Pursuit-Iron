package com.pursuitiron.app.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pursuitiron.app.ui.theme.Motion

/**
 * Drag to reorder a short list (a day's lifts, the days of a program, today's exercises). Drag a row by its handle: it
 * lifts and follows your finger, the rows it passes slide out of the way, and letting go commits one move from where it
 * started to where it landed. TalkBack gets "Move up" / "Move down" actions on every row instead of the drag.
 */
@Composable
fun <T> ReorderColumn(
    items: List<T>, keyOf: (T) -> Any, onMove: (from: Int, to: Int) -> Unit, modifier: Modifier = Modifier, gap: Dp = 6.dp,
    labelOf: (T) -> String = { "" },
    row: @Composable (item: T, index: Int, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var pending by remember { mutableStateOf(false) }
    val order = items.map(keyOf)
    // Reset in the same pass the new order arrives, so the moved row is never drawn lifted at its old place.
    val seen = remember { mutableStateOf(order) }
    if (seen.value != order) { seen.value = order; if (pending) { pending = false; dragIdx = null; offset = 0f } }
    androidx.compose.runtime.LaunchedEffect(pending) { if (pending) { kotlinx.coroutines.delay(1500); if (pending) { pending = false; dragIdx = null; offset = 0f } } }
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val haptic = LocalHapticFeedback.current
    val count by rememberUpdatedState(items.size)
    val move by rememberUpdatedState(onMove)
    // Where the dragged row would land: it passes a neighbour once it is more than halfway over it.
    fun target(from: Int, dy: Float): Int {
        var t = from; var acc = dy
        if (acc > 0) { var i = from + 1; while (i < count) { val h = (heights[i] ?: 0) + gapPx; if (acc > h / 2) { t = i; acc -= h; i++ } else break } }
        else { var i = from - 1; while (i >= 0) { val h = (heights[i] ?: 0) + gapPx; if (-acc > h / 2) { t = i; acc += h; i-- } else break } }
        return t
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items.forEachIndexed { i, item ->
            key(keyOf(item)) {
                val from = dragIdx
                val t = from?.let { target(it, offset) }
                val dh = from?.let { (heights[it] ?: 0) + gapPx } ?: 0f
                val shift = when {
                    from == null || t == null || i == from -> 0f
                    t > from && i in (from + 1)..t -> -dh
                    t < from && i in t until from -> dh
                    else -> 0f
                }
                // Rows slide aside while dragging; once the list itself reorders they snap (their real position changed).
                val slideAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(shift, from == null) { if (from == null) slideAnim.snapTo(0f) else slideAnim.animateTo(shift, Motion.spatialFast()) }
                val slide = slideAnim.value
                val lifting = i == from
                val handle = Modifier.pointerInput(i) {
                    detectDragGestures(
                        onDragStart = { dragIdx = i; offset = 0f; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDrag = { c, d -> c.consume(); val before = dragIdx?.let { target(it, offset) }; offset += d.y
                            if (dragIdx?.let { target(it, offset) } != before) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        // A committed move holds the lifted row where it was dropped until the list reorders (no snap back).
                        onDragEnd = { val f = dragIdx; val to = f?.let { target(it, offset) }; if (f != null && to != null && to != f) { pending = true; move(f, to) } else { dragIdx = null; offset = 0f } },
                        onDragCancel = { dragIdx = null; offset = 0f },
                    )
                }
                Box(Modifier.onSizeChanged { heights[i] = it.height }.zIndex(if (lifting) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (lifting) offset else slide
                        if (lifting) { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 12.dp.toPx(); shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp); clip = false }
                    }
                    .semantics {
                        val name = labelOf(item)
                        customActions = listOfNotNull(
                            if (i > 0) CustomAccessibilityAction("Move ${name} up") { move(i, i - 1); true } else null,
                            if (i < items.size - 1) CustomAccessibilityAction("Move ${name} down") { move(i, i + 1); true } else null,
                        )
                    }) {
                    row(item, i, handle, lifting)
                }
            }
        }
    }
}
