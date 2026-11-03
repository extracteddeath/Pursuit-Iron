package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeExerciseWeightEditorPolicyTest {
    private val policy=NativeExerciseWeightEditorPolicy
    private val candidates=listOf(
        NativeStackCandidate("leg-ext", "Leg Extension", "external_load"),
        NativeStackCandidate("hack-squat", "Hack Squat", "external_load"),
        NativeStackCandidate("cable-fly", "Cable Fly", "cable_stack"),
        NativeStackCandidate("machine-row", "Machine Row", "machine_stack")
    )
    private val draft=mapOf(
        "override:leg-ext" to "exact: 12.5, 17.5, 22.5",
        "override:cable-fly" to "range: 5, 7.5, 200"
    )

    @Test fun searchAndConfiguredFilterOnlySelectRealProgramCandidates() {
        assertEquals(listOf("leg-ext", "cable-fly"),
            policy.eligible(candidates, "", true, draft).map { it.id })
        assertEquals(listOf("hack-squat"),
            policy.eligible(candidates, "HACK", false, draft).map { it.id })
        assertEquals(listOf("cable-fly"),
            policy.eligible(candidates, "cable", true, draft).map { it.id })
        assertTrue(policy.eligible(candidates, "nonexistent", false, draft).isEmpty())
        assertEquals(2,policy.configuredCount(candidates,draft))
        assertEquals(2,policy.configuredCount(candidates+candidates[0],draft))
        assertEquals(listOf("hack-squat"),
            policy.eligible(candidates, "no match", true, draft,
                focusedId="hack-squat").map { it.id })
    }

    @Test fun ambiguousMachineIdentityRequiresExactUserLoggedWeights() {
        assertTrue(policy.exactOnly(candidates[0]))
        assertTrue(policy.exactOnly(candidates[1]))
        assertFalse(policy.exactOnly(candidates[2]))
        assertFalse(policy.exactOnly(candidates[3]))
        assertEquals("exact",policy.preferredKind(candidates[0],"range: 5,5,100"))
        assertEquals("exact",policy.preferredKind(candidates[0],""))
        assertEquals("range",policy.preferredKind(candidates[2],draft["override:cable-fly"]))
        assertEquals("exact",policy.preferredKind(candidates[2],"exact: 5,10"))
        assertEquals("exact: 12.5, 17.5",
            policy.formatEntry("exact","12.5, 17.5",true))
        assertThrows(IllegalArgumentException::class.java) {
            policy.formatEntry("range","5, 5, 100",true)
        }
    }

    @Test fun typedExerciseEntriesRoundTripThroughAuthoritativeInventoryCodec() {
        val external=policy.formatEntry("exact","12.5, 17.5, 22.5",true)
        val cable=policy.formatEntry("range","5, 7.5, 200",false)
        val entries=mapOf("override:leg-ext" to external,
            "override:cable-fly" to cable)
        val known=setOf("leg-ext","cable-fly")
        val result=NativeGymInventoryCodec.fromDraft(entries,"lb",known,setOf("leg-ext"))
        assertTrue(result.error.orEmpty(),result.valid)
        val parsed=checkNotNull(result.json)
        assertEquals(entries,NativeGymInventoryCodec.toDraft(parsed))
        assertFalse(NativeGymInventoryCodec.fromDraft(
            entries+("override:leg-ext" to "range: 5, 5, 100"),"lb",known,
            setOf("leg-ext")).valid)
        assertFalse(NativeGymInventoryCodec.fromDraft(
            entries+("override:cable-fly" to "range: 5, 0, 200"),"lb",known,
            setOf("leg-ext")).valid)
        assertTrue(NativeGymInventoryCodec.fromDraft(
            entries+("override:cable-fly" to ""), "lb", known,
            setOf("leg-ext")).valid)
        assertEquals(entries, mapOf("override:leg-ext" to external,
            "override:cable-fly" to cable))
    }

    @Test fun fieldNeverInventsWeightsAndLeavesExistingValuesIntact() {
        assertEquals("",policy.formatEntry("exact"," ",true))
        assertEquals("",policy.formatEntry("range"," ",false))
        assertEquals("range: 5, 7.5, 200",
            policy.formatEntry("range"," 5, 7.5, 200 ",false))
        assertEquals("12.5, 17.5, 22.5",
            policy.entryValues(draft.getValue("override:leg-ext")))
        assertEquals("5, 7.5, 200",
            policy.entryValues(draft.getValue("override:cable-fly")))
        assertEquals(draft["override:cable-fly"],"range: 5, 7.5, 200")
        assertThrows(IllegalArgumentException::class.java) {
            policy.formatEntry("unexpected","10",false)
        }
    }
}
