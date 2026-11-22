package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class BasicProgramProgressionPlanDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private fun working(value:String="100",band:RepBand=RepBand(6,10),
        amplifier:CustomSetAmplifier?=null,assisted:Boolean=false) =
        CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,
                if(assisted)LoadBasis.ASSISTANCE_COUNTERWEIGHT
                else LoadBasis.MACHINE_STACK,n(value)),
            band,2,45,90,amplifier)
    private fun slot(
        id:String="slot",role:ComposerRole=ComposerRole.SECONDARY,
        sets:List<CustomAuthoredSet> = listOf(working(),working()),
        exerciseId:String="press"
    )=CustomExerciseSlot(id,exerciseId,role,sets)
    private fun block(
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        purpose:CustomBlockPurpose=CustomBlockPurpose.TRAINING,
        slots:List<CustomExerciseSlot> = listOf(slot()),
        id:String="block"
    )=CustomTrainingBlock(id,"Block",phase,purpose,4,
        CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
        listOf(CustomTrainingDay(0,"Day",ComposerSessionPurpose.HYPERTROPHY,slots)))
    private fun cycle(vararg blocks:CustomTrainingBlock,
        overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap()) =
        CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.POWERBUILDING,
            "Program",blocks.toList(),weekOverrides=overrides)
    private fun assignment(cycle:CustomTrainingCycle,
        selected:Set<ProgramProgressionSlotKey> = emptySet()) =
        (BasicProgramProgressionPlanDomain.review(cycle,selected)
            as ProgramProgressionPlan.Ready).assignments.single()

    @Test fun progressionAssignmentRespectsWhatZeroPhysicallyMeans() {
        for (basis in LoadBasis.entries) {
            val set=working().copy(load=DraftSetLoad.Verified(LoadUnit.KG,basis,n("0.000")))
            val result=BasicProgramProgressionPlanDomain.review(
                cycle(block(slots=listOf(slot(sets=listOf(set))))))
            if (basis==LoadBasis.LANDMINE_ADDED_PLATES)
                assertTrue(basis.name,result is ProgramProgressionPlan.Ready)
            else
                assertTrue(basis.name,result is ProgramProgressionPlan.Refused)
        }
    }

    @Test fun unknownExperienceDoesNotInventLinearStrengthProgression() {
        val hypertrophy=assignment(cycle(block()))
        assertEquals(ProgramProgressionRoute.BASIC_WEIGHTED,hypertrophy.route)
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            hypertrophy.basicStrategy)
        val strengthCycle=cycle(block(phase=TrainingPhase.STRENGTH,
            slots=listOf(slot(role=ComposerRole.PRIMARY))))
        val strength=assignment(strengthCycle)
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            strength.basicStrategy)
        assertEquals(2,strength.workingSetCount)
        assertFalse(strength.mayModifyIssuedSet)
        assertFalse(strength.mayChangeSavedProgram)
        // A saved/reviewed linear choice still works; only the unsupported
        // inference from an unknown athlete is removed.
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val explicit=BasicProgramProgressionPlanDomain.review(
            strengthCycle,selectedStyles=mapOf(key to NativeProgressionStyle.LINEAR))
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            explicit.assignments.single().basicStrategy)
    }

    @Test fun bodyweightAndAssistanceNeverUseOrdinaryLoadIncrease() {
        val bw=slot(sets=List(2) { CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(8,15),2,45,90) })
        val body=assignment(cycle(block(slots=listOf(bw))))
        assertEquals(ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS,body.route)
        assertNull(body.basicStrategy)
        val ass=slot(sets=listOf(working("40",assisted=true),
            working("40",assisted=true)))
        val assisted=assignment(cycle(block(slots=listOf(ass))))
        assertEquals(BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            assisted.basicStrategy)
    }

    @Test fun authoredPeriodizationPeakRecoveryAndTestsDoNotReceiveHiddenDp() {
        val original=block()
        val override=CustomWeekSlotOverride(2,0,"slot",
            listOf(working("105"),working("105")))
        val byWeek=assignment(cycle(original,overrides=mapOf("block" to listOf(override))))
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            byWeek.route)
        assertEquals(setOf(2),byWeek.authoredWeekNumbers)
        assertNull(byWeek.basicStrategy)
        for(purpose in listOf(CustomBlockPurpose.RECOVERY,
            CustomBlockPurpose.PERFORMANCE_TEST)) {
            val review=assignment(cycle(block(purpose=purpose)))
            assertNull(review.basicStrategy)
            assertEquals(if(purpose==CustomBlockPurpose.RECOVERY)
                ProgramProgressionRoute.RECOVERY_HOLD
                else ProgramProgressionRoute.PEAK_OR_TEST_HOLD,review.route)
        }
        val peak=assignment(cycle(block(phase=TrainingPhase.PEAK)))
        assertEquals(ProgramProgressionRoute.PEAK_OR_TEST_HOLD,peak.route)
        assertNull(peak.basicStrategy)
    }

    @Test fun explicitlyDifferentLoadsCannotBecomeUnapprovedLeveling() {
        val different=slot(sets=listOf(working("100"),working("95")))
        val source=cycle(block(slots=listOf(different)))
        val baseline=assignment(source)
        assertEquals(ProgramProgressionRoute.SPECIAL_LOAD_SCHEME,baseline.route)
        assertNull(baseline.basicStrategy)
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val explicit=assignment(source,setOf(key))
        assertEquals(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            explicit.basicStrategy)
        assertEquals(ProgramProgressionRoute.BASIC_WEIGHTED,explicit.route)
        assertEquals(ProgramProgressionPlan.Refused(
            "Every selected progression must match one unique actual source slot."),
            BasicProgramProgressionPlanDomain.review(source,
                setOf(key.copy(slotId="not-in-program"))))
    }

    @Test fun invalidPerSetSelectionsAndIncompatiblePhysicalMeaningFailClosed() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val assistance=slot(sets=listOf(working("40",assisted=true),
            working("30",assisted=true)))
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(assistance))),setOf(key))
            is ProgramProgressionPlan.Refused)
        val differentBand=slot(sets=listOf(working("100"),
            working("100",RepBand(10,15))))
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(differentBand))),setOf(key))
            is ProgramProgressionPlan.Refused)
        val mixed=slot(sets=listOf(working(),
            CustomAuthoredSet(DraftSetType.WORKING,
                DraftSetLoad.ExplicitBodyweight,RepBand(6,10),2,45,90)))
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(mixed))))
            is ProgramProgressionPlan.Refused)
    }

    @Test fun explicitUserModesCoverManualRepOnlyAndPerSetWithoutOverridingPeak() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val source=cycle(block())
        val manual=BasicProgramProgressionPlanDomain.review(source,
            selectedStrategies=mapOf(key to BasicProgressionStrategy.MANUAL_LOGGING))
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.MANUAL,manual.assignments.single().route)
        assertEquals(BasicProgressionStrategy.MANUAL_LOGGING,
            manual.assignments.single().basicStrategy)
        val repsSource=cycle(block(slots=listOf(slot(sets=List(2){
            working().copy(exactRepTarget=8)
        }))))
        val reps=BasicProgramProgressionPlanDomain.review(repsSource,
            selectedStrategies=mapOf(key to BasicProgressionStrategy.REP_TARGET_PROGRESSION))
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            reps.assignments.single().basicStrategy)
        val dynamic=BasicProgramProgressionPlanDomain.review(source,
            selectedStrategies=mapOf(key to BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION))
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            dynamic.assignments.single().basicStrategy)
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(phase=TrainingPhase.PEAK)),
            selectedStrategies=mapOf(key to BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION))
            is ProgramProgressionPlan.Refused)
        val manuallyHeldPeak=BasicProgramProgressionPlanDomain.review(
            cycle(block(phase=TrainingPhase.PEAK)),
            selectedStrategies=mapOf(key to BasicProgressionStrategy.MANUAL_LOGGING))
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.MANUAL,
            manuallyHeldPeak.assignments.single().route)
    }

    @Test fun explicitAssistedRepOnlyNeverGetsOverriddenByDecrementStrategy() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val ass=slot(sets=listOf(working("40",assisted=true).copy(exactRepTarget=8),
            working("40",assisted=true).copy(exactRepTarget=8)))
        val result=BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(ass))),
            selectedStrategies=mapOf(key to BasicProgressionStrategy.REP_TARGET_PROGRESSION))
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            result.assignments.single().basicStrategy)
    }

    @Test fun allWebProgressionStyleNamesHaveTypedNativeRoutes() {
        assertEquals(setOf(NativeProgressionStyle.AUTO,
            NativeProgressionStyle.DOUBLE,NativeProgressionStyle.DYNAMIC,
            NativeProgressionStyle.LADDER,NativeProgressionStyle.LINEAR,
            NativeProgressionStyle.WAVE,NativeProgressionStyle.E1RM),
            NativeProgressionStyle.entries.toSet())
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val source=cycle(block(phase=TrainingPhase.STRENGTH,
            slots=listOf(slot(role=ComposerRole.PRIMARY))))
        for((style,expected) in mapOf(
            NativeProgressionStyle.DOUBLE to
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            NativeProgressionStyle.DYNAMIC to
                BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            NativeProgressionStyle.LADDER to
                BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            NativeProgressionStyle.LINEAR to
                BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION)) {
            val selectedSource=if(style==NativeProgressionStyle.LADDER)
                cycle(block(phase=TrainingPhase.STRENGTH,
                    slots=listOf(slot(role=ComposerRole.PRIMARY,
                        sets=List(2){working().copy(exactRepTarget=8)}))))
            else source
            val response=BasicProgramProgressionPlanDomain.review(
                selectedSource,selectedStyles=mapOf(key to style))
                as ProgramProgressionPlan.Ready
            assertEquals(expected,response.assignments.single().basicStrategy)
            assertEquals(style,response.assignments.single().selectedStyle)
        }
        assertEquals(ProgramProgressionPlan.Refused(
            "Wave progression needs a complete separately authored weekly load plan, not an invented wave."),
            BasicProgramProgressionPlanDomain.review(source,
                selectedStyles=mapOf(key to NativeProgressionStyle.WAVE)))
        val wave=BasicProgramProgressionPlanDomain.review(
            cycle(block(phase=TrainingPhase.STRENGTH),
                overrides=mapOf("block" to (2..4).map { week ->
                    CustomWeekSlotOverride(week,0,"slot",
                        listOf(working("${100+week*5}"),working("${100+week*5}")))
                })),
            selectedStyles=mapOf(key to NativeProgressionStyle.WAVE))
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            wave.assignments.single().route)
        val e1rm=BasicProgramProgressionPlanDomain.review(source,
            selectedStyles=mapOf(key to NativeProgressionStyle.E1RM))
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.E1RM_COACH_REVIEW,
            e1rm.assignments.single().route)
        assertNull(e1rm.assignments.single().basicStrategy)
        val auto=BasicProgramProgressionPlanDomain.review(source,
            selectedStyles=mapOf(key to NativeProgressionStyle.AUTO))
            as ProgramProgressionPlan.Ready
        assertNotNull(auto.assignments.single().basicStrategy)
        assertEquals(NativeProgressionStyle.AUTO,auto.assignments.single().selectedStyle)
    }

    @Test fun waveRequiresEverySubsequentAuthoredWeekAndRejectsRepeatedWeek() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val saved=block(phase=TrainingPhase.STRENGTH,slots=listOf(
            slot().copy(progressionMethod=SavedProgressionMethod.WAVE)))
        fun override(week:Int)=CustomWeekSlotOverride(week,0,"slot",
            listOf(working("${100+week*5}"),working("${100+week*5}")))
        val partial=cycle(saved,overrides=mapOf("block" to
            listOf(override(2),override(4))))
        assertTrue(BasicProgramProgressionPlanDomain.review(partial)
            is ProgramProgressionPlan.Refused)
        val duplicate=cycle(saved,overrides=mapOf("block" to
            listOf(override(2),override(3),override(3),override(4))))
        assertTrue(BasicProgramProgressionPlanDomain.review(duplicate)
            is ProgramProgressionPlan.Refused)
        val complete=cycle(saved,overrides=mapOf("block" to
            listOf(override(2),override(3),override(4))))
        val ready=BasicProgramProgressionPlanDomain.review(complete)
            as ProgramProgressionPlan.Ready
        val chosen=ready.assignments.single()
        assertEquals(key,chosen.key)
        assertEquals(SavedProgressionMethod.WAVE,chosen.savedMethod)
        assertEquals(setOf(2,3,4),chosen.authoredWeekNumbers)
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            chosen.route)
        assertNull(chosen.basicStrategy)
        assertFalse(ready.mayIssueWorkout)
    }

    @Test fun specialistMethodNamesArePreservedWithoutInventingBasicLoads() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val periodized=cycle(block(phase=TrainingPhase.STRENGTH),
            overrides=mapOf("block" to (2..4).map { week ->
                CustomWeekSlotOverride(week,0,"slot",
                    listOf(working("${100+week*5}"),working("${100+week*5}")))
            }))
        val ordinary=BasicProgramProgressionPlanDomain.review(periodized)
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            ordinary.assignments.single().route)
        assertNull(BasicProgramProgressionPlanDomain.savedChoice(
            ordinary.assignments.single()))
        val wave=BasicProgramProgressionPlanDomain.review(periodized,
            selectedStyles=mapOf(key to NativeProgressionStyle.WAVE))
            as ProgramProgressionPlan.Ready
        assertNull(wave.assignments.single().basicStrategy)
        assertEquals(SavedProgressionMethod.WAVE,
            BasicProgramProgressionPlanDomain.savedChoice(
                wave.assignments.single()))
        val frozen=periodized.copy(blocks=listOf(block(
            phase=TrainingPhase.STRENGTH,slots=listOf(slot().copy(
                progressionMethod=SavedProgressionMethod.WAVE)))))
        val reopened=BasicProgramProgressionPlanDomain.review(frozen)
            as ProgramProgressionPlan.Ready
        assertEquals(SavedProgressionMethod.WAVE,
            BasicProgramProgressionPlanDomain.savedChoice(
                reopened.assignments.single()))

        val strength=cycle(block(phase=TrainingPhase.STRENGTH,
            slots=listOf(slot(role=ComposerRole.PRIMARY))))
        val e1rm=BasicProgramProgressionPlanDomain.review(strength,
            selectedStyles=mapOf(key to NativeProgressionStyle.E1RM))
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.E1RM_COACH_REVIEW,
            e1rm.assignments.single().route)
        assertNull(e1rm.assignments.single().basicStrategy)
        assertEquals(SavedProgressionMethod.E1RM,
            BasicProgramProgressionPlanDomain.savedChoice(
                e1rm.assignments.single()))
    }

    @Test fun e1rmCannotBorrowBodyweightAssistanceOrConflictingAuthoredSchemes() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        fun review(sets:List<CustomAuthoredSet>,
            overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap(),
            method:SavedProgressionMethod?=null) =
            BasicProgramProgressionPlanDomain.review(
                cycle(block(phase=TrainingPhase.STRENGTH,
                    slots=listOf(slot(role=ComposerRole.PRIMARY,
                        sets=sets).copy(progressionMethod=method))),
                    overrides=overrides),
                selectedStyles=if(method==null)
                    mapOf(key to NativeProgressionStyle.E1RM) else emptyMap())
        val valid=review(listOf(working(),working()))
        assertTrue(valid is ProgramProgressionPlan.Ready)
        val bodyweight=CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.ExplicitBodyweight,RepBand(6,10),2,45,90)
        assertTrue(review(listOf(bodyweight,bodyweight))
            is ProgramProgressionPlan.Refused)
        assertTrue(review(listOf(working("40",assisted=true),
            working("40",assisted=true))) is ProgramProgressionPlan.Refused)
        assertTrue(review(listOf(working("100"),working("95")))
            is ProgramProgressionPlan.Refused)
        assertTrue(review(listOf(working("100"),
            working("100",RepBand(8,12)))) is ProgramProgressionPlan.Refused)
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=3)
        assertTrue(review(listOf(working(amplifier=amp),working()))
            is ProgramProgressionPlan.Refused)
        val authored=mapOf("block" to listOf(
            CustomWeekSlotOverride(2,0,"slot",listOf(working("105"),working("105")))))
        assertTrue(review(listOf(working(),working()),overrides=authored)
            is ProgramProgressionPlan.Refused)
        assertTrue(review(listOf(bodyweight,bodyweight),
            method=SavedProgressionMethod.E1RM)
            is ProgramProgressionPlan.Refused)
    }

    @Test fun savedBasicMethodSurvivesReviewedWeekEditsWithoutMutatingAuthoredNumbers() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val override=CustomWeekSlotOverride(2,0,"slot",
            listOf(working("105"),working("105")))
        val saved=cycle(block(slots=listOf(slot().copy(
            progressionMethod=SavedProgressionMethod.DOUBLE))),
            overrides=mapOf("block" to listOf(override)))
        val reviewed=BasicProgramProgressionPlanDomain.review(saved)
            as ProgramProgressionPlan.Ready
        val choice=reviewed.assignments.single()
        assertEquals(setOf(2),choice.authoredWeekNumbers)
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            choice.route)
        assertNull(choice.basicStrategy)
        assertEquals(SavedProgressionMethod.DOUBLE,
            BasicProgramProgressionPlanDomain.savedChoice(choice))
        assertFalse(choice.mayModifyIssuedSet)
        // A preview-only progression selection cannot supersede the same
        // athlete-authored week without editing and signing the source.
        val unsaved=saved.copy(blocks=listOf(block()))
        assertTrue(BasicProgramProgressionPlanDomain.review(unsaved,
            selectedStrategies=mapOf(key to
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION))
            is ProgramProgressionPlan.Refused)
    }

    @Test fun bodyweightLadderNeedsAnAuthoredRungForEveryWorkingSet() {
        val key = ProgramProgressionSlotKey("block", 0, "slot")
        val first = working().copy(load = DraftSetLoad.ExplicitBodyweight, exactRepTarget = 7)
        for (saved in listOf(false, true)) {
            val styles = if (saved) emptyMap() else mapOf(key to NativeProgressionStyle.LADDER)
            fun review(lastTarget: Int?): ProgramProgressionPlan {
                val movement = slot(sets = listOf(first, first.copy(exactRepTarget = lastTarget)))
                    .copy(progressionMethod = if (saved) SavedProgressionMethod.LADDER else null)
                return BasicProgramProgressionPlanDomain.review(
                    cycle(block(slots = listOf(movement))), selectedStyles = styles)
            }
            assertTrue(review(null) is ProgramProgressionPlan.Refused)
            assertTrue(review(11) is ProgramProgressionPlan.Refused)
            val ready = review(8) as ProgramProgressionPlan.Ready
            assertEquals(ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS, ready.assignments.single().route)
            assertEquals(SavedProgressionMethod.LADDER,
                BasicProgramProgressionPlanDomain.savedChoice(ready.assignments.single()))
        }
    }

    @Test fun sourceReadinessRequiresExactTargetsForSelectedRepModes() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val unset=cycle(block())
        assertTrue(BasicProgramProgressionPlanDomain.review(unset,
            selectedStrategies=mapOf(key to
                BasicProgressionStrategy.REP_TARGET_PROGRESSION))
            is ProgramProgressionPlan.Refused)
        assertTrue(BasicProgramProgressionPlanDomain.review(unset,
            selectedStyles=mapOf(key to NativeProgressionStyle.LADDER))
            is ProgramProgressionPlan.Refused)
        for(method in listOf(SavedProgressionMethod.REP_TARGETS,
            SavedProgressionMethod.LADDER)) {
            val noTargets=cycle(block(slots=listOf(slot().copy(
                progressionMethod=method))))
            assertTrue(BasicProgramProgressionPlanDomain.review(noTargets)
                is ProgramProgressionPlan.Refused)
            val outOfRange=cycle(block(slots=listOf(slot(sets=List(2){
                working().copy(exactRepTarget=11)
            }).copy(progressionMethod=method))))
            assertTrue(BasicProgramProgressionPlanDomain.review(outOfRange)
                is ProgramProgressionPlan.Refused)
            val exact=cycle(block(slots=listOf(slot(sets=List(2){
                working().copy(exactRepTarget=8)
            }).copy(progressionMethod=method))))
            val ready=BasicProgramProgressionPlanDomain.review(exact)
                as ProgramProgressionPlan.Ready
            assertEquals(method,ready.assignments.single().savedMethod)
        }
        val bw=slot(sets=List(2){CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(8,12),2,45,90)})
            .copy(progressionMethod=SavedProgressionMethod.BODYWEIGHT_REPS)
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(bw))))
            is ProgramProgressionPlan.Refused)
        assertEquals(ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS,
            assignment(cycle(block(slots=listOf(bw.copy(sets=bw.sets.map{
                it.copy(exactRepTarget=9)
            }))))).route)
        // Without a named rep-target mode, ordinary baseline progression
        // still accepts a valid 6–10 range and never invents exact targets.
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            assignment(unset).basicStrategy)
    }

    @Test fun savedMethodBeatsTransientPreviewAndSurvivesBlockDayIdentity() {
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val cycle=cycle(block(slots=listOf(slot(
            sets=List(2){working().copy(exactRepTarget=8)}).copy(
            progressionMethod=SavedProgressionMethod.LADDER))))
        val first=BasicProgramProgressionPlanDomain.review(cycle)
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            first.assignments.single().basicStrategy)
        assertEquals(SavedProgressionMethod.LADDER,
            first.assignments.single().savedMethod)
        assertEquals(SavedProgressionMethod.LADDER,
            BasicProgramProgressionPlanDomain.savedChoice(first.assignments.single()))
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle,selectedStrategies=mapOf(
                key to BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION))
            is ProgramProgressionPlan.Refused)
        // Explicitly changing the authored slot is the only way to select
        // a new progression; an ephemeral call cannot trump disk history.
        val revised=cycle.copy(blocks=listOf(block(
            slots=listOf(slot().copy(
                progressionMethod=SavedProgressionMethod.DOUBLE)))))
        val updated=BasicProgramProgressionPlanDomain.review(revised)
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            updated.assignments.single().basicStrategy)
    }

    @Test fun savedBodyweightAndMachineAssistanceCannotBeSwappedAcrossEquipment() {
        val bw=slot(sets=listOf(CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(8,12),2,45,90,exactRepTarget=8))).copy(
                progressionMethod=SavedProgressionMethod.BODYWEIGHT_REPS)
        val loaded=slot(sets=listOf(working(),working())).copy(
            progressionMethod=SavedProgressionMethod.BODYWEIGHT_REPS)
        val r=BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(bw)))) as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS,
            r.assignments.single().route)
        assertTrue(BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(loaded))))
            is ProgramProgressionPlan.Refused)
        val assistance=slot(sets=listOf(working("40",assisted=true),
            working("40",assisted=true))).copy(
                progressionMethod=SavedProgressionMethod.ASSISTANCE_DOUBLE)
        val a=BasicProgramProgressionPlanDomain.review(
            cycle(block(slots=listOf(assistance))))
            as ProgramProgressionPlan.Ready
        assertEquals(BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            a.assignments.single().basicStrategy)
        assertEquals(SavedProgressionMethod.ASSISTANCE_DOUBLE,
            BasicProgramProgressionPlanDomain.savedChoice(a.assignments.single()))
    }

    @Test fun techniqueFollowUpsNeverCreateNewIndependentLoadProgressions() {
        val amplifier=CustomSetAmplifier(
            CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=4)
        val tracked=slot(sets=listOf(
            working(amplifier=amplifier),working()))
        val review=assignment(cycle(block(slots=listOf(tracked))))
        assertTrue(review.hasAttachedAmplifier)
        assertEquals(2,review.workingSetCount)
        assertEquals(ProgramProgressionRoute.SPECIAL_LOAD_SCHEME,review.route)
        assertNull(review.basicStrategy)
        assertNull(BasicProgramProgressionPlanDomain.savedChoice(review))
        // Even a saved DOUBLE name cannot authorize a normal basic load step
        // while a myo-reps / drop-set technique owns follow-up work.
        val selected=assignment(cycle(block(slots=listOf(
            tracked.copy(progressionMethod=SavedProgressionMethod.DOUBLE)))))
        assertEquals(ProgramProgressionRoute.SPECIAL_LOAD_SCHEME,selected.route)
        assertNull(selected.basicStrategy)
        assertNull(BasicProgramProgressionPlanDomain.savedChoice(selected))
    }

    @Test fun everyGeneratedSlotGetsOneTypedRouteWithStableDayBlockIdentity() {
        val a=block(slots=listOf(slot("a"),slot("b",
            sets=List(2){CustomAuthoredSet(
                DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
                RepBand(8,12),2,40,70)},exerciseId="push-up")))
        val b=block(id="strength",phase=TrainingPhase.STRENGTH,
            slots=listOf(slot("c",role=ComposerRole.PRIMARY)))
        val result=BasicProgramProgressionPlanDomain.review(cycle(a,b))
            as ProgramProgressionPlan.Ready
        assertEquals(3,result.assignments.size)
        assertEquals(3,result.assignments.map{it.key}.distinct().size)
        assertTrue(result.assignments.all{it.explanation.isNotBlank()})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
    }
}
