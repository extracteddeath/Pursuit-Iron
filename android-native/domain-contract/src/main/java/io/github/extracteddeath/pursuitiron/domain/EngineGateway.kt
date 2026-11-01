package io.github.extracteddeath.pursuitiron.domain

/**
 * Kotlin UI boundary only. Canonical calculations stay in modules/engine-api.js.
 * Do not return plausible-looking prescriptions when the bridge is unavailable.
 */
sealed interface EngineStatus {
    data object NotConnected : EngineStatus
    data object Verifying : EngineStatus
    data class Ready(val canonicalEngineVersion: String, val fixtureDigest: String) : EngineStatus
    data class Unavailable(val reason: String) : EngineStatus
}

data class WorkoutSnapshot(
    val programId: String,
    val workoutId: String,
    val revision: Long,
    val title: String,
    val exerciseCount: Int
)

interface EngineGateway {
    val status: EngineStatus
    suspend fun currentWorkout(): Result<WorkoutSnapshot>
}

/** Prevents any training UI from pretending the native engine is connected. */
class UnconnectedEngineGateway : EngineGateway {
    override val status: EngineStatus = EngineStatus.NotConnected
    override suspend fun currentWorkout(): Result<WorkoutSnapshot> =
        Result.failure(IllegalStateException("Canonical engine bridge has not passed parity certification"))
}
