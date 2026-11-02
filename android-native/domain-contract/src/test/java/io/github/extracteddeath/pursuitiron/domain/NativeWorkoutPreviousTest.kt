package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeWorkoutPreviousTest {
    private fun issued(previous: JSONArray): JSONObject {
        val reference=JSONObject().put("programId","program-last").put("programUpdatedAt",1000)
            .put("storeSavedAt",1000).put("storeVersion",13).put("snapshotSha256","a".repeat(64))
        val session=JSONObject().put("reference",reference).put("workoutId","workout-last")
            .put("dayId","day-last").put("weekIndex",1).put("unit","kg").put("startedAt",2000)
        val sets=JSONArray()
        repeat(3) { i->
            val trace=JSONObject().put("schemaVersion",1).put("programId","program-last")
                .put("dayId","day-last").put("exerciseId","bb-bench").put("slot",0)
                .put("weekIndex",1).put("workingSetIndex",i)
            sets.put(JSONObject().put("warm",false).put("done",false).put("auto",true)
                .put("weight","50").put("reps","8").put("target",JSONObject().put("decisionTrace",trace)
                    .put("reps","8-12").put("rir","2")))
        }
        val row=JSONObject().put("id","bb-bench").put("slot",0).put("name","Bench press")
            .put("restSeconds",120).put("superset",false).put("previous",previous).put("sets",sets)
        return JSONObject().put("format",1).put("source",NativeWorkoutCodec.SOURCE)
            .put("preparedOnly",true).put("persisted",false).put("inputStartSha256","b".repeat(64))
            .put("session",session).put("programName","Last proof").put("dayLabel","Upper")
            .put("rows",JSONArray().put(row))
    }
    private fun previous(index: Long?=null)=JSONObject().put("load","40").put("reps",8).put("unit","kg")
        .apply { if(index!=null)put("workingSetIndex",index) }

    @Test fun skippedLastPositionsRemainEmptyInsteadOfBorrowingTheNextSet() {
        val plan=NativeWorkoutCodec.readIssued(issued(JSONArray().put(previous(0)).put(previous(2))).toString())
        val sets=plan.exercises.single().sets
        assertEquals("40 kg × 8",sets[0].last);assertNull(sets[1].last);assertEquals("40 kg × 8",sets[2].last)
    }

    @Test fun legacyIssuedPreviousArraysStillRecoverWithoutRewritingThem() {
        val raw=issued(JSONArray().put(previous()).put(previous())).toString()
        val plan=NativeWorkoutCodec.readIssued(raw)
        assertEquals(raw,plan.issuedJson)
        assertEquals("40 kg × 8",plan.exercises.single().sets[1].last)
        assertNull(plan.exercises.single().sets[2].last)
    }

    @Test fun mixedDuplicateOrOverflowedPreviousPositionsAreRejected() {
        for(value in listOf(JSONArray().put(previous(0)).put(previous(0)),
            JSONArray().put(previous(0)).put(previous()),JSONArray().put(previous()).put(previous(2)),
            JSONArray().put(previous(20)),JSONArray().put(previous(4294967296L)))) {
            assertTrue(runCatching { NativeWorkoutCodec.readIssued(issued(value).toString()) }.isFailure)
        }
    }

    @Test fun zeroRepRuntimeWritesRequireThePinnedWorkingSetCapability() {
        val raw=issued(JSONArray()).put("allowFailedAttempts",true)
        val sets=raw.getJSONArray("rows").getJSONObject(0).getJSONArray("sets")
        sets.put(JSONObject().put("warm",false).put("done",false).put("auto",true).put("sub",true).put("kind","drop")
            .put("prescribed",true).put("weight","25").put("reps","").put("target",JSONObject().put("reps","to failure")
                .put("rir",JSONObject.NULL).put("rest",15)))
        val plan=NativeWorkoutCodec.readIssued(raw.toString());val working=plan.exercises[0].sets[0]
        val extension=plan.exercises[0].sets.last()
        assertTrue(working.allowFailedAttempt);assertFalse(extension.allowFailedAttempt)
        assertTrue(NativeWorkoutCodec.acceptsValues(plan,working.slot,JournalSetValues("80","kg",0,null)))
        assertFalse(NativeWorkoutCodec.acceptsValues(plan,extension.slot,JournalSetValues("25","kg",0,null)))
        for(value in listOf(JournalSetValues("0","kg",0,null),JournalSetValues("","kg",0,null),
            JournalSetValues("80","lb",0,null),JournalSetValues("80","kg",-1,null)))
            assertFalse(NativeWorkoutCodec.acceptsValues(plan,working.slot,value))
        val ordinary=NativeWorkoutCodec.readIssued(issued(JSONArray()).toString())
        assertFalse(NativeWorkoutCodec.acceptsValues(ordinary,ordinary.slots[0],JournalSetValues("80","kg",0,null)))
    }

    @Test fun failedPreviousResultsKeepTheirPositionAndAttemptLabel() {
        val raw=issued(JSONArray().put(previous(2).put("reps",0)))
        val plan=NativeWorkoutCodec.readIssued(raw.toString());val sets=plan.exercises[0].sets
        assertNull(sets[0].last);assertNull(sets[1].last);assertEquals("40 kg × 0 · failed attempt",sets[2].last)
    }
}
