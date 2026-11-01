package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeShortSessionPreviewTest {
    private val source="{\"v\":13,\"savedAt\":100,\"saved\":[],\"history\":[],\"cycles\":[],\"custom\":[],\"gyms\":[],\"tombs\":{},\"perf\":{}}"
    private fun response(): JSONObject {
        val v=JSONObject().put("source","modules/engine-api.js:planShorterSession")
            .put("snapshotSha256",WorkoutStoreRuntimeCodec.sha(source))
            .put("programId","program-1").put("dayId","day-1").put("weekIndex",1)
            .put("minutes",30).put("originalMinutes",70).put("estimatedMinutes",27)
            .put("fits",true).put("changed",true)
            .put("readOnly",true).put("applied",false)
            .put("cuts",org.json.JSONArray().put(JSONObject().put("slot",3)
                .put("name","Cable fly").put("fromSets",4).put("toSets",2).put("amplifierRemoved",false)))
        return JSONObject().put("protocolVersion",1).put("id","preview-1")
            .put("ok",true).put("result",v)
    }
    @Test fun exactRequestAndResultAreBoundToSourceAndReadOnlyFlag() {
        val wire=NativeShortSessionPreviewCodec.encode("preview-1",source,"program-1",30)
        assertEquals("previewNativeShorterSession",WorkoutStoreRuntimeCodec.checkedRequest(wire).getString("method"))
        assertEquals(27,checkNotNull(NativeShortSessionPreviewCodec.decode(response().toString(),
            "preview-1",source,"program-1",30)).estimatedMinutes)
        for (edit in listOf<(JSONObject)->Unit>(
            {it.put("id","wrong")},
            {it.getJSONObject("result").put("snapshotSha256","f".repeat(64))},
            {it.getJSONObject("result").put("applied",true)},
            {it.getJSONObject("result").put("readOnly",false)},
            {it.getJSONObject("result").put("programId","another")},
            {it.getJSONObject("result").put("minutes",20)},
            {it.getJSONObject("result").put("fits",false)},
            {it.getJSONObject("result").put("unexpected",1)},
            {it.getJSONObject("result").getJSONArray("cuts").getJSONObject(0).put("toSets",5)})) {
            val r=response();edit(r)
            assertTrue(runCatching { NativeShortSessionPreviewCodec.decode(r.toString(),
                "preview-1",source,"program-1",30) }.isFailure)
        }
    }
    @Test fun noShorteningWriteOrInvalidDurationCanEnterWorker() {
        for(minutes in listOf(0,15,35,60))assertThrows(IllegalArgumentException::class.java) {
            NativeShortSessionPreviewCodec.encode("preview-1",source,"program-1",minutes)
        }
        val bad=JSONObject(NativeShortSessionPreviewCodec.encode("preview-1",source,"program-1",30))
        bad.getJSONObject("params").put("apply",true)
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutStoreRuntimeCodec.checkedRequest(bad.toString())
        }
    }
}
