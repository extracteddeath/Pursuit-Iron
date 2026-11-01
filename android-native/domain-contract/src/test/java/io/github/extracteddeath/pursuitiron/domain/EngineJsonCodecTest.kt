package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** These are transport-only tests. They don't authorize engine execution. */
class EngineJsonCodecTest {
    private val input = ProgressionPreviewInput(
        exerciseId = "back_squat",
        repMin = 5, repMax = 8, targetRir = 2,
        sets = listOf(
            ObservedWorkingSet(185.0, 5, 2),
            ObservedWorkingSet(182.5, 5, null)
        ),
        equipmentAvailable = listOf("barbell", "rack")
    )

    private fun okResponse(): String = """
        {
          "protocolVersion":1,"id":"fixture-1","ok":true,
          "result":{
            "source":"modules/engine-api.js:evaluateWorkoutProgression",
            "previewOnly":true,"action":"add_reps",
            "suggestedLoad":182.5,"suggestedReps":6,
            "reasonCode":null,"confidence":"moderate",
            "setTargets":[{"weight":182.5,"reps":6},{"weight":180,"reps":5}]
          }
        }
    """.trimIndent()

    @Test fun encodesExactObservedLoadsAndNullRir() {
        val result = JSONObject(EngineJsonCodec.encodeProgressionPreview("fixture-1", input))
        assertEquals(1, result.getInt("protocolVersion"))
        assertEquals("progressionPreview", result.getString("method"))
        val params = result.getJSONObject("params")
        assertEquals("back_squat",params.getString("exerciseId"))
        val sets = params.getJSONArray("sets")
        assertEquals(2, sets.length())
        assertEquals(185.0, sets.getJSONObject(0).getDouble("load"), 0.0)
        assertEquals(182.5, sets.getJSONObject(1).getDouble("load"), 0.0)
        assertTrue(sets.getJSONObject(1).isNull("rir"))
        assertEquals(2, params.getJSONArray("equipmentAvailable").length())
        assertThrows(IllegalArgumentException::class.java) {
            EngineJsonCodec.encodeProgressionPreview("x",input.copy(repMin = 20))
        }
    }

    @Test fun decodesAllFieldsWithoutRoundingOrFillingDefaults() {
        val response = EngineJsonCodec.decodeProgressionPreview(okResponse(),"fixture-1")
        assertTrue(response is EngineResponse.Success)
        val result = (response as EngineResponse.Success).result
        assertEquals("add_reps",result.action)
        assertEquals(182.5,result.suggestedLoad!!,0.0)
        assertEquals(6,result.suggestedReps)
        assertEquals(null,result.reasonCode)
        assertEquals("moderate",result.confidence)
        assertEquals(2,result.setTargets.size)
        assertEquals(182.5,result.setTargets[0].weight!!,0.0)
        assertEquals(180.0,result.setTargets[1].weight!!,0.0)
    }

    @Test fun rejectsUnapprovedAndMalformedEngineOutputs() {
        fun reject(s: String) = assertTrue(runCatching {
            EngineJsonCodec.decodeProgressionPreview(s,"fixture-1")
        }.isFailure)
        reject(okResponse().replace("\"previewOnly\":true","\"previewOnly\":false"))
        reject(okResponse().replace("fixture-1","other-id"))
        reject(okResponse().replace("evaluateWorkoutProgression","inventedFormula"))
        reject(okResponse().replace("\"action\":\"add_reps\"","\"action\":123"))
        reject(okResponse().replace("\"suggestedLoad\":182.5","\"suggestedLoad\":182.25e309"))
        reject(okResponse().replace("\"suggestedReps\":6","\"suggestedReps\":6.5"))
        reject(okResponse().replace("\"source\":","\"missingSource\":"))
        reject(okResponse().replace("\"setTargets\":[","\"setTargets\":{} , \"ignored\":["))
        reject(okResponse().replace("\"protocolVersion\":1","\"protocolVersion\":2"))
    }

    @Test fun surfacesStructuredErrorsWithoutFabricatingRecommendations() {
        val response = EngineJsonCodec.decodeProgressionPreview(
            """{"protocolVersion":1,"id":"fixture-1","ok":false,
               "error":{"code":"ENGINE_UNAVAILABLE","message":"Canonical engine unavailable"}}""",
            "fixture-1"
        )
        assertEquals(EngineResponse.Failure("fixture-1",
            EngineError("ENGINE_UNAVAILABLE","Canonical engine unavailable")),response)
    }
}
