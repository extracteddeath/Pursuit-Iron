package com.pursuitiron.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.R

/* Roboto Flex, variable. Display = wdth 118 / wght 780 / opsz 96; emphasis = wdth 108 / wght 680. */
@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int, width: Float = 100f, opsz: Float? = null) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    variationSettings = if (opsz != null) FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width), FontVariation.Setting("opsz", opsz))
    else FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val DisplayFamily = FontFamily(flex(780, 118f, 96f))
val EmphFamily = FontFamily(flex(680, 108f))
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

/* Motion tokens. ease-spring ≈ medium-low stiffness with ~8% overshoot. */
object Motion {
    fun <T> spring() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    fun <T> springStrong() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

@Composable
fun PiTheme(seed: Seed = Seed.Ember, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(seed, dark), typography = PiTypography, shapes = PiShapes, content = content)
}
