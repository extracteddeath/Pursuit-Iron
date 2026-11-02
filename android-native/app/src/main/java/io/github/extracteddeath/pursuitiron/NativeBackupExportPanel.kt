package io.github.extracteddeath.pursuitiron

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.extracteddeath.pursuitiron.domain.NativeBackupExportDocument
import java.io.IOException
import java.time.LocalDate

/** Only a newly created Save As destination is passed to the process-owned exporter. */
private class AndroidBackupExportDocument(private val resolver: ContentResolver,private val uri: Uri): NativeBackupExportDocument {
    override fun openOutput()=resolver.openOutputStream(uri,"wt") ?: throw IOException("Backup destination unavailable")
    override fun openInput()=resolver.openInputStream(uri) ?: throw IOException("Backup readback unavailable")
    override fun discard() { check(DocumentsContract.deleteDocument(resolver,uri)) }
}

@Composable
internal fun NativeBackupExportPanel(state: NativeRuntimeState,onExport: (NativeBackupExportDocument)->Unit,onReset: ()->Unit) {
    val resolver=LocalContext.current.applicationContext.contentResolver
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pickerMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val destination=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        choosing=false
        if(uri==null)pickerMessage="Backup canceled."
        else { pickerMessage=null;onExport(AndroidBackupExportDocument(resolver,uri)) }
    }
    PursuitCard {
        Text("Save a training backup",style=MaterialTheme.typography.titleMedium)
        Text("Keep a copy of your saved programs and completed training data.")
        if(state.workout!=null)Text("Finish your active workout before saving a backup.",style=MaterialTheme.typography.bodySmall)
        if(state.backupExportBusy)LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        Button(onClick={
            onReset();pickerMessage=null;choosing=true
            try { destination.launch("Pursuit-Iron-backup-${LocalDate.now()}.json") }
            catch(_: Exception) { choosing=false;pickerMessage="The file picker could not be opened. Try again." }
        },enabled=state.ready && !state.blocked && !state.busy && !state.workoutBlocked &&
            !state.historyBusy && !state.workoutBusy && !state.backupExportBusy && state.workout==null && !choosing,
            modifier=Modifier.fillMaxWidth()) {
            Text(if(state.backupExportBusy)"Saving backup…" else if(choosing)"Choose a destination…" else "Save backup")
        }
        pickerMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        state.backupExportMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
        state.backupExportError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}
