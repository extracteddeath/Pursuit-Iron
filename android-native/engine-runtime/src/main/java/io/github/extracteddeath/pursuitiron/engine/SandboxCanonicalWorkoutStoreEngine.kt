package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.PinnedCanonicalWorkoutStoreEngine
import io.github.extracteddeath.pursuitiron.domain.WorkoutStoreRuntimeCodec
import io.github.extracteddeath.pursuitiron.domain.NativeBackupMergePreview
import io.github.extracteddeath.pursuitiron.domain.NativeBackupMergePreviewCodec
import io.github.extracteddeath.pursuitiron.domain.NativeProgramCreationEngine
import io.github.extracteddeath.pursuitiron.domain.NativeProgramSetupCodec
import io.github.extracteddeath.pursuitiron.domain.NativeProgramOptions
import io.github.extracteddeath.pursuitiron.domain.NativeProgramProposal
import io.github.extracteddeath.pursuitiron.domain.NativeSavedProgram
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutEngine
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutPlan
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutCodec
import io.github.extracteddeath.pursuitiron.domain.NativeShortSessionPreview
import io.github.extracteddeath.pursuitiron.domain.NativeShortSessionPreviewCodec
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutMuscleReview
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutMuscleReviewCodec
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryEngine
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryCodec
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutHistory
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryPage
import io.github.extracteddeath.pursuitiron.domain.NativeHistoryPageCodec
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Pure transport for the current-store owner; cannot import or write storage. */
internal fun interface CanonicalWorkoutStoreTransport {
    suspend fun request(wire: String): String
}

/**
 * Connects the exact pinned canonical worker to the atomic native store. The
 * worker must come from verified packaged bytes, never a URI or supplied JS.
 * No UI or storage handle is acquired here. The native owner separately pins
 * issued targets before logging; this transport cannot grant a write itself.
 */
