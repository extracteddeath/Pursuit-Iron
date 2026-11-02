package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeHistoryObservationTest {
    private val snapshot="{}"
    private fun values(load: String="40")=JSONObject().put("load",load).put("unit","kg").put("reps",8).put("rir",JSONObject.NULL)
    private fun result(): JSONObject {
        val sets=JSONArray()
        for((index,position) in listOf(0,2).withIndex())sets.put(JSONObject().put("setIndex",index).put("exerciseIndex",0)
            .put("ordinal",position+1).put("kind","working").put("load","40").put("reps",8).put("rir",JSONObject.NULL))
        val rows=JSONArray()
        repeat(4) { i->rows.put(JSONObject().put("slot",JSONObject().put("exerciseIndex",0).put("setIndex",i).put("exerciseId","bb-bench"))
            .put("name","Bench press").put("kind","working").put("retained",i==0 || i==2)
            .put("disposition",if(i==1)"SKIPPED" else "COMPLETED")
            .put("values",if(i==1)JSONObject.NULL else values(if(i==3)"0" else "40"))) }
        return JSONObject().put("source","modules/engine-api.js:normalizeEditedHistoryEntry+perfAfterHistoryReplace+exRecords")
            .put("snapshotSha256",WorkoutStoreRuntimeCodec.sha(snapshot)).put("entrySha256","a".repeat(64))
            .put("historyId","workout-observations").put("dayLabel","Upper").put("weekIndex",1).put("unit","kg")
            .put("date",62000).put("setsDone",3).put("totalSets",4).put("volume",640)
            .put("exercises",JSONArray().put(JSONObject().put("id","bb-bench").put("name","Bench press").put("sets",sets).put("record",JSONObject.NULL)))
            .put("editRows",rows)
    }
    private fun decode(value: JSONObject)=NativeHistoryCodec.decodeInspect(JSONObject().put("protocolVersion",1)
        .put("id","inspect").put("ok",true).put("result",value).toString(),"inspect",snapshot,"workout-observations")

    @Test fun skippedAndOmittedRowsRetainTheirOriginalSlotAndExactActualValues() {
        val view=checkNotNull(decode(result()));val rows=checkNotNull(view.editRows)
        assertEquals(4,rows.size);assertEquals(2,view.exercises.single().sets.size)
        assertNull(rows[1].values);assertEquals(JournalSetDisposition.SKIPPED,rows[1].disposition)
        assertEquals("0",checkNotNull(rows[3].values).load);assertFalse(rows[3].retained)
        val command=JSONObject(NativeHistoryObservationCodec.correctionJson(view,"restore-once",70000,
            listOf(NativeHistoryObservationChange(rows[1].slot,JournalSetDisposition.COMPLETED,JournalSetValues("45","kg",9,3)))))
        assertEquals(2,command.getInt("format"))
        assertEquals(1,command.getJSONArray("changes").getJSONObject(0).getJSONObject("slot").getInt("setIndex"))
    }

    @Test fun missingInvalidOrForeignActualsCannotBecomeACompletedSet() {
        val view=checkNotNull(decode(result()));val row=checkNotNull(view.editRows)[1]
        for(value in listOf(null,JournalSetValues(null,null,8,null),JournalSetValues("","kg",8,null),
            JournalSetValues("45","lb",8,null),JournalSetValues("45","kg",0,null),JournalSetValues("45","kg",8,11))) {
            assertTrue(runCatching { NativeHistoryObservationCodec.correctionJson(view,"bad-observation",70000,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,value))) }.isFailure)
        }
        val change=NativeHistoryObservationChange(row.slot,JournalSetDisposition.SKIPPED,null)
        assertTrue(runCatching { NativeHistoryObservationCodec.correctionJson(view,"duplicate",70000,listOf(change,change)) }.isFailure)
        assertTrue(runCatching { NativeHistoryObservationCodec.correctionJson(view,"foreign",70000,
            listOf(change.copy(slot=row.slot.copy(exerciseIndex=47)))) }.isFailure)
    }

    @Test fun forgedRosterStatusOrRetainedValuesFailClosed() {
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("retained",true) },{ it.put("disposition","COMPLETED") },
            { it.put("values",values()) },{ it.getJSONObject("slot").put("setIndex",0) },
            { it.getJSONObject("slot").put("exerciseId","other") },{ it.put("extra",true) }
        )
        for(mutate in mutations) {
            val value=result();mutate(value.getJSONArray("editRows").getJSONObject(1))
            assertTrue(runCatching { decode(value) }.isFailure)
        }
        val wrong=result();wrong.getJSONArray("editRows").getJSONObject(0).getJSONObject("values").put("load","50")
        assertTrue(runCatching { decode(wrong) }.isFailure)
    }

    @Test fun whollySkippedWorkoutsStillExposeEveryOriginalRow() {
        val value=result().put("setsDone",0).put("volume",0).put("exercises",JSONArray())
        val rows=value.getJSONArray("editRows")
        for(i in 0 until rows.length())rows.getJSONObject(i).put("retained",false).put("disposition","SKIPPED").put("values",JSONObject.NULL)
        assertEquals(4,checkNotNull(checkNotNull(decode(value)).editRows).size)
    }
}
