package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Newly source-linked, high-rep bodyweight movements must survive the REAL
 * initial program → prescribed working sets → saved custom cycle pathway.
 *
 * This proves a source-valid starter target, NOT a calibrated personal
 * rep-capacity prediction or approval to issue workouts.
 */
class FirstProgramPracticalRepsV8Test {
    private fun athlete():FirstProgramStartRequest {
        val original=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        return original.copy(
            startingLoads=original.startingLoads-"back_squat",
            selectedBodyweightExerciseIds=setOf("bw-squat"),
            nonSetSecondsPerDay=900)
    }

    @Test fun selectedHighRepSquatReachesActualPrescribedWorkingSets() {
        val source=athlete()
        val squat=source.catalog.single{it.exerciseId=="bw-squat"}
        assertEquals(ComposerRole.SECONDARY,squat.role)
        assertEquals(RepBand(15,25),squat.sourcePracticalReps)
        assertNull(squat.equipmentId)
        val reviewed=CoachFirstProgramStartDomain.review(source)
        assertEquals(
            "source valid but unusable: issues=${reviewed.issues}; "+
                "generation=${reviewed.generation?.issues}; "+
                "prescription=${reviewed.prescription?.reviewNotes}",
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,reviewed.status)
        assertEquals("v8",reviewed.policy?.revision)
        assertTrue(reviewed.chosenExerciseIds.contains("bw-squat"))
        val sets=checkNotNull(reviewed.prescription).sessions
            .flatMap{it.exercises}.filter{it.exerciseId=="bw-squat"}
            .flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
        assertTrue("The verified squat vanished from the week",sets.isNotEmpty())
        assertTrue("No phantom 0 lb weight or invented 6–12 reps",sets.all{
            it.load==DraftSetLoad.ExplicitBodyweight &&
                it.targetReps==RepBand(15,20) && it.targetRir==2
        })
        val audit=checkNotNull(reviewed.prescription?.finalAudit)
        assertTrue(audit.satisfiesDeclaredHardConstraints)
        assertTrue(audit.daysOutsidePreferredWindow.isEmpty())
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.maySaveProgram)
    }

    @Test fun fullCycleConversionKeepsSquatPrescriptionsAndStillNeedsConsent() {
        val source=athlete()
        val reviewed=CoachFirstProgramStartDomain.review(source)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            reviewed.status)
        val proposed=CoachFirstProgramDraftConversionDomain.propose(
            reviewed,source.setup,source.catalog,
            "bw-first-program-v8","Starting bodyweight squat program",4)
        assertEquals(proposed.reason,
            FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,proposed.status)
        val cycle=checkNotNull(proposed.cycle)
        assertEquals(4,cycle.blocks.single().weeks)
        val actual=cycle.blocks.single().days.flatMap{it.slots}
            .filter{it.exerciseId=="bw-squat"}
            .flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
        assertTrue(actual.isNotEmpty())
        assertTrue(actual.all{
            it.load==DraftSetLoad.ExplicitBodyweight &&
                it.reps==RepBand(15,20)
        })
        val policy=checkNotNull(
            CoachFirstProgramDraftConversionDomain.policyForInitialFullCycleReview(
                reviewed,source.setup))
        val audit=CustomCycleCoachAuditDomain.review(
            cycle,source.setup,source.catalog,policy)
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertFalse(proposed.mayIssueWorkout)
        assertFalse(proposed.mayApproveProgram)
    }

    @Test fun savedV7StillReplaysOldTargetsWithoutImplicitMigration() {
        val old=athlete().copy(
            policyVersion=CoachFirstProgramStartDomain.V7_POLICY_VERSION)
        val replay=CoachFirstProgramStartDomain.review(old)
        assertTrue(CoachFirstProgramStartDomain.supportsPolicy(old.policyVersion))
        assertEquals("v7",replay.policy?.revision)
        // No restored saved program can silently take a V8 rep amendment.
        assertFalse(replay.prescription?.sessions.orEmpty().flatMap{it.exercises}
            .filter{it.exerciseId=="bw-squat"}
            .flatMap{it.sets}.filter{it.type==DraftSetType.WORKING}
            .any{it.targetReps==RepBand(15,20)})
        assertFalse(replay.mayIssueWorkout)
    }

    @Test fun newPolicyCannotInventEquipmentOrAcceptUnconsentedBodyweight() {
        val input=athlete()
        val invalid=CoachFirstProgramStartDomain.review(input.copy(
            startingLoads=input.startingLoads+
                ("one_arm_db_row" to BigDecimal("31"))))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,invalid.status)
        val absent=CoachFirstProgramStartDomain.review(input.copy(
            selectedBodyweightExerciseIds=setOf("bw-squat","unknown-bw")))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,absent.status)
        val noConsent=input.copy(setup=input.setup.copy(
            reviewed=checkNotNull(input.setup.reviewed).copy(
                allowBodyweight=false)))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,
            CoachFirstProgramStartDomain.review(noConsent).status)
    }
}
