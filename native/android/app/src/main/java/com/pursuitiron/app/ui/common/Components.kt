package com.pursuitiron.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.ui.theme.Motion

/** A tonal card: depth comes from the surface ladder, not shadows. */
@Composable
fun PiCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    radius: Dp = 28.dp,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val base = modifier.fillMaxWidth().clip(shape).background(color)
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Kicker(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(start = 4.dp, top = 8.dp))

/** Pill segmented control: the selected segment is secondary-container. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHigh, gap: Dp = 4.dp) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(track).padding(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, com.pursuitiron.app.ui.theme.Motion.effects(), label = "seg")
            Box(
                Modifier.weight(1f).height(height).clip(CircleShape).background(bg)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A chip that squares off when selected: shape carries state, not colour alone. The design's filter chip by
    default (selected: secondary container, squared; otherwise outlined pill); [strong] is the workout's lift chip
    and the chart metric tabs (selected: primary). [check] adds the design's tick to a selected muscle chip. */
@Composable
fun MorphChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, done: Boolean = false,
              height: Dp = 40.dp, strong: Boolean = false, check: Boolean = false) {
    val r by animateDpAsState(if (selected) 12.dp else height / 2, Motion.spring(), label = "chip")
    val cs = MaterialTheme.colorScheme
    val bg = when { selected && strong -> cs.primary; selected -> cs.secondaryContainer; done -> cs.primaryContainer; strong -> cs.surfaceContainerHigh; else -> Color.Transparent }
    val fg = when { selected && strong -> cs.onPrimary; selected -> cs.onSecondaryContainer; done -> cs.onPrimaryContainer; strong -> cs.onSurface; else -> cs.onSurfaceVariant }
    val outlined = !selected && !done && !strong
    Row(
        modifier.heightIn(min = height).clip(RoundedCornerShape(r)).background(bg)
            .then(if (outlined) Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(r)) else Modifier)
            .clickable(onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (check && selected) Icon(PiIcons.Check, null, tint = fg, modifier = Modifier.size(16.dp))
        // No weight here: inside a LazyRow the chip is measured with unbounded width, where a weighted
        // child gets no space at all and the label vanished. maxLines + ellipsis still bound it in a FlowRow.
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.8f))
    }
}

@Composable
fun StatTile(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, onClick: (() -> Unit)? = null) {
    PiCard(modifier, color = color, radius = 20.dp, padding = 14.dp, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))

@Composable
fun IconBubble(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

/** Line chart of a short series with the last point emphasised. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, description: String = "") {
    Canvas(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val pad = 6.dp.toPx()
        fun x(i: Int) = pad + i * (size.width - 2 * pad) / (values.size - 1)
        fun y(v: Double) = (size.height - pad - ((v - lo) / span) * (size.height - 2 * pad)).toFloat()
        val p = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val area = Path().apply { addPath(p); lineTo(x(values.size - 1), size.height); lineTo(x(0), size.height); close() }
        drawPath(area, color.copy(alpha = 0.12f))
        drawPath(p, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 5.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
    }
}

/** The brand's cookie: two rounded squares, one turned 45°. */
@Composable
fun Cookie(color: Color, modifier: Modifier = Modifier, radiusFraction: Float = 0.26f, alpha: Float = 1f) {
    Box(modifier) {
        val shape = RoundedCornerShape(percent = (radiusFraction * 100).toInt())
        Box(Modifier.fillMaxSize().clip(shape).background(color.copy(alpha = alpha)))
        Box(Modifier.fillMaxSize().rotate(45f).clip(shape).background(color.copy(alpha = alpha)))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    PiCard(modifier, radius = 32.dp, padding = 24.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Cookie(MaterialTheme.colorScheme.primaryContainer, Modifier.size(96.dp)) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (action != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { action() }
    }
}

@Composable
fun Badge(text: String, bg: Color = MaterialTheme.colorScheme.tertiaryContainer, fg: Color = MaterialTheme.colorScheme.onTertiaryContainer) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
