package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A readable display label, never the athlete ID or a history authorization. */
private const val PROFILE_DISPLAY_PREFS="pursuit-profile-display-v1"
private const val MAX_PROFILE_LABEL_LENGTH=32
private val weekdays=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
private fun profileText(value:Enum<*>):String =
    value.name.lowercase().replace('_',' ')
        .replaceFirstChar{it.uppercase()}

private fun profileSplit(split:ComposerSplit):String=when(split) {
    ComposerSplit.FULL_BODY -> "Full Body"
    ComposerSplit.UPPER_LOWER -> "Upper / Lower"
    ComposerSplit.PUSH_PULL_LEGS -> "Push / Pull / Legs"
    ComposerSplit.ULPPL -> "Upper / Lower / Push / Pull / Legs"
    else -> profileText(split)
}

private fun verifiedWorkingCount(timeline:NativeKotlinMixedHistoryTimeline):Int =
    timeline.completions.sumOf { entry ->
        val summary=when(entry) {
            is NativeKotlinMixedHistoryCompletion.Composer ->
                KotlinHistoryPerformanceSummaryDomain.fromComposer(entry.evidence.sets)
            is NativeKotlinMixedHistoryCompletion.Custom ->
                KotlinHistoryPerformanceSummaryDomain.fromCustom(entry.evidence.visits)
        }
        summary.completedWorkingSets
    }

/**
 * Profile is a presentation and navigation surface, not a parallel owner or
 * a hidden user-account mechanism. Only source-verified setup and history
 * become numbers; unverifiable history is shown as unavailable, never zero.
 */
