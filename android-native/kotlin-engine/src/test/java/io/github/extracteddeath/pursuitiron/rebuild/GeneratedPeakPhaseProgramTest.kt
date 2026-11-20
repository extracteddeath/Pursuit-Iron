package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

/**
 * Full independent Kotlin-only Peak generation from a real saved Strength
 * block. Unlike a comparison-only test, this runs the actual weekly
 * composer, applies purpose-specific Peak prescriptions and converts a
 * concrete, unsigned whole-cycle alternative. No JavaScript or APK.
 */
class GeneratedPeakPhaseProgramTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    private fun sourceCycle():CustomTrainingCycle {
        val original=fixture.cycle()
        val template=original.blocks.single()
        val strength=template.copy(blockId="strength",weeks=2,
            label="Strength",phase=TrainingPhase.STRENGTH,
            days=template.days.map{day ->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                    slots=day.slots.map{slot->
                        slot.copy(sets=List(3){slot.sets.single()})
                    })
            })
        val recovery=strength.copy(blockId="recovery",label="Deload",weeks=1,
            purpose=CustomBlockPurpose.RECOVERY,
            days=strength.days.map{day ->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=listOf(slot.sets.first().copy(
                        reps=RepBand(5,8))))
                })
            })
        val peak=template.copy(blockId="peak",label="Peak",weeks=2,
            phase=TrainingPhase.PEAK,days=template.days.map{day ->
                day.copy(purpose=if(day.weekday==0)
                    ComposerSessionPurpose.STRENGTH_SKILL
                else ComposerSessionPurpose.GENERAL_PREPARATION)
            })
        return original.copy(blocks=listOf(strength,recovery,peak))
    }

    private fun peakProgram():AthleteProgramGenerationRequest {
        val base=fixture.generation()
        val old=checkNotNull(base.policy)
        // All phase-specific volume targets are explicitly authored, not
        // deduced by copying the preceding Strength week.
        val phaseBounds=old.selectedBounds.map{bound ->
            if(bound.mev>fixture.n("0"))
                bound.copy(mev=fixture.n("1"),
                    mav=fixture.n("2"),mrv=fixture.n("6"))
            else bound
        }
        // The actual generated-block converter derives time from the
        // later phase-specific prescriptions. Callers cannot rely on the
        // generic offer's slower/faster set timings or smuggle a conflict.
        return base.copy(phase=TrainingPhase.PEAK,
            policy=old.copy(phase=TrainingPhase.PEAK,
                baselineBounds=phaseBounds,selectedBounds=phaseBounds,
                minimumPurposeDays=mapOf(
                    ComposerSessionPurpose.STRENGTH_SKILL to 1,
                    ComposerSessionPurpose.GENERAL_PREPARATION to 2),
                primaryLiftMinimumDays=mapOf("bench" to 1)))
    }

    private fun prescriptions()=fixture.movements.flatMap{movement ->
        listOf(
            ExercisePrescriptionPolicy(movement.exerciseId,
                ComposerSessionPurpose.STRENGTH_SKILL,
                RepBand(1,3),1,50,180,SetLoadScheme.STRAIGHT,
                purposeWorkingLoad=fixture.n("30")),
            ExercisePrescriptionPolicy(movement.exerciseId,
                ComposerSessionPurpose.GENERAL_PREPARATION,
                RepBand(6,8),2,60,90,SetLoadScheme.STRAIGHT,
                purposeWorkingLoad=fixture.n("25"))
        )
    }

    @Test fun peakGeneratorCreatesRealTaperWithDistinctHeavyAndGeneralSessions() {
        val source=sourceCycle()
        val sourceText=source.toString()
        val requested=GeneratedAdaptiveBlockRequest(source,"peak",
            "phase_specific_peak",peakProgram(),prescriptions())
        val candidate=GeneratedAdaptiveBlockDomain.propose(requested)
        assertEquals("Peak result ${candidate.status}: ${candidate.explanation}; "+
            "generated=${candidate.generatedWeek?.issues}; "+
            "prescribed=${candidate.prescribedWeek?.reviewNotes}",
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            candidate.status)
        val proposed=checkNotNull(candidate.proposedOption).proposedCycle
        val peak=proposed.blocks.last()
        assertEquals(source.revision+1,proposed.revision)
        assertEquals(source.blocks.take(2),proposed.blocks.take(2))
        assertEquals(TrainingPhase.PEAK,peak.phase)
        assertEquals(setOf(0,2,4),peak.days.map{it.weekday}.toSet())
        assertEquals(1,peak.days.count{
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        })
        assertEquals(2,peak.days.count{
            it.purpose==ComposerSessionPurpose.GENERAL_PREPARATION
        })
        assertTrue(peak.days.all{it.slots.isNotEmpty()})
        val heavy=peak.days.single{
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }
        assertTrue(heavy.slots.any {
            it.exerciseId=="bench" && it.role==ComposerRole.PRIMARY
        })
        assertTrue(heavy.slots.all{slot ->slot.sets.all{set->
            set.type==DraftSetType.WORKING &&
                set.reps==RepBand(1,3) && set.targetRir==1 &&
                set.activeSeconds==50 && set.restSeconds==180 &&
                (set.load as DraftSetLoad.Verified).value==fixture.n("30")
        }})
        assertTrue(peak.days.filter{
            it.purpose==ComposerSessionPurpose.GENERAL_PREPARATION
        }.all{day ->day.slots.all{slot->slot.sets.all{set->
            set.type==DraftSetType.WORKING &&
                set.reps==RepBand(6,8) && set.targetRir==2 &&
                set.activeSeconds==60 && set.restSeconds==90 &&
                (set.load as DraftSetLoad.Verified).value==fixture.n("25")
        }}})
        val taper=checkNotNull(candidate.peakTaperBoundary)
        assertEquals(PeakTaperBoundaryStatus.MEASURED_FOR_REVIEW,
            taper.status)
        assertEquals(2,taper.strengthExitWeek)
        assertEquals(4,taper.peakEntryWeek)
        assertEquals(27,taper.strengthWorkingSets)
        assertTrue(checkNotNull(taper.peakWorkingSets)<
            checkNotNull(taper.strengthWorkingSets))
        assertTrue(taper.primaryPractice.any{
            it.exerciseId=="bench" && it.peakDays>=1
        })
        assertTrue(taper.lostMainLiftIds.isEmpty())
        val numeric=checkNotNull(candidate.upstreamNumberTrace)
        val boundary=checkNotNull(numeric.phaseBoundary)
        assertEquals(TrainingPhase.STRENGTH,boundary.sourcePhase)
        assertEquals(TrainingPhase.PEAK,boundary.destinationPhase)
        assertEquals(2,boundary.sourceExitWeek)
        assertEquals(4,boundary.destinationEntryWeek)
        assertEquals(27,boundary.sourceWorkingSets)
        assertEquals(taper.peakWorkingSets,boundary.destinationWorkingSets)
        assertTrue(numeric.sets.isNotEmpty())
        assertTrue(numeric.sets.filter{
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }.all{
            it.reps==RepBand(1,3) && it.targetRir==1 &&
            it.restSeconds==180 &&
            it.origin==GeneratedSetNumberOrigin.EXERCISE_PHASE_POLICY
        })
        assertTrue(numeric.sets.filter{
            it.purpose==ComposerSessionPurpose.GENERAL_PREPARATION
        }.all{
            it.reps==RepBand(6,8) && it.targetRir==2 &&
            it.restSeconds==90
        })
        assertEquals(GeneratedHistoryEvidenceState.NOT_CONSUMED,
            numeric.historyEvidence)
        assertEquals(TrainingPhase.PEAK,numeric.plannedWeekDose.phase)
        assertEquals(checkNotNull(candidate.prescribedWeek?.finalAudit).muscles,
            numeric.plannedWeekDose.muscles)
        assertEquals(checkNotNull(candidate.prescribedWeek?.finalAudit).sessions,
            numeric.plannedWeekDose.sessions)
        assertTrue(numeric.plannedWeekDose.sessions.all {
            it.declaredSeconds>0 && it.roundedMinutes>0
        })
        assertEquals(sourceText,source.toString())
        assertFalse(candidate.maySaveProgram)
        assertFalse(candidate.mayIssueWorkout)
        assertEquals(candidate,GeneratedAdaptiveBlockDomain.propose(requested))
    }

    @Test fun mismatchedPeakPolicyNeverBorrowsSourceStrengthPrescription() {
        val source=sourceCycle()
        val incorrect=peakProgram().copy(phase=TrainingPhase.STRENGTH)
        val invalid=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(source,"peak","invalid_phase",
                incorrect,prescriptions()))
        assertEquals(GeneratedAdaptiveBlockStatus.INVALID_SOURCE,
            invalid.status)
        assertNull(invalid.proposedOption)
        val missing=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(source,"peak",
                "missing_peak_prescription",peakProgram(),
                prescriptions().filterNot{
                    it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
                }))
        assertEquals(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
            missing.status)
        assertNull(missing.proposedOption)
        assertFalse(missing.mayIssueWorkout)

        val declaredDifferent=peakProgram().copy(
            visitTimings=CoachSessionPrescriptionDomain
                .timingsForPolicies(prescriptions()).map {
                    if(it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                        it.copy(restSecondsPerSet=it.restSecondsPerSet+30)
                    else it
                })
        val conflict=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(source,"peak",
                "mismatched_rest_seconds",declaredDifferent,
                prescriptions()))
        assertEquals(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
            conflict.status)
        assertNull(conflict.proposedOption)
    }
}
