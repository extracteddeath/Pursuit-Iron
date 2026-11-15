package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections
import kotlin.math.abs

enum class CustomSupersetIdeasStatus {
    READY, COACHING_OFF, SUPERSETS_OFF, SOURCE_UNRESOLVED,
    ANCHOR_UNAVAILABLE, ANCHOR_PROTECTED, NO_MATCH
}

data class CustomSupersetPartnerIdea(
    val slotId:String,val exerciseId:String,val workingSets:Int,
    val anchorWorkingSets:Int,
    /** Provisional catalog credits, not a physiological interference model. */
    val sharedSupportingMuscles:Set<TrainingMuscle>
) {
    val mayApplyWithoutReview:Boolean get()=false
    val mayTransferWeight:Boolean get()=false
}

data class CustomSupersetPartnerIdeas(
    val status:CustomSupersetIdeasStatus,val partners:List<CustomSupersetPartnerIdea> = emptyList()
)

/** Read-only ideas from movements ALREADY in this day. `available` must be
 * CustomExerciseAuthoringDomain.picker from the reviewed owner/gym/catalog.
 * No fatigue, time-saving, load equivalence or training approval is inferred.
 * Main/supporting lifts and overlapping direct targets remain manual choices. */
object CustomSupersetPartnerRankingDomain {
    private data class Targets(val direct:Set<TrainingMuscle>,val all:Set<TrainingMuscle>)
    private fun targets(option:CustomExercisePickerOption):Targets? {
        if(option.pattern==null || option.role==null || option.familyId.isBlank() ||
            option.variantId.isBlank() || option.muscleCredits.isEmpty() ||
            option.muscleCredits.map{it.muscle}.distinct().size!=option.muscleCredits.size ||
            option.muscleCredits.any{it.fractionalSet<=BigDecimal.ZERO || it.fractionalSet>BigDecimal.ONE})return null
        val direct=option.muscleCredits.filter{it.role==MuscleCreditRole.DIRECT}.map{it.muscle}.toSet()
        return if(direct.isEmpty())null else Targets(direct,option.muscleCredits.map{it.muscle}.toSet())
    }
    private fun accessory(slot:CustomExerciseSlot,option:CustomExercisePickerOption)=
        slot.role==ComposerRole.ACCESSORY && option.role==ComposerRole.ACCESSORY &&
            option.pattern in setOf(ComposerPattern.ISOLATION,ComposerPattern.CORE)
    private fun working(slot:CustomExerciseSlot)=slot.sets.count{it.type==DraftSetType.WORKING}

    fun rank(day:CustomTrainingDay,available:List<CustomExercisePickerOption>,
        anchorSlotId:String,editableGroupId:String?,allowSupersets:Boolean,
        coachingSuggestionsEnabled:Boolean):CustomSupersetPartnerIdeas {
        if(!coachingSuggestionsEnabled)return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.COACHING_OFF)
        if(!allowSupersets)return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.SUPERSETS_OFF)
        if(available.isEmpty() || available.size>CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            available.map{it.exerciseId}.distinct().size!=available.size ||
            day.slots.size !in 1..32 || day.slots.map{it.slotId}.distinct().size!=day.slots.size)
            return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED)
        val byId=available.associateBy{it.exerciseId}
        val anchorIndex=day.slots.indexOfFirst{it.slotId==anchorSlotId}
        val anchor=day.slots.getOrNull(anchorIndex)
            ?:return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.ANCHOR_UNAVAILABLE)
        val anchorOption=byId[anchor.exerciseId]
            ?:return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED)
        val anchorTargets=targets(anchorOption)
            ?:return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.SOURCE_UNRESOLVED)
        if(anchor.attachmentId!=anchorOption.attachmentId || working(anchor) !in 1..20 ||
            anchor.sets.size !in 1..20 ||
            (anchor.supersetGroup!=null && anchor.supersetGroup!=editableGroupId))
            return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.ANCHOR_UNAVAILABLE)
        if(!accessory(anchor,anchorOption))return CustomSupersetPartnerIdeas(CustomSupersetIdeasStatus.ANCHOR_PROTECTED)
        val anchorSets=working(anchor)
        val ranked=day.slots.withIndex().mapNotNull {indexed->
            val slot=indexed.value
            if(slot.slotId==anchorSlotId || slot.exerciseId==anchor.exerciseId || working(slot) !in 1..20 ||
                slot.sets.size !in 1..20 ||
                (slot.supersetGroup!=null && slot.supersetGroup!=editableGroupId))return@mapNotNull null
            val option=byId[slot.exerciseId] ?:return@mapNotNull null
            val credits=targets(option) ?:return@mapNotNull null
            if(!accessory(slot,option) || option.familyId==anchorOption.familyId ||
                slot.attachmentId!=option.attachmentId ||
                credits.direct.any{it in anchorTargets.all} || anchorTargets.direct.any{it in credits.all})
                return@mapNotNull null
            val idea=CustomSupersetPartnerIdea(slot.slotId,slot.exerciseId,working(slot),anchorSets,
                Collections.unmodifiableSet(credits.all.intersect(anchorTargets.all)))
            indexed.index to idea
        }.sortedWith(compareBy<Pair<Int,CustomSupersetPartnerIdea>>{it.second.sharedSupportingMuscles.size}
            .thenBy{abs(it.second.workingSets-anchorSets)}
            .thenBy{abs(it.first-anchorIndex)}.thenBy{if(it.first>anchorIndex)0 else 1}
            .thenBy{it.second.slotId}).map{it.second}
        return CustomSupersetPartnerIdeas(if(ranked.isEmpty())CustomSupersetIdeasStatus.NO_MATCH
            else CustomSupersetIdeasStatus.READY,Collections.unmodifiableList(ranked))
    }
}
