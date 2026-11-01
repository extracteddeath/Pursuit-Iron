package io.github.extracteddeath.pursuitiron.domain

/**
 * Language-neutral feature gateway. Canonical JS is the benchmark; Kotlin may
 * replace an individual feature only after matching that benchmark.
 * This is a read-only preview operation, NOT the workout logging engine.
 */
enum class EngineImplementation { CANONICAL_JS, NATIVE_KOTLIN }
enum class EngineFeature { PROGRESSION_PREVIEW }

data class FeatureCertification(
    val feature: EngineFeature,
    val protocolVersion: Int,
    val canonicalSourceDigest: String,
    val fixtureDigest: String
)

/** Certification is externally checked, never inferred from an engine handshake. */
data class VerifiedFeatureGate(
    val required: FeatureCertification,
    val approvedImplementations: Set<EngineImplementation>
) {
    private val sha256 = Regex("^[0-9a-f]{64}$")

    fun permits(
        backend: EngineImplementation,
        attestation: FeatureCertification?
    ): Boolean =
        backend in approvedImplementations && attestation == required &&
        required.feature == EngineFeature.PROGRESSION_PREVIEW &&
        required.protocolVersion == EngineProtocol.VERSION &&
        sha256.matches(required.canonicalSourceDigest) &&
        sha256.matches(required.fixtureDigest)
}

interface ProgressionBackend {
    val implementation: EngineImplementation
    val certification: FeatureCertification?
    suspend fun preview(input: ProgressionPreviewInput): Result<ProgressionPreviewResult>
}

/**
 * Fail closed if the chosen backend has not been independently certified.
 * Do NOT silently fall back after an execution error. A future stateful write
 * must never replay itself on a second backend.
 */
class ParityGatedProgressionRouter(
    private val gate: VerifiedFeatureGate,
    private val canonical: ProgressionBackend?,
    private val nativeKotlin: ProgressionBackend?
) {
    fun selectedBackend(): ProgressionBackend? {
        val kotlin = nativeKotlin
        if (kotlin != null && kotlin.implementation == EngineImplementation.NATIVE_KOTLIN &&
            gate.permits(kotlin.implementation, kotlin.certification)) return kotlin
        val js = canonical
        if (js != null && js.implementation == EngineImplementation.CANONICAL_JS &&
            gate.permits(js.implementation, js.certification)) return js
        return null
    }

    suspend fun preview(input: ProgressionPreviewInput): Result<ProgressionPreviewResult> {
        if (!input.hasValidShape())
            return Result.failure(IllegalArgumentException("Invalid progression preview request"))
        val backend = selectedBackend()
            ?: return Result.failure(IllegalStateException("No parity-certified progression backend"))
        return backend.preview(input)
    }
}
