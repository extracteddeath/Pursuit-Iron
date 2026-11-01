package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExerciseCatalogCodecTest {
    private val ids = listOf("bb-bench", "inc-db-press", "not-a-real-exercise")
    private val body = """
        {
          "protocolVersion":1,"id":"catalog-test","ok":true,
          "result":{
            "source":"modules/engine-api.js:EXERCISES","readOnly":true,
            "exercises":[
              {"id":"bb-bench","name":"Barbell Bench Press","part":"chest",
               "type":"compound","equipment":["barbell","bench","rack"],
               "repMin":5,"repMax":10,"pattern":"horizontal-push"},
              {"id":"inc-db-press","name":"Incline Dumbbell Press","part":"chest",
               "type":"compound","equipment":["dumbbell","inclinebench"],
               "repMin":8,"repMax":12,"pattern":null}
            ],
            "missingIds":["not-a-real-exercise"]
          }
        }
    """.trimIndent()

    @Test fun keepsStableCanonicalIdsAndListOrdering() {
        val params = JSONObject(ExerciseCatalogCodec.encodeLookup("catalog-test",ids))
        assertEquals("exerciseLookup",params.getString("method"))
        assertEquals("bb-bench",params.getJSONObject("params").getJSONArray("ids").getString(0))
        assertEquals("inc-db-press",params.getJSONObject("params").getJSONArray("ids").getString(1))
        val result = ExerciseCatalogCodec.decodeLookup(body,"catalog-test",ids)
        assertTrue(result is EngineResponse.Success)
        val value = (result as EngineResponse.Success).result
        assertEquals(ids.take(2),value.exercises.map { it.id })
        assertEquals(listOf("not-a-real-exercise"),value.missingIds)
        assertEquals(5,value.exercises[0].repMin)
        assertEquals("barbell",value.exercises[0].equipment[0])
        assertNull(value.exercises[1].movementPattern)
    }

    @Test fun rejectsMalformedOrCrossRequestCatalogData() {
        fun reject(json: String) = assertTrue(runCatching {
            ExerciseCatalogCodec.decodeLookup(json,"catalog-test",ids)
        }.isFailure)
        reject(body.replace("\"readOnly\":true","\"readOnly\":false"))
        reject(body.replace("catalog-test","wrong-request"))
        reject(body.replace("modules/engine-api.js:EXERCISES","untrusted"))
        reject(body.replace("\"repMin\":5","\"repMin\":15"))
        reject(body.replace("\"id\":\"bb-bench\"","\"id\":\"unknown\""))
        reject(body.replace("\"not-a-real-exercise\"","\"bb-bench\""))
        reject(body.replace("\"type\":\"compound\"","\"type\":\"other\""))
        reject(body.replace("\"equipment\":[\"barbell\"","\"equipment\":[123"))
        reject(body.replace("\"protocolVersion\":1","\"protocolVersion\":2"))
        assertFalse(ExerciseCatalogCodec.validateIds(listOf("bb-bench","bb-bench")))
        assertFalse(ExerciseCatalogCodec.validateIds(List(13){"bb-bench-$it"}))
        assertFalse(ExerciseCatalogCodec.validateIds(listOf("__bad!")))
    }

    @Test fun returnsExplicitErrorWithoutInventedExerciseData() {
        val packet = ExerciseCatalogCodec.decodeLookup(
            """{"protocolVersion":1,"id":"catalog-test","ok":false,
                "error":{"code":"ENGINE_UNAVAILABLE","message":"Catalog unavailable"}}""",
            "catalog-test",ids
        )
        assertEquals(EngineResponse.Failure("catalog-test",
            EngineError("ENGINE_UNAVAILABLE","Catalog unavailable")),packet)
    }
}
