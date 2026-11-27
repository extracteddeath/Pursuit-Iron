package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MidSessionWorkingEditDomainTest {
    private val load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("30"))
    private val source=KotlinTrainingSourceRevision("athlete",1,"program",1,"gym",1,"catalog","rules")
    private fun generated():KotlinWorkoutJournalState {
        val targets=(0..3).map{i->KotlinWorkoutTarget("curl",ComposerRole.ACCESSORY,
            ComposerSessionPurpose.HYPERTROPHY,"curl-$i",if(i==0)DraftSetType.WARMUP else DraftSetType.WORKING,
            i,load,RepBand(8,12),2,60,120)}
        val snapshot=KotlinStagedWorkout(source,"grant","workout",0,0,ComposerSessionPurpose.HYPERTROPHY,12,targets)
        val initial=KotlinWorkoutJournalDomain.begin(snapshot,KotlinStoredWorkoutIdentity("receipt","workout",source,1))
        val actual=KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,10,1)
        return (KotlinWorkoutJournalDomain.reduce(initial,KotlinJournalOperation.RecordSet(
            "record",0,"curl-1",KotlinJournalSetOutcome.COMPLETED,actual))
            as KotlinJournalDecision.Proposed).state
    }
    private fun custom(group:String?=null,pinned:Boolean=false,amp:Boolean=false):CustomWorkoutJournalDraft {
        val sets=(0..3).map{i->CustomWorkoutSetSnapshot("curl-$i","slot",i+1,CustomAuthoredSet(
            if(i==0)DraftSetType.WARMUP else DraftSetType.WORKING,load,RepBand(8,12),2,60,120,
            if(amp && i==3)CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20) else null))}
        val slot=CustomWorkoutSlotSnapshot("slot","curl","curl","standard",ComposerRole.ACCESSORY,
            null,group,pinned,"",sets)
        val snap=CustomWorkoutDaySnapshot(CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"block",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            0,"Day",ComposerSessionPurpose.HYPERTROPHY,listOf(slot),720,100)
        val initial=CustomWorkoutJournalDomain.begin("workout",snap,"a".repeat(64))
        val actual=KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,10,1)
        return (CustomWorkoutJournalDomain.reduce(initial,CustomWorkoutJournalOperation.Record(
            "record",0,CustomWorkoutVisitKey("curl-1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual)) as CustomWorkoutJournalDecision.Proposed).draft
    }
    @Test fun sharedPreviewPreservesWarmupsAndCompletedWorkAndChangesOnlyPendingRows() {
        val g=generated()
        val c=custom()
        val request=MidSessionWorkingEdit("workout",1,"curl",2,RepBand(10,15),150)
        val gr=MidSessionWorkingEditDomain.generated(g,request) as MidSessionWorkingEditReview.Reviewed
        val cr=MidSessionWorkingEditDomain.custom(c,request.copy(occurrenceId="slot")) as MidSessionWorkingEditReview.Reviewed
        assertEquals(listOf("curl-3"),gr.omittedSetKeys)
        assertEquals(gr.omittedSetKeys,cr.omittedSetKeys)
        assertEquals(listOf("curl-1"),gr.resolvedSetKeys)
        assertEquals(gr.resolvedSetKeys,cr.resolvedSetKeys)
        assertEquals(g.snapshot.targets.take(2),gr.after.take(2))
        assertEquals(c.snapshot.slots.single().sets.take(2),cr.after.take(2))
        assertEquals(RepBand(10,15),gr.after.last().reps)
        assertEquals(150,cr.after.last().prescription.restSeconds)
        assertEquals(1L,g.revision);assertEquals(1L,c.revision)
        assertEquals(4,g.snapshot.targets.size);assertEquals(4,c.snapshot.slots.single().sets.size)
        assertFalse(gr.mayAppendJournal);assertFalse(cr.maySaveProgram)
    }
    @Test fun addedRowsReuseExactPhysicalPrescriptionButHaveNoActuals() {
        val g=generated()
        val request=MidSessionWorkingEdit("workout",1,"curl",4,restSeconds=180)
        val gr=MidSessionWorkingEditDomain.generated(g,request) as MidSessionWorkingEditReview.Reviewed
        assertEquals(listOf("mid-2-1"),gr.addedSetKeys)
        assertEquals(load,gr.after.last().load)
        assertEquals(4,gr.after.last().ordinal)
        assertEquals(180,gr.after.last().restSeconds)
        assertFalse(g.effectiveSets.containsKey(gr.addedSetKeys.single()))
        val c=custom()
        val cr=MidSessionWorkingEditDomain.custom(c,request.copy(occurrenceId="slot")) as MidSessionWorkingEditReview.Reviewed
        assertEquals(gr.addedSetKeys,cr.addedSetKeys)
        assertEquals(load,cr.after.last().prescription.load)
        assertEquals(5,cr.after.last().ordinalInSlot)
        assertEquals(180,cr.after.last().prescription.restSeconds)
    }
    @Test fun customSessionProgressionChoicesReuseExactSavedMethodPolicyWithoutWriting() {
        val journal=custom()
        val original=journal.originalSnapshot
        val slot=original.slots.single()
        val authored=CustomExerciseSlot(slot.slotId,slot.exerciseId,slot.role,
            slot.sets.map{it.prescription},attachmentId=slot.attachmentId,
            pinned=slot.pinned,supersetGroup=slot.supersetGroup)
        val day=CustomTrainingDay(original.weekday,original.dayLabel,
            original.sessionPurpose,listOf(authored))
        val block=CustomTrainingBlock(original.blockId,"Base",original.phase,
            original.blockPurpose,4,original.split,listOf(day))
        val cycle=CustomTrainingCycle(original.stamp.cycleId,original.stamp.cycleRevision,
            original.stamp.athleteId,AthleteTrainingGoal.POWERBUILDING,"Cycle",listOf(block))
        fun choices(state:CustomWorkoutJournalDraft=journal,source:CustomTrainingCycle=cycle,
            revision:Long=journal.revision,workout:String=journal.workoutId,id:String=slot.slotId)=
            MidSessionWorkingEditDomain.availableCustomProgressionMethods(state,source,workout,revision,id)
        val expected=CustomCycleBuilderDomain.availableProgressionMethods(
            cycle,ProgramProgressionSlotKey(block.blockId,day.weekday,slot.slotId))
        assertTrue(expected.isNotEmpty())
        assertEquals(expected,choices())
        assertTrue(SavedProgressionMethod.DOUBLE in expected)
        assertFalse(SavedProgressionMethod.BODYWEIGHT_REPS in expected)
        assertTrue(choices(revision=0).isEmpty())
        assertTrue(choices(workout="foreign").isEmpty())
        assertTrue(choices(id="foreign").isEmpty())
        for(other in listOf(cycle.copy(cycleId="foreign"),cycle.copy(athleteId="foreign"),
            cycle.copy(revision=cycle.revision+1),
            cycle.copy(blocks=listOf(block.copy(days=listOf(day.copy(
                slots=listOf(authored.copy(exerciseId="foreign"))))))))) {
            assertTrue(choices(source=other).isEmpty())
        }
        assertTrue(choices(state=custom(amp=true)).isEmpty())
        assertTrue(choices(state=custom(group="pair")).isEmpty())
        assertNull(cycle.blocks.single().days.single().slots.single().progressionMethod)
        assertEquals(original,journal.originalSnapshot)
        assertEquals(1L,journal.revision)
    }

    @Test fun refusalBoundariesPreserveProtectedAndResolvedWork() {
        val g=generated()
        val request=MidSessionWorkingEdit("workout",1,"curl",2,restSeconds=150)
        fun reject(reason:MidSessionWorkingEditRefusal)=MidSessionWorkingEditReview.Rejected(reason)
        assertEquals(reject(MidSessionWorkingEditRefusal.STALE_WORKOUT),
            MidSessionWorkingEditDomain.generated(g,request.copy(expectedRevision=0)))
        assertEquals(reject(MidSessionWorkingEditRefusal.INVALID_REQUEST),
            MidSessionWorkingEditDomain.generated(g,request.copy(restSeconds=3601)))
        val resolvedLater=(KotlinWorkoutJournalDomain.reduce(g,KotlinJournalOperation.RecordSet(
            "skip",1,"curl-3",KotlinJournalSetOutcome.SKIPPED,null)) as KotlinJournalDecision.Proposed).state
        assertEquals(reject(MidSessionWorkingEditRefusal.RESOLVED_WORK),
            MidSessionWorkingEditDomain.generated(resolvedLater,request.copy(expectedRevision=2)))
        for(c in listOf(custom(group="pair"),custom(pinned=true),custom(amp=true))) {
            assertEquals(reject(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL),
                MidSessionWorkingEditDomain.custom(c,request.copy(occurrenceId="slot")))
        }
    }
}
