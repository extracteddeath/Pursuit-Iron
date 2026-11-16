package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomAuthoringSnapshot
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeGymExerciseExclusionChange
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Locale

internal const val nativeLibraryBodyweight="__bodyweight__"
internal enum class NativeLibraryAvailability { ALL, ELIGIBLE, EXCLUDED }
internal fun nativeLibraryWords(value:String)=value.replace('_',' ').replace('-',' ')
    .lowercase(Locale.ROOT).replaceFirstChar{it.uppercase()}

internal data class NativeKotlinExerciseLibraryRecord(
    val movement:CatalogMovement,val label:String,val equipmentLabel:String,
    val eligible:Boolean,val savedExcluded:Boolean,val searchText:String
) {
    val gearIds:Set<String> get()=setOf(movement.equipmentId ?: nativeLibraryBodyweight)+
        movement.requiredFacilityIds+listOfNotNull(movement.attachmentId)
}

/** Display facts from one saved source. Eligibility never grants a prescription. */
internal fun nativeExerciseLibraryRecords(
    source:NativeCustomAuthoringSnapshot,
    equipmentLabel:(String)->String,
    facilityLabel:(String)->String,
    attachmentLabel:(String)->String
):List<NativeKotlinExerciseLibraryRecord> {
    val athlete=source.setup.reviewed ?:return emptyList()
    if(source.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
        athlete.athleteProfileId!=source.athleteId || source.revision<1 ||
        source.catalog.size>CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
        source.catalog.map{it.exerciseId}.distinct().size!=source.catalog.size ||
        source.catalog.map{it.familyId to it.variantId}.distinct().size!=source.catalog.size)
        return emptyList()
    val eligible=CustomExerciseAuthoringDomain.picker(source.setup,source.catalog)
        .map{it.exerciseId}.toSet()
    return source.catalog.map { move ->
        val label=kotlinExerciseLabel(move.exerciseId)
        val equipment=move.equipmentId?.let(equipmentLabel) ?: "Bodyweight"
        val fields=listOf(label,move.exerciseId,move.familyId,move.variantId,
            move.equipmentId.orEmpty(),equipment,move.pattern.name,move.role.name)+
            move.requiredFacilityIds.flatMap{listOf(it,facilityLabel(it))}+
            listOfNotNull(move.attachmentId?.let{it+" "+attachmentLabel(it)})+
            move.credits.filter{it.fractionalSet.signum()>0}.map{it.muscle.name}
        NativeKotlinExerciseLibraryRecord(move,label,equipment,
            move.exerciseId in eligible,move.exerciseId in athlete.excludedExerciseIds,
            fields.joinToString(" "))
    }.sortedWith(compareBy<NativeKotlinExerciseLibraryRecord>{it.label.lowercase(Locale.ROOT)}
        .thenBy{it.movement.exerciseId})
}

/** Search the full source before paging; each filter narrows that same result. */
internal fun filterNativeExerciseLibrary(
    records:List<NativeKotlinExerciseLibraryRecord>,query:String,
    muscle:TrainingMuscle?,gearId:String?,availability:NativeLibraryAvailability,
    draftExclusions:Set<String>,pattern:ComposerPattern?=null,
    role:ComposerRole?=null
):List<NativeKotlinExerciseLibraryRecord> {
    val byId=records.associateBy{it.movement.exerciseId}
    if(byId.size!=records.size)return emptyList()
    val matches=CoachExerciseChoiceSearchDomain.filter(byId.keys.toList(),query) {
        checkNotNull(byId[it]).searchText
    }.toSet()
    return records.filter { record ->
        val id=record.movement.exerciseId
        id in matches && (pattern==null || record.movement.pattern==pattern) &&
            (role==null || record.movement.role==role) &&
            (muscle==null || record.movement.credits.any{
            it.muscle==muscle && it.fractionalSet.signum()>0}) &&
            (gearId==null || gearId in record.gearIds) && when(availability) {
                NativeLibraryAvailability.ALL -> true
                NativeLibraryAvailability.ELIGIBLE -> record.eligible && id !in draftExclusions
                NativeLibraryAvailability.EXCLUDED -> id in draftExclusions
            }
    }
}

/** Session-local Undo is retained only against the exact saved source. */
internal data class NativeKotlinExerciseExclusionCommit(
    val request:NativeGymExerciseExclusionChange,val savedSource:NativeCustomAuthoringSnapshot
) {
    fun matches(source:NativeCustomAuthoringSnapshot?):Boolean = source==savedSource &&
        savedSource.ownerKey==request.ownerKey && savedSource.athleteId==request.athleteId &&
        savedSource.revision==request.sourceRevision+1 &&
        savedSource.setup.reviewed?.excludedExerciseIds==request.next
    fun undoRequest()=request.copy(sourceRevision=savedSource.revision,
        previous=request.next.toSet(),next=request.previous.toSet())
}
