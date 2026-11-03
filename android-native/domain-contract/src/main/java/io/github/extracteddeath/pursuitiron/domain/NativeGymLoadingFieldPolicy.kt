package io.github.extracteddeath.pursuitiron.domain

/**
 * Presentation-only relevance of physical loading fields.
 *
 * Equipment availability is not proof of a load increment. This policy merely
 * avoids prompting for unrelated hardware in new onboarding. Any existing,
 * nonblank draft field remains visible and editable even when its equipment is
 * deselected, so changing a checkbox cannot silently discard known weights.
 *
 * A null equipment list means an existing saved-program inventory editor with
 * no authority to reinterpret which fixtures the program provides.
 */
object NativeGymLoadingFieldPolicy {
    private val fieldEquipment = mapOf(
        "bar" to "barbell",
        "plates" to "barbell",
        "dumbbells" to "dumbbell",
        "cable" to "cable",
        "machine" to "machine",
        "smith" to "smith"
    )
    val standardFields: List<String> = listOf(
        "bar", "plates", "dumbbells", "cable", "machine", "smith"
    )

    fun visibleFields(
        availableEquipment: Collection<String>?,
        draft: Map<String, String>
    ): List<String> {
        if (availableEquipment == null) return standardFields
        val available = availableEquipment.toSet()
        val hasBarbellDraft = !draft["bar"].isNullOrBlank() ||
            !draft["plates"].isNullOrBlank()
        return standardFields.filter { key ->
            fieldEquipment.getValue(key) in available ||
                !draft[key].isNullOrBlank() ||
                (key in setOf("bar", "plates") && hasBarbellDraft)
        }
    }

    fun retainedUnselectedFields(
        availableEquipment: Collection<String>?,
        draft: Map<String, String>
    ): List<String> {
        if (availableEquipment == null) return emptyList()
        val available = availableEquipment.toSet()
        val hasBarbellDraft = !draft["bar"].isNullOrBlank() ||
            !draft["plates"].isNullOrBlank()
        return standardFields.filter { key ->
            fieldEquipment.getValue(key) !in available &&
                (!draft[key].isNullOrBlank() ||
                    (key in setOf("bar", "plates") && hasBarbellDraft))
        }
    }

    /**
     * A specialty machine is NOT entitled to the generic "machine" loading
     * range. Its exercise-specific confirmed totals must be authored after
     * actual program exercise identities are available for validation.
     */
    fun hasSpecialtyMachine(availableEquipment: Collection<String>?): Boolean {
        if (availableEquipment == null) return false
        val specificMachineIds = NativeEquipmentCatalog.groups
            .filter { it.title in setOf(
                "Leg machines", "Hip & glute machines",
                "Upper-body machines", "Arm & core machines"
            ) }
            .flatMap { group -> group.choices.map { it.id } }
            .toSet() + "assisted"
        return availableEquipment.any { it in specificMachineIds }
    }
}
