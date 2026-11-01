package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CurrentProgramProjectionCodecTest {
    private val fixture by lazy {
        val source = checkNotNull(javaClass.classLoader
            ?.getResourceAsStream("read-only-transport-golden.json"))
        JSONObject(source.bufferedReader().use { it.readText() })
    }
    private val exact get() = fixture.getString("rawSnapshot")
    private val reference get() = fixture.getJSONObject("wire").getJSONObject("reference")
    private val id get() = fixture.getString("selectedProgramId")

    private fun response(value: JSONObject = fixture.getJSONObject("wire")) = JSONObject()
        .put("protocolVersion", EngineProtocol.VERSION)
        .put("id", "current-test-1")
        .put("ok", true)
        .put("result", value)
        .toString()

    @Test fun encodePinsOriginalUTF8AndNeverRequestsWrites() {
        val wire = JSONObject(CurrentProgramProjectionCodec.encode("current-test-1", exact, id))
        assertEquals("currentProgramProjection", wire.getString("method"))
        val params = wire.getJSONObject("params")
        assertEquals(exact,params.getString("rawSnapshot"))
        assertEquals(id,params.getString("programId"))
        assertEquals(reference.getString("snapshotSha256"),params.getString("snapshotSha256"))
        assertEquals(3,params.length())
        assertFalse(wire.has("write"))
        assertEquals(1,wire.getInt("protocolVersion"))
    }

    @Test fun exactCanonicalGoldenResponseDecodesIntoReadOnlyFrame() {
        val frame = checkNotNull(CurrentProgramProjectionCodec.decode(
            response(),"current-test-1",exact,id))
        assertEquals(id,frame.reference.programId)
        assertEquals(reference.getString("snapshotSha256"),frame.reference.snapshotSha256)
        assertEquals(CurrentProgramDeletionEvidence.CheckedAbsent,frame.deletion)
        val summary = ReadOnlyProgramTransportCodec.decode(frame.transportJson,frame.reference)
        assertEquals("Synthetic full body",summary.name)
        assertEquals(listOf("bb-bench","cable-fly"),summary.days[0].exerciseIds)
    }

    @Test fun staleDigestMalformedTransportOrDifferentRequestRejects() {
        val changed = exact.replace("Synthetic full body","Edited name")
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(),"current-test-1",changed,id)
        }.isFailure)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(),"incorrect-request",exact,id)
        }.isFailure)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(),"current-test-1",exact,"other")
        }.isFailure)
        val extra = JSONObject(fixture.getJSONObject("wire").toString())
            .put("privateHistory", 123)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(extra),"current-test-1",exact,id)
        }.isFailure)
        val bad = JSONObject(fixture.getJSONObject("wire").toString())
        bad.getJSONObject("program").put("targetWeight",225)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(bad),"current-test-1",exact,id)
        }.isFailure)
        val badType = JSONObject(fixture.getJSONObject("wire").toString())
        badType.getJSONObject("reference").put("programUpdatedAt","42")
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(response(badType),"current-test-1",exact,id)
        }.isFailure)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.encode("bad","",id)
        }.isFailure)
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.encode("bad",exact,"bad id")
        }.isFailure)
    }

    @Test fun canonicalErrorAndOversizedTransportFailClosed() {
        val denied = JSONObject()
            .put("protocolVersion",EngineProtocol.VERSION)
            .put("id","current-test-1").put("ok",false)
            .put("error",JSONObject().put("code","SCHEMA_REJECTED")
                .put("message","Current program unavailable")).toString()
        assertNull(CurrentProgramProjectionCodec.decode(denied,"current-test-1",exact,id))
        assertTrue(runCatching {
            CurrentProgramProjectionCodec.decode(" ".repeat(65_537),
                "current-test-1",exact,id)
        }.isFailure)
    }
}
