package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedHipCatalogTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=listOf("hip-abduction","cable-abduction",
        "standing-cable-abduction","side-lying-abduction","adduction-machine",
        "cable-adduction","cossack-squat","adductor-sumo")
    private val all=StarterExerciseTranche.entries()
    private val added=all.drop(293)
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(it,
            BigDecimal.ZERO,n("12"),n("20"),0)},emptyList(),4,1)
    private fun review(id:String,stock:Boolean=true,cuff:Boolean=true,
        legacy:Boolean=false,override:BigDecimal?=null):CatalogWeekReview {
        val movement=added.single{it.legacyCuratedId==id}.movement
        val basis=movement.expectedLoadBasis
        val gym=CatalogGym(
            if(stock && basis!=null)mapOf(checkNotNull(movement.equipmentId) to
                VerifiedLoadStock.Totals(LoadUnit.LB,basis,listOf(n("15"),n("20"),n("25"))))
            else emptyMap(),if(cuff)setOf("ankle-cuff") else emptySet())
        val request=if(legacy)template.copy(bounds=template.bounds.filter{
            it.muscle.introducedIn==1}) else template
        return CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            request,listOf(CatalogExerciseChoice(id,override ?:if(basis==null)null else n("20"),
                60,90,2,3,2,1,1,5)),gym))
    }

    @Test fun eightSourceIdentitiesUseSeparateHipLedgersAndRemainAppendOnly() {
        assertEquals(301,all.size)
        assertEquals(ids,added.map{it.legacyCuratedId})
        assertEquals(all.size,all.map{it.movement.exerciseId}.distinct().size)
        assertEquals(all.size,all.map{it.movement.familyId to it.movement.variantId}.distinct().size)
        for((index,entry) in added.withIndex()) {
            val expected=if(index<4)TrainingMuscle.HIP_ABDUCTORS else TrainingMuscle.HIP_ADDUCTORS
            val direct=entry.movement.credits.filter{it.role==MuscleCreditRole.DIRECT}
            assertEquals(listOf(expected),direct.map{it.muscle})
            assertEquals(BigDecimal.ONE,direct.single().fractionalSet)
            assertEquals(entry.practicalRepBand,entry.movement.sourcePracticalReps)
            assertFalse(entry.mayTransferPreviousLoad)
            if(index<4)assertFalse(entry.movement.credits.any{it.muscle==TrainingMuscle.GLUTES})
        }
    }

    @Test fun eachLoadedMovementNeedsItsExactPhysicalStockAndCableCuff() {
        for(id in ids) {
            val movement=added.single{it.legacyCuratedId==id}.movement
            assertTrue("$id: ${review(id).issues}",review(id).issues.isEmpty())
            if(movement.expectedLoadBasis!=null) {
                assertTrue(id,review(id,stock=false).issues.any{
                    it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
                assertTrue(id,review(id,override=n("999")).issues.any{
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED})
            } else assertTrue(review(id,override=n("20")).issues.any{
                it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY})
            if(movement.attachmentId!=null)assertTrue(id,review(id,cuff=false).issues.any{
                it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE})
            assertTrue(id,review(id,legacy=true).issues.any{
                it.kind==CatalogIssueType.MUSCLE_TARGETS_UNDECLARED})
        }
        val abduction=added.first{it.legacyCuratedId=="hip-abduction"}.movement
        val adduction=added.first{it.legacyCuratedId=="adduction-machine"}.movement
        assertEquals("hip-abduction-machine",abduction.equipmentId)
        assertEquals("hip-adduction-machine",adduction.equipmentId)
        assertNotEquals(abduction.equipmentId,adduction.equipmentId)
    }

    @Test fun oneDumbbellAndCompoundCreditConventionsAreExplicit() {
        for(id in listOf("cossack-squat","adductor-sumo")) {
            val entry=added.single{it.legacyCuratedId==id}
            assertEquals(LoadBasis.DUMBBELL_PER_HAND,entry.movement.expectedLoadBasis)
            assertTrue(entry.mappingNotes.contains("one "))
            assertTrue(entry.movement.credits.any{it.muscle==TrainingMuscle.QUADS &&
                it.role==MuscleCreditRole.INDIRECT && it.fractionalSet==n("0.25")})
        }
    }

    @Test fun actualHipPrescriptionsReachGeneratedProgramsAndEveryConvertedCycleWeek() {
        for((split,bucket) in listOf(
            ComposerSplit.UPPER_LOWER to SessionTimeBucket.FORTY_TO_SIXTY,
            ComposerSplit.FULL_BODY to SessionTimeBucket.SIXTY_TO_NINETY)) {
            val base=StarterProgramReleaseFixture.request(split,TrainingPhase.HYPERTROPHY)
            val profile=checkNotNull(base.setup.reviewed)
            val machines=listOf("hip-abduction-machine","hip-adduction-machine")
                .associateWith {VerifiedLoadStock.Totals(LoadUnit.LB,
                    LoadBasis.EXERCISE_OVERRIDE,listOf(n("15"),n("20"),n("25")))}
            val input=base.copy(setup=base.setup.copy(reviewed=profile.copy(
                sessionTimeBucket=bucket,
                gym=profile.gym.copy(confirmedStock=profile.gym.confirmedStock+machines))),
                startingLoads=base.startingLoads+mapOf(
                    "hip-abduction" to n("20"),"adduction-machine" to n("20")))
            val result=CoachFirstProgramStartDomain.review(input)
            assertEquals("$split: ${result.issues}",
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
            val prescription=checkNotNull(result.prescription)
            for(muscle in listOf(TrainingMuscle.HIP_ABDUCTORS,TrainingMuscle.HIP_ADDUCTORS)) {
                val dose=checkNotNull(prescription.finalAudit).muscles.getValue(muscle)
                assertEquals(DoseFinding.WITHIN_BOUNDS,dose.finding)
                assertEquals(0,dose.missingDirectDays)
                assertTrue(dose.directWorkingSets>=n("4"))
            }
            val converted=CoachFirstProgramDraftConversionDomain.propose(result,
                input.setup,input.catalog,"hip-cycle","Hip-inclusive training",4)
            assertEquals(converted.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,converted.status)
            val cycle=checkNotNull(converted.cycle)
            val inputs=checkNotNull(CoachFirstProgramDraftConversionDomain
                .policyForInitialFullCycleReview(result,input.setup))
            val audit=CustomCycleCoachAuditDomain.review(cycle,input.setup,input.catalog,inputs)
            assertEquals(audit.issues.toString(),
                CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
            for(weekNumber in 1..4) {
                val week=checkNotNull(CustomTrainingCycleDomain.week(
                    checkNotNull(audit.structuralReview),weekNumber))
                for(id in listOf("hip-abduction","adduction-machine")) {
                    val sets=week.days.flatMap{it.slots}.filter{it.exerciseId==id}
                        .flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
                    assertTrue("$split / week $weekNumber / $id",sets.isNotEmpty())
                    assertTrue(sets.all{(it.load as? DraftSetLoad.Verified)?.value==n("20")})
                    assertTrue(sets.all{it.reps.minimum>=12 && it.reps.maximum<=20})
                }
            }
            assertFalse(result.mayIssueWorkout)
            assertFalse(audit.mayIssueWorkout)
        }
    }
}
