package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedSerratusTibialisCatalogTest {
    private val ids=listOf("serratus-pushup","tibialis-raise")
    private val added=StarterExerciseTranche.entries().filter { it.legacyCuratedId in setOf("serratus-pushup","tibialis-raise") }
    private fun n(v:String)=BigDecimal(v)
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(it,n("0"),n("12"),n("24"),0)},emptyList(),4,1)
    private fun review(id:String,wall:Boolean=true,load:BigDecimal?=null,
        scope:List<TrainingMuscle> = TrainingMuscleCoverage.current)=
        CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(template.copy(bounds=template.bounds.filter{it.muscle in scope}),
            listOf(CatalogExerciseChoice(id,load,60,90,2,3,2,1,1,5)),
            CatalogGym(emptyMap(),emptySet(),if(wall)setOf("wall-support") else emptySet())))

    @Test fun exactSourceIdentitiesDeclareSeparateAnatomyAndUnweightedConventions() {
        assertEquals(ids,added.map{it.legacyCuratedId})
        assertEquals(listOf(RepBand(12,20),RepBand(15,25)),added.map{it.practicalRepBand})
        assertEquals(listOf(TrainingMuscle.SERRATUS_ANTERIOR,TrainingMuscle.TIBIALIS_ANTERIOR),
            added.map{it.movement.credits.single().muscle})
        assertTrue(added.all{it.movement.expectedLoadBasis==null && it.movement.equipmentId==null && !it.mayTransferPreviousLoad})
        assertTrue(StarterExerciseTranche.entries().size>=325)
        assertEquals((0..19).toList(),TrainingMuscleCoverage.legacyV3.map{it.ordinal})
    }

    @Test fun noExternalLoadOrUnconfirmedWallCanBeInferred() {
        for(id in ids) {
            assertTrue(review(id).issues.toString(),review(id).issues.isEmpty())
            assertTrue(review(id,load=n("0")).issues.any{it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY})
            assertTrue(review(id,load=n("20")).issues.any{it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY})
        }
        assertTrue(review("tibialis-raise",wall=false).issues.any{it.kind==CatalogIssueType.FACILITY_UNAVAILABLE})
        assertTrue(review("serratus-pushup",wall=false).issues.isEmpty())
    }

    @Test fun allThreeOldScopesRejectUntrackedCreditsInsteadOfCountingThemAsAbsOrCalves() {
        for(scope in listOf(TrainingMuscleCoverage.legacyV1,TrainingMuscleCoverage.legacyV2,TrainingMuscleCoverage.legacyV3))
            for(id in ids)assertTrue("$id/${scope.size}",review(id,scope=scope).issues.any{it.kind==CatalogIssueType.MUSCLE_TARGETS_UNDECLARED})
    }

    @Test fun observedSerratusAndDorsiflexionNeverBecomeAbdominalOrCalfVolume() {
        val summary=MuscleVolumeAccountingDomain.summarize(added.flatMapIndexed{index,entry->
            listOf(ObservedMuscleSet("working-$index",ExposureSetKind.WORKING,SetOutcome.COMPLETED,entry.movement.credits),
                ObservedMuscleSet("skipped-$index",ExposureSetKind.WORKING,SetOutcome.SKIPPED,entry.movement.credits),
                ObservedMuscleSet("failed-$index",ExposureSetKind.WORKING,SetOutcome.FAILED_ATTEMPT,entry.movement.credits))})
        for(m in listOf(TrainingMuscle.SERRATUS_ANTERIOR,TrainingMuscle.TIBIALIS_ANTERIOR))
            assertEquals(BigDecimal.ONE,summary.totals.getValue(m).workingDirect)
        for(m in listOf(TrainingMuscle.ABS,TrainingMuscle.CALVES))
            assertEquals(BigDecimal.ZERO,summary.totals.getValue(m).workingEquivalent)
        assertEquals(2,summary.completedWorking);assertEquals(2,summary.skippedWorking);assertEquals(2,summary.failedWorking)
    }

    @Test fun v10RetainsItsExactTwentyRowsAndFrozenV11DoesNotAlterExistingPrescriptions() {
        val input=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=CoachFirstProgramStartDomain.review(input.copy(policyVersion=CoachFirstProgramStartDomain.V10_POLICY_VERSION))
        val current=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V11_POLICY_VERSION))
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,old.status)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,current.status)
        assertEquals("v10",old.policy?.revision);assertEquals("v11",current.policy?.revision)
        assertEquals(TrainingMuscleCoverage.legacyV3,old.policy?.selectedBounds?.map{it.muscle})
        assertEquals(TrainingMuscleCoverage.legacyV4,
            current.policy?.selectedBounds?.map{it.muscle})
        assertEquals(old.prescription?.sessions,current.prescription?.sessions)
        assertEquals(old.policy?.selectedBounds,current.policy?.selectedBounds?.filter{it.muscle.introducedIn<=3})
        for(id in ids) {
            val athlete=checkNotNull(input.setup.reviewed)
            val withWall=input.setup.copy(reviewed=athlete.copy(gym=athlete.gym.copy(
                availableFacilities=athlete.gym.availableFacilities+"wall-support")))
            val rejected=CoachFirstProgramStartDomain.review(input.copy(setup=withWall,
                policyVersion=CoachFirstProgramStartDomain.V10_POLICY_VERSION,
                selectedBodyweightExerciseIds=input.selectedBodyweightExerciseIds+id))
            assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,rejected.status)
            assertNull(rejected.prescription)
        }
    }

    @Test fun newMusclePrioritiesRequireReviewedCurrentCoverage() {
        val input=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=checkNotNull(CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V10_POLICY_VERSION)).policy)
        for(muscle in listOf(TrainingMuscle.SERRATUS_ANTERIOR,TrainingMuscle.TIBIALIS_ANTERIOR)) {
            val athlete=checkNotNull(input.setup.reviewed).copy(focusedMuscles=setOf(muscle))
            val review=CoachGoalProgramPolicyDomain.review(athlete,TrainingPhase.HYPERTROPHY,old.selectedBounds,
                athlete.trainingWeekdays.map{AthleteCalendarIntent(it,ComposerSessionPurpose.HYPERTROPHY)},old)
            assertNotEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,review.status)
            assertTrue(review.issues.any{it.kind==GoalPolicyIssueKind.INVALID_DOSE})
        }
    }

    @Test fun fullProgramAndEveryConvertedWeekPreserveBothNewBodyweightTargets() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val athlete=checkNotNull(base.setup.reviewed)
        val input=base.copy(setup=base.setup.copy(reviewed=athlete.copy(sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            gym=athlete.gym.copy(availableFacilities=athlete.gym.availableFacilities+"wall-support"))),
            selectedBodyweightExerciseIds=base.selectedBodyweightExerciseIds+ids)
        val result=CoachFirstProgramStartDomain.review(input)
        assertEquals(result.issues.toString(),FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        for(m in listOf(TrainingMuscle.SERRATUS_ANTERIOR,TrainingMuscle.TIBIALIS_ANTERIOR)) {
            val audit=checkNotNull(result.prescription?.finalAudit).muscles.getValue(m)
            assertEquals(DoseFinding.WITHIN_BOUNDS,audit.finding)
            assertTrue(audit.directWorkingSets>=n("4"))
        }
        val conversion=CoachFirstProgramDraftConversionDomain.propose(result,input.setup,input.catalog,"new-groups","New groups",4)
        assertEquals(conversion.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,conversion.status)
        val saved=checkNotNull(conversion.cycle)
        val unweightedSlots=saved.blocks.flatMap{it.days}.flatMap{it.slots}
            .filter{it.exerciseId in ids}
        assertTrue(unweightedSlots.isNotEmpty())
        assertTrue(unweightedSlots.all{slot->
            slot.progressionMethod==SavedProgressionMethod.BODYWEIGHT_REPS &&
                slot.sets.filter{it.type==DraftSetType.WORKING}.all {set->
                    set.load==DraftSetLoad.ExplicitBodyweight &&
                        set.exactRepTarget==set.reps.minimum
                }
        })
        val policy=checkNotNull(CoachFirstProgramDraftConversionDomain.policyForInitialFullCycleReview(result,input.setup))
        val audit=CustomCycleCoachAuditDomain.review(checkNotNull(conversion.cycle),input.setup,input.catalog,policy)
        assertEquals(audit.issues.toString(),CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        for(number in 1..4) {
            val week=checkNotNull(CustomTrainingCycleDomain.week(checkNotNull(audit.structuralReview),number))
            for(entry in added) {
                val sets=week.days.flatMap{it.slots}.filter{it.exerciseId==entry.legacyCuratedId}.flatMap{it.sets}
                assertTrue(sets.isNotEmpty())
                assertTrue(sets.all{it.load==DraftSetLoad.ExplicitBodyweight && it.type==DraftSetType.WORKING &&
                    it.reps.minimum>=entry.practicalRepBand.minimum && it.reps.maximum<=entry.practicalRepBand.maximum})
            }
        }
        assertFalse(result.mayIssueWorkout)
    }
}
