package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * The fifth source-matched web catalog wave. These are explicit, conservative
 * engineering mappings, not independently certified physiology. No movement
 * grants an undocumented machine load, history transfer or implicit facility.
 */
class ReviewedCatalogWaveFiveTest {
    private fun n(value:String)=BigDecimal(value)
    private val all=StarterExerciseTranche.entries()
    private val wave=all.drop(164)
    private val ids=listOf(
        "inv-row","chinup","dips-chest","dips-tri","hanging-raise",
        "hanging-knee","toes-to-bar","russian-twist","v-up",
        "sissy-squat","ghr","reverse-hyper","leg-press-calf","donkey-calf"
    )
    private val byId=all.associateBy{it.legacyCuratedId}
    private val bodyweight=ids.take(11).toSet()
    private val machine=ids.drop(11).toSet()

    private val request=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{
            ComposerDay(it,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))
        },
        TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,n("12"),n("20"),0)
        },emptyList(),4,1
    )

    @Test fun preserveTheExactFourteenWebIdsAndNoBorrowedExerciseHistory() {
        assertEquals(178,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(all.size,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(all.size,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(wave.all{
            it.movement.exerciseId==it.legacyCuratedId &&
                it.practicalRepBand==it.movement.sourcePracticalReps &&
                it.movement.creditStatus==
                    CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                it.movement.creditReviewReference=="engineering-provisional-v1" &&
                !it.mayTransferPreviousLoad &&
                it.movement.credits.any{credit->
                    credit.role==MuscleCreditRole.DIRECT
                }
        })
        assertNotEquals(byId.getValue("dips-chest").movement.variantId,
            byId.getValue("dips-tri").movement.variantId)
        assertNotEquals(byId.getValue("hanging-raise").movement.variantId,
            byId.getValue("hanging-knee").movement.variantId)
        assertNotEquals(byId.getValue("hanging-knee").movement.variantId,
            byId.getValue("toes-to-bar").movement.variantId)
    }

    @Test fun unweightedMovementsDoNotAcquirePhantomMachineOrPlateLoads() {
        for(id in bodyweight) {
            val move=byId.getValue(id).movement
            assertNull(id,move.equipmentId)
            assertNull(id,move.expectedLoadBasis)
            assertNull(id,move.attachmentId)
        }
        assertEquals(setOf(TrainingMuscle.CHEST),byId.getValue("dips-chest")
            .movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
            .map{it.muscle}.toSet())
        assertEquals(setOf(TrainingMuscle.TRICEPS),byId.getValue("dips-tri")
            .movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
            .map{it.muscle}.toSet())
        assertEquals(setOf(TrainingMuscle.LATS),byId.getValue("chinup")
            .movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
            .map{it.muscle}.toSet())
    }

    @Test fun realBarDipRackAndGhdFacilitiesAreRequiredBeforeSelection() {
        val gated=ids.filter{
            byId.getValue(it).movement.requiredFacilityIds.isNotEmpty()
        }
        assertEquals(setOf("inv-row","chinup","dips-chest","dips-tri",
            "hanging-raise","hanging-knee","toes-to-bar","ghr"),
            gated.toSet())
        val gym=CatalogGym(emptyMap(),emptySet())
        for(id in gated) {
            val found=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,null,90,120,2,3,2,1,1,5)),
                    gym))
            assertTrue(id+": "+found.issues,
                found.issues.any{
                    it.exerciseId==id &&
                    it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
                })
            assertFalse(found.mayIssueWorkout)
        }
    }

    @Test fun verifiedFixturesAndStockPermitOnlyReviewedExerciseVariants() {
        for(id in bodyweight) {
            val movement=byId.getValue(id).movement
            val gym=CatalogGym(emptyMap(),emptySet(),
                movement.requiredFacilityIds)
            val accepted=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,null,90,120,
                        2,3,2,1,1,5)),gym))
            assertTrue(id+": "+accepted.issues,accepted.issues.isEmpty())
            assertNotNull(id,accepted.composedRequest)
            // A bodyweight entry never becomes a loaded alternative.
            val illicit=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,n("50"),90,120,
                        2,3,2,1,1,5)),gym))
            assertTrue(id+": "+illicit.issues,illicit.issues.any {
                it.exerciseId==id &&
                    it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
            })
        }
        for(id in machine) {
            val movement=byId.getValue(id).movement
            val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.EXERCISE_OVERRIDE,listOf(n("50"),n("100")))
            val gym=CatalogGym(
                mapOf(checkNotNull(movement.equipmentId) to stock),emptySet())
            val valid=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,n("100"),90,120,
                        2,3,2,1,1,5)),gym))
            assertTrue(id+": "+valid.issues,valid.issues.isEmpty())
            assertNotNull(id,valid.composedRequest)
            val unlisted=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,n("125"),90,120,
                        2,3,2,1,1,5)),gym))
            assertTrue(id+": "+unlisted.issues,unlisted.issues.any {
                it.exerciseId==id &&
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED
            })
        }
    }

    @Test fun dedicatedMachinesRefuseAbsentInventoryAndNeverTransferOtherLifts() {
        assertEquals(setOf("reverse-hyper","leg-press-calf","donkey-calf"),
            machine)
        for(id in machine) {
            val movement=byId.getValue(id).movement
            assertNotNull(id,movement.equipmentId)
            assertEquals(id,LoadBasis.EXERCISE_OVERRIDE,
                movement.expectedLoadBasis)
            val none=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(id,n("100"),90,120,
                        2,3,2,1,1,5)),CatalogGym(emptyMap(),emptySet())))
            assertTrue(id+": "+none.issues,
                none.issues.any{
                    it.exerciseId==id &&
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
                })
            assertFalse(none.mayIssueWorkout)
        }
        assertNotEquals(
            byId.getValue("reverse-hyper").movement.equipmentId,
            byId.getValue("leg-press-calf").movement.equipmentId)
        assertNotEquals(
            byId.getValue("donkey-calf").movement.equipmentId,
            byId.getValue("leg-press-calf").movement.equipmentId)
    }
}
