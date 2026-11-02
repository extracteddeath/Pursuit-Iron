package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeGymInventoryEditCodecTest {
    private val reference=ReadOnlyProgramReference("native-gym-program",200L,300L,13,"a".repeat(64))

    @Test fun exactRequestIncludesOwnedReferenceAndPhysicalStockOnly() {
        val physical=checkNotNull(NativeGymInventoryCodec.fromDraft(mapOf(
            "bar" to "45","plates" to "2.5x2, 5x2","dumbbells" to "20, 25",
            "smith" to "15, 25"),"lb").json)
        val raw=NativeGymInventoryEditCodec.editJson(reference,"lb",physical,"gym-stock-save-1",301L)
        val r=JSONObject(raw)
        assertEquals(1,r.getInt("version"))
        assertEquals("gym-stock-save-1",r.getString("operationId"))
        assertEquals("a".repeat(64),r.getJSONObject("reference").getString("snapshotSha256"))
        assertEquals(301L,r.getLong("editedAt"))
        assertEquals("lb",r.getJSONObject("loadingInventory").getString("unit"))
        assertEquals(2,r.getJSONObject("loadingInventory")
            .getJSONObject("barbell").getJSONArray("platePairs").length())
        assertFalse(r.has("history"))
        assertFalse(r.has("programExercises"))
        val clear=JSONObject(NativeGymInventoryEditCodec.editJson(reference,"lb",null,
            "gym-stock-clear-2",302L))
        assertTrue(clear.isNull("loadingInventory"))
    }

    @Test fun noInventedStepsAndNoStaleRevision() {
        val kg=checkNotNull(NativeGymInventoryCodec.fromDraft(mapOf(
            "dumbbells" to "10, 12.5"),"kg").json)
        assertTrue(runCatching {
            NativeGymInventoryEditCodec.editJson(reference,"lb",kg,"gym-edit-wrong",301L)
        }.isFailure)
        assertTrue(runCatching {
            NativeGymInventoryEditCodec.editJson(reference,"kg",kg,"gym-edit-stale",300L)
        }.isFailure)
        assertTrue(runCatching {
            NativeGymInventoryEditCodec.editJson(reference,"kg",kg,"bad id!",301L)
        }.isFailure)
        assertTrue(runCatching {
            NativeGymInventoryEditCodec.editJson(reference,"kg",kg,"gym-stock",200L)
        }.isFailure)
        val raw=NativeGymInventoryEditCodec.encode("gym-wire","{}", "{}")
        val transport=JSONObject(raw)
        assertEquals("prepareNativeGymInventoryEdit",transport.getString("method"))
        assertEquals("{}",transport.getJSONObject("params").getString("currentSnapshot"))
        assertEquals("{}",transport.getJSONObject("params").getString("editJson"))
    }

    @Test fun invalidOrForgedWorkerResponseNeverAuthorizesWrite() {
        val r=NativeGymInventoryEditCodec.editJson(reference,"lb",null,"gym-stock-reject",301L)
        assertNull(NativeGymInventoryEditCodec.decode(
            JSONObject().put("protocolVersion",1).put("id","gym-wire").put("ok",false)
                .put("error",JSONObject().put("code","PREPARATION_REJECTED")
                    .put("message","Canonical workout-store preparation unavailable")).toString(),
            "gym-wire","{}",r))
        val forged=JSONObject().put("protocolVersion",1).put("id","gym-wire")
            .put("ok",true).put("result",JSONObject().put("source","fake-owner"))
        assertTrue(runCatching {
            NativeGymInventoryEditCodec.decode(forged.toString(),"gym-wire","{}",r)
        }.isFailure)
    }
}
