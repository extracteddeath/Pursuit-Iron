package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

class NativeStrengthVolumeRepairReviewTest {
    private fun work()=CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.ExplicitBodyweight,RepBand(6,8),2,45,120)
    private fun slot(id:String,exercise:String,sets:Int)=CustomExerciseSlot(
        id,exercise,ComposerRole.PRIMARY,List(sets){work()})
    private fun draft():CustomTrainingCycle {
        val block=CustomTrainingBlock(
            "strength","Strength",TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING,2,CustomSplitIdentity.Freeform("Full Body"),
            listOf(
                CustomTrainingDay(0,"Full 1",ComposerSessionPurpose.STRENGTH_SKILL,
                    listOf(slot("bench-1","bench",2),slot("row-1","row",2))),
                CustomTrainingDay(2,"Full 2",ComposerSessionPurpose.STRENGTH_SKILL,
                    listOf(slot("squat-1","squat",2)))
            ))
        return CustomTrainingCycle("my-cycle",1,"athlete-1",
            AthleteTrainingGoal.STRENGTH,"Strength",
            listOf(block))
    }

    @Test fun showsExactDifferentWorkingSetCountsWithoutNewExerciseIdentity() {
        val original=draft()
        val block=original.blocks.single()
        val replacement=block.copy(days=block.days.map {day->
            day.copy(slots=day.slots.map {slot->
                when(slot.slotId) {
                    "bench-1"->slot.copy(sets=slot.sets+work())
                    "squat-1"->slot.copy(sets=slot.sets+work()+work())
                    else ->slot
                }
            })
        })
        val proposed=original.copy(revision=2,blocks=listOf(replacement))
        assertEquals(listOf(
            NativeStrengthVolumeRepairSlot(0,"bench-1","bench",2,3),
            NativeStrengthVolumeRepairSlot(2,"squat-1","squat",2,4)),
            reviewedStrengthVolumeSlots(original,proposed,"strength"))
        assertEquals(listOf("bench-1","row-1","squat-1"),
            proposed.blocks.single().days.flatMap{it.slots}.map{it.slotId})
        assertEquals(6,original.blocks.single().days.flatMap{it.slots}.sumOf{it.sets.size})
        assertEquals(6,original.blocks.single().days.flatMap{it.slots}.sumOf{it.sets.size})
    }

    @Test fun anUnchangedProposalHasNoFakeAddedSets() {
        val original=draft()
        assertTrue(reviewedStrengthVolumeSlots(
            original,original.copy(revision=original.revision+1),"strength")
            .isEmpty())
    }

    @Test fun previewResultCannotIssueOrAutomaticallyEdit() {
        val original=draft()
        val preview=NativeStrengthVolumeRepairReady(
            request=NativeStrengthVolumeRepairRequest(
                original.cycleId,"strength",1,1,
                CustomCoachAuditInputs(),5),
            expectedPermissionRevision=3,
            expectedCoachSettingsRevision=2,
            proposedCycle=original.copy(revision=2),
            additionsPerWeek=0,weeksCovered=0,changedSlots=emptyList())
        assertFalse(preview.mayApplyAutomatically)
        assertFalse(preview.mayIssueWorkout)
        assertFalse(preview.mayAlterHistory)
    }
}
