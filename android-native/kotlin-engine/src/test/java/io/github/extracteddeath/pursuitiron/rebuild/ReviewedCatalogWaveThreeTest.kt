package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Third source-linked native catalog review, with 70 individually distinct
 * physical movements. This is an integrity/availability gate, NOT a proof
 * of physiological dose, forecast superiority, or approved weight transfer.
 */
class ReviewedCatalogWaveThreeTest {
    private val all=StarterExerciseTranche.entries()
    private val addedIds=listOf(
        "spider-curl","low-incline-db","decline-db-press","db-neutral-press","seated-db-press",
        "squeeze-press","db-incline-fly","rear-fly","seated-rear-fly","prone-db-rear-fly",
        "side-lying-raise","seated-db-lat-raise","chest-supported-lat-raise","helms-row","concentration-curl",
        "incline-hammer","cross-hammer","db-rdl","kroc-row","inc-db-row",
        "cs-db-row","lying-db-ext","pin-squat","ohp","seated-ohp",
        "underhand-row","high-bar-squat","low-bar-squat","paused-squat","tempo-squat",
        "good-morning","yates-row","pendlay-row","floor-press","decline-bench",
        "seated-incline-curl","incline-machine-press","seated-cable-lat-raise","chest-supported-rear-fly","machine-lat-raise",
        "machine-curl","machine-ext","belt-squat","machine-hip-thrust","machine-crunch",
        "nordic","slrdl","single-leg-press","smith-incline","bw-lunge",
        "bw-bulgarian","glute-bridge","sl-hip-thrust","bw-calf","crunch",
        "bicycle","oblique-crunch","leg-raise","bench-dip","standing-curl",
        "inc-cable-fly","high-cable-fly","pendulum-squat","cable-press","v-squat",
        "wide-pulldown","close-pulldown","one-arm-pulldown","straight-pulldown","cable-woodchop"
    )
    private val wave=all.drop(76).take(70)
    private val byId=all.associateBy {it.legacyCuratedId}
    private fun n(v:String)=BigDecimal(v)

    @Test fun sourceIDsAndPhysicalVariantsAreStableAndNonTransferring() {
        assertEquals(178,all.size)
        assertEquals(70,wave.size)
        assertEquals(addedIds,wave.map {it.legacyCuratedId})
        assertEquals(all.size,byId.size)
        assertEquals(all.size,all.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(wave.all{
            it.movement.exerciseId==it.legacyCuratedId &&
            it.practicalRepBand==it.movement.sourcePracticalReps &&
            it.movement.creditStatus==CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
            it.movement.creditReviewReference=="engineering-provisional-v1" &&
            !it.mayTransferPreviousLoad &&
            it.movement.credits.any{credit->credit.role==MuscleCreditRole.DIRECT}
        })
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,
            byId.getValue("spider-curl").movement.expectedLoadBasis)
        assertEquals(LoadBasis.BARBELL_TOTAL,
            byId.getValue("pin-squat").movement.expectedLoadBasis)
        assertNotEquals(byId.getValue("pendulum-squat").movement.equipmentId,
            byId.getValue("v-squat").movement.equipmentId)
        assertEquals("wide-bar",
            byId.getValue("wide-pulldown").movement.attachmentId)
        assertNotEquals(byId.getValue("db_curl").movement.variantId,
            byId.getValue("spider-curl").movement.variantId)
        assertNotEquals(byId.getValue("incline_db_press").movement.variantId,
            byId.getValue("low-incline-db").movement.variantId)
    }

    @Test fun gymWithoutPhysicalEquipmentCannotIssueLoadedWaveOrInventMachineWeights() {
        val policy=TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,n("10"),n("20"),0)
        }
        val request=ComposerRequest(ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map{
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },policy,emptyList(),4,1)
        val emptyGym=CatalogGym(emptyMap(),emptySet(),emptySet())
        for(ex in wave) {
            val movement=ex.movement
            val weight=if(movement.equipmentId==null)null else n("25")
            val review=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(request,
                    listOf(CatalogExerciseChoice(ex.legacyCuratedId,
                        weight,90,120,2,3,2,1,1,5)),emptyGym))
            if(weight!=null)
                assertTrue("Missing equipment should block "+ex.legacyCuratedId+
                    ": "+review.issues,
                    review.issues.any {it.exerciseId==ex.legacyCuratedId &&
                        it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
            if(weight==null && movement.requiredFacilityIds.isNotEmpty())
                assertTrue("A required bodyweight bench is missing for "+
                    ex.legacyCuratedId,review.issues.any{
                        it.exerciseId==ex.legacyCuratedId &&
                            it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
                    })
            if(weight==null)
                assertFalse(review.issues.any{it.exerciseId==ex.legacyCuratedId &&
                    (it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE ||
                        it.kind==CatalogIssueType.LOAD_UNVERIFIED)})
            assertFalse(review.mayIssueWorkout)
            assertFalse(review.mayOverwriteAuthoredProgram)
        }
    }

    @Test fun machineAndShoulderVariantsHaveNoInventedGenericWeightBasis() {
        val machines=setOf("incline-machine-press","machine-lat-raise",
            "machine-curl","machine-ext","belt-squat",
            "machine-hip-thrust","machine-crunch","single-leg-press",
            "pendulum-squat","v-squat","standing-curl")
        assertEquals(machines.size,machines.map{
            byId.getValue(it).movement.equipmentId
        }.distinct().size)
        assertTrue(machines.all{
            byId.getValue(it).movement.expectedLoadBasis==LoadBasis.EXERCISE_OVERRIDE
        })
        assertTrue(listOf("spider-curl","low-incline-db","helms-row",
            "concentration-curl","incline-hammer","cross-hammer").all{
            byId.getValue(it).movement.expectedLoadBasis==
                LoadBasis.DUMBBELL_PER_HAND
        })
        assertEquals(setOf(TrainingMuscle.BRACHIALIS),
            byId.getValue("cross-hammer").movement.credits.filter{
                it.role==MuscleCreditRole.DIRECT
            }.map{it.muscle}.toSet())
        assertEquals(setOf(TrainingMuscle.BICEPS),
            byId.getValue("spider-curl").movement.credits.filter{
                it.role==MuscleCreditRole.DIRECT
            }.map{it.muscle}.toSet())
        assertTrue(listOf("bw-lunge","bw-bulgarian","glute-bridge",
            "sl-hip-thrust","bw-calf","crunch","bicycle",
            "oblique-crunch","leg-raise","bench-dip").all{
            val movement=byId.getValue(it).movement
            movement.equipmentId==null && movement.expectedLoadBasis==null &&
                movement.attachmentId==null
        })
        assertTrue(listOf("wide-pulldown","close-pulldown",
            "one-arm-pulldown","straight-pulldown","cable-woodchop").all {
            byId.getValue(it).movement.expectedLoadBasis==LoadBasis.CABLE_STACK &&
                byId.getValue(it).movement.attachmentId!=null
        })
    }
}
