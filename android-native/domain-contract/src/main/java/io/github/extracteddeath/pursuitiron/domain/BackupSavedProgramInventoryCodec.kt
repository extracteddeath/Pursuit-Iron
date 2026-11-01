package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject

/** Backup-only view; never a live Plan or an active-revision certificate. */
data class BackupSavedProgramEntry(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val trainingDays: Int,
    val exerciseSlots: Int
)

data class BackupSavedProgramInventory(
    val storeSavedAt: Long,
    val entries: List<BackupSavedProgramEntry>
)

object BackupSavedProgramInventoryCodec {
    private const val SOURCE = "modules/engine-api.js:parseStoredData"
    private val validId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private const val MAX_SAFE_CLOCK = 9007199254740991L

    private fun exact(value: JSONObject, vararg fields: String) {
        require(value.keys().asSequence().toSet() == fields.toSet()) {
            "Unexpected backup program field"
        }
    }
    private fun number(value: JSONObject, key: String, max: Long): Long {
        val source = value.get(key)
        require(source is Number && source.toString().matches(Regex("^(0|[1-9][0-9]*)$")))
        val parsed = source.toString().toLongOrNull()
        require(parsed != null && parsed in 0..max)
        return parsed
    }

    fun encode(id: String, exactSnapshot: String): String {
        val base = ReadOnlyBackupSchemaCodec.encode(id, exactSnapshot)
        return JSONObject(base)
            .put("method",EngineProtocol.METHOD_BACKUP_PROGRAM_INVENTORY).toString()
    }

    fun decode(wire: String, expectedId: String): EngineResponse<BackupSavedProgramInventory> {
        require(wire.toByteArray(Charsets.UTF_8).size in 1..65536)
        require(expectedId.isNotBlank() && expectedId.length <= 128)
        val envelope=JSONObject(wire)
        require(number(envelope,"protocolVersion",1) == 1L)
        require(envelope.get("id") == expectedId)
        require(envelope.get("ok") is Boolean)
        if (!envelope.getBoolean("ok")) {
            exact(envelope,"protocolVersion","id","ok","error")
            val error=envelope.getJSONObject("error")
            exact(error,"code","message")
            val code=error.get("code")
            val message=error.get("message")
            require(code is String && code.length in 1..64)
            require(message is String && message.length in 1..512)
            return EngineResponse.Failure(expectedId,EngineError(code,message))
        }
        exact(envelope,"protocolVersion","id","ok","result")
        val result=envelope.getJSONObject("result")
        exact(result,"source","readOnly","validation","snapshotOnly","imported",
            "approvedForWorkoutExecution","storeVersion","storeSavedAt","programs")
        require(result.get("source") == SOURCE &&
            result.get("validation") == "canonical-v13-backup-program-inventory" &&
            result.get("readOnly") === true &&
            result.get("snapshotOnly") === true &&
            result.get("imported") === false &&
            result.get("approvedForWorkoutExecution") === false &&
            number(result,"storeVersion",13) == 13L)
        val savedAt=number(result,"storeSavedAt",MAX_SAFE_CLOCK)
        val programs=result.getJSONArray("programs")
        require(programs.length() <= 24)
        val seen=mutableSetOf<String>()
        val entries=(0 until programs.length()).map { idx ->
            val row=programs.getJSONObject(idx)
            exact(row,"id","name","updatedAt","trainingDays","exerciseSlots")
            val id=row.get("id")
            val name=row.get("name")
            require(id is String && validId.matches(id) && seen.add(id))
            require(name is String && name.isNotBlank() && name.length <= 120 &&
                name.none { it.isISOControl() })
            val days=number(row,"trainingDays",7).toInt()
            require(days in 1..7)
            BackupSavedProgramEntry(
                id=id,name=name,
                updatedAt=number(row,"updatedAt",MAX_SAFE_CLOCK),
                trainingDays=days,
                exerciseSlots=number(row,"exerciseSlots",210).toInt()
            )
        }
        return EngineResponse.Success(expectedId,BackupSavedProgramInventory(savedAt,entries))
    }
}
