package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicLong
import java.math.BigDecimal

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
    val liveCoach:LiveSetCoachResult.Reviewed?=null,
    val unavailableReason:NativeJournalFileRefusal?=null,
    val journalRefusal:KotlinJournalRefusal?=null
)

internal class NativeKotlinSessionController(
    private val host:NativeKotlinCandidateHost
):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val pending=AtomicBoolean(false)
    private val closed=AtomicBoolean(false)
    private val latestCoachRequest=AtomicLong(0)
    // Invalidate callbacks from a previous controller lifecycle, including
    // completions racing with close() or a newly selected workout.
    private val generation=AtomicLong(0)
    private val mutable=MutableStateFlow(NativeKotlinSessionState())
    val state:StateFlow<NativeKotlinSessionState> = mutable.asStateFlow()

    private fun accepted(workoutId:String,result:NativeJournalFileResult,
        journalRefusal:KotlinJournalRefusal?=null) {
        latestCoachRequest.incrementAndGet()
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
        recoverOnReject:Boolean=false,
        hideUnverified:Boolean=false
    ) {
        if(closed.get() || !pending.compareAndSet(false,true))return
        if(closed.get()) {
            pending.set(false)
            return
        }
        val requestGeneration=generation.incrementAndGet()
        // Opening or issuing is an authorization/recovery boundary. Never
        // display a previous athlete/session's receipts while disk ownership
        // and approval are being revalidated. In-session writes can keep
        // their verified presentation visible under the busy lock.
        mutable.value=if(hideUnverified)
            NativeKotlinSessionState(workoutId=workoutId,busy=true)
        else mutable.value.copy(busy=true,
            unavailableReason=null,journalRefusal=null)
        if(closed.get()) {
            mutable.value=NativeKotlinSessionState()
            pending.set(false)
            return
        }
        scope.launch {
            try {
                val result=operation()
                if(closed.get() || generation.get()!=requestGeneration)return@launch
                if(result is NativeJournalFileResult.Rejected && recoverOnReject) {
                    // A stale-revision rejection is not data loss. Reload the
                    // authoritative disk state; keep the refusal visible.
                    val current=host.recoverWorkout(workoutId)
                    if(closed.get() || generation.get()!=requestGeneration)return@launch
                    accepted(workoutId,current,result.journalReason)
                    if(current is NativeJournalFileResult.Durable)
                        mutable.value=mutable.value.copy(
                            unavailableReason=result.reason)
                } else {
                    accepted(workoutId,result)
                }
            } catch(cancelled:CancellationException) {
                throw cancelled
            } catch(_:Exception) {
                if(generation.get()!=requestGeneration)return@launch
                // A failed disk read/write leaves the last in-memory journal
                // unverified. Fail closed: do not allow another set edit or
                // Finish against a possibly stale revision. The athlete can
                // explicitly reopen and recover the authoritative receipt.
                latestCoachRequest.incrementAndGet()
                mutable.value=NativeKotlinSessionState(
                    workoutId=workoutId,
                    unavailableReason=NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
            } finally {
                pending.set(false)
            }
        }
    }

    /** Reading never issues a new workout. */
    fun open(workoutId:String) {
        if(!workoutId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")))
            return
        run(workoutId,{host.recoverWorkout(workoutId)},hideUnverified=true)
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
                        when(result.reason) {
                            NativeKotlinIssueRefusal.HISTORY_NOT_READY ->
                                NativeJournalFileRefusal.HISTORY_NOT_READY
                            else -> result.storageReason
                                ?: NativeJournalFileRefusal.NOT_AUTHORIZED
                        })
            }
        },hideUnverified=true)
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

    /** A single confirmed Composer exercise replacement is workout-only;
     * future program edits remain a separately reviewed transaction. */
    fun swapExercise(
        previousExerciseId:String,replacementExerciseId:String,
        intents:List<KotlinComposerSwapSetIntent>
    ) {
        val prior=mutable.value
        val workoutId=prior.workoutId ?:return
        val journal=prior.journal ?:return
        if(journal.finishedByEventId!=null ||
            journal.snapshot.targets.none{it.exerciseId==previousExerciseId} ||
            journal.snapshot.targets.filter{
                it.exerciseId==previousExerciseId
            }.any{it.setKey in journal.effectiveSets})return
        val eventId="swap-"+UUID.randomUUID()
        run(workoutId,{
            host.swapIssuedComposerExercise(workoutId,eventId,
                journal.revision,previousExerciseId,replacementExerciseId,
                intents)
        },recoverOnReject=true)
    }

    fun undoExerciseSwap(swapEventId:String) {
        val prior=mutable.value
        val workoutId=prior.workoutId ?:return
        val journal=prior.journal ?:return
        if(journal.finishedByEventId!=null)return
        val eventId="undo-"+UUID.randomUUID()
        run(workoutId,{
            host.undoIssuedComposerExerciseSwap(workoutId,eventId,
                journal.revision,swapEventId)
        },recoverOnReject=true)
    }

    /**
     * Nonblocking read-only next-set guidance. Every request rereads saved
     * native receipts and current gym off Main. A newer selection or journal
     * revision invalidates any older callback; no UI input grants authority.
     */
    fun reviewNextSet(
        setKey:String,readiness:CoachReadiness,movementRestricted:Boolean
    ) {
        val before=mutable.value
        val current=before.journal ?:return
        if(closed.get() || before.busy || current.finishedByEventId!=null)return
        val workoutId=before.workoutId ?:return
        val observedRevision=current.revision
        val requestId=latestCoachRequest.incrementAndGet()
        mutable.value=before.copy(liveCoach=null)
        scope.launch {
            // Live coaching is OPTIONAL and read-only. A broken preference,
            // gym file or unavailable projection must never throw out of
            // the workout coroutine, crash the beta, or affect issued sets.
            // Cancellation still propagates so disposed scopes cannot
            // resurrect advice for an old athlete or session.
            val reviewed=try {
                host.reviewLiveIssuedNextSet(
                    workoutId,setKey,readiness,movementRestricted,
                    BigDecimal("5"))
            } catch(cancelled:CancellationException) {
                throw cancelled
            } catch(_:Exception) {
                null
            }
            val state=mutable.value
            if(!closed.get() &&
                latestCoachRequest.get()==requestId &&
                state.workoutId==workoutId &&
                state.journal?.revision==observedRevision &&
                !state.busy)
                mutable.value=state.copy(
                    liveCoach=reviewed as? LiveSetCoachResult.Reviewed)
        }
    }

    /** Explicit per-session note save; the host/store rechecks current
     * athlete ownership, immutable source and disk revision before append.
     * Only a durable result updates the controller's visible journal. */
    fun saveSessionNote(text:String) {
        val prior=mutable.value
        val workoutId=prior.workoutId ?:return
        val journal=prior.journal ?:return
        if(prior.busy || journal.finishedByEventId!=null)return
        val note=KotlinJournalOperation.SessionNote(
            "session-note-"+UUID.randomUUID(),journal.revision,text)
        run(workoutId,{
            host.recordWorkoutOperation(workoutId,note)
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
        if(closed.get() || pending.get())return
        latestCoachRequest.incrementAndGet()
        mutable.value=NativeKotlinSessionState()
    }

    override fun close() {
        if(!closed.compareAndSet(false,true))return
        generation.incrementAndGet()
        latestCoachRequest.incrementAndGet()
        scope.cancel()
        mutable.value=NativeKotlinSessionState()
    }
}
