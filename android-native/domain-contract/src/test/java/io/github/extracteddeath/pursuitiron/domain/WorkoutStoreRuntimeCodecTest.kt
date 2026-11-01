package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Shared canonical JS result; no personal data or independent Kotlin engine. */
class WorkoutStoreRuntimeCodecTest {
    private val fixture: JSONObject by lazy {
        JSONObject(checkNotNull(javaClass.classLoader?.getResourceAsStream("finished-journal-golden.json"))
            .bufferedReader().use { it.readText() })
    }
    private val source get()=fixture.getString("rawSnapshot")
    private val handoff get()=fixture.getJSONObject("handoff").toString()
    private fun response(): JSONObject {
        val p=JSONObject(fixture.getJSONObject("preparation").toString())
        // JSONObject serializes key order differently; bind the actual request
        // string without changing the canonical semantic history identity.
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(handoff.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        p.put("inputHandoffSha256",hash)
        return JSONObject().put("protocolVersion",1).put("id","store-1").put("ok",true).put("result",p)
    }
    @Test fun completionIsBoundToExactCallerAndAllInputOutputBytes() {
        val request=WorkoutStoreRuntimeCodec.encodePreparation("store-1",source,source,handoff)
        assertEquals("prepareWorkoutHistory",WorkoutStoreRuntimeCodec.checkedRequest(request).getString("method"))
        assertNotNull(WorkoutStoreRuntimeCodec.decodePreparation(response().toString(),"store-1",source,source,handoff))
        for (key in listOf("sourceSnapshotSha256","inputHandoffSha256","expectedSnapshotSha256","candidateSnapshotSha256")) {
            val bad=response();bad.getJSONObject("result").put(key,"f".repeat(64))
            assertTrue(runCatching {
                WorkoutStoreRuntimeCodec.decodePreparation(bad.toString(),"store-1",source,source,handoff)
            }.isFailure)
        }
        for (edit in listOf<(JSONObject)->Unit>(
            { it.put("id","another-request") },{ it.put("protocolVersion",1.5) },
            { it.put("ok","true") },{ it.getJSONObject("result").put("persisted",true) },
            { it.getJSONObject("result").put("approvedForWorkoutExecution",true) },
            { it.getJSONObject("result").put("alreadyPresent",true) },
            { it.getJSONObject("result").put("unexpected",true) })) {
            val bad=response();edit(bad)
            assertTrue(runCatching {
                WorkoutStoreRuntimeCodec.decodePreparation(bad.toString(),"store-1",source,source,handoff)
            }.isFailure)
        }
    }
    @Test fun requestBoundsApplyToUtf8BytesAndRejectMalformedUnicodeBeforeSandbox() {
        for (bad in listOf("é".repeat(600000),"\uD800","\uFEFF{}", ""))
            assertTrue(runCatching { WorkoutStoreRuntimeCodec.encodeValidation("store-1",bad) }.isFailure)
        val valid=JSONObject(WorkoutStoreRuntimeCodec.encodeValidation("store-1",source))
        assertThrows(Exception::class.java) { WorkoutStoreRuntimeCodec.checkedRequest(
            JSONObject(valid.toString()).put("method","deleteHistory").toString()) }
        valid.getJSONObject("params").put("hiddenWrite",true)
        assertThrows(Exception::class.java) { WorkoutStoreRuntimeCodec.checkedRequest(valid.toString()) }
        val error=JSONObject().put("protocolVersion",1).put("id","store-1").put("ok",false)
            .put("error",JSONObject().put("code","PREPARATION_REJECTED").put("message","Unavailable"))
        assertNull(WorkoutStoreRuntimeCodec.decodePreparation(error.toString(),"store-1",source,source,handoff))
    }
}
