package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Actual complete Kotlin session snapshots: no per-set ghost weights,
 * no +3-rep jumps, no all-set mirroring, no destruction of other exercises.
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
        restricted:Boolean=false
    )=AdaptiveSetPrescriptionRequest(
        next,historicalSource,previous,historyRevision,
        currentHistoryRevision,readiness,restricted)
    private fun candidate(
        req:AdaptiveSetPrescriptionRequest=request()
    ):AdaptiveSetPrescriptionResult.Candidate {
        val result=KotlinAdaptiveSetPrescriptionDomain.draft(req)
        assertTrue(result.toString(),result is AdaptiveSetPrescriptionResult.Candidate)
        return result as AdaptiveSetPrescriptionResult.Candidate
    }

    @Test fun constructsActualPerSetTargetsWithExactlyOneEarnedExtraRep() {
        val original=KotlinWorkoutSnapshotDomain.stage(stagedRequest())
        assertTrue(original.toString(),original is KotlinWorkoutSnapshotResult.Staged)
        val base=(original as KotlinWorkoutSnapshotResult.Staged).snapshot
        val reviewed=candidate()
        val next=reviewed.workout
        assertEquals("next-session",next.workoutId)
        assertEquals("next-grant",next.grantId)
        assertEquals(7L,reviewed.historicalRevision)
        assertEquals("previous-session",reviewed.historicalWorkoutId)
        assertEquals("db-press:d1:s1",reviewed.editedSetKey)
        assertEquals(listOf(14,13,12),reviewed.observedReps)
        assertEquals(listOf(14,14,12),reviewed.newRepTargets)
        val press=next.targets.filter {it.exerciseId=="db-press"}
        assertEquals(listOf(RepBand(14,14),RepBand(14,14),RepBand(12,12)),
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

    @Test fun noUnobservedRepsNearFailureOrMissingEffortCanGenerateNextTargets() {
        val cases=listOf(
            request(previous=observed(rir=listOf(3,null,2))),
            request(previous=observed(rir=listOf(3,3,1))),
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
        assertEquals(AdaptiveSetPrescriptionRefusal.UNSUPPORTED_SET_SCHEME,
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
}
