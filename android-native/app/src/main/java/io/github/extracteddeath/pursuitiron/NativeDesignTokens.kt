package io.github.extracteddeath.pursuitiron

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Original Pursuit Iron themes, copied from modules/App.js THEMES/TOK.
 * First 12 colors are its literal palette inputs (background, tint,
 * surface, surface2, cream, muted, mutedSoft, accent, onAccent, accentInk,
 * warm and danger). The final five are precomputed by the original TOK
 * contrast-floor formulas; they are NOT a new set of theme colors.
 *
 * UI preferences live outside canonical workouts and history.
 */
internal data class PursuitPalette(
    val id:String,val name:String,val isLight:Boolean,
    val background:Color,val tint:Color,val card:Color,val raised:Color,
    val text:Color,val sourceMuted:Color,val sourceFaint:Color,
    val accent:Color,val onAccent:Color,val sourceAccentInk:Color,
    val warning:Color,val sourceDanger:Color,
    val muted:Color,val faint:Color,val accentInk:Color,
    val danger:Color,val onDanger:Color
) {
    val scheme:ColorScheme get() {
        // M3 surface roles reuse the actual per-theme surfaces rather than
        // blending every theme into one graphite/violet appearance.
        val accentContainer=lerp(raised,accent,if(isLight)0.09f else 0.16f)
        val colors=if(isLight) lightColorScheme() else darkColorScheme()
        return colors.copy(
            primary=accent,onPrimary=onAccent,
            primaryContainer=accentContainer,onPrimaryContainer=text,
            secondary=accentInk,onSecondary=onAccent,
            secondaryContainer=tint,onSecondaryContainer=text,
            tertiary=accentInk,onTertiary=onAccent,
            background=background,onBackground=text,
            surface=card,onSurface=text,
            surfaceVariant=raised,onSurfaceVariant=muted,
            surfaceContainerLowest=background,
            surfaceContainerLow=tint,surfaceContainer=card,
            surfaceContainerHigh=raised,
            surfaceContainerHighest=lerp(raised,accent,0.08f),
            outline=faint,outlineVariant=muted.copy(alpha=0.35f),
            error=danger,onError=onDanger,
            errorContainer=lerp(raised,danger,0.16f),onErrorContainer=text,
            inverseSurface=text,inverseOnSurface=background,
            inversePrimary=accent
        )
    }
}

