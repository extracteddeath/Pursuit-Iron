package io.github.extracteddeath.pursuitiron

import java.util.concurrent.atomic.AtomicReference

/** Coalesced read requests, not a queue of edits or saves. A passive return
 * cannot replace a pending explicit program choice or creation handoff. */
internal class NativeKotlinPlanEntryRequests {
    private data class Refresh(val cycleId:String,val currentCycle:()->String?,val action:()->Unit)
    private data class Pending(
        val selected:(()->Unit)?=null,
        val refreshed:Refresh?=null,
        val resumed:(()->Unit)?=null
    )
    private val pending=AtomicReference<Pending?>(null)
    val hasPending:Boolean get()=pending.get()!=null

    fun select(action:()->Unit) {pending.set(Pending(selected=action))}
    fun resume(action:()->Unit) {
        pending.updateAndGet {previous->
            if(previous?.selected!=null)previous else (previous ?: Pending()).copy(resumed=action)
        }
    }
    fun refreshSaved(cycleId:String,currentCycle:()->String?,action:()->Unit) {
        pending.updateAndGet {previous->
            if(previous?.selected!=null)previous else (previous ?: Pending()).copy(
                refreshed=Refresh(cycleId,currentCycle,action))
        }
    }
    fun takeNext():(()->Unit)? {
        val queued=pending.getAndSet(null) ?:return null
        queued.selected?.let{return it}
        val refresh=queued.refreshed ?:return queued.resumed
        return {
            // Late save acknowledgments neither steal another program's
            // selection nor swallow a pending return to that newer task.
            if(refresh.currentCycle()==refresh.cycleId)refresh.action() else queued.resumed?.invoke()
        }
    }
    fun clear() {pending.set(null)}
}
