package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.EngineResponse
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutline
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutlineCodec
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Strictly synthetic, read-only canonical workout projection.
 * Not a personalized program, set prescription or write gateway.
 * Does not connect to Compose until parity gates and stored program
 * contracts are independently certified.
 */
class SandboxWorkoutOutline(private val worker: CanonicalJsReadOnlyService) {
    private val nextId = AtomicLong(0)

    suspend fun inspect(split: String, dayIndex: Int): Result<WorkoutOutline> =
        suspendCoroutine { continuation ->
            val id = "android-outline-${nextId.incrementAndGet()}"
            val request = try {
                WorkoutOutlineCodec.encodeOutline(id,split,dayIndex)
            } catch (failure: Throwable) {
                continuation.resume(Result.failure(failure))
                return@suspendCoroutine
            }
            try {
                worker.syntheticOutlineAsync(request).whenComplete { raw, failure ->
                    continuation.resume(runCatching {
                        if (failure != null) throw (failure.cause ?: failure)
                        when (val decoded = WorkoutOutlineCodec.decodeOutline(
                            raw,id,split,dayIndex
                        )) {
                            is EngineResponse.Success -> decoded.result
                            is EngineResponse.Failure ->
                                throw IllegalStateException(
                                    "Canonical workout outline: ${decoded.error.code}"
                                )
                        }
                    })
                }
            } catch (failure: Throwable) {
                continuation.resume(Result.failure(failure))
            }
        }
}
