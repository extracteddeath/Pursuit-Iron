package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
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
import io.github.extracteddeath.pursuitiron.rebuild.CoachReadiness

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
    subtitle:String?=null, enabled:Boolean=true,
    onInfo:(()->Unit)?=null
) {
    val titleContent: @Composable (Modifier)->Unit = {modifier->
        Column(modifier,verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
            Text(kotlinExerciseLabel(exerciseId),
                style=MaterialTheme.typography.titleLarge)
            if(!subtitle.isNullOrBlank())Text(subtitle,
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val historyAction: @Composable (Modifier)->Unit = {modifier->
        OutlinedButton(onClick=onHistory,enabled=enabled,
            contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
            modifier=modifier.heightIn(min=48.dp)) {
            Icon(Icons.Outlined.History,contentDescription=null,
                modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(PursuitSpace.xxs))
            Text("History")
        }
    }
    val infoAction: @Composable ()->Unit = {
        if(onInfo!=null) OutlinedIconButton(
            onClick=onInfo,enabled=enabled,modifier=Modifier.size(48.dp)
        ) {
            Icon(Icons.Outlined.Info,contentDescription="Exercise details")
        }
    }
    // The lift title may be long. Don't squeeze it beside two actions at
    // large Android font scales; keep full History and Info accessible.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked=maxWidth<380.dp || LocalDensity.current.fontScale>=1.35f
        if(stacked) {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                titleContent(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    infoAction()
                    historyAction(Modifier.weight(1f))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                titleContent(Modifier.weight(1f))
                infoAction()
                historyAction(Modifier)
            }
        }
    }
}

/**
 * One shared readiness / movement-restriction surface for both native journal
 * types. These are athlete-provided inputs to the optional coach review only:
 * they cannot mutate the issued prescription, recorded sets or program.
 * Keep all selection state in the parent screen's saved state owner.
 */
@Composable
internal fun PursuitWorkoutReadinessPanel(
    readiness:CoachReadiness,
    onReadinessChange:(CoachReadiness)->Unit,
    movementRestricted:Boolean,
    onMovementRestrictedChange:(Boolean)->Unit,
    enabled:Boolean=true,
    showReadiness:Boolean=true,
    restrictionLabel:String="Movement restricted"
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
    ) {
        if(showReadiness) {
        Text("How are you feeling?",style=MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
        ) {
            CoachReadiness.entries.forEach { option ->
                FilterChip(
                    selected=readiness==option,
                    onClick={onReadinessChange(option)},
                    enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp),
                    label={Text(option.name.lowercase().replaceFirstChar{
                        it.uppercase()
                    })}
                )
            }
        }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
        ) {
            Checkbox(
                checked=movementRestricted,
                onCheckedChange=onMovementRestrictedChange,
                enabled=enabled
            )
            Column(Modifier.weight(1f)) {
                Text(restrictionLabel,
                    style=MaterialTheme.typography.bodyMedium)
                if(movementRestricted) Text(
                    "Review a safer adjustment or alternative. Nothing changes until you approve it.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One shared Material 3 Expressive focus header for generated and custom
 * journals. History and Info remain one touch from the active set, even
 * while its editor is in compact focus mode. This component owns only
 * presentation: neither action changes prescribed targets or recorded sets.
 *
 * At large font scales and narrow widths, exercise name and actions stack
 * without squeezing titles or reducing the 48dp tap targets.
 */
@Composable
internal fun PursuitWorkoutFocusHeader(
    exerciseLabel:String?,
    setDetail:String?,
    focusOnNext:Boolean,
    onFocusChange:(Boolean)->Unit,
    enabled:Boolean=true,
    heading:String="UP NEXT",
    onHistory:(()->Unit)?=null,
    onInfo:(()->Unit)?=null
) {
    PursuitCard(highlighted=focusOnNext) {
        Text(heading,style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.primary)
        val titleContent:@Composable (Modifier)->Unit={modifier->
            Column(modifier,
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                Text(exerciseLabel ?: "All entries recorded · review Finish",
                    style=MaterialTheme.typography.titleLarge)
                if(!setDetail.isNullOrBlank())
                    Text(setDetail,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val historyAction:@Composable (Modifier)->Unit={modifier->
            if(exerciseLabel!=null && onHistory!=null) {
                OutlinedButton(
                    onClick=onHistory,enabled=enabled,
                    contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
                    modifier=modifier.heightIn(min=48.dp)
                ) {
                    Icon(Icons.Outlined.History,contentDescription=null,
                        modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(PursuitSpace.xxs))
                    Text("History")
                }
            }
        }
        val infoAction:@Composable ()->Unit={
            if(exerciseLabel!=null && onInfo!=null) {
                OutlinedIconButton(
                    onClick=onInfo,enabled=enabled,
                    modifier=Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Info,
                        contentDescription="Exercise details")
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked=maxWidth<380.dp ||
                LocalDensity.current.fontScale>=1.35f
            val hasActions=exerciseLabel!=null &&
                (onHistory!=null || onInfo!=null)
            if(stacked && hasActions) {
                Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    titleContent(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                        verticalAlignment=Alignment.CenterVertically) {
                        infoAction()
                        historyAction(Modifier.weight(1f))
                    }
                }
            } else if(hasActions) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                    verticalAlignment=Alignment.CenterVertically) {
                    titleContent(Modifier.weight(1f))
                    infoAction()
                    historyAction(Modifier)
                }
            } else titleContent(Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            FilterChip(
                selected=focusOnNext,
                onClick={onFocusChange(true)},
                enabled=enabled,
                modifier=Modifier.heightIn(min=48.dp),
                label={Text("Focus on next")})
            FilterChip(
                selected=!focusOnNext,
                onClick={onFocusChange(false)},
                enabled=enabled,
                modifier=Modifier.heightIn(min=48.dp),
                label={Text("All sets")})
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
