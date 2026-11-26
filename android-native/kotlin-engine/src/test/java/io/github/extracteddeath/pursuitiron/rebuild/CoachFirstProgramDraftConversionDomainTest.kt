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
        val plan=checkNotNull(proposed.progressionPlan)
        val savedSlots=cycle.blocks.flatMap{it.days}.flatMap{it.slots}
        assertEquals(savedSlots.size,plan.assignments.size)
        assertEquals(savedSlots.size,plan.assignments.map{it.key}.distinct().size)
        assertTrue(plan.assignments.all{it.route==ProgramProgressionRoute.BASIC_WEIGHTED})
        // Reviewed quick-change dumbbell compounds are individually
        // loadable: Auto routes these to per-set dynamic, not global DP.
        assertTrue(plan.assignments.all{
            it.basicStrategy==BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
        })
        // This independently authored fixture is intentionally NOT a new
        // first-program v12 policy, so its old cycle is byte-compatible.
        assertTrue(savedSlots.all{it.progressionMethod==null})
        val reopenedPlan=BasicProgramProgressionPlanDomain.review(
            cycle,reviewedCatalog=source.catalog.associateBy{it.exerciseId},
            experience=checkNotNull(source.setup.reviewed).experience)
            as ProgramProgressionPlan.Ready
        assertEquals(plan.assignments.map{it.basicStrategy},
            reopenedPlan.assignments.map{it.basicStrategy})
        assertFalse(proposed.mayApproveProgram)
        assertFalse(proposed.mayIssueWorkout)
        assertEquals(candidate,reviewed())
    }

    @Test fun currentPolicyPersistsChoicesWhileLegacyPolicyNeverRewritesExistingCycles() {
        val source=GeneratedAdaptiveBlockFixture.generation()
        val legacy=reviewed()
        val modern=legacy.copy(policy=checkNotNull(legacy.policy).copy(
            policyId=CoachFirstProgramStartDomain.POLICY_VERSION))
        val old=CoachFirstProgramDraftConversionDomain.propose(
            legacy,source.setup,source.catalog,"same-id","Same title",4)
        val next=CoachFirstProgramDraftConversionDomain.propose(
            modern,source.setup,source.catalog,"same-id","Same title",4)
        assertEquals(FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,old.status)
        assertEquals(FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,next.status)
        val oldCycle=checkNotNull(old.cycle)
        val nextCycle=checkNotNull(next.cycle)
        assertTrue(oldCycle.blocks.flatMap{it.days}.flatMap{it.slots}
            .all{it.progressionMethod==null})
        assertTrue(nextCycle.blocks.flatMap{it.days}.flatMap{it.slots}
            .all{it.progressionMethod==SavedProgressionMethod.DYNAMIC})
        val reopened = BasicProgramProgressionPlanDomain.review(
            nextCycle, reviewedCatalog = source.catalog.associateBy { it.exerciseId },
            experience = checkNotNull(source.setup.reviewed).experience)
        assertEquals(reopened, next.progressionPlan)
        assertTrue(checkNotNull(next.progressionPlan).assignments.all {
            it.savedMethod == SavedProgressionMethod.DYNAMIC
        })
        // Only saved methods and the separately versioned explanatory source
        // differ. No added, reduced, reshuffled, or transformed working sets.
        assertEquals(oldCycle.blocks.map{block->
            block.copy(days=block.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(progressionMethod=null)
                })
            })
        }, nextCycle.blocks.map{block->
            block.copy(days=block.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(progressionMethod=null)
                })
            })
        })
        assertEquals(legacy.policy?.revision,
            modern.policy?.revision)
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

    @Test fun optionalFinisherPreviewUsesFullCycleAuditTimeAndExactCalendarMapping() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        val inputs=checkNotNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(candidate,source.setup))
        val baseline=CoachFirstProgramDraftConversionDomain.propose(
            candidate,source.setup,source.catalog,"timing-cycle","Timing",4)
        assertTrue(baseline.auditedMinutesByDayIndex.isEmpty())
        val cycle=checkNotNull(baseline.cycle)
        val block=cycle.blocks.single()
        val day=block.days.first()
        val slot=day.slots.last()
        val index=slot.sets.indexOfLast{it.type==DraftSetType.WORKING}
        assertTrue(index>=0)
        val amp=CoachFirstProgramMyoFinisherDomain.prescribedTechnique()
        val amplified=cycle.copy(blocks=listOf(block.copy(days=block.days.map{d->
            if(d.weekday!=day.weekday)d else d.copy(slots=d.slots.map{s->
                if(s.slotId!=slot.slotId)s else s.copy(sets=s.sets.mapIndexed{i,set->
                    if(i!=index)set else set.copy(amplifier=amp)
                })
            })
        })))
        val normalAudit=CustomCycleCoachAuditDomain.review(
            cycle,source.setup,source.catalog,inputs)
        val withTechnique=CustomCycleCoachAuditDomain.review(
            amplified,source.setup,source.catalog,inputs)
        assertEquals(withTechnique.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,withTechnique.status)
        // The generated display index is not necessarily its weekday.
        val dayIndexToWeekday=checkNotNull(candidate.generation?.candidate
            ?.catalogReview?.composedRequest).days.mapIndexed{i,d->i to d.calendarDay}.toMap()
        val before=checkNotNull(CoachFirstProgramDraftConversionDomain
            .auditedFirstWeekMinutes(normalAudit,dayIndexToWeekday))
        val after=checkNotNull(CoachFirstProgramDraftConversionDomain
            .auditedFirstWeekMinutes(withTechnique,dayIndexToWeekday))
        val selected=dayIndexToWeekday.entries.single{it.value==day.weekday}.key
        val seconds=checkNotNull(withTechnique.weeks.first().estimatedSecondsByDay[day.weekday])
        val originalSeconds=checkNotNull(normalAudit.weeks.first().estimatedSecondsByDay[day.weekday])
        assertEquals(140L,seconds-originalSeconds)
        assertEquals((seconds+59L)/60L,after[selected])
        assertTrue(checkNotNull(after[selected])>checkNotNull(before[selected]))
        assertEquals(before-selected,after-selected)
        assertNull(CoachFirstProgramDraftConversionDomain.auditedFirstWeekMinutes(
            withTechnique,dayIndexToWeekday-selected))
        assertNull(CoachFirstProgramDraftConversionDomain.auditedFirstWeekMinutes(
            withTechnique.copy(status=CustomCoachAuditStatus.UNRESOLVED),dayIndexToWeekday))
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

    @Test fun oldDefaultConversionStaysStraightAndUnsafeTechniqueFailsClosed() {
        val candidate=reviewed()
        val source=GeneratedAdaptiveBlockFixture.generation()
        val old=CoachFirstProgramDraftConversionDomain.propose(candidate,
            source.setup,source.catalog,"first-safe","First program",4)
        val explicitOff=CoachFirstProgramDraftConversionDomain.propose(candidate,
            source.setup,source.catalog,"first-safe","First program",4,
            includeMyoFinisher=false)
        assertEquals(old,explicitOff)
        assertEquals(FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,old.status)
        assertTrue(checkNotNull(old.cycle).blocks.flatMap{it.days}
            .flatMap{it.slots}.flatMap{it.sets}.all{it.amplifier==null})
        // The fixture's primary lifts and unsuited accessories cannot
        // magically become staged failure-oriented myo prescriptions.
        val unsafe=CoachFirstProgramDraftConversionDomain.propose(candidate,
            source.setup,source.catalog,"first-safe","First program",4,
            includeMyoFinisher=true)
        assertEquals(FirstProgramDraftStatus.UNRESOLVED_PRESCRIPTION,
            unsafe.status)
        assertNull(unsafe.cycle)
        assertFalse(unsafe.mayApproveProgram)
        assertFalse(unsafe.mayIssueWorkout)
    }

}
