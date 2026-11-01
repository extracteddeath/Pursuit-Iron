package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Shared fixture originates from a completely invented canonical v13 store. */
class ReadOnlyProgramTransportCodecTest {
    private val fixture: JSONObject by lazy {
        val resource = checkNotNull(javaClass.classLoader
            ?.getResourceAsStream("read-only-transport-golden.json"))
        JSONObject(resource.bufferedReader().use { it.readText() })
    }
    private val reference by lazy {
        val obj = fixture.getJSONObject("wire").getJSONObject("reference")
        ReadOnlyProgramReference(
            programId = obj.getString("programId"),
            programUpdatedAt = obj.getLong("programUpdatedAt"),
            storeSavedAt = obj.getLong("storeSavedAt"),
            storeVersion = obj.getInt("storeVersion"),
            snapshotSha256 = obj.getString("snapshotSha256")
        )
    }
    private fun wire() = JSONObject(fixture.getJSONObject("wire").toString())
    private fun rejects(change: (JSONObject) -> Unit) {
        val payload = wire()
        change(payload)
        assertTrue(runCatching {
            ReadOnlyProgramTransportCodec.decode(payload.toString(), reference)
        }.isFailure)
    }

    @Test fun sharedSyntheticNodeWireDecodesWithoutPrescriptions() {
        val result = ReadOnlyProgramTransportCodec.decode(wire().toString(),reference)
        assertEquals("synthetic-program", result.id)
        assertEquals("Synthetic full body",result.name)
        assertTrue(result.custom)
        assertEquals(6,result.weeks)
        assertEquals("hypertrophy",result.goal)
        assertEquals(listOf("synthetic-day-1","synthetic-day-2"),result.days.map { it.id })
        assertEquals(listOf("bb-bench","cable-fly"),result.days[0].exerciseIds)
        assertEquals(reference,result.reference)
        assertEquals(5,ReadOnlyProgramReference::class.java.declaredFields
            .filter { !it.isSynthetic && !it.name.startsWith("$") }.size)
    }

    @Test fun staleOrWritablePayloadCannotEnterTheNativeSummaryLayer() {
        rejects { it.put("readOnly",false) }
        rejects { it.put("protocolVersion",2) }
        rejects { it.put("source","untrusted") }
        rejects { it.put("mayLogWorkout",true) }
        rejects { it.getJSONObject("reference").put("snapshotSha256","f".repeat(64)) }
        rejects { it.getJSONObject("reference").put("storeSavedAt",101) }
        rejects { it.getJSONObject("reference").put("programUpdatedAt",43) }
        rejects { it.getJSONObject("reference").put("programUpdatedAt",9007199254740992L) }
        rejects { it.getJSONObject("reference").put("programUpdatedAt","42") }
        rejects { it.getJSONObject("reference").put("programId","other-program") }
        rejects { it.getJSONObject("program").put("id","other-program") }
        rejects { it.getJSONObject("program").put("custom","true") }
        rejects { it.getJSONObject("program").put("weeks",1.5) }
        rejects { it.getJSONObject("program").put("goal","\n") }
        rejects { it.getJSONObject("program").put("targetWeight",185) }
        rejects { it.getJSONObject("program").getJSONArray("days")
            .getJSONObject(1).put("id","synthetic-day-1") }
        rejects { it.getJSONObject("program").getJSONArray("days")
            .getJSONObject(0).getJSONArray("exerciseIds").put(0,JSONObject()
                .put("id","bb-bench").put("weight",200)) }
        assertTrue(runCatching {
            ReadOnlyProgramTransportCodec.decode(wire().toString(), reference.copy(
                programUpdatedAt = 43))
        }.isFailure)
        assertTrue(runCatching {
            ReadOnlyProgramTransportCodec.decode(" ".repeat(65_537),reference)
        }.isFailure)
    }
}
