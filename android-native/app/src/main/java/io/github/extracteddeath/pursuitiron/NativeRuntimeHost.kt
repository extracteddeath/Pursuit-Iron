package io.github.extracteddeath.pursuitiron

import android.content.Context
import io.github.extracteddeath.pursuitiron.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

internal data class NativeRuntimeState(
    val loading: Boolean=true,val ready: Boolean=false,val busy: Boolean=false,val blocked: Boolean=false,
    val verifier: PackagedReadOnlySchemaVerifier?=null,val options: NativeProgramOptions?=null,
    val proposal: NativeProgramProposal?=null,val saved: NativeSavedProgram?=null,val error: String?=null,
    val planSource: CurrentReadOnlyPlanSource=UnconnectedCurrentReadOnlyPlanSource,val storeChangeKey: Long=0,
    val workout: NativeWorkoutProgress?=null,val workoutBusy: Boolean=false,val workoutError: String?=null,
    val workoutBlocked: Boolean=false,val completion: CanonicalWorkoutHistoryReceipt?=null
)

/** One process-scoped engine, canonical writer and workout journal. No backup import. */
internal class NativeRuntimeHost(context: Context) : AutoCloseable {
    private val context=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val started=AtomicBoolean(false)
    private val lifetime=Any()
    private var closed=false
    private val actionVersion=AtomicLong(0)
    private val mutable=MutableStateFlow(NativeRuntimeState())
    val state: StateFlow<NativeRuntimeState> = mutable.asStateFlow()
    private var store: CanonicalWorkoutStorePersistence?=null
    private var journal: WorkoutJournalPersistence?=null
    private var subscription: AutoCloseable?=null
    private var action: Job?=null

