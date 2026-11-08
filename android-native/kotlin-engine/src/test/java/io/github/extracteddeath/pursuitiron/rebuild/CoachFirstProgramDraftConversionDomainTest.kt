package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CoachFirstProgramDraftConversionDomainTest {
    private fun reviewed():FirstProgramStartReview {
        val input=GeneratedAdaptiveBlockFixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val composed=checkNotNull(generated.candidate?.catalogReview?.composedRequest)
        val prescribed=CoachSessionPrescriptionDomain.draft(
            composed,GeneratedAdaptiveBlockFixture.policies())
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,prescribed.status)
        return FirstProgramStartReview(
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            emptyList(),listOf("bench","row","squat"),
            emptySet(),emptyList(),input.policy,generated,prescribed)
    }

    @Test fun completeWeekCanBecomeExactNativeCustomDraftWithoutApproval() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        val proposed=CoachFirstProgramDraftConversionDomain.propose(
            candidate,source.setup,source.catalog,"my-first-cycle",
            "My first cycle",4)
        assertEquals(FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,proposed.status)
        val cycle=checkNotNull(proposed.cycle)
        assertEquals(1L,cycle.revision)
        assertEquals(4,cycle.blocks.first().weeks)
        assertEquals(3,cycle.blocks.first().days.size)
        assertEquals(TrainingPhase.HYPERTROPHY,cycle.blocks.first().phase)
        assertEquals(setOf("bench","row","squat"),
            cycle.blocks.first().days.flatMap {it.slots.map {s->s.exerciseId}}.toSet())
        assertTrue(cycle.blocks.first().days.flatMap {it.slots}.all {slot->
            slot.sets.any{it.type==DraftSetType.WORKING}
        })
        assertFalse(proposed.mayApproveProgram)
        assertFalse(proposed.mayIssueWorkout)
        assertEquals(candidate,reviewed())
    }

    @Test fun invalidProgramNameAndUnresolvedPrescriptionNeverBecomeSavedDraft() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        assertEquals(FirstProgramDraftStatus.INCOMPLETE_SOURCE,
            CoachFirstProgramDraftConversionDomain.propose(
                candidate,source.setup,source.catalog,"bad cycle",
                "My first cycle",4).status)
        assertEquals(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
            CoachFirstProgramDraftConversionDomain.propose(
                candidate.copy(status=FirstProgramStartStatus.DRAFT_NEEDS_REVIEW),
                source.setup,source.catalog,"my-first-cycle",
                "My first cycle",4).status)
    }
    @Test fun generatedAuditInputsReachFullCycleReviewWithoutReenteringTechnicalPolicy() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        val inputs=CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(candidate,source.setup)
        assertNotNull(inputs)
        assertEquals(source.policy,inputs?.policiesByPhase?.get(TrainingPhase.HYPERTROPHY))
        assertEquals(mapOf(0 to 2100,2 to 2100,4 to 2100),
            inputs?.nonSetSecondsByWeekday)
        val proposal=CoachFirstProgramDraftConversionDomain.propose(
            candidate,source.setup,source.catalog,"first-review",
            "My first program",4)
        assertEquals(FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,proposal.status)
        val audit=CustomCycleCoachAuditDomain.review(
            checkNotNull(proposal.cycle),source.setup,source.catalog,
            checkNotNull(inputs))
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertTrue(audit.issues.isEmpty())
        assertEquals(4,audit.weeks.size)
        assertFalse(proposal.mayApproveProgram)
        assertFalse(proposal.mayIssueWorkout)
    }

    @Test fun changedOrMissingGeneratedPolicyDoesNotTransferToApprovalAudit() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        assertNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(
                candidate.copy(status=FirstProgramStartStatus.DRAFT_NEEDS_REVIEW),
                source.setup))
        assertNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(
                candidate.copy(policy=null),source.setup))
        assertNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(
                candidate,source.setup.copy(reviewed=null)))
    }

    @Test fun savedFirstProgramPreservesEveryPlannedLoadRepTargetAndWeekday() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        val response=CoachFirstProgramDraftConversionDomain.propose(
            candidate,source.setup,source.catalog,"exact-first-cycle",
            "First exact cycle",4)
        val cycle=checkNotNull(response.cycle)
        val week=checkNotNull(candidate.prescription)
        val composed=checkNotNull(
            candidate.generation?.candidate?.catalogReview?.composedRequest)
        val days=cycle.blocks.single().days
        assertEquals(composed.days.map {it.calendarDay}.toSet(),
            days.map {it.weekday}.toSet())
        val allSlots=days.flatMap {it.slots}
        assertEquals(allSlots.size,allSlots.map {it.slotId}.distinct().size)
        for(session in week.sessions) {
            val weekday=composed.days[session.dayIndex].calendarDay
            val saved=days.single {it.weekday==weekday}
            assertEquals(session.exercises.map {it.exerciseId},
                saved.slots.map {it.exerciseId})
            for((sourceExercise,slot) in session.exercises.zip(saved.slots)) {
                assertEquals(sourceExercise.role,slot.role)
                assertEquals(sourceExercise.sets.size,slot.sets.size)
                for((planned,actual) in sourceExercise.sets.zip(slot.sets)) {
                    assertEquals(planned.type,actual.type)
                    assertEquals(planned.load,actual.load)
                    assertEquals(planned.targetReps,actual.reps)
                    assertEquals(planned.targetRir,actual.targetRir)
                    assertEquals(planned.activeSeconds,actual.activeSeconds)
                    assertEquals(planned.restSeconds,actual.restSeconds)
                }
            }
        }
        assertFalse(response.mayIssueWorkout)
    }

}
