package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.EngineResponse
import io.github.extracteddeath.pursuitiron.domain.ExerciseCatalogCodec
import io.github.extracteddeath.pursuitiron.domain.ExerciseLookupResult
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Metadata-only canonical catalog reader. This has no workout/plan output,
 * local storage, custom exercise import or authorization to mutate data.
 * It remains disconnected from Compose pending device verification.
 */
class SandboxExerciseCatalog(
    private val worker: CanonicalJsReadOnlyService
) {
    private val sequence = AtomicLong(0)

    suspend fun lookup(ids: List<String>): Result<ExerciseLookupResult> =
        suspendCoroutine { continuation ->
            val requestId = "android-exercises-${sequence.incrementAndGet()}"
            val request = try {
                ExerciseCatalogCodec.encodeLookup(requestId, ids)
            } catch (failure: Throwable) {
                continuation.resume(Result.failure(failure))
                return@suspendCoroutine
            }
            try {
                worker.lookupExercisesAsync(request).whenComplete { raw, failure ->
                    continuation.resume(runCatching {
                        if (failure != null) throw (failure.cause ?: failure)
                        when (val decoded = ExerciseCatalogCodec.decodeLookup(
                            raw, requestId, ids
                        )) {
                            is EngineResponse.Success -> decoded.result
                            is EngineResponse.Failure ->
                                throw IllegalStateException(
                                    "Canonical catalog: ${decoded.error.code}"
                                )
                        }
                    })
                }
            } catch (failure: Throwable) {
                continuation.resume(Result.failure(failure))
            }
        }
}
