package io.github.extracteddeath.pursuitiron

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native-only brand tokens. Components consume semantic M3 color roles,
 * never hard-coded per-screen fills. Graphite remains neutral while violet
 * supplies an intentional, legible accent in both system themes.
 */
internal val PursuitDarkColors=darkColorScheme(
    primary=Color(0xFFCFBCFF),
    onPrimary=Color(0xFF30204B),
    primaryContainer=Color(0xFF493568),
    onPrimaryContainer=Color(0xFFF0E9FF),
    secondary=Color(0xFFC6C3CF),
    onSecondary=Color(0xFF302F37),
    secondaryContainer=Color(0xFF3E3B47),
    onSecondaryContainer=Color(0xFFE5E1ED),
    tertiary=Color(0xFFC5D0FA),
    onTertiary=Color(0xFF243052),
    background=Color(0xFF111217),
    onBackground=Color(0xFFF0EFF5),
    surface=Color(0xFF13141A),
    onSurface=Color(0xFFF0EFF5),
    surfaceVariant=Color(0xFF33343E),
    onSurfaceVariant=Color(0xFFC5C5D1),
    surfaceContainerLowest=Color(0xFF0D0E13),
    surfaceContainerLow=Color(0xFF1C1D24),
    surfaceContainer=Color(0xFF23242C),
    surfaceContainerHigh=Color(0xFF2B2C35),
    surfaceContainerHighest=Color(0xFF363741),
    outline=Color(0xFF91919F),
    outlineVariant=Color(0xFF484954),
    error=Color(0xFFFFB4AB),
    onError=Color(0xFF690005)
)

internal val PursuitLightColors=lightColorScheme(
    primary=Color(0xFF60428C),
    onPrimary=Color(0xFFFFFFFF),
    primaryContainer=Color(0xFFE8DCFF),
    onPrimaryContainer=Color(0xFF382157),
    secondary=Color(0xFF605C6A),
    onSecondary=Color(0xFFFFFFFF),
    secondaryContainer=Color(0xFFE7E1EF),
    onSecondaryContainer=Color(0xFF35313F),
    tertiary=Color(0xFF4B5985),
    onTertiary=Color(0xFFFFFFFF),
    background=Color(0xFFF9F8FC),
    onBackground=Color(0xFF1B1B23),
    surface=Color(0xFFF9F8FC),
    onSurface=Color(0xFF1B1B23),
    surfaceVariant=Color(0xFFE5E3EC),
    onSurfaceVariant=Color(0xFF494954),
    surfaceContainerLowest=Color(0xFFFFFFFF),
    surfaceContainerLow=Color(0xFFF2F0F7),
    surfaceContainer=Color(0xFFEDEBF3),
    surfaceContainerHigh=Color(0xFFE7E5ED),
    surfaceContainerHighest=Color(0xFFDFDDE6),
    outline=Color(0xFF777782),
    outlineVariant=Color(0xFFC9C7D2),
    error=Color(0xFFB3261E),
    onError=Color(0xFFFFFFFF)
)

internal val PursuitTypography=Typography(
    headlineLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=30.sp,lineHeight=36.sp,letterSpacing=(-0.5f).sp),
    headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=26.sp,lineHeight=32.sp,letterSpacing=(-0.3f).sp),
    headlineSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=27.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=23.sp),
    titleSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
    labelMedium=TextStyle(fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=17.sp),
    labelSmall=TextStyle(fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=16.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp)
)

/** Shared native spacing scale: no stylesheet, inline CSS or screen-owned token copies. */
internal object PursuitSpace {
    val xxs=4.dp
    val xs=8.dp
    val sm=12.dp
    val md=16.dp
    val lg=20.dp
    val xl=24.dp
    val xxl=32.dp
}
