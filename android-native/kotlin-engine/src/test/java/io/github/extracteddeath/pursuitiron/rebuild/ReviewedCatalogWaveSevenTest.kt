package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Wave seven: source-matched active web exercise identities, with independently
 * verified physical resistance and provisional, deliberately bounded anatomy.
 * No JavaScript runtime, inferred loads or cross-variant progression transfer.
 */
class ReviewedCatalogWaveSevenTest {
    private fun n(value:String)=BigDecimal(value)
    private val all=StarterExerciseTranche.entries()
    private val ids=listOf("underhand-pulldown","single-cable-row","close-cable-row","wide-cable-row","underhand-cable-row","rope-cable-row","dual-cable-row","wide-machine-row","neutral-machine-row","t-bar-chest-supported","cable-pullover","machine-pullover","cable-upright-row","cable-y-raise","cable-rdl","glute-kickback-machine","hack-calf","reverse-crunch","cable-reverse-crunch","db-floor-press")
    private val wave=all.drop(208).take(ids.size)
    private val bodyweight=setOf("reverse-crunch")
    private val machines=setOf("wide-machine-row","neutral-machine-row",
        "t-bar-chest-supported","machine-pullover",
        "glute-kickback-machine","hack-calf")
    private val dumbbells=setOf("db-floor-press")
    private val template=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map {
            ComposerDay(it,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))
        },
        TrainingMuscle.entries.map {
            DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,n("12"),n("20"),0)
        },emptyList(),4,1
    )

    private fun stock(id:String)=when {
        id in dumbbells->VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("20"),n("25"),n("30")))
        id in machines->VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
            listOf(n("50"),n("60"),n("70")))
        else->VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))
    }

    private fun review(id:String,includeStock:Boolean=true,
        includeAttachment:Boolean=true,load:BigDecimal?=null):CatalogWeekReview {
        val movement=all.single{it.legacyCuratedId==id}.movement
        val expectedLoad=load ?: if(id in bodyweight)null
            else if(id in machines)n("60") else n("25")
        val gym=CatalogGym(
            if(includeStock&&movement.equipmentId!=null)
                mapOf(movement.equipmentId to stock(id)) else emptyMap(),
            if(includeAttachment) movement.attachmentId?.let{setOf(it)}
                ?:emptySet() else emptySet(),
            movement.requiredFacilityIds
        )
        return CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template,
                listOf(CatalogExerciseChoice(id,expectedLoad,
                    90,120,2,3,2,1,1,5)),gym))
    }

    @Test fun exactSourceIdentityAndNoHistoryTransfer() {
        assertEquals(263,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(263,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(263,all.map {
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        for(entry in wave) {
            assertEquals(entry.legacyCuratedId,entry.movement.exerciseId)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                entry.movement.creditStatus)
            assertNotNull(entry.movement.creditReviewReference)
            assertFalse(entry.mayTransferPreviousLoad)
            assertTrue(entry.movement.credits.any {
                it.role==MuscleCreditRole.DIRECT
            })
        }
        assertNotEquals(
            all.single{it.legacyCuratedId=="wide-machine-row"}.movement.equipmentId,
            all.single{it.legacyCuratedId=="neutral-machine-row"}.movement.equipmentId)
        assertNotEquals(
            all.single{it.legacyCuratedId=="machine-pullover"}.movement.equipmentId,
            all.single{it.legacyCuratedId=="cable-pullover"}.movement.equipmentId)
    }

    @Test fun everyVariantRequiresItsOwnVerifiedStockAndCableAttachment() {
        for(id in ids) {
            val movement=all.single{it.legacyCuratedId==id}.movement
            if(id=="reverse-crunch") {
                assertNull(movement.equipmentId)
                assertNull(movement.attachmentId)
            } else {
                assertNotNull(movement.equipmentId)
                val missing=review(id,includeStock=false)
                assertTrue("$id : ${missing.issues}",missing.issues.any {
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
                })
            }
            val valid=review(id)
            assertTrue("$id : ${valid.issues}",valid.issues.isEmpty())
            if(movement.attachmentId!=null) {
                val noHandle=review(id,includeAttachment=false)
                assertTrue("$id : ${noHandle.issues}",noHandle.issues.any {
                    it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE
                })
            }
        }
    }

    @Test fun uncertainMuscleCreditsAndBodyweightDoNotInventResistance() {
        for(id in listOf("single-cable-row","wide-cable-row",
            "neutral-machine-row","cable-rdl")) {
            val credits=all.single{it.legacyCuratedId==id}.movement.credits
            assertFalse(id,credits.any{it.muscle==TrainingMuscle.WRIST_FLEXORS ||
                it.muscle==TrainingMuscle.WRIST_EXTENSORS})
        }
        val crunch=all.single{it.legacyCuratedId=="reverse-crunch"}.movement
        assertNull(crunch.expectedLoadBasis)
        assertEquals(listOf(TrainingMuscle.ABS),
            crunch.credits.filter{it.role==MuscleCreditRole.DIRECT}
                .map{it.muscle})
        val illegal=review("reverse-crunch",load=n("25"))
        // An explicit numeric override for an unweighted movement must refuse;
        // neither the catalog nor caller may silently interpret it as plates.
        assertTrue(illegal.issues.any {
            it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
    }
}
