package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

/** Shared responsive reading width for Profile, Settings, Coach and Progress. */
@Composable
internal fun PursuitScreenContent(
    modifier:Modifier=Modifier,
    scrollState:ScrollState=rememberScrollState(),
    content:@Composable ColumnScope.()->Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(scrollState),
        horizontalAlignment=Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().widthIn(max=920.dp)
            .padding(horizontal=PursuitSpace.md,vertical=PursuitSpace.lg),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.lg),
            content=content)
    }
}

/** A clear one-tap navigation item with a generous touch target. */
@Composable
internal fun PursuitDestinationRow(
    title:String,description:String,onClick:()->Unit,
    icon:@Composable ()->Unit
) {
    Surface(
        onClick=onClick,
        modifier=Modifier.fillMaxWidth().heightIn(min=64.dp),
        shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ListItem(
            headlineContent={Text(title,style=MaterialTheme.typography.titleMedium)},
            supportingContent={Text(description,
                style=MaterialTheme.typography.bodySmall)},
            leadingContent=icon,
            trailingContent={
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription=null)
            },
            colors=ListItemDefaults.colors(
                containerColor=MaterialTheme.colorScheme.surfaceContainerLow)
        )
    }
}

/**
 * Shared, screen-reader-friendly progressive disclosure for long programs,
 * nested training days and preview lists. Only presentation state changes;
 * it never mutates program approvals or sets.
 */
@Composable
internal fun PursuitDisclosureRow(
    title:String,
    subtitle:String?,
    expanded:Boolean,
    onToggle:()->Unit,
    modifier:Modifier=Modifier
) {
    Surface(
        onClick=onToggle,
        modifier=modifier.fillMaxWidth().heightIn(min=56.dp)
            .semantics { stateDescription=if(expanded)"Expanded" else "Collapsed" },
        shape=MaterialTheme.shapes.medium,
        color=MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal=PursuitSpace.md,vertical=PursuitSpace.sm),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                if(!subtitle.isNullOrBlank())
                    Text(subtitle,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if(expanded)Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription=null
            )
        }
    }
}


/**
 * Shared two-action layout for Save/Clear, Confirm/Cancel and other paired
 * actions. Keeps touch targets readable on small screens and at 200% text
 * without duplicating responsive branches throughout the native app.
 */
@Composable
internal fun PursuitResponsiveActionPair(
    primary:@Composable (Modifier)->Unit,
    secondary:@Composable (Modifier)->Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack=maxWidth<380.dp || LocalDensity.current.fontScale>=1.35f
        if(stack) {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                primary(Modifier.weight(1f))
                secondary(Modifier.weight(1f))
            }
        }
    }
}
