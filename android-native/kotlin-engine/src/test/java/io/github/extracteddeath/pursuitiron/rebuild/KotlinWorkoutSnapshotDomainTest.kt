package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class KotlinWorkoutSnapshotDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2"
    )
    private val grant=KotlinWorkoutSelectionGrant("approval-1","session-1",source,0)
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(
            id,if(muscle==TrainingMuscle.CHEST)
                ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,3,3,2,1,2,role=role
        )
    private val press=option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY)
    private val row=option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)
    private fun program(hard:Int?=null,options:List<ComposerExerciseOption> =
        listOf(press,row)):ComposerRequest {
        val window=DeclaredSessionWindow(1,120,hard,60)
        val bounds=TrainingMuscle.entries.map { muscle->
            when(muscle) {
                TrainingMuscle.CHEST,TrainingMuscle.LATS ->
                    DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
                else -> DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
            }
        }
        return ComposerRequest(
            ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,options,3,1,
            listOf(ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY))
        )
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose,
        reps:RepBand,rir:Int?,rest:Int,
        scheme:SetLoadScheme=SetLoadScheme.STRAIGHT,
        backoff:BigDecimal?=null,warmups:List<DeclaredWarmupStep> = emptyList(),
        backoffReps:RepBand?=null,backoffRir:Int?=null)=ExercisePrescriptionPolicy(
        id,purpose,reps,rir,90,rest,scheme,backoff,warmups,backoffReps,backoffRir)
    private fun policies()=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL,
            RepBand(3,5),3,240,SetLoadScheme.LAST_SET_BACKOFF,n("5"),
            listOf(DeclaredWarmupStep(n("20"),8,60,60)),RepBand(6,8),2),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY,
            RepBand(8,12),2,120),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL,
            RepBand(6,10),2,180),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY,
            RepBand(10,15),null,120)
    )
    private fun request(
        approved:ComposerRequest=program(),
        authoredPolicies:List<ExercisePrescriptionPolicy> = policies()
    )=KotlinWorkoutSnapshotRequest(source,source,grant,approved,authoredPolicies)

    @Test fun stagesExactUnmodifiedKotlinPrescriptionsForOneApprovedDay() {
        val draft=CoachSessionPrescriptionDomain.draft(program(),policies())
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,draft.status)
        val result=KotlinWorkoutSnapshotDomain.stage(request())
        val staged=(result as KotlinWorkoutSnapshotResult.Staged).snapshot
        assertFalse(staged.persisted)
        assertFalse(staged.canModifyJournal)
        assertEquals(source,staged.source)
        assertEquals("session-1",staged.workoutId)
        assertEquals(0,staged.calendarDay)
        assertEquals(0,staged.dayIndex)
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,staged.purpose)
        assertEquals(draft.sessions.first().estimatedMinutes,staged.estimatedMinutes)
        val expected=draft.sessions.first().exercises.flatMap {ex->
            ex.sets.map {set->set.stableSetKey}
        }
        assertEquals(expected,staged.targets.map {it.setKey})
        assertTrue(staged.targets.none {it.setKey.contains(":d2:")})
        assertEquals(DraftSetType.WARMUP,staged.targets.first().type)
        assertEquals(n("20"),
            (staged.targets.first().load as DraftSetLoad.Verified).value)
        assertNull(staged.targets.first().targetRir)
        val pressWorking=staged.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }
        assertEquals(n("25"),(pressWorking.last().load as DraftSetLoad.Verified).value)
        assertEquals(RepBand(6,8),pressWorking.last().reps)
        assertEquals(2,pressWorking.last().targetRir)
        assertEquals(240,pressWorking.last().restSeconds)
        assertTrue(pressWorking.dropLast(1).all {
            (it.load as DraftSetLoad.Verified).value==n("30") &&
                it.reps==RepBand(3,5) && it.targetRir==3
        })
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (staged.targets as MutableList<KotlinWorkoutTarget>).clear()
        }
        assertEquals(expected.size,staged.targets.size)
    }

    @Test fun staleProgramAthleteGymAndCoachRulesNeverProduceWorkout() {
        for(next in listOf(
            source.copy(athleteRevision=5),
            source.copy(programRevision=9),
            source.copy(gymRevision=4),
            source.copy(catalogRevision="catalog-6"),
            source.copy(coachingRulesRevision="coach-rules-3")
        )) {
            assertEquals(
                KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.SOURCE_CHANGED),
                KotlinWorkoutSnapshotDomain.stage(request().copy(currentSource=next))
            )
        }
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_SOURCE),
            KotlinWorkoutSnapshotDomain.stage(
                request().copy(source=source.copy(programRevision=0))))
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_GRANT),
            KotlinWorkoutSnapshotDomain.stage(
                request().copy(grant=grant.copy(approvedSource=
                    source.copy(gymRevision=20)))))
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_GRANT),
            KotlinWorkoutSnapshotDomain.stage(
                request().copy(grant=grant.copy(dayIndex=9))))
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_GRANT),
            KotlinWorkoutSnapshotDomain.stage(
                request().copy(grant=grant.copy(workoutId=""))))
    }

    @Test fun missingPolicyUnapprovedLoadAndImpossibleTimeDoNotCreatePartialWorkout() {
        val missing=policies().filterNot {
            it.exerciseId=="db-row" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(
            KotlinSnapshotRejection.PROGRAM_UNRESOLVED),
            KotlinWorkoutSnapshotDomain.stage(request(authoredPolicies=missing)))
        val unverified=press.copy(equipment=ComposerEquipment.Verified(
            LoadUnit.LB,n("38.5"),stock))
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(
            KotlinSnapshotRejection.PROGRAM_UNRESOLVED),
            KotlinWorkoutSnapshotDomain.stage(
                request(approved=program(options=listOf(unverified,row)))))
        val hard=program(hard=30)
        val impossible=policies().map {it.copy(restSecondsPerSet=3600)}
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(
            KotlinSnapshotRejection.PROGRAM_UNRESOLVED),
            KotlinWorkoutSnapshotDomain.stage(
                request(approved=hard,authoredPolicies=impossible)))
        val existing=program()
        val malformed=existing.copy(days=listOf(
            existing.days[0],existing.days[0],existing.days[2]))
        assertEquals(KotlinWorkoutSnapshotResult.Rejected(
            KotlinSnapshotRejection.INVALID_PROGRAM),
            KotlinWorkoutSnapshotDomain.stage(
                request(approved=malformed)))
    }

    @Test fun anotherApprovedDayHasItsOwnActualTargetsAndPurpose() {
        val changed=grant.copy(dayIndex=2,workoutId="session-2",grantId="approval-2")
        val result=KotlinWorkoutSnapshotDomain.stage(request().copy(grant=changed))
        val snapshot=(result as KotlinWorkoutSnapshotResult.Staged).snapshot
        assertEquals(2,snapshot.dayIndex)
        assertEquals(4,snapshot.calendarDay)
        assertEquals("session-2",snapshot.workoutId)
        assertEquals(ComposerSessionPurpose.HYPERTROPHY,snapshot.purpose)
        assertTrue(snapshot.targets.all {it.setKey.contains(":d2:")})
        assertTrue(snapshot.targets.none {it.type==DraftSetType.WARMUP})
        assertTrue(snapshot.targets.filter {it.exerciseId=="db-row"}.all {
            it.reps==RepBand(10,15) && it.targetRir==null
        })
    }

    @Test fun kotlinProgramToWorkoutToCompletedJournalRunsWithoutJavaScript() {
        val staged=(KotlinWorkoutSnapshotDomain.stage(request()) as
            KotlinWorkoutSnapshotResult.Staged).snapshot
        val stored=KotlinStoredWorkoutIdentity(
            "local-commit-1",staged.workoutId,staged.source,1)
        var journal=KotlinWorkoutJournalDomain.begin(staged,stored)
        assertEquals(staged.targets.size,journal.unresolvedSetCount)
        for(target in staged.targets) {
            val physical=target.load as? DraftSetLoad.Verified
            val performed=if(physical==null)
                KotlinPerformedSet(null,null,target.reps.minimum,
                    if(target.type==DraftSetType.WARMUP)null else 2)
            else KotlinPerformedSet(physical.value,physical.unit,target.reps.minimum,
                if(target.type==DraftSetType.WARMUP)null else 2)
            val operation=KotlinJournalOperation.RecordSet(
                "receipt-${journal.revision}",journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,performed)
            val proposal=KotlinWorkoutJournalDomain.reduce(journal,operation)
            assertTrue(proposal.toString(),proposal is KotlinJournalDecision.Proposed)
            journal=(proposal as KotlinJournalDecision.Proposed).state
            assertFalse(journal.persisted)
        }
        assertEquals(0,journal.unresolvedSetCount)
        assertTrue(journal.canProposeFinish)
        val finish=KotlinJournalOperation.Finish("finish-complete",journal.revision)
        val result=KotlinWorkoutJournalDomain.reduce(journal,finish)
        val completed=(result as KotlinJournalDecision.Proposed).state
        assertEquals(staged.targets.size+1,completed.revision.toInt())
        assertEquals("finish-complete",completed.finishedByEventId)
        assertFalse(completed.canProposeFinish)
        assertEquals(staged.targets,completed.snapshot.targets)
        assertEquals(KotlinJournalDecision.Proposed(completed,true),
            KotlinWorkoutJournalDomain.reduce(completed,finish))
    }

    private fun reviewedStraightProgram(load: String = "30"): ComposerRequest {
        val a=press.copy(
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n(load),stock),
            minimumSetsPerVisit=2,maximumSetsPerVisit=2)
        val b=row.copy(minimumSetsPerVisit=2,maximumSetsPerVisit=2)
        return program(options=listOf(a,b))
    }

    private fun reviewedStraightPolicies()=policies().map { p ->
        if(p.exerciseId=="db-press") p.copy(
            workingReps=RepBand(8,12), targetRir=2,
            scheme=SetLoadScheme.STRAIGHT, maximumAuthorizedBackoff=null,
            warmups=emptyList(),backoffReps=null,backoffRir=null)
        else p
    }

    private fun observed(
        id:String, loads:List<Int>, reps:List<Int>,
        rir:List<Int?>=listOf(2,2)
    )=ProgressionWorkoutEvidence(
        id,"db-press",LoadUnit.LB,RepBand(8,12),2,
        loads.indices.map { i -> WorkingSetFact(
            i,SetOutcome.COMPLETED,n(loads[i].toString()),reps[i],rir[i])
        })

    private fun nextLoadRequest(
        previous:ProgressionWorkoutEvidence=
            observed("week-old",listOf(30,30),listOf(12,12)),
        latest:ProgressionWorkoutEvidence=
            observed("week-new",listOf(30,30),listOf(12,12)),
        approved:ComposerRequest=reviewedStraightProgram(),
        declaredPolicies:List<ExercisePrescriptionPolicy>=reviewedStraightPolicies(),
        current:KotlinTrainingSourceRevision=source,
        previousHistory:Long=7,
        currentHistory:Long=7,
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false
    )=CoachNextProgramLoadRequest(
        approved,source,current,previousHistory,currentHistory,
        previous,latest,"db-press",declaredPolicies,n("5"),
        readiness,restricted
    )

    @Test fun twoCompleteComparableWorkoutsProposeAuditedNextProgramLoad() {
        val original=reviewedStraightProgram()
        val reviewed=CoachNextProgramLoadReviewDomain.review(
            nextLoadRequest(approved=original))
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,reviewed.kind)
        assertEquals(n("30"),reviewed.originalLoad)
        assertEquals(n("35"),reviewed.candidateLoad)
        val next=checkNotNull(reviewed.candidateProgram)
        assertTrue(reviewed.affectedDayIndices.isNotEmpty())
        assertTrue(reviewed.affectedDayIndices.all {
            index -> index in 0..2
        })
        assertEquals(n("35"),
            ((next.exercises.first { it.exerciseId=="db-press" }).equipment
                as ComposerEquipment.Verified).plannedLoad)
        assertEquals(n("30"),
            ((original.exercises.first { it.exerciseId=="db-press" }).equipment
                as ComposerEquipment.Verified).plannedLoad)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,
            CoachSessionPrescriptionDomain.draft(next,reviewedStraightPolicies()).status)
        assertFalse(reviewed.maySaveProgram)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayModifyHistory)
    }

    @Test fun unearnedHeavySetsReturnOnlyToVerifiedPriorCapacityForReview() {
        val before=observed("week-old",listOf(30,30),listOf(12,12))
        val failed=observed("week-new",listOf(30,35),listOf(3,2),
            listOf(2,0))
        val reviewed=CoachNextProgramLoadReviewDomain.review(nextLoadRequest(
            previous=before,latest=failed,approved=reviewedStraightProgram("35")))
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertEquals(CoachNextLoadChangeKind.RETURN_TO_DEMONSTRATED_LOAD,
            reviewed.kind)
        assertEquals(n("30"),reviewed.candidateLoad)
        assertNotEquals(n("35"),reviewed.candidateLoad)
        assertFalse(reviewed.mayIssueWorkout)
        assertEquals("week-new",failed.workoutId)
    }

    @Test fun noComparableEvidenceStaleHistoryAndRestrictionsCannotOverrideSavedTargets() {
        val old=observed("week-old",listOf(30,30),listOf(12,12))
        val poor=observed("week-new",listOf(30,35),listOf(3,2),
            listOf(2,0))
        val cases=listOf(
            nextLoadRequest(current=source.copy(gymRevision=4)),
            nextLoadRequest(previousHistory=7,currentHistory=8),
            nextLoadRequest(readiness=CoachReadiness.LOW),
            nextLoadRequest(restricted=true),
            nextLoadRequest(previous=old,latest=poor),
            nextLoadRequest(previous=old,latest=old),
            nextLoadRequest(previous=old,latest=poor.copy(
                exerciseId="other-exercise")),
            nextLoadRequest(latest=observed(
                "week-new",listOf(30,30),listOf(8,9))),
            nextLoadRequest(approved=reviewedStraightProgram("35"))
        )
        for(candidate in cases) {
            val review=CoachNextProgramLoadReviewDomain.review(candidate)
            assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
                review.status)
            assertNull(review.candidateProgram)
            assertNull(review.candidateLoad)
            assertFalse(review.mayIssueWorkout)
        }
    }

    @Test fun customBackoffAndWarmupsRemainAuthoredRatherThanCovertlyChanged() {
        val current=program()
        val review=CoachNextProgramLoadReviewDomain.review(nextLoadRequest(
            approved=current,declaredPolicies=policies()))
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            review.status)
        assertNull(review.candidateProgram)
        assertEquals(n("30"),
            ((current.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
    }

}
