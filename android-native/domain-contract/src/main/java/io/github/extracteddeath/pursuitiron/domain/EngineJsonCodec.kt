package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire-only JSON codec for the read-only diagnostic progression feature.
 * No engine calculations, default values, history writes or backend activation.
 * Invalid or partially typed results throw and therefore fail closed.
 */
object EngineJsonCodec {
    private const val SOURCE = "modules/engine-api.js:evaluateWorkoutProgression"
    private val actionPattern = Regex("^[a-z][a-z0-9_]{0,59}$")

    fun encodeProgressionPreview(id: String, input: ProgressionPreviewInput): String {
        require(id.isNotEmpty() && id.length <= 128) { "Invalid request id" }
        require(input.hasValidShape()) { "Invalid progression preview input" }
        val sets = JSONArray()
        input.sets.forEach { set ->
            sets.put(JSONObject()
                .put("load", set.load)
                .put("reps", set.reps)
                .put("rir", set.rir ?: JSONObject.NULL))
        }
        val equipment = JSONArray()
        input.equipmentAvailable.forEach { equipment.put(it) }
        val params = JSONObject()
            .put("exerciseId", input.exerciseId)
            .put("repMin", input.repMin)
            .put("repMax", input.repMax)
            .put("targetRir", input.targetRir)
            .put("sets", sets)
            .put("equipmentAvailable", equipment)
        return JSONObject()
            .put("protocolVersion", EngineProtocol.VERSION)
            .put("id", id)
            .put("method", EngineProtocol.METHOD_PROGRESSION_PREVIEW)
            .put("params", params)
            .toString()
    }

    private fun requiredString(obj: JSONObject, key: String, maximum: Int): String {
        val value = obj.get(key)
        require(value is String && value.isNotEmpty() && value.length <= maximum) {
            "Invalid engine field: $key"
        }
        return value
    }

    private fun nullableString(obj: JSONObject, key: String, maximum: Int): String? {
        val value = obj.get(key)
        if (value === JSONObject.NULL) return null
        require(value is String && value.length <= maximum) {
            "Invalid engine field: $key"
        }
        return value
    }

    private fun requiredBool(obj: JSONObject, key: String): Boolean {
        val value = obj.get(key)
        require(value is Boolean) { "Invalid engine field: $key" }
        return value
    }

    private fun nullableNumber(obj: JSONObject, key: String, max: Double): Double? {
        val value = obj.get(key)
        if (value === JSONObject.NULL) return null
        require(value is Number) { "Invalid engine field: $key" }
        val number = value.toDouble()
        require(number.isFinite() && number >= 0.0 && number <= max) {
            "Invalid engine field: $key"
        }
        return number
    }

    private fun nullableInt(obj: JSONObject, key: String, max: Int): Int? {
        val number = nullableNumber(obj, key, max.toDouble()) ?: return null
        require(number == number.toInt().toDouble()) { "Noninteger engine field: $key" }
        return number.toInt()
    }

    fun decodeProgressionPreview(
        json: String, expectedId: String
    ): EngineResponse<ProgressionPreviewResult> {
        require(json.length <= 65536) { "Oversized engine result" }
        require(expectedId.isNotEmpty() && expectedId.length <= 128)
        val response = JSONObject(json)
        require(response.getInt("protocolVersion") == EngineProtocol.VERSION)
        require(requiredString(response, "id", 128) == expectedId)
        if (!requiredBool(response, "ok")) {
            val error = response.getJSONObject("error")
            return EngineResponse.Failure(
                expectedId,
                EngineError(
                    requiredString(error, "code", 64),
                    requiredString(error, "message", 512)
                )
            )
        }
        val result = response.getJSONObject("result")
        require(requiredString(result, "source", 160) == SOURCE)
        require(requiredBool(result, "previewOnly")) { "Unapproved writable engine output" }
        val action = requiredString(result, "action", 60)
        require(actionPattern.matches(action)) { "Invalid engine action" }
        val setsArray = result.getJSONArray("setTargets")
        require(setsArray.length() <= 8) { "Oversized set targets" }
        val targets = (0 until setsArray.length()).map { i ->
            val set = setsArray.getJSONObject(i)
            ProgressionSetTarget(
                nullableNumber(set, "weight", 2500.0),
                nullableInt(set, "reps", 100)
            )
        }
        return EngineResponse.Success(
            expectedId,
            ProgressionPreviewResult(
                source = SOURCE,
                previewOnly = true,
                action = action,
                suggestedLoad = nullableNumber(result, "suggestedLoad", 2500.0),
                suggestedReps = nullableInt(result, "suggestedReps", 100),
                reasonCode = nullableString(result, "reasonCode", 120),
                confidence = nullableString(result, "confidence", 120),
                setTargets = targets
            )
        )
    }
}
