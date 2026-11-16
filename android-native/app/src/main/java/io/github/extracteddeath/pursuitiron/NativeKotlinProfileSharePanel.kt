package io.github.extracteddeath.pursuitiron

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** A private on-screen summary and Android text Sharesheet, never automatic sharing. */
@Composable
internal fun NativeKotlinProfileSharePanel(
    athleteId: String,
    progress: NativeProfileActivityProgress,
    displayName: String,
    verifiedFavoriteLabels: List<String>,
    hasLoggedThousandPoundTotal: Boolean
) {
    val context = LocalContext.current
    var previewOpen by rememberSaveable(athleteId) { mutableStateOf(false) }
    var includeName by rememberSaveable(athleteId) { mutableStateOf(false) }
    var includeFavorites by rememberSaveable(athleteId) { mutableStateOf(false) }
    var shareNotice by remember(athleteId) { mutableStateOf<String?>(null) }
    val summary = NativeKotlinProfileShareSummary.render(
        progress,displayName,verifiedFavoriteLabels,includeName,includeFavorites,
        hasLoggedThousandPoundTotal
    )
    PursuitCard {
        PursuitSectionHeading("Share training summary",
            "Preview exactly what another app would receive")
        Text("Share your activity level, verified workout totals and earned milestones. "+
            "Your name and favorite exercises are private unless you choose to include them.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={previewOpen=true},modifier=Modifier.fillMaxWidth()) {
            Text("Review sharing preview")
        }
        shareNotice?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(previewOpen) AlertDialog(
        onDismissRequest={previewOpen=false},
        title={Text("Review training summary")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .toggleable(includeName,role=Role.Checkbox,
                        onValueChange={includeName=it}),
                    verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=includeName,onCheckedChange=null)
                    Text("Include my display name")
                }
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .toggleable(value=includeFavorites,
                        enabled=verifiedFavoriteLabels.isNotEmpty(),role=Role.Checkbox,
                        onValueChange={includeFavorites=it}),
                    verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=includeFavorites,onCheckedChange=null,
                        enabled=verifiedFavoriteLabels.isNotEmpty())
                    Text("Include up to five favorite logged exercises")
                }
                Text(summary,style=MaterialTheme.typography.bodyMedium)
                Text("This opens the Android sharing chooser; nothing is sent until you choose a destination. "+
                    "This is a text summary, not an image or a certified strength record.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={TextButton(onClick={
            val send=Intent(Intent.ACTION_SEND).apply {
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,summary)
            }
            val chooser=Intent.createChooser(send,"Share training summary")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started=runCatching{context.startActivity(chooser)}.isSuccess
            shareNotice=if(started)null else "Could not open Android sharing. Nothing was sent."
            previewOpen=false
        }) {Text("Open Android sharing")}},
        dismissButton={TextButton(onClick={previewOpen=false}){Text("Cancel")}}
    )
}
