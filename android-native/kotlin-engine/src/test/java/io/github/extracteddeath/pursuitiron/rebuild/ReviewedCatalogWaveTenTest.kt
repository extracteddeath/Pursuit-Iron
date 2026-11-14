package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Provisional physical-source parity, never cross-exercise load permission. */
class ReviewedCatalogWaveTenTest {
    private fun n(x:String)=BigDecimal(x)
    private val all=StarterExerciseTranche.entries()
    private val ids=listOf(
        "tbar-row","wide-tbar-row","sumo-dl","rev-grip-curl",
        "kas-glute-bridge","board-press","barbell-skullover",
        "jefferson-curl","bodyweight-doorway-row","doorway-biceps-curl",
        "viking-press","stir-pot"
    )
    private val wave=all.drop(281).take(ids.size)
    private val bodyweight=setOf(
        "bodyweight-doorway-row","doorway-biceps-curl","stir-pot")
    private val dumbbell=setOf("jefferson-curl")
    private val machine=setOf("viking-press")
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
    private fun physical(id:String): Pair<BigDecimal?, VerifiedLoadStock?> = when {
        id in bodyweight -> null to null
        id in dumbbell -> n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("20"),n("25"),n("30")))
        id in machine -> n("70") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
            listOf(n("50"),n("60"),n("70")))
        else -> n("135") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("45"),mapOf(n("45") to 2))
    }
    private fun check(id:String,stockPresent:Boolean=true,
        fixturesPresent:Boolean=true,override:BigDecimal?=null
    ):CatalogWeekReview {
        val movement=wave.single{it.legacyCuratedId==id}.movement
        val (chosen,stock)=physical(id)
        val gym=CatalogGym(
            if(stockPresent && stock!=null)
                mapOf(checkNotNull(movement.equipmentId) to stock)
            else emptyMap(),
            emptySet(),
            if(fixturesPresent)movement.requiredFacilityIds else emptySet()
        )
        return CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template,listOf(
                CatalogExerciseChoice(id,override ?:chosen,90,120,2,3,2,1,1,5)
            ),gym))
    }

    @Test fun sourceIdentityAndEquipmentVariantRemainIndependent() {
        assertEquals(301,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(all.size,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(all.size,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        for(e in wave) {
            assertEquals(e.legacyCuratedId,e.movement.exerciseId)
            assertEquals(e.practicalRepBand,e.movement.sourcePracticalReps)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                e.movement.creditStatus)
            assertFalse(e.mayTransferPreviousLoad)
            assertTrue(e.movement.credits.any{
                it.role==MuscleCreditRole.DIRECT
            })
        }
        val byId=wave.associateBy{it.legacyCuratedId}
        assertEquals("heavy-barbell-hinge",
            byId.getValue("sumo-dl").movement.recoveryGroupId)
        assertEquals("heavy-bench",
            byId.getValue("board-press").movement.recoveryGroupId)
        assertNotEquals(byId.getValue("tbar-row").movement.variantId,
            byId.getValue("wide-tbar-row").movement.variantId)
        assertNotEquals(byId.getValue("tbar-row").movement.requiredFacilityIds,
            byId.getValue("wide-tbar-row").movement.requiredFacilityIds)
    }

    @Test fun exactStockIsRequiredAndBodyweightNeverBecomesPhantomLoading() {
        for(id in ids) {
            val move=wave.single{it.legacyCuratedId==id}.movement
            if(id in bodyweight) {
                assertNull(move.equipmentId)
                assertNull(move.expectedLoadBasis)
                assertTrue("$id: ${check(id).issues}",check(id).issues.isEmpty())
                assertTrue(id,check(id,override=n("25")).issues.any{
                    it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
                })
            } else {
                assertNotNull(move.equipmentId)
                assertTrue(id,check(id,stockPresent=false).issues.any{
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
                })
                assertTrue("$id: ${check(id).issues}",check(id).issues.isEmpty())
                assertTrue(id,check(id,override=n("999")).issues.any{
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED
                })
            }
            if(move.requiredFacilityIds.isNotEmpty()) {
                assertTrue(id,check(id,fixturesPresent=false).issues.any{
                    it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
                })
            }
        }
    }

    @Test fun vikingAndDoorwayNeverBorrowOtherStockOrUnsafeAnchors() {
        val viking=wave.single{it.legacyCuratedId=="viking-press"}.movement
        assertEquals("viking-press-machine",viking.equipmentId)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,viking.expectedLoadBasis)
        assertEquals(setOf("secure-doorway-anchor"),
            wave.single{it.legacyCuratedId=="doorway-biceps-curl"}
                .movement.requiredFacilityIds)
        assertEquals(setOf("stability-ball"),
            wave.single{it.legacyCuratedId=="stir-pot"}
                .movement.requiredFacilityIds)
        val curl=wave.single{it.legacyCuratedId=="rev-grip-curl"}.movement
        assertEquals(setOf(TrainingMuscle.BICEPS),
            curl.credits.map{it.muscle}.toSet())
        assertFalse(wave.any{it.movement.credits.any{credit->
            credit.muscle==TrainingMuscle.WRIST_FLEXORS ||
                credit.muscle==TrainingMuscle.WRIST_EXTENSORS
        }})
    }
}
