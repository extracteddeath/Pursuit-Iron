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
}
