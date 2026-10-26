package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.IconTile
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's per-phase targets (Main.dc.html BT): rep range, effort and what the block does. */
private val PHASE_INFO = mapOf(
    "hypertrophy_accumulation" to Triple("8–12 reps", "2 → 0 RIR", "Volume climbs each week; the last week deloads"),
    "strength_accumulation" to Triple("4–6 reps", "2 → 1 RIR", "Fewer sets, heavier main lifts"),
    "mixed_accumulation" to Triple("5–10 reps", "2 → 1 RIR", "Heavy main lifts with hypertrophy accessories"),
    "intensification" to Triple("3–5 reps", "2 → 1 RIR", "Loads climb while volume drops"),
    "peak" to Triple("1–3 reps", "1 RIR", "Low volume, top sets near your max"),
    "foundation" to Triple("8–15 reps", "3 → 2 RIR", "Technique and work capacity first"),
    "recovery" to Triple("Lighter work", "3+ RIR", "Recovery before the next planned phase"))
private val PHASE_NAMES = mapOf("hypertrophy_accumulation" to "Hypertrophy", "strength_accumulation" to "Strength", "mixed_accumulation" to "Powerbuilding",
    "intensification" to "Intensification", "peak" to "Peak", "foundation" to "Foundation", "recovery" to "Recovery")
private val PHASE_ORDER = listOf("hypertrophy_accumulation", "strength_accumulation", "mixed_accumulation", "intensification", "peak", "foundation")

/** One block in the editor's draft. Past and current blocks are fixed: their programs already run. */
private data class DBlock(val phase: String, val weeks: Int, val label: String?, val fixed: Boolean, val state: String = "upcoming",
                          val weeksIn: Int = 0, val id: String? = null, val sessions: Int = 0, val index: Int = -1)

private val shortDate = SimpleDateFormat("MMM d", Locale.getDefault())
private fun fmtD(ms: Long) = shortDate.format(Date(ms))