@Composable
internal fun NativeKotlinProfileScreen(
    host:NativeKotlinCandidateHost,
    themeId:String,
    onBack:()->Unit,
    onPlan:()->Unit,
    onProgress:()->Unit,
    onProgressLift:(String)->Unit,
    onGym:()->Unit,
    onSettings:()->Unit,
    onAppearance:()->Unit
) {
    val context=LocalContext.current.applicationContext
    val prefs=remember(context) {
        context.getSharedPreferences(PROFILE_DISPLAY_PREFS,0)
    }
    val favoritePreferences=remember(context) {
        context.getSharedPreferences(NativeStrengthFavorites.PREFS_NAME,0)
    }
    val exercisePreferences=remember(context) {
        context.getSharedPreferences(NativeExerciseFavorites.PREFS_NAME,0)
    }
    var setup by remember(host) {mutableStateOf<NativeSavedSetupResume?>(null)}
    var history by remember(host) {
        mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)
    }
    var refresh by remember{mutableIntStateOf(0)}
    var displayName by remember{mutableStateOf("")}
    var favoriteLifts by remember{mutableStateOf<List<String>>(emptyList())}
    var favoriteExercises by remember(host){
        mutableStateOf<List<String>>(emptyList())
    }
    var draftName by rememberSaveable{mutableStateOf("")}
    var editingName by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(host,refresh) {
        setup=null
        history=null
        val results=withContext(Dispatchers.IO) {
            host.reviewActiveAthleteSetup() to host.reviewVerifiedKotlinHistory()
        }
        setup=results.first
        history=results.second
    }
    val current=(setup as? NativeSavedSetupResume.Ready)?.setup
    LaunchedEffect(current?.athleteProfileId) {
        val athleteId=current?.athleteProfileId
        displayName=athleteId?.let {
            prefs.getString("name:$it","").orEmpty()
        }.orEmpty()
        favoriteLifts=if(athleteId==null)emptyList() else withContext(Dispatchers.IO) {
            NativeStrengthFavorites.read(favoritePreferences,athleteId)
        }
    }
    // Suppress stale owner history during a profile switch.
    val timeline=(history as? NativeKotlinMixedHistoryReadResult.Review)
        ?.timeline?.takeIf { it.athleteId==current?.athleteProfileId }
    val strengthClub=remember(timeline) {
        ((timeline?.reviewWorkingSetFacts()) as? NativeMixedFactsResult.Review)
            ?.facts?.let(NativeStrengthClubDomain::review)
    }
    LaunchedEffect(current?.athleteProfileId,timeline?.manifestRevision) {
        favoriteExercises=emptyList()
        val athleteId=current?.athleteProfileId
        val verified=timeline?.takeIf{it.athleteId==athleteId}
        if(athleteId!=null && verified!=null) {
            val stored=withContext(Dispatchers.IO) {
                NativeExerciseFavorites.read(exercisePreferences,athleteId,
                    NativeExerciseFavorites.loggedIds(verified).toSet())
            }
            if(current?.athleteProfileId==athleteId &&
                timeline?.athleteId==athleteId &&
                timeline?.manifestRevision==verified.manifestRevision)
                favoriteExercises=stored
        }
    }
    val finished=timeline?.completions?.size?.toString()?:"—"
    val working=timeline?.let{verifiedWorkingCount(it).toString()}?:"—"
    val days=current?.trainingWeekdays?.size?.toString()?:"—"
    PursuitScreenContent {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {
                Icon(Icons.Filled.ArrowBack,contentDescription="Back to previous screen")
            }
            Text("Your profile",style=MaterialTheme.typography.headlineSmall)
        }
        PursuitCard(highlighted=true) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.md)) {
                Surface(
                    modifier=Modifier.size(72.dp),
                    shape=MaterialTheme.shapes.extraLarge,
                    color=MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(Icons.Filled.Person,contentDescription=null,
                            modifier=Modifier.size(36.dp),
                            tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                    Text(displayName.ifBlank{"Pursuit athlete"},
                        style=MaterialTheme.typography.headlineSmall)
                    Text("Local training profile",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={
                        draftName=displayName
                        editingName=true
                    },enabled=current!=null) {
                        Text("Customize display name")
                    }
                }
            }
            PursuitMetricStrip(listOf(
                "Sessions" to finished,
                "Working sets" to working,
                "Days / week" to days
            ))
            if(timeline==null)Text(
                "History totals appear only after the native training ledger is verified; previous web records are not counted here.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when(val read=setup) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is NativeSavedSetupResume.Rejected ->
                PursuitEmptyState("Training setup unavailable",read.reason,
                    "Open Plan",onPlan)
            is NativeSavedSetupResume.Ready -> {
                val athlete=read.setup
                PursuitCard {
                    PursuitSectionHeading("Training preferences",
                        "Your saved, reviewed setup · changes belong in program review.")
                    PursuitMetricStrip(listOf(
                        "Goal" to profileText(athlete.goal),
                        "Level" to profileText(athlete.experience),
                        "Schedule" to athlete.trainingWeekdays.sorted().joinToString(" / ") {
                            weekdays.getOrElse(it){"Day"}
                        }
                    ))
                    Text("Split · ${profileSplit(athlete.split)}",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("Session preference · ${athlete.sessionTimeBucket.minimumMinutes}"+
                        (athlete.sessionTimeBucket.preferredMaximumMinutes?.let{
                            "–$it min"
                        }?:" min or longer"),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Gym · ${athlete.gym.confirmedStock.size} verified load sources · "+
                        "${athlete.gym.availableFacilities.size} facilities",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PursuitSectionHeading("Your progress",
            "Strength Standards and workout records from your verified training.")
        NativeKotlinStrengthClubPanel(strengthClub,onOpenStandards=onProgress)
        PursuitDestinationRow(
            title="Strength Standards",
            description="Compare your recorded lifts with sourced community benchmarks.",
            onClick=onProgress,
            icon={Icon(Icons.Filled.Insights,contentDescription=null)}
        )
        if(current!=null) {
            PursuitSectionHeading("Pinned strength charts",
                "Your saved favorites are shortcuts, not personal records or achievements.")
            if(favoriteLifts.isEmpty())Text(
                "No favorite lifts pinned yet. Open Strength Standards and pin the lifts you follow.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            favoriteLifts.forEach { liftId ->
                PursuitDestinationRow(
                    title=NativeStrengthFavorites.title(liftId),
                    description="Open this lift's verified estimated strength trend.",
                    onClick={onProgressLift(liftId)},
                    icon={Icon(Icons.Filled.Star,contentDescription=null)}
                )
            }
        }
        if(current!=null && timeline?.athleteId==current.athleteProfileId) {
            PursuitSectionHeading("Pinned exercise histories",
                "Your ordered favorite movements from completed, verified sessions.")
            if(favoriteExercises.isEmpty())Text(
                "Pin any logged exercise in Progress to keep its history here.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            favoriteExercises.forEach {exerciseId ->
                PursuitDestinationRow(
                    title=kotlinExerciseLabel(exerciseId),
                    description="Open full recorded sets, including corrections.",
                    onClick={onProgressLift(exerciseId)},
                    icon={Icon(Icons.Filled.Star,contentDescription=null)}
                )
            }
        }
        PursuitSectionHeading("Training and preferences")
        PursuitDestinationRow(
            title="Full training program",
            description="Review and edit your approved plan and future blocks.",
            onClick=onPlan,
            icon={Icon(Icons.Filled.FitnessCenter,contentDescription=null)}
        )
        PursuitDestinationRow(
            title="Gym and equipment",
            description="Verify the exact machines, attachments and available weights.",
            onClick=onGym,
            icon={Icon(Icons.Filled.Tune,contentDescription=null)}
        )
        PursuitDestinationRow(
            title="Training settings",
            description="Coach controls, assistance modes and workout preferences.",
            onClick=onSettings,
            icon={Icon(Icons.Filled.Settings,contentDescription=null)}
        )
        OutlinedButton(onClick=onAppearance,modifier=Modifier.fillMaxWidth()) {
            Text("Appearance · ${PursuitThemes.resolve(themeId).name}")
        }
        Text("This is a local profile, not an online account. Display-name edits never change your athlete ID, saved training or exercise history.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={refresh++},modifier=Modifier.fillMaxWidth()) {
            Text("Refresh verified profile")
        }
    }
    if(editingName)AlertDialog(
        onDismissRequest={editingName=false},
        title={Text("Customize profile name")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                OutlinedTextField(
                    value=draftName,
                    onValueChange={if(it.length<=MAX_PROFILE_LABEL_LENGTH)
                        draftName=it.filter{char->char.code>=32 && char.code!=127}},
                    singleLine=true,
                    label={Text("Display name")},
                    supportingText={Text("Optional, stored on this device only.")},
                    modifier=Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton={
            TextButton(onClick={
                current?.athleteProfileId?.let {
                    val sanitized=draftName.trim()
                    prefs.edit().putString("name:$it",sanitized).apply()
                    displayName=sanitized
                }
                editingName=false
            },enabled=current!=null) {Text("Save")}
        },
        dismissButton={
            TextButton(onClick={editingName=false}){Text("Cancel")}
        }
    )
}
