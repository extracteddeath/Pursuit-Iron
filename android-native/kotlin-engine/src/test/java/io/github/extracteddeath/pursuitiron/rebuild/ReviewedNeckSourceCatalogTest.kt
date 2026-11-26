package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Legacy source calls weighted neck variants "bodyweight" and groups all
 * neck fibers in one ambiguous bucket. Neither claim grants an issued load.
 * These fixtures exercise the SAME source-backed CatalogWeek review used
 * by native Gym and native athlete-authored selections.
 */
class ReviewedNeckSourceCatalogTest {
    private val ids=listOf("neck-extension","neck-curl","neck-harness","neck-lateral")
    private val entries=StarterExerciseTranche.entries().filter {
        it.legacyCuratedId in ids
    }
    private fun n(v:String)=BigDecimal(v)
    private val stableLoad=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("2.5"),n("5"),n("7.5")))
    private val template=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{day->ComposerDay(day,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscleCoverage.current.map{muscle->
            DeclaredMuscleDoseBounds(muscle,n("0"),n("12"),n("24"),0)
        },emptyList(),4,1)

    private fun assess(
        id:String,selected:BigDecimal?=n("5"),available:Boolean=true,
        fixture:Boolean=true,
        basis:LoadBasis=LoadBasis.EXERCISE_OVERRIDE,
        scope:List<TrainingMuscle> =TrainingMuscleCoverage.current
    ):CatalogWeekReview {
        val movement=entries.single{it.legacyCuratedId==id}.movement
        val stock=mapOf(checkNotNull(movement.equipmentId) to
            VerifiedLoadStock.Totals(LoadUnit.LB,basis,
                listOf(n("2.5"),n("5"),n("7.5"))))
        return CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(
                template.copy(bounds=template.bounds.filter{it.muscle in scope}),
                listOf(CatalogExerciseChoice(id,selected,60,90,2,3,2,1,1,8)),
                CatalogGym(if(available)stock else emptyMap(),emptySet(),
                    if(fixture)movement.requiredFacilityIds else emptySet())))
    }

    private fun rejects(id:String,reason:CatalogIssueType,selected:BigDecimal?=n("5"),
        available:Boolean=true,fixture:Boolean=true,
        basis:LoadBasis=LoadBasis.EXERCISE_OVERRIDE,
        scope:List<TrainingMuscle> =TrainingMuscleCoverage.current)=
        assess(id,selected,available,fixture,basis,scope)
            .issues.any{it.kind==reason}

    @Test fun sourceNeckIdsAppendWithoutModifyingPreviousWorkoutIdentity() {
        assertEquals(ids,entries.map{it.legacyCuratedId})
        assertEquals(ids,StarterExerciseTranche.entries().drop(365).take(4)
            .map{it.legacyCuratedId})
        assertEquals(372,StarterExerciseTranche.entries().size)
        assertTrue(entries.all {it.practicalRepBand==RepBand(12,20)})
        assertTrue(entries.all{it.movement.pattern==ComposerPattern.ISOLATION &&
            it.movement.role==ComposerRole.ACCESSORY &&
            it.movement.expectedLoadBasis==LoadBasis.EXERCISE_OVERRIDE &&
            it.movement.equipmentId!=null && !it.mayTransferPreviousLoad})
        assertEquals(4,entries.map{it.movement.equipmentId}.distinct().size)
        assertEquals(4,entries.map{it.movement.variantId}.distinct().size)
        assertTrue(entries.all{it.mappingNotes.contains("verified",ignoreCase=true) ||
            it.mappingNotes.contains("confirmed",ignoreCase=true)})
    }

    @Test fun independentlyVerifiedResistanceAndSupportedSetupAreMandatory() {
        for(id in ids) {
            val result=assess(id)
            assertTrue("$id: ${result.issues}",result.issues.isEmpty())
            assertTrue(id,rejects(id,CatalogIssueType.EQUIPMENT_UNAVAILABLE,
                available=false))
            assertTrue(id,rejects(id,CatalogIssueType.FACILITY_UNAVAILABLE,
                fixture=false))
            assertTrue(id,rejects(id,CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
                basis=LoadBasis.MACHINE_STACK))
            assertTrue(id,rejects(id,CatalogIssueType.LOAD_UNVERIFIED,
                selected=n("6")))
            assertTrue(id,rejects(id,CatalogIssueType.LOAD_UNVERIFIED,
                selected=null))
        }
        assertEquals(4,entries.map{it.movement.requiredFacilityIds.single()}
            .distinct().size)
    }

    @Test fun neckMusclesAreThreeAnatomicalFunctionsNotACommonTrapsBucket() {
        val muscles=entries.associate{it.legacyCuratedId to
            it.movement.credits.single().muscle}
        assertEquals(TrainingMuscle.NECK_EXTENSORS,muscles["neck-extension"])
        assertEquals(TrainingMuscle.NECK_FLEXORS,muscles["neck-curl"])
        assertEquals(TrainingMuscle.NECK_EXTENSORS,muscles["neck-harness"])
        assertEquals(TrainingMuscle.NECK_LATERAL_FLEXORS,muscles["neck-lateral"])
        assertTrue(entries.all{it.movement.credits.size==1 &&
            it.movement.credits.single().role==MuscleCreditRole.DIRECT &&
            it.movement.credits.single().fractionalSet==BigDecimal.ONE})
        val observations=entries.mapIndexed {i,entry->
            ObservedMuscleSet("neck-$i",ExposureSetKind.WORKING,
                SetOutcome.COMPLETED,entry.movement.credits)
        }+listOf(
            ObservedMuscleSet("skipped-neck",ExposureSetKind.WORKING,
                SetOutcome.SKIPPED,entries.first().movement.credits))
        val totals=MuscleVolumeAccountingDomain.summarize(observations)
        assertEquals(n("2"),totals.totals.getValue(
            TrainingMuscle.NECK_EXTENSORS).workingDirect)
        assertEquals(n("1"),totals.totals.getValue(
            TrainingMuscle.NECK_FLEXORS).workingDirect)
        assertEquals(n("1"),totals.totals.getValue(
            TrainingMuscle.NECK_LATERAL_FLEXORS).workingDirect)
        assertEquals(n("0"),totals.totals.getValue(
            TrainingMuscle.TRAPS).workingEquivalent)
        assertEquals(4,totals.completedWorking)
        assertEquals(1,totals.skippedWorking)
    }

    @Test fun oldSignedV4PolicyCannotSilentlyAcceptNeckExposure() {
        for(id in ids) {
            assertTrue(id,rejects(id,CatalogIssueType.MUSCLE_TARGETS_UNDECLARED,
                scope=TrainingMuscleCoverage.legacyV4))
            assertTrue(id,rejects(id,CatalogIssueType.MUSCLE_TARGETS_UNDECLARED,
                scope=TrainingMuscleCoverage.legacyV3))
        }
        assertEquals(4,TrainingMuscleCoverage.versionOf(
            TrainingMuscleCoverage.legacyV4))
        assertEquals(5,TrainingMuscleCoverage.versionOf(
            TrainingMuscleCoverage.current))
    }
}
