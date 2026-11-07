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

    @Test fun changedTrainingPurposeGetsNewSlotIdentityWithoutBorrowingSameDayHistory() {
        val original=fixture.cycle()
        val before=original.blocks.single()
        val changed=original.copy(blocks=listOf(before.copy(
            days=before.days.map { day ->
                if(day.weekday==0)
                    day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
                else day
            })))
        val request=fixture.request(original=changed)
        val first=GeneratedAdaptiveBlockDomain.propose(request)
        assertEquals(first.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,first.status)
        assertEquals(first,GeneratedAdaptiveBlockDomain.propose(request))
        val candidate=checkNotNull(first.proposedOption).proposedCycle
        val oldByDay=before.days.associateBy {it.weekday}
        val newByDay=candidate.blocks.single().days.associateBy {it.weekday}
        assertEquals(9,first.slotContinuity.size)
        assertEquals(3,first.slotContinuity.count {
            it.relationship==GeneratedAdaptiveSlotRelationship.CHANGED_SESSION_PURPOSE
        })
        assertEquals(6,first.slotContinuity.count {
            it.relationship==GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE
        })
        first.slotContinuity.forEach { history ->
            val old=checkNotNull(oldByDay[history.weekday]).slots.single {
                it.exerciseId==history.exerciseId
            }
            val next=checkNotNull(newByDay[history.weekday]).slots.single {
                it.exerciseId==history.exerciseId
            }
            assertEquals(old.slotId,history.priorSlotId)
            assertEquals(next.slotId,history.proposedSlotId)
            if(history.weekday==0) {
                assertNotEquals(old.slotId,next.slotId)
                assertTrue(next.slotId.startsWith("coach_d0_"))
            } else assertEquals(old.slotId,next.slotId)
        }
        // An edited cycle proposal must never rewrite the source block.
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,
            changed.blocks.single().days.first().purpose)
        assertEquals(4L,changed.revision)
        assertFalse(first.mayIssueWorkout)
        assertFalse(first.maySaveProgram)
        // The returned evidence cannot be modified after review.
        assertThrows(UnsupportedOperationException::class.java) {
            (first.slotContinuity as MutableList<GeneratedAdaptiveSlotContinuity>)
                .clear()
        }
    }

    @Test fun changedExerciseRoleCannotReuseOtherwiseMatchingTrainingSlot() {
        val original=fixture.cycle()
        val before=original.blocks.single()
        val amended=original.copy(blocks=listOf(before.copy(
            days=before.days.map { day ->
                if(day.weekday!=2) day else day.copy(slots=day.slots.map { slot ->
                    if(slot.exerciseId=="bench")
                        slot.copy(role=ComposerRole.SECONDARY)
                    else slot
                })
            })))
        val result=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=amended))
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        val changed=result.slotContinuity.single {
            it.weekday==2 && it.exerciseId=="bench"
        }
        assertEquals(
            GeneratedAdaptiveSlotRelationship.CHANGED_EXERCISE_ROLE,
            changed.relationship)
        assertEquals("old_bench",changed.priorSlotId)
        assertNotEquals("old_bench",changed.proposedSlotId)
        assertEquals(8,result.slotContinuity.count {
            it.relationship==GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE
        })
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
