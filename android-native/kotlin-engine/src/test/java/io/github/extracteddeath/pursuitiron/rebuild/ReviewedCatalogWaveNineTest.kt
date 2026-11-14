package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Ninth append-only, source-matched wave of reviewed physical exercise
 * identities. Each load is independently verified against exact gym stock;
 * no source movement may carry old exercise progress or invent weights.
 * Exact identity coverage is not independent scientific/UX certification.
 */
class ReviewedCatalogWaveNineTest {
    private fun n(value:String)=BigDecimal(value)
    private val all=StarterExerciseTranche.entries()
    private val ids=listOf(
        "db-hip-thrust","db-pullover","floor-db-pullover",
        "bent-db-pullover","single-calf","bstance-hip-thrust",
        "waiter-curl","heels-up-goblet","sumo-squat",
        "step-through-lunge","db-good-morning","db-weighted-crunch",
        "goblet","db-oh-ext","reverse-curl",
        "bodyweight-deficit-pushup","cable-kickback","seal-row"
    )
    private val wave=all.drop(263).take(ids.size)
    private val bodyweight=setOf("bodyweight-deficit-pushup")
    private val cable=setOf("cable-kickback")
    private val ez=setOf("reverse-curl")
    private val barbell=setOf("seal-row")
    private val template=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map { day ->
            ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))
        },
        TrainingMuscle.entries.map { muscle ->
            DeclaredMuscleDoseBounds(muscle,BigDecimal.ZERO,n("12"),n("20"),0)
        },emptyList(),4,1
    )

    private fun stock(id:String): Pair<BigDecimal?, VerifiedLoadStock?> = when {
        id in bodyweight -> null to null
        id in cable -> n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))
        id in ez -> n("115") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("25"),mapOf(n("45") to 2))
        id in barbell -> n("135") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("45"),mapOf(n("45") to 2))
        else -> n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("20"),n("25"),n("30")))
    }
    private fun check(
        id:String,stockPresent:Boolean=true,
        attachmentPresent:Boolean=true,override:BigDecimal?=null
    ):CatalogWeekReview {
        val movement=wave.single{it.legacyCuratedId==id}.movement
        val (suggested,physical)=stock(id)
        val gym=CatalogGym(
            if(stockPresent && physical!=null)
                mapOf(checkNotNull(movement.equipmentId) to physical)
            else emptyMap(),
            if(attachmentPresent)movement.attachmentId?.let{setOf(it)}
                ?:emptySet() else emptySet(),
            movement.requiredFacilityIds
        )
        return CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template,listOf(
                CatalogExerciseChoice(id,override ?:suggested,90,120,2,3,2,1,1,5)
            ),gym))
    }

    @Test fun allEighteenExactWebIdsAppendAfterUnmodifiedWaveEight() {
        assertEquals(281,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(all.size,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(all.size,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        for (e in wave) {
            assertEquals(e.legacyCuratedId,e.movement.exerciseId)
            assertEquals(e.practicalRepBand,e.movement.sourcePracticalReps)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                e.movement.creditStatus)
            assertFalse(e.mayTransferPreviousLoad)
            assertTrue(e.movement.credits.any{
                it.role==MuscleCreditRole.DIRECT
            })
        }
    }

    @Test fun allLoadsRequireExactPhysicalStockWithoutImplicitTransfer() {
        for(id in ids) {
            val movement=wave.single{it.legacyCuratedId==id}.movement
            if(id in bodyweight) {
                assertNull(movement.equipmentId)
                assertNull(movement.expectedLoadBasis)
                assertEquals(setOf("pushup-handles"),
                    movement.requiredFacilityIds)
                assertTrue(id,check(id).issues.isEmpty())
                assertTrue(id,check(id,override=n("25")).issues.any{
                    it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
                })
            } else {
                assertNotNull(movement.equipmentId)
                assertNotNull(movement.expectedLoadBasis)
                assertTrue(id,check(id,stockPresent=false).issues.any{
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
                })
                val valid=check(id)
                assertTrue("$id: ${valid.issues}",valid.issues.isEmpty())
                assertTrue(id,check(id,override=n("999")).issues.any{
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED
                })
            }
        }
    }

    @Test fun physicallyDistinctGripBenchAndMuscleCreditRemainExplicit() {
        val kickback=wave.single{
            it.legacyCuratedId=="cable-kickback"
        }.movement
        assertEquals("cable-station",kickback.equipmentId)
        assertEquals(LoadBasis.CABLE_STACK,kickback.expectedLoadBasis)
        assertEquals("ankle-cuff",kickback.attachmentId)
        assertTrue(check("cable-kickback",attachmentPresent=false)
            .issues.any{it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE})
        val seal=wave.single{it.legacyCuratedId=="seal-row"}.movement
        assertEquals(setOf("seal-bench"),seal.requiredFacilityIds)
        assertEquals(LoadBasis.BARBELL_TOTAL,seal.expectedLoadBasis)
        val ezCurl=wave.single{it.legacyCuratedId=="reverse-curl"}.movement
        assertEquals("ez-curl-bar",ezCurl.equipmentId)
        assertEquals(setOf(TrainingMuscle.BICEPS),
            ezCurl.credits.map{it.muscle}.toSet())
        assertTrue(wave.none{e->e.movement.credits.any{
            it.muscle==TrainingMuscle.WRIST_EXTENSORS ||
                it.muscle==TrainingMuscle.WRIST_FLEXORS
        }})
        assertNotEquals(
            all.single{it.legacyCuratedId=="db_overhead_triceps"}.movement.variantId,
            wave.single{it.legacyCuratedId=="db-oh-ext"}.movement.variantId)
        assertNotEquals(
            wave.single{it.legacyCuratedId=="db-pullover"}.movement.variantId,
            wave.single{it.legacyCuratedId=="floor-db-pullover"}.movement.variantId)
    }
}
