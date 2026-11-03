package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class NativeCoachVerifiedLoadEvidenceTest {
    private val eligible=listOf(
        NativeStackCandidate("leg-ext","Leg Extension","external_load"),
        NativeStackCandidate("hack-squat","Hack Squat","external_load"),
        NativeStackCandidate("lat-pulldown","Lat Pulldown","cable_stack"),
        NativeStackCandidate("standard-row","Row Stack","machine_stack")
    )

    @Test fun onlyAuthenticatedExerciseSpecificFactsMayLeaveTheNativeGym() {
        val raw=checkNotNull(NativeGymInventoryCodec.fromDraft(mapOf(
            "bar" to "45", "plates" to "2.5x2, 5x2",
            "cable" to "5, 5, 150", "machine" to "10, 10, 200",
            "override:leg-ext" to "exact: 12.5, 17.5, 22.5",
            "override:lat-pulldown" to "range: 5, 7.5, 200",
            "override:standard-row" to "exact: 10, 20, 30"
        ),"lb",eligible.map {it.id}.toSet(),
            setOf("leg-ext","hack-squat")).json)
        val review=NativeCoachVerifiedLoadEvidence.review(raw,"lb",eligible)
        assertEquals(4,review.candidateCount)
        assertEquals(3,review.facts.size)
        assertEquals(1,review.remainingManual)
        assertEquals(listOf("leg-ext","lat-pulldown","standard-row"),
            review.facts.map {it.exerciseId})
        val leg=review.facts[0] as NativeCoachVerifiedLoadFact.Exact
        assertEquals(listOf(BigDecimal("12.5"),BigDecimal("17.5"),
            BigDecimal("22.5")),leg.loads)
        assertEquals("Leg Extension",leg.name)
        assertEquals("lb",leg.unit)
        val cable=review.facts[1] as NativeCoachVerifiedLoadFact.Range
        assertEquals(BigDecimal("5"),cable.minimum)
        assertEquals(BigDecimal("7.5"),cable.increment)
        assertEquals(BigDecimal("200"),cable.maximum)
        assertEquals("lat-pulldown",cable.exerciseId)
        val row=review.facts[2] as NativeCoachVerifiedLoadFact.Exact
        assertEquals(listOf(BigDecimal("10"),BigDecimal("20"),BigDecimal("30")),row.loads)
        assertEquals("hack-squat",eligible[1].id)
        // Shared cable, generic machine and barbell inventory never become
        // invented exercise-specific loading facts.
        val onlyShared=checkNotNull(NativeGymInventoryCodec.fromDraft(
            mapOf("cable" to "5, 5, 150","machine" to "10,10,200"),"lb").json)
        assertEquals(0,NativeCoachVerifiedLoadEvidence.review(onlyShared,"lb",eligible).facts.size)
        assertEquals(4,NativeCoachVerifiedLoadEvidence.review(onlyShared,"lb",eligible).remainingManual)
    }

    @Test fun noInventoryMeansUnknownNotFactoryOrMinimumLoading() {
        val result=NativeCoachVerifiedLoadEvidence.review(null,"kg",eligible)
        assertEquals(4,result.remainingManual)
        assertTrue(result.facts.isEmpty())
        assertEquals(0,NativeCoachVerifiedLoadEvidence.review(null,"lb",emptyList()).remainingManual)
    }

    @Test fun maliciousUnknownOrRangeSpecialtyMachineIsRejected() {
        val allowed=eligible.map {it.id}.toSet()
        val valid=checkNotNull(NativeGymInventoryCodec.fromDraft(
            mapOf("override:leg-ext" to "exact: 12.5,17.5"),"lb",allowed,
            setOf("leg-ext","hack-squat")).json)
        fun rejected(raw:String) = assertTrue(runCatching {
            NativeCoachVerifiedLoadEvidence.review(raw,"lb",eligible)
        }.isFailure)
        rejected(JSONObject(valid).apply {
            getJSONObject("exerciseOverrides").put("unknown-move",
                JSONObject().put("availableLoads",org.json.JSONArray().put(10).put(15)))
        }.toString())
        rejected(JSONObject(valid).apply {
            getJSONObject("exerciseOverrides").put("leg-ext",JSONObject()
                .put("minimum",5).put("increment",5).put("maximum",150))
        }.toString())
        rejected(JSONObject(valid).put("unit","kg").toString())
        rejected(JSONObject(valid).apply {
            getJSONObject("exerciseOverrides").getJSONObject("leg-ext")
                .put("availableLoads",org.json.JSONArray().put(20).put(20))
        }.toString())
        assertTrue(runCatching { NativeCoachVerifiedLoadEvidence.review(
            valid,"lb",eligible+eligible[0]) }.isFailure)
        assertTrue(runCatching { NativeCoachVerifiedLoadEvidence.review(
            valid,"kg",eligible) }.isFailure)
        assertTrue(runCatching { NativeCoachVerifiedLoadEvidence.review(
            valid,"lb",eligible+NativeStackCandidate("bodyweight","Bodyweight","bodyweight")) }
            .isFailure)
    }
}
