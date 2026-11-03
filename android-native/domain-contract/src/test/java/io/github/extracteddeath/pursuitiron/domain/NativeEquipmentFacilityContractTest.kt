package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeEquipmentFacilityContractTest {
    @Test fun newProfilesNeverInferAnUnconfirmedSquatRack() {
        val fresh = NativeProgramProfile()
        assertTrue("barbell" in fresh.equipment)
        assertTrue("no-rack" in fresh.equipment)
        assertFalse("rack" in fresh.equipment)
        assertEquals(40, NativeEquipmentCatalog.allIds.size)
        assertFalse("no-rack" in NativeEquipmentCatalog.allIds)
        val restored = NativeProgramSetupCodec.readProfile(
            NativeProgramSetupCodec.profileJson(fresh))
        assertEquals(fresh.equipment.toSet(), restored.equipment.toSet())
        assertTrue("no-rack" in restored.equipment)
    }

    @Test fun explicitRackAbsenceIsValidOnlyWithBarbellAndWithoutPositiveRack() {
        val withNoRack = NativeProgramProfile(equipment = listOf(
            "barbell", "dumbbell", "no-rack"))
        val encoded = NativeProgramSetupCodec.profileJson(withNoRack)
        val decoded = NativeProgramSetupCodec.readProfile(encoded)
        assertEquals(setOf("barbell", "dumbbell", "no-rack"),
            decoded.equipment.toSet())
        for (bad in listOf(
            listOf("barbell", "rack", "no-rack"),
            listOf("dumbbell", "no-rack"),
            listOf("no-rack")
        )) {
            val raw = NativeProgramSetupCodec.profileJson(
                withNoRack.copy(equipment = bad))
            assertTrue("Contradictory or orphaned rack flag accepted: $bad",
                runCatching { NativeProgramSetupCodec.readProfile(raw) }.isFailure)
        }
    }

    @Test fun historicalPositiveRackIsStillReadableWithoutRewritingIt() {
        val old = NativeProgramProfile(equipment = listOf(
            "barbell", "rack", "bench", "dumbbell"))
        val raw = NativeProgramSetupCodec.profileJson(old)
        val read = NativeProgramSetupCodec.readProfile(raw)
        assertTrue("rack" in read.equipment)
        assertFalse("no-rack" in read.equipment)
        assertEquals(raw, NativeProgramSetupCodec.profileJson(read))
    }
}
