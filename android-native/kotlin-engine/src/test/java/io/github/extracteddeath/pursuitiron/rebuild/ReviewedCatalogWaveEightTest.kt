package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Eighth explicitly reviewed Kotlin catalog wave, sourced from current effective
 * web built-ins. Physical stock, exact cable grip and safe anatomy remain owner
 * decisions. This is NOT full web engine or device parity certification.
 */
class ReviewedCatalogWaveEightTest {
    private fun n(value:String)=BigDecimal(value)
    private val all=StarterExerciseTranche.entries()
    private val ids=listOf(
        "spoto-press","larsen-press","pin-bench","pause-bench",
        "pin-press-ohp","deficit-barbell-deadlift","jefferson-squat",
        "db-deficit-lunge","db-deficit-step-up","deficit-db-rdl",
        "smith-reverse-lunge","bodyweight-reverse-nordic","db-lu-raise",
        "db-6-way-raise","single-db-lat-raise","cable-behind-back-lateral",
        "cuff-cable-lat-raise","cable-rear-delt-row",
        "cable-behind-back-curl","cable-cross-body-extension",
        "bw-single-calf","dragon-flag","situp","leaning-lat-raise",
        "cable-side-bend","hanging-oblique","ez-cable-curl",
        "single-lying-curl","smith-calf","windshield-wiper",
        "single-leg-press-calf","cable-wrist-curl","behind-wrist-curl",
        "upright-row","ez-oh-ext"
    )
    private val wave=all.drop(228).take(ids.size)
    private val bodyweight=setOf(
        "bodyweight-reverse-nordic","bw-single-calf","dragon-flag",
        "situp","hanging-oblique","windshield-wiper"
    )
    private val cable=setOf(
        "cable-behind-back-lateral","cuff-cable-lat-raise",
        "cable-rear-delt-row","cable-behind-back-curl",
        "cable-cross-body-extension","leaning-lat-raise",
        "cable-side-bend","ez-cable-curl","cable-wrist-curl"
    )
    private val smith=setOf("smith-reverse-lunge","smith-calf")
    private val machine=setOf("single-lying-curl","single-leg-press-calf")
    private val dumbbell=setOf(
        "db-deficit-lunge","db-deficit-step-up","deficit-db-rdl",
        "db-lu-raise","db-6-way-raise","single-db-lat-raise"
    )
    private val ez=setOf("ez-oh-ext")
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
    private fun stock(id:String): Pair<BigDecimal?, VerifiedLoadStock?> = when {
        id in bodyweight->null to null
        id in dumbbell->n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
        id in smith->n("85") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("65"),n("85"),n("105")))
        id in machine->n("70") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,listOf(n("50"),n("60"),n("70")))
        id in cable->n("25") to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.CABLE_STACK,listOf(n("20"),n("25"),n("30")))
        id in ez->n("115") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("25"),mapOf(n("45") to 2))
        else->n("135") to VerifiedLoadStock.Barbell(
            LoadUnit.LB,n("45"),mapOf(n("45") to 2))
    }
    private fun check(id:String,stockPresent:Boolean=true,
        attachmentPresent:Boolean=true,override:BigDecimal?=null):CatalogWeekReview {
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

    @Test fun exactWebIdentityAndNoCrossVariantLoadTransfer() {
        assertEquals(301,all.size)
        assertEquals(ids,wave.map{it.legacyCuratedId})
        assertEquals(301,all.map{it.legacyCuratedId}.distinct().size)
        assertEquals(301,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        for(e in wave) {
            assertEquals(e.legacyCuratedId,e.movement.exerciseId)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                e.movement.creditStatus)
            assertNotNull(e.movement.creditReviewReference)
            assertFalse(e.mayTransferPreviousLoad)
            assertEquals(e.practicalRepBand,e.movement.sourcePracticalReps)
            assertTrue(e.movement.credits.any{it.role==MuscleCreditRole.DIRECT})
        }
        for(id in listOf("spoto-press","larsen-press","pin-bench","pause-bench")) {
            val movement=wave.single{it.legacyCuratedId==id}.movement
            assertEquals("heavy-bench",movement.recoveryGroupId)
            assertEquals(2,movement.minimumRecoveryGroupDayGap)
        }
        assertEquals("heavy-barbell-hinge",
            wave.single{it.legacyCuratedId=="deficit-barbell-deadlift"}
                .movement.recoveryGroupId)
        assertNotEquals(wave.single{it.legacyCuratedId=="smith-calf"}
                .movement.equipmentId,
            wave.single{it.legacyCuratedId=="smith-reverse-lunge"}
                .movement.equipmentId)
    }

    @Test fun everyLoadedVariantRequiresExactlyVerifiedStockAndAppropriateCableGrip() {
        for(id in ids) {
            val e=wave.single{it.legacyCuratedId==id}.movement
            if(id in bodyweight) {
                assertNull(e.equipmentId)
                assertNull(e.expectedLoadBasis)
                assertNull(e.attachmentId)
                val valid=check(id)
                assertTrue("$id : ${valid.issues}",valid.issues.isEmpty())
                val noPhantom=check(id,override=n("25"))
                assertTrue(id,noPhantom.issues.any{
                    it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
                })
            } else {
                assertNotNull(e.equipmentId)
                assertNotNull(e.expectedLoadBasis)
                val invalid=check(id,stockPresent=false)
                assertTrue("$id : ${invalid.issues}",invalid.issues.any {
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
                })
                val review=check(id)
                assertTrue("$id : ${review.issues}",review.issues.isEmpty())
                val impossible=check(id,override=n("999"))
                assertTrue("$id : ${impossible.issues}",impossible.issues.any{
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED
                })
                if(id in cable) {
                    assertEquals("cable-station",e.equipmentId)
                    assertEquals(LoadBasis.CABLE_STACK,e.expectedLoadBasis)
                    assertNotNull(id,e.attachmentId)
                    val missingGrip=check(id,attachmentPresent=false)
                    assertTrue("$id : ${missingGrip.issues}",missingGrip.issues.any{
                        it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE
                    })
                }
            }
        }
    }

    @Test fun armAndForearmAccountingKeepsRealMuscleIdentitiesSeparate() {
        val wristIds=listOf("cable-wrist-curl","behind-wrist-curl")
        for(id in wristIds) {
            val movement=wave.single{it.legacyCuratedId==id}.movement
            assertEquals(setOf(TrainingMuscle.WRIST_FLEXORS),
                movement.credits.map{it.muscle}.toSet())
            assertTrue(movement.credits.none{
                it.muscle==TrainingMuscle.WRIST_EXTENSORS
            })
        }
        val curl=wave.single{it.legacyCuratedId=="cable-behind-back-curl"}
            .movement
        assertEquals(setOf(TrainingMuscle.BICEPS),
            curl.credits.map{it.muscle}.toSet())
        assertFalse(curl.credits.any{it.muscle==TrainingMuscle.BRACHIALIS})
    }
}
