package com.pursuitiron.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A horizontally scrolling chip row that fades out at whichever edge has more to scroll to, so a chip is never
    cut off flat at the screen edge ("Last yea") with nothing to say the row continues. */
fun Modifier.fadingHScroll(): Modifier = composed {
    val st = rememberScrollState()
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = 24.dp.toPx()
            if (st.value > 0) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                topLeft = Offset.Zero, size = Size(w, size.height), blendMode = BlendMode.DstIn)
            if (st.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), blendMode = BlendMode.DstIn)
        }
        .horizontalScroll(st)
}

/** The same fade for a LazyRow, driven by its list state. */
fun Modifier.lazyFade(st: androidx.compose.foundation.lazy.LazyListState): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = 24.dp.toPx()
            if (st.canScrollBackward) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                topLeft = Offset.Zero, size = Size(w, size.height), blendMode = BlendMode.DstIn)
            if (st.canScrollForward) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), blendMode = BlendMode.DstIn)
        }
