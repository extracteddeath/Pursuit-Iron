package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Repeated lifts use a merged history index and a separate issued exercise slot. */
class NativeHistoryCodecTest {
    private val snapshot="{}"
    private fun result(): JSONObject {
        val sets=JSONArray()
        repeat(24) { i->sets.put(JSONObject().put("setIndex",i).put("exerciseIndex",i/12)
            .put("kind",if(i%12>=10)"drop" else "working").put("ordinal",i%12+1)
            .put("load","40").put("reps",8).put("rir",JSONObject.NULL)) }
        return JSONObject().put("source","modules/engine-api.js:normalizeEditedHistoryEntry+perfAfterHistoryReplace+exRecords")
            .put("snapshotSha256",WorkoutStoreRuntimeCodec.sha(snapshot)).put("entrySha256","a".repeat(64))
            .put("historyId","workout-repeat").put("dayLabel","Repeated lift").put("weekIndex",1)
            .put("unit","kg").put("date",62000).put("setsDone",24).put("totalSets",24).put("volume",7680)
            .put("exercises",JSONArray().put(JSONObject().put("id","bb-bench").put("name","Bench press")
                .put("sets",sets).put("record",JSONObject.NULL)))
    }
    private fun decode(value: JSONObject)=NativeHistoryCodec.decodeInspect(JSONObject()
        .put("protocolVersion",1).put("id","inspect-repeat").put("ok",true).put("result",value).toString(),
        "inspect-repeat",snapshot,"workout-repeat")

    @Test fun mergedIndicesBeyondTwentyRemainBoundToSeparateOccurrencesAndEditable() {
        val view=checkNotNull(decode(result()));val sets=view.exercises.single().sets
        assertEquals(24,sets.size)
        assertEquals(listOf(0,1),sets.map { it.exerciseIndex }.distinct())
        assertEquals(1,sets[12].ordinal)
        assertEquals("drop",sets[23].kind)
        val request=JSONObject(NativeHistoryCodec.correctionJson(view,"correct-repeat",70000,
            listOf(NativeHistoryChange("bb-bench",23,"35",9,null))))
        assertEquals(23,request.getJSONArray("changes").getJSONObject(0).getInt("setIndex"))
        assertTrue(runCatching { NativeHistoryCodec.correctionJson(view,"unknown-row",70000,
            listOf(NativeHistoryChange("bb-bench",24,"35",9,null))) }.isFailure)
    }

    @Test fun malformedOrReorderedSlotIdentitiesFailClosed() {
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("setIndex",480) },{ it.put("setIndex",0) },
            { it.put("exerciseIndex",48) },{ it.put("exerciseIndex",-1) },
            { it.put("exerciseIndex",0) },{ it.put("ordinal",21) },
            { it.put("ordinal",0) },{ it.put("extra",true) },{ it.remove("exerciseIndex") }
        )
        for(mutate in mutations) {
            val value=result();val set=value.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets").getJSONObject(12)
            mutate(set)
            assertTrue(runCatching { decode(value) }.isFailure)
        }
    }

    @Test fun oneIssuedOccurrenceCannotBelongToDifferentExercises() {
        val value=result();val exercises=value.getJSONArray("exercises")
        val other=JSONObject(exercises.getJSONObject(0).toString()).put("id","db-bench")
        exercises.put(other)
        assertTrue(runCatching { decode(value) }.isFailure)
    }
}
