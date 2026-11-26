package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Eligibility must use genuine receipt history, not just an unsaved checkbox. */
internal class NativeKotlinMovementSwapEligibilityTest {
    private fun load()=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("30"))
    private fun generated():KotlinWorkoutJournalState {
        val source=KotlinTrainingSourceRevision(
            "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
        val target=KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"db-press:working-1",
            DraftSetType.WORKING,0,load(),RepBand(8,12),2,60,120)
        val issued=KotlinStagedWorkout(source,"grant-1","session-1",0,0,
            ComposerSessionPurpose.HYPERTROPHY,40,listOf(target))
        return KotlinWorkoutJournalDomain.begin(issued,
            KotlinStoredWorkoutIdentity("storage-1","session-1",source,1))
    }
    private fun custom(linked:Boolean=false):CustomWorkoutJournalDraft {
        val set=CustomWorkoutSetSnapshot("slot-a:s1","slot-a",1,
            CustomAuthoredSet(DraftSetType.WORKING,load(),
                RepBand(8,12),2,45,90,null))
        val slot=CustomWorkoutSlotSnapshot(
            "slot-a","db-press","press","dumbbell",
            ComposerRole.PRIMARY,null,if(linked)"pair-1" else null,true,"",
            listOf(set))
        val issued=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"phase",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),900,null)
        return CustomWorkoutJournalDomain.begin("workout-1",issued,"a".repeat(64))
    }

    @Test fun generatedRepeatedMovementCannotOpenAmbiguousInjuryReplacement() {
        val first=generated()
        val target=first.snapshot.targets.single()
        val repeated=first.snapshot.copy(targets=listOf(target,
            target.copy(exerciseId="row",setKey="row:working-1"),
            target.copy(setKey="db-press:working-2",ordinal=1)))
        val journal=KotlinWorkoutJournalDomain.begin(repeated,first.identity)
        assertFalse(NativeMovementSwapEligibility.generated(journal,"db-press"))
        assertTrue(NativeMovementSwapEligibility.generated(journal,"row"))
        assertNull(WorkoutRemainingExerciseDomain.generated(journal,"db-press"))
    }

    @Test fun generatedRecordedOrCorrectedWorkNeverReopensReplacement() {
        val original=generated()
        assertTrue(NativeMovementSwapEligibility.generated(original,"db-press"))
        assertFalse(NativeMovementSwapEligibility.generated(original,"other"))
        assertFalse(NativeMovementSwapEligibility.generated(original,null))
        val operation=KotlinJournalOperation.RecordSet(
            "r1",original.revision,"db-press:working-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,8,2))
        val saved=(KotlinWorkoutJournalDomain.reduce(original,operation)
            as KotlinJournalDecision.Proposed).state
        assertFalse(NativeMovementSwapEligibility.generated(saved,"db-press"))
        val corrected=KotlinJournalOperation.RecordSet(
            "r2",saved.revision,"db-press:working-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,8,2),"r1")
        val newJournal=(KotlinWorkoutJournalDomain.reduce(saved,corrected)
            as KotlinJournalDecision.Proposed).state
        assertFalse(NativeMovementSwapEligibility.generated(newJournal,"db-press"))
        assertEquals(original.snapshot,newJournal.snapshot)
    }

    @Test fun customRecordedAndLinkedSlotsRemainUnavailable() {
        val open=custom()
        assertTrue(NativeMovementSwapEligibility.custom(open,"slot-a"))
        assertFalse(NativeMovementSwapEligibility.custom(open,"wrong-slot"))
        assertFalse(NativeMovementSwapEligibility.custom(open,null))
        assertFalse(NativeMovementSwapEligibility.custom(custom(linked=true),"slot-a"))
        val event=CustomWorkoutJournalOperation.Record(
            "event-1",open.revision,
            CustomWorkoutVisitKey("slot-a:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.SKIPPED,null)
        val saved=(CustomWorkoutJournalDomain.reduce(open,event)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertFalse(NativeMovementSwapEligibility.custom(saved,"slot-a"))
        assertEquals(open.snapshot,saved.snapshot)
    }
}
