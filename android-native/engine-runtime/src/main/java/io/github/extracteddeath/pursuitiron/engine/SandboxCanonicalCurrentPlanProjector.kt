package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.CanonicalCurrentPlanFrame
import io.github.extracteddeath.pursuitiron.domain.CurrentProgramProjectionCodec
import io.github.extracteddeath.pursuitiron.domain.PinnedCanonicalPlanProjector
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Reuses the already pinned single-worker canonical JavaScript sandbox.
 * Not connected to Activity or an authoritative current-store owner.
 * Offline backups may NOT be promoted via this projector.
 */
class SandboxCanonicalCurrentPlanProjector(
    private val worker: CanonicalJsReadOnlyService
) : PinnedCanonicalPlanProjector {
    private val sequence = AtomicLong(0)

    override suspend fun projectCurrentSavedProgram(
        exactUtf8Snapshot: String, programId: String
    ): CanonicalCurrentPlanFrame? = suspendCoroutine { continuation ->
        val requestId = "native-current-projection-" + sequence.incrementAndGet()
        val wire = try {
            CurrentProgramProjectionCodec.encode(requestId, exactUtf8Snapshot, programId)
        } catch (cancelled: CancellationException) {
            continuation.resumeWith(Result.failure(cancelled))
            return@suspendCoroutine
        } catch (_: Exception) {
            continuation.resume(null)
            return@suspendCoroutine
        }
        try {
            worker.currentProgramProjectionAsync(wire).whenComplete { raw, failure ->
                val frame = try {
                    if (failure != null) throw failure
                    CurrentProgramProjectionCodec.decode(
                        raw, requestId, exactUtf8Snapshot, programId
                    )
                } catch (_: Exception) {
                    null
                }
                continuation.resume(frame)
            }
        } catch (_: Exception) {
            continuation.resume(null)
        }
    }
}
