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
        assertEquals(setOf("chest","quads"),JSONObject(raw).getJSONObject("focus").keys()
            .asSequence().toSet())
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

    @Test fun engineDeclaredMuscleOptionsAreStrictlyVerified() {
        val splits=JSONArray()
        listOf("full_body","upper_lower","ppl","ulppl").forEach { id ->
            splits.put(JSONObject().put("id",id).put("label",id)
                .put("days",JSONArray().put(3).put(4)))
        }
        val muscles=JSONArray()
        listOf("chest","lats","upper_back","shoulders","biceps","triceps",
            "quads","hamstrings","glutes","calves","abs","traps","forearms",
            "adductors","abductors","lower_back","neck").forEach { id ->
            muscles.put(JSONObject().put("id",id).put("label",id))
        }
        fun response(value: JSONObject)=JSONObject().put("protocolVersion",1)
            .put("id","muscle-options").put("ok",true).put("result",value).toString()
        val valid=JSONObject().put("source","modules/engine-api.js:splitSupportsTrainingDays")
            .put("readOnly",true).put("splits",splits)
            .put("equipment",JSONArray().put("barbell")).put("muscles",muscles)
        val result=checkNotNull(NativeProgramSetupCodec.decodeOptions(
            response(valid),"muscle-options"))
        assertEquals(17,result.muscles.size)
        assertEquals("Chest",result.muscles.first().label.replaceFirstChar { it.uppercase() })
        assertEquals("neck",result.muscles.last().id)
        assertTrue(runCatching {
            NativeProgramSetupCodec.decodeOptions(response(
                JSONObject(valid.toString()).apply { remove("muscles") }),"muscle-options")
        }.isFailure)
        val duplicate=JSONObject(valid.toString())
        duplicate.getJSONArray("muscles").getJSONObject(0).put("id","biceps")
        assertTrue(runCatching {
            NativeProgramSetupCodec.decodeOptions(response(duplicate),"muscle-options")
        }.isFailure)
        val unknown=JSONObject(valid.toString())
        unknown.getJSONArray("muscles").getJSONObject(0).put("id","made-up-part")
        assertTrue(runCatching {
            NativeProgramSetupCodec.decodeOptions(response(unknown),"muscle-options")
        }.isFailure)
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
