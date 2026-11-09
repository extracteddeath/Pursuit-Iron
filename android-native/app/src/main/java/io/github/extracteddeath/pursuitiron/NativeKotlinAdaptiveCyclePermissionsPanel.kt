package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.CustomCycleChangeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Durable rights correspond 1:1 with the engine's change classifier.
 * Splits, training-day count and exercise choices are separate permissions. */
private data class AdaptivePermissionChoice(
    val kind:CustomCycleChangeKind,
    val title:String,
    val detail:String
)

private val structureRights=listOf(
    AdaptivePermissionChoice(CustomCycleChangeKind.BLOCKS,"Adapt training blocks",
        "Suggest adding, removing or rearranging future blocks."),
    AdaptivePermissionChoice(CustomCycleChangeKind.PHASE,"Adapt phase",
        "Suggest a future hypertrophy, strength or peak phase change."),
    AdaptivePermissionChoice(CustomCycleChangeKind.SPLIT,"Adapt training split",
        "Consider a different weekly split for a future block."),
    AdaptivePermissionChoice(CustomCycleChangeKind.SCHEDULE,"Adapt workout days",
        "Consider moving days within a future weekly schedule."),
    AdaptivePermissionChoice(CustomCycleChangeKind.DAY_COUNT,"Adapt days per week",
        "Consider a different future training frequency, separately from split."),
    AdaptivePermissionChoice(CustomCycleChangeKind.EXERCISES,"Adapt exercises",
        "Consider changing eligible exercises after checking your gym."),
    AdaptivePermissionChoice(CustomCycleChangeKind.REMOVE_PINNED,"Allow removing pinned lifts",
        "Permit reviewing removal of athlete-pinned exercises, never automatically.")
)
private val prescriptionRights=listOf(
    AdaptivePermissionChoice(CustomCycleChangeKind.GOAL,"Change training goal",
        "Consider a different long-term goal for a reviewed cycle."),
    AdaptivePermissionChoice(CustomCycleChangeKind.ATTACHMENTS,"Adapt attachments",
        "Review different exercise attachments and variants."),
    AdaptivePermissionChoice(CustomCycleChangeKind.SUPERSETS,"Adapt supersets",
        "Review changes to paired exercises and superset structure."),
    AdaptivePermissionChoice(CustomCycleChangeKind.SET_COUNT,"Adapt set counts",
        "Review changes to working-set volume."),
    AdaptivePermissionChoice(CustomCycleChangeKind.LOADS,"Adapt weights",
        "Review different loads when independently verified."),
    AdaptivePermissionChoice(CustomCycleChangeKind.REPS,"Adapt repetition ranges",
        "Review different rep targets."),
    AdaptivePermissionChoice(CustomCycleChangeKind.RIR,"Adapt target RIR",
        "Review changes to intended effort."),
    AdaptivePermissionChoice(CustomCycleChangeKind.REST,"Adapt rest periods",
        "Review rest or prescribed working time."),
    AdaptivePermissionChoice(CustomCycleChangeKind.AMPLIFIERS,"Adapt intensifiers",
        "Review drop sets, myo-reps and other authored amplifiers."),
    AdaptivePermissionChoice(CustomCycleChangeKind.CUES,"Adapt coaching notes",
        "Review the cycle's descriptive coaching guidance."),
    AdaptivePermissionChoice(CustomCycleChangeKind.WEEK_OVERRIDES,"Adapt individual weeks",
        "Review week-specific exceptions to base prescriptions.")
)

