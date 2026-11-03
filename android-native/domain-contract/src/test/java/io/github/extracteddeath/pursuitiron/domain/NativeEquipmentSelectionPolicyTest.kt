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

    @Test fun rackFacilityDefaultsAndOldProgramsStayIntentionallyDistinct() {
        val fresh = listOf("barbell", "no-rack", "dumbbell")
        assertFalse(policy.initialRackAvailable(fresh))
        assertEquals(fresh, policy.forReview(fresh, rackAvailable = false))
        assertEquals(listOf("barbell", "dumbbell"),
            policy.forReview(fresh, rackAvailable = true))
        val old = listOf("barbell", "dumbbell")
        assertTrue(policy.initialRackAvailable(old))
        assertEquals(old, policy.forReview(old, rackAvailable = true))
        val oldExplicit = listOf("barbell", "rack", "dumbbell")
        assertTrue(policy.initialRackAvailable(oldExplicit))
        assertEquals(listOf("barbell", "dumbbell", "rack"),
            policy.forReview(oldExplicit, rackAvailable = true))
        assertEquals(listOf("barbell", "dumbbell", "no-rack"),
            policy.forReview(oldExplicit, rackAvailable = false))
    }

    @Test fun rackCannotExistWithoutBarbellAndOrphanLegacyChoiceIsRemovable() {
        val initial = listOf("rack", "dumbbell")
        assertFalse(policy.initialRackAvailable(initial))
        assertEquals(initial, policy.pickerSelections(initial))
        // The legacy chip stays visible when no barbell can expose its switch.
        assertEquals(listOf("dumbbell"), policy.toggle(
            policy.pickerSelections(initial), "rack", listOf("dumbbell")))
        assertEquals(listOf("dumbbell"), policy.forReview(initial, rackAvailable = true))
        assertEquals(listOf("dumbbell"),
            policy.forReview(listOf("dumbbell", "no-rack"), rackAvailable = false))
        val oldBarbell = listOf("barbell", "rack", "dumbbell")
        assertEquals(listOf("barbell", "dumbbell"),
            policy.pickerSelections(oldBarbell))
        assertEquals(listOf("barbell", "dumbbell"),
            policy.pickerSelections(listOf("barbell", "no-rack", "dumbbell")))
    }
}