/* The design's Training cycle page (Main.dc.html cyc): the blocks as a strip and cards, each with its phase targets;
   Edit reshapes the blocks after this one; a single program can become a cycle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleSheet(vm: AppViewModel, id: String?, programId: String? = null, onClose: () -> Unit) {
    var cycleId by remember { mutableStateOf(id) }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(cycleId, vm.cycles) { d = cycleId?.let { vm.cycleDetail(it) } }
    val cs = MaterialTheme.colorScheme
    val p = vm.plan
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val draft = remember { mutableStateListOf<DBlock>() }
    var preview by remember { mutableStateOf<Int?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<Int?>(null) }
    val c = d
    val converting = cycleId == null

    fun startEdit() {
        draft.clear()
        if (c != null) {
            name = c.s("name")
            c.list("blocks").forEach { b ->
                val st = b.s("state")
                draft.add(DBlock(b.s("phase").ifBlank { "mixed_accumulation" }, b.i("weeks").coerceAtLeast(1), b.s("label"), st != "upcoming", st, b.i("weeksIn"), b.s("id"), b.i("sessions"), b.i("index")))
            }
        } else if (p != null) {
            name = "${p.s("name")} cycle"
            val ph = when (p.s("goal")) { "strength" -> "strength_accumulation"; "hypertrophy" -> "hypertrophy_accumulation"; else -> "mixed_accumulation" }
            draft.add(DBlock(ph, p.i("weeksTotal").coerceAtLeast(1), p.s("name"), true, "active", (p.i("current") - 1).coerceAtLeast(0), p.s("programId")))
            draft.add(DBlock("strength_accumulation", 5, "Strength", false)); draft.add(DBlock("peak", 3, "Peak", false))
        }
        editing = true
    }
    // A single program opens straight on its Turn-into-cycle card; Edit on a cycle starts the draft from its blocks.
    val blocks: List<DBlock> = if (editing) draft else c?.list("blocks")?.map { b ->
        DBlock(b.s("phase").ifBlank { "mixed_accumulation" }, b.i("weeks").coerceAtLeast(1), b.s("label"), b.s("state") != "upcoming", b.s("state"), b.i("weeksIn"), b.s("id"), b.i("sessions"), b.i("index"))
    } ?: emptyList()
    val totW = blocks.sumOf { it.weeks }
    val err = when {
        !editing -> ""
        blocks.size < 2 || blocks.size > 8 -> "A cycle needs 2–8 blocks."
        totW > 104 -> "A training cycle can run at most 104 weeks."
        else -> ""
    }
    val actIdx = blocks.indexOfFirst { it.state == "active" }.let { if (it < 0) blocks.indexOfLast { b -> b.fixed } else it }

    DesignPage("Training cycle", { if (editing && !converting) editing = false else onClose() },
        actions = if (!editing && c != null && !c.b("done") && c.b("reshapable")) ({
            Box(Modifier.height(40.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { startEdit() }.padding(horizontal = 14.dp)
                .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                Text("Edit", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
        }) else null,
        bottom = if (editing) ({
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { if (converting) { editing = false } else editing = false }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Cancel", fontWeight = FontWeight.Bold) }
                Button(onClick = {
                    val fut = JSONArray(); blocks.filter { !it.fixed }.forEach { b -> fut.put(JSONObject().put("phase", b.phase).put("weeks", b.weeks).put("label", b.label ?: PHASE_NAMES[b.phase])) }
                    if (converting) { val pid = programId ?: p.s("programId"); vm.convertToCycle(pid, name.trim(), fut, true) { newId -> cycleId = newId; editing = false } }
                    else c?.let { cc ->
                        val orig = cc.list("blocks").filter { it.s("state") == "upcoming" }.map { Triple(it.s("phase"), it.i("weeks"), it.s("label")) }
                        val now = blocks.filter { !it.fixed }.map { Triple(it.phase, it.weeks, it.label ?: PHASE_NAMES[it.phase] ?: "") }
                        val renamed = name.trim() != cc.s("name")
                        if (orig == now && !renamed) editing = false
                        else vm.editCycle(cc.s("id"), name = if (renamed) name.trim() else null, blocks = if (orig != now) fut else null) { editing = false }
                    }
                }, enabled = err.isEmpty() && name.isNotBlank(), modifier = Modifier.weight(1.4f).height(54.dp)) {
                    Text(if (converting) "Save cycle" else "Save cycle", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }) else null) {
        if (converting && !editing) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Single program", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold))
                Text("This block runs on its own. Turn it into a training cycle to plan the blocks that follow it, each with its own phase and length.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = cs.onSurfaceVariant)
                if (p?.b("endless") == true) Text("Endless programs have no block boundary. Set a fixed length in Program settings first.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Button(onClick = { startEdit() }, enabled = p != null && !p.b("endless"), modifier = Modifier.height(48.dp)) {
                    Text("Turn into training cycle", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            return@DesignPage
        }
        if (!editing && c == null) return@DesignPage
        if (!editing && c != null) Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(c.o("icon"), 56.dp)
            Column(Modifier.weight(1f)) {
                Text(c.s("name"), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold))
                Text(blocks.joinToString(" → ") { it.label ?: PHASE_NAMES[it.phase].orEmpty() }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 2.dp))
                val start = c.optLong("startMs"); val end = c.optLong("endMs")
                Text(listOfNotNull("$totW weeks", "${blocks.size} blocks", start.takeIf { it > 0 }?.let { "started ${fmtD(it)}" },
                    end.takeIf { it > 0 && !c.b("done") }?.let { "ends ${fmtD(it)}" },
                    if (c.b("done")) "complete" else c.o("live")?.let { "week ${it.i("weekIndex")} of block ${it.i("blockIdx") + 1}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (editing) {
            CapLabel("CYCLE NAME")
            BasicTextField(name, { name = it.take(40) }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = cs.onSurface),
                cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).semantics { contentDescription = "Cycle name" },
                decorationBox = { inner -> Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { inner() } })
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapLabel("START FROM A TEMPLATE", Modifier.weight(1f))
                Text("$totW weeks · ${blocks.size} blocks", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            val templates = c?.list("templates") ?: vm.cycles.list("templates")
            Row(Modifier.layout { m, k -> val e = 16.dp.roundToPx(); val pl = m.measure(k.copy(minWidth = 0, maxWidth = k.maxWidth + 2 * e)); layout(pl.width - 2 * e, pl.height) { pl.place(-e, 0) } }
                .fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                templates.forEach { t ->
                    val fut = t.list("blocks").drop(1)
                    val on = blocks.filter { !it.fixed }.map { it.phase to it.weeks } == fut.map { it.s("phase") to it.i("weeks") }
                    Box(Modifier.height(38.dp).clip(CircleShape).background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh)
                        .clickable { val keep = draft.filter { it.fixed }; draft.clear(); draft.addAll(keep); fut.forEach { b -> draft.add(DBlock(b.s("phase"), b.i("weeks"), b.s("label"), false)) } }
                        .semantics { role = Role.Button; stateDescription = if (on) "Selected" else "Not selected" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(t.s("name"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
                    }
                }
            }
            Text("A template replaces the blocks after this one. The block you’re in keeps running.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
        }
        // Strip: each block's share of the cycle; the current one fills to this week.
        Row(Modifier.fillMaxWidth().semantics { contentDescription = blocks.joinToString(" → ") { it.label ?: PHASE_NAMES[it.phase].orEmpty() } }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            blocks.forEachIndexed { k, b ->
                val r = RoundedCornerShape(topStart = if (k == 0) 999.dp else 4.dp, bottomStart = if (k == 0) 999.dp else 4.dp, topEnd = if (k == blocks.lastIndex) 999.dp else 4.dp, bottomEnd = if (k == blocks.lastIndex) 999.dp else 4.dp)
                val frac = when (b.state) { "done" -> 1f; "active" -> ((b.weeksIn + 1f) / b.weeks).coerceIn(0f, 1f); else -> 0f }
                Box(Modifier.weight(b.weeks.toFloat()).height(14.dp).clip(r).background(if (b.state == "active") cs.primaryContainer else cs.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(cs.primary))
                }
            }
        }
        // Dates follow from the cycle start and each block's length.
        val starts = remember(blocks.map { it.weeks }, c) {
            val first = c?.list("blocks")?.firstOrNull()?.optJSONArray("dates")?.optLong(0) ?: System.currentTimeMillis()
            var at = first; blocks.map { b -> val s = at; at += b.weeks * 7L * 86_400_000L; s }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEachIndexed { k, b ->
                val now = b.state == "active"
                val nm = b.label ?: PHASE_NAMES[b.phase].orEmpty()
                val info = PHASE_INFO[b.phase] ?: PHASE_INFO.getValue("mixed_accumulation")
                val upcomingRank = blocks.take(k).count { !it.fixed }
                val tag = when (b.state) { "active" -> "NOW · WEEK ${(b.weeksIn + 1).coerceAtMost(b.weeks)} OF ${b.weeks}"; "done" -> "DONE · BLOCK ${k + 1}"
                    else -> if (upcomingRank == 0) "NEXT · BLOCK ${k + 1}" else "LATER · BLOCK ${k + 1}" }
                val fg = if (now) cs.onPrimaryContainer else cs.onSurface
                Column(Modifier.fillMaxWidth().clip(if (now) RoundedCornerShape(28.dp, 28.dp, 28.dp, 10.dp) else RoundedCornerShape(24.dp))
                    .background(if (now) cs.primaryContainer else cs.surfaceContainerLow).padding(16.dp)
                    .semantics(mergeDescendants = !editing) { contentDescription = "$nm block, ${b.weeks} weeks" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tag, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
                        color = if (now) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).background(if (now) cs.primary else cs.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 4.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(nm, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), color = fg, modifier = Modifier.weight(1f))
                        Text("${b.weeks} ${if (b.weeks == 1) "week" else "weeks"} · ${fmtD(starts[k])}–${fmtD(starts[k] + b.weeks * 7L * 86_400_000L - 86_400_000L)}",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg)
                    }
                    Text("${PHASE_NAMES[b.phase] ?: nm} · ${info.first} · ${info.second}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg)
                    Text(info.third, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = fg.copy(alpha = 0.85f))
                    if (!editing && b.state == "upcoming" && b.id != null) {
                        val open = preview == k
                        Box(Modifier.height(36.dp).clip(CircleShape).border(1.5.dp, cs.outlineVariant, CircleShape).clickable { preview = if (open) null else k }
                            .semantics { role = Role.Button; stateDescription = if (open) "Expanded" else "Collapsed" }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(if (open) "Hide week 1" else "Preview week 1", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg)
                        }
                        AnimatedVisibility(open, enter = Motion.expandIn, exit = Motion.shrinkOut) {
                            Column(Modifier.clip(RoundedCornerShape(16.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                c?.o("previews")?.optJSONArray(b.id)?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }.orEmpty().forEach { r ->
                                    Column(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 12.dp, vertical = 9.dp)) {
                                        Text("${r.s("day")} · ${r.s("name")}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = cs.onSurface)
                                        Text(r.s("lifts"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = cs.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (now) {
                        val fill by animateFloatAsState(((b.weeksIn + 1f) / b.weeks).coerceIn(0f, 1f), Motion.data(), label = "cyc")
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.surface)) { Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape).background(cs.primary)) }
                    }
                    if (!editing && b.state == "done" && b.sessions > 0 && b.index >= 0)
                        TextButton(onClick = { review = b.index }) { Text("Review block", fontWeight = FontWeight.Bold) }
                    if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val locked = b.fixed
                        Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(3.dp).alpha(if (locked) 0.5f else 1f).semantics { contentDescription = "Weeks" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            StepBtn("−", "One week shorter") { if (locked) vm.toast("This block’s length is set in Program settings") else draft[k] = b.copy(weeks = (b.weeks - 1).coerceAtLeast(1)) }
                            Text("${b.weeks} wk", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
                            StepBtn("+", "One week longer") { if (locked) vm.toast("This block’s length is set in Program settings") else draft[k] = b.copy(weeks = (b.weeks + 1).coerceAtMost(24)) }
                        }
                        Box(Modifier.height(42.dp).clip(CircleShape).background(cs.secondaryContainer).alpha(if (locked) 0.5f else 1f)
                            .clickable(enabled = !locked) { val nx = PHASE_ORDER[(PHASE_ORDER.indexOf(b.phase) + 1) % PHASE_ORDER.size]; draft[k] = b.copy(phase = nx, label = null) }
                            .semantics { role = Role.Button; contentDescription = "Training phase ${PHASE_NAMES[b.phase]}. Tap for the next phase" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Phase: ${PHASE_NAMES[b.phase]}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                        }
                        val firstFree = blocks.indexOfFirst { !it.fixed }
                        if (!locked && k > firstFree) IconBtn(PiIcons.ChevronUp, "Move block ${k + 1} earlier") { swap(draft, k, k - 1) }
                        if (!locked && k < blocks.lastIndex) IconBtn(PiIcons.ChevronDown, "Move block ${k + 1} later") { swap(draft, k, k + 1) }
                        if (!locked && blocks.size > 2) Box(Modifier.height(42.dp).clip(CircleShape).clickable { draft.removeAt(k) }.semantics { role = Role.Button }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Remove", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg)
                        }
                    }
                }
            }
        }
        if (editing && blocks.size < 8) Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(18.dp))
            .clickable { val last = draft.last(); draft.add(DBlock(last.phase, 4, null, false)) }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
            Text("+ Add a block", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = cs.primary)
        }
        if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp))
        if (!editing && c != null) {
            if (!c.b("done")) {
                val adapt = c.b("adapts")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Adapt exercises to each phase", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                        Text("Real block history may change compatible exercises at the next phase while protecting successful lifts. Off: the same exercises carry on and only sets, reps, RIR and rest retarget.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                    }
                    Switch(adapt, { vm.editCycle(c.s("id"), adapt = it) }, enabled = c.b("reshapable"), modifier = Modifier.semantics { contentDescription = "Adapt exercises to each phase" })
                }
                Text(if (adapt) "The next block reads this one’s workouts: lifts that responded stay, stalled ones may get a fresh variation."
                    else "Each block keeps today’s exercises; only the prescription changes for its phase.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("End this block early", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = cs.onSurface) }
                TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Leave the cycle", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onTertiaryContainer) }
            } else OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Delete cycle", fontWeight = FontWeight.Bold) }
        }
    }
    review?.let { idx -> BlockReviewSheet(vm, cycleId ?: return@let, idx) { review = null } }
    if (confirmEnd && c != null) {
        val cur = blocks.getOrNull(actIdx)
        AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("End this block now?") },
            text = { Text("You’re in week ${((cur?.weeksIn ?: 0) + 1).coerceAtMost(cur?.weeks ?: 1)} of ${cur?.weeks ?: 0}. The block review shows what changed and builds the next block from it. The remaining weeks are skipped, not deleted.") },
            confirmButton = { Button(onClick = { confirmEnd = false; review = c.i("activeBlock") }) { Text("Review block") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } })
    }
    if (confirmLeave && c != null) {
        val cur = blocks.getOrNull(actIdx)
        AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Leave the training cycle?") },
            text = { Text("This block keeps running as a single program to week ${cur?.weeks ?: 0}. The planned ${blocks.count { it.state == "upcoming" }} later blocks are removed. Your logged sessions stay.") },
            confirmButton = { Button(onClick = { confirmLeave = false; vm.leaveCycle(c.s("id")) { onClose() } }) { Text("Leave cycle") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } })
    }
    if (confirmDelete && c != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${c.s("name")}?") },
        text = { Text("Keep its ${c.list("blocks").size} blocks as standalone programs, or delete them too. Logged workouts are never deleted.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCycle(c.s("id"), false); onClose() }) { Text("Keep blocks") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCycle(c.s("id"), true); onClose() }) { Text("Delete blocks too") } })
}

private fun swap(l: SnapshotStateList<DBlock>, a: Int, b: Int) { if (a in l.indices && b in l.indices) { val t = l[a]; l[a] = l[b]; l[b] = t } }

@Composable
private fun CapLabel(t: String, modifier: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun StepBtn(t: String, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surface).clickable(onClick = onClick).semantics { contentDescription = desc; role = Role.Button }, contentAlignment = Alignment.Center) {
        Text(t, fontSize = 18.sp, color = cs.onSurface)
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) =
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).semantics { contentDescription = desc; role = Role.Button }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp))
    }