@Composable
private fun AdaptivePermissionGroup(
    title:String,
    choices:List<AdaptivePermissionChoice>,
    selected:Set<CustomCycleChangeKind>,
    enabled:Boolean,
    onToggle:(CustomCycleChangeKind,Boolean)->Unit
) {
    PursuitSectionHeading(title)
    choices.forEach {choice ->
        Row(
            Modifier.fillMaxWidth().heightIn(min=56.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
        ) {
            Column(Modifier.weight(1f)) {
                Text(choice.title,style=MaterialTheme.typography.bodyLarge)
                Text(choice.detail,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked=choice.kind in selected,
                enabled=enabled,
                onCheckedChange={onToggle(choice.kind,it)})
        }
    }
}

/** A genuine opt-in native permission editor, not a mock toggle.
 * Its only authority is to save/revoke versioned rights for a specific cycle.
 * All actual block options must still pass independent full-cycle source,
 * fatigue, schedule, equipment, consent and issuance checks.
 */
@Composable
internal fun NativeKotlinAdaptiveCyclePermissionsPanel(
    host:NativeKotlinCandidateHost,
    cycleId:String,
    expectedAthleteId:String,
    sourceRevision:Long
) {
    var expanded by rememberSaveable(cycleId){mutableStateOf(false)}
    var state by remember(host,cycleId,expectedAthleteId){
        mutableStateOf<NativeAdaptivePermissionRead?>(null)
    }
    var selected by remember(host,cycleId,expectedAthleteId){
        mutableStateOf<Set<CustomCycleChangeKind>>(emptySet())
    }
    var loading by remember(host,cycleId,expectedAthleteId){mutableStateOf(true)}
    var saving by remember(host,cycleId,expectedAthleteId){mutableStateOf(false)}
    var confirmed by remember(host,cycleId,expectedAthleteId){mutableStateOf(false)}
    var message by remember(host,cycleId,expectedAthleteId){mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    suspend fun refresh() {
        loading=true
        val read=withContext(Dispatchers.IO) {
            runCatching{host.readAdaptiveCyclePermissions(cycleId)}.getOrNull()
        } ?: NativeAdaptivePermissionRead.Rejected(
            NativeAdaptivePermissionRefusal.STORAGE_UNAVAILABLE)
        // Never display another athlete's grant if the owner changed
        // while this panel was mounted.
        val found=(read as? NativeAdaptivePermissionRead.Found)?.receipt
        state=if(found!=null &&
            (found.cycleId!=cycleId || found.athleteId!=expectedAthleteId))
            NativeAdaptivePermissionRead.Rejected(
                NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        else read
        selected=(state as? NativeAdaptivePermissionRead.Found)
            ?.receipt?.allowedKinds.orEmpty()
        confirmed=false
        loading=false
    }
    LaunchedEffect(host,cycleId,expectedAthleteId,sourceRevision){refresh()}
    val existing=(state as? NativeAdaptivePermissionRead.Found)
        ?.receipt?.takeIf{
            it.cycleId==cycleId && it.athleteId==expectedAthleteId
        }
    val revision=when(val read=state) {
        NativeAdaptivePermissionRead.Missing -> 0L
        is NativeAdaptivePermissionRead.Revoked -> read.revision
        is NativeAdaptivePermissionRead.Found -> existing?.revision
        else -> null
    }
    val saved=existing?.allowedKinds.orEmpty()
    val changed=selected!=saved || existing==null && selected.isNotEmpty()
    val canEdit=!loading && !saving && revision!=null
    PursuitCard {
        PursuitSectionHeading("Adaptive cycle permissions",
            "Choose which changes your coach may suggest for this exact program.")
        Text(when {
            loading -> "Checking your saved adaptation permissions…"
            state is NativeAdaptivePermissionRead.Rejected ->
                "Permissions could not be verified. No changes can be saved until they are reloaded."
            existing!=null -> "${existing.allowedKinds.size} types of change permitted for review."
            state is NativeAdaptivePermissionRead.Revoked ->
                "Previously granted permissions are revoked."
            else -> "No adaptive changes have been authorized."
        },style=MaterialTheme.typography.bodyMedium)
        Text("Permission is not approval. The coach cannot silently change splits, days, exercises or weights. Every proposed change still needs an independent review and your approval.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={expanded=!expanded},
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(expanded)"Hide adaptation controls" else "Choose adaptation controls")
        }
        if(expanded) {
            AdaptivePermissionGroup("Training structure",
                structureRights,selected,canEdit) {kind,checked->
                selected=if(checked)selected+kind else selected-kind
                confirmed=false
                message=null
            }
            HorizontalDivider()
            var showAdvanced by rememberSaveable(cycleId) {mutableStateOf(false)}
            OutlinedButton(onClick={showAdvanced=!showAdvanced},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(if(showAdvanced)"Hide prescription permissions"
                    else "More adaptation permissions")
            }
            if(showAdvanced)AdaptivePermissionGroup("Exercise and workout prescriptions",
                prescriptionRights,selected,canEdit) {kind,checked->
                selected=if(checked)selected+kind else selected-kind
                confirmed=false
                message=null
            }
            if(changed && canEdit) {
                HorizontalDivider()
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=confirmed,
                        onCheckedChange={confirmed=it})
                    Text("I authorize the selected kinds of proposals for this program. Changes will not be applied automatically.",
                        style=MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled=confirmed && !saving,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    onClick={
                        val expected=revision ?:return@Button
                        val chosen=selected.toSet()
                        saving=true
                        message=null
                        scope.launch {
                            val result=withContext(Dispatchers.IO) {
                                runCatching {
                                    host.confirmAdaptiveCyclePermissions(
                                        cycleId,expected,chosen,true)
                                }.getOrNull()
                            }
                            refresh()
                            val verified=(state as? NativeAdaptivePermissionRead.Found)
                                ?.receipt?.takeIf{
                                    it.cycleId==cycleId &&
                                    it.athleteId==expectedAthleteId
                                }
                            message=if(result is NativeAdaptivePermissionWrite.Committed &&
                                verified?.revision==result.revision &&
                                verified.allowedKinds==chosen)
                                "Adaptation permissions saved for this program. Proposals still require review."
                            else "Could not verify saved permissions. Review the reloaded record before continuing."
                            saving=false
                        }
                    }) {Text(if(saving)"Saving permissions…" else "Save permissions")}
            }
            if(existing!=null) {
                OutlinedButton(
                    enabled=canEdit,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    onClick={
                        val expected=revision ?:return@OutlinedButton
                        saving=true
                        message=null
                        scope.launch {
                            val result=withContext(Dispatchers.IO) {
                                runCatching {
                                    host.revokeAdaptiveCyclePermissions(cycleId,expected)
                                }.getOrNull()
                            }
                            refresh()
                            val revoked=state as? NativeAdaptivePermissionRead.Revoked
                            message=if(result is NativeAdaptivePermissionWrite.Committed &&
                                revoked?.revision==result.revision)
                                "All adaptation permissions revoked. Saved workouts remain unchanged."
                            else "Could not verify revocation. Review the current reloaded permissions."
                            saving=false
                        }
                    }) {Text("Revoke all adaptation permissions")}
            }
            if(state is NativeAdaptivePermissionRead.Rejected)
                TextButton(onClick={scope.launch {refresh()}},enabled=!loading) {
                    Text("Retry verified permissions")
                }
        }
        message?.let {Text(it,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
