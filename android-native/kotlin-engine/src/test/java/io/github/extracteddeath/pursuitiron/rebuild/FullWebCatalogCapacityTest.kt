package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * A web-sized identity inventory must be usable throughout the ONE Kotlin
 * catalog/selection/first-program pipeline. These synthetic unavailable
 * identities test capacity only; they are not certified exercise metadata.
 * Selection and workout work remain limited by verified stock and dose.
 */
class FullWebCatalogCapacityTest {
    private fun n(value:String)=BigDecimal(value)

    private fun expanded(
        baseline:List<CatalogMovement>,total:Int
    ):List<CatalogMovement> {
        require(total>=baseline.size)
        val prototype=baseline.first()
        val additional=(1..(total-baseline.size)).map {i->
            prototype.copy(
                exerciseId="unavailable-web-identity-$i",
                familyId="unavailable-web-family-$i",
                variantId="provisional",
                equipmentId="unavailable-physical-station",
                expectedLoadBasis=LoadBasis.EXERCISE_OVERRIDE,
                requiredFacilityIds=emptySet())
        }
        return baseline+additional
    }

    @Test fun firstProgramAccepts376IdentitiesWithoutMakingUpEquipment() {
        val source=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val original=CoachFirstProgramStartDomain.review(source)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            original.status)
        val full=expanded(source.catalog,376)
        val reviewed=CoachFirstProgramStartDomain.review(
            source.copy(catalog=full))
        assertEquals(376,full.size)
        assertEquals(original.status,reviewed.status)
        assertEquals(original.chosenExerciseIds,reviewed.chosenExerciseIds)
        assertEquals(original.unsupportedMuscles,reviewed.unsupportedMuscles)
        assertTrue(checkNotNull(reviewed.prescription)
            .finalAudit!!.satisfiesDeclaredHardConstraints)
        assertTrue(reviewed.chosenExerciseIds.none{
            it.startsWith("unavailable-web-")})
        assertFalse(reviewed.mayIssueWorkout)
        val oversized=expanded(source.catalog,
            CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES+1)
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,
            CoachFirstProgramStartDomain.review(
                source.copy(catalog=oversized)).status)
    }

    @Test fun strengthPhaseUsesFullCatalogWithoutOld256EntryShortCircuit() {
        val source=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH)
        val original=CoachFirstProgramStartDomain.review(source)
        val widened=CoachFirstProgramStartDomain.review(
            source.copy(catalog=expanded(source.catalog,376)))
        assertEquals(original.status,widened.status)
        assertEquals(original.chosenExerciseIds,widened.chosenExerciseIds)
        assertEquals(original.unsupportedMuscles,widened.unsupportedMuscles)
        assertTrue(checkNotNull(widened.prescription)
            .finalAudit!!.satisfiesDeclaredHardConstraints)
        assertFalse(widened.mayIssueWorkout)
    }

    @Test fun actualCatalogComposerAccepts376AndRejectsUnboundedInventories() {
        val first=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val profile=checkNotNull(first.setup.reviewed)
        val bounds=TrainingMuscle.entries.map {muscle->
            val direct=muscle in setOf(
                TrainingMuscle.CHEST,TrainingMuscle.UPPER_BACK,
                TrainingMuscle.QUADS)
            DeclaredMuscleDoseBounds(
                muscle,n(if(direct)"2"else"0"),n("20"),n("30"),
                if(direct)1 else 0)
        }
        val template=ComposerRequest(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map{day->ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))},
            bounds,emptyList(),4,1)
        fun choice(id:String,weight:String)=CatalogExerciseChoice(
            id,n(weight),90,150,2,3,3,1,1,5)
        val selected=listOf(choice("barbell_bench","135"),
            choice("one_arm_db_row","30"),choice("back_squat","135"))
        val gym=CatalogGym(profile.gym.confirmedStock,
            profile.gym.availableAttachments,profile.gym.availableFacilities)
        val base=StarterExerciseTranche.movements()
        val all=expanded(base,376)
        val reviewed=CoachExerciseCatalogDomain.review(
            CatalogWeekRequest(template,all,selected,gym))
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            reviewed.status)
        assertTrue(checkNotNull(reviewed.draft)
            .audit.satisfiesDeclaredHardConstraints)
        assertEquals(selected.map{it.exerciseId},reviewed.requestedExerciseIds)
        assertFalse(reviewed.mayIssueWorkout)
        assertThrows(IllegalArgumentException::class.java){
            CoachExerciseCatalogDomain.review(CatalogWeekRequest(
                template,expanded(base,
                    CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES+1),
                selected,gym))
        }
    }
}
