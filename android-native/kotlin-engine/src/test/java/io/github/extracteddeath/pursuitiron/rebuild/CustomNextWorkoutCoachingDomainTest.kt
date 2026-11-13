package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomNextWorkoutCoachingDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private fun band()=RepBand(5,8)
    private fun load(v:Int)=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(v))
    private fun set(v:Int=200)=CustomAuthoredSet(
        DraftSetType.WORKING,load(v),band(),2,60,120)
    private fun snapshot(
        week:Int,day:Int=0,exercise:String="chest_press",
        loads:List<Int> = listOf(200,200)
    ):CustomWorkoutDaySnapshot {
        val slot=CustomWorkoutSlotSnapshot(
            "main",exercise,"chest","machine",ComposerRole.ACCESSORY,
            null,null,true,"Coach",
            loads.mapIndexed {index,weight->
                CustomWorkoutSetSnapshot(
                    "cycle:w$week:d$day:main:s${index+1}","main",
                    index+1,set(weight))
            })
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle",3,
                "gym-1",2),week,week,"build",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("ULPPL"),
            day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),360,200)
    }
    private fun completed(
        order:Long,week:Int,day:Int=0,
        reps:List<Int> = listOf(8,8),
        rirs:List<Int?> = listOf(2,2),
        issued:List<Int> = listOf(200,200),
        actual:List<Int> = issued,
        exercise:String = "chest_press",
        failed:Set<Int> = emptySet()
    ):CustomCoachingHistoricalSession {
        val snapshot=snapshot(week,day,exercise,issued)
        val visits=snapshot.slots.single().sets.mapIndexed{index,prescribed ->
            val fail=index in failed
            val fact=KotlinPerformedSet(n(actual[index]),LoadUnit.LB,
                if(fail)0 else reps[index],if(fail)null else rirs[index])
            CustomHistoricalVisitFact("main",exercise,prescribed.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                prescribed.prescription.load,band(),2,null,
                if(fail)KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
                fact,"log-${week}-${day}-${index}")
        }
        return CustomCoachingHistoricalSession(order,snapshot,
            CustomWorkoutHistoricalEvidence("athlete-1",
                "workout-${week}-${day}","cycle",3,
                "finish-${week}-${day}",3,visits))
    }
    private fun addWarmup(
        original:CustomCoachingHistoricalSession,
        weight:Int=195,
        includeReceipt:Boolean=true
    ):CustomCoachingHistoricalSession {
        val slot=original.snapshot.slots.single()
        val before=CustomWorkoutSetSnapshot(
            "cycle:w${original.snapshot.absoluteWeek}:d${original.snapshot.weekday}:main:w1",
            slot.slotId,0,
            CustomAuthoredSet(DraftSetType.WARMUP,load(weight),
                RepBand(8,8),null,60,60))
        val updated=original.snapshot.copy(slots=listOf(
            slot.copy(sets=listOf(before)+slot.sets)))
        val warmupFact=CustomHistoricalVisitFact(slot.slotId,slot.exerciseId,
            before.setKey,CustomWorkoutVisitPart.MAIN,DraftSetType.WARMUP,
            before.prescription.load,before.prescription.reps,
            before.prescription.targetRir,null,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(weight),LoadUnit.LB,8,null),
            "warmup-${original.snapshot.absoluteWeek}")
        return original.copy(snapshot=updated,
            evidence=original.evidence.copy(
                visits=(if(includeReceipt)listOf(warmupFact) else emptyList())+
                    original.evidence.visits))
    }

    private fun addWarmup(
        original:CustomWorkoutDaySnapshot,weight:Int=195
    ):CustomWorkoutDaySnapshot {
        val slot=original.slots.single()
        val before=CustomWorkoutSetSnapshot(
            "cycle:w${original.absoluteWeek}:d${original.weekday}:main:w1",
            slot.slotId,0,
            CustomAuthoredSet(DraftSetType.WARMUP,load(weight),
                RepBand(8,8),null,60,60))
        return original.copy(slots=listOf(slot.copy(sets=listOf(before)+slot.sets)))
    }

    private fun stock()=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(n(195),n(200),n(205),n(210),n(215)))
    private fun review(
        next:CustomWorkoutDaySnapshot=snapshot(5),
        sessions:List<CustomCoachingHistoricalSession> = listOf(
            completed(1,3),completed(2,4)),
        stock:VerifiedLoadStock?=stock(),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,
        step:Int=5
    )=CustomNextWorkoutCoachingDomain.review(
        CustomNextWorkoutCoachingRequest(next,"main",sessions,
            stock,n(step),readiness,restricted))

    @Test fun identicalCustomAndGeneratedEvidenceHasIdenticalCurrentRepTargets() {
        val last=completed(2,4,reps=listOf(5,6),rirs=listOf(5,4))
        val custom=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(snapshot(5),"main",
                listOf(completed(1,3),last),stock(),n(5),
                CoachReadiness.NORMAL,false,CustomRepForecastPolicy.TARGET_RIR_V2))
        val canonical=FatigueAwareRepTargetReviewDomain.assess(
            ProgressionWorkoutEvidence("workout-4-0","chest_press",
                LoadUnit.LB,RepBand(5,8),2,listOf(
                    WorkingSetFact(0,SetOutcome.COMPLETED,n(200),5,5,n(200),2),
                    WorkingSetFact(1,SetOutcome.COMPLETED,n(200),6,4,n(200),2))),
            stock(),LoadBasis.MACHINE_STACK,CoachReadiness.NORMAL,false,
            verifiedPerSetLoading=true,
            targetRirByOrdinal=listOf(2,2))
        assertEquals(FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW,
            canonical.reason)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,custom.status)
        assertEquals(canonical.candidateRepTargets,
            custom.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(canonical.assumedGoalRirSetCount,
            custom.assumedGoalRirSetCount)
        assertTrue(custom.nextSetSuggestions.none{it.actualRir==null})
    }

    @Test fun boundPhysicalMachineCannotUseUnverifiedLegacyRepOnlyFallback() {
        // Two authentic A visits are not independently verified physical
        // stock. A null live override must retain the approved bound A.
        fun physicalA(session:CustomCoachingHistoricalSession)=
            session.copy(evidence=session.evidence.copy(
                visits=session.evidence.visits.map { fact ->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId="A"))
                }))
        val earlier=physicalA(completed(1,3))
        val latest=physicalA(completed(2,4,
            reps=listOf(5,6),rirs=listOf(5,4)))
        val next=snapshot(5).let {day ->
            day.copy(slots=day.slots.map {it.copy(machineInstanceId="A")})
        }
        fun inspect(verified:VerifiedLoadStock?)=
            CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(next,"main",
                    listOf(earlier,latest),verified,n(5),
                    CoachReadiness.NORMAL,false,
                    CustomRepForecastPolicy.TARGET_RIR_V2,
                    requestedMachineInstanceId=null))
        val unknownStock=inspect(null)
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
            unknownStock.status)
        assertTrue(unknownStock.nextSetSuggestions.isEmpty())
        assertNull(unknownStock.loadReviewCandidate)
        assertFalse(unknownStock.mayIssueWorkout)
        val independentlyVerified=inspect(stock())
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            independentlyVerified.status)
        assertEquals(listOf(8,8),
            independentlyVerified.nextSetSuggestions.map {it.proposedNextReps})
        assertNull(independentlyVerified.loadReviewCandidate)
    }

    @Test fun targetRirV2UsesCanonicalStockEvidenceNotACustomShortcut() {
        val older=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(5,4))
        val next=snapshot(5)
        fun reviewWith(
            history:CustomCoachingHistoricalSession=latest,
            inventory:VerifiedLoadStock?=stock(),
            policy:CustomRepForecastPolicy=CustomRepForecastPolicy.TARGET_RIR_V2
        )=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(next,"main",
                listOf(older,history),inventory,n(5),
                CoachReadiness.NORMAL,false,policy))
        val verified=reviewWith()
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,verified.status)
        assertEquals(listOf(8,8),
            verified.nextSetSuggestions.map{it.proposedNextReps})
        // The ORIGINAL signed, unchanged-load sets may support a provisional
        // rep-only review even without a fresh gym read; no new load can be
        // proposed, and final issuance still requires the complete audit.
        val missingStock=reviewWith(inventory=null)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            missingStock.status)
        assertEquals(listOf(8,8),
            missingStock.nextSetSuggestions.map{it.proposedNextReps})
        assertNull(missingStock.loadReviewCandidate)
        assertTrue(missingStock.forecastLimitations.contains(
            RepForecastLimitation.CURRENT_EQUIPMENT_NOT_REVERIFIED))
        // Available but CONTRADICTORY inventory never inherits this
        // read-only exception. Weights missing from a real rack are refused.
        val wrongStock=reviewWith(inventory=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,
            listOf(n(195),n(205))))
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
            wrongStock.status)
        assertTrue(wrongStock.nextSetSuggestions.isEmpty())
        assertNull(wrongStock.loadReviewCandidate)
        val edited=reviewWith(history=completed(2,4,reps=listOf(5,6),
            rirs=listOf(5,4),actual=listOf(205,200)))
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
            edited.status)
        assertTrue(edited.nextSetSuggestions.isEmpty())
        assertNull(edited.loadReviewCandidate)
        assertEquals(listOf(5,6),latest.evidence.visits.map{it.actual?.reps})
        // Older signed V1 receipts still replay the old policy exactly.
        val legacy=reviewWith(inventory=null,
            policy=CustomRepForecastPolicy.LEGACY_STEP_V1)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,legacy.status)
        assertEquals(listOf(6,7),
            legacy.nextSetSuggestions.map{it.proposedNextReps})
    }

    @Test fun V2AndV3UseGeneratedEnginesBelowFloorTargetRirRule() {
        // Same factual 5 reps at 2 RIR with an issued 4 RIR goal is
        // estimated at 3 reps at goal. It is NOT an earned 5-rep target.
        fun goal4(day:CustomWorkoutDaySnapshot)=day.copy(
            slots=day.slots.map{slot->slot.copy(
                sets=slot.sets.map{set->set.copy(
                    prescription=set.prescription.copy(targetRir=4))})})
        fun goal4(entry:CustomCoachingHistoricalSession)=entry.copy(
            snapshot=goal4(entry.snapshot),
            evidence=entry.evidence.copy(visits=entry.evidence.visits.map{
                it.copy(prescribedRir=4)
            }))
        val history=listOf(
            goal4(completed(1,3,reps=listOf(5,5),rirs=listOf(2,2))),
            goal4(completed(2,4,reps=listOf(5,5),rirs=listOf(2,2))))
        val next=goal4(snapshot(5))
        fun check(policy:CustomRepForecastPolicy)=
            CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(next,"main",history,
                    stock(),n(5),CoachReadiness.NORMAL,false,policy))
        for(policy in listOf(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3)) {
            val reviewed=check(policy)
            assertEquals(policy.toString(),CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                reviewed.status)
            assertTrue(reviewed.nextSetSuggestions.isEmpty())
            assertNull(reviewed.loadReviewCandidate)
            assertTrue(reviewed.explanation.contains("below its minimum"))
            assertEquals(CustomNextRepAmendmentResult.Rejected(
                CustomNextRepAmendmentRefusal.NOT_AN_ELIGIBLE_REP_REVIEW),
                CustomNextRepAmendmentDomain.propose(next,"main",reviewed))
        }
        // Historical frozen V1 receipts still replay their old rule.
        val legacy=check(CustomRepForecastPolicy.LEGACY_STEP_V1)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,legacy.status)
        assertEquals(listOf(6,6),
            legacy.nextSetSuggestions.map{it.proposedNextReps})
    }

    @Test fun aBoundMachineWithoutExplicitOverrideNeverUsesLegacyProgression() {
        fun tagged(session:CustomCoachingHistoricalSession,id:String)=
            session.copy(evidence=session.evidence.copy(
                visits=session.evidence.visits.map {
                    it.copy(actual=it.actual?.copy(machineInstanceId=id))
                }))
        val b3=tagged(completed(1,3),"B")
        val a4=tagged(completed(2,4),"A")
        val a5=tagged(completed(3,5),"A")
        val records=listOf(b3,a4,a5)
        val base=snapshot(6)
        val boundA=base.copy(slots=base.slots.map {
            it.copy(machineInstanceId="A")
        })
        fun inspect(day:CustomWorkoutDaySnapshot,
            selected:String?=null)=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(day,"main",records,
                stock(),n(5),CoachReadiness.NORMAL,false,
                requestedMachineInstanceId=selected))
        val autoBound=inspect(boundA)
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            autoBound.status)
        assertEquals(listOf("workout-4-0","workout-5-0"),
            autoBound.comparedWorkoutIds)
        assertEquals(n(205),autoBound.loadReviewCandidate)
        assertFalse(autoBound.mayIssueWorkout)

        val legacyUnknown=inspect(base)
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,
            legacyUnknown.status)
        assertNull(legacyUnknown.loadReviewCandidate)
        // An explicit B choice starts its OWN thread even when the default
        // physical station on the approved slot is A.
        val overrideB=inspect(boundA,"B")
        assertNull(overrideB.loadReviewCandidate)
        assertNotEquals(listOf("workout-4-0","workout-5-0"),
            overrideB.comparedWorkoutIds)
    }

    @Test fun differentMachineStacksNeverCreateFalseStrengthDropOrBorrowProgression() {
        fun tagged(session:CustomCoachingHistoricalSession,id:String):
            CustomCoachingHistoricalSession =
            session.copy(evidence=session.evidence.copy(
                visits=session.evidence.visits.map {
                    it.copy(actual=it.actual?.copy(machineInstanceId=id))
                }))
        val b3=tagged(completed(1,3,issued=listOf(200,200),
            actual=listOf(200,200)),"B")
        val a4=tagged(completed(2,4,issued=listOf(200,200),
            actual=listOf(140,140),reps=listOf(5,5)),"A")
        val b5=tagged(completed(3,5,issued=listOf(200,200),
            actual=listOf(200,200)),"B")
        val full=listOf(b3,a4,b5)
        val proposed=snapshot(6)
        fun coach(machine:String?)=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(
                proposed,"main",full,stock(),n(5),
                CoachReadiness.NORMAL,false,
                requestedMachineInstanceId=machine))
        val b=coach("B")
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,b.status)
        assertEquals(n(205),b.loadReviewCandidate)
        assertEquals(listOf("workout-3-0","workout-5-0"),b.comparedWorkoutIds)
        val a=coach("A")
        assertNull(a.loadReviewCandidate)
        assertEquals("workout-4-0",a.latestWorkoutId)
        val legacy=coach(null)
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,legacy.status)
        assertNull(legacy.loadReviewCandidate)

        val mixed=b5.copy(evidence=b5.evidence.copy(visits=b5.evidence.visits
            .mapIndexed {index,visit->
                visit.copy(actual=visit.actual?.copy(
                    machineInstanceId=if(index==0)"A" else "B"))
            }))
        val refusal=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(snapshot(6),"main",
                listOf(b3,a4,mixed),stock(),n(5),CoachReadiness.NORMAL,false,
                requestedMachineInstanceId="B"))
        assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
            refusal.status)
        assertNull(refusal.loadReviewCandidate)
    }

    @Test fun twoStraightSetCeilingSessionsEarnOnlyNextPhysicallyVerifiedIncrement() {
        val result=review()
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            result.status)
        assertEquals(n(200),result.observedStraightSetLoad)
        assertEquals(n(205),result.loadReviewCandidate)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            result.comparedWorkoutIds)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayEditProgram)
        assertFalse(result.mayChangeHistory)
        assertNull(review(step=2).loadReviewCandidate)
        assertNull(review(stock=null).loadReviewCandidate)
    }

    @Test fun upperAndPushAppearancesCannotEarnEachOthersWeightIncrease() {
        val history=listOf(
            completed(1,3,0),completed(2,3,2),
            completed(3,4,0,reps=listOf(5,7)),
            completed(4,4,2))
        val upper=review(next=snapshot(5,0),sessions=history)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,upper.status)
        assertNull(upper.loadReviewCandidate)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            upper.comparedWorkoutIds)
        assertEquals(listOf(6,8),upper.nextSetSuggestions.map{
            it.proposedNextReps
        })
        val push=review(next=snapshot(5,2),sessions=history)
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            push.status)
        assertEquals(n(205),push.loadReviewCandidate)
        assertEquals(listOf("workout-3-2","workout-4-2"),
            push.comparedWorkoutIds)
    }

    @Test fun oldDoubleProgressionFailureNeverRaisesAllSetsToHeaviestSet() {
        // Mirrors the legacy bug: 200x3, 205x3, 210x2, 215x2
        // with a 5-8 rep target must not prescribe 215 for everything.
        val poor=completed(2,4,reps=listOf(3,2,2,2),
            rirs=listOf(0,0,0,0),
            issued=listOf(200,205,210,215),
            actual=listOf(200,205,210,215))
        val next=snapshot(5,loads=listOf(200,205,210,215))
        val first=completed(1,3,reps=listOf(3,3,2,2),
            rirs=listOf(0,0,0,0),
            issued=listOf(200,205,210,215))
        val result=review(next=next,sessions=listOf(first,poor))
        assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
            result.status)
        assertNull(result.loadReviewCandidate)
        assertTrue(result.nextSetSuggestions.isEmpty())
    }

    @Test fun oneRepAtATimeWithoutInventedEffortOrNearFailureProgression() {
        val stable=review(sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(5,6))))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,stable.status)
        assertEquals(listOf(6,7),stable.nextSetSuggestions.map{
            it.proposedNextReps
        })
        assertEquals(1,stable.nextSetSuggestions.maxOf{
            it.proposedNextReps-it.observedReps
        })
        val assumed=review(sessions=listOf(completed(1,3),
            completed(2,4,rirs=listOf(2,null))))
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,assumed.status)
        assertEquals(n(205),assumed.loadReviewCandidate)
        assertEquals(1,assumed.assumedGoalRirSetCount)
        val unreported=assumed.nextSetSuggestions[1]
        assertNull(unreported.actualRir)
        assertEquals(2,unreported.progressionRir)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,unreported.effortSource)
        assertTrue(assumed.explanation.contains("assumes their original goal RIR"))
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_NEAR_FAILURE,
            review(sessions=listOf(completed(1,3),
                completed(2,4,rirs=listOf(2,1)))).status)
        assertEquals(CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
            review(readiness=CoachReadiness.LOW).status)
    }

    @Test fun completedFutureWeekCannotCoachAnAlreadyTrainedEarlierWeek() {
        val completedNext=review(
            next=snapshot(4),sessions=listOf(
                completed(1,3,reps=listOf(5,5)),
                completed(2,4,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
            completedNext.status)
        assertNull(completedNext.loadReviewCandidate)
        assertTrue(completedNext.nextSetSuggestions.isEmpty())

        // Even if an older completed week looks eligible, a later same-day
        // completion must not be silently skipped to justify coaching.
        val late=review(
            next=snapshot(5),sessions=listOf(
                completed(1,3,reps=listOf(8,8)),
                completed(2,6,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
            late.status)
        assertEquals("workout-6-0",late.latestWorkoutId)
        assertTrue(late.nextSetSuggestions.isEmpty())
    }

    @Test fun customRepAmendmentUsesAuthenticGoalAssumptionsWithoutInventingActualRir() {
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(null,null))
        val original=snapshot(5)
        val result=review(next=original,sessions=listOf(completed(1,3),latest))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        val amended=CustomNextRepAmendmentDomain.propose(original,"main",result)
        assertTrue(amended.toString(),amended is CustomNextRepAmendmentResult.Proposed)
        assertEquals(listOf(6,7),(amended as CustomNextRepAmendmentResult.Proposed).amendment.nextMinimumReps)
        assertTrue(result.nextSetSuggestions.all {it.actualRir==null && it.progressionRir==2})
        assertTrue(latest.evidence.visits.all {it.actual?.actualRir==null})
        assertTrue(amended.amendment.amended.slots.single().sets.all {it.prescription.targetRir==2})
        val forged=result.copy(nextSetSuggestions=result.nextSetSuggestions.mapIndexed {i,s->
            if(i==0)s.copy(progressionRir=5) else s
        })
        assertEquals(CustomNextRepAmendmentResult.Rejected(CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(original,"main",forged))
    }

    @Test fun futureCompletionOnDifferentWeekdayDoesNotBlockIndependentDay() {
        val result=review(next=snapshot(5,0),
            sessions=listOf(
                completed(1,3,0,reps=listOf(5,5)),
                completed(2,5,2,reps=listOf(8,8))))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(6,6),result.nextSetSuggestions.map{
            it.proposedNextReps})
        assertEquals(listOf("workout-3-0"),result.comparedWorkoutIds)
    }

    @Test fun duplicateFinishedWorkoutIdentityInvalidatesProgressionEvidence() {
        val earlier=completed(1,3)
        val duplicate=completed(2,4).let{
            it.copy(evidence=it.evidence.copy(
                workoutId=earlier.evidence.workoutId))
        }
        val result=review(sessions=listOf(earlier,duplicate))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            result.status)
        assertNull(result.loadReviewCandidate)
        assertTrue(result.nextSetSuggestions.isEmpty())
    }

    @Test fun alteredHistoricalPrescriptionCannotEarnRepsOrNextLoad() {
        val earlier=completed(1,3)
        val latest=completed(2,4)
        val changedLoad=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedLoad=load(205)) else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,changedLoad)).status)
        val changedReps=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedReps=RepBand(6,8)) else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,changedReps)).status)
        val alteredPrior=earlier.copy(evidence=earlier.evidence.copy(
            visits=earlier.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(prescribedRir=0) else visit
            }))
        val result=review(sessions=listOf(alteredPrior,latest))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            result.status)
        assertNull(result.loadReviewCandidate)
        val inventedEvent=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.mapIndexed{index,visit->
                if(index==0)visit.copy(effectiveEventId="") else visit
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            review(sessions=listOf(earlier,inventedEvent)).status)
    }

    @Test fun incompleteRecentDayAndChangedExerciseOrSourceNeverCherryPickSuccess() {
        val mostRecent=completed(3,4,reps=listOf(0,0),
            failed=setOf(0,1),rirs=listOf(null,null))
        val missed=review(next=snapshot(5),
            sessions=listOf(completed(1,2),completed(2,3),mostRecent))
        assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
            missed.status)
        assertEquals(listOf("workout-3-0","workout-4-0"),
            missed.comparedWorkoutIds)
        val changed=review(sessions=listOf(completed(1,3),
            completed(2,4,exercise="other_press")))
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_DAY_OR_EXERCISE,
            changed.status)
        val changedPrescription=review(
            next=snapshot(5,loads=listOf(205,205)))
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
            changedPrescription.status)
    }
    @Test fun approvedRepAmendmentChangesOnlyTheNextSetsMinimums() {
        val next=snapshot(5)
        val reviewed=review(next=next,sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(6,5)) ))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,reviewed.status)
        val offer=CustomNextRepAmendmentDomain.propose(next,"main",reviewed)
        assertTrue(offer.toString(),offer is CustomNextRepAmendmentResult.Proposed)
        val amended=(offer as CustomNextRepAmendmentResult.Proposed).amendment
        assertEquals(listOf(7,6),amended.nextMinimumReps)
        assertEquals(next.slots.single().sets.map{it.setKey},
            amended.originalSetKeys)
        assertEquals(listOf(7,6),amended.amended.slots.single().sets.map{
            it.prescription.reps.minimum})
        assertEquals(listOf(8,8),amended.amended.slots.single().sets.map{
            it.prescription.reps.maximum})
        assertEquals(listOf(5,5),next.slots.single().sets.map{
            it.prescription.reps.minimum})
        assertEquals(next.stamp,amended.amended.stamp)
        assertEquals(next.slots.single().sets.map{it.prescription.load},
            amended.amended.slots.single().sets.map{it.prescription.load})
        assertFalse(amended.mayIssueWorkout)
        assertFalse(amended.mayChangeCycle)
    }

    @Test fun tamperedRepOfferAndLowReadinessCannotCreateApprovedAmendment() {
        val next=snapshot(5)
        val reviewed=review(next=next,sessions=listOf(
            completed(1,3,reps=listOf(5,5)),
            completed(2,4,reps=listOf(5,6))))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(next,"main",
                reviewed.copy(nextSetSuggestions=
                    reviewed.nextSetSuggestions.mapIndexed{i,value->
                        if(i==0)value.copy(proposedNextReps=8) else value
                    })))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.INVALID_TARGET_SLOT),
            CustomNextRepAmendmentDomain.propose(next,"another-slot",reviewed))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.NOT_AN_ELIGIBLE_REP_REVIEW),
            CustomNextRepAmendmentDomain.propose(next,"main",
                review(next=next,readiness=CoachReadiness.LOW)))
    }

    @Test fun nextWeekAfterApprovedRepFloorStillReceivesCoaching() {
        val latest=completed(2,5,reps=listOf(6,6))
        val raised=latest.copy(
            snapshot=latest.snapshot.copy(slots=latest.snapshot.slots.map{
                slot->slot.copy(sets=slot.sets.map{set->set.copy(
                    prescription=set.prescription.copy(reps=RepBand(6,8)))
                })
            }),
            evidence=latest.evidence.copy(visits=latest.evidence.visits.map{
                it.copy(prescribedReps=RepBand(6,8))
            }))
        val result=review(next=snapshot(6),sessions=listOf(
            completed(1,4,reps=listOf(5,5)),raised))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(7,7),result.nextSetSuggestions.map{
            it.proposedNextReps})
        assertEquals(listOf("cycle:w6:d0:main:s1","cycle:w6:d0:main:s2"),
            result.nextSetSuggestions.map{it.setKey})
    }


    @Test fun verifiedLoadAmendmentOnlyChangesWorkingSetsForThisDay() {
        val planned=snapshot(5)
        val reviewed=review(next=planned)
        val approved=CustomNextLoadAmendmentDomain.propose(
            planned,"main",reviewed,stock(),n(5))
        assertTrue(approved.toString(),approved is CustomNextLoadAmendmentResult.Proposed)
        val changed=(approved as CustomNextLoadAmendmentResult.Proposed).amendment
        assertEquals(n(200),changed.previousLoad)
        assertEquals(n(205),changed.proposedLoad)
        assertEquals(LoadUnit.LB,changed.unit)
        assertEquals(LoadBasis.MACHINE_STACK,changed.basis)
        assertEquals(planned.slots.single().sets.map{it.setKey},
            changed.workingSetKeys)
        assertEquals(listOf(n(205),n(205)),
            changed.amended.slots.single().sets.map{
                (it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(planned.slots.single().sets.map{it.prescription.reps},
            changed.amended.slots.single().sets.map{it.prescription.reps})
        assertEquals(listOf(n(200),n(200)),planned.slots.single().sets.map{
            (it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals(planned.stamp,changed.amended.stamp)
        assertFalse(changed.mayIssueWorkout)
        assertFalse(changed.mayEditCycle)
    }

    @Test fun unavailableStepMixedLoadsAndNearFailureCannotBecomeWeightApprovals() {
        val planned=snapshot(5)
        val success=review(next=planned)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.UNVERIFIED_EQUIPMENT),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                success,VerifiedLoadStock.Totals(
                    LoadUnit.LB,LoadBasis.MACHINE_STACK,listOf(n(200),n(210))),n(5)))
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.INVALID_INCREMENT),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                success,stock(),n(2)))
        val unsafe=review(next=snapshot(5,loads=listOf(200,215)),
            sessions=listOf(
                completed(1,3,issued=listOf(200,215),actual=listOf(200,215)),
                completed(2,4,issued=listOf(200,215),actual=listOf(200,215))))
        assertNull(unsafe.loadReviewCandidate)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(
                snapshot(5,loads=listOf(200,215)),"main",unsafe,stock(),n(15)))
        val near=review(sessions=listOf(
            completed(1,3),completed(2,4,rirs=listOf(2,1))))
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(planned,"main",
                near,stock(),n(5)))
    }

    @Test fun weekdaySpecificLoadReviewCannotIncreaseAnotherDaysBench() {
        val history=listOf(
            completed(1,3,day=0),completed(2,3,day=2),
            completed(3,4,day=0,reps=listOf(7,8)),
            completed(4,4,day=2))
        val upper=review(next=snapshot(5,0),sessions=history)
        val push=review(next=snapshot(5,2),sessions=history)
        assertNull(upper.loadReviewCandidate)
        assertEquals(n(205),push.loadReviewCandidate)
        assertEquals(CustomNextLoadAmendmentResult.Rejected(
            CustomNextLoadAmendmentRefusal.NOT_EARNED),
            CustomNextLoadAmendmentDomain.propose(
                snapshot(5,0),"main",upper,stock(),n(5)))
        assertTrue(CustomNextLoadAmendmentDomain.propose(
            snapshot(5,2),"main",push,stock(),n(5))
            is CustomNextLoadAmendmentResult.Proposed)
    }



    @Test fun verifiedCustomWarmupsSupportV2WithoutEarningWorkingReps() {
        val older=addWarmup(completed(1,3,reps=listOf(8,8)))
        val latest=addWarmup(completed(2,4,reps=listOf(5,6),
            rirs=listOf(5,4)))
        val next=addWarmup(snapshot(5))
        val result=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(next,"main",listOf(older,latest),
                stock(),n(5),CoachReadiness.NORMAL,false,
                CustomRepForecastPolicy.TARGET_RIR_V2))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(8,8),result.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(2,result.nextSetSuggestions.size)
        assertNull(result.loadReviewCandidate)
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,result.repForecastPolicy)
    }

    @Test fun V2CannotBorrowChangedOrMissingCustomWarmupHistory() {
        val older=addWarmup(completed(1,3,reps=listOf(8,8)))
        val latest=addWarmup(completed(2,4,reps=listOf(5,6),
            rirs=listOf(5,4)))
        val next=addWarmup(snapshot(5))
        fun check(history:List<CustomCoachingHistoricalSession>,
            target:CustomWorkoutDaySnapshot=next
        )=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(target,"main",history,stock(),
                n(5),CoachReadiness.NORMAL,false,
                CustomRepForecastPolicy.TARGET_RIR_V2))
        val noReceipt=addWarmup(completed(2,4,reps=listOf(5,6),
            rirs=listOf(5,4)),includeReceipt=false)
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            check(listOf(older,noReceipt)).status)
        val changedIssued=latest.copy(evidence=latest.evidence.copy(
            visits=latest.evidence.visits.map{fact->
                if(fact.setType==DraftSetType.WARMUP)
                    fact.copy(prescribedLoad=load(200)) else fact
            }))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            check(listOf(older,changedIssued)).status)
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
            check(listOf(older,latest),addWarmup(snapshot(5),200)).status)
        // A V1 comparison retains its original historical semantics.
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            review(next=next,sessions=listOf(older,noReceipt)).status)
    }

    @Test fun V2LoadReviewNeedsBothAuthenticatedWarmupWorkouts() {
        val older=addWarmup(completed(1,3))
        val latest=addWarmup(completed(2,4))
        val next=addWarmup(snapshot(5))
        fun check(sessions:List<CustomCoachingHistoricalSession>)=
            CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(next,"main",sessions,
                    stock(),n(5),CoachReadiness.NORMAL,false,
                    CustomRepForecastPolicy.TARGET_RIR_V2))
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            check(listOf(older,latest)).status)
        assertEquals(n(205),check(listOf(older,latest)).loadReviewCandidate)
        val corruptedOlder=older.copy(evidence=older.evidence.copy(
            visits=older.evidence.visits.filter{
                it.setType!=DraftSetType.WARMUP
            }))
        val rejected=check(listOf(corruptedOlder,latest))
        assertEquals(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
            rejected.status)
        assertNull(rejected.loadReviewCandidate)
    }

    @Test fun v3HoldsARealMarkedSameWeekdayDropWithoutRewritingV2Receipts() {
        val older=addWarmup(completed(1,3,reps=listOf(8,8),
            rirs=listOf(5,5)))
        val weaker=addWarmup(completed(2,4,reps=listOf(5,5),
            rirs=listOf(4,4)))
        val next=addWarmup(snapshot(5))
        fun check(policy:CustomRepForecastPolicy)=
            CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(next,"main",
                    listOf(older,weaker),stock(),n(5),
                    CoachReadiness.NORMAL,false,policy))
        val legacy=check(CustomRepForecastPolicy.LEGACY_STEP_V1)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,legacy.status)
        assertEquals(listOf(6,6),legacy.nextSetSuggestions.map{
            it.proposedNextReps})
        val v2=check(CustomRepForecastPolicy.TARGET_RIR_V2)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,v2.status)
        assertEquals(listOf(7,7),v2.nextSetSuggestions.map{
            it.proposedNextReps})
        val v3=check(CustomRepForecastPolicy.TARGET_RIR_TREND_V3)
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_MARKED_SAME_DAY_CAPACITY_DROP,
            v3.status)
        assertEquals(listOf(4,4),v3.capacityDropsFromPreviousSession)
        assertFalse(v3.forecastLimitations.contains(
            RepForecastLimitation.SINGLE_EXPOSURE))
        assertNull(v3.loadReviewCandidate)
        assertTrue(v3.nextSetSuggestions.isEmpty())
        assertFalse(v3.mayIssueWorkout)
        assertFalse(v3.mayChangeHistory)
        assertTrue(v3.explanation.contains("do not establish chronic fatigue"))
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.NOT_AN_ELIGIBLE_REP_REVIEW),
            CustomNextRepAmendmentDomain.propose(next,"main",v3))
    }

    @Test fun v3DoesNotInferFatigueFromUnknownRirOrOneBadSet() {
        val prior=addWarmup(completed(1,3,reps=listOf(8,8),
            rirs=listOf(5,5)))
        val latest=addWarmup(completed(2,4,reps=listOf(5,5),
            rirs=listOf(null,4)))
        val next=addWarmup(snapshot(5))
        fun check(previous:CustomCoachingHistoricalSession,
            current:CustomCoachingHistoricalSession)=
            CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(next,"main",
                    listOf(previous,current),stock(),n(5),
                    CoachReadiness.NORMAL,false,
                    CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        val unknown=check(prior,latest)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,unknown.status)
        assertTrue(unknown.capacityDropsFromPreviousSession.isEmpty())
        assertEquals(1,unknown.assumedGoalRirSetCount)
        assertTrue(unknown.forecastLimitations.contains(
            RepForecastLimitation.SINGLE_EXPOSURE))
        val oneSet=check(prior,addWarmup(completed(2,4,
            reps=listOf(5,8),rirs=listOf(4,5))))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,oneSet.status)
        assertTrue(oneSet.capacityDropsFromPreviousSession.isEmpty())
        val differentLoad=addWarmup(completed(1,3,reps=listOf(8,8),
            rirs=listOf(5,5),actual=listOf(205,205)))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            check(differentLoad,addWarmup(completed(2,4,
                reps=listOf(5,5),rirs=listOf(4,4)))).status)
    }

    @Test fun v3StillRecommendsFullEarnedPerSetHeadroomWhenTrendIsStable() {
        val previous=addWarmup(completed(1,3,reps=listOf(5,6),
            rirs=listOf(5,4)))
        val latest=addWarmup(completed(2,4,reps=listOf(5,6),
            rirs=listOf(5,4)))
        val next=addWarmup(snapshot(5))
        val result=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(next,"main",
                listOf(previous,latest),stock(),n(5),
                CoachReadiness.NORMAL,false,
                CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(listOf(8,8),result.nextSetSuggestions.map{
            it.proposedNextReps})
        assertTrue(result.capacityDropsFromPreviousSession.isEmpty())
        assertFalse(result.forecastLimitations.contains(
            RepForecastLimitation.SINGLE_EXPOSURE))
        val edit=CustomNextRepAmendmentDomain.propose(next,"main",result)
        assertTrue(edit.toString(),edit is CustomNextRepAmendmentResult.Proposed)
        assertEquals(listOf(8,8),
            (edit as CustomNextRepAmendmentResult.Proposed).amendment.nextMinimumReps)
        assertEquals(next.slots.single().sets.filter{
            it.prescription.type==DraftSetType.WARMUP},
            edit.amendment.amended.slots.single().sets.filter{
                it.prescription.type==DraftSetType.WARMUP})
    }

    private fun blockEntry(
        week:Int=5,day:Int=0
    )=snapshot(week,day).copy(
        blockId="new-block",weekInBlock=1)

    private fun baseline(
        next:CustomWorkoutDaySnapshot=blockEntry(),
        sessions:List<CustomCoachingHistoricalSession> =
            listOf(completed(1,4)),
        physical:VerifiedLoadStock?=stock()
    )=CustomBlockBaselineContinuityDomain.review(
        CustomBlockBaselineRequest(next,"main",sessions,physical))

    @Test fun adjacentBlocksMayCarryExactObservedContextButNeverEarnAnIncrease() {
        val latest=completed(2,4,rirs=listOf(null,2))
        val carried=baseline(sessions=listOf(completed(1,3),latest))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY,carried.status)
        assertEquals("workout-4-0",carried.previousWorkoutId)
        assertEquals("build",carried.previousBlockId)
        assertEquals("main",carried.previousSlotId)
        assertEquals(listOf(8,8),carried.priorWorkingSets.map{it.performedReps})
        assertEquals(listOf(null,2),carried.priorWorkingSets.map{it.actualRir})
        assertEquals(listOf(n(200),n(200)),carried.priorWorkingSets.map{it.priorLoad})
        assertFalse(carried.mayApplyProgression)
        assertFalse(carried.mayChangePrescription)
        assertFalse(carried.mayIssueWorkout)
        assertFalse(carried.mayModifyHistory)
        assertTrue(runCatching {
            (carried.priorWorkingSets as MutableList<CustomBlockBaselineSet>).clear()
        }.isFailure)
        // Coaching still rejects both rep/weight promotion across blocks.
        val coached=review(next=blockEntry(),
            sessions=listOf(completed(1,3),latest))
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,coached.status)
        assertNull(coached.loadReviewCandidate)
        assertTrue(coached.nextSetSuggestions.isEmpty())
        assertNull(latest.evidence.visits.first().actual?.actualRir)
    }

    @Test fun phaseAndPurposeChangesExposeOnlyFactsButNewSplitsRequireNewBaseline() {
        val current=blockEntry()
        val phase=baseline(next=current.copy(phase=TrainingPhase.STRENGTH))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY,
            phase.status)
        assertEquals(listOf(8,8),phase.priorWorkingSets.map{it.performedReps})
        assertFalse(phase.mayApplyProgression)
        val purpose=baseline(next=current.copy(
            sessionPurpose=ComposerSessionPurpose.STRENGTH_SKILL))
        assertEquals(CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY,
            purpose.status)
        assertNull(purpose.priorWorkingSets.firstOrNull{it.actualRir==null})
        assertFalse(purpose.mayIssueWorkout)
        assertEquals(CustomBlockBaselineStatus.PHASE_PURPOSE_OR_SPLIT_CHANGED,
            baseline(next=current.copy(split=CustomSplitIdentity.Freeform("PPL"))).status)
        assertEquals(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
            baseline(next=current.copy(stamp=current.stamp.copy(
                gymCatalogSourceRevision=3))).status)
        assertEquals(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
            baseline(next=current.copy(stamp=current.stamp.copy(
                athleteProfileRevision="new-profile"))).status)
    }

    @Test fun newWeightsRepsAndEquipmentCannotBorrowOldBlockWorkingTargets() {
        val current=blockEntry()
        fun amendedSets(change:(CustomAuthoredSet)->CustomAuthoredSet)=
            current.copy(slots=current.slots.map {slot ->
                slot.copy(sets=slot.sets.map {set ->
                    set.copy(prescription=change(set.prescription))
                })
            })
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(load=load(205))}).status)
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(reps=RepBand(6,10))}).status)
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            baseline(next=amendedSets{it.copy(targetRir=3)}).status)
        assertEquals(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
            baseline(physical=null).status)
        assertEquals(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
            baseline(physical=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.MACHINE_STACK,listOf(n(205),n(210)))).status)
        val distinctVariant=current.copy(slots=current.slots.map{
            it.copy(variantId="cable")
        })
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(next=distinctVariant).status)
        val anotherMovement=current.copy(slots=current.slots.map {
            it.copy(exerciseId="other_chest_press")
        })
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(next=anotherMovement).status)
    }

    @Test fun failedLatestSameDayCannotCherryPickEarlierPerfectExposure() {
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),
                completed(2,4,failed=setOf(0)))).status)
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),
                completed(2,4,reps=listOf(4,8)))).status)
        val altered=completed(2,4)
        val mismatch=altered.copy(evidence=altered.evidence.copy(
            visits=altered.evidence.visits.mapIndexed{index,visit ->
                if(index==0)visit.copy(actual=visit.actual?.copy(load=n(205)))
                else visit
            }))
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            baseline(sessions=listOf(completed(1,3),mismatch)).status)
        val moved=completed(2,4,exercise="different_machine")
        assertEquals(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
            baseline(sessions=listOf(completed(1,3),moved)).status)
    }

    private fun starting(
        next:CustomWorkoutDaySnapshot=blockEntry(),
        sessions:List<CustomCoachingHistoricalSession> = listOf(completed(1,4)),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,physical:VerifiedLoadStock?=stock()
    )=CustomBlockStartingLoadDomain.review(
        CustomBlockBaselineRequest(next,"main",sessions,physical),
        readiness,restricted)

    @Test fun newBlockStartingRecommendationKeepsEachSetsOwnApprovedWeight() {
        val prior=completed(1,4,issued=listOf(200,215))
        val next=blockEntry().copy(slots=blockEntry().slots.map {slot->
            slot.copy(sets=slot.sets.mapIndexed {index,set->
                set.copy(prescription=set.prescription.copy(
                    load=load(if(index==0)200 else 215)))
            })
        })
        val result=starting(next=next,sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,result.status)
        assertEquals(listOf(n(200),n(215)),result.startingSets.map{it.load})
        assertEquals(next.slots.single().sets.map{it.setKey},
            result.startingSets.map{it.setKey})
        assertEquals(listOf(1,2),result.startingSets.map{it.ordinal})
        assertTrue(result.startingSets.all{it.reps==band() && it.goalRir==2})
        assertFalse(result.mayApplyProgression)
        assertFalse(result.mayChangePrescription)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
        assertTrue(runCatching {
            (result.startingSets as MutableList<CustomBlockStartingSet>).clear()
        }.isFailure)
        assertEquals(listOf(n(200),n(215)),prior.evidence.visits.map{it.actual!!.load})
    }

    @Test fun unreportedEffortUsesOnlyOriginalGoalAndKeepsActualBlank() {
        val prior=completed(1,4,rirs=listOf(null,2))
        val result=starting(sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,result.status)
        assertEquals(1,result.assumedGoalRirSetCount)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,
            result.startingSets.first().effortSource)
        assertEquals(ProgressionEffortSource.REPORTED,
            result.startingSets.last().effortSource)
        assertNull(result.startingSets.first().actualRir)
        assertNull(prior.evidence.visits.first().actual!!.actualRir)
        assertTrue(result.explanation.contains("original goal RIR"))
    }

    @Test fun realNearFailureOverridesGoalAndBlocksStartingRecommendation() {
        for(rir in listOf(0,1)) {
            val result=starting(sessions=listOf(
                completed(1,4,rirs=listOf(rir,null))))
            assertEquals(CustomBlockStartingLoadStatus.NEAR_FAILURE_REQUIRES_REVIEW,
                result.status)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun noEffortSourceNeverBecomesAnAssumedStartingWeight() {
        fun withoutGoals(snapshot:CustomWorkoutDaySnapshot)=snapshot.copy(
            slots=snapshot.slots.map {slot->slot.copy(sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(targetRir=null))
            })})
        val original=completed(1,4,rirs=listOf(null,null))
        val prior=original.copy(snapshot=withoutGoals(original.snapshot),
            evidence=original.evidence.copy(visits=original.evidence.visits.map {
                it.copy(prescribedRir=null)
            }))
        val result=starting(next=withoutGoals(blockEntry()),sessions=listOf(prior))
        assertEquals(CustomBlockStartingLoadStatus.EFFORT_UNAVAILABLE,result.status)
        assertEquals(0,result.assumedGoalRirSetCount)
        assertTrue(result.startingSets.isEmpty())
        assertTrue(prior.evidence.visits.all{it.actual!!.actualRir==null})
    }

    @Test fun newBlockRecoveryAndMovementRestrictionTakePrecedence() {
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            starting(readiness=CoachReadiness.LOW).status)
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            starting(readiness=CoachReadiness.STRONG,restricted=true).status)
        assertEquals(starting().startingSets,
            starting(readiness=CoachReadiness.STRONG).startingSets)
    }

    @Test fun phaseAndPurposeReferencesNeverConvertIntoNewStartingWeights() {
        for(next in listOf(blockEntry().copy(phase=TrainingPhase.STRENGTH),
            blockEntry().copy(sessionPurpose=ComposerSessionPurpose.STRENGTH_SKILL))) {
            val result=starting(next=next)
            assertEquals(CustomBlockStartingLoadStatus.CHANGED_TRAINING_CONTEXT,
                result.status)
            assertEquals(2,result.baseline.priorWorkingSets.size)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun changedOrFailedLatestWorkCannotBecomeAStartingLoadRecommendation() {
        val current=blockEntry()
        val changed=current.copy(slots=current.slots.map {slot->
            slot.copy(sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(load=load(205)))
            })
        })
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            starting(next=changed).status)
        for(latest in listOf(completed(2,4,failed=setOf(0)),
            completed(2,4,reps=listOf(4,8)),
            completed(2,4,exercise="different_machine"))) {
            val result=starting(sessions=listOf(completed(1,3),latest))
            assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,result.status)
            assertTrue(result.startingSets.isEmpty())
        }
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            starting(physical=null).status)
    }

    @Test fun startingWeightsCannotBorrowOtherDaysOrSkipAnUntrainedWeek() {
        for(sessions in listOf(listOf(completed(1,4,day=2)),
            listOf(completed(1,3)),
            listOf(completed(1,4),completed(2,5)))) {
            val result=starting(sessions=sessions)
            assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,result.status)
            assertTrue(result.startingSets.isEmpty())
        }
    }

    @Test fun noBorrowingBetweenWeekdaysOrAfterMissingWeekOrFinishedTarget() {
        val otherDay=completed(1,4,2)
        assertEquals(CustomBlockBaselineStatus.NO_PRIOR_SAME_WEEKDAY,
            baseline(sessions=listOf(otherDay)).status)
        assertEquals(CustomBlockBaselineStatus.GAP_OR_SAME_BLOCK,
            baseline(sessions=listOf(completed(1,3))).status)
        assertEquals(CustomBlockBaselineStatus.NOT_BLOCK_ENTRY,
            baseline(next=blockEntry().copy(weekInBlock=2)).status)
        val finished=completed(2,5)
        assertEquals(CustomBlockBaselineStatus.TARGET_ALREADY_TRAINED,
            baseline(sessions=listOf(completed(1,4),finished)).status)
        val duplicate=completed(2,4).copy(completionOrder=1)
        assertEquals(CustomBlockBaselineStatus.INVALID_HISTORY,
            baseline(sessions=listOf(completed(1,3),duplicate)).status)
    }


    @Test fun optionalTargetRirV2CanRecommendSeveralRepsWithoutOneRepCap() {
        val older=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(5,4))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(older,latest),stock(),n(5),CoachReadiness.NORMAL,false,
            CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,result.status)
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,result.repForecastPolicy)
        assertEquals(listOf(8,8),result.nextSetSuggestions.map{it.proposedNextReps})
        assertTrue(result.forecastLimitations.contains(
            RepForecastLimitation.CHRONIC_FATIGUE_NOT_ESTIMATED))
        assertTrue(result.forecastLimitations.contains(
            RepForecastLimitation.EARLIER_SET_WORK_CHANGED))
        assertTrue(result.explanation.contains("Earlier harder sets"))
        assertNull(result.loadReviewCandidate)
        val amendment=CustomNextRepAmendmentDomain.propose(
            snapshot(5),"main",result) as CustomNextRepAmendmentResult.Proposed
        assertEquals(listOf(8,8),amendment.amendment.nextMinimumReps)
        assertTrue(amendment.amendment.amended.slots.single().sets.all {
            it.prescription.reps==RepBand(8,8) &&
                (it.prescription.load as DraftSetLoad.Verified).value==n(200)
        })
        val legacy=review(sessions=listOf(older,latest))
        assertEquals(CustomRepForecastPolicy.LEGACY_STEP_V1,legacy.repForecastPolicy)
        assertEquals(listOf(6,7),legacy.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(listOf(5,6),latest.evidence.visits.map{it.actual!!.reps})
    }

    @Test fun targetRirV2AndV3DoNotTreatOverCeilingRepCutsAsProgression() {
        val previous=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(9,5),rirs=listOf(2,5))
        for(version in listOf(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3)) {
            val result=CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(snapshot(5),"main",
                    listOf(previous,latest),stock(),n(5),
                    CoachReadiness.NORMAL,false,version))
            assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                result.status)
            assertTrue(result.nextSetSuggestions.isEmpty())
            assertNull(result.loadReviewCandidate)
            assertTrue(result.explanation.contains("exceeded"))
            assertTrue(CustomNextRepAmendmentDomain.propose(
                snapshot(5),"main",result) is CustomNextRepAmendmentResult.Rejected)
            // Over-ceiling actuals are NEVER rewritten in the source facts.
            assertEquals(listOf(9,5),latest.evidence.visits.map{it.actual?.reps})
        }
        // A previously issued V1 receipt retains its historical behavior.
        val legacy=review(sessions=listOf(previous,latest))
        assertEquals(CustomRepForecastPolicy.LEGACY_STEP_V1,legacy.repForecastPolicy)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,legacy.status)
        assertEquals(listOf(8,6),legacy.nextSetSuggestions.map{it.proposedNextReps})
    }

    @Test fun repeatedOverCeilingSuccessCanStillEarnVerifiedLoadReview() {
        val previous=completed(1,3,reps=listOf(8,8))
        val latest=completed(2,4,reps=listOf(9,8),rirs=listOf(2,2))
        val v2=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(snapshot(5),"main",
                listOf(previous,latest),stock(),n(5),
                CoachReadiness.NORMAL,false,CustomRepForecastPolicy.TARGET_RIR_V2))
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,v2.status)
        assertEquals(n(205),v2.loadReviewCandidate)
        assertTrue(v2.explanation.contains("higher LOAD review"))
        assertTrue(v2.nextSetSuggestions.none{it.changed})
        assertTrue(CustomNextLoadAmendmentDomain.propose(
            snapshot(5),"main",v2,stock(),n(5))
            is CustomNextLoadAmendmentResult.Proposed)
        assertTrue(CustomNextRepAmendmentDomain.propose(
            snapshot(5),"main",v2) is CustomNextRepAmendmentResult.Rejected)
    }

    @Test fun targetRirV2NeverTurnsAssumedEffortIntoExtraHeadroom() {
        val previous=completed(1,3,reps=listOf(5,5))
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(null,null))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(previous,latest),stock(),n(5),CoachReadiness.NORMAL,false,
            CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,result.status)
        assertEquals(listOf(5,6),result.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(2,result.assumedGoalRirSetCount)
        assertTrue(result.nextSetSuggestions.all {it.actualRir==null})
        assertTrue(CustomNextRepAmendmentDomain.propose(snapshot(5),"main",result)
            is CustomNextRepAmendmentResult.Rejected)
    }

    @Test fun v2AmendmentRejectsForgedTargetsAboveObservedRirHeadroom() {
        val latest=completed(2,4,reps=listOf(5,6),rirs=listOf(4,3))
        val request=CustomNextWorkoutCoachingRequest(snapshot(5),"main",
            listOf(completed(1,3),latest),stock(),n(5),
            CoachReadiness.NORMAL,false,CustomRepForecastPolicy.TARGET_RIR_V2)
        val result=CustomNextWorkoutCoachingDomain.review(request)
        assertEquals(listOf(7,7),result.nextSetSuggestions.map{it.proposedNextReps})
        val forged=result.copy(nextSetSuggestions=result.nextSetSuggestions.mapIndexed {
            i,value-> if(i==0)value.copy(proposedNextReps=8) else value
        })
        assertEquals(CustomNextRepAmendmentResult.Rejected(
            CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED),
            CustomNextRepAmendmentDomain.propose(snapshot(5),"main",forged))
    }
    @Test fun V2AndV3NeverSkipMixedOrUnassignedMachineHistoryToEarnProgression() {
        // One athlete, one logical exercise and training day, two distinct
        // physical presses. Unknown sets can belong to either station.
        fun station(order:Long,week:Int,machine:String)=
            completed(order,week).let {base->
                base.copy(evidence=base.evidence.copy(visits=
                    base.evidence.visits.map {visit->
                        visit.copy(actual=visit.actual?.copy(
                            machineInstanceId=machine))
                    }))
            }
        val a1=station(1,1,"A")
        val b2=station(2,2,"B")
        val a3=station(3,3,"A")
        val a4=station(4,4,"A")
        val mixed=b2.copy(evidence=b2.evidence.copy(visits=
            b2.evidence.visits.mapIndexed {index,visit->
                visit.copy(actual=visit.actual?.copy(
                    machineInstanceId=if(index==0)"A" else "B"))
            }))
        // The completed workout exists, but both main working sets were
        // skipped without a physical machine observation.
        val unrecorded=b2.copy(evidence=b2.evidence.copy(visits=
            b2.evidence.visits.map {visit->
                visit.copy(outcome=KotlinJournalSetOutcome.SKIPPED,actual=null)
            }))
        val legacy=b2.copy(evidence=b2.evidence.copy(visits=
            b2.evidence.visits.map {visit->
                visit.copy(actual=visit.actual?.copy(machineInstanceId=null))
            }))
        fun machineReview(
            sessions:List<CustomCoachingHistoricalSession>,
            nextWeek:Int,policy:CustomRepForecastPolicy,
            machine:String="A"
        )=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(
                snapshot(nextWeek),"main",sessions,stock(),n(5),
                CoachReadiness.NORMAL,false,policy,
                requestedMachineInstanceId=machine))
        for(policy in listOf(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3)) {
            // Legitimate B sessions do NOT erase two independent A exposures.
            val bBetween=machineReview(listOf(a1,b2,a3),4,policy)
            assertEquals(policy.toString(),
                CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                bBetween.status)
            assertEquals(listOf("workout-1-0","workout-3-0"),
                bBetween.comparedWorkoutIds)
            assertEquals(n(205),bBetween.loadReviewCandidate)

            for(unknown in listOf(mixed,unrecorded,legacy)) {
                // Without a fence, these would cherry-pick the two
                // successful A workouts and offer a phantom 205-lb step.
                val held=machineReview(listOf(a1,unknown,a3),4,policy)
                assertEquals("$policy, ${unknown.evidence.visits}",
                    CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
                    held.status)
                assertTrue(held.nextSetSuggestions.isEmpty())
                assertNull(held.loadReviewCandidate)
                assertTrue(held.explanation.contains("mixed or unassigned"))
                assertFalse(held.mayIssueWorkout)
                assertFalse(held.mayEditProgram)
            }

            // A failed/unassigned workout AFTER the latest A is also an
            // unresolved next-session boundary, not an invitation to revive
            // the two older successful A workouts.
            val after=completed(4,4).let {base ->
                base.copy(evidence=base.evidence.copy(visits=
                    base.evidence.visits.map {visit ->
                        visit.copy(outcome=KotlinJournalSetOutcome.SKIPPED,
                            actual=null)
                    }))
            }
            val newestUnknown=machineReview(listOf(a1,a3,after),5,policy)
            assertEquals(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
                newestUnknown.status)
            assertNull(newestUnknown.loadReviewCandidate)
            assertTrue(newestUnknown.nextSetSuggestions.isEmpty())

            // Once two fresh, fully labelled A visits follow the ambiguous
            // one, that older uncertainty cannot suppress A forever.
            val recovered=machineReview(listOf(a1,mixed,a3,a4),5,policy)
            assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                recovered.status)
            assertEquals(listOf("workout-3-0","workout-4-0"),
                recovered.comparedWorkoutIds)
            assertEquals(n(205),recovered.loadReviewCandidate)

            // A later, fully identified B session cannot invalidate A's
            // two latest authenticated visits or alter A's 200-lb baseline.
            val newerB=station(5,5,"B")
            val lateB=machineReview(listOf(a1,a3,a4,newerB),6,policy)
            assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                lateB.status)
            assertEquals(listOf("workout-3-0","workout-4-0"),
                lateB.comparedWorkoutIds)
            assertEquals(n(205),lateB.loadReviewCandidate)
        }
    }

    @Test fun V2AndV3MachineProgressionCannotJumpOverInterveningGymOrProgramRevision() {
        fun onStation(order:Long,week:Int,id:String)=
            completed(order,week).let {entry ->
                entry.copy(evidence=entry.evidence.copy(visits=
                    entry.evidence.visits.map {visit->
                        visit.copy(actual=visit.actual?.copy(
                            machineInstanceId=id))
                    }))
            }
        val a1=onStation(1,1,"A")
        val b2=onStation(2,2,"B")
        val a3=onStation(3,3,"A")
        val a4=onStation(4,4,"A")
        val changed=listOf(
            b2.copy(snapshot=b2.snapshot.copy(stamp=
                b2.snapshot.stamp.copy(gymId="gym-2"))),
            b2.copy(snapshot=b2.snapshot.copy(stamp=
                b2.snapshot.stamp.copy(gymCatalogSourceRevision=3))),
            b2.copy(snapshot=b2.snapshot.copy(phase=TrainingPhase.STRENGTH)),
            b2.copy(snapshot=b2.snapshot.copy(
                sessionPurpose=ComposerSessionPurpose.STRENGTH_SKILL)),
            b2.copy(snapshot=b2.snapshot.copy(
                split=CustomSplitIdentity.Freeform("PPL"))),
            b2.copy(snapshot=b2.snapshot.copy(
                blockPurpose=CustomBlockPurpose.RECOVERY))
        )
        fun reviewMachine(
            sessions:List<CustomCoachingHistoricalSession>,
            next:Int,policy:CustomRepForecastPolicy
        )=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(snapshot(next),"main",
                sessions,stock(),n(5),CoachReadiness.NORMAL,false,policy,
                requestedMachineInstanceId="A"))
        for(policy in listOf(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3)) {
            val normal=reviewMachine(listOf(a1,b2,a3),4,policy)
            assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                normal.status)
            assertEquals(n(205),normal.loadReviewCandidate)
            for(intervening in changed) {
                val refusal=reviewMachine(listOf(a1,intervening,a3),4,policy)
                assertEquals("$policy: ${intervening.snapshot}",
                    CustomNextWorkoutCoachingStatus.CHANGED_COACHING_SOURCE,
                    refusal.status)
                assertEquals(listOf("workout-1-0","workout-3-0"),
                    refusal.comparedWorkoutIds)
                assertTrue(refusal.nextSetSuggestions.isEmpty())
                assertNull(refusal.loadReviewCandidate)
                assertTrue(refusal.explanation.contains("source"))
                assertFalse(refusal.mayIssueWorkout)
                assertFalse(refusal.mayEditProgram)

                // Historical version drift is not a permanent penalty.
                // Two new comparable A workouts following the changed
                // session re-establish the athlete's current A baseline.
                val fresh=reviewMachine(
                    listOf(a1,intervening,a3,a4),5,policy)
                assertEquals("$policy: renewed A sessions",
                    CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                    fresh.status)
                assertEquals(listOf("workout-3-0","workout-4-0"),
                    fresh.comparedWorkoutIds)
                assertEquals(n(205),fresh.loadReviewCandidate)
            }
        }
    }

}
