package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject

/** Synthetic audited session snapshot. No user-selected loads or logging state. */
data class WorkoutOutlineExercise(
    val exerciseId: String,
    val name: String,
    val role: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val rirMin: Int,
    val rirMax: Int,
    val restSeconds: Double,
    val progressionStyle: String
)

data class WorkoutOutline(
    val source: String,
    val readOnly: Boolean,
    val syntheticOnly: Boolean,
    val split: String,
    val dayIndex: Int,
    val sessionId: String,
    val day: String,
    val estimatedMinutes: Double,
    val maxMinutes: Double,
    val exercises: List<WorkoutOutlineExercise>
)

object WorkoutOutlineCodec {
    const val SOURCE = "modules/engine-api.js:generateNextProgramForShell"
    private val splits = mapOf("full_body" to 3, "upper_lower" to 4, "ulppl" to 5)
    private val idPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$")

    fun validChoice(split: String, dayIndex: Int): Boolean =
        splits[split]?.let { dayIndex in 0 until it } ?: false

    fun encodeOutline(requestId: String, split: String, dayIndex: Int): String {
        require(requestId.isNotEmpty() && requestId.length <= 128)
        require(validChoice(split, dayIndex))
        return JSONObject()
            .put("protocolVersion", EngineProtocol.VERSION)
            .put("id", requestId)
            .put("method", EngineProtocol.METHOD_WORKOUT_OUTLINE)
            .put("params", JSONObject().put("split", split).put("dayIndex", dayIndex))
            .toString()
    }

    private fun text(obj: JSONObject, key: String, max: Int): String {
        val value = obj.get(key)
        require(value is String && value.isNotEmpty() && value.length <= max) {
            "Invalid outline $key"
        }
        return value
    }

    private fun finiteNumber(obj: JSONObject, key: String, max: Double): Double {
        val raw = obj.get(key)
        require(raw is Number)
        val value = raw.toDouble()
        require(value.isFinite() && value in 0.0..max) { "Invalid outline $key" }
        return value
    }

    private fun strictInteger(obj: JSONObject, key: String, low: Int, high: Int): Int {
        val value = finiteNumber(obj,key,high.toDouble())
        require(value >= low && value == value.toInt().toDouble()) {
            "Invalid outline integer $key"
        }
        return value.toInt()
    }

    fun decodeOutline(
        json: String, requestId: String, expectedSplit: String, expectedDayIndex: Int
    ): EngineResponse<WorkoutOutline> {
        require(json.length <= 65536)
        require(requestId.isNotEmpty() && requestId.length <= 128)
        require(validChoice(expectedSplit,expectedDayIndex))
        val envelope = JSONObject(json)
        require(envelope.getInt("protocolVersion") == EngineProtocol.VERSION)
        require(text(envelope, "id", 128) == requestId)
        val ok = envelope.get("ok")
        require(ok is Boolean)
        if (!ok) {
            val err = envelope.getJSONObject("error")
            return EngineResponse.Failure(
                requestId, EngineError(text(err,"code",64),text(err,"message",512))
            )
        }
        val result = envelope.getJSONObject("result")
        require(text(result,"source",160) == SOURCE)
        require(result.get("readOnly") === true && result.get("syntheticOnly") === true)
        require(text(result,"split",40) == expectedSplit)
        require(strictInteger(result,"dayIndex",0,6) == expectedDayIndex)
        val exercisesArray = result.getJSONArray("exercises")
        require(exercisesArray.length() in 1..24)
        val exercises = (0 until exercisesArray.length()).map { index ->
            val ex = exercisesArray.getJSONObject(index)
            val id = text(ex,"exerciseId",80)
            require(idPattern.matches(id))
            val repsLow = strictInteger(ex,"repMin",1,100)
            val repsHigh = strictInteger(ex,"repMax",repsLow,100)
            val rirLow = strictInteger(ex,"rirMin",0,10)
            val rirHigh = strictInteger(ex,"rirMax",rirLow,10)
            WorkoutOutlineExercise(
                exerciseId = id,
                name = text(ex,"name",120),
                role = text(ex,"role",60),
                sets = strictInteger(ex,"sets",1,20),
                repMin = repsLow,
                repMax = repsHigh,
                rirMin = rirLow,
                rirMax = rirHigh,
                restSeconds = finiteNumber(ex,"restSeconds",600.0),
                progressionStyle = text(ex,"progressionStyle",50)
            )
        }
        return EngineResponse.Success(
            requestId, WorkoutOutline(
                source = SOURCE,
                readOnly = true,
                syntheticOnly = true,
                split = expectedSplit,
                dayIndex = expectedDayIndex,
                sessionId = text(result,"sessionId",128),
                day = text(result,"day",40),
                estimatedMinutes = finiteNumber(result,"estimatedMinutes",600.0),
                maxMinutes = finiteNumber(result,"maxMinutes",600.0),
                exercises = exercises
            )
        )
    }
}
