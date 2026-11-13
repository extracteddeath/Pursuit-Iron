package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Reviewed web identity wave six. Exact source IDs, independently calibrated
 * implement stocks and anatomy are preserved; no web policy runs on Android.
 * These are provisional engineering mappings, NOT certified physiology.
 */
class ReviewedCatalogWaveSixTest {
    private fun n(s:String)=BigDecimal(s)
    private val all=StarterExerciseTranche.entries()
    private val wave=all.drop(178)
    private val ids=listOf(
        "split-squat","step-up","box-squat","cyclist-squat",
        "zercher-squat","pistol-squat","hip-thrust","smith-hip-thrust",
        "frog-pump","stiff-deadlift","slider-curl","drag-curl",
        "pinwheel-curl","jm-press","pjr-pullover","california-press",
        "smith-ohp","push-press","z-press","bradford-press","dead-bug",
        "mountain-climber","ab-wheel","single-cable-curl",
        "cable-tri-kickback","incline-cable-press","machine-dip",
        "underhand-pushdown","cable-crossover","single-cable-raise"
    )
    private val bodyweight=setOf(
        "pistol-squat","frog-pump","slider-curl","dead-bug",
        "mountain-climber","ab-wheel"
    )
    private val cable=setOf(
        "single-cable-curl","cable-tri-kickback","incline-cable-press",
        "underhand-pushdown","cable-crossover","single-cable-raise"
    )
    private val db=setOf("split-squat","step-up","pinwheel-curl")
    private val smith=setOf("smith-hip-thrust","smith-ohp")
    private val ez=setOf("pjr-pullover","california-press")
    private val dedicatedMachine=setOf("machine-dip")

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

    private fun stockFor(id:String):Pair<BigDecimal?,VerifiedLoadStock?>=when {
        id in bodyweight->null to null
        id in cable->n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,listOf(n("20"),n("25"),n("30")))
        id in db->n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("15"),n("20"),n("25"),n("30")))
        id in smith->n("85") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("65"),n("85"),n("105")))
        id in ez->n("115") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("25"),mapOf(n("45") to 2,n("25") to 2))
        id in dedicatedMachine->n("70") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
            listOf(n("50"),n("60"),n("70")))
        else->n("135") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("45"),mapOf(n("45") to 2,n("25") to 2))
    }

    private fun review(id:String,includeStock:Boolean,override:BigDecimal?=null):
        CatalogReviewResult {
        val movement=all.single{it.legacyCuratedId==id}.movement
        val (suggested,stock)=stockFor(id)
        val gym=CatalogGym(
            if(includeStock && stock!=null)
                mapOf(checkNotNull(movement.equipmentId) to stock)
            else emptyMap(),
            movement.attachmentId?.let{setOf(it)} ?:emptySet(),
            movement.requiredFacilityIds
        )
        val weight=override ?:suggested
        return CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(request,
                listOf(CatalogExerciseChoice(id,weight,90,120,2,3,2,1,1,5)),
                gym))
    }

    @Test fun preserveExactWebIdentitiesAndUniqueEquipmentVariants() {
        assertEquals(208,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(208,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(208,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(wave.all{
            it.movement.exerciseId==it.legacyCuratedId &&
                it.movement.creditStatus==
                    CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                !it.mayTransferPreviousLoad &&
                it.movement.credits.any{credit->
                    credit.role==MuscleCreditRole.DIRECT
                }
        })
        assertNotEquals(all.single{it.legacyCuratedId=="hip_thrust"}
            .movement.variantId,all.single{it.legacyCuratedId=="hip-thrust"}
            .movement.variantId)
        assertNotEquals(all.single{it.legacyCuratedId=="db_split_squat"}
            .movement.variantId,all.single{it.legacyCuratedId=="split-squat"}
            .movement.variantId)
        assertEquals(setOf(TrainingMuscle.BRACHIALIS),
            all.single{it.legacyCuratedId=="pinwheel-curl"}
                .movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
                .map{it.muscle}.toSet())
        assertEquals(setOf(TrainingMuscle.BICEPS),
            all.single{it.legacyCuratedId=="drag-curl"}
                .movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
                .map{it.muscle}.toSet())
    }

    @Test fun unweightedMovementsNeverAssumeMachineOrPlateResistance() {
        for(id in bodyweight) {
            val movement=all.single{it.legacyCuratedId==id}.movement
            assertNull(id,movement.equipmentId)
            assertNull(id,movement.expectedLoadBasis)
            assertNull(id,movement.attachmentId)
            val valid=review(id,includeStock=false)
            assertTrue("$id: ${valid.issues}",valid.issues.isEmpty())
            val illicit=review(id,includeStock=false,override=n("25"))
            assertTrue(id,illicit.issues.any{
                it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
            })
        }
        assertEquals(setOf("ab-wheel"),
            all.single{it.legacyCuratedId=="ab-wheel"}
                .movement.requiredFacilityIds)
        assertEquals(setOf("exercise-sliders"),
            all.single{it.legacyCuratedId=="slider-curl"}
                .movement.requiredFacilityIds)
    }

    @Test fun verifiedPerExerciseStockAndCableAttachmentsAreMandatory() {
        for(id in ids.filterNot{it in bodyweight}) {
            val move=all.single{it.legacyCuratedId==id}.movement
            val (load,stock)=stockFor(id)
            assertNotNull(id,load)
            assertNotNull(id,stock)
            assertNotNull(id,move.equipmentId)
            val absent=review(id,includeStock=false)
            assertTrue("$id: ${absent.issues}",absent.issues.any{
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
            })
            val present=review(id,includeStock=true)
            assertTrue("$id: ${present.issues}",present.issues.isEmpty())
            val impossible=review(id,includeStock=true,override=n("999"))
            assertTrue("$id: ${impossible.issues}",impossible.issues.any{
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
            })
        }
        for(id in cable) {
            val move=all.single{it.legacyCuratedId==id}.movement
            assertEquals(LoadBasis.CABLE_STACK,move.expectedLoadBasis)
            assertNotNull(id,move.attachmentId)
            assertEquals("cable-station",move.equipmentId)
        }
        for(id in smith) {
            val move=all.single{it.legacyCuratedId==id}.movement
            assertEquals(LoadBasis.SMITH_TOTAL,move.expectedLoadBasis)
        }
        for(id in ez) {
            assertEquals("ez-curl-bar",
                all.single{it.legacyCuratedId==id}.movement.equipmentId)
        }
    }

    @Test fun noUniversalMachineScaleOrUnverifiedLoadTransfer() {
        val source=all.single{it.legacyCuratedId=="machine-dip"}.movement
        assertEquals("chest-dip-machine",source.equipmentId)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,source.expectedLoadBasis)
        assertNotEquals(source.equipmentId,
            all.single{it.legacyCuratedId=="machine_press"}
                .movement.equipmentId)
        assertNotEquals(all.single{it.legacyCuratedId=="smith-ohp"}
            .movement.equipmentId,
            all.single{it.legacyCuratedId=="smith-hip-thrust"}
                .movement.equipmentId)
        assertTrue(wave.none{it.mayTransferPreviousLoad})
    }
}
