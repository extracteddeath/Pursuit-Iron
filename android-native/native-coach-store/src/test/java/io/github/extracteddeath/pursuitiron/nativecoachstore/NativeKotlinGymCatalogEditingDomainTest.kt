package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinGymCatalogEditingDomainTest {
    private fun n(text: String) = BigDecimal(text)
    private fun setup():AthleteSetupReview =
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1", athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.POWERBUILDING,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.UPPER_LOWER,
            trainingWeekdays=setOf(0,1,3,5),
            timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("cable" to VerifiedLoadStock.Stack(LoadUnit.LB,
                    LoadBasis.CABLE_STACK,n("5"),n("5"),n("100"))),
                setOf("cable-station"),setOf("rope")),
            allowBodyweight=true,allowSupersets=true,
            allowTricepsOnLower=true,maxBarbellExercisesPerDay=2,
            focusedMuscles=setOf(TrainingMuscle.TRICEPS),
            reducedMuscles=setOf(TrainingMuscle.CALVES),
            excludedExerciseIds=emptySet(),
            lockedTrainingDays=setOf(0),
            hardTrainingDays=setOf(3),
            longTrainingDays=setOf(5),
            declaredRestDays=setOf(2,4,6)
        ))
    private fun catalog()=listOf(CatalogMovement(
        "rope-pushdown","pushdown","rope","rope",
        ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.UPPER),
        "cable",LoadBasis.CABLE_STACK,
        listOf(DeclaredMuscleCredit(TrainingMuscle.TRICEPS,
            MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
        "review-1",setOf("cable-station"),RepBand(8,15)
    ))
    private fun source()=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",1,setup(),catalog())
    private fun proposed(result:NativeGymCatalogEdit) =
        (result as NativeGymCatalogEdit.Proposed).source
    private fun refused(result:NativeGymCatalogEdit) =
        (result as NativeGymCatalogEdit.Rejected).reason

    @Test fun actualLoadEditRetainsEveryNonGymPreferenceAndCatalogIdentity() {
        val original=source()
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,
            original.setup.status)
        val newStock=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("10"),n("12.5"),n("15")))
        val changed=proposed(NativeKotlinGymCatalogEditingDomain.upsertStock(
            original,1,"dumbbells",newStock))
        assertEquals(2L,changed.revision)
        assertEquals(newStock,changed.setup.reviewed!!.gym
            .confirmedStock.getValue("dumbbells"))
        assertEquals(original.catalog,changed.catalog)
        val before=original.setup.reviewed!!
        val after=changed.setup.reviewed!!
        assertEquals(before.goal,after.goal)
        assertEquals(before.trainingWeekdays,after.trainingWeekdays)
        assertEquals(before.focusedMuscles,after.focusedMuscles)
        assertEquals(before.declaredRestDays,after.declaredRestDays)
        assertEquals(before.hardSessionLimit,after.hardSessionLimit)
        assertEquals(before.gym.availableAttachments,after.gym.availableAttachments)
        assertEquals(NativeGymCatalogEditRefusal.STALE_SOURCE,
            refused(NativeKotlinGymCatalogEditingDomain.upsertStock(
                changed,1,"dumbbells",newStock)))
    }

    @Test fun invalidUnitsAndImpossibleEquipmentSettingsAreRejected() {
        val original=source()
        assertEquals(NativeGymCatalogEditRefusal.INVALID_GYM,
            refused(NativeKotlinGymCatalogEditingDomain.upsertStock(
                original,1,"dumbbells",VerifiedLoadStock.Totals(
                    LoadUnit.KG,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n("10"))))))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EQUIPMENT,
            refused(NativeKotlinGymCatalogEditingDomain.upsertStock(
                original,1,"invalid ID",VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                    listOf(n("10"))))))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_EQUIPMENT,
            refused(NativeKotlinGymCatalogEditingDomain.upsertStock(
                original,1,"unavailable",VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n("25"),n("25.0"))))))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_GYM,
            refused(NativeKotlinGymCatalogEditingDomain.removeStock(
                original,1,"not-present").let {
                // Removing an unknown implement is not allowed.
                it
            }))
    }

    @Test fun removingGymStockDoesNotChangeApprovedExercisesOrInventSubstitute() {
        val before=source()
        assertEquals(listOf("rope-pushdown"),
            CustomExerciseAuthoringDomain.picker(before.setup,before.catalog)
                .map{it.exerciseId})
        val removed=proposed(NativeKotlinGymCatalogEditingDomain.removeStock(
            before,1,"cable"))
        assertEquals(before.catalog,removed.catalog)
        assertTrue(removed.setup.reviewed!!.gym.confirmedStock.isEmpty())
        assertTrue(CustomExerciseAuthoringDomain.picker(
            removed.setup,removed.catalog).isEmpty())
        assertEquals(NativeGymCatalogEditRefusal.INVALID_GYM,
            refused(NativeKotlinGymCatalogEditingDomain.removeStock(
                before,1,"unknown")))
    }

    @Test fun attachmentAndFacilityEditsHideIncompatibleVariantsWithoutRewritingThem() {
        val before=source()
        val noRope=proposed(
            NativeKotlinGymCatalogEditingDomain.setFacilitiesAndAttachments(
                before,1,setOf("cable-station"),emptySet()))
        assertTrue(CustomExerciseAuthoringDomain.picker(
            noRope.setup,noRope.catalog).isEmpty())
        assertEquals(before.catalog,noRope.catalog)
        val restored=proposed(
            NativeKotlinGymCatalogEditingDomain.setFacilitiesAndAttachments(
                noRope,2,setOf("cable-station"),setOf("rope")))
        assertEquals(listOf("rope-pushdown"),
            CustomExerciseAuthoringDomain.picker(
                restored.setup,restored.catalog).map{it.exerciseId})
        val withoutStation=proposed(
            NativeKotlinGymCatalogEditingDomain.setFacilitiesAndAttachments(
                before,1,emptySet(),setOf("rope")))
        assertTrue(CustomExerciseAuthoringDomain.picker(
            withoutStation.setup,withoutStation.catalog).isEmpty())
    }

    @Test fun authoredCatalogMutationsRequireCreditsAndHumanReviewReference() {
        val before=source()
        val next=proposed(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
            before,1,catalog()+catalog().single().copy(
                exerciseId="straight-pushdown",variantId="straight",
                attachmentId="straight-bar")))
        assertEquals(2L,next.revision)
        assertEquals(2,next.catalog.size)
        assertEquals(before.setup,next.setup)
        assertEquals(NativeGymCatalogEditRefusal.INVALID_CATALOG,
            refused(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
                before,1,listOf(catalog().single().copy(
                    creditStatus=CatalogCreditStatus.UNREVIEWED)))))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_CATALOG,
            refused(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
                before,1,catalog()+catalog())))
        assertEquals(NativeGymCatalogEditRefusal.INVALID_CATALOG,
            refused(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
                before,1,listOf(catalog().single().copy(
                    credits=emptyList())))))
        assertEquals(NativeGymCatalogEditRefusal.STALE_SOURCE,
            refused(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
                next,1,catalog())))
    }
}
