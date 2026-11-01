package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadOnlyBackupSchemaCodecTest {
    private val id = "schema-1"
    private val result = """{"protocolVersion":1,"id":"schema-1","ok":true,
      "result":{"source":"modules/engine-api.js:parseStoredData",
      "readOnly":true,"validation":"canonical-v13-structure-only",
      "imported":false,"approvedForWorkoutExecution":false,"storeVersion":13,
      "counts":{"saved":1,"history":2,"cycles":0,"custom":0,"gyms":0}}}""".trimIndent()
    @Test fun encodesOnlyBoundedSourceAndNeverAWriteMethod() {
        val input=ReadOnlyBackupSchemaCodec.encode(id,"{\"v\":13}")
        val parsed=JSONObject(input)
        assertEquals("backupSchemaCheck", parsed.getString("method"))
        assertEquals("{\"v\":13}",parsed.getJSONObject("params").getString("rawSnapshot"))
        assertTrue(runCatching { ReadOnlyBackupSchemaCodec.encode(id,"x".repeat(65537)) }.isFailure)
    }
    @Test fun decodesMetadataOnlyAndRefusesAnyWriteAuthorization() {
        val value=ReadOnlyBackupSchemaCodec.decode(result,id)
        assertTrue(value is EngineResponse.Success)
        val info=(value as EngineResponse.Success).result
        assertEquals(13,info.storeVersion)
        assertEquals(2,info.counts["history"])
        fun bad(raw:String)=assertTrue(runCatching {
            ReadOnlyBackupSchemaCodec.decode(raw,id)
        }.isFailure)
        bad(result.replace("schema-1","wrong"))
        bad(result.replace("\"readOnly\":true","\"readOnly\":false"))
        bad(result.replace("\"imported\":false","\"imported\":true"))
        bad(result.replace("\"approvedForWorkoutExecution\":false",
            "\"approvedForWorkoutExecution\":true"))
        bad(result.replace("\"storeVersion\":13","\"storeVersion\":12"))
        bad(result.replace("\"saved\":1","\"saved\":1.5"))
        bad(result.replace("\"saved\":1","\"saved\":-1"))
        bad(result.replace("\"saved\":1","\"saved\":\"1\""))
        bad(result.replace("\"counts\":{","\"privateHistory\":{},\"counts\":{"))
        bad(result.replace("\"gyms\":0","\"gyms\":0,\"weight\":205"))
    }
    @Test fun failureKeepsErrorEnvelopeButCannotFabricateAProgram() {
        val failure="""{"protocolVersion":1,"id":"schema-1","ok":false,
          "error":{"code":"SCHEMA_REJECTED","message":"Canonical v13 backup structure rejected"}}"""
        assertEquals(EngineResponse.Failure(id,EngineError(
            "SCHEMA_REJECTED","Canonical v13 backup structure rejected"
        )),ReadOnlyBackupSchemaCodec.decode(failure,id))
    }
}
