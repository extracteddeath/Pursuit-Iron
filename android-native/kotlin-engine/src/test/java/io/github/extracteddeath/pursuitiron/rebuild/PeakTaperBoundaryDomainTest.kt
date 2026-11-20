package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class PeakTaperBoundaryDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    private fun cycle():CustomTrainingCycle {
        val original=fixture.cycle()
        val base=original.blocks.single()
        val strength=base.copy(blockId="strength",label="Strength",weeks=2,
            phase=TrainingPhase.STRENGTH,
            days=base.days.map{day ->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                    slots=day.slots.map{slot ->
                        slot.copy(sets=List(3){slot.sets.single()})
                    })
            })
        val bridge=strength.copy(blockId="deload",label="Recovery",weeks=1,
            purpose=CustomBlockPurpose.RECOVERY,
            days=strength.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=listOf(slot.sets.first()))
                })
            })
        val peak=base.copy(blockId="peak",label="Peak",weeks=2,
            phase=TrainingPhase.PEAK,
            days=base.days.map{day->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                    slots=day.slots.filter{slot->
                        when(day.weekday) {
                            0 ->slot.exerciseId in setOf("bench","row")
                            2 ->slot.exerciseId=="row"
                            else ->slot.exerciseId=="squat"
                        }
                    }.map{slot ->
                        slot.copy(sets=listOf(slot.sets.first().copy(
                            reps=RepBand(1,3),targetRir=1)))
                    })
            })
        return original.copy(blocks=listOf(strength,bridge,peak))
    }

    private fun structure(cycle:CustomTrainingCycle):CustomCycleReview =
        CustomTrainingCycleDomain.review(cycle,
            CustomExerciseAuthoringDomain.picker(
                fixture.athlete(),fixture.movements).map{it.availability})

    @Test fun peakTaperUsesActualStrengthExitNotRecoveryBridge() {
        val source=cycle()
        val before=source.toString()
        val verified=structure(source)
        assertEquals(verified.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,verified.status)
        val review=PeakTaperBoundaryDomain.review(verified,"peak")
        assertEquals(PeakTaperBoundaryStatus.MEASURED_FOR_REVIEW,
            review.status)
        assertEquals(2,review.strengthExitWeek)
        assertEquals(4,review.peakEntryWeek)
        assertEquals(27,review.strengthWorkingSets)
        assertEquals(4,review.peakWorkingSets)
        assertEquals(9,review.strengthExerciseVisits)
        assertEquals(4,review.peakExerciseVisits)
        assertFalse(review.splitChanged)
        val bench=review.primaryPractice.single{it.exerciseId=="bench"}
        assertEquals(3,bench.strengthDays)
        assertEquals(1,bench.peakDays)
        assertTrue(bench.practiceReduced)
        assertFalse(bench.absentFromPeak)
        assertEquals(setOf("bench"),review.reducedPrimaryPracticeIds)
        assertTrue(review.lostMainLiftIds.isEmpty())
        assertEquals(listOf(0,2,4),review.dayChanges.map{it.weekday})
        assertEquals(listOf(9,9,9),
            review.dayChanges.map{it.strengthWorkingSets})
        assertEquals(listOf(2,1,1),
            review.dayChanges.map{it.peakWorkingSets})
        assertEquals(before,source.toString())
        assertFalse(review.maySaveProgram)
        assertFalse(review.mayApplyAutomatically)
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayAlterHistory)
    }

    @Test fun noPeakBenchPracticeIsShownSeparatelyFromIntentionalSetTaper() {
        val original=cycle()
        val peak=original.blocks.last()
        val withoutBench=original.copy(blocks=original.blocks.dropLast(1)+
            peak.copy(days=peak.days.map{day ->
                day.copy(slots=day.slots.filterNot{
                    it.exerciseId=="bench"
                })
            }))
        val checked=structure(withoutBench)
        assertEquals(checked.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,checked.status)
        val outcome=PeakTaperBoundaryDomain.review(checked,"peak")
        assertEquals(PeakTaperBoundaryStatus.MEASURED_FOR_REVIEW,outcome.status)
        assertEquals(3,outcome.peakWorkingSets)
        assertEquals(setOf("bench"),outcome.lostMainLiftIds)
        assertEquals(0,outcome.primaryPractice.single().peakDays)
        assertFalse(outcome.maySaveProgram)
        assertEquals(27,outcome.strengthWorkingSets)
    }

    @Test fun phaseMisidentificationAndUnsignedSourceNeverProduceTaperEvidence() {
        val original=cycle()
        val prepared=structure(original)
        assertEquals(PeakTaperBoundaryStatus.NOT_COMPARABLE,
            PeakTaperBoundaryDomain.review(prepared,"strength").status)
        assertEquals(PeakTaperBoundaryStatus.NOT_COMPARABLE,
            PeakTaperBoundaryDomain.review(prepared,"not-a-block").status)
        val changed=original.copy(blocks=original.blocks.map{block ->
            if(block.blockId=="strength")block.copy(phase=TrainingPhase.HYPERTROPHY)
            else block
        })
        val notStrength=structure(changed)
        assertEquals(PeakTaperBoundaryStatus.NOT_COMPARABLE,
            PeakTaperBoundaryDomain.review(notStrength,"peak").status)
        val unsigned=prepared.copy(verifiedDraft=null)
        assertEquals(PeakTaperBoundaryStatus.SOURCE_UNVERIFIED,
            PeakTaperBoundaryDomain.review(unsigned,"peak").status)
    }
}
