package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Four source-matched barbell estimates, using an owner-verified history
 * projection supplied by the screen. This is a selection control and
 * read-only overview; it does not publish PRs or infer coaching progress.
 *
 * Layout owns responsiveness centrally. At ordinary phone widths it uses
 * two columns; compact phones and large accessibility font sizes stack.
 */
internal data class NativeStrengthOverviewItem(
    val exerciseId:String,
    val title:String,
    val estimatedOneRepMax:String?,
    val referenceLabel:String?
)

@Composable
internal fun NativeKotlinStrengthOverview(
    items:List<NativeStrengthOverviewItem>,
    selectedExerciseId:String,
    onSelect:(String)->Unit
) {
    PursuitSectionHeading("Strength at a glance",
        "Best verified estimated 1RM for each exact barbell lift. Not a tested maximum.")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale=LocalDensity.current.fontScale
        val columns=when {
            fontScale>=1.35f || maxWidth<360.dp -> 1
            maxWidth>=760.dp -> 4
            else -> 2
        }
        Column(Modifier.fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            items.chunked(columns).forEach {rowItems ->
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    rowItems.forEach {item ->
                        val selected=item.exerciseId==selectedExerciseId
                        Surface(
                            onClick={onSelect(item.exerciseId)},
                            modifier=Modifier.weight(1f).heightIn(min=124.dp)
                                .semantics {
                                    stateDescription=if(selected)
                                        "Selected strength trend" else "Select strength trend"
                                },
                            shape=MaterialTheme.shapes.large,
                            color=if(selected)
                                MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor=if(selected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            border=BorderStroke(
                                1.dp,if(selected)MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(PursuitSpace.sm),
                                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                Text(item.title,style=MaterialTheme.typography.titleSmall)
                                Text(item.estimatedOneRepMax ?: "No qualifying set",
                                    style=if(item.estimatedOneRepMax==null)
                                        MaterialTheme.typography.bodyMedium
                                    else MaterialTheme.typography.titleLarge)
                                Text(
                                    item.referenceLabel ?: "Community comparison not set",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    repeat(columns-rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