class SandboxCanonicalWorkoutStoreEngine internal constructor(
    private val transport: CanonicalWorkoutStoreTransport
) : PinnedCanonicalWorkoutStoreEngine, NativeProgramCreationEngine, NativeWorkoutEngine, NativeHistoryEngine {
    constructor(worker: CanonicalJsReadOnlyService) : this(CanonicalWorkoutStoreTransport { wire ->
        suspendCancellableCoroutine { continuation ->
            val future=worker.prepareWorkoutStoreAsync(wire)
            continuation.invokeOnCancellation { future.cancel(false) }
            future.whenComplete { raw,failure ->
                if (failure!=null) continuation.resumeWithException(failure.cause ?: failure)
                else continuation.resume(raw)
            }
        }
    })
    private val sequence=AtomicLong(0)

    /**
     * Pure owner-agnostic dry-run. Callers must pin exact owned bytes and hold
     * selected provider bytes only for this operation; no writer is exposed.
     */
    suspend fun previewBackupMerge(currentSnapshot: String, incomingSnapshot: String): NativeBackupMergePreview? {
        val id="native-backup-preflight-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeBackupMergePreviewCodec.encode(id,currentSnapshot,incomingSnapshot))
        currentCoroutineContext().ensureActive()
        return NativeBackupMergePreviewCodec.decode(raw,id,currentSnapshot,incomingSnapshot)
    }

    override suspend fun inspectHistoryPage(snapshot: String,queryJson: String): NativeHistoryPage? {
        val id="native-history-page-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeHistoryPageCodec.encodePage(id,snapshot,queryJson))
        currentCoroutineContext().ensureActive()
        return NativeHistoryPageCodec.decodePage(raw,id,snapshot,queryJson)
    }

    override suspend fun inspectHistory(snapshot: String,historyId: String): NativeWorkoutHistory? {
        val id="native-history-read-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeHistoryCodec.encodeInspect(id,snapshot,historyId))
        currentCoroutineContext().ensureActive()
        return NativeHistoryCodec.decodeInspect(raw,id,snapshot,historyId)
    }
    override suspend fun prepareHistoryCorrection(snapshot: String,correctionJson: String): String? {
        val id="native-history-correct-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeHistoryCodec.encodeCorrection(id,snapshot,correctionJson))
        currentCoroutineContext().ensureActive()
        return NativeHistoryCodec.decodeCorrection(raw,id,snapshot,correctionJson)
    }

    override suspend fun inspectWorkoutMuscles(snapshot: String,issuedWorkout: String): NativeWorkoutMuscleReview? {
        val id="native-muscle-review-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeWorkoutMuscleReviewCodec.encode(id,snapshot,issuedWorkout))
        currentCoroutineContext().ensureActive()
        return NativeWorkoutMuscleReviewCodec.decode(raw,id,snapshot,issuedWorkout)
    }

    override suspend fun previewShorterSession(snapshot: String,programId: String,minutes: Int): NativeShortSessionPreview? {
        val id="native-short-session-${sequence.incrementAndGet()}"
        val request=NativeShortSessionPreviewCodec.encode(id,snapshot,programId,minutes)
        val raw=transport.request(request)
        currentCoroutineContext().ensureActive()
        return NativeShortSessionPreviewCodec.decode(raw,id,snapshot,programId,minutes)
    }

    override suspend fun prepareWorkout(snapshot: String,startJson: String): NativeWorkoutPlan? {
        val id="native-workout-start-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeWorkoutCodec.encodeWorkout(id,snapshot,startJson))
        currentCoroutineContext().ensureActive()
        return NativeWorkoutCodec.decodeWorkout(raw,id,snapshot,startJson)
    }
    override suspend fun prepareIssuedCompletion(origin: String,current: String,journal: String,issued: String): String? {
        val id="native-workout-finish-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeWorkoutCodec.encodeCompletion(id,origin,current,journal,issued))
        currentCoroutineContext().ensureActive()
        return WorkoutStoreRuntimeCodec.decodePreparation(raw,id,origin,current,journal)
    }

    override suspend fun programOptions(): NativeProgramOptions? {
        val id="native-program-options-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeProgramSetupCodec.encodeOptions(id))
        currentCoroutineContext().ensureActive()
        return NativeProgramSetupCodec.decodeOptions(raw,id)
    }
    override suspend fun prepareProgram(setupJson: String): NativeProgramProposal? {
        val id="native-program-create-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeProgramSetupCodec.encodeProgram(id,setupJson))
        currentCoroutineContext().ensureActive()
        return NativeProgramSetupCodec.decodeProposal(raw,id,setupJson)
    }
    override suspend fun inspectProgram(exactSnapshot: String): NativeSavedProgram? {
        val id="native-program-restore-${sequence.incrementAndGet()}"
        val raw=transport.request(NativeProgramSetupCodec.encodeInspect(id,exactSnapshot))
        currentCoroutineContext().ensureActive()
        return NativeProgramSetupCodec.decodeSaved(raw,id,exactSnapshot)
    }

    override suspend fun validateInitialSnapshot(exactSnapshot: String): Boolean {
        val id="native-store-validation-${sequence.incrementAndGet()}"
        val request=WorkoutStoreRuntimeCodec.encodeValidation(id,exactSnapshot)
        val raw=transport.request(request)
        currentCoroutineContext().ensureActive()
        return WorkoutStoreRuntimeCodec.decodeValidation(raw,id,exactSnapshot)
    }

    override suspend fun prepareCompletion(origin: String,current: String,finishedJournal: String): String? {
        val id="native-store-completion-${sequence.incrementAndGet()}"
        val request=WorkoutStoreRuntimeCodec.encodePreparation(id,origin,current,finishedJournal)
        val raw=transport.request(request)
        currentCoroutineContext().ensureActive()
        return WorkoutStoreRuntimeCodec.decodePreparation(raw,id,origin,current,finishedJournal)
    }
}
