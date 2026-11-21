package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class GeneratedStrengthDoseRecoveryDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun cycle():CustomTrainingCycle {
        val original=fixture.cycle()
        val base=original.blocks.single()
        val hyper=base.copy(blockId="hypertrophy",weeks=2,
            days=base.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=List(4){slot.sets.single()})
                })
            })
        val strength=base.copy(blockId="strength",weeks=2,
            phase=TrainingPhase.STRENGTH,
            days=base.days.map{day->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                    slots=day.slots.map{slot ->
                        slot.copy(sets=List(2){slot.sets.single().copy(
                            reps=RepBand(3,5),targetRir=1,
                            restSeconds=180)})
                    })
            })
        return original.copy(blocks=listOf(hyper,strength))
    }

    private fun review(cycle:CustomTrainingCycle=cycle()):CustomCycleReview {
        val available=CustomExerciseAuthoringDomain.picker(
            fixture.athlete(),fixture.movements)
                .map{it.availability}
        val structure=CustomTrainingCycleDomain.review(cycle,available)
        assertEquals(structure.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            structure.status)
        return structure
    }

    private fun request(
        structure:CustomCycleReview=review(),
        choices:List<CatalogExerciseChoice> = fixture.generation().choices,
        maxMinutes:Int=60,
        excluded:Set<String> =emptySet(),
        dose:List<DeclaredMuscleDoseBounds> =
            checkNotNull(fixture.generation().policy).selectedBounds
    )=GeneratedStrengthDoseRecoveryDomain.preview(
        structure,"strength",choices,fixture.movements,dose,
        CustomExerciseAuthoringDomain.picker(
            fixture.athlete(),fixture.movements).map{it.availability},
        mapOf(0 to 1200,2 to 1200,4 to 1200),maxMinutes,
        excludedSlotIds=excluded)

    @Test fun aRealThirtySixToEighteenDoseCanRecoverFromExistingStrengthSets() {
        val original=cycle()
        val before=original.toString()
        val source=review(original)
        val boundary=StrengthDoseBoundaryGuardDomain.review(source,"strength")
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            boundary.status)
        assertEquals(36,boundary.hypertrophyWorkingSets)
        assertEquals(18,boundary.strengthWorkingSets)
        assertEquals(setOf(0,2,4),
            boundary.affectedDays.map{it.weekday}.toSet())

        val result=request(source)
        assertNotNull(result.explanation,result.structure)
        assertEquals(3,result.added.size)
        assertEquals(setOf(0,2,4),
            result.added.map{it.weekday}.toSet())
        val candidate=checkNotNull(result.structure).verifiedDraft!!
        assertEquals(original.revision,candidate.revision)
        assertEquals(original.blocks.first(),candidate.blocks.first())
        assertEquals(7,candidate.blocks.last().days.first().slots
            .sumOf { it.sets.size })
        assertEquals(listOf(7,7,7),candidate.blocks.last().days
            .map{it.slots.sumOf {slot->slot.sets.size}})
        val after=StrengthDoseBoundaryGuardDomain.review(
            checkNotNull(result.structure),"strength")
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,after.status)
        assertEquals(36,after.hypertrophyWorkingSets)
        assertEquals(21,after.strengthWorkingSets)
        val oldSlots=original.blocks.last().days.flatMap{it.slots}
            .associateBy{it.slotId}
        assertTrue(candidate.blocks.last().days.flatMap{it.slots}
            .all{slot ->
                val prior=oldSlots.getValue(slot.slotId)
                slot.sets.size in 2..4 &&
                    slot.sets.all{
                        it==prior.sets.first() &&
                            it.reps==RepBand(3,5) &&
                            it.targetRir==1 &&
                            it.restSeconds==180
                    } &&
                    slot.exerciseId==prior.exerciseId &&
                    slot.role==prior.role
            })
        assertEquals(before,original.toString())
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun authoredSetCeilingsTimeLimitsAndProtectedVisitsRefuseFakeRecovery() {
        val original=cycle()
        val source=review(original)
        val choices=fixture.generation().choices
        assertNull(request(source,choices.map{
            it.copy(maximumSetsPerVisit=2)
        }).structure)
        assertNull(request(source,maxMinutes=41).structure)
        assertNull(request(source,excluded=original.blocks.last().days
            .flatMap{it.slots}.map{it.slotId}.toSet()).structure)
        assertNull(request(source,dose=checkNotNull(
            fixture.generation().policy).selectedBounds.map{bound->
            if(bound.mrv>fixture.n("0"))bound.copy(mrv=fixture.n("6"))
            else bound
        }).structure)
        assertEquals(original,source.verifiedDraft)
    }

    @Test fun recoveredSameDaySourceDoesNotBecomeAnUncontrolledSetCounter() {
        val original=cycle()
        val source=review(original)
        val noBoundary=original.copy(blocks=listOf(
            original.blocks.first(),
            original.blocks.last().copy(days=original.blocks.last().days.map{
                day->day.copy(slots=day.slots.map{slot ->
                    slot.copy(sets=List(4){slot.sets.first()})
                })
            })))
        val inspected=review(noBoundary)
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,
            StrengthDoseBoundaryGuardDomain.review(inspected,"strength").status)
        assertNull(request(inspected).structure)
        assertTrue(request(inspected).added.isEmpty())
        assertEquals(2,source.verifiedDraft?.blocks?.size)
    }
    @Test fun strengthRepairCannotHideAuthoredClusterTimeElsewhereOnTheDay() {
        val original=cycle()
        // With only ordinary strength work, 6 x (90+180) seconds plus
        // 1200 overhead, a single additional 270-second set fits 52 min.
        assertNotNull(request(review(original),maxMinutes=52).structure)
        val strength=original.blocks.last()
        val modified=strength.copy(days=strength.days.map {day->
            if(day.weekday!=0)day else {
                val first=day.slots.first()
                val augmented=first.copy(sets=first.sets.mapIndexed {i,set->
                    if(i!=1)set else set.copy(
                        amplifier=CustomSetAmplifier(
                            CustomAmplifierKind.CLUSTER,2,30,
                            followUpActiveSeconds=120,followUpRounds=3))
                })
                day.copy(slots=listOf(augmented)+day.slots.drop(1))
            }
        })
        val altered=original.copy(blocks=listOf(
            original.blocks.first(),modified))
        val source=review(altered)
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            StrengthDoseBoundaryGuardDomain.review(source,"strength").status)
        val result=request(source,maxMinutes=52)
        assertNull(result.explanation,result.structure)
        assertTrue(result.added.isEmpty())
        // The source still has 18 authored Strength sets; no review has
        // auto-mutated the athlete's work to hide the missing 210 seconds.
        assertEquals(18,modified.days.sumOf{day->
            day.slots.sumOf{slot->slot.sets.count {
                it.type==DraftSetType.WORKING
            }}
        })
        assertEquals(original.blocks.first(),altered.blocks.first())
    }

    @Test fun unmeasuredAdvancedActivityCannotBeDeclaredInsideAHardSessionLimit() {
        val original=cycle()
        val strength=original.blocks.last()
        val amended=strength.copy(days=strength.days.map{day->
            if(day.weekday!=0)day else {
                val first=day.slots.first()
                day.copy(slots=listOf(first.copy(sets=first.sets.mapIndexed{i,set->
                    if(i==1)set.copy(amplifier=CustomSetAmplifier(
                        CustomAmplifierKind.CLUSTER,2,30,
                        followUpActiveSeconds=null,followUpRounds=3)) else set
                }))+day.slots.drop(1))
            }
        })
        val source=review(original.copy(blocks=listOf(
            original.blocks.first(),amended)))
        val result=request(source,maxMinutes=60)
        assertNull(result.structure)
        assertTrue(result.explanation.contains("not measured"))
        assertTrue(result.added.isEmpty())
    }

}
