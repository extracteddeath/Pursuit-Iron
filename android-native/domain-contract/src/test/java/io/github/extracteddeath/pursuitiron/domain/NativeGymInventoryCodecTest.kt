package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeGymInventoryCodecTest {
    @Test fun realInventoryRoundTripAndLegacyNoInventory() {
        assertNull(NativeGymInventoryCodec.fromDraft(emptyMap(),"lb").json)
        assertTrue(NativeGymInventoryCodec.fromDraft(emptyMap(),"lb").valid)
        val fields=mapOf("bar" to "45", "plates" to "2.5x2, 5x2, 25x1",
            "dumbbells" to "20, 25, 30", "cable" to "5, 5, 150",
            "smith" to "15, 25, 35")
        val built=NativeGymInventoryCodec.fromDraft(fields,"lb")
        assertTrue(built.error.orEmpty(),built.valid)
        val raw=checkNotNull(built.json)
        val json=JSONObject(raw)
        assertEquals("lb",json.getString("unit"))
        assertEquals(3,json.getJSONObject("barbell").getJSONArray("platePairs").length())
        assertFalse(json.has("machine"))
        assertEquals("25",NativeGymInventoryCodec.toDraft(raw)["plates"]?.split(",")
            ?.last()?.trim()?.substringBefore("x"))
        val profile=NativeProgramProfile(loadingInventory=raw)
        val restored=NativeProgramSetupCodec.readProfile(NativeProgramSetupCodec.profileJson(profile))
        assertEquals(json.toString(),restored.loadingInventory)
        assertEquals(raw,NativeGymInventoryCodec.validate(raw,"lb"))
        assertFalse(JSONObject(NativeProgramSetupCodec.profileJson(NativeProgramProfile()))
            .has("loadingInventory"))
    }

    @Test fun noUnitConversionAndNoUnknownHardware() {
        val stock=checkNotNull(NativeGymInventoryCodec.fromDraft(mapOf("dumbbells" to "20, 25"),"lb").json)
        assertTrue(runCatching { NativeGymInventoryCodec.validate(stock,"kg") }.isFailure)
        val valid=JSONObject(stock)
        val changes=listOf<(JSONObject)->Unit>(
            {it.getJSONObject("dumbbells").put("availablePerHand",org.json.JSONArray().put(20).put(20))},
            {it.getJSONObject("dumbbells").put("availablePerHand",org.json.JSONArray().put(20).put(-5))},
            {it.put("barbell",JSONObject().put("barWeight",45))},
            {it.put("machine",JSONObject().put("minimum",5).put("increment",0).put("maximum",100))},
            {it.put("exerciseOverrides",JSONObject().put("back-squat",JSONObject()))},
            {it.put("invented",true)}
        )
        changes.forEach { change ->
            val copy=JSONObject(valid.toString());change(copy)
            assertTrue("Unsafe physical stock accepted: $copy",
                runCatching { NativeGymInventoryCodec.validate(copy.toString(),"lb") }.isFailure)
        }
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("bar" to "45"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("plates" to "5x2"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("smith" to "20, 20"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("smith" to "20.0, 20"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("bar" to "45",
            "plates" to "5x2, 5.0x2"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("machine" to "5, 5"),"lb").valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(mapOf("cable" to "5, 0, 100"),"lb").valid)
    }
}
