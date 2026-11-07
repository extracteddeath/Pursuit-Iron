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
}
