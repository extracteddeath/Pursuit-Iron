package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeGymLoadingFieldPolicyTest {
    private val policy = NativeGymLoadingFieldPolicy

    @Test fun newAthleteOnlySeesLoadFamiliesForSelectedEquipment() {
        assertEquals(emptyList<String>(), policy.visibleFields(emptyList(), emptyMap()))
        assertEquals(listOf("dumbbells"),
            policy.visibleFields(listOf("dumbbell"), emptyMap()))
        assertEquals(listOf("bar", "plates", "smith"),
            policy.visibleFields(listOf("barbell", "smith", "no-rack"), emptyMap()))
        assertEquals(listOf("cable", "machine"),
            policy.visibleFields(listOf("cable", "machine"), emptyMap()))
        assertFalse(policy.visibleFields(listOf("legpress", "legext"), emptyMap())
            .contains("machine"))
    }

    @Test fun deselectionNeverErasesPreviouslyMeasuredWeights() {
        val fields = mapOf("bar" to "45", "plates" to "25x1",
            "dumbbells" to "20,25", "smith" to "   ")
        assertEquals(listOf("bar", "plates", "dumbbells"),
            policy.visibleFields(listOf("dumbbell"), fields))
        assertEquals(listOf("bar", "plates"),
            policy.retainedUnselectedFields(listOf("dumbbell"), fields))
        assertEquals("45", fields["bar"])
        assertEquals("25x1", fields["plates"])
        assertEquals(listOf("bar", "plates", "dumbbells", "cable", "machine", "smith"),
            policy.visibleFields(null, emptyMap()))
        assertTrue(policy.retainedUnselectedFields(null, fields).isEmpty())
    }

    @Test fun specialtyMachinesRequireTheirOwnPostGenerationProfiles() {
        val specific = listOf("legpress", "hacksquat", "legext", "legcurl",
            "hipthrustmachine", "kickback", "abduction", "adduction",
            "machinepress", "pecdeck", "machineshoulder", "machinelatraise",
            "machinerow", "machinepullover", "machineshrug",
            "machinecurl", "machineext", "machinecrunch", "assisted")
        specific.forEach { id ->
            assertTrue("Missing machine-specific guidance for $id",
                policy.hasSpecialtyMachine(listOf(id)))
        }
        assertFalse(policy.hasSpecialtyMachine(listOf("dumbbell", "barbell",
            "no-rack", "machine", "cable", "smith")))
        assertFalse(policy.hasSpecialtyMachine(null))
        assertFalse(policy.hasSpecialtyMachine(emptyList()))
    }
}
