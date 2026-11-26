package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Paired web metadata is provenance, NOT approval to inherit its load units. */
class PhysicalImplementWaveEightTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=listOf(
        "dumbbell_crunch",
        "cable-radial-dev",
        "hammer-radial-dev",
        "hammer-ulnar-dev",
        "wrist-roller",
        "plate-front-raise"
    )
    private val catalog=StarterExerciseTranche.entries()
    private val byId=catalog.associateBy{it.legacyCuratedId}
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25")))
    private val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.CABLE_STACK,listOf(n("10"),n("15"),n("20")))
    private val lever=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("1"),n("2"),n("3")))
    private val roller=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("5"),n("10"),n("15")))
    private val plate=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("10"),n("20"),n("25")))
    private val stock=mapOf(
        "dumbbell-rack" to db,
        "cable-station" to cable,
        "wrist-lever-hammer" to lever,
        "wrist-roller-handle" to roller,
        "single-weight-plate" to plate
    )
    private val loads=mapOf(
        "dumbbell_crunch" to "20",
        "cable-radial-dev" to "20",
        "hammer-radial-dev" to "2",
        "hammer-ulnar-dev" to "2",
        "wrist-roller" to "10",
        "plate-front-raise" to "20"
    )
    private fun template()=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,n("0"),n("20"),n("30"),0)
        },emptyList(),4,1
    )
    private fun review(id:String,weight:String?=loads.getValue(id),
        inventory:Map<String,VerifiedLoadStock> = stock
    )=CoachExerciseCatalogDomain.review(
        StarterExerciseTranche.forGym(template(),listOf(
            CatalogExerciseChoice(id,weight?.let(::n),90,150,2,3,3,1,1,5)
        ),CatalogGym(inventory,emptySet(),emptySet()))
    )
    private fun issue(id:String,kind:CatalogIssueType,result:CatalogWeekReview)=
        result.issues.any{it.exerciseId==id && it.kind==kind}

    @Test fun sixSourceMatchedIdentitiesAppendOnlyWithIndependentlyAuthoredRepBands() {
        assertEquals(370,catalog.size)
        assertEquals(ids,catalog.drop(337).take(6).map{it.legacyCuratedId})
        assertTrue(ids.all{!byId.getValue(it).mayTransferPreviousLoad})
        assertEquals(ids.size,ids.map{
            byId.getValue(it).movement.let{movement->
                movement.familyId to movement.variantId
            }
        }.distinct().size)
        assertEquals(listOf(RepBand(8,30),RepBand(12,20),
            RepBand(12,20),RepBand(12,20),RepBand(10,15),RepBand(12,20)),
            ids.map{byId.getValue(it).practicalRepBand})
    }

    @Test fun sourceBodyweightAndDumbbellMetadataNeverCreateFakeStock() {
        val implements=mapOf(
            "dumbbell_crunch" to ("dumbbell-rack" to LoadBasis.DUMBBELL_PER_HAND),
            "cable-radial-dev" to ("cable-station" to LoadBasis.CABLE_STACK),
            "hammer-radial-dev" to ("wrist-lever-hammer" to LoadBasis.EXERCISE_OVERRIDE),
            "hammer-ulnar-dev" to ("wrist-lever-hammer" to LoadBasis.EXERCISE_OVERRIDE),
            "wrist-roller" to ("wrist-roller-handle" to LoadBasis.EXERCISE_OVERRIDE),
            "plate-front-raise" to ("single-weight-plate" to LoadBasis.EXERCISE_OVERRIDE)
        )
        implements.forEach{(id,implement)->
            val movement=byId.getValue(id).movement
            assertEquals(implement.first,movement.equipmentId)
            assertEquals(implement.second,movement.expectedLoadBasis)
            assertFalse(issue(id,CatalogIssueType.LOAD_UNVERIFIED,review(id)))
            assertFalse(issue(id,CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,review(id)))
            assertTrue(issue(id,CatalogIssueType.EQUIPMENT_UNAVAILABLE,
                review(id,inventory=stock-implement.first)))
            assertTrue(issue(id,CatalogIssueType.LOAD_UNVERIFIED,
                review(id,weight=null)))
            assertFalse(review(id).mayIssueWorkout)
            assertFalse(review(id).mayOverwriteAuthoredProgram)
        }
        assertTrue("Physical plate is not a dumbbell rack",issue(
            "plate-front-raise",CatalogIssueType.EQUIPMENT_UNAVAILABLE,
            review("plate-front-raise",inventory=mapOf("dumbbell-rack" to db))))
        assertTrue("A roller may not be represented as unloaded bodyweight",issue(
            "wrist-roller",CatalogIssueType.LOAD_UNVERIFIED,
            review("wrist-roller",weight=null)))
        assertTrue(issue("cable-radial-dev",CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
            review("cable-radial-dev",inventory=stock+
                ("cable-station" to roller))))
    }

    @Test fun noUnreviewedWeightRoundingOrCrossImplementSubstitution() {
        assertTrue(issue("dumbbell_crunch",CatalogIssueType.LOAD_UNVERIFIED,
            review("dumbbell_crunch",weight="22")))
        assertTrue(issue("hammer-radial-dev",CatalogIssueType.LOAD_UNVERIFIED,
            review("hammer-radial-dev",weight="4")))
        assertTrue(issue("wrist-roller",CatalogIssueType.LOAD_UNVERIFIED,
            review("wrist-roller",weight="12")))
        assertTrue(issue("plate-front-raise",CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
            review("plate-front-raise",inventory=stock+
                ("single-weight-plate" to db))))
        assertNotEquals(byId.getValue("hammer-radial-dev").movement.variantId,
            byId.getValue("hammer-ulnar-dev").movement.variantId)
        assertTrue(byId.getValue("plate-front-raise").mappingNotes
            .contains("legacy web record"))
    }

    @Test fun forearmFlexorsAndExtensorsRemainSeparateCreditedMuscles() {
        val wristRoller=byId.getValue("wrist-roller").movement
        assertEquals(setOf(TrainingMuscle.WRIST_FLEXORS,TrainingMuscle.WRIST_EXTENSORS),
            wristRoller.credits.map{it.muscle}.toSet())
        assertTrue(wristRoller.credits.all{it.role==MuscleCreditRole.DIRECT})
        assertEquals(TrainingMuscle.WRIST_EXTENSORS,
            byId.getValue("cable-radial-dev").movement.credits.first().muscle)
        assertEquals(TrainingMuscle.WRIST_EXTENSORS,
            byId.getValue("hammer-radial-dev").movement.credits.first().muscle)
        assertEquals(TrainingMuscle.WRIST_FLEXORS,
            byId.getValue("hammer-ulnar-dev").movement.credits.first().muscle)
        assertEquals(TrainingMuscle.ABS,
            byId.getValue("dumbbell_crunch").movement.credits.single().muscle)
        assertEquals(TrainingMuscle.FRONT_DELTS,
            byId.getValue("plate-front-raise").movement.credits.single().muscle)
    }
}
