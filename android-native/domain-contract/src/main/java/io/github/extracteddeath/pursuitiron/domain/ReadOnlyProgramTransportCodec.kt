package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject

/**
 * Pure read-only summary DTO. This is NOT a program, a prescription, an
 * importer, or permission to access the athlete's data from a native screen.
 */
data class ReadOnlyProgramDay(
    val id: String,
    val label: String,
    val exerciseIds: List<String>
)

data class ReadOnlyProgramSummary(
    val reference: ReadOnlyProgramReference,
    val id: String,
    val name: String,
    val custom: Boolean,
    val goal: String,
    val weeks: Int,
    val days: List<ReadOnlyProgramDay>
)

/**
 * Decodes ONLY the canonical JS projection from an exact pinned reference.
 * The caller still must verify the current snapshot and tombstone independently
 * before allowing personal data anywhere near Compose. No bridge to UI exists.
 */
object ReadOnlyProgramTransportCodec {
    private const val SOURCE = "modules/engine-api.js:parseStoredData"
    private const val MAX_BYTES = 65_536
    private const val MAX_JS_INTEGER = 9_007_199_254_740_991L
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")

    private fun exactKeys(value: JSONObject, vararg expected: String) {
        require(value.keys().asSequence().toSet() == expected.toSet()) {
            "Unexpected read-only program field"
        }
    }

    private fun string(value: JSONObject, key: String, max: Int, identifier: Boolean = false): String {
        val raw = value.get(key)
        require(raw is String && raw.isNotBlank() && raw.length <= max &&
            raw.none { it.isISOControl() } &&
            (!identifier || idPattern.matches(raw))) { "Invalid program field: $key" }
        return raw
    }

    private fun wholeNumber(value: JSONObject, key: String, max: Long): Long {
        val number = value.get(key)
        require(number is Number) { "Invalid integer field: $key" }
        // JSONObject.getLong and Number.toLong silently coerce strings,
        // fractional values, scientific notation and oversized JavaScript clocks.
        val literal = number.toString()
        require(Regex("^(0|[1-9][0-9]*)$").matches(literal))
        val exact = literal.toLongOrNull()
        require(exact != null && exact <= max && exact <= MAX_JS_INTEGER)
        return exact
    }

    fun decode(wire: String, expectedReference: ReadOnlyProgramReference): ReadOnlyProgramSummary {
        require(wire.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) {
            "Unbounded read-only program payload"
        }
        val envelope = JSONObject(wire)
        exactKeys(envelope, "protocolVersion", "source", "readOnly", "reference", "program")
        require(wholeNumber(envelope, "protocolVersion", 1) == 1L)
        require(string(envelope, "source", 160) == SOURCE)
        require(envelope.get("readOnly") === true)

        val reference = envelope.getJSONObject("reference")
        exactKeys(reference, "programId", "programUpdatedAt", "storeSavedAt",
            "storeVersion", "snapshotSha256")
        val candidate = ReadOnlyProgramReference(
            programId = string(reference, "programId", 128, identifier = true),
            programUpdatedAt = wholeNumber(reference, "programUpdatedAt", MAX_JS_INTEGER),
            storeSavedAt = wholeNumber(reference, "storeSavedAt", MAX_JS_INTEGER),
            storeVersion = wholeNumber(reference, "storeVersion", 13).toInt(),
            snapshotSha256 = string(reference, "snapshotSha256", 64)
        )
        require(ReadOnlyProgramReferenceGate.sameActiveRevision(expectedReference, candidate, null)) {
            "Program revision does not match expected canonical snapshot"
        }

        val program = envelope.getJSONObject("program")
        exactKeys(program, "id", "name", "custom", "goal", "weeks", "days")
        val id = string(program, "id", 128, identifier = true)
        require(id == candidate.programId)
        val custom = program.get("custom")
        require(custom is Boolean)
        val weeks = wholeNumber(program, "weeks", 104).toInt()
        require(weeks >= 1)
        val jsonDays = program.getJSONArray("days")
        require(jsonDays.length() in 1..7)
        val dayIds = mutableSetOf<String>()
        val days = (0 until jsonDays.length()).map { index ->
            val day = jsonDays.getJSONObject(index)
            exactKeys(day, "id", "label", "exerciseIds")
            val dayId = string(day, "id", 128, identifier = true)
            require(dayIds.add(dayId)) { "Duplicate saved-program training day" }
            val exercises = day.getJSONArray("exerciseIds")
            require(exercises.length() <= 30)
            val ids = (0 until exercises.length()).map { exIndex ->
                val exerciseId = exercises.get(exIndex)
                require(exerciseId is String && exerciseId.length <= 80 &&
                    idPattern.matches(exerciseId))
                exerciseId
            }
            ReadOnlyProgramDay(
                id = dayId,
                label = string(day, "label", 120),
                exerciseIds = ids
            )
        }
        return ReadOnlyProgramSummary(
            reference = candidate,
            id = id,
            name = string(program, "name", 120),
            custom = custom,
            goal = string(program, "goal", 40),
            weeks = weeks,
            days = days
        )
    }
}
