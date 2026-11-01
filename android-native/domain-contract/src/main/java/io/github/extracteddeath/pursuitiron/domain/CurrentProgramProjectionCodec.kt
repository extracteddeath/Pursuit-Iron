package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.security.MessageDigest

/**
 * One bounded read-only response decoder. The Java/JS sandbox must use this
 * exact protocol; the authenticated snapshot owner is a SEPARATE requirement.
 */
object CurrentProgramProjectionCodec {
    private const val MAX_RESPONSE_BYTES = 65_536
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")

    private fun sha256(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return buildString(64) {
            MessageDigest.getInstance("SHA-256").digest(bytes).forEach { byte ->
                val code = byte.toInt() and 255
                append(digits[code ushr 4])
                append(digits[code and 15])
            }
        }
    }

    fun encode(requestId: String, exactSnapshot: String, programId: String): String {
        require(requestId.isNotBlank() && requestId.length <= 128)
        require(id.matches(programId))
        val bytes = exactSnapshot.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
        require(exactSnapshot.length <= ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS)
        return JSONObject()
            .put("protocolVersion", EngineProtocol.VERSION)
            .put("id", requestId)
            .put("method", "currentProgramProjection")
            .put("params", JSONObject()
                .put("rawSnapshot", exactSnapshot)
                .put("programId", programId)
                .put("snapshotSha256", sha256(bytes)))
            .toString().also {
                require(it.toByteArray(Charsets.UTF_8).size <= ReadOnlyBackupLimits.MAX_WIRE_BYTES)
            }
    }

    fun decode(
        responseJson: String, requestId: String,
        exactSnapshot: String, requestedProgramId: String
    ): CanonicalCurrentPlanFrame? {
        require(requestId.isNotBlank() && requestId.length <= 128)
        require(id.matches(requestedProgramId))
        require(responseJson.toByteArray(Charsets.UTF_8).size in 1..MAX_RESPONSE_BYTES)
        val envelope = JSONObject(responseJson)
        require(envelope.keys().asSequence().toSet() ==
            setOf("protocolVersion", "id", "ok", if (envelope.optBoolean("ok",false)) "result" else "error"))
        require(envelope.get("protocolVersion") is Number &&
            envelope.getInt("protocolVersion") == EngineProtocol.VERSION)
        require(envelope.get("id") is String && envelope.getString("id") == requestId)
        require(envelope.get("ok") is Boolean)
        if (!envelope.getBoolean("ok")) return null
        val projected = envelope.getJSONObject("result")
        val ref = projected.getJSONObject("reference")
        val reference = ReadOnlyProgramReference(
            programId = ref.getString("programId"),
            programUpdatedAt = ref.getLong("programUpdatedAt"),
            storeSavedAt = ref.getLong("storeSavedAt"),
            storeVersion = ref.getInt("storeVersion"),
            snapshotSha256 = ref.getString("snapshotSha256")
        )
        // SHA covers the exact original UTF-8 text, not parsed/re-serialized JSON.
        val original = exactSnapshot.toByteArray(Charsets.UTF_8)
        require(original.isNotEmpty() && original.size <= ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
        require(reference.programId == requestedProgramId &&
            reference.snapshotSha256 == sha256(original) &&
            ReadOnlyProgramReferenceGate.sameActiveRevision(reference, reference, null))
        val json = projected.toString()
        val safe = ReadOnlyProgramTransportCodec.decode(json, reference)
        require(safe.reference == reference)
        // Explicit JS contract rejects any equal/newer tombstone. A different
        // current store revision is caught by the outer current-source gate.
        return CanonicalCurrentPlanFrame(
            reference, CurrentProgramDeletionEvidence.CheckedAbsent, json
        )
    }
}
