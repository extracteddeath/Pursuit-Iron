package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeWorkoutMuscleReviewTest {
    private val fixture: JSONObject by lazy {
        JSONObject(checkNotNull(javaClass.classLoader?.getResourceAsStream("finished-journal-golden.json"))
            .bufferedReader().use { it.readText() })
    }
    @Test fun invalidSourceOrUntrustedResultCannotBeUsedAsExerciseEvidence() {
        // An issued full-workout fixture is required. A legacy journal-only
        // handoff cannot authorize this inspection.
        val src=fixture.getString("rawSnapshot")
        assertThrows(IllegalArgumentException::class.java) {
            NativeWorkoutMuscleReviewCodec.encode("inspect",src,"")
        }
        assertTrue(runCatching {
            NativeWorkoutMuscleReviewCodec.decode("{}", "inspect",src,"{}")
        }.isFailure)
        val wire=JSONObject().put("protocolVersion",1).put("id","inspect")
            .put("method","inspectNativeWorkoutMuscles")
            .put("params",JSONObject().put("rawSnapshot",src).put("issuedWorkout","not-issued"))
        assertEquals("inspectNativeWorkoutMuscles",
            WorkoutStoreRuntimeCodec.checkedRequest(wire.toString()).getString("method"))
        wire.getJSONObject("params").put("write",true)
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutStoreRuntimeCodec.checkedRequest(wire.toString())
        }
    }
}
