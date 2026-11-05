package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Synthetic timeline rules; real disk ownership is enforced by coordinator. */
class NativeKotlinCurrentLoadCoachReviewTest {
    private fun n(value: String)=BigDecimal(value)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private val verified=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("30"))
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(
            id,if(muscle==TrainingMuscle.CHEST)
                ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,2,3,2,1,2,role=role)
    private fun program():ComposerRequest {
        val window=DeclaredSessionWindow(1,120,null,60)
        val bounds=TrainingMuscle.entries.map { muscle ->
            if(muscle in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS))
                DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
            else DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
        return ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(
                ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,
            listOf(option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY),
                option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)),
            3,1,listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)))
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose)=
        ExercisePrescriptionPolicy(id,purpose,RepBand(8,12),2,90,120,
            SetLoadScheme.STRAIGHT)
    private val policies=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY))
    private fun approval()=NativeKotlinWorkoutApproval(
        "owner-1","athlete-1",source,source,
        KotlinWorkoutSelectionGrant("grant-1","workout-1",source,0),
        program(),policies,"receipt-1",1)

    private fun composer(i:Int,sourceOverride:KotlinTrainingSourceRevision=source,
        completedReps:List<Int> = listOf(12,12),
        purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY):
        NativeKotlinMixedHistoryCompletion.Composer {
        val facts=completedReps.mapIndexed { ordinal,reps ->
            KotlinHistoricalSetFact(
                "db-press","db-press:d1:s$ordinal",DraftSetType.WORKING,
                ordinal,verified,RepBand(8,12),2,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.LB,reps,2),
                "event-$i-$ordinal",null)
        }
        val observed=ProgressionWorkoutEvidence(
            "workout-$i","db-press",LoadUnit.LB,RepBand(8,12),2,
            completedReps.mapIndexed { index,reps ->
                WorkingSetFact(index,SetOutcome.COMPLETED,n("30"),reps,2,n("30"),2)
            })
        val assessed=DoubleProgressionEvidenceDomain.assess(
            observed.targetReps,observed.expectedWorkingSets,observed.observations)
        val review=KotlinExerciseHistoryEvidence(
            "db-press",facts,KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW,
            assessed,observed)
        val manifest=KotlinHistoryManifestEntry(i.toLong(),"workout-$i",
            "receipt-$i","finish-$i",3,0)
        return NativeKotlinMixedHistoryCompletion.Composer(
            manifest,KotlinWorkoutHistoryEvidence(sourceOverride,
                "workout-$i","finish-$i",3,facts,listOf(review)),purpose)
    }
    /**
     * Projector preserves the actual immutable issued narrow bands. Only the
     * next coach calculation can restore the original approved 8–12 range.
     */
    private fun withExactIssuedTargets(
        original:NativeKotlinMixedHistoryCompletion.Composer,
        issued:List<RepBand>,
        targetRir:Int=2,
        historicalWeight:BigDecimal=n("30")
    ):NativeKotlinMixedHistoryCompletion.Composer {
        val before=original.evidence.exerciseReviews.single()
        require(issued.size==before.workingSets.size)
        val historicalLoad=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,historicalWeight)
        val sets=before.workingSets.mapIndexed {i,fact->
            fact.copy(issuedReps=issued[i],issuedRir=targetRir,
                issuedLoad=historicalLoad,
                actual=fact.actual?.copy(load=historicalWeight))
        }
        val comparable=issued.distinct().size==1
        val target=if(comparable) issued.first() else null
        val originalFacts=checkNotNull(before.progressionWorkout)
        val workout=target?.let {originalFacts.copy(targetReps=it,
            observations=originalFacts.observations.map {
                it.copy(actualLoad=historicalWeight,issuedLoad=historicalWeight)
            })}
        val assessed=workout?.let {
            DoubleProgressionEvidenceDomain.assess(
                it.targetReps,it.expectedWorkingSets,it.observations)
        }
        val review=before.copy(
            workingSets=sets,
            progressionGate=if(comparable)
                KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW
            else KotlinHistoryProgressionGate.MIXED_REP_BANDS,
            progressionReview=assessed,progressionWorkout=workout)
        return original.copy(evidence=original.evidence.copy(
            sets=sets,exerciseReviews=listOf(review)))
    }

    private fun custom(i:Int):NativeKotlinMixedHistoryCompletion.Custom {
        val visit=CustomHistoricalVisitFact(
            "slot-a","db-press","custom-set-$i",CustomWorkoutVisitPart.MAIN,
            DraftSetType.WORKING,verified,RepBand(8,12),2,null,
            KotlinJournalSetOutcome.SKIPPED,null,"event-custom-$i")
        return NativeKotlinMixedHistoryCompletion.Custom(
            KotlinHistoryManifestEntry(i.toLong(),"workout-$i",
                "receipt-$i","finish-$i",3,0),
            CustomWorkoutHistoricalEvidence("athlete-1","workout-$i",
                "cycle-1",1,"finish-$i",3,listOf(visit)))
    }
    private fun timeline(vararg values:NativeKotlinMixedHistoryCompletion,
        revision:Long=7)=NativeKotlinMixedHistoryTimeline(
        "athlete-1",revision,values.toList())
    private fun inspect(t:NativeKotlinMixedHistoryTimeline,
        accepted:NativeKotlinWorkoutApproval=approval(),
        expectedRevision:Long=7)=NativeKotlinCurrentLoadCoachReview.inspect(
        accepted,t,"db-press",expectedRevision,n("5"),
        CoachReadiness.NORMAL,false)

    @Test fun lastTwoVerifiedFullHistoryAppearancesCanRecommendOnlyReview() {
        val result=inspect(timeline(composer(1),composer(2)))
        assertTrue(result.toString(),result is NativeCurrentLoadReviewResult.Reviewed)
        val ready=result as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-2"),ready.comparedWorkoutIds)
        assertEquals(7L,ready.historyRevision)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            ready.coaching.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,ready.coaching.kind)
        assertEquals(n("35"),ready.coaching.candidateLoad)
        assertFalse(ready.mayApply)
        assertFalse(ready.mayIssueWorkout)
        assertFalse(ready.coaching.maySaveProgram)
    }

    @Test fun twoCeilingSessionsCanIncreaseLoadAfterIndividualizedRepTargets() {
        val first=withExactIssuedTargets(composer(1),
            listOf(RepBand(12,12),RepBand(11,11)))
        val second=withExactIssuedTargets(composer(2),
            listOf(RepBand(11,11),RepBand(12,12)))
        assertEquals(KotlinHistoryProgressionGate.MIXED_REP_BANDS,
            first.evidence.exerciseReviews.single().progressionGate)
        assertEquals(listOf(RepBand(12,12),RepBand(11,11)),
            first.evidence.exerciseReviews.single().workingSets.map {it.issuedReps})
        val result=inspect(timeline(first,second))
        assertTrue(result.toString(),
            result is NativeCurrentLoadReviewResult.Reviewed)
        val coach=(result as NativeCurrentLoadReviewResult.Reviewed).coaching
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coach.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,coach.kind)
        assertEquals(n("35"),coach.candidateLoad)
        assertFalse(coach.maySaveProgram)
        assertEquals(listOf(RepBand(11,11),RepBand(12,12)),
            second.evidence.exerciseReviews.single().workingSets.map {it.issuedReps})
    }

    @Test fun historicalExactTargetsRemainComparableToOrdinaryBroadRange() {
        val previous=withExactIssuedTargets(composer(1),
            listOf(RepBand(12,12),RepBand(12,12)))
        val result=inspect(timeline(previous,composer(2)))
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            (result as NativeCurrentLoadReviewResult.Reviewed).coaching.status)
    }

    @Test fun overloadedNewerWorkoutStillOffersOnlyVerifiedDemonstratedLowerLoad() {
        val demonstrated=withExactIssuedTargets(
            composer(1,completedReps=listOf(12,12)),
            listOf(RepBand(11,11),RepBand(12,12)),
            historicalWeight=n("25"))
        val overloaded=withExactIssuedTargets(
            composer(2,completedReps=listOf(7,7)),
            listOf(RepBand(12,12),RepBand(11,11)),
            historicalWeight=n("30"))
        val review=inspect(timeline(demonstrated,overloaded))
        assertTrue(review.toString(),review is NativeCurrentLoadReviewResult.Reviewed)
        val coach=(review as NativeCurrentLoadReviewResult.Reviewed).coaching
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coach.status)
        assertEquals(CoachNextLoadChangeKind.RETURN_TO_DEMONSTRATED_LOAD,
            coach.kind)
        assertEquals(n("25"),coach.candidateLoad)
        assertEquals(n("30"),coach.originalLoad)
        assertFalse(coach.maySaveProgram)
        // The original captured weights remain the evidence; never
        // substitute the athlete's new planned load for the prior workout.
        assertEquals(n("25"),
            (demonstrated.evidence.sets.first().issuedLoad
                as DraftSetLoad.Verified).value)
        assertEquals(n("30"),
            (overloaded.evidence.sets.first().issuedLoad
                as DraftSetLoad.Verified).value)
    }

    @Test fun historicalNarrowTargetsDoNotAuthorizeIncreaseAfterMissedRep() {
        val before=withExactIssuedTargets(composer(1),
            listOf(RepBand(12,12),RepBand(11,11)))
        val poor=withExactIssuedTargets(
            composer(2,completedReps=listOf(12,11)),
            listOf(RepBand(12,12),RepBand(12,12)))
        val result=inspect(timeline(before,poor))
        assertTrue(result is NativeCurrentLoadReviewResult.Reviewed)
        val coach=(result as NativeCurrentLoadReviewResult.Reviewed).coaching
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coach.status)
        assertNull(coach.candidateLoad)
        val later=withExactIssuedTargets(
            composer(3,completedReps=listOf(12,9)),
            listOf(RepBand(12,12),RepBand(11,11)))
        val failed=inspect(timeline(before,composer(2),later))
        assertTrue(failed is NativeCurrentLoadReviewResult.Reviewed)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            (failed as NativeCurrentLoadReviewResult.Reviewed).coaching.status)
    }

    @Test fun forgedOrIncompatibleNarrowRangesNeverJustifyNextWeight() {
        val first=composer(1)
        val invalid=listOf(
            withExactIssuedTargets(composer(2),
                listOf(RepBand(12,12),RepBand(13,13))),
            withExactIssuedTargets(composer(2),
                listOf(RepBand(8,10),RepBand(12,12))),
            withExactIssuedTargets(composer(2),
                listOf(RepBand(12,12),RepBand(11,11)),targetRir=3)
        )
        for(latest in invalid) {
            assertEquals(NativeCurrentLoadReviewResult.Rejected(
                NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT),
                inspect(timeline(first,latest)))
        }
        val changed=withExactIssuedTargets(composer(2),
            listOf(RepBand(12,12),RepBand(11,11)))
        val base=changed.evidence.exerciseReviews.single()
        val load=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,n("35"))
        val stale=changed.copy(evidence=changed.evidence.copy(
            sets=changed.evidence.sets.mapIndexed {i,item->
                if(i==0) item.copy(issuedLoad=load) else item
            },
            exerciseReviews=listOf(base.copy(
                workingSets=base.workingSets.mapIndexed {i,item->
                    if(i==0) item.copy(issuedLoad=load) else item
                }))))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT),
            inspect(timeline(first,stale)))
    }

    @Test fun doNotSkipLatestCustomVisitToCherryPickOlderSuccessfulWorkouts() {
        val result=inspect(timeline(composer(1),composer(2),custom(3)))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.CUSTOM_OR_UNWEIGHTED_INTERVENING_VISIT),
            result)
    }

    @Test fun doNotSkipPoorOrIncompatibleNewestComposerSession() {
        val poor=inspect(timeline(composer(1),composer(2),
            composer(3,completedReps=listOf(8,9))))
        assertTrue(poor is NativeCurrentLoadReviewResult.Reviewed)
        val reviewed=poor as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-2","workout-3"),reviewed.comparedWorkoutIds)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.coaching.status)
        assertNull(reviewed.coaching.candidateLoad)
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.PURPOSE_CHANGED),
            inspect(timeline(composer(1,purpose=ComposerSessionPurpose.STRENGTH_SKILL),
                composer(2))))
    }

    @Test fun staleOwnerProgramOrRevisionCanNeverProposeNextTarget() {
        val good=timeline(composer(1),composer(2))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.HISTORY_NOT_CURRENT),
            inspect(good,expectedRevision=8))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.SOURCE_NOT_CURRENT),
            inspect(good,accepted=approval().copy(
                currentSource=source.copy(programRevision=9))))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.HISTORICAL_SOURCE_CHANGED),
            inspect(timeline(composer(1,source.copy(gymRevision=2)),
                composer(2))))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.INVALID_HISTORY),
            inspect(timeline(composer(2),composer(1))))
    }

    @Test fun missingHistoryCustomAndNoEquipmentAreNeverTreatedAsProof() {
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.NOT_ENOUGH_EXERCISE_HISTORY),
            inspect(timeline(composer(1))))
        val altered=approval().copy(program=program().copy(
            exercises=program().exercises.map { if(it.exerciseId=="db-press")
                it.copy(equipment=ComposerEquipment.Bodyweight) else it }))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT),
            inspect(timeline(composer(1),composer(2)),accepted=altered))
    }

    private fun purposeLoad(
        sourceCompletion:NativeKotlinMixedHistoryCompletion.Composer,
        weight:String,
        actualReps:List<Int> = listOf(11,10),
        actualRir:Int=3
    ):NativeKotlinMixedHistoryCompletion.Composer {
        val before=sourceCompletion.evidence.exerciseReviews.single()
        val replacement=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n(weight))
        val revised=before.workingSets.mapIndexed {i,set ->
            set.copy(issuedLoad=replacement,
                actual=KotlinPerformedSet(n(weight),LoadUnit.LB,
                    actualReps[i],actualRir))
        }
        val evidence=sourceCompletion.evidence
        return sourceCompletion.copy(evidence=evidence.copy(
            sets=revised,
            exerciseReviews=listOf(before.copy(workingSets=revised,
                progressionGate=KotlinHistoryProgressionGate.MIXED_REP_BANDS,
                progressionReview=null,progressionWorkout=null))))
    }

    @Test fun nativeRepReviewUsesLatestMatchingPurposeAndExactSessionLoad() {
        val policies=listOf(
            policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL)
                .copy(purposeWorkingLoad=n("25")),
            policy("db-press",ComposerSessionPurpose.HYPERTROPHY),
            policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL),
            policy("db-row",ComposerSessionPurpose.HYPERTROPHY))
        val approved=approval().copy(policies=policies,
            grant=KotlinWorkoutSelectionGrant("grant-3","workout-3",source,0))
        val strength=purposeLoad(composer(1,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL),"25")
        val volume=composer(2,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val result=NativeKotlinCurrentRepPrescriptionDomain.inspect(
            approved,timeline(strength,volume),"db-press",
            CoachReadiness.NORMAL,false)
        assertTrue(result.toString(),
            result is NativeCurrentRepPrescriptionResult.Reviewed)
        val reviewed=result as NativeCurrentRepPrescriptionResult.Reviewed
        assertEquals("workout-1",reviewed.priorWorkoutId)
        assertTrue(reviewed.proposal.toString(),
            reviewed.proposal is AdaptiveSetPrescriptionResult.Candidate)
        val candidate=reviewed.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("db-press",candidate.exerciseId)
        assertEquals(2,candidate.newRepTargets.size)
        assertTrue(candidate.workout.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
        assertFalse(candidate.mayIssueWorkout)
        assertEquals(n("30"),
            ((approved.program.exercises.first().equipment as
                ComposerEquipment.Verified).plannedLoad))
    }

    @Test fun nativePurposeReviewWillNotSkipCustomOrChangedSources() {
        val policies=policies.map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(purposeWorkingLoad=n("25"))
            else it
        }
        val approved=approval().copy(policies=policies,
            grant=KotlinWorkoutSelectionGrant("grant-3","workout-3",source,0))
        val strength=purposeLoad(composer(1,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL),"25")
        fun inspect(vararg histories:NativeKotlinMixedHistoryCompletion)=
            NativeKotlinCurrentRepPrescriptionDomain.inspect(
                approved,timeline(*histories),"db-press",
                CoachReadiness.NORMAL,false)
        assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
            NativeCurrentRepPrescriptionRefusal.CUSTOM_WORKOUT_IS_LATEST),
            inspect(strength,custom(2)))
        val changed=composer(2,sourceOverride=source.copy(programRevision=9))
        assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
            NativeCurrentRepPrescriptionRefusal.EXERCISE_SOURCE_CHANGED),
            inspect(strength,changed))
        val twoUnrelated=composer(1,
            purpose=ComposerSessionPurpose.HYPERTROPHY)
        assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
            NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED),
            inspect(twoUnrelated))
    }


    @Test fun interleavedPurposeSpecificLoadReviewChangesOnlyTargetPolicy() {
        val separate=policies.map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(purposeWorkingLoad=n("25"))
            else it
        }
        val approved=approval().copy(policies=separate,
            grant=KotlinWorkoutSelectionGrant("grant-4","workout-4",source,0))
        val first=withExactIssuedTargets(
            composer(1,purpose=ComposerSessionPurpose.STRENGTH_SKILL),
            listOf(RepBand(12,12),RepBand(12,12)),
            historicalWeight=n("25"))
        val middle=composer(2,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val latest=withExactIssuedTargets(
            composer(3,purpose=ComposerSessionPurpose.STRENGTH_SKILL),
            listOf(RepBand(12,12),RepBand(12,12)),
            historicalWeight=n("25"))
        val result=inspect(timeline(first,middle,latest),accepted=approved)
        assertTrue(result.toString(),result is NativeCurrentLoadReviewResult.Reviewed)
        val reviewed=result as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),reviewed.comparedWorkoutIds)
        val coach=reviewed.coaching
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,coach.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,coach.kind)
        assertEquals(n("25"),coach.originalLoad)
        assertEquals(n("30"),coach.candidateLoad)
        assertEquals(listOf(0),coach.affectedDayIndices)
        assertEquals(approved.program,coach.candidateProgram)
        val updated=checkNotNull(coach.candidatePolicies)
        assertEquals(n("30"),updated.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }.purposeWorkingLoad)
        assertEquals(null,updated.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.purposeWorkingLoad)
        assertEquals(approved.policies.filter {
            it.exerciseId=="db-row"
        },updated.filter {it.exerciseId=="db-row"})
        assertFalse(coach.maySaveProgram)
    }

    @Test fun purposeSpecificLoadingNeverSkipsFailedSamePurposeOrCustomSource() {
        val separate=policies.map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(purposeWorkingLoad=n("25")) else it
        }
        val approved=approval().copy(policies=separate,
            grant=KotlinWorkoutSelectionGrant("grant-4","workout-4",source,0))
        fun strength(i:Int,reps:List<Int>=listOf(12,12))=
            withExactIssuedTargets(
                composer(i,completedReps=reps,
                    purpose=ComposerSessionPurpose.STRENGTH_SKILL),
                listOf(RepBand(12,12),RepBand(12,12)),
                historicalWeight=n("25"))
        val poor=inspect(timeline(strength(1),composer(2),
            strength(3,listOf(8,7))),accepted=approved)
        assertTrue(poor is NativeCurrentLoadReviewResult.Reviewed)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            (poor as NativeCurrentLoadReviewResult.Reviewed).coaching.status)
        assertNull(poor.coaching.candidatePolicies)
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.CUSTOM_OR_UNWEIGHTED_INTERVENING_VISIT),
            inspect(timeline(strength(1),custom(2),strength(3)),accepted=approved))
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.HISTORICAL_SOURCE_CHANGED),
            inspect(timeline(strength(1),
                composer(2,sourceOverride=source.copy(programRevision=9)),
                strength(3)),accepted=approved))
    }

}
