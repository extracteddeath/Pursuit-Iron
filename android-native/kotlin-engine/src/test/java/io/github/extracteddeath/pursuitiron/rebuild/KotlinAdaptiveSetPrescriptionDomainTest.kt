package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Actual complete Kotlin session snapshots: no per-set ghost weights,
 * no unsupported jumps, no first-set capacity mirroring, no destruction of other exercises.
 */
class KotlinAdaptiveSetPrescriptionDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("25"),n("30"),n("35")))
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-2")
    private val grant=KotlinWorkoutSelectionGrant("next-grant",
        "next-session",source,1)
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(id,if(role==ComposerRole.PRIMARY)
            ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,3,3,3,3,1,2,role=role,
            sourcePracticalReps=RepBand(10,15))
    private fun approved():ComposerRequest {
        val bounds=TrainingMuscle.entries.map {muscle ->
            if(muscle==TrainingMuscle.CHEST || muscle==TrainingMuscle.LATS)
                DeclaredMuscleDoseBounds(muscle,n("2"),n("10"),n("12"),1)
            else DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
        return ComposerRequest(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map {
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,120,null,60))
            },bounds,listOf(
                option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY),
                option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)),
            2,1,listOf(0,1,2).map {
                ComposerSessionIntent(it,ComposerSessionPurpose.HYPERTROPHY)
            })
    }
    private fun policies()=listOf("db-press","db-row").map {
        ExercisePrescriptionPolicy(
            it,ComposerSessionPurpose.HYPERTROPHY,
            RepBand(10,15),2,90,120,SetLoadScheme.STRAIGHT)
    }
    private fun stagedRequest(
        program:ComposerRequest=approved(),
        g:KotlinWorkoutSelectionGrant=grant,
        p:List<ExercisePrescriptionPolicy> = policies()
    )=KotlinWorkoutSnapshotRequest(source,source,g,program,p)
    private fun observed(
        reps:List<Int> = listOf(14,13,12),
        rir:List<Int?> = listOf(3,3,2),
        actualLoads:List<String> = listOf("30","30","30"),
        issuedLoads:List<String> = listOf("30","30","30"),
        id:String="previous-session",
        band:RepBand=RepBand(10,15)
    )=ProgressionWorkoutEvidence(
        id,"db-press",LoadUnit.LB,band,3,
        (0..2).map {i->
            WorkingSetFact(i,SetOutcome.COMPLETED,
                n(actualLoads[i]),reps[i],rir[i],n(issuedLoads[i]),2)
        })
    private fun request(
        previous:ProgressionWorkoutEvidence=observed(),
        next:KotlinWorkoutSnapshotRequest=stagedRequest(),
        historicalSource:KotlinTrainingSourceRevision=source,
        historyRevision:Long=7,
        currentHistoryRevision:Long=7,
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,
        issuedExactReps:List<Int>?=null
    )=AdaptiveSetPrescriptionRequest(
        next,historicalSource,previous,historyRevision,
        currentHistoryRevision,readiness,restricted,issuedExactReps)
    private fun candidate(
        req:AdaptiveSetPrescriptionRequest=request()
    ):AdaptiveSetPrescriptionResult.Candidate {
        val result=KotlinAdaptiveSetPrescriptionDomain.draft(req)
        assertTrue(result.toString(),result is AdaptiveSetPrescriptionResult.Candidate)
        return result as AdaptiveSetPrescriptionResult.Candidate
    }

    @Test fun constructsActualPerSetTargetsAtIntendedEffortAcrossSeveralSets() {
        val original=KotlinWorkoutSnapshotDomain.stage(stagedRequest())
        assertTrue(original.toString(),original is KotlinWorkoutSnapshotResult.Staged)
        val base=(original as KotlinWorkoutSnapshotResult.Staged).snapshot
        val reviewed=candidate()
        val next=reviewed.workout
        assertEquals("next-session",next.workoutId)
        assertEquals("next-grant",next.grantId)
        assertEquals(7L,reviewed.historicalRevision)
        assertEquals("previous-session",reviewed.historicalWorkoutId)
        assertEquals("db-press:d1:s0",reviewed.editedSetKey)
        assertEquals(listOf(14,13,12),reviewed.observedReps)
        assertEquals(listOf(15,14,12),reviewed.newRepTargets)
        val press=next.targets.filter {it.exerciseId=="db-press"}
        assertEquals(listOf(RepBand(15,15),RepBand(14,14),RepBand(12,12)),
            press.map {it.reps})
        assertEquals(listOf(n("30"),n("30"),n("30")),
            press.map {(it.load as DraftSetLoad.Verified).value})
        assertTrue(press.all {it.targetRir==2 && it.restSeconds==120})
        assertEquals(base.targets.filter {it.exerciseId=="db-row"},
            next.targets.filter {it.exerciseId=="db-row"})
        assertEquals(base.targets.map {it.setKey},
            next.targets.map {it.setKey})
        assertEquals(base.estimatedMinutes,next.estimatedMinutes)
        assertEquals(base.source,next.source)
        assertFalse(reviewed.mayAutoApply)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(next.persisted)
        assertFalse(next.canModifyJournal)
        assertTrue(base.targets.filter {it.exerciseId=="db-press"}.all {
            it.reps==RepBand(10,15)
        })
    }

    @Test fun assumedOriginalGoalDoesNotInventExtraHeadroomAtTheSameGoal() {
        val previous=observed(rir=listOf(null,null,null)).let {w->w.copy(
            observations=w.observations.map {it.copy(issuedRir=3)})}
        val next=stagedRequest(p=policies().map {it.copy(targetRir=3)})
        assertTrue(KotlinAdaptiveSetPrescriptionDomain.draft(request(previous=previous,next=next))
            is AdaptiveSetPrescriptionResult.Rejected)
        assertTrue(previous.observations.all {it.actualRir==null})
        // Changing the FUTURE goal is distinct from inventing historical effort.
        val lowerGoal=candidate(request(previous=previous,next=stagedRequest()))
        assertEquals(listOf(15,14,13),lowerGoal.newRepTargets)
        assertEquals(3,lowerGoal.assumedGoalRirSetCount)
        assertTrue(previous.observations.all {it.actualRir==null && it.issuedRir==3})
    }

    @Test fun aMissedExactGoalDoesNotSuppressOtherSetsWithSufficientReserve() {
        val last=listOf(14,14,12)
        val proposal=candidate(request(previous=observed(),issuedExactReps=last))
        assertEquals(listOf(15,14,12),proposal.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS,proposal.decisionKind)
        val advanced=candidate(request(previous=observed(reps=last,rir=listOf(3,3,3)),
            issuedExactReps=last))
        assertEquals(listOf(15,15,13),advanced.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS,advanced.decisionKind)
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(request(issuedExactReps=listOf(14,14)))
                as AdaptiveSetPrescriptionResult.Rejected).reason)
    }

    @Test fun anOvershotEffortTargetCanReduceRepsInsteadOfRepeatingAnUnrealisticGoal() {
        val result=candidate(request(previous=observed(rir=listOf(3,3,0)),
            issuedExactReps=listOf(15,14,12)))
        assertEquals(listOf(15,14,10),result.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS,result.decisionKind)
    }

    @Test fun legacyAcceptedPolicyCanBeReplayedButIsNotUsedForNewReviews() {
        val legacy=candidate(request().copy(policyVersion=AdaptiveRepPolicyVersion.LEGACY_SINGLE_SET_V1))
        assertEquals(listOf(14,14,12),legacy.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP,legacy.decisionKind)
        assertEquals(listOf(15,14,12),candidate().newRepTargets)
    }

    @Test fun meetingGoalEffortKeepsAcceptedExactTargetsInsteadOfResettingTheRange() {
        val exact=listOf(14,14,12)
        for(rir in listOf(listOf(2,2,2),listOf(null,null,null))) {
            val previous=observed(reps=exact,rir=rir)
            val result=candidate(request(previous=previous,issuedExactReps=exact))
            assertEquals(exact,result.newRepTargets)
            assertEquals(AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS,
                result.decisionKind)
            assertEquals(if(rir.first()==null)3 else 0,result.assumedGoalRirSetCount)
            assertEquals(exact.map{RepBand(it,it)},result.workout.targets.filter {
                it.exerciseId=="db-press"
            }.map{it.reps})
            assertEquals(rir,previous.observations.map{it.actualRir})
            assertFalse(result.mayAutoApply)
            assertFalse(result.mayIssueWorkout)
        }
    }

    @Test fun ceilingTargetsCanBeRepeatedWithoutInventingAnotherRepOrLoadIncrease() {
        val result=candidate(request(previous=observed(
            reps=listOf(15,15,15),rir=listOf(2,2,2)),
            issuedExactReps=listOf(15,15,15)))
        assertEquals(listOf(15,15,15),result.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS,
            result.decisionKind)
        assertTrue(result.workout.targets.filter{it.exerciseId=="db-press"}.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("30"))==0
        })
    }

    @Test fun multipleMissedExactGoalsRepeatInsteadOfInflatingOrResettingTargets() {
        val old=listOf(14,14,12)
        val before=observed(reps=listOf(13,13,11),
            rir=listOf(3,3,3))
        val result=candidate(request(previous=before,issuedExactReps=old))
        assertEquals(AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS,
            result.decisionKind)
        assertEquals(old,result.newRepTargets)
        assertEquals(listOf(13,13,11),result.observedReps)
        assertTrue(result.workout.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.map{it.reps.minimum}==old)
        assertEquals(listOf(13,13,11),
            before.observations.map{it.actualReps})
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun repeatTargetsCannotBypassRecoveryIncompleteEffortOrLoadingGuards() {
        val exact=listOf(14,14,12)
        val safe=observed(reps=exact,rir=listOf(2,2,2))
        val cases=listOf(
            request(previous=safe,readiness=CoachReadiness.LOW,issuedExactReps=exact),
            request(previous=safe,restricted=true,issuedExactReps=exact),
            request(previous=observed(reps=listOf(14,13,10),rir=listOf(null,null,0)),issuedExactReps=exact),
            request(previous=observed(reps=listOf(14,9,12)),issuedExactReps=exact),
            request(previous=observed(actualLoads=listOf("30","35","30")),issuedExactReps=exact),
            request(previous=safe.copy(observations=safe.observations.map {
                it.copy(actualRir=null,issuedRir=null)
            }),issuedExactReps=exact),
            request(previous=safe.copy(observations=safe.observations.mapIndexed {i,s->
                if(i==1)s.copy(outcome=SetOutcome.SKIPPED,actualReps=null,actualLoad=null,actualRir=null) else s
            }),issuedExactReps=exact),
            request(previous=safe,issuedExactReps=listOf(16,14,12))
        )
        for(input in cases) {
            val result=KotlinAdaptiveSetPrescriptionDomain.draft(input)
            assertTrue(result.toString(),result is AdaptiveSetPrescriptionResult.Rejected)
        }
    }

    @Test fun unavailableEvidenceOrUnsatisfiedConstraintsCannotGenerateNextTargets() {
        val cases=listOf(
            request(previous=observed(rir=listOf(3,null,2)).let {w->w.copy(
                observations=w.observations.map {s->
                    if(s.workingOrdinal==1)s.copy(issuedRir=null) else s
                })}),
            request(previous=observed(rir=listOf(2,2,2))),
            request(previous=observed(reps=listOf(14,9,12))),
            request(previous=observed(reps=listOf(15,15,15))),
            request(readiness=CoachReadiness.LOW),
            request(restricted=true)
        )
        for(input in cases) {
            val result=KotlinAdaptiveSetPrescriptionDomain.draft(input)
            assertEquals(result.toString(),
                AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                (result as AdaptiveSetPrescriptionResult.Rejected).reason)
        }
    }

    @Test fun noUserEditedMixedOrUnbuildableWeightTransfers() {
        for(input in listOf(
            request(previous=observed(actualLoads=listOf("30","35","30"))),
            request(previous=observed(issuedLoads=listOf("25","30","30"))),
            request(previous=observed(actualLoads=listOf("35","35","35"),
                issuedLoads=listOf("35","35","35"))),
            request(next=stagedRequest(program=approved().copy(
                exercises=approved().exercises.map {e->
                    if(e.exerciseId=="db-press")
                        e.copy(equipment=ComposerEquipment.Verified(
                            LoadUnit.LB,n("32.5"),stock))
                    else e
                })))
        )) {
            assertTrue(KotlinAdaptiveSetPrescriptionDomain.draft(input)
                is AdaptiveSetPrescriptionResult.Rejected)
        }
    }

    @Test fun sameExerciseMustKeepExactlyItsPurposeBandAndThreeSets() {
        val original=observed()
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=original.copy(
                    targetReps=RepBand(8,12)))) as
                    AdaptiveSetPrescriptionResult.Rejected).reason)
        val wrong=original.copy(exerciseId="not-in-program")
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=wrong)) as
                    AdaptiveSetPrescriptionResult.Rejected).reason)
        val unmatched=original.copy(expectedWorkingSets=2,
            observations=original.observations.take(2))
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=unmatched)) as
                    AdaptiveSetPrescriptionResult.Rejected).reason)
    }

    @Test fun neverOverwriteWarmupsOrTopBackoffSchemesWithStraightSetProgression() {
        val w=DeclaredWarmupStep(n("25"),8,60,60)
        val changed=policies().map {
            if(it.exerciseId=="db-press")it.copy(warmups=listOf(w)) else it
        }
        val result=KotlinAdaptiveSetPrescriptionDomain.draft(
            request(next=stagedRequest(p=changed)))
        assertEquals(AdaptiveSetPrescriptionRefusal.UNSUPPORTED_SET_SCHEME,
            (result as AdaptiveSetPrescriptionResult.Rejected).reason)
        val backoff=policies().map {
            if(it.exerciseId=="db-press") it.copy(
                scheme=SetLoadScheme.LAST_SET_BACKOFF,
                maximumAuthorizedBackoff=n("5"),
                backoffReps=RepBand(10,15),backoffRir=2
            ) else it
        }
        val result2=KotlinAdaptiveSetPrescriptionDomain.draft(
            request(next=stagedRequest(p=backoff)))
        // The backoff itself is now supported when its rep/RIR targets
        // match, but a historical uniform 30-lb day is NOT comparable
        // to the new authentic 30/30/25-lb backoff prescription.
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (result2 as AdaptiveSetPrescriptionResult.Rejected).reason)
    }

    @Test fun staleHistoryRevisionAndChangedAthleteGymProgramCannotShapeWorkout() {
        val requests=listOf(
            request(currentHistoryRevision=8),
            request(historicalSource=source.copy(gymRevision=4)),
            request(historicalSource=source.copy(programRevision=7)),
            request(previous=observed(id="next-session")),
            request(next=stagedRequest().copy(currentSource=source.copy(
                coachingRulesRevision="coach-3")))
        )
        requests.forEach {input ->
            assertEquals(AdaptiveSetPrescriptionRefusal.STALE_SOURCE_OR_HISTORY,
                (KotlinAdaptiveSetPrescriptionDomain.draft(input) as
                    AdaptiveSetPrescriptionResult.Rejected).reason)
        }
        assertEquals(AdaptiveSetPrescriptionRefusal.APPROVED_PROGRAM_UNRESOLVED,
            (KotlinAdaptiveSetPrescriptionDomain.draft(request(
                next=stagedRequest(program=approved().copy(
                    days=approved().days.take(2))))) as
                AdaptiveSetPrescriptionResult.Rejected).reason)
    }

    @Test fun completedPrescriptionIsImmutableAndNeverModifiesObservedHistory() {
        val history=observed()
        val result=candidate(request(previous=history))
        assertEquals(listOf(14,13,12),history.observations.map {it.actualReps})
        assertEquals(listOf(n("30"),n("30"),n("30")),
            history.observations.map {it.actualLoad})
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.workout.targets as MutableList<KotlinWorkoutTarget>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (result.newRepTargets as MutableList<Int>).clear()
        }
    }

    @Test fun purposeSpecificPhysicalLoadCanEarnOneRepWithoutChangingWeight() {
        val different=policies().map {p->
            if(p.exerciseId=="db-press")p.copy(purposeWorkingLoad=n("25"))
            else p
        }
        val last=observed(
            actualLoads=listOf("25","25","25"),
            issuedLoads=listOf("25","25","25"))
        val reviewed=candidate(request(previous=last,
            next=stagedRequest(p=different)))
        assertEquals(listOf(15,14,12),reviewed.newRepTargets)
        val prescribed=reviewed.workout.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }
        assertEquals(3,prescribed.size)
        assertTrue(prescribed.all {
            (it.load as DraftSetLoad.Verified).value==n("25")
        })
        assertFalse(reviewed.mayIssueWorkout)
        assertEquals(n("30"),
            ((approved().exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
    }

    @Test fun purposeLoadProgressionRefusesOtherIssuedOrActualWeights() {
        val different=policies().map {p->
            if(p.exerciseId=="db-press")p.copy(purposeWorkingLoad=n("25"))
            else p
        }
        for(last in listOf(
            observed(actualLoads=listOf("25","30","25"),
                issuedLoads=listOf("25","25","25")),
            observed(actualLoads=listOf("25","25","25"),
                issuedLoads=listOf("30","30","30")),
            observed(actualLoads=listOf("30","30","30"),
                issuedLoads=listOf("30","30","30"))
        )) {
            assertTrue(KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=last,next=stagedRequest(p=different)))
                    is AdaptiveSetPrescriptionResult.Rejected)
        }
        val invalid=different.map {p->
            if(p.exerciseId=="db-press")p.copy(purposeWorkingLoad=n("32.5"))
            else p
        }
        val rejected=KotlinAdaptiveSetPrescriptionDomain.draft(
            request(previous=observed(actualLoads=listOf("25","25","25"),
                issuedLoads=listOf("25","25","25")),
                next=stagedRequest(p=invalid)))
        assertEquals(AdaptiveSetPrescriptionRefusal.APPROVED_PROGRAM_UNRESOLVED,
            (rejected as AdaptiveSetPrescriptionResult.Rejected).reason)
    }


    @Test fun dayScopedRepPolicyUsesThatDaysEffortInsteadOfSharedPurposeDefaults() {
        val individualized=policies()+policies().first {
            it.exerciseId=="db-press"
        }.copy(dayIndex=1,purposeWorkingLoad=n("25"),
            workingReps=RepBand(10,15),targetRir=3,restSecondsPerSet=180)
        val draft=KotlinWorkoutSnapshotDomain.stage(
            stagedRequest(p=individualized))
        val issued=(draft as KotlinWorkoutSnapshotResult.Staged).snapshot
        assertTrue(issued.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25") &&
            it.targetRir==3 && it.restSeconds==180})
        val previous=observed(actualLoads=listOf("25","25","25"),
            issuedLoads=listOf("25","25","25"),rir=listOf(4,4,3))
        val reviewed=candidate(request(previous=previous,
            next=stagedRequest(p=individualized)))
        assertEquals(listOf(15,14,12),reviewed.newRepTargets)
        assertTrue(reviewed.workout.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
        assertFalse(reviewed.mayIssueWorkout)
    }


    private fun matchingBackoffPolicy(
        backoffReps:RepBand=RepBand(10,15),backoffRir:Int?=2
    )=policies().map{p->
        if(p.exerciseId=="db-press")p.copy(
            scheme=SetLoadScheme.LAST_SET_BACKOFF,
            maximumAuthorizedBackoff=n("5"),
            backoffReps=backoffReps,backoffRir=backoffRir
        ) else p
    }

    @Test fun authenticBackoffKeepsEachLoadAndUsesEachSetsEffort() {
        val last=observed(
            reps=listOf(14,13,12),rir=listOf(3,3,4),
            actualLoads=listOf("30","30","25"),
            issuedLoads=listOf("30","30","25"))
        val base=(KotlinWorkoutSnapshotDomain.stage(stagedRequest(
            p=matchingBackoffPolicy())) as KotlinWorkoutSnapshotResult.Staged).snapshot
        assertEquals(listOf(n("30"),n("30"),n("25")),
            base.targets.filter{it.exerciseId=="db-press" &&
                it.type==DraftSetType.WORKING}.map{
                (it.load as DraftSetLoad.Verified).value
            })
        val next=candidate(request(previous=last,
            next=stagedRequest(p=matchingBackoffPolicy())))
        assertEquals(listOf(15,14,14),next.newRepTargets)
        assertEquals("db-press:d1:s0",next.editedSetKey)
        assertEquals(AdaptiveRepDecisionKind.TARGET_RIR_ALIGNED_REPS,
            next.decisionKind)
        val rows=next.workout.targets.filter{
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }
        assertEquals(listOf(n("30"),n("30"),n("25")),
            rows.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(listOf(RepBand(15,15),RepBand(14,14),RepBand(14,14)),
            rows.map{it.reps})
        assertEquals(base.targets.filter{it.exerciseId=="db-row"},
            next.workout.targets.filter{it.exerciseId=="db-row"})
        assertFalse(next.mayIssueWorkout)
    }

    @Test fun backoffDoesNotBorrowOtherRepRirOrEditedSetLoads() {
        val correct=observed(
            reps=listOf(14,13,12),rir=listOf(3,3,4),
            actualLoads=listOf("30","30","25"),
            issuedLoads=listOf("30","30","25"))
        val differentReps=KotlinAdaptiveSetPrescriptionDomain.draft(
            request(previous=correct,next=stagedRequest(
                p=matchingBackoffPolicy(backoffReps=RepBand(12,15)))))
            as AdaptiveSetPrescriptionResult.Rejected
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            differentReps.reason)
        val differentEffort=KotlinAdaptiveSetPrescriptionDomain.draft(
            request(previous=correct,next=stagedRequest(
                p=matchingBackoffPolicy(backoffRir=1))))
            as AdaptiveSetPrescriptionResult.Rejected
        assertEquals(AdaptiveSetPrescriptionRefusal.UNSUPPORTED_SET_SCHEME,
            differentEffort.reason)
        val changed=correct.copy(observations=correct.observations.mapIndexed{i,row->
            if(i==2) row.copy(actualLoad=n("30")) else row
        })
        assertEquals(AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=changed,next=stagedRequest(
                    p=matchingBackoffPolicy()))) as AdaptiveSetPrescriptionResult.Rejected).reason)
        val forged=correct.copy(observations=correct.observations.mapIndexed{i,row->
            if(i==2)row.copy(issuedLoad=n("30")) else row
        })
        assertEquals(AdaptiveSetPrescriptionRefusal.HISTORY_NOT_COMPARABLE,
            (KotlinAdaptiveSetPrescriptionDomain.draft(
                request(previous=forged,next=stagedRequest(
                    p=matchingBackoffPolicy()))) as AdaptiveSetPrescriptionResult.Rejected).reason)
    }

}
