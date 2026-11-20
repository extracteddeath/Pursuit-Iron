package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetType
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutSetPresentation
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutTarget

/**
 * Read-only label for a *source-issued* final lower-load working set.
 *
 * The generated prescription author is the only authority for these
 * immutable targets. The display must not infer a downstep from actuals,
 * readiness, a coach suggestion, a different exercise or physical machine.
 */
internal object NativeKotlinPlannedBackoffNote {
    fun fromIssuedTargets(
        all:List<KotlinWorkoutSetPresentation>,
        selected:KotlinWorkoutTarget
    ):String? {
        if(selected.type!=DraftSetType.WORKING)return null
        val working=all.filter{
            it.target.exerciseId==selected.exerciseId &&
                it.target.type==DraftSetType.WORKING
        }.sortedBy{it.target.ordinal}
        if(working.size<2 ||
            working.map{it.target.ordinal}.distinct().size!=working.size ||
            working.last().target.setKey!=selected.setKey)
            return null
        val before=working.dropLast(1).map{
            it.target.load as? DraftSetLoad.Verified ?:return null
        }
        val last=selected.load as? DraftSetLoad.Verified ?:return null
        val base=before.first()
        if(before.any{
            it.unit!=base.unit || it.basis!=base.basis ||
                it.value.compareTo(base.value)!=0
        } || last.unit!=base.unit || last.basis!=base.basis ||
            last.value.signum()<0 || last.value>=base.value)
            return null
        val firstLoad=base.value.stripTrailingZeros().toPlainString()
        val finalLoad=last.value.stripTrailingZeros().toPlainString()
        return "Planned last-set back-off · set ${working.size} of ${working.size}: "+
            "$firstLoad → $finalLoad ${last.unit.name.lowercase()}. "+
            "Authored before this workout; not an automatic weight drop."
    }
}
