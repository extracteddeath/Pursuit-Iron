package com.pursuitiron.app.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/** M3: every icon-only button names itself on long-press, so an icon nobody recognises is never a guess. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiTooltip(label: String, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION")
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()) { content() }
}
