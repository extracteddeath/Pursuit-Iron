package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * These tests prove transport validation and engine selection, NOT that a
 * native Kotlin training formula has passed canonical parity.
 */
class ProgressionContractTest {
    private val sample = ProgressionPreviewInput(
        exerciseId = "back_squat",
        repMin = 5,
        repMax = 8,
        targetRir = 2,
        sets = listOf(
            ObservedWorkingSet(200.0, 3, null),
            ObservedWorkingSet(205.0, 3, null),
            ObservedWorkingSet(210.0, 2, null),
            ObservedWorkingSet(215.0, 2, 0)
        ),
        equipmentAvailable = listOf("barbell", "rack")
    )

    @Test fun validatesShapeAndEquipmentPrecision() {
        assertTrue(sample.hasValidShape())
        assertFalse(sample.copy(exerciseId = "__proto__!").hasValidShape())
        assertFalse(sample.copy(repMax = 4).hasValidShape())
        assertFalse(sample.copy(targetRir = 11).hasValidShape())
        assertFalse(sample.copy(sets = emptyList()).hasValidShape())
        assertFalse(sample.copy(sets = List(9) { sample.sets.first() }).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(Double.NaN, 8, 2))).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(Double.POSITIVE_INFINITY, 8, 2))).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(-5.0, 8, 2))).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(2500.5, 8, 2))).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(200.0, -1, 2))).hasValidShape())
        assertFalse(sample.copy(sets = listOf(ObservedWorkingSet(200.0, 8, -1))).hasValidShape())
        assertFalse(sample.copy(equipmentAvailable = listOf("barbell", "barbell")).hasValidShape())
        assertTrue(sample.copy(sets = listOf(ObservedWorkingSet(38.5, 8, 2))).hasValidShape())
    }

    private val certification = FeatureCertification(
        EngineFeature.PROGRESSION_PREVIEW, EngineProtocol.VERSION,
        "a".repeat(64), "b".repeat(64)
    )

    private class StubBackend(
        override val implementation: EngineImplementation,
        override val certification: FeatureCertification?
    ) : ProgressionBackend {
        override suspend fun preview(input: ProgressionPreviewInput): Result<ProgressionPreviewResult> =
            Result.failure(IllegalStateException("No actual engine"))
    }

    @Test fun failClosedWithoutIndependentCertification() {
        val js = StubBackend(EngineImplementation.CANONICAL_JS, certification)
        val kotlin = StubBackend(EngineImplementation.NATIVE_KOTLIN, certification)
        val none = VerifiedFeatureGate(certification, emptySet())
        assertNull(ParityGatedProgressionRouter(none, js, kotlin).selectedBackend())
        assertNull(ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification, setOf(EngineImplementation.NATIVE_KOTLIN)),
            js, StubBackend(EngineImplementation.NATIVE_KOTLIN, certification.copy(fixtureDigest = "wrong"))
        ).selectedBackend())
        assertSame(js, ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification, setOf(EngineImplementation.CANONICAL_JS)),
            js, kotlin
        ).selectedBackend())
        assertSame(kotlin, ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification, EngineImplementation.entries.toSet()),
            js, kotlin
        ).selectedBackend())
        assertNull(ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification.copy(protocolVersion = 5), EngineImplementation.entries.toSet()),
            js, kotlin
        ).selectedBackend())
        assertNull(ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification.copy(canonicalSourceDigest = ""), EngineImplementation.entries.toSet()),
            js, kotlin
        ).selectedBackend())
        assertNull(ParityGatedProgressionRouter(
            VerifiedFeatureGate(certification.copy(fixtureDigest = "looks-like-a-digest"), EngineImplementation.entries.toSet()),
            js, kotlin
        ).selectedBackend())
    }
}
