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

    @Test fun perExerciseStacksKeepExactWeightsAndDoNotBorrowFactorySettings() {
        val allowed=setOf("lat-pulldown","cable-fly")
        val fields=mapOf("override:lat-pulldown" to "range: 5, 7.5, 200",
            "override:cable-fly" to "exact: 17.5, 22.5, 27.5")
        val result=NativeGymInventoryCodec.fromDraft(fields,"lb",allowed)
        assertTrue(result.error.orEmpty(),result.valid)
        val raw=checkNotNull(result.json)
        val inventory=JSONObject(raw)
        assertFalse(inventory.has("cable"))
        val profiles=inventory.getJSONObject("exerciseOverrides")
        assertEquals(7.5,profiles.getJSONObject("lat-pulldown").getDouble("increment"),0.00001)
        assertEquals(3,profiles.getJSONObject("cable-fly").getJSONArray("availableLoads").length())
        assertEquals("exact: 17.5, 22.5, 27.5",
            NativeGymInventoryCodec.toDraft(raw)["override:cable-fly"])
        assertEquals("range: 5, 7.5, 200",
            NativeGymInventoryCodec.toDraft(raw)["override:lat-pulldown"])
        assertEquals(raw,NativeGymInventoryCodec.validate(raw,"lb",allowed))
        assertFalse(NativeGymInventoryCodec.fromDraft(fields,"lb").valid)
        assertTrue(runCatching { NativeGymInventoryCodec.validate(raw,"kg",allowed) }.isFailure)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:lat-pulldown" to "exact: 20, 20.0"),"lb",allowed).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:lat-pulldown" to "range: 5, 0, 200"),"lb",allowed).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:lat-pulldown" to "range: 5, 7.5"),"lb",allowed).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:lat-pulldown" to "exact: 20.001, 25"),"lb",allowed).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:back-squat" to "exact: 45, 50"),"lb",allowed).valid)
    }

    @Test fun verifiedOverrideIdsExcludeFactoryLoadingAndRejectUntrustedEntries() {
        val eligible=setOf("lat-pulldown","leg-ext")
        val physical=checkNotNull(NativeGymInventoryCodec.fromDraft(
            mapOf("cable" to "5, 5, 150",
                "override:lat-pulldown" to "range: 5, 7.5, 200",
                "override:leg-ext" to "exact: 12.5, 17.5, 22.5"),
            "lb",eligible,setOf("leg-ext")).json)
        assertEquals(setOf("lat-pulldown","leg-ext"),NativeGymInventoryCodec.verifiedOverrideIds(
            physical,"lb",eligible,setOf("leg-ext")))
        assertEquals(emptySet<String>(),NativeGymInventoryCodec.verifiedOverrideIds(
            null,"lb",eligible,setOf("leg-ext")))
        assertEquals(emptySet<String>(),NativeGymInventoryCodec.verifiedOverrideIds(
            checkNotNull(NativeGymInventoryCodec.fromDraft(mapOf("cable" to "5, 5, 150"),"lb").json),
            "lb",eligible,setOf("leg-ext")))
        assertTrue(runCatching {
            NativeGymInventoryCodec.verifiedOverrideIds(physical,"kg",eligible,setOf("leg-ext"))
        }.isFailure)
        assertTrue(runCatching {
            NativeGymInventoryCodec.verifiedOverrideIds(physical,"lb",setOf("leg-ext"),setOf("leg-ext"))
        }.isFailure)
    }

    @Test fun specialtyMachinesRequireUserVerifiedExactLoggedValues() {
        val eligible=setOf("leg-ext","lat-pulldown")
        val specialty=setOf("leg-ext")
        val known=NativeGymInventoryCodec.fromDraft(
            mapOf("override:leg-ext" to "exact: 12.5, 17.5, 22.5"),
            "lb",eligible,specialty)
        assertTrue(known.error.orEmpty(),known.valid)
        val raw=checkNotNull(known.json)
        assertEquals("exact: 12.5, 17.5, 22.5",
            NativeGymInventoryCodec.toDraft(raw)["override:leg-ext"])
        assertEquals(raw,NativeGymInventoryCodec.validate(raw,"lb",eligible,specialty))
        val unsafe=JSONObject(raw)
        unsafe.getJSONObject("exerciseOverrides").put("leg-ext",JSONObject()
            .put("minimum",5).put("increment",5).put("maximum",250))
        assertTrue(runCatching {
            NativeGymInventoryCodec.validate(unsafe.toString(),"lb",eligible,specialty)
        }.isFailure)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:leg-ext" to "range: 5, 5, 250"),
            "lb",eligible,specialty).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:leg-ext" to "exact: 20, 20"),
            "lb",eligible,specialty).valid)
        assertTrue(NativeGymInventoryCodec.fromDraft(
            mapOf("override:lat-pulldown" to "range: 5, 7.5, 200"),
            "lb",eligible,specialty).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            mapOf("override:leg-ext" to "exact: 20, 25"),
            "lb",setOf("lat-pulldown"),specialty).valid)
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
