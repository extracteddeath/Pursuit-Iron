package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.PinnedCanonicalWorkoutStoreEngine
import io.github.extracteddeath.pursuitiron.domain.WorkoutStoreRuntimeCodec
import io.github.extracteddeath.pursuitiron.domain.NativeProgramCreationEngine
import io.github.extracteddeath.pursuitiron.domain.NativeProgramSetupCodec
import io.github.extracteddeath.pursuitiron.domain.NativeProgramOptions
import io.github.extracteddeath.pursuitiron.domain.NativeProgramProposal
import io.github.extracteddeath.pursuitiron.domain.NativeSavedProgram
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutEngine
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutPlan
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutCodec
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
) : PinnedCanonicalWorkoutStoreEngine, NativeProgramCreationEngine, NativeWorkoutEngine {
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
