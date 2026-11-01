package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Strict transport for pure canonical store preparation; never grants live training. */
object WorkoutStoreRuntimeCodec {
    const val MAX_SNAPSHOT_BYTES=1024*1024
    const val MAX_JOURNAL_BYTES=256*1024
    const val MAX_REQUEST_BYTES=12*1024*1024
    const val MAX_RESPONSE_BYTES=8*1024*1024
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    internal fun exact(v: JSONObject,vararg keys: String) {
        require(v.keys().asSequence().toSet()==keys.toSet())
    }
    internal fun bounded(raw: String,max: Int) {
        require(raw.isNotEmpty() && raw.length<=max && !raw.startsWith('\uFEFF'))
        val bytes=Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(raw))
        try { require(bytes.remaining()<=max) }
        finally { if (bytes.hasArray()) bytes.array().fill(0) }
    }
    internal fun text(v: JSONObject,key: String): String {
        val raw=v.get(key); require(raw is String);return raw
    }
    internal fun integer(v: JSONObject,key: String): Long {
        val n=v.get(key)
        require(n is Number && n.toString().matches(Regex("^(0|[1-9][0-9]*)$")))
        return requireNotNull(n.toString().toLongOrNull())
    }
    internal fun sha(raw: String): String {
        val bytes=raw.toByteArray(Charsets.UTF_8)
        try {
            val alphabet="0123456789abcdef"
            return buildString(64) { MessageDigest.getInstance("SHA-256").digest(bytes).forEach {
                val n=it.toInt() and 255;append(alphabet[n ushr 4]);append(alphabet[n and 15])
            } }
        } finally { bytes.fill(0) }
    }
    internal fun encode(id: String,method: String,params: JSONObject): String {
        require(identifier.matches(id))
        val wire=JSONObject().put("protocolVersion",1).put("id",id)
            .put("method",method).put("params",params).toString()
        checkedRequest(wire)
        return wire
    }
    fun encodeValidation(id: String,source: String)=encode(id,"validateOwnedStore",
        JSONObject().put("rawSnapshot",source))
    fun encodePreparation(id: String,origin: String,current: String,journal: String)=
        encode(id,"prepareWorkoutHistory",JSONObject().put("originSnapshot",origin)
            .put("currentSnapshot",current).put("finishedJournal",journal))

    /** Called before a Java sandbox worker or its process lease is touched. */
    @JvmStatic fun checkedRequest(json: String): JSONObject {
        bounded(json,MAX_REQUEST_BYTES)
        val request=JSONObject(json)
        exact(request,"protocolVersion","id","method","params")
        require(integer(request,"protocolVersion")==1L && identifier.matches(text(request,"id")))
        val p=request.getJSONObject("params")
        when (text(request,"method")) {
            "inspectNativeHistory" -> { exact(p,"rawSnapshot","historyId");bounded(text(p,"rawSnapshot"),MAX_SNAPSHOT_BYTES)
                require(identifier.matches(text(p,"historyId"))) }
            "prepareNativeHistoryCorrection" -> { exact(p,"currentSnapshot","correctionJson")
                bounded(text(p,"currentSnapshot"),MAX_SNAPSHOT_BYTES);bounded(text(p,"correctionJson"),16384) }
            "prepareNativeWorkout" -> { exact(p,"rawSnapshot","startJson");bounded(text(p,"rawSnapshot"),MAX_SNAPSHOT_BYTES);bounded(text(p,"startJson"),16384) }
            "nativeProgramOptions" -> exact(p)
            "prepareNativeProgram" -> { exact(p,"setupJson");bounded(text(p,"setupJson"),16384) }
            "inspectNativeProgram" -> { exact(p,"rawSnapshot");bounded(text(p,"rawSnapshot"),MAX_SNAPSHOT_BYTES) }
            "validateOwnedStore" -> {
                exact(p,"rawSnapshot");bounded(text(p,"rawSnapshot"),MAX_SNAPSHOT_BYTES)
            }
            "prepareWorkoutHistory", "prepareIssuedWorkoutHistory" -> {
                if(text(request,"method")=="prepareIssuedWorkoutHistory") {
                    exact(p,"originSnapshot","currentSnapshot","finishedJournal","issuedWorkout")
                    bounded(text(p,"issuedWorkout"),NativeWorkoutCodec.MAX_ISSUED_BYTES)
                } else exact(p,"originSnapshot","currentSnapshot","finishedJournal")
                bounded(text(p,"originSnapshot"),MAX_SNAPSHOT_BYTES)
                bounded(text(p,"currentSnapshot"),MAX_SNAPSHOT_BYTES)
                bounded(text(p,"finishedJournal"),MAX_JOURNAL_BYTES)
            }
            else -> throw IllegalArgumentException("Workout-store method required")
        }
        return request
    }
    internal fun result(raw: String,expectedId: String): JSONObject? {
        require(identifier.matches(expectedId));bounded(raw,MAX_RESPONSE_BYTES)
        val response=JSONObject(raw)
        val ok=response.get("ok");require(ok is Boolean)
        exact(response,"protocolVersion","id","ok",if(ok) "result" else "error")
        require(integer(response,"protocolVersion")==1L && text(response,"id")==expectedId)
        if (!ok) {
            val error=response.getJSONObject("error");exact(error,"code","message")
            require(text(error,"code").length in 1..64 && text(error,"message").length in 1..512)
            return null
        }
        return response.getJSONObject("result")
    }
    fun decodeValidation(raw: String,id: String,source: String): Boolean {
        bounded(source,MAX_SNAPSHOT_BYTES)
        val value=result(raw,id) ?: return false
        exact(value,"source","validated","storeVersion","snapshotSha256","approvedForWorkoutExecution")
        require(value.get("source")=="modules/engine-api.js:parseStoredData" &&
            value.get("validated")==true && integer(value,"storeVersion")==13L &&
            value.get("approvedForWorkoutExecution")==false && text(value,"snapshotSha256")==sha(source))
        return true
    }
    fun decodePreparation(raw: String,id: String,origin: String,current: String,journal: String): String? {
        bounded(origin,MAX_SNAPSHOT_BYTES);bounded(current,MAX_SNAPSHOT_BYTES);bounded(journal,MAX_JOURNAL_BYTES)
        val value=result(raw,id) ?: return null
        exact(value,"source","preparedOnly","persisted","approvedForWorkoutExecution",
            "sourceSnapshotSha256","inputHandoffSha256","expectedSnapshotSha256",
            "candidateSnapshotSha256","candidateSnapshot","alreadyPresent","historyEntry")
        require(value.get("source")=="modules/engine-api.js:loggedWorkoutPerformance+mergeStores" &&
            value.get("preparedOnly")==true && value.get("persisted")==false &&
            value.get("approvedForWorkoutExecution")==false && value.get("alreadyPresent") is Boolean)
        require(text(value,"sourceSnapshotSha256")==sha(origin) &&
            text(value,"inputHandoffSha256")==sha(journal) &&
            text(value,"expectedSnapshotSha256")==sha(current))
        val candidate=text(value,"candidateSnapshot");bounded(candidate,MAX_SNAPSHOT_BYTES)
        require(text(value,"candidateSnapshotSha256")==sha(candidate))
        if (value.getBoolean("alreadyPresent")) require(candidate==current)
        value.getJSONObject("historyEntry")
        // The transaction owner independently checks session/receipt identity
        // immediately before its atomic expected-snapshot publication.
        return value.toString()
    }
}
