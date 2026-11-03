package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-scoped Compose-facing controller for the independent Kotlin workout.
 *
 * Only NativeJournalFileResult.Durable advances journal/presentation state.
 * No optimistic set completion, UI-generated target, JS fallback or assumed
 * actual RIR. Writes are serialized and executed off Main. Recovery reloads
 * the journal after any write rejection, never erasing a saved receipt.
 *
 * Not selected by the current production-facing MainActivity routes.
 */
internal data class NativeKotlinSessionState(
    val workoutId:String?=null,
    val busy:Boolean=false,
    val journal:KotlinWorkoutJournalState?=null,
    val presentation:KotlinWorkoutPresentation?=null,
    val unavailableReason:NativeJournalFileRefusal?=null,
    val journalRefusal:KotlinJournalRefusal?=null
)

internal class NativeKotlinSessionController(
    private val host:NativeKotlinCandidateHost
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val pending=AtomicBoolean(false)
    private val mutable=MutableStateFlow(NativeKotlinSessionState())
    val state:StateFlow<NativeKotlinSessionState> = mutable.asStateFlow()

    private fun accepted(workoutId:String,result:NativeJournalFileResult,
        journalRefusal:KotlinJournalRefusal?=null) {
        mutable.value=when(result) {
            is NativeJournalFileResult.Durable -> NativeKotlinSessionState(
                workoutId=workoutId,
                journal=result.journal,
                presentation=KotlinWorkoutPresentationDomain.inspect(result.journal),
                journalRefusal=journalRefusal
            )
            is NativeJournalFileResult.Rejected -> NativeKotlinSessionState(
                workoutId=workoutId,
                unavailableReason=result.reason,
                journalRefusal=result.journalReason?:journalRefusal
            )
        }
    }

    private fun run(
        workoutId:String,operation:()->NativeJournalFileResult,
        recoverOnReject:Boolean=false
    ) {
        if(!pending.compareAndSet(false,true))return
        mutable.value=mutable.value.copy(busy=true,
            unavailableReason=null,journalRefusal=null)
        scope.launch {
            try {
                val result=operation()
                if(result is NativeJournalFileResult.Rejected && recoverOnReject) {
                    // A stale-revision rejection is not data loss. Reload the
                    // authoritative disk state; keep the refusal visible.
                    val current=host.recoverWorkout(workoutId)
                    accepted(workoutId,current,result.journalReason)
                    if(current is NativeJournalFileResult.Durable)
                        mutable.value=mutable.value.copy(
                            unavailableReason=result.reason)
                } else {
                    accepted(workoutId,result)
                }
            } catch(_:Exception) {
                mutable.value=mutable.value.copy(
                    busy=false,unavailableReason=
                        NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
            } finally {
                pending.set(false)
            }
        }
    }

    /** Reading never issues a new workout. */
    fun open(workoutId:String) {
        if(!workoutId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")))
            return
        run(workoutId,{host.recoverWorkout(workoutId)})
    }

    /** Explicit action only; a valid persisted grant is required by store. */
    fun startApproved(workoutId:String) {
        if(!workoutId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")))
            return
        run(workoutId,{
            when(val result=host.issueWorkout(workoutId)) {
                is NativeKotlinIssueResult.Committed ->
                    NativeJournalFileResult.Durable(result.journal,result.replayed)
                is NativeKotlinIssueResult.Rejected ->
                    NativeJournalFileResult.Rejected(
                        result.storageReason ?: NativeJournalFileRefusal.NOT_AUTHORIZED)
            }
        })
    }

    fun record(
        setKey:String,
        outcome:KotlinJournalSetOutcome,
        performed:KotlinPerformedSet?
    ) {
        val prior=mutable.value
        val workoutId=prior.workoutId ?:return
        val journal=prior.journal ?:return
        if(journal.finishedByEventId!=null ||
            journal.snapshot.targets.none{it.setKey==setKey})return
        val existing=journal.effectiveSets[setKey]
        // An edit is a NEW correction event referencing the CURRENT receipt.
        // Never overwrite actuals or reuse a completed event ID.
        val operation=KotlinJournalOperation.RecordSet(
            eventId="set-"+UUID.randomUUID(),
            expectedJournalRevision=journal.revision,
            setKey=setKey,outcome=outcome,performed=performed,
            correctsEventId=existing?.operation?.eventId)
        run(workoutId,{
            host.recordWorkoutOperation(workoutId,operation)
        },recoverOnReject=true)
    }

    fun finish() {
        val prior=mutable.value
        val workoutId=prior.workoutId ?:return
        val journal=prior.journal ?:return
        if(!journal.canProposeFinish)return
        val operation=KotlinJournalOperation.Finish(
            "finish-"+UUID.randomUUID(),journal.revision)
        run(workoutId,{host.recordWorkoutOperation(workoutId,operation)},
            recoverOnReject=true)
    }

    fun clear() {
        if(pending.get())return
        mutable.value=NativeKotlinSessionState()
    }

    override fun close() {
        scope.cancel()
        mutable.value=NativeKotlinSessionState()
    }
}
