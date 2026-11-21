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
        sets:List<CustomAuthoredSet>=listOf(working(),working()),
        exerciseId:String="press"
    )=CustomExerciseSlot(id,exerciseId,role,sets)
    private fun block(
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        purpose:CustomBlockPurpose=CustomBlockPurpose.TRAINING,
        slots:List<CustomExerciseSlot>=listOf(slot()),
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

    @Test fun hypertrophyDefaultsToStrictDoubleAndStrengthPrimaryToLinear() {
        val hypertrophy=assignment(cycle(block()))
        assertEquals(ProgramProgressionRoute.BASIC_WEIGHTED,hypertrophy.route)
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            hypertrophy.basicStrategy)
        val strength=assignment(cycle(block(phase=TrainingPhase.STRENGTH,
            slots=listOf(slot(role=ComposerRole.PRIMARY)))))
        assertEquals(BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            strength.basicStrategy)
        assertEquals(2,strength.workingSetCount)
        assertFalse(strength.mayModifyIssuedSet)
        assertFalse(strength.mayChangeSavedProgram)
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

    @Test fun techniqueFollowUpsNeverCreateNewIndependentLoadProgressions() {
        val amplifier=CustomSetAmplifier(
            CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=4)
        val tracked=slot(sets=listOf(
            working(amplifier=amplifier),working()))
        val review=assignment(cycle(block(slots=listOf(tracked))))
        assertTrue(review.hasAttachedAmplifier)
        assertEquals(2,review.workingSetCount)
        assertEquals(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            review.basicStrategy)
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
