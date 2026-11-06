package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Native receipt replay -> precise future strength day -> whole cycle audit. */
class EvidenceBackedStrengthWeekLoadDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30")))
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"cycle-1",4,"gym-1",1,"catalog-2","rules-3")

    private fun cycle():CustomTrainingCycle {
        val original=fixture.cycle()
        return original.copy(blocks=original.blocks.map {block->
            block.copy(phase=TrainingPhase.STRENGTH,
                days=block.days.mapIndexed {dayIndex,day->
                    day.copy(
                        purpose=if(dayIndex<2)
                            ComposerSessionPurpose.STRENGTH_SKILL
                        else ComposerSessionPurpose.GENERAL_PREPARATION,
                        slots=day.slots.map {slot->
                            slot.copy(
                                pinned=dayIndex<2 &&
                                    slot.exerciseId=="bench",
                                sets=slot.sets.map {set->
                                    set.copy(reps=RepBand(4,6),targetRir=2,
                                        restSeconds=150)
                                })
                        })
                })
        })
    }

    private fun coaching():CustomCoachAuditInputs {
        val policy=checkNotNull(fixture.generation().policy).copy(
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(
                ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to 1),
            primaryLiftMinimumDays=mapOf("bench" to 2))
        return CustomCoachAuditInputs(
            policiesByPhase=mapOf(TrainingPhase.STRENGTH to policy),
            nonSetSecondsByWeekday=mapOf(0 to 1200,2 to 1200,4 to 1200))
    }

    private fun complete(
        id:Int,actualReps:Int=6,actualRir:Int=2,
        historicalSource:KotlinTrainingSourceRevision=source,
        dayIndex:Int=0
    ):KotlinWorkoutJournalState {
        val workoutId="history-$id"
        val target=KotlinWorkoutTarget(
            "bench",ComposerRole.PRIMARY,
            ComposerSessionPurpose.STRENGTH_SKILL,
            "set-$id",DraftSetType.WORKING,0,
            DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("25")),
            RepBand(4,6),2,90,150)
        val stage=KotlinStagedWorkout(
            historicalSource,"grant-$id",workoutId,dayIndex,dayIndex,
            ComposerSessionPurpose.STRENGTH_SKILL,48,listOf(target))
        var journal=KotlinWorkoutJournalDomain.begin(stage,
            KotlinStoredWorkoutIdentity(
                "stored-$id",workoutId,historicalSource,1))
        journal=(KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.RecordSet(
                "event-$id",journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,
                    actualReps,actualRir)))
            as KotlinJournalDecision.Proposed).state
        return (KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.Finish(
                "finish-$id",journal.revision))
            as KotlinJournalDecision.Proposed).state
    }

    private fun history(
        a:KotlinWorkoutJournalState=complete(1),
        b:KotlinWorkoutJournalState=complete(2)
    )=KotlinProgressionTimelineRequest(
        "athlete-1","bench",10,10,
        listOf(KotlinChronologicalWorkout(1,a),
            KotlinChronologicalWorkout(2,b)),
        stock,n("5"),
        requestedPurpose=ComposerSessionPurpose.STRENGTH_SKILL,
        requestedDayIndex=0)

    private fun attempt(
        cycle:CustomTrainingCycle=cycle(),
        timeline:KotlinProgressionTimelineRequest=history(),
        permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(
            adaptWeekOverrides=true,adaptLoads=true),
        week:Int=2,
        firstUnissued:Int=1,
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,
        assistance:CoachAssistancePolicy=CoachAssistancePolicy.STANDARD,
        carryForward:Boolean=false
    )=EvidenceBackedStrengthWeekLoadDomain.propose(
        EvidenceBackedStrengthWeekLoadRequest(
            AdaptiveCycleOptionRequest(
                cycle,"block-1",fixture.athlete(),fixture.movements,
                coaching(),permissions,emptyList()),
            timeline,week,firstUnissued,0,"old_bench",readiness,
            restricted,assistance=assistance,
            carryForwardThroughBlock=carryForward))

    @Test fun earnedLoadAffectsOneFutureStrengthSlotAndNoOtherWorkout() {
        val before=cycle()
        val initial=CustomCycleCoachAuditDomain.review(
            before,fixture.athlete(),fixture.movements,coaching())
        assertEquals(initial.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            initial.status)
        val result=attempt(before)
        assertEquals(result.explanation,
            EvidenceBackedStrengthWeekLoadStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(n("25"),result.oldLoad)
        assertEquals(n("30"),result.nextLoad)
        assertEquals(listOf(2),result.affectedWeeks)
        assertEquals(listOf("history-1","history-2"),
            result.comparedWorkoutIds)
        val option=checkNotNull(result.proposed)
        val after=option.proposedCycle
        assertEquals(before.blocks,after.blocks)
        assertEquals(before.revision+1,after.revision)
        assertEquals(listOf(9,9,9),
            result.cycleComparison!!.evaluated.single()
                .coachingAudit!!.weeks.map {it.workingSets})
        val structural=checkNotNull(
            result.cycleComparison.evaluated.single()
                .coachingAudit!!.structuralReview)
        fun load(week:Int,weekday:Int,exercise:String):BigDecimal {
            val projected=checkNotNull(
                CustomTrainingCycleDomain.week(structural,week))
            val set=projected.days.single {it.weekday==weekday}
                .slots.single {it.exerciseId==exercise}.sets.single()
            return (set.load as DraftSetLoad.Verified).value
        }
        assertEquals(n("25"),load(1,0,"bench"))
        assertEquals(n("30"),load(2,0,"bench"))
        assertEquals(n("25"),load(3,0,"bench"))
        assertEquals(n("25"),load(2,2,"bench"))
        assertEquals(n("25"),load(2,0,"row"))
        assertEquals(n("25"),load(2,0,"squat"))
        assertEquals(before,cycle())
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterHistory)
        assertEquals(result,attempt(before))
    }

    @Test fun explicitCarryForwardKeepsEarnedLoadWithoutChangingOtherDays() {
        val result=attempt(carryForward=true)
        assertEquals(result.explanation,
            EvidenceBackedStrengthWeekLoadStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(listOf(2,3),result.affectedWeeks)
        val proposed=checkNotNull(result.proposed).proposedCycle
        val override=proposed.weekOverrides.getValue("block-1")
        assertEquals(listOf(2,3),override.map {it.weekInBlock})
        assertTrue(override.all {it.weekday==0 && it.slotId=="old_bench"})
        assertEquals(listOf(9,9,9),
            result.cycleComparison!!.evaluated.single()
                .coachingAudit!!.weeks.map {it.workingSets})
        val structural=checkNotNull(
            result.cycleComparison.evaluated.single()
                .coachingAudit!!.structuralReview)
        for(week in 1..3) for(day in listOf(0,2)) {
            val resolved=checkNotNull(
                CustomTrainingCycleDomain.week(structural,week))
            val load=(resolved.days.single {it.weekday==day}
                .slots.single {it.exerciseId=="bench"}
                .sets.single().load as DraftSetLoad.Verified).value
            assertEquals(if(day==0 && week>=2)n("30") else n("25"),load)
        }
        assertEquals(result,attempt(carryForward=true))
        assertFalse(result.mayApplyAutomatically)
    }

    @Test fun carryForwardDoesNotOverwriteAnyLaterCustomWeek() {
        val original=cycle()
        val later=original.blocks.single().days.single {it.weekday==0}
            .slots.single {it.slotId=="old_bench"}
        val protected=original.copy(weekOverrides=mapOf(
            "block-1" to listOf(
                CustomWeekSlotOverride(3,0,later.slotId,later.sets))))
        val blocked=attempt(cycle=protected,carryForward=true)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.PROTECTED_WEEK,
            blocked.status)
        assertNull(blocked.proposed)
        val single=attempt(cycle=protected,carryForward=false)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.READY_FOR_ATHLETE_REVIEW,
            single.status)
        assertEquals(listOf(2),single.affectedWeeks)
        assertEquals(protected.weekOverrides.getValue("block-1").first(),
            checkNotNull(single.proposed).proposedCycle
                .weekOverrides.getValue("block-1").first())
    }

    @Test fun persistedManualAndProgressionOffModesDoNotOfferChanges() {
        for(mode in listOf(CoachAssistancePolicy.MANUAL,
            CoachAssistancePolicy.STANDARD.copy(
                progressionSuggestionsEnabled=false))) {
            val refused=attempt(assistance=mode)
            assertEquals(EvidenceBackedStrengthWeekLoadStatus.ASSISTANCE_DISABLED,
                refused.status)
            assertNull(refused.proposed)
            assertNull(refused.cycleComparison)
            assertTrue(refused.affectedWeeks.isEmpty())
        }
        // Fatigue off preserves natural progression, not injury exemptions.
        val straightforward=attempt(
            readiness=CoachReadiness.LOW,
            assistance=CoachAssistancePolicy.PROGRESSION_ONLY)
        assertEquals(straightforward.explanation,
            EvidenceBackedStrengthWeekLoadStatus.READY_FOR_ATHLETE_REVIEW,
            straightforward.status)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.RECOVERY_PRIORITY,
            attempt(readiness=CoachReadiness.LOW).status)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.RECOVERY_PRIORITY,
            attempt(assistance=CoachAssistancePolicy.PROGRESSION_ONLY,
                restricted=true).status)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.EVIDENCE_NOT_EARNED,
            attempt(assistance=CoachAssistancePolicy.PROGRESSION_ONLY,
                timeline=history(b=complete(2,actualReps=3,actualRir=0)))
                .status)
    }

    @Test fun failedLatestSetOrMissingEffortCannotEarnWeight() {
        val failed=attempt(timeline=history(
            b=complete(2,actualReps=3,actualRir=0)))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.EVIDENCE_NOT_EARNED,
            failed.status)
        assertNull(failed.proposed)
        val effort=attempt(timeline=history(
            b=complete(2,actualRir=1)))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.EVIDENCE_NOT_EARNED,
            effort.status)
    }

    @Test fun athleteMustPermitBothWeeklyEditAndActualLoadChange() {
        val noLoad=attempt(permissions=CustomCycleCoachPermissions(
            adaptWeekOverrides=true))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.PERMISSION_REQUIRED,
            noLoad.status)
        assertEquals(setOf(CustomCycleChangeKind.LOADS),
            noLoad.cycleComparison!!.evaluated.single()
                .transition!!.blockedKinds)
        val noOverride=attempt(permissions=CustomCycleCoachPermissions(
            adaptLoads=true))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.PERMISSION_REQUIRED,
            noOverride.status)
        assertEquals(setOf(CustomCycleChangeKind.WEEK_OVERRIDES),
            noOverride.cycleComparison!!.evaluated.single()
                .transition!!.blockedKinds)
    }

    @Test fun changedProgramGymOrHistoryRevisionIsNeverReusable() {
        val wrong=attempt(timeline=history(
            b=complete(2,historicalSource=source.copy(
                programRevision=3))))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.STALE_SOURCE_OR_GYM,
            wrong.status)
        val alteredStock=attempt(timeline=history().copy(
            currentVerifiedStock=VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(n("20"),n("25"),n("27.5"),n("30")))))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.STALE_SOURCE_OR_GYM,
            alteredStock.status)
        val stale=attempt(timeline=history().copy(
            currentHistoryRevision=11))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.HISTORY_UNVERIFIED,
            stale.status)
    }

    @Test fun crossDayHistoryAndPastWeekCannotEarnTargetDayChange() {
        val differentDay=attempt(timeline=history().copy(
            requestedDayIndex=2))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.INVALID_SCOPE,
            differentDay.status)
        val alreadyIssued=attempt(week=1,firstUnissued=2)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.INVALID_SCOPE,
            alreadyIssued.status)
    }

    @Test fun originalCustomWeekAndRecoveryHavePriority() {
        val before=cycle()
        val bench=before.blocks.single().days.first()
            .slots.single {it.exerciseId=="bench"}
        val protected=before.copy(weekOverrides=mapOf(
            "block-1" to listOf(CustomWeekSlotOverride(
                2,0,bench.slotId,bench.sets))))
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.PROTECTED_WEEK,
            attempt(cycle=protected).status)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.RECOVERY_PRIORITY,
            attempt(readiness=CoachReadiness.LOW).status)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.RECOVERY_PRIORITY,
            attempt(restricted=true).status)
    }

    @Test fun sourcePlannedLoadMustMatchActualLastIssuedLoad() {
        val before=cycle()
        val changed=before.copy(blocks=before.blocks.map {block->
            block.copy(days=block.days.map {day->
                day.copy(slots=day.slots.map {slot->
                    if(day.weekday==0 && slot.exerciseId=="bench")
                        slot.copy(sets=slot.sets.map {
                            it.copy(load=DraftSetLoad.Verified(
                                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                                n("20")))
                        })
                    else slot
                })
            })
        })
        val refused=attempt(cycle=changed)
        assertEquals(EvidenceBackedStrengthWeekLoadStatus.DIFFERENT_PRESCRIPTION,
            refused.status)
        assertNull(refused.proposed)
    }
}
