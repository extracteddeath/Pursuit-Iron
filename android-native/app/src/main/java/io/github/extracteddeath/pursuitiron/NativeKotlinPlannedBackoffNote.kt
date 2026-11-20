package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutSetSnapshot
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
            working.map{it.target.ordinal}!=(0 until working.size).toList() ||
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

    /**
     * A custom athlete-authored slot is not the generated LAST_SET_BACKOFF
     * scheme. Say only what the signed original slot actually prescribes.
     * Never compare to another slot/exercise, a logged actual or a follow-up.
     */
    fun fromCustomSlot(
        slotSets:List<CustomWorkoutSetSnapshot>,
        selected:CustomWorkoutSetSnapshot
    ):String? {
        if(selected.prescription.type!=DraftSetType.WORKING)return null
        val working=slotSets.filter{
            it.prescription.type==DraftSetType.WORKING
        }
        if(working.size<2 || working.last()!=selected ||
            working.map{it.ordinalInSlot}!=
                (working.first().ordinalInSlot until
                    working.first().ordinalInSlot+working.size).toList())
            return null
        val earlier=working.dropLast(1).map{
            it.prescription.load as? DraftSetLoad.Verified ?:return null
        }
        val final=selected.prescription.load as? DraftSetLoad.Verified
            ?:return null
        val base=earlier.first()
        if(earlier.any{
            it.unit!=base.unit || it.basis!=base.basis ||
                it.value.compareTo(base.value)!=0
        } || final.unit!=base.unit || final.basis!=base.basis ||
            final.value.signum()<0 || final.value>=base.value)
            return null
        val original=base.value.stripTrailingZeros().toPlainString()
        val lighter=final.value.stripTrailingZeros().toPlainString()
        return "Authored lighter final set · set ${working.size} of ${working.size}: "+
            "$original → $lighter ${final.unit.name.lowercase()}. "+
            "Your original target was written before logging; actual weight stays separate."
    }
}
