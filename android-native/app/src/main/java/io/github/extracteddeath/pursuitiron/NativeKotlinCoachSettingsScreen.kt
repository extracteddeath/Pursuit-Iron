package io.github.extracteddeath.pursuitiron

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCoachSettingsRead
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCoachSettingsWrite
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCoachAssistanceSettings
import io.github.extracteddeath.pursuitiron.rebuild.CoachAssistancePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Compose-only preference editor for the isolated candidate app.
 *
 * The store owns the accepted revision. A toggle is never shown as saved
 * until the owner-fenced, checksummed write succeeds; conflicts force a
 * fresh read instead of silently overriding changes from another screen.
 */
@Composable
internal fun NativeKotlinCoachSettingsScreen(
    host:NativeKotlinCandidateHost,
    themeId:String=PursuitThemes.DEFAULT_ID,
    onOpenAppearance:()->Unit={}
) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var feedbackOpen by rememberSaveable { mutableStateOf(false) }
    var feedbackDraft by rememberSaveable { mutableStateOf("") }
    var current by remember(host) {
        mutableStateOf<NativeCoachAssistanceSettings?>(null)
    }
    var loading by remember(host){mutableStateOf(true)}
    var saving by remember(host){mutableStateOf(false)}
    var notice by remember(host){mutableStateOf<String?>(null)}

    fun refresh() {
        loading=true
        scope.launch {
            val result=runCatching {
                withContext(Dispatchers.IO){host.readCoachAssistance()}
            }.getOrNull()
            current=(result as? NativeCoachSettingsRead.Current)?.settings
            notice=if(current==null)
                "Coaching preferences could not be verified. Suggestions remain unavailable."
            else null
            loading=false
        }
    }

    LaunchedEffect(host) {
        val result=runCatching {
            withContext(Dispatchers.IO){host.readCoachAssistance()}
        }.getOrNull()
        current=(result as? NativeCoachSettingsRead.Current)?.settings
        notice=if(current==null)
            "Coaching preferences could not be verified. Suggestions remain unavailable."
        else null
        loading=false
    }

    fun save(next:CoachAssistancePolicy) {
        val existing=current?:return
        if(loading || saving || existing.policy==next)return
        saving=true
        notice=null
        scope.launch {
            val result=runCatching {
                withContext(Dispatchers.IO){
                    host.updateCoachAssistance(existing.revision,next)
                }
            }.getOrNull()
            when(result) {
                is NativeCoachSettingsWrite.Saved -> {
                    current=result.settings
                    notice="Coaching preferences saved."
                }
                else -> {
                    // Read the authoritative version again after a conflict,
                    // owner switch, disk error, or interrupted update.
                    val fresh=runCatching {
                        withContext(Dispatchers.IO){host.readCoachAssistance()}
                    }.getOrNull()
                    current=(fresh as? NativeCoachSettingsRead.Current)?.settings
                    notice="Settings were not changed. Review the current values and retry."
                }
            }
            saving=false
        }
    }

    val policy=current?.policy
    val editable=policy!=null && !loading && !saving
    PursuitScreenContent {
        PageHeading(
            "PREFERENCES",
            "Your coaching, your choice",
            "Optional suggestions never control workout logging or your authored program."
        )
        PursuitFormSection("Appearance",
            "Choose from the original Pursuit Iron web-app themes. This changes the app's colors, not training data."
        ) {
            Text("Current: ${PursuitThemes.resolve(themeId).name}",
                style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick=onOpenAppearance) {
                Text("Choose color theme")
            }
        }

        PursuitFormSection(
            "Coaching style",
            "Choose a starting point, then customize the individual switches."
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected=policy==CoachAssistancePolicy.STANDARD,
                    enabled=editable,
                    onClick={save(CoachAssistancePolicy.STANDARD)},
                    label={Text("Full coach")}
                )
                FilterChip(
                    selected=policy==CoachAssistancePolicy.PROGRESSION_ONLY,
                    enabled=editable,
                    onClick={save(CoachAssistancePolicy.PROGRESSION_ONLY)},
                    label={Text("Straightforward")}
                )
                FilterChip(
                    selected=policy?.coachingSuggestionsEnabled==false,
                    enabled=editable,
                    onClick={save(CoachAssistancePolicy.MANUAL)},
                    label={Text("Manual")}
                )
            }
            if(loading) {
                Text("Loading your saved preferences…")
            } else if(policy==null) {
                Text("Unable to verify preferences. Coaching suggestions will stay off.",
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodyMedium)
                Button(onClick={refresh()},enabled=!saving) {
                    Text("Try again")
                }
            }
        }

        if(policy!=null) {
            PursuitFormSection("Coach controls",
                "Each switch applies only to future suggestions. No automatic plan edits."
            ) {
                PreviewSettingSwitch(
                    title="Coaching suggestions",
                    description="Master switch for coaching advice and adaptive recommendations.",
                    checked=policy.coachingSuggestionsEnabled,
                    enabled=editable,
                    onCheckedChange={save(policy.copy(coachingSuggestionsEnabled=it))}
                )
                if(policy.coachingSuggestionsEnabled) {
                    PreviewSettingSwitch(
                        title="Fatigue-aware coaching",
                        description="Let reported readiness influence suggestions. Actual failed sets and movement restrictions always count.",
                        checked=policy.fatigueAdaptationEnabled,
                        enabled=editable,
                        onCheckedChange={save(policy.copy(fatigueAdaptationEnabled=it))}
                    )
                    PreviewSettingSwitch(
                        title="Progression suggestions",
                        description="Offer verified rep and weight increases from completed workouts.",
                        checked=policy.progressionSuggestionsEnabled,
                        enabled=editable,
                        onCheckedChange={save(policy.copy(progressionSuggestionsEnabled=it))}
                    )
                } else {
                    Text(
                        "Manual training is active. Your planned sets, gym equipment, history, and Finish remain available without suggestions.",
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PursuitFormSection("What stays in your control",
                "These rules apply in every mode."
            ) {
                Text(
                    "Your program isn't rewritten when you change these settings. Proposed changes always need your approval; existing workouts and completed history remain intact.",
                    style=MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Equipment limits and movement restrictions still apply, even with fatigue guidance turned off.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        PursuitFormSection(
            "Early beta feedback",
            "Tell us which workout or screen felt wrong. You decide what to share."
        ) {
            OutlinedButton(onClick={feedbackOpen=true}) {
                Text("Share beta feedback")
            }
            Text(
                "Opens an app you choose. No training records, injury notes or diagnostics are attached automatically.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if(saving) {
            Text("Saving coaching preferences…",
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        notice?.let {
            Text(it,style=MaterialTheme.typography.bodyMedium,
                color=if(current==null)MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Isolated Kotlin candidate · production settings are not changed.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(feedbackOpen) {
        AlertDialog(
            onDismissRequest={ feedbackOpen=false },
            title={Text("Share early beta feedback")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Describe what happened or what you would improve. Avoid personal or medical details unless you choose to include them.",
                        style=MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value=feedbackDraft,
                        onValueChange={feedbackDraft=it.take(2000)},
                        label={Text("Your feedback")},
                        minLines=3,
                        maxLines=7,
                        modifier=Modifier.fillMaxWidth()
                    )
                    Text("Only your message and app version are shared after you choose an app. This does not send anything automatically.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton={
                TextButton(
                    enabled=feedbackDraft.isNotBlank(),
                    onClick={
                        val version=runCatching {
                            context.packageManager.getPackageInfo(
                                context.packageName,0).versionName
                        }.getOrNull().orEmpty().ifBlank { "unknown" }
                        val report="Pursuit Iron early beta feedback\n"+
                            "App version: $version\n\n"+
                            feedbackDraft.trim()
                        val send=Intent(Intent.ACTION_SEND).apply {
                            type="text/plain"
                            putExtra(Intent.EXTRA_SUBJECT,
                                "Pursuit Iron early beta feedback")
                            putExtra(Intent.EXTRA_TEXT,report)
                        }
                        val launched=runCatching {
                            context.startActivity(Intent.createChooser(
                                send,"Choose where to share feedback"))
                        }.isSuccess
                        if(launched) feedbackOpen=false
                        else notice="No app could open the feedback share sheet."
                    }
                ) {Text("Choose app to send")}
            },
            dismissButton={
                TextButton(onClick={feedbackOpen=false}) {Text("Cancel")}
            }
        )
    }
}
