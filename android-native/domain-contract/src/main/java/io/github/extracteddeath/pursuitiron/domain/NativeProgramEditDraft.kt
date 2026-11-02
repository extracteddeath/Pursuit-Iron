package io.github.extracteddeath.pursuitiron.domain

import java.util.Collections

/**
 * Immutable organization draft; only the canonical editor can prepare its save.
 *
 * The only accepted source is the verified CURRENT saved-program projection.
 * Every mutation preserves the identity and membership of all issued exercise
 * slots. This is not an authored program, an engine prescription, a candidate
 * canonical snapshot or consent to modify the personal store.
 */
class NativeProgramEditDraft private constructor(
    val reference: ReadOnlyProgramReference,
    val originalName: String,
    val name: String,
    val originalDays: List<ReadOnlyProgramDay>,
    val days: List<ReadOnlyProgramDay>,
    private val slotOrders: Map<String,List<Int>>
) {
    companion object {
        private fun freeze(days: List<ReadOnlyProgramDay>): List<ReadOnlyProgramDay> =
            Collections.unmodifiableList(days.map { day ->
                day.copy(exerciseIds=Collections.unmodifiableList(day.exerciseIds.toList()))
            })

        private fun orders(days: List<ReadOnlyProgramDay>) = freezeOrders(
            days.associate { it.id to it.exerciseIds.indices.toList() })
        private fun freezeOrders(orders: Map<String,List<Int>>): Map<String,List<Int>> =
            Collections.unmodifiableMap(orders.mapValues { Collections.unmodifiableList(it.value.toList()) })

        fun from(verified: ReadOnlyProgramPresentation.Structure): NativeProgramEditDraft {
            val p=verified.program
            require(p.days.size in 1..7 && p.id==p.reference.programId)
            val original=freeze(p.days)
            return NativeProgramEditDraft(p.reference,p.name,p.name,original,original,orders(original))
        }
    }

    /** The owner must refresh evidence, including snapshot hash, before acting. */
    fun matchesCurrent(verified: ReadOnlyProgramPresentation.Structure): Boolean =
        reference==verified.program.reference &&
        verified.program.name==originalName &&
        verified.program.days==originalDays

    val isChanged: Boolean get() = name!=originalName || days!=originalDays ||
        slotOrders.values.any { order -> order.withIndex().any { it.index!=it.value } }

    private fun next(name: String=this.name, days: List<ReadOnlyProgramDay> = this.days,
        orders: Map<String,List<Int>> = slotOrders):
        NativeProgramEditDraft =
        NativeProgramEditDraft(reference,originalName,name,originalDays,freeze(days),freezeOrders(orders))

    fun renameProgram(newName: String): NativeProgramEditDraft? =
        if(validLabel(newName,120)) next(name=newName) else null

    fun renameDay(dayId: String, label: String): NativeProgramEditDraft? {
        if(!validLabel(label,120)) return null
        val index=days.indexOfFirst { it.id==dayId }
        if(index<0) return null
        val updated=days.toMutableList()
        updated[index]=updated[index].copy(label=label)
        return next(days=updated)
    }

    /** Reorder whole days locally without changing any day or movement identity. */
    fun moveDay(from: Int, delta: Int): NativeProgramEditDraft? {
        if (delta !in listOf(-1, 1)) return null
        val target = from + delta
        if (from !in days.indices || target !in days.indices) return null
        val moved = days.toMutableList()
        val value = moved.removeAt(from)
        moved.add(target, value)
        return next(days = moved)
    }

    fun moveExercise(dayId: String, from: Int, delta: Int): NativeProgramEditDraft? {
        if(delta !in listOf(-1,1)) return null
        val day=days.indexOfFirst { it.id==dayId }
        if(day<0) return null
        val before=days[day].exerciseIds
        val target=from+delta
        if(from !in before.indices || target !in before.indices) return null
        val moved=before.toMutableList()
        val value=moved.removeAt(from)
        moved.add(target,value)
        val updated=days.toMutableList()
        updated[day]=updated[day].copy(exerciseIds=moved)
        val positions=slotOrders.getValue(dayId).toMutableList()
        val source=positions.removeAt(from)
        positions.add(target,source)
        return next(days=updated,orders=slotOrders+(dayId to positions))
    }

    /** Original slot identity survives even when the same movement occurs twice. */
    fun originalSlots(dayId: String): List<Int> = slotOrders.getValue(dayId)

    fun reset(): NativeProgramEditDraft = next(
        name=originalName,days=originalDays,orders=orders(originalDays)
    )

    val changedDayCount: Int get()=days.zip(originalDays).count { (now,old)->
        now!=old || originalSlots(now.id).withIndex().any { it.index!=it.value } }

    /** Labels are user data; controls and blank names cannot become saved labels. */
    private fun validLabel(value: String,max: Int)=
        value.isNotBlank() && value.length<=max && value.none { it.isISOControl() }
}