    fun start() {
        if (!started.compareAndSet(false,true)) return
        scope.launch {
            val loaded=PackagedReadOnlySchemaVerifier.openIfPackaged(context)
            synchronized(lifetime) {
                if (closed || !isActive) { loaded?.close();return@launch }
                if (loaded!=null) mutable.update { it.copy(verifier=loaded) }
            }
            if (loaded==null) {
                mutable.update { it.copy(loading=false,error="Program creation is unavailable in this preview.") }
                return@launch
            }
            try {
                val engine=loaded.programEngine
                val options=checkNotNull(engine.programOptions())
                val owner=CanonicalWorkoutStorePersistence(AppPrivateCanonicalWorkoutStoreBytes(context),engine)
                store=owner
                val watching=owner.subscribeCommittedChanges {
                    mutable.update { it.copy(storeChangeKey=it.storeChangeKey+1) }
                }
                synchronized(lifetime) {
                    if(closed) { watching.close();throw CancellationException("Runtime closed") }
                    subscription=watching
                }
                val source=CanonicalCurrentPlanSourceAdapter(
                    ExactCurrentSnapshotProjectionPort(owner,loaded.currentPlanProjector))
                val saved=readSaved(owner,loaded)
                val recovery=WorkoutJournalPersistence(AppPrivateWorkoutJournalBytes(context))
                journal=recovery
                val workout=owner.recoverIssuedSession(recovery)
                val completion=owner.lastCompletionReceipt()
                ensureActive()
                mutable.update { it.copy(loading=false,ready=true,options=options,saved=saved,
                    planSource=source,error=null,workout=workout,completion=completion) }
                // A durable Finish remains authorized after process death. An
                // unfinished workout only resumes; it is never auto-completed.
                if(workout?.journal?.isFinished==true)finishWorkout()
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                // Unreadable existing bytes are not a fresh onboarding store.
                mutable.update { it.copy(loading=false,blocked=true,
                    error="Program setup could not be opened. Any saved training data has been kept on this device.") }
            }
        }
    }
    private suspend fun readSaved(owner: CanonicalWorkoutStorePersistence,
        loaded: PackagedReadOnlySchemaVerifier): NativeSavedProgram? {
        val bytes=owner.readOwnedCurrentStoreBytes() ?: return null
        return try {
            checkNotNull(loaded.programEngine.inspectProgram(String(bytes,Charsets.UTF_8)))
        } finally { bytes.fill(0) }
    }
    fun review(profile: NativeProgramProfile) {
        val current=mutable.value
        if (!current.ready || current.busy || current.blocked || current.saved!=null) return
        val version=actionVersion.incrementAndGet()
        mutable.update { it.copy(busy=true,error=null,proposal=null) }
        action=scope.launch {
            try {
                val request=NativeProgramSetupCodec.setupJson(profile,"create-"+UUID.randomUUID(),
                    "program-"+UUID.randomUUID(),System.currentTimeMillis(),Random.nextInt(1,Int.MAX_VALUE))
                val proposal=checkNotNull(current.verifier?.programEngine?.prepareProgram(request))
                ensureActive()
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,proposal=proposal,error=null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,error="A program could not be built with these choices. Review your equipment and schedule, then try again.") else it }
            }
        }
    }
    fun edit() {
        actionVersion.incrementAndGet()
        action?.cancel()
        mutable.update { it.copy(proposal=null,busy=false,error=null) }
    }
    fun save() {
        val current=mutable.value;val proposal=current.proposal ?: return
        if (current.busy || current.blocked || current.saved!=null) return
        val owner=store ?: return;val loaded=current.verifier ?: return
        val version=actionVersion.incrementAndGet()
        mutable.update { it.copy(busy=true,error=null) }
        action=scope.launch {
            try {
                check(owner.initialize(proposal.snapshot)) // exact retry only; never replaces existing data
                val saved=checkNotNull(readSaved(owner,loaded))
                check(saved.creationId==proposal.creationId)
                ensureActive()
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,saved=saved,proposal=null,error=null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,error="The save could not be confirmed. Your choices are still here. Try saving again.") else it }
            }
        }
    }

    fun startWorkout() {
        val current=mutable.value
        if(!current.ready || current.workoutBusy || current.workoutBlocked || current.workout!=null || current.saved==null)return
        val owner=store ?: return;val recovery=journal ?: return;val loaded=current.verifier ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null) }
        scope.launch {
            try {
                val existing=owner.recoverIssuedSession(recovery)
                val progress=existing ?: run {
                    val bytes=checkNotNull(owner.readOwnedCurrentStoreBytes())
                    val plan=try {
                        checkNotNull(loaded.programEngine.prepareWorkout(String(bytes,Charsets.UTF_8),
                            NativeWorkoutCodec.startJson(current.saved.program.id,"workout-"+UUID.randomUUID(),System.currentTimeMillis())))
                    } finally { bytes.fill(0) }
                    ensureActive()
                    checkNotNull(owner.beginIssuedSession(plan,recovery))
                }
                ensureActive()
                // Starting another workout cannot erase the last durable completion receipt.
                mutable.update { it.copy(workout=progress,workoutBusy=false,workoutError=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                // A begin may have landed before its ack was lost. Recover the
                // pinned session rather than generating a second workout.
                val restored=runCatching { owner.recoverIssuedSession(recovery) }.getOrNull()
                mutable.update { it.copy(workout=restored ?: it.workout,workoutBusy=false,
                    workoutError=if(restored!=null)null else "This workout could not be opened. Advanced sub-sets and one-shot deloads are not available yet. Saved training data has been kept.") }
            }
        }
    }

    fun recordSet(slot: JournalSetSlot,disposition: JournalSetDisposition,values: JournalSetValues?) {
        val current=mutable.value;val progress=current.workout ?: return
        if(current.workoutBusy || current.workoutBlocked || progress.journal.isFinished)return
        // A blank load is never turned into zero; target RIR is never actual RIR.
        if(disposition==JournalSetDisposition.COMPLETED && (values?.load==null || values.unit!=progress.plan.session.unit || values.reps !in 1..100))return
        val owner=store ?: return;val recovery=journal ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null) }
        scope.launch {
            try {
                val next=checkNotNull(recovery.recordSet(progress.plan.session,progress.journal.revision,
                    JournalSetIntent("set-"+UUID.randomUUID(),slot,disposition,values)))
                mutable.update { it.copy(workout=NativeWorkoutProgress(progress.plan,next),workoutBusy=false,workoutError=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                val restored=runCatching { owner.recoverIssuedSession(recovery) }.getOrNull()
                val confirmed=restored?.journal?.completed?.any { it.intent.slot==slot &&
                    it.intent.disposition==disposition && it.intent.values==values }==true
                mutable.update { it.copy(workout=restored ?: it.workout,workoutBusy=false,
                    workoutError=if(confirmed)null else "This set could not be confirmed as saved. Your entries are still here. Try again.") }
            }
        }
    }

    fun finishWorkout() {
        val current=mutable.value;val progress=current.workout ?: return
        if(current.workoutBusy || current.workoutBlocked || progress.journal.unresolvedCount!=0)return
        val owner=store ?: return;val recovery=journal ?: return;val loaded=current.verifier ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null) }
        scope.launch {
            try {
                val now=maxOf(System.currentTimeMillis(),progress.plan.session.startedAt)
                val finished=if(progress.journal.isFinished)progress.journal else checkNotNull(recovery.finish(
                    progress.plan.session,progress.journal.revision,JournalFinishIntent("finish-"+UUID.randomUUID(),
                        now,now-progress.plan.session.startedAt)))
                mutable.update { it.copy(workout=NativeWorkoutProgress(progress.plan,finished)) }
                val outcome=checkNotNull(owner.complete(progress.plan.session,finished.revision,recovery))
                check(outcome.recoveryCleared)
                val saved=checkNotNull(readSaved(owner,loaded))
                mutable.update { it.copy(workout=null,workoutBusy=false,workoutError=null,completion=outcome.receipt,saved=saved) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                // This also recovers history committed immediately before an
                // interrupted acknowledgement or recovery cleanup.
                val restored=runCatching { owner.recoverIssuedSession(recovery) }
                val receipt=runCatching { owner.lastCompletionReceipt() }.getOrNull()
                val done=restored.isSuccess && restored.getOrNull()==null && receipt?.session==progress.plan.session
                val saved=if(done)runCatching { readSaved(owner,loaded) }.getOrNull() else null
                mutable.update { it.copy(workout=if(done)null else restored.getOrNull() ?: it.workout,workoutBusy=false,
                    completion=if(done)receipt else it.completion,saved=saved ?: it.saved,
                    workoutError=if(done)null else "Workout completion could not be confirmed. Your saved sets are kept here. Retry finishing.") }
            }
        }
    }
    override fun close() {
        synchronized(lifetime) {
            if(closed)return
            closed=true;actionVersion.incrementAndGet()
            scope.cancel();subscription?.close();mutable.value.verifier?.close()
        }
    }
}
