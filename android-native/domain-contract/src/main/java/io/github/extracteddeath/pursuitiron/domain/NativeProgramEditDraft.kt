package io.github.extracteddeath.pursuitiron.domain

import java.util.Collections

/**
 * Pure, non-persistable program edit workspace.
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
    val days: List<ReadOnlyProgramDay>
) {
    companion object {
        private fun freeze(days: List<ReadOnlyProgramDay>): List<ReadOnlyProgramDay> =
            Collections.unmodifiableList(days.map { day ->
                day.copy(exerciseIds=Collections.unmodifiableList(day.exerciseIds.toList()))
            })

        fun from(verified: ReadOnlyProgramPresentation.Structure): NativeProgramEditDraft {
            val p=verified.program
            require(p.days.size in 1..7 && p.id==p.reference.programId)
            val original=freeze(p.days)
            return NativeProgramEditDraft(p.reference,p.name,p.name,original,original)
        }
    }

    /** The owner must refresh evidence, including snapshot hash, before acting. */
    fun matchesCurrent(verified: ReadOnlyProgramPresentation.Structure): Boolean =
        reference==verified.program.reference &&
        verified.program.name==originalName &&
        verified.program.days==originalDays

    val isChanged: Boolean get() = name!=originalName || days!=originalDays

    private fun next(name: String=this.name, days: List<ReadOnlyProgramDay> = this.days):
        NativeProgramEditDraft =
        NativeProgramEditDraft(reference,originalName,name,originalDays,freeze(days))

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
        return next(days=updated)
    }

    fun reset(): NativeProgramEditDraft = next(
        name=originalName,days=originalDays
    )

    val changedDayCount: Int get()=days.zip(originalDays).count { (now,old)->now!=old }

    /** Never expose the draft to a store writer; the canonical editor is not wired. */
    private fun validLabel(value: String,max: Int)=
        value.isNotBlank() && value.length<=max && value.none { it.isISOControl() }
}
