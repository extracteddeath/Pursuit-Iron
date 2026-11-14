package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Navigation into existing owners only. These controls never save a set. */
@Composable
internal fun NativeKotlinWorkoutTools(
    enabled:Boolean,
    onSessionNote:()->Unit,
    onPinnedNote:(()->Unit)?,
    onSwap:(()->Unit)?,
    onWarmup:(()->Unit)?
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
        verticalAlignment=Alignment.CenterVertically) {
        onWarmup?.let {
            WorkoutTool("View warm-up targets",Icons.Outlined.FitnessCenter,enabled,it)
        }
        onSwap?.let {
            WorkoutTool("Swap exercise",Icons.Outlined.SwapHoriz,enabled,it)
        }
        WorkoutTool("Session note",Icons.Outlined.EditNote,enabled,onSessionNote)
        onPinnedNote?.let {
            WorkoutTool("Pinned exercise note and setup",Icons.Outlined.PushPin,enabled,it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTool(label:String,icon:ImageVector,enabled:Boolean,onClick:()->Unit) {
    TooltipBox(
        positionProvider=TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above),
        tooltip={PlainTooltip{Text(label)}},
        state=rememberTooltipState()
    ) {
        FilledTonalIconButton(onClick=onClick,enabled=enabled,
            modifier=Modifier.size(48.dp)) {
            Icon(icon,contentDescription=label)
        }
    }
}