internal object PursuitThemes {
    // Intentional native-first default: an ORIGINAL shipped web palette,
    // while all 20 web theme definitions stay byte-for-byte color-matched.
    // Explicitly saved Lime selections remain valid and are never overwritten.
    const val DEFAULT_ID="amethyst"
    val all:List<PursuitPalette> = listOf(
        PursuitPalette("lime", "Lime", false, Color(0xFF0A0908), Color(0xFF0F0D10), Color(0xFF15131A), Color(0xFF1E1A25), Color(0xFFF2EEE4), Color(0xFFA29EA8), Color(0xFF6F6B76), Color(0xFFD4FF3D), Color(0xFF0A0908), Color(0xFFD4FF3D), Color(0xFFE4BC5D), Color(0xFFFF5D6C), Color(0xFFACA9B1), Color(0xFF94919B), Color(0xFFD4FF3D), Color(0xFFFF6E7B), Color(0xFF1A1508)),
        PursuitPalette("ember", "Ember", false, Color(0xFF120907), Color(0xFF17100C), Color(0xFF1E1511), Color(0xFF2A1D17), Color(0xFFF5EDE2), Color(0xFFA3968A), Color(0xFF786D64), Color(0xFFFF8A3D), Color(0xFF120907), Color(0xFFFF8A3D), Color(0xFFF5B85D), Color(0xFFFF5D6C), Color(0xFFB3A89E), Color(0xFF9B9188), Color(0xFFFF8A3D), Color(0xFFFF7480), Color(0xFF1A1508)),
        PursuitPalette("ice", "Ice", false, Color(0xFF090D12), Color(0xFF0D1219), Color(0xFF131A22), Color(0xFF1B2430), Color(0xFFE8EDF2), Color(0xFF8A94A0), Color(0xFF68717C), Color(0xFF5DD4E4), Color(0xFF090D12), Color(0xFF5DD4E4), Color(0xFFE4C25D), Color(0xFFFF5D8A), Color(0xFFA3ABB4), Color(0xFF8A939D), Color(0xFF5DD4E4), Color(0xFFFF6791), Color(0xFF1A1508)),
        PursuitPalette("rose", "Rose", false, Color(0xFF12091A), Color(0xFF170C20), Color(0xFF1D1227), Color(0xFF281A35), Color(0xFFF0E8EE), Color(0xFFA0939E), Color(0xFF756B73), Color(0xFFFF6B8A), Color(0xFF12091A), Color(0xFFFF6B8A), Color(0xFFE4A55D), Color(0xFFFF5D6C), Color(0xFFB1A7B0), Color(0xFF999097), Color(0xFFFF7995), Color(0xFFFF7480), Color(0xFF1A1508)),
        PursuitPalette("forest", "Forest", false, Color(0xFF080C0A), Color(0xFF0C120F), Color(0xFF121A15), Color(0xFF1A2620), Color(0xFFE8EFE6), Color(0xFF8A948A), Color(0xFF68726E), Color(0xFFA8DC5D), Color(0xFF080C0A), Color(0xFFA8DC5D), Color(0xFFD4B85D), Color(0xFFE4615D), Color(0xFFA4ACA4), Color(0xFF8A9590), Color(0xFFA8DC5D), Color(0xFFE97E7A), Color(0xFF1A1508)),
        PursuitPalette("light", "Mint", true, Color(0xFFF4F3F0), Color(0xFFEDECEA), Color(0xFFFFFFFF), Color(0xFFF8F7F5), Color(0xFF1A1A18), Color(0xFF6B6A66), Color(0xFF8C8B88), Color(0xFF238070), Color(0xFFFFFFFF), Color(0xFF238070), Color(0xFFB87A2E), Color(0xFFCC3F3F), Color(0xFF595855), Color(0xFF6C6B69), Color(0xFF1E6C5F), Color(0xFFB33030), Color(0xFFFFFFFF)),
        PursuitPalette("clay", "Clay", true, Color(0xFFF2EDE1), Color(0xFFECE5D4), Color(0xFFFFFFFF), Color(0xFFFAF5EB), Color(0xFF1A1510), Color(0xFF78685A), Color(0xFF998A79), Color(0xFFBE573A), Color(0xFFFFFFFF), Color(0xFFBE573A), Color(0xFFB88936), Color(0xFFC4413D), Color(0xFF5F5247), Color(0xFF726557), Color(0xFF96442E), Color(0xFFA63633), Color(0xFFFFFFFF)),
        PursuitPalette("graphite", "Graphite", false, Color(0xFF0B0B0C), Color(0xFF101012), Color(0xFF161618), Color(0xFF1F1F22), Color(0xFFF0F0F1), Color(0xFFA0A0A6), Color(0xFF6C6C72), Color(0xFFD8D8DC), Color(0xFF0B0B0C), Color(0xFFD8D8DC), Color(0xFFD8C08A), Color(0xFFFF5D6C), Color(0xFFA9A9AE), Color(0xFF929297), Color(0xFFD8D8DC), Color(0xFFFF707D), Color(0xFF1A1508)),
        PursuitPalette("amethyst", "Amethyst", false, Color(0xFF0C0913), Color(0xFF110D1A), Color(0xFF181221), Color(0xFF231A30), Color(0xFFECE6F2), Color(0xFF9D93AB), Color(0xFF6F687C), Color(0xFFB388FF), Color(0xFF0C0913), Color(0xFFB388FF), Color(0xFFE4C25D), Color(0xFFFF5D8A), Color(0xFFAFA7BA), Color(0xFF9690A1), Color(0xFFB88FFF), Color(0xFFFF7097), Color(0xFF1A1508)),
        PursuitPalette("stone", "Stone", true, Color(0xFFEFEFEF), Color(0xFFE7E7E7), Color(0xFFFFFFFF), Color(0xFFF6F6F6), Color(0xFF1B1B1D), Color(0xFF6A6A6E), Color(0xFF8D8D90), Color(0xFF3F3F46), Color(0xFFFFFFFF), Color(0xFF3F3F46), Color(0xFFA87E3C), Color(0xFFC43F3F), Color(0xFF555559), Color(0xFF67676A), Color(0xFF3F3F46), Color(0xFFA83434), Color(0xFFFFFFFF)),
        PursuitPalette("patrol", "Patrol", false, Color(0xFF070A0F), Color(0xFF0B0F16), Color(0xFF11161F), Color(0xFF19202C), Color(0xFFE6EBF2), Color(0xFF8C97A8), Color(0xFF646D7B), Color(0xFF2563EB), Color(0xFFF4F7FC), Color(0xFF4D8DF7), Color(0xFFF0B429), Color(0xFFEF4444), Color(0xFFA2AAB8), Color(0xFF8A93A0), Color(0xFF649CF8), Color(0xFFF36F6F), Color(0xFF1A1508)),
        PursuitPalette("slate", "Slate", true, Color(0xFFF0F2F6), Color(0xFFE7EBF1), Color(0xFFFFFFFF), Color(0xFFF6F8FB), Color(0xFF15191F), Color(0xFF5B6470), Color(0xFF878F9A), Color(0xFF2563EB), Color(0xFFFFFFFF), Color(0xFF2563EB), Color(0xFF9A6B1E), Color(0xFFC43F3F), Color(0xFF505863), Color(0xFF636B76), Color(0xFF1452DB), Color(0xFFAD3535), Color(0xFFFFFFFF)),
        PursuitPalette("lilac", "Lilac", true, Color(0xFFF4F1F8), Color(0xFFECE6F3), Color(0xFFFFFFFF), Color(0xFFF8F5FC), Color(0xFF1C1726), Color(0xFF6B6178), Color(0xFF938A9E), Color(0xFF7C4DD4), Color(0xFFFFFFFF), Color(0xFF7C4DD4), Color(0xFF9A6B1E), Color(0xFFC43F5A), Color(0xFF5B5266), Color(0xFF6E657A), Color(0xFF6F3BD0), Color(0xFFA8344B), Color(0xFFFFFFFF)),
        PursuitPalette("emerald", "Emerald", false, Color(0xFF05100B), Color(0xFF081511), Color(0xFF0D1C16), Color(0xFF14281F), Color(0xFFE8F2EC), Color(0xFF93A79C), Color(0xFF63756B), Color(0xFF34D399), Color(0xFF04120C), Color(0xFF34D399), Color(0xFFE4BC5D), Color(0xFFFF5D6C), Color(0xFF9BAEA3), Color(0xFF82968B), Color(0xFF34D399), Color(0xFFFF6876), Color(0xFF1A1508)),
        PursuitPalette("gold", "Gold", false, Color(0xFF100D07), Color(0xFF15110A), Color(0xFF1C1710), Color(0xFF272016), Color(0xFFF3EDE0), Color(0xFFA9A08C), Color(0xFF726B5C), Color(0xFFF5C542), Color(0xFF14100A), Color(0xFFF5C542), Color(0xFFE58F4E), Color(0xFFFF5D6C), Color(0xFFB1A997), Color(0xFF999281), Color(0xFFF5C542), Color(0xFFFF727F), Color(0xFF1A1508)),
        PursuitPalette("sage", "Sage", true, Color(0xFFF1F4F0), Color(0xFFE8EDE6), Color(0xFFFFFFFF), Color(0xFFF7FAF6), Color(0xFF16201A), Color(0xFF5C6B62), Color(0xFF84918A), Color(0xFF2F6B4F), Color(0xFFFFFFFF), Color(0xFF2F6B4F), Color(0xFFA8761C), Color(0xFFC0303F), Color(0xFF4E5B53), Color(0xFF626D67), Color(0xFF2F6A4E), Color(0xFFB22C3A), Color(0xFFFFFFFF)),
        PursuitPalette("ochre", "Ochre", true, Color(0xFFF7F2E8), Color(0xFFF0E9DA), Color(0xFFFFFFFF), Color(0xFFFBF7EF), Color(0xFF201A10), Color(0xFF6E6048), Color(0xFF93856D), Color(0xFF9A5B10), Color(0xFFFFFFFF), Color(0xFF9A5B10), Color(0xFF7A6A12), Color(0xFFB93A32), Color(0xFF625540), Color(0xFF746855), Color(0xFF8A510E), Color(0xFFAA352E), Color(0xFFFFFFFF)),
        PursuitPalette("rosewater", "Rosewater", true, Color(0xFFF8F0F1), Color(0xFFF2E6E9), Color(0xFFFFFFFF), Color(0xFFFCF5F6), Color(0xFF241519), Color(0xFF7A6469), Color(0xFF9C868D), Color(0xFFC43B63), Color(0xFFFFFFFF), Color(0xFFC43B63), Color(0xFFB07A2E), Color(0xFFC43F3F), Color(0xFF635256), Color(0xFF7A636B), Color(0xFFA63254), Color(0xFFAB3434), Color(0xFFFFFFFF)),
        PursuitPalette("ocean", "Ocean", true, Color(0xFFEEF6F8), Color(0xFFE3EFF2), Color(0xFFFFFFFF), Color(0xFFF6FBFC), Color(0xFF142126), Color(0xFF53666C), Color(0xFF7C8D92), Color(0xFF0A6F8E), Color(0xFFFFFFFF), Color(0xFF0A6F8E), Color(0xFFA66A1F), Color(0xFFBE3F50), Color(0xFF4A5B61), Color(0xFF606E73), Color(0xFF096884), Color(0xFFAA3848), Color(0xFFFFFFFF)),
        PursuitPalette("citrus", "Citrus", true, Color(0xFFF7F9EC), Color(0xFFEEF2DD), Color(0xFFFFFFFF), Color(0xFFFBFCEF), Color(0xFF1D2112), Color(0xFF646B4E), Color(0xFF8A9074), Color(0xFF748600), Color(0xFF0A0D00), Color(0xFF748600), Color(0xFF9B6D18), Color(0xFFBB3F3B), Color(0xFF575D44), Color(0xFF6B6F59), Color(0xFF5C6A00), Color(0xFFAE3A37), Color(0xFFFFFFFF))
    )
    fun find(id:String):PursuitPalette?=all.firstOrNull { it.id==id }
    fun resolve(id:String):PursuitPalette=find(id) ?: checkNotNull(find(DEFAULT_ID))
}

/** The current ink accent is distinct from the filled accent in Patrol. */
internal val LocalPursuitPalette=compositionLocalOf { PursuitThemes.resolve(PursuitThemes.DEFAULT_ID) }

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
