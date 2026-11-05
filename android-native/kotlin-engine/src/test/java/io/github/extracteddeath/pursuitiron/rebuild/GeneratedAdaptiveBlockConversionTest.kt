package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class GeneratedAdaptiveBlockConversionTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    @Test fun realGeneratorProducesCompleteExactFutureBlockWithoutIssuing() {
        val request=fixture.request()
        val outcome=GeneratedAdaptiveBlockDomain.propose(request)
        assertTrue("Conversion status: "+outcome.status+
            " reason: "+outcome.explanation+
            " generation: "+outcome.generatedWeek?.status+
            " issues: "+outcome.generatedWeek?.issues+
            " prescription: "+outcome.prescribedWeek?.status+
            " notes: "+outcome.prescribedWeek?.reviewNotes,
            outcome.status==GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW)
        val proposed=checkNotNull(outcome.proposedOption).proposedCycle
        assertEquals(5L,proposed.revision)
        assertEquals(3,proposed.blocks.single().weeks)
        assertEquals(setOf(0,2,4),
            proposed.blocks.single().days.map {it.weekday}.toSet())
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,
            outcome.prescribedWeek?.status)
        assertTrue(proposed.blocks.single().days.all {day->
            day.slots.isNotEmpty() && day.slots.all {slot ->
                slot.slotId=="old_"+slot.exerciseId &&
                    !slot.pinned &&
                    slot.sets.any {it.type==DraftSetType.WORKING} &&
                    slot.sets.all {set->
                        set.reps==RepBand(8,12) && set.targetRir==2 &&
                            (set.load as DraftSetLoad.Verified).value==
                            fixture.n("25")
                    }
            }
        })
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            CustomTrainingCycleDomain.review(proposed,
                CustomExerciseAuthoringDomain.picker(
                    fixture.athlete(),fixture.movements)
                    .map {it.availability}).status)
        assertEquals(4L,request.currentCycle.revision)
        assertFalse(outcome.maySaveProgram)
        assertFalse(outcome.mayIssueWorkout)
        assertEquals(outcome,GeneratedAdaptiveBlockDomain.propose(request))
    }

    @Test fun missingPrescriptionAndImpossibleGymWeightFailClosed() {
        val absent=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(prescriptions=fixture.policies().dropLast(1)))
        assertTrue("missing policy returned "+absent.status+
            " / "+absent.explanation,
            absent.status==GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED)
        assertNull(absent.proposedOption)
        val bad=fixture.policies().map {policy->
            if(policy.exerciseId=="bench")
                policy.copy(purposeWorkingLoad=fixture.n("27.5"))
            else policy
        }
        val impossible=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(prescriptions=bad))
        assertEquals(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
            impossible.status)
        assertNull(impossible.proposedOption)
    }

    @Test fun pinnedAndPerWeekCustomOverridesAreNotOverwritten() {
        val original=fixture.cycle()
        val prior=original.blocks.single()
        val day=prior.days.first()
        val pinned=original.copy(blocks=listOf(prior.copy(
            days=listOf(day.copy(slots=day.slots.mapIndexed {i,slot->
                if(i==0)slot.copy(pinned=true) else slot
            }))+prior.days.drop(1))))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            GeneratedAdaptiveBlockDomain.propose(
                fixture.request(original=pinned)).status)
        val existing=day.slots.first()
        val override=CustomWeekSlotOverride(2,day.weekday,existing.slotId,
            existing.sets+existing.sets)
        val withOverride=original.copy(weekOverrides=mapOf(
            prior.blockId to listOf(override)))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            GeneratedAdaptiveBlockDomain.propose(
                fixture.request(original=withOverride)).status)
        assertEquals(listOf(override),withOverride.weekOverrides[prior.blockId])
    }
}
