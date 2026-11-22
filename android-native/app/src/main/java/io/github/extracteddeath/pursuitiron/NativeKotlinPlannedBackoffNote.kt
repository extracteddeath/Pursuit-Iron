package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutSetSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetType
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutSetPresentation
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutTarget

/**
 * Source-issued load difference only. Neither native workout snapshot
 * currently supplies a verified scheme label for these individual sets.
 * Never infer that any descending pyramid is a "back-off" or a fatigue
 * recommendation from weight shape alone. The original targets are the
 * evidence; athlete actuals and advisor proposals are separate authorities.
 *
 * Supports lower loads at any consecutively issued working ordinal, not just
 * the last, without guessing what training adaptation the program intended.
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
            working.map{it.target.setKey}.distinct().size!=working.size)
            return null
        val index=working.indexOfFirst{it.target.setKey==selected.setKey}
        if(index<1 || working[index].target!=selected)return null
        val previous=working[index-1].target.load as? DraftSetLoad.Verified
            ?:return null
        val current=selected.load as? DraftSetLoad.Verified ?:return null
        if(previous.unit!=current.unit || previous.basis!=current.basis ||
            current.value.signum()<0 ||
            current.value.compareTo(previous.value)>=0)return null
        val former=previous.value.stripTrailingZeros().toPlainString()
        val lighter=current.value.stripTrailingZeros().toPlainString()
        return "Issued lighter working set · set ${index+1} of ${working.size}: "+
            "$former → $lighter ${current.unit.name.lowercase()}. "+
            "Prescribed before this workout, not lowered by fatigue coaching."
    }

    /**
     * Custom source-authored variations have the same display boundary:
     * preserve set order, physical basis and existing slot identity, and
     * never describe a specific scheme that the signed sets do not name.
     */
    fun fromCustomSlot(
        slotSets:List<CustomWorkoutSetSnapshot>,
        selected:CustomWorkoutSetSnapshot
    ):String? {
        if(selected.prescription.type!=DraftSetType.WORKING ||
            slotSets.any{it.slotId!=selected.slotId} ||
            slotSets.map{it.setKey}.distinct().size!=slotSets.size)
            return null
        val firstWorking=slotSets.indexOfFirst{
            it.prescription.type==DraftSetType.WORKING
        }
        if(firstWorking<0 || slotSets.drop(firstWorking).any{
            it.prescription.type!=DraftSetType.WORKING
        })return null
        val working=slotSets.drop(firstWorking)
        if(working.size<2 || working.map{it.ordinalInSlot}!=
            (working.first().ordinalInSlot until
                working.first().ordinalInSlot+working.size).toList())
            return null
        val index=working.indexOfFirst{it.setKey==selected.setKey}
        if(index<1 || working[index]!=selected)return null
        val previous=working[index-1].prescription.load
            as? DraftSetLoad.Verified ?:return null
        val current=selected.prescription.load
            as? DraftSetLoad.Verified ?:return null
        if(previous.unit!=current.unit || previous.basis!=current.basis ||
            current.value.signum()<0 ||
            current.value.compareTo(previous.value)>=0)return null
        val former=previous.value.stripTrailingZeros().toPlainString()
        val lighter=current.value.stripTrailingZeros().toPlainString()
        return "Authored lighter working set · set ${index+1} of ${working.size}: "+
            "$former → $lighter ${current.unit.name.lowercase()}. "+
            "Saved before logging; the actual weight remains independent."
    }
}
