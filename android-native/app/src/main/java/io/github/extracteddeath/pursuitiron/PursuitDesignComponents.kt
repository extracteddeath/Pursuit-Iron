package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared Material 3 presentation primitives. These contain NO training
 * mathematics, approval state, history fallbacks or receipt writes.
 * Screen hierarchy, minimum touch areas and spacing live here.
 */
@Composable
internal fun PursuitSectionHeading(
    title:String, description:String?=null,
    trailing: (@Composable ()->Unit)?=null
) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
        Column(Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            if(!description.isNullOrBlank())Text(description,
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PursuitExerciseHeading(
    exerciseId:String, onHistory:()->Unit,
    subtitle:String?=null, enabled:Boolean=true
) {
    Row(Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Column(Modifier.weight(1f),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
            Text(kotlinExerciseLabel(exerciseId),
                style=MaterialTheme.typography.titleLarge)
            if(!subtitle.isNullOrBlank())Text(subtitle,
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick=onHistory,enabled=enabled,
            contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
            modifier=Modifier.heightIn(min=48.dp)) {
            Icon(Icons.Outlined.History,contentDescription=null,
                modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(PursuitSpace.xxs))
            Text("History")
        }
    }
}

@Composable
internal fun PursuitEmptyState(
    title:String,description:String,
    actionLabel:String?=null,onAction:(()->Unit)?=null
) {
    PursuitCard {
        Text(title,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(actionLabel!=null && onAction!=null)
            OutlinedButton(onClick=onAction,
                modifier=Modifier.fillMaxWidth()) {Text(actionLabel)}
    }
}

/** Historical data is informational; it must never silently change a set. */
@Composable
internal fun PursuitHistoryStatus(
    title:String,description:String
) {
    Column(Modifier.fillMaxWidth(),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
        Text(title,style=MaterialTheme.typography.titleSmall)
        Text(description,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
