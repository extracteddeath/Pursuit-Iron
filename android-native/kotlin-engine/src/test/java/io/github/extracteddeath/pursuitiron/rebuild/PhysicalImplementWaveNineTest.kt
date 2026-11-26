package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** A source label saying "bodyweight" cannot authorize a loaded plate or spring. */
class PhysicalImplementWaveNineTest {
    private fun n(value:String)=BigDecimal(value)
    private val catalog=StarterExerciseTranche.entries()
    private val byId=catalog.associateBy{it.legacyCuratedId}
    private val plate=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("5"),n("10"),n("25")))
    private val gripper=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("60"),n("80"),n("100")))
    private val gym=CatalogGym(mapOf(
        "single-weight-plate" to plate,
        "hand-gripper-device" to gripper),emptySet(),emptySet())
    private fun template()=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,n("0"),n("20"),n("30"),0)
        },emptyList(),4,1
    )
    private fun review(id:String,load:String?="10",currentGym:CatalogGym=gym)=
        CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(
                CatalogExerciseChoice(id,load?.let(::n),90,150,2,3,3,1,1,5)
            ),currentGym))
    private fun issue(id:String,kind:CatalogIssueType,r:CatalogWeekReview)=
        r.issues.any{it.exerciseId==id && it.kind==kind}

    @Test fun waveNineAddsTwoDistinctRepetitionBasedRealResistanceIdentities() {
        assertEquals(372,catalog.size)
        assertEquals(listOf("svend-press","gripper"),
            catalog.drop(343).take(2).map{it.legacyCuratedId})
        val svend=byId.getValue("svend-press")
        val grip=byId.getValue("gripper")
        assertEquals(RepBand(12,20),svend.practicalRepBand)
        assertEquals(RepBand(10,20),grip.practicalRepBand)
        assertEquals("single-weight-plate",svend.movement.equipmentId)
        assertEquals("hand-gripper-device",grip.movement.equipmentId)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,svend.movement.expectedLoadBasis)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,grip.movement.expectedLoadBasis)
        assertFalse(svend.mayTransferPreviousLoad)
        assertFalse(grip.mayTransferPreviousLoad)
        assertFalse(CatalogPhysicalMachineEligibility.eligible(svend.movement))
        assertFalse(CatalogPhysicalMachineEligibility.eligible(grip.movement))
        assertEquals(TrainingMuscle.CHEST,svend.movement.credits.first().muscle)
        assertEquals(TrainingMuscle.WRIST_FLEXORS,
            grip.movement.credits.single().muscle)
        assertTrue(grip.mappingNotes.contains("manufacturer-rated"))
        assertTrue(svend.mappingNotes.contains("old web bodyweight"))
    }

    @Test fun loadedImplementsCannotMasqueradeAsBodyweightOrAnotherPieceOfHardware() {
        val absent=CatalogGym(emptyMap(),emptySet(),emptySet())
        for(id in listOf("svend-press","gripper")) {
            assertTrue(issue(id,CatalogIssueType.EQUIPMENT_UNAVAILABLE,
                review(id,currentGym=absent)))
            assertTrue(issue(id,CatalogIssueType.LOAD_UNVERIFIED,
                review(id,load=null)))
            assertFalse(issue(id,CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY,
                review(id,load=null)))
        }
        assertTrue(issue("svend-press",CatalogIssueType.EQUIPMENT_UNAVAILABLE,
            review("svend-press",currentGym=CatalogGym(
                mapOf("dumbbell-rack" to plate),emptySet()))))
        assertTrue(issue("gripper",CatalogIssueType.EQUIPMENT_UNAVAILABLE,
            review("gripper",currentGym=CatalogGym(
                mapOf("single-weight-plate" to plate),emptySet()))))
        assertTrue(issue("svend-press",CatalogIssueType.LOAD_UNVERIFIED,
            review("svend-press",load="15")))
        assertTrue(issue("gripper",CatalogIssueType.LOAD_UNVERIFIED,
            review("gripper",load="75")))
        assertTrue(issue("gripper",CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
            review("gripper",load="80",currentGym=CatalogGym(
                mapOf("hand-gripper-device" to VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(n("80")))),
                emptySet()))))
    }

    @Test fun ratedGripperAndPlateLoadsStayInTheirVerifiedUnit() {
        // Manufacturer-rated gripper resistance is an equipment-specific
        // setting, not a free-weight equivalent or automatically converted KG.
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(gripper,n("80"),LoadUnit.KG))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(plate,n("10"),LoadUnit.KG))
        assertNotEquals(gripper,plate)
    }

    @Test fun exactGripperSettingIsNotAutomaticallyEquivalentToAWeightPlate() {
        assertFalse(issue("svend-press",CatalogIssueType.LOAD_UNVERIFIED,
            review("svend-press",load="10")))
        assertFalse(issue("gripper",CatalogIssueType.LOAD_UNVERIFIED,
            review("gripper",load="80")))
        assertEquals("svend-press",byId.getValue("svend-press").movement.exerciseId)
        assertEquals("gripper",byId.getValue("gripper").movement.exerciseId)
        assertNotEquals(byId.getValue("svend-press").movement.familyId,
            byId.getValue("plate-front-raise").movement.familyId)
        assertFalse(review("svend-press").mayOverwriteAuthoredProgram)
        assertFalse(review("gripper",load="80").mayIssueWorkout)
    }
}
