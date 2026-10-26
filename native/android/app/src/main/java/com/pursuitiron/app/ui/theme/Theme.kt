package com.pursuitiron.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.R

/* Roboto Flex at its normal width, as M3 sets it: display = wght 700 / opsz 96; emphasis = wght 600. Hierarchy comes from size and weight, never from stretching the letters. */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, width: Float = 100f, opsz: Float? = null) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = if (opsz != null) FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width), FontVariation.Setting("opsz", opsz))
    else FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val DisplayFamily = FontFamily(flex(700, 100f, 96f))
val EmphFamily = FontFamily(flex(600, 100f))
val BodyFamily = FontFamily(flex(400), flex(500), flex(600), flex(700), flex(800))

private val Base = Typography()
val PiTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 84.sp, lineHeight = 84.sp),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontSize = 34.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = EmphFamily, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = EmphFamily, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = EmphFamily, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight(600)),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(700)),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(600)),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight(800), letterSpacing = 0.4.sp),
)

/* Radii from the design system: row 18, sheet 28, card 32, hero 36. */
val PiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/* M3 Expressive motion tokens (m3.material.io/styles/motion). Spatial springs move, resize and reshape things and
   settle with a slight overshoot; effect springs change colour and opacity and never overshoot. Entering content
   uses emphasized decelerate, leaving content emphasized accelerate. Every animation in the app picks from these. */
object Motion {
    fun <T> spatialFast() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatial() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow() = spring<T>(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effectsFast() = spring<T>(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectsSlow() = spring<T>(dampingRatio = 1f, stiffness = 800f)
    val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** Data (bars, rings, lines) grows in without overshooting the value it shows. */
    fun <T> data(ms: Int = 500, delay: Int = 0) = androidx.compose.animation.core.tween<T>(ms, delay, EmphasizedDecelerate)
    fun <T> enter(ms: Int = 400, delay: Int = 0) = androidx.compose.animation.core.tween<T>(ms, delay, EmphasizedDecelerate)
    fun <T> exit(ms: Int = 200) = androidx.compose.animation.core.tween<T>(ms, 0, EmphasizedAccelerate)
    /** Shape morphs (a chip squaring off, a check settling). */
    fun <T> spring() = spatialFast<T>()

    /** Expand/collapse a region in place. */
    val expandIn get() = androidx.compose.animation.expandVertically(spatial()) + androidx.compose.animation.fadeIn(effects())
    val shrinkOut get() = androidx.compose.animation.shrinkVertically(spatialFast()) + androidx.compose.animation.fadeOut(effectsFast())
    /** A number rolling to its new value. */
    fun tick() = (androidx.compose.animation.slideInVertically(spatial()) { it / 2 } + androidx.compose.animation.fadeIn(effects())) togetherWith
        (androidx.compose.animation.slideOutVertically(spatial()) { -it / 2 } + androidx.compose.animation.fadeOut(effectsFast()))
    /** Top-level destinations (the tabs): fade through. */
    fun fadeThrough() = (androidx.compose.animation.fadeIn(enter(210, 90)) + androidx.compose.animation.scaleIn(enter(210, 90), initialScale = 0.96f)) togetherWith
        androidx.compose.animation.fadeOut(exit(90))
    /** Forward into a full-screen task (workout, builder, editor) and back. */
    fun forward() = (androidx.compose.animation.fadeIn(enter(300, 60)) + androidx.compose.animation.scaleIn(spatial(), initialScale = 0.92f)) togetherWith
        androidx.compose.animation.fadeOut(exit(90))
    fun peer() = (androidx.compose.animation.fadeIn(effects())) togetherWith androidx.compose.animation.fadeOut(effectsFast())
}

@Composable
fun PiTheme(seed: Seed = Seed.Ember, dark: Boolean = isSystemInDarkTheme(), black: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The expressive motion scheme, so Material's own components (switches, the navigation bar, sheets) move like the rest.
    @OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    MaterialTheme(colorScheme = schemeFor(seed, dark, black, context), motionScheme = androidx.compose.material3.MotionScheme.expressive(), typography = PiTypography, shapes = PiShapes, content = content)
}
