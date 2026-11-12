package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * First wider physical-gym catalog batch. These are only independently
 * source-matched PROVISIONAL Kotlin movement definitions, never proof of
 * superior coaching, load carryover or official exercise-science coefficients.
 */
class ReviewedBroadSourceCatalogTest {
    private fun n(s:String)=BigDecimal(s)
    private val additions=listOf("low-inc-bb-bench","high-inc-bb-bench",
        "smith-bench","front-squat","smith-squat","walking-lunge",
        "bulgarian","lying-curl","seated-calf","bb-curl","ez-curl",
        "bayesian-curl","cgbp","skullcrusher","pushdown","ez-pushdown",
        "oh-cable-ext","machine-row","seated-row","front-raise","arnold",
        "pushup","bw-squat","diamond-pushup","pike-pushup",
        "incline-pushup","decline-pushup")
    private val catalog=StarterExerciseTranche.entries()
    private val byId=catalog.associateBy {it.legacyCuratedId}

    @Test fun exactlyTwentySevenSourceIdsAppendAfterPreservedFirstFortyNine() {
        assertTrue(catalog.size>=76)
        assertEquals(additions,catalog.drop(49).take(27).map{it.legacyCuratedId})
        assertEquals(catalog.size,byId.size)
        assertEquals(catalog.size,catalog.map{
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(additions.all{
            val entry=byId.getValue(it)
            entry.legacyCuratedId==entry.movement.exerciseId &&
                entry.practicalRepBand==entry.movement.sourcePracticalReps &&
                entry.movement.creditStatus==
                    CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                entry.movement.credits.any{
                    credit->credit.role==MuscleCreditRole.DIRECT
                } && !entry.mayTransferPreviousLoad
        })
    }

    @Test fun physicalEquipmentAndAnatomyRemainIndependentAcrossVariants() {
        val machineIds=setOf("lying-curl","seated-calf","machine-row")
        assertEquals(machineIds.size,machineIds.map{
            byId.getValue(it).movement.equipmentId
        }.distinct().size)
        assertTrue(machineIds.all{
            byId.getValue(it).movement.expectedLoadBasis==
                LoadBasis.EXERCISE_OVERRIDE
        })
        assertEquals("flat-smith-station",
            byId.getValue("smith-bench").movement.equipmentId)
        assertEquals("squat-smith-station",
            byId.getValue("smith-squat").movement.equipmentId)
        assertTrue(listOf("smith-bench","smith-squat").all{
            byId.getValue(it).movement.expectedLoadBasis==LoadBasis.SMITH_TOTAL
        })
        assertTrue(listOf("bb-curl","ez-curl","skullcrusher","cgbp").all{
            byId.getValue(it).movement.expectedLoadBasis==LoadBasis.BARBELL_TOTAL
        })
        assertTrue(listOf("bulgarian","walking-lunge","arnold","front-raise").all{
            byId.getValue(it).movement.expectedLoadBasis==
                LoadBasis.DUMBBELL_PER_HAND
        })
        val bodyweight=listOf("pushup","bw-squat","diamond-pushup",
            "pike-pushup","incline-pushup","decline-pushup")
        assertTrue(bodyweight.all{
            val movement=byId.getValue(it).movement
            movement.equipmentId==null && movement.expectedLoadBasis==null &&
                movement.attachmentId==null
        })
        assertTrue(listOf("bayesian-curl","pushdown","ez-pushdown",
            "oh-cable-ext","seated-row").all{
            val movement=byId.getValue(it).movement
            movement.equipmentId=="cable-station" &&
                movement.expectedLoadBasis==LoadBasis.CABLE_STACK &&
                movement.attachmentId!=null
        })
        assertEquals("ez-bar",byId.getValue("ez-pushdown").movement.attachmentId)
        assertEquals("seated-row-handle",
            byId.getValue("seated-row").movement.attachmentId)
        assertEquals(listOf(TrainingMuscle.TRICEPS,
            TrainingMuscle.CHEST,TrainingMuscle.FRONT_DELTS),
            byId.getValue("cgbp").movement.credits.map {it.muscle})
        assertTrue(byId.getValue("machine-row").movement.credits.none{
            it.muscle==TrainingMuscle.TRICEPS ||
                it.muscle==TrainingMuscle.WRIST_FLEXORS ||
                it.muscle==TrainingMuscle.WRIST_EXTENSORS
        })
    }

    @Test fun gymCannotSilentlyBorrowOtherMachineSmithCableOrBarStock() {
        val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
        val smith=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n("65"),n("70"),n("75")))
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50"),n("60")))
        val cable=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
            mapOf(n("45") to 2,n("25") to 2,n("10") to 2,n("5") to 2))
        val stocked=mapOf("dumbbell-rack" to dumbbells,
            "flat-smith-station" to smith,
            "lying-leg-curl-machine" to machine,
            "cable-station" to cable,"straight-bar" to bar)
        val bounds=TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,
                n("10"),n("20"),0)
        }
        val template=ComposerRequest(ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map{
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },bounds,emptyList(),4,1)
        fun inspect(id:String,load:BigDecimal?,
            stock:Map<String,VerifiedLoadStock> =stocked,
            attachments:Set<String> =emptySet(),
            facilities:Set<String> =setOf("flat-bench","rack","incline-bench"))=
            CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template,listOf(CatalogExerciseChoice(id,load,90,120,2,3,2,1,1,5)),
                CatalogGym(stock,attachments,facilities)))
        fun has(id:String,load:BigDecimal?,kind:CatalogIssueType,
            stock:Map<String,VerifiedLoadStock> =stocked,
            attachments:Set<String> =emptySet(),
            facilities:Set<String> =setOf("flat-bench","rack","incline-bench"))=
            inspect(id,load,stock,attachments,facilities).issues.any{
                it.exerciseId==id && it.kind==kind
            }
        assertTrue(has("smith-squat",n("70"),CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has("smith-bench",n("70"),CatalogIssueType.FACILITY_UNAVAILABLE,
            facilities=emptySet()))
        assertTrue(has("machine-row",n("50"),CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has("lying-curl",n("55"),CatalogIssueType.LOAD_UNVERIFIED))
        assertTrue(has("seated-calf",n("50"),CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has("ez-curl",n("65"),CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has("bayesian-curl",n("25"),CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        assertTrue(has("ez-pushdown",n("25"),CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            attachments=setOf("straight-bar")))
        assertFalse(has("ez-pushdown",n("25"),CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            attachments=setOf("ez-bar")))
        assertTrue(has("bulgarian",n("25"),CatalogIssueType.FACILITY_UNAVAILABLE,
            facilities=setOf("rack")))
        assertTrue(has("walking-lunge",n("25"),CatalogIssueType.EQUIPMENT_UNAVAILABLE,
            stock=stocked-"dumbbell-rack"))
        assertTrue(has("incline-pushup",null,CatalogIssueType.FACILITY_UNAVAILABLE,
            facilities=emptySet()))
        val body=inspect("pushup",null,emptyMap(),emptySet(),emptySet())
        assertFalse(body.issues.any {
            it.exerciseId=="pushup" &&
                (it.kind==CatalogIssueType.LOAD_UNVERIFIED ||
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE)
        })
        assertFalse(body.mayIssueWorkout)
        assertFalse(body.mayOverwriteAuthoredProgram)
    }
}
