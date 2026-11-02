package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Typed authoring contracts only. Prescribed volume and exercise choices
 * remain the canonical engine's responsibility.
 */
class NativeProgramPriorityCodecTest {
    @Test fun focusAndReductionRoundTripWithDeterministicSourceOrdering() {
        val profile=NativeProgramProfile(focus=linkedMapOf("quads" to 1,"chest" to 2),
            reduced=listOf("calves","traps"))
        val raw=NativeProgramSetupCodec.profileJson(profile)
        val decoded=NativeProgramSetupCodec.readProfile(raw)
        assertEquals(profile.focus,decoded.focus)
        assertEquals(profile.reduced.toSet(),decoded.reduced.toSet())
        assertEquals(listOf("chest","quads"),JSONObject(raw).getJSONObject("focus").keys()
            .asSequence().toList())
        assertEquals("calves",JSONObject(raw).getJSONArray("reduced").getString(0))
        assertEquals(raw,NativeProgramSetupCodec.profileJson(decoded))
        val request=NativeProgramSetupCodec.setupJson(profile,
            "create-priority","program-priority",12345L,902)
        assertEquals(2,JSONObject(request).getJSONObject("profile")
            .getJSONObject("focus").getInt("chest"))
    }

    @Test fun legacySavedProfileWithoutPriorityFieldsStillDecodes() {
        val old=JSONObject(NativeProgramSetupCodec.profileJson(NativeProgramProfile()))
        old.remove("focus")
        old.remove("reduced")
        val restored=NativeProgramSetupCodec.readProfile(old.toString())
        assertTrue(restored.focus.isEmpty())
        assertTrue(restored.reduced.isEmpty())
    }

    @Test fun invalidPrioritiesAndOverlappingMusclesFailClosed() {
        val valid=JSONObject(NativeProgramSetupCodec.profileJson(NativeProgramProfile()))
        val invalid=listOf<(JSONObject)->Unit>(
            {it.getJSONObject("focus").put("unknown",1)},
            {it.getJSONObject("focus").put("chest",0)},
            {it.getJSONObject("focus").put("chest",3)},
            {it.getJSONObject("focus").put("chest",1.5)},
            {it.getJSONObject("focus").put("chest",true)},
            {it.put("focus",JSONArray())},
            {it.put("reduced",JSONObject())},
            {it.getJSONObject("focus").put("chest",1);it.getJSONArray("reduced").put("chest")},
            {it.getJSONObject("focus").put("chest",1).put("quads",1).put("glutes",1).put("lats",1)},
            {it.getJSONArray("reduced").put("neck").put("neck")},
            {it.getJSONArray("reduced").put("neck").put("lats").put("quads").put("abs")},
            {it.getJSONArray("reduced").put("invented")},
            {it.remove("reduced")},
            {it.remove("focus")},
            {it.put("extra","disallowed")}
        )
        for (change in invalid) {
            val value=JSONObject(valid.toString())
            change(value)
            assertTrue("Malformed authoring priority accepted: $value",
                runCatching { NativeProgramSetupCodec.readProfile(value.toString()) }.isFailure)
        }
    }
}
