package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Parse-result metadata only; never contains an individual user record,
 * source bytes, active program reference or native import authorization.
 */
data class ReadOnlyBackupSchemaResult(
    val storeVersion: Int,
    val counts: Map<String, Int>
)

object ReadOnlyBackupSchemaCodec {
    private val collections = listOf("saved", "history", "cycles", "custom", "gyms")
    private const val SOURCE = "modules/engine-api.js:parseStoredData"
    private fun keys(obj: JSONObject, vararg names: String) {
        require(obj.keys().asSequence().toSet() == names.toSet())
    }
    private fun number(obj: JSONObject, key: String, max: Int): Int {
        val value = obj.get(key)
        require(value is Number && value.toString().matches(Regex("^(0|[1-9][0-9]*)$")))
        val count = value.toString().toIntOrNull()
        require(count != null && count in 0..max)
        return count
    }
    fun encode(id: String, rawSnapshot: String): String {
        require(id.isNotBlank() && id.length <= 128)
        require(rawSnapshot.isNotEmpty() &&
            rawSnapshot.length <= ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS &&
            rawSnapshot.toByteArray(StandardCharsets.UTF_8).size <=
                ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
        val wire = JSONObject()
            .put("protocolVersion", EngineProtocol.VERSION)
            .put("id", id)
            .put("method", EngineProtocol.METHOD_BACKUP_SCHEMA_CHECK)
            .put("params", JSONObject().put("rawSnapshot",rawSnapshot))
            .toString()
        require(wire.toByteArray(StandardCharsets.UTF_8).size <= ReadOnlyBackupLimits.MAX_WIRE_BYTES)
        return wire
    }

    fun decode(json: String, expectedId: String): EngineResponse<ReadOnlyBackupSchemaResult> {
        require(json.toByteArray(StandardCharsets.UTF_8).size in 1..65536)
        require(expectedId.isNotBlank() && expectedId.length <= 128)
        val root = JSONObject(json)
        require(number(root, "protocolVersion", 1) == EngineProtocol.VERSION)
        require(root.get("id") === expectedId || root.getString("id") == expectedId)
        val ok = root.get("ok")
        require(ok is Boolean)
        if (!ok) {
            keys(root,"protocolVersion","id","ok","error")
            val error = root.getJSONObject("error")
            keys(error,"code","message")
            val code = error.get("code")
            val message = error.get("message")
            require(code is String && code.length in 1..64)
            require(message is String && message.length in 1..512)
            return EngineResponse.Failure(expectedId,EngineError(code,message))
        }
        keys(root,"protocolVersion","id","ok","result")
        val result = root.getJSONObject("result")
        keys(result,"source","readOnly","validation","imported",
            "approvedForWorkoutExecution","storeVersion","counts")
        require(result.get("source") == SOURCE &&
            result.get("validation") == "canonical-v13-structure-only" &&
            result.get("readOnly") === true &&
            result.get("imported") === false &&
            result.get("approvedForWorkoutExecution") === false &&
            number(result,"storeVersion",13) == 13)
        val counts = result.getJSONObject("counts")
        require(counts.keys().asSequence().toSet() == collections.toSet())
        val values = collections.associateWith { number(counts,it,100000) }
        return EngineResponse.Success(expectedId,ReadOnlyBackupSchemaResult(13,values))
    }
}
