package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.EngineImplementation
import io.github.extracteddeath.pursuitiron.domain.EngineJsonCodec
import io.github.extracteddeath.pursuitiron.domain.EngineResponse
import io.github.extracteddeath.pursuitiron.domain.FeatureCertification
import io.github.extracteddeath.pursuitiron.domain.ProgressionBackend
import io.github.extracteddeath.pursuitiron.domain.ProgressionPreviewInput
import io.github.extracteddeath.pursuitiron.domain.ProgressionPreviewResult
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Native-language adapter for the exact canonical JS read-only wire protocol.
 * It is deliberately UNCERTIFIED and cannot be selected by the parity-gated
 * router. This must never be constructed by Compose until additional feature,
 * crash-recovery, version and data-ownership gates pass.
 *
 * No Kotlin progression calculations or inferred fallback suggestions.
 */
class SandboxProgressionBackend(
    private val worker: CanonicalJsReadOnlyService
) : ProgressionBackend {
    override val implementation: EngineImplementation = EngineImplementation.CANONICAL_JS
    override val certification: FeatureCertification? = null

    private val sequence = AtomicLong(0)

    override suspend fun preview(
        input: ProgressionPreviewInput
    ): Result<ProgressionPreviewResult> = suspendCoroutine { continuation ->
        val id = "android-readonly-${sequence.incrementAndGet()}"
        val wire = try {
            EngineJsonCodec.encodeProgressionPreview(id, input)
        } catch (error: Throwable) {
            continuation.resume(Result.failure(error))
            return@suspendCoroutine
        }
        try {
            worker.previewAsync(wire).whenComplete { raw, failure ->
                val result = runCatching {
                    if (failure != null) throw (failure.cause ?: failure)
                    when (val packet = EngineJsonCodec.decodeProgressionPreview(raw, id)) {
                        is EngineResponse.Success -> packet.result
                        is EngineResponse.Failure ->
                            throw IllegalStateException("Canonical bridge: ${packet.error.code}")
                    }
                }
                continuation.resume(result)
            }
        } catch (error: Throwable) {
            continuation.resume(Result.failure(error))
        }
    }
}
