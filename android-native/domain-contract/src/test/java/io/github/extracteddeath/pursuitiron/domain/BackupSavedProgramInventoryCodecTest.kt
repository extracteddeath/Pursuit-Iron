package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupSavedProgramInventoryCodecTest {
    private val ok = """{"protocolVersion":1,"id":"test-inventory","ok":true,
      "result":{"source":"modules/engine-api.js:parseStoredData",
      "readOnly":true,"validation":"canonical-v13-backup-program-inventory",
      "snapshotOnly":true,"imported":false,"approvedForWorkoutExecution":false,
      "storeVersion":13,"storeSavedAt":100,
      "programs":[{"id":"synthetic","name":"Back-up plan","updatedAt":42,
      "trainingDays":2,"exerciseSlots":19,
      "days":[{"label":"Push","exerciseSlots":10},
              {"label":null,"exerciseSlots":9}]}]}}"""
    @Test fun inventoryRequestKeepsSingleBoundedRawSnapshot() {
        val request = JSONObject(BackupSavedProgramInventoryCodec.encode(
            "test-inventory","{\"v\":13}"))
        assertEquals("backupProgramInventory", request.getString("method"))
        assertEquals(1,request.getJSONObject("params").length())
        assertTrue(runCatching { BackupSavedProgramInventoryCodec.encode(
            "test-inventory","x".repeat(65537)) }.isFailure)
    }
    @Test fun strictBackupOnlyContractBlocksLiveOrWritePrivileges() {
        val good = BackupSavedProgramInventoryCodec.decode(ok,"test-inventory")
        assertTrue(good is EngineResponse.Success)
        val values=(good as EngineResponse.Success).result
        assertEquals(100L,values.storeSavedAt)
        assertEquals(2,values.entries.single().trainingDays)
        assertEquals("Push",values.entries.single().days.first().label)
        assertNull(values.entries.single().days.last().label)
        assertEquals(19,values.entries.single().days.sumOf { it.exerciseSlots })
        assertEquals(19,values.entries.single().exerciseSlots)
        fun bad(wire:String)=assertTrue(runCatching {
            BackupSavedProgramInventoryCodec.decode(wire,"test-inventory")
        }.isFailure)
        bad(ok.replace("test-inventory","different"))
        bad(ok.replace("\"snapshotOnly\":true","\"snapshotOnly\":false"))
        bad(ok.replace("\"imported\":false","\"imported\":true"))
        bad(ok.replace("\"approvedForWorkoutExecution\":false",
            "\"approvedForWorkoutExecution\":true"))
        bad(ok.replace("\"updatedAt\":42","\"updatedAt\":42.5"))
        bad(ok.replace("\"trainingDays\":2","\"trainingDays\":0"))
        bad(ok.replace("\"name\":\"Back-up plan\"","\"name\":\"\\u0000\""))
        bad(ok.replace("\"exerciseSlots\":19","\"exerciseSlots\":220"))
        bad(ok.replace("\"exerciseSlots\":10","\"exerciseSlots\":11"))
        bad(ok.replace("\"trainingDays\":2","\"trainingDays\":3"))
        bad(ok.replace("\"label\":null","\"label\":\"\\u0000\""))
        bad(ok.replace("\"days\":[","\"prescription\":{},\"days\":["))
        bad(ok.replace("\"exerciseSlots\":9","\"exerciseSlots\":31"))
        bad(ok.replace("\"programs\":[","\"history\":[],\"programs\":["))
        bad(ok.replace("\"programs\":[{","\"programs\":[{"
            .replace("{","{\"weight\":150,")))
        val duplicate=ok.replace("}]}}","},{\"id\":\"synthetic\",\"name\":\"Other\","
            + "\"updatedAt\":43,\"trainingDays\":2,\"exerciseSlots\":1}]}}")
        bad(duplicate)
    }
    @Test fun errorsExposeNoProgramMetadata() {
        val error="""{"protocolVersion":1,"id":"test-inventory","ok":false,
            "error":{"code":"SCHEMA_REJECTED","message":"Backup program inventory unavailable"}}"""
        assertTrue(BackupSavedProgramInventoryCodec.decode(error,"test-inventory")
            is EngineResponse.Failure)
    }
}
