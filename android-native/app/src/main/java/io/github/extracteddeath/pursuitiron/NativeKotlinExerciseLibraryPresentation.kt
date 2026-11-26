package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomAuthoringSnapshot
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeGymExerciseExclusionChange
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Locale

internal const val nativeLibraryBodyweight="__bodyweight__"
internal enum class NativeLibraryAvailability { ALL, ELIGIBLE, EXCLUDED }
internal fun nativeLibraryWords(value:String)=value.replace('_',' ').replace('-',' ')
    .lowercase(Locale.ROOT).replaceFirstChar{it.uppercase()}

/** One discovery list; typed records never masquerade as repetition movements. */
internal sealed interface NativeExerciseLibraryEntry {
    val exerciseId:String
    val label:String
    val equipmentLabel:String
    val eligible:Boolean
    val savedExcluded:Boolean
    val searchText:String
    val gearIds:Set<String>
    val angle:NativeBenchAngle? get()=null
    val variantDescription:String
    val standard:CatalogMovement? get()=null
}

internal data class NativeKotlinExerciseLibraryRecord(
    val movement:CatalogMovement,override val label:String,override val equipmentLabel:String,
    override val eligible:Boolean,override val savedExcluded:Boolean,override val searchText:String
):NativeExerciseLibraryEntry {
    override val exerciseId get()=movement.exerciseId
    override val standard get()=movement
    override val gearIds:Set<String> get()=setOf(movement.equipmentId ?: nativeLibraryBodyweight)+
        movement.requiredFacilityIds+listOfNotNull(movement.attachmentId)
    override val angle:NativeBenchAngle? get()=
        nativeBenchAngle(movement.variantId,movement.requiredFacilityIds,
            movement.familyId)
    override val variantDescription:String get()=nativeExerciseVariantCategory(movement)
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
            move.equipmentId.orEmpty(),equipment,move.pattern.name,move.role.name,
            nativeExerciseVariantCategory(move))+

            move.requiredFacilityIds.flatMap{listOf(it,facilityLabel(it))}+
            listOfNotNull(move.attachmentId?.let{it+" "+attachmentLabel(it)})+
            move.credits.filter{it.fractionalSet.signum()>0}.map{it.muscle.name}
        NativeKotlinExerciseLibraryRecord(move,label,equipment,
            move.exerciseId in eligible,move.exerciseId in athlete.excludedExerciseIds,
            fields.joinToString(" "))
    }.sortedWith(compareBy<NativeKotlinExerciseLibraryRecord>{it.label.lowercase(Locale.ROOT)}
        .thenBy{it.movement.exerciseId})
}

internal data class NativeMeasuredLibraryRecord(
    val definition:MeasuredExerciseDefinition,override val equipmentLabel:String,
    override val eligible:Boolean,override val savedExcluded:Boolean,
    override val gearIds:Set<String>,private val searchFields:String
):NativeExerciseLibraryEntry {
    override val searchText get()="$searchFields $variantDescription"
    override val exerciseId get()=definition.exerciseId
    override val label get()=definition.displayName
    override val variantDescription get()=when(definition.kind) {
        MeasuredExerciseKind.HOLD_SECONDS -> "Timed hold"
        MeasuredExerciseKind.CARRY_CENTIMETERS -> "Distance carry"
        MeasuredExerciseKind.BANDED_REPETITIONS -> "Banded repetitions"
    }
}

internal fun nativeExerciseLibraryEntries(
    source:NativeCustomAuthoringSnapshot,equipmentLabel:(String)->String,
    facilityLabel:(String)->String,attachmentLabel:(String)->String
):List<NativeExerciseLibraryEntry> {
    val ordinary=nativeExerciseLibraryRecords(source,equipmentLabel,facilityLabel,attachmentLabel)
    if(ordinary.isEmpty())return emptyList()
    val athlete=source.setup.reviewed ?:return emptyList()
    val definitions=MeasuredExerciseCatalogDomain.all
    if(ordinary.any{MeasuredExerciseCatalogDomain.find(it.exerciseId)!=null})
        return emptyList()
    val eligible=MeasuredSessionAuthoringDomain.available(athlete).map{it.exerciseId}.toSet()
    val measured=definitions.map { definition ->
        val equipment=MeasuredPhysicalStockSource.equipmentIdFor(definition.exerciseId)
        val gear=definition.requiredFixtures+when(definition.loadKind) {
            MeasuredLoadKind.UNWEIGHTED ->setOf(nativeLibraryBodyweight)
            MeasuredLoadKind.BANDED_BODYWEIGHT ->setOf(nativeLibraryBodyweight,"bands")
            else ->setOfNotNull(equipment)
        }
        val label=equipment?.let(equipmentLabel) ?:if(
            definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT)"Resistance band + bodyweight"
            else "Bodyweight"
        NativeMeasuredLibraryRecord(definition,label,definition.exerciseId in eligible,
            definition.exerciseId in athlete.excludedExerciseIds,gear,
            (listOf(definition.displayName,definition.exerciseId,label,
                nativeLibraryWords(definition.kind.name),nativeLibraryWords(definition.loadKind.name))+
                gear.flatMap{listOf(it,facilityLabel(it))}).joinToString(" "))
    }
    return (ordinary+measured).sortedWith(compareBy<NativeExerciseLibraryEntry>{
        it.label.lowercase(Locale.ROOT)}.thenBy{it.exerciseId})
}

/** Search the full source before paging; each filter narrows that same result. */
internal fun <T:NativeExerciseLibraryEntry> filterNativeExerciseLibrary(
    records:List<T>,query:String,
    muscle:TrainingMuscle?,gearId:String?,availability:NativeLibraryAvailability,
    draftExclusions:Set<String>,pattern:ComposerPattern?=null,
    role:ComposerRole?=null,
    familyId:String?=null,angle:NativeBenchAngle?=null,
    attachmentId:String?=null
):List<T> {
    val byId=records.associateBy{it.exerciseId}
    if(byId.size!=records.size)return emptyList()
    val matches=CoachExerciseChoiceSearchDomain.filter(byId.keys.toList(),query) {
        checkNotNull(byId[it]).searchText
    }.toSet()
    return records.filter { record ->
        val id=record.exerciseId
        val move=record.standard
        id in matches && (pattern==null || move?.pattern==pattern) &&
            (role==null || move?.role==role) &&
            (familyId==null || move?.familyId==familyId) &&
            (angle==null || record.angle==angle) &&
            (attachmentId==null || move?.attachmentId==attachmentId) &&
            (muscle==null || move?.credits?.any{
            it.muscle==muscle && it.fractionalSet.signum()>0}==true) &&
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
