package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** One program outline, with inline day disclosures on compact screens. */
@Composable
internal fun NativeKotlinPlanEditorNavigator(
    draft:CustomTrainingCycle,busy:Boolean,
    expandedBlockId:String?,expandedDayKey:String?,twoPane:Boolean,
    onToggleBlock:(String)->Unit,onSelectDay:(String?)->Unit,
    onEditBlock:(String)->Unit,onDuplicateBlock:(String)->Unit,
    onMoveBlock:(String,Int)->Unit,onAddDay:(String)->Unit,
    onRemoveBlock:(String)->Unit,onAddBlock:()->Unit,
    dayContent:@Composable (CustomTrainingBlock,CustomTrainingDay)->Unit
) {
    draft.blocks.forEachIndexed { index, block ->
        key(block.blockId) {
            val blockExpanded=expandedBlockId==block.blockId
            PursuitCard {
                PursuitDisclosureRow(
                    title="${index+1}. ${block.label}",
                    subtitle="${nativePlanEnumLabel(block.phase)} · ${block.weeks} weeks · " +
                        "${nativePlanSplitLabel(block.split)} · ${block.days.size} training days",
                    expanded=blockExpanded,
                    onToggle={onToggleBlock(block.blockId)}
                )
                if(blockExpanded) {
                    Text("${nativePlanEnumLabel(block.purpose)} · changes require renewed review",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(PursuitSpace.sm)) {
                        TextButton(onClick = { onEditBlock(block.blockId) },
                            enabled = !busy) { Text("Edit block") }
                        TextButton(onClick = { onDuplicateBlock(block.blockId) },
                            enabled = !busy) { Text("Duplicate") }
                        TextButton(onClick = { onMoveBlock(block.blockId,-1) },
                            enabled = !busy && index > 0) { Text("Earlier") }
                        TextButton(onClick = { onMoveBlock(block.blockId,1) },
                            enabled = !busy && index < draft.blocks.lastIndex) {
                            Text("Later")
                        }
                        if(draft.blocks.size>1)TextButton(
                            onClick={onRemoveBlock(block.blockId)},
                            enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp)
                        ) {Text("Remove block")}
                    }
                    block.days.sortedBy { it.weekday }.forEach { day ->
                        val dayKey=programDayKey(block.blockId,day.weekday)
                        val dayExpanded=expandedDayKey==dayKey
                        HorizontalDivider()
                        val dayTitle="${customWeekdays.getOrElse(day.weekday) { "Day" }} · ${day.label}"
                        val daySummary="${nativePlanEnumLabel(day.purpose)} · ${day.slots.size} exercises · " +
                            "${day.slots.sumOf { it.sets.count { set->set.type==DraftSetType.WORKING } }} working sets"
                        if(twoPane) PursuitDestinationRow(
                            title=dayTitle,description=daySummary,
                            onClick={onSelectDay(dayKey)},selected=dayExpanded,
                            icon={Icon(Icons.Outlined.FitnessCenter,contentDescription=null)}
                        ) else PursuitDisclosureRow(
                            title=dayTitle,subtitle=daySummary,expanded=dayExpanded,
                            onToggle={onSelectDay(if(dayExpanded)null else dayKey)}
                        )
                        if(dayExpanded && !twoPane) {
                            dayContent(block,day)
                        } // Collapsed day: no hidden edit controls are lost.
                    }
                    OutlinedButton(onClick = { onAddDay(block.blockId) },
                        enabled = !busy && block.days.size < 7,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Add a custom training day")
                    }
                } // Collapsed block: its label and schedule remain visible.
            }
        }
    }
    OutlinedButton(onClick = { onAddBlock() },
        enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text("Add a training block")
    }
}
