package com.pursuitiron.app.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.ui.theme.Motion
import com.pursuitiron.app.ui.theme.Seed
import com.pursuitiron.app.ui.theme.swatchFor

/**
 * The design's colour picker (Settings → Color): six to a row, each a circle split like Android's own
 * wallpaper picker — primary on top, tertiary and secondary containers below. The chosen one morphs
 * to a rounded square with a check. Wallpaper is only offered on Android 12 and up.
 */
@Composable
fun ThemePicker(selected: Seed, dark: Boolean, onPick: (Seed) -> Unit, onUnavailable: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Seed.entries.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { seed ->
                    val on = seed == selected
                    val available = !seed.isDynamic || Seed.dynamicSupported
                    val sw = swatchFor(seed, dark, context)
                    val corner by animateDpAsState(if (on) 16.dp else 24.dp, Motion.spring(), label = "swatch")
                    val inner by animateDpAsState(if (on) 12.dp else 20.dp, Motion.spring(), label = "swatchInner")
                    val cs = MaterialTheme.colorScheme
                    val desc = seed.label + (if (seed.isDynamic) " theme, colours from your wallpaper" else " theme") +
                        (if (!available) ", needs Android 12 or newer" else "")
                    Column(
                        Modifier.weight(1f)
                            .alpha(if (available) 1f else 0.45f)
                            .clickable { if (!available) onUnavailable("Wallpaper colours need Android 12 or newer") else if (!on) onPick(seed) }
                            .semantics(mergeDescendants = true) { role = Role.RadioButton; this.selected = on; contentDescription = desc },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(corner))
                                .border(if (on) 2.5.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(corner))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(inner))) {
                                val h = size.height / 2f
                                drawRect(sw.top, Offset.Zero, Size(size.width, h))
                                drawRect(sw.left, Offset(0f, h), Size(size.width / 2f, h))
                                drawRect(sw.right, Offset(size.width / 2f, h), Size(size.width / 2f, h))
                            }
                            if (on) Box(Modifier.size(22.dp).clip(CircleShape).background(sw.top), contentAlignment = Alignment.Center) {
                                Icon(PiIcons.Check, null, Modifier.size(14.dp), tint = if (sw.top.lum() > 0.45f) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)
                            }
                        }
                        Text(
                            seed.label, maxLines = 1, overflow = TextOverflow.Visible, softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = if (on) FontWeight(700) else FontWeight(500)),
                            color = if (on) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }
                repeat(6 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
