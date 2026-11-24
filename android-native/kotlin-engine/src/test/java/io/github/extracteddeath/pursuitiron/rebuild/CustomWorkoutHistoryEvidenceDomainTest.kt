package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomWorkoutHistoryEvidenceDomainTest {
    private val mainKey="cycle-1:w1:d0:slot-1:s1"
    private val main=CustomWorkoutVisitKey(mainKey,CustomWorkoutVisitPart.MAIN)
    private val follow=CustomWorkoutVisitKey(mainKey,CustomWorkoutVisitPart.FOLLOW_UP)
    private fun draft():CustomWorkoutJournalDraft {
        val work=CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("20")),RepBand(10,15),2,60,90,
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,6,15,
                DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("15"))))
        val snapshot=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","revision-1",
                "cycle-1",2,"gym-1",5),
            1,1,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("My custom"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot(
                "slot-1","db_lateral_raise","lateral_raise",
                "dumbbell",ComposerRole.ACCESSORY,
                null,null,true,"Strict",
                listOf(CustomWorkoutSetSnapshot(
                    mainKey,"slot-1",1,work)))),
            165,180)
        return CustomWorkoutJournalDomain.begin(
            "workout-1",snapshot,"a".repeat(64))
    }
    private fun accept(
        state:CustomWorkoutJournalDraft,
        op:CustomWorkoutJournalOperation
    )=(CustomWorkoutJournalDomain.reduce(state,op)
        as CustomWorkoutJournalDecision.Proposed).draft
    private fun record(
        id:String,rev:Long,visit:CustomWorkoutVisitKey,
        outcome:KotlinJournalSetOutcome,
        actual:KotlinPerformedSet?
    )=CustomWorkoutJournalOperation.Record(
        id,rev,visit,outcome,actual)

    @Test fun exactMainAndDropFactsRetainSeparateLoadsAndActualRir() {
        val first=accept(draft(),record("main-1",0,main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,13,null)))
        val second=accept(first,record("drop-1",1,follow,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,6,0)))
        val finished=accept(second,CustomWorkoutJournalOperation.Finish(
            "finish-1",2))
        val result=CustomWorkoutHistoryEvidenceDomain.project(finished)
        assertTrue(result.toString(),result is CustomHistoryEvidenceResult.Candidate)
        val facts=(result as CustomHistoryEvidenceResult.Candidate).evidence
        assertEquals(2,facts.visits.size)
        assertEquals(listOf(CustomWorkoutVisitPart.MAIN,
            CustomWorkoutVisitPart.FOLLOW_UP),facts.visits.map{it.part})
        assertEquals(BigDecimal("20"),
            (facts.visits[0].prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(BigDecimal("15"),
            (facts.visits[1].prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(2,facts.visits[0].prescribedRir)
        assertNull(facts.visits[0].actual?.actualRir)
        assertEquals(0,facts.visits[1].actual?.actualRir)
        assertEquals(6,facts.visits[1].actual?.reps)
        assertEquals(CustomAmplifierKind.DROP_SET,facts.visits[1].amplifierKind)
        val grouped=CustomHistoricalTechniqueGroupingDomain.group(facts.visits)!!
        assertEquals(1,grouped.size)
        assertEquals("Drop set · 1 drop · 15 lb × 6",grouped.single().techniqueCaption)
        assertEquals(facts.visits,grouped.flatMap{listOf(it.main)+it.followUps})
        assertTrue("The performed main working set is distinct from the drop",
            facts.allIssuedMainWorkingSetsCompleted)
        assertFalse(facts.canPublishGlobalHistory)
        assertFalse(facts.canApplyProgression)
    }

    @Test fun failedMainDoesNotInventFollowUpCompletedWork() {
        val failed=accept(draft(),record("failed",0,main,
            KotlinJournalSetOutcome.FAILED_ATTEMPT,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,0,null)))
        val finished=accept(failed,
            CustomWorkoutJournalOperation.Finish("finish",1))
        val result=CustomWorkoutHistoryEvidenceDomain.project(finished)
        assertTrue(result is CustomHistoryEvidenceResult.Candidate)
        val evidence=(result as CustomHistoryEvidenceResult.Candidate).evidence
        assertFalse("Finish is not full working-set completion",
            evidence.allIssuedMainWorkingSetsCompleted)
        val facts=evidence.visits
        assertEquals(1,facts.size)
        assertEquals(KotlinJournalSetOutcome.FAILED_ATTEMPT,
            facts.single().outcome)
        assertEquals(CustomWorkoutVisitPart.MAIN,facts.single().part)
    }

    @Test fun realFinishedSkippedMainSetCannotCompleteAnIssuedTrainingWeek() {
        val skipped=accept(draft(),record("skipped-main",0,main,
            KotlinJournalSetOutcome.SKIPPED,null))
        val finished=accept(skipped,
            CustomWorkoutJournalOperation.Finish("finished-skipped",1))
        val projected=CustomWorkoutHistoryEvidenceDomain.project(finished)
            as CustomHistoryEvidenceResult.Candidate
        assertEquals(KotlinJournalSetOutcome.SKIPPED,
            projected.evidence.visits.single().outcome)
        assertFalse(projected.evidence.allIssuedMainWorkingSetsCompleted)
        assertFalse(projected.evidence.copy(
            shortSessionLimitMinutes=25,
            shortSessionOmittedSetKeys=emptyList()
        ).allIssuedMainWorkingSetsCompleted)
    }

    @Test fun forgedMachineIdInColdReplayedReceiptCannotBecomeProgressionHistory() {
        val start=draft()
        val first=accept(start,record("machine-main",0,main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,12,2,"A")))
        val second=accept(first,record("machine-follow",1,follow,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,6,2,"A")))
        val finished=accept(second,CustomWorkoutJournalOperation.Finish(
            "machine-finish",2))
        val verified=CustomWorkoutHistoryEvidenceDomain.project(finished)
        assertTrue(verified is CustomHistoryEvidenceResult.Candidate)
        assertEquals(listOf("A","A"),
            (verified as CustomHistoryEvidenceResult.Candidate)
                .evidence.visits.map{it.actual?.machineInstanceId})

        // A forged stored journal cannot claim a machine identity outside
        // the canonical A/B namespace by changing a durable receipt.
        val firstReceipt=finished.receipts.first()
        val record=firstReceipt.operation as CustomWorkoutJournalOperation.Record
        val forgedReceipt=firstReceipt.copy(operation=record.copy(
            performed=record.performed?.copy(machineInstanceId="machine bad")))
        val forged=finished.copy(receipts=listOf(forgedReceipt)+
            finished.receipts.drop(1))
        assertEquals(CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL),
            CustomWorkoutHistoryEvidenceDomain.project(forged))
    }

    @Test fun uncommittedOrMutatedReceiptsCannotBecomeHistoricalEvidence() {
        val initial=draft()
        assertEquals(CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNFINISHED),
            CustomWorkoutHistoryEvidenceDomain.project(initial))
        val main=accept(initial,record("main-1",0,this.main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,12,1)))
        val after=accept(main,record("follow-1",1,follow,
            KotlinJournalSetOutcome.SKIPPED,null))
        val complete=accept(after,
            CustomWorkoutJournalOperation.Finish("finish",2))
        val withOptionalSkipped=CustomWorkoutHistoryEvidenceDomain.project(complete)
            as CustomHistoryEvidenceResult.Candidate
        assertTrue("An optional follow-up isn't another mandatory working set",
            withOptionalSkipped.evidence.allIssuedMainWorkingSetsCompleted)
        assertFalse("Short-session omissions cannot be called complete",
            withOptionalSkipped.evidence.copy(
                shortSessionLimitMinutes=20,
                shortSessionOmittedSetKeys=listOf(mainKey)
            ).allIssuedMainWorkingSetsCompleted)
        assertEquals(CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL),
            CustomWorkoutHistoryEvidenceDomain.project(
                complete.copy(effectiveVisits=emptyMap())))
    }
    @Test fun everySavedMyoStageAppearsInHistoryButNotAsAWorkingSet() {
        val base=draft()
        val workout=base.snapshot.copy(
            slots=base.snapshot.slots.map { slot -> slot.copy(sets=slot.sets.map { set ->
                set.copy(prescription=set.prescription.copy(amplifier=CustomSetAmplifier(
                    CustomAmplifierKind.MYO_REPS,5,20,
                    followUpActiveSeconds=80,followUpRounds=5)))
            }) },
            knownSetAndRestSeconds=330)
        var session=CustomWorkoutJournalDomain.begin(
            "workout-myo",workout,base.preparedConsentFingerprint)
        session=accept(session,record("myo-main",0,main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,17,1)))
        for((index,reps) in listOf(5,4,3).withIndex()) {
            session=accept(session,record("mini-${index+1}",session.revision,
                CustomWorkoutVisitKey(mainKey,CustomWorkoutVisitPart.FOLLOW_UP,index+1),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,reps,1)))
        }
        session=accept(session,CustomWorkoutJournalOperation.Finish(
            "myo-finished",session.revision))
        val candidate=CustomWorkoutHistoryEvidenceDomain.project(session)
        assertTrue(candidate.toString(),candidate is CustomHistoryEvidenceResult.Candidate)
        val evidence=(candidate as CustomHistoryEvidenceResult.Candidate).evidence
        assertTrue("Three logged mini-sets must not replace or multiply the main",
            evidence.allIssuedMainWorkingSetsCompleted)
        val facts=evidence.visits
        assertEquals(4,facts.size)
        assertEquals(listOf(1,1,2,3),facts.map{it.stage})
        assertEquals(listOf(17,5,4,3),facts.map{it.actual?.reps})
        assertEquals(listOf(BigDecimal("20"),BigDecimal("20"),
            BigDecimal("20"),BigDecimal("20")),facts.map{
                (it.prescribedLoad as DraftSetLoad.Verified).value
        })
        val groups=CustomHistoricalTechniqueGroupingDomain.group(facts)!!
        assertEquals(1,groups.size)
        assertEquals("Myo-reps · 3 mini-sets · 5 + 4 + 3 reps",
            groups.single().techniqueCaption)
        assertEquals(facts,groups.flatMap{listOf(it.main)+it.followUps})
        val score=KotlinHistoryPerformanceSummaryDomain.fromCustom(facts)
        assertEquals(1,score.completedWorkingSets)
        assertEquals(3,score.completedFollowUpVisits)
    }

    @Test fun namedRepOutRetainsIssuedIntentThroughRealCorrectionAndFinish() {
        val base=draft()
        val original=base.snapshot.slots.single()
        val originalSet=original.sets.single()
        val intent=CustomPercentageSetIntent(
            NamedPercentageScheme.GZCLP,PercentageTier.PRIMARY,
            PercentageDayRole.STANDARD,1,4,5,5,BigDecimal("20.00"),
            BigDecimal("100"),BigDecimal("100.000"),0,true,false)
        val target=originalSet.copy(prescription=originalSet.prescription.copy(
            amplifier=null,reps=RepBand(3,3),exactRepTarget=3,percentageIntent=intent))
        val slot=original.copy(role=ComposerRole.PRIMARY,sets=listOf(target))
        val snapshot=base.snapshot.copy(slots=listOf(slot),knownSetAndRestSeconds=150)
        var journal=CustomWorkoutJournalDomain.begin("named-history",snapshot,
            base.preparedConsentFingerprint)
        journal=accept(journal,record("attempt-1",0,main,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,3,0)))
        journal=accept(journal,CustomWorkoutJournalOperation.Record(
            "corrected-1",1,main,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,7,null),"attempt-1"))
        journal=accept(journal,CustomWorkoutJournalOperation.Finish("finish-named",2))
        val evidence=(CustomWorkoutHistoryEvidenceDomain.project(journal)
            as CustomHistoryEvidenceResult.Candidate).evidence
        val fact=evidence.visits.single()
        assertEquals(intent,fact.percentageIntent)
        assertEquals(RepBand(3,3),fact.prescribedReps)
        assertEquals(7,fact.actual?.reps)
        assertNull(fact.actual?.actualRir)
        assertEquals("corrected-1",fact.effectiveEventId)
        assertTrue(fact.matchesIssuedMain(slot,target))
        assertFalse(fact.copy(percentageIntent=null).matchesIssuedMain(slot,target))
        assertFalse(fact.copy(percentageIntent=intent.copy(repOut=false)).matchesIssuedMain(slot,target))
        assertEquals(intent,journal.originalSnapshot.slots.single().sets.single().prescription.percentageIntent)
        assertFalse(evidence.canApplyProgression)
        val scope=BasicProgressionScope("athlete-1","cycle-1","gym-1",
            "block-1","block-1:d0",slot.exerciseId,null,
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND)
        val projected=CustomTieredJournalEvidenceDomain.project(journal,1,scope,
            "slot-1",PercentageTier.PRIMARY) as CustomTieredJournalResult.Candidate
        assertEquals(7,projected.exposure.observations.single().actualReps)
        assertEquals(3,projected.exposure.issuedTargets.single().minimumReps)
        assertTrue(projected.exposure.issuedTargets.single().repOut)
        assertFalse(projected.mayIssueWorkout)
        assertEquals(CustomTieredJournalResult.Refused(CustomTieredJournalRefusal.INVALID_SCOPE),
            CustomTieredJournalEvidenceDomain.project(journal,1,scope.copy(gymId="other"),
                "slot-1",PercentageTier.PRIMARY))
        assertEquals(CustomTieredJournalResult.Refused(CustomTieredJournalRefusal.UNVERIFIED_JOURNAL),
            CustomTieredJournalEvidenceDomain.project(journal.copy(effectiveVisits=emptyMap()),
                1,scope,"slot-1",PercentageTier.PRIMARY))
        var changed=CustomWorkoutJournalDomain.begin("changed-load",snapshot,
            base.preparedConsentFingerprint)
        changed=accept(changed,record("changed-1",0,main,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,7,null)))
        changed=accept(changed,CustomWorkoutJournalOperation.Finish("changed-finish",1))
        assertEquals(CustomTieredJournalResult.Refused(CustomTieredJournalRefusal.INCOMPATIBLE_OBSERVATION),
            CustomTieredJournalEvidenceDomain.project(changed,2,scope,"slot-1",PercentageTier.PRIMARY))
        var skipped=CustomWorkoutJournalDomain.begin("skipped",snapshot,
            base.preparedConsentFingerprint)
        skipped=accept(skipped,record("skip-1",0,main,KotlinJournalSetOutcome.SKIPPED,null))
        skipped=accept(skipped,CustomWorkoutJournalOperation.Finish("skip-finish",1))
        assertEquals(CustomTieredJournalResult.Refused(CustomTieredJournalRefusal.INCOMPATIBLE_OBSERVATION),
            CustomTieredJournalEvidenceDomain.project(skipped,3,scope,"slot-1",PercentageTier.PRIMARY))
        val coached=CustomNextWorkoutCoachingDomain.review(CustomNextWorkoutCoachingRequest(
            snapshot,"slot-1",emptyList(),null,BigDecimal("5"),CoachReadiness.NORMAL,false))
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,coached.status)
        assertNull(coached.loadReviewCandidate)
        assertTrue(coached.nextSetSuggestions.isEmpty())
    }

}
