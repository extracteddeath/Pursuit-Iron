package io.github.extracteddeath.pursuitiron

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Image preview and PNG are the SAME bitmap. Text sharing is retained for
 * accessibility and plain-text workflows; neither path runs automatically.
 */
@Composable
internal fun NativeKotlinProfileSharePanel(
    athleteId:String,
    progress:NativeProfileActivityProgress,
    displayName:String,
    verifiedFavoriteLabels:List<String>,
    hasLoggedThousandPoundTotal:Boolean
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var previewOpen by rememberSaveable(athleteId){mutableStateOf(false)}
    var includeName by rememberSaveable(athleteId){mutableStateOf(false)}
    var includeFavorites by rememberSaveable(athleteId){mutableStateOf(false)}
    var sharing by remember(athleteId){mutableStateOf(false)}
    var shareNotice by remember(athleteId){mutableStateOf<String?>(null)}
    val summary=NativeKotlinProfileShareSummary.render(
        progress,displayName,verifiedFavoriteLabels,includeName,includeFavorites,
        hasLoggedThousandPoundTotal)
    val scheme=MaterialTheme.colorScheme
    val colors=NativeProfileShareImageColors(
        scheme.background.toArgb(),scheme.surfaceContainerHigh.toArgb(),
        scheme.onSurface.toArgb(),scheme.onSurfaceVariant.toArgb(),scheme.primary.toArgb()
    )

    PursuitCard {
        PursuitSectionHeading("Share your training card",
            "Review the exact information before sharing an image or text")
        Text("Show your verified activity, level and milestones. Your display name "+
            "and favorite logged exercises are excluded unless you opt in.",
            style=MaterialTheme.typography.bodySmall,
            color=scheme.onSurfaceVariant)
        OutlinedButton(onClick={previewOpen=true},modifier=Modifier.fillMaxWidth()) {
            Text("Preview share card")
        }
        shareNotice?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=scheme.onSurfaceVariant)
        }
    }

    if(previewOpen) {
        // Rendering only after opening avoids expensive bitmaps on the normal
        // Profile screen. Recompute whenever an explicit privacy toggle changes.
        val bitmap=remember(summary,colors) {
            NativeKotlinProfileShareImage.render(summary,colors)
        }
        AlertDialog(
            onDismissRequest={if(!sharing) previewOpen=false},
            title={Text("Review share card")},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .toggleable(value=includeName,role=Role.Checkbox,
                            onValueChange={includeName=it}),
                        verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=includeName,onCheckedChange=null)
                        Text("Include my display name")
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .toggleable(value=includeFavorites,
                            enabled=verifiedFavoriteLabels.isNotEmpty(),
                            role=Role.Checkbox,
                            onValueChange={includeFavorites=it}),
                        verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=includeFavorites,onCheckedChange=null,
                            enabled=verifiedFavoriteLabels.isNotEmpty())
                        Text("Include favorite logged exercises")
                    }
                    Image(
                        bitmap=bitmap.asImageBitmap(),
                        contentDescription="Exact share card preview: "+summary,
                        contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxWidth().heightIn(min=230.dp,max=420.dp)
                    )
                    Text("The PNG and preview contain the same information. "+
                        "Sharing is optional; the receiving app may retain the image. "+
                        "This is logged activity, not a certified strength record.",
                        style=MaterialTheme.typography.bodySmall,
                        color=scheme.onSurfaceVariant)
                }
            },
            confirmButton={
                Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                    Button(
                        onClick={
                            val reviewedBitmap=bitmap
                            sharing=true
                            scope.launch {
                                val result=withContext(Dispatchers.IO) {
                                    runCatching {
                                        NativeKotlinProfileShareImage
                                            .writeForExplicitShare(context,reviewedBitmap)
                                    }
                                }
                                result.fold(
                                    onSuccess={ uri ->
                                        val send=Intent(Intent.ACTION_SEND).apply {
                                            type="image/png"
                                            putExtra(Intent.EXTRA_STREAM,uri)
                                            clipData=ClipData.newRawUri(
                                                "Pursuit Iron training summary",uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val started=runCatching {
                                            context.startActivity(Intent.createChooser(
                                                send,"Share training image"))
                                        }.isSuccess
                                        shareNotice=if(started)null else
                                            "Could not open Android sharing. The image remains private."
                                    },
                                    onFailure={
                                        shareNotice="Could not prepare sharing image. Nothing was sent."
                                    }
                                )
                                sharing=false
                                previewOpen=false
                            }
                        },
                        enabled=!sharing,
                        modifier=Modifier.fillMaxWidth()
                    ) {Text(if(sharing)"Preparing image…" else "Share image")}
                    TextButton(
                        onClick={
                            val send=Intent(Intent.ACTION_SEND).apply {
                                type="text/plain"
                                putExtra(Intent.EXTRA_TEXT,summary)
                            }
                            val started=runCatching {
                                context.startActivity(Intent.createChooser(
                                    send,"Share training summary"))
                            }.isSuccess
                            shareNotice=if(started)null else
                                "Could not open Android sharing. Nothing was sent."
                            previewOpen=false
                        },
                        enabled=!sharing,
                        modifier=Modifier.fillMaxWidth()
                    ) {Text("Share as text")}
                }
            },
            dismissButton={
                TextButton(onClick={previewOpen=false},enabled=!sharing) {
                    Text("Cancel")
                }
            }
        )
    }
}
