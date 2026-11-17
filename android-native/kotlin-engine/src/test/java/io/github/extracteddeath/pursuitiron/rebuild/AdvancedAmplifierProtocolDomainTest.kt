package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class AdvancedAmplifierProtocolDomainTest {
    @Test fun myoTemplateRequiresMultipleShortSameLoadMiniSets() {
        val plan = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.MYO_REPS)
        assertEquals(12..20, plan.activationReps)
        assertEquals(3..5, plan.followUpReps)
        assertEquals(3..5, plan.followUpRounds)
        assertEquals(10..30, plan.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.SAME_LOAD, plan.loadRule)
        assertTrue(plan.oneLine.contains("mini-sets"))
        assertTrue(plan.techniqueCue.contains("Stop"))
    }

    @Test fun underTargetEndsMyoSequenceInsteadOfForcingRemainingRounds() {
        fun step(target: Int, reps: List<Int>, cap: Int = 5, compromised: Boolean = false) =
            AdvancedAmplifierProtocolDomain.nextMyoRepStep(
                target, reps, cap, techniqueCompromised = compromised)
        assertEquals(MyoRepNextStep.KEEP_GOING, step(4, listOf(4, 4), 5))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(4, listOf(4, 4, 3), 5))
        assertEquals(MyoRepNextStep.KEEP_GOING, step(5, listOf(5, 4), 3))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(5, listOf(5, 4, 3), 5))
        assertEquals(MyoRepNextStep.STOP_TECHNIQUE,
            step(4, listOf(4), 5, compromised = true))
    }

    @Test fun rangeBoundaryAndCapAreExplicit() {
        fun step(target: Int, reps: List<Int>, cap: Int = 5, compromised: Boolean = false) =
            AdvancedAmplifierProtocolDomain.nextMyoRepStep(
                target, reps, cap, techniqueCompromised = compromised)
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED, step(3, listOf(3, 3, 3), 3))
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED,
            step(5, listOf(5, 5, 5, 5, 5), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(2, listOf(2), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE,
            step(4, listOf(4, 4, 4, 4, 4, 4), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(-1), 5))
        assertEquals(MyoRepNextStep.KEEP_GOING, step(3, listOf(3, 3), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(4), 6))
    }

    @Test fun restPauseMayBeStagedButNeverSilentlyChangesWeightOrForcesExtraEfforts() {
        val policy=AdvancedAmplifierProtocolDomain
        val staged=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        assertTrue(policy.validStagedFollowUps(staged))
        assertTrue(policy.validStagedFollowUps(staged.copy(followUpRounds=2)))
        // Older single-visit prescriptions still decode and execute identically.
        assertTrue(policy.validStagedFollowUps(staged.copy(followUpRounds=1)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpRounds=4)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpReps=6)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpRestSeconds=10)))
        assertFalse(policy.validStagedFollowUps(staged.copy(
            followUpLoad=DraftSetLoad.ExplicitBodyweight)))
        assertFalse(policy.validStagedFollowUps(
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,3,20,
                followUpRounds=2)))
        assertTrue(policy.validStagedFollowUps(
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,3,20,
                followUpRounds=2)))
        assertTrue(policy.continueRestPause(3,listOf(3),3))
        assertTrue(policy.continueRestPause(3,listOf(3,4),3))
        assertFalse(policy.continueRestPause(3,listOf(3,2),3))
        assertFalse(policy.continueRestPause(3,listOf(3,3,3),3))
        assertFalse(policy.continueRestPause(6,listOf(6),3))
    }

    @Test fun dropsRequireLoadReductionsAndClustersAreNotMyoReps() {
        val drop = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.DROP_SET)
        val cluster = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.CLUSTER)
        assertEquals(AmplifierLoadRule.VERIFIED_REDUCED_LOAD, drop.loadRule)
        assertEquals(1..2, drop.followUpRounds)
        assertTrue(drop.oneLine.contains("weight drops"))
        assertFalse(drop.oneLine.contains("mini-sets"))
        assertTrue(cluster.oneLine.contains("clusters"))
        assertEquals(0..15, drop.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.AUTHORED_LOAD, cluster.loadRule)
        assertTrue(cluster.techniqueCue.contains("unlike myo-reps"))
    }

    @Test fun oneTechniqueVocabularyAcrossPlanPreviewAndStagedWorkout() {
        val policy=AdvancedAmplifierProtocolDomain
        val myo=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,followUpRounds=5)
        val pause=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,followUpRounds=3)
        assertEquals("Myo-reps · mini-set 2 of 5",policy.stageLabel(myo,2))
        assertEquals("Rest-pause · effort 2 of 3",policy.stageLabel(pause,2))
        assertEquals("Rest-pause · follow-up",policy.stageLabel(pause.copy(followUpRounds=1),1))
        assertEquals("efforts",policy.stageNoun(CustomAmplifierKind.REST_PAUSE,true))
        assertEquals("weight drops",policy.stageNoun(CustomAmplifierKind.DROP_SET,true))
        assertEquals("clusters",policy.stageNoun(CustomAmplifierKind.CLUSTER,true))
        assertTrue(policy.stageReviewCue(myo).contains("rep floor"))
        assertTrue(policy.stageReviewCue(pause).contains("rep shortfall"))
        assertFalse(policy.stageReviewCue(pause).contains("myo"))
        assertEquals("End rest-pause early?",policy.stageEarlyStopTitle(pause.kind))
        assertTrue(policy.stageEarlyStopDescription(pause.kind).contains("no later rest-pause efforts"))
    }

    @Test fun advancedTechniquesAreNotMandatoryAndNotPlacedOnEveryStrengthSet() {
        fun eligible(
            kind: CustomAmplifierKind, phase: TrainingPhase,
            experienced: Boolean = true, stable: Boolean = true,
            fatigued: Boolean = false, enabled: Boolean = true,
            used: Boolean = false
        ) = AdvancedAmplifierProtocolDomain.recommendedAsHypertrophyFinisher(
            kind, phase, experienced, stable, fatigued, enabled, used)
        assertTrue(eligible(CustomAmplifierKind.MYO_REPS, TrainingPhase.HYPERTROPHY))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, experienced = false))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, stable = false))
        assertFalse(eligible(CustomAmplifierKind.DROP_SET,
            TrainingPhase.HYPERTROPHY, fatigued = true))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, enabled = false))
        assertFalse(eligible(CustomAmplifierKind.REST_PAUSE,
            TrainingPhase.HYPERTROPHY, used = true))
        assertFalse(eligible(CustomAmplifierKind.DROP_SET, TrainingPhase.STRENGTH))
        assertFalse(eligible(CustomAmplifierKind.CLUSTER, TrainingPhase.HYPERTROPHY))
    }
    @Test fun plannedClustersUseTwoToFiveSameLoadQualityRepStagesNotFailureSets() {
        val policy=AdvancedAmplifierProtocolDomain
        fun cluster(reps:Int=2,rest:Int=30,rounds:Int=3,
            load:DraftSetLoad?=null)=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,
            reps,rest,followUpLoad=load,followUpRounds=rounds)
        for(rounds in 2..5) {
            assertTrue(policy.validStagedFollowUps(cluster(rounds=rounds)))
        }
        assertTrue(policy.validStagedFollowUps(cluster(rounds=1)))
        for(rounds in listOf(0,6)) {
            assertFalse(policy.validStagedFollowUps(cluster(rounds=rounds)))
        }
        assertFalse(policy.validStagedFollowUps(cluster(reps=0)))
        assertFalse(policy.validStagedFollowUps(cluster(reps=5)))
        assertFalse(policy.validStagedFollowUps(cluster(rest=14)))
        assertFalse(policy.validStagedFollowUps(cluster(rest=46)))
        assertFalse(policy.validStagedFollowUps(cluster(
            load=DraftSetLoad.ExplicitBodyweight)))
        assertTrue(policy.continueCluster(2,listOf(2,2),4))
        assertFalse(policy.continueCluster(2,listOf(2,2,2,2),4))
        assertFalse(policy.continueCluster(2,listOf(2,1),4))
        assertFalse(policy.continueCluster(2,listOf(2,3),4))
        assertFalse(policy.continueCluster(0,listOf(1),3))
        assertFalse(policy.continueCluster(2,emptyList(),3))
        val staged=cluster(rounds=4)
        assertEquals("Cluster sets · cluster 2 of 4",policy.stageLabel(staged,2))
        assertTrue(policy.stageReviewCue(staged).contains("quality-rep"))
        assertTrue(policy.stageEarlyStopDescription(staged.kind).contains(
            "no later clusters"))
        assertFalse(policy.recommendedAsHypertrophyFinisher(
            CustomAmplifierKind.CLUSTER,TrainingPhase.HYPERTROPHY,
            true,true,false,true,false))
    }

    @Test fun lengthenedPartialsAreSeparateSameLoadFinalSetExtensionNotAutomaticFullRangeReps() {
        val policy=AdvancedAmplifierProtocolDomain
        val template=policy.template(CustomAmplifierKind.LENGTHENED_PARTIALS)
        assertEquals("Lengthened partials",template.title)
        assertEquals(1..8,template.followUpReps)
        assertEquals(1..1,template.followUpRounds)
        assertEquals(0..0,template.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.SAME_LOAD,template.loadRule)
        assertTrue(template.techniqueCue.contains("SAME load"))
        assertTrue(template.techniqueCue.contains("partials are not full-range"))
        assertTrue(template.selectionCue.contains("not automatic"))
        val original=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,3,0)
        assertTrue(policy.validStagedFollowUps(original))
        assertTrue(policy.validStagedFollowUps(original.copy(followUpReps=1)))
        assertTrue(policy.validStagedFollowUps(original.copy(followUpReps=8)))
        assertFalse(policy.validStagedFollowUps(original.copy(followUpReps=0)))
        assertFalse(policy.validStagedFollowUps(original.copy(followUpReps=9)))
        assertFalse(policy.validStagedFollowUps(original.copy(followUpRestSeconds=1)))
        assertFalse(policy.validStagedFollowUps(original.copy(followUpRounds=2)))
        assertFalse(policy.validStagedFollowUps(original.copy(
            followUpLoad=DraftSetLoad.ExplicitBodyweight)))
        assertEquals("Lengthened partials · final-set extension",
            policy.stageLabel(original,1))
        assertTrue(policy.stageReviewCue(original).contains("final working set") ||
            policy.stageReviewCue(original).contains("Final working set"))
        assertFalse(policy.recommendedAsHypertrophyFinisher(
            CustomAmplifierKind.LENGTHENED_PARTIALS,TrainingPhase.HYPERTROPHY,
            true,true,false,true,false))
    }

    @Test fun twoDropSequencesRequireTwoDistinctVerifiedDescendingWeights() {
        val policy=AdvancedAmplifierProtocolDomain
        fun weight(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.SMITH_TOTAL,java.math.BigDecimal(n))
        val main=weight("185")
        val first=weight("155")
        val second=weight("135")
        val two=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=first,followUpRounds=2,secondDropLoad=second)
        assertTrue(policy.validStagedFollowUps(two))
        assertTrue(policy.validDropSequence(main,two))
        assertEquals(first,policy.dropStageLoad(two,1))
        assertEquals(second,policy.dropStageLoad(two,2))
        assertNull(policy.dropStageLoad(two,3))
        assertEquals(second,policy.followUpLoadForStage(two,main,2))
        assertEquals("Drop set · weight drop 2 of 2",policy.stageLabel(two,2))
        assertFalse(policy.validDropSequence(main,two.copy(secondDropLoad=first)))
        assertFalse(policy.validDropSequence(main,two.copy(secondDropLoad=weight("175"))))
        assertFalse(policy.validDropSequence(main,two.copy(
            secondDropLoad=DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.SMITH_TOTAL,
                java.math.BigDecimal("65")))))
        assertFalse(policy.validDropSequence(main,two.copy(secondDropLoad=null)))
        assertFalse(policy.validStagedFollowUps(two.copy(secondDropLoad=null)))
        assertFalse(policy.validStagedFollowUps(two.copy(followUpRounds=3)))
        assertFalse(policy.validStagedFollowUps(two.copy(followUpRestSeconds=30)))
        assertFalse(policy.validStagedFollowUps(two.copy(followUpReps=16)))
        assertFalse(policy.validStagedFollowUps(two.copy(followUpRounds=1)))
        assertFalse(policy.validStagedFollowUps(CustomSetAmplifier(
            CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5,secondDropLoad=second)))
        val legacy=two.copy(followUpRounds=1,secondDropLoad=null)
        assertTrue(policy.validStagedFollowUps(legacy))
        assertTrue(policy.validDropSequence(main,legacy))
        assertEquals(first,policy.followUpLoadForStage(legacy,main,1))
        assertEquals("Drop set · follow-up",policy.stageLabel(legacy,1))
    }

}
