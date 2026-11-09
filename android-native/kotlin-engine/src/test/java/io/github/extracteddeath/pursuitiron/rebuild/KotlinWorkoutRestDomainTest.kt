package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutRestDomainTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete",1,"program",1,"gym",1,"catalog","rules")
    private fun target(key:String,exercise:String="press",rest:Int=120,
        type:DraftSetType=DraftSetType.WORKING)=KotlinWorkoutTarget(
        exercise,ComposerRole.PRIMARY,ComposerSessionPurpose.HYPERTROPHY,
        key,type,1,DraftSetLoad.ExplicitBodyweight,RepBand(8,12),2,45,rest)
    private fun composer(targets:List<KotlinWorkoutTarget> =listOf(
        target("warmup",rest=45,type=DraftSetType.WARMUP),
        target("work"),target("row",exercise="row",rest=0)))=
        KotlinWorkoutJournalDomain.begin(KotlinStagedWorkout(
            source,"grant","workout",0,0,ComposerSessionPurpose.HYPERTROPHY,
            45,targets),KotlinStoredWorkoutIdentity("stored","workout",source,1))
    private fun record(j:KotlinWorkoutJournalState,key:String,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        correction:String?=null):KotlinWorkoutJournalState {
        val operation=KotlinJournalOperation.RecordSet("event-${j.revision}",
            j.revision,key,outcome,actual(outcome),correction)
        return (KotlinWorkoutJournalDomain.reduce(j,operation)
            as KotlinJournalDecision.Proposed).state
    }
    private fun actual(outcome:KotlinJournalSetOutcome)=when(outcome) {
        KotlinJournalSetOutcome.COMPLETED->KotlinPerformedSet(null,null,8,null)
        KotlinJournalSetOutcome.FAILED_ATTEMPT->KotlinPerformedSet(null,null,0,null)
        KotlinJournalSetOutcome.SKIPPED->null
    }
    private fun custom(amp:CustomSetAmplifier?=null,rest:Int=90,
        superset:String?=null,second:Boolean=true):CustomWorkoutJournalDraft {
        fun slot(id:String,amplifier:CustomSetAmplifier?,group:String?)=
            CustomWorkoutSlotSnapshot(id,"same-exercise","family","variant",
                ComposerRole.ACCESSORY,null,group,true,"",listOf(
                    CustomWorkoutSetSnapshot("$id:s1",id,1,CustomAuthoredSet(
                        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
                        RepBand(8,12),2,45,rest,amplifier))))
        val snapshot=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"base",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("Custom"),0,"Upper A",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot("a",amp,superset))+if(second)
                listOf(slot("b",null,superset)) else emptyList(),200,null)
        return CustomWorkoutJournalDomain.begin("custom",snapshot,"a".repeat(64))
    }
    private fun visit(key:String,part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN)=
        CustomWorkoutVisitKey(key,part)
    private fun record(j:CustomWorkoutJournalDraft,key:String="a:s1",
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        correction:String?=null):CustomWorkoutJournalDraft {
        val operation=CustomWorkoutJournalOperation.Record("event-${j.revision}",
            j.revision,visit(key,part),outcome,actual(outcome),correction)
        return (CustomWorkoutJournalDomain.reduce(j,operation)
            as CustomWorkoutJournalDecision.Proposed).draft
    }
    private fun rest(j:CustomWorkoutJournalDraft)=KotlinWorkoutRestDomain.inspect(
        j,CustomWorkoutJournalDomain.nextUnresolvedVisit(j))

    @Test fun generatedNoteEditDoesNotResetPriorCompletedSetRest() {
        val recorded=record(composer(),"warmup")
        val before=KotlinWorkoutRestDomain.inspect(recorded,"work")
        val note=KotlinJournalOperation.SessionNote(
            "rest-note",recorded.revision,"Warm-up complete")
        val updated=(KotlinWorkoutJournalDomain.reduce(recorded,note)
            as KotlinJournalDecision.Proposed).state
        val after=KotlinWorkoutRestDomain.inspect(updated,"work")
        assertEquals(before.latestReceiptEventId,after.latestReceiptEventId)
        assertEquals(before.automaticCue,after.automaticCue)
        assertEquals(before.manualRestSeconds,after.manualRestSeconds)
        assertEquals(recorded.resolvedSetCount,updated.resolvedSetCount)
        assertEquals("Warm-up complete",updated.sessionNote)
        val corrected=record(updated,"warmup",correction="event-0")
        assertNull(KotlinWorkoutRestDomain.inspect(corrected,"work").automaticCue)
    }

    @Test fun customNoteEditDoesNotResetMainSetOrAmplifierRest() {
        val planned=custom(
            amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20))
        val logged=record(planned)
        val before=rest(logged)
        assertEquals(20,before.automaticCue?.seconds)
        val note=CustomWorkoutJournalOperation.SessionNote(
            "custom-rest-note",logged.revision,"Remember setup")
        val updated=(CustomWorkoutJournalDomain.reduce(logged,note)
            as CustomWorkoutJournalDecision.Proposed).draft
        val after=rest(updated)
        assertEquals(before.latestReceiptEventId,after.latestReceiptEventId)
        assertEquals(before.automaticCue,after.automaticCue)
        assertEquals("Remember setup",updated.sessionNote)
        assertEquals(logged.effectiveVisits,updated.effectiveVisits)
    }

    @Test fun freshJournalShowsOnlySelectedIssuedManualDuration() {
        val j=composer()
        val warm=KotlinWorkoutRestDomain.inspect(j,"warmup")
        assertEquals(45,warm.manualRestSeconds)
        assertTrue(warm.hasRemainingWork)
        assertNull(warm.automaticCue)
        assertNull(warm.latestReceiptEventId)
        assertEquals(120,KotlinWorkoutRestDomain.inspect(j,"work").manualRestSeconds)
        assertEquals(0,KotlinWorkoutRestDomain.inspect(j,"unknown").manualRestSeconds)
        assertEquals(0,KotlinWorkoutRestDomain.inspect(j,"row").manualRestSeconds)
    }

    @Test fun savedWarmupAndExerciseBoundaryKeepTheCompletedTargetsRest() {
        var j=record(composer(),"warmup")
        var ui=KotlinWorkoutRestDomain.inspect(j,"work")
        assertEquals(KotlinWorkoutRestCue("event-0",45,0),ui.automaticCue)
        assertEquals(120,ui.manualRestSeconds)
        j=record(j,"work")
        ui=KotlinWorkoutRestDomain.inspect(j,"row")
        assertEquals(KotlinWorkoutRestCue("event-1",120,0),ui.automaticCue)
        assertEquals(0,ui.manualRestSeconds) // no invented next-exercise rest
        assertEquals(j,KotlinWorkoutJournalDomain.reduce(j,j.receipts.last().operation)
            .let{(it as KotlinJournalDecision.Proposed).state})
        assertEquals(ui,KotlinWorkoutRestDomain.inspect(j,"row"))
        assertNull((j.receipts.last().operation as KotlinJournalOperation.RecordSet)
            .performed?.actualRir)
    }

    @Test fun skippedFailedAndCorrectedComposerReceiptsDoNotStartRest() {
        for(outcome in listOf(KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            val ui=KotlinWorkoutRestDomain.inspect(record(composer(),"work",outcome),"row")
            assertEquals("event-0",ui.latestReceiptEventId)
            assertNull(ui.automaticCue)
        }
        var j=record(composer(),"work")
        j=record(j,"work",correction="event-0")
        val ui=KotlinWorkoutRestDomain.inspect(j,"warmup")
        assertEquals("event-1",ui.latestReceiptEventId)
        assertNull(ui.automaticCue)
    }

    @Test fun lastComposerSetAndFinishHaveNoPostWorkoutCountdown() {
        val j=record(composer(listOf(target("only"))),"only")
        assertFalse(KotlinWorkoutRestDomain.inspect(j,"only").hasRemainingWork)
        assertNull(KotlinWorkoutRestDomain.inspect(j,"only").automaticCue)
        assertEquals(0,KotlinWorkoutRestDomain.inspect(j,"only").manualRestSeconds)
        val done=(KotlinWorkoutJournalDomain.reduce(j,
            KotlinJournalOperation.Finish("finish",j.revision))
            as KotlinJournalDecision.Proposed).state
        assertEquals(KotlinWorkoutRestPresentation("finish",0,null,false),
            KotlinWorkoutRestDomain.inspect(done,"only"))
    }

    @Test fun amplifierUsesShortPauseBeforeFollowupAndOrdinaryRestAfterIt() {
        for(kind in CustomAmplifierKind.entries) {
            var j=custom(CustomSetAmplifier(kind,4,20))
            assertEquals(visit("a:s1"),CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertEquals(90,rest(j).manualRestSeconds)
            j=record(j)
            assertEquals(visit("a:s1",CustomWorkoutVisitPart.FOLLOW_UP),
                CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertEquals(20,rest(j).manualRestSeconds)
            assertEquals(KotlinWorkoutRestCue("event-0",20,0),rest(j).automaticCue)
            j=record(j,part=CustomWorkoutVisitPart.FOLLOW_UP)
            assertEquals(visit("b:s1"),CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertEquals(KotlinWorkoutRestCue("event-1",90,0),rest(j).automaticCue)
        }
    }

    @Test fun immediateDropHasNoInventedPauseAndLastFollowupHasNoFinalRest() {
        var j=record(custom(CustomSetAmplifier(CustomAmplifierKind.DROP_SET,4,0),
            second=false))
        assertTrue(rest(j).hasRemainingWork)
        assertEquals(0,rest(j).manualRestSeconds)
        assertEquals(KotlinWorkoutRestCue("event-0",0,0),rest(j).automaticCue)
        j=record(j,part=CustomWorkoutVisitPart.FOLLOW_UP)
        assertFalse(rest(j).hasRemainingWork)
        assertNull(rest(j).automaticCue)
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
        val done=(CustomWorkoutJournalDomain.reduce(j,
            CustomWorkoutJournalOperation.Finish("finish",j.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        assertEquals(KotlinWorkoutRestPresentation("finish",0,null,false),rest(done))
    }

    @Test fun customCorrectionSkipAndFailureDoNotBecomeWorkOrStartRest() {
        for(outcome in listOf(KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            val j=record(custom(CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20)),
                outcome=outcome)
            assertEquals(visit("b:s1"),CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertNull(rest(j).automaticCue)
            assertEquals("event-0",rest(j).latestReceiptEventId)
        }
        var j=record(custom())
        j=record(j,correction="event-0")
        assertNull(rest(j).automaticCue)
        assertEquals("event-1",rest(j).latestReceiptEventId)
        assertEquals(j,(CustomWorkoutJournalDomain.reduce(j,j.receipts.last().operation)
            as CustomWorkoutJournalDecision.Proposed).draft)
    }

    @Test fun supersetVisitDoesNotPretendThatAnEntireRoundIsComplete() {
        var j=custom(CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,4,15),
            superset="pair")
        assertTrue(rest(j).manualIsSuperset)
        assertEquals(90,rest(j).manualRestSeconds)
        j=record(j)
        assertNull(rest(j).automaticCue)
        assertEquals(15,rest(j).manualRestSeconds)
        j=record(j,part=CustomWorkoutVisitPart.FOLLOW_UP)
        assertNull(rest(j).automaticCue)
        assertEquals(90,rest(j).manualRestSeconds)
    }

    @Test fun duplicateExerciseSlotsKeepTheirOwnDurationAndIndex() {
        var j=custom()
        val extra=j.snapshot.slots.last().let {slot->slot.copy(sets=slot.sets.map {
            it.copy(prescription=it.prescription.copy(restSeconds=150))
        })}
        j=CustomWorkoutJournalDomain.begin("custom",j.snapshot.copy(slots=
            listOf(j.snapshot.slots.first(),extra,extra.copy(slotId="c",sets=extra.sets.map {
                it.copy(setKey="c:s1",slotId="c")
            }))),j.preparedConsentFingerprint)
        // Log the second occurrence first: same movement, independent slot.
        j=record(j,"b:s1")
        assertEquals(KotlinWorkoutRestCue("event-0",150,1),rest(j).automaticCue)
        assertEquals(visit("a:s1"),CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
    }

    @Test fun absentOrOutOfRangeTargetsNeverAcquireAFallbackRest() {
        assertEquals(0,KotlinWorkoutRestDomain.inspect(
            composer(listOf(target("bad",rest=3601),target("other"))),"bad")
            .manualRestSeconds)
        assertNull(KotlinWorkoutRestDomain.inspect(record(
            composer(listOf(target("bad",rest=3601),target("other"))),"bad"),"other")
            .automaticCue)
        val j=custom(rest=3601)
        assertEquals(0,rest(j).manualRestSeconds)
        assertNull(rest(record(j)).automaticCue)
        assertEquals(0,KotlinWorkoutRestDomain.inspect(j,visit("unknown")).manualRestSeconds)
        assertEquals(0,KotlinWorkoutRestDomain.inspect(j,visit("a:s1",
            CustomWorkoutVisitPart.FOLLOW_UP)).manualRestSeconds)
    }
}
