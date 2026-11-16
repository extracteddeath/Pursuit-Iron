package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomSplitIdentity
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingBlock
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycle
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingDay

internal val customWeekdays = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

/** A presentation key; never a durable workout or exercise identifier. */
internal fun programDayKey(blockId:String,weekday:Int)="$blockId:$weekday"

internal fun nativePlanEnumLabel(value:Enum<*>) =
    value.name.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }

internal fun nativePlanSplitLabel(split:CustomSplitIdentity):String = when(split) {
    is CustomSplitIdentity.Freeform -> split.label
    is CustomSplitIdentity.Named -> nativePlanEnumLabel(split.split)
}

/** Use the available pane width, with room for enlarged text in both panes. */
internal fun nativePlanUsesTwoPanes(widthDp:Float,fontScale:Float):Boolean =
    widthDp.isFinite() && fontScale.isFinite() && fontScale>0f &&
        widthDp>=900f && widthDp/fontScale>=900f

internal data class NativeKotlinPlanEditorDay(
    val block:CustomTrainingBlock,val day:CustomTrainingDay
)

/** Resolve against the current draft, never a remembered copy of its day. */
internal fun resolveNativePlanEditorDay(
    draft:CustomTrainingCycle?,dayKey:String?
):NativeKotlinPlanEditorDay? {
    if(draft==null || dayKey==null)return null
    return draft.blocks.flatMap { block ->
        block.days.filter { programDayKey(block.blockId,it.weekday)==dayKey }
            .map { NativeKotlinPlanEditorDay(block,it) }
    }.singleOrNull()
}
