package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeEquipmentSelectionPolicyTest {
    private val policy = NativeEquipmentSelectionPolicy
    private val hips = NativeEquipmentCatalog.groups.single {
        it.title == "Hip & glute machines"
    }

    @Test fun groupSelectionIsScopedToOfferedIds() {
        val available = listOf("dumbbell", "abduction", "adduction", "kickback")
        val original = listOf("rack", "dumbbell", "abduction")
        val grouped = policy.setGroup(original, hips, available, include = true)
        assertEquals(listOf("rack", "dumbbell", "abduction", "kickback", "adduction"), grouped)
        assertFalse("Unlisted hip thrust machine cannot be invented",
            "hipthrustmachine" in grouped)
        val cleared = policy.setGroup(grouped, hips, available, include = false)
        assertEquals(listOf("rack", "dumbbell"), cleared)
        assertEquals(original, listOf("rack", "dumbbell", "abduction"))
    }

    @Test fun unsupportedAndLegacyEquipmentAreNeverInsertedByToggle() {
        val available = listOf("abduction", "adduction")
        val start = listOf("rack", "abduction")
        assertEquals(start, policy.toggle(start, "not-real", available))
        assertEquals(start, policy.toggle(start, "hipthrustmachine", available))
        assertEquals(listOf("abduction"), policy.toggle(start, "rack", available))
        assertEquals(listOf("rack", "abduction", "adduction"),
            policy.toggle(start, "adduction", available))
        assertEquals(listOf("rack"), policy.toggle(start, "abduction", available))
    }

    @Test fun groupCountsReflectSelectionNotStoredPhysicalLoading() {
        val selected = listOf("rack", "abduction", "adduction", "adduction", "dumbbell")
        val available = listOf("abduction", "adduction", "dumbbell")
        assertEquals(3, policy.selectedCount(selected, available))
        assertEquals(2, policy.selectedInGroup(selected, hips))
        assertEquals(4, hips.choices.size)
        assertEquals(40, NativeEquipmentCatalog.allIds.size)
    }

    @Test fun duplicateInputCannotCreateDuplicateSelections() {
        val available = listOf("abduction", "adduction", "kickback")
        val start = listOf("abduction", "abduction", "rack", "rack")
        assertEquals(listOf("rack"), policy.toggle(start, "abduction", available))
        assertEquals(listOf("abduction", "rack", "kickback", "adduction"),
            policy.setGroup(start, hips, available, include = true))
        assertEquals(listOf("rack"),
            policy.setGroup(start, hips, available, include = false))
    }
}
