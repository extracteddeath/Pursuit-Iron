package io.github.extracteddeath.pursuitiron.domain

/**
 * The wire schema is versioned independently of its implementation language.
 * The Android-only typed JSON codec can validate read-only responses; native JS runtime remains unconnected.
 * Kotlin must not invent engine decisions or serialize params as Map<String, String>.
 */
object EngineProtocol {
    const val VERSION: Int = 1
    const val METHOD_HANDSHAKE: String = "handshake"
    const val METHOD_CATALOG_SUMMARY: String = "catalogSummary"
    const val METHOD_EXERCISE_LOOKUP: String = "exerciseLookup"
    const val METHOD_WORKOUT_OUTLINE: String = "workoutOutline"
    const val METHOD_BACKUP_SCHEMA_CHECK: String = "backupSchemaCheck"
    const val METHOD_BACKUP_PROGRAM_INVENTORY: String = "backupProgramInventory"
    const val METHOD_PROGRESSION_PREVIEW: String = "progressionPreview"
}

data class ObservedWorkingSet(
    val load: Double,
    val reps: Int,
    val rir: Int?
)

data class ProgressionPreviewInput(
    val exerciseId: String,
    val repMin: Int,
    val repMax: Int,
    val targetRir: Int,
    val sets: List<ObservedWorkingSet>,
    val equipmentAvailable: List<String>
) {
    /**
     * Exactly the same bounds as the read-only JS bridge.
     * This validates transport values; it does not implement progression.
     */
    fun hasValidShape(): Boolean =
        Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$").matches(exerciseId) &&
        repMin in 1..100 && repMax in repMin..100 && targetRir in 0..10 &&
        sets.size in 1..8 &&
        sets.all { it.load.isFinite() && it.load in 0.0..2500.0 &&
            it.reps in 0..100 && (it.rir == null || it.rir in 0..10) } &&
        equipmentAvailable.size <= 32 &&
        equipmentAvailable.all { Regex("^[a-z0-9_-]{1,40}$").matches(it) } &&
        equipmentAvailable.size == equipmentAvailable.toSet().size
}

sealed interface EngineParams {
    data object Empty : EngineParams
    data class ProgressionPreview(val input: ProgressionPreviewInput) : EngineParams
}

data class EngineRequest(
    val protocolVersion: Int = EngineProtocol.VERSION,
    val id: String,
    val method: String,
    val params: EngineParams = EngineParams.Empty
)

data class EngineError(val code: String, val message: String)

sealed interface EngineResponse<out T> {
    data class Success<T>(val id: String, val result: T) : EngineResponse<T>
    data class Failure(val id: String?, val error: EngineError) : EngineResponse<Nothing>
}

data class EngineHandshake(
    val protocolVersion: Int,
    val engineSource: String,
    val engineCertified: Boolean,
    val capabilities: List<String>
)

data class ProgressionSetTarget(val weight: Double?, val reps: Int?)

data class ProgressionPreviewResult(
    val source: String,
    val previewOnly: Boolean,
    val action: String,
    val suggestedLoad: Double?,
    val suggestedReps: Int?,
    val reasonCode: String?,
    val confidence: String?,
    val setTargets: List<ProgressionSetTarget>
)
