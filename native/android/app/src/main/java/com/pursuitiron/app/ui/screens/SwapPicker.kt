package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject

/** The web's swap list (App.js SwapSheet): search the whole ranked pool, each option with why it ranks where it does
 *  (Same pattern, Fills gap, Top match, or Extra setup when it overlaps another lift's setup), and the current lift
 *  can be banned from here. */
@Composable
fun SwapPicker(opts: List<JSONObject>?, currentName: String, onBan: (() -> Unit)?, maxHeight: Dp = 520.dp, onPick: (JSONObject) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Reset the choice when a different lift opens the same sheet.
    var q by remember(currentName) { mutableStateOf("") }
    var filter by remember(currentName) { mutableStateOf("all") }
    val filtersScroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(PiIcons.Search, null, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (q.isNotEmpty()) ({ Icon(PiIcons.Close, "Clear search", modifier = Modifier.size(18.dp).clickable { q = "" }) }) else null,
            placeholder = { Text("Search replacements") })
        Text("Ranked for your equipment, movement role and this program's weekly muscle coverage.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        if (onBan != null) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(cs.errorContainer).clickable(onClick = onBan)
            .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            Icon(PiIcons.Ban, null, tint = cs.onErrorContainer, modifier = Modifier.size(18.dp))
            Text("Never use $currentName again", style = MaterialTheme.typography.labelLarge, color = cs.onErrorContainer)
        }
        val all = opts
        // The source keeps the original engine rank even when a badge (such as Extra setup) masks a pattern match.
        if (!all.isNullOrEmpty()) {
            val facets = buildList {
                add("all" to "All")
                if (all.size > 5) add("top" to "Top 5")
                if (all.any { it.b("samePattern") }) add("pattern" to "Same pattern")
                if (all.any { it.b("fillsGap") }) add("gap" to "Fills gap")
                if (all.any { it.b("extraSetup") }) add("setup" to "Extra setup")
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(filtersScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                facets.forEach { (id, title) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(title) })
                }
            }
        }
        val needle = q.trim().lowercase()
        // Match every word, in any order, across lift, muscle, equipment and the ranking explanation.
        val words = needle.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val shown = all?.filter { x ->
            val facet = when (filter) {
                "top" -> x.i("rank") <= 5
                "pattern" -> x.b("samePattern")
                "gap" -> x.b("fillsGap")
                "setup" -> x.b("extraSetup")
                else -> true
            }
            val hay = listOf(x.s("name"), x.s("equipLabel"), x.s("part"), x.s("type"), x.s("badge"), x.s("reason")).joinToString(" ").lowercase()
            facet && words.all { hay.contains(it) }
        }
        when {
            all == null -> com.pursuitiron.app.ui.common.LoadingSkeleton(Modifier.padding(4.dp))
            all.isEmpty() -> Text("No other lift for this muscle at your gym.", color = cs.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            shown.isNullOrEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (q.isNotBlank()) "Nothing matches “${q.trim()}” with this filter." else "No alternatives in this group.",
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(4.dp))
                TextButton(onClick = { q = ""; filter = "all" }) { Text("Show all alternatives") }
            }
            else -> LazyColumn(Modifier.clip(RoundedCornerShape(22.dp)).heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.s("id") }) { x ->
                    val badge = x.s("badge")
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { onPick(x) }
                        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = listOf("Rank ${x.i("rank")}: " + x.s("name") + (if (badge.isNotBlank()) ", $badge" else ""), x.s("reason"), x.s("equipLabel")).filter { it.isNotBlank() }.joinToString(". ") }
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${x.i("rank")}.", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                if (badge.isNotBlank()) {
                                    val warn = x.b("warn")
                                    Text(badge.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
                                        color = if (warn) cs.onTertiaryContainer else cs.onSecondaryContainer, maxLines = 1,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (warn) cs.tertiaryContainer else cs.secondaryContainer).padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                            x.s("reason").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurface) }
                            Text(listOfNotNull(x.s("type").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }, x.s("equipLabel").takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                        }
                        Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
