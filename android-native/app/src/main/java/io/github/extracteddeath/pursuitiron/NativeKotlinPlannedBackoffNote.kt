package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutSetSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetType
import io.github.extracteddeath.pursuitiron.rebuild.LoadBasis
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
    /**
     * This is a source-issued DIFFERENCE, never a coach-created reduction.
     * A smaller assistance counterweight is harder, unlike a lighter
     * barbell/dumbbell/stack. One formatter serves both journal types.
     */
    private fun issuedDifference(
        previous:DraftSetLoad.Verified,current:DraftSetLoad.Verified,
        workingIndex:Int,workingCount:Int,custom:Boolean
    ):String? {
        if(previous.unit!=current.unit || previous.basis!=current.basis ||
            current.value.signum()<0 ||
            current.value.compareTo(previous.value)==0)return null
        val from=previous.value.stripTrailingZeros().toPlainString()
        val to=current.value.stripTrailingZeros().toPlainString()
        val physical="${current.unit.name.lowercase()} · ${kotlinLoadBasisLabel(current.basis)}"
        val assisted=current.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT
        val moreAssistance=assisted && current.value>previous.value
        // Only assistance counterweights reverse the ordinary numeric
        // meaning of an authored lighter working load.
        if(!assisted && current.value>previous.value)return null
        val title=when {
            assisted && moreAssistance && custom -> "Authored higher-assistance working set"
            assisted && moreAssistance -> "Issued higher-assistance working set"
            assisted && custom -> "Authored lower-assistance working set"
            assisted -> "Issued lower-assistance working set"
            custom -> "Authored lighter working set"
            else -> "Issued lighter working set"
        }
        val explanation=when {
            moreAssistance -> "More counterweight means an easier assisted movement. "+
                "This planned reduction in difficulty was prescribed before logging, "+
                "not applied by fatigue coaching."
            assisted -> "Less counterweight means a harder movement, NOT a fatigue backoff. "+
                "This setting was prescribed before workout logging."
            custom -> "Saved before logging; the actual weight remains independent."
            else -> "Prescribed before this workout, not lowered by fatigue coaching."
        }
        return "$title · set ${workingIndex+1} of $workingCount: "+
            "$from → $to $physical. $explanation"
    }
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
        return issuedDifference(previous,current,index,working.size,custom=false)
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
        return issuedDifference(previous,current,index,working.size,custom=true)
    }
}
