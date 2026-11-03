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

    fun selectedCount(
        selected: Collection<String>,
        available: Collection<String>
    ): Int = selected.toSet().intersect(available.toSet()).size

    fun selectedInGroup(
        selected: Collection<String>,
        group: NativeEquipmentGroup
    ): Int = group.choices.count { it.id in selected }
}
