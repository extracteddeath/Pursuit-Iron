@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.pursuitiron.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.pursuitiron.app.ui.theme.Motion

/* Shared elements between top-level destinations (the design's view-transition names), e.g. the avatar
   that travels from Today's header to the middle of You. */
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalTabScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** A container that morphs to its namesake on the next destination: bounds on a spatial spring, scaled uniformly, contents cross-faded. */
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val s = LocalSharedScope.current ?: return this
    val a = LocalTabScope.current ?: return this
    return with(s) {
        this@sharedElement.sharedBounds(
            rememberSharedContentState(key), a,
            boundsTransform = { _, _ -> Motion.spatial() },
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit, Alignment.Center),
        )
    }
}
