package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class GeneratedUpstreamNumberTraceDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    @Test fun everyGeneratedWorkingSetExplainsItsExerciseSpecificNumbers() {
        val request=fixture.request()
        val first=GeneratedAdaptiveBlockDomain.propose(request)
        assertEquals(first.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,first.status)
        val trace=checkNotNull(first.upstreamNumberTrace)
        val candidate=checkNotNull(first.proposedOption).proposedCycle
        assertEquals(1,trace.schemaVersion)
        assertEquals(request.currentCycle.revision,trace.sourceCycleRevision)
        assertEquals(candidate.revision,trace.candidateCycleRevision)
        assertEquals(64,trace.exactRequestDigest.length)
        assertEquals(GeneratedHistoryEvidenceState.NOT_CONSUMED,
            trace.historyEvidence)
        val dosage=trace.plannedWeekDose
        assertEquals(TrainingPhase.HYPERTROPHY,dosage.phase)
        assertEquals(checkNotNull(first.prescribedWeek?.finalAudit).muscles,
            dosage.muscles)
        assertEquals(checkNotNull(first.prescribedWeek?.finalAudit).sessions,
            dosage.sessions)
        assertEquals(3,dosage.sessions.size)
        assertEquals(checkNotNull(request.program.policy).selectedBounds
            .map{it.muscle}.toSet(),dosage.muscles.keys)
        assertTrue(dosage.muscles.values.all{
            it.effectiveWorkingSets==it.directWorkingSets+
                it.indirectWorkingSets
        })
        assertTrue(trace.sets.isNotEmpty())
        assertEquals(candidate.blocks.single().days.sumOf{day->
            day.slots.sumOf{it.sets.size}
        },trace.sets.size)
        assertTrue(trace.sets.all{
            it.origin==GeneratedSetNumberOrigin.EXERCISE_PHASE_POLICY &&
            it.reps==RepBand(8,12) &&
            it.targetRir==2 &&
            it.load is DraftSetLoad.Verified &&
            (it.load as DraftSetLoad.Verified).value==fixture.n("25")
        })
        assertEquals(trace,GeneratedAdaptiveBlockDomain.propose(request)
            .upstreamNumberTrace)
        assertThrows(UnsupportedOperationException::class.java){
            (trace.sets as MutableList<GeneratedSetNumberLineage>).clear()
        }
        assertFalse(trace.maySaveProgram)
        assertFalse(trace.mayIssueWorkout)
    }

    @Test fun pinnedSetRetainsItsSeparateAuthoredRestAndOrigin() {
        val source=fixture.cycle()
        val block=source.blocks.single()
        val day=block.days.first()
        val pinned=day.slots.first().copy(
            pinned=true,
            sets=listOf(day.slots.first().sets.single().copy(
                restSeconds=140)),
            coachNote="Athlete-authored rest")
        val edited=source.copy(blocks=listOf(block.copy(
            days=listOf(day.copy(slots=listOf(pinned)+
                day.slots.drop(1)))+block.days.drop(1))))
        val result=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=edited))
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        val traced=checkNotNull(result.upstreamNumberTrace).sets.filter{
            it.weekday==day.weekday && it.slotId==pinned.slotId
        }
        assertEquals(pinned.sets.size,traced.size)
        assertEquals(140,traced.single().restSeconds)
        assertNull(traced.single().policyDayIndex)
        assertEquals(GeneratedSetNumberOrigin.PROTECTED_ATHLETE_PRESCRIPTION,
            traced.single().origin)
    }

    @Test fun auditRefusesAnUnexplainedRepOrSetChangeInReviewedFutureCandidate() {
        val request=fixture.request()
        val valid=GeneratedAdaptiveBlockDomain.propose(request)
        assertEquals(GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            valid.status)
        val candidate=checkNotNull(valid.proposedOption).proposedCycle
        val block=candidate.blocks.single()
        val day=block.days.first()
        val slot=day.slots.first()
        val silentlyChanged=slot.copy(sets=slot.sets.mapIndexed{index,set->
            if(index==0)set.copy(reps=RepBand(4,5)) else set
        })
        val corrupted=candidate.copy(blocks=listOf(block.copy(
            days=listOf(day.copy(
                slots=listOf(silentlyChanged)+day.slots.drop(1)))+
                block.days.drop(1))))
        val availability=CustomExerciseAuthoringDomain.picker(
            fixture.athlete(),fixture.movements).map{it.availability}
        val reviewed=CustomTrainingCycleDomain.review(corrupted,availability)
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            reviewed.status)
        val result=GeneratedUpstreamNumberTraceDomain.review(
            request,reviewed,checkNotNull(valid.generatedWeek),
            checkNotNull(valid.prescribedWeek),valid.slotContinuity,
            valid.restoredSourceExercises,valid.addedStrengthWorkingSets)
        assertNull("A valid physical load is not enough: the reps must come "+
            "from the exact exercise/day/phase author",result)
        val silentlyAdded=slot.copy(sets=slot.sets+slot.sets.last())
        val extraCandidate=candidate.copy(blocks=listOf(block.copy(
            days=listOf(day.copy(
                slots=listOf(silentlyAdded)+day.slots.drop(1)))+
                block.days.drop(1))))
        val extraReviewed=CustomTrainingCycleDomain.review(
            extraCandidate,availability)
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            extraReviewed.status)
        assertNull(GeneratedUpstreamNumberTraceDomain.review(
            request,extraReviewed,checkNotNull(valid.generatedWeek),
            checkNotNull(valid.prescribedWeek),valid.slotContinuity,
            valid.restoredSourceExercises,valid.addedStrengthWorkingSets))
    }

    @Test fun staleProgramOrPolicyCannotBorrowAnotherRequestsNumericalTrace() {
        val original=fixture.request()
        val issued=GeneratedAdaptiveBlockDomain.propose(original)
        assertEquals(GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            issued.status)
        val candidate=checkNotNull(issued.proposedOption).proposedCycle
        val reviewed=CustomTrainingCycleDomain.review(candidate,
            CustomExerciseAuthoringDomain.picker(
                fixture.athlete(),fixture.movements).map{it.availability})
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            reviewed.status)
        fun trace(request:GeneratedAdaptiveBlockRequest)=
            GeneratedUpstreamNumberTraceDomain.review(request,reviewed,
                checkNotNull(issued.generatedWeek),
                checkNotNull(issued.prescribedWeek),
                issued.slotContinuity,issued.restoredSourceExercises,
                issued.addedStrengthWorkingSets)
        assertEquals(issued.upstreamNumberTrace,trace(original))

        // The rep-band author changed, but the submitted generated week
        // still describes the old policy. Merely recomputing a new digest
        // for the stale result would be false numerical provenance.
        val newReps=original.copy(prescriptions=
            original.prescriptions.map {p->
                if(p.exerciseId=="bench")p.copy(workingReps=RepBand(6,10))
                else p
            })
        assertNull("Stale exercise-specific rep prescriptions must fail",
            trace(newReps))
        val newRir=original.copy(prescriptions=
            original.prescriptions.map {p->
                if(p.exerciseId=="bench")p.copy(targetRir=1)
                else p
            })
        assertNull("Stale target RIR must fail",trace(newRir))
        val newRest=original.copy(prescriptions=
            original.prescriptions.map {p->
                if(p.exerciseId=="bench")p.copy(restSecondsPerSet=75)
                else p
            })
        assertNull("Stale authored rest/duration must fail",trace(newRest))

        // Physical stock and athlete-selected working loads belong to
        // program construction, not merely the post-hoc set label.
        val changedChoice=original.copy(program=original.program.copy(
            choices=original.program.choices.map {choice->
                if(choice.exerciseId=="bench")
                    choice.copy(selectedLoad=fixture.n("30"))
                else choice
            }))
        assertNull("A new load choice cannot reuse the old generation",
            trace(changedChoice))
    }

    @Test fun sourceRevisionChangesCannotPassAnOldNumericalTrace() {
        val request=fixture.request()
        val outcome=GeneratedAdaptiveBlockDomain.propose(request)
        val reviewed=CustomTrainingCycleDomain.review(
            checkNotNull(outcome.proposedOption).proposedCycle,
            CustomExerciseAuthoringDomain.picker(
                fixture.athlete(),fixture.movements)
                .map{it.availability})
        val replay=GeneratedUpstreamNumberTraceDomain.review(
            request,reviewed,checkNotNull(outcome.generatedWeek),
            checkNotNull(outcome.prescribedWeek),outcome.slotContinuity,
            outcome.restoredSourceExercises,outcome.addedStrengthWorkingSets)
        assertEquals(outcome.upstreamNumberTrace,replay)
        val stale=request.copy(currentCycle=request.currentCycle.copy(
            revision=request.currentCycle.revision+1))
        assertNull(GeneratedUpstreamNumberTraceDomain.review(
            stale,reviewed,checkNotNull(outcome.generatedWeek),
            checkNotNull(outcome.prescribedWeek),outcome.slotContinuity,
            outcome.restoredSourceExercises,outcome.addedStrengthWorkingSets))
    }

    @Test fun realGeneratedProgramMayInspectFinishedReceiptsButNeverClaimStorageAttestation() {
        val ordinary=fixture.request()
        val initial=GeneratedAdaptiveBlockDomain.propose(ordinary)
        assertEquals(GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            initial.status)
        val issuedSource=KotlinTrainingSourceRevision(
            "athlete-1",4,"cycle-1",4,"gym-1",3,"catalog-5","rules-1")
        val target=KotlinWorkoutTarget(
            "bench",ComposerRole.PRIMARY,ComposerSessionPurpose.HYPERTROPHY,
            "bench:s0",DraftSetType.WORKING,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                fixture.n("25")),RepBand(8,12),2,60,120)
        val snapshot=KotlinStagedWorkout(issuedSource,"grant-1",
            "actual-workout-1",0,0,
            ComposerSessionPurpose.HYPERTROPHY,45,listOf(target))
        var journal=KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("stored-1",snapshot.workoutId,
                issuedSource,1))
        journal=(KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.RecordSet("actual-1",0,"bench:s0",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(fixture.n("25"),LoadUnit.LB,9,null,
                    "machine-A"))
        ) as KotlinJournalDecision.Proposed).state
        journal=(KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.Finish("finished-1",journal.revision))
            as KotlinJournalDecision.Proposed).state
        val historical=KotlinProgramHistoryReadoutRequest(
            "athlete-1","cycle-1",issuedSource,setOf(0,2,4),
            listOf(journal))
        val withHistory=GeneratedAdaptiveBlockDomain.propose(
            ordinary.copy(historyReadoutRequest=historical))
        assertEquals(withHistory.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            withHistory.status)
        assertEquals(initial.proposedOption,withHistory.proposedOption)
        assertEquals(initial.prescribedWeek,withHistory.prescribedWeek)
        val observed=checkNotNull(withHistory.upstreamNumberTrace)
        assertEquals(GeneratedHistoryEvidenceState.REPLAY_VERIFIED_NOT_STORE_ATTESTED,
            observed.historyEvidence)
        assertEquals(1,checkNotNull(observed.replayedHistory).workingSets.size)
        assertNull(checkNotNull(observed.replayedHistory)
            .workingSets.single().reportedRir)
        assertEquals(initial.upstreamNumberTrace?.sets,observed.sets)
        assertNotEquals(initial.upstreamNumberTrace?.exactRequestDigest,
            observed.exactRequestDigest)
        assertFalse(observed.replayedHistory!!.canAuthorizeProgression)
        assertEquals(withHistory.upstreamNumberTrace,
            GeneratedAdaptiveBlockDomain.propose(
                ordinary.copy(historyReadoutRequest=historical))
                .upstreamNumberTrace)

        val mismatched=GeneratedAdaptiveBlockDomain.propose(
            ordinary.copy(historyReadoutRequest=historical.copy(
                cycleId="unrelated-cycle")))
        assertEquals(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
            mismatched.status)
        assertNull(mismatched.proposedOption)
        val wrongGym=GeneratedAdaptiveBlockDomain.propose(
            ordinary.copy(historyReadoutRequest=historical.copy(
                expectedSource=issuedSource.copy(gymId="another-gym"))))
        assertEquals(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
            wrongGym.status)
        assertNull(wrongGym.proposedOption)
        val future=GeneratedAdaptiveBlockDomain.propose(
            ordinary.copy(historyReadoutRequest=historical.copy(
                expectedSource=issuedSource.copy(programRevision=99L))))
        assertEquals(GeneratedAdaptiveBlockStatus.CYCLE_UNRESOLVED,
            future.status)
        assertNull(future.proposedOption)
    }
}
