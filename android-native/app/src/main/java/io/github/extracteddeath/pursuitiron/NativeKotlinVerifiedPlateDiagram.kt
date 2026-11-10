package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.KotlinVerifiedPlateLayout
import java.math.BigDecimal

private const val MAX_DIAGRAM_PLATES_PER_SIDE = 12

private fun plateAmount(weight: BigDecimal): String =
    weight.stripTrailingZeros().toPlainString()

/**
 * Presentation only: mirrors the exact confirmed pairs emitted by the pure
 * Kotlin plate solver. Shapes and heights are schematic, not a representation
 * of manufacturer-specific plate diameter or collar stock.
 *
 * Do not calculate weights, invent plates, change targets, or save anything
 * here. The same UI is used by custom and generated Kotlin workout journals.
 */
@Composable
internal fun NativeKotlinVerifiedPlateDiagram(layout: KotlinVerifiedPlateLayout) {
    val unit=layout.unit.name.lowercase()
    val totalPerSide=layout.perSide.sumOf { it.countPerSide }
    val shownTypes=layout.perSide.flatMapIndexed { index,pair ->
        List(pair.countPerSide) { index }
    }.take(MAX_DIAGRAM_PLATES_PER_SIDE)
    val hidden=totalPerSide-shownTypes.size
    val explanation=if(totalPerSide==0) "Unloaded bar."
        else layout.perSide.joinToString(", ") { pair ->
            "${plateAmount(pair.weight)} $unit, ${pair.countPerSide} per side"
        }
    val shades=listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    Surface(
        modifier=Modifier.fillMaxWidth(),
        shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
        ) {
            Text("Balanced plate layout",
                style=MaterialTheme.typography.titleMedium)
            Text("Matching plates on both ends · diagram not to scale",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clearAndSetSemantics {
                        contentDescription="Schematic barbell, left and right mirrored. $explanation"
                    },
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xxs),
                verticalAlignment=Alignment.CenterVertically
            ) {
                // Left is mirrored: the largest plate in the solver's ordered
                // breakdown stays closest to the bar on BOTH sides.
                shownTypes.asReversed().forEach { tier ->
                    VerifiedPlateGlyph(tier,shades)
                }
                Box(
                    Modifier.width(72.dp).height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
                shownTypes.forEach { tier ->
                    VerifiedPlateGlyph(tier,shades)
                }
            }
            if(hidden>0) Text(
                "Showing ${shownTypes.size} of $totalPerSide plates per side in the diagram. "+
                    "The full verified counts are below.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall)
            if(layout.perSide.isEmpty()) {
                Text("Bar only · no plates required",
                    style=MaterialTheme.typography.bodyMedium)
            } else {
                Text("Verified plates on EACH side",
                    style=MaterialTheme.typography.labelLarge)
                layout.perSide.forEachIndexed { index,pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(shades[index%shades.size])
                        )
                        Text("${plateAmount(pair.weight)} $unit × ${pair.countPerSide} per side",
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifiedPlateGlyph(tier:Int,shades:List<androidx.compose.ui.graphics.Color>) {
    // Distinguish ordered plate groups visually, without claiming real-world
    // diameter or pretending that Olympic competition colors apply.
    val height=(68-tier.coerceAtMost(6)*5).dp
    val shape=RoundedCornerShape(4.dp)
    Box(
        Modifier.width(13.dp).height(height)
            .clip(shape)
            .background(shades[tier%shades.size])
            .border(1.dp,MaterialTheme.colorScheme.outlineVariant,shape)
    )
}
