package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StarterExerciseTrancheTest {
    private fun n(value:String)=BigDecimal(value)
    private val data=StarterExerciseTranche.entries()
    private val availableBar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("45") to 2,n("25") to 2,n("10") to 2,n("5") to 2))
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25"),
            n("30"),n("35"),n("40"),n("45"),n("50")))
    private fun gym(
        facilities:Set<String> =setOf("flat-bench","rack","incline-bench"),
        stock:Map<String,VerifiedLoadStock> =mapOf(
            "straight-bar" to availableBar,"dumbbell-rack" to db)
    )=CatalogGym(stock,emptySet(),facilities)
    private fun bounds():List<DeclaredMuscleDoseBounds> =
        TrainingMuscle.entries.map {muscle->
            val minimum=if(muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.UPPER_BACK,TrainingMuscle.QUADS))n("2")else n("0")
            DeclaredMuscleDoseBounds(muscle,minimum,n("20"),n("30"),
                if(minimum>BigDecimal.ZERO)1 else 0)
        }
    private fun template():ComposerRequest=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map {day->
            ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))
        },bounds(),emptyList(),4,1)
    private fun choice(id:String,load:BigDecimal?)=CatalogExerciseChoice(
        id,load,90,150,2,3,3,1,1,5)
    private fun selected()=listOf(choice("barbell_bench",n("135")),
        choice("one_arm_db_row",n("30")),choice("back_squat",n("135")))

    @Test fun exactSourceIdsAreStableAndNoVariantsShareIdentity() {
        assertEquals(13,data.size)
        assertEquals(13,data.map {it.legacyCuratedId}.distinct().size)
        assertTrue(data.all {it.legacyCuratedId==it.movement.exerciseId})
        assertEquals(13,data.map {
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(data.all {
            it.movement.creditReviewReference=="engineering-provisional-v1"
        })
        assertTrue(data.all {it.mappingNotes.isNotBlank()})
        assertTrue(data.none {it.mayTransferPreviousLoad})
    }

    @Test fun allRealGymMovementsRequireCorrectLoadBasis() {
        val required=StarterExerciseTranche.movements()
        assertTrue(required.filter {it.equipmentId=="straight-bar"}
            .all {it.expectedLoadBasis==LoadBasis.BARBELL_TOTAL})
        assertTrue(required.filter {it.equipmentId=="dumbbell-rack"}
            .all {it.expectedLoadBasis==LoadBasis.DUMBBELL_PER_HAND})
        assertEquals(setOf("flat-bench","rack"),
            required.first {it.exerciseId=="barbell_bench"}.requiredFacilityIds)
        assertEquals(setOf("incline-bench"),
            required.first {it.exerciseId=="incline_db_press"}.requiredFacilityIds)
        assertEquals(null,required.first {
            it.exerciseId=="bodyweight_crunch"
        }.equipmentId)
    }

    @Test fun realThreeDayCompoundWeekComposesOnlyWithConfirmedPhysicalGym() {
        val request=StarterExerciseTranche.forGym(template(),selected(),gym())
        val result=CoachExerciseCatalogDomain.review(request)
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            result.status)
        assertTrue(result.issues.isEmpty())
        val draft=checkNotNull(result.draft)
        assertTrue(draft.audit.satisfiesDeclaredHardConstraints)
        assertTrue(draft.visits.any {it.exerciseId=="barbell_bench"})
        assertTrue(draft.visits.any {it.exerciseId=="one_arm_db_row"})
        assertTrue(draft.visits.any {it.exerciseId=="back_squat"})
        assertEquals(3,draft.audit.sessions.size)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayOverwriteAuthoredProgram)
        assertTrue(request.template.exercises.isEmpty())
    }

    @Test fun theSameBarbellCannotBenchWithoutBenchOrSquatWithoutRack() {
        val withoutBench=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selected(),
                gym(facilities=setOf("rack"))))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
            withoutBench.status)
        assertTrue(withoutBench.issues.any {
            it.exerciseId=="barbell_bench" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
        val withoutRack=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selected(),
                gym(facilities=setOf("flat-bench"))))
        assertTrue(withoutRack.issues.any {
            it.exerciseId=="back_squat" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
    }

    @Test fun aFlatBenchDoesNotConfirmAnInclineBench() {
        val request=StarterExerciseTranche.forGym(template(),listOf(
            choice("incline_db_press",n("30")),
            choice("one_arm_db_row",n("30")),
            choice("back_squat",n("135"))
        ),gym(facilities=setOf("flat-bench","rack")))
        val result=CoachExerciseCatalogDomain.review(request)
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.exerciseId=="incline_db_press" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
    }

    @Test fun missingOrInvalidAthleteWeightIsNeverInventedFromTheCatalog() {
        val missing=selected().map {if(it.exerciseId=="barbell_bench")
            it.copy(selectedLoad=null)else it}
        val noLoad=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),missing,gym()))
        assertTrue(noLoad.issues.any {
            it.exerciseId=="barbell_bench" &&
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        val improbable=selected().map {
            if(it.exerciseId=="one_arm_db_row")
                it.copy(selectedLoad=n("38.5"))else it
        }
        val fabricated=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),improbable,gym()))
        assertTrue(fabricated.issues.any {
            it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertNull(fabricated.composedRequest)
    }

    @Test fun bicepsAndBrachialisAreNotConflatedInCurlPrescriptions() {
        val curl=data.first {it.legacyCuratedId=="db_curl"}.movement
        assertEquals(listOf(TrainingMuscle.BICEPS),
            curl.credits.map {it.muscle})
        assertFalse(curl.credits.any {it.muscle==TrainingMuscle.BRACHIALIS})
        val row=data.first {it.legacyCuratedId=="one_arm_db_row"}.movement
        assertEquals(TrainingMuscle.UPPER_BACK,
            row.credits.first {it.role==MuscleCreditRole.DIRECT}.muscle)
        assertTrue(row.credits.any {it.muscle==TrainingMuscle.LATS &&
            it.role==MuscleCreditRole.INDIRECT})
        assertTrue(row.credits.any {it.muscle==TrainingMuscle.BICEPS &&
            it.role==MuscleCreditRole.INDIRECT})
        assertTrue(data.first {it.legacyCuratedId=="db_rdl"}
            .mappingNotes.contains("quarantined"))
    }

    @Test fun bodyweightAndCalvesAreNotRewrittenAsGenericZeroPoundEquipment() {
        val core=data.first {it.legacyCuratedId=="bodyweight_crunch"}.movement
        assertNull(core.equipmentId)
        assertEquals(TrainingMuscle.ABS,core.credits.single().muscle)
        val calf=data.first {it.legacyCuratedId=="db_calf_raise"}.movement
        assertEquals(TrainingMuscle.CALVES,calf.credits.single().muscle)
        val invalid=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(
                choice("bodyweight_crunch",n("0"))),gym()))
        assertTrue(invalid.issues.any {
            it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
    }

    @Test fun returnedTrancheAndSourceChoicesCannotMutateProgramState() {
        val entries=StarterExerciseTranche.entries()
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=entries as MutableList<StarterCatalogEntry>
            writable.clear()
        }
        val choices=selected()
        val selected=StarterExerciseTranche.forGym(template(),choices,gym())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=selected.chosen as MutableList<CatalogExerciseChoice>
            writable.clear()
        }
        assertEquals(n("135"),choices.first().selectedLoad)
        assertFalse(CoachExerciseCatalogDomain.review(selected).mayIssueWorkout)
    }
}
