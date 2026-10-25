package com.pursuitiron.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.data.Photos
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/* The design's progress photos (You → Body). Private to this phone: see data/Photos.kt for the rules. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressPhotosCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val store = remember { Photos(ctx) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var photos by remember { mutableStateOf(store.list()) }
    val picked = remember { mutableStateListOf<String>() }
    var deleting by remember { mutableStateOf<Photos.Photo?>(null) }
    val add = { uri: Uri? ->
        if (uri != null) scope.launch {
            val p = store.add(uri, "Front")
            if (p == null) vm.toast("That photo couldn't be read") else {
                photos = store.list(); vm.toast("Saved on this phone only")
                if (photos.size >= 2) { picked.clear(); picked.add(photos.first().id); picked.add(p.id) }
            }
        }
        Unit
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { add(it) }
    val shotFile = remember { File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot.jpg") }
    val shotUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", shotFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) add(shotUri) }

    var adding by remember { mutableStateOf(false) }
    val md = remember { java.text.SimpleDateFormat("MMM d", java.util.Locale.US) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Progress photos", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Icon(PiIcons.Lock, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            Text("Private · this phone only", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
        }
        Text(if (picked.size == 2) "Comparing two photos. Tap a photo to change the pair." else if (photos.size >= 2) "Tap two photos to compare them side by side." else "Same pose, same light and the same time of day make photos comparable.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
        val tiles: List<Photos.Photo?> = photos + listOf(null)
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    if (p == null) {
                        Box(Modifier.weight(1f).aspectRatio(3f / 4f)) {
                            Column(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(18.dp))
                                .clickable { adding = true }.semantics(mergeDescendants = true) { role = androidx.compose.ui.semantics.Role.Button },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                                Icon(PiIcons.Plus, null, tint = cs.primary)
                                Text("Add photo", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary)
                            }
                            DropdownMenu(adding, { adding = false }) {
                                DropdownMenuItem(text = { Text("Take photo") }, leadingIcon = { Icon(PiIcons.Plus, null) }, onClick = { adding = false; runCatching { camera.launch(shotUri) }.onFailure { vm.toast("No camera app found") } })
                                DropdownMenuItem(text = { Text("Choose photo") }, leadingIcon = { Icon(PiIcons.Upload, null) }, onClick = { adding = false; choose.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            }
                        }
                    } else {
                        val on = p.id in picked
                        val label = md.format(Date(p.date))
                        val r by androidx.compose.animation.core.animateDpAsState(if (on) 14.dp else 18.dp, androidx.compose.animation.core.spring(dampingRatio = 0.6f), label = "ph")
                        Box(Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(r)).background(cs.surfaceContainerHigh)
                            .combinedClickable(onClick = { if (on) picked.remove(p.id) else { if (picked.size >= 2) picked.removeAt(0); picked.add(p.id) } }, onLongClick = { deleting = p })
                            .semantics { contentDescription = "Photo, ${p.pose}, $label" + (if (on) ", selected for compare" else "") + ". Long-press to delete"; selected = on }) {
                            PhotoImage(p.file, 400, Modifier.fillMaxSize())
                            if (on) Box(Modifier.fillMaxSize().border(3.dp, cs.primary, RoundedCornerShape(r)))
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = cs.onSurface,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(cs.surface).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        val pair = picked.mapNotNull { id -> photos.firstOrNull { it.id == id } }.sortedBy { it.date }
        androidx.compose.animation.AnimatedVisibility(pair.size == 2, enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            if (pair.size < 2) return@AnimatedVisibility
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { p ->
                        Box(Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(16.dp)).background(cs.surface)) {
                            PhotoImage(p.file, 1200, Modifier.fillMaxSize())
                            Text(md.format(Date(p.date)), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = cs.onPrimary,
                                modifier = Modifier.padding(8.dp).clip(CircleShape).background(cs.primary).padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
                Text("${md.format(Date(pair[0].date))} → ${md.format(Date(pair[1].date))} · same pose, same light works best", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this photo?") },
            text = { Text("It's removed from this phone. There's no other copy.") },
            confirmButton = { TextButton(onClick = { store.delete(p.id); picked.remove(p.id); photos = store.list(); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } })
    }
}

@Composable
private fun PhotoImage(file: File, maxPx: Int, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(null, file, file.lastModified(), maxPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, b)
                var s = 1; while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val i = img
    if (i != null) Image(i, null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh))
}
