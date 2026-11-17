package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CatalogMovement
import java.util.Locale

/**
 * Read-only variant vocabulary shared by Gym, Plan and active Train swaps.
 * Source family/variant/physical attachment IDs remain authoritative.
 * Never invent numeric bench angles, station equivalence or transferable loads.
 */
internal enum class NativeBenchAngle(val display:String) {
    FLAT("Flat"), LOW_INCLINE("Low incline"), INCLINE("Incline"),
    HIGH_INCLINE("High incline"), DECLINE("Decline")
}

internal fun nativeBenchAngle(
    variantId:String,facilities:Set<String> = emptySet()
):NativeBenchAngle? {
    val variant=variantId.lowercase(Locale.ROOT).replace('_','-')
    return when {
        variant.contains("high-incline") -> NativeBenchAngle.HIGH_INCLINE
        variant.contains("low-incline") -> NativeBenchAngle.LOW_INCLINE
        variant.contains("decline") || "decline-bench" in facilities ->
            NativeBenchAngle.DECLINE
        variant.contains("incline") || "incline-bench" in facilities ->
            NativeBenchAngle.INCLINE
        variant.contains("flat") || "flat-bench" in facilities ->
            NativeBenchAngle.FLAT
        else -> null
    }
}

/** Search and selection descriptors only; never imply a 15/30/45-degree setting. */
internal fun nativeVariantDescription(
    variantId:String,facilityIds:Set<String>,attachmentId:String?,
    role:String?=null
):String {
    val angle=nativeBenchAngle(variantId,facilityIds)
    val parts=buildList {
        if(angle!=null)add(angle.display)
        add(nativeLibraryWords(variantId))
        if(attachmentId!=null)
            add("Attachment: "+kotlinGymAttachmentLabel(attachmentId))
        if(role!=null)add(nativeLibraryWords(role))
    }
    return parts.distinct().joinToString(" · ")
}

internal fun nativeExerciseFamilyVariants(
    records:List<NativeKotlinExerciseLibraryRecord>,
    selectedExerciseId:String
):List<NativeKotlinExerciseLibraryRecord> {
    if(records.map{it.movement.exerciseId}.distinct().size!=records.size ||
        records.map{it.movement.familyId to it.movement.variantId}.distinct().size!=records.size)
        return emptyList()
    val current=records.singleOrNull {
        it.movement.exerciseId==selectedExerciseId
    } ?:return emptyList()
    return records.filter {
        it.movement.familyId==current.movement.familyId &&
            it.movement.exerciseId!=selectedExerciseId
    }.sortedWith(compareByDescending<NativeKotlinExerciseLibraryRecord>{it.eligible}
        .thenBy{it.label.lowercase(Locale.ROOT)}
        .thenBy{it.movement.exerciseId})
}

internal fun nativeExerciseVariantCategory(move:CatalogMovement):String =
    nativeVariantDescription(move.variantId,move.requiredFacilityIds,
        move.attachmentId,move.role.name)
