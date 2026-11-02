package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeBackupMergePreparationCodecTest {
    private val current="""{"v":13,"savedAt":10,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val incoming="""{"v":13,"savedAt":11,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val candidate="""{"v":13,"savedAt":11,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val digest get()=WorkoutStoreRuntimeCodec.sha(candidate)
    private fun result()=JSONObject()
        .put("source","modules/engine-api.js:mergeStores")
        .put("validation","canonical-v13-merge-preparation")
        .put("preparedOnly",true).put("persisted",false)
        .put("approvedForImport",false).put("approvedForWorkoutExecution",false)
        .put("requiresAtomicUndo",true).put("storeVersion",13)
        .put("currentSha256",WorkoutStoreRuntimeCodec.sha(current))
        .put("incomingSha256",WorkoutStoreRuntimeCodec.sha(incoming))
        .put("candidateSnapshotSha256",digest)
        .put("candidateSnapshotBytes",candidate.toByteArray(Charsets.UTF_8).size)
        .put("candidateSnapshot",candidate)
    private fun response(value: JSONObject=result())=JSONObject()
        .put("protocolVersion",1).put("id","prep-1")
        .put("ok",true).put("result",value).toString()
    private fun decode(raw: String=response())=NativeBackupMergePreparationCodec.decode(
        raw,"prep-1",current,incoming,digest)

    @Test fun validCandidateIsTiedToBothSourcesAndSpecificPreview() {
        val request=NativeBackupMergePreparationCodec.encode("prep-1",current,incoming,digest)
        val parsed=WorkoutStoreRuntimeCodec.checkedRequest(request)
        assertEquals("prepareBackupMerge",parsed.getString("method"))
        assertEquals(digest,parsed.getJSONObject("params").getString("expectedCandidateSha256"))
        val prepared=checkNotNull(decode())
        assertEquals(candidate,prepared.candidateSnapshot)
        assertEquals(digest,prepared.candidateSha256)
        assertEquals(WorkoutStoreRuntimeCodec.sha(current),prepared.currentSha256)
        assertEquals(WorkoutStoreRuntimeCodec.sha(incoming),prepared.incomingSha256)
        assertTrue(runCatching {
            NativeBackupMergePreparationCodec.decode(response(),"prep-1",incoming,current,digest)
        }.isFailure)
        assertTrue(runCatching {
            NativeBackupMergePreparationCodec.decode(response(),"prep-1",current,incoming,"f".repeat(64))
        }.isFailure)
    }

    @Test fun rejectForgedApprovalIdentityCandidateOrAdditionalData() {
        val corruptions=listOf<(JSONObject)->Unit>(
            {it.put("persisted",true)},
            {it.put("approvedForImport",true)},
            {it.put("approvedForWorkoutExecution",true)},
            {it.put("requiresAtomicUndo",false)},
            {it.put("preparedOnly",false)},
            {it.put("storeVersion",14)},
            {it.put("validation","other")},
            {it.put("source","other")},
            {it.put("currentSha256","0".repeat(64))},
            {it.put("incomingSha256","0".repeat(64))},
            {it.put("candidateSnapshotSha256","1".repeat(64))},
            {it.put("candidateSnapshotBytes",1)},
            {it.put("candidateSnapshot",candidate+" ")},
            {it.put("candidateSnapshot","{\\\"v\\\":14}")},
            {it.put("hiddenImport",true)}
        )
        for(change in corruptions) {
            val corrupt=JSONObject(result().toString());change(corrupt)
            assertTrue(runCatching { decode(response(corrupt)) }.isFailure)
        }
        val tampered=JSONObject(NativeBackupMergePreparationCodec.encode("prep-1",current,incoming,digest))
        tampered.getJSONObject("params").put("publish",true)
        assertTrue(runCatching { WorkoutStoreRuntimeCodec.checkedRequest(tampered.toString()) }.isFailure)
        assertTrue(runCatching {
            NativeBackupMergePreparationCodec.encode("prep-1",current,incoming,"bad")
        }.isFailure)
        assertTrue(runCatching {
            NativeBackupMergePreparationCodec.encode("prep-1",current,"\uD800",digest)
        }.isFailure)
    }

    @Test fun negativeEngineResponseCannotBeInterpretedAsPreparedCandidate() {
        val error=JSONObject().put("protocolVersion",1).put("id","prep-1").put("ok",false)
            .put("error",JSONObject().put("code","PREPARATION_REJECTED").put("message","Unavailable"))
        assertNull(decode(error.toString()))
    }
}
