package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomBasicHistoryProgressionDomainTest {
    private fun n(value:Int)=BigDecimal(value)
    private fun load(value:Int)=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n(value))
    private fun set(value:Int=100,amp:CustomSetAmplifier?=null)=CustomAuthoredSet(
        DraftSetType.WORKING,load(value),RepBand(6,10),2,45,90,amp)
    private fun cycle(
        method:SavedProgressionMethod=SavedProgressionMethod.DOUBLE,
        overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap()
    ):CustomTrainingCycle {
        val slot=CustomExerciseSlot("main","press",ComposerRole.SECONDARY,
            listOf(set(),set()),progressionMethod=method)
        return CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.HYPERTROPHY,
            "Plan",listOf(CustomTrainingBlock(
                "base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,4,
                CustomSplitIdentity.Freeform("Custom"),
                listOf(CustomTrainingDay(0,"Upper",
                    ComposerSessionPurpose.HYPERTROPHY,listOf(slot)),
                    CustomTrainingDay(2,"Push",
                    ComposerSessionPurpose.HYPERTROPHY,listOf(
                        slot.copy(slotId="push")))))),
            weekOverrides=overrides)
    }
    private fun snapshot(
        week:Int=2,day:Int=0,machine:String?=null,
        loads:List<Int> = listOf(100,100),
        shortened:Boolean=false,amp:CustomSetAmplifier?=null
    ):CustomWorkoutDaySnapshot {
        val slotId=if(day==0)"main" else "push"
        val slot=CustomWorkoutSlotSnapshot(slotId,"press","chest","machine",
            ComposerRole.SECONDARY,null,null,true,"",
            loads.mapIndexed{index,weight->
                CustomWorkoutSetSnapshot(
                    "cycle:w"+week+":d"+day+":"+slotId+":s"+(index+1),
                    slotId,index+1,set(weight,amp))
            },machineInstanceId=machine)
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile-1","cycle",1,
                "gym",1),week,week,"base",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            day,"Day",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),360,180,
            shortSessionLimitMinutes=if(shortened)30 else null)
    }
    private fun completed(
        order:Long,week:Int=1,day:Int=0,machine:String?=null,
        reps:List<Int> = listOf(10,10),
        loads:List<Int> = listOf(100,100),
        failed:Set<Int> = emptySet(),
        shortened:Boolean=false,
        amp:CustomSetAmplifier?=null
    ):CustomCoachingHistoricalSession {
        val daySnapshot=snapshot(week,day,machine,loads,shortened,amp)
        val slot=daySnapshot.slots.single()
        val visits=slot.sets.mapIndexed{index,scheduled->
            val missed=index in failed
            CustomHistoricalVisitFact(
                slot.slotId,slot.exerciseId,scheduled.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                scheduled.prescription.load,scheduled.prescription.reps,2,
                amp?.kind,
                if(missed) KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(loads[index]),LoadUnit.LB,
                    if(missed)0 else reps[index],null,machine),
                "record-"+order+"-"+index)
        }
        val evidence=CustomWorkoutHistoricalEvidence(
            "athlete","workout-"+order,"cycle",1,"finish-"+order,3,visits,
            shortSessionLimitMinutes=if(shortened)30 else null)
        return CustomCoachingHistoricalSession(order,daySnapshot,evidence)
    }
    private fun stock()=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n(95),n(100),n(105),n(110)))
    private fun review(
        next:CustomWorkoutDaySnapshot=snapshot(),
        sessions:List<CustomCoachingHistoricalSession> = listOf(completed(1)),
        program:CustomTrainingCycle=cycle(),
        nextWorkoutId:String="future-2",
        machine:String?=null
    ):CustomBasicHistoryResult =
        CustomBasicHistoryProgressionDomain.review(CustomBasicHistoryRequest(
            program,next,nextWorkoutId,if(next.weekday==0)"main" else "push",
            sessions,machine,stock(),n(5)))

    @Test fun strictDoubleUsesOnlyCompletedSameSlotWorkAndVerifiedPhysicalStep() {
        val actual=review()
        assertTrue(actual.toString(),actual is CustomBasicHistoryResult.Reviewed)
        val result=actual as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",result.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),result.candidate.proposedWorkingLoads)
        assertEquals(setOf(0,1),result.candidate.increasedOrdinals)
        assertEquals(BasicProgressionDecision.EARNED_INCREMENT,
            result.candidate.decision)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
    }

    @Test fun alreadyTrainedWeeksCannotReceiveBasicProgressionFromAnyStation() {
        val prior=completed(1,machine="A")
        for(week in listOf(2,3)) {
            val later=completed(2,week=week,machine="B")
            val otherGym=later.copy(snapshot=later.snapshot.copy(
                stamp=later.snapshot.stamp.copy(gymId="away")))
            for(session in listOf(later,otherGym)) {
                val result=review(next=snapshot(2,machine="A"),machine="A",
                    sessions=listOf(prior,session))
                assertEquals(CustomBasicHistoryRefusal.TARGET_ALREADY_TRAINED,
                    (result as CustomBasicHistoryResult.Rejected).reason)
            }
        }
        val otherDay=completed(2,week=2,day=2,machine="A")
        val result=review(next=snapshot(2,machine="A"),machine="A",
            sessions=listOf(prior,otherDay)) as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",result.historyWorkoutId)
    }

    @Test fun autoUsesTheReviewedMovementToChooseIndependentSetProgression() {
        val movement = GeneratedAdaptiveBlockFixture.generation().catalog.first { it.exerciseId == "bench" }
            .copy(exerciseId = "press")
        val request = CustomBasicHistoryRequest(
            cycle(SavedProgressionMethod.AUTO), snapshot(), "future", "main",
            listOf(completed(1, reps=listOf(10, 7))), null, stock(), n(5),
            reviewedCatalog = mapOf("press" to movement))
        val reviewed = CustomBasicHistoryProgressionDomain.review(request) as CustomBasicHistoryResult.Reviewed
        assertEquals(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION, reviewed.candidate.strategy)
        assertEquals(listOf(n(105), n(100)), reviewed.candidate.proposedWorkingLoads)

        val explicit = CustomBasicHistoryProgressionDomain.review(
            request.copy(cycle=cycle(SavedProgressionMethod.DOUBLE))) as CustomBasicHistoryResult.Reviewed
        assertEquals(listOf(n(100), n(100)), explicit.candidate.proposedWorkingLoads)
    }

    @Test fun dynamicProgressionNeverLevelsAHeavierSetAcrossTheWholeExercise() {
        val history=completed(1,reps=listOf(10,5))
        val result=review(program=cycle(SavedProgressionMethod.DYNAMIC),
            sessions=listOf(history))
        assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
        val candidate=(result as CustomBasicHistoryResult.Reviewed).candidate
        assertEquals(listOf(n(105),n(100)),candidate.proposedWorkingLoads)
        assertEquals(setOf(0),candidate.increasedOrdinals)
    }

    @Test fun manualLoggingHasNoHistoryDependencyOrHiddenLoadSuggestion() {
        val result=review(program=cycle(SavedProgressionMethod.MANUAL),
            sessions=emptyList())
        assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
        val ready=result as CustomBasicHistoryResult.Reviewed
        assertNull(ready.historyWorkoutId)
        assertNull(ready.completionOrder)
        assertEquals(BasicProgressionDecision.MANUAL_REPEAT,
            ready.candidate.decision)
        assertEquals(listOf(n(100),n(100)),
            ready.candidate.proposedWorkingLoads)
        assertTrue(ready.candidate.increasedOrdinals.isEmpty())
    }

    @Test fun manualPreviewUsesEachAuthoredSetWithoutRequiringComparableWork() {
        val amplifier = CustomSetAmplifier(CustomAmplifierKind.MYO_REPS, 4, 20, followUpRounds=4)
        for (bodyweight in listOf(true, false)) {
            val sets = listOf(
                set().copy(reps=RepBand(6, 8), exactRepTarget=7),
                set(95, amplifier).copy(reps=RepBand(10, 15))
            ).map { if (bodyweight) it.copy(load=DraftSetLoad.ExplicitBodyweight) else it }
            val week = CustomWeekSlotOverride(2, 0, "main", sets)
            val program = cycle(SavedProgressionMethod.MANUAL, mapOf("base" to listOf(week)))
            val day = snapshot()
            val slot = day.slots.single()
            val next = day.copy(slots=listOf(slot.copy(sets=slot.sets.zip(sets) { row, set ->
                row.copy(prescription=set)
            })))
            val result = review(next=next, program=program, sessions=emptyList())
            if (bodyweight) {
                assertEquals(CustomBasicHistoryResult.ManualBodyweight("main"), result)
                assertEquals(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,
                    (review(next=next, program=program, machine="A") as CustomBasicHistoryResult.Rejected).reason)
            } else {
                val weighted = result as CustomBasicHistoryResult.Reviewed
                assertEquals(listOf(n(100), n(95)), weighted.candidate.proposedWorkingLoads)
                assertEquals(BasicProgressionDecision.MANUAL_REPEAT, weighted.candidate.decision)
            }
            assertEquals(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,
                (review(next=day, program=program) as CustomBasicHistoryResult.Rejected).reason)
        }
    }

    @Test fun newestFailedSameStationBlocksOlderSuccessRatherThanSkippingBack() {
        val history=listOf(completed(1),completed(2,week=2,
            reps=listOf(10,10),failed=setOf(1)))
        val result=review(next=snapshot(3),sessions=history)
        assertEquals(CustomBasicHistoryResult.Rejected(
            CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS),result)
    }

    @Test fun differentPhysicalMachinesHaveIndependentEarnedLoadHistories() {
        val earlier=completed(1,week=1,machine="A")
        val newer=completed(2,week=2,machine="B",
            reps=listOf(5,5))
        val a=review(next=snapshot(3,machine="A"),
            sessions=listOf(earlier,newer),machine="A")
        assertTrue(a.toString(),a is CustomBasicHistoryResult.Reviewed)
        assertEquals("workout-1",
            (a as CustomBasicHistoryResult.Reviewed).historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),a.candidate.proposedWorkingLoads)
        // The saved default was A, but the athlete really switched to
        // B. Physical actuals, not the former default, isolate the evidence.
        val alternate=newer.copy(snapshot=
            newer.snapshot.copy(slots=newer.snapshot.slots.map{
                it.copy(machineInstanceId="A")
            }))
        val aAfterSwitch=review(next=snapshot(3,machine="A"),
            sessions=listOf(earlier,alternate),machine="A")
        assertTrue(aAfterSwitch.toString(),
            aAfterSwitch is CustomBasicHistoryResult.Reviewed)
        assertEquals("workout-1",
            (aAfterSwitch as CustomBasicHistoryResult.Reviewed).historyWorkoutId)
        val b=review(next=snapshot(3,machine="B"),
            sessions=listOf(earlier,newer),machine="B")
        assertTrue(b.toString(),b is CustomBasicHistoryResult.Reviewed)
        assertEquals(BasicProgressionDecision.BUILD_REPS,
            (b as CustomBasicHistoryResult.Reviewed).candidate.decision)
        assertEquals(listOf(n(100),n(100)),b.candidate.proposedWorkingLoads)
        assertEquals(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY,
            (review(next=snapshot(3),
                sessions=listOf(earlier,newer)) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun alternateMachineWithSameSlotRequiresEveryMatchingCompletedWorkingReceipt() {
        val a=completed(1,week=1,machine="A")
        val fullB=completed(2,week=2,machine="B")
        fun onA(newer:CustomCoachingHistoricalSession)=review(
            next=snapshot(3,machine="A"),machine="A",
            sessions=listOf(a,newer))
        val independent=onA(fullB) as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",independent.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),independent.candidate.proposedWorkingLoads)

        val observed=fullB.evidence.visits
        val incomplete=fullB.copy(evidence=fullB.evidence.copy(
            visits=observed.dropLast(1)))
        val duplicate=fullB.copy(evidence=fullB.evidence.copy(
            visits=listOf(observed.first(),observed.first())))
        val changedRepBand=fullB.copy(evidence=fullB.evidence.copy(
            visits=observed.mapIndexed { index,fact ->
                if(index==0) fact.copy(prescribedReps=RepBand(5,10)) else fact
            }))
        val changedLoad=fullB.copy(evidence=fullB.evidence.copy(
            visits=observed.mapIndexed { index,fact ->
                if(index==0)fact.copy(prescribedLoad=load(95)) else fact
            }))
        val uncompleted=fullB.copy(evidence=fullB.evidence.copy(
            visits=observed.mapIndexed { index,fact ->
                if(index==1)fact.copy(outcome=KotlinJournalSetOutcome.FAILED_ATTEMPT)
                else fact
            }))
        val wrongStage=fullB.copy(evidence=fullB.evidence.copy(
            visits=observed.mapIndexed { index,fact ->
                if(index==0)fact.copy(stage=2) else fact
            }))
        // A physically independent B cannot hide a changed coaching source
        // between two otherwise comparable Machine A sessions.
        val sourceDrift=fullB.copy(snapshot=fullB.snapshot.copy(
            stamp=fullB.snapshot.stamp.copy(
                athleteProfileRevision="new-profile-review")))
        for (invalid in listOf(incomplete,duplicate,changedRepBand,
            changedLoad,uncompleted,wrongStage)) {
            val result=onA(invalid)
            assertEquals(result.toString(),
                CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
                (result as CustomBasicHistoryResult.Rejected).reason)
        }
        val changedSource=onA(sourceDrift)
        assertEquals(changedSource.toString(),
            CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED,
            (changedSource as CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun issuedMachineWithNoActualStationNeverBecomesGenericOrOtherStationEvidence() {
        val unverified=completed(2,week=2,machine="B").let { session ->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map { visit ->
                    visit.copy(actual=visit.actual?.copy(machineInstanceId=null))
                }))
        }
        // Even a recorded B prescription is not proof that B was used.
        // Nor can its unverified actuals be credited to generic equipment.
        for(machine in listOf("B",null)) {
            val result=review(next=snapshot(3,machine=machine),
                sessions=listOf(unverified),machine=machine)
            assertEquals(machine.toString(),
                CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
                (result as CustomBasicHistoryResult.Rejected).reason)
        }
        // A more recent unknown station must block the older verified A
        // success instead of silently earning another A load increment.
        val priorA=completed(1,machine="A")
        val result=review(next=snapshot(3,machine="A"),
            sessions=listOf(priorA,unverified),machine="A")
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (result as CustomBasicHistoryResult.Rejected).reason)

        // Mixed known/unknown actual station within the latest visit cannot
        // be skipped as if all sets were verified on B. That would let the
        // older A workout earn an unapproved increase.
        val mixed=completed(3,week=3,machine="B").let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.mapIndexed{index,visit->
                    if(index==1) visit.copy(actual=visit.actual?.copy(
                        machineInstanceId=null)) else visit
                }))
        }
        val mixedReview=review(next=snapshot(4,machine="A"),
            sessions=listOf(priorA,mixed),machine="A")
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (mixedReview as CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun newerVerifiedStationSupersedesAnOlderAmbiguousVisit() {
        val ambiguous = completed(1, machine="A").let { visit ->
            visit.copy(evidence=visit.evidence.copy(visits=visit.evidence.visits.map {
                it.copy(actual=it.actual?.copy(machineInstanceId=null))
            }))
        }
        val verified = completed(2, week=2, machine="A")
        val result = review(next=snapshot(3, machine="A"), machine="A",
            sessions=listOf(ambiguous, verified)) as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-2", result.historyWorkoutId)
        assertEquals(listOf(n(105), n(105)), result.candidate.proposedWorkingLoads)
    }

    @Test fun consistentlyLabeledOtherAthleteCannotSupplyTheBaseline() {
        val other = completed(1).let { visit ->
            visit.copy(snapshot=visit.snapshot.copy(stamp=visit.snapshot.stamp.copy(
                athleteId="another-athlete")), evidence=visit.evidence.copy(athleteId="another-athlete"))
        }
        val result = review(sessions=listOf(other)) as CustomBasicHistoryResult.Rejected
        assertEquals(CustomBasicHistoryRefusal.INVALID_HISTORY, result.reason)
    }

    @Test fun programDayAndAuthoredWeekOverridesCannotBorrowOtherNumericalOwners() {
        assertEquals(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY,
            (review(next=snapshot(2,day=2)) as
                CustomBasicHistoryResult.Rejected).reason)
        val authored=CustomWeekSlotOverride(2,0,"main",
            listOf(set(105),set(105)))
        assertEquals(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD,
            (review(program=cycle(overrides=mapOf("base" to listOf(authored))),
                next=snapshot(2,loads=listOf(105,105))) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun savedDoubleProgressionAppliesOutsideItsWeekOverride() {
        val override = CustomWeekSlotOverride(3, 0, "main", listOf(set(105), set(105)))
        val program = cycle(overrides = mapOf("base" to listOf(override)))
        val earlier = review(program = program, next = snapshot(2)) as CustomBasicHistoryResult.Reviewed
        assertEquals(listOf(n(105), n(105)), earlier.candidate.proposedWorkingLoads)

        val editedWeek = review(program = program, next = snapshot(3, loads = listOf(105, 105)))
        assertEquals(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD,
            (editedWeek as CustomBasicHistoryResult.Rejected).reason)

        // Returning to 100 after the authored 105 week needs a comparable baseline.
        val later = review(program = program, next = snapshot(4),
            sessions = listOf(completed(1), completed(2, week = 3, loads = listOf(105, 105))))
        assertEquals(BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY,
            (later as CustomBasicHistoryResult.Rejected).evaluatorReason)
    }

    @Test fun waveKeepsItsAuthoredFirstWeekAndRejectsIncompleteSchedules() {
        val overrides = (2..4).map { CustomWeekSlotOverride(it, 0, "main", listOf(set(), set())) }
        val source = cycle(overrides = mapOf("base" to overrides))
        val block = source.blocks.single()
        val wave = source.copy(blocks = listOf(block.copy(days = block.days.map { day ->
            day.copy(slots = day.slots.map { slot -> if (slot.slotId == "main")
                slot.copy(progressionMethod = SavedProgressionMethod.WAVE) else slot })
        })))
        val week = ProgramProgressionWeek("base", 1)
        val plan = BasicProgramProgressionPlanDomain.review(wave, previewWeek = week)
            as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,
            plan.assignments.single { it.key.slotId == "main" }.route)
        assertTrue(BasicProgramProgressionPlanDomain.review(wave.copy(
            weekOverrides = mapOf("base" to overrides.dropLast(1))), previewWeek = week)
            is ProgramProgressionPlan.Refused)
        assertTrue(BasicProgramProgressionPlanDomain.review(wave,
            previewWeek = week.copy(weekInBlock = 5)) is ProgramProgressionPlan.Refused)
    }

    @Test fun sourceExactTargetsAdvanceAndLadderResetsAtTrueBandFloor() {
        fun planned(method:SavedProgressionMethod,target:Int):CustomTrainingCycle {
            val base=cycle(method)
            return base.copy(blocks=base.blocks.map{block ->
                block.copy(days=block.days.map{day ->
                    day.copy(slots=day.slots.map{slot ->
                        slot.copy(sets=slot.sets.map{
                            it.copy(exactRepTarget=target)
                        })
                    })
                })
            })
        }
        fun issued(target:Int)=snapshot().let{next ->
            next.copy(slots=next.slots.map{slot ->
                slot.copy(sets=slot.sets.map{set ->
                    set.copy(prescription=set.prescription.copy(exactRepTarget=target))
                })
            })
        }
        for(method in listOf(SavedProgressionMethod.REP_TARGETS,
            SavedProgressionMethod.LADDER)) {
            val result=review(next=issued(8),program=planned(method,8),
                sessions=listOf(completed(1,reps=listOf(8,8))))
            assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
            val candidate=(result as CustomBasicHistoryResult.Reviewed).candidate
            assertEquals(listOf(n(100),n(100)),candidate.proposedWorkingLoads)
            assertEquals(listOf(9,9),candidate.proposedRepTargets)
        }
        val result=review(next=issued(10),
            program=planned(SavedProgressionMethod.LADDER,10),
            sessions=listOf(completed(1,reps=listOf(10,10))))
        assertTrue(result.toString(),result is CustomBasicHistoryResult.Reviewed)
        val candidate=(result as CustomBasicHistoryResult.Reviewed).candidate
        assertEquals(listOf(n(105),n(105)),candidate.proposedWorkingLoads)
        assertEquals(listOf(6,6),candidate.proposedRepTargets)
    }

    @Test fun exactRepOnlyRoutesNeverDeriveExactTargetsFromARepBand() {
        assertEquals(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE,
            (review(program=cycle(SavedProgressionMethod.REP_TARGETS)) as
                CustomBasicHistoryResult.Rejected).reason)
        assertEquals(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE,
            (review(program=cycle(SavedProgressionMethod.LADDER)) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun swappedExerciseOrChangedAttachmentCannotEarnOriginalExerciseProgression() {
        val swap=completed(1).let {session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map{
                    it.copy(exerciseId="swapped-press")
                }))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(swap)) as
                CustomBasicHistoryResult.Rejected).reason)

        val alternateAttachment=completed(1).let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{it.copy(attachmentId="rope")}))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(alternateAttachment)) as
                CustomBasicHistoryResult.Rejected).reason)

        val alternateVariant=completed(1).let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{it.copy(variantId="different-angle")}))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(alternateVariant)) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun latestSourceIdentityMatrixKeepsStationVariantsAndActualSwapsIndependent() {
        // Same cycle and stable slot: the older A visit really qualified.
        val previousA=completed(1,week=1,machine="A")
        val latestB=completed(2,week=2,machine="B").let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{it.copy(variantId="alternate-angle")}))
        }
        fun nextA(sessions:List<CustomCoachingHistoricalSession>)=
            review(next=snapshot(3,machine="A"),sessions=sessions,machine="A")

        // A fully verified different physical B station is independent.
        // Its different exercise variant cannot erase previously earned A
        // progression when the athlete returns to the original A station.
        val verifiedB=nextA(listOf(previousA,latestB))
        assertTrue(verifiedB.toString(),verifiedB is CustomBasicHistoryResult.Reviewed)
        val earned=verifiedB as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",earned.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),earned.candidate.proposedWorkingLoads)

        // If even one of B's actual stations is unknown, skipping that visit
        // and promoting A's older success would be unsafe.
        val partlyUnknownB=latestB.copy(evidence=latestB.evidence.copy(visits=
            latestB.evidence.visits.mapIndexed{index,visit->
                if(index==1)visit.copy(actual=visit.actual?.copy(
                    machineInstanceId=null)) else visit
            }))
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (nextA(listOf(previousA,partlyUnknownB)) as
                CustomBasicHistoryResult.Rejected).reason)

        // A different completed exercise within the original A slot is not
        // the same exercise; a new attachment on A has its own load evidence.
        val actualSwapA=completed(2,week=2,machine="A").let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map{
                    it.copy(exerciseId="substitute-press")
                }))
        }
        val differentAttachmentA=completed(2,week=2,machine="A").let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{it.copy(attachmentId="rope")}))
        }
        for(recent in listOf(actualSwapA,differentAttachmentA)) {
            assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
                (nextA(listOf(previousA,recent)) as
                    CustomBasicHistoryResult.Rejected).reason)
        }
    }

    @Test fun latestRealSubstitutionOnTheSameStationBlocksAnOlderEarnedLoad() {
        val previousA=completed(1,week=1,machine="A")
        fun substituted(machine:String):CustomCoachingHistoricalSession {
            val prior=completed(2,week=2,machine=machine)
            // Both the ISSUED slot and the independently saved observation
            // identify a different movement. It is not merely a bad history
            // fact or a stale label for the original press.
            return prior.copy(
                snapshot=prior.snapshot.copy(slots=prior.snapshot.slots.map{
                    it.copy(exerciseId="substitute-press")
                }),
                evidence=prior.evidence.copy(visits=prior.evidence.visits.map{
                    it.copy(exerciseId="substitute-press")
                }))
        }
        fun nextA(newer:CustomCoachingHistoricalSession) = review(
            next=snapshot(3,machine="A"),machine="A",
            sessions=listOf(previousA,newer))
        // A different lift trained later ON MACHINE A invalidates the
        // earlier A press as an automatically earned next-session step.
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (nextA(substituted("A")) as CustomBasicHistoryResult.Rejected).reason)
        // A verified, distinct Machine B remains an independent thread:
        // its substitution must not erase valid prior Machine A press work.
        val recovered=nextA(substituted("B"))
            as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",recovered.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),recovered.candidate.proposedWorkingLoads)
        // A new workout on an unknown station is NOT evidence it happened on B.
        val unknown=substituted("B").let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map{
                    it.copy(actual=it.actual?.copy(machineInstanceId=null))
                }))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (nextA(unknown) as CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun nativeSwapWithNewSlotAndSetKeysMustNotPromoteOldMachineAProgression() {
        val earlierA=completed(1,week=1,machine="A")
        fun actuallySwapped(machine:String):CustomCoachingHistoricalSession {
            val prior=completed(2,week=2,machine=machine)
            val original=prior.snapshot.slots.single()
            // The real journal Swap reducer forbids reusing the old slot
            // identity AND its set keys. Model the independently finished
            // snapshot and visit evidence after that acknowledged swap.
            val replacementId="replacement-main"
            val changedSets=original.sets.mapIndexed { index,set ->
                set.copy(slotId=replacementId,
                    setKey="cycle:w2:d0:${replacementId}:s${index+1}")
            }
            val replacement=original.copy(slotId=replacementId,
                exerciseId="substitute-press",sets=changedSets)
            val visits=prior.evidence.visits.mapIndexed { index,fact ->
                fact.copy(slotId=replacementId,exerciseId="substitute-press",
                    setKey=changedSets[index].setKey)
            }
            return prior.copy(
                snapshot=prior.snapshot.copy(slots=listOf(replacement)),
                evidence=prior.evidence.copy(visits=visits))
        }
        fun forA(newer:CustomCoachingHistoricalSession)=review(
            next=snapshot(3,machine="A"),machine="A",
            sessions=listOf(earlierA,newer))
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (forA(actuallySwapped("A")) as CustomBasicHistoryResult.Rejected).reason)
        val independent=forA(actuallySwapped("B"))
            as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",independent.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),independent.candidate.proposedWorkingLoads)
        // A partial or malformed B replacement is not proof the missing
        // original A slot was never used. The coaching and basic readers
        // must consume exactly the same complete signed-set evidence rule.
        val completeB=actuallySwapped("B")
        val missingSet=completeB.copy(evidence=completeB.evidence.copy(
            visits=completeB.evidence.visits.dropLast(1)))
        val skippedSet=completeB.copy(evidence=completeB.evidence.copy(
            visits=completeB.evidence.visits.mapIndexed{index,fact->
                if(index==1)fact.copy(outcome=KotlinJournalSetOutcome.SKIPPED,
                    actual=null) else fact
            }))
        val changedPrescription=completeB.copy(evidence=completeB.evidence.copy(
            visits=completeB.evidence.visits.mapIndexed{index,fact->
                if(index==0)fact.copy(prescribedReps=RepBand(5,10)) else fact
            }))
        for(invalid in listOf(missingSet,skippedSet,changedPrescription)) {
            assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
                (forA(invalid) as CustomBasicHistoryResult.Rejected).reason)
        }
        val ambiguous=actuallySwapped("B").let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map {fact->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId=null))
                }))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (forA(ambiguous) as CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun finishedRealJournalSwapCannotEarnAnOlderSameStationIncrease() {
        val earlierA=completed(1,week=1,machine="A")
        fun finishSwap(machine:String):CustomCoachingHistoricalSession {
            val original=snapshot(2,machine=machine)
            var journal=CustomWorkoutJournalDomain.begin(
                "real-swap-$machine",original,"a".repeat(64))
            val priorSlot=original.slots.single()
            val newId="replacement-main"
            val replacement=priorSlot.copy(slotId=newId,
                exerciseId="substitute-press",
                sets=priorSlot.sets.map{set ->
                    set.copy(slotId=newId,
                        setKey="cycle:w2:d0:${newId}:s${set.ordinalInSlot}")
                })
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Swap(
                    "swap-$machine",journal.revision,"main",replacement))
                as CustomWorkoutJournalDecision.Proposed).draft
            for(set in replacement.sets) {
                journal=(CustomWorkoutJournalDomain.reduce(journal,
                    CustomWorkoutJournalOperation.Record(
                        "record-$machine-${set.ordinalInSlot}",journal.revision,
                        CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                        KotlinJournalSetOutcome.COMPLETED,
                        KotlinPerformedSet(n(100),LoadUnit.LB,10,null,machine)))
                    as CustomWorkoutJournalDecision.Proposed).draft
            }
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Finish(
                    "finish-$machine",journal.revision))
                as CustomWorkoutJournalDecision.Proposed).draft
            val evidence=(CustomWorkoutHistoryEvidenceDomain.project(journal)
                as CustomHistoryEvidenceResult.Candidate).evidence
            assertEquals(newId,journal.snapshot.slots.single().slotId)
            assertTrue(evidence.visits.all{it.slotId==newId})
            return CustomCoachingHistoricalSession(2,journal.snapshot,evidence)
        }
        fun nextA(replacement:CustomCoachingHistoricalSession)=review(
            next=snapshot(3,machine="A"),machine="A",
            sessions=listOf(earlierA,replacement))
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (nextA(finishSwap("A")) as CustomBasicHistoryResult.Rejected).reason)
        val anotherStation=nextA(finishSwap("B"))
            as CustomBasicHistoryResult.Reviewed
        assertEquals("workout-1",anotherStation.historyWorkoutId)
        assertEquals(listOf(n(105),n(105)),
            anotherStation.candidate.proposedWorkingLoads)
    }

    @Test fun unloadedBodyweightUsesVerifiedExactRepsWithoutAnyLoadOrUnit() {
        fun plan(target:Int, method:SavedProgressionMethod = SavedProgressionMethod.BODYWEIGHT_REPS):CustomTrainingCycle {
            val source=cycle(method)
            return source.copy(blocks=source.blocks.map{block ->
                block.copy(days=block.days.map{day ->
                    day.copy(slots=day.slots.map{slot ->
                        slot.copy(sets=slot.sets.map{
                            it.copy(load=DraftSetLoad.ExplicitBodyweight,
                                exactRepTarget=target)
                        })
                    })
                })
            })
        }
        fun next(target:Int,dayIndex:Int=0)=snapshot(day=dayIndex).let {day ->
            day.copy(slots=day.slots.map{slot ->
                slot.copy(sets=slot.sets.map{set ->
                    set.copy(prescription=set.prescription.copy(
                        load=DraftSetLoad.ExplicitBodyweight,
                        exactRepTarget=target))
                })
            })
        }
        fun prior(reps:List<Int>,machine:String?=null,
            failLast:Boolean=false):CustomCoachingHistoricalSession {
            val weighted=completed(1,reps=reps,failed=if(failLast)setOf(1) else emptySet())
            return weighted.copy(
                snapshot=weighted.snapshot.copy(slots=weighted.snapshot.slots.map{slot ->
                    slot.copy(sets=slot.sets.map{set ->
                        set.copy(prescription=set.prescription.copy(
                            load=DraftSetLoad.ExplicitBodyweight,exactRepTarget=8))
                    })
                }),
                evidence=weighted.evidence.copy(visits=weighted.evidence.visits.map{visit ->
                    visit.copy(prescribedLoad=DraftSetLoad.ExplicitBodyweight,
                        actual=visit.actual?.copy(load=null,unit=null,
                            machineInstanceId=machine))
                }))
        }
        val ladder = review(program=plan(8, SavedProgressionMethod.LADDER), next=next(8),
            sessions=listOf(prior(listOf(8, 7)))) as CustomBasicHistoryResult.UnloadedReviewed
        assertEquals(listOf(9, 8), ladder.candidate.proposedExactReps)
        val automatic = review(program=plan(8, SavedProgressionMethod.AUTO), next=next(8),
            sessions=listOf(prior(listOf(8, 7)))) as CustomBasicHistoryResult.UnloadedReviewed
        assertEquals(ladder.candidate, automatic.candidate)
        val athletePlan=plan(8)
        val earned=review(program=athletePlan,next=next(8),
            sessions=listOf(prior(listOf(8,7))))
        assertTrue(earned.toString(),earned is CustomBasicHistoryResult.UnloadedReviewed)
        val proposal=(earned as CustomBasicHistoryResult.UnloadedReviewed).candidate
        assertEquals(listOf(9,8),proposal.proposedExactReps)
        assertEquals(setOf(0),proposal.advancedOrdinals)
        assertFalse(earned.maySaveProgram)
        assertFalse(earned.mayIssueWorkout)
        assertFalse(earned.mayModifyHistory)
        val cap=review(program=plan(10),next=next(10),
            sessions=listOf(prior(listOf(10,10))))
        assertTrue(cap.toString(),cap is CustomBasicHistoryResult.UnloadedReviewed)
        assertTrue((cap as CustomBasicHistoryResult.UnloadedReviewed)
            .candidate.atRepCeiling)
        assertEquals(listOf(10,10),cap.candidate.proposedExactReps)
        val failed=review(program=athletePlan,next=next(8),
            sessions=listOf(prior(listOf(8,7),failLast=true)))
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (failed as CustomBasicHistoryResult.Rejected).reason)
        val falselyTagged=review(program=athletePlan,next=next(8),
            sessions=listOf(prior(listOf(8,8),machine="machine-A")))
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (falselyTagged as CustomBasicHistoryResult.Rejected).reason)
        val noExact=review(
            program=cycle(SavedProgressionMethod.BODYWEIGHT_REPS).let{source->
                source.copy(blocks=source.blocks.map{block->
                    block.copy(days=block.days.map{day->
                        day.copy(slots=day.slots.map{slot->
                            slot.copy(sets=slot.sets.map{
                                it.copy(load=DraftSetLoad.ExplicitBodyweight)
                            })
                        })
                    })
                })
            },next=next(8),sessions=listOf(prior(listOf(8,8))))
        assertEquals(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,
            (noExact as CustomBasicHistoryResult.Rejected).reason)
        assertEquals(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY,
            (review(program=athletePlan,next=next(8,dayIndex=2),
                sessions=listOf(prior(listOf(8,8)))) as
                CustomBasicHistoryResult.Rejected).reason)
    }

    @Test fun sourceChangesShortSessionsTechniqueFollowupsAndLoadEditsFailClosed() {
        val changed=completed(1).let {
            it.copy(snapshot=it.snapshot.copy(stamp=it.snapshot.stamp.copy(
                cycleRevision=2)),
                evidence=it.evidence.copy(cycleRevision=2))
        }
        assertEquals(CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED,
            (review(sessions=listOf(changed)) as
                CustomBasicHistoryResult.Rejected).reason)
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(completed(1,shortened=true))) as
                CustomBasicHistoryResult.Rejected).reason)
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=4)
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(completed(1,amp=amp))) as
                CustomBasicHistoryResult.Rejected).reason)
        val wrongUnit=completed(1).let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map{v->
                    v.copy(actual=v.actual?.copy(unit=LoadUnit.KG))
                }))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(wrongUnit)) as
                CustomBasicHistoryResult.Rejected).reason)
        val changedBand=completed(1).let{session->
            session.copy(snapshot=session.snapshot.copy(slots=
                session.snapshot.slots.map{slot->slot.copy(sets=
                    slot.sets.map{it.copy(prescription=
                        it.prescription.copy(reps=RepBand(5,8)))})}),
                evidence=session.evidence.copy(visits=
                    session.evidence.visits.map{it.copy(
                        prescribedReps=RepBand(5,8))}))
        }
        assertEquals(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,
            (review(sessions=listOf(changedBand)) as
                CustomBasicHistoryResult.Rejected).reason)
        val altered=completed(1).let{session->
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.mapIndexed{i,v->
                    if(i==0)v.copy(actual=v.actual?.copy(load=n(95))) else v
                }))
        }
        assertEquals(BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY,
            (review(sessions=listOf(altered)) as
                CustomBasicHistoryResult.Rejected).evaluatorReason)
        assertEquals(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,
            (review(next=snapshot(shortened=true)) as
                CustomBasicHistoryResult.Rejected).reason)
    }
}
