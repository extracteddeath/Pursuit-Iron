package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** No real backups. These tests deliberately never exercise a persistence writer. */
class NativeBackupMergePreviewCodecTest {
    private val current = """{"v":13,"savedAt":10,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val incoming = """{"v":13,"savedAt":12,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private fun counts(history: Int) = JSONObject().put("saved",0).put("history",history)
        .put("cycles",0).put("custom",0).put("gyms",0)
    private fun result() = JSONObject()
        .put("source","modules/engine-api.js:mergeStores")
        .put("validation","canonical-v13-merge-preview")
        .put("readOnly",true).put("imported",false).put("approvedForImport",false)
        .put("approvedForWorkoutExecution",false).put("requiresExplicitApproval",true)
        .put("storeVersion",13)
        .put("currentSha256",WorkoutStoreRuntimeCodec.sha(current))
        .put("incomingSha256",WorkoutStoreRuntimeCodec.sha(incoming))
        .put("hypotheticalMergedSha256","a".repeat(64))
        .put("hypotheticalMergedBytes",1024)
        .put("counts",JSONObject().put("current",counts(1))
            .put("incoming",counts(2)).put("hypothetical",counts(2)))
        .put("conflicts",1).put("historyDropped",0)
        .put("flags",JSONArray().put("RECORD_CONFLICTS_REQUIRE_REVIEW"))
    private fun response(value: JSONObject = result()) = JSONObject()
        .put("protocolVersion",1).put("id","backup-1").put("ok",true)
        .put("result",value).toString()

    @Test fun transportChecksBothBoundedExactSources() {
        val request=NativeBackupMergePreviewCodec.encode("backup-1",current,incoming)
        val parsed=WorkoutStoreRuntimeCodec.checkedRequest(request)
        assertEquals("previewBackupMerge",parsed.getString("method"))
        assertEquals(current,parsed.getJSONObject("params").getString("currentSnapshot"))
        assertEquals(incoming,parsed.getJSONObject("params").getString("incomingSnapshot"))
        for(bad in listOf("", "\uFEFF{}", "\uD800", "é".repeat(600000))) {
            assertTrue(runCatching { NativeBackupMergePreviewCodec.encode("backup-1",current,bad) }.isFailure)
        }
        val forged=JSONObject(request).getJSONObject("params").put("publish",true)
        assertTrue(runCatching { WorkoutStoreRuntimeCodec.checkedRequest(
            JSONObject(request).put("params",forged).toString()) }.isFailure)
    }

    @Test fun redactedSummaryIsBoundToBothSourcesAndCannotAuthorizeImport() {
        val preview=NativeBackupMergePreviewCodec.decode(response(),"backup-1",current,incoming)
        assertNotNull(preview)
        assertEquals(1,preview!!.conflicts)
        assertEquals(2,preview.hypothetical.history)
        assertEquals(listOf("RECORD_CONFLICTS_REQUIRE_REVIEW"),preview.flags)
        assertTrue(preview.requiresIntervention)
        assertFalse(preview.toString().contains("candidateSnapshot"))
        assertTrue(runCatching {
            NativeBackupMergePreviewCodec.decode(response(),"backup-1",incoming,current)
        }.isFailure)
        for(mutate in listOf<(JSONObject)->Unit>(
            { it.put("approvedForImport",true) },
            { it.put("readOnly",false) },
            { it.put("imported",true) },
            { it.put("requiresExplicitApproval",false) },
            { it.put("approvedForWorkoutExecution",true) },
            { it.put("hypotheticalMergedSha256","f") },
            { it.put("incomingSha256","0".repeat(64)) },
            { it.put("storeVersion",12) },
            { it.put("extra","private data") },
            { it.put("historyDropped",1) },
            { it.put("hypotheticalMergedBytes",1024*1024+1) },
            { it.put("flags",JSONArray().put("RECORD_CONFLICTS_REQUIRE_REVIEW").put("RECORD_CONFLICTS_REQUIRE_REVIEW")) },
            { it.getJSONObject("counts").getJSONObject("current").put("secret","bad") }
        )) {
            val copy=JSONObject(result().toString()); mutate(copy)
            assertTrue(runCatching {
                NativeBackupMergePreviewCodec.decode(response(copy),"backup-1",current,incoming)
            }.isFailure)
        }
    }
    @Test fun anExplicitHistoryLossMustBeReportedAsRisk() {
        val withLoss=result().put("historyDropped",5)
            .put("flags",JSONArray().put("HISTORY_CAP_LOSS").put("RECORD_CONFLICTS_REQUIRE_REVIEW"))
        val preview=NativeBackupMergePreviewCodec.decode(response(withLoss),"backup-1",current,incoming)
        assertEquals(5,preview!!.historyDropped)
        assertEquals("HISTORY_CAP_LOSS",preview.flags.first())
        val malformed=JSONObject(response(withLoss)).put("id","wrong-id")
        assertTrue(runCatching {
            NativeBackupMergePreviewCodec.decode(malformed.toString(),"backup-1",current,incoming)
        }.isFailure)
        val error=JSONObject().put("protocolVersion",1).put("id","backup-1")
            .put("ok",false).put("error",JSONObject().put("code","PREPARATION_REJECTED")
                .put("message","Unavailable"))
        assertNull(NativeBackupMergePreviewCodec.decode(error.toString(),"backup-1",current,incoming))
    }
}
