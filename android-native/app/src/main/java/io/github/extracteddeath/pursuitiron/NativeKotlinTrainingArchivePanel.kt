package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Export requires two separate owner-verified reads: one immediately before
 * opening Android's Save As picker and one AFTER Android hands back the URI.
 * A stale picker, profile switch, correction or measurement revision refuses
 * to write instead of shipping data from another athlete.
 */
private data class NativeTrainingArchiveTicket(
    val ownerKey:String,
    val athleteId:String,
    val historyRevision:Long,
    val measurementRevision:Long
)

@Composable
internal fun NativeKotlinTrainingArchivePanel(
    host:NativeKotlinCandidateHost,
    athleteId:String?,
    historyRevision:Long?
) {
    val context=LocalContext.current.applicationContext
    val store=remember(context){NativeKotlinBodyweightStore(context)}
    val scope=rememberCoroutineScope()
    var pending by remember(host){mutableStateOf<NativeTrainingArchiveTicket?>(null)}
    var busy by remember(host){mutableStateOf(false)}
    var notice by remember(host){mutableStateOf<String?>(null)}
    LaunchedEffect(athleteId) {
        pending=null
        notice=null
    }
    suspend fun verify():
        Pair<NativeTrainingArchiveTicket,Pair<
            NativeKotlinMixedHistoryTimeline,NativeBodyweightLedger>>? {
        val before=host.currentHistoryExportOwner() ?:return null
        val active=(host.reviewActiveAthleteSetup() as?
            NativeSavedSetupResume.Ready)?.setup?.athleteProfileId
        val history=(host.reviewVerifiedKotlinHistory() as?
            NativeKotlinMixedHistoryReadResult.Review)?.timeline
        val weights=(store.read(before.athleteId) as?
            NativeBodyweightRead.Ready)?.ledger
        val after=host.currentHistoryExportOwner()
        if(before!=after || before.athleteId!=athleteId ||
            active!=athleteId || history==null || weights==null ||
            history.athleteId!=athleteId ||
            history.manifestRevision!=historyRevision)return null
        return NativeTrainingArchiveTicket(
            before.ownerKey,before.athleteId,history.manifestRevision,
            weights.revision) to (history to weights)
    }
    val launcher=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val expected=pending
        pending=null
        if(uri==null) {
            busy=false
            notice="Export canceled. No records were shared."
        } else if(expected==null) {
            busy=false
            notice="The export request expired. The new file may be empty; delete it and try again."
        } else {
            scope.launch {
                notice=withContext(Dispatchers.IO) {
                    val checked=runCatching{verify()}.getOrNull()
                    val ticket=checked?.first
                    val records=checked?.second
                    if(ticket!=expected || records==null) {
                        "Training or bodyweight records changed while Save As was open. "+
                            "Nothing was exported. Delete the empty file and retry."
                    } else when(val archive=NativeKotlinTrainingArchiveDomain.prepare(
                        ticket.athleteId,records.first,records.second)) {
                        is NativeTrainingArchiveResult.Rejected ->
                            "The verified archive could not be prepared (${archive.reason}). "+
                                "Your data is unchanged; delete the empty file and retry."
                        is NativeTrainingArchiveResult.Ready -> {
                            val success=runCatching {
                                val before=host.currentHistoryExportOwner()
                                check(before!=null &&
                                    before.ownerKey==ticket.ownerKey &&
                                    before.athleteId==ticket.athleteId)
                                // Android's document URI belongs to the athlete's
                                // explicit CreateDocument choice, never an app
                                // path or an automatic remote upload.
                                context.contentResolver.openOutputStream(uri,"wt")
                                    ?.use {output->
                                        output.write(archive.bytes)
                                        output.flush()
                                    } ?:error("Destination unavailable")
                            }.isSuccess
                            if(success)
                                "Exported ${archive.sessions} completed workouts, "+
                                    "${archive.setRows} set entries and "+
                                    "${archive.bodyweightRows} weigh-ins. "+
                                    "Keep the ZIP private; it is not an app backup."
                            else
                                "The archive could not be written. The selected "+
                                    "file may be incomplete; delete it and try again."
                        }
                    }
                }
                busy=false
            }
        }
    }
    PursuitCard {
        PursuitSectionHeading("Export your training records",
            "Save a portable copy of verified workouts and bodyweight history.")
        Text("The ZIP includes completed native set outcomes and corrections "+
            "plus dated bodyweight measurements. It does not include program "+
            "approvals, unfinished sessions, coach settings, gym calibration, "+
            "notes or historical web workouts. It cannot restore the app.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick={
                if(!busy) {
                    busy=true
                    notice=null
                    pending=null
                    scope.launch {
                        val verified=withContext(Dispatchers.IO) {
                            runCatching {verify()?.first}.getOrNull()
                        }
                        if(verified==null) {
                            notice="Verify your athlete, completed History and "+
                                "bodyweight records before exporting."
                            busy=false
                        } else {
                            pending=verified
                            try {
                                launcher.launch(
                                    "Pursuit-Iron-native-records-${LocalDate.now()}.zip")
                            } catch(_:Exception) {
                                pending=null
                                busy=false
                                notice="Android Save As could not open. No file was shared."
                            }
                        }
                    }
                }
            },
            enabled=!busy && athleteId!=null && historyRevision!=null,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) {
            Text(if(busy)"Preparing private export…" else
                "Export workout + bodyweight ZIP")
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let {Text(it,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text("The unencrypted export is saved only to the file destination "+
            "you select. Nothing is sent automatically.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
