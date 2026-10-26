@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import java.io.File

@Composable
fun ShareCardButton(onShare: () -> Unit, onCustomize: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Row(Modifier.height(44.dp).clip(CircleShape).background(cs.secondaryContainer).clickable(onClick = onShare).semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Share, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
            Text("Share card", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
        }
        Row(Modifier.height(44.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable(onClick = onCustomize).semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Sliders, null, Modifier.size(18.dp))
            Text("Customize profile", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/* B892 (Main.dc.html pcard panel): a live preview of the share card, what it shows, the name on it and favorite lifts.
   Changes save as they're made; Share image draws the same rows as a 1080-wide image. Presentation only. */
@Composable
fun ShareCardSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val lay = vm.prefs.profileLayout
    var card by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(lay.share, lay.favorites) { card = vm.profileCard() }
    val title = lay.cardName.trim().ifBlank { "My training" }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Share card", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp))
            val rows = card?.list("rows").orEmpty()
            // The preview: the same content model as the exported image
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).animateContentSize()
                .semantics(mergeDescendants = true) { contentDescription = "Share card preview: $title. " + rows.joinToString("; ") { it.s("label") + " " + it.strings("lines").joinToString(", ") } }) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(cs.primary))
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PURSUIT IRON", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp), color = cs.primary)
                    Column {
                        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold))
                        Text("Built through every workout.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                    }
                    rows.forEach { r ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(r.s("label").uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp), color = cs.primary)
                            r.strings("lines").forEach { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)) }
                            Text(r.s("detail"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            ShareCap("NAME ON CARD", Modifier.padding(top = 4.dp))
            BasicTextField(lay.cardName, { vm.prefs.changeProfileLayout(lay.copy(cardName = it.take(60))) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).semantics { contentDescription = "Name on card" },
                decorationBox = { inner -> Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (lay.cardName.isEmpty()) Text("My training", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = cs.onSurfaceVariant)
                    inner() } })
            ShareCap("SHOW ON CARD")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card?.list("choices")?.forEach { c ->
                    val on = c.b("on"); val id = c.s("id"); val last = on && lay.share.size == 1
                    CardChip((if (on) "✓ " else "+ ") + c.s("label"), on) {
                        if (last) vm.toast("Keep at least one thing on the card")
                        else vm.prefs.changeProfileLayout(lay.copy(share = if (on) lay.share - id else lay.share + id))
                    }
                }
            }
            if ("favorites" in lay.share) {
                ShareCap("FAVORITE LIFTS · ${lay.favorites.size} OF 3")
                Text("Pick up to three. Logged records appear with them.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                val lifts = card?.list("lifts").orEmpty()
                if (lifts.isEmpty()) Text("Log a few sessions and your lifts appear here.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lifts.forEach { l ->
                        val id = l.s("id"); val on = id in lay.favorites
                        CardChip(l.s("name"), on) {
                            when { on -> vm.prefs.changeProfileLayout(lay.copy(favorites = lay.favorites - id))
                                lay.favorites.size >= 3 -> vm.toast("Up to three favorite lifts")
                                else -> vm.prefs.changeProfileLayout(lay.copy(favorites = lay.favorites + id)) }
                        }
                    }
                }
            }
            Text("Changes save automatically. Only what’s shown here goes on the image; your history stays on this device.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
            Button(onClick = { card?.let { runCatching { shareProfileCardImage(ctx, title, it, cs) }.onFailure { vm.toast("Couldn't make the image") } } },
                enabled = card != null, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(PiIcons.Share, null, Modifier.size(18.dp)); Text("  Share image", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun CardChip(label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (on) 12.dp else 20.dp)
    Box(Modifier.height(40.dp).clip(shape).background(if (on) cs.secondaryContainer else cs.surface)
        .then(if (on) Modifier else Modifier.border(1.dp, cs.outlineVariant, shape))
        .clickable(onClick = onClick).semantics { role = Role.Button; selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
    }
}

/** App.js shareCustomProfileCard: the card's rows drawn 1080 wide, shared through the system sheet. */
fun shareProfileCardImage(context: Context, title: String, card: JSONObject, cs: ColorScheme) {
    val rows = card.list("rows")
    val W = 1080; val pad = 104f; val inner = W - pad * 2
    val heights = rows.map { 142f + (it.strings("lines").size.coerceAtLeast(1) - 1) * 42f }
    val H = maxOf(1000, (400 + heights.sum() + rows.size * 16 + 140).toInt())
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    c.drawColor(cs.surface.toArgb())
    p.color = cs.surfaceContainerHigh.toArgb(); c.drawRoundRect(RectF(56f, 56f, W - 56f, H - 56f), 38f, 38f, p)
    c.save(); c.clipRect(56f, 56f, W - 56f, 70f); p.color = cs.primary.toArgb(); c.drawRoundRect(RectF(56f, 56f, W - 56f, H - 56f), 38f, 38f, p); c.restore()
    fun text(s: String, x: Float, top: Float, size: Float, color: Int, bold: Boolean, maxW: Float = inner) {
        p.color = color; p.textSize = size; p.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        var t = s
        while (t.length > 1 && p.measureText(t) > maxW) t = t.dropLast(2) + "…"
        c.drawText(t, x, top - p.ascent(), p)
    }
    text("PURSUIT IRON", pad, 128f, 31f, cs.primary.toArgb(), true)
    text(title, pad, 191f, 66f, cs.onSurface.toArgb(), true)
    text("Built through every workout.", pad, 281f, 23f, cs.onSurfaceVariant.toArgb(), false)
    var y = 360f
    rows.forEachIndexed { i, r ->
        val h = heights[i]
        p.color = cs.surface.toArgb(); c.drawRoundRect(RectF(pad, y, pad + inner, y + h), 20f, 20f, p)
        text(r.s("label").uppercase(), pad + 28, y + 20, 20f, cs.primary.toArgb(), true, inner - 56)
        r.strings("lines").ifEmpty { listOf("") }.forEachIndexed { j, line -> text(line, pad + 28, y + 54 + j * 42, 30f, cs.onSurface.toArgb(), true, inner - 56) }
        text(r.s("detail"), pad + 28, y + h - 34, 19f, cs.onSurfaceVariant.toArgb(), false, inner - 56)
        y += h + 16
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, "pursuit-iron-profile.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun ShareCap(t: String, modifier: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
