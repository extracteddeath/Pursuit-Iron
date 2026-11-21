package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class TrainingMuscleCoverageTest {
    private val zero=BigDecimal.ZERO
    private val originalNames=listOf("CHEST","LATS","UPPER_BACK","FRONT_DELTS",
        "SIDE_DELTS","REAR_DELTS","TRICEPS","BICEPS","BRACHIALIS",
        "WRIST_FLEXORS","WRIST_EXTENSORS","QUADS","HAMSTRINGS","GLUTES","CALVES","ABS")
    private fun bounds(scope:List<TrainingMuscle>)=scope.map {
        DeclaredMuscleDoseBounds(it,zero,zero,BigDecimal("20"),0)
    }
    private fun week(scope:List<TrainingMuscle>)=DeclaredTrainingWeek(
        TrainingPhase.HYPERTROPHY,3,List(3){DeclaredSessionWindow(1,90,null,60)},
        (0..2).map {day->PlannedWorkingExposure("chest-$day",day,
            ExposureSetKind.WORKING,listOf(DeclaredMuscleCredit(
                TrainingMuscle.CHEST,MuscleCreditRole.DIRECT,BigDecimal.ONE)),30,60)},
        bounds(scope))

    @Test fun completeLegacyCoverageAndExistingEnumPositionsRemainStable() {
        assertEquals(originalNames,TrainingMuscleCoverage.legacyV1.map{it.name})
        assertEquals(originalNames,TrainingMuscle.entries.take(16).map{it.name})
        assertEquals((0..15).toList(),TrainingMuscleCoverage.legacyV1.map{it.ordinal})
        assertEquals(18,TrainingMuscleCoverage.legacyV2.size)
        assertEquals(20,TrainingMuscleCoverage.legacyV3.size)
        assertEquals(22,TrainingMuscleCoverage.legacyV4.size)
        assertEquals(25,TrainingMuscleCoverage.current.size)
        assertEquals((0..21).toList(),
            TrainingMuscleCoverage.legacyV4.map{it.ordinal})
        assertEquals(listOf(TrainingMuscle.NECK_FLEXORS,
            TrainingMuscle.NECK_EXTENSORS,TrainingMuscle.NECK_LATERAL_FLEXORS),
            TrainingMuscleCoverage.current.drop(22))
        assertTrue(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.legacyV2))
        assertEquals(2,TrainingMuscleCoverage.versionOf(TrainingMuscleCoverage.legacyV2))
        assertEquals(3,TrainingMuscleCoverage.versionOf(TrainingMuscleCoverage.legacyV3))
        assertEquals(4,TrainingMuscleCoverage.versionOf(TrainingMuscleCoverage.legacyV4))
        assertEquals(5,TrainingMuscleCoverage.versionOf(TrainingMuscleCoverage.current))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.legacyV4+
            TrainingMuscle.NECK_FLEXORS))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.current.dropLast(1)))
        assertTrue(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.legacyV1))
        assertTrue(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.current))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.legacyV1.dropLast(1)))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.legacyV1+
            TrainingMuscle.HIP_ABDUCTORS))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.current+
            TrainingMuscle.CHEST))
        assertFalse(TrainingMuscleCoverage.isComplete(TrainingMuscleCoverage.current-
            TrainingMuscle.BRACHIALIS))
        assertTrue(runCatching{(TrainingMuscleCoverage.legacyV1 as MutableList<TrainingMuscle>).clear()}.isFailure)
    }

    @Test fun oldDoseAuditsKeepTheirOriginalScopeWithoutInventingNewZeroTargets() {
        val original=WeeklyDoseFeasibilityDomain.audit(week(TrainingMuscleCoverage.legacyV1))
        assertEquals(TrainingMuscleCoverage.legacyV1.toSet(),original.muscles.keys)
        assertFalse(original.muscles.containsKey(TrainingMuscle.HIP_ADDUCTORS))
        assertEquals(BigDecimal("3"),original.muscles.getValue(TrainingMuscle.CHEST)
            .effectiveWorkingSets)
        val new=WeeklyDoseFeasibilityDomain.audit(week(TrainingMuscleCoverage.current))
        assertEquals(original.muscles,new.muscles.filterKeys{it in original.muscles})
        assertEquals(TrainingMuscleCoverage.current.toSet(),new.muscles.keys)
    }

    @Test fun hipGroupsHaveIndependentCreditsAndCannotEnterAnUnreviewedOldScope() {
        val initial=week(TrainingMuscleCoverage.current)
        val hip=PlannedWorkingExposure("abduction",0,ExposureSetKind.WORKING,
            listOf(DeclaredMuscleCredit(TrainingMuscle.HIP_ABDUCTORS,
                MuscleCreditRole.DIRECT,BigDecimal.ONE)),30,60)
        val report=WeeklyDoseFeasibilityDomain.audit(initial.copy(
            exposures=initial.exposures+hip))
        assertEquals(BigDecimal.ONE,report.muscles.getValue(TrainingMuscle.HIP_ABDUCTORS)
            .directWorkingSets)
        assertEquals(zero,report.muscles.getValue(TrainingMuscle.HIP_ADDUCTORS).directWorkingSets)
        assertEquals(zero,report.muscles.getValue(TrainingMuscle.GLUTES).directWorkingSets)
        assertTrue(runCatching{WeeklyDoseFeasibilityDomain.audit(
            initial.copy(bounds=bounds(TrainingMuscleCoverage.legacyV1),
                exposures=initial.exposures+hip))}.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun v8ReplayRetainsItsMusclePolicyWhileV9ExplicitlyAddsBothGroups() {
        val input=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V8_POLICY_VERSION))
        val new=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V9_POLICY_VERSION))
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,old.status)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,new.status)
        assertEquals("v8",old.policy?.revision)
        assertEquals("v9",new.policy?.revision)
        assertEquals(TrainingMuscleCoverage.legacyV1,
            old.policy?.selectedBounds?.map{it.muscle})
        assertEquals(TrainingMuscleCoverage.legacyV2,
            new.policy?.selectedBounds?.map{it.muscle})
        assertEquals(old.prescription?.sessions,new.prescription?.sessions)
        assertEquals(old.policy?.selectedBounds,
            new.policy?.selectedBounds?.filter{it.muscle.introducedIn==1})
    }

    @Test fun aLegacyPolicyCannotSilentlyDropAnExplicitNewHipExercise() {
        val input=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val reviewed=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V8_POLICY_VERSION,
            selectedBodyweightExerciseIds=input.selectedBodyweightExerciseIds+
                "side-lying-abduction"))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,reviewed.status)
        assertTrue(reviewed.issues.any{it.contains("added hip muscles")})
        assertNull(reviewed.prescription)
    }

    @Test fun newHipPrioritiesCannotBeAppliedToAnOldPolicyByAccident() {
        val input=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=checkNotNull(CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V8_POLICY_VERSION)).policy)
        val profile=checkNotNull(input.setup.reviewed).copy(
            focusedMuscles=setOf(TrainingMuscle.HIP_ABDUCTORS))
        val review=CoachGoalProgramPolicyDomain.review(profile,TrainingPhase.HYPERTROPHY,
            old.selectedBounds,profile.trainingWeekdays.map{
                AthleteCalendarIntent(it,ComposerSessionPurpose.HYPERTROPHY)},old)
        assertNotEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,review.status)
        assertTrue(review.issues.any{it.kind==GoalPolicyIssueKind.INVALID_DOSE})
    }
    @Test fun originalFirstProgramV11Remains22RowsWhenNewNeckV5Exists() {
        val input=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val original=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.V11_POLICY_VERSION))
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            original.status)
        assertEquals("v11",original.policy?.revision)
        assertEquals(TrainingMuscleCoverage.legacyV4,
            original.policy?.selectedBounds?.map{it.muscle})
        assertEquals(22,original.policy?.baselineBounds?.size)
        assertTrue(original.policy?.selectedBounds.orEmpty().none{
            it.muscle.introducedIn==5
        })
        assertTrue(TrainingMuscleCoverage.isComplete(
            original.policy?.selectedBounds?.map{it.muscle}.orEmpty()))
    }

    @Test fun v5NeckDoseIsIndependentWithoutUpgradingSignedV4Policy() {
        val old=WeeklyDoseFeasibilityDomain.audit(
            week(TrainingMuscleCoverage.legacyV4))
        val selected=TrainingMuscleCoverage.current
        val exposure=PlannedWorkingExposure("neck-work",0,ExposureSetKind.WORKING,
            listOf(DeclaredMuscleCredit(TrainingMuscle.NECK_FLEXORS,
                MuscleCreditRole.DIRECT,BigDecimal.ONE)),30,60)
        val modern=WeeklyDoseFeasibilityDomain.audit(
            week(selected).copy(exposures=week(selected).exposures+exposure))
        assertFalse(old.muscles.containsKey(TrainingMuscle.NECK_FLEXORS))
        assertEquals(BigDecimal.ONE,modern.muscles
            .getValue(TrainingMuscle.NECK_FLEXORS).directWorkingSets)
        for(m in listOf(TrainingMuscle.NECK_EXTENSORS,
            TrainingMuscle.NECK_LATERAL_FLEXORS,TrainingMuscle.TRAPS))
            assertEquals(zero,modern.muscles.getValue(m).directWorkingSets)
        assertTrue(runCatching{
            WeeklyDoseFeasibilityDomain.audit(
                week(TrainingMuscleCoverage.legacyV4).copy(
                    exposures=week(TrainingMuscleCoverage.legacyV4)
                        .exposures+exposure))
        }.isFailure)
    }

    @Test fun nativeCoachEditorReopensV4WithoutSilentlyAddingNeckTargets() {
        val old=TrainingMuscleCoverage.legacyV4
        assertEquals(22,old.size)
        assertEquals(old,TrainingMuscleCoverage.forEditor("v4",old))
        assertEquals(old,TrainingMuscleCoverage.forEditor(null,old))
        assertEquals(4,TrainingMuscleCoverage.versionOf(old))
        assertTrue(TrainingMuscleCoverage.forEditor("v4",old)!!
            .none{it.introducedIn>=5})
        assertEquals(TrainingMuscleCoverage.current,
            TrainingMuscleCoverage.forEditor(null,null))
    }

    @Test fun nativeCoachEditorRequiresAnExplicitV5SelectionAndNeverFillsValues() {
        val original=TrainingMuscleCoverage.legacyV4
        val newScope=TrainingMuscleCoverage.forEditor("v5",original)
        assertEquals(25,newScope?.size)
        assertEquals(original,newScope?.filter{it.introducedIn<=4})
        assertEquals(setOf(TrainingMuscle.NECK_FLEXORS,
            TrainingMuscle.NECK_EXTENSORS,
            TrainingMuscle.NECK_LATERAL_FLEXORS),
            newScope?.filter{it.introducedIn==5}?.toSet())
        assertEquals(original,TrainingMuscleCoverage.forVersion(4))
        assertEquals(TrainingMuscleCoverage.current,TrainingMuscleCoverage.forVersion(5))
        assertNull(TrainingMuscleCoverage.forVersion(6))
        for(marker in listOf("v6","v0","vnull","v-1","v04","4","","vgarbled")) {
            assertNull(marker,TrainingMuscleCoverage.forEditor(marker,original))
        }
        assertNull(TrainingMuscleCoverage.forEditor(null,original.dropLast(1)))
        assertEquals(original,TrainingMuscleCoverage.forEditor("v4",null))
        // A stale editor marker may not silently drop a signed v4 policy's
        // remaining 6 muscles. Only the explicit new v5 choice is allowed.
        assertNull(TrainingMuscleCoverage.forEditor("v1",original))
        assertNull(TrainingMuscleCoverage.forEditor("v2",original))
        assertNull(TrainingMuscleCoverage.forEditor("v3",original))
        assertNull(TrainingMuscleCoverage.forEditor("v4",TrainingMuscleCoverage.current))
        assertEquals(TrainingMuscleCoverage.legacyV1,
            TrainingMuscleCoverage.forEditor("v1",null))
        assertEquals(TrainingMuscleCoverage.current,
            TrainingMuscleCoverage.forEditor("v5",TrainingMuscleCoverage.legacyV1))
    }

}
