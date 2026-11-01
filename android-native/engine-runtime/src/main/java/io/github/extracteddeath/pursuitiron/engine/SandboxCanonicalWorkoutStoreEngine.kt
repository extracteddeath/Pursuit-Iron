package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.PinnedCanonicalWorkoutStoreEngine
import io.github.extracteddeath.pursuitiron.domain.WorkoutStoreRuntimeCodec
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
 * No UI or storage handle is acquired here. Preview remains unconnected until
 * onboarding, authenticated sessions and Android acceptance are complete.
 */
class SandboxCanonicalWorkoutStoreEngine internal constructor(
    private val transport: CanonicalWorkoutStoreTransport
) : PinnedCanonicalWorkoutStoreEngine {
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
