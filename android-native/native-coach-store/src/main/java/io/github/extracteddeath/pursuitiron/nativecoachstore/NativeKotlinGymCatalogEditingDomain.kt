package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/**
 * Native gym/catalog authoring, independent of the legacy program inventory.
 *
 * Every edit creates a separately reviewable SOURCE proposal. It never saves
 * training targets, infers a stack increment, silently changes load units,
 * selects exercises or authorizes a workout. Only the source file authority
 * can persist a proposal with its owner-scoped revision CAS.
 */
enum class NativeGymCatalogEditRefusal {
    STALE_SOURCE, INVALID_EQUIPMENT, INVALID_GYM, INVALID_CATALOG,
    ATHLETE_CONFLICT
}
sealed interface NativeGymCatalogEdit {
    data class Proposed(
        val source: NativeCustomAuthoringSnapshot,
        val change: String
    ) : NativeGymCatalogEdit
    data class Rejected(val reason: NativeGymCatalogEditRefusal) :
        NativeGymCatalogEdit
}

object NativeKotlinGymCatalogEditingDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero=BigDecimal.ZERO

    /** Fully preserves all athlete intent not being edited. */
    private fun draft(profile: ReviewedAthleteSetup, gym: GymProfileDraft) =
        AthleteSetupDraft(
            profileRevision=profile.profileRevision,
            athleteProfileId=profile.athleteProfileId,
            goal=profile.goal,experience=profile.experience,
            split=profile.split,trainingWeekdays=profile.trainingWeekdays,
            timezoneId=profile.timezoneId,
            sessionTimeBucket=profile.sessionTimeBucket,
            hardSessionLimit=profile.hardSessionLimit,
            gym=gym,allowBodyweight=profile.allowBodyweight,
            allowSupersets=profile.allowSupersets,
            allowTricepsOnLower=profile.allowTricepsOnLower,
            maxBarbellExercisesPerDay=profile.maxBarbellExercisesPerDay,
            focusedMuscles=profile.focusedMuscles,
            reducedMuscles=profile.reducedMuscles,
            excludedExerciseIds=profile.excludedExerciseIds,
            lockedTrainingDays=profile.lockedTrainingDays,
            hardTrainingDays=profile.hardTrainingDays,
            longTrainingDays=profile.longTrainingDays,
            declaredRestDays=profile.declaredRestDays
        )

    private fun baseGym(gym: ReviewedGymProfile) =
        GymProfileDraft(gym.gymId,gym.unit,gym.confirmedStock,
            gym.availableFacilities,gym.availableAttachments)

    private fun check(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long
    ): NativeGymCatalogEditRefusal? =
        if (source.revision!=expectedRevision ||
            expectedRevision !in 1..9_007_199_254_740_990L)
            NativeGymCatalogEditRefusal.STALE_SOURCE
        else if(source.setup.reviewed?.athleteProfileId !=source.athleteId ||
            source.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW)
            NativeGymCatalogEditRefusal.ATHLETE_CONFLICT
        else null

    private fun gymEdit(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long,
        name: String,
        operation: (ReviewedGymProfile)->GymProfileDraft?
    ): NativeGymCatalogEdit {
        check(source,expectedRevision)?.let {
            return NativeGymCatalogEdit.Rejected(it)
        }
        val athlete=checkNotNull(source.setup.reviewed)
        val candidate=operation(athlete.gym)
            ?:return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_GYM)
        val reviewed=CoachAthleteSetupDomain.review(draft(athlete,candidate))
        if(reviewed.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            reviewed.reviewed==null)
            return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_GYM)
        return NativeGymCatalogEdit.Proposed(
            source.copy(revision=expectedRevision+1,setup=reviewed),name)
    }

    /**
     * Adds/replaces only one explicitly confirmed physical stock entry.
     * A separate equipment ID may represent one distinct machine stack;
     * there is no assumption that two machines share increments or loads.
     */
    fun upsertStock(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long,
        equipmentId: String,
        stock: VerifiedLoadStock
    ): NativeGymCatalogEdit {
        if(!id.matches(equipmentId) ||
            EquipmentLoadDomain.inspect(stock,zero,stock.unit)
                is LoadInspection.Unavailable)
            return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_EQUIPMENT)
        return gymEdit(source,expectedRevision,"upsert-verified-equipment") { gym ->
            if(stock.unit!=gym.unit || (gym.confirmedStock.size>=128 &&
                equipmentId !in gym.confirmedStock))
                null
            else baseGym(gym).copy(confirmedStock=
                gym.confirmedStock + (equipmentId to stock))
        }
    }

    /** Physical removal is allowed; the picker subsequently excludes users
     * of that implement. Existing saved authored sets remain unchanged. */
    fun removeStock(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long,
        equipmentId: String
    ): NativeGymCatalogEdit {
        if(!id.matches(equipmentId))
            return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_EQUIPMENT)
        return gymEdit(source,expectedRevision,"remove-verified-equipment") { gym ->
            if(equipmentId !in gym.confirmedStock) null
            else baseGym(gym).copy(
                confirmedStock=gym.confirmedStock - equipmentId)
        }
    }

    /** Every available fixture and attachment is a separately checked fact.
     * Do not infer one from an exercise name or installed stock. */
    fun setFacilitiesAndAttachments(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long,
        facilities: Set<String>,
        attachments: Set<String>
    ): NativeGymCatalogEdit {
        if(facilities.size>128 || attachments.size>128 ||
            facilities.any { !id.matches(it) } ||
            attachments.any { !id.matches(it) })
            return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_GYM)
        return gymEdit(source,expectedRevision,"set-verified-facilities") { gym ->
            baseGym(gym).copy(
                availableFacilities=facilities,
                availableAttachments=attachments)
        }
    }

    /**
     * Catalog curation has to preserve exact stable family/variant IDs,
     * explicit implement, fixture, attachment, muscle credit and review
     * reference. No conversion from legacy display-name metadata.
     */
    fun replaceCuratedCatalog(
        source: NativeCustomAuthoringSnapshot,
        expectedRevision: Long,
        catalog: List<CatalogMovement>
    ): NativeGymCatalogEdit {
        check(source,expectedRevision)?.let {
            return NativeGymCatalogEdit.Rejected(it)
        }
        if(catalog.isEmpty() || catalog.size>CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            catalog.map{it.exerciseId}.distinct().size!=catalog.size ||
            catalog.map{it.familyId to it.variantId}.distinct().size!=catalog.size ||
            catalog.any { move ->
                !id.matches(move.exerciseId) ||
                !id.matches(move.familyId) || !id.matches(move.variantId) ||
                (move.attachmentId?.let { !id.matches(it) } == true) ||
                (move.equipmentId?.let { !id.matches(it) } == true) ||
                move.equipmentId==null && move.expectedLoadBasis!=null ||
                move.equipmentId!=null && move.expectedLoadBasis==null ||
                move.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                move.creditReviewReference.isNullOrBlank() ||
                move.creditReviewReference?.let{id.matches(it)} != true ||
                move.credits.isEmpty() ||
                move.credits.size>TrainingMuscle.entries.size ||
                move.credits.map{it.muscle}.distinct().size!=move.credits.size ||
                move.credits.any { it.fractionalSet<=zero ||
                    it.fractionalSet>BigDecimal.ONE ||
                    it.fractionalSet.stripTrailingZeros().scale()>3 } ||
                move.permittedFocus.isEmpty() ||
                move.requiredFacilityIds.size>8 ||
                move.requiredFacilityIds.any {!id.matches(it)} ||
                move.minimumRecoveryGroupDayGap !in 1..6 ||
                (move.recoveryGroupId==null &&
                    move.minimumRecoveryGroupDayGap!=1) ||
                (move.recoveryGroupId?.let { !id.matches(it) } == true) ||
                (move.sourcePracticalReps?.let { reps ->
                    reps.minimum !in 1..100 ||
                        reps.maximum !in reps.minimum..100
                } == true)
            })
            return NativeGymCatalogEdit.Rejected(
                NativeGymCatalogEditRefusal.INVALID_CATALOG)
        return NativeGymCatalogEdit.Proposed(
            source.copy(revision=expectedRevision+1,
                catalog=Collections.unmodifiableList(catalog.map{it.copy(
                    permittedFocus=Collections.unmodifiableSet(it.permittedFocus.toSet()),
                    requiredFacilityIds=Collections.unmodifiableSet(
                        it.requiredFacilityIds.toSet()),
                    credits=Collections.unmodifiableList(it.credits.map{credit ->
                        credit.copy()
                    })
                )})),
            "replace-curated-exercise-catalog")
    }
}
