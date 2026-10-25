package com.pursuitiron.app.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/* The design's program icon tile (prototype iconTile): icon, colour and shape. */
object ProgramIconStyle {
    val colors = listOf("theme" to null, "lime" to Color(0xFFB8E62E), "green" to Color(0xFF4ADE80), "teal" to Color(0xFF2DD4BF), "cyan" to Color(0xFF3ECFCF),
        "blue" to Color(0xFF4C8DFF), "violet" to Color(0xFFA78BFA), "pink" to Color(0xFFF472B6), "red" to Color(0xFFF0553F), "orange" to Color(0xFFFB923C),
        "amber" to Color(0xFFF0A93B), "slate" to Color(0xFF94A3B8))
    val colorLabel = mapOf("theme" to "Theme", "lime" to "Lime", "green" to "Green", "teal" to "Teal", "cyan" to "Cyan", "blue" to "Blue", "violet" to "Violet",
        "pink" to "Pink", "red" to "Red", "orange" to "Orange", "amber" to "Amber", "slate" to "Slate")
    val shapes = listOf("circle" to "Circle", "rounded" to "Rounded", "leaf" to "Leaf", "blob" to "Blob")
    fun shape(id: String): Shape = when (id) {
        "circle" -> CircleShape
        "leaf" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 14, bottomEndPercent = 50, bottomStartPercent = 14)
        "blob" -> RoundedCornerShape(topStartPercent = 47, topEndPercent = 51, bottomEndPercent = 51, bottomStartPercent = 51)
        else -> RoundedCornerShape(30)
    }
    private val cache = HashMap<String, ImageVector>()
    fun vector(name: String): ImageVector = cache.getOrPut(name) {
        val d = ProgramIconLib.all.firstOrNull { it.first == name }?.third ?: ProgramIconLib.all.first().third
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }
}

@Composable
fun IconTile(icon: JSONObject?, size: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val col = ProgramIconStyle.colors.firstOrNull { it.first == icon.s("color", "theme") }?.second
    val bg = col ?: cs.primaryContainer
    val fg = if (col != null) (if (col.luminance() > 0.45f) Color(0xFF16130F) else Color.White) else cs.onPrimaryContainer
    Box(modifier.size(size).clip(ProgramIconStyle.shape(icon.s("shape", "rounded"))).background(bg), contentAlignment = Alignment.Center) {
        Icon(ProgramIconStyle.vector(icon.s("icon", "dumbbell")), null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}
