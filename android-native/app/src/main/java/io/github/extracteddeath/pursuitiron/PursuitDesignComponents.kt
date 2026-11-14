package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.CoachReadiness
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPerformedSet
import io.github.extracteddeath.pursuitiron.rebuild.LevelingReviewResult
import io.github.extracteddeath.pursuitiron.rebuild.LevelingRefusal

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
    restrictionLabel:String="Movement restricted",
    /** UI-only handoff into the current owner's existing durable note editor. */
    onReportConcern:(()->Unit)?=null,
    /** Keep the current set closer to the top; all coach inputs stay accessible. */
    compactByDefault:Boolean=false
) {
    // Only the visibility of advisory inputs is saved. Readiness/restrictions
    // remain athlete-owned parent state and never mutate a set or program.
    var detailsExpanded by rememberSaveable(compactByDefault) {
        mutableStateOf(!compactByDefault)
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
    ) {
        if(compactByDefault) {
            Text("Coaching context",style=MaterialTheme.typography.titleSmall)
            Text(
                (if(showReadiness) "Readiness · "+
                    readiness.name.lowercase().replaceFirstChar{it.uppercase()}+
                    " · " else "") +
                    (if(movementRestricted)restrictionLabel else "Unrestricted"),
                style=MaterialTheme.typography.bodySmall,
                color=if(movementRestricted)MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            // An injury remains ONE tap even when readiness fields are
            // collapsed. No auto skip, weight change or unsaved note claim.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                verticalAlignment=Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick={detailsExpanded=!detailsExpanded},
                    enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp)
                ) {Text(if(detailsExpanded)"Done" else "Edit readiness")}
                if(onReportConcern!=null) TextButton(
                    onClick=onReportConcern,
                    enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp)
                ) {Text("Injury / discomfort")}
            }
        }
        if(!compactByDefault || detailsExpanded) {
            if(showReadiness) {
                Text("How are you feeling?",
                    style=MaterialTheme.typography.titleSmall)
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
                Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(
                    value=movementRestricted,enabled=enabled,role=Role.Checkbox,
                    onValueChange=onMovementRestrictedChange
                ),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
            ) {
                // One labeled accessible touch target, not two nested toggles.
                Checkbox(
                    checked=movementRestricted,
                    onCheckedChange=null,
                    enabled=enabled
                )
                Column(Modifier.weight(1f)) {
                    Text(restrictionLabel,style=MaterialTheme.typography.bodyMedium)
                    if(movementRestricted) Text(
                        "Review a safer adjustment or alternative. Nothing changes until you approve it.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if(onReportConcern!=null) {
                if(!compactByDefault) OutlinedButton(
                    onClick=onReportConcern,
                    enabled=enabled,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {Text("Injury or discomfort · add a note")}
                Text(
                    "This marks the movement as restricted for optional coaching and opens the saved session note. You still choose Skip or Swap for any unfinished sets; nothing is changed automatically.",
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
    onInfo:(()->Unit)?=null,
    /** Opens the owner's confirmation, never records a skip directly. */
    onSkip:(()->Unit)?=null
) {
    PursuitCard(highlighted=focusOnNext) {
        Text(heading,style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.primary)
        val titleContent:@Composable (Modifier)->Unit={modifier->
            Column(modifier,
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                Text(exerciseLabel ?: if(focusOnNext)
                    "All entries recorded · review Finish"
                else "All sets · choose a set",
                    style=MaterialTheme.typography.titleLarge)
                if(!setDetail.isNullOrBlank())
                    Text(setDetail,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val skipAction:@Composable (Modifier)->Unit={modifier->
            if(exerciseLabel!=null && onSkip!=null) {
                OutlinedButton(
                    onClick=onSkip,enabled=enabled,
                    contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
                    modifier=modifier.heightIn(min=48.dp)
                ) {Text("Skip")}
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
            // Even a long lift name has its own full-width line on a phone.
            // All three actions remain accessible at 200% font size.
            val stacked=maxWidth<520.dp ||
                LocalDensity.current.fontScale>=1.30f
            val hasActions=exerciseLabel!=null &&
                (onSkip!=null || onHistory!=null || onInfo!=null)
            if(stacked && hasActions) {
                Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    titleContent(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                        verticalAlignment=Alignment.CenterVertically) {
                        skipAction(Modifier)
                        historyAction(Modifier)
                        infoAction()
                    }
                }
            } else if(hasActions) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs),
                    verticalAlignment=Alignment.CenterVertically) {
                    titleContent(Modifier.weight(1f))
                    skipAction(Modifier)
                    historyAction(Modifier)
                    infoAction()
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


/** One Material 3 Expressive review surface shared by generated and custom plans.
 * The caller supplies source-verified previous observations and an independent
 * Kotlin domain decision. This composable owns NO training math, future targets,
 * coaching state or journal writes. At high font scales the facts scroll while
 * both confirmation actions retain their 48dp minimum touch target.
 */
@Composable
internal fun PursuitPreviousWorkoutLevelingDialog(
    review:LevelingReviewResult?,
    historyLoading:Boolean,
    goalComparable:Boolean,
    stationValid:Boolean,
    loadUnit:String,
    observed:List<KotlinPerformedSet>,
    unfinishedCount:Int,
    busy:Boolean,
    onApply:()->Unit,
    onDismiss:()->Unit
) {
    val candidate=review as? LevelingReviewResult.Candidate
    val actionable=candidate!=null && !historyLoading &&
        goalComparable && stationValid && unfinishedCount>0
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Leveling · previous workout")},
        text={
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
            ) {
                when {
                    historyLoading ->
                        Text("Verifying the last completed workout and physical machine…")
                    !stationValid ->
                        Text("Select a confirmed, active machine before using previous sets.")
                    !goalComparable ->
                        Text("Working sets need the same rep range and target RIR for this review.")
                    review==null ->
                        Text("Reviewing previous performance and verified equipment…")
                    candidate!=null -> {
                        Text("Your previous actual working sets",
                            style=MaterialTheme.typography.titleSmall)
                        observed.forEachIndexed { index,actual->
                            val actualLoad=actual.load?.stripTrailingZeros()
                                ?.toPlainString() ?: "—"
                            val effort=actual.actualRir?.let{ "RIR $it" }
                                ?: "RIR not reported"
                            Text("Set ${index+1} · $actualLoad $loadUnit × "+
                                "${actual.reps} · $effort",
                                style=MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Text("Recorded mean: "+
                            candidate.averageObservedLoad.stripTrailingZeros()
                                .toPlainString()+" $loadUnit",
                            style=MaterialTheme.typography.bodyMedium)
                        Text("Verified starting point: "+
                            candidate.proposedStraightSetLoad.stripTrailingZeros()
                                .toPlainString()+" $loadUnit × "+
                            candidate.repsPerSet+" · "+
                            unfinishedCount+" unfinished working set(s)",
                            style=MaterialTheme.typography.titleMedium)
                        Text(candidate.note,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text(when((review as LevelingReviewResult.Rejected).reason) {
                        LevelingRefusal.UNVERIFIED_STOCK ->
                            "Confirm this gym's machine stack, plates or valid weight increments."
                        LevelingRefusal.ALREADY_LEVEL ->
                            "The last workout already used one working weight."
                        LevelingRefusal.BELOW_REP_FLOOR,
                        LevelingRefusal.ABOVE_REP_CEILING,
                        LevelingRefusal.INSUFFICIENT_EFFORT_EVIDENCE ->
                            "Previous actual reps or reported RIR do not support Leveling within this goal."
                        LevelingRefusal.NO_SUPPORTED_CANDIDATE ->
                            "There is no verified equipment load near that average."
                        else ->
                            "No complete, comparable previous set sequence was verified for this workout day and machine."
                    })
                }
                Text("This is an optional unsaved prefill. Your issued sets, "+
                    "training history, RIR entries and future workouts do not change. "+
                    "Log what you actually perform.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={
            if(actionable) Button(
                onClick=onApply,enabled=!busy,
                modifier=Modifier.heightIn(min=48.dp)
            ) { Text("Prefill unfinished sets") }
            else TextButton(
                onClick=onDismiss,modifier=Modifier.heightIn(min=48.dp)
            ) { Text("Close") }
        },
        dismissButton={
            if(actionable) TextButton(
                onClick=onDismiss,modifier=Modifier.heightIn(min=48.dp)
            ) { Text("Keep existing values") }
        }
    )
}
