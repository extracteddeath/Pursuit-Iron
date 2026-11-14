package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.AthleteTrainingGoal
import java.util.Locale

/** Transient browsing state, kept separate from the open Plan and its Undo. */
internal data class NativeKotlinProgramPickerState(
    val token:Long,
    val busy:Boolean=true,
    val owner:NativeKotlinOwnerIdentity?=null,
    val programs:List<NativeCustomDraftSummary> = emptyList(),
    val opening:String?=null,
    val failure:NativeCustomDraftRefusal?=null
)

/**
 * Cancel/reopen invalidates outstanding reads. Callbacks publish memory only;
 * native file reads and policy recovery must happen outside this short lock.
 */
internal class NativeKotlinProgramPickerSession {
    private var generation=0L
    private var active:Long?=null
    @Synchronized fun begin(publish:(Long)->Unit):Long {
        val token=++generation
        active=token
        publish(token)
        return token
    }
    @Synchronized fun publish(token:Long,action:()->Unit):Boolean {
        if(active!=token)return false
        action()
        return true
    }
    @Synchronized fun claim(token:Long,action:()->Unit):Boolean {
        if(active!=token)return false
        active=null
        action()
        return true
    }
    @Synchronized fun dismiss(publish:()->Unit) {
        active=null
        publish()
    }
}

internal fun NativeCustomDraftSummary.programDescription():String = buildList {
    goal?.let {add(when(it) {
        AthleteTrainingGoal.HYPERTROPHY -> "Hypertrophy"
        AthleteTrainingGoal.STRENGTH -> "Strength"
        AthleteTrainingGoal.POWERBUILDING -> "Powerbuilding"
    })}
    weeks?.let{add("$it ${if(it==1)"week" else "weeks"}")}
    val low=minimumDays
    val high=maximumDays
    if(low!=null && high!=null)add(when {
        high==0 -> "Add training days"
        low==high -> "$high ${if(high==1)"day" else "days"}/week"
        else -> "$low–$high days/week"
    })
    add("$blocks ${if(blocks==1)"block" else "blocks"}")
}.joinToString(" · ")

/** Search every saved record before paging; IDs and revisions stay internal. */
internal fun matchingPrograms(programs:List<NativeCustomDraftSummary>,query:String,
    currentCycleId:String?):List<NativeCustomDraftSummary> {
    val terms=query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter{it.isNotEmpty()}
    return programs.filter {program ->
        val text="${program.title} ${program.programDescription()}".lowercase(Locale.ROOT)
        terms.all{text.contains(it)}
    }.sortedWith(compareBy<NativeCustomDraftSummary>{it.cycleId!=currentCycleId}
        .thenBy{it.title.lowercase(Locale.ROOT)}.thenBy{it.cycleId})
}

internal fun NativeCustomDraftRefusal.programPickerMessage():String=when(this) {
    NativeCustomDraftRefusal.NOT_AUTHORIZED -> "Your athlete changed. Reopen Plan for the current athlete."
    NativeCustomDraftRefusal.CORRUPT_RECORD -> "A saved program could not be verified. Your current Plan is preserved. Retry after restoring the saved record."
    NativeCustomDraftRefusal.INVALID_DRAFT -> "This program is no longer available. Refresh the list and choose again."
    NativeCustomDraftRefusal.STALE_REVISION -> "The saved list changed. Refresh it and choose again."
    NativeCustomDraftRefusal.STORAGE_UNAVAILABLE -> "Saved programs are temporarily unavailable. Your current Plan is preserved. Retry when storage is available."
}
