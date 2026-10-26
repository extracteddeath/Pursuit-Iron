@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.widget.NextWorkoutWidgetReceiver

/* The design's Widgets & watch panel (Main.dc.html pSurf), home-screen part: a preview of the widget with the
   real next session, and a button that asks the launcher to place it. There is no watch app yet, so that half stays out. */
@Composable
fun WidgetsSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val next = vm.home?.o("next")
    val mgr = AppWidgetManager.getInstance(ctx)
    val canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Widgets & watch", style = MaterialTheme.typography.headlineSmall)
            Text("HOME-SCREEN WIDGET", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(18.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Widget preview: " + (next?.s("dayLabel") ?: "no session planned") },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NEXT · TODAY", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp), color = cs.onPrimaryContainer)
                Text(next?.s("dayLabel") ?: "No session planned", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer)
                next?.let { n -> Text(listOfNotNull("${n.list("exercises").size} exercises", n.i("minutes").takeIf { it > 0 }?.let { "~$it min" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onPrimaryContainer) }
                Box(Modifier.padding(top = 8.dp).height(40.dp).clip(CircleShape).background(cs.primary).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text("Start", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = cs.onPrimary)
                }
            }
            Text("Shows your next session and starts it in one tap. It follows the workout while one is running.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = cs.onSurfaceVariant)
            if (canPin) Button(onClick = {
                mgr.requestPinAppWidget(ComponentName(ctx, NextWorkoutWidgetReceiver::class.java), null, null); onClose()
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add to home screen", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)) }
            else Text("Long-press your home screen, choose Widgets, then find Pursuit Iron.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}
