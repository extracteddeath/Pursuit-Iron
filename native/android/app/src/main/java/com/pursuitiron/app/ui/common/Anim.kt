package com.pursuitiron.app.ui.common

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.ui.theme.Motion
import kotlinx.coroutines.delay

/* The prototype's entrance motion (Main.dc.html .stg / .pop / .chipIn / .draw / .tick), on the app's motion tokens.
   Every one of them settles instantly under Reduce motion. */

/** One screen entry: content composed in its first moments rises in, 20 ms apart (.stg); content that
    scrolls in later just appears, as it does in the prototype. */
class StaggerClock { val start = SystemClock.uptimeMillis(); var n = 0 }
val LocalStagger = staticCompositionLocalOf<StaggerClock?> { null }

@Composable
fun Rise(content: @Composable () -> Unit) {
    val clock = LocalStagger.current
    val anim = remember {
        if (clock == null || Motion.reduced || SystemClock.uptimeMillis() - clock.start > 450) null
        else Animatable(0f) to clock.n++
    }
    LaunchedEffect(Unit) { anim?.let { (a, i) -> delay(minOf(i, 6) * 20L); a.animateTo(1f, Motion.enter(400)) } }
    Box(if (anim == null) Modifier else Modifier.graphicsLayer { val v = anim.first.value; alpha = v; translationY = (1 - v) * 12.dp.toPx() }) { content() }
}

/** .pop / .chipIn: scale up from [from] with the fast spatial spring (a slight overshoot), fading in. */
fun Modifier.popIn(delayMs: Int = 0, from: Float = 0.6f, origin: TransformOrigin = TransformOrigin.Center): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) { delay(delayMs.toLong()); a.animateTo(1f, Motion.spatialFast()) } }
    graphicsLayer { val v = a.value; val sc = from + (1 - from) * v; scaleX = sc; scaleY = sc; alpha = v.coerceIn(0f, 1f); transformOrigin = origin }
}

/** .chipIn: a row of chips arriving one after another. */
fun Modifier.chipIn(index: Int): Modifier = popIn(delayMs = index * 20, from = 0.8f)

/** .draw: the check mark drawing itself (M5 12.5l4.5 4.5L19 7 on a 24 grid), 400 ms after a 150 ms beat. */
@Composable
fun DrawnCheck(color: Color, modifier: Modifier, stroke: Dp = 2.8.dp, animate: Boolean = true) {
    val p = remember { Animatable(if (Motion.reduced || !animate) 1f else 0f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, tween(400, 150, Motion.EmphasizedDecelerate)) }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val ox = (size.width - 24 * s) / 2; val oy = (size.height - 24 * s) / 2
        val path = Path().apply { moveTo(ox + 5 * s, oy + 12.5f * s); lineTo(ox + 9.5f * s, oy + 17 * s); lineTo(ox + 19 * s, oy + 7 * s) }
        val out = Path(); PathMeasure().apply { setPath(path, false); getSegment(0f, length * p.value, out, true) }
        drawPath(out, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** .tick: each digit rolls to its new value, up when the number grows and down when it shrinks. */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val last = remember { arrayOf(text) }
    val up = (text.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0) >= (last[0].filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0)
    SideEffect { last[0] = text }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            // Keyed from the right so the ones digit stays the ones digit when the number gains a digit.
            key(text.length - i) {
                AnimatedContent(ch, transitionSpec = {
                    val d = if (up) 1 else -1
                    (slideInVertically(Motion.spatial()) { d * it / 2 } + fadeIn(Motion.effects())) togetherWith
                        (slideOutVertically(Motion.spatial()) { -d * it / 2 } + fadeOut(Motion.effectsFast()))
                }, label = "tick") { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** .hbar: a horizontal bar revealed from its start, without overshooting the value it shows. */
fun Modifier.growX(delayMs: Int = 0): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, Motion.data(500, delayMs)) }
    drawWithContent { clipRect(right = size.width * a.value) { this@drawWithContent.drawContent() } }
}

/** .prglow: one ring of the colour spreading out and fading, once, when a record appears. */
fun Modifier.prGlow(color: Color, radius: Dp): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, tween(1200, 0, Motion.EmphasizedDecelerate)) }
    drawBehind {
        val t = a.value; if (t >= 1f) return@drawBehind
        val spread = 22.dp.toPx() * t
        drawRoundRect(color.copy(alpha = 0.6f * (1 - t)), topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * spread, size.height + 2 * spread),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + spread))
    }
}

/** .ring: a progress ring sweeps to its value (600 ms, emphasized decelerate) when it first appears, and glides to a
    new value after. Multiply the arc's sweep by the returned fraction of its target. */
@Composable
fun ringSweep(target: Float): Float {
    val a = remember { Animatable(if (Motion.reduced) target else 0f) }
    LaunchedEffect(target) { a.animateTo(target, Motion.data(600)) }
    return a.value
}

/** .bars .bar: a vertical bar revealed from its base, each one 20 ms after the one before (barGrow). */
fun Modifier.growY(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (a.value < 1f) a.animateTo(1f, Motion.data(500, minOf(index, 7) * 20)) }
    drawWithContent { clipRect(top = size.height * (1 - a.value)) { this@drawWithContent.drawContent() } }
}

/** In place of "Loading…": nothing for the first 250 ms (most reads land sooner, so nothing flashes), then three soft
    placeholder lines breathing gently until the content arrives. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    val pulse = remember { Animatable(0.45f) }
    LaunchedEffect(Unit) {
        delay(250); shown.animateTo(1f, Motion.enter(200))
        if (!Motion.reduced) while (true) { pulse.animateTo(0.8f, tween(700)); pulse.animateTo(0.45f, tween(700)) }
    }
    val c = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
    androidx.compose.foundation.layout.Column(modifier.graphicsLayer { alpha = shown.value }.semantics { contentDescription = "Loading" },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        listOf(0.9f, 0.7f, 0.5f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(16.dp).graphicsLayer { alpha = pulse.value }
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(c))
        }
    }
}
