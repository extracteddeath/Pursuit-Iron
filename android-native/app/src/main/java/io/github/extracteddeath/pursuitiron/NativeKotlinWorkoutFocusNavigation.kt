package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Browsing an issued set is never permission to log, skip, change a machine,
 * advance a superset or edit an immutable target. Either existing journal
 * remains the exclusive writer when the athlete explicitly confirms a set.
 */
internal data class NativeKotlinFocusStep<K:Any>(
    val key:K,
    val resolved:Boolean
)

internal data class NativeKotlinFocusNavigation<K:Any>(
    val selected:K,
    val position:Int,
    val total:Int,
    val previous:K?,
    val next:K?,
    val nextUnfinished:K?,
    val reviewingResolved:Boolean
) {
    val canReturnToUnfinished:Boolean
        get()=nextUnfinished!=null && nextUnfinished!=selected
}

/** One read-only, typed navigation policy for generated set IDs and custom
 * main/follow-up visit IDs. Never flatten a follow-up to its parent's setKey.
 * A missing, duplicate or stale view fails closed instead of guessing an ID. */
internal object NativeKotlinWorkoutFocusNavigationPolicy {
    fun <K:Any> inspect(
        steps:List<NativeKotlinFocusStep<K>>,
        selected:K?,
        nextUnfinished:K?
    ):NativeKotlinFocusNavigation<K>? {
        if(steps.isEmpty() || steps.size>2048 ||
            steps.map{it.key}.distinct().size!=steps.size)return null
        val index=steps.indexOfFirst{it.key==selected}
        if(index<0)return null
        if(nextUnfinished!=null && steps.none{
            it.key==nextUnfinished && !it.resolved
        })return null
        return NativeKotlinFocusNavigation(
            selected=steps[index].key,
            position=index+1,
            total=steps.size,
            previous=steps.getOrNull(index-1)?.key,
            next=steps.getOrNull(index+1)?.key,
            nextUnfinished=nextUnfinished,
            reviewingResolved=steps[index].resolved
        )
    }
}

/** Compact, accessible controls for moving around a real issued workout.
 * Both routes supply their own source-verified entries and callbacks; this
 * component owns no mutable workout state and issues no journal operations. */
@Composable
internal fun <K:Any> NativeKotlinWorkoutFocusNavigation(
    steps:List<NativeKotlinFocusStep<K>>,
    selected:K?,
    nextUnfinished:K?,
    enabled:Boolean,
    onChoose:(K)->Unit,
    onReturnToNextUnfinished:()->Unit
) {
    val navigation=NativeKotlinWorkoutFocusNavigationPolicy.inspect(
        steps,selected,nextUnfinished)?:return
    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
        Text(
            "Entry ${navigation.position} of ${navigation.total}" +
                if(navigation.reviewingResolved)" · already recorded" else "",
            style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier=Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
        ) {
            OutlinedButton(
                onClick={
                    val previous=navigation.previous
                    if(previous!=null)onChoose(previous)
                },
                enabled=enabled && navigation.previous!=null,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)
            ) {Text("Previous set")}
            OutlinedButton(
                onClick={
                    val next=navigation.next
                    if(next!=null)onChoose(next)
                },
                enabled=enabled && navigation.next!=null,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)
            ) {Text("Next set")}
        }
        if(navigation.canReturnToUnfinished) {
            TextButton(
                onClick=onReturnToNextUnfinished,
                enabled=enabled,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
            ) {Text("Jump to next unfinished set")}
        }
    }
}
