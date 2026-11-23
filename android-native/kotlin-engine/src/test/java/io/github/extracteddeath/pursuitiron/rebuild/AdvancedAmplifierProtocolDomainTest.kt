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


    @Test fun everyMyoStagePrefillsFromLastLoggedActivationNotFirstSetOrIssuedWeight() {
        val issued=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            java.math.BigDecimal("215"))
        // Earlier working sets could be 200, 205 or 210; the technique
        // attaches to the last working set, which the athlete performed
        // at 195 despite a different issued target of 215.
        val lastActual=KotlinPerformedSet(java.math.BigDecimal("195.0"),
            LoadUnit.LB,15,1,"machine-b")
        val myo=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
            4,20,followUpRounds=5)
        for(stage in 1..5) {
            val decision=AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                myo,issued,lastActual,"machine-b",stage)
            assertTrue(decision is AmplifierFollowUpEntryLoad.RecordedActivation)
            assertEquals(0,(decision as
                AmplifierFollowUpEntryLoad.RecordedActivation)
                .performed.load!!.compareTo(java.math.BigDecimal("195")))
            assertEquals(lastActual,decision.performed)
            assertEquals(issued,AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                myo,issued,stage))
        }
        // Neither the first set nor the scripted target is an actual fact.
        assertNotEquals(java.math.BigDecimal("200"),
            lastActual.load)
        assertNotEquals((issued as DraftSetLoad.Verified).value,
            lastActual.load)
    }

    @Test fun activationLoadResolutionIsOneSourceAcrossSingleAndStagedProtocols() {
        val issued=DraftSetLoad.Verified(LoadUnit.KG,LoadBasis.DUMBBELL_PER_HAND,
            java.math.BigDecimal("25"))
        val logged=KotlinPerformedSet(java.math.BigDecimal("22"),
            LoadUnit.KG,14,1)
        val methods=listOf(CustomAmplifierKind.MYO_REPS,
            CustomAmplifierKind.REST_PAUSE,CustomAmplifierKind.CLUSTER,
            CustomAmplifierKind.LENGTHENED_PARTIALS)
        methods.forEach {kind->
            val roundOptions=when(kind) {
                CustomAmplifierKind.REST_PAUSE -> listOf(1,3)
                CustomAmplifierKind.LENGTHENED_PARTIALS -> listOf(1)
                else -> listOf(1,4)
            }
            for(rounds in roundOptions) {
                val amp=CustomSetAmplifier(kind,4,
                    if(kind==CustomAmplifierKind.LENGTHENED_PARTIALS)0 else 20,
                    followUpRounds=rounds)
                val result=AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                    amp,issued,logged,null,1)
                assertEquals(kind.name,AmplifierFollowUpEntryLoad.RecordedActivation(
                    logged),result)
            }
        }
    }

    @Test fun incompatibleActivationAndMachineNeverFallbackToEarlierSet() {
        val issued=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            java.math.BigDecimal("185"))
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
            4,20,followUpRounds=4)
        val valid=KotlinPerformedSet(java.math.BigDecimal("175"),
            LoadUnit.LB,13,1,"station-a")
        val unavailable=AmplifierFollowUpEntryLoad.ActivationUnavailable
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,null,"station-a",1))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,valid,"station-b",1))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,valid.copy(unit=LoadUnit.KG),"station-a",1))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,valid.copy(reps=0),"station-a",1))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,valid.copy(load=null),"station-a",1))
    }

    @Test fun singleDropEditorUsesOnlyVerifiedReductionFromLastActualSet() {
        fun weight(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.SMITH_TOTAL,java.math.BigDecimal(n))
        val issued=weight("200")
        val drop=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=weight("150"))
        fun entry(actual:String,station:String?="machine-A",
            unit:LoadUnit=LoadUnit.LB,selected:String?="machine-A")=
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                drop,issued,KotlinPerformedSet(java.math.BigDecimal(actual),
                    unit,12,1,station),selected,1)
        val unavailable=AmplifierFollowUpEntryLoad.ActivationUnavailable
        assertEquals(unavailable,entry("135"))
        assertEquals(unavailable,entry("150"))
        assertEquals(unavailable,entry("150.00"))
        assertEquals(unavailable,entry("175",selected="machine-B"))
        assertEquals(unavailable,entry("175",unit=LoadUnit.KG))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                drop,issued,null,"machine-A",1))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                drop,issued,KotlinPerformedSet(java.math.BigDecimal("175"),
                    LoadUnit.LB,12,1,"machine-A"),"machine-A",2))
        assertEquals(AmplifierFollowUpEntryLoad.AuthoredStage(weight("150")),
            entry("175"))
        // Issued load (200) is not used to pretend the real 135 lb
        // activation allows a heavier 150 lb "drop".
        assertEquals(issued,weight("200"))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                drop.copy(followUpLoad=weight("200")),issued,
                KotlinPerformedSet(java.math.BigDecimal("205"),
                    LoadUnit.LB,12,1,"machine-A"),"machine-A",1))
    }

    @Test fun onlyDropsHaveIndependentAuthoredLoadWhileBodyweightFollowsActual() {
        val issued=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            java.math.BigDecimal("185"))
        val logged=KotlinPerformedSet(java.math.BigDecimal("175"),
            LoadUnit.LB,15,1)
        val reduced=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            java.math.BigDecimal("145"))
        val drop=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            8,10,followUpLoad=reduced)
        assertEquals(AmplifierFollowUpEntryLoad.AuthoredStage(reduced),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                drop,issued,logged,null,1))
        val invalidMyo=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
            4,20,followUpLoad=reduced,followUpRounds=1)
        assertFalse(AdvancedAmplifierProtocolDomain.validStagedFollowUps(invalidMyo))
        assertEquals(AmplifierFollowUpEntryLoad.ActivationUnavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                invalidMyo,issued,logged,null,1))
        // Even an old encoded one-stage same-load recipe cannot turn
        // its independent number into a new legitimate myo weight.
        assertEquals(issued,AdvancedAmplifierProtocolDomain.followUpLoadForStage(
            invalidMyo,issued,1))
        val bodyweight=KotlinPerformedSet(null,null,15,1)
        assertEquals(AmplifierFollowUpEntryLoad.RecordedActivation(bodyweight),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
                    4,20,followUpRounds=3),
                DraftSetLoad.ExplicitBodyweight,bodyweight,null,2))
    }
    @Test fun doubleDropEditorAndExecutionAnchorStageTwoToActualFirstReduction() {
        fun load(s:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.MACHINE_STACK,java.math.BigDecimal(s))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=load("75"),followUpRounds=2,
            secondDropLoad=load("55"))
        val activation=KotlinPerformedSet(java.math.BigDecimal("95"),
            LoadUnit.LB,12,1,"machine-A")
        val first=KotlinPerformedSet(java.math.BigDecimal("75.000"),
            LoadUnit.LB,9,1,"machine-A")
        val unavailable=AmplifierFollowUpEntryLoad.ActivationUnavailable
        val firstEntry=AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
            amp,load("100"),activation,"machine-A",1)
        assertEquals(AmplifierFollowUpEntryLoad.AuthoredStage(load("75")),firstEntry)
        // The activation was heavier than 55, but it cannot stand in for
        // an absent or changed preceding physical weight drop.
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,
                first.copy(load=java.math.BigDecimal("50"))))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,
                first.copy(load=java.math.BigDecimal("70"))))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,
                first.copy(machineInstanceId="machine-B")))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-B",2,first))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,
                first.copy(unit=LoadUnit.KG)))
        assertEquals(unavailable,
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,
                first.copy(reps=0)))
        assertEquals(AmplifierFollowUpEntryLoad.AuthoredStage(load("55")),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-A",2,first))
    }


    @Test fun oneDropStageAuthorityOwnsLiveReceiptsAndSavedCorrections() {
        fun load(n:String)=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            java.math.BigDecimal(n))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
            8,10,followUpLoad=load("75"),followUpRounds=2,
            secondDropLoad=load("55"))
        val activation=KotlinPerformedSet(
            java.math.BigDecimal("95"),LoadUnit.LB,12,2,"machine-a")
        val first=KotlinPerformedSet(
            java.math.BigDecimal("75.000"),LoadUnit.LB,8,2,"machine-a")
        val second=KotlinPerformedSet(
            java.math.BigDecimal("60"),LoadUnit.LB,8,2,"machine-a")
        fun accepted(stage:Int,preceding:KotlinPerformedSet?,
            performed:KotlinPerformedSet?,
            activationActual:KotlinPerformedSet?=activation)=
            AdvancedAmplifierProtocolDomain.validPerformedDropStage(
                amp,load("100"),activationActual,preceding,performed,stage)
        assertTrue(accepted(1,null,first))
        assertTrue(accepted(2,first,second))
        // A physical first drop may differ from the scripted target, but
        // such a deviation NEVER licenses the independently authored next.
        assertTrue(accepted(1,null,
            first.copy(load=java.math.BigDecimal("70"))))
        assertFalse(accepted(2,
            first.copy(load=java.math.BigDecimal("70")),second))
        assertFalse(accepted(2,null,second))
        assertFalse(accepted(1,null,
            first.copy(load=java.math.BigDecimal("95"))))
        assertFalse(accepted(1,null,
            first.copy(load=java.math.BigDecimal("96"))))
        assertFalse(accepted(2,first,
            second.copy(load=java.math.BigDecimal("75"))))
        assertFalse(accepted(2,first,
            second.copy(load=java.math.BigDecimal("76"))))
        assertFalse(accepted(2,first,
            second.copy(load=java.math.BigDecimal.ZERO)))
        assertFalse(accepted(2,first,
            second.copy(machineInstanceId="machine-b")))
        assertFalse(accepted(2,first,
            second.copy(unit=LoadUnit.KG)))
        assertFalse(accepted(2,
            first.copy(machineInstanceId="machine-b"),second))
        assertFalse(accepted(2,
            first.copy(unit=LoadUnit.KG),second))
        assertFalse(accepted(2,
            first.copy(reps=0),second))
        assertFalse(accepted(2,first,second,
            activation.copy(load=java.math.BigDecimal("70"))))
        assertFalse(accepted(2,first,second,
            activation.copy(machineInstanceId="machine-b")))
        assertFalse(accepted(2,first,second,
            activation.copy(unit=LoadUnit.KG)))
        assertFalse(accepted(2,first,second.copy(reps=0)))
        // A physically impossible or corrupt authored plan refuses all
        // attempts, even if the athlete's two actual loads descend.
        val malformed=amp.copy(secondDropLoad=load("80"))
        assertFalse(AdvancedAmplifierProtocolDomain.validPerformedDropStage(
            malformed,load("100"),activation,first,second,2))
        assertEquals(AmplifierFollowUpEntryLoad.AuthoredStage(load("55")),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,load("100"),activation,"machine-a",2,first))
    }


    @Test fun sameLoadAmplifiersReuseCompletedSetAuthorityInsteadOfLooseNumericChecks() {
        fun n(value:String)=java.math.BigDecimal(value)
        val issued=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("100"))
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,followUpRounds=3)
        val actual=KotlinPerformedSet(n("95"),LoadUnit.LB,12,2,"machine-a")
        val expected=AmplifierFollowUpEntryLoad.ActivationUnavailable
        assertEquals(AmplifierFollowUpEntryLoad.RecordedActivation(actual),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,issued,actual,"machine-a",1))
        val malformed=listOf(
            actual.copy(load=n("-1")),
            actual.copy(load=n("95.0001")),
            actual.copy(load=n("100000.001")),
            actual.copy(reps=101),
            actual.copy(actualRir=11),
            actual.copy(actualRir=-1),
            actual.copy(unit=LoadUnit.KG),
            actual.copy(load=null))
        for(observation in malformed) {
            assertFalse(KotlinSetNumberAuthorityDomain.valid(
                issued,KotlinJournalSetOutcome.COMPLETED,observation))
            assertEquals("Unverified activation must not seed a mini-set: $observation",
                expected,AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                    amp,issued,observation,"machine-a",1))
        }
        assertEquals(expected,AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
            amp,issued,actual,"machine-b",1))
        val bodyweight=KotlinPerformedSet(null,null,10,2)
        assertEquals(AmplifierFollowUpEntryLoad.RecordedActivation(bodyweight),
            AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                amp,DraftSetLoad.ExplicitBodyweight,bodyweight,null,2))
        assertEquals(expected,AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
            amp,DraftSetLoad.ExplicitBodyweight,bodyweight.copy(reps=101),null,2))
    }

    @Test fun oneNumericalAndMachineAuthorityCoversEverySameLoadTechnique() {
        val load=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.MACHINE_STACK,java.math.BigDecimal("50"))
        val actual=KotlinPerformedSet(java.math.BigDecimal("47.50"),
            LoadUnit.LB,13,1,"station-A")
        val kinds=listOf(CustomAmplifierKind.MYO_REPS,
            CustomAmplifierKind.REST_PAUSE,CustomAmplifierKind.CLUSTER,
            CustomAmplifierKind.LENGTHENED_PARTIALS)
        for(kind in kinds) {
            val amp=CustomSetAmplifier(kind,
                if(kind==CustomAmplifierKind.CLUSTER)2 else 4,
                if(kind==CustomAmplifierKind.LENGTHENED_PARTIALS)0 else 20,
                followUpRounds=if(kind==CustomAmplifierKind.LENGTHENED_PARTIALS)1 else 3)
            val same=KotlinPerformedSet(java.math.BigDecimal("47.5"),
                LoadUnit.LB,4,null,"station-A")
            assertTrue(kind.name,AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                amp,load,actual,same,KotlinJournalSetOutcome.COMPLETED))
            for(wrong in listOf(
                same.copy(load=java.math.BigDecimal("30")),
                same.copy(load=java.math.BigDecimal("25")),
                same.copy(load=java.math.BigDecimal("45")),
                same.copy(machineInstanceId="station-B"),
                same.copy(unit=LoadUnit.KG),
                same.copy(load=null))) {
                assertFalse("$kind must reject $wrong",
                    AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                        amp,load,actual,wrong,KotlinJournalSetOutcome.COMPLETED))
            }
            assertTrue(AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                amp,load,actual,same.copy(reps=0),
                KotlinJournalSetOutcome.FAILED_ATTEMPT))
            assertFalse(AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                amp,load,actual,same.copy(reps=0,load=java.math.BigDecimal("25")),
                KotlinJournalSetOutcome.FAILED_ATTEMPT))
        }
        // Different techniques cannot receive automatic same-load semantics.
        val drop=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,6,10,
            followUpLoad=DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.MACHINE_STACK,java.math.BigDecimal("35")))
        assertFalse(AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
            drop,load,actual,actual,KotlinJournalSetOutcome.COMPLETED))
    }

    @Test fun dropStagesRejectInvalidActualsAndMalformedAuthoredWeightPrecision() {
        fun n(value:String)=java.math.BigDecimal(value)
        fun load(value:String)=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,n(value))
        val issued=load("120")
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=load("95"),followUpRounds=2,secondDropLoad=load("75"))
        val activation=KotlinPerformedSet(n("115"),LoadUnit.LB,12,2,"machine-a")
        val first=KotlinPerformedSet(n("95"),LoadUnit.LB,8,2,"machine-a")
        val second=KotlinPerformedSet(n("80"),LoadUnit.LB,8,2,"machine-a")
        fun valid(stage:Int,initial:KotlinPerformedSet?,
            previous:KotlinPerformedSet?,done:KotlinPerformedSet?)=
            AdvancedAmplifierProtocolDomain.validPerformedDropStage(
                amp,issued,initial,previous,done,stage)
        assertTrue(valid(1,activation,null,first))
        assertTrue(valid(2,activation,first,second))
        assertFalse(valid(1,activation.copy(actualRir=11),null,first))
        assertFalse(valid(2,activation,first.copy(actualRir=11),second))
        assertFalse(valid(2,activation,first,second.copy(load=n("80.0001"))))
        assertFalse(valid(2,activation,first,second.copy(reps=101)))
        assertFalse(valid(2,activation,first,second.copy(actualRir=-1)))
        assertFalse(AdvancedAmplifierProtocolDomain.validDropSequence(
            issued,amp.copy(secondDropLoad=load("75.0001"))))
        assertFalse(AdvancedAmplifierProtocolDomain.validDropSequence(
            issued,amp.copy(followUpLoad=load("95.0001"))))
    }
}
