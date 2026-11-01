package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutOutlineCodecTest {
    private val response = """
      {"protocolVersion":1,"id":"outline-1","ok":true,"result":{
        "source":"modules/engine-api.js:generateNextProgramForShell",
        "readOnly":true,"syntheticOnly":true,"split":"full_body","dayIndex":0,
        "sessionId":"day-1","day":"monday","estimatedMinutes":48.5,"maxMinutes":60,
        "exercises":[
          {"exerciseId":"barbell_bench","name":"Barbell Bench Press",
          "role":"primary_strength","sets":3,"repMin":5,"repMax":8,
          "rirMin":2,"rirMax":3,"restSeconds":180,
          "progressionStyle":"double"}
        ]
      }}
    """.trimIndent()

    @Test fun encodesOnlySyntheticSessionSelection() {
        val request = JSONObject(WorkoutOutlineCodec.encodeOutline("outline-1","full_body",0))
        assertEquals("workoutOutline",request.getString("method"))
        assertEquals("full_body",request.getJSONObject("params").getString("split"))
        assertEquals(0,request.getJSONObject("params").getInt("dayIndex"))
        assertFalse(WorkoutOutlineCodec.validChoice("full_body",3))
        assertFalse(WorkoutOutlineCodec.validChoice("custom",0))
        assertTrue(runCatching { WorkoutOutlineCodec.encodeOutline("other","custom",0) }.isFailure)
    }

    @Test fun parsesReadOnlyOutlineWithoutInferredLoads() {
        val value = WorkoutOutlineCodec.decodeOutline(response,"outline-1","full_body",0)
        assertTrue(value is EngineResponse.Success)
        val session = (value as EngineResponse.Success).result
        assertEquals(48.5,session.estimatedMinutes,0.0)
        assertEquals("monday",session.day)
        assertEquals(1,session.exercises.size)
        val ex = session.exercises.single()
        assertEquals("barbell_bench",ex.exerciseId)
        assertEquals(3,ex.sets)
        assertEquals(180.0,ex.restSeconds,0.0)
        assertEquals(2,ex.rirMin)
        assertEquals(3,ex.rirMax)
    }

    @Test fun refusesWrongProvenanceOrInventedPrescriptions() {
        fun rejects(text: String) = assertTrue(runCatching {
            WorkoutOutlineCodec.decodeOutline(text,"outline-1","full_body",0)
        }.isFailure)
        rejects(response.replace("\"readOnly\":true","\"readOnly\":false"))
        rejects(response.replace("\"syntheticOnly\":true","\"syntheticOnly\":false"))
        rejects(response.replace("outline-1","mismatched-request"))
        rejects(response.replace("generateNextProgramForShell","inventedGenerator"))
        rejects(response.replace("\"dayIndex\":0","\"dayIndex\":1"))
        rejects(response.replace("\"sets\":3","\"sets\":3.5"))
        rejects(response.replace("\"repMax\":8","\"repMax\":4"))
        rejects(response.replace("\"rirMax\":3","\"rirMax\":1"))
        rejects(response.replace("\"restSeconds\":180","\"restSeconds\":-1"))
        rejects(response.replace("\"protocolVersion\":1","\"protocolVersion\":2"))
    }

    @Test fun propagatesErrorsWithoutFabricatingWorkout() {
        val error = WorkoutOutlineCodec.decodeOutline(
            """{"protocolVersion":1,"id":"outline-1","ok":false,
            "error":{"code":"ENGINE_UNAVAILABLE","message":"Synthetic outline unavailable"}}""",
            "outline-1","full_body",0
        )
        assertEquals(EngineResponse.Failure("outline-1",
            EngineError("ENGINE_UNAVAILABLE","Synthetic outline unavailable")),error)
    }
}
