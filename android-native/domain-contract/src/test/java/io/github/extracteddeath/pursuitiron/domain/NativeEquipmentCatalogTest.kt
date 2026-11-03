package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeEquipmentCatalogTest {
    @Test fun eightGroupsCoverCanonicalEquipmentWithoutDuplicates() {
        assertEquals(listOf(
            "Free weights", "Bars", "Benches & stations",
            "Cables & general machines", "Leg machines",
            "Hip & glute machines", "Upper-body machines", "Arm & core machines"
        ), NativeEquipmentCatalog.groups.map { it.title })
        val all = NativeEquipmentCatalog.allIds
        assertEquals(40, all.size)
        assertEquals(40, all.toSet().size)
        assertEquals("dumbbell", all.first())
        val hips = NativeEquipmentCatalog.groups.single { it.title == "Hip & glute machines" }
        assertEquals(listOf("hipthrustmachine", "kickback", "abduction", "adduction"),
            hips.choices.map { it.id })
        assertEquals(all.indexOf("abduction") + 1, all.indexOf("adduction"))
        assertTrue("Do not offer legacy inferred rack as a real separate inventory choice",
            "rack" !in all)
    }

    @Test fun engineAvailabilityFiltersWithoutReorderingOrInventingItems() {
        assertTrue(NativeEquipmentCatalog.supported(emptyList()).isEmpty())
        val groups = NativeEquipmentCatalog.supported(
            listOf("adduction", "unknown-future-id", "dumbbell", "abduction")
        )
        assertEquals(listOf("Free weights", "Hip & glute machines"),
            groups.map { it.title })
        assertEquals(listOf("dumbbell", "abduction", "adduction"),
            groups.flatMap { it.choices }.map { it.id })
        assertEquals(40, NativeEquipmentCatalog.allIds.size)
    }
}
