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
    val workoutBlocked: Boolean=false,val completion: CanonicalWorkoutHistoryReceipt?=null,
    val history: NativeWorkoutHistory?=null,val historyPage: NativeHistoryPage?=null,
    val historyBusy: Boolean=false,val historyError: String?=null,
    val historyFilter: NativeHistoryFilter=NativeHistoryFilter(),
    val historyCalendarQuery: NativeHistoryCalendarQuery?=null,val historyCalendar: NativeHistoryCalendar?=null,
    val backupExportBusy: Boolean=false,val backupExportMessage: String?=null,val backupExportError: String?=null,
    val shortPreview: NativeShortSessionPreview?=null,
    val shortPreviewBusy: Boolean=false,val shortPreviewError: String?=null,
    val muscleReview: NativeWorkoutMuscleReview?=null,
    val muscleReviewBusy: Boolean=false,val muscleReviewError: String?=null
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
    private var historyReader: NativeHistoryReadPort?=null
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
                historyReader=NativeHistoryReadPort(owner,engine)
                val watching=owner.subscribeCommittedChanges {
                    mutable.update { it.copy(storeChangeKey=it.storeChangeKey+1,shortPreview=null,muscleReview=null,historyCalendar=null) }
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
                val historyPage=optionalHistoryPage()
                ensureActive()
                mutable.update { it.copy(loading=false,ready=true,options=options,saved=saved,
                    planSource=source,error=null,workout=workout,completion=completion,historyPage=historyPage) }
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
    private suspend fun readHistory(receipt: CanonicalWorkoutHistoryReceipt?): NativeWorkoutHistory? {
        val id=receipt?.session?.workoutId ?: return null
        return historyReader?.workout(id)
    }
    private suspend fun optionalHistoryPage(offset: Int=0,expected: String?=null,
        filter: NativeHistoryFilter=NativeHistoryFilter()): NativeHistoryPage? = try {
        historyReader?.page(offset,expected,filter)
    } catch(cancelled: CancellationException) { throw cancelled }
      catch(_: Exception) { null }

    private suspend fun optionalHistoryCalendar(query: NativeHistoryCalendarQuery?,expected: String?=null): NativeHistoryCalendar? = try {
        query?.let { historyReader?.calendar(it,expected) }
    } catch(cancelled: CancellationException) { throw cancelled }
      catch(_: Exception) { null }

    fun resetBackupExportStatus() {
        mutable.update { if(it.backupExportBusy)it else it.copy(backupExportMessage=null,backupExportError=null) }
    }
    fun exportBackup(document: NativeBackupExportDocument) {
        val current=mutable.value;val owner=store;val loaded=current.verifier
        if(!current.ready || current.blocked || current.busy || current.workoutBlocked || current.workout!=null ||
            current.workoutBusy || current.historyBusy || current.backupExportBusy || current.saved==null || owner==null || loaded==null) {
            scope.launch { runCatching { document.discard() } }
            if(!current.backupExportBusy)mutable.update { it.copy(backupExportMessage=null,
                backupExportError="Backup could not start. Finish any active workout and wait for saved changes, then try again.") }
            return
        }
        // Hold the existing history interaction gate until readback finishes;
        // open correction sheets must also wait rather than submit a no-op.
        mutable.update { it.copy(backupExportBusy=true,historyBusy=true,backupExportMessage=null,backupExportError=null) }
        scope.launch {
            try {
                val job=currentCoroutineContext()
                NativeBackupExporter(owner,loaded.programEngine).export(document) { job.ensureActive() }
                ensureActive()
                mutable.update { it.copy(backupExportBusy=false,historyBusy=false,backupExportMessage="Backup saved and verified.",backupExportError=null) }
            } catch(cancelled: CancellationException) {
                mutable.update { it.copy(backupExportBusy=false,historyBusy=false,backupExportMessage=null) };throw cancelled
            } catch(_: Exception) {
                mutable.update { it.copy(backupExportBusy=false,historyBusy=false,backupExportMessage=null,
                    backupExportError="The backup could not be confirmed. Discard any incomplete copy and try again.") }
            }
        }
    }

    fun reloadHistory() {
        val current=mutable.value
        val selected=current.history
        if(selected!=null)loadHistoryWorkout(selected.historyId,null)
        else current.historyCalendarQuery?.let(::loadHistoryCalendar) ?: loadHistoryPage(0)
    }
    fun showHistoryList() {
        if(mutable.value.historyBusy)return
        mutable.update { it.copy(history=null,historyError=null) }
    }
    fun filterHistory(filter: NativeHistoryFilter)=loadHistoryPage(0,filter)
    fun loadHistoryPage(offset: Int)=loadHistoryPage(offset,mutable.value.historyFilter)
    private fun loadHistoryPage(offset: Int,filter: NativeHistoryFilter) {
        val current=mutable.value
        if(!current.ready || current.busy || current.saved==null || current.historyBusy || current.workoutBusy)return
        val prior=current.historyPage
        if(offset!=0 && (prior==null || prior.filter!=filter ||
            offset!=prior.nextOffset && offset!=(prior.offset-NativeHistoryPageCodec.PAGE_SIZE).coerceAtLeast(0)))return
        val reader=historyReader ?: return
        val expected=if(offset==0)null else prior?.snapshotSha256
        mutable.update { it.copy(historyBusy=true,historyError=null) }
        scope.launch {
            try {
                val page=checkNotNull(reader.page(offset,expected,filter))
                ensureActive()
                mutable.update { if(it.storeChangeKey==current.storeChangeKey)
                    it.copy(history=null,historyPage=page,historyFilter=page.filter,historyBusy=false,historyError=null,
                        historyCalendarQuery=null,historyCalendar=null)
                    else it.copy(historyBusy=false,historyError="History changed while loading. Reload workouts to continue.") }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) { mutable.update { it.copy(historyBusy=false,
                historyError="History could not be refreshed. Reload workouts to check the latest saved values.") } }
        }
    }
    fun loadHistoryCalendar(query: NativeHistoryCalendarQuery) {
        val current=mutable.value
        if(!current.ready || current.busy || current.saved==null || current.historyBusy || current.workoutBusy)return
        val reader=historyReader ?: return
        mutable.update { it.copy(history=null,historyCalendarQuery=query,historyCalendar=null,historyBusy=true,historyError=null) }
        scope.launch {
            try {
                val calendar=checkNotNull(reader.calendar(query))
                ensureActive()
                mutable.update { if(it.storeChangeKey==current.storeChangeKey)
                    it.copy(historyCalendar=calendar,historyBusy=false,historyError=null)
                    else it.copy(historyBusy=false,historyError="History changed while loading. Reload the calendar to continue.") }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) { mutable.update { it.copy(historyBusy=false,
                historyError="The calendar could not be refreshed. Reload it to check your saved workouts.") } }
        }
    }
    fun openHistory(historyId: String) {
        val current=mutable.value
        if(current.historyCalendarQuery!=null) {
            val calendar=current.historyCalendar ?: return
            if(calendar.query!=current.historyCalendarQuery || calendar.entries.none { it.historyId==historyId && it.detailsAvailable })return
            loadHistoryWorkout(historyId,calendar.snapshotSha256)
        } else {
            val page=current.historyPage ?: return
            if(page.entries.none { it.historyId==historyId && it.detailsAvailable })return
            loadHistoryWorkout(historyId,page.snapshotSha256)
        }
    }
    private fun loadHistoryWorkout(historyId: String,expected: String?) {
        val current=mutable.value
        if(!current.ready || current.busy || current.historyBusy || current.workoutBusy)return
        val reader=historyReader ?: return
        mutable.update { it.copy(historyBusy=true,historyError=null) }
        scope.launch {
            try {
                val history=checkNotNull(reader.workout(historyId,expected))
                val page=current.historyPage?.takeIf { it.snapshotSha256==history.snapshotSha256 && it.filter==current.historyFilter }
                    ?: optionalHistoryPage(current.historyPage?.offset ?: 0,history.snapshotSha256,current.historyFilter)
                val calendar=current.historyCalendar?.takeIf { it.snapshotSha256==history.snapshotSha256 && it.query==current.historyCalendarQuery }
                    ?: optionalHistoryCalendar(current.historyCalendarQuery,history.snapshotSha256)
                ensureActive()
                // Browsing an older workout never replaces the latest completion receipt.
                mutable.update { if(it.storeChangeKey==current.storeChangeKey)
                    it.copy(history=history,historyPage=page,historyCalendar=calendar,historyBusy=false,historyError=null)
                    else it.copy(historyBusy=false,historyError="History changed while loading. Reload workouts to continue.") }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) { mutable.update { it.copy(historyBusy=false,
                historyError="This workout could not be opened. Reload workouts to check the latest saved values.") } }
        }
    }
    fun correctHistory(correctionJson: String) {
        val current=mutable.value
        if(!current.ready || current.historyBusy || current.workoutBusy || current.backupExportBusy || current.workout!=null)return
        val owner=store ?: return
        mutable.update { it.copy(historyBusy=true,historyError=null) }
        scope.launch {
            try {
                val history=checkNotNull(owner.correctHistory(correctionJson))
                val receipt=owner.lastCompletionReceipt()
                val page=optionalHistoryPage(current.historyPage?.offset ?: 0,history.snapshotSha256,current.historyFilter)
                val calendar=optionalHistoryCalendar(current.historyCalendarQuery,history.snapshotSha256)
                ensureActive()
                mutable.update { it.copy(history=history,historyPage=page,historyCalendar=calendar,completion=receipt,historyBusy=false,
                    historyError=if(current.historyCalendarQuery!=null && calendar==null)"Correction saved. Reload the calendar to refresh it."
                        else if(page==null)"Correction saved. Reload workouts to refresh the list." else null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) { mutable.update { it.copy(historyBusy=false,
                historyError="The correction could not be confirmed. Retry saving, or reload saved sets to check the current values.") } }
        }
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
                val historyPage=optionalHistoryPage()
                ensureActive()
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,saved=saved,proposal=null,error=null,historyPage=historyPage) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                mutable.update { if(actionVersion.get()==version)it.copy(busy=false,error="The save could not be confirmed. Your choices are still here. Try saving again.") else it }
            }
        }
    }

    /** Show canonical choices without changing this or any future session. */
    fun previewShorterSession(minutes: Int) {
        val current=mutable.value
        if(minutes !in listOf(20,30,45) || !current.ready ||
            current.workout!=null || current.workoutBusy || current.busy ||
            current.shortPreviewBusy || current.saved==null)return
        val owner=store ?: return;val engine=current.verifier?.programEngine ?: return
        val programId=current.saved.program.id
        mutable.update { it.copy(shortPreviewBusy=true,shortPreview=null,shortPreviewError=null) }
        scope.launch {
            try {
                val bytes=checkNotNull(owner.readOwnedCurrentStoreBytes())
                val preview=try {
                    val snapshot=Charsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    checkNotNull(engine.previewShorterSession(snapshot,programId,minutes))
                } finally { bytes.fill(0) }
                ensureActive()
                mutable.update { latest ->
                    if(latest.storeChangeKey==current.storeChangeKey &&
                        latest.workout==null && !latest.workoutBusy &&
                        latest.saved?.program?.id==programId)
                        latest.copy(shortPreviewBusy=false,shortPreview=preview,shortPreviewError=null)
                    else latest.copy(shortPreviewBusy=false,shortPreview=null,
                        shortPreviewError="Training data changed. Preview the next session again.")
                }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                mutable.update { it.copy(shortPreviewBusy=false,shortPreview=null,
                    shortPreviewError="The shortened workout could not be previewed. Your program was not changed.") }
            }
        }
    }

    fun startWorkout(minutes: Int?=null) {
        val current=mutable.value
        if(!current.ready || current.workoutBusy || current.historyBusy || current.backupExportBusy || current.workoutBlocked || current.workout!=null || current.saved==null)return
        // A shortened start is permitted only for the preview actually shown.
        // The owned snapshot is verified AGAIN immediately before issuing.
        val approvedPreview=if(minutes!=null) current.shortPreview?.takeIf {
            it.minutes==minutes && it.fits && it.changed && !current.shortPreviewBusy
        } ?: return else null
        val owner=store ?: return;val recovery=journal ?: return;val loaded=current.verifier ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null,shortPreview=null,shortPreviewError=null,muscleReview=null,muscleReviewError=null) }
        scope.launch {
            try {
                val existing=owner.recoverIssuedSession(recovery)
                val progress=existing ?: run {
                    val bytes=checkNotNull(owner.readOwnedCurrentStoreBytes())
                    val plan=try {
                        val snapshot=String(bytes,Charsets.UTF_8)
                        if(minutes!=null)check(NativeShortSessionPreviewCodec.matchesSource(
                            checkNotNull(approvedPreview),snapshot,current.saved.program.id,minutes))
                        checkNotNull(loaded.programEngine.prepareWorkout(snapshot,
                            NativeWorkoutCodec.startJson(current.saved.program.id,"workout-"+UUID.randomUUID(),
                                System.currentTimeMillis(),minutes)))
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
                    workoutError=if(restored!=null)null else "This workout could not be opened. Saved training data has been kept. Try again.") }
            }
        }
    }

    /**
     * Explicitly confirmed exercise-level skip. This only writes individual
     * SKIPPED receipts to the existing journal. It cannot re-prescribe a set,
     * convert a skip into performance or silently change a future workout.
     *
     * A crash between receipts retains already-saved skips. Reopening the
     * same session finds only unresolved slots and can safely retry.
     */
    /**
     * Read the exact engine-issued workout's primary and secondary muscles.
     * This has no permission to change exercises or saved training records.
     */
    fun inspectWorkoutMuscles() {
        val current=mutable.value
        val plan=current.workout?.plan ?: return
        if(!current.ready || current.workoutBusy || current.historyBusy ||
            current.muscleReviewBusy || current.muscleReview?.workoutId==plan.session.workoutId)return
        val owner=store ?: return;val engine=current.verifier?.programEngine ?: return
        mutable.update { it.copy(muscleReviewBusy=true,muscleReviewError=null) }
        scope.launch {
            try {
                val bytes=checkNotNull(owner.readOwnedCurrentStoreBytes())
                val view=try {
                    val source=String(bytes,Charsets.UTF_8)
                    check(NativeWorkoutMuscleReviewCodec.matchesOrigin(source,plan))
                    checkNotNull(engine.inspectWorkoutMuscles(source,plan.issuedJson))
                } finally { bytes.fill(0) }
                ensureActive()
                mutable.update { latest ->
                    if(latest.workout?.plan?.issuedJson==plan.issuedJson &&
                        latest.storeChangeKey==current.storeChangeKey)
                        latest.copy(muscleReview=view,muscleReviewBusy=false,muscleReviewError=null)
                    else latest.copy(muscleReview=null,muscleReviewBusy=false,
                        muscleReviewError="Workout changed. Reopen the exercise review.")
                }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                mutable.update { it.copy(muscleReviewBusy=false,
                    muscleReviewError="Muscle involvement could not be checked. Your workout is unchanged.") }
            }
        }
    }

    fun skipRemainingExercise(exerciseIndex: Int) = skipSelectedIssuedSlots {
        NativeSessionSkipPolicy.remaining(it,exerciseIndex)
    }

    /** Explicit confirmation only; the muscle review must match the pinned issue. */
    fun skipAffectedMuscle(part: String) {
        val current=mutable.value
        val plan=current.workout?.plan ?: return
        val review=current.muscleReview?.takeIf { it.workoutId==plan.session.workoutId } ?: return
        skipSelectedIssuedSlots(JournalSetAdjustment("skip",part)) {
            NativeSessionSkipPolicy.remainingAffected(it,review,part)
        }
    }

    /**
     * Reduce the original issued work by a stable 50% cap per affected lift.
     * Reconfirming after a lost acknowledgement only sees the same remaining
     * tail slots, never the "half again" error from recomputing an open dose.
     */
    fun reduceAffectedMuscle(part: String) {
        val current=mutable.value
        val plan=current.workout?.plan ?: return
        val review=current.muscleReview?.takeIf { it.workoutId==plan.session.workoutId } ?: return
        skipSelectedIssuedSlots(JournalSetAdjustment("reduce",part)) {
            NativeSessionSkipPolicy.reduceAffected(it,review,part)
        }
    }

    /**
     * Single-exercise skips, all-related skips and reduced work share durable receipts.
     * After an interrupted write the new invocation chooses only pending slots.
     * No injury diagnosis or reason is inferred from a generic SKIPPED receipt.
     */
    private fun skipSelectedIssuedSlots(adjustment: JournalSetAdjustment?=null,
        select: (WorkoutJournalState)->List<JournalSetSlot>) {
        val current=mutable.value;val progress=current.workout ?: return
        if(current.workoutBusy || current.historyBusy || current.workoutBlocked ||
            progress.journal.isFinished || progress.plan.slots!=progress.journal.slots)return
        val pending=runCatching { select(progress.journal) }.getOrNull() ?: return
        if(pending.isEmpty())return
        val owner=store ?: return;val recovery=journal ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null) }
        scope.launch {
            try {
                var active=checkNotNull(owner.recoverIssuedSession(recovery))
                check(active.plan.issuedJson==progress.plan.issuedJson &&
                    active.plan.session==progress.plan.session &&
                    active.plan.slots==progress.plan.slots)
                for(slot in select(active.journal)) {
                    ensureActive()
                    val intent=JournalSetIntent("skip-"+UUID.randomUUID(),slot,
                        JournalSetDisposition.SKIPPED,null,adjustment=adjustment)
                    val saved=checkNotNull(recovery.recordSet(active.plan.session,active.journal.revision,intent))
                    active=NativeWorkoutProgress(active.plan,saved)
                    // Report only receipts confirmed on disk, never optimistic taps.
                    mutable.update { it.copy(workout=active,workoutBusy=true) }
                }
                mutable.update { it.copy(workout=active,workoutBusy=false,workoutError=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                val restored=runCatching { owner.recoverIssuedSession(recovery) }.getOrNull()
                mutable.update { it.copy(workout=restored ?: it.workout,workoutBusy=false,
                    workoutError="Some skips may have been saved. Check the remaining sets before trying again.") }
            }
        }
    }

    fun recordSet(slot: JournalSetSlot,disposition: JournalSetDisposition,values: JournalSetValues?) {
        persistSet(JournalSetIntent("set-"+UUID.randomUUID(),slot,disposition,values))
    }

    fun correctSet(intent: JournalSetIntent) {
        if(intent.replacesOperationId==null)return
        persistSet(intent)
    }

    private fun persistSet(intent: JournalSetIntent) {
        val current=mutable.value;val progress=current.workout ?: return
        if(current.workoutBusy || current.workoutBlocked || progress.journal.isFinished)return
        // A blank load is never turned into zero; target RIR is never actual RIR.
        val values=intent.values
        if(intent.disposition==JournalSetDisposition.COMPLETED && !NativeWorkoutCodec.acceptsValues(progress.plan,intent.slot,values))return
        val owner=store ?: return;val recovery=journal ?: return
        mutable.update { it.copy(workoutBusy=true,workoutError=null) }
        scope.launch {
            try {
                val previous=progress.journal.events.firstOrNull { it.intent==intent }
                val next=checkNotNull(recovery.recordSet(progress.plan.session,
                    previous?.receipt?.fromRevision ?: progress.journal.revision,intent))
                mutable.update { it.copy(workout=NativeWorkoutProgress(progress.plan,next),workoutBusy=false,workoutError=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                val restored=runCatching { owner.recoverIssuedSession(recovery) }.getOrNull()
                val confirmed=restored?.journal?.events?.any { it.intent==intent }==true
                mutable.update { it.copy(workout=restored ?: it.workout,workoutBusy=false,
                    workoutError=if(confirmed)null else "This set change could not be confirmed. Your saved sets are kept. Review your entries and try again.") }
            }
        }
    }

    fun finishWorkout() {
        val current=mutable.value;val progress=current.workout ?: return
        if(current.workoutBusy || current.historyBusy || current.workoutBlocked || progress.journal.unresolvedCount!=0)return
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
                val history=runCatching { readHistory(outcome.receipt) }.getOrNull()
                val page=optionalHistoryPage(filter=current.historyFilter)
                val calendar=optionalHistoryCalendar(current.historyCalendarQuery,page?.snapshotSha256)
                ensureActive()
                mutable.update { it.copy(workout=null,workoutBusy=false,workoutError=null,completion=outcome.receipt,saved=saved,muscleReview=null,
                    history=history,historyPage=page,historyCalendar=calendar,historyError=null) }
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) {
                // This also recovers history committed immediately before an
                // interrupted acknowledgement or recovery cleanup.
                val restored=runCatching { owner.recoverIssuedSession(recovery) }
                val receipt=runCatching { owner.lastCompletionReceipt() }.getOrNull()
                val done=restored.isSuccess && restored.getOrNull()==null && receipt?.session==progress.plan.session
                val saved=if(done)runCatching { readSaved(owner,loaded) }.getOrNull() else null
                val history=if(done)runCatching { readHistory(receipt) }.getOrNull() else null
                val page=if(done)optionalHistoryPage(filter=current.historyFilter) else null
                val calendar=if(done)optionalHistoryCalendar(current.historyCalendarQuery,page?.snapshotSha256) else null
                mutable.update { it.copy(workout=if(done)null else restored.getOrNull() ?: it.workout,workoutBusy=false,
                    completion=if(done)receipt else it.completion,saved=saved ?: it.saved,history=if(done)history else it.history,
                    historyPage=if(done)page else it.historyPage,historyCalendar=if(done)calendar else it.historyCalendar,
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
