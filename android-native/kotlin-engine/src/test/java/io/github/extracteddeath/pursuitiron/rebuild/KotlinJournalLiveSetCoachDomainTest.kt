package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class KotlinJournalLiveSetCoachDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"program-1",1,"gym-1",1,"catalog-1","coach-1")
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("200"),n("205"),
            n("210"),n("215")))
    private fun target(
        ordinal:Int,load:String="215",reps:RepBand=RepBand(5,8),
        exerciseId:String="bench"
    )=KotlinWorkoutTarget(exerciseId,ComposerRole.PRIMARY,
        ComposerSessionPurpose.STRENGTH_SKILL,
        "$exerciseId:s$ordinal",DraftSetType.WORKING,ordinal,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(load)),
        reps,3,60,120)
    private fun started(sets:List<KotlinWorkoutTarget> =listOf(
        target(0),target(1),target(2,"205"),target(3,"205")
    )):KotlinWorkoutJournalState {
        val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",
            0,0,ComposerSessionPurpose.STRENGTH_SKILL,48,sets)
        return KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("receipt-1","workout-1",source,1))
    }
    private fun record(j:KotlinWorkoutJournalState,set:String,
        reps:Int=2,rir:Int?=0,load:String="215"
    ):KotlinWorkoutJournalState {
        val op=KotlinJournalOperation.RecordSet("event-${j.revision}",
            j.revision,set,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(load),LoadUnit.LB,reps,rir))
        return (KotlinWorkoutJournalDomain.reduce(j,op)
            as KotlinJournalDecision.Proposed).state
    }
    private fun look(
        j:KotlinWorkoutJournalState,
        set:String,
        gym:VerifiedLoadStock?=stock
    )=KotlinJournalLiveSetCoachDomain.review(
        j,set,gym,CoachReadiness.NORMAL,false,n("5"))

    @Test fun completedTopSetRecognizesAlreadyPlannedLighterBackoff() {
        val j=record(started(),"bench:s0")
        val first=look(j,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            first.coaching.direction)
        assertEquals(n("210"),first.coaching.weightForHumanReview)
        val planned=look(j,"bench:s2") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            planned.coaching.direction)
        val j2=record(j,"bench:s1",2,0,"215")
        val coached=look(j2,"bench:s2") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.FOLLOW_PLANNED_BACKOFF,
            coached.coaching.direction)
        assertNull(coached.coaching.weightForHumanReview)
        assertEquals(n("205"),coached.coaching.issuedTargetUnchanged)
        assertFalse(coached.canIssueWorkout)
        assertFalse(coached.canModifyWorkout)
        assertEquals(n("205"),
            (j2.snapshot.targets[2].load as DraftSetLoad.Verified).value)
    }

    @Test fun onlyDurableEffectiveCorrectionMayInfluenceNextCoaching() {
        val first=record(started(),"bench:s0")
        val prior=look(first,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            prior.coaching.direction)
        val corrected=(KotlinWorkoutJournalDomain.reduce(first,
            KotlinJournalOperation.RecordSet("correction-1",first.revision,
                "bench:s0",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("215"),LoadUnit.LB,6,3),
                correctsEventId="event-0")
        ) as KotlinJournalDecision.Proposed).state
        val next=look(corrected,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            next.coaching.direction)
        assertEquals(corrected.revision,next.journalRevision)
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            (look(first,"bench:s1") as LiveSetCoachResult.Reviewed)
                .coaching.direction)
    }

    @Test fun neverIssueSuggestionsForUnsavedOrMixedRepsOrFinishedWork() {
        val empty=started()
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET),
            look(empty,"bench:s0"))
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET),
            look(empty,"missing"))
        val hetero=started(listOf(target(0),target(1,reps=RepBand(8,12))))
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME),
            look(hetero,"bench:s1"))
        var complete=empty
        for(set in complete.snapshot.targets) {
            val load=(set.load as DraftSetLoad.Verified).value
            complete=record(complete,set.setKey,5,3,load.toPlainString())
        }
        val finished=(KotlinWorkoutJournalDomain.reduce(complete,
            KotlinJournalOperation.Finish("finished",complete.revision))
            as KotlinJournalDecision.Proposed).state
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.FINISHED),
            look(finished,"bench:s2"))
    }

    @Test fun laterRecordedSetBlocksLoadAdviceForEarlierStillOpenSet() {
        val initial=record(started(),"bench:s0",2,0,"215")
        // Journal events are saved by set key, not necessarily in target order.
        // This could happen through skipping ahead or a history correction.
        val outOfOrder=record(initial,"bench:s2",5,2,"205")
        val review=look(outOfOrder,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            review.coaching.direction)
        assertNull(review.coaching.weightForHumanReview)
        assertEquals(n("215"),review.coaching.issuedTargetUnchanged)
        assertFalse(review.canModifyWorkout)
        assertFalse(review.canIssueWorkout)
        // It is still okay to coach an actually next, unrecorded set.
        val last=look(outOfOrder,"bench:s3") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            last.coaching.direction)
    }

    @Test fun laterSkippedSetAlsoBlocksEarlierLoadSuggestion() {
        val initial=record(started(),"bench:s0")
        val skipped=KotlinJournalOperation.RecordSet(
            "skipped-later",initial.revision,"bench:s2",
            KotlinJournalSetOutcome.SKIPPED,null)
        val saved=(KotlinWorkoutJournalDomain.reduce(initial,skipped)
            as KotlinJournalDecision.Proposed).state
        val reviewed=look(saved,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            reviewed.coaching.direction)
        assertNull(reviewed.coaching.weightForHumanReview)
    }

    @Test fun gymMustBeVerifiedBeforeSuggestingAnyWeightChange() {
        val j=record(started(),"bench:s0")
        val suggestion=look(j,"bench:s1",null) as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,
            suggestion.coaching.direction)
        assertNull(suggestion.coaching.weightForHumanReview)
    }

    private fun custom():CustomWorkoutJournalDraft {
        fun slot(id:String,load:String)=CustomWorkoutSlotSnapshot(
            id,"bench","press","smith",ComposerRole.PRIMARY,null,null,
            false,"",(0..2).map {ordinal->
                CustomWorkoutSetSnapshot("$id:s$ordinal",id,ordinal+1,
                    CustomAuthoredSet(DraftSetType.WORKING,
                        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(load)),
                        RepBand(5,8),3,60,120))
            })
        return CustomWorkoutJournalDomain.begin("custom-1",
            CustomWorkoutDaySnapshot(CustomWorkoutSourceStamp(
                "athlete-1","profile-1","cycle-1",1,"gym-1",1),
                1,1,"base",TrainingPhase.STRENGTH,CustomBlockPurpose.TRAINING,
                CustomSplitIdentity.Freeform("My split"),0,"Upper",
                ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(slot("first","215"),slot("second","205")),1080,900),
            "a".repeat(64))
    }
    private fun customRecord(j:CustomWorkoutJournalDraft,key:String,
        reps:Int,load:String,rir:Int?=0,corrects:String?=null
    ):CustomWorkoutJournalDraft = (CustomWorkoutJournalDomain.reduce(j,
        CustomWorkoutJournalOperation.Record("custom-event-${j.revision}",
            j.revision,CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(load),LoadUnit.LB,reps,rir),corrects))
        as CustomWorkoutJournalDecision.Proposed).draft

    @Test fun customLiveAdviceUsesOnlyItsStableSlotAndSavedEffectiveCorrection() {
        val saved=customRecord(custom(),"first:s0",2,"215")
        fun review(j:CustomWorkoutJournalDraft,key:String)=
            KotlinJournalLiveSetCoachDomain.review(j,key,stock,
                CoachReadiness.NORMAL,false,n("5")) as LiveSetCoachResult.Reviewed
        val hard=review(saved,"first:s1")
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,hard.coaching.direction)
        assertEquals(n("210"),hard.coaching.weightForHumanReview)
        // A repeated exercise in another slot has no prior recorded work.
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            review(saved,"second:s1").coaching.direction)
        val corrected=customRecord(saved,"first:s0",6,"215",3,"custom-event-0")
        assertEquals(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            review(corrected,"first:s1").coaching.direction)
        assertEquals(saved.snapshot,corrected.snapshot)
        assertEquals(corrected.revision,review(corrected,"first:s1").journalRevision)
        assertFalse(hard.canModifyWorkout)
        assertFalse(hard.canIssueWorkout)
        val later=customRecord(saved,"first:s2",5,"215",3)
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            review(later,"first:s1").coaching.direction)
    }

    private fun physicalMachines()=KotlinGymMachineAlternatives(
        "gym-1","bench","A",listOf(
            KotlinPhysicalMachine("A","Press A",unit=LoadUnit.LB,
                increment=n("5"),minimum=n("10"),maximum=n("300")),
            KotlinPhysicalMachine("B","Press B",unit=LoadUnit.LB,
                increment=n("10"),minimum=n("10"),maximum=n("300"))))

    @Test fun verifiedComposerStackRequiresSameMachinePriorSetAndCorrectedFacts() {
        fun targetForMachine(index:Int)=target(index,"100").copy(
            load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("100")))
        val base=started((0..2).map(::targetForMachine))
        val aOperation=KotlinJournalOperation.RecordSet(
            "machine-a",base.revision,"bench:s0",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("100"),LoadUnit.LB,5,0,"A"))
        val withA=(KotlinWorkoutJournalDomain.reduce(base,aOperation)
            as KotlinJournalDecision.Proposed).state
        fun stockFor(j:KotlinWorkoutJournalState,machine:String?,
            config:KotlinGymMachineAlternatives?=physicalMachines())=
            KotlinMachineLiveCoachingDomain.confirmedForNext(
                j,"bench:s1",machine,config)
        assertEquals(n("5"),stockFor(withA,"A")?.increment)
        assertNull(stockFor(withA,"B"))
        assertNull(stockFor(withA,null))
        assertNull(stockFor(withA,"A",physicalMachines().copy(gymId="gym-2")))
        assertNull(stockFor(withA,"A",physicalMachines().copy(machines=listOf(
            physicalMachines().machines.first().copy(minimum=null),
            physicalMachines().machines.last()))))
        assertNull(stockFor(withA,"A",physicalMachines().copy(machines=listOf(
            physicalMachines().machines.first().copy(minimum=n("0")),
            physicalMachines().machines.last()))))
        // The issued 100 lb and the recorded 100 lb must be physically
        // attainable on A. An otherwise well-formed 7 lb stack cannot
        // silently borrow an exercise-wide 5 lb increment.
        assertNull(stockFor(withA,"A",physicalMachines().copy(machines=listOf(
            physicalMachines().machines.first().copy(increment=n("7")),
            physicalMachines().machines.last()))))
        assertNull(stockFor(withA,"A",physicalMachines().copy(machines=listOf(
            physicalMachines().machines.first().copy(unit=LoadUnit.KG),
            physicalMachines().machines.last()))))
        assertNull(stockFor(base,"A"))
        val corrected=(KotlinWorkoutJournalDomain.reduce(withA,
            KotlinJournalOperation.RecordSet("machine-b-correction",withA.revision,
                "bench:s0",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("100"),LoadUnit.LB,5,0,"B"),
                correctsEventId="machine-a"))
            as KotlinJournalDecision.Proposed).state
        assertNull(stockFor(corrected,"A"))
        assertEquals(n("10"),stockFor(corrected,"B")?.increment)
        val newReviewed=KotlinJournalLiveSetCoachDomain.review(
            corrected,"bench:s1",stockFor(corrected,"B"),
            CoachReadiness.NORMAL,false,n("10"))
        assertTrue(newReviewed is LiveSetCoachResult.Reviewed)
        assertFalse((newReviewed as LiveSetCoachResult.Reviewed).canModifyWorkout)
    }

    @Test fun verifiedCustomStackUsesOnlyItsSlotAndCorrectedMachine() {
        val old=custom()
        val day=old.originalSnapshot.copy(
            slots=old.originalSnapshot.slots.map {slot->
                slot.copy(sets=slot.sets.map {set->
                    set.copy(prescription=set.prescription.copy(
                        load=DraftSetLoad.Verified(LoadUnit.LB,
                            LoadBasis.MACHINE_STACK,n("100"))))
                })
            })
        val base=CustomWorkoutJournalDomain.begin("custom-physical",day,
            "a".repeat(64))
        val key=CustomWorkoutVisitKey("first:s0",CustomWorkoutVisitPart.MAIN)
        val first=CustomWorkoutJournalOperation.Record(
            "custom-machine-a",base.revision,key,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("100"),LoadUnit.LB,5,0,"A"))
        val withA=(CustomWorkoutJournalDomain.reduce(base,first)
            as CustomWorkoutJournalDecision.Proposed).draft
        fun stockFor(j:CustomWorkoutJournalDraft,key:String,machine:String?)=
            KotlinMachineLiveCoachingDomain.confirmedForNext(
                j,key,machine,physicalMachines())
        assertEquals(n("5"),stockFor(withA,"first:s1","A")?.increment)
        assertNull(stockFor(withA,"first:s1","B"))
        assertNull(stockFor(withA,"second:s1","A"))
        val fixed=(CustomWorkoutJournalDomain.reduce(withA,
            CustomWorkoutJournalOperation.Record(
                "custom-machine-b",withA.revision,key,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("100"),LoadUnit.LB,5,0,"B"),
                correctsEventId="custom-machine-a"))
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(stockFor(fixed,"first:s1","A"))
        assertEquals(n("10"),stockFor(fixed,"first:s1","B")?.increment)
    }

    @Test fun bothJournalKindsRespectFatigueOptOutAndKeepRestrictionBoundary() {
        val regular=record(started(),"bench:s0")
        val authored=customRecord(custom(),"first:s0",2,"215")
        for(policy in listOf(CoachAssistancePolicy.MANUAL,
            CoachAssistancePolicy.PROGRESSION_ONLY,
            CoachAssistancePolicy(true,false,false))) {
            assertEquals(LiveSetCoachResult.Unavailable(
                LiveSetCoachUnavailable.ASSISTANCE_DISABLED),
                KotlinJournalLiveSetCoachDomain.review(regular,"bench:s1",
                    stock,CoachReadiness.LOW,false,n("5"),policy))
            assertEquals(LiveSetCoachResult.Unavailable(
                LiveSetCoachUnavailable.ASSISTANCE_DISABLED),
                KotlinJournalLiveSetCoachDomain.review(authored,"first:s1",
                    stock,CoachReadiness.LOW,false,n("5"),policy))
            val restricted=KotlinJournalLiveSetCoachDomain.review(authored,
                "first:s1",stock,CoachReadiness.LOW,true,n("5"),policy)
                as LiveSetCoachResult.Reviewed
            assertEquals(InWorkoutDirection.REVIEW_RESTRICTION_OR_READINESS,
                restricted.coaching.direction)
            assertNull(restricted.coaching.weightForHumanReview)
        }
    }

    @Test fun customUnknownEffortDoesNotBecomeReportedAndAdvancedSetsNeedTheirOwnModel() {
        val saved=customRecord(custom(),"first:s0",2,"215",null)
        fun review(j:CustomWorkoutJournalDraft)=
            KotlinJournalLiveSetCoachDomain.review(j,"first:s1",stock,
                CoachReadiness.NORMAL,false,n("5"))
        val advice=review(saved) as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,advice.coaching.direction)
        assertFalse(advice.coaching.actualRirKnown)
        assertEquals(CoachConfidence.LIMITED,advice.coaching.confidence)
        val original=saved.snapshot.slots.first()
        for(changed in listOf(original.copy(supersetGroup="paired"),
            original.copy(sets=original.sets.mapIndexed {i,set->
                if(i!=0)set else set.copy(prescription=set.prescription.copy(
                    amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20)))
            }),original.copy(sets=original.sets.mapIndexed {i,set->
                if(i!=1)set else set.copy(prescription=set.prescription.copy(
                    reps=RepBand(8,12)))
            }))) {
            assertEquals(LiveSetCoachResult.Unavailable(
                LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME),
                review(saved.copy(snapshot=saved.snapshot.copy(
                    slots=listOf(changed)+saved.snapshot.slots.drop(1)))))
        }
    }
}
