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
        KotlinWorkoutSelectionGrant("grant-1","workout-1",source,1),
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
                "workout-$i","finish-$i",3,facts,listOf(review)),purpose,1)
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
            NativeCurrentLoadReviewRefusal.NOT_ENOUGH_EXERCISE_HISTORY),
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
            purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            .copy(dayIndex=0),"25")
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
            purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            .copy(dayIndex=0),"25")
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
            composer(1,purpose=ComposerSessionPurpose.STRENGTH_SKILL)
                .copy(dayIndex=0),
            listOf(RepBand(12,12),RepBand(12,12)),
            historicalWeight=n("25"))
        val middle=composer(2,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val latest=withExactIssuedTargets(
            composer(3,purpose=ComposerSessionPurpose.STRENGTH_SKILL)
                .copy(dayIndex=0),
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
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                it.dayIndex==0
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
        fun strength(i:Int,reps:List<Int> = listOf(12,12))=
            withExactIssuedTargets(
                composer(i,completedReps=reps,
                    purpose=ComposerSessionPurpose.STRENGTH_SKILL)
                    .copy(dayIndex=0),
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


    @Test fun defaultHypertrophyProgressesWhileStrengthHasDistinctWeight() {
        val mixed=policies.map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("25")) else p
        }
        val approved=approval().copy(policies=mixed,
            grant=KotlinWorkoutSelectionGrant("grant-5","workout-5",source,1))
        val first=composer(1,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val strength=withExactIssuedTargets(composer(2,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            .copy(dayIndex=0),
            listOf(RepBand(12,12),RepBand(12,12)),historicalWeight=n("25"))
        val latest=composer(3,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val result=inspect(timeline(first,strength,latest),accepted=approved)
        assertTrue(result.toString(),result is NativeCurrentLoadReviewResult.Reviewed)
        val coach=(result as NativeCurrentLoadReviewResult.Reviewed).coaching
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,coach.status)
        assertEquals(n("30"),coach.originalLoad)
        assertEquals(n("35"),coach.candidateLoad)
        assertEquals(listOf(1),coach.affectedDayIndices)
        assertEquals(approved.program,coach.candidateProgram)
        val after=checkNotNull(coach.candidatePolicies)
        assertEquals(n("25"),after.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }.purposeWorkingLoad)
        assertEquals(n("35"),after.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY &&
                it.dayIndex==1
        }.purposeWorkingLoad)
        assertNull(after.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY &&
                it.dayIndex==null
        }.purposeWorkingLoad)
        assertEquals(approved.policies.filter {it.exerciseId=="db-row"},
            after.filter {it.exerciseId=="db-row"})
    }

    @Test fun failedLatestDefaultPurposeDoesNotEarnWeightFromOlderSuccesses() {
        val mixed=policies.map {p ->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("25")) else p
        }
        val approved=approval().copy(policies=mixed,
            grant=KotlinWorkoutSelectionGrant("grant-5","workout-5",source,1))
        val first=composer(1,purpose=ComposerSessionPurpose.HYPERTROPHY)
        val middle=composer(2,purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            .copy(dayIndex=0)
        val failed=composer(3,completedReps=listOf(12,7),
            purpose=ComposerSessionPurpose.HYPERTROPHY)
        val review=inspect(timeline(first,middle,failed),accepted=approved)
        assertTrue(review.toString(),review is NativeCurrentLoadReviewResult.Reviewed)
        val result=review as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),result.comparedWorkoutIds)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            result.coaching.status)
        assertNull(result.coaching.candidateLoad)
        assertNull(result.coaching.candidatePolicies)
    }


    @Test fun samePurposeDifferentDayCannotSupplyEachOthersProgressionEvidence() {
        val dayPolicies=policies+policies.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.copy(dayIndex=2,purposeWorkingLoad=n("25"))
        val next=approval().copy(policies=dayPolicies,grant=
            KotlinWorkoutSelectionGrant("grant-4","workout-4",source,1))
        val upper=composer(1).copy(dayIndex=1)
        val push=withExactIssuedTargets(composer(2).copy(dayIndex=2),
            listOf(RepBand(12,12),RepBand(12,12)),historicalWeight=n("25"))
        val notEnough=inspect(timeline(upper,push),accepted=next)
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.NOT_ENOUGH_EXERCISE_HISTORY),
            notEnough)
        val last=composer(3).copy(dayIndex=1)
        val valid=inspect(timeline(upper,push,last),accepted=next)
        assertTrue(valid.toString(),valid is NativeCurrentLoadReviewResult.Reviewed)
        val coached=valid as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),coached.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coached.coaching.status)
        assertEquals(listOf(1),coached.coaching.affectedDayIndices)
        val after=checkNotNull(coached.coaching.candidatePolicies)
        assertEquals(n("35"),after.single {
            it.exerciseId=="db-press" && it.dayIndex==1
        }.purposeWorkingLoad)
        assertEquals(n("25"),after.single {
            it.exerciseId=="db-press" && it.dayIndex==2
        }.purposeWorkingLoad)
        assertFalse(coached.mayApply)
    }

    @Test fun repReviewerUsesMatchingDayEvenWhenSessionPurposesMatch() {
        val scoped=policies+policies.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.copy(dayIndex=2,purposeWorkingLoad=n("25"))
        val approval=approval().copy(policies=scoped,
            grant=KotlinWorkoutSelectionGrant("grant-4","workout-4",source,1))
        val other=composer(1).copy(dayIndex=2)
        assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
            NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED),
            NativeKotlinCurrentRepPrescriptionDomain.inspect(
                approval,timeline(other),"db-press",CoachReadiness.NORMAL,false))
        val upper=composer(2).copy(dayIndex=1)
        val returned=NativeKotlinCurrentRepPrescriptionDomain.inspect(
            approval,timeline(other,upper),"db-press",
            CoachReadiness.NORMAL,false)
        assertTrue(returned.toString(),
            returned is NativeCurrentRepPrescriptionResult.Reviewed)
        assertEquals("workout-2",
            (returned as NativeCurrentRepPrescriptionResult.Reviewed).priorWorkoutId)
    }


    @Test fun unscopedRepPolicyKeepsUpperAndPushEvidenceIndependent() {
        // Without an explicit day-specific policy, day ownership must still
        // be taken from the original authenticated issued workout.
        val next=approval().copy(grant=
            KotlinWorkoutSelectionGrant("grant-4","workout-4",source,1))
        val unrelated=composer(1).copy(dayIndex=2)
        val absent=NativeKotlinCurrentRepPrescriptionDomain.inspect(
            next,timeline(unrelated),"db-press",CoachReadiness.NORMAL,false)
        assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
            NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED),absent)
        // Sufficient actual RIR is deliberate: the source must be an
        // earned rep candidate, not merely a matching day identity.
        val upper=purposeLoad(composer(2).copy(dayIndex=1),
            "30",listOf(11,10),3)
        val latestPush=composer(3,completedReps=listOf(12,12))
            .copy(dayIndex=2)
        val valid=NativeKotlinCurrentRepPrescriptionDomain.inspect(
            next,timeline(unrelated,upper,latestPush),"db-press",
            CoachReadiness.NORMAL,false)
        assertTrue(valid.toString(),
            valid is NativeCurrentRepPrescriptionResult.Reviewed)
        val selected=valid as NativeCurrentRepPrescriptionResult.Reviewed
        assertEquals("workout-2",selected.priorWorkoutId)
        assertFalse(selected.canIssueWorkout)
        assertTrue(selected.proposal.toString(),
            selected.proposal is AdaptiveSetPrescriptionResult.Candidate)
        assertEquals(listOf(11,10),
            (selected.proposal as AdaptiveSetPrescriptionResult.Candidate)
                .observedReps)
    }


    private fun historicalWarmup(i:Int,load:String="25"):KotlinHistoricalSetFact =
        KotlinHistoricalSetFact("db-press","db-press:d1:w0",
            DraftSetType.WARMUP,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n(load)),
            RepBand(5,5),null,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(load),LoadUnit.LB,5,null),
            "warmup-$i",null)

    @Test fun currentNativeRepReviewAcceptsRealWarmupsWithoutEditingThem() {
        val current=approval().copy(policies=policies.map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                p.copy(warmups=listOf(DeclaredWarmupStep(n("25"),5,30,60)))
            else p
        })
        val earlier=purposeLoad(composer(1),"30",listOf(11,10),3)
        val history=earlier.copy(evidence=earlier.evidence.copy(
            sets=listOf(historicalWarmup(1))+earlier.evidence.sets))
        val result=NativeKotlinCurrentRepPrescriptionDomain.inspect(
            current,timeline(history),"db-press",CoachReadiness.NORMAL,false)
        assertTrue(result.toString(),
            result is NativeCurrentRepPrescriptionResult.Reviewed)
        val reviewed=result as NativeCurrentRepPrescriptionResult.Reviewed
        assertTrue("Actual native rep review: ${reviewed.proposal}",
            reviewed.proposal is AdaptiveSetPrescriptionResult.Candidate)
        val adapted=reviewed.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals(listOf(12,11),adapted.newRepTargets)
        val warmups=adapted.workout.targets.filter {it.exerciseId=="db-press" &&
            it.type==DraftSetType.WARMUP}
        assertEquals(1,warmups.size)
        assertEquals("db-press:d1:w0",warmups.single().setKey)
        assertEquals(RepBand(5,5),warmups.single().reps)
        assertEquals(n("25"),(warmups.single().load as DraftSetLoad.Verified).value)
        assertEquals(60,warmups.single().restSeconds)
        assertEquals(listOf(11,10),earlier.evidence.exerciseReviews.single()
            .workingSets.map {it.actual!!.reps})
        assertFalse(adapted.mayIssueWorkout)
    }

    @Test fun missingOrChangedHistoricalWarmupCannotPassNativeRepReview() {
        val current=approval().copy(policies=policies.map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                p.copy(warmups=listOf(DeclaredWarmupStep(n("25"),5,30,60)))
            else p
        })
        val performed=purposeLoad(composer(1),"30",listOf(11,10),3)
        val unchanged=performed.copy(evidence=performed.evidence.copy(
            sets=listOf(historicalWarmup(1))+performed.evidence.sets))
        val changed=unchanged.copy(evidence=unchanged.evidence.copy(
            sets=listOf(historicalWarmup(1,"30"))+performed.evidence.sets))
        for(entry in listOf(performed,changed)) {
            assertEquals(NativeCurrentRepPrescriptionResult.Rejected(
                NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT),
                NativeKotlinCurrentRepPrescriptionDomain.inspect(
                    current,timeline(entry),"db-press",
                    CoachReadiness.NORMAL,false))
        }
    }

    @Test fun warmupAwareNativeLoadReviewKeepsPreparationAndProgressesWork() {
        val planned=DeclaredWarmupStep(n("25"),5,30,60)
        val current=approval().copy(policies=policies.map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                p.copy(warmups=listOf(planned))
            else p
        })
        fun completed(i:Int):NativeKotlinMixedHistoryCompletion.Composer {
            val original=composer(i)
            return original.copy(evidence=original.evidence.copy(
                sets=listOf(historicalWarmup(i))+original.evidence.sets))
        }
        val older=completed(1)
        val latest=completed(2)
        val result=inspect(timeline(older,latest),accepted=current)
        assertTrue(result.toString(),result is NativeCurrentLoadReviewResult.Reviewed)
        val coached=(result as NativeCurrentLoadReviewResult.Reviewed).coaching
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coached.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,coached.kind)
        assertEquals(n("35"),coached.candidateLoad)
        assertEquals(listOf(1),coached.affectedDayIndices)
        val revised=checkNotNull(coached.candidatePolicies)
        val perDay=revised.single {it.exerciseId=="db-press" &&
            it.purpose==ComposerSessionPurpose.HYPERTROPHY &&
            it.dayIndex==1}
        assertEquals(n("35"),perDay.purposeWorkingLoad)
        assertEquals(listOf(planned),perDay.warmups)
        assertEquals(listOf(n("25"),n("25")),
            listOf(older,latest).map {entry ->
                (entry.evidence.sets.first().issuedLoad
                    as DraftSetLoad.Verified).value
            })
        assertFalse(result.mayApply)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun nativeLoadReviewRejectsMissingOrChangedIssuedWarmups() {
        val current=approval().copy(policies=policies.map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                p.copy(warmups=listOf(DeclaredWarmupStep(n("25"),5,30,60)))
            else p
        })
        val previous=composer(1)
        val second=composer(2)
        val secondWithWarmup=second.copy(evidence=second.evidence.copy(
            sets=listOf(historicalWarmup(2))+second.evidence.sets))
        val badHistory=second.copy(evidence=second.evidence.copy(
            sets=listOf(historicalWarmup(2,"30"))+second.evidence.sets))
        for(t in listOf(timeline(previous,secondWithWarmup),
            timeline(previous.copy(evidence=previous.evidence.copy(
                sets=listOf(historicalWarmup(1))+previous.evidence.sets)),
                badHistory))) {
            assertEquals(NativeCurrentLoadReviewResult.Rejected(
                NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT),
                inspect(t,accepted=current))
        }
    }
}
