package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class GeneratedAdaptiveBlockSafetyTest {
    private fun cycle()=CustomTrainingCycle("cycle-1",4,"athlete-1",
        AthleteTrainingGoal.HYPERTROPHY,"Existing cycle",
        listOf(CustomTrainingBlock("block-1","Future",
            TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),emptyList())))
    private fun intent(source:CustomTrainingCycle)=
        GeneratedAdaptiveBlockRequest(source,"block-1","option-1",
            AthleteProgramGenerationRequest(
                CoachAthleteSetupDomain.review(AthleteSetupDraft()),
                TrainingPhase.HYPERTROPHY,null,emptyList(),emptyList(),
                emptyMap(),3,1),emptyList())

    @Test fun unreviewedProfileFailsBeforeGeneration() {
        val source=cycle()
        val result=GeneratedAdaptiveBlockDomain.propose(intent(source))
        assertEquals(GeneratedAdaptiveBlockStatus.INVALID_SOURCE,result.status)
        assertNull(result.proposedOption)
        assertFalse(result.mayIssueWorkout)
        assertEquals(source,cycle())
    }

    @Test fun missingTargetAndRecoveryBlockCannotBeReplaced() {
        val source=cycle()
        assertEquals(GeneratedAdaptiveBlockStatus.INVALID_SOURCE,
            GeneratedAdaptiveBlockDomain.propose(
                intent(source).copy(targetBlockId="missing")).status)
        val recovery=source.copy(blocks=listOf(
            source.blocks.single().copy(purpose=CustomBlockPurpose.RECOVERY)))
        assertEquals(GeneratedAdaptiveBlockStatus.INVALID_SOURCE,
            GeneratedAdaptiveBlockDomain.propose(intent(recovery)).status)
    }
}
