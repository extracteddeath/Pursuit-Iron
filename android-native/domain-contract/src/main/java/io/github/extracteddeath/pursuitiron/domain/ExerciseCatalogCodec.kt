package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

/** A narrow immutable projection; not a program, workout or custom exercise record. */
data class CanonicalExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val type: String,
    val equipment: List<String>,
    val repMin: Int,
    val repMax: Int,
    val movementPattern: String?
)

data class ExerciseLookupResult(
    val source: String,
    val readOnly: Boolean,
    val exercises: List<CanonicalExercise>,
    val missingIds: List<String>
)

object ExerciseCatalogCodec {
    const val SOURCE = "modules/engine-api.js:EXERCISES"
    private val idPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$")
    private val equipmentPattern = Regex("^[a-z0-9_-]{1,40}$")

    fun validateIds(ids: List<String>): Boolean =
        ids.size in 1..12 && ids.distinct().size == ids.size &&
            ids.all { idPattern.matches(it) }

    fun encodeLookup(id: String, exerciseIds: List<String>): String {
        require(id.isNotEmpty() && id.length <= 128)
        require(validateIds(exerciseIds)) { "Invalid canonical exercise IDs" }
        return JSONObject()
            .put("protocolVersion", EngineProtocol.VERSION)
            .put("id", id)
            .put("method", EngineProtocol.METHOD_EXERCISE_LOOKUP)
            .put("params", JSONObject().put("ids", JSONArray(exerciseIds)))
            .toString()
    }

    private fun string(obj: JSONObject, key: String, max: Int): String {
        val raw = obj.get(key)
        require(raw is String && raw.isNotEmpty() && raw.length <= max) {
            "Invalid catalog field: $key"
        }
        return raw
    }

    private fun stringArray(obj: JSONObject, key: String, max: Int): List<String> {
        val array = obj.getJSONArray(key)
        require(array.length() <= max)
        return (0 until array.length()).map { index ->
            val raw = array.get(index)
            require(raw is String && equipmentPattern.matches(raw)) { "Invalid $key" }
            raw
        }
    }

    fun decodeLookup(
        responseJson: String, requestId: String, requestedIds: List<String>
    ): EngineResponse<ExerciseLookupResult> {
        require(responseJson.length <= 65536)
        require(requestId.isNotEmpty() && requestId.length <= 128)
        require(validateIds(requestedIds))
        val envelope = JSONObject(responseJson)
        require(envelope.getInt("protocolVersion") == EngineProtocol.VERSION)
        require(string(envelope,"id",128) == requestId)
        val ok = envelope.get("ok")
        require(ok is Boolean)
        if (!ok) {
            val error = envelope.getJSONObject("error")
            return EngineResponse.Failure(requestId, EngineError(
                string(error,"code",64), string(error,"message",512)
            ))
        }
        val result = envelope.getJSONObject("result")
        require(string(result, "source", 160) == SOURCE)
        require(result.get("readOnly") is Boolean && result.getBoolean("readOnly")) { "Unapproved writable catalog result" }
        val records = result.getJSONArray("exercises")
        require(records.length() <= requestedIds.size)
        val exercises = (0 until records.length()).map { i ->
            val ex = records.getJSONObject(i)
            val id = string(ex,"id",80)
            require(idPattern.matches(id))
            val kind = string(ex,"type",32)
            require(kind == "compound" || kind == "isolation")
            fun strictRep(key: String): Int {
                val raw = ex.get(key)
                require(raw is Number && raw.toDouble().isFinite())
                val value = raw.toDouble()
                require(value in 1.0..100.0 && value == value.toInt().toDouble())
                return value.toInt()
            }
            val lower = strictRep("repMin")
            val upper = strictRep("repMax")
            require(upper >= lower)
            val equipment = stringArray(ex,"equipment",20)
            val pattern = ex.get("pattern").let { raw ->
                if (raw === JSONObject.NULL) null
                else {
                    require(raw is String && raw.length in 1..80)
                    raw
                }
            }
            CanonicalExercise(
                id = id,
                name = string(ex,"name",120),
                muscleGroup = string(ex,"part",48),
                type = kind,
                equipment = equipment,
                repMin = lower,
                repMax = upper,
                movementPattern = pattern
            )
        }
        val missing = result.getJSONArray("missingIds").let { array ->
            require(array.length() <= requestedIds.size)
            (0 until array.length()).map { i ->
                val raw = array.get(i)
                require(raw is String && idPattern.matches(raw))
                raw
            }
        }
        val returnedIds = exercises.map { it.id } + missing
        require(returnedIds.distinct().size == returnedIds.size)
        require(returnedIds.size == requestedIds.size)
        require(returnedIds.toSet() == requestedIds.toSet()) {
            "Catalog lookup returned unrequested or omitted IDs"
        }
        // Relative order of found and missing requests must be unchanged.
        require(exercises.map { requestedIds.indexOf(it.id) }
            .zipWithNext().all { (a,b) -> a < b })
        require(missing.map { requestedIds.indexOf(it) }
            .zipWithNext().all { (a,b) -> a < b })
        return EngineResponse.Success(
            requestId, ExerciseLookupResult(SOURCE, true, exercises, missing)
        )
    }
}
