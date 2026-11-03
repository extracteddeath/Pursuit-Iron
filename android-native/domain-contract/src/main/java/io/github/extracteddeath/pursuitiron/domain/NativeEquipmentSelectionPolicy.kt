package io.github.extracteddeath.pursuitiron.domain

/**
 * Pure editing rules for the available-equipment chooser.
 *
 * Only IDs explicitly offered by the engine can be added. Removing a legacy
 * saved selection is always permitted; a group edit never touches unsupported
 * or previously saved IDs outside that group. Choices never imply physical
 * loading stock, attachments, variants or weight increments.
 */
object NativeEquipmentSelectionPolicy {
    fun toggle(
        selected: List<String>,
        id: String,
        available: Collection<String>
    ): List<String> {
        val next = selected.distinct().toMutableList()
        if (id in next) next.remove(id)
        else if (id in available) next.add(id)
        return next
    }

    fun setGroup(
        selected: List<String>,
        group: NativeEquipmentGroup,
        available: Collection<String>,
        include: Boolean
    ): List<String> {
        val permitted = available.toSet()
        val groupIds = group.choices.map { it.id }.filter { it in permitted }
        val next = selected.distinct().toMutableList()
        if (include) groupIds.filterNot { it in next }.forEach(next::add)
        else next.removeAll(groupIds.toSet())
        return next
    }

    /**
     * Legacy profiles without an explicit no-rack flag historically derived a
     * rack from barbell equipment. Do not reinterpret those saved choices.
     * Fresh profiles must explicitly carry no-rack until the athlete confirms
     * the facility in onboarding.
     */
    fun initialRackAvailable(selected: Collection<String>): Boolean =
        "barbell" in selected &&
            ("rack" in selected || "no-rack" !in selected)

    /**
     * For a newly reviewed candidate, use the supplied rack confirmation.
     * Keep all other selected IDs for canonical validation; do not silently
     * drop unknown legacy IDs or invent loading inventory. A positive old
     * rack token is only retained when it already existed in the profile.
     */
    fun forReview(selected: List<String>, rackAvailable: Boolean): List<String> {
        val base = selected.distinct().filterNot { it == "rack" || it == "no-rack" }
        if ("barbell" !in base) return base
        return when {
            !rackAvailable -> base + "no-rack"
            "rack" in selected -> base + "rack"
            else -> base
        }
    }

    /** The rack setting is its own facility switch, not a 41st catalog chip. */
    fun pickerSelections(selected: List<String>): List<String> =
        selected.distinct().filterNot { it == "no-rack" ||
            (it == "rack" && "barbell" in selected) }

    fun selectedCount(
        selected: Collection<String>,
        available: Collection<String>
    ): Int = selected.toSet().intersect(available.toSet()).size

    fun selectedInGroup(
        selected: Collection<String>,
        group: NativeEquipmentGroup
    ): Int = group.choices.count { it.id in selected }
}
